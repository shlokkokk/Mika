package com.wangzan101.mika.task.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wangzan101.mika.R
import com.wangzan101.mika.core.device.CredentialKind
import com.wangzan101.mika.core.trigger.ConditionNotify
import com.wangzan101.mika.core.trigger.PlanDraft
import com.wangzan101.mika.core.trigger.PlanExecutionWindow
import com.wangzan101.mika.core.trigger.PlanRepeat
import com.wangzan101.mika.core.trigger.formatPlanClock
import com.wangzan101.mika.core.trigger.PlanValidation
import com.wangzan101.mika.core.trigger.PlanValidationException
import com.wangzan101.mika.core.trigger.TriggerDefinition
import com.wangzan101.mika.core.trigger.editedTrigger
import com.wangzan101.mika.core.trigger.formatPlanEditorTime
import com.wangzan101.mika.core.trigger.newPlanDraft
import com.wangzan101.mika.core.trigger.normalizedClockText
import com.wangzan101.mika.core.trigger.parsePlanEditorTime
import com.wangzan101.mika.core.trigger.planClockEntries
import com.wangzan101.mika.core.trigger.toPlanDraft
import com.wangzan101.mika.core.trigger.upcoming
import com.wangzan101.mika.device.ui.DeviceAccessSection
import com.wangzan101.mika.i18n.tr
import com.wangzan101.mika.i18n.uiLocale
import com.wangzan101.mika.settings.DeviceAccessSettings
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.EmptyNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.KeyboardDismissArea
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.MikaDialog
import com.wangzan101.mika.ui.components.MikaSwitch
import com.wangzan101.mika.ui.components.PageBackground
import com.wangzan101.mika.ui.components.RowChevron
import com.wangzan101.mika.ui.components.RowValue
import com.wangzan101.mika.ui.components.SegmentedControl
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.components.keepsKeyboard
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaSpace
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.UUID
import kotlin.math.roundToInt

/** Full-screen schedule editor shared by new, copied, and edited plans. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanEditorDialog(
    original: TriggerDefinition?,
    newThreadId: String,
    onDismiss: () -> Unit,
    onSave: (TriggerDefinition) -> Unit,
    initialDraft: PlanDraft? = null,
    deviceAccess: DeviceAccessSettings? = null,
) {
    val context = LocalContext.current
    val locale = uiLocale()
    val system24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    var use24Hour by remember(system24Hour) { mutableStateOf(system24Hour) }

    val newId = remember(original?.id) { original?.id ?: UUID.randomUUID().toString() }
    var draft by remember(original?.id) {
        mutableStateOf(original?.toPlanDraft() ?: initialDraft ?: newPlanDraft(System.currentTimeMillis()))
    }
    var attempted by remember(original?.id) { mutableStateOf(false) }
    var pickingDate by remember(original?.id) { mutableStateOf(false) }
    var pickingTime by remember(original?.id) { mutableStateOf<TimeTarget?>(null) }
    var pickingZone by remember(original?.id) { mutableStateOf(false) }
    var conditionExpanded by remember(original?.id) { mutableStateOf(draft.condition.isNotBlank()) }
    var configuringScreenOff by remember { mutableStateOf(false) }

    val nowMs = System.currentTimeMillis()
    val pickerZone = runCatching { ZoneId.of(draft.zone.trim()) }.getOrElse { ZoneId.systemDefault() }
    val todayInZone = Instant.ofEpochMilli(nowMs).atZone(pickerZone).toLocalDate()
    val parsedFirst = parsePlanEditorTime(draft.firstAt, pickerZone.id)
        ?: Instant.ofEpochMilli(nowMs).atZone(pickerZone).plusHours(1).withSecond(0).withNano(0).toLocalDateTime()
    val clockEntries = planClockEntries(draft.clock).orEmpty()
    val candidate = runCatching { editedTrigger(draft, original, newId, newThreadId, nowMs) }
    val preview = candidate.getOrNull()

    if (pickingDate) {
        PlanDatePickerDialog(
            initial = parsedFirst.toLocalDate(),
            onDismiss = { pickingDate = false },
            onPicked = { date ->
                pickingDate = false
                draft = draft.copy(firstAt = formatPlanEditorTime(LocalDateTime.of(date, parsedFirst.toLocalTime())))
            },
        )
    }
    pickingTime?.let { target ->
        PlanTimePickerDialog(
            initial = when (target) {
                is TimeTarget.FirstRun -> parsedFirst.toLocalTime()
                is TimeTarget.Clock -> target.index?.let { clockEntries.getOrNull(it) }
                    ?: clockEntries.lastOrNull()?.plusHours(1)
                    ?: LocalTime.of(9, 0)
                TimeTarget.WindowStart -> runCatching { LocalTime.parse(draft.windowStart.trim()) }.getOrNull() ?: LocalTime.of(22, 0)
                TimeTarget.WindowEnd -> runCatching { LocalTime.parse(draft.windowEnd.trim()) }.getOrNull() ?: LocalTime.of(2, 0)
            },
            title = tr(R.string.plans_pick_time),
            use24Hour = use24Hour,
            onToggle24Hour = { use24Hour = it },
            onDismiss = { pickingTime = null },
            onPicked = { time ->
                when (target) {
                    is TimeTarget.FirstRun ->
                        draft = draft.copy(firstAt = formatPlanEditorTime(LocalDateTime.of(target.date, time)))
                    is TimeTarget.Clock -> {
                        val updated = clockEntries.toMutableList()
                        val index = target.index
                        if (index != null && index in updated.indices) updated[index] = time else updated.add(time)
                        draft = draft.copy(clock = normalizedClockText(updated))
                    }
                    TimeTarget.WindowStart -> draft = draft.copy(windowStart = formatPlanClock(time))
                    TimeTarget.WindowEnd -> draft = draft.copy(windowEnd = formatPlanClock(time))
                }
                pickingTime = null
            },
        )
    }
    if (pickingZone) {
        PlanTimezonePickerDialog(
            currentZoneId = pickerZone.id,
            onDismiss = { pickingZone = false },
            onPicked = { zoneId ->
                draft = draft.copy(zone = zoneId)
                pickingZone = false
            },
        )
    }
    if (configuringScreenOff && deviceAccess != null) {
        ScreenOffUnlockDialog(deviceAccess, onDismiss = { configuringScreenOff = false })
    }

    val titleRes = when {
        original != null -> R.string.plans_edit_title
        initialDraft != null -> R.string.plans_copy
        else -> R.string.plans_create_title
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        PageBackground {
            Column(Modifier.fillMaxSize().imePadding()) {
                TopBar(
                    title = tr(titleRes),
                    left = MikaIcons.Close,
                    onLeft = onDismiss,
                    leftLabel = tr(R.string.cancel),
                    right = {
                        MikaButton(
                            tr(R.string.save),
                            onClick = {
                                val saved = runCatching {
                                    editedTrigger(draft, original, newId, newThreadId, System.currentTimeMillis())
                                }
                                if (saved.isFailure) attempted = true
                                else onSave(saved.getOrThrow())
                            },
                            modifier = Modifier.testTag("plan-editor-save"),
                            kind = ButtonKind.Primary,
                            size = ButtonSize.Small,
                        )
                    },
                )
                KeyboardDismissArea {
                    Column(
                        Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                            .padding(horizontal = MikaSpace.Page),
                    ) {
                        Spacer(Modifier.height(MikaSpace.S8))
                        SegmentedControl(
                            listOf(tr(R.string.plans_task_mode), tr(R.string.plans_reminder_mode)),
                            if (draft.reminder) 1 else 0,
                            onSelect = { selected ->
                                val isReminder = selected == 1
                                draft = draft.copy(
                                    reminder = isReminder,
                                    condition = if (isReminder) "" else draft.condition,
                                    cooldownMinutes = if (isReminder) "" else draft.cooldownMinutes,
                                )
                                if (isReminder) conditionExpanded = false
                            },
                            tags = listOf("plan-editor-task", "plan-editor-reminder"),
                        )
                        Spacer(Modifier.height(MikaSpace.S12))
                        GroupCard {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                OutlinedTextField(
                                    value = draft.content,
                                    onValueChange = { draft = draft.copy(content = it) },
                                    modifier = Modifier.fillMaxWidth().testTag("plan-editor-content").keepsKeyboard(),
                                    label = { Text(tr(if (draft.reminder) R.string.plans_content_reminder else R.string.plans_content_task)) },
                                    minLines = 2,
                                    maxLines = 5,
                                )
                            }
                        }

                        GroupLabel(tr(R.string.plans_time))
                        GroupCard {
                            Column(Modifier.padding(14.dp)) {
                                SegmentedControl(
                                    listOf(
                                        tr(R.string.plans_once),
                                        tr(R.string.plans_repeat_interval),
                                        tr(R.string.plans_every_day),
                                        tr(R.string.plans_repeat_weekly),
                                    ),
                                    draft.repeat.ordinal,
                                    onSelect = { selected ->
                                        val repeat = PlanRepeat.entries[selected]
                                        val todayDay = Instant.now().atZone(pickerZone).dayOfWeek.value
                                        draft = draft.copy(
                                            repeat = repeat,
                                            weekdays = if (repeat == PlanRepeat.Weekly && draft.weekdays.isEmpty()) setOf(todayDay)
                                            else draft.weekdays,
                                        )
                                    },
                                    tags = listOf("plan-editor-once", "plan-editor-interval", "plan-editor-daily", "plan-editor-weekly"),
                                )
                                Spacer(Modifier.height(12.dp))

                                if (draft.repeat in setOf(PlanRepeat.Once, PlanRepeat.Interval)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        PickerTapTile(
                                            icon = MikaIcons.Calendar,
                                            label = tr(R.string.plans_date),
                                            value = PlanTimeFormat.formatFriendlyDate(
                                                parsedFirst.toLocalDate(),
                                                todayInZone,
                                                tr(R.string.plans_today),
                                                tr(R.string.plans_tomorrow),
                                                locale,
                                            ),
                                            tag = "plan-editor-first-date-pick",
                                            modifier = Modifier.weight(1f),
                                            onClick = { pickingDate = true },
                                        )
                                        PickerTapTile(
                                            icon = MikaIcons.Clock,
                                            label = tr(R.string.plans_time),
                                            value = PlanTimeFormat.formatDisplayTime(parsedFirst.toLocalTime(), use24Hour, locale),
                                            tag = "plan-editor-first-time-pick",
                                            modifier = Modifier.weight(1f),
                                            onClick = { pickingTime = TimeTarget.FirstRun(parsedFirst.toLocalDate()) },
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    QuickTimeChips(
                                        nowMs = nowMs,
                                        zone = pickerZone,
                                        onPick = { picked ->
                                            draft = draft.copy(firstAt = formatPlanEditorTime(picked))
                                        },
                                    )
                                    val firstAtMs = parsedFirst.atZone(pickerZone).toInstant().toEpochMilli()
                                    if (draft.repeat == PlanRepeat.Once && firstAtMs <= nowMs) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .clip(MikaShapes.Tile)
                                                .background(MikaTheme.colors.warn.copy(alpha = 0.12f))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                tr(R.string.plans_time_past_hint),
                                                style = MikaType.Caption,
                                                color = MikaTheme.colors.warn,
                                                modifier = Modifier.weight(1f),
                                            )
                                            TextButton(
                                                onClick = {
                                                    val tomorrow = LocalDateTime.of(todayInZone.plusDays(1), parsedFirst.toLocalTime())
                                                    draft = draft.copy(firstAt = formatPlanEditorTime(tomorrow))
                                                },
                                            ) { Text(tr(R.string.plans_use_tomorrow)) }
                                        }
                                    }
                                }

                                if (draft.repeat == PlanRepeat.Interval) {
                                    Spacer(Modifier.height(12.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        listOf(15 to "15m", 30 to "30m", 60 to "1h", 120 to "2h", 360 to "6h").forEach { (mins, label) ->
                                            MikaButton(
                                                label,
                                                onClick = { draft = draft.copy(intervalMinutes = mins.toString()) },
                                                kind = if (draft.intervalMinutes.trim() == mins.toString()) ButtonKind.Primary else ButtonKind.Tonal,
                                                size = ButtonSize.Small,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = draft.intervalMinutes,
                                        onValueChange = { draft = draft.copy(intervalMinutes = it) },
                                        modifier = Modifier.fillMaxWidth().testTag("plan-editor-interval-minutes").keepsKeyboard(),
                                        label = { Text(tr(R.string.plans_interval_minutes)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    )
                                }

                                if (draft.repeat in setOf(PlanRepeat.Daily, PlanRepeat.Weekly)) {
                                    ClockChips(
                                        entries = clockEntries,
                                        use24Hour = use24Hour,
                                        onEdit = { pickingTime = TimeTarget.Clock(it) },
                                        onRemove = { index ->
                                            val updated = clockEntries.toMutableList().apply { removeAt(index) }
                                            draft = draft.copy(clock = normalizedClockText(updated))
                                        },
                                        onAdd = { pickingTime = TimeTarget.Clock(null) },
                                    )
                                }

                                if (draft.repeat == PlanRepeat.Weekly) {
                                    Spacer(Modifier.height(12.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        (1..7).forEach { day ->
                                            MikaButton(
                                                DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale),
                                                onClick = {
                                                    draft = draft.copy(weekdays = draft.weekdays.toMutableSet().apply {
                                                        if (!add(day)) remove(day)
                                                    })
                                                },
                                                modifier = Modifier.testTag("plan-editor-weekday-$day"),
                                                kind = if (day in draft.weekdays) ButtonKind.Primary else ButtonKind.Tonal,
                                                size = ButtonSize.Small,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val allDays = setOf(1, 2, 3, 4, 5, 6, 7)
                                        val workDays = setOf(1, 2, 3, 4, 5)
                                        val weekends = setOf(6, 7)
                                        MikaButton(
                                            tr(R.string.plans_every_day),
                                            { draft = draft.copy(weekdays = allDays) },
                                            kind = if (draft.weekdays == allDays) ButtonKind.Primary else ButtonKind.Tonal,
                                            size = ButtonSize.Small,
                                        )
                                        MikaButton(
                                            tr(R.string.plans_weekdays_workdays),
                                            { draft = draft.copy(weekdays = workDays) },
                                            kind = if (draft.weekdays == workDays) ButtonKind.Primary else ButtonKind.Tonal,
                                            size = ButtonSize.Small,
                                        )
                                        MikaButton(
                                            tr(R.string.plans_weekdays_weekends),
                                            { draft = draft.copy(weekdays = weekends) },
                                            kind = if (draft.weekdays == weekends) ButtonKind.Primary else ButtonKind.Tonal,
                                            size = ButtonSize.Small,
                                        )
                                    }
                                }
                            }
                        }

                        if (!draft.reminder && deviceAccess != null) {
                            ScreenOffAccessCard(
                                settings = deviceAccess,
                                onOpenSetup = { configuringScreenOff = true },
                            )
                        }

                        if (!draft.reminder) {
                            GroupLabel(tr(R.string.plans_smart_condition))
                            GroupCard {
                                ListRow(
                                    icon = MikaIcons.Search,
                                    title = tr(R.string.plans_smart_condition),
                                    subtitle = tr(R.string.plans_smart_condition_sub),
                                    first = true,
                                    onClick = { conditionExpanded = !conditionExpanded },
                                ) {
                                    MikaSwitch(
                                        checked = conditionExpanded,
                                        onCheckedChange = { on ->
                                            conditionExpanded = on
                                            if (!on) draft = draft.copy(condition = "", cooldownMinutes = "")
                                        },
                                        modifier = Modifier.testTag("plan-editor-condition-toggle"),
                                    )
                                }
                                AnimatedVisibility(conditionExpanded) {
                                    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                                        OutlinedTextField(
                                            value = draft.condition,
                                            onValueChange = { draft = draft.copy(condition = it) },
                                            modifier = Modifier.fillMaxWidth().testTag("plan-editor-condition").keepsKeyboard(),
                                            label = { Text(tr(R.string.plans_condition_optional)) },
                                            placeholder = { Text(tr(R.string.plans_condition_placeholder)) },
                                            minLines = 2,
                                            maxLines = 4,
                                        )
                                        if (draft.condition.isNotBlank()) {
                                            Spacer(Modifier.height(10.dp))
                                            SegmentedControl(
                                                listOf(tr(R.string.plans_notify_change), tr(R.string.plans_notify_each)),
                                                if (draft.notifyWhen == ConditionNotify.BecomesMet) 0 else 1,
                                                onSelect = {
                                                    draft = draft.copy(
                                                        notifyWhen = if (it == 0) ConditionNotify.BecomesMet else ConditionNotify.EachTimeMet,
                                                    )
                                                },
                                                tags = listOf("plan-editor-notify-change", "plan-editor-notify-each"),
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            OutlinedTextField(
                                                value = draft.cooldownMinutes,
                                                onValueChange = { draft = draft.copy(cooldownMinutes = it) },
                                                modifier = Modifier.fillMaxWidth().testTag("plan-editor-cooldown").keepsKeyboard(),
                                                label = { Text(tr(R.string.plans_cooldown_minutes)) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!draft.reminder) {
                            GroupLabel(tr(R.string.plans_execution_window))
                            GroupCard {
                                Column(Modifier.padding(14.dp)) {
                                    SegmentedControl(
                                        listOf(
                                            tr(R.string.plans_window_anytime),
                                            tr(R.string.plans_window_off_peak),
                                            tr(R.string.plans_window_custom),
                                        ),
                                        draft.executionWindow.ordinal,
                                        onSelect = { draft = draft.copy(executionWindow = PlanExecutionWindow.entries[it]) },
                                        tags = listOf("plan-editor-window-anytime", "plan-editor-window-off-peak", "plan-editor-window-custom"),
                                    )
                                    when (draft.executionWindow) {
                                        PlanExecutionWindow.DeepSeekOffPeak -> {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                tr(R.string.plans_window_off_peak_hint),
                                                style = MikaType.Caption,
                                                color = MikaTheme.colors.ink3,
                                            )
                                        }
                                        PlanExecutionWindow.Custom -> {
                                            Spacer(Modifier.height(10.dp))
                                            OutlinedTextField(
                                                value = draft.executionZone,
                                                onValueChange = { draft = draft.copy(executionZone = it) },
                                                modifier = Modifier.fillMaxWidth().testTag("plan-editor-window-zone").keepsKeyboard(),
                                                label = { Text(tr(R.string.plans_zone)) },
                                                singleLine = true,
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            OutlinedTextField(
                                                value = draft.windowStart,
                                                onValueChange = { draft = draft.copy(windowStart = it) },
                                                modifier = Modifier.fillMaxWidth().testTag("plan-editor-window-start").keepsKeyboard(),
                                                label = { Text(tr(R.string.plans_window_start)) },
                                                singleLine = true,
                                                trailingIcon = {
                                                    Box(
                                                        Modifier.size(44.dp).clip(CircleShape)
                                                            .clickable(role = Role.Button) { pickingTime = TimeTarget.WindowStart }
                                                            .testTag("plan-editor-window-start-pick"),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Icon(MikaIcons.Clock, tr(R.string.plans_pick_time), Modifier.size(20.dp), tint = MikaTheme.colors.ink3)
                                                    }
                                                },
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            OutlinedTextField(
                                                value = draft.windowEnd,
                                                onValueChange = { draft = draft.copy(windowEnd = it) },
                                                modifier = Modifier.fillMaxWidth().testTag("plan-editor-window-end").keepsKeyboard(),
                                                label = { Text(tr(R.string.plans_window_end)) },
                                                singleLine = true,
                                                trailingIcon = {
                                                    Box(
                                                        Modifier.size(44.dp).clip(CircleShape)
                                                            .clickable(role = Role.Button) { pickingTime = TimeTarget.WindowEnd }
                                                            .testTag("plan-editor-window-end-pick"),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Icon(MikaIcons.Clock, tr(R.string.plans_pick_time), Modifier.size(20.dp), tint = MikaTheme.colors.ink3)
                                                    }
                                                },
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                tr(R.string.plans_window_custom_hint, draft.executionZone),
                                                style = MikaType.Caption,
                                                color = MikaTheme.colors.ink3,
                                            )
                                        }
                                        PlanExecutionWindow.Anytime -> Unit
                                    }
                                }
                            }
                        }

                        GroupLabel(tr(R.string.plans_more_options))
                        GroupCard {
                            val zoneOffset = remember(pickerZone.id) { PlanTimeFormat.zoneOffsetLabel(pickerZone) }
                            ListRow(
                                icon = MikaIcons.Globe,
                                title = tr(R.string.plans_timezone),
                                subtitle = "${pickerZone.id} ($zoneOffset)",
                                first = true,
                                onClick = { pickingZone = true },
                            ) { RowChevron() }
                            ListRow(
                                icon = null,
                                title = tr(R.string.plans_when_idle),
                                subtitle = tr(R.string.plans_when_idle_sub),
                                onClick = { draft = draft.copy(whenIdle = !draft.whenIdle) },
                            ) {
                                MikaSwitch(draft.whenIdle, { draft = draft.copy(whenIdle = it) }, Modifier.testTag("plan-editor-idle"))
                            }
                        }

                        Spacer(Modifier.height(MikaSpace.S12))
                        GroupCard {
                            ListRow(
                                icon = null,
                                title = tr(R.string.plans_enabled),
                                subtitle = tr(R.string.plans_enabled_sub),
                                first = true,
                                onClick = { draft = draft.copy(enabled = !draft.enabled) },
                            ) {
                                MikaSwitch(draft.enabled, { draft = draft.copy(enabled = it) }, Modifier.testTag("plan-editor-enabled"))
                            }
                        }

                        preview?.let { plan ->
                            Spacer(Modifier.height(MikaSpace.S12))
                            val upcomingRuns = upcoming(plan, nowMs, 3)
                            GroupCard {
                                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                    upcomingRuns.firstOrNull()?.let { firstAtMs ->
                                        val firstZdt = Instant.ofEpochMilli(firstAtMs).atZone(pickerZone)
                                        val friendly = "${PlanTimeFormat.formatFriendlyDate(firstZdt.toLocalDate(), todayInZone, tr(R.string.plans_today), tr(R.string.plans_tomorrow), locale)} · ${PlanTimeFormat.formatDisplayTime(firstZdt.toLocalTime(), use24Hour, locale)}"
                                        val rel = PlanTimeFormat.formatRelativeDuration(firstAtMs - nowMs)
                                        Text(
                                            tr(R.string.plans_next_in, friendly, rel),
                                            style = MikaType.Body,
                                            fontWeight = FontWeight.Medium,
                                            color = MikaTheme.colors.ink,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    Text(tr(R.string.plans_preview), style = MikaType.Caption, color = MikaTheme.colors.ink3)
                                    upcomingRuns.forEachIndexed { index, at ->
                                        val zdt = Instant.ofEpochMilli(at).atZone(pickerZone)
                                        val formatted = "${PlanTimeFormat.formatFriendlyDate(zdt.toLocalDate(), todayInZone, tr(R.string.plans_today), tr(R.string.plans_tomorrow), locale)} ${PlanTimeFormat.formatDisplayTime(zdt.toLocalTime(), use24Hour, locale)}"
                                        Text(
                                            "${index + 1}  $formatted (${pickerZone.id})",
                                            Modifier.testTag("plan-editor-preview-$index"),
                                            style = MikaType.Caption,
                                            color = MikaTheme.colors.ink3,
                                        )
                                    }
                                }
                            }
                        }

                        if (attempted && preview == null) {
                            val reason = (candidate.exceptionOrNull() as? PlanValidationException)?.reason
                            Spacer(Modifier.height(MikaSpace.S8))
                            Text(
                                reason?.let { planValidationText(it) } ?: tr(R.string.op_failed),
                                Modifier.testTag("plan-editor-error"),
                                style = MikaType.Caption,
                                color = MikaTheme.colors.bad,
                            )
                        }
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerTapTile(
    icon: ImageVector,
    label: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = MikaTheme.colors
    Row(
        modifier
            .clip(MikaShapes.Card)
            .background(c.fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = c.ink2)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MikaType.Caption, color = c.ink3)
            Text(value, style = MikaType.Body, fontWeight = FontWeight.Medium, color = c.ink)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickTimeChips(
    nowMs: Long,
    zone: ZoneId,
    onPick: (LocalDateTime) -> Unit,
) {
    val nowZdt = Instant.ofEpochMilli(nowMs).atZone(zone).withSecond(0).withNano(0)
    val tonight = nowZdt.toLocalDate().atTime(21, 0)
    val tomorrowMorning = nowZdt.toLocalDate().plusDays(1).atTime(9, 0)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MikaButton(tr(R.string.plans_quick_15m), { onPick(nowZdt.plusMinutes(15).toLocalDateTime()) }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
        MikaButton(tr(R.string.plans_quick_1h), { onPick(nowZdt.plusHours(1).toLocalDateTime()) }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
        if (tonight.isAfter(nowZdt.toLocalDateTime())) {
            MikaButton(tr(R.string.plans_quick_tonight), { onPick(tonight) }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
        }
        MikaButton(tr(R.string.plans_quick_tomorrow_morning), { onPick(tomorrowMorning) }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
    }
}

@Composable
internal fun ScreenOffAccessCard(
    settings: DeviceAccessSettings,
    onOpenSetup: () -> Unit,
) {
    val config by settings.config.collectAsState()
    val scope = rememberCoroutineScope()
    var toggling by remember { mutableStateOf(false) }

    val subtitle = when {
        config.localBlocked -> tr(R.string.plans_screen_off_blocked)
        config.localEnabled && config.kind != null -> tr(R.string.plans_screen_off_ready)
        config.localEnabled -> tr(R.string.plans_screen_off_needs_setup)
        else -> tr(R.string.plans_screen_off_disabled)
    }
    GroupLabel(tr(R.string.plans_screen_off_title))
    GroupCard {
        ListRow(
            icon = MikaIcons.Lock,
            title = tr(R.string.plans_screen_off_title),
            subtitle = subtitle,
            first = true,
            onClick = onOpenSetup,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (config.localBlocked || (config.localEnabled && config.kind == null)) {
                    MikaButton(tr(R.string.plans_screen_off_setup_btn), onOpenSetup, kind = ButtonKind.Tonal, size = ButtonSize.Small)
                }
                MikaSwitch(
                    checked = config.localEnabled,
                    onCheckedChange = { on ->
                        if (toggling) return@MikaSwitch
                        toggling = true
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { settings.setLocalEnabled(on) } }
                            toggling = false
                            if (on && config.kind == null) onOpenSetup()
                        }
                    },
                    modifier = Modifier.testTag("plan-editor-screen-off"),
                    enabled = !toggling,
                )
            }
        }
    }
}

@Composable
internal fun ScreenOffUnlockDialog(settings: DeviceAccessSettings, onDismiss: () -> Unit) {
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.plans_screen_off_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DeviceAccessSection(settings, localTasks = true)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(tr(R.string.done)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClockChips(
    entries: List<LocalTime>,
    use24Hour: Boolean,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    val c = MikaTheme.colors
    val locale = uiLocale()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        entries.forEachIndexed { index, time ->
            Row(
                Modifier.height(34.dp).background(c.fill, MikaShapes.Card).clip(MikaShapes.Card),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.fillMaxHeight().clickable { onEdit(index) }
                        .padding(start = 12.dp, end = 4.dp).testTag("plan-editor-clock-$index"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(MikaIcons.Clock, null, Modifier.size(14.dp), tint = c.ink3)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        PlanTimeFormat.formatDisplayTime(time, use24Hour, locale),
                        style = MikaType.Caption,
                        color = c.ink2,
                    )
                }
                Box(
                    Modifier.fillMaxHeight().width(32.dp).clickable(role = Role.Button) { onRemove(index) }
                        .testTag("plan-editor-clock-remove-$index"),
                    contentAlignment = Alignment.Center,
                ) { Icon(MikaIcons.Close, tr(R.string.plans_remove_time), Modifier.size(13.dp), tint = c.ink3) }
            }
        }
        MikaButton(
            "＋ " + tr(R.string.plans_add_time),
            onAdd,
            modifier = Modifier.testTag("plan-editor-clock-add"),
            kind = ButtonKind.Tonal,
            size = ButtonSize.Small,
        )
    }
}

private sealed interface TimeTarget {
    data class FirstRun(val date: LocalDate) : TimeTarget
    data class Clock(val index: Int?) : TimeTarget
    data object WindowStart : TimeTarget
    data object WindowEnd : TimeTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("plan-date-ok"),
                onClick = {
                    state.selectedDateMillis?.let {
                        onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text(tr(R.string.done)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr(R.string.cancel)) } },
    ) {
        DatePicker(state, Modifier.testTag("plan-date-picker"), showModeToggle = false)
    }
}

/** Hour / minute / AM-PM wheels supporting both 12-hour and 24-hour modes. */
@Composable
private fun PlanTimePickerDialog(
    initial: LocalTime,
    title: String,
    use24Hour: Boolean,
    onToggle24Hour: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onPicked: (LocalTime) -> Unit,
) {
    var hour24 by remember(initial) { mutableIntStateOf(initial.hour) }
    var minute by remember(initial) { mutableIntStateOf(initial.minute) }
    val time12 = PlanTimeFormat.to12Hour(LocalTime.of(hour24, minute))
    var hour12Index by remember(initial, use24Hour) { mutableIntStateOf(time12.hour12 - 1) }
    var amPmIndex by remember(initial, use24Hour) { mutableIntStateOf(if (time12.isPm) 1 else 0) }

    MikaDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title)
                MikaButton(
                    text = tr(if (use24Hour) R.string.plans_format_24h else R.string.plans_format_12h),
                    onClick = {
                        if (use24Hour) {
                            val t = PlanTimeFormat.to12Hour(LocalTime.of(hour24, minute))
                            hour12Index = t.hour12 - 1
                            amPmIndex = if (t.isPm) 1 else 0
                        } else {
                            hour24 = PlanTimeFormat.from12Hour(hour12Index + 1, minute, amPmIndex == 1).hour
                        }
                        onToggle24Hour(!use24Hour)
                    },
                    modifier = Modifier.testTag("plan-time-format-toggle"),
                    kind = ButtonKind.Tonal,
                    size = ButtonSize.Small,
                )
            }
        },
        text = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (use24Hour) {
                        NumberWheel(24, hour24, "plan-time-hour", { hour24 = it }, Modifier.weight(1f))
                        Text(":", style = MikaType.Title, color = MikaTheme.colors.ink3, modifier = Modifier.padding(horizontal = 6.dp))
                        NumberWheel(60, minute, "plan-time-minute", { minute = it }, Modifier.weight(1f))
                    } else {
                        LabelWheel(
                            labels = (1..12).map { it.toString() },
                            initial = hour12Index,
                            tag = "plan-time-hour12",
                            onValueChange = { hour12Index = it },
                            modifier = Modifier.weight(1f),
                        )
                        Text(":", style = MikaType.Title, color = MikaTheme.colors.ink3, modifier = Modifier.padding(horizontal = 4.dp))
                        NumberWheel(60, minute, "plan-time-minute", { minute = it }, Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        LabelWheel(
                            labels = listOf(tr(R.string.plans_am), tr(R.string.plans_pm)),
                            initial = amPmIndex,
                            tag = "plan-time-ampm",
                            onValueChange = { amPmIndex = it },
                            modifier = Modifier.weight(0.9f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.fillMaxWidth().testTag("plan-time-ok"),
                onClick = {
                    val picked = if (use24Hour) LocalTime.of(hour24, minute)
                    else PlanTimeFormat.from12Hour(hour12Index + 1, minute, amPmIndex == 1)
                    onPicked(picked)
                },
            ) { Text(tr(R.string.save)) }
        },
        dismissButton = { TextButton(onDismiss, modifier = Modifier.fillMaxWidth()) { Text(tr(R.string.cancel)) } },
    )
}

@Composable
private fun PlanTimezonePickerDialog(
    currentZoneId: String,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit,
) {
    val locale = uiLocale()
    var query by remember { mutableStateOf("") }
    val zones = remember(query, currentZoneId, locale) {
        PlanTimeFormat.searchZones(query, preferredZoneId = currentZoneId, locale = locale)
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
                    if (zones.isEmpty()) {
                        EmptyNote(tr(R.string.plans_timezone_empty))
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = listMaxHeight)) {
                            items(zones, key = { it.id }) { entry ->
                                ListRow(
                                    icon = null,
                                    title = entry.id,
                                    subtitle = "${entry.displayName} · ${entry.offsetLabel}",
                                    first = entry == zones.first(),
                                    onClick = { onPicked(entry.id) },
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
            MikaButton(
                text = tr(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().testTag("plan-zone-cancel"),
                kind = ButtonKind.Tonal,
                size = ButtonSize.Small,
            )
        },
    )
}

private val WheelItem = 40.dp

@Composable
private fun NumberWheel(count: Int, initial: Int, tag: String, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    LabelWheel(
        labels = List(count) { "%02d".format(java.util.Locale.ROOT, it) },
        initial = initial,
        tag = tag,
        onValueChange = onValueChange,
        modifier = modifier,
    )
}

@Composable
private fun LabelWheel(
    labels: List<String>,
    initial: Int,
    tag: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MikaTheme.colors
    val density = LocalDensity.current
    val itemPx = with(density) { WheelItem.roundToPx() }
    val safeInitial = initial.coerceIn(0, (labels.size - 1).coerceAtLeast(0))
    val scroll = rememberScrollState(safeInitial * itemPx)
    val scope = rememberCoroutineScope()
    val selected by remember(labels.size) {
        derivedStateOf { (scroll.value.toFloat() / itemPx).roundToInt().coerceIn(0, (labels.size - 1).coerceAtLeast(0)) }
    }
    LaunchedEffect(scroll, itemPx, labels.size) {
        snapshotFlow { scroll.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val target = selected * itemPx
                if (scroll.value != target) scroll.animateScrollTo(target)
            }
        }
    }
    LaunchedEffect(selected) { onValueChange(selected) }
    Box(modifier.height(WheelItem * 5f), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(WheelItem).background(c.fill, MikaShapes.Tile))
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).testTag(tag),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(WheelItem * 2f))
            labels.forEachIndexed { index, text ->
                Box(
                    Modifier.fillMaxWidth().height(WheelItem)
                        .clip(MikaShapes.Tile)
                        .clickable { scope.launch { scroll.animateScrollTo(index * itemPx) } }
                        .testTag("$tag-$index"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text,
                        style = MikaType.Title.copy(fontFeatureSettings = "tnum"),
                        fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (index == selected) c.ink else c.ink3,
                    )
                }
            }
            Spacer(Modifier.height(WheelItem * 2f))
        }
    }
}

@Composable
private fun planValidationText(reason: PlanValidation): String = tr(when (reason) {
    PlanValidation.MissingContent -> R.string.plans_error_content
    PlanValidation.ContentTooLong -> R.string.plans_error_content_long
    PlanValidation.ConditionTooLong -> R.string.plans_error_condition_long
    PlanValidation.ReminderWithCondition -> R.string.plans_error_reminder_condition
    PlanValidation.InvalidFirstTime -> R.string.plans_error_time
    PlanValidation.TimeNotFuture -> R.string.plans_need_future
    PlanValidation.InvalidInterval -> R.string.plans_error_interval
    PlanValidation.InvalidClock -> R.string.plans_error_clock
    PlanValidation.InvalidZone -> R.string.plans_error_zone
    PlanValidation.InvalidWeekdays -> R.string.plans_error_weekdays
    PlanValidation.InvalidCooldown -> R.string.plans_error_cooldown
    PlanValidation.InvalidExecutionWindow -> R.string.plans_error_window
})
