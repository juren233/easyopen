package com.juren233.easyopen.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding
import com.juren233.easyopen.shared.state.EasyOpenBleDevice
import com.juren233.easyopen.shared.state.EasyOpenBleOperation
import com.juren233.easyopen.shared.state.EasyOpenSavedDevice
import com.juren233.easyopen.shared.state.EasyOpenBleSnapshot
import com.juren233.easyopen.shared.state.displayIdentifier
import easyopen.shared.generated.resources.Res
import easyopen.shared.generated.resources.add_another_opener_section
import easyopen.shared.generated.resources.add_opener_title
import easyopen.shared.generated.resources.back
import easyopen.shared.generated.resources.cancel
import easyopen.shared.generated.resources.configure_opener_title
import easyopen.shared.generated.resources.continue_action
import easyopen.shared.generated.resources.device_signal_summary
import easyopen.shared.generated.resources.discard_settings_message
import easyopen.shared.generated.resources.discard_settings_title
import easyopen.shared.generated.resources.found_openers
import easyopen.shared.generated.resources.keep_opener_powered_nearby
import easyopen.shared.generated.resources.no_opener_found
import easyopen.shared.generated.resources.no_opener_found_summary
import easyopen.shared.generated.resources.open_bluetooth_settings
import easyopen.shared.generated.resources.pair_opener_section
import easyopen.shared.generated.resources.pairing_flow_page_transition
import easyopen.shared.generated.resources.imported_opener_profile
import easyopen.shared.generated.resources.pairing_password_in_progress
import easyopen.shared.generated.resources.password_dialog_description
import easyopen.shared.generated.resources.password_dialog_title
import easyopen.shared.generated.resources.password_field_label
import easyopen.shared.generated.resources.search_again
import easyopen.shared.generated.resources.scan_import_title
import easyopen.shared.generated.resources.search_nearby_openers
import easyopen.shared.generated.resources.search_results
import easyopen.shared.generated.resources.saved_openers
import easyopen.shared.generated.resources.saved_opener_summary
import easyopen.shared.generated.resources.searching
import easyopen.shared.generated.resources.start_search
import easyopen.shared.generated.resources.verifying
import easyopen.shared.generated.resources.verify_and_pair
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Platform-neutral pairing discovery, password and opener-settings flow.
 *
 * Android and iOS only provide discovery state and the pair command. Native
 * Bluetooth permissions, scanner objects and storage remain outside this UI.
 */
@Composable
fun PairingPageContent(
    existingDeviceCount: Int,
    snapshot: EasyOpenBleSnapshot,
    bluetoothEnabled: Boolean = true,
    onBack: (() -> Unit)? = null,
    onOpenBluetoothSettings: () -> Unit,
    onOpenScanner: (() -> Unit)? = null,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onPairRequested: (DeviceBinding, CoreDeviceProfile) -> Unit,
    onPaired: (DeviceBinding, CoreDeviceProfile) -> Unit,
    pairedDevices: List<EasyOpenSavedDevice> = emptyList(),
    initialProfile: CoreDeviceProfile? = null,
    onSelectPairedDevice: (EasyOpenSavedDevice) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var selectedDevice by remember { mutableStateOf<EasyOpenBleDevice?>(null) }
    var password by rememberSaveable(initialProfile?.password) { mutableStateOf(initialProfile?.password.orEmpty()) }
    var pairAttempted by rememberSaveable { mutableStateOf(false) }
    var showSettingsPage by rememberSaveable { mutableStateOf(false) }
    var showDiscardSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var customName by rememberSaveable { mutableStateOf("我的开门器") }
    var attribute by rememberSaveable { mutableStateOf(0) }
    var openTime by rememberSaveable { mutableStateOf("650") }
    var waitTime by rememberSaveable { mutableStateOf("2000") }
    var closeTime by rememberSaveable { mutableStateOf("600") }
    var autoMatchDismissed by rememberSaveable(initialProfile?.hardwareMac) { mutableStateOf(false) }

    val pairingInProgress = snapshot.operation == EasyOpenBleOperation.PAIRING
    val errorMessage = snapshot.message.takeIf { snapshot.operation == EasyOpenBleOperation.ERROR }
    val selectedBinding = selectedDevice?.binding

    LaunchedEffect(Unit) {
        onStartScan()
    }
    DisposableEffect(Unit) {
        onDispose(onStopScan)
    }
    LaunchedEffect(initialProfile?.hardwareMac, snapshot.discoveredDevices) {
        val expectedHardwareMac = initialProfile?.hardwareMac
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return@LaunchedEffect
        if (autoMatchDismissed || selectedDevice != null) return@LaunchedEffect
        val matchedDevice = snapshot.discoveredDevices.firstOrNull { discovered ->
            discovered.hardwareMac.equals(expectedHardwareMac, ignoreCase = true)
        } ?: return@LaunchedEffect
        selectedDevice = matchedDevice
        password = initialProfile.password
        pairAttempted = false
    }

    LaunchedEffect(snapshot.operation, selectedBinding, showSettingsPage) {
        if (snapshot.operation != EasyOpenBleOperation.PAIRED || showSettingsPage) return@LaunchedEffect
        val device = selectedDevice ?: return@LaunchedEffect
        if (snapshot.activeBinding != device.binding) return@LaunchedEffect
        val imported = initialProfile?.normalized()
        customName = imported?.name ?: device.name.trim().ifBlank { "我的开门器" }
        attribute = imported?.attribute ?: 0
        openTime = (imported?.openTimeMs ?: 650).toString()
        waitTime = (imported?.waitTimeMs ?: 2_000).toString()
        closeTime = (imported?.closeTimeMs ?: 600).toString()
        showSettingsPage = true
    }

    fun requestBack() {
        if (showSettingsPage) {
            showDiscardSettingsDialog = true
        } else {
            onBack?.invoke()
        }
    }

    fun finishPairing() {
        val device = selectedDevice ?: return
        onPaired(
            device.binding,
            CoreDeviceProfile(
                name = customName.trim().ifBlank { "我的开门器" },
                password = password,
                attribute = attribute,
                openTimeMs = openTime.toIntOrNull()?.coerceIn(0, 60_000) ?: 650,
                waitTimeMs = waitTime.toIntOrNull()?.coerceIn(0, 60_000) ?: 2_000,
                closeTimeMs = closeTime.toIntOrNull()?.coerceIn(0, 60_000) ?: 600,
                hardwareMac = device.hardwareMac ?: initialProfile?.hardwareMac,
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (showSettingsPage) {
                    stringResource(Res.string.configure_opener_title)
                } else if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 32) {
                    stringResource(Res.string.add_opener_title)
                } else {
                    ""
                },
                largeTitle = stringResource(
                    if (showSettingsPage) Res.string.configure_opener_title else Res.string.add_opener_title,
                ),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (showSettingsPage || onBack != null) {
                        IconButton(onClick = ::requestBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(Res.string.back),
                            )
                        }
                    }
                },
                actions = {
                    if (!showSettingsPage) {
                        onOpenScanner?.let { openScanner ->
                            IconButton(onClick = openScanner) {
                                Icon(
                                    imageVector = MiuixIcons.Scan,
                                    contentDescription = stringResource(Res.string.scan_import_title),
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = showSettingsPage,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = stringResource(Res.string.pairing_flow_page_transition),
        ) { settingsPage ->
            if (settingsPage) {
                PairingSettingsPage(
                    innerPadding = innerPadding,
                    name = customName,
                    onNameChange = { customName = it },
                    attribute = attribute,
                    onAttributeChange = { attribute = it },
                    openTime = openTime,
                    onOpenTimeChange = { openTime = it },
                    waitTime = waitTime,
                    onWaitTimeChange = { waitTime = it },
                    closeTime = closeTime,
                    onCloseTimeChange = { closeTime = it },
                    onComplete = ::finishPairing,
                )
            } else {
                PairingDiscoveryContent(
                    innerPadding = innerPadding,
                    listState = listState,
                    existingDeviceCount = existingDeviceCount,
                    snapshot = snapshot,
                    bluetoothEnabled = bluetoothEnabled,
                    pairingInProgress = pairingInProgress,
                    errorMessage = errorMessage,
                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                    onStartScan = onStartScan,
                    pairedDevices = pairedDevices,
                    initialProfile = initialProfile,
                    onSelectPairedDevice = onSelectPairedDevice,
                    onSelectDevice = { device ->
                        autoMatchDismissed = true
                        selectedDevice = device
                        password = initialProfile?.password.orEmpty()
                        pairAttempted = false
                    },
                )
            }
        }
    }

    if (!showSettingsPage) {
        selectedDevice?.let { device ->
            WindowDialog(
                title = stringResource(Res.string.password_dialog_title),
                show = true,
                onDismissRequest = { if (!pairingInProgress) selectedDevice = null },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixText(
                        text = stringResource(Res.string.password_dialog_description, device.name),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (pairAttempted && errorMessage != null) {
                        MiuixText(text = errorMessage, color = Color(0xFFD32F2F), fontSize = 14.sp)
                    }
                    TextField(
                        value = password,
                        onValueChange = { password = it.filter(Char::isDigit).take(6) },
                        label = stringResource(Res.string.password_field_label),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        MiuixTextButton(
                            text = stringResource(Res.string.cancel),
                            onClick = {
                            autoMatchDismissed = true
                            selectedDevice = null
                        },
                            enabled = !pairingInProgress,
                            modifier = Modifier.weight(1f),
                        )
                        MiuixTextButton(
                            text = stringResource(if (pairingInProgress) Res.string.verifying else Res.string.verify_and_pair),
                            onClick = {
                                pairAttempted = true
                                onPairRequested(
                                    device.binding,
                                    CoreDeviceProfile(password = password),
                                )
                            },
                            enabled = !pairingInProgress && password.length == 6,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        }
    }

    if (showDiscardSettingsDialog) {
        WindowDialog(
            title = stringResource(Res.string.discard_settings_title),
            show = true,
            onDismissRequest = { showDiscardSettingsDialog = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixText(
                    text = stringResource(Res.string.discard_settings_message),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MiuixTextButton(
                        text = stringResource(Res.string.cancel),
                        onClick = { showDiscardSettingsDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    MiuixTextButton(
                        text = stringResource(Res.string.continue_action),
                        onClick = {
                            showDiscardSettingsDialog = false
                            showSettingsPage = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PairingDiscoveryContent(
    innerPadding: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    existingDeviceCount: Int,
    snapshot: EasyOpenBleSnapshot,
    bluetoothEnabled: Boolean,
    pairingInProgress: Boolean,
    errorMessage: String?,
    onOpenBluetoothSettings: () -> Unit,
    onStartScan: () -> Unit,
    pairedDevices: List<EasyOpenSavedDevice>,
    initialProfile: CoreDeviceProfile?,
    onSelectPairedDevice: (EasyOpenSavedDevice) -> Unit,
    onSelectDevice: (EasyOpenBleDevice) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        if (pairedDevices.isNotEmpty()) {
            item { SmallTitle(text = stringResource(Res.string.saved_openers)) }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Column {
                        pairedDevices.forEach { savedDevice ->
                            ArrowPreference(
                                title = savedDevice.profile.name,
                                summary = stringResource(
                                    Res.string.saved_opener_summary,
                                    savedDevice.binding.displayIdentifier(),
                                ),
                                enabled = !pairingInProgress,
                                onClick = { onSelectPairedDevice(savedDevice) },
                            )
                        }
                    }
                }
            }
        }
        initialProfile?.let { importedProfile ->
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth(),
                ) {
                    BasicComponent(
                        title = stringResource(Res.string.imported_opener_profile),
                        summary = importedProfile.name,
                    )
                }
            }
        }
        item {
            SmallTitle(
                text = stringResource(
                    if (existingDeviceCount == 0) Res.string.pair_opener_section
                    else Res.string.add_another_opener_section,
                ),
            )
        }
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth(),
            ) {
                Column {
                    BasicComponent(
                        title = stringResource(Res.string.search_nearby_openers),
                        summary = when {
                            pairingInProgress -> stringResource(Res.string.pairing_password_in_progress)
                            snapshot.operation == EasyOpenBleOperation.SCANNING -> stringResource(Res.string.searching)
                            errorMessage != null -> errorMessage
                            snapshot.discoveredDevices.isEmpty() -> stringResource(Res.string.keep_opener_powered_nearby)
                            else -> stringResource(Res.string.found_openers, snapshot.discoveredDevices.size)
                        },
                    )
                    if (!bluetoothEnabled) {
                        MiuixTextButton(
                            text = stringResource(Res.string.open_bluetooth_settings),
                            onClick = onOpenBluetoothSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                    MiuixTextButton(
                        text = stringResource(
                            if (snapshot.operation == EasyOpenBleOperation.SCANNING) Res.string.search_again
                            else Res.string.start_search,
                        ),
                        onClick = onStartScan,
                        enabled = !pairingInProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
        item { SmallTitle(text = stringResource(Res.string.search_results)) }
        if (snapshot.discoveredDevices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth(),
                ) {
                    BasicComponent(
                        title = stringResource(Res.string.no_opener_found),
                        summary = stringResource(Res.string.no_opener_found_summary),
                    )
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Column {
                        snapshot.discoveredDevices.forEach { device ->
                            ArrowPreference(
                                title = device.name,
                                summary = stringResource(
                                    Res.string.device_signal_summary,
                                    device.binding.displayIdentifier(),
                                    device.rssi,
                                ),
                                enabled = !pairingInProgress,
                                onClick = { onSelectDevice(device) },
                            )
                        }
                    }
                }
            }
        }
    }
}
