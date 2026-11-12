package com.juren233.easyopen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.juren233.easyopen.utils.UpdateData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.juren233.easyopen.ble.BleDoorController
import com.juren233.easyopen.data.AppSettingsStore
import com.juren233.easyopen.data.DeviceStore
import com.juren233.easyopen.nfc.NfcTagReader

class MainActivity : ComponentActivity() {
    private val updateCheckScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateCheckJob: Job? = null

    private lateinit var controller: BleDoorController
    private lateinit var nfcReader: NfcTagReader
    private var permissionsGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsGranted = controller.hasBluetoothPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = (application as EasyOpenApplication).bleDoorController
        nfcReader = NfcTagReader(this)
        permissionsGranted = controller.hasBluetoothPermission()
        Log.i(TAG, "BLE_OWNER controller=${System.identityHashCode(controller)} pid=${android.os.Process.myPid()}")
        Log.i(
            TAG,
            "onCreate action=${intent?.action} hasTag=${nfcReader.hasTag(intent)} " +
                "dispatchRecords=${com.juren233.easyopen.nfc.NfcCommand.messageFromIntent(intent)?.records?.size ?: 0}",
        )
        nfcReader.handleIntent(intent)
        startOpenerMonitoringEarly()
        setContent {
            EasyOpenApp(
                controller = controller,
                permissionsGranted = permissionsGranted,
                onRequestPermissions = ::requestBluetoothPermissions,
                onOpenBluetoothSettings = {
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                onNfcWriteTagReset = nfcReader::resetDuplicateDetection,
                nfcEvents = nfcReader.events,
                nfcReaderState = nfcReader.state,
            )
        }
    }

    /**
     * Starts scanning before the first Compose frame so the opener is usually already
     * discovered when the home page appears. EasyOpenNavigation re-issues the same call later.
     */
    private fun startOpenerMonitoringEarly() {
        if (!controller.hasBluetoothPermission()) return
        val preferences = getSharedPreferences("easyopen", MODE_PRIVATE)
        val devices = DeviceStore.load(preferences)
        if (!DeviceStore.onboardingComplete(preferences, devices)) return
        val activeAddress = DeviceStore.activeAddress(preferences, devices)
        val profile = devices.firstOrNull { it.address.equals(activeAddress, ignoreCase = true) }
            ?: devices.firstOrNull()
            ?: return
        val settings = AppSettingsStore.load(preferences)
        controller.startOpenerMonitoring(
            profile = profile,
            autoConnectEnabled = settings.autoConnectEnabled,
            autoConnectRssiThreshold = settings.autoConnectRssiThreshold,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.i(
            TAG,
            "onNewIntent action=${intent.action} hasTag=${nfcReader.hasTag(intent)} " +
                "dispatchRecords=${com.juren233.easyopen.nfc.NfcCommand.messageFromIntent(intent)?.records?.size ?: 0}",
        )
        nfcReader.handleIntent(intent)
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "BLE_LIFECYCLE onStart controller=${System.identityHashCode(controller)}")
        controller.enterForeground()
        updateCheckJob?.cancel()
        updateCheckJob = updateCheckScope.launch {
            UpdateData.refresh(
                currentVersionName = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        nfcReader.onResume()
    }

    override fun onPause() {
        nfcReader.onPause()
        super.onPause()
    }

    override fun onStop() {
        Log.d(TAG, "BLE_LIFECYCLE onStop controller=${System.identityHashCode(controller)}")
        controller.enterBackground()
        super.onStop()
    }

    override fun onDestroy() {
        updateCheckScope.cancel()
        nfcReader.close()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "EasyOpenMainActivity"
    }
}
