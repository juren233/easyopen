package com.juren233.easyopen.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun EasyOpenTheme(
    themeMode: Int,
    monetEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val colorSchemeMode = remember(themeMode, monetEnabled) {
        when {
            monetEnabled && themeMode == 1 -> ColorSchemeMode.MonetLight
            monetEnabled && themeMode == 2 -> ColorSchemeMode.MonetDark
            monetEnabled -> ColorSchemeMode.MonetSystem
            themeMode == 1 -> ColorSchemeMode.Light
            themeMode == 2 -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
    }
    val controller = remember(colorSchemeMode) {
        ThemeController(colorSchemeMode = colorSchemeMode)
    }
    MiuixTheme(controller = controller, content = content)
}
