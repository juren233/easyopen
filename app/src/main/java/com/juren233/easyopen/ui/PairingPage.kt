package com.juren233.easyopen.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.juren233.easyopen.data.DeviceProfile
import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding
import com.juren233.easyopen.shared.platform.EasyOpenBlePort
import com.juren233.easyopen.shared.ui.PairingPageContent

/** Android host for the shared pairing flow. */
@Composable
internal fun PairingPage(
    blePort: EasyOpenBlePort,
    existingDeviceCount: Int,
    onOpenBluetoothSettings: () -> Unit,
    onOpenScanner: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    onPaired: (DeviceProfile) -> Unit,
    initialProfile: CoreDeviceProfile? = null,
) {
    val snapshot by blePort.state.collectAsState()
    val controller = (blePort as? com.juren233.easyopen.ble.AndroidBlePort)
    val bluetoothEnabled = controller?.isBluetoothEnabled() ?: true

    PairingPageContent(
        existingDeviceCount = existingDeviceCount,
        snapshot = snapshot,
        bluetoothEnabled = bluetoothEnabled && snapshot.bluetoothAvailable,
        onBack = onCancel,
        onOpenBluetoothSettings = onOpenBluetoothSettings,
        onOpenScanner = onOpenScanner,
        onStartScan = blePort::startScan,
        onStopScan = blePort::stopScan,
        onPairRequested = { binding, profile ->
            blePort.stopScan()
            blePort.pair(binding, profile)
        },
        initialProfile = initialProfile,
        onPaired = { binding, profile ->
            val address = (binding as? DeviceBinding.AndroidMac)?.address ?: return@PairingPageContent
            onPaired(profile.toAndroidProfile(address))
        },
    )
}

private fun CoreDeviceProfile.toAndroidProfile(address: String): DeviceProfile = DeviceProfile(
    name = name,
    address = address,
    password = password,
    attribute = attribute,
    openTimeMs = openTimeMs,
    waitTimeMs = waitTimeMs,
    closeTimeMs = closeTimeMs,
    batteryLevel = batteryLevel,
    hardwareMac = hardwareMac,
)
