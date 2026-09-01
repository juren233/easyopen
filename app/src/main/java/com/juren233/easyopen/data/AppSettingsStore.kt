package com.juren233.easyopen.data

import android.content.SharedPreferences

object AppSettingsStore {
    private const val KEY_THEME_MODE = "themeMode"
    private const val KEY_MONET_ENABLED = "monetEnabled"
    private const val KEY_AUTO_UNLOCK_ON_APP_OPEN = "autoUnlockOnAppOpen"
    private const val KEY_AUTO_CONNECT_ENABLED = "autoConnectEnabled"
    private const val KEY_AUTO_CONNECT_RANGE = "autoConnectRange"
    private const val KEY_CUSTOM_AUTO_CONNECT_RSSI = "customAutoConnectRssi"

    fun load(preferences: SharedPreferences): AppSettings {
        return AppSettings(
            themeMode = preferences.getInt(KEY_THEME_MODE, 0).coerceIn(0, 2),
            monetEnabled = preferences.getBoolean(KEY_MONET_ENABLED, false),
            autoUnlockOnAppOpen = preferences.getBoolean(KEY_AUTO_UNLOCK_ON_APP_OPEN, false),
            autoConnectEnabled = preferences.getBoolean(KEY_AUTO_CONNECT_ENABLED, true),
            autoConnectRange = AutoConnectSettings.normalizeRange(
                preferences.getInt(KEY_AUTO_CONNECT_RANGE, AutoConnectSettings.DEFAULT_RANGE),
            ),
            customAutoConnectRssi = AutoConnectSettings.normalizeRssiThreshold(
                preferences.getInt(
                    KEY_CUSTOM_AUTO_CONNECT_RSSI,
                    AutoConnectSettings.DEFAULT_RSSI_THRESHOLD,
                ),
            ),
        )
    }

    fun save(preferences: SharedPreferences, settings: AppSettings) {
        preferences.edit()
            .putInt(KEY_THEME_MODE, settings.themeMode.coerceIn(0, 2))
            .putBoolean(KEY_MONET_ENABLED, settings.monetEnabled)
            .putBoolean(KEY_AUTO_UNLOCK_ON_APP_OPEN, settings.autoUnlockOnAppOpen)
            .putBoolean(KEY_AUTO_CONNECT_ENABLED, settings.autoConnectEnabled)
            .putInt(KEY_AUTO_CONNECT_RANGE, AutoConnectSettings.normalizeRange(settings.autoConnectRange))
            .putInt(
                KEY_CUSTOM_AUTO_CONNECT_RSSI,
                AutoConnectSettings.normalizeRssiThreshold(settings.customAutoConnectRssi),
            )
            .apply()
    }
}
