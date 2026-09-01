package com.juren233.easyopen.shared.platform

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding
import com.juren233.easyopen.shared.state.EasyOpenBleOperation
import com.juren233.easyopen.shared.state.EasyOpenBleSnapshot
import com.juren233.easyopen.shared.state.EasyOpenConnectionStatus
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSUUID
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

/**
 * iOS CoreBluetooth transport seam.
 *
 * CoreBluetooth owns all native peripheral instances here. Only the stable
 * UUID string crosses the common boundary through DeviceBinding.IosPeripheral.
 * Service/characteristic selection is intentionally kept behind this type.
 * The transport now discovers a writable characteristic, a notify/indicate
 * characteristic, and enables notifications; command encoding/writing remains
 * a separate protocol step.
 */
class IosCoreBluetoothPort(
    serviceUuid: String = EasyOpenBleUuids.SERVICE,
    writeCharacteristicUuid: String = EasyOpenBleUuids.WRITE,
    notifyCharacteristicUuid: String = EasyOpenBleUuids.NOTIFY,
    onPeripheralDiscovered: ((DeviceBinding.IosPeripheral, String?, Int) -> Unit)? = null,
    onConnectionChanged: ((DeviceBinding.IosPeripheral, Boolean, String?) -> Unit)? = null,
    onTransportReady: ((DeviceBinding.IosPeripheral) -> Unit)? = null,
) : EasyOpenBlePort {
    private val _state = MutableStateFlow(EasyOpenBleSnapshot())
    override val state: StateFlow<EasyOpenBleSnapshot> = _state.asStateFlow()

    private val delegate = IosCoreBluetoothDelegate(
        serviceUuid = serviceUuid,
        writeCharacteristicUuid = writeCharacteristicUuid,
        notifyCharacteristicUuid = notifyCharacteristicUuid,
        onConnectionChanged = { binding, connected, message ->
            publishConnectionChanged(binding, connected, message)
            onConnectionChanged?.invoke(binding, connected, message)
        },
        onPeripheralDiscovered = { binding, name, rssi ->
            publishDiscovered(binding, rssi)
            onPeripheralDiscovered?.invoke(binding, name, rssi)
        },
        onTransportReady = { binding ->
            publishTransportReady(binding)
            onTransportReady?.invoke(binding)
        },
    )

    override fun startScan() {
        delegate.startScan()
        _state.value = _state.value.copy(
            operation = EasyOpenBleOperation.SCANNING,
            message = null,
        )
    }

    override fun stopScan() {
        delegate.stopScan()
        if (_state.value.operation == EasyOpenBleOperation.SCANNING) {
            _state.value = _state.value.copy(operation = EasyOpenBleOperation.IDLE)
        }
    }

    override fun connect(binding: DeviceBinding, profile: CoreDeviceProfile) {
        _state.value = _state.value.copy(
            operation = EasyOpenBleOperation.CONNECTING,
            connectionStatus = EasyOpenConnectionStatus.CONNECTING,
            activeBinding = binding,
            message = null,
        )
        delegate.connect(binding, profile)
    }

    override fun unlock(binding: DeviceBinding, profile: CoreDeviceProfile) {
        _state.value = _state.value.copy(
            operation = EasyOpenBleOperation.UNLOCKING,
            connectionStatus = EasyOpenConnectionStatus.CONNECTING,
            activeBinding = binding,
            message = null,
        )
        delegate.unlock(binding, profile)
    }

    internal fun publishDiscovered(binding: DeviceBinding.IosPeripheral, rssi: Int) {
        _state.value = _state.value.copy(
            connectionStatus = EasyOpenConnectionStatus.DISCOVERED,
            activeBinding = binding,
            rssi = rssi,
            message = null,
        )
    }

    internal fun publishTransportReady(binding: DeviceBinding.IosPeripheral) {
        _state.value = _state.value.copy(
            operation = EasyOpenBleOperation.READY,
            connectionStatus = EasyOpenConnectionStatus.CONNECTED,
            activeBinding = binding,
            message = null,
        )
    }

    internal fun publishConnectionChanged(
        binding: DeviceBinding.IosPeripheral,
        connected: Boolean,
        message: String?,
    ) {
        _state.value = _state.value.copy(
            operation = if (connected) {
                _state.value.operation.takeUnless { it == EasyOpenBleOperation.IDLE }
                    ?: EasyOpenBleOperation.CONNECTING
            } else {
                EasyOpenBleOperation.ERROR
            },
            connectionStatus = if (connected) {
                EasyOpenConnectionStatus.CONNECTED
            } else {
                EasyOpenConnectionStatus.NOT_FOUND
            },
            activeBinding = binding,
            message = message,
        )
    }
}

private class IosCoreBluetoothDelegate(
    private val serviceUuid: String,
    private val writeCharacteristicUuid: String,
    private val notifyCharacteristicUuid: String,
    private val onPeripheralDiscovered: ((DeviceBinding.IosPeripheral, String?, Int) -> Unit)?,
    private val onConnectionChanged: ((DeviceBinding.IosPeripheral, Boolean, String?) -> Unit)?,
    private val onTransportReady: ((DeviceBinding.IosPeripheral) -> Unit)?,
) : NSObject(), CBCentralManagerDelegateProtocol, CBPeripheralDelegateProtocol {
    private val centralManager = CBCentralManager(
        delegate = this,
        queue = dispatch_get_main_queue(),
    )
    private val peripherals = mutableMapOf<String, CBPeripheral>()
    private val pendingProfiles = mutableMapOf<String, CoreDeviceProfile>()
    private val writeCharacteristics = mutableMapOf<String, CBCharacteristic>()
    private val notifyCharacteristics = mutableMapOf<String, CBCharacteristic>()
    private val readyIdentifiers = mutableSetOf<String>()

    private val scanService: CBUUID
        get() = CBUUID.UUIDWithString(serviceUuid)

    private val writeUuid: CBUUID
        get() = CBUUID.UUIDWithString(writeCharacteristicUuid)

    private val notifyUuid: CBUUID
        get() = CBUUID.UUIDWithString(notifyCharacteristicUuid)

    fun startScan() {
        if (centralManager.state != CBManagerStatePoweredOn) return
        centralManager.scanForPeripheralsWithServices(
            serviceUUIDs = listOf(scanService),
            options = null,
        )
    }

    fun stopScan() {
        centralManager.stopScan()
    }

    fun connect(binding: DeviceBinding, profile: CoreDeviceProfile) {
        val identifier = (binding as? DeviceBinding.IosPeripheral)?.identifier ?: return
        val peripheral = resolvePeripheral(binding)
        if (peripheral == null) {
            onConnectionChanged?.invoke(
                DeviceBinding.IosPeripheral(identifier),
                false,
                "无法恢复已保存的 iOS 蓝牙设备，请重新扫描",
            )
            return
        }
        pendingProfiles[identifier] = profile.normalized()
        centralManager.connectPeripheral(peripheral, options = null)
    }

    fun unlock(binding: DeviceBinding, profile: CoreDeviceProfile) {
        connect(binding, profile)
    }

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        if (central.state != CBManagerStatePoweredOn) {
            central.stopScan()
        }
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber,
    ) {
        val identifier = didDiscoverPeripheral.identifier.UUIDString
        peripherals[identifier] = didDiscoverPeripheral
        onPeripheralDiscovered?.invoke(
            DeviceBinding.IosPeripheral(identifier),
            didDiscoverPeripheral.name,
            RSSI.intValue,
        )
    }

    override fun centralManager(
        central: CBCentralManager,
        didConnectPeripheral: CBPeripheral,
    ) {
        val binding = DeviceBinding.IosPeripheral(didConnectPeripheral.identifier.UUIDString)
        didConnectPeripheral.delegate = this
        didConnectPeripheral.discoverServices(listOf(scanService))
        onConnectionChanged?.invoke(binding, true, null)
    }

    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didFailToConnectPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        onConnectionChanged?.invoke(
            DeviceBinding.IosPeripheral(didFailToConnectPeripheral.identifier.UUIDString),
            false,
            error?.localizedDescription,
        )
    }

    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        val identifier = didDisconnectPeripheral.identifier.UUIDString
        pendingProfiles.remove(identifier)
        writeCharacteristics.remove(identifier)
        notifyCharacteristics.remove(identifier)
        readyIdentifiers.remove(identifier)
        onConnectionChanged?.invoke(
            DeviceBinding.IosPeripheral(identifier),
            false,
            error?.localizedDescription,
        )
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverServices: NSError?,
    ) {
        if (didDiscoverServices != null) return
        peripheral.services.orEmpty()
            .filterIsInstance<CBService>()
            .filter { service -> service.UUID == scanService }
            .forEach { service -> peripheral.discoverCharacteristics(listOf(writeUuid, notifyUuid), forService = service) }
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverCharacteristicsForService: CBService,
        error: NSError?,
    ) {
        if (error != null) return
        val identifier = peripheral.identifier.UUIDString
        didDiscoverCharacteristicsForService.characteristics.orEmpty()
            .filterIsInstance<CBCharacteristic>()
            .forEach { characteristic ->
                val properties = characteristic.properties
                if (characteristic.UUID == writeUuid && (
                        properties and CBCharacteristicPropertyWrite != 0uL ||
                            properties and CBCharacteristicPropertyWriteWithoutResponse != 0uL
                        )
                ) {
                    writeCharacteristics[identifier] = characteristic
                }
                if (characteristic.UUID == notifyUuid && (
                        properties and platform.CoreBluetooth.CBCharacteristicPropertyNotify != 0uL ||
                            properties and platform.CoreBluetooth.CBCharacteristicPropertyIndicate != 0uL
                        )
                ) {
                    notifyCharacteristics[identifier] = characteristic
                    peripheral.setNotifyValue(true, forCharacteristic = characteristic)
                }
            }

        pendingProfiles[identifier]?.let {
            // Both channels are required by the current Nordic UART-style
            // transport: one characteristic accepts commands and one reports
            // responses. Command encoding/writing is the next protocol step.
            if (writeCharacteristics[identifier] != null &&
                notifyCharacteristics[identifier] != null &&
                readyIdentifiers.add(identifier)
            ) {
                onTransportReady?.invoke(DeviceBinding.IosPeripheral(identifier))
            }
        }
    }

    private fun resolvePeripheral(binding: DeviceBinding): CBPeripheral? {
        val identifier = (binding as? DeviceBinding.IosPeripheral)?.identifier ?: return null
        peripherals[identifier]?.let { return it }
        val uuid = NSUUID(uUIDString = identifier)
        val peripheral = centralManager.retrievePeripheralsWithIdentifiers(listOf(uuid))
            .firstOrNull() as? CBPeripheral
        return peripheral?.also { peripherals[identifier] = it }
    }
}
