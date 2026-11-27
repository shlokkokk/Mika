package com.wangzan101.mika.settings.ui.models

import com.wangzan101.mika.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.wangzan101.mika.ui.components.MikaDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var removingSlot by rememberSaveable(role.name) { mutableStateOf<Int?>(null) }
    val c = MikaTheme.colors

    GroupCard {
        // Collapsible Header Row - sleek and compact
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
                    val primarySnippet = ui.configFor(role)?.model?.substringAfterLast('/') ?: stringResource(R.string.model_fallback_primary_badge)
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
            Icon(
                MikaIcons.Chevron,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                tint = c.ink3,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (expanded) 90f else 0f),
            )
        }

        // Expanded Interactive Chain - nested inside an indented tray card
        if (expanded) {
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
                    val canUp = slot > 0
                    val canDown = slot < fallbacks.lastIndex
                    val moveUp = stringResource(R.string.model_fallback_move_up)
                    val moveDown = stringResource(R.string.model_fallback_move_down)
                    val remove = stringResource(R.string.model_fallback_remove_desc)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPickFallback(role, slot) }
                            // 读屏:在这一行上就能上移、下移、移除,不用逐个找右边的小按钮
                            .semantics {
                                customActions = listOfNotNull(
                                    CustomAccessibilityAction(moveUp) { onMoveFallback(role, slot, slot - 1); true }.takeIf { canUp },
                                    CustomAccessibilityAction(moveDown) { onMoveFallback(role, slot, slot + 1); true }.takeIf { canDown },
                                    CustomAccessibilityAction(remove) { removingSlot = slot; true },
                                )
                            }
                            .testTag("role-fallback-${role.name.lowercase()}-$slot")
                            .padding(start = 14.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .background(c.surface, MikaShapes.Tile),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${slot + 2}", style = MikaType.Caption.copy(fontWeight = FontWeight.Bold), color = c.ink2)
                        }
                        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
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
                        Row {
                            // 只有一个备援时没有可挪的,不占地方
                            if (fallbacks.size > 1) {
                                FallbackIconButton(
                                    MikaIcons.Up, stringResource(R.string.model_fallback_move_up_desc, slot + 1), canUp,
                                    Modifier.testTag("role-fallback-${role.name.lowercase()}-$slot-up"),
                                ) { onMoveFallback(role, slot, slot - 1) }
                                FallbackIconButton(
                                    MikaIcons.Down, stringResource(R.string.model_fallback_move_down_desc, slot + 1), canDown,
                                    Modifier.testTag("role-fallback-${role.name.lowercase()}-$slot-down"),
                                ) { onMoveFallback(role, slot, slot + 1) }
                            }
                            FallbackIconButton(
                                MikaIcons.Close, remove, true,
                                Modifier.testTag("role-fallback-${role.name.lowercase()}-$slot-remove"),
                            ) { removingSlot = slot }
                        }
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

    if (hasAny && expanded) {
        FooterNote(stringResource(R.string.model_fallback_footer_note, roleLabel))
    }
}


/** 备援一行右边的图标按钮:点按区域 48dp,图标 16dp;不能用时变淡、不可点。 */
@Composable
private fun FallbackIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = MikaTheme.colors
    Box(
        modifier
            .size(48.dp)
            .clip(MikaShapes.Pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) c.ink3 else c.ink3.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp),
        )
    }
}
