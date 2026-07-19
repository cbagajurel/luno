# Luno — Test Backend

A **reference Luno-protocol server** for demoing the Android node's pairing and
live connection. It is now a **thin adapter over [`@luno/core`](../packages/core)**:
all pairing, enrolment, session-handshake and messaging logic lives in the SDK,
and this app only translates HTTP and WebSocket to SDK calls. That is the point of
the port — the same engine runs behind any framework, and this Next.js app is one
~40-line-per-route example of it.

- `POST /enroll` and `/enroll/status` hand the request straight to
  `luno.http.handle` (a Next `Request` is already fetch-native).
- `WS /ws` authorises with `luno.connections.authorize` and pumps frames through
  `luno.connections.open` (see [`lib/ws.mjs`](lib/ws.mjs)).
- The dashboard routes call `luno.pairing` / `luno.sms` / `luno.devices`.
- [`lib/luno.mjs`](lib/luno.mjs) is the whole projection layer: it reads SDK state
  and caches the two things the SDK emits but doesn't store (last heartbeat and
  status per device) to build the dashboard snapshot.

It is **not** the production backend — it uses the in-memory `memoryStore()` and a
per-process secret, so restarting drops all devices and codes. It exists to prove
the node (and the SDK) work end-to-end and to give you a dashboard to drive them.

> **Stack note:** this is a Next.js app with a **custom Node server** (`server.mjs`)
> because the node needs a long-lived `wss://` socket. That runs on Railway,
> Render, Fly.io, or any VPS. It does **not** run on Vercel serverless — Vercel
> can't hold an open WebSocket.

## Run locally

This app is part of the pnpm workspace, so install from the repo root (it links
the `@luno/*` packages):

```bash
pnpm install                       # from the repo root, once
pnpm --filter luno-test-backend dev   # http://localhost:3000
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

The platform provides `$PORT` and public `https`/`wss`. When the enrol response
omits `wsUrl` (this adapter does not set one), the node derives `wss://<host>/ws`
from the enrolment host. Set `LUNO_SECRET` to a durable value so issued codes and
credentials survive a restart — without it the adapter generates a fresh secret
each boot and everything paired before the restart must re-enrol.

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
