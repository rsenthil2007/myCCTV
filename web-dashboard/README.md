# myCCTV Remote Dashboard

A single-file web dashboard to control one or more myCCTV phones over local Wi-Fi.

## Usage

1. Install/open myCCTV on each phone and grant camera permission.
2. Connect each phone to Wi-Fi. Copy the LAN IP from the app header / Network card.
3. Keep myCCTV open.
4. On a computer on the **same Wi-Fi**, open `index.html` as a local file (not from an `https://` site).
5. Enter a name and IP, then **Add camera**. Repeat for more phones.
6. Use **Start** / **Stop**, **Photo**, and **Screen off/on** on each card.

Live video reconnects automatically after Stop then Start. You do not need to Disconnect.

## If Connect says "Failed to fetch"

- Use the IP shown in the app, not a guessed address.
- Phone and computer must be on the same LAN. Guest Wi-Fi and AP/client isolation block phone-to-PC traffic.
- Keep the myCCTV activity in the foreground.
- Open this dashboard from a file (`file://.../index.html`).

## Features

- Multiple phones in parallel
- Live MJPEG per camera, with automatic reconnect after Start
- Remote JPEG photo download
- Screen off (blackout overlay) and screen on
- Camera, resolution, FPS, quality, zoom, torch, overlay, audio

LAN only. Streams have no password.
