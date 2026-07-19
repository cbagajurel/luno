# Luno — Architecture

> Companion to [`../plan.md`](../plan.md). This is the "how it fits together"
> document: responsibilities, every lifecycle and flow, and the wire protocol.

## 0. The mental model

```
┌──────────────────────────────────────────────────────────────────────┐
│        CONTROL PLANE  (any Luno-protocol backend, out of scope)        │
│  device registry · auth/pairing · command dispatch · event ingestion  │
│  rate-limit policy · dashboards for many nodes                         │
└───────────────▲───────────────────────────────────────────▲──────────┘
                │ WSS (primary)                              │ HTTPS (fallback)
                │ versioned wire protocol (§8)               │
┌───────────────┴────────────────────────────────────────────────────────┐
│  ANDROID NODE  (this repo)                                               │
│                                                                         │
│  ┌───────────────── Native Kotlin agent (headless-capable) ──────────┐  │
│  │  GatewayForegroundService  ── owns the process lifetime           │  │
│  │    AgentController         ── orchestrates everything below        │  │
│  │      backend/  WebSocketClient · ProtocolCodec · Heartbeat · auth  │  │
│  │      transport/  Transport interface → SmsTransport (send/receive) │  │
│  │      telephony/  Sim · Signal · Battery · Network monitors         │  │
│  │      data/  Room outbox/inbox/reports  + repositories  (durable)   │  │
│  │      work/  BootReceiver · WorkManager retry/reconnect (safety net)│  │
│  └────────────────────────────▲──────────────────────────────────────┘  │
│                               │ Pigeon HostApi (commands/queries)         │
│                               │ EventChannel (live streams)               │
│  ┌────────────────────────────┴──────────────────────────────────────┐  │
│  │  Flutter UI  (control panel; renders native state, issues commands)│  │
│  │    pairing · dashboard · logs · settings                          │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                │ Android Telephony APIs (SmsManager, TelephonyManager, …)
                ▼
          SIM / Radio / Carrier
```

Two hard boundaries define the whole system:

- **Native ↔ Flutter** is Pigeon + EventChannel. Crossing it: commands and
  rendered state. *Not* crossing it: sockets, queues, telephony, retries.
- **Node ↔ Backend** is the versioned wire protocol (§8). This is the real API
  and the real extension seam. A future Linux modem node is a different program
  that speaks this same protocol.

---

## 1. Responsibility split (who owns what)

### 1.1 Flutter (Dart) — **UI and only UI**
Owns: pairing/enrollment screens, the live dashboard, the log viewer, settings,
the connection indicator, and *ephemeral* UI state (which tab is open, form
input). Issues commands to native (`sendTestSms`, `startPairing`,
`requestPermission`, `openBatterySettings`) and renders what native streams
back.

Explicitly does **not** own: the WebSocket, the message queue, retry/backoff,
delivery correlation, telephony, credentials, or any state that must outlive the
Activity. If the Dart process is killed, the gateway keeps running unaffected —
that is the acceptance test for this boundary.

### 1.2 Native Android (Kotlin) — **the entire agent**
Owns everything load-bearing:
- **Process lifetime:** `GatewayForegroundService` + `BootReceiver`.
- **Backend connection:** `WebSocketClient`, protocol codec, heartbeat, reconnect,
  REST fallback, credential store, pairing.
- **Transports:** `Transport` interface with `SmsTransport` (send + receive)
  as the v1 implementation; multi-SIM aware.
- **Telemetry:** SIM/signal/battery/network monitors.
- **Durability:** Room outbox/inbox/report tables + repositories; the message
  state machines; idempotency and dedupe.
- **Resilience:** WorkManager retry/reconnect, offline queueing, resync on
  reconnect.
- **Security:** Keystore, encryption at rest, redaction, rate-limit enforcement.

### 1.3 Backend / control plane (any Luno-protocol server — not this repo) — **fleet brain**
Owns: the device registry and identity, pairing/enrollment and credential
issuance/rotation, command origination (`send_sms`), event ingestion and
persistence, **authoritative rate-limit and recipient policy**, and multi-node
dashboards. It is the source of truth for *what the fleet should do*; the node
is the source of truth for *what physically happened on the radio*.

This repo does not implement the backend, but it defines the contract it must
honor (§8) so the two can be built independently.

### 1.4 The backend is out of scope; the contract is the boundary
The node does not know or care how the control plane is built. It may be
Firebase, Supabase, an Express / Next / Nest app, a Go service, or a shell
script behind a reverse proxy — the node depends only on the versioned wire
protocol (§8). Rules we hold ourselves to:
- **No backend stack is named or assumed anywhere in the node.** "NestJS",
  "Firebase", etc. never appear in the node's code, config, or branching logic.
- **Backend endpoints are runtime configuration** (set during pairing), never
  compiled in. Point a node at any conformant server and it works.
- **The contract is node-owned and versioned.** We publish it (§8) so any
  backend author can implement it without reading the Android source.

The node's entire responsibility reduces to: *receive a command → act on the
radio → report faithfully what happened.* Everything past the socket is someone
else's problem.

---

## 2. Communication flow (end to end)

**Outbound (backend wants an SMS sent):**
```
Backend ──command:send_sms(id=C1)──▶ WebSocketClient
  → ProtocolCodec decodes → AgentController
  → OutboxRepository.enqueue(C1)         [PERSIST FIRST]  ── idempotent on C1
  → ack(C1 accepted) ──────────────────▶ Backend
  → SmsTransport.send(part…) via SmsManager (per subId)
  → sentIntent fires  → outbox: QUEUED→SENT/FAILED  → event:sms_sent ▶ Backend
  → deliveryIntent fires → outbox: SENT→DELIVERED/UNDELIVERED
                         → event:delivery_report ▶ Backend
  → on backend ack of each event → mark event acked (stop resending)
```

**Inbound (a message arrives at the SIM):**
```
SMS_RECEIVED broadcast → SmsReceiver.goAsync()
  → hand PDUs to service → MultipartAssembler
  → InboxRepository.insert(RECEIVED)     [PERSIST FIRST]
  → event:sms_received(id=E7) ─────────▶ Backend
  → Backend ack(E7) → inbox: mark reported/acked
```

The invariant in both directions: **the durable store is written before the
network is trusted and before any ack is sent.** Everything else is a state
machine draining that store.

---

## 3. Authentication & pairing flow

Design goal: a node proves its identity to the backend with a credential that is
**bound to the device** (useless if exfiltrated) and **rotatable**.

**Enrollment (one-time):**
```
1. Operator triggers pairing in the app (Flutter → HostApi.startPairing()).
2. Backend (via dashboard) issues a short-lived, single-use PAIRING CODE
   (or QR encoding {backendUrl, pairingCode}). Operator enters/scans it.
3. Node POSTs /enroll {pairingCode, deviceInfo, pubKey?} over HTTPS.
4. Backend validates the code, registers the device, returns a long-lived
   DEVICE CREDENTIAL (opaque bearer token, or client cert for mTLS).
5. Node stores the credential in DeviceCredentialStore:
   Keystore-wrapped key → EncryptedSharedPreferences. Plaintext never hits disk.
```

**Session auth (every connect):**
```
WSS handshake carries the device credential (Authorization header or a first
AUTH frame). Backend validates → session bound to deviceId. On invalid/expired
credential the socket is refused; node falls back to re-enroll UX.
```

**Rotation & revocation:** backend can issue a `config_update` carrying a new
credential (rotate) or a `revoke`/`wipe` command (unpair). Credentials have an
expiry; the node refreshes before expiry over the authenticated channel.

**Why bearer-token-in-Keystore for v1 (with mTLS as an upgrade path):** it's the
simplest thing that is genuinely secure when stored correctly, easy for any
backend to validate, and easy for future non-Android nodes to reproduce. mTLS is
stronger but adds cert-provisioning complexity; the protocol leaves room to
adopt it without a redesign.

---

## 4. Device lifecycle

```
INSTALLED
  └─ first launch (required once; Android won't deliver BOOT_COMPLETED to an
     app that has never been launched or is force-stopped)
       └─ PERMISSIONS granted (phone state → SMS)
            └─ BATTERY EXEMPTION + OEM autostart guidance accepted
                 └─ PAIRED (credential stored)
                      └─ RUNNING  ◀───────────────┐
                           ├─ reboot → BootReceiver → service restart → re-auth → resync ─┘
                           ├─ process killed → WorkManager revives → service → resync
                           ├─ credential rotated (config_update)
                           └─ UNPAIRED / WIPED → credential + queues cleared → INSTALLED-idle
```

Key lifecycle rules:
- The node needs **one** manual first launch and a **one-time** battery
  exemption; after that it should be hands-off across reboots.
- Every re-entry into RUNNING goes through **re-auth + resync** (§7), never a
  blind resume.

---

## 5. SMS lifecycle (message state machines)

**Outbox (outbound):**
```
QUEUED ─▶ SENDING ─▶ SENT ─────────▶ DELIVERED        (terminal, success)
   │         │         └─▶ UNDELIVERED                 (terminal, delivery failed)
   │         └─▶ FAILED_RETRYABLE ─▶ (backoff) ─▶ QUEUED
   │         └─▶ FAILED_TERMINAL                        (terminal, e.g. bad number)
   └─▶ CANCELLED                                        (backend cancel before send)
```
Multipart: a logical message fans out to N parts; the message reaches SENT only
when all parts report sent, DELIVERED only when all report delivered, and rolls
to the worst part outcome otherwise. Each part carries its own `PendingIntent`
correlation id.

**Inbox (inbound):**
```
RECEIVED ─▶ REASSEMBLED (multipart complete or single) ─▶ REPORTED ─▶ ACKED
   └─▶ REASSEMBLY_TIMEOUT (parts never completed) → REPORTED (as partial) → ACKED
```

Status transitions are the only place message state changes, they are all
persisted, and they are all idempotent on the message/part id.

---

## 6. Connection lifecycle (state machine)

```
        ┌────────────► OFFLINE_NO_NETWORK ◄─────────┐  (ConnectivityManager says no transport)
        │                     │ network available   │
        │                     ▼                      │
   DISCONNECTED ─────────▶ CONNECTING ─────────▶ CONNECTED ─auth ok─▶ AUTHENTICATED ─▶ READY
        ▲                     │ fail                  │ socket drop        │
        │                     ▼                       │                    │ heartbeat miss×N
        └──── BACKING_OFF ◀── RECONNECTING ◀──────────┴────────────────────┘
              (exp backoff + jitter, capped; reset on clean READY)
```

- **Transport liveness** (WebSocket ping/pong) detects dead sockets fast.
- **Application heartbeat** (§7) detects a *useless* connection (socket up but
  peer not processing) and drives backend-side online/offline.
- Network changes are driven by `ConnectivityManager.NetworkCallback`, not
  polling. Backoff resets only after a *stably* READY connection, so flapping
  networks don't hammer the backend.

---

## 7. Retry, heartbeat, delivery, resync

### 7.1 Retry strategy
- **Connection retry:** exponential backoff with full jitter, capped (e.g.
  1s→2s→4s…→max 60s). WorkManager schedules a reconnect attempt even if the FGS
  is killed.
- **Send retry:** only `FAILED_RETRYABLE` outcomes (radio off, no service,
  transient RIL errors) re-enter the queue with backoff and a max-attempt cap;
  terminal outcomes (invalid number, no SIM) never retry.
- **Event delivery retry:** unacked events are resent on reconnect (at-least-once);
  the backend dedupes on event id.

### 7.2 Heartbeat
Two layers, deliberately separate:
- **Transport ping/pong** — OkHttp-level, proves the socket is alive.
- **App heartbeat** — every ~30–60s the node sends `heartbeat{signal, battery,
  queueDepth, transportStates}`; the backend marks the node offline after N
  missed. This carries *cheap* telemetry so the dashboard is live without
  spamming full status events.

### 7.3 Delivery report flow
`SmsManager` delivery reports arrive as `PendingIntent` broadcasts, correlated
back to the exact part via a unique request id embedded in the intent. Each
transition emits a `delivery_report` event. Because reports can arrive minutes
later (or never), delivery tracking is durable and time-bounded (a
`deliveryTimeout` moves a message to UNDELIVERED-unknown rather than hanging).

### 7.4 Resync (the reconnect handshake)
On every (re)connect after AUTHENTICATED:
```
Node ──▶ resync{lastAckedInboundEventSeq, outstandingOutboxIds}
Backend ──▶ re-dispatches any commands the node hasn't acked,
            acks/ignores events it already has (by id).
Node ──▶ replays unacked events; skips commands whose ids it already applied.
```
This makes reconnection lossless and duplicate-free regardless of who dropped
first. Idempotency keys (command id, event id) are the backbone.

---

## 8. Wire protocol (API-first)

The protocol is the contract frozen in Phase 0. It is **transport-agnostic**
(SMS today, MMS/USSD/voice/modem tomorrow) and **platform-agnostic** (Android
today, Linux/Windows modem nodes later). JSON over WSS for v1
(`kotlinx.serialization`), REST for fallback/enrollment.

Because it is the **only** thing the node requires of a server, the protocol is
node-owned, versioned, and published as a standalone spec (AsyncAPI/OpenAPI-
style) so any backend — in any language or stack — can implement it without
reading the Android source. Nothing below prescribes a backend technology.

### 8.1 Envelope
Every frame shares one envelope:
```jsonc
{
  "v": 1,                       // protocol version (integer, negotiated)
  "kind": "command|event|ack|control",
  "id": "uuid",                 // idempotency key, unique per frame
  "ts": "2026-07-14T09:00:00Z", // sender clock (advisory; not trusted for order)
  "deviceId": "dev_abc123",
  "type": "send_sms",           // sub-type within kind
  "seq": 128,                   // per-direction monotonic sequence (for resync)
  "payload": { /* type-specific */ }
}
```

### 8.2 Commands (backend → node)
| type | payload | notes |
|---|---|---|
| `send_sms` | `{to, body, subscriptionId?, deliveryReport:true, ref}` | `ref` = caller's correlation id echoed back on events |
| `cancel_sms` | `{commandId}` | best-effort; only if not yet SENT |
| `get_status` | `{}` | node replies with a full `device_status` event |
| `config_update` | `{heartbeatSec?, rateLimitPerMinute?, allowlist?, credential?}` | rotate creds / push policy |
| `revoke` / `wipe` | `{}` | unpair; clear credential + queues |

### 8.3 Events (node → backend)
| type | payload |
|---|---|
| `sms_accepted` | `{commandId, messageId}` (queued & idempotency-checked) |
| `sms_sent` | `{messageId, parts:[{index, status, errorCode?}]}` |
| `delivery_report` | `{messageId, part, status, at}` |
| `sms_received` | `{from, body, subscriptionId, receivedAt, parts}` |
| `device_status` | `{battery?, network?, sims[]}` — full `DeviceState` snapshot |
| `heartbeat` | `{queueDepth, battery?, signals[], transports[]}` |
| `log` | `{level, tag, msg, at}` (redacted) |
| `error` | `{ref?, code, message}` (normalized taxonomy §10) |

### 8.4 Acks & control
`ack{ackedId}` confirms receipt of a specific frame (both directions).
`control` carries `resync`, `version_negotiate`, `ping`/`pong` at the app layer.

### 8.5 Versioning rules
`v` is sent on connect; the backend picks the highest mutually supported version.
Additive fields don't bump `v`; removals/semantic changes do. Unknown fields are
ignored, never rejected — so an older node and a newer backend interoperate.

---

## 9. Logging strategy

- **Structured** `LunoLogger` with levels; every log line is `{level, tag, msg,
  fields, at}`.
- **Sinks:** logcat (debug), a bounded Room ring buffer (on-device viewer +
  post-mortem), and throttled `log` events to the backend (Phase 10).
- **Redaction is mandatory and central:** phone numbers and message bodies are
  redacted at info level and above by a single redaction function, so no call
  site can accidentally leak PII. Full bodies exist only in the encrypted
  inbox/outbox tables, never in logs.
- **Correlation:** every log carries the relevant `messageId`/`commandId`/`seq`
  so a message can be traced end-to-end across DB, radio, and wire.

---

## 10. Error handling (normalized taxonomy)

Both `SmsManager` result codes and backend/transport errors map onto **one**
taxonomy so the retry logic never special-cases a subsystem:

| class | meaning | policy | examples |
|---|---|---|---|
| `TRANSIENT` | will likely succeed later | retry w/ backoff, capped | radio off, no service, network down, transient RIL |
| `TERMINAL` | will never succeed as-is | fail fast, report, no retry | invalid number, no SIM, body too long after split, policy reject |
| `THROTTLED` | rate/policy limited now | delay to next window, then retry | local/backend rate limit hit |
| `AUTH` | credential problem | pause, re-auth/re-enroll | expired/invalid credential |
| `INTERNAL` | our bug/invariant broken | log loudly, quarantine message | assertion failures, decode errors |

A `SecurityException` from a revoked runtime permission is caught and surfaced as
`AUTH`/`TERMINAL` with a UI prompt — never an uncaught crash.

---

## 11. Offline behavior

- **No network:** connection SM sits in `OFFLINE_NO_NETWORK`; outbound sends
  still queue durably; inbound still captured durably; heartbeats suppressed.
- **Network but no backend:** backoff reconnect; queues keep filling; after a
  threshold, REST fallback may flush critical events.
- **On reconnect:** resync handshake (§7.4) reconciles both directions.
- **Bounded growth:** queues have a max depth and an oldest-first retention
  policy with a surfaced warning, so a node offline for a week doesn't OOM.

The user-visible promise: **anything that physically happened on the radio while
offline is never lost — it's delivered to the backend, in order, once, when the
link returns.**

---

## 12. Extensibility — the two axes

**Axis A — more transports on Android** (behind `Transport`):
```kotlin
interface Transport {
  val id: TransportId                       // SMS, MMS, USSD, VOICE…
  val capabilities: Set<TransportCapability> // SEND, RECEIVE, DELIVERY_REPORT…
  suspend fun send(request: OutboundMessage): SendHandle
  fun incoming(): Flow<InboundMessage>
  fun state(): Flow<TransportState>
}
```
The `AgentController` and the wire protocol only ever speak in these neutral
terms, so adding MMS means writing one `MmsTransport` and registering it — no
changes to the queue, the protocol, or the UI’s data model.

**Axis B — more node platforms** (behind the wire protocol, §8): a Linux or
Windows GSM-modem node is a *separate program* (no shared Android/Kotlin code —
per the scope decision) that implements the same envelope, commands, and events.
The backend cannot tell what kind of node it is talking to; it just dispatches
commands and ingests events. This is why the protocol, not any class hierarchy,
is the primary extension point.
