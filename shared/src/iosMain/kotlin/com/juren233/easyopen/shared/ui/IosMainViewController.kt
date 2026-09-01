package com.juren233.easyopen.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.juren233.easyopen.data.AppSettings
import com.juren233.easyopen.data.AutoConnectSettings
import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding
import com.juren233.easyopen.shared.platform.IosCoreBluetoothPort
import com.juren233.easyopen.shared.state.HomeDeviceSnapshot
import com.juren233.easyopen.shared.state.HomePageSnapshot
import com.juren233.easyopen.shared.state.displayIdentifier
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

/** iOS host entry point; the Swift shell embeds the shared Compose UI here. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    IosRootContent()
}

@Composable
private fun IosRootContent() {
    val defaults = remember { NSUserDefaults.standardUserDefaults }
    var appSettings by remember { mutableStateOf(loadIosSettings(defaults)) }

    EasyOpenTheme(
        themeMode = appSettings.themeMode,
        monetEnabled = appSettings.monetEnabled,
    ) {
        IosRootContentBody(
            defaults = defaults,
            appSettings = appSettings,
            onSettingsChange = {
                appSettings = it
                saveIosSettings(defaults, it)
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
    var profile by remember { mutableStateOf(loadIosProfile(defaults)) }
    var savedBinding by remember { mutableStateOf(loadIosBinding(defaults)) }
    var showPairing by rememberSaveable { mutableStateOf(savedBinding == null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

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
                existingDeviceCount = if (savedBinding == null) 0 else 1,
                snapshot = bleSnapshot,
                bluetoothEnabled = bleSnapshot.bluetoothAvailable,
                onBack = { if (savedBinding != null) showPairing = false },
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
                    savedBinding = iosBinding
                    profile = pairedProfile.normalized()
                    saveIosDevice(defaults, iosBinding, profile)
                    showPairing = false
                },
            )
        }

        else -> {
            val binding = savedBinding ?: (bleSnapshot.activeBinding as? DeviceBinding.IosPeripheral)
            val effectiveBinding = binding ?: DeviceBinding.IosPeripheral("")
            val effectiveProfile = profile
            HomePageContent(
                snapshot = HomePageSnapshot(
                    activeDevice = HomeDeviceSnapshot(
                        id = effectiveBinding.identifier,
                        identifierLabel = effectiveBinding.identifier
                            .takeIf(String::isNotBlank)
                            ?.let { effectiveBinding.displayIdentifier() }
                            ?: "未配对开门器",
                        profile = effectiveProfile,
                    ),
                    connectionStatus = bleSnapshot.connectionStatus,
                    batteryLevel = bleSnapshot.batteryLevel(effectiveBinding, effectiveProfile.batteryLevel),
                    busy = bleSnapshot.busy,
                    canUnlock = effectiveBinding.identifier.isNotBlank() &&
                        bleSnapshot.canUnlock(effectiveBinding, effectiveProfile),
                ),
                onOpenScanner = { showPairing = true },
                onOpenSettings = { showSettings = true },
                onShareRequested = { /* iOS QR export is the next platform adapter. */ },
                onSwitchOpener = { showPairing = true },
                onUnlock = {
                    if (effectiveBinding.identifier.isNotBlank()) {
                        blePort.unlock(effectiveBinding, effectiveProfile)
                    }
                },
                onProfileChange = {
                    profile = it.normalized()
                    savedBinding?.let { binding -> saveIosDevice(defaults, binding, profile) }
                },
                onNfcWriteRequested = { /* Core NFC is intentionally separate. */ },
                onUpdateRequested = { /* Release/update presentation is next. */ },
            )
        }
    }
}

private const val IOS_BINDING_KEY = "easyopen.ios.binding"
private const val IOS_PROFILE_PREFIX = "easyopen.ios.profile."
private const val IOS_SETTINGS_PREFIX = "easyopen.ios.settings."

private fun loadIosBinding(defaults: NSUserDefaults): DeviceBinding.IosPeripheral? =
    defaults.stringForKey(IOS_BINDING_KEY)
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { identifier -> DeviceBinding.IosPeripheral(identifier) }

private fun loadIosProfile(defaults: NSUserDefaults): CoreDeviceProfile = CoreDeviceProfile(
    name = defaults.stringForKey(IOS_PROFILE_PREFIX + "name") ?: "我的开门器",
    password = defaults.stringForKey(IOS_PROFILE_PREFIX + "password") ?: "",
    attribute = defaults.integerForKey(IOS_PROFILE_PREFIX + "attribute").toInt(),
    openTimeMs = defaults.integerForKey(IOS_PROFILE_PREFIX + "openTimeMs").toInt().takeIf { it > 0 } ?: 650,
    waitTimeMs = defaults.integerForKey(IOS_PROFILE_PREFIX + "waitTimeMs").toInt().takeIf { it > 0 } ?: 2_000,
    closeTimeMs = defaults.integerForKey(IOS_PROFILE_PREFIX + "closeTimeMs").toInt().takeIf { it > 0 } ?: 600,
).normalized()

private fun saveIosDevice(
    defaults: NSUserDefaults,
    binding: DeviceBinding.IosPeripheral,
    profile: CoreDeviceProfile,
) {
    val normalized = profile.normalized()
    defaults.setObject(binding.identifier, forKey = IOS_BINDING_KEY)
    defaults.setObject(normalized.name, forKey = IOS_PROFILE_PREFIX + "name")
    defaults.setObject(normalized.password, forKey = IOS_PROFILE_PREFIX + "password")
    defaults.setInteger(normalized.attribute.toLong(), forKey = IOS_PROFILE_PREFIX + "attribute")
    defaults.setInteger(normalized.openTimeMs.toLong(), forKey = IOS_PROFILE_PREFIX + "openTimeMs")
    defaults.setInteger(normalized.waitTimeMs.toLong(), forKey = IOS_PROFILE_PREFIX + "waitTimeMs")
    defaults.setInteger(normalized.closeTimeMs.toLong(), forKey = IOS_PROFILE_PREFIX + "closeTimeMs")
}

private fun loadIosSettings(defaults: NSUserDefaults): AppSettings = AppSettings(
    themeMode = defaults.integerForKey(IOS_SETTINGS_PREFIX + "themeMode").toInt().coerceIn(0, 2),
    monetEnabled = defaults.boolForKey(IOS_SETTINGS_PREFIX + "monetEnabled"),
    autoUnlockOnAppOpen = defaults.boolForKey(IOS_SETTINGS_PREFIX + "autoUnlockOnAppOpen"),
    autoConnectEnabled = defaults.objectForKey(IOS_SETTINGS_PREFIX + "autoConnectEnabled")
        ?.let { defaults.boolForKey(IOS_SETTINGS_PREFIX + "autoConnectEnabled") }
        ?: true,
    autoConnectRange = defaults.integerForKey(IOS_SETTINGS_PREFIX + "autoConnectRange")
        .toIntOrDefault(defaults, IOS_SETTINGS_PREFIX + "autoConnectRange", AutoConnectSettings.DEFAULT_RANGE),
    customAutoConnectRssi = defaults.integerForKey(IOS_SETTINGS_PREFIX + "customAutoConnectRssi")
        .toIntOrDefault(defaults, IOS_SETTINGS_PREFIX + "customAutoConnectRssi", AutoConnectSettings.DEFAULT_RSSI_THRESHOLD),
)

private fun saveIosSettings(defaults: NSUserDefaults, settings: AppSettings) {
    defaults.setInteger(settings.themeMode.coerceIn(0, 2).toLong(), forKey = IOS_SETTINGS_PREFIX + "themeMode")
    defaults.setBool(settings.monetEnabled, forKey = IOS_SETTINGS_PREFIX + "monetEnabled")
    defaults.setBool(settings.autoUnlockOnAppOpen, forKey = IOS_SETTINGS_PREFIX + "autoUnlockOnAppOpen")
    defaults.setBool(settings.autoConnectEnabled, forKey = IOS_SETTINGS_PREFIX + "autoConnectEnabled")
    defaults.setInteger(settings.autoConnectRange.toLong(), forKey = IOS_SETTINGS_PREFIX + "autoConnectRange")
    defaults.setInteger(settings.customAutoConnectRssi.toLong(), forKey = IOS_SETTINGS_PREFIX + "customAutoConnectRssi")
}


private fun Long.toIntOrDefault(defaults: NSUserDefaults, key: String, fallback: Int): Int =
    if (defaults.objectForKey(key) == null) fallback else toInt()
