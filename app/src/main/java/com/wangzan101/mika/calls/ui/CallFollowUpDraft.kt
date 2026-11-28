package com.wangzan101.mika.calls.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.calls.CallActionResult
import com.wangzan101.mika.calls.CallHistory
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.MikaDialog
import com.wangzan101.mika.ui.components.MikaTextField
import com.wangzan101.mika.ui.theme.MikaTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A local draft only: no contact lookup, WeChat launch or send action. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CallFollowUpDraft(
    recordId: String,
    draft: String?,
    onSave: (suspend (String) -> CallActionResult)?,
) {
    val context = LocalContext.current
    val title = stringResource(R.string.calls_draft_title)
    val copied = stringResource(R.string.calls_draft_copied)
    val saveFailed = stringResource(R.string.calls_draft_save_failed)
    var editing by rememberSaveable(recordId) { mutableStateOf(false) }
    var text by rememberSaveable(recordId) { mutableStateOf(draft.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val c = MikaTheme.colors

    if (draft != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.fill)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = c.ink2)
            SelectionContainer {
                Text(draft, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.testTag("call-draft-text"))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MikaButton(
                    text = stringResource(R.string.calls_draft_copy),
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)?.let { clipboard ->
                            clipboard.setPrimaryClip(ClipData.newPlainText(title, draft))
                            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                        }
                    },
                    kind = ButtonKind.Tonal,
                    size = ButtonSize.Small,
                    modifier = Modifier.testTag("call-draft-copy"),
                )
                if (onSave != null) {
                    MikaButton(
                        text = stringResource(R.string.calls_draft_edit),
                        onClick = { text = draft; error = false; editing = true },
                        kind = ButtonKind.Text,
                        size = ButtonSize.Small,
                        modifier = Modifier.testTag("call-draft-edit"),
                    )
                }
            }
        }
    } else if (onSave != null) {
        MikaButton(
            text = stringResource(R.string.calls_draft_create),
            onClick = { text = ""; error = false; editing = true },
            kind = ButtonKind.Tonal,
            size = ButtonSize.Small,
            modifier = Modifier.fillMaxWidth().testTag("call-draft-create"),
        )
    }

    if (editing && onSave != null) MikaDialog(
        onDismissRequest = { if (!saving) editing = false },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MikaTextField(
                    value = text,
                    onValueChange = { if (it.length <= CallHistory.MAX_DRAFT_LENGTH) { text = it; error = false } },
                    enabled = !saving,
                    minLines = 3,
                    maxLines = 8,
                    label = { Text(title) },
                    placeholder = { Text(stringResource(R.string.calls_draft_placeholder)) },
                    supportingText = { Text("${text.length}/${CallHistory.MAX_DRAFT_LENGTH}") },
                    modifier = Modifier.fillMaxWidth().testTag("call-draft-input"),
                )
                if (error) Text(saveFailed, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && text.isNotBlank(),
                modifier = Modifier.testTag("call-draft-save"),
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            val result = onSave(text.trim())
                            error = !result.ok
                            if (result.ok) editing = false
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            error = true
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.calls_save)) }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = { editing = false }) {
                Text(stringResource(R.string.calls_cancel))
            }
        },
    )
}
