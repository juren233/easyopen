package com.juren233.easyopen.ui

import com.juren233.easyopen.shared.resources.EasyOpenStrings


import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.camera.CameraSettings
import com.journeyapps.barcodescanner.camera.CenterCropStrategy
import com.juren233.easyopen.BuildConfig
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Camera preview backed by JourneyApps' open-source ZXing Android integration.
 *
 * The library owns the camera preview callback and decoder thread, so QR frames
 * are decoded continuously without copying every CameraX frame in Compose code.
 */
@Composable
internal fun QrCameraPreview(
    onDecoded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var cameraError by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        cameraError = false
        if (BuildConfig.DEBUG) Log.d(TAG, "camera permission result=$granted")
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MiuixText(
                text = stringResource(EasyOpenStrings.camera_permission_required),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            MiuixTextButton(
                text = stringResource(EasyOpenStrings.allow_camera),
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
        return
    }

    val barcodeView = remember(context) {
        AdaptiveBarcodeView(context).apply {
            // TextureView avoids the SurfaceView clipping/black-frame issue on MIUI.
            setUseTextureView(true)
            setPreviewScalingStrategy(CenterCropStrategy())
            setMarginFraction(0.03)
            setDecoderFactory(
                DefaultDecoderFactory(
                    listOf(BarcodeFormat.QR_CODE),
                    mapOf(DecodeHintType.TRY_HARDER to true),
                    "UTF-8",
                    0,
                ),
            )
            setCameraSettings(
                CameraSettings().apply {
                    setAutoFocusEnabled(true)
                    setContinuousFocusEnabled(true)
                    setExposureEnabled(true)
                    setMeteringEnabled(true)
                },
            )
            addStateListener(
                object : CameraPreview.StateListener {
                    override fun previewSized() = Unit
                    override fun previewStarted() {
                        if (BuildConfig.DEBUG) Log.d(TAG, "preview started")
                    }
                    override fun previewStopped() = Unit
                    override fun cameraError(error: Exception) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "camera preview failed", error)
                        cameraError = true
                    }
                    override fun cameraClosed() = Unit
                },
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { barcodeView },
            modifier = Modifier.fillMaxSize(),
        )
        if (cameraError) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MiuixText(
                    text = stringResource(EasyOpenStrings.camera_start_failed),
                    color = MiuixTheme.colorScheme.error,
                )
                MiuixTextButton(
                    text = stringResource(EasyOpenStrings.retry),
                    onClick = { cameraError = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }

    DisposableEffect(barcodeView, cameraError) {
        if (!cameraError) {
            barcodeView.decodeContinuous(
                object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult) {
                        val text = result.text?.takeIf { it.isNotBlank() } ?: return
                        if (BuildConfig.DEBUG) Log.d(TAG, "camera QR decoded")
                        onDecoded(text)
                    }
                },
            )
            barcodeView.resume()
        } else {
            barcodeView.stopDecoding()
            barcodeView.pause()
        }
        onDispose {
            barcodeView.stopDecoding()
            barcodeView.pause()
        }
    }
}

private const val TAG = "EasyOpenQrCamera"
