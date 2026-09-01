package com.juren233.easyopen.shared.transfer

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Portable opener configuration used by QR and backup adapters.
 *
 * [androidMac] is the hardware MAC mirrored in the opener's Manufacturer Data.
 * It is an optional cross-platform matching key: Android also uses the same
 * value as its local connection address, while iOS still stores its own
 * CBPeripheral identifier separately. The legacy [address] JSON name remains
 * accepted for decoding old Android backups/shares.
 */
@Serializable
data class EasyOpenTransferProfile(
    val name: String,
    val androidMac: String? = null,
    @SerialName("address") val legacyAndroidMac: String? = null,
    val password: String,
    val attribute: Int,
    val openTimeMs: Int,
    val waitTimeMs: Int,
    val closeTimeMs: Int,
    val batteryLevel: Int? = null,
) {
    fun toCoreProfile(): CoreDeviceProfile = CoreDeviceProfile(
        name = name,
        password = password,
        attribute = attribute,
        openTimeMs = openTimeMs,
        waitTimeMs = waitTimeMs,
        closeTimeMs = closeTimeMs,
        batteryLevel = batteryLevel,
        hardwareMac = resolvedAndroidMac(),
    ).normalized()

    fun resolvedAndroidMac(): String? = (androidMac ?: legacyAndroidMac)
        ?.trim()
        ?.takeIf(String::isNotBlank)

    companion object {
        fun fromCoreProfile(
            profile: CoreDeviceProfile,
            androidMac: String? = null,
        ): EasyOpenTransferProfile {
            val normalized = profile.normalized()
            return EasyOpenTransferProfile(
                name = normalized.name,
                androidMac = androidMac?.trim()?.takeIf(String::isNotBlank)
                    ?: normalized.hardwareMac?.trim()?.takeIf(String::isNotBlank),
                password = normalized.password,
                attribute = normalized.attribute,
                openTimeMs = normalized.openTimeMs,
                waitTimeMs = normalized.waitTimeMs,
                closeTimeMs = normalized.closeTimeMs,
                batteryLevel = normalized.batteryLevel,
            )
        }
    }
}

@Serializable
data class EasyOpenTransferEnvelope(
    val version: Int,
    val devices: List<EasyOpenTransferProfile>,
)

/** Cross-platform backup envelope. Platform bindings are intentionally optional. */
@Serializable
data class EasyOpenBackupEnvelope(
    val version: Int,
    val activeAddress: String? = null,
    val activeAndroidMac: String? = null,
    val themeMode: Int,
    val monetEnabled: Boolean,
    val autoUnlockOnAppOpen: Boolean = false,
    val autoConnectEnabled: Boolean = true,
    val autoConnectRange: Int,
    val customAutoConnectRssi: Int,
    val devices: List<EasyOpenTransferProfile>,
)
