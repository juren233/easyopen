package com.juren233.easyopen.shared.ui

import com.juren233.easyopen.shared.text.EasyOpenPlatformText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.juren233.easyopen.shared.platform.IosNfcPresenter
import com.juren233.easyopen.shared.platform.IosUpdateChecker
import com.juren233.easyopen.shared.state.EasyOpenSavedDevice
import com.juren233.easyopen.shared.state.HomeDeviceSnapshot
import com.juren233.easyopen.shared.state.HomePageSnapshot
import com.juren233.easyopen.shared.state.HomeUpdateNotice
import com.juren233.easyopen.shared.state.activeSavedDevice
import com.juren233.easyopen.shared.state.displayIdentifier
import com.juren233.easyopen.shared.state.isUsable
import com.juren233.easyopen.shared.state.savedDeviceIdentityKeys
import com.juren233.easyopen.shared.state.upsertSavedDevice
import com.juren233.easyopen.shared.storage.IosDeviceStore
import com.juren233.easyopen.shared.storage.IosSettingsStore
import com.juren233.easyopen.shared.transfer.EasyOpenBackupCodec
import com.juren233.easyopen.shared.transfer.EasyOpenQrCodec
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
    var showDeviceChooser by rememberSaveable { mutableStateOf(false) }
    var showShareChooser by rememberSaveable { mutableStateOf(false) }
    var shareSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    val initialRoute = if (savedDevices.isEmpty()) EasyOpenRoute.AddDevice else EasyOpenRoute.Home
    val backStack = remember { mutableStateListOf<androidx.navigation3.runtime.NavKey>(initialRoute) }
    val navigator = remember { EasyOpenNavigator(backStack) }
    val currentRoute = backStack.lastOrNull() ?: EasyOpenRoute.Home
    val activeDevice = activeSavedDevice(savedDevices, activeIdentifier)
    val effectiveBinding = activeDevice?.binding ?: DeviceBinding.IosPeripheral("")
    val effectiveProfile = activeDevice?.profile ?: CoreDeviceProfile()
    var autoUnlockEvaluated by remember(currentRoute, activeIdentifier) { mutableStateOf(false) }

    fun requestQrImport() {
        IosDocumentTransferPresenter.presentQrScanner { payload ->
            val profiles = EasyOpenQrCodec.decode(payload)
            if (profiles.isNullOrEmpty()) {
                IosDocumentTransferPresenter.presentError(EasyOpenPlatformText.invalidQr)
            } else {
                pendingImportedProfiles = profiles
                navigator.navigate(EasyOpenRoute.AddDevice)
            }
        }
    }

    fun requestQrShare(devices: List<EasyOpenSavedDevice>) {
        if (devices.isEmpty()) return
        runCatching {
            EasyOpenQrCodec.encode(devices.map { it.profile })
        }.onSuccess { payload ->
            IosDocumentTransferPresenter.presentQrCode(
                title = EasyOpenPlatformText.shareOpenerTitle,
                payload = payload,
                summary = EasyOpenPlatformText.shareQrSummary(devices.size),
            )
        }.onFailure {
            IosDocumentTransferPresenter.presentError(EasyOpenPlatformText.qrGenerationRequiresSixDigitPassword)
        }
    }

    LaunchedEffect(Unit) {
        val currentVersionCode = NSBundle.mainBundle
            .objectForInfoDictionaryKey("CFBundleVersion")
            ?.toString()
            ?.toLongOrNull()
            ?: return@LaunchedEffect
        availableUpdate = IosUpdateChecker.findUpdate(currentVersionCode)
    }

    DisposableEffect(currentRoute, appSettings.autoConnectEnabled) {
        if (currentRoute != EasyOpenRoute.Home && currentRoute != EasyOpenRoute.AddDevice) {
            blePort.stopScan()
        }
        onDispose { blePort.stopScan() }
    }
    LaunchedEffect(
        currentRoute,
        activeIdentifier,
        bleSnapshot.bluetoothAvailable,
        bleSnapshot.discoveredDevices,
        appSettings.autoConnectEnabled,
        appSettings.autoConnectRssiThreshold,
    ) {
        if (
            currentRoute != EasyOpenRoute.Home ||
            !appSettings.autoConnectEnabled ||
            !effectiveBinding.isUsable()
        ) {
            if (currentRoute == EasyOpenRoute.Home && !appSettings.autoConnectEnabled) {
                blePort.stopScan()
            }
            return@LaunchedEffect
        }
        val expectedHardwareMac = effectiveProfile.hardwareMac
        val nearbySavedDevice = bleSnapshot.discoveredDevices.firstOrNull { discovered ->
            discovered.binding == effectiveBinding ||
                (expectedHardwareMac != null &&
                    discovered.hardwareMac.equals(expectedHardwareMac, ignoreCase = true))
        }
        if (nearbySavedDevice != null && nearbySavedDevice.rssi >= appSettings.autoConnectRssiThreshold) {
            blePort.stopScan()
            blePort.connect(effectiveBinding, effectiveProfile)
        } else {
            blePort.startScan()
        }
    }
    LaunchedEffect(
        currentRoute,
        activeIdentifier,
        appSettings.autoUnlockOnAppOpen,
        effectiveProfile.password,
    ) {
        if (currentRoute != EasyOpenRoute.Home || autoUnlockEvaluated) return@LaunchedEffect
        autoUnlockEvaluated = true
        if (appSettings.autoUnlockOnAppOpen && effectiveBinding.isUsable()) {
            blePort.unlock(effectiveBinding, effectiveProfile)
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
                                IosDocumentTransferPresenter.presentError(EasyOpenPlatformText.backupUnreadable)
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
                    "${it.name}:${it.password}:${it.openTimeMs}:${it.waitTimeMs}:${it.closeTimeMs}:${it.hardwareMac}"
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
                    onOpenScanner = ::requestQrImport,
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
                            ?: EasyOpenPlatformText.unpairedOpener,
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
                    message = bleSnapshot.message,
                ),
                onOpenScanner = ::requestQrImport,
                onOpenSettings = { navigator.navigate(EasyOpenRoute.Settings) },
                onShareRequested = {
                    if (savedDevices.size == 1) {
                        requestQrShare(savedDevices)
                    } else {
                        shareSelection = savedDeviceIdentityKeys(savedDevices)
                        showShareChooser = true
                    }
                },
                onSwitchOpener = { showDeviceChooser = true },
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
                onNfcWriteRequested = {
                    IosNfcPresenter.presentWrite { success, message ->
                        if (!success && message != null) {
                            IosDocumentTransferPresenter.presentError(message)
                        }
                    }
                },
                onNfcReadRequested = {
                    var validUnlockPayload = false
                    IosNfcPresenter.presentRead(
                        onPayload = { validUnlockPayload = true },
                        onFinished = { success, message ->
                            when {
                                success && validUnlockPayload && effectiveBinding.isUsable() -> {
                                    blePort.unlock(effectiveBinding, effectiveProfile)
                                }
                                !success && message != null -> {
                                    IosDocumentTransferPresenter.presentError(message)
                                }
                            }
                        },
                    )
                },
                onUpdateRequested = {
                    availableUpdate?.releaseUrl?.let { releaseUrl ->
                        NSURL.URLWithString(releaseUrl)?.let { url ->
                            UIApplication.sharedApplication.openURL(url)
                        }
                    }
                },
                showScannerAction = true,
                showShareAction = true,
                showNfcAction = true,
                showNfcReadAction = true,
            )

            if (showDeviceChooser) {
                SavedDeviceChooserDialog(
                    devices = savedDevices,
                    activeIdentifier = activeIdentifier,
                    onDismiss = { showDeviceChooser = false },
                    onSelect = { selected ->
                        showDeviceChooser = false
                        activeIdentifier = selected.binding.displayIdentifier()
                        IosDeviceStore.save(defaults, savedDevices, activeIdentifier)
                    },
                    onAddDevice = {
                        showDeviceChooser = false
                        navigator.navigate(EasyOpenRoute.AddDevice)
                    },
                )
            }
            if (showShareChooser) {
                SavedDeviceShareDialog(
                    devices = savedDevices,
                    selectedIdentifiers = shareSelection,
                    onSelectionChange = { shareSelection = it },
                    onDismiss = { showShareChooser = false },
                    onConfirm = { selected ->
                        showShareChooser = false
                        requestQrShare(selected)
                    },
                )
            }
        }
    }
}
