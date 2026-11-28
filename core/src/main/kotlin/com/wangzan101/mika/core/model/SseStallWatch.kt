package com.wangzan101.mika.core.model

import java.net.SocketTimeoutException

/**
 * 流式回复的读超时按数据计,补上套接字读超时管不到的一种停滞(见 [HttpSupport.stream])。
 * 套接字的读超时只看有没有字节到:服务端(或途中的网关)在等上游时定时发 SSE 注释保活,字节一直在来,它就永远不触发。
 * 这里记最近一行 `data:` 的时刻;之后再来别的行(注释、空行、event / id / retry)时对照,超过 [limitMs] 就报和套接字
 * 读超时同一个异常,由调用方按网络超时处理(重试、换备援)。
 * [limitMs] 取两倍读超时而不是一倍:网关在模型思考期间也会保活(OpenRouter 的注释就是为了让客户端别超时),
 * 经这类网关用思考久的推理模型不能掐。读超时为 0(不设)时 [limitMs] 也是 0,不查。
 * 纯逻辑:时刻由 [now] 给(单调,毫秒),测试里换成定值。
 */
internal class SseStallWatch(
    readTimeoutMs: Long,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val limitMs = readTimeoutMs * KEEP_ALIVE_PATIENCE
    private val readTimeoutSec = readTimeoutMs / 1000
    private var lastDataAt = now()

    /** 读到一行数据之后调。 */
    fun onData() {
        lastDataAt = now()
    }

    /** 读到一行不是数据的之后调:自上一行数据起已经超过 [limitMs] 就返回要抛的异常,否则 null。 */
    fun stalled(): SocketTimeoutException? {
        if (limitMs <= 0) return null
        val withoutDataMs = now() - lastDataAt
        if (withoutDataMs < limitMs) return null
        return SocketTimeoutException(
            "no SSE data for ${withoutDataMs / 1000} s (read timeout ${readTimeoutSec} s, keep-alive lines only)",
        )
    }

    private companion object {
        /** 保活行最多把等待撑到读超时的几倍。 */
        const val KEEP_ALIVE_PATIENCE = 2L
    }
}
