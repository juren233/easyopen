# EasyOpen 代码组织

本项目按职责拆分 Android/iOS 代码：shared 负责跨平台 UI、状态和协议核心，平台模块负责 BLE、NFC、权限、存储和系统入口，避免页面、导航、BLE 协议和持久化逻辑继续集中在单个文件中。

## 模块边界

- `app/src/main/java/com/juren233/easyopen/MainActivity.kt`
  - Android Activity 生命周期、权限申请和蓝牙设置入口。
- `app/src/main/java/com/juren233/easyopen/EasyOpenApp.kt`
  - 应用级状态编排：已配对设备、当前设备、引导状态和持久化回调。
- `app/src/main/java/com/juren233/easyopen/EasyOpenNavigation.kt`
  - Navigation 3 路由、页面栈和页面间导航回调。
- `app/src/main/java/com/juren233/easyopen/ui/`
  - `PermissionGuidePage.kt`：首次授权引导。
  - `PairingPage.kt`：Android 平台宿主，只负责端口转换和 Android 回调。
  - `HomePage.kt`：Android 主页宿主、开门操作、二维码/NFC/更新等平台副作用。
  - `DeviceChooserDialog.kt`：已添加开门器切换弹窗。
  - `UiComponents.kt`：仍在 Android 使用的本地复用组件。
- `shared/src/commonMain/kotlin/com/juren233/easyopen/shared/ui/`
  - `PairingPageContent.kt`：Android/iOS 共用的搜索、密码配对和开门器设置 UI。
  - `HomePageContent.kt`：Android/iOS 共用主页 UI。
  - `SettingsPageContent.kt`：Android/iOS 共用设置 UI。
- `app/src/main/java/com/juren233/easyopen/ble/`
  - `BleDoorController.kt`：Android 扫描、连接、配对、通知、重试和状态流。
  - `AndroidBlePort.kt`：将 Android BLE 状态和命令映射到 shared API。
  - `OpenerConnectionState.kt`：主页四态连接快照（未发现、已发现、连接中、已连接）。
  - `OpenerConnectionPolicy.kt`：RSSI 自动连接阈值、信号新鲜度和自动重试冷却策略。
  - `UnlockProtocol.kt`：shared 协议核心的 Android 兼容 facade。
- `shared/src/commonMain/kotlin/com/juren233/easyopen/shared/protocol/`
  - `UnlockProtocol.kt`：Android/iOS 共用的命令构造、响应解析和广告电量解析。
  - `Aes128.kt`、`Md5.kt`：仅实现旧设备协议所需的跨平台密码算法。
- `app/src/main/java/com/juren233/easyopen/data/`
  - `DeviceProfile.kt`：设备配置模型。
  - `DeviceStore.kt`：本地设备配置持久化。
- `app/src/main/res/values/strings.xml`
  - 用户可见固定文案和错误提示资源。

## 依赖方向

shared 页面只依赖平台无关快照和回调；页面不直接操作 GATT、`BluetoothDevice`、`CBPeripheral` 或 `SharedPreferences`。Android `AndroidBlePort` 和 iOS `IosCoreBluetoothPort` 分别实现同一份 BLE 边界。协议解析保持在 `commonMain` 的纯逻辑模块，便于 Android host test 和 iOS target 编译验证。新增功能优先放入职责对应的文件，避免继续扩大 `MainActivity.kt` 或单个页面文件。

## NFC NDEF 开门链路

- `app/src/main/java/com/juren233/easyopen/nfc/NfcCommand.kt` 定义 EasyOpen 自有的应用专用 MIME 类型 `application/com.juren233.easyopen.unlock`，并生成标准 NDEF MIME 记录；不使用或持久化 NFC 标签 UID。
- `app/src/main/java/com/juren233/easyopen/nfc/NfcTagReader.kt` 在主界面前台使用 `enableForegroundDispatch`，把标签交给写入流程或开门流程，并读取写入前的 NDEF 内容。
- `app/src/main/java/com/juren233/easyopen/nfc/NfcTagWriter.kt` 支持向可写 NDEF 标签写入、保留原有记录并将 EasyOpen 记录放在第一条，也支持先格式化 `NdefFormatable` 空白标签。
- `NfcEntryActivity` 只注册 `NDEF_DISCOVERED` + EasyOpen MIME 类型，因此未写入 NDEF 的空白标签不会误唤起应用；后台碰到已写入标签时由透明入口直接执行 BLE 开门，不要求用户先手动打开主界面。

## 主页连接状态流

- 主页可见时由 `EasyOpenNavigation` 启动 `startOpenerMonitoring(profile)`；离开主页时停止监视，但不因为页面切换主动断开已经建立的 GATT 链路。
- 监视器只用已配对设备的 MAC 地址锁定目标，持续运行低延迟扫描窗口；窗口结束、超时或临时扫描失败都自动重启，不把扫描异常写入主页四态。
- 目标广播先发布“已发现”；只有 RSSI 达到 `OpenerConnectionPolicy.AUTO_CONNECT_RSSI_THRESHOLD` 才自动连接。用户点击一键开门时可以绕过阈值立即连接。
- 连接成功并完成服务/通知初始化后发布“已连接”；物理链路断开或连接准备失败后回到“未发现”/仍然新鲜的“已发现”，等待下一次目标广播。
- 电量更新与目标广播在同一监视回调内完成，避免为了电量再开第二条扫描连接路径。

## 分享、扫码与应用设置模块

- `app/src/main/java/com/juren233/easyopen/data/TransferCodec.kt`
  - 统一处理开门器分享二维码和本地备份 JSON；二维码载荷使用 AES-GCM 加密并带认证校验，分享使用紧凑二进制 envelope 降低 QR 版本，且兼容旧的 JSON 分享载荷。
- `app/src/main/java/com/juren233/easyopen/transfer/QrTransfer.kt`
  - 负责 QR Code 位图生成，以及从系统相册 URI 解码二维码；不把图片选择或页面状态放进 BLE 控制器。
- `app/src/main/java/com/juren233/easyopen/transfer/TransferFileDecoder.kt`
  - 负责从系统文件选择器读取并解析备份 JSON，避免把文件 I/O 和页面状态混在一起。
- `app/src/main/java/com/juren233/easyopen/data/AppSettingsStore.kt`
  - 持久化主题模式和莫奈取色开关，默认系统主题、关闭莫奈取色。
- `app/src/main/java/com/juren233/easyopen/ui/ShareDialogs.kt`
  - 多开门器分享选择与二维码预览；单开门器直接展示二维码，不额外弹选择列表。
- `app/src/main/java/com/juren233/easyopen/ui/QrImportPage.kt`
  - 独立扫码添加子页面，使用实时相机、系统相册和备份文件选择器识别/读取传输内容，并在识别成功后提供三种重新操作入口。
- `app/src/main/java/com/juren233/easyopen/ui/TransferSourceActions.kt`
  - 复用初始态和识别成功态的扫码、相册和备份恢复操作按钮，避免文案和状态分支散落在页面内。
- `app/src/main/java/com/juren233/easyopen/ui/SettingsPage.kt`
  - Android 设置宿主，包含 Android 文件备份/恢复副作用。
- `shared/src/commonMain/kotlin/com/juren233/easyopen/shared/ui/EasyOpenTheme.kt`
  - 将持久化主题模式映射到 Miuix `ThemeController`，避免在 `MainActivity` 固定包裹主题导致设置无法即时生效。
- `app/src/main/java/com/juren233/easyopen/ui/QrCameraPreview.kt`
  - JourneyApps ZXing Android Embedded 相机预览、运行时相机权限、连续解码和相机生命周期绑定；页面上叠加扫码框，不再只有相册导入。
- `app/src/main/java/com/juren233/easyopen/ui/AdaptiveBarcodeView.kt`
  - 保留方形相机展示，但把 JourneyApps 的内部解码取景区域扩展到完整 Camera1 预览源，避免二维码位置或尺寸被 UI 方框限制。
- `app/src/main/java/com/juren233/easyopen/transfer/QrFrameDecoder.kt`
  - 从 CameraX Y 平面提取亮度数据并尝试多方向 QR 解码，避免将相机帧处理堆进页面。

- `app/src/main/java/com/juren233/easyopen/OnboardingNavigation.kt`
  - 首启引导专用的真实 Navigation 3 栈；首启配对页是根页面，扫码导入页作为子页面入栈，返回通过 `NavDisplay` 出栈，不再用布尔状态覆盖页面。

## 更新检查与正式签名

- `app/src/main/java/com/juren233/easyopen/utils/UpdateData.kt` 在 Activity 进入前台时读取 `juren233/easyopen` 的最新正式 GitHub Release，并从 Release APK 文件名提取 `versionCode`；只有远端版本更高时才发布更新状态。
- `HomePage.kt` 只在 `UpdateData.availableUpdate` 非空时插入顶部更新横幅，未检测到更新时不占用主页布局空间。
- `app/build.gradle.kts` 将 Debug 与 Release 指向同一个 `easyOpenRelease` 签名配置；本地密钥通过被忽略的 `keystore.properties` 提供，CI 通过仓库 Secrets 注入同一份密钥。


## iOS 平台边界

- `shared/src/iosMain/kotlin/com/juren233/easyopen/shared/platform/IosCoreBluetoothPort.kt` 持有 `CBCentralManager`、`CBPeripheral`、服务/特征发现、通知启用、配对/开门写入和通知响应状态。
- 只有 `DeviceBinding.IosPeripheral(identifier)` 作为 iOS 本地连接句柄进入 shared 状态；开门器广播中的硬件 MAC 通过 `CoreDeviceProfile.hardwareMac` 作为跨平台匹配键保存，不把 `CBPeripheral` 或 iOS UUID 写入分享/备份格式。iOS 多设备列表保存在 `NSUserDefaults`，开门器密码保存在 `Keychain` generic-password 项，由 shared 的 `EasyOpenSavedDevice` 规则处理活动设备切换。
- `shared/src/iosMain/kotlin/com/juren233/easyopen/shared/ui/IosMainViewController.kt` 提供 shared Pairing/Home/Settings 宿主；iOS 设备元数据、密码和设置分别由 `shared/src/iosMain/kotlin/com/juren233/easyopen/shared/storage/` 下的平台存储负责。
- `shared/src/iosMain/kotlin/com/juren233/easyopen/shared/platform/IosUpdateChecker.kt` 通过 `CFBundleVersion` 与 GitHub Release APK 资产比较更新，并把 Release 页面跳转交给 iOS 宿主。
- `shared/src/commonMain/kotlin/com/juren233/easyopen/shared/transfer/EasyOpenBackupCodec.kt` 统一 Android/iOS 的备份 JSON：可携带开门器广播镜像的硬件 MAC 作为跨平台匹配键，但不携带 iOS 本地 Peripheral UUID；`shared/src/iosMain/kotlin/com/juren233/easyopen/shared/platform/IosDocumentTransferPresenter.kt` 负责 iOS 系统分享面板和文件选择器。
- Core NFC、iOS 二维码导入导出和 iOS 真机验收仍是独立待办。Android/iOS 备份可携带 `androidMac` 作为硬件广播匹配键，并继续兼容旧 `address`；导入后优先依据 Manufacturer Data 自动匹配当前平台 Peripheral，匹配不到时再进入手动选择/重新配对流程；不写入 iOS `CBPeripheral.identifier`。
