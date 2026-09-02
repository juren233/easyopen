package com.juren233.easyopen.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.state.EasyOpenConnectionStatus
import com.juren233.easyopen.shared.state.HomePageSnapshot
import easyopen.shared.generated.resources.Res
import easyopen.shared.generated.resources.close_duration
import easyopen.shared.generated.resources.device_summary
import easyopen.shared.generated.resources.forward
import easyopen.shared.generated.resources.hold_duration
import easyopen.shared.generated.resources.home_title
import easyopen.shared.generated.resources.lock_direction
import easyopen.shared.generated.resources.nfc_write_title
import easyopen.shared.generated.resources.nfc_read_title
import easyopen.shared.generated.resources.one_tap_unlock
import easyopen.shared.generated.resources.opener_name
import easyopen.shared.generated.resources.opener_settings_collapsed
import easyopen.shared.generated.resources.opener_settings_expanded
import easyopen.shared.generated.resources.open_duration
import easyopen.shared.generated.resources.password_field_settings
import easyopen.shared.generated.resources.reverse
import easyopen.shared.generated.resources.save_settings
import easyopen.shared.generated.resources.scan_import_title
import easyopen.shared.generated.resources.settings_title
import easyopen.shared.generated.resources.share_opener_title
import easyopen.shared.generated.resources.status_connected
import easyopen.shared.generated.resources.status_connecting
import easyopen.shared.generated.resources.status_discovered
import easyopen.shared.generated.resources.status_not_found
import easyopen.shared.generated.resources.switch_opener
import easyopen.shared.generated.resources.update_available_notice
import com.juren233.easyopen.shared.resources.easyOpenStringResource
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

/** Shared home surface. Platform hosts provide state and side-effect callbacks. */
@Composable
fun HomePageContent(
    snapshot: HomePageSnapshot,
    onOpenScanner: () -> Unit,
    onOpenSettings: () -> Unit,
    onShareRequested: () -> Unit,
    onSwitchOpener: () -> Unit,
    onUnlock: () -> Unit,
    onProfileChange: (CoreDeviceProfile) -> Unit,
    onNfcWriteRequested: () -> Unit,
    onNfcReadRequested: () -> Unit = {},
    onUpdateRequested: () -> Unit,
    showScannerAction: Boolean = true,
    showShareAction: Boolean = true,
    showNfcAction: Boolean = true,
    showNfcReadAction: Boolean = false,
) {
    val activeDevice = snapshot.activeDevice
    val activeProfile = activeDevice.profile
    val backdrop = rememberMiuixBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var name by remember(activeProfile) { mutableStateOf(activeProfile.name) }
    var password by remember(activeProfile) { mutableStateOf(activeProfile.password) }
    var attribute by remember(activeProfile) { mutableStateOf(activeProfile.attribute) }
    var openTime by remember(activeProfile) { mutableStateOf(activeProfile.openTimeMs.toString()) }
    var waitTime by remember(activeProfile) { mutableStateOf(activeProfile.waitTimeMs.toString()) }
    var closeTime by remember(activeProfile) { mutableStateOf(activeProfile.closeTimeMs.toString()) }

    val statusText = when (snapshot.connectionStatus) {
        EasyOpenConnectionStatus.NOT_FOUND -> easyOpenStringResource(Res.string.status_not_found)
        EasyOpenConnectionStatus.DISCOVERED -> easyOpenStringResource(Res.string.status_discovered)
        EasyOpenConnectionStatus.CONNECTING -> easyOpenStringResource(Res.string.status_connecting)
        EasyOpenConnectionStatus.CONNECTED -> easyOpenStringResource(Res.string.status_connected)
    }

    Scaffold(
        topBar = {
            MiuixBlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = easyOpenStringResource(Res.string.home_title),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        if (showScannerAction) {
                            IconButton(onClick = onOpenScanner) {
                                Icon(
                                    imageVector = MiuixIcons.Scan,
                                    contentDescription = easyOpenStringResource(Res.string.scan_import_title),
                                )
                            }
                        }
                    },
                    actions = {
                        if (showShareAction) {
                            IconButton(onClick = onShareRequested) {
                                Icon(
                                    imageVector = MiuixIcons.Share,
                                    contentDescription = easyOpenStringResource(Res.string.share_opener_title),
                                )
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = MiuixIcons.Settings,
                                contentDescription = easyOpenStringResource(Res.string.settings_title),
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
                snapshot.availableUpdate?.let { update ->
                    item(key = "update_notice") {
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                                .fillMaxWidth()
                                .clickable(onClick = onUpdateRequested),
                        ) {
                            MiuixText(
                                text = easyOpenStringResource(Res.string.update_available_notice, update.displayVersion),
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
                            summary = easyOpenStringResource(
                                Res.string.device_summary,
                                activeDevice.identifierLabel,
                                statusText,
                                formatBatteryLevel(snapshot.batteryLevel ?: activeProfile.batteryLevel),
                            ),
                            endActions = {
                                MiuixTextButton(
                                    text = easyOpenStringResource(Res.string.switch_opener),
                                    onClick = onSwitchOpener,
                                    enabled = !snapshot.busy,
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                            },
                        )
                    }
                }
                item {
                    Button(
                        onClick = onUnlock,
                        enabled = snapshot.canUnlock,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                            .height(128.dp),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        MiuixText(
                            text = if (snapshot.busy) {
                                easyOpenStringResource(Res.string.status_connecting)
                            } else {
                                easyOpenStringResource(Res.string.one_tap_unlock)
                            },
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
                            text = easyOpenStringResource(
                                if (showSettings) Res.string.opener_settings_expanded
                                else Res.string.opener_settings_collapsed,
                            ),
                        )
                    }
                }
                item {
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
                                    label = easyOpenStringResource(Res.string.opener_name),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 1,
                                )
                                TextField(
                                    value = password,
                                    onValueChange = { password = it.filter(Char::isDigit).take(6) },
                                    label = easyOpenStringResource(Res.string.password_field_settings),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 1,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                                MiuixText(
                                    text = easyOpenStringResource(Res.string.lock_direction),
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    MiuixTextButton(
                                        text = easyOpenStringResource(Res.string.forward),
                                        onClick = { attribute = 0 },
                                        modifier = Modifier.weight(1f),
                                        colors = if (attribute == 0) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                                    )
                                    MiuixTextButton(
                                        text = easyOpenStringResource(Res.string.reverse),
                                        onClick = { attribute = 1 },
                                        modifier = Modifier.weight(1f),
                                        colors = if (attribute == 1) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    NumberField(easyOpenStringResource(Res.string.open_duration), openTime, { openTime = it }, Modifier.weight(1f))
                                    NumberField(easyOpenStringResource(Res.string.hold_duration), waitTime, { waitTime = it }, Modifier.weight(1f))
                                    NumberField(easyOpenStringResource(Res.string.close_duration), closeTime, { closeTime = it }, Modifier.weight(1f))
                                }
                                if (showNfcAction) {
                                    MiuixTextButton(
                                        text = easyOpenStringResource(Res.string.nfc_write_title),
                                        onClick = onNfcWriteRequested,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.textButtonColors(),
                                    )
                                }
                                if (showNfcReadAction) {
                                    MiuixTextButton(
                                        text = easyOpenStringResource(Res.string.nfc_read_title),
                                        onClick = onNfcReadRequested,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                    )
                                }
                                MiuixTextButton(
                                    text = easyOpenStringResource(Res.string.save_settings),
                                    onClick = {
                                        onProfileChange(
                                            activeProfile.copy(
                                                name = name.trim().ifBlank { "我的开门器" },
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
