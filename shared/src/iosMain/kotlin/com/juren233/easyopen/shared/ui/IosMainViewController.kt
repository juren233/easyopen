package com.juren233.easyopen.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeUIViewController
import com.juren233.easyopen.data.AppSettings
import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding
import com.juren233.easyopen.shared.platform.IosCoreBluetoothPort
import com.juren233.easyopen.shared.state.isUsable
import com.juren233.easyopen.shared.state.activeSavedDevice
import com.juren233.easyopen.shared.state.upsertSavedDevice
import com.juren233.easyopen.shared.storage.IosDeviceStore
import com.juren233.easyopen.shared.storage.IosSettingsStore
import com.juren233.easyopen.shared.state.HomeDeviceSnapshot
import com.juren233.easyopen.shared.state.HomePageSnapshot
import com.juren233.easyopen.shared.state.EasyOpenSavedDevice
import com.juren233.easyopen.shared.state.displayIdentifier
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

/**
 * iOS host entry point; the Swift shell embeds the shared Compose UI here.
 *
 * Keep rendering on the main thread for now. The first device reports from
 * iOS 27.0 showed an uncaught Kotlin exception on Compose's
 * `com.apple.root.utility-qos` render worker followed by SIGABRT during the
 * first frame. Disabling the separate render thread is the documented Compose
 * fallback for parallel-rendering issues; we can re-enable it after a clean
 * device smoke test on the target iOS versions.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun MainViewController(): UIViewController = ComposeUIViewController(
    configure = { parallelRendering = false },
) {
    IosRootContent()
}

@Composable
private fun IosRootContent() {
    val defaults = remember { NSUserDefaults.standardUserDefaults }
    var appSettings by remember { mutableStateOf(IosSettingsStore.load(defaults)) }

    EasyOpenTheme(
        themeMode = appSettings.themeMode,
        monetEnabled = appSettings.monetEnabled,
    ) {
        IosRootContentBody(
            defaults = defaults,
            appSettings = appSettings,
            onSettingsChange = {
                appSettings = it
                IosSettingsStore.save(defaults, it)
            },
        )
    }
}

@Composable
private fun IosRootContentBody(
    defaults: NSUserDefaults,
    appSettings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val blePort = remember { IosCoreBluetoothPort() }
    val bleSnapshot by blePort.state.collectAsState()
    var savedDevices by remember { mutableStateOf(IosDeviceStore.load(defaults)) }
    var activeIdentifier by remember {
        mutableStateOf(IosDeviceStore.activeIdentifier(defaults, savedDevices))
    }
    var showPairing by rememberSaveable { mutableStateOf(savedDevices.isEmpty()) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val activeDevice = activeSavedDevice(savedDevices, activeIdentifier)
    val effectiveBinding = activeDevice?.binding ?: DeviceBinding.IosPeripheral("")
    val effectiveProfile = activeDevice?.profile ?: CoreDeviceProfile()

    DisposableEffect(showPairing, showSettings) {
        if (showPairing && !showSettings) blePort.startScan() else blePort.stopScan()
        onDispose { blePort.stopScan() }
    }

    when {
        showSettings -> {
            SettingsPageContent(
                settings = appSettings,
                onBack = { showSettings = false },
                onThemeModeChange = { onSettingsChange(appSettings.copy(themeMode = it)) },
                onMonetChange = { onSettingsChange(appSettings.copy(monetEnabled = it)) },
                onAutoUnlockOnAppOpenChange = {
                    onSettingsChange(appSettings.copy(autoUnlockOnAppOpen = it))
                },
                onAutoConnectEnabledChange = {
                    onSettingsChange(appSettings.copy(autoConnectEnabled = it))
                },
                onAutoConnectRangeChange = {
                    onSettingsChange(appSettings.copy(autoConnectRange = it))
                },
                onCustomAutoConnectRssiChange = {
                    onSettingsChange(appSettings.copy(customAutoConnectRssi = it))
                },
                // File picker/backup codecs are platform work for the next
                // batch; keeping the shared surface wired avoids an iOS-only
                // settings implementation drifting from Android.
                onBackupRequested = {},
                onRestoreRequested = {},
            )
        }

        showPairing -> {
            PairingPageContent(
                existingDeviceCount = savedDevices.size,
                pairedDevices = savedDevices,
                onSelectPairedDevice = { selected ->
                    activeIdentifier = selected.binding.displayIdentifier()
                    IosDeviceStore.save(defaults, savedDevices, activeIdentifier)
                    showPairing = false
                },
                snapshot = bleSnapshot,
                bluetoothEnabled = bleSnapshot.bluetoothAvailable,
                onBack = { if (savedDevices.isNotEmpty()) showPairing = false },
                onOpenBluetoothSettings = {
                    // CoreBluetooth requests Bluetooth authorization when the
                    // central manager is first used; iOS has no Android-style
                    // Bluetooth settings intent to launch here.
                },
                onStartScan = blePort::startScan,
                onStopScan = blePort::stopScan,
                onPairRequested = { binding, pairingProfile ->
                    blePort.stopScan()
                    blePort.pair(binding, pairingProfile)
                },
                onPaired = { binding, pairedProfile ->
                    val iosBinding = binding as? DeviceBinding.IosPeripheral
                        ?: return@PairingPageContent
                    val nextDevice = EasyOpenSavedDevice(
                        binding = iosBinding,
                        profile = pairedProfile.normalized(),
                    )
                    savedDevices = upsertSavedDevice(savedDevices, nextDevice)
                    activeIdentifier = iosBinding.identifier
                    IosDeviceStore.save(defaults, savedDevices, activeIdentifier)
                    showPairing = false
                },
            )
        }

        else -> {
            HomePageContent(
                snapshot = HomePageSnapshot(
                    activeDevice = HomeDeviceSnapshot(
                        id = effectiveBinding.displayIdentifier(),
                        identifierLabel = effectiveBinding.displayIdentifier()
                            .takeIf(String::isNotBlank)
                            ?: "未配对开门器",
                        profile = effectiveProfile,
                    ),
                    connectionStatus = bleSnapshot.connectionStatus,
                    batteryLevel = bleSnapshot.batteryLevel(effectiveBinding, effectiveProfile.batteryLevel),
                    busy = bleSnapshot.busy,
                    canUnlock = effectiveBinding.isUsable() &&
                        bleSnapshot.canUnlock(effectiveBinding, effectiveProfile),
                ),
                onOpenScanner = { showPairing = true },
                onOpenSettings = { showSettings = true },
                onShareRequested = { /* iOS QR export is the next platform adapter. */ },
                onSwitchOpener = { showPairing = true },
                onUnlock = {
                    if (effectiveBinding.isUsable()) {
                        blePort.unlock(effectiveBinding, effectiveProfile)
                    }
                },
                onProfileChange = { updatedProfile ->
                    val normalized = updatedProfile.normalized()
                    savedDevices = savedDevices.map { saved ->
                        if (saved.binding.displayIdentifier().equals(activeIdentifier, ignoreCase = true)) {
                            saved.copy(profile = normalized)
                        } else {
                            saved
                        }
                    }
                    IosDeviceStore.save(defaults, savedDevices, activeIdentifier)
                },
                onNfcWriteRequested = { /* Core NFC is intentionally separate. */ },
                onUpdateRequested = { /* Release/update presentation is next. */ },
            )
        }
    }
}
