package com.juren233.easyopen.shared.text

/**
 * User-facing copy needed by native callbacks and UIKit/ CoreBluetooth APIs.
 *
 * Compose UI resolves its copy through Compose Multiplatform Resources. Native
 * delegates, however, report errors outside a composable and cannot synchronously
 * resolve a [StringResource]. Keeping these messages here prevents iOS platform
 * code from growing another set of ad-hoc Chinese literals while preserving the
 * current Android wording.
 */
internal object EasyOpenPlatformText {
    const val defaultAdvertisedOpenerName = "YILA 开门器"
    const val defaultSavedOpenerName = "我的开门器"
    const val bluetoothPermissionRequired = "请先打开蓝牙并允许 EasyOpen 使用蓝牙"
    const val cannotRestoreSavedIosDevice = "无法恢复已保存的 iOS 蓝牙设备，请重新扫描"
    const val bluetoothConnectionFailed = "iOS 蓝牙连接失败"
    const val openerServiceMissing = "未找到开门器通信服务"
    const val notificationEnableFailed = "启用开门器通知失败"
    const val bluetoothConnectionTimeout = "iOS 蓝牙连接超时，请确认开门器在附近后重试"
    const val serviceDiscoveryTimeout = "iOS 蓝牙服务发现超时，请确认开门器固件支持 EasyOpen 协议"
    const val commandParametersInvalid = "iOS 蓝牙命令参数无效"
    const val commandTimeout = "iOS 蓝牙命令超时，请确认开门器在附近后重试"

    const val qrGenerationFailed = "无法生成分享二维码"
    const val confirm = "确定"
    const val cameraUnavailable = "无法访问相机"
    const val qrScannerStartFailed = "无法启动二维码扫描"
    const val close = "关闭"

    const val nfcUnsupported = "当前设备不支持 Core NFC"
    const val nfcSessionAlreadyActive = "已有 NFC 会话正在进行"
    const val nfcReadPrompt = "请将 iPhone 靠近 EasyOpen NFC 标签"
    const val nfcWritePrompt = "请将 iPhone 靠近要写入的 NFC 标签"
    const val ndefUnsupported = "此 NFC 标签不支持 NDEF"
    const val nfcReadOnly = "此 NFC 标签是只读标签"
    const val nfcCapacityInsufficient = "NFC 标签空间不足"
    const val nfcWriteSucceeded = "写入成功"
    const val easyOpenNfcContentMissing = "未找到 EasyOpen NFC 内容"

    const val invalidQr = "二维码无效或不是 EasyOpen 分享码"
    const val shareOpenerTitle = "分享开门器"
    const val qrGenerationRequiresSixDigitPassword = "无法生成分享二维码，请检查开门器密码是否为 6 位数字"
    const val backupUnreadable = "无法读取 EasyOpen 备份文件"
    const val unpairedOpener = "未配对开门器"

    fun shareQrSummary(deviceCount: Int): String =
        "已包含 ${deviceCount} 台开门器配置。请仅向可信设备展示此二维码。"
}
