package com.juren233.easyopen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.juren233.easyopen.ble.BleDoorController
import com.juren233.easyopen.shared.platform.EasyOpenBlePort
import com.juren233.easyopen.shared.navigation.EasyOpenNavigator
import com.juren233.easyopen.shared.navigation.EasyOpenRoute
import com.juren233.easyopen.data.AppSettings
import com.juren233.easyopen.data.DeviceProfile
import com.juren233.easyopen.data.TransferCodec
import com.juren233.easyopen.ui.HomePage
import com.juren233.easyopen.ui.PairingPage
import com.juren233.easyopen.ui.QrImportPage
import com.juren233.easyopen.ui.SettingsPage

@Composable
internal fun EasyOpenNavigation(
    controller: BleDoorController,
    blePort: EasyOpenBlePort,
    devices: List<DeviceProfile>,
    appSettings: AppSettings,
    appSettingsState: State<AppSettings>,
    onOpenBluetoothSettings: () -> Unit,
    activeProfileState: State<DeviceProfile>,
    activeAddress: String,
    onActiveDeviceChange: (String) -> Unit,
    onDevicePaired: (DeviceProfile) -> Unit,
    onProfileChange: (DeviceProfile) -> Unit,
    onImported: (List<DeviceProfile>) -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onNfcWriteRequested: () -> Unit,
    onRestore: (TransferCodec.BackupSnapshot) -> Unit,
) {
    val backStack = rememberNavBackStack(EasyOpenRoute.Home)
    val navigator = remember { EasyOpenNavigator(backStack) }
    val activeProfile by activeProfileState
    val currentRoute = backStack.lastOrNull()
    var autoOpenEvaluated by remember { mutableStateOf(false) }
    LaunchedEffect(
        currentRoute,
        activeProfile.address,
        appSettings.autoConnectEnabled,
        appSettings.autoConnectRange,
        appSettings.customAutoConnectRssi,
    ) {
        if (currentRoute == EasyOpenRoute.Home) {
            controller.startOpenerMonitoring(
                profile = activeProfile,
                autoConnectEnabled = appSettings.autoConnectEnabled,
                autoConnectRssiThreshold = appSettings.autoConnectRssiThreshold,
            )
            if (!autoOpenEvaluated) {
                autoOpenEvaluated = true
                if (appSettings.autoUnlockOnAppOpen) {
                    controller.unlock(activeProfile)
                }
            }
        } else {
            controller.stopOpenerMonitoring()
            controller.stopBatteryScan()
        }
    }

    val entryProvider = remember(
        backStack,
        blePort,
        devices,
        appSettingsState,
        activeProfileState,
        activeAddress,
        onActiveDeviceChange,
        onDevicePaired,
        onProfileChange,
        onImported,
        onSettingsChange,
        onNfcWriteRequested,
        onRestore,
    ) {
        entryProvider<NavKey> {
            entry<EasyOpenRoute.Home> {
                HomePage(
                    blePort = blePort,
                    devices = devices,
                    activeProfileState = activeProfileState,
                    activeAddress = activeAddress,
                    onActiveDeviceChange = onActiveDeviceChange,
                    onAddDevice = { navigator.navigate(EasyOpenRoute.AddDevice) },
                    onOpenScanner = { navigator.navigate(EasyOpenRoute.ScanImport) },
                    onOpenSettings = { navigator.navigate(EasyOpenRoute.Settings) },
                    onProfileChange = onProfileChange,
                    onNfcWriteRequested = onNfcWriteRequested,
                )
            }
            entry<EasyOpenRoute.AddDevice> {
                PairingPage(
                    blePort = blePort,
                    existingDeviceCount = devices.size,
                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                    onOpenScanner = { navigator.navigate(EasyOpenRoute.ScanImport) },
                    onCancel = { navigator.pop() },
                    onPaired = { profile ->
                        onDevicePaired(profile)
                        navigator.pop()
                    },
                )
            }
            entry<EasyOpenRoute.ScanImport> {
                QrImportPage(
                    onBack = { navigator.pop() },
                    onImported = { imported ->
                        onImported(imported)
                        val returnToAddDevice = backStack.getOrNull(backStack.lastIndex - 1) == EasyOpenRoute.AddDevice
                        navigator.pop()
                        if (returnToAddDevice) navigator.pop()
                    },
                )
            }
            entry<EasyOpenRoute.Settings> {
                val currentSettings by appSettingsState
                SettingsPage(
                    devices = devices,
                    activeAddress = activeAddress,
                    settings = currentSettings,
                    onBack = { navigator.pop() },
                    onThemeModeChange = { index ->
                        onSettingsChange(currentSettings.copy(themeMode = index))
                    },
                    onMonetChange = { enabled ->
                        onSettingsChange(currentSettings.copy(monetEnabled = enabled))
                    },
                    onAutoUnlockOnAppOpenChange = { enabled ->
                        onSettingsChange(currentSettings.copy(autoUnlockOnAppOpen = enabled))
                    },
                    onAutoConnectEnabledChange = { enabled ->
                        onSettingsChange(currentSettings.copy(autoConnectEnabled = enabled))
                    },
                    onAutoConnectRangeChange = { range ->
                        onSettingsChange(currentSettings.copy(autoConnectRange = range))
                    },
                    onCustomAutoConnectRssiChange = { rssi ->
                        onSettingsChange(currentSettings.copy(customAutoConnectRssi = rssi))
                    },

                    onRestore = onRestore,
                )
            }
        }
    }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider,
    )

    NavDisplay(
        entries = entries,
        onBack = { navigator.pop() },
    )
}
