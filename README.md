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
2. Connect the phone to Wi‑Fi. The app shows Wi‑Fi status and LAN IP in the header and Network card.
3. Keep myCCTV open — the HTTP control server starts with the app on port `41737`.
4. On another device on the same Wi‑Fi, open `web-dashboard/index.html`, paste the LAN IP, and Connect.
   Or open `http://<phone-ip>:41737/` in a browser for the built-in dashboard.
5. Press Start live for video. RTSP is available when streaming.

If the remote dashboard says **Failed to fetch**, copy the exact LAN IP from the app, keep myCCTV open, and avoid guest Wi‑Fi / AP isolation. Open the dashboard as a local file, not over HTTPS.

Based on the open-source LensCast project, rebranded and English-only as **myCCTV**.
