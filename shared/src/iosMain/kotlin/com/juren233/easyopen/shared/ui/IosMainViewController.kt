package com.juren233.easyopen.shared.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** iOS host entry point; the Swift shell embeds the shared Compose UI here. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    SharedMiuixSmokeScreen()
}
