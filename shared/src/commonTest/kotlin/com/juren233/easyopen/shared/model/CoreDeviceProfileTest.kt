package com.juren233.easyopen.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoreDeviceProfileTest {
    @Test
    fun normalizedKeepsOnlyProtocolIndependentFields() {
        val normalized = CoreDeviceProfile(
            name = "  门口  ",
            attribute = 8,
            openTimeMs = 70_000,
            waitTimeMs = -1,
            closeTimeMs = 600,
            batteryLevel = 9,
        ).normalized()

        assertEquals("门口", normalized.name)
        assertEquals(1, normalized.attribute)
        assertEquals(60_000, normalized.openTimeMs)
        assertEquals(0, normalized.waitTimeMs)
        assertNull(normalized.batteryLevel)
    }
}
