package com.juren233.easyopen.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.juren233.easyopen.R
import com.juren233.easyopen.data.AppSettings
import com.juren233.easyopen.shared.ui.SettingsPageContent
import com.juren233.easyopen.data.DeviceProfile
import com.juren233.easyopen.data.TransferCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
@Composable
internal fun SettingsPage(
    devices: List<DeviceProfile>,
    activeAddress: String,
    settings: AppSettings,
    onBack: () -> Unit,
    onThemeModeChange: (Int) -> Unit,
    onMonetChange: (Boolean) -> Unit,
    onAutoUnlockOnAppOpenChange: (Boolean) -> Unit,
    onAutoConnectEnabledChange: (Boolean) -> Unit,
    onAutoConnectRangeChange: (Int) -> Unit,
    onCustomAutoConnectRssiChange: (Int) -> Unit,
    onRestore: (TransferCodec.BackupSnapshot) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupSuccessMessage = stringResource(R.string.backup_success)
    val backupFailedMessage = stringResource(R.string.backup_failed)
    val restoreSuccessMessage = stringResource(R.string.restore_success)
    val restoreFailedMessage = stringResource(R.string.restore_failed)

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = withContext(Dispatchers.Default) {
                TransferCodec.encodeBackup(
                    devices = devices,
                    activeAddress = activeAddress,
                    themeMode = settings.themeMode,
                    monetEnabled = settings.monetEnabled,
                    autoUnlockOnAppOpen = settings.autoUnlockOnAppOpen,
                    autoConnectEnabled = settings.autoConnectEnabled,
                    autoConnectRange = settings.autoConnectRange,
                    customAutoConnectRssi = settings.customAutoConnectRssi,
                )
            }
            val saved = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(raw.toByteArray(Charsets.UTF_8))
                    } ?: error("output stream unavailable")
                }
            }.isSuccess
            Toast.makeText(
                context,
                if (saved) backupSuccessMessage else backupFailedMessage,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val snapshot = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        TransferCodec.decodeBackup(input.bufferedReader(Charsets.UTF_8).readText())
                    }
                }
            }.getOrNull()
            if (snapshot != null) onRestore(snapshot)
            Toast.makeText(
                context,
                if (snapshot != null) restoreSuccessMessage else restoreFailedMessage,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    SettingsPageContent(
        settings = settings,
        onBack = onBack,
        onThemeModeChange = onThemeModeChange,
        onMonetChange = onMonetChange,
        onAutoUnlockOnAppOpenChange = onAutoUnlockOnAppOpenChange,
        onAutoConnectEnabledChange = onAutoConnectEnabledChange,
        onAutoConnectRangeChange = onAutoConnectRangeChange,
        onCustomAutoConnectRssiChange = onCustomAutoConnectRssiChange,
        onBackupRequested = { backupLauncher.launch("easyopen_backup.json") },
        onRestoreRequested = { restoreLauncher.launch(arrayOf("application/json", "text/plain")) },
    )
}
