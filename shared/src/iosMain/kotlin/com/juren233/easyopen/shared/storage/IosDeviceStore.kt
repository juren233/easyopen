package com.juren233.easyopen.shared.storage

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding
import com.juren233.easyopen.shared.state.EasyOpenSavedDevice
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/** iOS-only persistence for local CoreBluetooth bindings and opener profiles. */
internal object IosDeviceStore {
    private const val LEGACY_BINDING_KEY = "easyopen.ios.binding"
    private const val LEGACY_PROFILE_PREFIX = "easyopen.ios.profile."
    private const val DEVICES_KEY = "easyopen.ios.devices.v1"
    private const val ACTIVE_IDENTIFIER_KEY = "easyopen.ios.activeIdentifier"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class StoredDevice(
        val identifier: String,
        val name: String,
        val password: String,
        val attribute: Int,
        val openTimeMs: Int,
        val waitTimeMs: Int,
        val closeTimeMs: Int,
        val batteryLevel: Int? = null,
    )

    fun load(defaults: NSUserDefaults): List<EasyOpenSavedDevice> {
        val stored = defaults.stringForKey(DEVICES_KEY)
            ?.let { raw -> runCatching { json.decodeFromString<List<StoredDevice>>(raw) }.getOrNull() }
            .orEmpty()
            .mapNotNull(::toSavedDevice)
            .distinctBy { it.iosIdentifier().lowercase() }
        if (stored.isNotEmpty()) return stored

        val legacyBinding = defaults.stringForKey(LEGACY_BINDING_KEY)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let(DeviceBinding::IosPeripheral)
            ?: return emptyList()
        val migrated = listOf(EasyOpenSavedDevice(legacyBinding, loadLegacyProfile(defaults)))
        save(defaults, migrated, legacyBinding.identifier)
        return migrated
    }

    fun activeIdentifier(
        defaults: NSUserDefaults,
        devices: List<EasyOpenSavedDevice>,
    ): String = defaults.stringForKey(ACTIVE_IDENTIFIER_KEY)
        ?.trim()
        ?.takeIf { identifier -> devices.any { it.iosIdentifier().equals(identifier, ignoreCase = true) } }
        ?: devices.firstOrNull()?.iosIdentifier().orEmpty()

    fun save(
        defaults: NSUserDefaults,
        devices: List<EasyOpenSavedDevice>,
        activeIdentifier: String,
    ) {
        val normalized = devices
            .map { it.copy(profile = it.profile.normalized()) }
            .filter { it.iosIdentifier().isNotBlank() }
            .distinctBy { it.iosIdentifier().lowercase() }
        defaults.setObject(
            json.encodeToString(
                normalized.map { device ->
                    StoredDevice(
                        identifier = device.iosIdentifier(),
                        name = device.profile.name,
                        password = device.profile.password,
                        attribute = device.profile.attribute,
                        openTimeMs = device.profile.openTimeMs,
                        waitTimeMs = device.profile.waitTimeMs,
                        closeTimeMs = device.profile.closeTimeMs,
                        batteryLevel = device.profile.batteryLevel,
                    )
                },
            ),
            forKey = DEVICES_KEY,
        )
        defaults.setObject(
            normalized.firstOrNull { it.iosIdentifier().equals(activeIdentifier, ignoreCase = true) }
                ?.iosIdentifier()
                ?: normalized.firstOrNull()?.iosIdentifier().orEmpty(),
            forKey = ACTIVE_IDENTIFIER_KEY,
        )
    }

    private fun EasyOpenSavedDevice.iosIdentifier(): String =
        (binding as? DeviceBinding.IosPeripheral)?.identifier.orEmpty()

    private fun toSavedDevice(stored: StoredDevice): EasyOpenSavedDevice? {
        val identifier = stored.identifier.trim()
        if (identifier.isBlank()) return null
        return EasyOpenSavedDevice(
            binding = DeviceBinding.IosPeripheral(identifier),
            profile = CoreDeviceProfile(
                name = stored.name,
                password = stored.password,
                attribute = stored.attribute,
                openTimeMs = stored.openTimeMs,
                waitTimeMs = stored.waitTimeMs,
                closeTimeMs = stored.closeTimeMs,
                batteryLevel = stored.batteryLevel,
            ).normalized(),
        )
    }

    private fun loadLegacyProfile(defaults: NSUserDefaults): CoreDeviceProfile = CoreDeviceProfile(
        name = defaults.stringForKey(LEGACY_PROFILE_PREFIX + "name") ?: "我的开门器",
        password = defaults.stringForKey(LEGACY_PROFILE_PREFIX + "password") ?: "",
        attribute = defaults.integerForKey(LEGACY_PROFILE_PREFIX + "attribute").toInt(),
        openTimeMs = defaults.integerForKey(LEGACY_PROFILE_PREFIX + "openTimeMs").toInt().takeIf { it > 0 } ?: 650,
        waitTimeMs = defaults.integerForKey(LEGACY_PROFILE_PREFIX + "waitTimeMs").toInt().takeIf { it > 0 } ?: 2_000,
        closeTimeMs = defaults.integerForKey(LEGACY_PROFILE_PREFIX + "closeTimeMs").toInt().takeIf { it > 0 } ?: 600,
    ).normalized()
}
