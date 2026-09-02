package com.juren233.easyopen.ui

import com.juren233.easyopen.shared.resources.EasyOpenStrings


import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.juren233.easyopen.data.DeviceProfile
import com.juren233.easyopen.data.DeviceStore
import com.juren233.easyopen.data.TransferCodec
import com.juren233.easyopen.transfer.QrTransfer
import androidx.compose.ui.graphics.asImageBitmap
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun ShareChooserDialog(
    devices: List<DeviceProfile>,
    selectedAddresses: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (List<DeviceProfile>) -> Unit,
) {
    val allSelected = devices.isNotEmpty() && selectedAddresses.size == devices.size
    WindowDialog(
        title = stringResource(EasyOpenStrings.share_opener_title),
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SwitchPreference(
                title = stringResource(EasyOpenStrings.select_all),
                checked = allSelected,
                onCheckedChange = { checked ->
                    onSelectionChange(
                        if (checked) devices.map { DeviceStore.normalizeAddress(it.address) }.toSet() else emptySet(),
                    )
                },
            )
            LazyColumn(modifier = Modifier.size(width = 300.dp, height = 240.dp)) {
                items(devices, key = { it.address }) { device ->
                    val address = DeviceStore.normalizeAddress(device.address)
                    SwitchPreference(
                        title = device.name,
                        summary = device.address,
                        checked = address in selectedAddresses,
                        onCheckedChange = { checked ->
                            onSelectionChange(
                                if (checked) selectedAddresses + address else selectedAddresses - address,
                            )
                        },
                    )
                }
            }
            MiuixTextButton(
                text = stringResource(EasyOpenStrings.generate_share_qr),
                onClick = {
                    onConfirm(devices.filter { DeviceStore.normalizeAddress(it.address) in selectedAddresses })
                },
                enabled = selectedAddresses.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
internal fun ShareQrDialog(
    devices: List<DeviceProfile>,
    onDismiss: () -> Unit,
) {
    val bitmap = androidx.compose.runtime.remember(devices) {
        QrTransfer.createBitmap(TransferCodec.encodeShare(devices))
    }
    WindowDialog(
        title = stringResource(EasyOpenStrings.share_qr_title),
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(EasyOpenStrings.share_qr_content_description),
                modifier = Modifier.size(260.dp),
            )
            MiuixText(
                text = stringResource(EasyOpenStrings.share_qr_summary, devices.size),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            MiuixTextButton(
                text = stringResource(EasyOpenStrings.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
