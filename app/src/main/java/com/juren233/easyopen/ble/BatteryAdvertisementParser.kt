package com.juren233.easyopen.ble

/** Android compatibility facade for the shared advertisement parser. */
object BatteryAdvertisementParser {
    fun parse(scanRecord: ByteArray?): Int? =
        com.juren233.easyopen.shared.protocol.BatteryAdvertisementParser.parse(scanRecord)
}
