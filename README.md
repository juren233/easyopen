<p align="center">
  <img src="web/icons/icon-512.png" alt="EasyOpen" width="160" />
</p>

<h1 align="center">EasyOpen</h1>

<p align="center">
  <strong>面向 YILA 开门器的本地蓝牙开门工具</strong>
</p>

<p align="center">
  <a href="https://creativecommons.org/licenses/by-nc/4.0/"><img src="https://img.shields.io/badge/License-CC%20BY--NC%204.0-blue.svg" alt="License CC BY-NC 4.0" /></a>
  <a href="https://developer.android.com/"><img src="https://img.shields.io/badge/Android-13.0%2B-3DDC84.svg" alt="Android 13.0+" /></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.3.21-7F52FF.svg" alt="Kotlin 2.3.21" /></a>
  <a href="https://developer.mozilla.org/docs/Web/API/Web_Bluetooth_API"><img src="https://img.shields.io/badge/Web-Bluetooth-0F172A.svg" alt="Web Bluetooth" /></a>
</p>

***

## 项目简介

EasyOpen 是一个面向 YILA 开门器的本地控制工具，提供 **Android 原生应用**、**iOS（Compose Multiplatform + Miuix）应用** 和 **Web Bluetooth 网页版**（不再维护）三种形态。

项目只实现本地 BLE 开门主链路，不依赖登录、云端、MQTT、网关或远程开门服务。

## 主要功能

- 搜索附近的 YILA 开门器，并通过 6 位数字密码完成配对初始化。
- 本地 BLE 连接、状态展示、重试与一键开门。
- 配置开门方向，以及开启、保持、关闭时长。
- 保存多台开门器配置，并在主页快速切换。
- 读取开门器广播中的电量信息（设备支持时）。
- 使用写入指定 NDEF MIME 内容的 NFC 标签碰一碰开门；不绑定标签 UID。
- 可选的自动连接、进入 App 自动开门、二维码分享和备份恢复。
- 提供无需安装 APK 的 Web Bluetooth 版本，以及可通过 ADB 隧道在手机本地使用的脚本。

## Android 版

### 环境要求

- Android 13.0（API 33）及以上。
- 首次使用需要蓝牙扫描、蓝牙连接权限；部分系统可能同时要求定位权限。
- NFC 开门需要设备支持并开启 NFC；标签必须先写入 EasyOpen 的 NDEF MIME 内容。
- 扫描二维码需要相机权限。

### 使用流程

1. 打开手机蓝牙，并启动 EasyOpen。
2. 按提示授予蓝牙权限；需要时同时授予定位权限。
3. 搜索附近的 YILA 开门器，输入设备的 6 位数字密码并完成配对。
4. 根据实际设备配置方向、开启时长、保持时长和关闭时长。
5. 回到主页，确认设备状态后点击“一键开门”。
6. 如需 NFC：展开主页“开门器设置”，点击“写入NFC标签”，再将手机靠近空白标签。应用会写入一条 EasyOpen 自有 NDEF MIME 记录：类型为 `application/com.juren233.easyopen.unlock`，内容为 `unlock_current=1`。如果标签已有 NDEF 内容，应用会先让你选择保留还是覆盖；选择保留时，原有记录会保留，EasyOpen 记录会放在第一条以确保 Android 能正确唤起 EasyOpen。
7. 也可以使用其他 NFC 写入工具手动写入同样的 EasyOpen MIME 类型。写入成功后，即使没有手动打开 EasyOpen，直接将手机碰到该标签，Android 会自动唤起 EasyOpen 的 NFC 入口并执行开门；标签不需要在设置中绑定 UID。NDEF 内容可以被复制，因此它不是防复制的安全凭证。

### 构建与测试

在项目根目录执行：

```bash
# 运行 JVM 单元测试
./gradlew --no-daemon --max-workers=2 :app:testDebugUnitTest

# 构建使用正式签名的 Debug APK
./gradlew --no-daemon --max-workers=2 :app:assembleDebug

# 构建使用同一正式签名的 Release APK
./gradlew --no-daemon --max-workers=2 :app:assembleRelease
```

生成的 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

Debug 和 Release 默认使用项目的同一份正式签名。签名私钥只保存在本地的 `signing/easyopen-release.jks`，不应提交到仓库；首次克隆项目时，请按 [`keystore.properties.example`](keystore.properties.example) 创建本地 `keystore.properties`。

## Web Bluetooth 版（不再维护）

网页版本位于 [`web/`](web/)，不需要安装 APK。浏览器必须支持 Web Bluetooth；正式部署建议使用 HTTPS，本地开发可使用 `localhost`。

### 本机启动

```bash
cd web
python3 -m http.server 8765
```

然后在支持 Web Bluetooth 的浏览器中打开：

```text
http://localhost:8765/
```

也可以使用单文件版本 [`web/easyopen.html`](web/easyopen.html)。完整的浏览器兼容性、协议和安全边界说明见 [`web/README.md`](web/README.md)。

### 通过 ADB 在手机本地使用

如果不想部署到公网，可以让电脑启动本地静态服务，再通过 ADB 反向隧道让手机访问：

```bash
./web/serve-phone.sh
```

确认 `adb devices` 中的设备状态为 `device` 后，在手机 Chrome 打开 `http://localhost:8765/easyopen.html`。网页中的蓝牙操作仍由手机浏览器和手机蓝牙适配器完成。

## 数据与安全提示

- Android 端会在本地保存已配对设备配置，其中包括开门器密码；请妥善保护手机和备份文件。
- 分享二维码和备份文件包含开门器密码，只应发送给可信的人。
- Web 版不会将密码写入 `localStorage`，刷新页面后需要重新输入。
- Web 版和 Android 版都只执行本地 BLE 操作，不提供远程开门能力。
- 实际设备控制具有物理风险。首次配对或修改动作参数后，请在确认设备周围安全的情况下测试。

## 项目结构

```text
app/                         Android 应用源码
web/                         Web Bluetooth 版本
extracted/                   原始 APK 的逆向分析与协议重建记录
signing/easyopen-release-cert.sha256  正式签名证书公开指纹
ARCHITECTURE.md              Android 端代码组织说明
```

`extracted/` 中的原始 APK、反编译文件和逆向分析材料用于协议研究，不应默认视为本项目原创内容，也不自动适用本项目的 CC BY-NC 4.0 许可。重新分发这些材料前，请确认你拥有相应权利。

## 许可证与第三方组件

除特别注明外，EasyOpen 的原创 Android/Web 源代码、文档和原创资源采用 [CC BY-NC 4.0 International](https://creativecommons.org/licenses/by-nc/4.0/) 许可，详见 [`LICENSE`](LICENSE)。

> 注意：CC BY-NC 4.0 包含“非商业使用”限制。它允许非商业分享和改编，但不属于 OSI 定义的传统软件开源许可证。若要将 EasyOpen 的原创部分用于商业产品或商业分发，请先取得额外授权。

本项目当前直接使用的主要第三方组件如下。第三方组件不受本项目 CC BY-NC 4.0 声明覆盖，仍按其各自许可证执行：

| 组件                                    | 用途                  | 许可证        | 上游许可证/项目                                                                                                        |
| :------------------------------------ | :------------------ | :--------- | :-------------------------------------------------------------------------------------------------------------- |
| AndroidX、Jetpack Compose、Navigation 3 | Android 基础能力、UI 与导航 | Apache-2.0 | [androidx/androidx](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt)                        |
| Kotlin Standard Library               | Kotlin 运行时          | Apache-2.0 | [JetBrains/kotlin](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)                         |
| kotlinx.serialization                 | 分享/备份数据序列化          | Apache-2.0 | [Kotlin/kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt)         |
| Miuix                                 | Android 界面组件与主题     | Apache-2.0 | [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix/blob/main/LICENSE)                           |
| ZXing Core                            | 二维码编码/解码核心          | Apache-2.0 | [zxing/zxing](https://github.com/zxing/zxing/blob/master/LICENSE)                                               |
| ZXing Android Embedded                | Android 相机扫码集成      | Apache-2.0 | [journeyapps/zxing-android-embedded](https://github.com/journeyapps/zxing-android-embedded/blob/master/COPYING) |

