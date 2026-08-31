package com.juren233.easyopen.shared.platform

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding

/** Platform boundary for BLE. Android and iOS own their native objects locally. */
interface EasyOpenBlePort {
    fun startScan()
    fun stopScan()
    fun connect(binding: DeviceBinding)
    fun unlock(profile: CoreDeviceProfile)
}

/** Platform boundary for NFC. The common layer only owns the payload contract. */
interface EasyOpenNfcPort {
    fun startReading()
    fun stopReading()
    fun writeCurrentDevice()
}
