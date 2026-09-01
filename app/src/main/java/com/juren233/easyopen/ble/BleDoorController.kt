package com.juren233.easyopen.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.juren233.easyopen.BuildConfig
import com.juren233.easyopen.R
import com.juren233.easyopen.data.AutoConnectSettings
import com.juren233.easyopen.data.DeviceProfile
import com.juren233.easyopen.shared.protocol.EasyOpenAdvertisementParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface BleState {
    data object Idle : BleState
    data object Scanning : BleState
    data class Connecting(val address: String) : BleState
    data class Pairing(val address: String) : BleState
    data class Ready(val address: String) : BleState
    data class Unlocking(val address: String) : BleState
    data class Paired(val address: String) : BleState
    data class Success(val message: String) : BleState
    data class Error(val message: String) : BleState
}

data class DiscoveredDevice(
    val device: BluetoothDevice,
    val name: String,
    val rssi: Int,
    val hardwareMac: String? = null,
    val likelyYiLa: Boolean = true,
)

/** BLE controller for the Nordic UART-compatible YiLa/Macronum opener. */
class BleDoorController(context: Context) {
    companion object {
        private const val TAG = "BleDoorController"
        private const val DISCOVERY_SCAN_WINDOW_MS = 12_000L
        private const val SCAN_RESTART_DELAY_MS = 350L
        private const val PRESENCE_SCAN_WINDOW_MS = 8_000L
        /** Keep a foreground preheat link briefly across a transient app switch, then release it. */
        private const val BACKGROUND_RELEASE_GRACE_MS = 2_000L

        /** The original app accepts local openers whose advertised name contains YILA, except remotes. */
        fun isYiLaOpenerName(name: String): Boolean {
            val normalized = name.trim().uppercase()
            return normalized.contains("YILA") && !normalized.contains("REMOTE")
        }
    }

    private fun text(resourceId: Int, vararg formatArgs: Any): String =
        appContext.getString(resourceId, *formatArgs)

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The controller starts in the foreground; MainActivity updates this at lifecycle boundaries. */
    private var appInForeground = true
    private var backgroundRelease: Runnable? = null
    private var queuedExplicitConnect: Pair<String, BleConnectionPurpose>? = null

    private val _state = MutableStateFlow<BleState>(BleState.Idle)
    val state: StateFlow<BleState> = _state.asStateFlow()
    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()
    private val _batteryLevels = MutableStateFlow<Map<String, Int>>(emptyMap())
    val batteryLevels: StateFlow<Map<String, Int>> = _batteryLevels.asStateFlow()
    private val _openerConnection = MutableStateFlow(OpenerConnectionSnapshot())
    /** Four-state availability/link status for the active opener shown on the home page. */
    val openerConnection: StateFlow<OpenerConnectionSnapshot> = _openerConnection.asStateFlow()

    private var presenceMonitoringActive = false
    private var presenceProfile: DeviceProfile? = null
    private var presenceLockedAddress: String? = null
    private var presenceLastRssi: Int? = null
    private var presenceLastSeenAtMs: Long = 0L
    private var presenceWindowHadTarget = false
    private var presenceWindowId = 0
    private var presenceWindowTargetCount = 0
    private var presenceAutoConnectEnabled = true
    private var presenceAutoConnectRssiThreshold = OpenerConnectionPolicy.AUTO_CONNECT_RSSI_THRESHOLD
    private var batteryScanDurationMs = 12_000L
    private var batteryScanTargetAddress: String? = null
    private val batteryDiagnosticSignatures = LinkedHashSet<String>()
    private var batteryWindowResultCount = 0
    private var batteryWindowTargetCount = 0
    private var batteryWindowExactAddressCount = 0
    private var batteryWindowValidLevelCount = 0
    private val discoveryScanner = BleScanWindow(
        scannerProvider = { adapter?.bluetoothLeScanner },
        canScan = { hasBluetoothPermission() && isBluetoothEnabled() },
        durationMs = { DISCOVERY_SCAN_WINDOW_MS },
        restartDelayMs = SCAN_RESTART_DELAY_MS,
        label = "discovery",
        handler = mainHandler,
        handleScanResult = { _, result -> consumeDiscoveryScanResult(result) },
    )
    private val presenceScanner = BleScanWindow(
        scannerProvider = { adapter?.bluetoothLeScanner },
        canScan = { appInForeground && hasBluetoothPermission() && isBluetoothEnabled() },
        durationMs = { PRESENCE_SCAN_WINDOW_MS },
        restartDelayMs = SCAN_RESTART_DELAY_MS,
        label = "presence",
        handler = mainHandler,
        filtersProvider = ::presenceScanFilters,
        handleScanResult = { windowId, result -> consumePresenceScanResult(windowId, result) },
        handleWindowFinished = ::finishPresenceScanWindow,
    )
    private val batteryScanner = BleScanWindow(
        scannerProvider = { adapter?.bluetoothLeScanner },
        canScan = { hasBluetoothPermission() && isBluetoothEnabled() },
        durationMs = { batteryScanDurationMs },
        restartDelayMs = SCAN_RESTART_DELAY_MS,
        label = "battery",
        handler = mainHandler,
        handleScanResult = { windowId, result ->
            batteryWindowResultCount += 1
            consumeBatteryScanResult(windowId, result)
        },
        handleBatchScanResults = { windowId, results ->
            batteryWindowResultCount += results.size
            results.forEach { consumeBatteryScanResult(windowId, it) }
        },
        handleScanFailed = ::handleBatteryScanFailure,
        handleWindowFinished = ::finishBatteryScanWindow,
    )
    private var pendingOperation: PendingOperation = PendingOperation.None
    private var operationTimeout: Runnable? = null
    private val diagnostics = BleConnectionDiagnostics(TAG)
    private val gattSession = BleGattSession(appContext, object : BleGattSessionListener {
        override fun onLinkConnecting(address: String, purpose: BleConnectionPurpose) {
            _connectionState.value = BleConnectionState.Connecting(address)
            publishConnecting(address)
        }

        override fun onLinkConnected(address: String, purpose: BleConnectionPurpose) {
            _connectionState.value = BleConnectionState.Connecting(address)
            publishConnecting(address)
        }

        override fun onLinkReady(address: String, purpose: BleConnectionPurpose) {
            handleGattReady(address)
        }

        override fun onGattResponse(bytes: ByteArray) {
            handleGattResponse(bytes)
        }

        override fun onGattFailure(
            address: String,
            purpose: BleConnectionPurpose,
            failure: BleGattFailure,
        ) {
            handleGattFailure(address, purpose, failure)
        }

        override fun onGattReleased(address: String, purpose: BleConnectionPurpose, reason: String) {
            handleGattReleased(address, purpose, reason)
        }
    })

    private sealed interface PendingOperation {
        data object None : PendingOperation
        data class Pairing(val password: String) : PendingOperation
        data class Unlock(
            val profile: DeviceProfile,
            val onComplete: ((success: Boolean) -> Unit)? = null,
        ) : PendingOperation
    }

    fun hasBluetoothPermission(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScan() {
        when {
            !hasBluetoothPermission() -> _state.value = BleState.Error(text(R.string.error_bluetooth_permission))
            !isBluetoothEnabled() -> _state.value = BleState.Error(text(R.string.error_bluetooth_disabled))
            adapter?.bluetoothLeScanner == null -> _state.value = BleState.Error(text(R.string.error_scanner_unavailable))
            else -> {
                _devices.value = emptyList()
                _state.value = BleState.Scanning
                discoveryScanner.start()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun consumeDiscoveryScanResult(result: ScanResult) {
        val device = result.device ?: return
        val name = advertisedName(device, result)
        BleIdentityDiagnostics.logScanResult(result, name)
        if (!isYiLaOpenerName(name)) return
        val identity = parseHardwareIdentity(result)
        val previous = _devices.value.firstOrNull {
            it.device.address.equals(device.address, ignoreCase = true)
        }
        updateBatteryLevel(device.address, result.scanRecord?.bytes)
        val next = DiscoveredDevice(
            device = device,
            name = name.trim().ifBlank { text(R.string.default_opener_advertised_name) },
            rssi = result.rssi,
            hardwareMac = identity?.hardwareMac ?: previous?.hardwareMac,
        )
        _devices.value = (_devices.value
            .filterNot { it.device.address.equals(device.address, ignoreCase = true) } + next)
            .sortedByDescending(DiscoveredDevice::rssi)
    }

    private fun parseHardwareIdentity(result: ScanResult) = result.scanRecord
        ?.manufacturerSpecificData
        ?.let { data ->
            (0 until data.size()).asSequence()
                .mapNotNull { index ->
                    EasyOpenAdvertisementParser.parseAndroidManufacturerData(
                        companyId = data.keyAt(index),
                        data = data.valueAt(index),
                    )
                }
                .firstOrNull()
        }

    @SuppressLint("MissingPermission")
    private fun advertisedName(device: BluetoothDevice, result: ScanResult): String {
        return runCatching { device.name.orEmpty() }.getOrDefault("")
            .ifBlank { AdvertisementNameParser.parse(result.scanRecord?.bytes).orEmpty() }
            .ifBlank { result.scanRecord?.deviceName.orEmpty() }
    }

    private fun cancelOperationTimeout() {
        operationTimeout?.let(mainHandler::removeCallbacks)
        operationTimeout = null
    }

    private fun scheduleBackgroundRelease(reason: String) {
        if (appInForeground || pendingOperation !is PendingOperation.None) return
        backgroundRelease?.let(mainHandler::removeCallbacks)
        backgroundRelease = Runnable {
            backgroundRelease = null
            if (!appInForeground && pendingOperation is PendingOperation.None) {
                releaseIdleConnection(reason)
            }
        }.also { mainHandler.postDelayed(it, BACKGROUND_RELEASE_GRACE_MS) }
    }

    /**
     * Marks the UI as visible and resumes foreground preheating for the home page.
     * The monitor itself is restored by the existing Home navigation effect.
     */
    @SuppressLint("MissingPermission")
    fun enterForeground() {
        appInForeground = true
        backgroundRelease?.let(mainHandler::removeCallbacks)
        backgroundRelease = null
        if (BuildConfig.DEBUG) Log.d(TAG, "BLE_LIFECYCLE state=foreground")
        if (presenceMonitoringActive) {
            val address = presenceProfile?.address.orEmpty().uppercase()
            val link = _connectionState.value
            if (link is BleConnectionState.Connected && link.address.equals(address, ignoreCase = true)) {
                publishOpenerConnection(OpenerConnectionStatus.CONNECTED, address)
            } else {
                publishOpenerConnection(OpenerConnectionStatus.NOT_FOUND, address)
                startPresenceScanWindow()
            }
        }
    }

    /**
     * Stops background scanning/reconnect attempts. An idle preheat link gets a
     * short grace period so a transient app switch does not throw away warm-up,
     * but it cannot remain an indefinite owner of the opener.
     */
    @SuppressLint("MissingPermission")
    fun enterBackground() {
        appInForeground = false
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "BLE_LIFECYCLE state=background pending=${pendingOperation::class.simpleName} " +
                    "connection=${_connectionState.value::class.simpleName}",
            )
        }
        presenceScanner.stop()
        if (pendingOperation is PendingOperation.None) {
            scheduleBackgroundRelease("background_idle")
        } else if (BuildConfig.DEBUG) {
            Log.d(TAG, "BLE_LIFECYCLE background_keeps_active_operation=true")
        }
    }

    /**
     * Keeps looking for the active opener while the home page is visible.
     *
     * The opener stops advertising while another phone owns the GATT link, so a
     * failed/expired scan is not an error. We keep the monitor alive and let the
     * next scan window decide whether the device is still discoverable.
     */
    @SuppressLint("MissingPermission")
    fun startOpenerMonitoring(
        profile: DeviceProfile,
        autoConnectEnabled: Boolean = true,
        autoConnectRssiThreshold: Int = OpenerConnectionPolicy.AUTO_CONNECT_RSSI_THRESHOLD,
    ) {
        backgroundRelease?.let(mainHandler::removeCallbacks)
        backgroundRelease = null
        val address = profile.address.trim().uppercase()
        val sameTarget = presenceMonitoringActive &&
            presenceLockedAddress.equals(address, ignoreCase = true)
        stopScan()
        stopBatteryScan()
        if (!sameTarget) presenceScanner.stop()
        presenceMonitoringActive = address.isNotBlank() && BluetoothAdapter.checkBluetoothAddress(address)
        presenceProfile = profile
        presenceLockedAddress = address.takeIf(BluetoothAdapter::checkBluetoothAddress)
        if (!sameTarget) {
            presenceLastRssi = null
            presenceLastSeenAtMs = 0L
            presenceWindowHadTarget = false
            presenceWindowId = 0
            presenceWindowTargetCount = 0
        }
        presenceAutoConnectEnabled = autoConnectEnabled
        presenceAutoConnectRssiThreshold = AutoConnectSettings.normalizeRssiThreshold(autoConnectRssiThreshold)
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "BLE_SCAN presence_monitoring target=$address sameTarget=$sameTarget " +
                    "autoConnect=$autoConnectEnabled threshold=$presenceAutoConnectRssiThreshold",
            )
        }

        val link = _connectionState.value
        when {
            link is BleConnectionState.Connected && link.address.equals(address, ignoreCase = true) -> {
                publishOpenerConnection(OpenerConnectionStatus.CONNECTED, address)
                return
            }
            link is BleConnectionState.Connecting && link.address.equals(address, ignoreCase = true) -> {
                publishOpenerConnection(OpenerConnectionStatus.CONNECTING, address)
                return
            }
            !presenceMonitoringActive -> {
                publishOpenerConnection(OpenerConnectionStatus.NOT_FOUND, address)
                return
            }
            else -> {
                // A user-selected profile may differ from the old active link.
                // Release the old link before waiting for the new opener's signal.
                closeCurrentLinkForDifferentAddress(address)
                publishOpenerConnection(OpenerConnectionStatus.NOT_FOUND, address)
                startPresenceScanWindow()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopOpenerMonitoring() {
        presenceMonitoringActive = false
        if (pendingOperation is PendingOperation.None &&
            gattSession.phase != BleConnectionPhase.DISCONNECTED
        ) {
            releaseIdleConnection("monitoring_stopped")
        }
        presenceProfile = null
        presenceLockedAddress = null
        presenceLastRssi = null
        presenceLastSeenAtMs = 0L
        presenceWindowHadTarget = false
        presenceAutoConnectEnabled = true
        presenceAutoConnectRssiThreshold = OpenerConnectionPolicy.AUTO_CONNECT_RSSI_THRESHOLD
        presenceScanner.stop()
        _openerConnection.value = OpenerConnectionSnapshot()
    }

    @SuppressLint("MissingPermission")
    private fun startPresenceScanWindow() {
        if (!presenceMonitoringActive || !appInForeground) return
        if (!hasBluetoothPermission() || !isBluetoothEnabled()) {
            publishOpenerConnection(
                OpenerConnectionStatus.NOT_FOUND,
                presenceProfile?.address.orEmpty().uppercase(),
            )
            return
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "BLE_SCAN presence_start target=${presenceLockedAddress ?: "unknown"} " +
                    "phase=${gattSession.phase} waitingForFresh=${gattSession.waitingForFreshAdvertisement}",
            )
        }
        presenceScanner.start()
    }

    private fun presenceScanFilters(): List<ScanFilter> {
        val address = presenceLockedAddress ?: return emptyList()
        if (!BluetoothAdapter.checkBluetoothAddress(address)) return emptyList()
        return listOf(ScanFilter.Builder().setDeviceAddress(address).build())
    }

    private fun finishPresenceScanWindow(windowId: Int) {
        val address = presenceProfile?.address.orEmpty().uppercase()
        val connected = _connectionState.value is BleConnectionState.Connected &&
            (_connectionState.value as BleConnectionState.Connected).address.equals(address, ignoreCase = true)
        val signalFresh = OpenerConnectionPolicy.isSignalFresh(presenceLastSeenAtMs, System.currentTimeMillis())
        if (!connected && !signalFresh) publishOpenerConnection(OpenerConnectionStatus.NOT_FOUND, address)
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "BLE_SCAN presence_window_finished window=$windowId targetSeen=$presenceWindowHadTarget " +
                    "targetCount=$presenceWindowTargetCount " +
                    "signalFresh=$signalFresh rssi=${presenceLastRssi ?: "unknown"} " +
                    "status=${_openerConnection.value.status}",
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun consumePresenceScanResult(windowId: Int, result: ScanResult) {
        val profile = presenceProfile ?: return
        val device = result.device ?: return
        BleIdentityDiagnostics.logScanResult(result, advertisedName(device, result))
        val address = device.address.trim().uppercase()
        val targetAddress = presenceLockedAddress ?: profile.address.trim().uppercase()
        // Once the active profile is known, only its address can claim the lock.
        // Name/service matches are useful for pairing, but are not safe for an
        // already-paired home device when several openers are nearby.
        if (!address.equals(targetAddress, ignoreCase = true)) return

        val rssi = result.rssi
        if (presenceWindowId != windowId) {
            presenceWindowId = windowId
            presenceWindowTargetCount = 0
        }
        presenceWindowTargetCount += 1
        val firstTarget = !presenceWindowHadTarget
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "BLE_SCAN presence_target_result window=$windowId address=$address rssi=$rssi " +
                    "firstTarget=$firstTarget count=$presenceWindowTargetCount",
            )
        }
        presenceWindowHadTarget = true
        presenceLastRssi = rssi
        presenceLastSeenAtMs = System.currentTimeMillis()
        updateBatteryLevel(address, result.scanRecord?.bytes)
        publishOpenerConnection(OpenerConnectionStatus.DISCOVERED, targetAddress, rssi)
        val freshAfterRelease = gattSession.consumeFreshAdvertisement()
        if (presenceAutoConnectEnabled && freshAfterRelease &&
            OpenerConnectionPolicy.shouldAutoConnect(rssi, presenceAutoConnectRssiThreshold) &&
            canAutoConnect(profile)
        ) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "BLE_SCAN auto_connect_allowed window=$windowId address=$address rssi=$rssi " +
                        "threshold=$presenceAutoConnectRssiThreshold autoConnectEnabled=$presenceAutoConnectEnabled",
                )
            }
            connectAddress(profile.address, BleConnectionPurpose.PREHEAT)
        } else if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "BLE_SCAN auto_connect_rejected window=$windowId address=$address rssi=$rssi " +
                    "freshAfterRelease=$freshAfterRelease threshold=$presenceAutoConnectRssiThreshold " +
                    "autoConnectEnabled=$presenceAutoConnectEnabled phase=${gattSession.phase}",
            )
        }
    }

    private fun canAutoConnect(profile: DeviceProfile): Boolean {
        val targetAddress = profile.address.trim().uppercase()
        if (gattSession.address.equals(targetAddress, ignoreCase = true) &&
            gattSession.phase != BleConnectionPhase.DISCONNECTED
        ) return false
        if (gattSession.phase != BleConnectionPhase.DISCONNECTED) return false
        return appInForeground && pendingOperation is PendingOperation.None &&
            !gattSession.waitingForFreshAdvertisement
    }

    private fun publishOpenerConnection(
        status: OpenerConnectionStatus,
        address: String,
        rssi: Int? = presenceLastRssi,
    ) {
        _openerConnection.value = OpenerConnectionSnapshot(
            status = status,
            address = address.trim().uppercase(),
            rssi = rssi,
        )
    }

    private fun releaseIdleConnection(reason: String) {
        if (pendingOperation !is PendingOperation.None || gattSession.phase == BleConnectionPhase.DISCONNECTED) return
        gattSession.release(
            reason = reason,
            waitForFreshAdvertisement = presenceMonitoringActive,
        )
    }

    private fun closeCurrentLinkForDifferentAddress(address: String) {
        val current = gattSession.address
        if (current.isNullOrBlank() || current.equals(address, ignoreCase = true)) return
        pendingOperation = PendingOperation.None
        gattSession.release("switch_profile", waitForFreshAdvertisement = false)
        _connectionState.value = BleConnectionState.Disconnected
    }

    @SuppressLint("MissingPermission")
    fun startBatteryScan(address: String? = null, durationMs: Long = 12_000L) {
        if (!hasBluetoothPermission() || !isBluetoothEnabled()) return
        batteryScanTargetAddress = address?.trim()?.uppercase()?.takeIf(BluetoothAdapter::checkBluetoothAddress)
        batteryScanDurationMs = durationMs.coerceAtLeast(1_000L)
        batteryDiagnosticSignatures.clear()
        batteryWindowResultCount = 0
        batteryWindowTargetCount = 0
        batteryWindowExactAddressCount = 0
        batteryWindowValidLevelCount = 0
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "start battery scan target=${batteryScanTargetAddress ?: "any YiLa opener"} durationMs=$batteryScanDurationMs")
        }
        batteryScanner.start()
    }

    private fun handleBatteryScanFailure(windowId: Int, errorCode: Int) {
        if (BuildConfig.DEBUG) {
            Log.w(
                TAG,
                "battery scan failed window=$windowId errorCode=$errorCode " +
                    "results=$batteryWindowResultCount targetHits=$batteryWindowTargetCount " +
                    "exactAddressHits=$batteryWindowExactAddressCount validLevels=$batteryWindowValidLevelCount " +
                    "target=${batteryScanTargetAddress ?: "any YiLa opener"}",
            )
        }
    }

    private fun finishBatteryScanWindow(windowId: Int) {
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "battery scan window=$windowId finished results=$batteryWindowResultCount " +
                    "targetHits=$batteryWindowTargetCount exactAddressHits=$batteryWindowExactAddressCount " +
                    "validLevels=$batteryWindowValidLevelCount",
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun consumeBatteryScanResult(windowId: Int, result: ScanResult) {
        val device = result.device ?: return
        BleIdentityDiagnostics.logScanResult(result, advertisedName(device, result))
        val address = device.address.trim().uppercase()
        val targetAddress = batteryScanTargetAddress
        val name = advertisedName(device, result)
        val rawRecord = result.scanRecord?.bytes
        val serviceMatch = result.scanRecord?.serviceUuids?.any { it.uuid == BleGattSession.SERVICE_UUID } == true
        val match = BatteryScanMatcher.match(
            address = address,
            targetAddress = targetAddress,
            advertisedName = name,
            hasNordicUartService = serviceMatch,
        )
        logBatteryScanResult(windowId, address, name, rawRecord, match)
        if (!match.isTarget) return
        batteryWindowTargetCount += 1
        if (match.addressMatches) batteryWindowExactAddressCount += 1
        val logicalAddress = if (match.addressMatches) {
            targetAddress ?: address
        } else {
            // A privacy address/name update can arrive under a different address. Only
            // alias a candidate when it also advertises the opener's Nordic UART service.
            targetAddress?.takeIf { match.nameMatches && match.serviceMatches } ?: address
        }
        if (updateBatteryLevel(logicalAddress, rawRecord)) {
            batteryWindowValidLevelCount += 1
        }
    }

    private fun logBatteryScanResult(
        windowId: Int,
        address: String,
        name: String,
        rawRecord: ByteArray?,
        match: BatteryScanMatch,
    ) {
        if (!BuildConfig.DEBUG) return
        val targetSpecific = match.isTarget || match.addressMatches
        if (!targetSpecific && batteryDiagnosticSignatures.size >= 32) return
        val rawHex = rawRecord?.take(64)?.joinToString("") { "%02X".format(it.toInt() and 0xFF) } ?: "<none>"
        val signature = if (targetSpecific) {
            "$windowId|$address|$rawHex"
        } else {
            "$address|$rawHex"
        }
        if (!batteryDiagnosticSignatures.add(signature)) return
        Log.d(
            TAG,
            "battery scan result window=$windowId address=$address name=${name.ifBlank { "<none>" }} " +
                "addressMatch=${match.addressMatches} nameMatch=${match.nameMatches} " +
                "serviceMatch=${match.serviceMatches} level=${BatteryAdvertisementParser.parse(rawRecord) ?: "unknown"} raw=$rawHex",
        )
    }

    private fun updateBatteryLevel(address: String, scanRecord: ByteArray?): Boolean {
        val level = BatteryAdvertisementParser.parse(scanRecord) ?: return false
        val normalizedAddress = address.trim().uppercase()
        _batteryLevels.update { current ->
            if (current[normalizedAddress] == level) current else current + (normalizedAddress to level)
        }
        return true
    }

    fun restoreBatteryLevels(profiles: List<DeviceProfile>) {
        val restored = profiles.mapNotNull { profile ->
            profile.batteryLevel?.takeIf { it in 1..5 }?.let {
                profile.address.trim().uppercase() to it
            }
        }.toMap()
        if (restored.isEmpty()) return
        _batteryLevels.update { current -> restored + current }
    }

    @SuppressLint("MissingPermission")
    fun stopBatteryScan() {
        batteryScanTargetAddress = null
        batteryScanner.stop()
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        discoveryScanner.stop()
        if (_state.value == BleState.Scanning) _state.value = BleState.Idle
    }

    /** Starts the original six-digit password initialization command, without opening the lock. */
    @SuppressLint("MissingPermission")
    fun pair(address: String, password: String) {
        val normalizedAddress = address.trim().uppercase()
        val device = _devices.value.firstOrNull {
            it.device.address.equals(normalizedAddress, ignoreCase = true)
        }
        if (device == null) {
            _state.value = BleState.Error("请重新搜索并选择开门器")
            return
        }
        pair(device, password)
    }

    @SuppressLint("MissingPermission")
    fun pair(device: DiscoveredDevice, password: String) {
        val normalizedPassword = password.trim()
        if (!normalizedPassword.matches(Regex("^[0-9]{6}$"))) {
            _state.value = BleState.Error(text(com.juren233.easyopen.R.string.error_password_length))
            return
        }
        cancelOperationTimeout()
        pendingOperation = PendingOperation.Pairing(normalizedPassword)
        connectAddress(device.device.address, BleConnectionPurpose.PAIRING)
    }

    @SuppressLint("MissingPermission")
    fun connect(profile: DeviceProfile) {
        ensureConnected(profile)
    }

    /** Reconnects only when the requested opener is not already connected or connecting. */
    @SuppressLint("MissingPermission")
    fun ensureConnected(profile: DeviceProfile) {
        val address = profile.address.trim().uppercase()
        val link = _connectionState.value
        if (link is BleConnectionState.Connected && link.address.equals(address, ignoreCase = true)) {
            publishOpenerConnection(OpenerConnectionStatus.CONNECTED, address)
            return
        }
        if (link is BleConnectionState.Connecting && link.address.equals(address, ignoreCase = true)) {
            publishOpenerConnection(OpenerConnectionStatus.CONNECTING, address)
            return
        }
        pendingOperation = PendingOperation.None
        connectAddress(address, BleConnectionPurpose.EXPLICIT_CONNECT)
    }

    fun isConnected(address: String): Boolean =
        (_connectionState.value as? BleConnectionState.Connected)?.address.equals(address.trim(), ignoreCase = true)

    /** Compatibility entry point used by navigation; the monitor now owns home auto-connect. */
    @SuppressLint("MissingPermission")
    fun prepareHomeConnection(profile: DeviceProfile, probeDelayMs: Long = 0L) {
        startOpenerMonitoring(profile)
    }

    @SuppressLint("MissingPermission")
    private fun connectAddress(address: String, purpose: BleConnectionPurpose) {
        if (!appInForeground && purpose == BleConnectionPurpose.PREHEAT) {
            if (BuildConfig.DEBUG) Log.d(TAG, "BLE_CONNECT skipped=background_preheat")
            return
        }
        backgroundRelease?.let(mainHandler::removeCallbacks)
        backgroundRelease = null
        if (!hasBluetoothPermission()) {
            _state.value = BleState.Error(text(R.string.error_bluetooth_permission))
            return
        }
        if (!isBluetoothEnabled()) {
            _state.value = BleState.Error(text(R.string.error_bluetooth_disabled))
            return
        }
        val normalizedAddress = address.trim().uppercase()
        if (!BluetoothAdapter.checkBluetoothAddress(normalizedAddress)) {
            _state.value = BleState.Error(text(R.string.error_address_invalid))
            return
        }
        val sessionAddress = gattSession.address
        if (sessionAddress.equals(normalizedAddress, ignoreCase = true)) {
            if (gattSession.phase == BleConnectionPhase.READY) {
                publishOpenerConnection(OpenerConnectionStatus.CONNECTED, normalizedAddress)
                return
            }
            if (gattSession.phase == BleConnectionPhase.CONNECTING) return
        }
        if (gattSession.phase != BleConnectionPhase.DISCONNECTED) {
            if (purpose == BleConnectionPurpose.PREHEAT) {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "BLE_CONNECT skipped=phase_${gattSession.phase.name.lowercase()} " +
                            "purpose=${purpose.name.lowercase()} address=$normalizedAddress",
                    )
                }
                return
            }
            queuedExplicitConnect = normalizedAddress to purpose
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "BLE_CONNECT queued address=$normalizedAddress purpose=${purpose.name.lowercase()} " +
                        "phase=${gattSession.phase.name.lowercase()}",
                )
            }
            if (gattSession.phase != BleConnectionPhase.RELEASING) {
                gattSession.release("replace_for_new_address", waitForFreshAdvertisement = false)
            }
            return
        }
        stopScan()
        presenceScanner.stop()
        batteryScanner.stop()
        cancelOperationTimeout()
        _connectionState.value = BleConnectionState.Disconnected
        if (!gattSession.connect(normalizedAddress, purpose)) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "BLE_CONNECT rejected address=$normalizedAddress purpose=${purpose.name.lowercase()} " +
                        "phase=${gattSession.phase} " +
                        "waitingForFresh=${gattSession.waitingForFreshAdvertisement}",
                )
            }
        }
    }

    fun unlock(profile: DeviceProfile, onComplete: ((success: Boolean) -> Unit)? = null) {
        if (profile.password.isBlank()) {
            _state.value = BleState.Error(text(R.string.error_password_not_configured))
            notifyUnlockComplete(onComplete, success = false)
            return
        }
        cancelOperationTimeout()
        pendingOperation = PendingOperation.Unlock(profile, onComplete)
        val address = profile.address.trim().uppercase()
        if (gattSession.phase == BleConnectionPhase.READY &&
            gattSession.address.equals(address, ignoreCase = true)
        ) {
            sendPendingOperation()
        } else {
            connectAddress(address, BleConnectionPurpose.UNLOCK)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (BuildConfig.DEBUG) Log.d(TAG, "BLE_LIFECYCLE explicit_disconnect")
        queuedExplicitConnect = null
        backgroundRelease?.let(mainHandler::removeCallbacks)
        backgroundRelease = null
        stopOpenerMonitoring()
        stopBatteryScan()
        val operation = pendingOperation
        pendingOperation = PendingOperation.None
        notifyUnlockComplete(operation, success = false)
        cancelOperationTimeout()
        gattSession.closeNow("explicit_disconnect", waitForFreshAdvertisement = false)
        _connectionState.value = BleConnectionState.Disconnected
        _state.value = BleState.Idle
    }

    /** Releases the NFC-owned link without discarding the main activity's monitor state. */
    @SuppressLint("MissingPermission")
    fun releaseAfterNfcUnlock() {
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "NFC_BLE release_session monitoring=$presenceMonitoringActive " +
                    "phase=${gattSession.phase}",
            )
        }
        queuedExplicitConnect = null
        backgroundRelease?.let(mainHandler::removeCallbacks)
        backgroundRelease = null
        val operation = pendingOperation
        pendingOperation = PendingOperation.None
        notifyUnlockComplete(operation, success = false)
        cancelOperationTimeout()
        gattSession.closeNow(
            reason = "nfc_complete",
            waitForFreshAdvertisement = presenceMonitoringActive,
        )
        _connectionState.value = BleConnectionState.Disconnected
        _state.value = BleState.Idle
    }

    private fun notifyUnlockComplete(operation: PendingOperation, success: Boolean) {
        if (operation is PendingOperation.Unlock) {
            notifyUnlockComplete(operation.onComplete, success)
        }
    }

    private fun notifyUnlockComplete(callback: ((success: Boolean) -> Unit)?, success: Boolean) {
        if (callback == null) return
        runCatching { callback(success) }.onFailure { error ->
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "NFC_BLE completion_callback_failed: ${error.message}")
            }
        }
    }

    private fun sendPendingOperation(restartTimeout: Boolean = true) {
        val address = gattSession.address ?: return
        if (gattSession.phase != BleConnectionPhase.READY) {
            val operation = pendingOperation
            val pairing = operation is PendingOperation.Pairing
            pendingOperation = PendingOperation.None
            notifyUnlockComplete(operation, success = false)
            _state.value = if (pairing) {
                BleState.Error(text(R.string.error_pairing_service_not_ready))
            } else {
                BleState.Error(text(R.string.error_unlock_service_not_ready))
            }
            return
        }
        val packet: ByteArray
        val timeoutMs: Long
        when (val operation = pendingOperation) {
            PendingOperation.None -> return
            is PendingOperation.Pairing -> {
                packet = try {
                    UnlockProtocol.buildPasswordPacket(operation.password)
                } catch (error: IllegalArgumentException) {
                    pendingOperation = PendingOperation.None
                    _state.value = BleState.Error(error.message ?: text(R.string.error_invalid_password_parameter))
                    return
                }
                timeoutMs = 13_000L
                _state.value = BleState.Pairing(address)
            }
            is PendingOperation.Unlock -> {
                packet = try {
                    UnlockProtocol.buildOpenPacket(operation.profile)
                } catch (error: IllegalArgumentException) {
                    pendingOperation = PendingOperation.None
                    notifyUnlockComplete(operation, success = false)
                    _state.value = BleState.Error(error.message ?: text(R.string.error_unlock_parameter_invalid))
                    return
                }
                timeoutMs = 8_000L
                _state.value = BleState.Unlocking(address)
            }
        }

        if (restartTimeout || operationTimeout == null) {
            cancelOperationTimeout()
            operationTimeout = Runnable {
                operationTimeout = null
                diagnostics.log("operation_timeout", "timeoutMs=$timeoutMs")
                val operation = pendingOperation
                pendingOperation = PendingOperation.None
                notifyUnlockComplete(operation, success = false)
                _state.value = when (operation) {
                    is PendingOperation.Pairing -> BleState.Error(text(R.string.error_pairing_timeout))
                    else -> BleState.Error(text(R.string.error_unlock_timeout))
                }
                releaseIdleConnection("operation_timeout")
            }.also { mainHandler.postDelayed(it, timeoutMs) }
        }

        if (!gattSession.write(packet) && pendingOperation !is PendingOperation.None) {
            cancelOperationTimeout()
            val operation = pendingOperation
            val pairing = operation is PendingOperation.Pairing
            pendingOperation = PendingOperation.None
            notifyUnlockComplete(operation, success = false)
            _state.value = if (pairing) {
                BleState.Error(text(R.string.error_pairing_service_not_ready))
            } else {
                BleState.Error(text(R.string.error_unlock_service_not_ready))
            }
            releaseIdleConnection("command_not_accepted")
        }
    }

    private fun publishConnecting(address: String) {
        if (presenceMonitoringActive && presenceProfile?.address.equals(address, ignoreCase = true)) {
            publishOpenerConnection(OpenerConnectionStatus.CONNECTING, address)
        }
        _state.value = if (pendingOperation is PendingOperation.Pairing) {
            BleState.Pairing(address)
        } else {
            BleState.Connecting(address)
        }
    }

    private fun handleGattReady(address: String) {
        diagnostics.log("controller_ready", overrideAddress = address)
        _connectionState.value = BleConnectionState.Connected(address)
        if (presenceMonitoringActive && presenceProfile?.address.equals(address, ignoreCase = true)) {
            publishOpenerConnection(OpenerConnectionStatus.CONNECTED, address)
        }
        if (pendingOperation !is PendingOperation.None) {
            sendPendingOperation()
        } else {
            _state.value = BleState.Ready(address)
        }
    }

    private fun handleGattResponse(bytes: ByteArray) {
        diagnostics.log(
            "response",
            "bytes=${bytes.size} summary=${UnlockProtocol.responseSummary(bytes)}",
        )
        cancelOperationTimeout()
        val address = gattSession.address.orEmpty()
        val operation = pendingOperation
        pendingOperation = PendingOperation.None
        when (operation) {
            is PendingOperation.Pairing -> {
                _state.value = if (UnlockProtocol.isSuccess(bytes)) {
                    BleState.Paired(address)
                } else {
                    BleState.Error(
                        if (UnlockProtocol.isFailure(bytes)) {
                            text(R.string.error_pairing_password_wrong)
                        } else {
                            text(R.string.error_pairing_unknown_response, UnlockProtocol.responseSummary(bytes))
                        },
                    )
                }
            }
            is PendingOperation.Unlock -> {
                val summary = UnlockProtocol.responseSummary(bytes)
                val success = UnlockProtocol.isSuccess(bytes)
                _state.value = if (success) {
                    BleState.Success(text(R.string.unlock_success, summary))
                } else {
                    BleState.Error(text(R.string.error_opener_response, summary))
                }
                notifyUnlockComplete(operation, success)
            }
            PendingOperation.None -> _state.value = BleState.Ready(address)
        }
        scheduleBackgroundRelease("background_operation_complete")
    }

    private fun handleGattFailure(
        address: String,
        purpose: BleConnectionPurpose,
        failure: BleGattFailure,
    ) {
        if (BuildConfig.DEBUG) Log.d(TAG, "BLE_FAILURE purpose=${purpose.name.lowercase()} failure=$failure")
        val operation = pendingOperation
        val pairing = operation is PendingOperation.Pairing
        pendingOperation = PendingOperation.None
        notifyUnlockComplete(operation, success = false)
        _state.value = when (failure) {
            BleGattFailure.ConnectionStartFailed,
            BleGattFailure.ConnectionTimeout,
            -> if (pairing) {
                BleState.Error(text(R.string.error_pairing_connection_timeout))
            } else {
                BleState.Error(text(R.string.error_connection_timeout))
            }
            is BleGattFailure.UnexpectedDisconnect -> when (operation) {
                is PendingOperation.Pairing -> BleState.Error(text(R.string.error_pairing_connection_lost, failure.status))
                is PendingOperation.Unlock -> BleState.Error(text(R.string.error_unlock_connection_lost, failure.status))
                PendingOperation.None -> BleState.Error(text(R.string.error_connection_lost, failure.status))
            }
            is BleGattFailure.ServicesFailed -> if (pairing) {
                BleState.Error(text(R.string.error_pairing_services_failed, failure.status))
            } else {
                BleState.Error(text(R.string.error_services_failed, failure.status))
            }
            BleGattFailure.ServiceMissing -> if (pairing) {
                BleState.Error(text(R.string.error_pairing_service_missing))
            } else {
                BleState.Error(text(R.string.error_service_missing))
            }
            BleGattFailure.NotificationsFailed -> if (pairing) {
                BleState.Error(text(R.string.error_pairing_notifications_failed))
            } else {
                BleState.Error(text(R.string.error_notifications_failed))
            }
            is BleGattFailure.WriteStatus -> if (pairing) {
                BleState.Error(text(R.string.error_pairing_write_status, failure.status))
            } else {
                BleState.Error(text(R.string.error_unlock_write_status, failure.status))
            }
            is BleGattFailure.WriteException -> if (pairing) {
                BleState.Error(text(R.string.error_pairing_write_exception, failure.message))
            } else {
                BleState.Error(text(R.string.error_unlock_write_exception, failure.message))
            }
            BleGattFailure.WriteBusy -> if (pairing) {
                BleState.Error(text(R.string.error_pairing_write_busy))
            } else {
                BleState.Error(text(R.string.error_unlock_write_busy))
            }
            BleGattFailure.NotificationBusy -> if (pairing) {
                BleState.Error(text(R.string.error_pairing_notification_busy))
            } else {
                BleState.Error(text(R.string.error_unlock_notification_busy))
            }
        }
        scheduleBackgroundRelease("failure")
    }

    private fun handleGattReleased(address: String, purpose: BleConnectionPurpose, reason: String) {
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "BLE_RELEASED address=${address.ifBlank { "unknown" }} " +
                    "purpose=${purpose.name.lowercase()} reason=$reason " +
                    "waitingForFresh=${gattSession.waitingForFreshAdvertisement}",
            )
        }
        _connectionState.value = BleConnectionState.Disconnected
        if (_state.value is BleState.Connecting || _state.value is BleState.Ready) {
            _state.value = BleState.Idle
        }
        val queued = queuedExplicitConnect
        val queuedCanRunInBackground = queued?.second == BleConnectionPurpose.UNLOCK ||
            queued?.second == BleConnectionPurpose.PAIRING
        if (queued != null && (appInForeground || queuedCanRunInBackground)) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "BLE_CONNECT dequeue_after_release address=${queued.first} " +
                        "purpose=${queued.second.name.lowercase()} foreground=$appInForeground",
                )
            }
            queuedExplicitConnect = null
            connectAddress(queued.first, queued.second)
            return
        }
        if (presenceMonitoringActive &&
            reason != "monitoring_stopped" && reason != "explicit_disconnect"
        ) {
            presenceLastRssi = null
            presenceLastSeenAtMs = 0L
            publishOpenerConnection(OpenerConnectionStatus.NOT_FOUND, presenceProfile?.address.orEmpty())
            if (appInForeground) startPresenceScanWindow()
        }
    }

}
