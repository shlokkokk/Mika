package com.wangzan101.mika.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.i18n.tr
import com.wangzan101.mika.ui.theme.MikaColors
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaMotion
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 分组小标题:13 / 500,辅助文字色。 */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 8.dp, bottom = 8.dp), style = MikaType.Label, color = MikaTheme.colors.ink3)
}

/** 分组或页面底部的说明,和卡片里的文字对齐。 */
@Composable
fun FooterNote(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text,
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
        style = MikaType.Hint,
        color = color.takeOrElse { MikaTheme.colors.ink3 },
    )
}

/** 一行摘要 + 可展开的「详情」:默认只显示 [summary],点「详情」展开 [detail] 全文。间距由调用方用 [modifier] 给。 */
@Composable
fun DetailNote(summary: String, detail: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val reduce = MikaTheme.reduceMotion
    val chevronAngle by animateFloatAsState(if (open) 90f else 0f, MikaMotion.switch(reduce), label = "detail-chevron")
    Column(modifier) {
        Text(summary, style = MikaType.Hint, color = MikaTheme.colors.ink3)
        if (detail.isNotBlank()) {
            Row(
                Modifier.clickable { open = !open }.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DirectionalIcon(
                    MikaIcons.Chevron, null,
                    Modifier.size(MikaSize.SmallIcon).rotate(chevronAngle),
                    tint = MikaTheme.colors.accent,
                )
                Text(
                    tr(R.string.details), Modifier.padding(start = 2.dp),
                    style = MikaType.Hint.copy(fontWeight = FontWeight.SemiBold), color = MikaTheme.colors.accent,
                )
            }
            AnimatedVisibility(
                visible = open,
                enter = if (reduce) EnterTransition.None else expandVertically(tween(MikaMotion.PAGE_MS, easing = MikaMotion.Decelerate)) + fadeIn(tween(MikaMotion.SWITCH_MS, easing = MikaMotion.Decelerate)),
                exit = if (reduce) ExitTransition.None else shrinkVertically(tween(MikaMotion.PAGE_MS, easing = MikaMotion.Decelerate)) + fadeOut(tween(MikaMotion.SWITCH_MS, easing = MikaMotion.Decelerate)),
            ) {
                Text(detail, Modifier.padding(top = 6.dp), style = MikaType.Hint, color = MikaTheme.colors.ink2)
            }
        }
    }
}

/**
 * Shared expandable row with smooth expand/collapse, rotating chevron pill, right-aligned trailing text
 * (never fighting the title for weight), and automatic bring-into-view with bottom breathing space.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoldRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: TileTone = TileTone.Neutral,
    subtitle: String? = null,
    trailingText: String? = null,
    trailingTone: ValueTone = ValueTone.Plain,
    first: Boolean = false,
    enabled: Boolean = true,
    bottomBreathingSpace: Dp = 28.dp,
    headerModifier: Modifier = Modifier,
    stateLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MikaTheme.colors
    val reduce = MikaTheme.reduceMotion
    val requester = remember { BringIntoViewRequester() }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        suspend fun bringWithMargin() {
            val extraBottomPx = with(density) { bottomBreathingSpace.toPx() }
            val rect = if (boxSize.width > 0 && boxSize.height > 0) {
                Rect(0f, 0f, boxSize.width.toFloat(), boxSize.height.toFloat() + extraBottomPx)
            } else null
            requester.bringIntoView(rect)
        }
        bringWithMargin()
        if (!reduce) {
            delay(MikaMotion.PAGE_MS.toLong())
            bringWithMargin()
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .onSizeChanged { boxSize = it }
            .bringIntoViewRequester(requester),
    ) {
        Row(
            headerModifier
                .fillMaxWidth()
                .topHairline(first, if (icon != null) TextStartWithIcon else TextStartPlain, c.hair)
                .clickable(enabled = enabled, role = Role.Button, onClick = onToggle)
                .then(if (stateLabel != null) Modifier.semantics { stateDescription = stateLabel } else Modifier)
                .heightIn(min = MikaSize.RowMin)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconTile(icon, tone)
                Spacer(Modifier.width(RowGap))
            }
            Column(Modifier.weight(1f).padding(vertical = 11.dp)) {
                Text(title, style = MikaType.Body, color = if (enabled) c.ink else c.ink3)
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, Modifier.padding(top = 2.dp), style = MikaType.Caption, color = c.ink3)
                }
            }
            Row(
                Modifier.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!trailingText.isNullOrEmpty()) {
                    val valColor = when (trailingTone) {
                        ValueTone.Plain -> c.ink3
                        ValueTone.Ok -> if (LocalPlainList.current) c.ink3 else c.ok
                        ValueTone.Warn -> c.warn
                        ValueTone.Bad -> c.bad
                        ValueTone.Link -> c.accent
                    }
                    Text(
                        trailingText,
                        Modifier.widthIn(max = 160.dp),
                        style = MikaType.Value,
                        fontWeight = if (trailingTone == ValueTone.Link) FontWeight.Medium else FontWeight.Normal,
                        color = valColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FoldExpandPill(expanded, reduce)
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduce) EnterTransition.None else expandVertically(tween(MikaMotion.PAGE_MS, easing = MikaMotion.Decelerate)) + fadeIn(tween(MikaMotion.SWITCH_MS, easing = MikaMotion.Decelerate)),
            exit = if (reduce) ExitTransition.None else shrinkVertically(tween(MikaMotion.PAGE_MS, easing = MikaMotion.Decelerate)) + fadeOut(tween(MikaMotion.SWITCH_MS, easing = MikaMotion.Decelerate)),
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun FoldExpandPill(expanded: Boolean, reduceMotion: Boolean) {
    val c = MikaTheme.colors
    val angle by animateFloatAsState(if (expanded) -90f else 90f, MikaMotion.switch(reduceMotion), label = "fold-chevron")
    val bg by animateColorAsState(if (expanded) c.accentSoft else c.fill, MikaMotion.switch(reduceMotion), label = "fold-pill-bg")
    val tint by animateColorAsState(if (expanded) c.accent else c.ink3, MikaMotion.switch(reduceMotion), label = "fold-pill-fg")
    Box(
        Modifier.size(26.dp)
            .background(bg, CircleShape)
            .border(0.5.dp, c.edge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(MikaIcons.Chevron, null, Modifier.size(13.dp).rotate(angle), tint = tint)
    }
}

/** 图标块的底色和图形色。默认中性;只有状态才上色。 */
enum class TileTone {
    Neutral, Accent, Ok, Warn, Bad;

    fun colors(c: MikaColors): Pair<Color, Color> = when (this) {
        Neutral -> c.neutralSoft to c.ink
        Accent -> c.accentSoft to c.accent
        Ok -> c.okSoft to c.ok
        Warn -> c.warnSoft to c.warn
        Bad -> c.badSoft to c.bad
    }
}

/**
 * 行左边的图标块:30dp、圆角 9,里面图形 18dp。
 * [brandTile] 非空时是自着色的品牌标:底色用它(两种模式一致),图形保留自身颜色。
 */
@Composable
fun IconTile(icon: ImageVector, tone: TileTone = TileTone.Neutral, brandTile: Color? = null) {
    val (bg, fg) = if (brandTile != null) (brandTile to Color.Unspecified) else tone.colors(MikaTheme.colors)
    // 简约列表:图标直接画,不垫底块;占位宽度不变,分隔线和文字仍对齐
    if (brandTile == null && LocalPlainList.current) {
        Box(Modifier.size(MikaSize.Tile), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (tone == TileTone.Neutral || tone == TileTone.Ok) MikaTheme.colors.ink else fg)
        }
        return
    }
    Box(Modifier.size(MikaSize.Tile).background(bg, MikaShapes.Tile), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(MikaSize.TileIcon), tint = fg)
    }
}

/** 行与行之间的分隔线,从 [start] 画到右边;分组首行([first])不画。 */
private fun Modifier.topHairline(first: Boolean, start: Dp, color: Color): Modifier =
    if (first) this
    else drawBehind {
        val y = 0.25.dp.toPx()
        drawLine(color, Offset(start.toPx(), y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
    }

/** 有图标块时文字从这里开始:左边距 16 + 图标块 30 + 间距 14。 */
private val TextStartWithIcon = 60.dp
private val TextStartPlain = 16.dp

/**
 * 列表行:左边图标块可选,中间标题和说明,右边是 [trailing](值、胶囊、开关、箭头)。
 * [detail] 是说明下面再一行的小字(如「上次结果」),颜色由 [detailColor] 定。
 * [first] 为分组首行时不画上分隔线;[onClick] 为空时整行不可点。
 */
@Composable
fun ListRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    first: Boolean = false,
    titleColor: Color = Color.Unspecified,
    tone: TileTone = TileTone.Neutral,
    brandTile: Color? = null,
    detail: String? = null,
    detailColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** 标题最多几行,超出省略(列表里的长标题用 1)。 */
    titleMaxLines: Int = Int.MAX_VALUE,
    /** 解释这一行是什么的说明:简约列表里收到卡片下面当脚注,别处照旧显示在标题下。 */
    note: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = MikaTheme.colors
    val noteInCard = rememberCardNote(title, note)
    val icon = if (brandTile != null) icon else rememberCardIcon(icon)
    val sub = listOfNotNull(subtitle?.takeIf { it.isNotEmpty() }, note?.takeIf { !noteInCard && it.isNotEmpty() }).joinToString("\n").ifEmpty { null }
    Row(
        modifier.fillMaxWidth()
            .topHairline(first, if (icon != null) TextStartWithIcon else TextStartPlain, c.hair)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = MikaSize.RowMin)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon, tone, brandTile)
            Spacer(Modifier.width(RowGap))
        }
        Column(Modifier.weight(1f).padding(vertical = 11.dp)) {
            Text(title, style = MikaType.Body, color = titleColor.takeOrElse { c.ink },
                maxLines = titleMaxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (!sub.isNullOrEmpty()) Text(sub, Modifier.padding(top = 2.dp), style = MikaType.Caption, color = c.ink3)
            if (!detail.isNullOrEmpty()) Text(detail, Modifier.padding(top = 2.dp), style = MikaType.Hint, color = detailColor.takeOrElse { c.ink3 })
        }
        // 行尾最多占标题旁可用宽度的一半出头:值再长也只省略值,标题不被挤没
        Row(
            Modifier.trailingSlot(TrailingMaxFraction, RowGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RowGap),
            content = trailing,
        )
    }
}

/** 行里图标块、标题、行尾之间的间距。 */
private val RowGap = 14.dp

/** 行尾([ListRow] 的 trailing)最多占图标块以外宽度的比例。 */
private const val TrailingMaxFraction = 0.52f

/**
 * 行尾的位置:宽度不超过父布局给的最大宽度的 [fraction](宽度不受限时不管),有内容时前面空出 [gap],
 * 没内容时整块不占地方(和以前一样,标题一直排到行尾)。一次测量,不用子组合。
 */
private fun Modifier.trailingSlot(fraction: Float, gap: Dp) = layout { measurable, constraints ->
    val gapPx = gap.roundToPx()
    val c = if (constraints.hasBoundedWidth) {
        val max = ((constraints.maxWidth * fraction).roundToInt() - gapPx).coerceAtLeast(0)
        constraints.copy(minWidth = 0, maxWidth = max)
    } else constraints.copy(minWidth = 0)
    val p = measurable.measure(c)
    if (p.width == 0) layout(0, p.height) {}
    else layout(p.width + gapPx, p.height) { p.placeRelative(gapPx, 0) }
}

enum class ValueTone { Plain, Ok, Warn, Bad, Link }

/** 行尾的值。放不下时省略,把位置先让给同一行尾的开关、箭头。 */
@Composable
fun RowScope.RowValue(text: String, tone: ValueTone = ValueTone.Plain, modifier: Modifier = Modifier) {
    val c = MikaTheme.colors
    Text(
        text, modifier.weight(1f, fill = false), style = MikaType.Value,
        fontWeight = if (tone == ValueTone.Link) FontWeight.Medium else FontWeight.Normal,
        color = when (tone) {
            ValueTone.Plain -> c.ink3
            // 简约列表里「正常」不上色,只有要你处理的才有颜色
            ValueTone.Ok -> if (LocalPlainList.current) c.ink3 else c.ok
            ValueTone.Warn -> c.warn
            ValueTone.Bad -> c.bad
            ValueTone.Link -> c.accent
        },
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

/** 行尾的小箭头:点进去还有一页。 */
@Composable
fun RowChevron() {
    DirectionalIcon(MikaIcons.Chevron, null, Modifier.size(MikaSize.SmallIcon), tint = MikaTheme.colors.ink3)
}

/** 单选行:左边圆圈,选中时实心强调色。分隔线从文字处开始。 */
@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    first: Boolean = false,
    enabled: Boolean = true,
    titleStyle: TextStyle = MikaType.Body,
    /** 标题下面的附加内容(例如能力小标签)。 */
    below: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** 这一项的说明:简约列表里只在选中时显示在卡片下面,别处显示在标题下。 */
    note: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = MikaTheme.colors
    val noteInCard = rememberCardNote(title, note, selected)
    val sub = listOfNotNull(subtitle?.takeIf { it.isNotEmpty() }, note?.takeIf { !noteInCard && it.isNotEmpty() }).joinToString("\n").ifEmpty { null }
    Row(
        modifier.fillMaxWidth()
            .topHairline(first, 50.dp, c.hair)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioMark(selected, enabled)
        Column(Modifier.weight(1f).padding(vertical = 9.dp)) {
            Text(title, style = titleStyle, color = if (enabled) c.ink else c.ink3)
            if (!sub.isNullOrEmpty()) Text(sub, Modifier.padding(top = 2.dp), style = MikaType.Caption, color = c.ink3)
            if (below != null) Box(Modifier.padding(top = 4.dp)) { below() }
        }
        trailing()
    }
}

@Composable
private fun RadioMark(selected: Boolean, enabled: Boolean = true) {
    val c = MikaTheme.colors
    val reduce = MikaTheme.reduceMotion
    val active = selected && enabled
    val fillTarget = if (active) (if (LocalPlainList.current) c.ink else c.accent) else Color.Transparent
    val borderTarget = if (active) fillTarget else c.hair
    val fill by animateColorAsState(fillTarget, MikaMotion.switch(reduce), label = "radio-fill")
    val borderColor by animateColorAsState(borderTarget, MikaMotion.switch(reduce), label = "radio-border")
    val dotScale by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (reduce) snap() else spring(dampingRatio = 0.72f, stiffness = 580f),
        label = "radio-dot",
    )
    Box(
        Modifier.size(22.dp)
            .background(fill, CircleShape)
            .border(1.6.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (dotScale > 0.01f) {
            Box(
                Modifier.size(10.dp)
                    .graphicsLayer { scaleX = dotScale; scaleY = dotScale }
                    .background(if (LocalPlainList.current) c.surface else c.onAccent, CircleShape),
            )
        }
    }
}

/**
 * 卡片里的输入行:左边固定宽的标题,右边单行输入框(空时显示 [placeholder])。
 * [secret] 为真时按密码显示;[trailing] 放在输入框右边(例如「显示」)。
 * [onClear] 非空且输入框里有字时,在文字右边放一个清空按钮(和搜索框的叉同一套)。
 */
@Composable
fun InputRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    first: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    labelWidth: Dp = 84.dp,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    clearDescription: String = "",
    clearModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = MikaTheme.colors
    Row(
        Modifier.fillMaxWidth().topHairline(first, TextStartPlain, c.hair).keepsKeyboard()
            .heightIn(min = MikaSize.RowMin).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(labelWidth), style = MikaType.Body, color = c.ink)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = MikaType.Body, color = c.ink3, maxLines = 1)
            BasicTextField(
                value, onChange, modifier.fillMaxWidth(), singleLine = true,
                textStyle = MikaType.Body.copy(color = c.ink, textDirection = androidx.compose.ui.text.style.TextDirection.Content),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            )
        }
        if (onClear != null && value.isNotEmpty()) {
            FieldClearButton(clearDescription, onClear, clearModifier.padding(start = 4.dp))
        }
        if (trailing != null) Box(Modifier.padding(start = 8.dp)) { trailing() }
    }
}

/**
 * 卡片里和 [InputRow] 排成一列的选择行:左边同样宽的标题,现在选的值从输入框里文字的位置起排,行尾一个小箭头;
 * 点了由调用方弹出选择。同一张卡片里既有输入行又有选择行时用它,两种行的内容上下对齐。
 */
@Composable
fun PickRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    first: Boolean = false,
    labelWidth: Dp = 84.dp,
    modifier: Modifier = Modifier,
) {
    val c = MikaTheme.colors
    Row(
        modifier.fillMaxWidth().topHairline(first, TextStartPlain, c.hair)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = MikaSize.RowMin).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(labelWidth), style = MikaType.Body, color = c.ink)
        Text(
            value, Modifier.weight(1f), style = MikaType.Body, color = c.ink,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        RowChevron()
    }
}

/** 输入框末尾的叉:点一下清空。和搜索框的清空按钮同一套大小和颜色,只在有内容时由调用方放出来。 */
@Composable
fun FieldClearButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(30.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(MikaIcons.Close, description, Modifier.size(14.dp), tint = MikaTheme.colors.ink3)
    }
}

/** 卡片里自己排版的块之间的分隔线,从 [start] 画到右边。 */
@Composable
fun Hairline(start: Dp = TextStartWithIcon) {
    Box(Modifier.fillMaxWidth().padding(start = start).height(0.5.dp).background(MikaTheme.colors.hair))
}
