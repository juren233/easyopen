package com.juren233.easyopen.ble

import com.juren233.easyopen.data.DeviceProfile
import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding
import com.juren233.easyopen.shared.platform.EasyOpenBlePort
import com.juren233.easyopen.shared.state.EasyOpenBleDevice
import com.juren233.easyopen.shared.state.EasyOpenBleOperation
import com.juren233.easyopen.shared.state.EasyOpenBleSnapshot
import com.juren233.easyopen.shared.state.EasyOpenConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Android implementation of the shared BLE boundary.
 *
 * [BleDoorController] remains the Android owner of BluetoothGatt and
 * BluetoothDevice. This adapter translates its flows into platform-neutral
 * state and translates common commands back to Android profiles.
 */
class AndroidBlePort(
    private val controller: BleDoorController,
    scope: CoroutineScope,
) : EasyOpenBlePort {
    override val state: StateFlow<EasyOpenBleSnapshot> = combine(
        controller.state,
        controller.openerConnection,
        controller.batteryLevels,
        controller.devices,
    ) { operation, connection, batteryLevels, devices ->
        snapshot(
            operation = operation,
            connection = connection,
            batteryLevels = batteryLevels,
            devices = devices,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = snapshot(
            operation = controller.state.value,
            connection = controller.openerConnection.value,
            batteryLevels = controller.batteryLevels.value,
            devices = controller.devices.value,
        ),
    )

    fun isBluetoothEnabled(): Boolean = controller.isBluetoothEnabled()

    override fun startScan() {
        controller.startScan()
    }

    override fun stopScan() {
        controller.stopScan()
    }

    override fun connect(binding: DeviceBinding, profile: CoreDeviceProfile) {
        val address = (binding as? DeviceBinding.AndroidMac)?.address ?: return
        controller.connect(profile.toAndroidProfile(address))
    }

    override fun pair(binding: DeviceBinding, profile: CoreDeviceProfile) {
        val address = (binding as? DeviceBinding.AndroidMac)?.address ?: return
        controller.pair(address, profile.password)
    }

    override fun unlock(binding: DeviceBinding, profile: CoreDeviceProfile) {
        val address = (binding as? DeviceBinding.AndroidMac)?.address ?: return
        controller.unlock(profile.toAndroidProfile(address))
    }

    private fun snapshot(
        operation: BleState,
        connection: OpenerConnectionSnapshot,
        batteryLevels: Map<String, Int>,
        devices: List<DiscoveredDevice>,
    ): EasyOpenBleSnapshot = EasyOpenBleSnapshot(
        operation = operation.toCommonOperation(),
        connectionStatus = connection.status.toCommonStatus(),
        activeBinding = connection.address
            .takeIf(String::isNotBlank)
            ?.let(::androidBinding),
        rssi = connection.rssi,
        bluetoothAvailable = controller.isBluetoothEnabled(),
        batteryLevels = batteryLevels.mapKeys { (address, _) -> androidBinding(address) },
        discoveredDevices = devices.map { device ->
            EasyOpenBleDevice(
                binding = androidBinding(device.device.address),
                name = device.name,
                rssi = device.rssi,
                hardwareMac = device.hardwareMac,
            )
        },
        message = operation.messageOrNull(),
    )
}

private fun androidBinding(address: String): DeviceBinding.AndroidMac =
    DeviceBinding.AndroidMac(address.trim().uppercase())

private fun BleState.toCommonOperation(): EasyOpenBleOperation = when (this) {
    BleState.Idle -> EasyOpenBleOperation.IDLE
    BleState.Scanning -> EasyOpenBleOperation.SCANNING
    is BleState.Connecting -> EasyOpenBleOperation.CONNECTING
    is BleState.Pairing -> EasyOpenBleOperation.PAIRING
    is BleState.Ready -> EasyOpenBleOperation.READY
    is BleState.Unlocking -> EasyOpenBleOperation.UNLOCKING
    is BleState.Paired -> EasyOpenBleOperation.PAIRED
    is BleState.Success -> EasyOpenBleOperation.SUCCESS
    is BleState.Error -> EasyOpenBleOperation.ERROR
}

private fun BleState.messageOrNull(): String? = when (this) {
    is BleState.Success -> message
    is BleState.Error -> message
    else -> null
}

private fun OpenerConnectionStatus.toCommonStatus(): EasyOpenConnectionStatus = when (this) {
    OpenerConnectionStatus.NOT_FOUND -> EasyOpenConnectionStatus.NOT_FOUND
    OpenerConnectionStatus.DISCOVERED -> EasyOpenConnectionStatus.DISCOVERED
    OpenerConnectionStatus.CONNECTING -> EasyOpenConnectionStatus.CONNECTING
    OpenerConnectionStatus.CONNECTED -> EasyOpenConnectionStatus.CONNECTED
}

private fun CoreDeviceProfile.toAndroidProfile(address: String): DeviceProfile = DeviceProfile(
    name = name,
    address = address.trim().uppercase(),
    password = password,
    attribute = attribute,
    openTimeMs = openTimeMs,
    waitTimeMs = waitTimeMs,
    closeTimeMs = closeTimeMs,
    batteryLevel = batteryLevel,
)
