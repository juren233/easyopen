package com.juren233.easyopen.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.defaultTextStyles
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
    val textStyles = remember(easyOpenTextFontFamily) {
        val defaults = defaultTextStyles()
        defaults.copy(
            main = defaults.main.copy(fontFamily = easyOpenTextFontFamily),
            paragraph = defaults.paragraph.copy(fontFamily = easyOpenTextFontFamily),
            body1 = defaults.body1.copy(fontFamily = easyOpenTextFontFamily),
            body2 = defaults.body2.copy(fontFamily = easyOpenTextFontFamily),
            button = defaults.button.copy(fontFamily = easyOpenTextFontFamily),
            footnote1 = defaults.footnote1.copy(fontFamily = easyOpenTextFontFamily),
            footnote2 = defaults.footnote2.copy(fontFamily = easyOpenTextFontFamily),
            headline1 = defaults.headline1.copy(fontFamily = easyOpenTextFontFamily),
            headline2 = defaults.headline2.copy(fontFamily = easyOpenTextFontFamily),
            subtitle = defaults.subtitle.copy(fontFamily = easyOpenTextFontFamily),
            title1 = defaults.title1.copy(fontFamily = easyOpenTextFontFamily),
            title2 = defaults.title2.copy(fontFamily = easyOpenTextFontFamily),
            title3 = defaults.title3.copy(fontFamily = easyOpenTextFontFamily),
            title4 = defaults.title4.copy(fontFamily = easyOpenTextFontFamily),
        )
    }
    MiuixTheme(controller = controller, textStyles = textStyles, content = content)
}
