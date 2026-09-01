# EasyOpen shared

这是 Android/iOS 共用代码的目标目录。

## 目录边界

- `src/commonMain`：跨平台模型、状态、Compose Multiplatform + Miuix UI 和平台接口。
- `src/commonTest`：跨平台纯逻辑测试。
- `src/androidMain`：Android BLE、NFC、权限、存储和相机适配。
- `src/iosMain`：iOS CoreBluetooth、Core NFC、权限、存储和相机适配。

当前已接入首批共用 UI、路由、资源、Home 状态模型和 BLE 平台边界：

- `commonMain/ui/HomePageContent.kt`、`SettingsPageContent.kt` 等只依赖跨平台模型和回调。
- `commonMain/state` 保存 Home/BLE 快照，不持有 Android `BluetoothDevice`、iOS `CBPeripheral` 或 Context。
- Android `AndroidBlePort` 将现有 `BleDoorController` 的 StateFlow 转成 shared 快照。
- `shared/platform/EasyOpenBleUuids.kt` 维护 Android/iOS 共同的 Nordic UART-style UUID 合约。
- iOS `IosCoreBluetoothPort` 已有扫描、连接、服务/特征发现、通知配置、配对/开门命令写入、通知响应分类、连接/服务发现超时、13 秒命令超时和一次有限重试；真机时序仍待确认。
- `commonMain/state` 提供 `EasyOpenSavedDevice` 及活动设备/替换规则，iOS 宿主使用它实现多设备切换。
- `commonMain/transfer` 的 `EasyOpenTransferProfile` 将 `androidMac` 定义为可选硬件匹配字段；它来自开门器 Manufacturer Data 中镜像的 MAC，iOS 不写入 `CBPeripheral.identifier`。Android/iOS 导入会优先匹配广播身份，匹配不到时保留手动选择兜底，并兼容旧 `address` 字段。

尚未把所有 Android 页面、存储、NFC、相机和配对流程迁移到 shared；这些迁移必须逐批编译并保留 Android 回滚路径。协议核心和配对页面已经完成首批迁移。
