# EasyOpen iOS App

当前目录包含一个最小的 Xcode 宿主工程，用于把 `shared` 的 Compose Multiplatform UI 生成未签名 IPA。它目前只承载 KMP/Miuix smoke screen，BLE、NFC、权限和完整页面仍按计划迁移。

## 本地/macOS 构建边界

- `shared` 负责生成 `EasyOpenShared.framework`。
- `iosApp/EasyOpen.xcodeproj` 负责生成 iOS App。
- `.github/scripts/build-unsigned-ipa.sh` 通过 `xcodebuild archive CODE_SIGNING_ALLOWED=NO` 归档，再手动打包 `Payload/*.app` 为未签名 `.ipa`。
- Linux 可以编译 Kotlin/Native KLIB，但不能替代 macOS/Xcode 的链接、签名和真机验证。

## GitHub Actions

- `iOS IPA Build`：在版本门控允许时自动构建未签名 IPA，Android 版本/tag 作为显示和产物命名依据。
- `iOS IPA Validation`：PR 或手动触发的强制构建，不检查版本号是否变化，适合普通代码提交后验证。

## 版本映射

版本来源始终是 `app/build.gradle.kts`：

```text
Android 1.1.0              -> iOS 1.1.0
Android 1.1.0-beta.1       -> iOS 1.1.0.1
Android 1.1.0-canary.1     -> iOS 1.1.0.0.1
```

`CFBundleVersion` 直接使用 Android `versionCode`。版本转换只作用于 iOS `CFBundleShortVersionString`，不修改 Android 文件、仓库 tag 或 Android Release 显示版本。
