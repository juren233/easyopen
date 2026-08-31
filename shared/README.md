# EasyOpen shared

这是 Android/iOS 共用代码的目标目录。

## 目录边界

- `src/commonMain`：跨平台模型、状态、Compose Multiplatform + Miuix UI 和平台接口。
- `src/commonTest`：跨平台纯逻辑测试。
- `src/androidMain`：Android BLE、NFC、权限、存储和相机适配。
- `src/iosMain`：iOS CoreBluetooth、Core NFC、权限、存储和相机适配。

当前只建立迁移骨架，尚未把现有 Android App 接入本模块。接入前先完成 Gradle/Kotlin/Compose Multiplatform 工具链兼容性验证。
