# myCCTV Remote Dashboard

Single-file dashboard for **LAN** phones and **Cloud relay** (HostingRaja HTTPS + Interserver).

## HostingRaja (static)

Upload `index.html` to your site root (or `/mycctv/`).

Use **Cloud relay** mode on the hosted HTTPS page (LAN mode from HTTPS is blocked by the browser).

## Cloud relay flow

1. Deploy `cloud-relay/` on Interserver (see that folder’s README).
2. On the phone: Network card → enter `https://your-relay-host`, token, enable **Relay**, Start live.
3. Copy the phone **Device id**.
4. On HostingRaja dashboard: Cloud relay → same URL + token → paste device id → Add → Connect.

Photos download to the PC only (relay keeps frames in memory, no disk storage).

## LAN mode

Open this file locally (`file://`) or over HTTP on the same Wi-Fi. Add phone IP `:41737`.
