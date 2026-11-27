package com.wangzan101.mika.task.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.icu.text.TimeZoneNames
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.i18n.tr
import com.wangzan101.mika.i18n.uiLocale
import com.wangzan101.mika.ui.components.EmptyNote
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaDialog
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 时区选择:按界面语言显示城市和时区名,可按城市、编号、时区名或「+8」「GMT+8」这样的偏移搜。
 * 几百个时区的本地化名字在后台线程建一次索引,输入时只在索引里筛,不再逐个查名字。
 */
@Composable
internal fun PlanTimezonePickerDialog(
    currentZoneId: String,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit,
) {
    val locale = uiLocale()
    var query by remember { mutableStateOf("") }
    val index by produceState<List<ZoneEntry>?>(null, locale) {
        value = withContext(Dispatchers.Default) { zoneIndexFor(locale) }
    }
    val zones = remember(index, query, currentZoneId) {
        index?.let { PlanTimeFormat.searchZones(it, query, currentZoneId) }
    }
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.plans_timezone)) },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val listMaxHeight = maxHeight * 0.52f
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().testTag("plan-zone-search"),
                        placeholder = { Text(tr(R.string.plans_timezone_search)) },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    when {
                        zones == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = MikaTheme.colors.accent, strokeWidth = 2.dp)
                        }
                        zones.isEmpty() -> EmptyNote(tr(R.string.plans_timezone_empty))
                        else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = listMaxHeight).testTag("plan-zone-list")) {
                            items(zones, key = { it.id }) { entry ->
                                ListRow(
                                    icon = null,
                                    title = entry.city,
                                    subtitle = "${entry.displayName} · ${entry.offsetLabel}",
                                    first = entry === zones.first(),
                                    onClick = { onPicked(entry.id) },
                                    modifier = Modifier.testTag("plan-zone-${entry.id}"),
                                ) {
                                    if (entry.id == currentZoneId) {
                                        Icon(MikaIcons.Check, null, Modifier.size(18.dp), tint = MikaTheme.colors.accent)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("plan-zone-cancel")) { Text(tr(R.string.cancel)) }
        },
    )
}

/** 选中时区在编辑器里的说法:本地化城市名 + 偏移。 */
internal fun zoneSummary(zoneId: String, locale: Locale): String {
    val city = runCatching { TimeZoneNames.getInstance(locale).getExemplarLocationName(zoneId) }.getOrNull()
    val offset = runCatching { PlanTimeFormat.zoneOffsetLabel(java.time.ZoneId.of(zoneId)) }.getOrNull()
    return listOfNotNull(city?.takeIf { it.isNotBlank() } ?: zoneId, offset).joinToString(" · ")
}

private fun zoneIndexFor(locale: Locale): List<ZoneEntry> {
    val names = TimeZoneNames.getInstance(locale)
    return PlanTimeFormat.zoneIndex(locale, cityName = { id -> runCatching { names.getExemplarLocationName(id) }.getOrNull() })
}
