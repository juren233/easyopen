package com.juren233.easyopen

import com.juren233.easyopen.shared.resources.EasyOpenStrings


import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import com.juren233.easyopen.ble.AndroidBlePort
import com.juren233.easyopen.ble.BleDoorController
import com.juren233.easyopen.data.AppSettings
import com.juren233.easyopen.data.DeviceProfile
import com.juren233.easyopen.data.DeviceStore
import com.juren233.easyopen.data.TransferCodec
import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.ui.PermissionGuidePage
import com.juren233.easyopen.nfc.NfcTagEvent
import com.juren233.easyopen.nfc.NfcWriteRequest
import com.juren233.easyopen.nfc.NfcReaderState
import com.juren233.easyopen.nfc.NfcTagWriter
import com.juren233.easyopen.ui.NfcWriteDialogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EasyOpenContent(
    controller: BleDoorController,
    preferences: SharedPreferences,
    permissionsGranted: Boolean,
    appSettings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onNfcWriteTagReset: () -> Unit,
    nfcEvents: Flow<NfcTagEvent>,
    nfcReaderState: StateFlow<NfcReaderState>,
) {
    val context = LocalContext.current
    val nfcWriteSuccessMessage = stringResource(EasyOpenStrings.nfc_write_success)
    val nfcWriteFailedFormat = stringResource(EasyOpenStrings.nfc_write_failed)
    val nfcWriteFailedUnknownMessage = stringResource(EasyOpenStrings.nfc_write_failed_unknown)
    val nfcNotSupportedMessage = stringResource(EasyOpenStrings.nfc_not_supported)
    val nfcTurnOnMessage = stringResource(EasyOpenStrings.nfc_turn_on)
    val scannedDevices by controller.devices.collectAsState()
    val batteryLevels by controller.batteryLevels.collectAsState()
    val nfcState by nfcReaderState.collectAsState()
    val nfcWriteScope = rememberCoroutineScope()
    val blePort = remember(controller, nfcWriteScope) { AndroidBlePort(controller, nfcWriteScope) }
    var nfcWriteWaiting by remember { mutableStateOf(false) }
    var nfcWriteRequest by remember { mutableStateOf<NfcWriteRequest?>(null) }
    var nfcWriteAwaitingTag by remember { mutableStateOf(false) }
    var nfcWritePreserveOriginal by remember { mutableStateOf<Boolean?>(null) }
    var nfcWriting by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf(DeviceStore.load(preferences)) }
    var activeAddress by remember {
        mutableStateOf(DeviceStore.activeAddress(preferences, pairedDevices))
    }
    var onboardingComplete by remember {
        mutableStateOf(DeviceStore.onboardingComplete(preferences, pairedDevices))
    }
    var pendingImportedProfiles by remember { mutableStateOf<List<DeviceProfile>>(emptyList()) }

    LaunchedEffect(permissionsGranted) {
        if (!permissionsGranted) onRequestPermissions()
    }

    LaunchedEffect(Unit) {
        controller.restoreBatteryLevels(pairedDevices)
    }

    LaunchedEffect(batteryLevels) {
        if (batteryLevels.isEmpty() || pairedDevices.isEmpty()) return@LaunchedEffect
        var changed = false
        val nextDevices = pairedDevices.map { profile ->
            val liveLevel = batteryLevels[DeviceStore.normalizeAddress(profile.address)]
            if (liveLevel != null && liveLevel != profile.batteryLevel) {
                changed = true
                profile.copy(batteryLevel = liveLevel)
            } else {
                profile
            }
        }
        if (changed) {
            pairedDevices = nextDevices
            DeviceStore.save(
                preferences = preferences,
                devices = pairedDevices,
                activeAddress = activeAddress,
                onboardingComplete = onboardingComplete,
            )
        }
    }

    fun persistDevices(nextDevices: List<DeviceProfile>, nextActiveAddress: String) {
        val normalizedActive = DeviceStore.normalizeAddress(nextActiveAddress)
        pairedDevices = nextDevices
            .map { it.copy(address = DeviceStore.normalizeAddress(it.address)) }
            .distinctBy { it.address }
        activeAddress = normalizedActive
        onboardingComplete = pairedDevices.isNotEmpty()
        DeviceStore.save(
            preferences = preferences,
            devices = pairedDevices,
            activeAddress = activeAddress,
            onboardingComplete = onboardingComplete,
        )
    }

    fun importDevices(imported: List<DeviceProfile>) {
        if (imported.isEmpty()) return
        val bound = imported.filter { it.address.isNotBlank() }
        val unbound = imported.filter { it.address.isBlank() }
        if (bound.isNotEmpty()) {
            val merged = pairedDevices.filterNot { existing ->
                bound.any { it.address.equals(existing.address, ignoreCase = true) }
            } + bound
            persistDevices(merged, bound.first().address)
        } else if (pairedDevices.isEmpty()) {
            // A cross-platform QR can intentionally omit the Android address.
            // Keep the profile in the re-binding flow instead of persisting an
            // unusable empty-address device as if it were already paired.
            onboardingComplete = false
            activeAddress = ""
            DeviceStore.save(
                preferences = preferences,
                devices = emptyList(),
                activeAddress = "",
                onboardingComplete = false,
            )
        }
        if (unbound.isNotEmpty()) {
            pendingImportedProfiles = pendingImportedProfiles + unbound
        }
    }

    fun applyRestoredBackup(snapshot: TransferCodec.BackupSnapshot) {
        val bound = snapshot.devices.filter { it.address.isNotBlank() }
        if (bound.isNotEmpty()) {
            persistDevices(bound, snapshot.activeAddress)
        } else if (pairedDevices.isEmpty()) {
            DeviceStore.save(
                preferences = preferences,
                devices = emptyList(),
                activeAddress = "",
                onboardingComplete = false,
            )
            onboardingComplete = false
        }
        pendingImportedProfiles = snapshot.devices.filter { it.address.isBlank() }
        onSettingsChange(
            AppSettings(
                themeMode = snapshot.themeMode,
                monetEnabled = snapshot.monetEnabled,
                autoUnlockOnAppOpen = snapshot.autoUnlockOnAppOpen,
                autoConnectEnabled = snapshot.autoConnectEnabled,
                autoConnectRange = snapshot.autoConnectRange,
                customAutoConnectRssi = snapshot.customAutoConnectRssi,
            ),
        )
    }

    fun finishOnboardingPair(profile: DeviceProfile) {
        if (pendingImportedProfiles.isNotEmpty()) {
            importDevices(listOf(profile))
            pendingImportedProfiles = pendingImportedProfiles.drop(1)
        } else if (pairedDevices.isEmpty()) {
            persistDevices(listOf(profile), profile.address)
        } else {
            importDevices(listOf(profile))
        }
    }

    val activeProfile = pairedDevices.firstOrNull {
        it.address.equals(activeAddress, ignoreCase = true)
    } ?: pairedDevices.firstOrNull()
    val latestActiveProfile by rememberUpdatedState(activeProfile)
    val latestNfcWriteWaiting by rememberUpdatedState(nfcWriteWaiting)
    val latestNfcWriteRequest by rememberUpdatedState(nfcWriteRequest)
    val latestNfcWritePreserveOriginal by rememberUpdatedState(nfcWritePreserveOriginal)
    val latestOnboardingComplete by rememberUpdatedState(onboardingComplete)
    val latestPermissionsGranted by rememberUpdatedState(permissionsGranted)

    fun writeFreshNfcTag(
        event: NfcTagEvent,
        request: NfcWriteRequest,
        preserveOriginal: Boolean,
    ) {
        nfcWriteScope.launch {
            val result = withContext(Dispatchers.IO) {
                NfcTagWriter.write(
                    tag = event.tag,
                    originalMessage = request.originalMessage,
                    originalReadSucceeded = request.originalReadSucceeded,
                    preserveOriginal = preserveOriginal,
                )
            }
            nfcWriting = false
            val message = result.fold(
                onSuccess = { nfcWriteSuccessMessage },
                onFailure = { error ->
                    nfcWriteFailedFormat.format(
                        error.message ?: nfcWriteFailedUnknownMessage,
                    )
                },
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(nfcEvents) {
        nfcEvents.collect { event ->
            val pendingWriteRequest = latestNfcWriteRequest
            val preserveOriginal = latestNfcWritePreserveOriginal
            if (pendingWriteRequest != null && preserveOriginal != null) {
                nfcWriteRequest = null
                nfcWritePreserveOriginal = null
                nfcWriteAwaitingTag = false
                nfcWriting = true
                writeFreshNfcTag(event, pendingWriteRequest, preserveOriginal)
            } else if (latestNfcWriteWaiting) {
                nfcWriteWaiting = false
                if (!event.ndefReadSucceeded) {
                    Toast.makeText(
                        context,
                        nfcWriteFailedFormat.format("无法读取原 NFC 内容"),
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    nfcWriteRequest = NfcWriteRequest(
                        originalMessage = event.ndefMessage,
                        originalReadSucceeded = event.ndefReadSucceeded,
                    )
                }
            } else if (
                event.isUnlockCommand &&
                latestPermissionsGranted &&
                latestOnboardingComplete &&
                latestActiveProfile != null
            ) {
                controller.unlock(latestActiveProfile!!)
            }
        }
    }

    val latestRequestNfcWrite = rememberUpdatedState<() -> Unit> {
        when {
            !nfcState.supported -> Toast.makeText(
                context,
                nfcNotSupportedMessage,
                Toast.LENGTH_SHORT,
            ).show()
            !nfcState.enabled -> Toast.makeText(
                context,
                nfcTurnOnMessage,
                Toast.LENGTH_SHORT,
            ).show()
            nfcWriting -> Unit
            else -> {
                onNfcWriteTagReset()
                nfcWriteRequest = null
                nfcWriteAwaitingTag = false
                nfcWritePreserveOriginal = null
                nfcWriteWaiting = true
            }
        }
    }
    val requestNfcWrite = remember { { latestRequestNfcWrite.value() } }

    fun chooseNfcWriteMode(preserveOriginal: Boolean) {
        val request = nfcWriteRequest ?: return
        if (nfcWriteAwaitingTag || nfcWriting) return
        onNfcWriteTagReset()
        nfcWritePreserveOriginal = preserveOriginal
        nfcWriteAwaitingTag = true
    }

    when {
        !permissionsGranted -> PermissionGuidePage(
            onRequestPermissions = onRequestPermissions,
        )
        pendingImportedProfiles.isNotEmpty() -> OnboardingNavigation(
            controller = controller,
            blePort = blePort,
            existingDeviceCount = pairedDevices.size,
            onOpenBluetoothSettings = onOpenBluetoothSettings,
            onPaired = ::finishOnboardingPair,
            onImported = { imported ->
                importDevices(imported)
            },
            onRestored = ::applyRestoredBackup,
            initialProfile = pendingImportedProfiles.first().toCoreProfile(),
        )
        !onboardingComplete -> OnboardingNavigation(
            controller = controller,
            blePort = blePort,
            existingDeviceCount = pairedDevices.size,
            onOpenBluetoothSettings = onOpenBluetoothSettings,
            onPaired = ::finishOnboardingPair,
            onImported = { imported ->
                importDevices(imported)
            },
            onRestored = ::applyRestoredBackup,
        )
        activeProfile == null -> OnboardingNavigation(
            controller = controller,
            blePort = blePort,
            existingDeviceCount = 0,
            onOpenBluetoothSettings = onOpenBluetoothSettings,
            onPaired = ::finishOnboardingPair,
            onImported = { imported ->
                importDevices(imported)
            },
            onRestored = ::applyRestoredBackup,
        )
        else -> {
            val activeProfileState = androidx.compose.runtime.rememberUpdatedState(activeProfile)
            val appSettingsState = rememberUpdatedState(appSettings)
            EasyOpenNavigation(
                controller = controller,
                blePort = blePort,
                devices = pairedDevices,
                appSettings = appSettings,
                appSettingsState = appSettingsState,
                onOpenBluetoothSettings = onOpenBluetoothSettings,
                activeProfileState = activeProfileState,
                activeAddress = activeAddress,
                onActiveDeviceChange = { address ->
                    activeAddress = address
                    DeviceStore.save(
                        preferences = preferences,
                        devices = pairedDevices,
                        activeAddress = address,
                        onboardingComplete = true,
                    )
                },
                onDevicePaired = { profile -> importDevices(listOf(profile)) },
                onProfileChange = { updated ->
                    persistDevices(
                        nextDevices = pairedDevices.map {
                            if (it.address.equals(updated.address, ignoreCase = true)) updated else it
                        },
                        nextActiveAddress = activeAddress,
                    )
                },
                onImported = ::importDevices,
                onSettingsChange = onSettingsChange,
                onNfcWriteRequested = requestNfcWrite,
                onRestore = ::applyRestoredBackup,
            )
        }
    }

    // Keep NFC dialogs outside Navigation 3 entries. A remembered nav entry
    // can retain its content while the root state changes; rendering the
    // dialogs here makes waiting/choice/cancel transitions immediate.
    NfcWriteDialogs(
        waiting = nfcWriteWaiting,
        request = nfcWriteRequest.takeUnless { nfcWriteAwaitingTag },
        awaitingTag = nfcWriteAwaitingTag,
        writing = nfcWriting,
        onChoice = ::chooseNfcWriteMode,
        onCancel = {
            if (!nfcWriting) {
                nfcWriteWaiting = false
                nfcWriteRequest = null
                nfcWriteAwaitingTag = false
                nfcWritePreserveOriginal = null
            }
        },
    )

    @Suppress("UNUSED_VARIABLE")
    val ignored = scannedDevices
    DisposableEffect(Unit) {
        onDispose {
            controller.stopScan()
            controller.stopOpenerMonitoring()
            controller.stopBatteryScan()
        }
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
    hardwareMac = hardwareMac,
)
