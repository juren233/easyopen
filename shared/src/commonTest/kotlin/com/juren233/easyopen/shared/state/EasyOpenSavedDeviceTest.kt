package com.juren233.easyopen.shared.state

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class EasyOpenSavedDeviceTest {
    private val first = EasyOpenSavedDevice(
        binding = DeviceBinding.IosPeripheral("A1"),
        profile = CoreDeviceProfile(name = "车库"),
    )
    private val second = EasyOpenSavedDevice(
        binding = DeviceBinding.IosPeripheral("B2"),
        profile = CoreDeviceProfile(name = "侧门"),
    )

    @Test
    fun upsertReplacesSameIosIdentifierIgnoringCase() {
        val replacement = first.copy(profile = CoreDeviceProfile(name = "新车库"))

        val result = upsertSavedDevice(listOf(first, second), replacement)

        assertEquals(listOf(replacement, second), result)
    }

    @Test
    fun activeFallsBackToFirstWhenStoredIdentifierIsMissing() {
        assertSame(first, activeSavedDevice(listOf(first, second), "missing"))
        assertSame(second, activeSavedDevice(listOf(first, second), "b2"))
    }
}
