# EasyOpen iOS App

当前目录包含一个最小的 Xcode 宿主工程，用于把 `shared` 的 Compose Multiplatform UI 生成未签名 IPA。宿主已经切换到 shared 的真实 Home/Pairing Compose 页面；shared 已接入 iOS CoreBluetooth 的扫描、连接、服务/特征发现、通知、配对命令和开门命令骨架；iOS 宿主已支持多设备列表、旧单设备 key 迁移，并将开门器密码迁移到 Keychain。NFC、二维码、备份文件、权限细化和真机验收仍按计划迁移。

## 本地/macOS 构建边界

- `shared` 负责生成 `EasyOpenShared.framework`。
- `iosApp/EasyOpen.xcodeproj` 负责生成 iOS App。
- `.github/scripts/build-unsigned-ipa.sh` 通过 `xcodebuild archive CODE_SIGNING_ALLOWED=NO` 归档；随后显式执行 `:shared:syncComposeResourcesForIos`，把 `compose-resources/` 同步到归档 App，再手动打包 `Payload/*.app` 为未签名 `.ipa`。
- iOS 宿主当前显式关闭 Compose 的 `parallelRendering`，让首帧渲染回到主线程；这是针对 iOS 27.0 真机首帧在 Compose 独立渲染线程上触发 `SIGABRT` 的稳定性保护，待真机回归通过后再评估恢复并行渲染。
- iOS 宿主使用 Info.plist 的 `UILaunchScreen` 启动屏配置，不使用固定设备尺寸的 `LaunchScreen.storyboard`；这样不会把 iPhone 预览画布尺寸误认为运行时窗口尺寸。SwiftUI 容器同时使用最大尺寸和 `ignoresSafeArea()`，让 shared Compose 页面占满窗口。
- iOS 宿主现在与 Android 一样使用 shared `EasyOpenNavigator` 管理 Home、添加开门器和设置页面；无已保存设备的首启是不可返回的根页面，有已保存设备时添加开门器才可返回。
- 已保存的 iOS 开门器进入主页后会主动恢复 CoreBluetooth 连接；首次授权蓝牙时，如果首个扫描请求早于授权回调到达，扫描会在状态变为 powered on 后自动恢复。
- iOS 尚未完成二维码、Core NFC 和备份文件适配；这些入口在 iOS 上暂时不渲染，避免出现点击无反应的死按钮。Android 仍保留完整入口，待对应平台适配完成后再打开。iOS 开门器密码已经使用 Keychain 保存，设备元数据仍使用 NSUserDefaults。
- `iosApp/EasyOpen/Info.plist` 必须声明 `CADisableMinimumFrameDurationOnPhone=true`。Compose 1.11.1 的 iOS `PlistSanityCheck` 会在启动时校验该键；缺失时会在后台队列抛出未捕获 Kotlin 异常并以 `SIGABRT` 结束进程。IPA 构建脚本会读取归档后的 plist 并阻止缺失该键的产物继续打包。
- Linux 可以编译 Kotlin/Native KLIB，但不能替代 macOS/Xcode 的链接、资源归档、签名和真机验证。
- 2026-08-31 的 `iOS IPA Validation` run `33417601264` 在 `macos-15` / Xcode 16.4 最终链接阶段失败；2026-09-01 的 `iOS IPA Validation` run `33462150647` 已在 `macos-26` 成功完成 archive、未签名 IPA 打包和产物检查。

## GitHub Actions

- `iOS IPA Build`：在版本门控允许时自动构建未签名 IPA，Android 版本/tag 作为显示和产物命名依据。
- `iOS IPA Validation`：PR 或手动触发的强制构建，不检查版本号是否变化，适合普通代码提交后验证；当前使用 `actions/upload-artifact@v7`，并缓存 Kotlin/Native `~/.konan` 工具链。

## iOS 数据与分享边界

- `easyopen.ios.devices.v1` 保存 iOS 多设备元数据，`easyopen.ios.activeIdentifier` 保存当前选择；旧的单设备 key 首次加载时迁移。开门器密码使用 Keychain generic-password 项保存，成功迁移后不再写入 `easyopen.ios.devices.v1` 的密码字段；若系统拒绝 Keychain 写入，才保留旧字段以避免静默丢失配置。
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
