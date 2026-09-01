package com.juren233.easyopen.shared.platform

import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.model.DeviceBinding
import com.juren233.easyopen.shared.protocol.EasyOpenAdvertisementParser
import com.juren233.easyopen.shared.protocol.UnlockProtocol
import com.juren233.easyopen.shared.state.EasyOpenBleDevice
import com.juren233.easyopen.shared.state.EasyOpenBleOperation
import com.juren233.easyopen.shared.state.EasyOpenBleSnapshot
import com.juren233.easyopen.shared.state.EasyOpenConnectionStatus
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithoutResponse
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSUUID
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_SEC
import platform.darwin.NSObject
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/**
 * iOS CoreBluetooth transport and legacy protocol adapter.
 *
 * CoreBluetooth owns all native peripheral instances here. Only the stable
 * UUID string crosses the common boundary through DeviceBinding.IosPeripheral.
 * The shared protocol encoder is used for unlock commands; native NSData and
 * CBPeripheral objects never leave iosMain.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosCoreBluetoothPort(
    serviceUuid: String = EasyOpenBleUuids.SERVICE,
    writeCharacteristicUuid: String = EasyOpenBleUuids.WRITE,
    notifyCharacteristicUuid: String = EasyOpenBleUuids.NOTIFY,
    onPeripheralDiscovered: ((DeviceBinding.IosPeripheral, String?, Int, String?) -> Unit)? = null,
    onConnectionChanged: ((DeviceBinding.IosPeripheral, Boolean, String?) -> Unit)? = null,
    onTransportReady: ((DeviceBinding.IosPeripheral) -> Unit)? = null,
) : EasyOpenBlePort {
    private val _state = MutableStateFlow(EasyOpenBleSnapshot())
    override val state: StateFlow<EasyOpenBleSnapshot> = _state.asStateFlow()
    private var scanRequested = false

    private val delegate = IosCoreBluetoothDelegate(
        serviceUuid = serviceUuid,
        writeCharacteristicUuid = writeCharacteristicUuid,
        notifyCharacteristicUuid = notifyCharacteristicUuid,
        onConnectionChanged = { binding, connected, message ->
            publishConnectionChanged(binding, connected, message)
            onConnectionChanged?.invoke(binding, connected, message)
        },
        onPeripheralDiscovered = { binding, name, rssi, hardwareMac ->
            publishDiscovered(binding, name, rssi, hardwareMac)
            onPeripheralDiscovered?.invoke(binding, name, rssi, hardwareMac)
        },
        onTransportReady = { binding ->
            publishTransportReady(binding)
            onTransportReady?.invoke(binding)
        },
        onBluetoothAvailabilityChanged = { available ->
            _state.value = _state.value.copy(bluetoothAvailable = available)
            if (available) resumeScanAfterBluetoothReady()
        },
        onResponse = { binding, bytes ->
            publishResponse(binding, bytes)
        },
    )

    override fun startScan() {
        scanRequested = true
        if (!delegate.isPoweredOn()) {
            _state.value = _state.value.copy(
                operation = EasyOpenBleOperation.ERROR,
                connectionStatus = EasyOpenConnectionStatus.NOT_FOUND,
                bluetoothAvailable = false,
                message = "请先打开蓝牙并允许 EasyOpen 使用蓝牙",
            )
            return
        }
        delegate.startScan()
        _state.value = _state.value.copy(
            operation = EasyOpenBleOperation.SCANNING,
            bluetoothAvailable = true,
            activeBinding = null,
            rssi = null,
            discoveredDevices = emptyList(),
            message = null,
        )
    }

    override fun stopScan() {
        scanRequested = false
        delegate.stopScan()
        if (_state.value.operation == EasyOpenBleOperation.SCANNING) {
            _state.value = _state.value.copy(operation = EasyOpenBleOperation.IDLE)
        }
    }

    private fun resumeScanAfterBluetoothReady() {
        if (!scanRequested || _state.value.operation != EasyOpenBleOperation.ERROR) return
        delegate.startScan()
        _state.value = _state.value.copy(
            operation = EasyOpenBleOperation.SCANNING,
            connectionStatus = EasyOpenConnectionStatus.NOT_FOUND,
            activeBinding = null,
            rssi = null,
            discoveredDevices = emptyList(),
            message = null,
        )
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

    override fun pair(binding: DeviceBinding, profile: CoreDeviceProfile) {
        _state.value = _state.value.copy(
            operation = EasyOpenBleOperation.PAIRING,
            connectionStatus = EasyOpenConnectionStatus.CONNECTING,
            activeBinding = binding,
            message = null,
        )
        delegate.pair(binding, profile)
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

    internal fun publishDiscovered(
        binding: DeviceBinding.IosPeripheral,
        name: String?,
        rssi: Int,
        hardwareMac: String?,
    ) {
        val previousHardwareMac = _state.value.discoveredDevices
            .firstOrNull { it.binding == binding }
            ?.hardwareMac
        val discovered = EasyOpenBleDevice(
            binding = binding,
            name = name?.trim().orEmpty().ifBlank { "YILA 开门器" },
            rssi = rssi,
            hardwareMac = hardwareMac ?: previousHardwareMac,
        )
        _state.value = _state.value.copy(
            connectionStatus = EasyOpenConnectionStatus.DISCOVERED,
            activeBinding = binding,
            rssi = rssi,
            discoveredDevices = (_state.value.discoveredDevices
                .filterNot { it.binding == binding } + discovered),
            message = null,
        )
    }

    internal fun publishTransportReady(binding: DeviceBinding.IosPeripheral) {
        _state.value = _state.value.copy(
            operation = when (_state.value.operation) {
                EasyOpenBleOperation.PAIRING -> EasyOpenBleOperation.PAIRING
                EasyOpenBleOperation.UNLOCKING -> EasyOpenBleOperation.UNLOCKING
                else -> EasyOpenBleOperation.READY
            },
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
                // CoreBluetooth connected only means the link is up; command
                // writes remain blocked until service/characteristic discovery
                // and notification enablement report transport ready.
                EasyOpenConnectionStatus.CONNECTING
            } else {
                EasyOpenConnectionStatus.NOT_FOUND
            },
            activeBinding = binding,
            message = message,
        )
    }

    internal fun publishResponse(binding: DeviceBinding.IosPeripheral, bytes: ByteArray) {
        val success = UnlockProtocol.isSuccess(bytes)
        val failure = UnlockProtocol.isFailure(bytes)
        _state.value = _state.value.copy(
            operation = when {
                success && _state.value.operation == EasyOpenBleOperation.PAIRING -> EasyOpenBleOperation.PAIRED
                success -> EasyOpenBleOperation.SUCCESS
                failure -> EasyOpenBleOperation.ERROR
                else -> EasyOpenBleOperation.ERROR
            },
            connectionStatus = EasyOpenConnectionStatus.CONNECTED,
            activeBinding = binding,
            message = UnlockProtocol.responseSummary(bytes),
        )
    }
}

private const val IOS_CONNECT_TIMEOUT_SECONDS = 15L
private const val IOS_TRANSPORT_TIMEOUT_SECONDS = 15L
private const val IOS_RESPONSE_TIMEOUT_SECONDS = 13L
private const val IOS_MAX_COMMAND_ATTEMPTS = 2

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosCoreBluetoothDelegate(
    private val serviceUuid: String,
    private val writeCharacteristicUuid: String,
    private val notifyCharacteristicUuid: String,
    private val onPeripheralDiscovered: ((DeviceBinding.IosPeripheral, String?, Int, String?) -> Unit)?,
    private val onConnectionChanged: ((DeviceBinding.IosPeripheral, Boolean, String?) -> Unit)?,
    private val onTransportReady: ((DeviceBinding.IosPeripheral) -> Unit)?,
    private val onBluetoothAvailabilityChanged: ((Boolean) -> Unit)?,
    private val onResponse: ((DeviceBinding.IosPeripheral, ByteArray) -> Unit)?,
) : NSObject(), CBCentralManagerDelegateProtocol, CBPeripheralDelegateProtocol {
    private val centralManager = CBCentralManager(
        delegate = this,
        queue = dispatch_get_main_queue(),
    )
    private val peripherals = mutableMapOf<String, CBPeripheral>()
    private val pendingProfiles = mutableMapOf<String, CoreDeviceProfile>()
    private val pendingCommands = mutableMapOf<String, PendingCommand>()
    private val writeCharacteristics = mutableMapOf<String, CBCharacteristic>()
    private val notifyCharacteristics = mutableMapOf<String, CBCharacteristic>()
    private val notifyEnabled = mutableSetOf<String>()
    private val readyIdentifiers = mutableSetOf<String>()
    private val awaitingResponses = mutableMapOf<String, AwaitingResponse>()
    private val commandAttempts = mutableMapOf<String, Int>()
    private val pendingConnections = mutableMapOf<String, Long>()
    private val pendingTransports = mutableMapOf<String, Long>()
    private val ignoredDisconnects = mutableSetOf<String>()
    private var scanRequested = false
    private var commandToken = 0L
    private var lifecycleToken = 0L

    private val scanService: CBUUID
        get() = CBUUID.UUIDWithString(serviceUuid)

    private val writeUuid: CBUUID
        get() = CBUUID.UUIDWithString(writeCharacteristicUuid)

    private val notifyUuid: CBUUID
        get() = CBUUID.UUIDWithString(notifyCharacteristicUuid)

    fun isPoweredOn(): Boolean = centralManager.state == CBManagerStatePoweredOn

    fun startScan() {
        scanRequested = true
        if (!isPoweredOn()) return
        centralManager.scanForPeripheralsWithServices(
            serviceUUIDs = null,
            options = null,
        )
    }

    fun stopScan() {
        scanRequested = false
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
        pendingCommands.remove(identifier)
        awaitingResponses.remove(identifier)
        commandAttempts.remove(identifier)
        beginConnection(identifier, peripheral)
    }

    fun pair(binding: DeviceBinding, profile: CoreDeviceProfile) {
        val identifier = (binding as? DeviceBinding.IosPeripheral)?.identifier ?: return
        val normalized = profile.normalized()
        pendingProfiles[identifier] = normalized
        pendingCommands[identifier] = PendingCommand.Pairing
        commandAttempts[identifier] = 0
        if (identifier in readyIdentifiers) {
            writePendingCommand(identifier)
            return
        }
        val peripheral = resolvePeripheral(binding)
        if (peripheral == null) {
            pendingCommands.remove(identifier)
            awaitingResponses.remove(identifier)
            onConnectionChanged?.invoke(
                DeviceBinding.IosPeripheral(identifier),
                false,
                "无法恢复已保存的 iOS 蓝牙设备，请重新扫描",
            )
            return
        }
        beginConnection(identifier, peripheral)
    }

    fun unlock(binding: DeviceBinding, profile: CoreDeviceProfile) {
        val identifier = (binding as? DeviceBinding.IosPeripheral)?.identifier ?: return
        val normalized = profile.normalized()
        pendingProfiles[identifier] = normalized
        pendingCommands[identifier] = PendingCommand.Unlock
        commandAttempts[identifier] = 0
        if (identifier in readyIdentifiers) {
            writePendingCommand(identifier)
            return
        }
        val peripheral = resolvePeripheral(binding)
        if (peripheral == null) {
            pendingCommands.remove(identifier)
            awaitingResponses.remove(identifier)
            onConnectionChanged?.invoke(
                DeviceBinding.IosPeripheral(identifier),
                false,
                "无法恢复已保存的 iOS 蓝牙设备，请重新扫描",
            )
            return
        }
        beginConnection(identifier, peripheral)
    }

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        val available = central.state == CBManagerStatePoweredOn
        onBluetoothAvailabilityChanged?.invoke(available)
        if (!available) {
            central.stopScan()
        } else if (scanRequested) {
            central.scanForPeripheralsWithServices(
                serviceUUIDs = null,
                options = null,
            )
        }
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber,
    ) {
        val manufacturerData = (advertisementData[CBAdvertisementDataManufacturerDataKey] as? NSData)
            ?.toByteArray()
        val identity = manufacturerData
            ?.let(EasyOpenAdvertisementParser::parseIosManufacturerData)
        val name = didDiscoverPeripheral.name.orEmpty()
        val isYiLa = name.contains("YILA", ignoreCase = true) &&
            !name.contains("REMOTE", ignoreCase = true)
        if (!isYiLa && identity == null) return

        val identifier = didDiscoverPeripheral.identifier.UUIDString
        peripherals[identifier] = didDiscoverPeripheral
        onPeripheralDiscovered?.invoke(
            DeviceBinding.IosPeripheral(identifier),
            didDiscoverPeripheral.name,
            RSSI.intValue,
            identity?.hardwareMac,
        )
    }

    override fun centralManager(
        central: CBCentralManager,
        didConnectPeripheral: CBPeripheral,
    ) {
        val binding = DeviceBinding.IosPeripheral(didConnectPeripheral.identifier.UUIDString)
        val identifier = binding.identifier
        pendingConnections.remove(identifier)
        pendingTransports[identifier] = ++lifecycleToken
        didConnectPeripheral.delegate = this
        didConnectPeripheral.discoverServices(listOf(scanService))
        scheduleTransportTimeout(identifier, pendingTransports.getValue(identifier))
        // A connected peripheral is not command-ready until the service,
        // characteristics and notifications have all completed.
        onConnectionChanged?.invoke(binding, true, null)
    }

    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didFailToConnectPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        val identifier = didFailToConnectPeripheral.identifier.UUIDString
        val ignored = ignoredDisconnects.remove(identifier)
        pendingConnections.remove(identifier)
        pendingTransports.remove(identifier)
        pendingCommands.remove(identifier)
        awaitingResponses.remove(identifier)
        if (ignored) return
        onConnectionChanged?.invoke(
            DeviceBinding.IosPeripheral(identifier),
            false,
            error?.localizedDescription ?: "iOS 蓝牙连接失败",
        )
    }

    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        val identifier = didDisconnectPeripheral.identifier.UUIDString
        val ignored = ignoredDisconnects.remove(identifier)
        pendingConnections.remove(identifier)
        pendingTransports.remove(identifier)
        pendingProfiles.remove(identifier)
        pendingCommands.remove(identifier)
        awaitingResponses.remove(identifier)
        commandAttempts.remove(identifier)
        writeCharacteristics.remove(identifier)
        notifyCharacteristics.remove(identifier)
        notifyEnabled.remove(identifier)
        readyIdentifiers.remove(identifier)
        if (ignored) return
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
        if (didDiscoverServices != null) {
            pendingTransports.remove(peripheral.identifier.UUIDString)
            onConnectionChanged?.invoke(
                bindingFor(peripheral),
                false,
                didDiscoverServices.localizedDescription,
            )
            return
        }
        val service = peripheral.services.orEmpty()
            .filterIsInstance<CBService>()
            .firstOrNull { it.UUID == scanService }
        if (service == null) {
            pendingTransports.remove(peripheral.identifier.UUIDString)
            onConnectionChanged?.invoke(
                bindingFor(peripheral),
                false,
                "未找到开门器通信服务",
            )
            return
        }
        peripheral.discoverCharacteristics(
            listOf(writeUuid, notifyUuid),
            forService = service,
        )
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverCharacteristicsForService: CBService,
        error: NSError?,
    ) {
        val binding = bindingFor(peripheral)
        val identifier = binding.identifier
        if (error != null) {
            pendingTransports.remove(identifier)
            onConnectionChanged?.invoke(binding, false, error.localizedDescription)
            return
        }
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
                        properties and CBCharacteristicPropertyNotify != 0uL ||
                            properties and CBCharacteristicPropertyIndicate != 0uL
                        )
                ) {
                    notifyCharacteristics[identifier] = characteristic
                    peripheral.setNotifyValue(true, forCharacteristic = characteristic)
                }
            }

        maybeMarkTransportReady(peripheral)
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateNotificationStateForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) {
        val identifier = peripheral.identifier.UUIDString
        if (didUpdateNotificationStateForCharacteristic.UUID != notifyUuid) return
        if (error != null || !didUpdateNotificationStateForCharacteristic.isNotifying) {
            pendingTransports.remove(identifier)
            onConnectionChanged?.invoke(
                bindingFor(peripheral),
                false,
                error?.localizedDescription ?: "启用开门器通知失败",
            )
            return
        }
        notifyEnabled.add(identifier)
        maybeMarkTransportReady(peripheral)
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateValueForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) {
        if (error != null) {
            onConnectionChanged?.invoke(
                bindingFor(peripheral),
                false,
                error.localizedDescription,
            )
            return
        }
        if (didUpdateValueForCharacteristic.UUID != notifyUuid) return
        val data = didUpdateValueForCharacteristic.value ?: return
        val waiting = awaitingResponses.remove(peripheral.identifier.UUIDString) ?: return
        commandAttempts.remove(peripheral.identifier.UUIDString)
        val bytes = data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt()) ?: return
        // The command is intentionally consumed before dispatch so duplicate
        // notifications cannot complete the same request twice.
        if (waiting.command == PendingCommand.Pairing || waiting.command == PendingCommand.Unlock) {
            onResponse?.invoke(bindingFor(peripheral), bytes)
        }
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didWriteValueForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) {
        if (error != null) {
            awaitingResponses.remove(peripheral.identifier.UUIDString)
            commandAttempts.remove(peripheral.identifier.UUIDString)
            onConnectionChanged?.invoke(
                bindingFor(peripheral),
                false,
                error.localizedDescription,
            )
        }
    }

    private fun maybeMarkTransportReady(peripheral: CBPeripheral) {
        val binding = bindingFor(peripheral)
        val identifier = binding.identifier
        if (writeCharacteristics[identifier] != null &&
            notifyCharacteristics[identifier] != null &&
            identifier in notifyEnabled &&
            readyIdentifiers.add(identifier)
        ) {
            pendingTransports.remove(identifier)
            onTransportReady?.invoke(binding)
            writePendingCommand(identifier)
        }
    }

    private fun beginConnection(identifier: String, peripheral: CBPeripheral) {
        ignoredDisconnects.remove(identifier)
        val token = ++lifecycleToken
        pendingConnections[identifier] = token
        centralManager.connectPeripheral(peripheral, options = null)
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, IOS_CONNECT_TIMEOUT_SECONDS * NSEC_PER_SEC.toLong()),
            dispatch_get_main_queue(),
        ) {
            if (pendingConnections[identifier] != token) return@dispatch_after
            pendingConnections.remove(identifier)
            pendingTransports.remove(identifier)
            ignoredDisconnects.add(identifier)
            pendingCommands.remove(identifier)
            awaitingResponses.remove(identifier)
            commandAttempts.remove(identifier)
            centralManager.cancelPeripheralConnection(peripheral)
            onConnectionChanged?.invoke(
                DeviceBinding.IosPeripheral(identifier),
                false,
                "iOS 蓝牙连接超时，请确认开门器在附近后重试",
            )
        }
    }

    private fun scheduleTransportTimeout(identifier: String, token: Long) {
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, IOS_TRANSPORT_TIMEOUT_SECONDS * NSEC_PER_SEC.toLong()),
            dispatch_get_main_queue(),
        ) {
            if (pendingTransports[identifier] != token || identifier in readyIdentifiers) {
                return@dispatch_after
            }
            pendingTransports.remove(identifier)
            ignoredDisconnects.add(identifier)
            pendingCommands.remove(identifier)
            awaitingResponses.remove(identifier)
            commandAttempts.remove(identifier)
            peripherals[identifier]?.let(centralManager::cancelPeripheralConnection)
            onConnectionChanged?.invoke(
                DeviceBinding.IosPeripheral(identifier),
                false,
                "iOS 蓝牙服务发现超时，请确认开门器固件支持 EasyOpen 协议",
            )
        }
    }

    private fun writePendingCommand(identifier: String) {
        val command = pendingCommands[identifier] ?: return
        val profile = pendingProfiles[identifier] ?: return
        val peripheral = peripherals[identifier] ?: return
        val characteristic = writeCharacteristics[identifier] ?: return
        val packet = runCatching {
            when (command) {
                PendingCommand.Pairing -> UnlockProtocol.buildPasswordPacket(profile.password)
                PendingCommand.Unlock -> UnlockProtocol.buildOpenPacket(profile)
            }
        }.getOrElse { error ->
            pendingCommands.remove(identifier)
            commandAttempts.remove(identifier)
            onConnectionChanged?.invoke(
                DeviceBinding.IosPeripheral(identifier),
                false,
                error.message ?: "iOS 蓝牙命令参数无效",
            )
            return
        }
        val data = packet.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = packet.size.toULong())
        }
        val writeType = if (
            characteristic.properties and CBCharacteristicPropertyWriteWithoutResponse != 0uL
        ) {
            CBCharacteristicWriteWithoutResponse
        } else {
            CBCharacteristicWriteWithResponse
        }
        val token = ++commandToken
        val attempt = commandAttempts[identifier] ?: 0
        awaitingResponses[identifier] = AwaitingResponse(command = command, token = token)
        pendingCommands.remove(identifier)
        peripheral.writeValue(data, forCharacteristic = characteristic, type = writeType)
        scheduleResponseTimeout(identifier, command, token, attempt)
    }

    private fun scheduleResponseTimeout(
        identifier: String,
        command: PendingCommand,
        token: Long,
        attempt: Int,
    ) {
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, IOS_RESPONSE_TIMEOUT_SECONDS * NSEC_PER_SEC.toLong()),
            dispatch_get_main_queue(),
        ) {
            val waiting = awaitingResponses[identifier]
            if (waiting == null || waiting.token != token) return@dispatch_after
            awaitingResponses.remove(identifier)
            if (attempt + 1 < IOS_MAX_COMMAND_ATTEMPTS) {
                commandAttempts[identifier] = attempt + 1
                pendingCommands[identifier] = command
                writePendingCommand(identifier)
                return@dispatch_after
            }
            commandAttempts.remove(identifier)
            onConnectionChanged?.invoke(
                DeviceBinding.IosPeripheral(identifier),
                false,
                "iOS 蓝牙命令超时，请确认开门器在附近后重试",
            )
        }
    }

    private data class AwaitingResponse(
        val command: PendingCommand,
        val token: Long,
    )

    private fun bindingFor(peripheral: CBPeripheral): DeviceBinding.IosPeripheral =
        DeviceBinding.IosPeripheral(peripheral.identifier.UUIDString)

    private fun peripheralFor(binding: DeviceBinding.IosPeripheral): CBPeripheral =
        peripherals[binding.identifier] ?: error("iOS peripheral is not cached: ${binding.identifier}")

    private fun resolvePeripheral(binding: DeviceBinding): CBPeripheral? {
        val identifier = (binding as? DeviceBinding.IosPeripheral)?.identifier ?: return null
        peripherals[identifier]?.let { return it }
        val uuid = NSUUID(uUIDString = identifier)
        val peripheral = centralManager.retrievePeripheralsWithIdentifiers(listOf(uuid))
            .firstOrNull() as? CBPeripheral
        return peripheral?.also { peripherals[identifier] = it }
    }

    private enum class PendingCommand {
        Pairing,
        Unlock,
    }

    private fun NSData.toByteArray(): ByteArray =
        bytes?.reinterpret<ByteVar>()?.readBytes(length.toInt()) ?: ByteArray(0)
}
