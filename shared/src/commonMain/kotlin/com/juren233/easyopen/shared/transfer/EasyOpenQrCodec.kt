package com.juren233.easyopen.shared.transfer

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.platform.secureRandomBytes
import com.juren233.easyopen.shared.protocol.AesGcm128
import com.juren233.easyopen.shared.protocol.Base64Url

/**
 * Cross-platform encrypted QR payload.
 *
 * Version 3 replaces the Android-only Java AES-GCM encoder. Android and iOS
 * now create and consume the same compact envelope. The hardware MAC is
 * optional in the format so an iOS profile never needs to leak its local
 * CBPeripheral UUID; normal paired devices include the manufacturer-data MAC.
 */
object EasyOpenQrCodec {
    private const val PREFIX = "EASYOPEN-SHARE:3:"
    private const val PAYLOAD_VERSION = 1
    private const val MAX_DEVICES = 255
    private const val MAX_NAME_BYTES = 255
    private const val NONCE_BYTES = 12
    private const val MAC_BYTES = 6
    private const val PASSWORD_BYTES = 6

    // QR sharing is transport obfuscation, not account-level encryption. Keep
    // the key in common code so every supported platform uses the same bytes.
    private val key = "EasyOpen QR key!".encodeToByteArray()

    fun encode(profiles: List<CoreDeviceProfile>): String {
        require(profiles.isNotEmpty()) { "At least one opener is required" }
        require(profiles.size <= MAX_DEVICES) { "Too many openers for a share" }
        val plaintext = BinaryWriter().apply {
            writeByte(PAYLOAD_VERSION)
            writeByte(profiles.size)
            profiles.forEach { profile -> writeProfile(this, profile) }
        }.toByteArray()
        val nonce = secureRandomBytes(NONCE_BYTES)
        val encrypted = AesGcm128.encrypt(key, nonce, plaintext)
        return PREFIX + Base64Url.encode(nonce) + "." + Base64Url.encode(encrypted)
    }

    fun decode(payload: String): List<CoreDeviceProfile>? = runCatching {
        require(payload.startsWith(PREFIX))
        val parts = payload.removePrefix(PREFIX).split('.', limit = 2)
        require(parts.size == 2)
        val nonce = Base64Url.decode(parts[0]) ?: error("Invalid QR nonce")
        require(nonce.size == NONCE_BYTES)
        val encrypted = Base64Url.decode(parts[1]) ?: error("Invalid QR body")
        val plaintext = AesGcm128.decrypt(key, nonce, encrypted) ?: return@runCatching null
        val reader = BinaryReader(plaintext)
        reader.readProfiles().also {
            require(reader.remaining == 0)
        }
    }.getOrNull()

    fun isPayload(payload: String): Boolean = payload.startsWith(PREFIX)

    private fun writeProfile(writer: BinaryWriter, profile: CoreDeviceProfile) {
        val normalized = profile.normalized()
        val mac = normalized.hardwareMac?.let(::parseMac)
        writer.writeByte(if (mac != null) 1 else 0)
        mac?.forEach { byte -> writer.writeByte(byte.toInt() and 0xff) }

        val password = normalized.password.encodeToByteArray()
        require(password.size == PASSWORD_BYTES && password.all { it in '0'.code..'9'.code }) {
            "Invalid opener password"
        }
        password.forEach { byte -> writer.writeByte(byte.toInt() and 0xff) }
        writer.writeByte(normalized.attribute.coerceIn(0, 1))
        writer.writeUnsignedShort(normalized.openTimeMs.coerceIn(0, 60_000))
        writer.writeUnsignedShort(normalized.waitTimeMs.coerceIn(0, 60_000))
        writer.writeUnsignedShort(normalized.closeTimeMs.coerceIn(0, 60_000))
        writer.writeByte(normalized.batteryLevel?.coerceIn(1, 5) ?: 0)

        val name = normalized.name.ifBlank { "我的开门器" }.encodeToByteArray()
        require(name.size <= MAX_NAME_BYTES) { "Opener name is too long" }
        writer.writeByte(name.size)
        writer.writeBytes(name)
    }

    private fun parseMac(value: String): ByteArray? {
        val normalized = value.trim().uppercase()
        val pieces = normalized.split(':')
        if (pieces.size != MAC_BYTES || pieces.any { it.length != 2 }) return null
        return runCatching { pieces.map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
    }

    private class BinaryWriter {
        private val bytes = mutableListOf<Byte>()

        fun writeByte(value: Int) {
            require(value in 0..255)
            bytes += value.toByte()
        }

        fun writeUnsignedShort(value: Int) {
            require(value in 0..65_535)
            writeByte(value ushr 8)
            writeByte(value and 0xff)
        }

        fun writeBytes(value: ByteArray) {
            value.forEach { bytes += it }
        }

        fun toByteArray(): ByteArray = bytes.toByteArray()
    }

    private class BinaryReader(private val bytes: ByteArray) {
        private var offset = 0
        val remaining: Int get() = bytes.size - offset

        fun readProfiles(): List<CoreDeviceProfile> {
            require(readUnsignedByte() == PAYLOAD_VERSION)
            val count = readUnsignedByte()
            require(count in 1..MAX_DEVICES)
            return buildList(count) { repeat(count) { add(readProfile()) } }
        }

        private fun readProfile(): CoreDeviceProfile {
            val flags = readUnsignedByte()
            require(flags and 0xfe == 0)
            val hardwareMac = if (flags and 1 != 0) {
                val bytes = readBytes(MAC_BYTES)
                bytes.joinToString(":") { (it.toInt() and 0xff).toString(16).padStart(2, '0').uppercase() }
            } else {
                null
            }
            val password = readBytes(PASSWORD_BYTES).decodeToString()
            require(password.length == PASSWORD_BYTES && password.all(Char::isDigit))
            val attribute = readUnsignedByte().coerceIn(0, 1)
            val openTimeMs = readUnsignedShort().coerceIn(0, 60_000)
            val waitTimeMs = readUnsignedShort().coerceIn(0, 60_000)
            val closeTimeMs = readUnsignedShort().coerceIn(0, 60_000)
            val batteryLevel = readUnsignedByte().takeIf { it in 1..5 }
            val nameLength = readUnsignedByte()
            require(nameLength <= MAX_NAME_BYTES)
            val name = readBytes(nameLength).decodeToString().ifBlank { "我的开门器" }
            return CoreDeviceProfile(
                name = name,
                password = password,
                attribute = attribute,
                openTimeMs = openTimeMs,
                waitTimeMs = waitTimeMs,
                closeTimeMs = closeTimeMs,
                batteryLevel = batteryLevel,
                hardwareMac = hardwareMac,
            ).normalized()
        }

        private fun readUnsignedByte(): Int {
            require(offset < bytes.size)
            return bytes[offset++].toInt() and 0xff
        }

        private fun readUnsignedShort(): Int = (readUnsignedByte() shl 8) or readUnsignedByte()

        private fun readBytes(size: Int): ByteArray {
            require(size >= 0 && offset + size <= bytes.size)
            return bytes.copyOfRange(offset, offset + size).also { offset += size }
        }
    }
}
