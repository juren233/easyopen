package com.juren233.easyopen

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.juren233.easyopen.ble.BleDoorController
import com.juren233.easyopen.data.DeviceStore
import com.juren233.easyopen.nfc.NfcCommand

/**
 * Invisible NFC entry point used when Android dispatches a valid EasyOpen NDEF
 * tag while the main activity is not running. It keeps the BLE controller alive
 * long enough for the unlock transaction, then closes without showing UI.
 */
class NfcEntryActivity : Activity() {
    private lateinit var controller: BleDoorController
    private val mainHandler = Handler(Looper.getMainLooper())
    private val finishRunnable = Runnable { finishEntry() }
    private var entryFinished = false
    private var unlockTriggered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = (application as EasyOpenApplication).bleDoorController
        Log.i(
            TAG,
            "BLE_OWNER controller=${System.identityHashCode(controller)} pid=${android.os.Process.myPid()}",
        )
        triggerUnlock(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!entryFinished && !unlockTriggered) triggerUnlock(intent)
    }

    private fun triggerUnlock(intent: Intent?) {
        val preferences = getSharedPreferences("easyopen", MODE_PRIVATE)
        val devices = DeviceStore.load(preferences)
        val activeAddress = DeviceStore.activeAddress(preferences, devices)
        val activeProfile = devices.firstOrNull {
            it.address.equals(activeAddress, ignoreCase = true)
        } ?: devices.firstOrNull()

        Log.d(
            TAG,
            "NFC_BLE trigger hasUnlockIntent=${NfcCommand.isUnlockIntent(intent)} " +
                "profile=${activeProfile?.address ?: "none"} elapsedMs=${SystemClock.elapsedRealtime()}",
        )

        if (
            NfcCommand.isUnlockIntent(intent) &&
            activeProfile != null &&
            controller.hasBluetoothPermission() &&
            controller.isBluetoothEnabled()
        ) {
            unlockTriggered = true
            Log.d(TAG, "NFC_BLE unlock_requested address=${activeProfile.address}")
            controller.unlock(activeProfile) { success ->
                Log.d(TAG, "NFC_BLE unlock_complete success=$success")
                mainHandler.post(::finishEntry)
            }
            mainHandler.removeCallbacks(finishRunnable)
            mainHandler.postDelayed(finishRunnable, BLE_UNLOCK_GRACE_PERIOD_MS)
        } else {
            finishEntry()
        }
    }

    private fun finishEntry() {
        if (entryFinished) return
        entryFinished = true
        mainHandler.removeCallbacks(finishRunnable)
        Log.d(TAG, "NFC_BLE finish elapsedMs=${SystemClock.elapsedRealtime()}")
        controller.releaseAfterNfcUnlock()
        finish()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(finishRunnable)
        if (!entryFinished) controller.releaseAfterNfcUnlock()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "NfcEntryActivity"
        // Worst case of BleDoorController's unlock (connect budget plus response wait), plus 1 second.
        const val BLE_UNLOCK_GRACE_PERIOD_MS = BleDoorController.UNLOCK_WORST_CASE_MS + 1_000L
    }
}
