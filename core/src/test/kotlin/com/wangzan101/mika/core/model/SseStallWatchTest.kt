package com.wangzan101.mika.core.model

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 保活行最多再等一个读超时:边界用定值时钟钉死,不靠真实时间。 */
class SseStallWatchTest {
    private var at = 0L

    private fun watch(readTimeoutMs: Long) = SseStallWatch(readTimeoutMs) { at }

    @Test fun waitsForOneMoreReadTimeoutAfterTheLastDataLine() {
        val watch = watch(1_000)
        at = 1_999L
        assertNull(watch.stalled())
        at = 2_000L
        assertTrue(watch.stalled() is java.net.SocketTimeoutException)
    }

    @Test fun aDataLineRestartsTheWait() {
        val watch = watch(1_000)
        at = 1_500L
        watch.onData()
        at = 3_499L
        assertNull(watch.stalled())
        at = 3_500L
        assertTrue(watch.stalled() is java.net.SocketTimeoutException)
    }

    @Test fun countsFromTheStartOfTheBody() {
        val watch = watch(1_000)
        at = 2_000L
        assertTrue(watch.stalled() is java.net.SocketTimeoutException)
    }

    @Test fun theLimitScalesWithTheReadTimeout() {
        val watch = watch(5_000)
        at = 9_999L
        assertNull(watch.stalled())
        at = 10_000L
        assertTrue(watch.stalled() is java.net.SocketTimeoutException)
    }

    @Test fun noReadTimeoutMeansNoLimit() {
        val watch = watch(0)
        at = Long.MAX_VALUE / 4
        assertNull(watch.stalled())
    }
}
