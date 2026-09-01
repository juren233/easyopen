package com.juren233.easyopen.shared.protocol

/** Decodes the five-level battery marker used by the original opener advertisement. */
object BatteryAdvertisementParser {
    private const val AD_TYPE_MANUFACTURER_DATA = 0xFF

    /**
     * Walks raw AD structures, preferring the last byte of manufacturer data
     * when it is 1..5, and falls back to the last byte of the complete record.
     */
    fun parse(scanRecord: ByteArray?): Int? {
        if (scanRecord == null || scanRecord.isEmpty()) return null
        var offset = 0
        while (offset < scanRecord.size) {
            val length = scanRecord[offset].toInt() and 0xff
            if (length == 0) break
            val endExclusive = offset + 1 + length
            if (endExclusive <= offset || endExclusive > scanRecord.size) break
            val type = scanRecord.getOrNull(offset + 1)?.toInt()?.and(0xff)
            if (type == AD_TYPE_MANUFACTURER_DATA && length >= 2) {
                val battery = scanRecord[endExclusive - 1].toInt() and 0xff
                if (battery in 1..5) return battery
            }
            offset = endExclusive
        }
        return (scanRecord.last().toInt() and 0xff).takeIf { it in 1..5 }
    }
}
