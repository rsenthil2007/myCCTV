# 全镜直播(LensCast)

<p align="center">
  <img src="docs/images/app-icon.png" alt="LensCast icon" width="128" height="128">
</p>

<p align="center">
  <strong>📷 把 Android 手机变成可调用广角 / 主摄 / 长焦等所有可用摄像头的本地网络摄像机。</strong>
</p>

<p align="center">
  <a href="#中文">中文</a> ·
  <a href="#视频流支持">视频流支持</a> ·
  <a href="#使用说明">使用说明</a> ·
  <a href="#english">English</a> ·
  <a href="https://github.com/AlexTOOT/LensCast/releases/tag/v0.5.31-100">下载 APK</a>
</p>

## 中文

全镜直播(LensCast) 是一个 Android 本地网络摄像机应用。它最大的特点不是单纯把手机变成 IP Camera，而是尽可能调用手机通过 Camera2 暴露出来的所有可用摄像头，包括广角、主摄、长焦 / 潜望长焦，以及部分普通相机选择器不会显示的物理摄像头。

> ⚠️ 实际可用镜头取决于手机厂商开放程度。全镜直播(LensCast) 会提供扫描功能，帮助确认哪些镜头可以被第三方应用稳定打开和推流。

## ✨ 核心能力

- 📷 **多摄像头调用**：广角、主摄、长焦、前摄，以及 Camera2 暴露的其他物理摄像头
- 🌐 **网页控制台**：在同一局域网内通过浏览器远程调整参数
- 🎥 **MJPEG 实时预览**：适合浏览器、网页嵌入和部分 NVR
- 📡 **RTSP 视频流**：适合 Home Assistant、VLC、NVR 等客户端
- 🔊 **AAC 音频流**：可选开启麦克风音频
- 🖼️ **Snapshot 截图**：保存点击时刻的当前帧
- ⚙️ **远程参数控制**：分辨率、帧率、质量、变焦、对焦、曝光、补光
- 🧭 **视频叠加层**：日期、时间、电量、充电状态，可调节尺寸
- 🌙 **横屏直播与黑屏省电模式**：适合长时间固定机位运行

## 视频流支持

| 类型 | 地址 | 用途 | 状态 |
| --- | --- | --- | --- |
| Web 控制台 | `http://<phone-ip>:41737/` | 浏览器控制与预览 | ✅ 支持 |
| MJPEG | `http://<phone-ip>:41737/video.mjpeg` | 浏览器 / 网页 / 部分 NVR | ✅ 支持 |
| Snapshot | `http://<phone-ip>:41737/snapshot.jpg` | 保存当前帧 JPEG | ✅ 支持 |
| AAC Audio | `http://<phone-ip>:41737/audio.aac` | 独立音频流 | ✅ 支持 |
| RTSP | `rtsp://<phone-ip>:8554/live` | H.264 视频流客户端 | ✅ 支持 |
| WebRTC / ONVIF | - | 暂未实现 | ⏳ 未支持 |

## 界面预览

### Web 控制台

![LensCast Web UI Chinese](docs/images/webui_CN.jpg)

### 摄像头扫描

![LensCast lens scan Chinese](docs/images/lenscan_CN.jpg)

## 使用说明

1. 在 Android 手机上安装 release 中的 APK。
2. 授予相机权限；如果需要音频，也授予麦克风权限。
3. 打开“全镜直播(LensCast)”，点击“扫描摄像头”。
4. 扫描完成后，在已验证摄像头列表中选择需要使用的镜头。
5. 选择预设，或手动调整分辨率、帧率和推流质量。
6. 点击“开始直播”。
7. 在同一局域网内，用电脑、平板或其他设备打开 App 中显示的 Web 地址。

## 摄像头扫描说明

Android 厂商对多摄像头的开放程度差异很大。全镜直播(LensCast) 会扫描 Camera2 暴露出的摄像头 ID，并尝试识别可以实际打开、可以稳定出图的镜头。

建议首次使用时先执行一次扫描。扫描结果会缓存在本机，之后启动时可以直接加载。更换手机、系统更新、清除应用数据后，建议重新扫描。

如果某个镜头在扫描结果中出现，但实际直播失败，通常说明该镜头虽然被系统暴露，但厂商仍限制了第三方应用的实际使用能力。

## 构建

需要：

- Android Studio 或兼容 Android Gradle Plugin 的构建环境
- JDK 17
- Android SDK 34
- Gradle 8.7 或兼容版本

当前源码快照不包含本地 SDK 文件，也不包含 Gradle Wrapper。可以使用本机 Gradle 构建：

```powershell
gradle assembleDebug
```

当前 Android 包名：

```text
com.opencode.multilensipcam
```

## 注意事项

全镜直播(LensCast) 面向本地局域网使用。不要在没有额外认证、反向代理或网络隔离的情况下，把 Web 控制台或视频流端点直接暴露到公网。

<details id="english">
<summary>English</summary>

## English

LensCast is a local Android network camera app focused on accessing every usable camera exposed by Camera2, including ultra-wide, main, telephoto / periscope, front-facing, and other physical camera IDs that typical IP camera apps often hide.

> ⚠️ Actual lens availability depends on vendor restrictions. LensCast includes a scan flow to verify which cameras can be opened and streamed reliably by third-party apps.

## ✨ Highlights

- 📷 **Multi-lens access**: ultra-wide, main, telephoto, front camera, and other Camera2-exposed physical cameras
- 🌐 **Web dashboard**: control the phone from another device on the same LAN
- 🎥 **MJPEG live preview**: useful for browsers, web embeds, and some NVRs
- 📡 **RTSP video stream**: works with clients such as Home Assistant, VLC, and NVR software
- 🔊 **AAC audio stream**: optional microphone audio
- 🖼️ **Snapshot endpoint**: saves the frame captured at click time
- ⚙️ **Remote controls**: resolution, frame rate, quality, zoom, focus, exposure, and flashlight
- 🧭 **Video overlay**: date, time, battery, and charging state with adjustable size
- 🌙 **Landscape live mode and black-screen power saving**: designed for long-running fixed-camera use

## Stream Support

| Type | URL | Use case | Status |
| --- | --- | --- | --- |
| Web dashboard | `http://<phone-ip>:41737/` | Browser control and preview | ✅ Supported |
| MJPEG | `http://<phone-ip>:41737/video.mjpeg` | Browser / web / some NVRs | ✅ Supported |
| Snapshot | `http://<phone-ip>:41737/snapshot.jpg` | Save current JPEG frame | ✅ Supported |
| AAC Audio | `http://<phone-ip>:41737/audio.aac` | Standalone audio stream | ✅ Supported |
| RTSP | `rtsp://<phone-ip>:8554/live` | H.264 video clients | ✅ Supported |
| WebRTC / ONVIF | - | Not implemented yet | ⏳ Not supported |

## Screenshots

### Web Dashboard

![LensCast Web UI English](docs/images/webui_EN.jpg)

### Lens Scan

![LensCast lens scan English](docs/images/lenscan_EN.jpg)

## Usage

1. Install the APK from the release page on an Android phone.
2. Grant camera permission. Grant microphone permission if audio is needed.
3. Open LensCast and tap Scan cameras.
4. Select a lens from the verified camera list.
5. Choose a preset or adjust resolution, frame rate, and stream quality manually.
6. Tap Start live.
7. Open the Web dashboard URL shown in the app from another device on the same LAN.

## Camera Scanning

Android vendors expose multi-camera hardware differently. LensCast scans Camera2 camera IDs and tries to identify lenses that can actually be opened and streamed reliably.

Run a scan the first time you use the app. The verified result is cached locally and loaded on later launches. Re-scan after switching phones, updating the system, or clearing app data.

If a lens appears during scanning but fails during live streaming, the phone may expose the camera ID while still restricting third-party access in practice.

## Build

Requirements:

- Android Studio or Android Gradle Plugin compatible toolchain
- JDK 17
- Android SDK 34
- Gradle 8.7 or compatible

This source snapshot does not include local SDK files or a Gradle wrapper. Build with your local Gradle installation:

```powershell
gradle assembleDebug
```

Current Android package name:

```text
com.opencode.multilensipcam
```

## Notes

LensCast is intended for local network use. Do not expose the web dashboard or stream endpoints directly to the public internet without adding your own authentication, reverse proxy, or network isolation.

</details>
