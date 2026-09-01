package com.juren233.easyopen.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Persists the locally paired openers and the current selection. */
object DeviceStore {
    const val DEFAULT_NAME = "我的开门器"

    private const val KEY_DEVICES = "devices"
    private const val KEY_ACTIVE_ADDRESS = "activeAddress"
    private const val KEY_ONBOARDING_COMPLETE = "onboardingComplete"

    private const val LEGACY_NAME = "name"
    private const val LEGACY_ADDRESS = "address"
    private const val LEGACY_PASSWORD = "password"
    private const val LEGACY_ATTRIBUTE = "attribute"
    private const val LEGACY_OPEN_TIME = "openTime"
    private const val LEGACY_WAIT_TIME = "waitTime"
    private const val LEGACY_CLOSE_TIME = "closeTime"

    fun load(preferences: SharedPreferences): List<DeviceProfile> {
        val stored = preferences.getString(KEY_DEVICES, null)
            ?.let(::decode)
            .orEmpty()
        if (stored.isNotEmpty()) return stored

        val legacyAddress = normalizeAddress(preferences.getString(LEGACY_ADDRESS, "").orEmpty())
        val legacyPassword = preferences.getString(LEGACY_PASSWORD, "").orEmpty()
        if (legacyAddress.isBlank() || legacyPassword.isBlank()) return emptyList()

        return listOf(
            DeviceProfile(
                name = preferences.getString(LEGACY_NAME, DEFAULT_NAME).orEmpty().ifBlank { DEFAULT_NAME },
                address = legacyAddress,
                password = legacyPassword,
                attribute = preferences.getInt(LEGACY_ATTRIBUTE, 0),
                openTimeMs = preferences.getInt(LEGACY_OPEN_TIME, 650),
                waitTimeMs = preferences.getInt(LEGACY_WAIT_TIME, 2_000),
                closeTimeMs = preferences.getInt(LEGACY_CLOSE_TIME, 600),
                hardwareMac = legacyAddress,
            ),
        )
    }

    fun activeAddress(preferences: SharedPreferences, devices: List<DeviceProfile>): String {
        val stored = normalizeAddress(preferences.getString(KEY_ACTIVE_ADDRESS, "").orEmpty())
        return devices.firstOrNull { it.address.equals(stored, ignoreCase = true) }?.address
            ?: devices.firstOrNull()?.address.orEmpty()
    }

    fun onboardingComplete(preferences: SharedPreferences, devices: List<DeviceProfile>): Boolean {
        return preferences.getBoolean(KEY_ONBOARDING_COMPLETE, devices.isNotEmpty()) && devices.isNotEmpty()
    }

    fun save(
        preferences: SharedPreferences,
        devices: List<DeviceProfile>,
        activeAddress: String,
        onboardingComplete: Boolean,
    ) {
        val normalizedDevices = devices
            .map {
                it.copy(
                    address = normalizeAddress(it.address),
                    hardwareMac = normalizeHardwareMac(it.hardwareMac ?: it.address),
                )
            }
            .distinctBy { it.address.uppercase() }
        preferences.edit()
            .putString(KEY_DEVICES, encode(normalizedDevices))
            .putString(KEY_ACTIVE_ADDRESS, normalizeAddress(activeAddress))
            .putBoolean(KEY_ONBOARDING_COMPLETE, onboardingComplete && normalizedDevices.isNotEmpty())
            .apply()
    }

    fun normalizeAddress(address: String): String = address.trim().uppercase()

    fun normalizeHardwareMac(address: String?): String? = address
        ?.let(::normalizeAddress)
        ?.takeIf { it.matches(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}")) }

    private fun encode(devices: List<DeviceProfile>): String {
        val array = JSONArray()
        devices.forEach { device ->
            array.put(
                JSONObject().apply {
                    put("name", device.name)
                    put("address", normalizeAddress(device.address))
                    put("password", device.password)
                    put("attribute", device.attribute)
                    put("openTimeMs", device.openTimeMs)
                    put("waitTimeMs", device.waitTimeMs)
                    put("closeTimeMs", device.closeTimeMs)
                    device.batteryLevel?.let { put("batteryLevel", it.coerceIn(1, 5)) }
                    device.hardwareMac?.let { put("hardwareMac", normalizeHardwareMac(it)) }
                },
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<DeviceProfile> {
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val address = normalizeAddress(item.optString("address"))
                    val password = item.optString("password")
                    if (address.isBlank() || password.isBlank()) continue
                    val hardwareMac = normalizeHardwareMac(item.optString("hardwareMac")) ?: address
                    add(
                        DeviceProfile(
                            name = item.optString("name").ifBlank { DEFAULT_NAME },
                            address = address,
                            password = password,
                            attribute = item.optInt("attribute", 0).coerceIn(0, 1),
                            openTimeMs = item.optInt("openTimeMs", 650).coerceIn(0, 60_000),
                            waitTimeMs = item.optInt("waitTimeMs", 2_000).coerceIn(0, 60_000),
                            closeTimeMs = item.optInt("closeTimeMs", 600).coerceIn(0, 60_000),
                            batteryLevel = item.optInt("batteryLevel", -1).takeIf { it in 1..5 },
                            hardwareMac = hardwareMac,
                        ),
                    )
                }
            }.distinctBy { it.address.uppercase() }
        }.getOrDefault(emptyList())
    }
}
