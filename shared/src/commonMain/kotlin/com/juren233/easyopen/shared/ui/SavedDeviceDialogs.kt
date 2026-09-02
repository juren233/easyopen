package com.juren233.easyopen.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juren233.easyopen.shared.state.EasyOpenSavedDevice
import com.juren233.easyopen.shared.state.displayIdentifier
import com.juren233.easyopen.shared.state.savedDeviceIdentityKeys
import com.juren233.easyopen.shared.state.selectedSavedDevices
import easyopen.shared.generated.resources.Res
import easyopen.shared.generated.resources.add_opener_dialog_summary
import easyopen.shared.generated.resources.add_opener_title
import easyopen.shared.generated.resources.current_device_summary
import easyopen.shared.generated.resources.generate_share_qr
import easyopen.shared.generated.resources.select_all
import easyopen.shared.generated.resources.share_opener_title
import easyopen.shared.generated.resources.switch_opener_dialog_title
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowDialog

/** Shared saved-device chooser used by Android and iOS home pages. */
@Composable
fun SavedDeviceChooserDialog(
    devices: List<EasyOpenSavedDevice>,
    activeIdentifier: String,
    onDismiss: () -> Unit,
    onSelect: (EasyOpenSavedDevice) -> Unit,
    onAddDevice: () -> Unit,
) {
    WindowDialog(
        title = stringResource(Res.string.switch_opener_dialog_title),
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column {
            devices.forEach { device ->
                val identifier = device.binding.displayIdentifier()
                ArrowPreference(
                    title = device.profile.name,
                    summary = if (identifier.equals(activeIdentifier, ignoreCase = true)) {
                        stringResource(Res.string.current_device_summary, identifier)
                    } else {
                        identifier
                    },
                    onClick = { onSelect(device) },
                )
            }
            ArrowPreference(
                title = stringResource(Res.string.add_opener_title),
                summary = stringResource(Res.string.add_opener_dialog_summary),
                onClick = onAddDevice,
            )
        }
    }
}

/** Shared multi-device share chooser; QR rendering remains platform-owned. */
@Composable
fun SavedDeviceShareDialog(
    devices: List<EasyOpenSavedDevice>,
    selectedIdentifiers: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (List<EasyOpenSavedDevice>) -> Unit,
) {
    val allIdentifiers = savedDeviceIdentityKeys(devices)
    val selectedDevices = selectedSavedDevices(devices, selectedIdentifiers)
    val allSelected = devices.isNotEmpty() && selectedDevices.size == devices.size

    WindowDialog(
        title = stringResource(Res.string.share_opener_title),
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SwitchPreference(
                title = stringResource(Res.string.select_all),
                checked = allSelected,
                onCheckedChange = { checked ->
                    onSelectionChange(if (checked) allIdentifiers else emptySet())
                },
            )
            LazyColumn(modifier = Modifier.size(width = 300.dp, height = 240.dp)) {
                items(devices) { device ->
                    val identifier = device.binding.displayIdentifier()
                    val identityKey = identifier.trim().uppercase()
                    SwitchPreference(
                        title = device.profile.name,
                        summary = identifier,
                        checked = identityKey in selectedIdentifiers.map(String::uppercase),
                        onCheckedChange = { checked ->
                            val normalizedSelection = selectedIdentifiers.map(String::uppercase).toSet()
                            onSelectionChange(
                                if (checked) normalizedSelection + identityKey
                                else normalizedSelection - identityKey,
                            )
                        },
                    )
                }
            }
            MiuixTextButton(
                text = stringResource(Res.string.generate_share_qr),
                onClick = { onConfirm(selectedDevices) },
                enabled = selectedDevices.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
