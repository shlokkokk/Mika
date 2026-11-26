package com.wangzan101.mika.task.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class PlanTimeFormatTest {
    @Test
    fun roundTripsAll24HoursThrough12HourAmPm() {
        for (h in 0..23) {
            for (m in listOf(0, 15, 30, 59)) {
                val original = LocalTime.of(h, m)
                val converted = PlanTimeFormat.to12Hour(original)
                assertTrue(converted.hour12 in 1..12)
                assertEquals(h >= 12, converted.isPm)
                assertEquals(original, PlanTimeFormat.from12Hour(converted.hour12, converted.minute, converted.isPm))
            }
        }
    }

    @Test
    fun handlesMidnightAndNoonEdgeCases() {
        assertEquals(Time12Hour(12, 0, isPm = false), PlanTimeFormat.to12Hour(LocalTime.MIDNIGHT))
        assertEquals(LocalTime.MIDNIGHT, PlanTimeFormat.from12Hour(12, 0, isPm = false))
        assertEquals(Time12Hour(12, 0, isPm = true), PlanTimeFormat.to12Hour(LocalTime.NOON))
        assertEquals(LocalTime.NOON, PlanTimeFormat.from12Hour(12, 0, isPm = true))
        assertEquals(LocalTime.of(0, 30), PlanTimeFormat.from12Hour(12, 30, isPm = false))
        assertEquals(LocalTime.of(12, 30), PlanTimeFormat.from12Hour(12, 30, isPm = true))
    }

    @Test
    fun formats12And24HourDisplay() {
        val morning = LocalTime.of(9, 5)
        val evening = LocalTime.of(21, 30)
        assertEquals("09:05", PlanTimeFormat.formatDisplayTime(morning, use24Hour = true, Locale.US))
        assertEquals("9:05 AM", PlanTimeFormat.formatDisplayTime(morning, use24Hour = false, Locale.US))
        assertEquals("21:30", PlanTimeFormat.formatDisplayTime(evening, use24Hour = true, Locale.US))
        assertEquals("9:30 PM", PlanTimeFormat.formatDisplayTime(evening, use24Hour = false, Locale.US))
    }

    @Test
    fun formatsFriendlyDateAndRelativeDuration() {
        val today = LocalDate.of(2026, 11, 25)
        assertEquals("Today", PlanTimeFormat.formatFriendlyDate(today, today, "Today", "Tomorrow", Locale.US))
        assertEquals("Tomorrow", PlanTimeFormat.formatFriendlyDate(today.plusDays(1), today, "Today", "Tomorrow", Locale.US))
        assertEquals("15m", PlanTimeFormat.formatRelativeDuration(15 * 60_000L))
        assertEquals("2h 30m", PlanTimeFormat.formatRelativeDuration(150 * 60_000L))
        assertEquals("1d 2h", PlanTimeFormat.formatRelativeDuration(26 * 3_600_000L))
    }

    @Test
    fun searchesGlobalTimezonesAndPinsPreferredFirst() {
        val now = Instant.parse("2026-01-15T12:00:00Z")
        val results = PlanTimeFormat.searchZones("tokyo", "UTC", Locale.US, now)
        assertTrue(results.any { it.id == "Asia/Tokyo" })
        val all = PlanTimeFormat.searchZones("", "Europe/London", Locale.US, now)
        assertEquals("Europe/London", all.first().id)
        assertFalse(all.any { it.id.startsWith("Etc/") })
        assertEquals("UTC", PlanTimeFormat.zoneOffsetLabel(ZoneId.of("UTC"), now))
    }
}
