package com.wangzan101.mika.settings.ui.models

import com.wangzan101.mika.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import com.wangzan101.mika.ui.components.MikaDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.core.model.ModelRole
import com.wangzan101.mika.settings.RoleChoice
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.Hairline
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlin.math.roundToInt

/** Backup models for a role: seamless failover if the primary model hits limits or goes down. */
@Composable
internal fun FallbackSection(
    ui: ModelSetupUi,
    role: ModelRole = ModelRole.Main,
    onPickFallback: (ModelRole, Int) -> Unit,
    onClearFallback: (ModelRole, Int) -> Unit,
    onMoveFallback: (ModelRole, Int, Int) -> Unit = { _, _, _ -> },
) {
    val shouldShow = when (role) {
        ModelRole.Main -> ui.roles.main != null
        ModelRole.Vision -> ui.roles.vision is RoleChoice.Model
        ModelRole.Light -> ui.roles.light is RoleChoice.Model
    }
    if (!shouldShow) return

    val fallbacks = ui.roles.fallbacks(role)
    val maxBackups = com.wangzan101.mika.settings.MAX_FALLBACKS
    val roleLabel = ModelsCopy.roleLabel(role)
    val hasAny = fallbacks.isNotEmpty()
    var expanded by rememberSaveable(role.name) { mutableStateOf(false) }
    var flowView by rememberSaveable(role.name) { mutableStateOf(false) }
    var removingSlot by rememberSaveable(role.name) { mutableStateOf<Int?>(null) }
    var movingFrom by rememberSaveable(role.name) { mutableStateOf<Int?>(null) }
    var movingTo by rememberSaveable(role.name) { mutableStateOf<Int?>(null) }
    val c = MikaTheme.colors

    GroupCard {
        // Collapsible Header Row - toggle integrated inline on the right
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(if (hasAny) c.accent.copy(alpha = 0.12f) else c.neutralSoft, MikaShapes.Tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    ModelsCopy.FALLBACK_ICON,
                    contentDescription = null,
                    tint = if (hasAny) c.accent else c.ink3,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.model_fallback_chain_title, roleLabel),
                        style = MikaType.BodyStrong,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (hasAny) {
                        Box(
                            Modifier
                                .background(c.accent.copy(alpha = 0.12f), MikaShapes.Pill)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                stringResource(R.string.model_fallback_active_count, fallbacks.size),
                                style = MikaType.Micro.copy(fontWeight = FontWeight.SemiBold),
                                color = c.accent,
                                maxLines = 1,
                            )
                        }
                    }
                }
                val previewText = if (expanded) {
                    stringResource(R.string.model_fallback_collapse_hint)
                } else if (hasAny) {
                    val primarySnippet = ui.configFor(role)?.model?.substringAfterLast('/') ?: "Primary"
                    val fallbackSnippets = fallbacks.mapNotNull { id ->
                        ui.configs.firstOrNull { it.id == id }?.model?.substringAfterLast('/')
                    }
                    (listOf(primarySnippet) + fallbackSnippets).joinToString(" ➔ ")
                } else {
                    stringResource(R.string.model_fallback_empty_preview)
                }
                Text(
                    previewText,
                    Modifier.padding(top = 2.dp),
                    style = MikaType.Caption,
                    color = c.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Inline List/Flow toggle — only visible when expanded, integrated into header
            if (expanded && hasAny) {
                Row(
                    Modifier
                        .background(c.fill, MikaShapes.Pill)
                        .border(0.5.dp, c.edge, MikaShapes.Pill)
                        .padding(2.dp)
                        .clickable(onClick = {}), // consume clicks so header doesn't toggle
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FallbackViewPill(
                        label = stringResource(R.string.model_fallback_view_list),
                        selected = !flowView,
                        onClick = { flowView = false },
                        tag = "fallback-view-list-${role.name.lowercase()}",
                        compact = true,
                    )
                    FallbackViewPill(
                        label = stringResource(R.string.model_fallback_view_flow),
                        selected = flowView,
                        onClick = { flowView = true },
                        tag = "fallback-view-flow-${role.name.lowercase()}",
                        compact = true,
                    )
                }
            }
            Icon(
                MikaIcons.Chevron,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = c.ink3,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (expanded) 90f else 0f),
            )
        }

        // Expanded Interactive Chain - List View or Flow View
        if (expanded) {

            if (flowView) {
                FallbackFlowView(
                    ui = ui,
                    role = role,
                    fallbacks = fallbacks,
                    maxBackups = maxBackups,
                    onPickFallback = onPickFallback,
                    onRequestMove = { from, to -> movingFrom = from; movingTo = to },
                    onRequestRemove = { removingSlot = it },
                )
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .background(c.fill, MikaShapes.Card)
                        .border(0.5.dp, c.edge, MikaShapes.Card)
                        .clip(MikaShapes.Card),
                ) {
                    val primaryCfg = ui.configFor(role)
                    if (primaryCfg != null) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .background(c.accent.copy(alpha = 0.14f), MikaShapes.Tile),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("1", style = MikaType.Caption.copy(fontWeight = FontWeight.Bold), color = c.accent)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.model_fallback_primary_badge),
                                    style = MikaType.Hint,
                                    color = c.accent,
                                    maxLines = 1,
                                )
                                Text(
                                    primaryCfg.model,
                                    Modifier.padding(top = 1.dp),
                                    style = MikaType.BodyStrong,
                                    fontFamily = FontFamily.Monospace,
                                    color = c.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val providerName = ui.providerOf(primaryCfg)?.let(ModelsCopy::providerName) ?: primaryCfg.name
                                if (providerName.isNotEmpty()) {
                                    Text(
                                        providerName,
                                        Modifier.padding(top = 1.dp),
                                        style = MikaType.Caption,
                                        color = c.ink2,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    fallbacks.forEachIndexed { slot, configId ->
                        Hairline(start = 14.dp)
                        val cfg = ui.configs.firstOrNull { it.id == configId }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPickFallback(role, slot) }
                                .testTag("role-fallback-${role.name.lowercase()}-$slot")
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .background(c.surface, MikaShapes.Tile),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${slot + 2}", style = MikaType.Caption.copy(fontWeight = FontWeight.Bold), color = c.ink2)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.model_fallback_backup_badge, slot + 1),
                                    style = MikaType.Hint,
                                    color = c.ink3,
                                    maxLines = 1,
                                )
                                Text(
                                    cfg?.model ?: ModelsCopy.UNSET,
                                    Modifier.padding(top = 1.dp),
                                    style = MikaType.BodyStrong,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (cfg == null) c.ink3 else c.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val providerName = cfg?.let { ui.providerOf(it)?.let(ModelsCopy::providerName) ?: it.name } ?: ""
                                if (providerName.isNotEmpty()) {
                                    Text(
                                        providerName,
                                        Modifier.padding(top = 1.dp),
                                        style = MikaType.Caption,
                                        color = c.ink2,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (fallbacks.size > 1) {
                                FallbackSlotControlButton(
                                    icon = MikaIcons.Up,
                                    contentDescription = stringResource(R.string.model_fallback_move_up),
                                    enabled = slot > 0,
                                    onClick = { movingFrom = slot; movingTo = slot - 1 },
                                )
                                FallbackSlotControlButton(
                                    icon = MikaIcons.Down,
                                    contentDescription = stringResource(R.string.model_fallback_move_down),
                                    enabled = slot < fallbacks.lastIndex,
                                    onClick = { movingFrom = slot; movingTo = slot + 1 },
                                )
                            }
                            FallbackSlotControlButton(
                                icon = MikaIcons.Close,
                                contentDescription = stringResource(R.string.model_fallback_remove_confirm),
                                enabled = true,
                                onClick = { removingSlot = slot },
                            )
                        }
                    }

                    if (fallbacks.size < maxBackups) {
                        Hairline(start = 14.dp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPickFallback(role, fallbacks.size) }
                                .testTag("role-fallback-${role.name.lowercase()}-${fallbacks.size}")
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .background(c.accent.copy(alpha = 0.08f), MikaShapes.Tile),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(MikaIcons.Plus, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
                            }
                            Text(
                                if (fallbacks.isEmpty()) stringResource(R.string.model_fallback_add_first)
                                else stringResource(R.string.model_fallback_add_another, fallbacks.size + 1, maxBackups),
                                style = MikaType.BodyStrong,
                                color = c.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    val pendingRemoveSlot = removingSlot
    if (pendingRemoveSlot != null && pendingRemoveSlot in fallbacks.indices) {
        val targetCfgId = fallbacks[pendingRemoveSlot]
        val targetCfg = ui.configs.firstOrNull { it.id == targetCfgId }
        val modelName = targetCfg?.model ?: ModelsCopy.UNSET
        MikaDialog(
            onDismissRequest = { removingSlot = null },
            title = { Text(stringResource(R.string.model_fallback_remove_title)) },
            text = {
                Text(stringResource(R.string.model_fallback_remove_body, modelName, roleLabel))
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = removingSlot
                    removingSlot = null
                    if (s != null) onClearFallback(role, s)
                }) {
                    Text(stringResource(R.string.model_fallback_remove_confirm), color = c.bad)
                }
            },
            dismissButton = {
                TextButton(onClick = { removingSlot = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val pendingMoveFrom = movingFrom
    val pendingMoveTo = movingTo
    if (pendingMoveFrom != null && pendingMoveTo != null &&
        pendingMoveFrom in fallbacks.indices && pendingMoveTo in fallbacks.indices &&
        pendingMoveFrom != pendingMoveTo
    ) {
        val fromModel = ui.configs.firstOrNull { it.id == fallbacks[pendingMoveFrom] }?.model ?: ModelsCopy.UNSET
        val toModel = ui.configs.firstOrNull { it.id == fallbacks[pendingMoveTo] }?.model ?: ModelsCopy.UNSET
        val direction = if (pendingMoveTo < pendingMoveFrom) {
            stringResource(R.string.model_fallback_move_before)
        } else {
            stringResource(R.string.model_fallback_move_after)
        }
        MikaDialog(
            onDismissRequest = { movingFrom = null; movingTo = null },
            title = { Text(stringResource(R.string.model_fallback_move_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.model_fallback_move_body,
                        fromModel,
                        pendingMoveFrom + 1,
                        direction,
                        toModel,
                        pendingMoveTo + 1,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val f = movingFrom
                    val t = movingTo
                    movingFrom = null
                    movingTo = null
                    if (f != null && t != null) onMoveFallback(role, f, t)
                }) {
                    Text(stringResource(R.string.model_fallback_move_confirm), color = c.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { movingFrom = null; movingTo = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (hasAny && expanded) {
        FooterNote(stringResource(R.string.model_fallback_footer_note, roleLabel))
    }
}

@Composable
private fun FallbackViewPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    compact: Boolean = false,
) {
    val c = MikaTheme.colors
    Box(
        Modifier
            .clip(MikaShapes.Pill)
            .background(if (selected) c.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(
                horizontal = if (compact) 9.dp else 12.dp,
                vertical = if (compact) 3.dp else 5.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = (if (compact) MikaType.Micro else MikaType.Caption).copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) c.ink else c.ink3,
            maxLines = 1,
        )
    }
}

@Composable
private fun FallbackSlotControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = MikaTheme.colors
    Box(
        Modifier
            .size(30.dp)
            .clip(MikaShapes.Pill)
            .alpha(if (enabled) 1f else 0.28f)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = c.ink3, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun FallbackFlowView(
    ui: ModelSetupUi,
    role: ModelRole,
    fallbacks: List<String>,
    maxBackups: Int,
    onPickFallback: (ModelRole, Int) -> Unit,
    onRequestMove: (Int, Int) -> Unit,
    onRequestRemove: (Int) -> Unit,
) {
    val c = MikaTheme.colors
    val density = LocalDensity.current
    val primaryCfg = ui.configFor(role)

    // Node geometry in dp, converted to px for crisp edge/grid math
    val nodeWidthDp = 182.dp
    val nodeHeightDp = 116.dp
    val hGapDp = 64.dp
    val startPadDp = 24.dp
    val baseTopDp = 84.dp

    val nodeWidthPx = with(density) { nodeWidthDp.toPx() }
    val nodeHeightPx = with(density) { nodeHeightDp.toPx() }
    val hGapPx = with(density) { hGapDp.toPx() }
    val startPadPx = with(density) { startPadDp.toPx() }
    val baseTopPx = with(density) { baseTopDp.toPx() }

    // Pan & Zoom camera state (Figma-style free canvas)
    var scale by remember(role.name) { mutableFloatStateOf(0.92f) }
    var panX by remember(role.name) { mutableFloatStateOf(0f) }
    var panY by remember(role.name) { mutableFloatStateOf(0f) }

    // Total nodes: Primary (if present) + backups + optional "+ Add" node
    val hasPrimary = primaryCfg != null
    val hasAddNode = fallbacks.size < maxBackups
    val totalNodes = (if (hasPrimary) 1 else 0) + fallbacks.size + (if (hasAddNode) 1 else 0)
    val worldWidthDp = startPadDp * 2 + nodeWidthDp * totalNodes.coerceAtLeast(1) +
        hGapDp * (totalNodes - 1).coerceAtLeast(0)
    val worldHeightDp = 300.dp

    val canvasBg = Color(0xFF0D0F14)
    val gridDotColor = c.ink3.copy(alpha = 0.22f)
    val edgeColor = c.accent.copy(alpha = 0.65f)
    val edgeMutedColor = c.ink3.copy(alpha = 0.45f)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .height(310.dp)
            .clip(MikaShapes.Card)
            .background(canvasBg)
            .border(0.75.dp, c.edge, MikaShapes.Card)
            .clipToBounds()
            .pointerInput(role.name) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.45f, 2.2f)
                    scale = newScale
                    panX += pan.x
                    panY += pan.y
                }
            }
            .testTag("flow-canvas-${role.name.lowercase()}"),
    ) {
        // Infinite-feeling dot grid background that moves and scales with the camera
        Canvas(Modifier.fillMaxSize()) {
            val gridStepPx = 24.dp.toPx() * scale
            if (gridStepPx >= 6f) {
                val offsetX = ((panX % gridStepPx) + gridStepPx) % gridStepPx
                val offsetY = ((panY % gridStepPx) + gridStepPx) % gridStepPx
                val dotRadius = (1.1.dp.toPx() * scale).coerceIn(0.7f, 2.4f)
                var x = offsetX
                while (x < size.width) {
                    var y = offsetY
                    while (y < size.height) {
                        drawCircle(color = gridDotColor, radius = dotRadius, center = Offset(x, y))
                        y += gridStepPx
                    }
                    x += gridStepPx
                }
            }
        }

        // Transformed World Layer (Edges + Nodes)
        Box(
            Modifier
                .requiredSize(width = worldWidthDp, height = worldHeightDp)
                .graphicsLayer {
                    translationX = panX
                    translationY = panY
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                },
        ) {
            // Draw directed Bezier edges + arrowheads between consecutive nodes
            Canvas(Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                val arrowSize = 7.dp.toPx()
                for (i in 0 until totalNodes - 1) {
                    val x1 = startPadPx + i * (nodeWidthPx + hGapPx) + nodeWidthPx
                    val y1 = baseTopPx + nodeHeightPx / 2f
                    val x2 = startPadPx + (i + 1) * (nodeWidthPx + hGapPx)
                    val y2 = baseTopPx + nodeHeightPx / 2f
                    val ctrlOffset = hGapPx * 0.48f

                    val isToAdd = hasAddNode && i == totalNodes - 2
                    val lineColor = if (isToAdd) edgeMutedColor else edgeColor

                    val path = Path().apply {
                        moveTo(x1, y1)
                        cubicTo(x1 + ctrlOffset, y1, x2 - ctrlOffset, y2, x2 - 2f, y2)
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )

                    // Port circles on source & target
                    drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = Offset(x1, y1))

                    // Arrowhead pointing right into target port
                    val arrowPath = Path().apply {
                        moveTo(x2, y2)
                        lineTo(x2 - arrowSize, y2 - arrowSize * 0.55f)
                        lineTo(x2 - arrowSize, y2 + arrowSize * 0.55f)
                        close()
                    }
                    drawPath(path = arrowPath, color = lineColor)
                }
            }

            // Edge condition pills ("on limit") centered on each connector
            for (i in 0 until totalNodes - 1) {
                val midX = startPadPx + i * (nodeWidthPx + hGapPx) + nodeWidthPx + hGapPx / 2f
                val midY = baseTopPx + nodeHeightPx / 2f
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                x = (midX - with(density) { 26.dp.toPx() }).roundToInt(),
                                y = (midY - with(density) { 10.dp.toPx() }).roundToInt(),
                            )
                        }
                        .background(canvasBg, MikaShapes.Pill)
                        .border(0.5.dp, c.edge, MikaShapes.Pill)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        stringResource(R.string.model_fallback_edge_short),
                        style = MikaType.Micro,
                        color = c.ink3,
                        maxLines = 1,
                    )
                }
            }

            var nodeIdx = 0

            // Primary Node
            if (primaryCfg != null) {
                val xPx = startPadPx + nodeIdx * (nodeWidthPx + hGapPx)
                val providerName = ui.providerOf(primaryCfg)?.let(ModelsCopy::providerName) ?: primaryCfg.name
                FlowGraphNodeCard(
                    modifier = Modifier.offset { IntOffset(xPx.roundToInt(), baseTopPx.roundToInt()) },
                    width = nodeWidthDp,
                    height = nodeHeightDp,
                    badge = stringResource(R.string.model_fallback_primary_badge),
                    badgeColor = c.accent,
                    borderColor = c.accent.copy(alpha = 0.65f),
                    title = primaryCfg.model,
                    subtitle = providerName,
                )
                nodeIdx++
            }

            // Backup Nodes
            fallbacks.forEachIndexed { slot, configId ->
                val xPx = startPadPx + nodeIdx * (nodeWidthPx + hGapPx)
                val cfg = ui.configs.firstOrNull { it.id == configId }
                val providerName = cfg?.let { ui.providerOf(it)?.let(ModelsCopy::providerName) ?: it.name } ?: ""
                FlowGraphNodeCard(
                    modifier = Modifier
                        .offset { IntOffset(xPx.roundToInt(), baseTopPx.roundToInt()) }
                        .testTag("flow-node-fallback-${role.name.lowercase()}-$slot"),
                    width = nodeWidthDp,
                    height = nodeHeightDp,
                    badge = stringResource(R.string.model_fallback_backup_badge, slot + 1),
                    badgeColor = c.ink2,
                    borderColor = c.edge,
                    title = cfg?.model ?: ModelsCopy.UNSET,
                    subtitle = providerName,
                    onClick = { onPickFallback(role, slot) },
                    canMoveLeft = fallbacks.size > 1 && slot > 0,
                    canMoveRight = fallbacks.size > 1 && slot < fallbacks.lastIndex,
                    onMoveLeft = { onRequestMove(slot, slot - 1) },
                    onMoveRight = { onRequestMove(slot, slot + 1) },
                    onRemove = { onRequestRemove(slot) },
                )
                nodeIdx++
            }

            // "+ Add Backup" Terminal Node
            if (hasAddNode) {
                val xPx = startPadPx + nodeIdx * (nodeWidthPx + hGapPx)
                Box(
                    Modifier
                        .offset { IntOffset(xPx.roundToInt(), baseTopPx.roundToInt()) }
                        .size(width = nodeWidthDp, height = nodeHeightDp)
                        .clip(MikaShapes.Card)
                        .background(Color(0xFF151922))
                        .border(1.dp, c.accent.copy(alpha = 0.4f), MikaShapes.Card)
                        .clickable { onPickFallback(role, fallbacks.size) }
                        .testTag("flow-node-add-${role.name.lowercase()}")
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(MikaIcons.Plus, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                        Text(
                            if (fallbacks.isEmpty()) stringResource(R.string.model_fallback_add_first)
                            else stringResource(R.string.model_fallback_add_another, fallbacks.size + 1, maxBackups),
                            style = MikaType.Caption.copy(fontWeight = FontWeight.SemiBold),
                            color = c.accent,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Top-right HUD: Zoom % & Reset Camera button
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .background(Color(0xCC161922), MikaShapes.Pill)
                .border(0.5.dp, c.edge, MikaShapes.Pill)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${(scale * 100).roundToInt()}%",
                style = MikaType.Micro.copy(fontFamily = FontFamily.Monospace),
                color = c.ink2,
            )
            Text(
                stringResource(R.string.model_fallback_flow_reset),
                Modifier
                    .clip(MikaShapes.Pill)
                    .clickable {
                        scale = 0.92f
                        panX = 0f
                        panY = 0f
                    }
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                style = MikaType.Micro.copy(fontWeight = FontWeight.SemiBold),
                color = c.accent,
            )
        }

        // Bottom-left HUD: Pan/Zoom gesture hint
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .background(Color(0xB3161922), MikaShapes.Pill)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                stringResource(R.string.model_fallback_flow_hint),
                style = MikaType.Micro,
                color = c.ink3,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FlowGraphNodeCard(
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    badge: String,
    badgeColor: Color,
    borderColor: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    canMoveLeft: Boolean = false,
    canMoveRight: Boolean = false,
    onMoveLeft: (() -> Unit)? = null,
    onMoveRight: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val c = MikaTheme.colors
    val hasActions = onMoveLeft != null || onMoveRight != null || onRemove != null
    Column(
        modifier
            .size(width = width, height = height)
            .clip(MikaShapes.Card)
            .background(Color(0xFF171A21))
            .border(1.dp, borderColor, MikaShapes.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier
                    .background(badgeColor.copy(alpha = 0.16f), MikaShapes.Pill)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    badge,
                    style = MikaType.Micro.copy(fontWeight = FontWeight.SemiBold),
                    color = badgeColor,
                    maxLines = 1,
                )
            }
            Column {
                Text(
                    title,
                    style = MikaType.Caption.copy(fontWeight = FontWeight.SemiBold),
                    fontFamily = FontFamily.Monospace,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        Modifier.padding(top = 2.dp),
                        style = MikaType.Micro,
                        color = c.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (hasActions) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.edge))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF12151C))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onMoveLeft != null) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(MikaShapes.Pill)
                                .background(Color(0xFF1C202B))
                                .alpha(if (canMoveLeft) 1f else 0.3f)
                                .then(if (canMoveLeft) Modifier.clickable(onClick = onMoveLeft) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                MikaIcons.Up,
                                contentDescription = stringResource(R.string.model_fallback_move_up),
                                tint = c.ink2,
                                modifier = Modifier.size(14.dp).rotate(-90f),
                            )
                        }
                    }
                    if (onMoveRight != null) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(MikaShapes.Pill)
                                .background(Color(0xFF1C202B))
                                .alpha(if (canMoveRight) 1f else 0.3f)
                                .then(if (canMoveRight) Modifier.clickable(onClick = onMoveRight) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                MikaIcons.Up,
                                contentDescription = stringResource(R.string.model_fallback_move_down),
                                tint = c.ink2,
                                modifier = Modifier.size(14.dp).rotate(90f),
                            )
                        }
                    }
                }
                if (onRemove != null) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(MikaShapes.Pill)
                            .background(c.bad.copy(alpha = 0.12f))
                            .clickable(onClick = onRemove),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            MikaIcons.Close,
                            contentDescription = stringResource(R.string.model_fallback_remove_confirm),
                            tint = c.bad,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}
