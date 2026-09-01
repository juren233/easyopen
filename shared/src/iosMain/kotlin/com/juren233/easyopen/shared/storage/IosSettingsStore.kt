package com.juren233.easyopen.shared.storage

import com.juren233.easyopen.data.AppSettings
import com.juren233.easyopen.data.AutoConnectSettings
import platform.Foundation.NSUserDefaults

/** iOS-only persistence for settings not owned by the Android DataStore. */
internal object IosSettingsStore {
    private const val PREFIX = "easyopen.ios.settings."

    fun load(defaults: NSUserDefaults): AppSettings = AppSettings(
        themeMode = defaults.integerForKey(PREFIX + "themeMode").toInt().coerceIn(0, 2),
        monetEnabled = defaults.boolForKey(PREFIX + "monetEnabled"),
        autoUnlockOnAppOpen = defaults.boolForKey(PREFIX + "autoUnlockOnAppOpen"),
        autoConnectEnabled = defaults.objectForKey(PREFIX + "autoConnectEnabled")
            ?.let { defaults.boolForKey(PREFIX + "autoConnectEnabled") }
            ?: true,
        autoConnectRange = defaults.integerForKey(PREFIX + "autoConnectRange")
            .toIntOrDefault(defaults, PREFIX + "autoConnectRange", AutoConnectSettings.DEFAULT_RANGE),
        customAutoConnectRssi = defaults.integerForKey(PREFIX + "customAutoConnectRssi")
            .toIntOrDefault(defaults, PREFIX + "customAutoConnectRssi", AutoConnectSettings.DEFAULT_RSSI_THRESHOLD),
    )

    fun save(defaults: NSUserDefaults, settings: AppSettings) {
        defaults.setInteger(settings.themeMode.coerceIn(0, 2).toLong(), forKey = PREFIX + "themeMode")
        defaults.setBool(settings.monetEnabled, forKey = PREFIX + "monetEnabled")
        defaults.setBool(settings.autoUnlockOnAppOpen, forKey = PREFIX + "autoUnlockOnAppOpen")
        defaults.setBool(settings.autoConnectEnabled, forKey = PREFIX + "autoConnectEnabled")
        defaults.setInteger(settings.autoConnectRange.toLong(), forKey = PREFIX + "autoConnectRange")
        defaults.setInteger(settings.customAutoConnectRssi.toLong(), forKey = PREFIX + "customAutoConnectRssi")
    }

    private fun Long.toIntOrDefault(defaults: NSUserDefaults, key: String, fallback: Int): Int =
        if (defaults.objectForKey(key) == null) fallback else toInt()
}
