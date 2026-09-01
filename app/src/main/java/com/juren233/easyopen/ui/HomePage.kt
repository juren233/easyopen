package com.juren233.easyopen.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.juren233.easyopen.data.DeviceProfile
import com.juren233.easyopen.data.DeviceStore
import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding
import com.juren233.easyopen.shared.platform.EasyOpenBlePort
import com.juren233.easyopen.shared.state.HomeDeviceSnapshot
import com.juren233.easyopen.shared.state.HomePageSnapshot
import com.juren233.easyopen.shared.state.HomeUpdateNotice
import com.juren233.easyopen.shared.state.displayIdentifier
import com.juren233.easyopen.shared.ui.HomePageContent
import com.juren233.easyopen.utils.UpdateData

@Composable
internal fun HomePage(
    blePort: EasyOpenBlePort,
    devices: List<DeviceProfile>,
    activeProfileState: State<DeviceProfile>,
    activeAddress: String,
    onActiveDeviceChange: (String) -> Unit,
    onAddDevice: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenSettings: () -> Unit,
    onProfileChange: (DeviceProfile) -> Unit,
    onNfcWriteRequested: () -> Unit,
) {
    val context = LocalContext.current
    val activeProfile by activeProfileState
    val bleSnapshot by blePort.state.collectAsState()
    val availableUpdate by UpdateData.availableUpdate.collectAsState()
    var showDeviceChooser by rememberSaveable { mutableStateOf(false) }
    var showShareChooser by rememberSaveable { mutableStateOf(false) }
    var shareDevices by remember { mutableStateOf<List<DeviceProfile>?>(null) }
    var shareSelection by remember { mutableStateOf<Set<String>>(emptySet()) }

    val binding = DeviceBinding.AndroidMac(DeviceStore.normalizeAddress(activeProfile.address))
    val coreProfile = activeProfile.toCoreProfile()

    fun showShareUi() {
        if (devices.size == 1) {
            shareDevices = devices
        } else {
            shareSelection = devices.map { DeviceStore.normalizeAddress(it.address) }.toSet()
            showShareChooser = true
        }
    }

    HomePageContent(
        snapshot = HomePageSnapshot(
            activeDevice = HomeDeviceSnapshot(
                id = binding.address,
                identifierLabel = binding.displayIdentifier(),
                profile = coreProfile,
            ),
            connectionStatus = bleSnapshot.connectionStatus,
            batteryLevel = bleSnapshot.batteryLevel(binding, activeProfile.batteryLevel),
            busy = bleSnapshot.busy,
            canUnlock = bleSnapshot.canUnlock(binding, coreProfile),
            availableUpdate = availableUpdate?.let { HomeUpdateNotice(it.displayVersion) },
        ),
        onOpenScanner = onOpenScanner,
        onOpenSettings = onOpenSettings,
        onShareRequested = ::showShareUi,
        onSwitchOpener = { showDeviceChooser = true },
        onUnlock = { blePort.unlock(binding, coreProfile) },
        onProfileChange = { next ->
            onProfileChange(next.toAndroidProfile(activeProfile.address))
        },
        onNfcWriteRequested = onNfcWriteRequested,
        onUpdateRequested = {
            availableUpdate?.releaseUrl?.let { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        },
    )

    if (showDeviceChooser) {
        DeviceChooserDialog(
            devices = devices,
            activeAddress = activeAddress,
            onDismiss = { showDeviceChooser = false },
            onSelect = { address ->
                showDeviceChooser = false
                onActiveDeviceChange(address)
            },
            onAddDevice = {
                showDeviceChooser = false
                onAddDevice()
            },
        )
    }
    if (showShareChooser) {
        ShareChooserDialog(
            devices = devices,
            selectedAddresses = shareSelection,
            onSelectionChange = { shareSelection = it },
            onDismiss = { showShareChooser = false },
            onConfirm = { selected ->
                showShareChooser = false
                shareDevices = selected
            },
        )
    }
    shareDevices?.let { selected ->
        ShareQrDialog(
            devices = selected,
            onDismiss = { shareDevices = null },
        )
    }
}

private fun DeviceProfile.toCoreProfile(): CoreDeviceProfile = CoreDeviceProfile(
    name = name,
    password = password,
    attribute = attribute,
    openTimeMs = openTimeMs,
    waitTimeMs = waitTimeMs,
    closeTimeMs = closeTimeMs,
    batteryLevel = batteryLevel,
)

private fun CoreDeviceProfile.toAndroidProfile(address: String): DeviceProfile = DeviceProfile(
    name = name,
    address = address,
    password = password,
    attribute = attribute,
    openTimeMs = openTimeMs,
    waitTimeMs = waitTimeMs,
    closeTimeMs = closeTimeMs,
    batteryLevel = batteryLevel,
)
