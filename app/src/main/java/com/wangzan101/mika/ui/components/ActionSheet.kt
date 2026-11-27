package com.wangzan101.mika.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.ui.theme.MikaShapes

/** 操作面板里的一项;[icon] 放在文字左边;[destructive] 为真时文字用出错红。 */
class SheetAction(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val trailingIcon: ImageVector? = null,
    val onTrailingClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

/**
 * 点一行后从底部升起的操作面板:顶上一行说明是在对什么操作,下面每项一行。
 * 点任何一项都会先执行它、再关掉面板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    title: String?,
    actions: List<SheetAction>,
    searchPlaceholder: String? = null,
    onDismiss: () -> Unit,
) {
    val c = MikaTheme.colors
    var query by remember { mutableStateOf("") }
    val shown = remember(actions, query, searchPlaceholder) {
        if (searchPlaceholder == null || query.isBlank()) actions
        else actions.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = MikaShapes.SheetTop,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            if (!title.isNullOrEmpty()) {
                Text(
                    title, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MikaType.Caption, color = c.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (searchPlaceholder != null) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = searchPlaceholder,
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                shown.forEachIndexed { i, a ->
                    ListRow(
                        a.icon, a.label, first = i == 0,
                        titleColor = if (a.destructive) c.bad else c.ink,
                        onClick = { a.onClick(); onDismiss() },
                        trailing = if (a.trailingIcon != null && a.onTrailingClick != null) {
                            {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(MikaShapes.Pill)
                                        .clickable { a.onTrailingClick.invoke() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(a.trailingIcon, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                                }
                            }
                        } else {
                            {}
                        },
                    )
                }
            }
        }
    }
}
