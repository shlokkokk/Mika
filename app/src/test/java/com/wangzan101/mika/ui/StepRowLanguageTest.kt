package com.wangzan101.mika.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepRowLanguageTest {

    @Test
    fun testShouldHideChineseReasonInNonChineseLocale() {
        assertTrue(StepRow.shouldHideRawReason("点击搜索按钮查找联系人 Ved", isChineseLocale = false))
        assertTrue(StepRow.shouldHideRawReason("在搜索框输入“ved”", isChineseLocale = false))
    }

    @Test
    fun testShouldKeepEnglishReasonInNonChineseLocale() {
        assertFalse(StepRow.shouldHideRawReason("Tap search button to find Ved", isChineseLocale = false))
        assertFalse(StepRow.shouldHideRawReason("Type \"ved\" in search box", isChineseLocale = false))
        assertFalse(StepRow.shouldHideRawReason("", isChineseLocale = false))
    }

    @Test
    fun testShouldKeepChineseReasonInChineseLocale() {
        assertFalse(StepRow.shouldHideRawReason("点击搜索按钮查找联系人 Ved", isChineseLocale = true))
    }

    @Test
    fun testStepRowTextFallback() {
        // When model outputs Chinese reason in an English environment, fallback to localized title
        val rowFallback = StepRow(
            step = 1,
            title = "Tap · WeChat",
            subtitle = "",
            state = StepState.Ok,
            reason = "点击“微信”应用图标",
        )
        // If system default is non-zh, it falls back to title
        val expected = if (java.util.Locale.getDefault().language == "zh") "点击“微信”应用图标" else "Tap · WeChat"
        assertEquals(expected, rowFallback.text)

        // When model outputs English reason, it is always displayed directly
        val rowEnglish = StepRow(
            step = 1,
            title = "Tap · WeChat",
            subtitle = "",
            state = StepState.Ok,
            reason = "Tap \"WeChat\" icon to open app",
        )
        assertEquals("Tap \"WeChat\" icon to open app", rowEnglish.text)
    }
}
