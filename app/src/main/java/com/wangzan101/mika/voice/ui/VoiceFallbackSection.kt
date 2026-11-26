package com.wangzan101.mika.voice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.components.ActionSheet
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.Hairline
import com.wangzan101.mika.ui.components.SheetAction
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.voice.model.VoiceCatalogSnapshot
import com.wangzan101.mika.voice.model.VoiceCatalogStore
import com.wangzan101.mika.voice.model.VoiceModelRef

/**
 * Clean collapsible backup chain card for Voice Input (STT) and Voice Model (TTS).
 * Supports adding, in-place replacing, reordering (up/down), and removing backups.
 */
@Composable
internal fun VoiceBackupChainCard(
    title: String,
    primaryRef: VoiceModelRef,
    fallbacks: List<VoiceModelRef>,
    options: List<VoiceOption>,
    snapshot: VoiceCatalogSnapshot,
    onEnsureInCatalog: (VoiceOption) -> VoiceModelRef,
    onUpdateFallbacks: (List<VoiceModelRef>) -> Unit,
) {
    val c = MikaTheme.colors
    val maxBackups = VoiceCatalogStore.MAX_VOICE_FALLBACKS
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    var pickingSlot by remember { mutableStateOf<Int?>(null) }
    val primaryName = snapshot.nameOf(primaryRef) ?: primaryRef.modelId

    GroupCard {
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
                    .background(if (fallbacks.isNotEmpty()) c.accent.copy(alpha = 0.12f) else c.neutralSoft, MikaShapes.Tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MikaIcons.Refresh, contentDescription = null, tint = if (fallbacks.isNotEmpty()) c.accent else c.ink3, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        title,
                        style = MikaType.BodyStrong,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (fallbacks.isNotEmpty()) {
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
                val preview = if (fallbacks.isEmpty()) {
                    stringResource(R.string.model_fallback_empty_preview)
                } else {
                    (listOf(primaryName) + fallbacks.map { snapshot.nameOf(it) ?: it.modelId }).joinToString(" -> ")
                }
                Text(
                    preview,
                    Modifier.padding(top = 2.dp),
                    style = MikaType.Caption,
                    color = c.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                MikaIcons.Chevron,
                contentDescription = null,
                tint = c.ink3,
                modifier = Modifier.size(16.dp).rotate(if (expanded) 90f else 0f),
            )
        }

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
                // Primary row
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(26.dp).background(c.accent.copy(alpha = 0.14f), MikaShapes.Tile), contentAlignment = Alignment.Center) {
                        Text("1", style = MikaType.Caption.copy(fontWeight = FontWeight.Bold), color = c.accent)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.model_fallback_primary_badge), style = MikaType.Hint, color = c.accent)
                        Text(primaryName, style = MikaType.BodyStrong, fontFamily = FontFamily.Monospace, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                fallbacks.forEachIndexed { slot, ref ->
                    Hairline(start = 14.dp)
                    val label = snapshot.nameOf(ref) ?: ref.modelId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { pickingSlot = slot }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(26.dp).background(c.surface, MikaShapes.Tile), contentAlignment = Alignment.Center) {
                            Text("${slot + 2}", style = MikaType.Caption.copy(fontWeight = FontWeight.Bold), color = c.ink2)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.model_fallback_backup_badge, slot + 1), style = MikaType.Hint, color = c.ink3)
                            Text(label, style = MikaType.BodyStrong, fontFamily = FontFamily.Monospace, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (fallbacks.size > 1) {
                            VoiceFallbackIconBtn(MikaIcons.Up, slot > 0) {
                                val next = fallbacks.toMutableList()
                                val item = next.removeAt(slot)
                                next.add(slot - 1, item)
                                onUpdateFallbacks(next)
                            }
                            VoiceFallbackIconBtn(MikaIcons.Down, slot < fallbacks.lastIndex) {
                                val next = fallbacks.toMutableList()
                                val item = next.removeAt(slot)
                                next.add(slot + 1, item)
                                onUpdateFallbacks(next)
                            }
                        }
                        VoiceFallbackIconBtn(MikaIcons.Close, true) {
                            onUpdateFallbacks(fallbacks.toMutableList().apply { removeAt(slot) })
                        }
                    }
                }

                if (fallbacks.size < maxBackups) {
                    Hairline(start = 14.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { pickingSlot = fallbacks.size }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(26.dp).background(c.accent.copy(alpha = 0.08f), MikaShapes.Tile), contentAlignment = Alignment.Center) {
                            Icon(MikaIcons.Plus, null, tint = c.accent, modifier = Modifier.size(14.dp))
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

    val slot = pickingSlot
    if (slot != null) {
        val candidates = options.filter { it.ref != primaryRef }
        val actions = candidates.map { opt ->
            val src = when (val s = opt.source) {
                VoiceSource.Device -> stringResource(R.string.voice_source_device)
                is VoiceSource.ChatAccount -> s.name
                is VoiceSource.Service -> s.name
            }
            SheetAction("${voiceModelDisplayName(opt.model)} · $src") {
                val chosenRef = onEnsureInCatalog(opt)
                val next = fallbacks.toMutableList()
                val existingIdx = next.indexOf(chosenRef)
                if (existingIdx >= 0 && existingIdx != slot) {
                    next.removeAt(existingIdx)
                    next.add(minOf(slot, next.size), chosenRef)
                } else if (slot in next.indices) {
                    next[slot] = chosenRef
                } else {
                    next.add(chosenRef)
                }
                onUpdateFallbacks(next)
            }
        }
        ActionSheet(
            title = title,
            actions = actions,
        ) { pickingSlot = null }
    }
}

@Composable
private fun VoiceFallbackIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        Icon(icon, contentDescription = null, tint = c.ink3, modifier = Modifier.size(15.dp))
    }
}
