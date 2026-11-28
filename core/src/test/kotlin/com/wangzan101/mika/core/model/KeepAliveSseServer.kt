package com.wangzan101.mika.core.model

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 原始 TCP 服务:收下请求、发完事件流的响应头后按 [script] 逐段写正文;写完保持连接,直到客户端关闭。
 * 给「服务端一直发保活、不出数据」这类停滞场景用。一次请求一个连接。
 */
internal class KeepAliveSseServer(private val script: Sink.() -> Unit) : Closeable {
    /** 写正文的一端;客户端已经断开时各个写法返回 false,脚本据此收场。 */
    class Sink(private val output: OutputStream) {
        fun line(text: String): Boolean = try {
            output.write((text + "\n").toByteArray(Charsets.UTF_8))
            output.flush()
            true
        } catch (_: IOException) {
            false
        }

        /** SSE 注释行:网关在等上游时发的保活(OpenRouter 的 `: OPENROUTER PROCESSING`)。 */
        fun comment(): Boolean = line(": OPENROUTER PROCESSING")

        /** 一件事件:每个元素是一行 `data:` 的内容,最后加一个空行。 */
        fun event(vararg data: String): Boolean = data.fold(true) { ok, text -> ok && line("data: $text") } && line("")

        fun pause(ms: Long) = Thread.sleep(ms)
    }

    private val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "keep-alive-sse-server").apply { isDaemon = true }
    }
    private val requestRead = CountDownLatch(1)
    @Volatile private var peer: Socket? = null
    private val task = executor.submit { serve() }

    val baseUrl = "http://${listener.inetAddress.hostAddress}:${listener.localPort}"

    fun awaitClientClosed() {
        try {
            task.get(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw AssertionError("客户端没有及时关闭连接", e)
        }
    }

    override fun close() {
        listener.close()
        peer?.close()
        executor.shutdownNow()
    }

    private fun serve() {
        try {
            listener.accept().use { socket ->
                peer = socket
                socket.soTimeout = 15_000
                val input = socket.getInputStream()
                readRequest(input)
                requestRead.countDown()
                val output = socket.getOutputStream()
                output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: keep-alive\r\n\r\n".toByteArray(Charsets.UTF_8))
                output.flush()
                Sink(output).script()
                while (input.read() != -1) {
                    // 正文写完了,连接留给客户端关
                }
            }
        } catch (_: IOException) {
            // 客户端断开或超时,这一次连接到此为止
        } finally {
            requestRead.countDown()
        }
    }

    private fun readRequest(input: InputStream) {
        check(readLine(input).startsWith("POST "))
        var contentLength = 0
        while (true) {
            val line = readLine(input)
            if (line.isEmpty()) break
            val header = line.split(':', limit = 2)
            if (header.size == 2 && header[0].equals("Content-Length", ignoreCase = true)) {
                contentLength = header[1].trim().toInt()
            }
        }
        repeat(contentLength) { check(input.read() != -1) }
    }

    private fun readLine(input: InputStream): String {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next == -1) throw EOFException("请求头不完整")
            if (next == '\n'.code) return String(bytes.toByteArray(), Charsets.ISO_8859_1).removeSuffix("\r")
            bytes.write(next)
        }
    }
}
