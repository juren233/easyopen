package com.juren233.easyopen.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juren233.easyopen.R
import com.juren233.easyopen.ble.BleDoorController
import com.juren233.easyopen.shared.ui.MiuixBlurredBar
import com.juren233.easyopen.shared.ui.rememberMiuixBlurBackdrop
import com.juren233.easyopen.ble.BleState
import com.juren233.easyopen.ble.OpenerConnectionStatus
import com.juren233.easyopen.data.DeviceProfile
import com.juren233.easyopen.data.DeviceStore
import com.juren233.easyopen.utils.UpdateData
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HomePage(
    controller: BleDoorController,
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
    val activeProfile by activeProfileState
    val context = LocalContext.current
    val backdrop = rememberMiuixBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    var showDeviceChooser by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showShareChooser by rememberSaveable { mutableStateOf(false) }
    var shareDevices by remember { mutableStateOf<List<DeviceProfile>?>(null) }
    var shareSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var name by remember(activeProfile) { mutableStateOf(activeProfile.name) }
    var password by remember(activeProfile) { mutableStateOf(activeProfile.password) }
    var attribute by remember(activeProfile) { mutableStateOf(activeProfile.attribute) }
    var openTime by remember(activeProfile) { mutableStateOf(activeProfile.openTimeMs.toString()) }
    var waitTime by remember(activeProfile) { mutableStateOf(activeProfile.waitTimeMs.toString()) }
    var closeTime by remember(activeProfile) { mutableStateOf(activeProfile.closeTimeMs.toString()) }

    val bleState by controller.state.collectAsState()
    val openerConnection by controller.openerConnection.collectAsState()
    val batteryLevels by controller.batteryLevels.collectAsState()
    val availableUpdate by UpdateData.availableUpdate.collectAsState()
    val connectionStatus = openerConnection.status
    val busy = bleState is BleState.Unlocking || connectionStatus == OpenerConnectionStatus.CONNECTING
    val canUnlock = activeProfile.address.isNotBlank() &&
        activeProfile.password.isNotBlank() &&
        connectionStatus in setOf(OpenerConnectionStatus.DISCOVERED, OpenerConnectionStatus.CONNECTED) &&
        !busy

    fun showShareUi() {
        if (devices.size == 1) {
            shareDevices = devices
        } else {
            shareSelection = devices.map { DeviceStore.normalizeAddress(it.address) }.toSet()
            showShareChooser = true
        }
    }

    Scaffold(
        topBar = {
            MiuixBlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.home_title),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onOpenScanner) {
                            Icon(
                                imageVector = MiuixIcons.Scan,
                                contentDescription = stringResource(R.string.scan_import_title),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = ::showShareUi) {
                            Icon(
                                imageVector = MiuixIcons.Share,
                                contentDescription = stringResource(R.string.share_opener_title),
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = MiuixIcons.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 28.dp,
                ),
            ) {
            availableUpdate?.let { update ->
                item(key = "update_notice") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)),
                                    )
                                }
                            },
                    ) {
                        MiuixText(
                            text = stringResource(
                                R.string.update_available_notice,
                                update.displayVersion,
                            ),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth(),
                ) {
                    BasicComponent(
                        title = activeProfile.name,
                        summary = stringResource(
                            R.string.device_summary,
                            activeProfile.address,
                            when (connectionStatus) {
                                OpenerConnectionStatus.NOT_FOUND -> stringResource(R.string.status_not_found)
                                OpenerConnectionStatus.DISCOVERED -> stringResource(R.string.status_discovered)
                                OpenerConnectionStatus.CONNECTING -> stringResource(R.string.status_connecting)
                                OpenerConnectionStatus.CONNECTED -> stringResource(R.string.status_connected)
                            },
                            formatBatteryLevel(
                                batteryLevels[activeProfile.address.uppercase()] ?: activeProfile.batteryLevel,
                            ),
                        ),
                        endActions = {
                            MiuixTextButton(
                                text = stringResource(R.string.switch_opener),
                                onClick = { showDeviceChooser = true },
                                enabled = !busy,
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        },
                    )
                }
            }
            item {
                Button(
                    onClick = { controller.unlock(activeProfile) },
                    enabled = canUnlock,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth()
                        .height(128.dp),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    MiuixText(
                        text = stringResource(if (busy) R.string.status_connecting else R.string.one_tap_unlock),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            item {
                Button(
                    onClick = { showSettings = !showSettings },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    MiuixText(
                        text = stringResource(
                            if (showSettings) R.string.opener_settings_expanded else R.string.opener_settings_collapsed,
                        ),
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AnimatedVisibility(
                        visible = showSettings,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                    ) {
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                                .fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = stringResource(R.string.opener_name),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 1,
                                )
                                TextField(
                                    value = password,
                                    onValueChange = { password = it.filter(Char::isDigit).take(6) },
                                    label = stringResource(R.string.password_field_settings),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 1,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                                MiuixText(
                                    text = stringResource(R.string.lock_direction),
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    MiuixTextButton(
                                        text = stringResource(R.string.forward),
                                        onClick = { attribute = 0 },
                                        modifier = Modifier.weight(1f),
                                        colors = if (attribute == 0) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                                    )
                                    MiuixTextButton(
                                        text = stringResource(R.string.reverse),
                                        onClick = { attribute = 1 },
                                        modifier = Modifier.weight(1f),
                                        colors = if (attribute == 1) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    NumberField(stringResource(R.string.open_duration), openTime, { openTime = it }, Modifier.weight(1f))
                                    NumberField(stringResource(R.string.hold_duration), waitTime, { waitTime = it }, Modifier.weight(1f))
                                    NumberField(stringResource(R.string.close_duration), closeTime, { closeTime = it }, Modifier.weight(1f))
                                }
                                MiuixTextButton(
                                    text = stringResource(R.string.nfc_write_title),
                                    onClick = onNfcWriteRequested,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.textButtonColors(),
                                )
                                MiuixTextButton(
                                    text = stringResource(R.string.save_settings),
                                    onClick = {
                                        onProfileChange(
                                            activeProfile.copy(
                                                name = name.trim().ifBlank { DeviceStore.DEFAULT_NAME },
                                                password = password,
                                                attribute = attribute,
                                                openTimeMs = openTime.toIntOrNull()?.coerceIn(0, 60_000) ?: 650,
                                                waitTimeMs = waitTime.toIntOrNull()?.coerceIn(0, 60_000) ?: 2_000,
                                                closeTimeMs = closeTime.toIntOrNull()?.coerceIn(0, 60_000) ?: 600,
                                            ),
                                        )
                                        showSettings = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }

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
