package com.juren233.easyopen.shared.ui

import androidx.compose.ui.text.font.FontFamily

/**
 * Font family used by shared UI text.
 *
 * Native text fallback is platform-specific. iOS uses an explicit CJK system
 * family so Skia does not resolve PingFang glyphs through the default family
 * when Miuix requests medium/bold text weights.
 */
internal expect val easyOpenTextFontFamily: FontFamily
