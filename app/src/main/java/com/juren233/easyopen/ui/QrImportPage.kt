package com.juren233.easyopen.ui

import com.juren233.easyopen.shared.resources.EasyOpenStrings


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.juren233.easyopen.data.DeviceProfile
import com.juren233.easyopen.data.TransferCodec
import com.juren233.easyopen.transfer.QrTransfer
import com.juren233.easyopen.transfer.TransferFileDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun QrImportPage(
    onBack: () -> Unit,
    onImported: (List<DeviceProfile>) -> Unit,
    onRestored: ((TransferCodec.BackupSnapshot) -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    var decodedProfiles by remember { mutableStateOf<List<DeviceProfile>?>(null) }
    var decodedBackup by remember { mutableStateOf<TransferCodec.BackupSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var decoding by remember { mutableStateOf(false) }
    var cameraSession by remember { mutableStateOf(0) }
    val invalidQrMessage = stringResource(EasyOpenStrings.qr_import_invalid)
    val restoreFailedMessage = stringResource(EasyOpenStrings.restore_failed)

    val recognized = decodedProfiles != null || decodedBackup != null

    fun clearRecognition(restartCamera: Boolean) {
        decodedProfiles = null
        decodedBackup = null
        error = null
        decoding = false
        if (restartCamera) cameraSession += 1
    }

    fun handleQrPayload(raw: String) {
        val profiles = TransferCodec.decodeShare(raw)
        if (profiles.isNullOrEmpty()) {
            error = invalidQrMessage
            cameraSession += 1
        } else {
            decodedProfiles = profiles
            decodedBackup = null
            error = null
        }
    }

    fun decodeGallery(uri: android.net.Uri?) {
        if (uri == null) return
        clearRecognition(restartCamera = true)
        decoding = true
        scope.launch {
            val profiles = withContext(Dispatchers.IO) {
                QrTransfer.decodeFromGallery(context.contentResolver, uri)
                    ?.let(TransferCodec::decodeShare)
            }
            decoding = false
            if (profiles.isNullOrEmpty()) {
                error = invalidQrMessage
                cameraSession += 1
            } else {
                decodedProfiles = profiles
                error = null
            }
        }
    }

    fun decodeBackup(uri: android.net.Uri?) {
        if (uri == null) return
        clearRecognition(restartCamera = true)
        decoding = true
        scope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                TransferFileDecoder.decodeBackup(context.contentResolver, uri)
            }
            decoding = false
            if (snapshot == null) {
                error = restoreFailedMessage
                cameraSession += 1
            } else {
                decodedBackup = snapshot
                error = null
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = ::decodeGallery,
    )
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = ::decodeBackup,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(EasyOpenStrings.scan_import_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(EasyOpenStrings.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (!recognized && !decoding) {
                            val scanShape = RoundedCornerShape(24.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .padding(16.dp)
                                    .clip(scanShape)
                                    .background(Color.Black)
                                    .border(
                                        width = 1.dp,
                                        color = MiuixTheme.colorScheme.primary,
                                        shape = scanShape,
                                    ),
                            ) {
                                key(cameraSession) {
                                    QrCameraPreview(
                                        onDecoded = ::handleQrPayload,
                                    )
                                }
                            }
                        }
                        TransferSourceActions(
                            recognized = recognized,
                            allowBackupRestore = onRestored != null,
                            decoding = decoding,
                            onRescan = { clearRecognition(restartCamera = true) },
                            onGallery = { galleryLauncher.launch("image/*") },
                            onRestoreFile = {
                                backupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                        )
                        error?.let {
                            MiuixText(
                                text = it,
                                color = MiuixTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            decodedProfiles?.let { profiles ->
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MiuixText(text = stringResource(EasyOpenStrings.qr_import_found, profiles.size))
                            profiles.forEach { profile ->
                                MiuixText(
                                    text = "${profile.name}\n${profile.address}",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            MiuixTextButton(
                                text = stringResource(EasyOpenStrings.import_opener),
                                onClick = { onImported(profiles) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                }
            }
            decodedBackup?.let { snapshot ->
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MiuixText(text = stringResource(EasyOpenStrings.backup_found, snapshot.devices.size))
                            snapshot.devices.forEach { profile ->
                                MiuixText(
                                    text = "${profile.name}\n${profile.address}",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            onRestored?.let { restore ->
                                MiuixTextButton(
                                    text = stringResource(EasyOpenStrings.restore_backup),
                                    onClick = { restore(snapshot) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
