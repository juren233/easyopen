package com.juren233.easyopen.shared.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EasyOpenAdvertisementParserTest {
    @Test
    fun parsesObservedAndroidManufacturerPayload() {
        val identity = EasyOpenAdvertisementParser.parseAndroidManufacturerData(
            companyId = 0x7777,
            data = byteArrayOf(
                0xB2.toByte(), 0xA5.toByte(), 0x3C, 0x6F, 0xE6.toByte(), 0xE0.toByte(), 0x04,
            ),
        )

        assertEquals("E0:E6:6F:3C:A5:B2", identity?.hardwareMac)
        assertEquals(4, identity?.batteryLevel)
    }

    @Test
    fun parsesIosPayloadWithCompanyIdPrefix() {
        val identity = EasyOpenAdvertisementParser.parseIosManufacturerData(
            byteArrayOf(
                0x77, 0x77,
                0xB2.toByte(), 0xA5.toByte(), 0x3C, 0x6F, 0xE6.toByte(), 0xE0.toByte(), 0x04,
            ),
        )

        assertEquals("E0:E6:6F:3C:A5:B2", identity?.hardwareMac)
        assertEquals(4, identity?.batteryLevel)
    }

    @Test
    fun rejectsUnknownCompanyId() {
        assertNull(
            EasyOpenAdvertisementParser.parseAndroidManufacturerData(
                companyId = 0x1234,
                data = ByteArray(7),
            ),
        )
    }
}
