package com.opencode.multilensipcam

object WebDashboardPage {
    fun render(versionLabel: String): String {
        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>全镜直播(LensCast)</title>
              <style>
                :root {
                  color-scheme: dark;
                  --bg-base: #070a0e;
                  --bg-elevated: #10161d;
                  --bg-surface: #151d24;
                  --bg-soft: #1b2830;
                  --stroke: #35505b;
                  --accent: #52e0c4;
                  --success: #a8f06a;
                  --warning: #ffca6a;
                  --danger: #ff7a7a;
                  --text: #f8fbf8;
                  --text-2: #c0d2d2;
                  --muted: #8ea0a2;
                }
                body.skin-sunrise {
                  --bg-base: #0c0a07;
                  --bg-elevated: #19140e;
                  --bg-surface: #211910;
                  --bg-soft: #30251a;
                  --stroke: #6a4f34;
                  --accent: #ffca6a;
                  --success: #ff8f5a;
                  --warning: #f6e6a7;
                  --danger: #ff7f6f;
                  --text: #fff8ec;
                  --text-2: #e7cfa9;
                  --muted: #b89d78;
                }
                body.skin-mineral {
                  --bg-base: #06090d;
                  --bg-elevated: #0e151d;
                  --bg-surface: #131e29;
                  --bg-soft: #1b2a35;
                  --stroke: #3c5365;
                  --accent: #8fc7ff;
                  --success: #b3e7d0;
                  --warning: #ffd37d;
                  --danger: #ff8c8c;
                  --text: #f7fbff;
                  --text-2: #bfd4e6;
                  --muted: #8fa7b8;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  min-height: 100vh;
                  font-family: "Segoe UI", "PingFang SC", sans-serif;
                  background: linear-gradient(135deg, var(--bg-elevated), var(--bg-base) 68%);
                  color: var(--text);
                }
                .shell {
                  width: min(1180px, calc(100% - 32px));
                  margin: 0 auto;
                  padding: 20px 0 28px;
                }
                .topbar {
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                  gap: 14px;
                  margin-bottom: 16px;
                }
                .top-actions {
                  display: flex;
                  align-items: center;
                  gap: 10px;
                  flex-wrap: wrap;
                  justify-content: flex-end;
                }
                .theme-switcher,
                .language-switcher {
                  display: flex;
                  gap: 8px;
                  padding: 6px;
                  border: 1px solid rgba(192, 210, 210, 0.16);
                  border-radius: 10px;
                  background: rgba(16, 22, 29, 0.72);
                }
                .theme-switcher button {
                  width: 30px;
                  height: 30px;
                  border: 1px solid var(--stroke);
                  border-radius: 999px;
                  cursor: pointer;
                }
                .language-switcher button {
                  min-width: 48px;
                  height: 30px;
                  border: 1px solid var(--stroke);
                  border-radius: 8px;
                  background: transparent;
                  color: var(--text-2);
                  cursor: pointer;
                  font: 700 12px/1 "Segoe UI", sans-serif;
                }
                .language-switcher button.active {
                  border-color: transparent;
                  background: linear-gradient(90deg, var(--accent), var(--success));
                  color: var(--bg-base);
                }
                .theme-switcher .green { background: linear-gradient(135deg, #52e0c4, #a8f06a); }
                .theme-switcher .sunrise { background: linear-gradient(135deg, #ffca6a, #ff8f5a); }
                .theme-switcher .mineral { background: linear-gradient(135deg, #8fc7ff, #b3e7d0); }
                h1, h2, h3, p { margin: 0; }
                h1 {
                  font-size: 22px;
                  line-height: 1.15;
                  letter-spacing: 0;
                }
                .hero {
                  display: grid;
                  grid-template-columns: minmax(0, 1.16fr) minmax(320px, 0.84fr);
                  gap: 16px;
                  align-items: start;
                }
                .preview-card {
                  align-self: start;
                }
                .card {
                  background: rgba(21, 29, 36, 0.94);
                  border: 1px solid var(--stroke);
                  border-radius: 12px;
                  padding: 14px;
                  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.34);
                }
                .preview-frame {
                  margin-top: 14px;
                  border-radius: 10px;
                  overflow: hidden;
                  background: #000;
                  border: 1px solid var(--stroke);
                  aspect-ratio: 16 / 9;
                }
                .preview-frame img {
                  width: 100%;
                  height: 100%;
                  object-fit: contain;
                  display: block;
                  background: #000;
                }
                h2 { margin: 0 0 8px; font-size: 15px; }
                p { color: var(--muted); }
                .row {
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                  gap: 12px;
                }
                .badge {
                  display: inline-flex;
                  align-items: center;
                  gap: 8px;
                  padding: 8px 12px;
                  border-radius: 999px;
                  border: 1px solid rgba(168, 240, 106, 0.38);
                  background: rgba(168, 240, 106, 0.12);
                  color: var(--success);
                  font-size: 12px;
                  font-weight: 700;
                }
                .badge.idle {
                  border-color: rgba(255, 202, 106, 0.42);
                  background: rgba(255, 202, 106, 0.1);
                  color: var(--warning);
                }
                .badge.error {
                  border-color: rgba(255, 122, 122, 0.44);
                  background: rgba(255, 122, 122, 0.1);
                  color: var(--danger);
                }
                .group {
                  margin-top: 10px;
                  padding: 12px;
                  border-radius: 10px;
                  background: var(--bg-surface);
                  border: 1px solid rgba(192, 210, 210, 0.16);
                }
                .preview-tuning-details summary {
                  min-height: 34px;
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  color: var(--text);
                  cursor: pointer;
                  font-weight: 800;
                  list-style-position: inside;
                }
                .preview-tuning-details summary::after {
                  content: "Show";
                  color: var(--muted);
                  font-size: 12px;
                  font-weight: 700;
                }
                .preview-tuning-details[open] summary {
                  margin-bottom: 10px;
                }
                .preview-tuning-details[open] summary::after {
                  content: "Hide";
                }
                .camera-debug-list {
                  display: grid;
                  grid-template-columns: 1fr;
                  gap: 8px;
                  margin-top: 10px;
                }
                .camera-debug-item {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 8px;
                  padding: 8px 10px;
                  border: 1px solid rgba(192, 210, 210, 0.16);
                  border-radius: 8px;
                  background: var(--bg-soft);
                  color: var(--text-2);
                  font-size: 12px;
                  line-height: 1.35;
                }
                .camera-debug-item .button {
                  min-height: 30px;
                  padding: 0 8px;
                  font-size: 11px;
                }
                label {
                  display: block;
                  font-size: 14px;
                  margin-bottom: 6px;
                  color: var(--text-2);
                  font-weight: 700;
                }
                select, input[type="number"], input[type="range"] {
                  width: 100%;
                }
                select, input[type="number"] {
                  min-height: 40px;
                  border-radius: 8px;
                  border: 1px solid var(--stroke);
                  background: var(--bg-soft);
                  color: var(--text);
                  padding: 0 10px;
                }
                input[type="range"] {
                  accent-color: var(--accent);
                }
                .value {
                  color: var(--warning);
                  font-size: 13px;
                  font-weight: 700;
                }
                .actions {
                  display: grid;
                  grid-template-columns: repeat(auto-fit, minmax(118px, 1fr));
                  gap: 8px;
                  margin-top: 12px;
                }
                a.button, button.button {
                  min-height: 40px;
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  padding: 0 12px;
                  border: 1px solid var(--stroke);
                  border-radius: 8px;
                  background: var(--bg-soft);
                  color: var(--text);
                  font: 700 13px/1 "Segoe UI", sans-serif;
                  text-decoration: none;
                  cursor: pointer;
                  white-space: nowrap;
                }
                .button.primary {
                  border-color: transparent;
                  background: linear-gradient(90deg, var(--accent), var(--success));
                  color: var(--bg-base);
                }
                .button.danger {
                  border-color: rgba(255, 122, 122, 0.54);
                  background: rgba(255, 122, 122, 0.08);
                  color: var(--danger);
                }
                .button.active {
                  border-color: transparent;
                  background: linear-gradient(90deg, var(--accent), var(--success));
                  color: var(--bg-base);
                }
                .preset-actions {
                  display: grid;
                  grid-template-columns: 1fr;
                  gap: 8px;
                  margin-top: 10px;
                }
                .preset-actions .button {
                  min-height: 34px;
                  justify-content: flex-start;
                  padding: 0 10px;
                  font-size: 12px;
                }
                .top-presets {
                  display: grid;
                  grid-template-columns: repeat(3, minmax(0, 1fr));
                  gap: 8px;
                }
                .top-presets .button {
                  min-height: 44px;
                  justify-content: center;
                }
                .camera-chip-grid {
                  display: grid;
                  grid-template-columns: repeat(auto-fit, minmax(128px, 1fr));
                  gap: 8px;
                }
                .camera-chip-grid .button {
                  min-height: 42px;
                  justify-content: flex-start;
                  overflow: hidden;
                  text-overflow: ellipsis;
                }
                .camera-chip-grid .button.active {
                  border-color: transparent;
                  background: linear-gradient(90deg, var(--accent), var(--success));
                  color: var(--bg-base);
                }
                .hidden-control {
                  position: absolute;
                  width: 1px;
                  height: 1px;
                  opacity: 0;
                  pointer-events: none;
                }
                .button:disabled {
                  opacity: 0.46;
                  cursor: default;
                }
                .subtle {
                  margin-top: 12px;
                  font-size: 13px;
                }
                .inline-toggle {
                  display: inline-flex;
                  align-items: center;
                  gap: 8px;
                  color: var(--text-2);
                  font-size: 13px;
                  font-weight: 700;
                }
                .inline-toggle input {
                  width: auto;
                  accent-color: var(--accent);
                }
                .audio-strip {
                  display: grid;
                  grid-template-columns: minmax(128px, auto) 1fr;
                  align-items: center;
                  gap: 8px 12px;
                  margin-top: 10px;
                  padding-top: 10px;
                  border-top: 1px solid rgba(192, 210, 210, 0.16);
                }
                .audio-status {
                  color: var(--muted);
                  font-size: 12px;
                  line-height: 1.35;
                }
                .audio-status.error {
                  color: var(--danger);
                }
                .audio-player {
                  grid-column: 1 / -1;
                  width: 100%;
                  height: 34px;
                  margin-top: 2px;
                }
                .config-grid {
                  display: grid;
                  grid-template-columns: 1fr;
                  gap: 10px;
                  margin-top: 10px;
                }
                .config-grid .group {
                  margin-top: 0;
                }
                .config-grid .span-all {
                  grid-column: 1 / -1;
                }
                .video-fields {
                  display: grid;
                  grid-template-columns: repeat(2, minmax(0, 1fr));
                  gap: 10px;
                }
                .metric-line {
                  margin-top: 8px;
                  display: flex;
                  justify-content: flex-start;
                  align-items: center;
                  gap: 8px;
                }
                .segmented {
                  display: inline-grid;
                  grid-template-columns: repeat(2, minmax(0, 1fr));
                  gap: 8px;
                  width: 100%;
                }
                .segmented .seg-btn {
                  min-height: 40px;
                  border-radius: 10px;
                  border: 1px solid var(--stroke);
                  background: transparent;
                  color: var(--text-2);
                  font: 700 14px/1 "Segoe UI", sans-serif;
                  cursor: pointer;
                }
                .segmented .seg-btn.active {
                  border-color: transparent;
                  background: linear-gradient(90deg, var(--accent), var(--success));
                  color: var(--bg-base);
                }
                .manual-focus-group {
                  margin-top: 10px;
                }
                .row-tight {
                  align-items: center;
                }
                .row-tight h2 {
                  margin-bottom: 0;
                }
                .switch {
                  position: relative;
                  display: inline-flex;
                  width: 48px;
                  height: 28px;
                }
                .switch input {
                  opacity: 0;
                  width: 0;
                  height: 0;
                }
                .switch-track {
                  position: absolute;
                  inset: 0;
                  border-radius: 999px;
                  border: 1px solid var(--stroke);
                  background: var(--bg-soft);
                  transition: 0.2s ease;
                }
                .switch-track::after {
                  content: "";
                  position: absolute;
                  top: 3px;
                  left: 3px;
                  width: 20px;
                  height: 20px;
                  border-radius: 50%;
                  background: var(--text-2);
                  transition: 0.2s ease;
                }
                .switch input:checked + .switch-track {
                  border-color: transparent;
                  background: linear-gradient(90deg, var(--accent), var(--success));
                }
                .switch input:checked + .switch-track::after {
                  transform: translateX(20px);
                  background: var(--bg-base);
                }
                .switch input:disabled + .switch-track {
                  opacity: 0.45;
                }
                @media (min-width: 901px) {
                  .preview-card {
                    position: sticky;
                    top: 20px;
                  }
                }
                @media (max-width: 900px) {
                  .hero {
                    grid-template-columns: 1fr;
                  }
                  .preview-card {
                    position: static;
                  }
                  .actions {
                    grid-template-columns: repeat(2, minmax(0, 1fr));
                  }
                }
                @media (max-width: 700px) {
                  .config-grid { grid-template-columns: 1fr; }
                  .video-fields { grid-template-columns: 1fr; }
                  .top-presets { grid-template-columns: 1fr; }
                }
                @media (max-width: 420px) {
                  .topbar { display: block; }
                  .top-actions { justify-content: flex-start; margin-top: 12px; }
                  .theme-switcher { width: max-content; }
                  .actions { grid-template-columns: 1fr; }
                  .config-grid { grid-template-columns: 1fr; }
                  .row { display: block; }
                  .row > * + * { margin-top: 8px; }
                }
              </style>
            </head>
            <body>
              <div class="shell">
                <div class="topbar">
                  <div>
                    <h1>全镜直播(LensCast)</h1>
                    <p id="versionText">${versionLabel}</p>
                  </div>
                  <div class="top-actions">
                    <nav class="language-switcher" aria-label="Language">
                      <button type="button" data-lang="en">EN</button>
                      <button type="button" data-lang="zh">中文</button>
                    </nav>
                    <nav class="theme-switcher" aria-label="Theme preview">
                      <button class="green" type="button" data-skin="" title="Green"></button>
                      <button class="sunrise" type="button" data-skin="skin-sunrise" title="Orange"></button>
                      <button class="mineral" type="button" data-skin="skin-mineral" title="Blue"></button>
                    </nav>
                  </div>
                </div>
                <div class="hero">
                  <section class="card preview-card">
                    <div class="row">
                      <div>
                        <h2 data-i18n="livePreview">Live preview</h2>
                      </div>
                      <span id="streamBadge" class="badge idle" data-i18n="checkingStream">Checking stream...</span>
                    </div>
                    <div class="preview-frame">
                      <img id="preview" alt="Live preview">
                    </div>
                    <div class="actions">
                      <a class="button primary" href="/mjpeg" target="_blank" rel="noreferrer" data-i18n="openMjpeg">Open MJPEG</a>
                      <button id="copyH264Button" class="button" type="button" data-i18n="copyH264">Copy RTSP URL</button>
                      <button id="copyAudioButton" class="button" type="button" data-i18n="copyAudio">Copy audio URL</button>
                      <button id="audioOpenButton" class="button" type="button" data-i18n="openAudio">Open audio AAC</button>
                      <a class="button" href="/snapshot.jpg" target="_blank" rel="noreferrer" data-i18n="snapshot">Snapshot</a>
                      <button id="streamingToggleButton" class="button primary" type="button" disabled data-i18n="startStreaming">Start streaming</button>
                    </div>
                    <div class="audio-strip">
                      <label class="inline-toggle">
                        <input id="audioToggle" type="checkbox">
                        <span data-i18n="enableMicAudio">Enable microphone AAC</span>
                      </label>
                      <span id="audioStatusText" class="audio-status">Audio disabled</span>
                      <audio id="audioPlayer" class="audio-player" controls playsinline preload="none" hidden></audio>
                      <span id="audioUrlText" class="hidden-control">/audio.aac</span>
                    </div>
                  </section>

                  <section class="card">
                    <div class="row">
                      <div>
                        <h2 data-i18n="configuration">Configuration</h2>
                        <p id="statusText" data-i18n="loadingState">Loading camera state...</p>
                      </div>
                      <span id="stateBadge" class="badge idle" data-i18n="loading">Loading</span>
                    </div>

                    <div class="config-grid">
                      <div class="group span-all">
                        <h2 data-i18n="recommendedSettings">Recommended settings</h2>
                        <div class="top-presets">
                          <button class="button" type="button" data-preset="4k30">4K 30 fps 60%</button>
                          <button class="button" type="button" data-preset="1080p30">1080p 30 fps 75%</button>
                          <button class="button" type="button" data-preset="720p30">720p 30 fps 100%</button>
                        </div>
                      </div>

                      <div class="group span-all">
                        <h2 data-i18n="camera">Camera</h2>
                        <select id="cameraSelect" class="hidden-control" aria-hidden="true" tabindex="-1"></select>
                        <div id="cameraChipGroup" class="camera-chip-grid"></div>
                      </div>

                      <div class="group span-all">
                        <h2 data-i18n="video">Video</h2>
                        <div class="video-fields">
                          <div>
                            <label for="resolutionSelect" data-i18n="resolution">Resolution</label>
                            <select id="resolutionSelect"></select>
                            <div id="manualResolutionGroup" style="display:none; margin-top:10px;">
                              <div class="row">
                                <input id="manualWidthInput" type="number" placeholder="Width">
                                <input id="manualHeightInput" type="number" placeholder="Height">
                              </div>
                              <button id="applyManualResolution" class="button" type="button" style="width:100%; margin-top:10px;" data-i18n="applyCustomResolution">Apply custom resolution</button>
                              <div id="manualResolutionHint" class="hidden-control" data-i18n="manualResolutionHint">Enter a custom size and apply the nearest supported resolution.</div>
                            </div>
                          </div>
                          <div>
                            <label for="fpsSelect" data-i18n="frameRate">Frame rate</label>
                            <select id="fpsSelect"></select>
                          </div>
                        </div>
                      </div>

                      <div class="group span-all">
                        <h2 data-i18n="streamQuality">Stream quality</h2>
                        <input id="qualitySlider" type="range" min="30" max="95" step="5" value="60">
                        <div class="metric-line">
                          <span id="qualityValue" class="value">60%</span>
                        </div>
                      </div>

                      <div class="group span-all">
                        <h2 data-i18n="focusMode">Focus mode</h2>
                        <select id="focusModeSelect" class="hidden-control" aria-hidden="true" tabindex="-1">
                          <option value="auto" data-i18n="autoFocus">Auto focus</option>
                          <option value="manual" data-i18n="manualFocus">Manual focus</option>
                        </select>
                        <div class="segmented" id="focusModeSegment">
                          <button id="focusAutoButton" class="seg-btn" type="button" data-focus-mode="auto" data-i18n="autoFocusShort">Auto</button>
                          <button id="focusManualButton" class="seg-btn" type="button" data-focus-mode="manual" data-i18n="manualFocusShort">Manual</button>
                        </div>
                        <div class="manual-focus-group" id="focusDistanceGroup" style="display:none;">
                          <label for="focusDistanceSlider" data-i18n="focusDistance">Focus distance</label>
                          <input id="focusDistanceSlider" type="range" min="0" max="0" step="0.001" value="0" disabled>
                          <div class="metric-line">
                            <span id="focusDistanceValue" class="value" data-i18n="infinity">Infinity</span>
                            <span id="focusDistanceHint" class="hidden-control" data-i18n="manualFocusAvailable">Available in manual focus.</span>
                          </div>
                        </div>
                      </div>

                      <div class="group span-all">
                        <h2 data-i18n="zoom">Zoom</h2>
                        <input id="zoomSlider" type="range" min="0" max="1000" value="0" disabled>
                        <div class="metric-line">
                          <span id="zoomValue" class="value">1.00x</span>
                          <span id="zoomHint" class="hidden-control" data-i18n="zoomUnavailable">Not available for this camera.</span>
                        </div>
                      </div>

                      <div class="group span-all">
                        <div class="row row-tight">
                          <h2 data-i18n="fillLight">Fill light</h2>
                          <label class="switch" aria-label="Fill light">
                            <input id="torchToggle" type="checkbox">
                            <span class="switch-track"></span>
                          </label>
                        </div>
                      </div>

                      <div class="group span-all">
                        <div class="row row-tight">
                          <h2 data-i18n="videoOverlay">Video Overlay</h2>
                          <label class="switch" aria-label="Video Overlay">
                            <input id="videoOverlayToggle" type="checkbox">
                            <span class="switch-track"></span>
                          </label>
                        </div>
                        <div class="manual-focus-group" id="videoOverlaySizeGroup" style="display:none;">
                          <label for="videoOverlaySizeSlider" data-i18n="videoOverlaySize">Overlay size</label>
                          <input id="videoOverlaySizeSlider" type="range" min="1" max="100" step="1" value="25">
                          <div class="metric-line">
                            <span id="videoOverlaySizeValue" class="value">25</span>
                          </div>
                        </div>
                      </div>

                      <div class="group span-all">
                        <h2 data-i18n="exposure">Exposure</h2>
                        <input id="exposureSlider" type="range" min="0" max="0" value="0" disabled>
                        <div class="metric-line">
                          <span id="exposureValue" class="value">0 EV</span>
                          <span id="exposureHint" class="hidden-control" data-i18n="unavailable">Unavailable.</span>
                        </div>
                      </div>

                      <details class="group span-all preview-tuning-details">
                        <summary><span data-i18n="cameraDebug">Camera debug</span></summary>
                        <div class="actions">
                          <button id="scanCamerasButton" class="button primary" type="button" data-i18n="scanCameras">Scan cameras</button>
                          <button id="refreshVerifiedCamerasButton" class="button" type="button" data-i18n="refreshCameras">Refresh list</button>
                          <button id="clearScanCacheButton" class="button danger" type="button" disabled data-i18n="clearScanCache">Clear local scan cache</button>
                        </div>
                        <p id="cameraScanStatus" class="subtle" data-i18n="verifiedCameraIdle">Verified camera cache is local to this phone.</p>
                        <label for="verifiedCameraSelect" style="margin-top:10px;" data-i18n="verifiedCameras">Verified cameras</label>
                        <select id="verifiedCameraSelect"></select>
                        <button id="applyVerifiedCameraButton" class="button" type="button" style="width:100%; margin-top:10px;" data-i18n="selectVerifiedCamera">Select verified camera</button>
                        <div id="verifiedCameraList" class="camera-debug-list"></div>
                      </details>

                      <details class="group span-all preview-tuning-details">
                        <summary><span data-i18n="previewTuning">App preview tuning</span></summary>
                        <label for="previewRotationSlider" data-i18n="previewRotation">Rotation offset</label>
                        <input id="previewRotationSlider" type="range" min="-180" max="180" step="90" value="0">
                        <div class="row">
                          <span data-i18n="previewRotationHint">Use this only if the app preview direction is wrong.</span>
                          <span id="previewRotationValue" class="value">0°</span>
                        </div>
                        <label for="previewScaleSlider" style="margin-top:10px;" data-i18n="previewScale">Scale</label>
                        <input id="previewScaleSlider" type="range" min="25" max="400" step="1" value="100">
                        <div class="row">
                          <span data-i18n="previewScaleHint">100% is the default fit calculation.</span>
                          <span id="previewScaleValue" class="value">100%</span>
                        </div>
                        <label for="previewStretchXSlider" style="margin-top:10px;" data-i18n="previewStretchX">Horizontal ratio</label>
                        <input id="previewStretchXSlider" type="range" min="25" max="400" step="1" value="100">
                        <div class="row">
                          <span data-i18n="previewStretchHint">Use ratio controls to correct visible stretching.</span>
                          <span id="previewStretchXValue" class="value">100%</span>
                        </div>
                        <label for="previewStretchYSlider" style="margin-top:10px;" data-i18n="previewStretchY">Vertical ratio</label>
                        <input id="previewStretchYSlider" type="range" min="25" max="400" step="1" value="100">
                        <div class="row">
                          <span data-i18n="previewFeedback">Send this feedback string back to Codex.</span>
                          <span id="previewStretchYValue" class="value">100%</span>
                        </div>
                        <input id="previewTuningCode" type="text" readonly value="PREVIEW_TUNING rotation=0 scale=1.00 stretchX=1.00 stretchY=1.00" style="margin-top:10px; font-family: Consolas, monospace;">
                        <button id="resetPreviewTuningButton" class="button" type="button" style="width:100%; margin-top:10px;" data-i18n="resetPreviewTuning">Reset preview tuning</button>
                      </details>
                    </div>
                  </section>
                </div>
              </div>

              <script>
                const skinButtons = document.querySelectorAll('[data-skin]');
                const skins = ['skin-sunrise', 'skin-mineral'];
                skinButtons.forEach(button => {
                  button.addEventListener('click', () => {
                    document.body.classList.remove(...skins);
                    if (button.dataset.skin) document.body.classList.add(button.dataset.skin);
                    try { localStorage.setItem('multilensSkin', button.dataset.skin || ''); } catch (_) {}
                  });
                });
                try {
                  const savedSkin = localStorage.getItem('multilensSkin');
                  if (savedSkin) document.body.classList.add(savedSkin);
                } catch (_) {}

                const languageButtons = document.querySelectorAll('[data-lang]');
                const copy = {
                  en: {
                    loadingState: 'Loading camera state...',
                    livePreview: 'Live preview',
                    previewHint: 'MJPEG browser preview with live camera controls.',
                    previewLive: 'Live MJPEG preview from /video.',
                    previewIdle: 'Idle. Use Start streaming while this dashboard remains open.',
                    checkingStream: 'Checking stream...',
                    openMjpeg: 'Open MJPEG',
                    copyH264: 'Copy RTSP URL',
                    copyAudio: 'Copy audio URL',
                    h264Hint: 'RTSP is for VLC, NVRs, ffplay, OBS, or later integrations; use MJPEG for direct browser preview.',
                    openAudio: 'Open audio AAC',
                    closeAudio: 'Close audio',
                    audio: 'Audio',
                    enableMicAudio: 'Enable microphone AAC',
                    audioCompatibility: 'Open Audio AAC enables the microphone first, then opens the raw AAC stream. Playback support depends on the browser and is not synced to video.',
                    audioDisabled: 'Audio disabled',
                    openingAudio: 'Opening audio...',
                    enablingAudio: 'Enabling audio...',
                    audioWaiting: 'Waiting for microphone audio...',
                    audioReady: 'Audio ready. Starting playback...',
                    audioNeedsPermission: 'Microphone permission required. Grant RECORD_AUDIO on the phone before opening audio.',
                    audioNeedsStreaming: 'Start video streaming before opening microphone audio.',
                    audioUnsupported: 'Microphone audio is unavailable on this device.',
                    audioEnableFailed: 'Audio did not start. Check streaming state and microphone permission.',
                    closingAudio: 'Closing audio...',
                    audioPlaying: 'Audio playing in this page.',
                    audioAutoplayBlocked: 'Audio is ready, but the browser blocked automatic playback.',
                    copied: 'Copied',
                    copyFailed: 'Copy failed',
                    snapshot: 'Snapshot',
                    startStreaming: 'Start streaming',
                    starting: 'Starting...',
                    stopStreaming: 'Stop streaming',
                    stopping: 'Stopping...',
                    configuration: 'Configuration',
                    loading: 'Loading',
                    camera: 'Camera',
                    recommendedSettings: 'Recommended settings',
                    video: 'Video',
                    videoProfile: 'Video profile',
                    resolution: 'Resolution',
                    manualResolutionHint: 'Enter a custom size and apply the nearest supported resolution.',
                    manualResolutionActive: 'Custom input mode. The app will pick the nearest supported size.',
                    applyCustomResolution: 'Apply custom resolution',
                    frameRate: 'Frame rate',
                    unlimitedFps: 'Unlimited fps',
                    streamQuality: 'Stream quality',
                    compressionBitrate: 'Compression / bitrate',
                    lensFocus: 'Lens and focus',
                    zoom: 'Zoom',
                    zoomRatio: 'Zoom ratio',
                    focusMode: 'Focus mode',
                    autoFocus: 'Auto focus',
                    manualFocus: 'Manual focus',
                    autoFocusShort: 'Auto',
                    manualFocusShort: 'Manual',
                    lightExposure: 'Light and exposure',
                    fillLight: 'Fill light',
                    videoOverlay: 'Video Overlay',
                    videoOverlaySize: 'Overlay size',
                    torch: 'Flashlight / torch',
                    focusDistance: 'Focus distance',
                    exposure: 'Exposure',
                    exposureCompensation: 'Exposure compensation',
                    unavailable: 'Unavailable.',
                    unavailableCamera: 'Unavailable on this camera.',
                    zoomUnavailable: 'Not available for this camera.',
                    torchAvailable: 'Toggle fill light during streaming.',
                    manualFocusAvailable: 'Available in manual focus.',
                    infinity: 'Infinity',
                    nearestFocus: 'Nearest',
                    focusDistanceRange: '0 = infinity, max = nearest focus',
                    streaming: 'Streaming',
                    idle: 'Idle',
                    stateOk: 'State OK',
                    stateUnavailable: 'State unavailable',
                    unavailableShort: 'Unavailable',
                    range: 'Range',
                    to: 'to',
                    evUnit: 'EV',
                    evSteps: 'EV steps',
                    width: 'Width',
                    height: 'Height',
                    cameraFallback: 'Camera',
                    fpsSuffix: ' fps',
                    diopters: ' diopters',
                    manualInput: 'Manual input',
                    previewTuning: 'App preview tuning',
                    previewRotation: 'Rotation offset',
                    previewRotationHint: 'Use this only if the app preview direction is wrong.',
                    previewScale: 'Scale',
                    previewScaleHint: '100% is the default fit calculation.',
                    previewStretchX: 'Horizontal ratio',
                    previewStretchY: 'Vertical ratio',
                    previewStretchHint: 'Use ratio controls to correct visible stretching.',
                    previewFeedback: 'Send this feedback string back to Codex.',
                    resetPreviewTuning: 'Reset preview tuning',
                    cameraDebug: 'Camera debug',
                    scanCameras: 'Scan cameras',
                    refreshCameras: 'Refresh list',
                    verifiedCameras: 'Verified cameras',
                    selectVerifiedCamera: 'Select verified camera',
                    verifiedCameraIdle: 'Verified camera cache is local to this phone.',
                    scanningCameras: 'Scanning cameras...',
                    scanComplete: 'Camera scan complete.',
                    noVerifiedCameras: 'No verified cameras saved yet.',
                    clearScanCache: 'Clear local scan cache',
                    scanCacheCleared: 'Local scan cache cleared.',
                    deleteScanEntry: 'Delete',
                    profileEntry: 'built-in',
                    sourceProfile: 'profile',
                    sourceScan: 'scan'
                  },
                  zh: {}
                };
                copy.en.video = 'Video';
                copy.en.zoom = 'Zoom';
                copy.en.fillLight = 'Fill light';
                copy.en.videoOverlay = 'Video Overlay';
                copy.en.exposure = 'Exposure';
                copy.en.autoFocusShort = 'Auto';
                copy.en.manualFocusShort = 'Manual';
                copy.en.evUnit = 'EV';
                Object.assign(copy.zh, {
                  loadingState: '正在加载摄像头状态...',
                  livePreview: '实时预览',
                  previewHint: '浏览器中的 MJPEG 实时画面与控制。',
                  previewLive: '来自 /video 的 MJPEG 实时画面。',
                  previewIdle: '当前未直播。保持此页面开启后点击开始直播。',
                  checkingStream: '正在检查直播状态...',
                  openMjpeg: '打开 MJPEG',
                  copyH264: '复制 RTSP 地址',
                  copyAudio: '复制音频地址',
                  h264Hint: 'RTSP 可用于 VLC、NVR、ffplay、OBS 等，浏览器直接预览请使用 MJPEG。',
                  openAudio: '打开音频 AAC',
                  closeAudio: '关闭音频',
                  audio: '音频',
                  enableMicAudio: '启用麦克风 AAC',
                  audioCompatibility: '“打开音频 AAC”会先启用麦克风，再打开原始 AAC 流。是否可播放取决于浏览器，且与视频不同步。',
                  audioDisabled: '音频已关闭',
                  openingAudio: '正在打开音频...',
                  enablingAudio: '正在启用音频...',
                  audioWaiting: '正在等待麦克风音频...',
                  audioReady: '音频已就绪，开始播放...',
                  audioNeedsPermission: '需要麦克风权限。请先在手机上授予 RECORD_AUDIO。',
                  audioNeedsStreaming: '请先开始视频直播，再打开麦克风音频。',
                  audioUnsupported: '此设备不支持麦克风音频。',
                  audioEnableFailed: '音频未能启动，请检查直播状态与麦克风权限。',
                  closingAudio: '正在关闭音频...',
                  audioPlaying: '此页面正在播放音频。',
                  audioAutoplayBlocked: '音频已就绪，但浏览器阻止了自动播放。',
                  copied: '已复制',
                  copyFailed: '复制失败',
                  snapshot: '抓拍',
                  startStreaming: '开始直播',
                  starting: '正在开始...',
                  stopStreaming: '停止直播',
                  stopping: '正在停止...',
                  configuration: '配置',
                  loading: '加载中',
                  camera: '摄像头',
                  recommendedSettings: '推荐设置',
                  video: '视频',
                  videoProfile: '视频配置',
                  resolution: '分辨率',
                  manualResolutionHint: '输入自定义尺寸并应用最接近的受支持分辨率。',
                  manualResolutionActive: '当前为自定义输入模式，应用会选择最接近的受支持尺寸。',
                  applyCustomResolution: '应用自定义分辨率',
                  frameRate: '帧率',
                  unlimitedFps: '不限帧率',
                  streamQuality: '推流质量',
                  compressionBitrate: '压缩率 / 码率',
                  lensFocus: '镜头与对焦',
                  zoom: '变焦',
                  zoomRatio: '变焦倍率',
                  focusMode: '对焦模式',
                  autoFocus: '自动对焦',
                  manualFocus: '手动对焦',
                  autoFocusShort: '自动',
                  manualFocusShort: '手动',
                  lightExposure: '补光与曝光',
                  fillLight: '补光',
                  videoOverlay: '视频叠加层',
                  videoOverlaySize: '叠加层尺寸',
                  torch: '手电筒 / 补光灯',
                  focusDistance: '对焦距离',
                  exposure: '曝光',
                  exposureCompensation: '曝光补偿',
                  unavailable: '不可用。',
                  unavailableCamera: '当前摄像头不支持。',
                  zoomUnavailable: '当前摄像头不支持变焦。',
                  torchAvailable: '直播期间可开关补光灯。',
                  manualFocusAvailable: '手动对焦模式下可用。',
                  infinity: '无限远',
                  nearestFocus: '最近',
                  focusDistanceRange: '0 = 无限远，最大值 = 最近对焦',
                  streaming: '直播中',
                  idle: '空闲',
                  stateOk: '状态正常',
                  stateUnavailable: '状态不可用',
                  unavailableShort: '不可用',
                  range: '范围',
                  to: '到',
                  evUnit: 'EV',
                  evSteps: 'EV 步进',
                  width: '宽度',
                  height: '高度',
                  cameraFallback: '摄像头',
                  fpsSuffix: ' fps',
                  diopters: ' 屈光度',
                  manualInput: '手动输入',
                  previewTuning: 'App 预览调校',
                  previewRotation: '旋转偏移',
                  previewRotationHint: '仅在 App 预览方向错误时使用。',
                  previewScale: '缩放',
                  previewScaleHint: '100% 为默认适配计算值。',
                  previewStretchX: '横向比例',
                  previewStretchY: '纵向比例',
                  previewStretchHint: '可用比例控制来修正拉伸观感。',
                  previewFeedback: '请将此反馈字符串返回给 Codex。',
                  resetPreviewTuning: '重置预览调校',
                  cameraDebug: '摄像头调试',
                  scanCameras: '扫描摄像头',
                  refreshCameras: '刷新列表',
                  verifiedCameras: '已验证摄像头',
                  selectVerifiedCamera: '选择已验证摄像头',
                  verifiedCameraIdle: '已验证摄像头缓存仅保存在本机。',
                  scanningCameras: '正在扫描摄像头...',
                  scanComplete: '摄像头扫描完成。',
                  noVerifiedCameras: '暂无已保存的验证摄像头。',
                  clearScanCache: '清除本地扫描缓存',
                  scanCacheCleared: '本地扫描缓存已清除。',
                  deleteScanEntry: '删除',
                  profileEntry: '内置',
                  sourceProfile: '配置',
                  sourceScan: '扫描'
                });
                let currentLang = 'zh';
                try {
                  currentLang = localStorage.getItem('multilensLanguage') || 'zh';
                } catch (_) {}
                if (!copy[currentLang]) currentLang = 'zh';

                function t(key) {
                  return (copy[currentLang] && copy[currentLang][key]) || copy.en[key] || key;
                }

                function applyLanguage() {
                  document.documentElement.lang = currentLang === 'zh' ? 'zh-CN' : 'en';
                  document.querySelectorAll('[data-i18n]').forEach(element => {
                    element.textContent = t(element.dataset.i18n);
                  });
                  manualWidthInput.placeholder = t('width');
                  manualHeightInput.placeholder = t('height');
                  languageButtons.forEach(button => {
                    button.classList.toggle('active', button.dataset.lang === currentLang);
                  });
                }

                languageButtons.forEach(button => {
                  button.addEventListener('click', () => {
                    currentLang = button.dataset.lang || 'en';
                    try { localStorage.setItem('multilensLanguage', currentLang); } catch (_) {}
                    applyLanguage();
                    refreshState();
                  });
                });

                const stateCache = {
                  streaming: false,
                  cameraOptions: [],
                  resolutionOptions: [],
                  fpsOptions: [],
                  selectedCameraKey: '',
                  rtspUrl: '',
                  rtspAvailable: false,
                  zoomMin: 1,
                  zoomMax: 1,
                  focusDistanceMin: 0,
                  focusDistanceMax: 0,
                  focusSupported: false,
                  videoOverlaySizeMin: 1,
                  videoOverlaySizeMax: 100,
                  exposureMin: 0,
                  exposureMax: 0
                };
                const presetDefinitions = {
                  '1080p30': { resolution: '1920x1080', fps: 30, quality: 75 },
                  '720p30': { resolution: '1280x720', fps: 30, quality: 100 },
                  '4k30': { resolution: '3840x2160', fps: 30, quality: 60 }
                };
                const cameraSelect = document.getElementById('cameraSelect');
                const cameraChipGroup = document.getElementById('cameraChipGroup');
                const resolutionSelect = document.getElementById('resolutionSelect');
                const presetButtons = document.querySelectorAll('[data-preset]');
                const manualResolutionGroup = document.getElementById('manualResolutionGroup');
                const manualWidthInput = document.getElementById('manualWidthInput');
                const manualHeightInput = document.getElementById('manualHeightInput');
                const manualResolutionHint = document.getElementById('manualResolutionHint');
                const applyManualResolutionButton = document.getElementById('applyManualResolution');
                const fpsSelect = document.getElementById('fpsSelect');
                const qualitySlider = document.getElementById('qualitySlider');
                const focusModeSelect = document.getElementById('focusModeSelect');
                const focusModeButtons = document.querySelectorAll('[data-focus-mode]');
                const torchToggle = document.getElementById('torchToggle');
                const videoOverlayToggle = document.getElementById('videoOverlayToggle');
                const videoOverlaySizeGroup = document.getElementById('videoOverlaySizeGroup');
                const videoOverlaySizeSlider = document.getElementById('videoOverlaySizeSlider');
                const zoomSlider = document.getElementById('zoomSlider');
                const focusDistanceGroup = document.getElementById('focusDistanceGroup');
                const focusDistanceSlider = document.getElementById('focusDistanceSlider');
                const exposureSlider = document.getElementById('exposureSlider');
                const previewRotationSlider = document.getElementById('previewRotationSlider');
                const previewScaleSlider = document.getElementById('previewScaleSlider');
                const previewStretchXSlider = document.getElementById('previewStretchXSlider');
                const previewStretchYSlider = document.getElementById('previewStretchYSlider');
                const previewTuningCode = document.getElementById('previewTuningCode');
                const resetPreviewTuningButton = document.getElementById('resetPreviewTuningButton');
                const scanCamerasButton = document.getElementById('scanCamerasButton');
                const refreshVerifiedCamerasButton = document.getElementById('refreshVerifiedCamerasButton');
                const clearScanCacheButton = document.getElementById('clearScanCacheButton');
                const verifiedCameraSelect = document.getElementById('verifiedCameraSelect');
                const applyVerifiedCameraButton = document.getElementById('applyVerifiedCameraButton');
                const cameraScanStatus = document.getElementById('cameraScanStatus');
                const verifiedCameraList = document.getElementById('verifiedCameraList');
                const streamBadge = document.getElementById('streamBadge');
                const stateBadge = document.getElementById('stateBadge');
                const versionText = document.getElementById('versionText');
                const preview = document.getElementById('preview');
                const copyH264Button = document.getElementById('copyH264Button');
                const copyAudioButton = document.getElementById('copyAudioButton');
                const audioToggle = document.getElementById('audioToggle');
                const audioStatusText = document.getElementById('audioStatusText');
                const audioUrlText = document.getElementById('audioUrlText');
                const audioOpenButton = document.getElementById('audioOpenButton');
                const audioPlayer = document.getElementById('audioPlayer');
                const streamingToggleButton = document.getElementById('streamingToggleButton');
                let applyingRemoteState = false;
                let controlTimer = null;
                let previewAttached = false;
                let stopping = false;
                let starting = false;
                let openingAudio = false;
                let lastAudioState = null;
                let audioActivationPromise = null;
                let audioPlaybackBlocked = false;
                let scanningCameras = false;
                let manualResolutionEditing = false;
                const unlimitedFpsValue = '__unlimited__';

                function fmt(value, suffix) {
                  return typeof value === 'number' && !Number.isNaN(value) ? value.toFixed(2) + suffix : '-';
                }

                function formatResolutionLabel(value) {
                  if (typeof value !== 'string') return '-';
                  const match = value.match(/^(\d+)x(\d+)$/i);
                  if (!match) return value || '-';
                  const height = Number(match[2]);
                  if (height === 2160) return '4K';
                  if (height === 1440) return '1440P';
                  if (height === 1080) return '1080P';
                  if (height === 720) return '720P';
                  if (height === 480) return '480P';
                  return value;
                }

                function buildCameraSummary(state, selectedCameraLabel) {
                  const fpsLabel = state.unlimitedFpsSelected
                    ? t('unlimitedFps')
                    : ((state.selectedFps || '-') + 'fps');
                  const qualityLabel = String(state.qualityValue ?? 60) + '%';
                  return [
                    selectedCameraLabel || t('cameraFallback'),
                    formatResolutionLabel(state.selectedResolution || '-'),
                    fpsLabel,
                    qualityLabel
                  ].join(' · ');
                }

                function fmtFocusDistance(value) {
                  if (typeof value !== 'number' || Number.isNaN(value) || value <= (stateCache.focusDistanceMin + 0.0005)) {
                    return t('infinity');
                  }
                  if (value >= (stateCache.focusDistanceMax - 0.0005)) {
                    return t('nearestFocus');
                  }
                  const span = Math.max(stateCache.focusDistanceMax - stateCache.focusDistanceMin, 0.0001);
                  const percent = Math.round(((value - stateCache.focusDistanceMin) / span) * 100);
                  return String(Math.min(100, Math.max(1, percent)));
                }

                function updateVideoOverlaySizeVisibility() {
                  const showOverlaySize = videoOverlayToggle.checked;
                  videoOverlaySizeGroup.style.display = showOverlaySize ? '' : 'none';
                  videoOverlaySizeSlider.disabled = !showOverlaySize;
                  return showOverlaySize;
                }

                function updateFocusDistanceVisibility() {
                  const showManualDistance = focusModeSelect.value === 'manual' && !!stateCache.focusSupported;
                  focusDistanceGroup.style.display = showManualDistance ? '' : 'none';
                  focusDistanceSlider.disabled = !showManualDistance;
                  return showManualDistance;
                }

                function updateFocusModeButtons() {
                  focusModeButtons.forEach(button => {
                    const mode = button.dataset.focusMode || 'auto';
                    const active = mode === focusModeSelect.value;
                    button.classList.toggle('active', active);
                    button.disabled = focusModeSelect.disabled;
                  });
                }

                function setFocusMode(mode) {
                  if (focusModeSelect.disabled) return;
                  const nextMode = mode === 'manual' ? 'manual' : 'auto';
                  if (focusModeSelect.value === nextMode) return;
                  focusModeSelect.value = nextMode;
                  updateFocusModeButtons();
                  updateFocusDistanceVisibility();
                  pushControls();
                }

                function setExposureValueLabel(value) {
                  if (value == null) {
                    document.getElementById('exposureValue').textContent = '-';
                    return;
                  }
                  document.getElementById('exposureValue').textContent = String(Math.round(Number(value))) + ' ' + t('evUnit');
                }

                function fillSelect(select, items, selectedValue, formatter) {
                  const currentValue = select.value;
                  select.innerHTML = '';
                  items.forEach(item => {
                    const option = document.createElement('option');
                    const value = typeof item === 'object' ? item.value : String(item);
                    const label = formatter ? formatter(item) : (typeof item === 'object' ? item.label : String(item));
                    option.value = value;
                    option.textContent = label;
                    select.appendChild(option);
                  });
                  const preferred = selectedValue != null ? String(selectedValue) : currentValue;
                  if (preferred) {
                    select.value = preferred;
                  }
                }

                function fpsOptionItems(state) {
                  const items = (stateCache.fpsOptions || []).map(value => ({
                    value: String(value),
                    label: String(value) + t('fpsSuffix')
                  }));
                  if (state.unlimitedFpsSupported) {
                    items.push({ value: unlimitedFpsValue, label: t('unlimitedFps') });
                  }
                  return items;
                }

                function fpsIsUnlimitedSelected() {
                  return fpsSelect.value === unlimitedFpsValue;
                }

                function renderCameraChips(items, selectedValue) {
                  cameraChipGroup.innerHTML = '';
                  if (!items || items.length === 0) {
                    const item = document.createElement('button');
                    item.className = 'button';
                    item.type = 'button';
                    item.disabled = true;
                    item.textContent = t('cameraFallback');
                    cameraChipGroup.appendChild(item);
                    return;
                  }
                  items.forEach(item => {
                    const button = document.createElement('button');
                    button.className = 'button';
                    button.type = 'button';
                    button.textContent = item.label || item.value;
                    button.classList.toggle('active', item.value === selectedValue);
                    button.addEventListener('click', () => {
                      cameraSelect.value = item.value;
                      pushControls();
                    });
                    cameraChipGroup.appendChild(button);
                  });
                }

                function setBadge(element, text, mode) {
                  element.textContent = text;
                  element.classList.remove('idle', 'error');
                  if (mode) element.classList.add(mode);
                }

                function setPreviewStreaming(streaming) {
                  if (streaming && !previewAttached) {
                    preview.src = '/video?preview=' + Date.now();
                    previewAttached = true;
                  } else if (!streaming && previewAttached) {
                    preview.removeAttribute('src');
                    previewAttached = false;
                  }
                }

                function setAudioStatus(text, mode) {
                  audioStatusText.textContent = text;
                  audioStatusText.classList.toggle('error', mode === 'error');
                }

                function resetAudioPlayer() {
                  try { audioPlayer.pause(); } catch (_) {}
                  audioPlayer.removeAttribute('src');
                  audioPlayer.load();
                  audioPlayer.hidden = true;
                  audioActivationPromise = null;
                }

                function audioUrlWithPlaybackParams(url) {
                  const separator = url.includes('?') ? '&' : '?';
                  return url + separator + 'wait=1&autoplay=' + Date.now();
                }

                function enableAudioSynchronouslyForOpen() {
                  const state = lastAudioState;
                  if (state && state.audioEnabled) return true;
                  if (state && (!state.audioSupported || !state.audioPermissionGranted || !state.streaming)) return true;
                  try {
                    const xhr = new XMLHttpRequest();
                    xhr.open('GET', '/api/control?audio=true', false);
                    xhr.send(null);
                    return xhr.status >= 200 && xhr.status < 300;
                  } catch (_) {
                    return false;
                  }
                }

                function primeAudioElement() {
                  const enabledSynchronously = enableAudioSynchronouslyForOpen();
                  audioPlayer.hidden = false;
                  audioPlayer.muted = false;
                  audioPlayer.controls = true;
                  audioPlayer.src = audioUrlWithPlaybackParams(audioUrlFromState(lastAudioState));
                  audioPlayer.load();
                  try {
                    const playPromise = audioPlayer.play();
                    audioActivationPromise = playPromise && playPromise.catch
                      ? playPromise.then(() => true).catch(() => false)
                      : Promise.resolve(true);
                  } catch (_) {
                    audioActivationPromise = Promise.resolve(false);
                  }
                  return enabledSynchronously;
                }

                function updateAudioState(state) {
                  const audioUrl = state.audioUrl || (window.location.origin + '/audio.aac');
                  lastAudioState = state;
                  audioToggle.checked = !!state.audioEnabled;
                  audioToggle.disabled = !state.audioSupported;
                  if (!openingAudio) {
                    if (audioPlaybackBlocked && audioReady(state)) {
                      setAudioStatus(t('audioAutoplayBlocked'), 'error');
                    } else {
                      setAudioStatus(state.audioStatus || t('audioDisabled'));
                    }
                  }
                  audioUrlText.textContent = audioUrl;
                  audioOpenButton.disabled = !state.audioSupported || openingAudio;
                  if (!openingAudio) {
                    const active = audioButtonActive(state);
                    audioOpenButton.textContent = active ? t('closeAudio') : t('openAudio');
                    audioOpenButton.classList.toggle('active', active);
                  }
                  if (!openingAudio && !state.audioEnabled) {
                    audioPlaybackBlocked = false;
                    resetAudioPlayer();
                  }
                }

                function zoomProgressToValue(progress) {
                  return stateCache.zoomMin + ((stateCache.zoomMax - stateCache.zoomMin) * progress / 1000);
                }

                function valueToZoomProgress(value) {
                  const span = stateCache.zoomMax - stateCache.zoomMin;
                  if (span <= 0) return 0;
                  return Math.round(((value - stateCache.zoomMin) / span) * 1000);
                }

                function scheduleControlPush() {
                  if (controlTimer) clearTimeout(controlTimer);
                  controlTimer = setTimeout(pushControls, 140);
                }

                function isManualResolutionSelected() {
                  return resolutionSelect.value === 'Manual input';
                }

                function updateManualResolutionVisibility(enabled) {
                  manualResolutionGroup.style.display = enabled ? 'block' : 'none';
                  manualResolutionHint.textContent = enabled
                    ? t('manualResolutionActive')
                    : t('manualResolutionHint');
                }

                function updatePresetButtonStates(state) {
                  presetButtons.forEach(button => {
                    const preset = presetDefinitions[button.dataset.preset || ''];
                    if (!preset) return;
                    const resolutionSupported = stateCache.resolutionOptions.includes(preset.resolution);
                    const fpsSupported = stateCache.fpsOptions.includes(preset.fps);
                    const active = state.selectedResolution === preset.resolution &&
                      Number(state.selectedFps) === preset.fps &&
                      Number(state.qualityValue) === preset.quality &&
                      !state.unlimitedFpsSelected;
                    button.disabled = !(resolutionSupported && fpsSupported);
                    button.classList.toggle('primary', active);
                  });
                }

                function setPreviewTuningLabels() {
                  const rotation = Number(previewRotationSlider.value);
                  const scale = Number(previewScaleSlider.value) / 100;
                  const stretchX = Number(previewStretchXSlider.value) / 100;
                  const stretchY = Number(previewStretchYSlider.value) / 100;
                  document.getElementById('previewRotationValue').textContent = rotation + '°';
                  document.getElementById('previewScaleValue').textContent = Math.round(scale * 100) + '%';
                  document.getElementById('previewStretchXValue').textContent = Math.round(stretchX * 100) + '%';
                  document.getElementById('previewStretchYValue').textContent = Math.round(stretchY * 100) + '%';
                  previewTuningCode.value = 'PREVIEW_TUNING rotation=' + rotation +
                    ' scale=' + scale.toFixed(2) +
                    ' stretchX=' + stretchX.toFixed(2) +
                    ' stretchY=' + stretchY.toFixed(2);
                }

                function schedulePreviewTuningPush() {
                  setPreviewTuningLabels();
                  if (controlTimer) clearTimeout(controlTimer);
                  controlTimer = setTimeout(pushPreviewTuning, 120);
                }

                async function pushPreviewTuning() {
                  const params = new URLSearchParams();
                  params.set('previewRotation', previewRotationSlider.value);
                  params.set('previewScale', (Number(previewScaleSlider.value) / 100).toFixed(3));
                  params.set('previewStretchX', (Number(previewStretchXSlider.value) / 100).toFixed(3));
                  params.set('previewStretchY', (Number(previewStretchYSlider.value) / 100).toFixed(3));
                  try {
                    await fetch('/api/control?' + params.toString(), { cache: 'no-store' });
                    await refreshState();
                  } catch (_) {
                  }
                }

                async function pushControls() {
                  if (applyingRemoteState) return;
                  const params = new URLSearchParams();
                  if (cameraSelect.value) params.set('camera', cameraSelect.value);
                  if (resolutionSelect.value) params.set('resolution', resolutionSelect.value);
                  if (isManualResolutionSelected()) {
                    if (manualWidthInput.value) params.set('manualWidth', manualWidthInput.value);
                    if (manualHeightInput.value) params.set('manualHeight', manualHeightInput.value);
                  }
                  if (fpsIsUnlimitedSelected()) {
                    params.set('unlimitedFps', 'true');
                  } else {
                    if (fpsSelect.value) params.set('fps', fpsSelect.value);
                    params.set('unlimitedFps', 'false');
                  }
                  params.set('quality', qualitySlider.value);
                  if (focusModeSelect.value) params.set('focus', focusModeSelect.value);
                  params.set('torch', torchToggle.checked ? 'true' : 'false');
                  params.set('videoOverlay', videoOverlayToggle.checked ? 'true' : 'false');
                  if (!videoOverlaySizeSlider.disabled) params.set('videoOverlaySize', videoOverlaySizeSlider.value);
                  if (!zoomSlider.disabled) params.set('zoom', zoomProgressToValue(Number(zoomSlider.value)).toFixed(3));
                  if (!focusDistanceSlider.disabled) params.set('focusDistance', Number(focusDistanceSlider.value).toFixed(3));
                  if (!exposureSlider.disabled) params.set('exposure', String(Math.round(Number(exposureSlider.value))));
                  try {
                    await fetch('/api/control?' + params.toString(), { cache: 'no-store' });
                    await refreshState();
                  } catch (_) {
                    // Ignore transient network failures.
                  }
                }

                async function applyPreset(preset) {
                  try {
                    await fetch('/api/control?preset=' + encodeURIComponent(preset), { cache: 'no-store' });
                    await refreshState();
                  } catch (_) {
                  }
                }

                async function copyText(value) {
                  let ok = false;
                  try {
                    if (navigator.clipboard && navigator.clipboard.writeText) {
                      await navigator.clipboard.writeText(value);
                      ok = true;
                    }
                  } catch (_) {}
                  if (!ok) {
                    const input = document.createElement('input');
                    input.value = value;
                    document.body.appendChild(input);
                    input.select();
                    try { ok = document.execCommand('copy'); } catch (_) {}
                    document.body.removeChild(input);
                  }
                  return ok;
                }

                async function copyH264Url() {
                  const ok = stateCache.rtspAvailable && !!stateCache.rtspUrl && await copyText(stateCache.rtspUrl);
                  copyH264Button.textContent = ok ? t('copied') : t('copyFailed');
                  window.setTimeout(() => { copyH264Button.textContent = t('copyH264'); }, 1200);
                }

                async function copyAudioUrl() {
                  const ok = await copyText(window.location.origin + '/audio.aac');
                  copyAudioButton.textContent = ok ? t('copied') : t('copyFailed');
                  window.setTimeout(() => { copyAudioButton.textContent = t('copyAudio'); }, 1200);
                }

                function audioUrlFromState(state) {
                  return (state && state.audioUrl) || audioUrlText.textContent || (window.location.origin + '/audio.aac');
                }

                function audioReady(state) {
                  const ready = state && (state.audioRunning || state.audioStreaming);
                  return !!(state && state.audioSupported && state.audioPermissionGranted && state.audioEnabled && ready);
                }

                function audioButtonActive(state) {
                  return !!(state && (state.audioEnabled || state.audioRunning || state.audioStreaming));
                }

                function delay(ms) {
                  return new Promise(resolve => window.setTimeout(resolve, ms));
                }

                function waitForAudioActivation(timeoutMs) {
                  if (!audioActivationPromise) return Promise.resolve(null);
                  return Promise.race([
                    audioActivationPromise,
                    delay(timeoutMs).then(() => null)
                  ]);
                }

                async function fetchStateForAudio() {
                  const response = await fetch('/api/state', { cache: 'no-store' });
                  if (!response.ok) throw new Error('state unavailable');
                  const state = await response.json();
                  updateAudioState(state);
                  return state;
                }

                async function waitForAudioReady() {
                  let state = null;
                  for (let attempt = 0; attempt < 24; attempt += 1) {
                    state = await fetchStateForAudio();
                    if (!state.audioSupported) return { ok: false, state: state, message: t('audioUnsupported') };
                    if (!state.audioPermissionGranted) return { ok: false, state: state, message: t('audioNeedsPermission') };
                    if (!state.streaming) return { ok: false, state: state, message: t('audioNeedsStreaming') };
                    if (audioReady(state)) return { ok: true, state: state };
                    await delay(250);
                  }
                  return { ok: false, state: state, message: state && state.audioStatus ? state.audioStatus : t('audioEnableFailed') };
                }

                function setOpeningAudioState(enabled, labelKey) {
                  openingAudio = enabled;
                  audioOpenButton.disabled = enabled || (lastAudioState ? !lastAudioState.audioSupported : false);
                  if (enabled) {
                    audioOpenButton.textContent = t(labelKey || 'openingAudio');
                    return;
                  }
                  const active = audioButtonActive(lastAudioState);
                  audioOpenButton.textContent = active ? t('closeAudio') : t('openAudio');
                  audioOpenButton.classList.toggle('active', active);
                }

                async function openAudioStream() {
                  if (openingAudio) return;
                  audioPlaybackBlocked = false;
                  const enabledSynchronously = primeAudioElement();
                  setOpeningAudioState(true, 'enablingAudio');
                  setAudioStatus(t('enablingAudio'));
                  try {
                    let state = await fetchStateForAudio();
                    if (!state.audioSupported) throw new Error(t('audioUnsupported'));
                    if (!state.audioPermissionGranted) throw new Error(t('audioNeedsPermission'));
                    if (!state.streaming) throw new Error(t('audioNeedsStreaming'));
                    if (!audioReady(state)) {
                      if (!state.audioEnabled || !enabledSynchronously) {
                        const controlResponse = await fetch('/api/control?audio=true', { cache: 'no-store' });
                        if (!controlResponse.ok) throw new Error(t('audioEnableFailed'));
                      }
                      setOpeningAudioState(true, 'audioWaiting');
                      setAudioStatus(t('audioWaiting'));
                    }
                    const result = await waitForAudioReady();
                    if (!result.ok) throw new Error(result.message || t('audioEnableFailed'));
                    setAudioStatus(t('audioReady'));
                    const playbackStarted = await waitForAudioActivation(2000);
                    if (playbackStarted === true) {
                      setAudioStatus(t('audioPlaying'));
                    } else if (playbackStarted === false) {
                      audioPlaybackBlocked = true;
                      setAudioStatus(t('audioAutoplayBlocked'), 'error');
                    }
                  } catch (error) {
                    resetAudioPlayer();
                    await refreshState();
                    setAudioStatus(error && error.message ? error.message : t('audioEnableFailed'), 'error');
                  } finally {
                    setOpeningAudioState(false);
                  }
                }

                async function closeAudioStream() {
                  if (openingAudio) return;
                  openingAudio = true;
                  audioOpenButton.disabled = true;
                  audioOpenButton.textContent = t('closingAudio');
                  audioPlaybackBlocked = false;
                  resetAudioPlayer();
                  try {
                    await fetch('/api/control?audio=false', { cache: 'no-store' });
                  } catch (_) {
                  } finally {
                    openingAudio = false;
                    await refreshState();
                  }
                }

                async function toggleAudioStream() {
                  const state = lastAudioState;
                  if (audioButtonActive(state)) {
                    await closeAudioStream();
                  } else {
                    await openAudioStream();
                  }
                }

                async function setAudioEnabled() {
                  try {
                    if (!audioToggle.checked) {
                      audioPlaybackBlocked = false;
                      resetAudioPlayer();
                    }
                    await fetch('/api/control?audio=' + (audioToggle.checked ? 'true' : 'false'), { cache: 'no-store' });
                    await refreshState();
                  } catch (_) {
                  }
                }

                async function applyManualResolution() {
                  if (!manualWidthInput.value || !manualHeightInput.value) return;
                  const params = new URLSearchParams();
                  params.set('resolution', 'Manual input');
                  params.set('manualWidth', manualWidthInput.value);
                  params.set('manualHeight', manualHeightInput.value);
                  try {
                    await fetch('/api/control?' + params.toString(), { cache: 'no-store' });
                    await refreshState();
                  } catch (_) {
                  }
                }

                async function startStreaming() {
                  if (starting) return;
                  starting = true;
                  streamingToggleButton.textContent = t('starting');
                  streamingToggleButton.disabled = true;
                  try {
                    await fetch('/api/control?streaming=true', { cache: 'no-store' });
                  } catch (_) {
                  } finally {
                    starting = false;
                    await refreshState();
                  }
                }

                async function stopStreaming() {
                  if (stopping) return;
                  stopping = true;
                  streamingToggleButton.textContent = t('stopping');
                  streamingToggleButton.disabled = true;
                  try {
                    await fetch('/api/control?stopStreaming=true&streaming=false', { cache: 'no-store' });
                  } catch (_) {
                  } finally {
                    stopping = false;
                    await refreshState();
                  }
                }

                async function toggleStreaming() {
                  if (starting || stopping) return;
                  if (stateCache.streaming) {
                    await stopStreaming();
                  } else {
                    await startStreaming();
                  }
                }

                function renderVerifiedCameraState(scanState, selectedCameraKey) {
                  const entries = (scanState && scanState.activeEntries) || [];
                  const cachedEntries = (scanState && scanState.cachedEntries) || [];
                  const currentValue = verifiedCameraSelect.value;
                  const selectedValue = entries.some(entry => entry.cameraId === selectedCameraKey)
                    ? selectedCameraKey
                    : (entries.some(entry => entry.cameraId === currentValue) ? currentValue : (entries[0] ? entries[0].cameraId : ''));
                  fillSelect(verifiedCameraSelect, entries.map(entry => ({ value: entry.cameraId, label: entry.label })), selectedValue);
                  verifiedCameraSelect.disabled = entries.length === 0;
                  applyVerifiedCameraButton.disabled = entries.length === 0;
                  clearScanCacheButton.disabled = cachedEntries.length === 0;
                  verifiedCameraList.innerHTML = '';
                  if (entries.length === 0) {
                    const item = document.createElement('div');
                    item.className = 'camera-debug-item';
                    const text = document.createElement('span');
                    text.textContent = t('noVerifiedCameras');
                    item.appendChild(text);
                    verifiedCameraList.appendChild(item);
                  } else {
                    entries.forEach(entry => {
                      const item = document.createElement('div');
                      item.className = 'camera-debug-item';
                      const source = entry.source === 'profile' ? t('sourceProfile') : t('sourceScan');
                      const text = document.createElement('span');
                      text.textContent = entry.label + ' / ' + source + ' / raw ID ' + entry.cameraId +
                        (entry.frames ? ' / frames ' + entry.frames : '');
                      item.appendChild(text);
                      if (entry.source === 'scan') {
                        const deleteButton = document.createElement('button');
                        deleteButton.className = 'button danger';
                        deleteButton.type = 'button';
                        deleteButton.textContent = t('deleteScanEntry');
                        deleteButton.addEventListener('click', () => deleteScanCacheEntry(entry.cameraId));
                        item.appendChild(deleteButton);
                      }
                      verifiedCameraList.appendChild(item);
                    });
                  }
                  const unsafe = (scanState && scanState.unsafeCameraIds) || [];
                  cameraScanStatus.textContent = unsafe.length > 0
                    ? t('verifiedCameraIdle') + ' Unsafe: ' + unsafe.join(', ')
                    : t('verifiedCameraIdle');
                }

                async function deleteScanCacheEntry(cameraId) {
                  try {
                    const response = await fetch('/api/debug/camera-scan-cache/delete?camera=' + encodeURIComponent(cameraId), { cache: 'no-store' });
                    const payload = await response.json();
                    if (payload.state) renderVerifiedCameraState(payload.state, stateCache.selectedCameraKey);
                    cameraScanStatus.textContent = payload.ok ? t('scanCacheCleared') : (payload.error || t('stateUnavailable'));
                    await refreshState();
                  } catch (_) {
                    cameraScanStatus.textContent = t('stateUnavailable');
                  }
                }

                async function clearScanCache() {
                  try {
                    const response = await fetch('/api/debug/camera-scan-cache/delete', { cache: 'no-store' });
                    const payload = await response.json();
                    if (payload.state) renderVerifiedCameraState(payload.state, stateCache.selectedCameraKey);
                    cameraScanStatus.textContent = payload.ok ? t('scanCacheCleared') : (payload.error || t('stateUnavailable'));
                    await refreshState();
                  } catch (_) {
                    cameraScanStatus.textContent = t('stateUnavailable');
                  }
                }

                async function loadVerifiedCameras() {
                  try {
                    const response = await fetch('/api/debug/camera-scan-cache', { cache: 'no-store' });
                    const scanState = await response.json();
                    renderVerifiedCameraState(scanState, stateCache.selectedCameraKey);
                  } catch (_) {
                    cameraScanStatus.textContent = t('stateUnavailable');
                  }
                }

                async function scanCameras() {
                  if (scanningCameras) return;
                  scanningCameras = true;
                  scanCamerasButton.disabled = true;
                  scanCamerasButton.textContent = t('scanningCameras');
                  cameraScanStatus.textContent = t('scanningCameras');
                  try {
                    const response = await fetch('/api/debug/camera-scan', { cache: 'no-store' });
                    const payload = await response.json();
                    if (payload.state) renderVerifiedCameraState(payload.state, stateCache.selectedCameraKey);
                    cameraScanStatus.textContent = payload.ok ? t('scanComplete') : (payload.error || t('stateUnavailable'));
                  } catch (_) {
                    cameraScanStatus.textContent = t('stateUnavailable');
                  } finally {
                    scanningCameras = false;
                    scanCamerasButton.disabled = false;
                    scanCamerasButton.textContent = t('scanCameras');
                    await refreshState();
                  }
                }

                async function applyVerifiedCamera() {
                  if (!verifiedCameraSelect.value) return;
                  try {
                    await fetch('/api/control?camera=' + encodeURIComponent(verifiedCameraSelect.value), { cache: 'no-store' });
                    await refreshState();
                  } catch (_) {
                  }
                }

                function bindEvents() {
                  cameraSelect.addEventListener('change', pushControls);
                  resolutionSelect.addEventListener('change', () => {
                    if (isManualResolutionSelected()) {
                      manualResolutionEditing = true;
                      updateManualResolutionVisibility(true);
                      if (!manualWidthInput.value || !manualHeightInput.value) {
                        const selected = stateCache.selectedResolution || '';
                        const parts = selected.split('x');
                        if (parts.length === 2) {
                          if (!manualWidthInput.value) manualWidthInput.value = parts[0];
                          if (!manualHeightInput.value) manualHeightInput.value = parts[1];
                        }
                      }
                      return;
                    }
                    manualResolutionEditing = false;
                    updateManualResolutionVisibility(false);
                    pushControls();
                  });
                  fpsSelect.addEventListener('change', pushControls);
                  presetButtons.forEach(button => {
                    button.addEventListener('click', () => applyPreset(button.dataset.preset || '1080p30'));
                  });
                  copyH264Button.addEventListener('click', copyH264Url);
                  copyAudioButton.addEventListener('click', copyAudioUrl);
                  audioOpenButton.addEventListener('click', toggleAudioStream);
                  audioToggle.addEventListener('change', setAudioEnabled);
                  applyManualResolutionButton.addEventListener('click', applyManualResolution);
                  streamingToggleButton.addEventListener('click', toggleStreaming);
                  scanCamerasButton.addEventListener('click', scanCameras);
                  refreshVerifiedCamerasButton.addEventListener('click', loadVerifiedCameras);
                  clearScanCacheButton.addEventListener('click', clearScanCache);
                  applyVerifiedCameraButton.addEventListener('click', applyVerifiedCamera);
                  qualitySlider.addEventListener('input', () => {
                    document.getElementById('qualityValue').textContent = qualitySlider.value + '%';
                    scheduleControlPush();
                  });
                  focusModeButtons.forEach(button => {
                    button.addEventListener('click', () => setFocusMode(button.dataset.focusMode || 'auto'));
                  });
                  torchToggle.addEventListener('change', pushControls);
                  videoOverlayToggle.addEventListener('change', () => {
                    updateVideoOverlaySizeVisibility();
                    pushControls();
                  });
                  videoOverlaySizeSlider.addEventListener('input', () => {
                    document.getElementById('videoOverlaySizeValue').textContent = videoOverlaySizeSlider.value;
                    scheduleControlPush();
                  });
                  zoomSlider.addEventListener('input', () => {
                    document.getElementById('zoomValue').textContent = fmt(zoomProgressToValue(Number(zoomSlider.value)), 'x');
                    scheduleControlPush();
                  });
                  focusDistanceSlider.addEventListener('input', () => {
                    document.getElementById('focusDistanceValue').textContent = fmtFocusDistance(Number(focusDistanceSlider.value));
                    scheduleControlPush();
                  });
                  exposureSlider.addEventListener('input', () => {
                    setExposureValueLabel(exposureSlider.value);
                    scheduleControlPush();
                  });
                  previewRotationSlider.addEventListener('input', schedulePreviewTuningPush);
                  previewScaleSlider.addEventListener('input', schedulePreviewTuningPush);
                  previewStretchXSlider.addEventListener('input', schedulePreviewTuningPush);
                  previewStretchYSlider.addEventListener('input', schedulePreviewTuningPush);
                  resetPreviewTuningButton.addEventListener('click', () => {
                    previewRotationSlider.value = '0';
                    previewScaleSlider.value = '100';
                    previewStretchXSlider.value = '100';
                    previewStretchYSlider.value = '100';
                    pushPreviewTuning();
                  });
                }

                async function refreshState() {
                  try {
                    const response = await fetch('/api/state', { cache: 'no-store' });
                    const state = await response.json();
                    applyingRemoteState = true;

                    stateCache.streaming = !!state.streaming;
                    stateCache.cameraOptions = state.cameraOptions || [];
                    stateCache.resolutionOptions = state.resolutionOptions || [];
                    stateCache.fpsOptions = state.fpsOptions || [];
                    stateCache.selectedCameraKey = state.selectedCameraKey || '';
                    stateCache.rtspUrl = state.rtspUrl || '';
                    stateCache.rtspAvailable = !!state.rtspAvailable;
                    stateCache.zoomMin = state.zoomMin ?? 1;
                    stateCache.zoomMax = state.zoomMax ?? 1;
                    stateCache.focusDistanceMin = state.focusDistanceMin ?? 0;
                    stateCache.focusDistanceMax = state.focusDistanceMax ?? 0;
                    stateCache.focusSupported = !!state.focusSupported;
                    stateCache.videoOverlaySizeMin = state.videoOverlaySizeMin ?? 1;
                    stateCache.videoOverlaySizeMax = state.videoOverlaySizeMax ?? 100;
                    stateCache.exposureMin = state.exposureMin ?? 0;
                    stateCache.exposureMax = state.exposureMax ?? 0;

                    fillSelect(cameraSelect, stateCache.cameraOptions, state.selectedCameraKey);
                    renderCameraChips(stateCache.cameraOptions, state.selectedCameraKey);
                    const resolutionItems = [...stateCache.resolutionOptions];
                    if (!resolutionItems.includes('Manual input')) resolutionItems.push('Manual input');
                    const keepManualOpen = manualResolutionEditing || state.manualResolutionEnabled;
                    fillSelect(
                      resolutionSelect,
                      resolutionItems,
                      keepManualOpen ? 'Manual input' : state.selectedResolution,
                      item => item === 'Manual input' ? t('manualInput') : String(item)
                    );
                    updateManualResolutionVisibility(keepManualOpen);
                    if (!manualResolutionEditing || state.manualResolutionEnabled) {
                      manualWidthInput.value = state.manualResolutionWidth != null ? String(state.manualResolutionWidth) : '';
                      manualHeightInput.value = state.manualResolutionHeight != null ? String(state.manualResolutionHeight) : '';
                    }
                    fillSelect(
                      fpsSelect,
                      fpsOptionItems(state),
                      state.unlimitedFpsSelected ? unlimitedFpsValue : state.selectedFps
                    );
                    qualitySlider.min = String(state.qualityMin ?? 30);
                    qualitySlider.max = String(state.qualityMax ?? 95);
                    qualitySlider.step = '5';
                    qualitySlider.value = String(state.qualityValue ?? 60);
                    document.getElementById('qualityValue').textContent = qualitySlider.value + '%';
                    updatePresetButtonStates(state);
                    focusModeSelect.value = state.focusMode || 'auto';
                    focusModeSelect.disabled = !state.focusSupported;
                    updateFocusModeButtons();
                    updateFocusDistanceVisibility();
                    torchToggle.disabled = !state.torchSupported;
                    torchToggle.checked = !!state.torchEnabled;
                    videoOverlayToggle.checked = !!state.videoOverlayEnabled;
                    videoOverlaySizeSlider.min = String(stateCache.videoOverlaySizeMin);
                    videoOverlaySizeSlider.max = String(stateCache.videoOverlaySizeMax);
                    videoOverlaySizeSlider.step = '1';
                    videoOverlaySizeSlider.value = String(state.videoOverlaySizeValue ?? 25);
                    document.getElementById('videoOverlaySizeValue').textContent = videoOverlaySizeSlider.value;
                    updateVideoOverlaySizeVisibility();
                    versionText.textContent = state.versionLabel || '${versionLabel}';

                    const selectedCamera = stateCache.cameraOptions.find(item => item.value === state.selectedCameraKey);
                    const selectedCameraLabel = state.selectedCameraLabel || (selectedCamera ? selectedCamera.label : t('cameraFallback'));
                    const summary = buildCameraSummary(state, selectedCameraLabel);
                    document.getElementById('statusText').textContent = summary;
                    setBadge(streamBadge, state.streaming ? t('streaming') : t('idle'), state.streaming ? '' : 'idle');
                    setBadge(stateBadge, t('stateOk'), '');
                    streamingToggleButton.disabled = starting || stopping;
                    streamingToggleButton.textContent = starting ? t('starting') : (stopping ? t('stopping') : (state.streaming ? t('stopStreaming') : t('startStreaming')));
                    streamingToggleButton.classList.toggle('primary', !state.streaming);
                    streamingToggleButton.classList.toggle('danger', !!state.streaming);
                    setPreviewStreaming(!!state.streaming);
                    updateAudioState(state);

                    zoomSlider.disabled = !state.zoomSupported;
                    if (state.zoomSupported && typeof state.zoomValue === 'number') {
                      zoomSlider.value = valueToZoomProgress(state.zoomValue);
                      document.getElementById('zoomValue').textContent = fmt(state.zoomValue, 'x');
                    } else {
                      document.getElementById('zoomValue').textContent = fmt(1, 'x');
                    }

                    const showFocusDistance = updateFocusDistanceVisibility();
                    if (showFocusDistance && state.focusDistanceSupported) {
                      focusDistanceSlider.min = String(state.focusDistanceMin ?? 0);
                      focusDistanceSlider.max = String(state.focusDistanceMax ?? 0);
                      focusDistanceSlider.step = '0.001';
                      focusDistanceSlider.value = String(state.focusDistanceValue ?? 0);
                      document.getElementById('focusDistanceHint').textContent = t('focusDistanceRange');
                      document.getElementById('focusDistanceValue').textContent = fmtFocusDistance(Number(state.focusDistanceValue ?? 0));
                    } else {
                      document.getElementById('focusDistanceHint').textContent = t('manualFocusAvailable');
                      document.getElementById('focusDistanceValue').textContent = t('infinity');
                    }

                    exposureSlider.disabled = !state.exposureSupported;
                    if (state.exposureSupported) {
                      exposureSlider.min = state.exposureMin;
                      exposureSlider.max = state.exposureMax;
                      exposureSlider.step = 1;
                      exposureSlider.value = state.exposureValue ?? 0;
                      setExposureValueLabel(state.exposureValue ?? 0);
                    } else {
                      setExposureValueLabel(null);
                    }
                    previewRotationSlider.value = String(state.previewRotation ?? 0);
                    previewScaleSlider.value = String(Math.round((state.previewScale ?? 1) * 100));
                    previewStretchXSlider.value = String(Math.round((state.previewStretchX ?? 1) * 100));
                    previewStretchYSlider.value = String(Math.round((state.previewStretchY ?? 1) * 100));
                    setPreviewTuningLabels();
                  } catch (_) {
                    setBadge(streamBadge, t('stateUnavailable'), 'error');
                    setBadge(stateBadge, t('unavailableShort'), 'error');
                  } finally {
                    applyingRemoteState = false;
                  }
                }

                bindEvents();
                applyLanguage();
                loadVerifiedCameras();
                refreshState();
                setInterval(refreshState, 1000);
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    fun renderShell(versionLabel: String): String {
        val currentAssets = assets(versionLabel)
        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>全镜直播(LensCast)</title>
            </head>
            <body>
              <div id="app">Loading…</div>
              <script>
                (async function () {
                  const versionLabel = ${quoteJs(versionLabel)};
                  const bodyChunkCount = ${currentAssets.bodyChunks.size};
                  const cssChunkCount = ${currentAssets.cssChunks.size};
                  const jsChunkCount = ${currentAssets.jsChunks.size};
                  const retryTimeoutsMs = [1500, 3000, 6000];
                  const app = document.getElementById('app');

                  const fetchChunkWithRetry = async (basePath, index) => {
                    let lastError = null;
                    for (let attempt = 0; attempt < retryTimeoutsMs.length; attempt++) {
                      const timeoutMs = retryTimeoutsMs[attempt];
                      const supportsAbortController = typeof AbortController === 'function';
                      const controller = supportsAbortController ? new AbortController() : null;
                      const timer = supportsAbortController ? setTimeout(() => controller.abort(), timeoutMs) : null;
                      try {
                        const cacheBust = 'v=' + encodeURIComponent(versionLabel) + '&i=' + index + '&a=' + attempt + '&t=' + Date.now();
                        const requestPath = basePath + '/' + index + '?' + cacheBust;
                        const response = supportsAbortController
                          ? await fetch(requestPath, { cache: 'no-store', signal: controller.signal })
                          : await fetch(requestPath, { cache: 'no-store' });
                        if (!response.ok) {
                          throw new Error('HTTP ' + response.status + ' ' + basePath + '/' + index);
                        }
                        return await response.text();
                      } catch (error) {
                        lastError = error;
                      } finally {
                        if (timer != null) clearTimeout(timer);
                      }
                    }
                    throw lastError || new Error('Failed to load ' + basePath + '/' + index);
                  };

                  const loadChunks = async (basePath, count) => {
                    if (count <= 0) throw new Error('No chunks for ' + basePath);
                    const requests = Array.from({ length: count }, (_, index) => fetchChunkWithRetry(basePath, index));
                    const parts = await Promise.all(requests);
                    return parts.join('');
                  };

                  try {
                    const body = await loadChunks('/assets/dashboard.body', bodyChunkCount);
                    app.innerHTML = body;
                    const cssText = await loadChunks('/assets/dashboard.css', cssChunkCount);
                    const style = document.createElement('style');
                    style.textContent = cssText;
                    document.head.appendChild(style);
                    const scriptText = await loadChunks('/assets/dashboard.js', jsChunkCount);
                    (0, eval)(scriptText);
                  } catch (error) {
                    app.textContent = 'Web UI load failed: ' + (error && error.message ? error.message : String(error));
                  }
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    fun bodyChunk(index: Int, versionLabel: String): String? {
        return assets(versionLabel).bodyChunks.getOrNull(index)
    }

    fun cssChunk(index: Int, versionLabel: String): String? {
        return assets(versionLabel).cssChunks.getOrNull(index)
    }

    fun jsChunk(index: Int, versionLabel: String): String? {
        return assets(versionLabel).jsChunks.getOrNull(index)
    }

    private data class DashboardAssets(
        val bodyChunks: List<String>,
        val cssChunks: List<String>,
        val jsChunks: List<String>
    )

    @Volatile
    private var cachedVersionLabel: String? = null

    @Volatile
    private var cachedAssets: DashboardAssets? = null

    private fun assets(versionLabel: String): DashboardAssets {
        val cached = cachedAssets
        if (cached != null && cachedVersionLabel == versionLabel) return cached
        return synchronized(this) {
            val insideLockCached = cachedAssets
            if (insideLockCached != null && cachedVersionLabel == versionLabel) {
                insideLockCached
            } else {
                val rebuilt = extractAssets(versionLabel)
                cachedVersionLabel = versionLabel
                cachedAssets = rebuilt
                rebuilt
            }
        }
    }

    private fun extractAssets(versionLabel: String): DashboardAssets {
        val html = render(versionLabel)
        val style = between(html, "<style>", "</style>").trim()
        val script = between(html, "<script>", "</script>").trim()
        val body = between(html, "<body>", "<script>").trim()
        return DashboardAssets(
            bodyChunks = splitChunks(body, ASSET_CHUNK_BYTES),
            cssChunks = splitChunks(style, ASSET_CHUNK_BYTES),
            jsChunks = splitChunks(script, ASSET_CHUNK_BYTES)
        )
    }

    private fun between(source: String, startToken: String, endToken: String): String {
        val start = source.indexOf(startToken)
        if (start < 0) return ""
        val from = start + startToken.length
        val end = source.indexOf(endToken, from)
        if (end < 0 || end < from) return ""
        return source.substring(from, end)
    }

    private fun splitChunks(text: String, chunkSize: Int): List<String> {
        if (text.isEmpty()) return emptyList()
        val chunks = ArrayList<String>((text.length + chunkSize - 1) / chunkSize)
        var offset = 0
        while (offset < text.length) {
            val end = minOf(offset + chunkSize, text.length)
            chunks += text.substring(offset, end)
            offset = end
        }
        return chunks
    }

    private fun quoteJs(value: String): String {
        return buildString(value.length + 2) {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\r' -> append("\\r")
                    '\n' -> append("\\n")
                    else -> append(ch)
                }
            }
            append('"')
        }
    }

    private const val ASSET_CHUNK_BYTES = 4 * 1024
}
