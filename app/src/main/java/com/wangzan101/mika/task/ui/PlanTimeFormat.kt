package com.wangzan101.mika.task.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class Time12Hour(val hour12: Int, val minute: Int, val isPm: Boolean)

data class ZoneEntry(
    val id: String,
    val displayName: String,
    val offsetLabel: String,
)

internal object PlanTimeFormat {
    fun to12Hour(time: LocalTime): Time12Hour {
        val isPm = time.hour >= 12
        val raw = time.hour % 12
        val hour12 = if (raw == 0) 12 else raw
        return Time12Hour(hour12 = hour12, minute = time.minute, isPm = isPm)
    }

    fun from12Hour(hour12: Int, minute: Int, isPm: Boolean): LocalTime {
        require(hour12 in 1..12) { "hour12 must be 1..12, got $hour12" }
        require(minute in 0..59) { "minute must be 0..59, got $minute" }
        val hour24 = when {
            hour12 == 12 && !isPm -> 0
            hour12 == 12 && isPm -> 12
            isPm -> hour12 + 12
            else -> hour12
        }
        return LocalTime.of(hour24, minute)
    }

    fun formatDisplayTime(time: LocalTime, use24Hour: Boolean, locale: Locale = Locale.getDefault()): String {
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, locale).format(time)
    }

    fun formatFriendlyDate(
        date: LocalDate,
        today: LocalDate,
        todayLabel: String,
        tomorrowLabel: String,
        locale: Locale = Locale.getDefault(),
    ): String = when (date) {
        today -> todayLabel
        today.plusDays(1) -> tomorrowLabel
        else -> {
            val pattern = if (date.year == today.year) "EEE, MMM d" else "EEE, MMM d, yyyy"
            DateTimeFormatter.ofPattern(pattern, locale).format(date)
        }
    }

    fun formatRelativeDuration(deltaMs: Long): String {
        if (deltaMs <= 0L) return "0m"
        val totalMinutes = (deltaMs + 59_999L) / 60_000L
        if (totalMinutes < 60L) return "${totalMinutes}m"
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        if (hours < 24L) return if (mins == 0L) "${hours}h" else "${hours}h ${mins}m"
        val days = hours / 24L
        val remHours = hours % 24L
        return if (remHours == 0L) "${days}d" else "${days}d ${remHours}h"
    }

    fun zoneOffsetLabel(zoneId: ZoneId, now: Instant = Instant.now()): String {
        val offset = zoneId.rules.getOffset(now)
        return if (offset.totalSeconds == 0) "UTC" else "GMT$offset"
    }

    fun zoneEntry(id: String, locale: Locale = Locale.getDefault(), now: Instant = Instant.now()): ZoneEntry? {
        val zoneId = runCatching { ZoneId.of(id) }.getOrNull() ?: return null
        val display = zoneId.getDisplayName(TextStyle.FULL, locale)
        return ZoneEntry(id = zoneId.id, displayName = display, offsetLabel = zoneOffsetLabel(zoneId, now))
    }

    fun searchZones(
        query: String,
        preferredZoneId: String,
        locale: Locale = Locale.getDefault(),
        now: Instant = Instant.now(),
        availableIds: Set<String> = ZoneId.getAvailableZoneIds(),
    ): List<ZoneEntry> {
        val q = query.trim().lowercase(Locale.ROOT)
        val entries = availableIds
            .asSequence()
            .filter { !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .sorted()
            .mapNotNull { zoneEntry(it, locale, now) }
            .filter { entry ->
                q.isEmpty() ||
                    entry.id.lowercase(Locale.ROOT).contains(q) ||
                    entry.displayName.lowercase(Locale.ROOT).contains(q) ||
                    entry.offsetLabel.lowercase(Locale.ROOT).contains(q)
            }
            .toList()
        val preferred = entries.firstOrNull { it.id == preferredZoneId }
        return if (preferred == null) entries else listOf(preferred) + entries.filter { it.id != preferredZoneId }
    }
}
