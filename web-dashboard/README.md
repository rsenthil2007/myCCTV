# myCCTV Remote Dashboard

A single-file web dashboard to control the myCCTV Android app over local WiFi.

## Usage

1. Open the myCCTV app on your Android phone and start the HTTP server (port 41737).
2. Make sure your phone and computer are on the same WiFi network.
3. Open `index.html` in any modern browser (Chrome, Edge, Firefox).
4. Enter your phone's local IP address in the connection bar, e.g. `http://192.168.1.42:41737`.
5. Click **Connect** — the live MJPEG stream will appear and all controls will populate from the camera state.

## Features

- Live MJPEG video stream with snapshot download
- Camera, resolution, and FPS selection
- Quality, zoom, and exposure sliders
- Focus mode switching (Continuous / Auto / Manual)
- Torch, video overlay, and audio toggles
- Start/Stop streaming controls
- Auto-refresh every 3 seconds to stay in sync
- Dark theme, responsive layout (desktop and mobile)

## Requirements

- No build step or dependencies — just a browser
- Phone and computer must be on the same local network
- CORS is handled by the myCCTV server (`Access-Control-Allow-Origin: *`)
