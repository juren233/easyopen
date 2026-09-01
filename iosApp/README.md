# EasyOpen iOS App

当前目录包含一个最小的 Xcode 宿主工程，用于把 `shared` 的 Compose Multiplatform UI 生成未签名 IPA。宿主已经切换到 shared 的真实 Home/Pairing Compose 页面；shared 已接入 iOS CoreBluetooth 的扫描、连接、服务/特征发现、通知、配对命令和开门命令骨架；iOS 宿主已支持多设备 NSUserDefaults 列表和旧单设备 key 迁移。NFC、二维码、Keychain、权限细化和真机验收仍按计划迁移。

## 本地/macOS 构建边界

- `shared` 负责生成 `EasyOpenShared.framework`。
- `iosApp/EasyOpen.xcodeproj` 负责生成 iOS App。
- `.github/scripts/build-unsigned-ipa.sh` 通过 `xcodebuild archive CODE_SIGNING_ALLOWED=NO` 归档，再手动打包 `Payload/*.app` 为未签名 `.ipa`。
- Linux 可以编译 Kotlin/Native KLIB，但不能替代 macOS/Xcode 的链接、签名和真机验证。
- 2026-08-31 的 `iOS IPA Validation` run `33417601264` 在 `macos-15` / Xcode 16.4 最终链接阶段失败；2026-09-01 的 `iOS IPA Validation` run `33462150647` 已在 `macos-26` 成功完成 archive、未签名 IPA 打包和产物检查。

## GitHub Actions

- `iOS IPA Build`：在版本门控允许时自动构建未签名 IPA，Android 版本/tag 作为显示和产物命名依据。
- `iOS IPA Validation`：PR 或手动触发的强制构建，不检查版本号是否变化，适合普通代码提交后验证；当前使用 `actions/upload-artifact@v7`，并缓存 Kotlin/Native `~/.konan` 工具链。

## iOS 数据与分享边界

- `easyopen.ios.devices.v1` 保存 iOS 多设备配置，`easyopen.ios.activeIdentifier` 保存当前选择；旧的单设备 key 首次加载时迁移。
- `CBPeripheral.identifier` 只作为 iOS 本地恢复句柄，不进入跨平台二维码/备份载荷。
- shared transfer profile 的 `androidMac` 是可选 Android 本地绑定；Android 新备份写入该字段并兼容旧 `address`。Android 紧凑二维码仍是旧 Android 格式，缺少本地绑定后的跨平台“扫描后绑定”流程尚未实现。

## 版本映射

版本来源始终是 `app/build.gradle.kts`：

```text
Android 1.1.0              -> iOS 1.1.0
Android 1.1.0-beta.1       -> iOS 1.1.0.1
Android 1.1.0-canary.1     -> iOS 1.1.0.0.1
```

`CFBundleVersion` 直接使用 Android `versionCode`。版本转换只作用于 iOS `CFBundleShortVersionString`，不修改 Android 文件、仓库 tag 或 Android Release 显示版本。
