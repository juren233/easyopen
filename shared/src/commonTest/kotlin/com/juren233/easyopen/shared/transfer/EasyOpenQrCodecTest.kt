package com.juren233.easyopen.shared.transfer

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EasyOpenQrCodecTest {
    private val profile = CoreDeviceProfile(
        name = "车库门",
        password = "123456",
        attribute = 1,
        openTimeMs = 700,
        waitTimeMs = 2_200,
        closeTimeMs = 650,
        batteryLevel = 4,
        hardwareMac = "E0:E6:6F:3C:A5:B2",
    )

    @Test
    fun roundTripsBoundProfileWithoutLeakingPassword() {
        val payload = EasyOpenQrCodec.encode(listOf(profile))

        assertTrue(payload.startsWith("EASYOPEN-SHARE:3:"))
        assertTrue(payload.length < 180)
        assertTrue(profile.password !in payload)
        assertEquals(listOf(profile), EasyOpenQrCodec.decode(payload))
    }

    @Test
    fun roundTripsUnboundProfileWithoutCreatingLocalIosIdentifier() {
        val unbound = profile.copy(hardwareMac = null)

        val decoded = EasyOpenQrCodec.decode(EasyOpenQrCodec.encode(listOf(unbound)))

        assertNotNull(decoded)
        assertEquals(null, decoded.single().hardwareMac)
        assertEquals(unbound, decoded.single())
    }

    @Test
    fun rejectsTamperedPayload() {
        val payload = EasyOpenQrCodec.encode(listOf(profile))
        val bodyStart = payload.lastIndexOf('.') + 1
        check(bodyStart in 1 until payload.length)
        val originalBody = payload.substring(bodyStart)
        val replacement = if (originalBody.first() == 'A') 'B' else 'A'
        val tampered = payload.substring(0, bodyStart) + replacement + originalBody.drop(1)

        assertNull(EasyOpenQrCodec.decode(tampered))
    }
}
