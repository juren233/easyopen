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
    fun discoveredOrConnectedDeviceCanUnlockWhenIdle() {
        assertTrue(
            EasyOpenBleSnapshot(
                connectionStatus = EasyOpenConnectionStatus.DISCOVERED,
            ).canUnlock(binding, profile),
        )
        assertTrue(
            EasyOpenBleSnapshot(
                connectionStatus = EasyOpenConnectionStatus.CONNECTED,
            ).canUnlock(binding, profile),
        )
    }

    @Test
    fun unavailableBluetoothCannotUnlockEvenWhenLinkSnapshotLooksReady() {
        assertFalse(
            EasyOpenBleSnapshot(
                bluetoothAvailable = false,
                connectionStatus = EasyOpenConnectionStatus.CONNECTED,
            ).canUnlock(binding, profile),
        )
    }

    @Test
    fun connectingBusyOrIncompleteProfileCannotUnlock() {
        assertFalse(
            EasyOpenBleSnapshot(
                connectionStatus = EasyOpenConnectionStatus.CONNECTING,
            ).canUnlock(binding, profile),
        )
        assertFalse(
            EasyOpenBleSnapshot(
                operation = EasyOpenBleOperation.UNLOCKING,
                connectionStatus = EasyOpenConnectionStatus.CONNECTED,
            ).canUnlock(binding, profile),
        )
        assertFalse(
            EasyOpenBleSnapshot(
                connectionStatus = EasyOpenConnectionStatus.CONNECTED,
            ).canUnlock(binding, profile.copy(password = "")),
        )
    }
}
