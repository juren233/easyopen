package com.juren233.easyopen.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeUIViewController
import com.juren233.easyopen.data.AppSettings
import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding
import com.juren233.easyopen.shared.navigation.EasyOpenNavigator
import com.juren233.easyopen.shared.navigation.EasyOpenRoute
import com.juren233.easyopen.shared.platform.IosAvailableUpdate
import com.juren233.easyopen.shared.platform.IosCoreBluetoothPort
import com.juren233.easyopen.shared.platform.IosDocumentTransferPresenter
import com.juren233.easyopen.shared.platform.IosUpdateChecker
import com.juren233.easyopen.shared.state.EasyOpenSavedDevice
import com.juren233.easyopen.shared.state.HomeDeviceSnapshot
import com.juren233.easyopen.shared.state.HomePageSnapshot
import com.juren233.easyopen.shared.state.HomeUpdateNotice
import com.juren233.easyopen.shared.state.activeSavedDevice
import com.juren233.easyopen.shared.state.displayIdentifier
import com.juren233.easyopen.shared.state.isUsable
import com.juren233.easyopen.shared.state.upsertSavedDevice
import com.juren233.easyopen.shared.storage.IosDeviceStore
import com.juren233.easyopen.shared.storage.IosSettingsStore
import com.juren233.easyopen.shared.transfer.EasyOpenBackupCodec
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
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
fun MainViewController(): UIViewController {
    val controller = ComposeUIViewController(
        configure = { parallelRendering = false },
    ) {
        IosRootContent()
    }
    IosDocumentTransferPresenter.attachHostViewController(controller)
    return controller
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
    var availableUpdate by remember { mutableStateOf<IosAvailableUpdate?>(null) }
    var pendingImportedProfiles by remember { mutableStateOf<List<CoreDeviceProfile>>(emptyList()) }
    val initialRoute = if (savedDevices.isEmpty()) EasyOpenRoute.AddDevice else EasyOpenRoute.Home
    val backStack = remember { mutableStateListOf<androidx.navigation3.runtime.NavKey>(initialRoute) }
    val navigator = remember { EasyOpenNavigator(backStack) }
    val currentRoute = backStack.lastOrNull() ?: EasyOpenRoute.Home
    val activeDevice = activeSavedDevice(savedDevices, activeIdentifier)
    val effectiveBinding = activeDevice?.binding ?: DeviceBinding.IosPeripheral("")
    val effectiveProfile = activeDevice?.profile ?: CoreDeviceProfile()

    LaunchedEffect(Unit) {
        val currentVersionCode = NSBundle.mainBundle
            .objectForInfoDictionaryKey("CFBundleVersion")
            ?.toString()
            ?.toLongOrNull()
            ?: return@LaunchedEffect
        availableUpdate = IosUpdateChecker.findUpdate(currentVersionCode)
    }

    DisposableEffect(currentRoute) {
        if (currentRoute != EasyOpenRoute.AddDevice) blePort.stopScan()
        onDispose { blePort.stopScan() }
    }
    LaunchedEffect(currentRoute, activeIdentifier, bleSnapshot.bluetoothAvailable) {
        if (currentRoute == EasyOpenRoute.Home && effectiveBinding.isUsable()) {
            blePort.connect(effectiveBinding, effectiveProfile)
        }
    }

    when {
        currentRoute == EasyOpenRoute.Settings -> {
            SettingsPageContent(
                settings = appSettings,
                onBack = navigator::pop,
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
                onBackupRequested = {
                    IosDocumentTransferPresenter.presentBackupExport(
                        EasyOpenBackupCodec.encode(savedDevices.map { it.profile }, appSettings),
                    )
                },
                onRestoreRequested = {
                    IosDocumentTransferPresenter.presentBackupImport { raw ->
                        val restored = raw?.let(EasyOpenBackupCodec::decode)
                        if (restored == null) {
                            if (raw != null) {
                                IosDocumentTransferPresenter.presentError("无法读取 EasyOpen 备份文件")
                            }
                            return@presentBackupImport
                        }
                        onSettingsChange(restored.settings)
                        pendingImportedProfiles = restored.profiles
                        navigator.replace(EasyOpenRoute.AddDevice)
                    }
                },
                showBackupActions = true,
            )
        }

        currentRoute == EasyOpenRoute.AddDevice -> {
            val importedProfile = pendingImportedProfiles.firstOrNull()
            key(
                importedProfile?.let {
                    "${it.name}:${it.password}:${it.openTimeMs}:${it.waitTimeMs}:${it.closeTimeMs}"
                } ?: "manual-pairing",
            ) {
                PairingPageContent(
                    existingDeviceCount = savedDevices.size,
                    pairedDevices = savedDevices,
                    initialProfile = importedProfile,
                    onSelectPairedDevice = { selected ->
                        pendingImportedProfiles = emptyList()
                        activeIdentifier = selected.binding.displayIdentifier()
                        IosDeviceStore.save(defaults, savedDevices, activeIdentifier)
                        navigator.replace(EasyOpenRoute.Home)
                    },
                    snapshot = bleSnapshot,
                    bluetoothEnabled = bleSnapshot.bluetoothAvailable,
                    onBack = if (backStack.size > 1) navigator::pop else null,
                    onOpenBluetoothSettings = {
                        NSURL.URLWithString("app-settings:")?.let { url ->
                            UIApplication.sharedApplication.openURL(url)
                        }
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
                        pendingImportedProfiles = pendingImportedProfiles.drop(1)
                        if (pendingImportedProfiles.isEmpty()) {
                            navigator.replace(EasyOpenRoute.Home)
                        } else {
                            navigator.replace(EasyOpenRoute.AddDevice)
                        }
                    },
                )
            }
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
                    availableUpdate = availableUpdate?.let {
                        HomeUpdateNotice(it.displayVersion)
                    },
                ),
                onOpenScanner = { navigator.navigate(EasyOpenRoute.AddDevice) },
                onOpenSettings = { navigator.navigate(EasyOpenRoute.Settings) },
                onShareRequested = { /* iOS QR export is the next platform adapter. */ },
                onSwitchOpener = { navigator.navigate(EasyOpenRoute.AddDevice) },
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
                onUpdateRequested = {
                    availableUpdate?.releaseUrl?.let { releaseUrl ->
                        NSURL.URLWithString(releaseUrl)?.let { url ->
                            UIApplication.sharedApplication.openURL(url)
                        }
                    }
                },
                showScannerAction = false,
                showShareAction = false,
                showNfcAction = false,
            )
        }
    }
}
