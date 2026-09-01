package com.juren233.easyopen.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juren233.easyopen.R
import com.juren233.easyopen.shared.ui.PairingSettingsPage
import com.juren233.easyopen.ble.BleDoorController
import com.juren233.easyopen.ble.BleState
import com.juren233.easyopen.ble.DiscoveredDevice
import com.juren233.easyopen.data.DeviceProfile
import com.juren233.easyopen.data.DeviceStore
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun PairingPage(
    controller: BleDoorController,
    existingDeviceCount: Int,
    onOpenBluetoothSettings: () -> Unit,
    onOpenScanner: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    onPaired: (DeviceProfile) -> Unit,
) {
    val state by controller.state.collectAsState()
    val devices by controller.devices.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var password by rememberSaveable { mutableStateOf("") }
    var pairingAttempted by rememberSaveable { mutableStateOf(false) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsPage by rememberSaveable { mutableStateOf(false) }
    var showDiscardSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var customName by rememberSaveable { mutableStateOf(DeviceStore.DEFAULT_NAME) }
    var attribute by rememberSaveable { mutableStateOf(0) }
    var openTime by rememberSaveable { mutableStateOf("650") }
    var waitTime by rememberSaveable { mutableStateOf("2000") }
    var closeTime by rememberSaveable { mutableStateOf("600") }

    LaunchedEffect(Unit) {
        controller.startScan()
    }
    DisposableEffect(Unit) {
        onDispose {
            controller.stopScan()
        }
    }

    val showTitle by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 32
        }
    }
    val pairingInProgress = state is BleState.Pairing
    val errorMessage = (state as? BleState.Error)?.message
    val pairingErrorMessage = if (pairingAttempted) errorMessage else null
    val selectedAddress = selectedDevice?.device?.address

    fun requestSettingsBack() {
        if (showSettingsPage) {
            showDiscardSettingsDialog = true
        } else {
            onCancel?.invoke()
        }
    }

    BackHandler(enabled = showSettingsPage) {
        requestSettingsBack()
    }

    LaunchedEffect(state, selectedAddress) {
        val paired = state as? BleState.Paired ?: return@LaunchedEffect
        if (!selectedAddress.equals(paired.address, ignoreCase = true)) return@LaunchedEffect

        showPasswordDialog = false
        customName = selectedDevice?.name
            ?.takeUnless { BleDoorController.isYiLaOpenerName(it) }
            .orEmpty()
            .ifBlank { DeviceStore.DEFAULT_NAME }
        attribute = 0
        openTime = "650"
        waitTime = "2000"
        closeTime = "600"
        showSettingsPage = true
    }

    fun finishPairing(useCustomSettings: Boolean) {
        val device = selectedDevice ?: return
        onPaired(
            DeviceProfile(
                name = if (useCustomSettings) {
                    customName.trim().ifBlank { DeviceStore.DEFAULT_NAME }
                } else {
                    DeviceStore.DEFAULT_NAME
                },
                address = device.device.address,
                password = password,
                attribute = if (useCustomSettings) attribute else 0,
                openTimeMs = if (useCustomSettings) {
                    openTime.toIntOrNull()?.coerceIn(0, 60_000) ?: 650
                } else {
                    650
                },
                waitTimeMs = if (useCustomSettings) {
                    waitTime.toIntOrNull()?.coerceIn(0, 60_000) ?: 2_000
                } else {
                    2_000
                },
                closeTimeMs = if (useCustomSettings) {
                    closeTime.toIntOrNull()?.coerceIn(0, 60_000) ?: 600
                } else {
                    600
                },
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (showSettingsPage || showTitle) {
                    if (showSettingsPage) {
                        stringResource(R.string.configure_opener_title)
                    } else {
                        stringResource(R.string.add_opener_title)
                    }
                } else {
                    ""
                },
                largeTitle = stringResource(
                    if (showSettingsPage) R.string.configure_opener_title else R.string.add_opener_title,
                ),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (showSettingsPage || onCancel != null) {
                        IconButton(
                            onClick = ::requestSettingsBack,
                        ) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (!showSettingsPage) {
                        onOpenScanner?.let { openScanner ->
                            IconButton(onClick = openScanner) {
                                Icon(
                                    imageVector = MiuixIcons.Scan,
                                    contentDescription = stringResource(R.string.scan_import_title),
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
                    (slideInHorizontally { fullWidth -> fullWidth } + fadeIn()) togetherWith
                        (slideOutHorizontally { fullWidth -> -fullWidth / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { fullWidth -> -fullWidth / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { fullWidth -> fullWidth } + fadeOut())
                }
            },
            label = stringResource(R.string.pairing_flow_page_transition),
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
                    onComplete = { finishPairing(useCustomSettings = true) },
                )
            } else {
                PairingDiscoveryPage(
                    innerPadding = innerPadding,
                    listState = listState,
                    existingDeviceCount = existingDeviceCount,
                    state = state,
                    devices = devices,
                    pairingInProgress = pairingInProgress,
                    errorMessage = errorMessage,
                    controller = controller,
                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                    onSelectDevice = { device ->
                        selectedDevice = device
                        password = ""
                        pairingAttempted = false
                        showPasswordDialog = true
                        controller.stopScan()
                    },
                )
            }
        }
    }

    if (!showSettingsPage && showPasswordDialog) {
        WindowDialog(
            title = stringResource(R.string.password_dialog_title),
            show = true,
            onDismissRequest = {
                if (!pairingInProgress) showPasswordDialog = false
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixText(
                    text = stringResource(
                        R.string.password_dialog_description,
                        selectedDevice?.name.orEmpty(),
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                pairingErrorMessage?.let { message ->
                    MiuixText(
                        text = message,
                        color = Color(0xFFD32F2F),
                        fontSize = 14.sp,
                    )
                }
                TextField(
                    value = password,
                    onValueChange = { value ->
                        password = value.filter(Char::isDigit).take(6)
                    },
                    label = stringResource(R.string.password_field_label),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MiuixTextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showPasswordDialog = false },
                        enabled = !pairingInProgress,
                        modifier = Modifier.weight(1f),
                    )
                    MiuixTextButton(
                        text = stringResource(
                            if (pairingInProgress) R.string.verifying else R.string.verify_and_pair,
                        ),
                        onClick = {
                            pairingAttempted = true
                            selectedDevice?.let { controller.pair(it, password) }
                        },
                        enabled = !pairingInProgress && password.length == 6 && selectedDevice != null,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }

    if (showDiscardSettingsDialog) {
        WindowDialog(
            title = stringResource(R.string.discard_settings_title),
            show = true,
            onDismissRequest = { showDiscardSettingsDialog = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixText(
                    text = stringResource(R.string.discard_settings_message),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MiuixTextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showDiscardSettingsDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    MiuixTextButton(
                        text = stringResource(R.string.continue_action),
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
