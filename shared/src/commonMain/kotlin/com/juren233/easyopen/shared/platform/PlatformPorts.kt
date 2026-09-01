package com.juren233.easyopen.shared.platform

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding
import com.juren233.easyopen.shared.state.EasyOpenBleSnapshot
import kotlinx.coroutines.flow.StateFlow

/** Platform boundary for BLE. Android and iOS own their native objects locally. */
interface EasyOpenBlePort {
    val state: StateFlow<EasyOpenBleSnapshot>

    fun startScan()
    fun stopScan()
    fun connect(binding: DeviceBinding, profile: CoreDeviceProfile)
    fun unlock(binding: DeviceBinding, profile: CoreDeviceProfile)
}

/** Platform boundary for NFC. The common layer only owns the payload contract. */
interface EasyOpenNfcPort {
    fun startReading()
    fun stopReading()
    fun writeCurrentDevice()
}
