# myCCTV

Turn an Android phone into a local-network IP camera with multi-lens support (wide, main, telephoto, front).

## Features

- Multi-camera access via Camera2 (wide, main, tele, front, and other physical lenses)
- Web dashboard on your LAN for live controls
- MJPEG preview for browsers
- RTSP stream for VLC, NVR, Home Assistant, OBS
- Optional AAC microphone audio
- Snapshot capture
- Remote controls: resolution, FPS, quality, zoom, focus, exposure, torch
- Video overlay: date, time, battery
- Landscape streaming and blackout power-saving mode

## Security

**LAN only.** Streams use cleartext HTTP/RTSP with **no password**. Fine for home Wi‑Fi testing; do not expose to the internet.

## Build

Requirements: JDK 17, Android SDK 35, Gradle 8.9.

```bat
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot
gradlew.bat :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` and `myCCTV-debug-latest.apk` at project root.

## Usage

1. Install the APK and grant camera (+ microphone if needed).
2. Connect phone and viewer to the same Wi‑Fi.
3. Start streaming in the app; note the IP and port shown.
4. Open `http://<phone-ip>:<port>/` in a browser, or use RTSP/MJPEG URLs from the app.

Based on the open-source LensCast project, rebranded and English-only as **myCCTV**.
