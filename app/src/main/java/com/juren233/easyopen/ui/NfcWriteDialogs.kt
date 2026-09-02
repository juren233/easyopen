package com.juren233.easyopen.ui

import com.juren233.easyopen.shared.resources.EasyOpenStrings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.juren233.easyopen.nfc.NfcWriteRequest
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * NFC write dialogs are rendered above the navigation host so their state is
 * not trapped inside a remembered Navigation 3 entry.
 */
@Composable
internal fun NfcWriteDialogs(
    waiting: Boolean,
    request: NfcWriteRequest?,
    awaitingTag: Boolean,
    writing: Boolean,
    onChoice: (Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    if (waiting) {
        WindowDialog(
            title = stringResource(EasyOpenStrings.nfc_write_waiting_title),
            show = true,
            onDismissRequest = onCancel,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixText(text = stringResource(EasyOpenStrings.nfc_write_waiting_description))
                MiuixTextButton(
                    text = stringResource(EasyOpenStrings.cancel),
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (awaitingTag) {
        WindowDialog(
            title = stringResource(EasyOpenStrings.nfc_write_reconnect_title),
            show = true,
            onDismissRequest = onCancel,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixText(text = stringResource(EasyOpenStrings.nfc_write_reconnect_description))
                MiuixTextButton(
                    text = stringResource(EasyOpenStrings.cancel),
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (!awaitingTag) request?.let { writeRequest ->
        val originalRecordCount = writeRequest.originalMessage?.records?.size ?: 0
        WindowDialog(
            title = stringResource(EasyOpenStrings.nfc_write_choice_title),
            show = true,
            onDismissRequest = onCancel,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixText(
                    text = if (originalRecordCount > 0) {
                        stringResource(EasyOpenStrings.nfc_write_choice_description, originalRecordCount)
                    } else {
                        stringResource(EasyOpenStrings.nfc_write_choice_empty)
                    },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MiuixTextButton(
                        text = stringResource(EasyOpenStrings.nfc_write_without_original),
                        onClick = { onChoice(false) },
                        modifier = Modifier.weight(1f),
                    )
                    MiuixTextButton(
                        text = stringResource(EasyOpenStrings.nfc_write_preserve_original),
                        onClick = { onChoice(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }

    if (writing) {
        WindowDialog(
            title = stringResource(EasyOpenStrings.nfc_write_in_progress_title),
            show = true,
            onDismissRequest = {},
        ) {
            MiuixText(text = stringResource(EasyOpenStrings.nfc_write_in_progress_description))
        }
    }
}
