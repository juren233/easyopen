package com.juren233.easyopen

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.juren233.easyopen.ble.BleDoorController
import com.juren233.easyopen.data.AppSettingsStore
import com.juren233.easyopen.shared.ui.EasyOpenTheme
import com.juren233.easyopen.nfc.NfcTagEvent
import com.juren233.easyopen.nfc.NfcReaderState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun EasyOpenApp(
    controller: BleDoorController,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onNfcWriteTagReset: () -> Unit,
    nfcEvents: Flow<NfcTagEvent>,
    nfcReaderState: StateFlow<NfcReaderState>,
) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("easyopen", Context.MODE_PRIVATE)
    }
    var appSettings by remember { mutableStateOf(AppSettingsStore.load(preferences)) }

    EasyOpenTheme(
        themeMode = appSettings.themeMode,
        monetEnabled = appSettings.monetEnabled,
    ) {
        EasyOpenContent(
            controller = controller,
            preferences = preferences,
            permissionsGranted = permissionsGranted,
            appSettings = appSettings,
            onSettingsChange = { next ->
                appSettings = next
                AppSettingsStore.save(preferences, next)
            },
            onRequestPermissions = onRequestPermissions,
            onOpenBluetoothSettings = onOpenBluetoothSettings,
            onNfcWriteTagReset = onNfcWriteTagReset,
            nfcEvents = nfcEvents,
            nfcReaderState = nfcReaderState,
        )
    }
}
