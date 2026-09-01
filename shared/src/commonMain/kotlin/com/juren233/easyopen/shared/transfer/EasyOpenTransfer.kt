package com.juren233.easyopen.shared.transfer

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Portable opener configuration used by QR and backup adapters.
 *
 * [androidMac] is deliberately optional and is the only platform binding that
 * this contract may carry. iOS adapters must leave it null and must never add
 * a CBPeripheral identifier. The legacy [address] JSON name is accepted only
 * for decoding old Android backups/shares.
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
                androidMac = androidMac?.trim()?.takeIf(String::isNotBlank),
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
