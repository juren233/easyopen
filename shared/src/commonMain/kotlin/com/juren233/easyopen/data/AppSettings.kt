package com.juren233.easyopen.data

data class AppSettings(
    val themeMode: Int = 0,
    val monetEnabled: Boolean = false,
    val autoUnlockOnAppOpen: Boolean = false,
    val autoConnectEnabled: Boolean = true,
    val autoConnectRange: Int = AutoConnectSettings.DEFAULT_RANGE,
    val customAutoConnectRssi: Int = AutoConnectSettings.DEFAULT_RSSI_THRESHOLD,
) {
    val autoConnectRssiThreshold: Int
        get() = AutoConnectSettings.thresholdFor(autoConnectRange, customAutoConnectRssi)
}
