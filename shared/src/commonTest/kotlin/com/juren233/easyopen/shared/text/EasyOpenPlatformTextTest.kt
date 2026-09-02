package com.juren233.easyopen.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EasyOpenPlatformTextTest {
    @Test
    fun shareQrSummaryUsesTheDeviceCount() {
        assertEquals(
            "已包含 2 台开门器配置。请仅向可信设备展示此二维码。",
            EasyOpenPlatformText.shareQrSummary(2),
        )
    }

    @Test
    fun nativeCallbackMessagesAreNotBlank() {
        val messages = listOf(
            EasyOpenPlatformText.bluetoothPermissionRequired,
            EasyOpenPlatformText.cannotRestoreSavedIosDevice,
            EasyOpenPlatformText.bluetoothConnectionFailed,
            EasyOpenPlatformText.openerServiceMissing,
            EasyOpenPlatformText.notificationEnableFailed,
            EasyOpenPlatformText.bluetoothConnectionTimeout,
            EasyOpenPlatformText.serviceDiscoveryTimeout,
            EasyOpenPlatformText.commandParametersInvalid,
            EasyOpenPlatformText.commandTimeout,
            EasyOpenPlatformText.qrGenerationFailed,
            EasyOpenPlatformText.confirm,
            EasyOpenPlatformText.cameraUnavailable,
            EasyOpenPlatformText.qrScannerStartFailed,
            EasyOpenPlatformText.close,
            EasyOpenPlatformText.nfcUnsupported,
            EasyOpenPlatformText.nfcSessionAlreadyActive,
            EasyOpenPlatformText.nfcReadPrompt,
            EasyOpenPlatformText.nfcWritePrompt,
            EasyOpenPlatformText.ndefUnsupported,
            EasyOpenPlatformText.nfcReadOnly,
            EasyOpenPlatformText.nfcCapacityInsufficient,
            EasyOpenPlatformText.nfcWriteSucceeded,
            EasyOpenPlatformText.easyOpenNfcContentMissing,
            EasyOpenPlatformText.invalidQr,
            EasyOpenPlatformText.shareOpenerTitle,
            EasyOpenPlatformText.qrGenerationRequiresSixDigitPassword,
            EasyOpenPlatformText.backupUnreadable,
            EasyOpenPlatformText.unpairedOpener,
        )

        messages.forEach { assertFalse(it.isBlank()) }
    }
}
