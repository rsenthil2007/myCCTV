# myCCTV cloud relay (Interserver)

In-memory JPEG forwarder. **No image/video files stored on disk.**

```text
Phone (myCCTV) --HTTPS POST frames--> Interserver relay
HostingRaja dashboard --HTTPS GET mjpeg/frame--> Interserver relay
```

## Deploy on Interserver (Ubuntu VPS)

1. Copy this folder to the VPS:

```bash
sudo mkdir -p /opt/mycctv-relay
# scp server.py + deploy/* to the server, then:
sudo cp server.py /opt/mycctv-relay/
sudo cp deploy/mycctv-relay.service /etc/systemd/system/
```

2. Edit the shared secret in the service file:

```bash
sudo nano /etc/systemd/system/mycctv-relay.service
# set MYCCTV_RELAY_TOKEN=your-long-secret
# optionally CORS_ORIGINS=https://your-hostingraja-site
```

3. Start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now mycctv-relay
sudo systemctl status mycctv-relay
```

4. Put nginx in front + HTTPS (Certbot):

```bash
sudo cp deploy/nginx-mycctv-relay.conf /etc/nginx/sites-available/mycctv-relay
# edit server_name
sudo ln -sf /etc/nginx/sites-available/mycctv-relay /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d cctv.yourdomain.com
```

5. Smoke test:

```bash
curl -s https://cctv.yourdomain.com/health
```

## API (MVP)

| Method | Path | Who | Purpose |
|--------|------|-----|---------|
| POST | `/api/register` | phone | register device `{deviceId,name}` |
| POST | `/api/devices/{id}/frame` | phone | raw JPEG body (or JSON base64) |
| GET | `/api/devices/{id}/commands` | phone | pull queued controls |
| GET | `/api/devices` | dashboard | list online devices |
| GET | `/api/devices/{id}/mjpeg` | dashboard | live multipart JPEG |
| GET | `/api/devices/{id}/frame.jpg` | dashboard | latest still |
| POST | `/api/devices/{id}/control` | dashboard | queue command JSON/query |

Auth: header `X-Relay-Token: SECRET` or `?token=SECRET`.

## HostingRaja dashboard

Open the hosted `index.html`, set **Relay URL** to `https://cctv.yourdomain.com` and the same token, then add the phone’s **device id**.

## Local test

```bash
python3 server.py
# MYCCTV_RELAY_TOKEN=dev python3 server.py
```
