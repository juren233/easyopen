package com.juren233.easyopen.ble

import com.juren233.easyopen.data.DeviceProfile
import com.juren233.easyopen.shared.model.CoreDeviceProfile

/**
 * Android compatibility facade for the shared YiLa/Macronum protocol.
 *
 * Keep this type while Android callers migrate; packet generation and response
 * parsing now live in commonMain so iOS and Android cannot drift.
 */
object UnlockProtocol {
    fun buildPasswordPacket(
        password: String,
        epochSeconds: Long = System.currentTimeMillis() / 1_000L,
    ): ByteArray = com.juren233.easyopen.shared.protocol.UnlockProtocol.buildPasswordPacket(password, epochSeconds)

    fun buildOpenPacket(
        profile: DeviceProfile,
        epochSeconds: Long = System.currentTimeMillis() / 1_000L,
    ): ByteArray = com.juren233.easyopen.shared.protocol.UnlockProtocol.buildOpenPacket(
        profile = CoreDeviceProfile(
            name = profile.name,
            password = profile.password,
            attribute = profile.attribute,
            openTimeMs = profile.openTimeMs,
            waitTimeMs = profile.waitTimeMs,
            closeTimeMs = profile.closeTimeMs,
            batteryLevel = profile.batteryLevel,
        ),
        epochSeconds = epochSeconds,
    )

    fun passwordToken(password: String): String =
        com.juren233.easyopen.shared.protocol.UnlockProtocol.passwordToken(password)

    fun md5(value: String): String =
        com.juren233.easyopen.shared.protocol.UnlockProtocol.md5(value)

    fun parseBatteryLevel(scanRecord: ByteArray?): Int? =
        com.juren233.easyopen.shared.protocol.UnlockProtocol.parseBatteryLevel(scanRecord)

    fun responseText(bytes: ByteArray): String =
        com.juren233.easyopen.shared.protocol.UnlockProtocol.responseText(bytes)

    fun responseHex(bytes: ByteArray): String =
        com.juren233.easyopen.shared.protocol.UnlockProtocol.responseHex(bytes)

    fun isSuccess(bytes: ByteArray): Boolean =
        com.juren233.easyopen.shared.protocol.UnlockProtocol.isSuccess(bytes)

    fun isFailure(bytes: ByteArray): Boolean =
        com.juren233.easyopen.shared.protocol.UnlockProtocol.isFailure(bytes)

    fun responseSummary(bytes: ByteArray): String =
        com.juren233.easyopen.shared.protocol.UnlockProtocol.responseSummary(bytes)
}
