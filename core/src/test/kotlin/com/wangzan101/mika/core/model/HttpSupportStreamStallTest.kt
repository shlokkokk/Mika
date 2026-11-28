package com.wangzan101.mika.core.model

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * 服务端在等上游时定时发 SSE 注释保活:字节一直在来,套接字的读超时永远不触发。
 * 只有保活、没有数据的流要在两倍读超时后按网络超时结束;数据之间夹着保活的健康流不受影响。
 */
class HttpSupportStreamStallTest {
    private val servers = mutableListOf<KeepAliveSseServer>()

    @After fun tearDown() { servers.forEach(Closeable::close) }

    private fun server(script: KeepAliveSseServer.Sink.() -> Unit) = KeepAliveSseServer(script).also(servers::add)

    private fun http(readTimeoutMs: Long = READ_TIMEOUT_MS) =
        OkHttpClient.Builder().readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS).build()

    /** 一种协议:适配器、一段完整回复(前几件带文字,其余到终态;每件是它的 `data:` 行)和读完得到的文字。 */
    private class Protocol(
        val name: String,
        val build: (KeepAliveSseServer, OkHttpClient) -> StreamingChatModel,
        val head: List<List<String>>,
        val tail: List<List<String>>,
        val text: String,
    )

    private val caps = Capabilities(toolCalls = true, vision = false, contextTokens = 32_000, reasoning = false)
    private val user = listOf(Message(Role.User, listOf(Part.Text("hi")), Author.User))

    private val protocols = listOf(
        Protocol("Chat Completions", { s, http -> OpenAiCompatModel("${s.baseUrl}/v1", "k", "m", caps, http) },
            listOf(listOf("""{"choices":[{"delta":{"content":"完整"}}]}""")),
            listOf(
                listOf("""{"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":9,"completion_tokens":2}}"""),
                listOf("[DONE]"),
            ), "完整"),
        Protocol("Messages", { s, http -> AnthropicModel(s.baseUrl, "k", "m", caps, http) },
            listOf(
                listOf("""{"type":"message_start","message":{"usage":{"input_tokens":10,"output_tokens":0}}}"""),
                listOf("""{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""),
                listOf("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"完整"}}"""),
            ),
            listOf(
                listOf("""{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":3}}"""),
                listOf("""{"type":"message_stop"}"""),
            ), "完整"),
        Protocol("Responses", { s, http -> ResponsesModel(s.baseUrl, BearerToken.fixed("k"), "m", caps, http) },
            listOf(listOf("""{"type":"response.output_text.delta","delta":"完整"}""")),
            listOf(
                listOf("""{"type":"response.completed","response":{"status":"completed","usage":{"input_tokens":12,"output_tokens":4,"input_tokens_details":{"cached_tokens":8}}}}"""),
            ), "完整"),
        Protocol("generateContent", { s, http -> GeminiModel(s.baseUrl, "k", "m", caps, http) },
            listOf(listOf("""{"candidates":[{"content":{"parts":[{"text":"你"}]}}],"usageMetadata":{"promptTokenCount":11,"candidatesTokenCount":1}}""")),
            listOf(
                listOf("""{"candidates":[{"content":{"parts":[{"text":"好"}]},"finishReason":"STOP"}]}"""),
                listOf("""{"usageMetadata":{"promptTokenCount":11,"candidatesTokenCount":4}}"""),
            ), "你好"),
    )

    private fun KeepAliveSseServer.Sink.events(list: List<List<String>>): Boolean =
        list.fold(true) { ok, lines -> ok && event(*lines.toTypedArray()) }

    /** 读循环在 OkHttp 的回调线程上抛出、协程恢复时又复制一份(栈恢复),原异常挂在 cause 上,所以顺着链找。 */
    private fun isReadTimeout(error: Throwable?): Boolean =
        generateSequence(error) { it.cause }.any { it is SocketTimeoutException }

    private fun chain(error: Throwable?): String =
        generateSequence(error) { it.cause }.joinToString(" <- ") { "${it::class.java.name}: ${it.message}" }

    /** 每种协议各起一个服务,同时跑。 */
    private suspend fun each(
        script: (KeepAliveSseServer.Sink, Protocol) -> Unit,
        check: suspend (Protocol, KeepAliveSseServer, Long) -> Unit,
    ) {
        coroutineScope {
            protocols.map { p ->
                async {
                    val server = server { script(this, p) }
                    val began = System.nanoTime()
                    check(p, server, began)
                }
            }.awaitAll()
        }
    }

    /** 只有保活注释(开头就是,或输出到一半之后)的流:两倍读超时后以网络超时结束,连接由客户端关掉。 */
    @Test fun keepAliveOnlyStreamTimesOutAtTwiceTheReadTimeout() = runBlocking {
        val stalls = listOf(
            "开头只有保活" to { s: KeepAliveSseServer.Sink, _: Protocol -> while (s.comment()) s.pause(50) },
            "输出到一半后只剩保活" to { s: KeepAliveSseServer.Sink, p: Protocol -> s.events(p.head); while (s.comment()) s.pause(50) },
        )
        for ((scenario, script) in stalls) {
            each(script) { p, server, began ->
                val failure = try {
                    withTimeout(OUTER_TIMEOUT_MS) { p.build(server, http()).completeStreaming("s", user, emptyList()) {} }
                    null
                } catch (e: ModelError.Network) {
                    e
                }
                val label = "${p.name}·$scenario"
                assertNotNull("$label: 只有保活的流必须以网络超时结束", failure)
                assertTrue("$label: 要报成读超时,实际是 ${chain(failure)}", isReadTimeout(failure))
                val elapsedMs = (System.nanoTime() - began) / 1_000_000
                assertTrue("$label: 保活行要再等满一个读超时(共 ${2 * READ_TIMEOUT_MS} ms),实际 ${elapsedMs} ms", elapsedMs >= 2 * READ_TIMEOUT_MS)
                server.awaitClientClosed()
            }
        }
    }

    /** 数据之间夹着保活注释、间隔不超过读超时:照常读完。 */
    @Test fun commentsBetweenDataLinesDoNotTimeOutAHealthyStream() = runBlocking {
        each({ s, p ->
            (p.head + p.tail).forEach { lines ->
                if (!s.event(*lines.toTypedArray())) return@each
                repeat(4) { s.pause(150); s.comment() }
            }
        }) { p, server, _ ->
            val reply = withTimeout(OUTER_TIMEOUT_MS) { p.build(server, http()).completeStreaming("s", user, emptyList()) {} }
            assertEquals(p.name, listOf(Part.Text(p.text)), reply.parts)
            assertEquals(p.name, StopReason.EndTurn, reply.stopReason)
            server.awaitClientClosed()
        }
    }

    /** 读超时为 0(不设超时)的客户端:保活再久也不限时,和以前一样。 */
    @Test fun zeroReadTimeoutKeepsWaiting() = runBlocking {
        val p = protocols[0]
        val server = server {
            repeat(30) { comment(); pause(50) }
            events(p.head + p.tail)
        }
        val reply = withTimeout(OUTER_TIMEOUT_MS) {
            p.build(server, http(readTimeoutMs = 0)).completeStreaming("s", user, emptyList()) {}
        }
        assertEquals(listOf(Part.Text(p.text)), reply.parts)
        server.awaitClientClosed()
    }

    /** 对照:服务端完全静默时仍由套接字读超时结束,这条路不变。 */
    @Test fun silentStreamStillTimesOutByTheSocketReadTimeout() = runBlocking {
        val p = protocols[0]
        val server = server { }
        val failure = try {
            withTimeout(OUTER_TIMEOUT_MS) { p.build(server, http()).completeStreaming("s", user, emptyList()) {} }
            null
        } catch (e: ModelError.Network) {
            e
        }
        assertNotNull("静默的流必须以网络超时结束", failure)
        assertTrue("要报成读超时,实际是 ${chain(failure)}", isReadTimeout(failure))
    }

    private companion object {
        const val READ_TIMEOUT_MS = 1_000L
        const val OUTER_TIMEOUT_MS = 10_000L
    }
}
