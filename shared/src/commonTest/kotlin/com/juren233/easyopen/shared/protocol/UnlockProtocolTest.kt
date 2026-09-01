package com.juren233.easyopen.shared.protocol

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnlockProtocolTest {
    @Test
    fun md5MatchesKnownVectors() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", UnlockProtocol.md5(""))
        assertEquals("e10adc3949ba59abbe56e057f20f883e", UnlockProtocol.md5("123456"))
    }

    @Test
    fun aesMatchesNist128BitVector() {
        val key = "000102030405060708090a0b0c0d0e0f".hexToBytes()
        val input = "00112233445566778899aabbccddeeff".hexToBytes()
        assertEquals(
            "69c4e0d86a7b0430d8cdb78070b4c55a",
            Aes128.encryptEcbNoPadding(key, input).toHexForTest(),
        )
    }

    @Test
    fun passwordTokenMatchesOriginalRules() {
        assertEquals("0123456789abcdef", UnlockProtocol.passwordToken("0123456789abcdef"))
        assertEquals("89abcdef01234567", UnlockProtocol.passwordToken("0123456789abcdef0123456789abcdef"))
        assertEquals("49ba59abbe56e057", UnlockProtocol.passwordToken("123456"))
    }

    @Test
    fun packetsAreBlockAlignedAndStable() {
        val profile = CoreDeviceProfile(password = "123456")
        val passwordPacket = UnlockProtocol.buildPasswordPacket("123456", epochSeconds = 1_700_000_000)
        val openPacket = UnlockProtocol.buildOpenPacket(profile, epochSeconds = 1_700_000_000)

        assertEquals(0, passwordPacket.size % 16)
        assertEquals(0, openPacket.size % 16)
        assertTrue(passwordPacket.any { it.toInt() !in 0x20..0x7e })
        assertTrue(openPacket.any { it.toInt() !in 0x20..0x7e })
        assertContentEquals(passwordPacket, UnlockProtocol.buildPasswordPacket("123456", 1_700_000_000))
        assertEquals(
            "1e0fc9fd88c18ed35e1e741c74bd5898746ab59b6e7fcb8794a5e932ee04eccc3c3a33af778caddc6f75744bf4fee7e7617141e4132fb39c5811d9f4590ca8dd",
            passwordPacket.toHexForTest(),
        )
        assertEquals(
            "1e0fc9fd88c18ed35e1e741c74bd58983e75d84a7b8b89eaba10dc1d6a31f4ad57e0c535bc44c2b190b301efc257a8361dd143adb2b87febe66989de87735eb8",
            openPacket.toHexForTest(),
        )
    }

    @Test
    fun responseParserAcceptsAsciiAndHexOk() {
        assertTrue(UnlockProtocol.isSuccess("OK".encodeToByteArray()))
        assertTrue(UnlockProtocol.isSuccess(byteArrayOf(0x4f, 0x4b)))
        assertEquals("OK", UnlockProtocol.responseSummary("OK".encodeToByteArray()))
        assertTrue(UnlockProtocol.isFailure("ERROR".encodeToByteArray()))
        assertTrue(UnlockProtocol.isFailure("FAIL".encodeToByteArray()))
        assertEquals("HEX 0102", UnlockProtocol.responseSummary(byteArrayOf(0x01, 0x02)))
    }

    @Test
    fun batteryParserKeepsOriginalAdvertisementRules() {
        val scanRecord = byteArrayOf(2, 0x01, 0x06, 4, 0xff.toByte(), 0x00, 0x00, 0x05)
        assertEquals(5, BatteryAdvertisementParser.parse(scanRecord))
        assertEquals(5, UnlockProtocol.parseBatteryLevel(scanRecord))
        assertEquals(null, BatteryAdvertisementParser.parse(byteArrayOf(5, 0xff.toByte(), 0x00)))
    }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHexForTest(): String = joinToString(separator = "") {
        val value = it.toInt() and 0xff
        val digits = "0123456789abcdef"
        "${digits[value ushr 4]}${digits[value and 0x0f]}"
    }
}
