package com.juren233.easyopen.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Small common UI seam used only to verify the KMP + Miuix dependency path.
 * It is intentionally not wired into the production Android navigation yet.
 */
@Composable
fun SharedMiuixSmokeScreen() {
    MiuixTheme {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("EasyOpen")
            Text("KMP + Miuix commonMain")
        }
    }
}
