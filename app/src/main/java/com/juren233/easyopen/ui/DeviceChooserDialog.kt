package com.juren233.easyopen.ui

import com.juren233.easyopen.shared.resources.EasyOpenStrings


import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.juren233.easyopen.data.DeviceProfile
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun DeviceChooserDialog(
    devices: List<DeviceProfile>,
    activeAddress: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onAddDevice: () -> Unit,
) {
    WindowDialog(
        title = stringResource(EasyOpenStrings.switch_opener_dialog_title),
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column {
            devices.forEach { device ->
                ArrowPreference(
                    title = device.name,
                    summary = if (device.address.equals(activeAddress, ignoreCase = true)) {
                        stringResource(EasyOpenStrings.current_device_summary, device.address)
                    } else {
                        device.address
                    },
                    onClick = { onSelect(device.address) },
                )
            }
            ArrowPreference(
                title = stringResource(EasyOpenStrings.add_opener_title),
                summary = stringResource(EasyOpenStrings.add_opener_dialog_summary),
                onClick = onAddDevice,
            )
        }
    }
}
