# Luno — Test Backend

A **reference Luno-protocol server** for demoing the Android node's pairing and
live connection. It speaks exactly the wire contract the node implements
(`backend/protocol`, `backend/auth`, `backend/ws`): `POST /enroll` for pairing and
a WebSocket at `/ws` running the §6 handshake, at-least-once event acks, and the
backend→node command set.

It is **not** the production backend — it keeps everything in memory and mints
credentials with no real trust store. It exists to prove the node works
end-to-end and to give you a dashboard to drive it.

> **Stack note:** this is a Next.js app with a **custom Node server** (`server.mjs`)
> because the node needs a long-lived `wss://` socket. That runs on Railway,
> Render, Fly.io, or any VPS. It does **not** run on Vercel serverless — Vercel
> can't hold an open WebSocket.

## Run locally

```bash
cd test-backend
npm install
npm run dev          # http://localhost:3000
```

Open http://localhost:3000 and click **Generate pairing code**.

### Try it without a phone

A simulator that behaves like the real node (enroll → handshake → heartbeats →
answers commands with the real event flow):

```bash
node scripts/fake-node.mjs http://localhost:3000 <pairing-code>
```

Watch the dashboard: the device appears, reaches **READY**, and streams
heartbeats. Send an SMS, hit `get_status`, or `revoke` — every frame shows up
live in the protocol feed.

### Pair the real Android node locally

The node requires **https** to enroll and **wss** for the socket, so put a TLS
tunnel in front:

```bash
cloudflared tunnel --url http://localhost:3000
#   or:  ngrok http 3000
```

In the app's pairing screen enter the tunnel's `https://…` URL as the **Backend
URL** plus a pairing code from the dashboard.

## Deploy (Railway / Render / Fly)

Any platform that runs a persistent Node process and terminates TLS works. Build
and start commands:

```
build:  npm install && npm run build
start:  npm start
```

The platform provides `$PORT` and public `https`/`wss`. The `/enroll` response
derives the WebSocket URL from the request host (`wss://<host>/ws`); override it
with `PUBLIC_WS_URL` if needed.

## Protocol surface

| Path            | Who      | Purpose                                                        |
| --------------- | -------- | ------------------------------------------------------------- |
| `POST /enroll`  | node     | Pairing: `{pairingCode, deviceInfo}` → `{deviceId, credential, wsUrl}` |
| `WS /ws`        | node     | `Authorization: Bearer <credential>`; §6 handshake then frames |
| `POST /api/pairing` | dashboard | mint a short-lived pairing code                           |
| `GET /api/stream`   | dashboard | SSE mirror of all devices + the live frame feed          |
| `POST /api/devices/:id/command` | dashboard | issue `send_sms` / `get_status` / `config_update` / `revoke` / `wipe` |

### Handshake (mirrors `ConnectionManager`)

1. socket opens → node sends `version_negotiate` → backend replies `version_negotiate` → node **AUTHENTICATED**
2. node sends `resync` → backend replies `ack` → node **READY**
3. node streams `heartbeat` / `sms_received` / `sms_accepted` / `sms_sent` / `delivery_report` / `device_status`; backend acks each by frame id

Everything is in-memory: restarting the server drops all devices and codes.
