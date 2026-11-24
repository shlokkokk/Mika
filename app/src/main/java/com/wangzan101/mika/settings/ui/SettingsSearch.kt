package com.wangzan101.mika.settings.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale

internal data class SettingsSearchEntry(
    val id: String,
    val title: String,
    val group: String,
    val keywords: String = "",
    val icon: ImageVector? = null,
    val onOpen: () -> Unit,
)

internal object SettingsSearch {
    fun filter(entries: List<SettingsSearchEntry>, query: String): List<SettingsSearchEntry> {
        val tokens = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return entries.filter { entry ->
            val haystack = "${entry.title} ${entry.group} ${entry.keywords}".lowercase(Locale.ROOT)
            tokens.all { haystack.contains(it) }
        }
    }

    fun highlightMatch(text: String, query: String, highlightColor: Color): AnnotatedString {
        val q = query.trim()
        if (q.isEmpty()) return AnnotatedString(text)
        val lowerText = text.lowercase(Locale.ROOT)
        val lowerQuery = q.lowercase(Locale.ROOT)
        val start = lowerText.indexOf(lowerQuery)
        if (start < 0) return AnnotatedString(text)
        val end = (start + lowerQuery.length).coerceAtMost(text.length)
        return buildAnnotatedString {
            append(text)
            addStyle(
                SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.SemiBold,
                    background = highlightColor.copy(alpha = 0.14f),
                ),
                start,
                end,
            )
        }
    }
}
