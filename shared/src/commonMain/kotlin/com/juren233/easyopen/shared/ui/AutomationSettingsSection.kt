package com.juren233.easyopen.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import easyopen.shared.generated.resources.Res
import easyopen.shared.generated.resources.automation_category
import easyopen.shared.generated.resources.auto_connect_opener
import easyopen.shared.generated.resources.auto_connect_range
import easyopen.shared.generated.resources.auto_connect_range_custom
import easyopen.shared.generated.resources.auto_connect_range_far
import easyopen.shared.generated.resources.auto_connect_range_moderate
import easyopen.shared.generated.resources.auto_connect_range_near
import easyopen.shared.generated.resources.auto_connect_signal_value
import easyopen.shared.generated.resources.auto_connect_signal_value_format
import easyopen.shared.generated.resources.auto_connect_signal_value_input
import easyopen.shared.generated.resources.auto_connect_signal_value_invalid
import easyopen.shared.generated.resources.auto_unlock_on_app_open
import easyopen.shared.generated.resources.cancel
import easyopen.shared.generated.resources.save_settings
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juren233.easyopen.data.AppSettings
import com.juren233.easyopen.data.AutoConnectSettings
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun AutomationSettingsSection(
    settings: AppSettings,
    onAutoUnlockOnAppOpenChange: (Boolean) -> Unit,
    onAutoConnectEnabledChange: (Boolean) -> Unit,
    onAutoConnectRangeChange: (Int) -> Unit,
    onCustomAutoConnectRssiChange: (Int) -> Unit,
) {
    val rangeOptions = listOf(
        stringResource(Res.string.auto_connect_range_near),
        stringResource(Res.string.auto_connect_range_moderate),
        stringResource(Res.string.auto_connect_range_far),
        stringResource(Res.string.auto_connect_range_custom),
    )
    var showCustomRssiDialog by rememberSaveable { mutableStateOf(false) }
    var customRssiInput by rememberSaveable { mutableStateOf("") }

    Column {
        SmallTitle(text = stringResource(Res.string.automation_category))
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                SwitchPreference(
                    title = stringResource(Res.string.auto_unlock_on_app_open),
                    checked = settings.autoUnlockOnAppOpen,
                    onCheckedChange = onAutoUnlockOnAppOpenChange,
                )
                SwitchPreference(
                    title = stringResource(Res.string.auto_connect_opener),
                    checked = settings.autoConnectEnabled,
                    onCheckedChange = onAutoConnectEnabledChange,
                )
                AnimatedVisibility(
                    visible = settings.autoConnectEnabled,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Column {
                        WindowDropdownPreference(
                            title = stringResource(Res.string.auto_connect_range),
                            items = rangeOptions,
                            selectedIndex = AutoConnectSettings.normalizeRange(settings.autoConnectRange),
                            onSelectedIndexChange = onAutoConnectRangeChange,
                        )
                        AnimatedVisibility(
                            visible = AutoConnectSettings.normalizeRange(settings.autoConnectRange) ==
                                AutoConnectSettings.RANGE_CUSTOM,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                        ) {
                            ArrowPreference(
                                title = stringResource(Res.string.auto_connect_signal_value),
                                endActions = {
                                    MiuixText(
                                        text = stringResource(
                                            Res.string.auto_connect_signal_value_format,
                                            AutoConnectSettings.inputMagnitudeFor(settings.customAutoConnectRssi),
                                        ),
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                },
                                onClick = {
                                    customRssiInput = AutoConnectSettings
                                        .inputMagnitudeFor(settings.customAutoConnectRssi)
                                        .toString()
                                    showCustomRssiDialog = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCustomRssiDialog) {
        val customMagnitude = customRssiInput.toIntOrNull()
        WindowDialog(
            title = stringResource(Res.string.auto_connect_signal_value),
            show = true,
            onDismissRequest = { showCustomRssiDialog = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = customRssiInput,
                    onValueChange = { value ->
                        customRssiInput = value.filter(Char::isDigit).take(3)
                    },
                    label = stringResource(Res.string.auto_connect_signal_value_input),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MiuixTextButton(
                        text = stringResource(Res.string.cancel),
                        onClick = { showCustomRssiDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    MiuixTextButton(
                        text = stringResource(Res.string.save_settings),
                        onClick = {
                            customMagnitude?.let { magnitude ->
                                onCustomAutoConnectRssiChange(
                                    AutoConnectSettings.normalizeRssiThreshold(-magnitude),
                                )
                                showCustomRssiDialog = false
                            }
                        },
                        enabled = customMagnitude in AutoConnectSettings.MIN_RSSI_MAGNITUDE..AutoConnectSettings.MAX_RSSI_MAGNITUDE,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
                if (customMagnitude != null &&
                    customMagnitude !in AutoConnectSettings.MIN_RSSI_MAGNITUDE..AutoConnectSettings.MAX_RSSI_MAGNITUDE
                ) {
                    MiuixText(
                        text = stringResource(Res.string.auto_connect_signal_value_invalid),
                        color = Color(0xFFD32F2F),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
