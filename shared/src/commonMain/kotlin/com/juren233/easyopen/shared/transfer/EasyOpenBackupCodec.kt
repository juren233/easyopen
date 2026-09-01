package com.juren233.easyopen.shared.transfer

import com.juren233.easyopen.data.AppSettings
import com.juren233.easyopen.data.AutoConnectSettings
import com.juren233.easyopen.shared.model.CoreDeviceProfile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Portable JSON backup codec shared by Android and iOS. */
@OptIn(ExperimentalSerializationApi::class)
object EasyOpenBackupCodec {
    private const val BACKUP_VERSION = 1

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    data class Snapshot(
        val profiles: List<CoreDeviceProfile>,
        val settings: AppSettings,
    )

    fun encode(
        profiles: List<CoreDeviceProfile>,
        settings: AppSettings,
    ): String = json.encodeToString(
        EasyOpenBackupEnvelope(
            version = BACKUP_VERSION,
            themeMode = settings.themeMode.coerceIn(0, 2),
            monetEnabled = settings.monetEnabled,
            autoUnlockOnAppOpen = settings.autoUnlockOnAppOpen,
            autoConnectEnabled = settings.autoConnectEnabled,
            autoConnectRange = AutoConnectSettings.normalizeRange(settings.autoConnectRange),
            customAutoConnectRssi = AutoConnectSettings.normalizeRssiThreshold(settings.customAutoConnectRssi),
            devices = profiles.map { profile ->
                EasyOpenTransferProfile.fromCoreProfile(
                    profile = profile,
                    androidMac = profile.hardwareMac,
                )
            },
        ),
    )

    fun decode(raw: String): Snapshot? = runCatching {
        val envelope = json.decodeFromString<EasyOpenBackupEnvelope>(raw)
        require(envelope.version == BACKUP_VERSION)
        val profiles = envelope.devices
            .map(EasyOpenTransferProfile::toCoreProfile)
            .filter { profile ->
                profile.password.length == 6 && profile.password.all(Char::isDigit)
            }
        require(profiles.isNotEmpty())
        Snapshot(
            profiles = profiles,
            settings = AppSettings(
                themeMode = envelope.themeMode.coerceIn(0, 2),
                monetEnabled = envelope.monetEnabled,
                autoUnlockOnAppOpen = envelope.autoUnlockOnAppOpen,
                autoConnectEnabled = envelope.autoConnectEnabled,
                autoConnectRange = AutoConnectSettings.normalizeRange(envelope.autoConnectRange),
                customAutoConnectRssi = AutoConnectSettings.normalizeRssiThreshold(envelope.customAutoConnectRssi),
            ),
        )
    }.getOrNull()
}
