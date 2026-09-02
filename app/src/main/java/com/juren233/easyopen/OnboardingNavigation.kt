package com.juren233.easyopen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.juren233.easyopen.ble.BleDoorController
import com.juren233.easyopen.shared.navigation.EasyOpenNavigator
import com.juren233.easyopen.shared.navigation.EasyOpenRoute
import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.data.DeviceProfile
import com.juren233.easyopen.data.TransferCodec
import com.juren233.easyopen.ui.PairingPage
import com.juren233.easyopen.ui.QrImportPage

/** Real Navigation 3 stack for the first-run flow and its QR import child page. */
@Composable
internal fun OnboardingNavigation(
    controller: BleDoorController,
    blePort: com.juren233.easyopen.shared.platform.EasyOpenBlePort,
    existingDeviceCount: Int,
    onOpenBluetoothSettings: () -> Unit,
    onPaired: (DeviceProfile) -> Unit,
    onImported: (List<DeviceProfile>) -> Unit,
    onRestored: (TransferCodec.BackupSnapshot) -> Unit,
    initialProfile: CoreDeviceProfile? = null,
) {
    val backStack = rememberNavBackStack(EasyOpenRoute.OnboardingPairing)
    val navigator = remember { EasyOpenNavigator(backStack) }
    val currentRoute = backStack.lastOrNull()

    LaunchedEffect(currentRoute) {
        if (currentRoute == EasyOpenRoute.ScanImport) {
            controller.stopScan()
        }
    }
    DisposableEffect(Unit) {
        onDispose { controller.stopScan() }
    }

    val entryProvider = remember(backStack, existingDeviceCount, initialProfile, onPaired, onImported, onRestored) {
        entryProvider<NavKey> {
            entry<EasyOpenRoute.OnboardingPairing> {
                val profileKey = initialProfile?.let {
                    "${it.name}:${it.password}:${it.attribute}:${it.openTimeMs}:${it.waitTimeMs}:${it.closeTimeMs}:${it.hardwareMac}"
                } ?: "manual-pairing"
                key(profileKey) {
                    PairingPage(
                        blePort = blePort,
                        existingDeviceCount = existingDeviceCount,
                        onOpenBluetoothSettings = onOpenBluetoothSettings,
                        onOpenScanner = { navigator.navigate(EasyOpenRoute.ScanImport) },
                        onCancel = null,
                        onPaired = onPaired,
                        initialProfile = initialProfile,
                    )
                }
            }
            entry<EasyOpenRoute.ScanImport> {
                QrImportPage(
                    onBack = { navigator.pop() },
                    onImported = { imported ->
                        onImported(imported)
                        navigator.pop()
                    },
                    onRestored = { snapshot ->
                        onRestored(snapshot)
                        navigator.pop()
                    },
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
