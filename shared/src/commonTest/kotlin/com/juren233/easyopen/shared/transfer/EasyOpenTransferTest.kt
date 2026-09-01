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
    fun androidProjectionCarriesHardwareMacForMatching() {
        val transfer = EasyOpenTransferProfile.fromCoreProfile(profile, "AA:BB:CC:DD:EE:FF")

        assertEquals("AA:BB:CC:DD:EE:FF", transfer.androidMac)
        assertEquals("AA:BB:CC:DD:EE:FF", transfer.toCoreProfile().hardwareMac)
        assertEquals(profile.copy(hardwareMac = "AA:BB:CC:DD:EE:FF"), transfer.toCoreProfile())
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

    @Test
    fun portableBackupDoesNotCarryPlatformBindings() {
        val envelope = EasyOpenBackupEnvelope(
            version = 1,
            themeMode = 2,
            monetEnabled = true,
            autoConnectRange = 1,
            customAutoConnectRssi = -80,
            devices = listOf(EasyOpenTransferProfile.fromCoreProfile(profile)),
        )
        val json = Json { encodeDefaults = true; explicitNulls = false }
        val raw = json.encodeToString(envelope)
        val decoded = json.decodeFromString<EasyOpenBackupEnvelope>(raw)

        assertFalse(raw.contains("androidMac"))
        assertFalse(raw.contains("address"))
        assertEquals(profile, decoded.devices.single().toCoreProfile())
    }


    @Test
    fun backupCodecCarriesHardwareMacButNotLocalBinding() {
        val profileWithIdentity = profile.copy(hardwareMac = "E0:E6:6F:3C:A5:B2")
        val raw = EasyOpenBackupCodec.encode(
            profiles = listOf(profileWithIdentity),
            settings = com.juren233.easyopen.data.AppSettings(),
        )
        val restored = EasyOpenBackupCodec.decode(raw)

        assertEquals("E0:E6:6F:3C:A5:B2", restored?.profiles?.single()?.hardwareMac)
        assertFalse(raw.contains("CBPeripheral"))
        assertFalse(raw.contains("identifier"))
    }

    @Test
    fun backupCodecRoundTripsProfilesAndSettings() {
        val settings = com.juren233.easyopen.data.AppSettings(
            themeMode = 2,
            monetEnabled = true,
            autoUnlockOnAppOpen = true,
            autoConnectEnabled = false,
            autoConnectRange = 3,
            customAutoConnectRssi = -92,
        )
        val raw = EasyOpenBackupCodec.encode(listOf(profile), settings)
        val restored = EasyOpenBackupCodec.decode(raw)

        assertEquals(profile, restored?.profiles?.single())
        assertEquals(settings, restored?.settings)
        assertFalse(raw.contains("androidMac"))
        assertFalse(raw.contains("address"))
    }

}
