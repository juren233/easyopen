package com.juren233.easyopen.shared.ui

import androidx.compose.runtime.Composable
import easyopen.shared.generated.resources.Res
import easyopen.shared.generated.resources.battery_100
import easyopen.shared.generated.resources.battery_25
import easyopen.shared.generated.resources.battery_50
import easyopen.shared.generated.resources.battery_75
import easyopen.shared.generated.resources.battery_low
import easyopen.shared.generated.resources.battery_unknown
import org.jetbrains.compose.resources.stringResource

/** Shared localized battery description used by both platform hosts. */
@Composable
fun formatBatteryLevel(level: Int?): String = stringResource(
    when (level) {
        1 -> Res.string.battery_low
        2 -> Res.string.battery_25
        3 -> Res.string.battery_50
        4 -> Res.string.battery_75
        5 -> Res.string.battery_100
        else -> Res.string.battery_unknown
    },
)
