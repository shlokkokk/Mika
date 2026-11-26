package com.wangzan101.mika.settings.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private val sample = listOf(
        SettingsSearchEntry("models", "Models", "Model", "api key openai gemini", null) {},
        SettingsSearchEntry("screen_off", "Run local tasks while locked", "Task running", "pin password wake unlock screen off", null) {},
        SettingsSearchEntry("plans", "Scheduled tasks", "Capabilities", "schedule alarm timer reminder", null) {},
    )

    @Test
    fun matchesByTitleGroupAndMultiTokenKeywordsCaseInsensitively() {
        assertEquals(listOf("models"), SettingsSearch.filter(sample, "GEMINI").map { it.id })
        assertEquals(listOf("screen_off"), SettingsSearch.filter(sample, "screen off").map { it.id })
        assertEquals(listOf("screen_off"), SettingsSearch.filter(sample, "lock pin").map { it.id })
        assertEquals(listOf("plans"), SettingsSearch.filter(sample, "Capabilities").map { it.id })
        assertTrue(SettingsSearch.filter(sample, "   ").isEmpty())
        assertTrue(SettingsSearch.filter(sample, "nonexistent").isEmpty())
    }

    @Test
    fun highlightsMatchedSubstringCaseInsensitively() {
        val highlighted = SettingsSearch.highlightMatch("Scheduled tasks", "sched", Color.Blue)
        assertEquals("Scheduled tasks", highlighted.text)
        assertEquals(1, highlighted.spanStyles.size)
        assertEquals(0, highlighted.spanStyles.first().start)
        assertEquals(5, highlighted.spanStyles.first().end)
    }
}
