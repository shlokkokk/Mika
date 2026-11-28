package com.wangzan101.mika.core.agent

import com.wangzan101.mika.core.assembly.toolDispatcher
import com.wangzan101.mika.core.model.Author
import com.wangzan101.mika.core.model.Capabilities
import com.wangzan101.mika.core.model.ChatModel
import com.wangzan101.mika.core.model.KeepAliveSseServer
import com.wangzan101.mika.core.model.Message
import com.wangzan101.mika.core.model.ModelReply
import com.wangzan101.mika.core.model.OpenAiCompatModel
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.model.Role
import com.wangzan101.mika.core.model.ToolSpec
import com.wangzan101.mika.core.run.record.RunEvent
import com.wangzan101.mika.core.testing.answerReply
import com.wangzan101.mika.core.tool.FakeScreen
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** 主模型的流只剩保活注释:按网络超时结束后换备援,这一步由备援回答。 */
class ModelCallerStallFallbackTest {
    private class Answerer(override val name: String) : ChatModel {
        override val capabilities = Capabilities(toolCalls = true, vision = false, contextTokens = 32_000, reasoning = false)

        override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply = answerReply("ok-$name")
    }

    private val caps = Capabilities(toolCalls = true, vision = false, contextTokens = 32_000, reasoning = false)
    private val history = listOf(Message(Role.User, listOf(Part.Text("你好")), Author.User))

    @Test fun stalledMainStreamFallsBackToTheBackup() = runBlocking {
        val server = KeepAliveSseServer { while (comment()) pause(50) }
        try {
            val http = OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build()
            val main = OpenAiCompatModel("${server.baseUrl}/v1", "k", "m", caps, http)
            val backup = Answerer("backup")
            val events = mutableListOf<RunEvent>()
            val caller = ModelCaller(main, toolDispatcher(FakeScreen()), null, SpeedFlags.OFF, RetryPolicy.NONE, events::add, { 0 }, fallbacks = listOf(backup))
                .also { it.reset("") }
            val reply = withTimeout(10_000) { caller.ask(history, emptyList(), step = 1, onText = {}) }
            assertEquals(listOf(Part.Text("ok-backup")), reply.parts)
            assertSame(backup, caller.lastMainUsed)
            assertTrue(events.filterIsInstance<RunEvent.State>().any { it.name == "model_switch" && it.detail.endsWith("network") })
            server.awaitClientClosed()
        } finally {
            server.close()
        }
    }
}
