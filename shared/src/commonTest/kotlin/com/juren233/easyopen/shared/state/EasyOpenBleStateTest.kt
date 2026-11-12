package com.juren233.easyopen.shared.state

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EasyOpenBleStateTest {
    private val binding = DeviceBinding.AndroidMac("AA:BB:CC:DD:EE:FF")
    private val profile = CoreDeviceProfile(password = "123456")

    @Test
    fun anyLinkStatusCanUnlockWhenIdle() {
        EasyOpenConnectionStatus.entries.forEach { status ->
            assertTrue(
                EasyOpenBleSnapshot(connectionStatus = status).canUnlock(binding, profile),
                status.name,
            )
        }
    }

    @Test
    fun connectingOrNotFoundOperationStatesCanUnlock() {
        assertTrue(
            EasyOpenBleSnapshot(
                operation = EasyOpenBleOperation.CONNECTING,
                connectionStatus = EasyOpenConnectionStatus.CONNECTING,
            ).canUnlock(binding, profile),
        )
        assertTrue(
            EasyOpenBleSnapshot(
                connectionStatus = EasyOpenConnectionStatus.NOT_FOUND,
            ).canUnlock(binding, profile),
        )
    }

    @Test
    fun unavailableBluetoothCannotUnlock() {
        assertFalse(
            EasyOpenBleSnapshot(
                bluetoothAvailable = false,
                connectionStatus = EasyOpenConnectionStatus.CONNECTED,
            ).canUnlock(binding, profile),
        )
    }

    @Test
    fun unlockingOrPairingOrIncompleteProfileCannotUnlock() {
        assertFalse(
            EasyOpenBleSnapshot(
                operation = EasyOpenBleOperation.UNLOCKING,
                connectionStatus = EasyOpenConnectionStatus.CONNECTED,
            ).canUnlock(binding, profile),
        )
        assertFalse(
            EasyOpenBleSnapshot(
                operation = EasyOpenBleOperation.PAIRING,
            ).canUnlock(binding, profile),
        )
        assertFalse(
            EasyOpenBleSnapshot(
                connectionStatus = EasyOpenConnectionStatus.CONNECTED,
            ).canUnlock(binding, profile.copy(password = "")),
        )
    }

    @Test
    fun unlockingFlagFollowsOperationOnly() {
        assertTrue(EasyOpenBleSnapshot(operation = EasyOpenBleOperation.UNLOCKING).unlocking)
        assertFalse(
            EasyOpenBleSnapshot(connectionStatus = EasyOpenConnectionStatus.CONNECTING).unlocking,
        )
    }
}
