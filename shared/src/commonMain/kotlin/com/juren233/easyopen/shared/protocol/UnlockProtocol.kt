package com.juren233.easyopen.shared.protocol

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.platform.currentEpochSeconds

/**
 * Platform-neutral implementation of the legacy YiLa/Macronum wire protocol.
 *
 * The Android app and the iOS CoreBluetooth transport must produce identical
 * packets and classify identical responses. Keep this object free of platform
 * APIs so protocol regressions are covered by common tests.
 */
object UnlockProtocol {
    private const val AES_KEY = "Fx4k6AWivOsLE4NI"
    private const val PASSWORD_PREFIX = "A:PW;P:"
    private const val OPEN_PREFIX = "A:OPEN;P:"
    private val hex16 = Regex("[0-9a-fA-F]{16}")
    private val hex32 = Regex("[0-9a-fA-F]{32}")

    fun buildPasswordPacket(
        password: String,
        epochSeconds: Long = currentEpochSeconds(),
    ): ByteArray {
        require(password.matches(Regex("^[0-9]{6}$"))) { "开门器密码必须是 6 位数字" }
        val token = passwordToken(password)
        return encrypt("$epochSeconds$token$PASSWORD_PREFIX$token;".encodeToByteArray())
    }

    fun buildOpenPacket(
        profile: CoreDeviceProfile,
        epochSeconds: Long = currentEpochSeconds(),
    ): ByteArray {
        val normalized = profile.normalized()
        require(normalized.password.isNotBlank()) { "请先配置开门器密码" }
        val sign = if (normalized.attribute == 1) '-' else '+'
        val command = "$OPEN_PREFIX$sign ${normalized.openTimeMs},${normalized.waitTimeMs},${normalized.closeTimeMs};"
        val token = passwordToken(normalized.password)
        return encrypt("$epochSeconds$token$command".encodeToByteArray())
    }

    fun passwordToken(password: String): String = when {
        password.matches(hex16) -> password
        password.matches(hex32) -> password.substring(8, 24)
        else -> md5(password).substring(8, 24)
    }

    fun md5(value: String): String = Md5.digest(value.encodeToByteArray()).toHexLowercase()

    fun parseBatteryLevel(scanRecord: ByteArray?): Int? = BatteryAdvertisementParser.parse(scanRecord)

    fun responseText(bytes: ByteArray): String = bytes.decodeToString()
        .filter { it == '\t' || it == '\n' || it == '\r' || it in ' '..'~' }
        .trim()

    fun responseHex(bytes: ByteArray): String = bytes.toHexUppercase()

    fun isSuccess(bytes: ByteArray): Boolean {
        val ascii = responseText(bytes).uppercase()
        val hex = responseHex(bytes)
        return ascii.contains("OK") || hex.contains("4F4B")
    }

    fun isFailure(bytes: ByteArray): Boolean {
        val ascii = responseText(bytes).uppercase()
        val hex = responseHex(bytes)
        return ascii.contains("ERROR") || ascii.contains("FAIL") || hex.contains("4552524F52")
    }

    fun responseSummary(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "空响应"
        val ascii = responseText(bytes)
        return if (ascii.isNotBlank()) ascii else "HEX ${responseHex(bytes)}"
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val paddedLength = ((plaintext.size + 15) / 16) * 16
        return Aes128.encryptEcbNoPadding(
            key = AES_KEY.encodeToByteArray(),
            input = plaintext.copyOf(paddedLength),
        )
    }
}

private fun ByteArray.toHexLowercase(): String = joinToString(separator = "") { byte ->
    val value = byte.toInt() and 0xff
    val high = "0123456789abcdef"[value ushr 4]
    val low = "0123456789abcdef"[value and 0x0f]
    "$high$low"
}


private fun ByteArray.toHexUppercase(): String = joinToString(separator = "") { byte ->
    val value = byte.toInt() and 0xff
    val high = "0123456789ABCDEF"[value ushr 4]
    val low = "0123456789ABCDEF"[value and 0x0f]
    "$high$low"
}
