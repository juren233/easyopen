package com.juren233.easyopen.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.juren233.easyopen.BuildConfig
import com.juren233.easyopen.shared.platform.EasyOpenBleUuids
import java.util.ArrayDeque
import java.util.UUID

internal sealed interface BleGattFailure {
    data object ConnectionStartFailed : BleGattFailure
    data object ConnectionTimeout : BleGattFailure
    data class UnexpectedDisconnect(val status: Int) : BleGattFailure
    data class ServicesFailed(val status: Int) : BleGattFailure
    data object ServiceMissing : BleGattFailure
    data object NotificationsFailed : BleGattFailure
    data class WriteStatus(val status: Int) : BleGattFailure
    data class WriteException(val message: String) : BleGattFailure
    data object WriteBusy : BleGattFailure
    data object NotificationBusy : BleGattFailure
}

internal interface BleGattSessionListener {
    fun onLinkConnecting(address: String, purpose: BleConnectionPurpose)
    fun onLinkConnected(address: String, purpose: BleConnectionPurpose)
    fun onLinkReady(address: String, purpose: BleConnectionPurpose)
    fun onGattResponse(bytes: ByteArray)
    fun onGattFailure(address: String, purpose: BleConnectionPurpose, failure: BleGattFailure)
    fun onGattReleased(address: String, purpose: BleConnectionPurpose, reason: String)
}

/** Owns one BluetoothGatt instance and serializes connect, release, setup, and writes. */
internal class BleGattSession(
    context: Context,
    private val listener: BleGattSessionListener,
) {
    companion object {
        private const val TAG = "BleDoorController"
        private const val MAX_WRITE_RETRIES = 8
        private const val WRITE_RETRY_DELAY_MS = 200L
        private const val GATT_CONNECTION_TIMEOUT_MS = 8_000L
        private const val RELEASE_WATCHDOG_MS = 1_500L
        private const val IDENTITY_READ_TIMEOUT_MS = 1_500L
        private const val MAX_IDENTITY_READS = 32
        val SERVICE_UUID: UUID = UUID.fromString(EasyOpenBleUuids.SERVICE)
        val WRITE_UUID: UUID = UUID.fromString(EasyOpenBleUuids.WRITE)
        val NOTIFY_UUID: UUID = UUID.fromString(EasyOpenBleUuids.NOTIFY)
        val CCCD_UUID: UUID = UUID.fromString(EasyOpenBleUuids.CCCD)
    }

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private val diagnostics = BleConnectionDiagnostics(TAG)
    private val retryPolicy = BleConnectionRetryPolicy()

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var currentAddress: String? = null
    private var currentPurpose = BleConnectionPurpose.NONE
    private var connectionTimeout: Runnable? = null
    private var releaseWatchdog: Runnable? = null
    private var writeRetry: Runnable? = null
    private var writeRetryCount = 0
    private var descriptorRetry: Runnable? = null
    private var descriptorRetryCount = 0
    private var releaseReason = "unknown"
    private var waitForFreshAdvertisementAfterRelease = true
    private val identityReadQueue = ArrayDeque<BluetoothGattCharacteristic>()
    private var identityReadInFlight: BluetoothGattCharacteristic? = null
    private var identityReadTimeout: Runnable? = null
    private var readyNotified = false

    val address: String?
        get() = currentAddress

    val purpose: BleConnectionPurpose
        get() = currentPurpose

    val phase: BleConnectionPhase
        get() = retryPolicy.phase

    val waitingForFreshAdvertisement: Boolean
        get() = retryPolicy.waitingForFreshAdvertisement

    fun consumeFreshAdvertisement(): Boolean = retryPolicy.consumeFreshAdvertisement()

    @SuppressLint("MissingPermission")
    fun connect(address: String, purpose: BleConnectionPurpose): Boolean {
        val normalizedAddress = address.trim().uppercase()
        if (!BluetoothAdapter.checkBluetoothAddress(normalizedAddress) ||
            !retryPolicy.beginConnect(requireFreshAdvertisement = purpose == BleConnectionPurpose.PREHEAT)
        ) {
            return false
        }
        currentAddress = normalizedAddress
        currentPurpose = purpose
        cancelIdentityDiagnostics()
        readyNotified = false
        diagnostics.begin(normalizedAddress, purpose)
        diagnostics.log("connect_gatt_start")
        val remote = runCatching { adapter?.getRemoteDevice(normalizedAddress) }.getOrNull()
        if (remote == null) {
            handleFailure(BleGattFailure.ConnectionStartFailed)
            return false
        }
        listener.onLinkConnecting(normalizedAddress, purpose)
        gatt = runCatching {
            if (Build.VERSION.SDK_INT >= 23) {
                remote.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                remote.connectGatt(appContext, false, callback)
            }
        }.getOrNull()
        val createdGatt = gatt
        diagnostics.log(
            "connect_gatt_return",
            "result=${if (createdGatt == null) "null" else "non_null"} gatt=${createdGatt?.let(System::identityHashCode) ?: "none"}",
        )
        if (createdGatt == null) {
            handleFailure(BleGattFailure.ConnectionStartFailed)
            return false
        }
        connectionTimeout = Runnable {
            connectionTimeout = null
            if (this.gatt === createdGatt && retryPolicy.phase == BleConnectionPhase.CONNECTING) {
                diagnostics.log("connection_timeout", "timeoutMs=$GATT_CONNECTION_TIMEOUT_MS")
                listener.onGattFailure(normalizedAddress, purpose, BleGattFailure.ConnectionTimeout)
                release("connection_timeout")
            }
        }.also { mainHandler.postDelayed(it, GATT_CONNECTION_TIMEOUT_MS) }
        return true
    }

    @SuppressLint("MissingPermission")
    fun write(packet: ByteArray): Boolean {
        val connection = gatt
        val characteristic = writeCharacteristic
        if (connection == null || characteristic == null || retryPolicy.phase != BleConnectionPhase.READY) {
            return false
        }
        diagnostics.log("command_write_start", "bytes=${packet.size}")
        return try {
            val status = if (Build.VERSION.SDK_INT >= 33) {
                connection.writeCharacteristic(
                    characteristic,
                    packet,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                )
            } else {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                characteristic.value = packet
                if (connection.writeCharacteristic(characteristic)) {
                    BluetoothGatt.GATT_SUCCESS
                } else {
                    BluetoothGatt.GATT_FAILURE
                }
            }
            diagnostics.log("command_write_return", "status=$status")
            when {
                status == BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY -> {
                    scheduleWriteRetry(connection, characteristic, packet)
                    true
                }
                status == BluetoothGatt.GATT_SUCCESS -> {
                    writeRetryCount = 0
                    true
                }
                else -> {
                    handleFailure(BleGattFailure.WriteStatus(status))
                    false
                }
            }
        } catch (error: Exception) {
            handleFailure(BleGattFailure.WriteException(error.message ?: "unknown"))
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun release(reason: String, waitForFreshAdvertisement: Boolean = true) {
        if (!retryPolicy.beginRelease()) return
        releaseReason = reason
        waitForFreshAdvertisementAfterRelease = waitForFreshAdvertisement
        cancelConnectionTimeout()
        cancelWriteRetry()
        cancelDescriptorRetry()
        val currentGatt = gatt
        if (currentGatt == null) {
            completeRelease()
            return
        }
        diagnostics.log("release_start", "reason=$reason gatt=${System.identityHashCode(currentGatt)}")
        runCatching { currentGatt.disconnect() }
        releaseWatchdog?.let(mainHandler::removeCallbacks)
        releaseWatchdog = Runnable {
            releaseWatchdog = null
            if (gatt === currentGatt && retryPolicy.phase == BleConnectionPhase.RELEASING) {
                diagnostics.log("release_watchdog", "reason=$reason")
                completeRelease()
            }
        }.also { mainHandler.postDelayed(it, RELEASE_WATCHDOG_MS) }
    }

    @SuppressLint("MissingPermission")
    fun closeNow(reason: String, waitForFreshAdvertisement: Boolean = false) {
        if (retryPolicy.phase != BleConnectionPhase.RELEASING) retryPolicy.beginRelease()
        releaseReason = reason
        waitForFreshAdvertisementAfterRelease = waitForFreshAdvertisement
        completeRelease()
    }

    @SuppressLint("MissingPermission")
    fun reset() {
        mainHandler.removeCallbacksAndMessages(null)
        gatt?.let { runCatching { it.disconnect() } }
        gatt?.let { runCatching { it.close() } }
        gatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        currentAddress = null
        currentPurpose = BleConnectionPurpose.NONE
        retryPolicy.reset()
        cancelConnectionTimeout()
        cancelWriteRetry()
        cancelDescriptorRetry()
        cancelIdentityDiagnostics()
        readyNotified = false
        releaseWatchdog = null
        waitForFreshAdvertisementAfterRelease = true
    }

    private fun handleFailure(failure: BleGattFailure) {
        val address = currentAddress.orEmpty()
        listener.onGattFailure(address, currentPurpose, failure)
        if (gatt == null) {
            retryPolicy.markReleased(waitForFreshAdvertisement = true)
            listener.onGattReleased(address, currentPurpose, "failure_without_gatt")
        } else {
            release("failure", waitForFreshAdvertisement = true)
        }
    }

    @SuppressLint("MissingPermission")
    private fun completeRelease() {
        val address = currentAddress.orEmpty()
        val purpose = currentPurpose
        val reason = releaseReason
        releaseWatchdog?.let(mainHandler::removeCallbacks)
        releaseWatchdog = null
        cancelConnectionTimeout()
        cancelWriteRetry()
        cancelDescriptorRetry()
        cancelIdentityDiagnostics()
        readyNotified = false
        gatt?.let { runCatching { it.close() } }
        gatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        currentAddress = null
        retryPolicy.markReleased(waitForFreshAdvertisement = waitForFreshAdvertisementAfterRelease)
        diagnostics.log("release_complete", "reason=$reason waitForFresh=$waitForFreshAdvertisementAfterRelease", address)
        currentPurpose = BleConnectionPurpose.NONE
        listener.onGattReleased(address, purpose, reason)
    }

    private fun cancelConnectionTimeout() {
        connectionTimeout?.let(mainHandler::removeCallbacks)
        connectionTimeout = null
    }

    private fun scheduleWriteRetry(
        connection: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        packet: ByteArray,
    ) {
        if (writeRetryCount >= MAX_WRITE_RETRIES) {
            cancelWriteRetry()
            handleFailure(BleGattFailure.WriteBusy)
            return
        }
        writeRetryCount += 1
        writeRetry?.let(mainHandler::removeCallbacks)
        writeRetry = Runnable {
            writeRetry = null
            if (gatt === connection && writeCharacteristic === characteristic && retryPolicy.phase == BleConnectionPhase.READY) {
                write(packet)
            }
        }.also { mainHandler.postDelayed(it, WRITE_RETRY_DELAY_MS) }
    }

    private fun cancelWriteRetry() {
        writeRetry?.let(mainHandler::removeCallbacks)
        writeRetry = null
        writeRetryCount = 0
    }

    private fun scheduleDescriptorRetry(
        connection: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
    ) {
        if (descriptorRetryCount >= MAX_WRITE_RETRIES) {
            cancelDescriptorRetry()
            handleFailure(BleGattFailure.NotificationBusy)
            return
        }
        descriptorRetryCount += 1
        descriptorRetry?.let(mainHandler::removeCallbacks)
        descriptorRetry = Runnable {
            descriptorRetry = null
            if (gatt === connection && retryPolicy.phase == BleConnectionPhase.CONNECTING) {
                handleDescriptorWriteResult(connection, descriptor, writeNotificationDescriptor(connection, descriptor))
            }
        }.also { mainHandler.postDelayed(it, WRITE_RETRY_DELAY_MS) }
    }

    private fun cancelDescriptorRetry() {
        descriptorRetry?.let(mainHandler::removeCallbacks)
        descriptorRetry = null
        descriptorRetryCount = 0
    }

    @SuppressLint("MissingPermission")
    private fun writeNotificationDescriptor(
        connection: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
    ): Int {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return BluetoothGatt.GATT_FAILURE
        }
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                connection.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (connection.writeDescriptor(descriptor)) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
            }
        } catch (_: SecurityException) {
            BluetoothGatt.GATT_FAILURE
        }
    }

    private fun handleDescriptorWriteResult(
        connection: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
    ) {
        diagnostics.log("cccd_write_return", "status=$status")
        when {
            status == BluetoothGatt.GATT_SUCCESS -> descriptorRetryCount = 0
            status == BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY -> scheduleDescriptorRetry(connection, descriptor)
            else -> markReady()
        }
    }

    private fun markReady() {
        if (readyNotified || identityReadInFlight != null) return
        if (BuildConfig.DEBUG && identityReadQueue.isNotEmpty()) {
            diagnostics.log(
                "identity_reads_start",
                "count=${identityReadQueue.size}",
            )
            readNextIdentityCharacteristic()
            return
        }
        finishReady()
    }

    private fun finishReady() {
        if (readyNotified) return
        readyNotified = true
        retryPolicy.markReady()
        diagnostics.log("ready")
        listener.onLinkReady(currentAddress.orEmpty(), currentPurpose)
    }

    @SuppressLint("MissingPermission")
    private fun readNextIdentityCharacteristic() {
        if (!BuildConfig.DEBUG) {
            finishReady()
            return
        }
        val connection = gatt
        val next = if (identityReadQueue.isEmpty()) null else identityReadQueue.removeFirst()
        if (connection == null || next == null || retryPolicy.phase == BleConnectionPhase.RELEASING) {
            finishReady()
            return
        }
        identityReadInFlight = next
        BleIdentityDiagnostics.logReadStart(currentAddress.orEmpty(), next)
        val accepted = runCatching { connection.readCharacteristic(next) }.getOrDefault(false)
        if (!accepted) {
            BleIdentityDiagnostics.logReadSkipped(
                currentAddress.orEmpty(),
                next,
                "readCharacteristic_returned_false",
            )
            identityReadInFlight = null
            readNextIdentityCharacteristic()
            return
        }
        identityReadTimeout?.let(mainHandler::removeCallbacks)
        identityReadTimeout = Runnable {
            identityReadTimeout = null
            if (this@BleGattSession.gatt === connection && identityReadInFlight === next) {
                BleIdentityDiagnostics.logReadSkipped(
                    currentAddress.orEmpty(),
                    next,
                    "callback_timeout_${IDENTITY_READ_TIMEOUT_MS}ms",
                )
                identityReadInFlight = null
                readNextIdentityCharacteristic()
            }
        }.also { mainHandler.postDelayed(it, IDENTITY_READ_TIMEOUT_MS) }
    }

    private fun cancelIdentityDiagnostics() {
        identityReadTimeout?.let(mainHandler::removeCallbacks)
        identityReadTimeout = null
        identityReadQueue.clear()
        identityReadInFlight = null
    }

    @SuppressLint("MissingPermission")
    private fun handleServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        diagnostics.log("services_discovered", "status=$status")
        if (status != BluetoothGatt.GATT_SUCCESS) {
            handleFailure(BleGattFailure.ServicesFailed(status))
            return
        }
        identityReadQueue.clear()
        if (BuildConfig.DEBUG) {
            val readable = BleIdentityDiagnostics.logGattTable(currentAddress.orEmpty(), gatt)
            identityReadQueue.addAll(readable.take(MAX_IDENTITY_READS))
            if (readable.size > MAX_IDENTITY_READS) {
                diagnostics.log(
                    "identity_reads_capped",
                    "readable=${readable.size} cap=$MAX_IDENTITY_READS",
                )
            }
        }
        val service = gatt.getService(SERVICE_UUID)
        writeCharacteristic = service?.getCharacteristic(WRITE_UUID)
        notifyCharacteristic = service?.getCharacteristic(NOTIFY_UUID)
        if (service == null || writeCharacteristic == null || notifyCharacteristic == null) {
            handleFailure(BleGattFailure.ServiceMissing)
            return
        }
        val notificationEnabled = gatt.setCharacteristicNotification(notifyCharacteristic, true)
        diagnostics.log("notification_local", "enabled=$notificationEnabled")
        if (!notificationEnabled) {
            handleFailure(BleGattFailure.NotificationsFailed)
            return
        }
        val descriptor = notifyCharacteristic?.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            markReady()
            return
        }
        diagnostics.log("cccd_write_start")
        handleDescriptorWriteResult(gatt, descriptor, writeNotificationDescriptor(gatt, descriptor))
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val isCurrent = this@BleGattSession.gatt === gatt
            diagnostics.logCallback("connection_state", gatt, isCurrent, "status=$status newState=$newState")
            if (!isCurrent) return
            val address = currentAddress.orEmpty()
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                if (retryPolicy.phase == BleConnectionPhase.RELEASING) {
                    diagnostics.log("late_connected_while_releasing", "status=$status")
                    runCatching { gatt.disconnect() }
                    return
                }
                cancelConnectionTimeout()
                listener.onLinkConnected(address, currentPurpose)
                val priorityRequested = runCatching {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                }.getOrDefault(false)
                diagnostics.log("connection_priority", "requested=$priorityRequested")
                diagnostics.log("mtu_request_start", "requested=100")
                if (!runCatching { gatt.requestMtu(100) }.getOrDefault(false)) {
                    diagnostics.log("mtu_request_return", "accepted=false fallback=discover_services")
                    mainHandler.postDelayed({
                        if (this@BleGattSession.gatt === gatt) {
                            diagnostics.log("service_discovery_start", "source=mtu_fallback")
                            runCatching { gatt.discoverServices() }
                        }
                    }, 300)
                } else {
                    diagnostics.log("mtu_request_return", "accepted=true")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                cancelConnectionTimeout()
                if (retryPolicy.phase == BleConnectionPhase.RELEASING) {
                    diagnostics.log("gatt_disconnected", "status=$status")
                    completeRelease()
                } else {
                    listener.onGattFailure(address, currentPurpose, BleGattFailure.UnexpectedDisconnect(status))
                    release("unexpected_disconnect", waitForFreshAdvertisement = true)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val isCurrent = this@BleGattSession.gatt === gatt
            diagnostics.logCallback("mtu_changed", gatt, isCurrent, "mtu=$mtu status=$status")
            if (!isCurrent) return
            if (Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) return
            runCatching {
                diagnostics.log("service_discovery_start", "source=mtu_callback")
                gatt.discoverServices()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val isCurrent = this@BleGattSession.gatt === gatt
            diagnostics.logCallback("services_callback", gatt, isCurrent, "status=$status")
            if (!isCurrent) return
            handleServicesDiscovered(gatt, status)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val isCurrent = this@BleGattSession.gatt === gatt
            diagnostics.logCallback("cccd_write_complete", gatt, isCurrent, "status=$status uuid=${descriptor.uuid}")
            if (!isCurrent) return
            if (status == BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY) {
                scheduleDescriptorRetry(gatt, descriptor)
            } else if (status == BluetoothGatt.GATT_SUCCESS) {
                cancelDescriptorRetry()
                markReady()
            } else {
                cancelDescriptorRetry()
                markReady()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            val isCurrent = this@BleGattSession.gatt === gatt
            diagnostics.logCallback(
                "identity_read_callback",
                gatt,
                isCurrent,
                "uuid=${characteristic.uuid} status=$status length=${value.size}",
            )
            if (!isCurrent || identityReadInFlight?.uuid != characteristic.uuid) return
            identityReadTimeout?.let(mainHandler::removeCallbacks)
            identityReadTimeout = null
            BleIdentityDiagnostics.logReadResult(
                currentAddress.orEmpty(),
                characteristic,
                value,
                status,
            )
            identityReadInFlight = null
            readNextIdentityCharacteristic()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (this@BleGattSession.gatt === gatt && characteristic.uuid == NOTIFY_UUID) {
                listener.onGattResponse(characteristic.value)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (this@BleGattSession.gatt === gatt && characteristic.uuid == NOTIFY_UUID) {
                listener.onGattResponse(value)
            }
        }
    }
}
