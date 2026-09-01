package com.juren233.easyopen.shared.protocol

/**
 * Identity fields emitted by the current opener in Manufacturer Specific Data.
 *
 * The observed payload is six MAC bytes in reverse order followed by the
 * existing one-byte battery level. Android exposes the company ID separately;
 * iOS may include it in the NSData value, so both forms are accepted here.
 */
data class EasyOpenAdvertisementIdentity(
    val hardwareMac: String,
    val batteryLevel: Int?,
)

object EasyOpenAdvertisementParser {
    const val COMPANY_ID = 0x7777
    private const val MAC_BYTES = 6

    fun parseAndroidManufacturerData(
        companyId: Int,
        data: ByteArray,
    ): EasyOpenAdvertisementIdentity? =
        if (companyId == COMPANY_ID) parsePayload(data, 0) else null

    fun parseIosManufacturerData(data: ByteArray): EasyOpenAdvertisementIdentity? {
        val payloadOffset = if (data.size >= MAC_BYTES + 3 && littleEndianU16(data, 0) == COMPANY_ID) 2 else 0
        return parsePayload(data, payloadOffset)
    }

    private fun parsePayload(
        data: ByteArray,
        offset: Int,
    ): EasyOpenAdvertisementIdentity? {
        if (offset < 0 || data.size < offset + MAC_BYTES) return null
        val mac = data
            .copyOfRange(offset, offset + MAC_BYTES)
            .reversedArray()
            .joinToString(":") { it.toHexByte() }
        val battery = data
            .getOrNull(offset + MAC_BYTES)
            ?.toInt()
            ?.and(0xFF)
            ?.takeIf { it in 1..5 }
        return EasyOpenAdvertisementIdentity(
            hardwareMac = mac,
            batteryLevel = battery,
        )
    }

    private fun Byte.toHexByte(): String {
        val value = toInt() and 0xFF
        val digits = "0123456789ABCDEF"
        return "${digits[value ushr 4]}${digits[value and 0x0F]}"
    }

    private fun littleEndianU16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8)
}
