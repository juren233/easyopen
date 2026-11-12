package com.juren233.easyopen.ble

import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import com.juren233.easyopen.BuildConfig

/** Shared low-latency scan-window lifecycle used by discovery, presence, and battery scans. */
internal class BleScanWindow(
    private val scannerProvider: () -> BluetoothLeScanner?,
    private val canScan: () -> Boolean,
    private val durationMs: () -> Long,
    private val restartDelayMs: Long,
    private val label: String,
    private val handler: Handler,
    private val budget: BleScanStartBudget,
    private val filtersProvider: () -> List<ScanFilter> = { emptyList() },
    private val handleScanResult: (windowId: Int, result: ScanResult) -> Unit,
    private val handleBatchScanResults: (windowId: Int, results: List<ScanResult>) -> Unit = { _, _ -> },
    private val handleScanFailed: (windowId: Int, errorCode: Int) -> Unit = { _, _ -> },
    private val handleWindowFinished: (windowId: Int) -> Unit = { _ -> },
) {
    private var active = false
    private var windowId = 0
    private var callback: ScanCallback? = null
    private var windowFinish: Runnable? = null
    private var restart: Runnable? = null
    private var windowStartedAtMs = 0L
    private var callbackCount = 0
    private var firstCallbackElapsedMs: Long? = null

    private val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        .setReportDelay(0L)
        .build()

    @SuppressLint("MissingPermission")
    fun start() {
        if (active && callback != null) {
            log("start_ignored", "reason=already_active")
            return
        }
        active = true
        restart?.let(handler::removeCallbacks)
        restart = null
        log("start_requested")
        startWindow()
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!active && callback == null && restart == null) return
        active = false
        restart?.let(handler::removeCallbacks)
        restart = null
        stopWindow("explicit_stop")
    }

    @SuppressLint("MissingPermission")
    private fun startWindow() {
        if (!active || !canScan()) return
        val scanner = scannerProvider() ?: run {
            log("scanner_unavailable")
            scheduleRestart()
            return
        }
        stopWindow("replace_window")
        // The system silently drops startScan beyond its per-app quota, so wait instead.
        val startDelayMs = budget.delayUntilStartAllowed(rotation = false)
        if (startDelayMs > 0L) {
            log("start_deferred_by_quota", "delayMs=$startDelayMs")
            scheduleRestart(startDelayMs)
            return
        }
        val currentWindowId = ++windowId
        windowStartedAtMs = SystemClock.elapsedRealtime()
        callbackCount = 0
        firstCallbackElapsedMs = null
        val filters = runCatching { filtersProvider() }.getOrElse { error ->
            log("filter_build_failed", "error=${error.javaClass.simpleName}:${error.message}")
            emptyList()
        }
        val currentCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (callback !== this) return
                callbackCount += 1
                if (firstCallbackElapsedMs == null) {
                    firstCallbackElapsedMs = SystemClock.elapsedRealtime() - windowStartedAtMs
                    log(
                        "first_result",
                        "window=$currentWindowId callbackType=$callbackType rssi=${result.rssi} " +
                            "elapsedMs=$firstCallbackElapsedMs",
                    )
                }
                handleScanResult(currentWindowId, result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                if (callback !== this) return
                callbackCount += results.size
                if (firstCallbackElapsedMs == null) {
                    firstCallbackElapsedMs = SystemClock.elapsedRealtime() - windowStartedAtMs
                    log(
                        "first_batch_result",
                        "window=$currentWindowId count=${results.size} elapsedMs=$firstCallbackElapsedMs",
                    )
                }
                handleBatchScanResults(currentWindowId, results)
            }

            override fun onScanFailed(errorCode: Int) {
                if (BuildConfig.DEBUG) {
                    Log.w(
                        TAG,
                        "$label scan failed window=$currentWindowId errorCode=$errorCode " +
                            "elapsedMs=${elapsedSinceStart()} callbacks=$callbackCount",
                    )
                }
                if (callback === this) {
                    callback = null
                    handleScanFailed(currentWindowId, errorCode)
                    scheduleRestart()
                }
            }
        }
        callback = currentCallback
        log(
            "start_scan_called",
            "window=$currentWindowId filters=${filters.size} mode=low_latency reportDelayMs=0",
        )
        budget.recordStart()
        runCatching { scanner.startScan(filters, settings, currentCallback) }.onSuccess {
            log("start_scan_returned", "window=$currentWindowId elapsedMs=${elapsedSinceStart()}")
        }.onFailure { error ->
            if (BuildConfig.DEBUG) {
                Log.w(
                    TAG,
                    "$label scan start failed window=$currentWindowId " +
                        "elapsedMs=${elapsedSinceStart()}: ${error.message}",
                )
            }
            if (callback === currentCallback) callback = null
            handleScanFailed(currentWindowId, -1)
            scheduleRestart()
        }
        armWindowFinish(currentCallback, currentWindowId, durationMs().coerceAtLeast(1_000L))
    }

    private fun armWindowFinish(currentCallback: ScanCallback, currentWindowId: Int, delayMs: Long) {
        windowFinish = Runnable {
            if (callback !== currentCallback) return@Runnable
            log(
                "window_finished",
                "window=$currentWindowId elapsedMs=${elapsedSinceStart()} callbacks=$callbackCount " +
                    "firstCallbackMs=${firstCallbackElapsedMs ?: "none"}",
            )
            handleWindowFinished(currentWindowId)
            // Rotating needs a fresh start; if the quota is short, keep the running scan instead.
            val rotationDelayMs = budget.delayUntilStartAllowed(rotation = true)
            if (rotationDelayMs > 0L) {
                log("rotation_deferred_by_quota", "delayMs=$rotationDelayMs")
                armWindowFinish(currentCallback, currentWindowId, rotationDelayMs.coerceAtLeast(1_000L))
                return@Runnable
            }
            stopWindow("window_finished")
            scheduleRestart()
        }.also { handler.postDelayed(it, delayMs) }
    }

    private fun scheduleRestart(delayMs: Long = restartDelayMs) {
        if (!active || restart != null) return
        log("restart_scheduled", "delayMs=$delayMs")
        restart = Runnable {
            restart = null
            log("restart_triggered")
            startWindow()
        }.also { handler.postDelayed(it, delayMs) }
    }

    @SuppressLint("MissingPermission")
    private fun stopWindow(reason: String) {
        windowFinish?.let(handler::removeCallbacks)
        windowFinish = null
        val currentCallback = callback ?: return
        log(
            "stop_scan_called",
            "reason=$reason elapsedMs=${elapsedSinceStart()} callbacks=$callbackCount",
        )
        runCatching { scannerProvider()?.stopScan(currentCallback) }
        callback = null
    }

    private fun elapsedSinceStart(): Long =
        if (windowStartedAtMs == 0L) 0L else SystemClock.elapsedRealtime() - windowStartedAtMs

    private fun log(stage: String, details: String = "") {
        if (!BuildConfig.DEBUG) return
        val suffix = details.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
        Log.d(TAG, "$label $stage$suffix")
    }

    private companion object {
        const val TAG = "BleScanWindow"
    }
}
