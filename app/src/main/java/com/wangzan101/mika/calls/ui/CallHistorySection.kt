package com.wangzan101.mika.calls.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.R
import com.wangzan101.mika.calls.CallActionResult
import com.wangzan101.mika.calls.CallDirection
import com.wangzan101.mika.calls.CallEndText
import com.wangzan101.mika.calls.CallTaskBrief
import com.wangzan101.mika.calls.CallRecord
import com.wangzan101.mika.calls.CallTranscriptLine
import com.wangzan101.mika.calls.SummaryStatus
import com.wangzan101.mika.ui.components.*
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import com.wangzan101.mika.memory.ui.Row

private enum class HistoryFilter(@StringRes val label: Int) {
    ALL(R.string.calls_history_all), OPEN(R.string.calls_history_open), DONE(R.string.calls_history_done),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CallHistorySection(
    history: List<CallRecord>,
    historyWriteFailed: Boolean,
    onMark: suspend (String, Boolean) -> CallActionResult,
    onDelete: suspend (String) -> CallActionResult,
    onRetrySummary: suspend (String) -> CallActionResult,
    onClear: suspend () -> CallActionResult,
    focusRecordId: String? = null,
    onReuseTask: ((String, CallTaskBrief) -> Unit)? = null,
    onSaveDraft: (suspend (String, String) -> CallActionResult)? = null,
) {
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingClear by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<CallActionResult?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(focusRecordId, history) {
        if (focusRecordId != null && history.any { it.id == focusRecordId }) {
            filter = HistoryFilter.ALL
            query = ""
            showAll = true
            expanded = focusRecordId
        }
    }
    val failure = stringResource(R.string.calls_action_failed)
    val clearFailure = stringResource(R.string.calls_history_clear_failed)
    val summaryFailure = stringResource(R.string.calls_summary_failed)
    val cleared = stringResource(R.string.calls_history_cleared)
    val deleted = stringResource(R.string.calls_history_deleted)
    val summaryUpdated = stringResource(R.string.calls_history_summary_updated)
    val handled = stringResource(R.string.calls_history_done)
    val unhandled = stringResource(R.string.calls_history_open)
    val incoming = directionLabel(CallDirection.INCOMING)
    val outgoing = directionLabel(CallDirection.OUTGOING)
    val results = remember(history, filter, query, incoming, outgoing) {
        val text = query.trim()
        val digits = text.filter(Char::isDigit).takeIf {
            it.isNotEmpty() && text.all { char -> char.isDigit() || char in "+ ().-" }
        }
        history.filter { record ->
            val matchesFilter = when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.OPEN -> !record.handled
                HistoryFilter.DONE -> record.handled
            }
            val direction = if (record.direction == CallDirection.INCOMING) incoming else outgoing
            matchesFilter && (text.isEmpty() || record.number.orEmpty().contains(text, ignoreCase = true) ||
                (digits != null && record.number.orEmpty().filter(Char::isDigit).contains(digits)) ||
                direction.contains(text, ignoreCase = true) || record.result.contains(text, ignoreCase = true) ||
                record.transcript.any { it.text.contains(text, ignoreCase = true) } ||
                record.summary?.let { summary ->
                    listOfNotNull(summary.purpose, summary.conclusion, summary.followUpDraft).plus(summary.highlights)
                        .plus(summary.uncertainties).plus(summary.actions.map { it.text })
                        .any { it.contains(text, ignoreCase = true) }
                } == true ||
                record.task?.let { task ->
                    (listOf(task.goal, task.context, task.constraints) + task.questions).any {
                        it.contains(text, ignoreCase = true)
                    }
                } == true)
        }
    }

    fun change(success: String, errorMessage: String? = null, action: suspend () -> CallActionResult) {
        if (busy) return
        busy = true
        feedback = null
        scope.launch {
            try {
                val result = action()
                feedback = if (result.ok) CallActionResult(true, success)
                    else if (errorMessage != null) CallActionResult(false, errorMessage) else result
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                feedback = CallActionResult(false, errorMessage ?: failure)
            } finally { busy = false }
        }
    }

    if (historyWriteFailed) FooterNote(
        stringResource(R.string.calls_history_write_failed),
        color = MaterialTheme.colorScheme.error,
    )
    feedback?.let { result ->
        FooterNote(result.message, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = if (result.ok) MikaTheme.colors.ink3 else MaterialTheme.colorScheme.error)
    }
    if (history.isEmpty()) {
        GroupCard { ListRow(MikaIcons.Clock, stringResource(R.string.calls_history_empty), first = true) }
    } else {
        // Clearing the search keeps the status filter; only "reset filters" goes back to everything.
        SearchField(query, { query = it; showAll = false }, stringResource(R.string.calls_history_search),
            Modifier.testTag("call-history-search"),
            onClear = { query = ""; showAll = false; expanded = null }, clearTag = "call-history-clear-search")
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryFilter.entries.forEach { choice ->
                SelectChip(
                    stringResource(choice.label), filter == choice, { filter = choice; showAll = false },
                    Modifier.testTag("call-history-filter-${choice.name.lowercase()}"), small = true,
                )
            }
        }
        if (results.isEmpty()) {
            GroupCard {
                ListRow(MikaIcons.Search, stringResource(R.string.calls_history_no_match), first = true)
                ListRow(
                    MikaIcons.Refresh,
                    stringResource(R.string.calls_reset_history_filters),
                    titleColor = MikaTheme.colors.accent,
                    tone = TileTone.Accent,
                    onClick = { query = ""; filter = HistoryFilter.ALL; showAll = false; expanded = null },
                    modifier = Modifier.testTag("call-history-reset-filters"),
                )
            }
        } else GroupCard {
            val visible = if (showAll) results else results.take(3)
            visible.forEachIndexed { index, record ->
                key(record.id) {
                    CallHistoryItem(
                        record, expanded == record.id, first = index == 0, busy = busy,
                        onExpand = { expanded = if (expanded == record.id) null else record.id },
                        onMark = {
                            val next = !record.handled
                            change(if (next) handled else unhandled) { onMark(record.id, next) }
                        },
                        onDelete = { pendingDelete = record.id },
                        onSaveDraft = onSaveDraft?.let { save -> { text -> save(record.id, text) } },
                        onRetrySummary = { change(summaryUpdated, summaryFailure) { onRetrySummary(record.id) } },
                        onReuse = if (record.number != null && record.task != null && onReuseTask != null) {
                            { onReuseTask(record.number, record.task) }
                        } else null,
                    )
                }
            }
            if (results.size > 3) {
                TextButton(
                    onClick = { showAll = !showAll },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (showAll) stringResource(R.string.calls_history_less)
                        else stringResource(R.string.calls_history_more, results.size))
                }
            }
        }
    }
    // 清空是危险操作,放在列表最下面,不放在最显眼的顶上
    if (history.isNotEmpty() || historyWriteFailed) {
        GroupCard {
            ListRow(
                MikaIcons.Trash,
                stringResource(R.string.calls_history_clear),
                first = true,
                titleColor = MikaTheme.colors.bad,
                tone = TileTone.Bad,
                onClick = if (!busy) ({ pendingClear = true }) else null,
                modifier = Modifier.testTag("call-history-clear"),
            )
        }
    }

    if (pendingClear) AlertDialog(
        onDismissRequest = { pendingClear = false },
        title = { Text(stringResource(R.string.calls_history_clear)) },
        text = { Text(stringResource(R.string.calls_history_clear_confirm)) },
        confirmButton = {
            TextButton(onClick = {
                pendingClear = false
                change(cleared, clearFailure) { onClear() }
            }, enabled = !busy, modifier = Modifier.testTag("call-history-clear-confirm")) {
                Text(stringResource(R.string.calls_history_clear), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { pendingClear = false }) { Text(stringResource(R.string.calls_cancel)) }
        },
    )

    val toDelete = history.firstOrNull { it.id == pendingDelete }
    if (toDelete != null) AlertDialog(
        onDismissRequest = { pendingDelete = null },
        title = { Text(stringResource(R.string.calls_history_delete)) },
        text = {
            val locale = LocalContext.current.resources.configuration.locales[0]
            val formatter = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }
            val whenCalled = remember(toDelete.startedAtMs, formatter) { formatter.format(Date(toDelete.startedAtMs)) }
            val number = toDelete.number?.let { "\u2066$it\u2069" } ?: stringResource(R.string.calls_unknown_number)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$number · $whenCalled", style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.calls_history_delete_confirm))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                pendingDelete = null
                change(deleted) { onDelete(toDelete.id) }
            }, enabled = !busy, modifier = Modifier.testTag("call-history-delete-confirm")) {
                Text(stringResource(R.string.calls_history_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.calls_cancel)) } },
    )
}

@Composable
private fun CallHistoryItem(
    record: CallRecord, expanded: Boolean, first: Boolean, busy: Boolean,
    onExpand: () -> Unit, onMark: () -> Unit, onDelete: () -> Unit, onRetrySummary: () -> Unit,
    onReuse: (() -> Unit)?,
    onSaveDraft: (suspend (String) -> CallActionResult)?,
) {
    var showTask by rememberSaveable(record.id) { mutableStateOf(false) }
    var showTranscript by rememberSaveable(record.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val formatter = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }
    val start = remember(record.startedAtMs, formatter) { formatter.format(Date(record.startedAtMs)) }
    val end = remember(record.endedAtMs, formatter) { formatter.format(Date(record.endedAtMs)) }
    val direction = directionLabel(record.direction)
    val status = stringResource(if (record.handled) R.string.calls_history_done else R.string.calls_history_open)
    val name = record.number?.let { "\u2066$it\u2069" } ?: stringResource(R.string.calls_unknown_number)
    val expansion = stringResource(if (expanded) R.string.calls_collapse else R.string.calls_transcript)
    val shortTime = remember(record.startedAtMs, locale) { callShortTime(record.startedAtMs, locale) }
    val c = MikaTheme.colors
    val incoming = record.direction == CallDirection.INCOMING
    val toneColor = if (incoming) c.ok else c.accent
    Column(Modifier.fillMaxWidth().testTag("call-history-item-${record.id}").semantics { stateDescription = "$status · $expansion" }) {
        if (!first) HorizontalDivider()
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = onExpand)
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(
                icon = if (incoming) MikaIcons.Handset else MikaIcons.Phone,
                tone = if (incoming) TileTone.Ok else TileTone.Accent,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    record.task?.goal ?: name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = toneColor.copy(alpha = 0.13f),
                        border = BorderStroke(1.dp, toneColor.copy(alpha = 0.28f)),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(Modifier.size(6.dp).background(toneColor, CircleShape))
                            Text(
                                direction,
                                style = MikaType.Caption.copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                                color = toneColor,
                                maxLines = 1,
                            )
                        }
                    }
                    if (record.task != null) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!record.handled) Box(Modifier.size(7.dp).background(c.accent, CircleShape))
                Text(shortTime, style = MikaType.Value, color = c.ink3, maxLines = 1)
                CallExpandPill(expanded)
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val resultText = localizedCallResult(record.result)
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider()
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = c.fill,
                    border = BorderStroke(1.dp, c.ink3.copy(alpha = 0.14f)),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SelectionContainer(Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        if (incoming) MikaIcons.Handset else MikaIcons.Phone,
                                        null,
                                        Modifier.size(14.dp),
                                        tint = toneColor,
                                    )
                                    Text(name, style = MaterialTheme.typography.titleSmall, color = c.ink)
                                }
                            }
                            Surface(
                                modifier = Modifier.widthIn(max = 170.dp),
                                shape = CircleShape,
                                color = c.ink3.copy(alpha = 0.12f),
                            ) {
                                Text(
                                    resultText,
                                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MikaType.Caption.copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                                    color = c.ink2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.calls_history_period, start, end),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.ink3,
                        )
                    }
                }
                record.summary?.let { summary ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.fill).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        summary.purpose?.let { Text(stringResource(R.string.calls_label_value, stringResource(R.string.calls_summary_purpose), it), style = MaterialTheme.typography.bodyMedium, color = c.ink) }
                        if (summary.highlights.isNotEmpty()) {
                            Text(stringResource(R.string.calls_summary_highlights), style = MaterialTheme.typography.labelLarge, color = c.ink2)
                            summary.highlights.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = c.ink) }
                        }
                        summary.conclusion?.let { Text(stringResource(R.string.calls_label_value, stringResource(R.string.calls_summary_conclusion), it), style = MaterialTheme.typography.bodyMedium, color = c.ink) }
                        if (summary.uncertainties.isNotEmpty()) {
                            Text(stringResource(R.string.calls_summary_uncertainties), style = MaterialTheme.typography.labelLarge, color = c.ink2)
                            summary.uncertainties.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = c.ink) }
                        }
                        if (summary.actions.isNotEmpty()) {
                            Text(stringResource(R.string.calls_summary_actions), style = MaterialTheme.typography.labelLarge, color = c.ink2)
                            summary.actions.forEach { item ->
                                val state = when (item.status) {
                                    com.wangzan101.mika.calls.CallFollowUp.Status.PENDING -> R.string.calls_summary_pending
                                    com.wangzan101.mika.calls.CallFollowUp.Status.DONE -> R.string.calls_summary_done
                                    com.wangzan101.mika.calls.CallFollowUp.Status.UNKNOWN -> R.string.calls_summary_unknown
                                }
                                Text(stringResource(R.string.calls_label_value, stringResource(state), item.text), style = MaterialTheme.typography.bodyMedium, color = c.ink)
                            }
                        }
                    }
                }
                if (record.summaryStatus == SummaryStatus.READY && record.summary != null) {
                    CallFollowUpDraft(record.id, record.summary.followUpDraft, onSaveDraft)
                }
                if (record.summaryStatus == SummaryStatus.PENDING) {
                    Text(stringResource(R.string.calls_summary_generating), style = MaterialTheme.typography.bodySmall, color = c.ink3)
                } else if (record.summaryStatus == SummaryStatus.FAILED) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.calls_summary_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        MikaButton(
                            text = stringResource(R.string.calls_summary_retry),
                            onClick = onRetrySummary,
                            enabled = !busy,
                            kind = ButtonKind.Tonal,
                            size = ButtonSize.Small,
                            modifier = Modifier.testTag("call-summary-retry"),
                        )
                    }
                }
                record.task?.let { task ->
                    Text(stringResource(R.string.calls_record_result_unconfirmed),
                        style = MaterialTheme.typography.bodySmall, color = c.ink3)
                    CallHistoryAccordionCard(
                        icon = MikaIcons.ListChecks,
                        title = stringResource(R.string.calls_record_brief),
                        expanded = showTask,
                        testTag = "call-record-task-toggle",
                        onToggle = { showTask = !showTask },
                    ) {
                        SelectionContainer {
                            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.calls_record_goal), style = MaterialTheme.typography.labelMedium, color = c.ink2)
                                Text(task.goal, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                                if (task.questions.isNotEmpty()) {
                                    Text(stringResource(R.string.calls_record_questions), style = MaterialTheme.typography.labelMedium, color = c.ink2)
                                    task.questions.forEachIndexed { index, question ->
                                        Text("${index + 1}. $question", style = MaterialTheme.typography.bodyMedium, color = c.ink)
                                    }
                                }
                                if (task.context.isNotBlank()) {
                                    Text(stringResource(R.string.calls_record_context), style = MaterialTheme.typography.labelMedium, color = c.ink2)
                                    Text(task.context, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                                }
                                if (task.constraints.isNotBlank()) {
                                    Text(stringResource(R.string.calls_record_constraints), style = MaterialTheme.typography.labelMedium, color = c.ink2)
                                    Text(task.constraints, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                                }
                            }
                        }
                    }
                    if (onReuse != null) {
                        MikaButton(
                            text = stringResource(R.string.calls_record_reuse),
                            onClick = onReuse,
                            kind = ButtonKind.Secondary,
                            size = ButtonSize.Small,
                            modifier = Modifier.fillMaxWidth().testTag("call-record-reuse"),
                        )
                    }
                }
                CallHistoryAccordionCard(
                    icon = MikaIcons.Message,
                    title = stringResource(R.string.calls_original_transcript),
                    expanded = showTranscript,
                    testTag = "call-record-transcript-toggle",
                    onToggle = { showTranscript = !showTranscript },
                ) {
                    if (record.transcript.isEmpty()) {
                        Text(stringResource(R.string.calls_no_transcript),
                            style = MaterialTheme.typography.bodySmall, color = c.ink3,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
                    } else SelectionContainer {
                        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            record.transcript.forEach { CallTranscriptText(it) }
                        }
                    }
                }
                record.audioReport?.let { report ->
                    val label = stringResource(R.string.diagnostics_copy)
                    val copied = stringResource(R.string.diagnostics_copied)
                    MikaButton(
                        text = label,
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(ClipData.newPlainText(label, report.supportSummary()))
                                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                            }
                        },
                        kind = ButtonKind.Text,
                        size = ButtonSize.Small,
                        modifier = Modifier.fillMaxWidth().testTag("call-record-diagnostics-copy"),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MikaButton(
                        text = stringResource(if (record.handled) R.string.calls_history_reopen else R.string.calls_history_mark_done),
                        onClick = onMark,
                        enabled = !busy,
                        kind = ButtonKind.Tonal,
                        size = ButtonSize.Small,
                        modifier = Modifier.weight(1f).testTag("call-history-mark"),
                    )
                    TextButton(
                        onClick = onDelete,
                        enabled = !busy,
                        modifier = Modifier.weight(1f).testTag("call-history-delete"),
                    ) {
                        Text(stringResource(R.string.calls_history_delete),
                            color = if (busy) c.ink3 else c.bad)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CallTranscriptText(line: CallTranscriptLine) {
    Column {
        Text(stringResource(callTranscriptSpeakerLabel(line.role)),
            style = MaterialTheme.typography.labelMedium, color = MikaTheme.colors.ink3)
        Text(line.text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One role mapping for the full transcript and the compact latest-line preview. */
@StringRes
internal fun callTranscriptSpeakerLabel(role: String): Int = when (role) {
    "assistant" -> R.string.app_name
    "system" -> R.string.calls_dtmf_status_label
    CallTranscriptLine.OWNER_UNRELAYED -> R.string.calls_transcript_owner_unrelayed
    else -> R.string.calls_caller
}

@Composable
private fun CallExpandPill(expanded: Boolean) {
    val c = MikaTheme.colors
    val rotation by animateFloatAsState(if (expanded) -90f else 90f, label = "call-expand-rotation")
    Surface(
        shape = CircleShape,
        color = if (expanded) c.accent.copy(alpha = 0.14f) else c.fill,
        border = BorderStroke(1.dp, if (expanded) c.accent.copy(alpha = 0.35f) else c.ink3.copy(alpha = 0.18f)),
    ) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            Icon(
                MikaIcons.Chevron,
                null,
                Modifier.size(13.dp).rotate(rotation),
                tint = if (expanded) c.accent else c.ink2,
            )
        }
    }
}

@Composable
private fun CallHistoryAccordionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    expanded: Boolean,
    testTag: String,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = MikaTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = c.fill,
        border = BorderStroke(1.dp, if (expanded) c.accent.copy(alpha = 0.30f) else c.ink3.copy(alpha = 0.14f)),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag(testTag),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, null, Modifier.size(16.dp), tint = if (expanded) c.accent else c.ink2)
                    Text(title, style = MaterialTheme.typography.labelLarge, color = c.ink)
                }
                CallExpandPill(expanded)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    HorizontalDivider(color = c.ink3.copy(alpha = 0.12f))
                    Spacer(Modifier.height(8.dp))
                    content()
                }
            }
        }
    }
}

/** 记录里存的是固定的中文说法([CallEndText]);认得的换成界面语言,其余照原文显示。 */
@Composable
private fun localizedCallResult(result: String): String = when (result) {
    CallEndText.FAREWELL_ENDED -> stringResource(R.string.calls_result_farewell_ended)
    CallEndText.MAX_DURATION_ENDED -> stringResource(R.string.calls_result_max_duration_ended)
    CallEndText.USER_HANGUP -> stringResource(R.string.calls_result_user_hangup)
    CallEndText.ANDROID_CONFIRMED, CallEndText.ENDED -> stringResource(R.string.calls_result_android_ended)
    CallEndText.BUSY -> stringResource(R.string.calls_result_busy)
    CallEndText.MISSED -> stringResource(R.string.calls_result_missed)
    CallEndText.FAILED -> stringResource(R.string.calls_result_failed)
    else -> result
}

/** 记录行尾的时间:当天只写时刻,其他日子只写日期。 */
internal fun callShortTime(at: Long, locale: java.util.Locale): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = at }
    val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    val format = if (sameDay) DateFormat.getTimeInstance(DateFormat.SHORT, locale) else DateFormat.getDateInstance(DateFormat.SHORT, locale)
    return format.format(Date(at))
}
