package com.juren233.easyopen.shared.transfer

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EasyOpenTransferTest {
    private val profile = CoreDeviceProfile(
        name = "车库",
        password = "123456",
        attribute = 1,
        openTimeMs = 700,
        waitTimeMs = 2_200,
        closeTimeMs = 650,
        batteryLevel = 4,
    )

    @Test
    fun iosProjectionDoesNotCarryAnyPlatformBinding() {
        val transfer = EasyOpenTransferProfile.fromCoreProfile(profile)

        assertNull(transfer.androidMac)
        assertNull(transfer.legacyAndroidMac)
        assertEquals(profile, transfer.toCoreProfile())
    }

    @Test
    fun androidProjectionCarriesOnlyOptionalMacBinding() {
        val transfer = EasyOpenTransferProfile.fromCoreProfile(profile, "AA:BB:CC:DD:EE:FF")

        assertEquals("AA:BB:CC:DD:EE:FF", transfer.androidMac)
        assertEquals(profile, transfer.toCoreProfile())
    }

    @Test
    fun legacyAddressIsReadableButNotEmittedByNewProjection() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val legacy = json.decodeFromString<EasyOpenTransferProfile>(
            """{"name":"车库","address":"AA:BB:CC:DD:EE:FF","password":"123456","attribute":0,"openTimeMs":650,"waitTimeMs":2000,"closeTimeMs":600}""",
        )
        val currentJson = json.encodeToString(EasyOpenTransferProfile.fromCoreProfile(profile))

        assertEquals("AA:BB:CC:DD:EE:FF", legacy.resolvedAndroidMac())
        assertFalse(currentJson.contains("\"androidMac\""))
        assertFalse(currentJson.contains("AA:BB:CC:DD:EE:FF"))
    }
}
