package com.juren233.easyopen.shared.state

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding

/** Platform-neutral BLE operation exposed to shared UI. */
enum class EasyOpenBleOperation {
    IDLE,
    SCANNING,
    CONNECTING,
    PAIRING,
    READY,
    UNLOCKING,
    PAIRED,
    SUCCESS,
    ERROR,
}

/** Availability and link state of the active opener. */
enum class EasyOpenConnectionStatus {
    NOT_FOUND,
    DISCOVERED,
    CONNECTING,
    CONNECTED,
}

/** A platform-neutral discovered peripheral entry used by shared pairing UI. */
data class EasyOpenBleDevice(
    val binding: DeviceBinding,
    val name: String,
    val rssi: Int,
)

/** A locally saved opener entry shown by the platform-neutral pairing UI. */
data class EasyOpenSavedDevice(
    val binding: DeviceBinding,
    val profile: CoreDeviceProfile,
)

/**
 * Immutable BLE state consumed by common UI.
 *
 * Platform-local identifiers stay wrapped in [DeviceBinding]. Native BLE
 * objects such as BluetoothDevice, BluetoothGatt and CBPeripheral never cross
 * this boundary.
 */
data class EasyOpenBleSnapshot(
    val operation: EasyOpenBleOperation = EasyOpenBleOperation.IDLE,
    val connectionStatus: EasyOpenConnectionStatus = EasyOpenConnectionStatus.NOT_FOUND,
    val activeBinding: DeviceBinding? = null,
    val rssi: Int? = null,
    val bluetoothAvailable: Boolean = true,
    val batteryLevels: Map<DeviceBinding, Int> = emptyMap(),
    val discoveredDevices: List<EasyOpenBleDevice> = emptyList(),
    val message: String? = null,
) {
    val busy: Boolean
        get() = operation == EasyOpenBleOperation.UNLOCKING ||
            connectionStatus == EasyOpenConnectionStatus.CONNECTING

    fun batteryLevel(binding: DeviceBinding, fallback: Int? = null): Int? =
        batteryLevels[binding] ?: fallback

    fun canUnlock(binding: DeviceBinding, profile: CoreDeviceProfile): Boolean =
        bluetoothAvailable &&
            profile.password.isNotBlank() &&
            binding.isUsable() &&
            connectionStatus in setOf(
                EasyOpenConnectionStatus.DISCOVERED,
                EasyOpenConnectionStatus.CONNECTED,
            ) &&
            !busy
}

fun DeviceBinding.isUsable(): Boolean = when (this) {
    is DeviceBinding.AndroidMac -> address.isNotBlank()
    is DeviceBinding.IosPeripheral -> identifier.isNotBlank()
}

fun DeviceBinding.displayIdentifier(): String = when (this) {
    is DeviceBinding.AndroidMac -> address
    is DeviceBinding.IosPeripheral -> identifier
}
