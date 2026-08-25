#!/usr/bin/env python3
"""myCCTV cloud relay — in-memory frame forwarder (no disk storage).

Phone posts JPEG frames outbound. Dashboard (e.g. HostingRaja HTTPS) pulls
live frames / MJPEG. Optional control commands are queued for the phone.

Env:
  MYCCTV_HTTP_PORT   default 8090
  MYCCTV_RELAY_TOKEN shared secret (required for write; recommended for read)
  CORS_ORIGINS       comma-separated origins, or * (default *)
"""

from __future__ import annotations

import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

from typing import Optional

PORT = int(os.environ.get("MYCCTV_HTTP_PORT", "8090"))
RELAY_TOKEN = os.environ.get("MYCCTV_RELAY_TOKEN", "").strip()
CORS_ORIGINS = os.environ.get("CORS_ORIGINS", "*").strip() or "*"
MAX_FRAME_BYTES = 2_500_000
DEVICE_TTL_SEC = 90
MAX_COMMANDS = 32

_lock = threading.Lock()
# device_id -> dict
_devices = {}


def now():
    return time.time()


def cors_headers(handler):
    origin = handler.headers.get("Origin", "")
    if CORS_ORIGINS == "*":
        allow = "*"
    else:
        allowed = [o.strip() for o in CORS_ORIGINS.split(",") if o.strip()]
        allow = origin if origin in allowed else (allowed[0] if allowed else "*")
    return [
        ("Access-Control-Allow-Origin", allow),
        ("Access-Control-Allow-Methods", "GET, POST, OPTIONS"),
        ("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Device-Id, X-Relay-Token, X-Device-Name"),
        ("Access-Control-Max-Age", "86400"),
    ]


def _bearer_token(header_value):
    if not header_value:
        return ""
    value = header_value.strip()
    if value.lower().startswith("bearer "):
        return value[7:].strip()
    return value


def token_ok(handler, qs, require):
    if not RELAY_TOKEN:
        return not require
    provided = (
        handler.headers.get("X-Relay-Token")
        or _bearer_token(handler.headers.get("Authorization", ""))
        or (qs.get("token") or [""])[0]
    )
    return provided == RELAY_TOKEN


def get_device(device_id):
    with _lock:
        device = _devices.get(device_id)
        if not device:
            return None
        if now() - device["last_seen"] > DEVICE_TTL_SEC:
            return None
        return device


def touch_device(device_id, name=None):
    with _lock:
        device = _devices.get(device_id)
        if device is None:
            device = {
                "id": device_id,
                "name": name or device_id,
                "last_seen": now(),
                "frame": None,
                "frame_ts": 0.0,
                "frame_seq": 0,
                "streaming": False,
                "commands": [],
            }
            _devices[device_id] = device
        device["last_seen"] = now()
        if name:
            device["name"] = name
        return device


def list_devices():
    cutoff = now() - DEVICE_TTL_SEC
    with _lock:
        stale = [k for k, v in _devices.items() if v["last_seen"] < cutoff]
        for k in stale:
            _devices.pop(k, None)
        return [
            {
                "id": d["id"],
                "name": d["name"],
                "streaming": bool(d["streaming"]),
                "lastSeenAgeMs": int((now() - d["last_seen"]) * 1000),
                "hasFrame": d["frame"] is not None,
                "frameAgeMs": int((now() - d["frame_ts"]) * 1000) if d["frame_ts"] else None,
            }
            for d in _devices.values()
            if d["last_seen"] >= cutoff
        ]


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt: str, *args) -> None:
        print(f"[relay] {self.address_string()} {fmt % args}")

    def _write(self, code: int, body: bytes, content_type: str, extra: list[tuple[str, str]] | None = None) -> None:
        self.send_response(code)
        for k, v in cors_headers(self):
            self.send_header(k, v)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        if extra:
            for k, v in extra:
                self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def _json(self, code: int, payload: dict | list) -> None:
        self._write(code, json.dumps(payload).encode("utf-8"), "application/json; charset=utf-8")

    def do_OPTIONS(self) -> None:
        self.send_response(204)
        for k, v in cors_headers(self):
            self.send_header(k, v)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        qs = parse_qs(parsed.query)

        if path in ("/", "/health"):
            self._json(200, {"ok": True, "service": "mycctv-relay", "devices": len(list_devices())})
            return

        if path == "/api/devices":
            if not token_ok(self, qs, require=bool(RELAY_TOKEN)):
                self._json(401, {"ok": False, "error": "unauthorized"})
                return
            self._json(200, {"ok": True, "devices": list_devices()})
            return

        parts = path.strip("/").split("/")
        # /api/devices/{id}/frame.jpg | mjpeg | state | commands
        if len(parts) == 4 and parts[0] == "api" and parts[1] == "devices":
            device_id = parts[2]
            action = parts[3]
            if action in ("frame.jpg", "mjpeg", "state") and not token_ok(self, qs, require=bool(RELAY_TOKEN)):
                self._json(401, {"ok": False, "error": "unauthorized"})
                return
            if action == "commands":
                if not token_ok(self, qs, require=True):
                    self._json(401, {"ok": False, "error": "unauthorized"})
                    return
                device = get_device(device_id) or touch_device(device_id)
                with _lock:
                    commands = list(device["commands"])
                    device["commands"].clear()
                    device["last_seen"] = now()
                self._json(200, {"ok": True, "commands": commands})
                return
            device = get_device(device_id)
            if device is None:
                self._json(404, {"ok": False, "error": "device offline or unknown"})
                return
            if action == "state":
                self._json(
                    200,
                    {
                        "ok": True,
                        "id": device["id"],
                        "name": device["name"],
                        "streaming": device["streaming"],
                        "hasFrame": device["frame"] is not None,
                        "frameAgeMs": int((now() - device["frame_ts"]) * 1000) if device["frame_ts"] else None,
                        "lastSeenAgeMs": int((now() - device["last_seen"]) * 1000),
                    },
                )
                return
            if action == "frame.jpg":
                frame = device["frame"]
                if not frame:
                    self._json(503, {"ok": False, "error": "no frame yet"})
                    return
                self._write(200, frame, "image/jpeg")
                return
            if action == "mjpeg":
                self._stream_mjpeg(device_id)
                return

        self._json(404, {"ok": False, "error": "not found"})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        qs = parse_qs(parsed.query)
        length = int(self.headers.get("Content-Length", "0") or 0)
        if length > MAX_FRAME_BYTES:
            self._json(413, {"ok": False, "error": "payload too large"})
            return
        body = self.rfile.read(length) if length > 0 else b""

        if path == "/api/register":
            if not token_ok(self, qs, require=True):
                self._json(401, {"ok": False, "error": "unauthorized"})
                return
            try:
                payload = json.loads(body.decode("utf-8") or "{}")
            except json.JSONDecodeError:
                self._json(400, {"ok": False, "error": "invalid json"})
                return
            device_id = str(payload.get("deviceId") or payload.get("id") or "").strip()
            name = str(payload.get("name") or device_id).strip() or device_id
            if not device_id:
                self._json(400, {"ok": False, "error": "deviceId required"})
                return
            touch_device(device_id, name)
            self._json(200, {"ok": True, "deviceId": device_id, "name": name})
            return

        parts = path.strip("/").split("/")
        if len(parts) == 4 and parts[0] == "api" and parts[1] == "devices":
            device_id = parts[2]
            action = parts[3]
            if not token_ok(self, qs, require=True):
                self._json(401, {"ok": False, "error": "unauthorized"})
                return
            if action == "frame":
                content_type = (self.headers.get("Content-Type") or "").lower()
                frame = body
                if "application/json" in content_type:
                    try:
                        payload = json.loads(body.decode("utf-8"))
                        import base64

                        frame = base64.b64decode(payload.get("jpegBase64") or payload.get("jpeg") or "")
                    except Exception:
                        self._json(400, {"ok": False, "error": "invalid frame json"})
                        return
                if not frame or frame[:2] != b"\xff\xd8":
                    self._json(400, {"ok": False, "error": "expected jpeg body"})
                    return
                name = self.headers.get("X-Device-Name")
                streaming = (qs.get("streaming") or ["1"])[0] not in ("0", "false", "False")
                device = touch_device(device_id, name)
                with _lock:
                    device["frame"] = frame
                    device["frame_ts"] = now()
                    device["frame_seq"] = int(device["frame_seq"]) + 1
                    device["streaming"] = streaming
                self._json(200, {"ok": True, "bytes": len(frame), "seq": device["frame_seq"]})
                return
            if action == "control":
                try:
                    payload = json.loads(body.decode("utf-8") or "{}") if body else {}
                except json.JSONDecodeError:
                    payload = {}
                # Also accept query params as control fields
                for key, values in qs.items():
                    if key == "token":
                        continue
                    if values:
                        payload.setdefault(key, values[0])
                if not payload:
                    self._json(400, {"ok": False, "error": "empty control"})
                    return
                device = touch_device(device_id)
                with _lock:
                    device["commands"].append({"ts": now(), "params": payload})
                    if len(device["commands"]) > MAX_COMMANDS:
                        device["commands"] = device["commands"][-MAX_COMMANDS:]
                self._json(200, {"ok": True, "queued": True})
                return
            if action == "heartbeat":
                name = None
                streaming = None
                if body:
                    try:
                        payload = json.loads(body.decode("utf-8"))
                        name = payload.get("name")
                        streaming = payload.get("streaming")
                    except json.JSONDecodeError:
                        pass
                device = touch_device(device_id, name)
                if streaming is not None:
                    with _lock:
                        device["streaming"] = bool(streaming)
                self._json(200, {"ok": True})
                return

        self._json(404, {"ok": False, "error": "not found"})

    def _stream_mjpeg(self, device_id: str) -> None:
        boundary = "mycctvframe"
        self.send_response(200)
        for k, v in cors_headers(self):
            self.send_header(k, v)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Pragma", "no-cache")
        self.send_header("Content-Type", f"multipart/x-mixed-replace; boundary={boundary}")
        self.send_header("Connection", "close")
        self.end_headers()
        last_seq = -1
        idle_rounds = 0
        try:
            while True:
                device = get_device(device_id)
                if device is None:
                    break
                frame = None
                seq = last_seq
                with _lock:
                    seq = int(device["frame_seq"])
                    if seq != last_seq and device["frame"] is not None:
                        frame = device["frame"]
                if frame is not None:
                    last_seq = seq
                    idle_rounds = 0
                    header = (
                        f"--{boundary}\r\n"
                        f"Content-Type: image/jpeg\r\n"
                        f"Content-Length: {len(frame)}\r\n"
                        f"X-Frame-Seq: {seq}\r\n\r\n"
                    ).encode("utf-8")
                    self.wfile.write(header)
                    self.wfile.write(frame)
                    self.wfile.write(b"\r\n")
                    self.wfile.flush()
                else:
                    idle_rounds += 1
                    if idle_rounds > 300:  # ~60s with 0.2s sleep
                        break
                time.sleep(0.2)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass


def main() -> None:
    if not RELAY_TOKEN:
        print("[relay] WARNING: MYCCTV_RELAY_TOKEN is empty — set a shared secret before production use")
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"[relay] myCCTV relay listening on http://0.0.0.0:{PORT}")
    server.serve_forever()


if __name__ == "__main__":
    main()
