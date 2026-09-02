# EasyOpen iOS App

当前目录包含一个最小的 Xcode 宿主工程，用于把 `shared` 的 Compose Multiplatform UI 生成未签名 IPA。宿主使用 UIKit 直接承载 shared 的真实 Home/Pairing Compose 页面；shared 已接入 iOS CoreBluetooth 的扫描、连接、服务/特征发现、通知、配对命令和开门命令骨架；iOS 宿主已支持多设备列表、旧单设备 key 迁移，并将开门器密码迁移到 Keychain。Core NFC 读写、权限细化和真机验收仍按计划迁移；二维码分享/扫描导入源码适配已接入，但仍需 macOS/真机验收；备份文件导入/导出源码适配也仍需 macOS/真机验收。

## 本地/macOS 构建边界

- `shared` 负责生成 `EasyOpenShared.framework`。
- `iosApp/EasyOpen.xcodeproj` 负责生成 iOS App。
- `.github/scripts/build-unsigned-ipa.sh` 通过 `xcodebuild archive CODE_SIGNING_ALLOWED=NO` 归档；随后重新生成同一次 Gradle 构建对应的 `iosArm64` Compose 资源聚合目录，校验 CVR 的 UTF-8 内容与生成 accessor 的字节偏移完全一致，再复制到归档 App 的 `compose-resources/`，打包后还会重新解压实际 IPA 做一次相同校验，最后产出未签名 `.ipa`。
- iOS 宿主使用 UIKit AppDelegate 直接把 `ComposeUIViewController` 设为窗口根控制器，不再经过 SwiftUI `UIViewControllerRepresentable` 的额外生命周期边界；同时显式关闭 Compose 的 `parallelRendering`，让首帧渲染回到主线程。这两项是针对 iOS 27.0 真机首帧/冷启动渲染不稳定的保护，待真机回归通过后再评估恢复并行渲染。
- iOS 宿主使用 Info.plist 的 `UILaunchScreen` 启动屏配置，不使用固定设备尺寸的 `LaunchScreen.storyboard`；这样不会把 iPhone 预览画布尺寸误认为运行时窗口尺寸。UIKit 窗口以屏幕尺寸创建并将 shared Compose 页面设为根控制器。
- iOS App 图标使用与 Android adaptive icon 相同的蓝底白色 EasyOpen 标记：`EasyOpen/Resources/AppIcon.icon` 是 Xcode 26/Icon Composer 的背景层 + 前景层分层图标，`EasyOpen/Resources/Assets.xcassets/AppIcon.appiconset` 提供 iOS 16+ 的静态 1024px 回退资源；Xcode 构建设置同时启用两者，避免旧系统没有分层图标支持时缺少 App Icon。
- iOS 宿主现在与 Android 一样使用 shared `EasyOpenNavigator` 管理 Home、添加开门器和设置页面；无已保存设备的首启是不可返回的根页面，有已保存设备时添加开门器才可返回。
- 已保存的 iOS 开门器进入主页后会按自动连接开关和 RSSI 阈值扫描，匹配到保存的 Peripheral UUID 或 Manufacturer Data 硬件 MAC 后再恢复 CoreBluetooth 连接；关闭自动连接时不会后台扫描。首次授权蓝牙时，如果首个扫描请求早于授权回调到达，扫描会在状态变为 powered on 后自动恢复。
- iOS 共享 UI 的静态文案通过 `EasyOpenStringResource` 使用 iOS 平台兜底表，不依赖受 build 51 影响的 `stringResource()` CVR 读取路径；该表由 `strings.xml` 校验保持同步。
- iOS 主页分享按钮已使用 shared `EasyOpenQrCodec` 生成 `EASYOPEN-SHARE:3:` 加密二维码；二维码生成使用 CoreImage，扫描导入使用 AVFoundation 视频帧 + Vision 条码识别，导入后进入 shared 配对页并优先按 `hardwareMac` 自动匹配，匹配不到时保留手动选择。Android 仍兼容旧 `EASYOPEN-SHARE:1/2` 载荷。Core NFC 写入入口已接入前台 NDEF reader session，并写入与 Android 相同的 EasyOpen MIME 内容；同时提供前台读取 NFC 并开门入口。iOS 不承诺 Android force-stop 式后台 NFC 分发。iOS 备份导出已经接入系统分享面板，备份导入已经接入系统文件选择器；恢复的配置会进入逐台重新配对流程，不会伪造或迁移 iOS Peripheral UUID。iOS 开门器密码已经使用 Keychain 保存，设备元数据仍使用 NSUserDefaults。
- iOS Home 已接入稳定版更新检查：使用归档中的 `CFBundleVersion`（与 Android `versionCode` 一致）匹配 GitHub Release 的 Android APK 资产；发现更新时点击提示会打开 Release 页面。网络失败不会阻断启动或 BLE。
- `iosApp/EasyOpen/Info.plist` 必须声明 `CADisableMinimumFrameDurationOnPhone=true`。Compose 1.12.0 的 iOS `PlistSanityCheck` 会在启动时校验该键；缺失时会在后台队列抛出未捕获 Kotlin 异常并以 `SIGABRT` 结束进程。IPA 构建脚本会读取归档后的 plist 并阻止缺失该键的产物继续打包。
- Linux 可以编译 Kotlin/Native KLIB，但不能替代 macOS/Xcode 的链接、资源归档、签名和真机验证。
- 2026-08-31 的 `iOS IPA Validation` run `33417601264` 在 `macos-15` / Xcode 16.4 最终链接阶段失败；2026-09-01 的 `iOS IPA Validation` run `33462150647` 已在 `macos-26` 成功完成 archive、未签名 IPA 打包和产物检查。

## GitHub Actions

- `iOS IPA Build`：在版本门控允许时自动构建未签名 IPA，Android 版本/tag 作为显示和产物命名依据。
- `iOS IPA Validation`：PR 或手动触发的强制构建，不检查版本号是否变化，适合普通代码提交后验证；当前使用 `actions/upload-artifact@v7`，并缓存 Kotlin/Native `~/.konan` 工具链。

## iOS 数据与分享边界

- `easyopen.ios.devices.v1` 保存 iOS 多设备元数据，`easyopen.ios.activeIdentifier` 保存当前选择；旧的单设备 key 首次加载时迁移。开门器密码使用 Keychain generic-password 项保存，成功迁移后不再写入 `easyopen.ios.devices.v1` 的密码字段；若系统拒绝 Keychain 写入，才保留旧字段以避免静默丢失配置。
- `CBPeripheral.identifier` 只作为 iOS 本地恢复句柄，不进入跨平台二维码/备份载荷；备份可携带开门器 Manufacturer Data 镜像的 `androidMac` 作为跨平台自动匹配键。
- shared transfer profile 的 `androidMac` 是可选硬件匹配字段；它来自开门器 Manufacturer Data 镜像的 MAC。Android 新备份写入该字段并兼容旧 `address`；iOS 扫描广播后优先自动匹配，匹配不到时保留手动选择。

## 版本映射

版本来源始终是 `app/build.gradle.kts`：

```text
Android 1.1.0              -> iOS 1.1.0
Android 1.1.0-beta.1       -> iOS 1.1.0.1
Android 1.1.0-canary.1     -> iOS 1.1.0.0.1
```

`CFBundleVersion` 直接使用 Android `versionCode`。版本转换只作用于 iOS `CFBundleShortVersionString`，不修改 Android 文件、仓库 tag 或 Android Release 显示版本。
