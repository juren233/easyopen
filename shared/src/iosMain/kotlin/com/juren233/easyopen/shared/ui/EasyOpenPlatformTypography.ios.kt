@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.juren233.easyopen.shared.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont

/**
 * Use PingFang explicitly on iOS. Compose's default system-family resolver can
 * select an incompatible fallback for CJK glyphs when a component asks for a
 * non-regular weight (for example Miuix's Medium titles).
 */
internal actual val easyOpenTextFontFamily: FontFamily = FontFamily(
    SystemFont("PingFang SC", FontWeight.W400),
    SystemFont("PingFang SC", FontWeight.W500),
    SystemFont("PingFang SC", FontWeight.W700),
)
