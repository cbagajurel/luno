# Luno — Development Plan & Roadmap

> **Status:** Design phase. No implementation has started.
> This document and the linked design docs must be reviewed and approved
> before any code is written (per the project's Step 7 rule).

Luno turns an Android device into a secure, self-hosted **communication
node**. Version 1 delivers an SMS gateway; the architecture is deliberately
built so additional transports (MMS, USSD, Voice, external GSM modems on
Linux/Windows) can be added later without redesign.

This is the master roadmap. It is intentionally high-level and phase-oriented.
The detailed design lives in four companion documents:

| Document | Covers | Prompt step |
|---|---|---|
| [`docs/architecture.md`](docs/architecture.md) | Responsibilities, all lifecycles & flows, wire protocol | Step 2 |
| [`docs/folder-structure.md`](docs/folder-structure.md) | Every folder in Dart + Kotlin and why it exists | Step 3 |
| [`docs/milestones.md`](docs/milestones.md) | Independently testable milestones with full detail | Steps 4 & 5 |
| [`docs/pitfalls.md`](docs/pitfalls.md) | Architectural mistakes to avoid, with alternatives | Step 6 |

---

## The one idea that drives every decision

**The Android node is a complete, headless-capable agent. Flutter is an
optional control panel bolted on top of it.**

The WebSocket connection, the durable message queue, all telephony, retries,
and reboot survival live in a native Kotlin **foreground service** that keeps
running when the Flutter Activity is dead, backgrounded, or was never opened
after a reboot. Flutter never owns state that must survive the UI being gone.
It reads native state through Pigeon and renders it.

If you internalize only one thing from this plan: **never put anything on the
Dart side that the gateway cannot afford to lose when the UI process dies.**
That single rule resolves most of the hard design questions below.

Everything else — the transport abstraction, the versioned wire protocol, the
outbox/inbox tables — exists to serve two goals: **reliability** (never
silently drop or duplicate a message) and **extensibility** (add a transport
or a whole new node platform without touching the core).

---

## Guiding principles (how we'll judge every PR)

1. **API-first & backend-agnostic.** The node↔backend wire protocol is specified
   and versioned *before* the client is implemented. It is the real contract and
   the real extension point. The node prescribes **no** server technology
   (Firebase, Supabase, Express, Next, Nest, … are all equally fine) and
   hardcodes no endpoints — a server just has to speak the protocol. How the
   backend is built is out of scope for this repo. See
   [`docs/architecture.md`](docs/architecture.md#8-wire-protocol-api-first).
2. **Flutter is UI-only.** No telephony, no sockets, no queue, no retry logic
   in Dart. If it must survive the UI dying, it lives in Kotlin.
3. **Native over plugins.** We call Android telephony APIs directly. No
   community SMS plugins. Third-party libraries are allowed only where they are
   load-bearing infrastructure (OkHttp, Room) and carry their weight. This rule
   governs the **native agent**; the Flutter UI layer uses a conventional
   code-gen stack (Riverpod, go_router, freezed) and that's fine — the ban is
   specifically on plugins doing the agent's telephony/background/network job.
4. **Durability before delivery.** Persist to disk *before* acting or acking.
   At-least-once delivery + idempotency keys. A dropped process must never lose
   or duplicate a message.
5. **Secure by default.** WSS only, credentials in the Keystore, PII redacted
   in logs, backend-enforced rate limits so the node can't become a spam
   cannon or get the SIM banned.
6. **Extensible by contract, not by inheritance.** New transports implement a
   small interface; new node platforms speak the wire protocol. No god objects.
7. **Testable in isolation.** Every milestone runs and is verifiable on its own,
   before the next one starts. The backend can be faked; the transport can be
   faked.

---

## Phase overview

| Phase | Theme | Ships when |
|---|---|---|
| 0 | Foundations, decisions, protocol spec, project hygiene | Repo is renamed, protocol v1 frozen, CI green |
| 1 | Native skeleton + Pigeon bridge + foreground service | "Hello from Kotlin" round-trips; FGS stays alive |
| 2 | Read-only device telemetry (SIM, battery, signal, network) | Dashboard shows live device health, no dangerous perms |
| 3 | Domain core + durable persistence (Room, outbox/inbox, repos) | Queue survives process death; transport interface exists |
| 4 | SMS send (SmsManager, multipart, sent/delivery reports, multi-SIM) | A button sends a real SMS and tracks it to DELIVERED |
| 5 | SMS receive (BroadcastReceiver, multipart reassembly) | Inbound SMS is captured, reassembled, persisted |
| 6 | Backend connectivity (WebSocket, protocol, auth, heartbeat, REST fallback) | Node pairs, connects, exchanges real protocol messages |
| 7 | Reliability & lifecycle (boot, WorkManager, reconnect, queue drain, idempotency) | Node survives reboot + offline + kill and reconciles cleanly |
| 8 | Security hardening (Keystore, encryption at rest, pinning, rate limits, redaction) | Threat-model checklist passes |
| 9 | Flutter dashboard (pairing, status, logs, settings) | Operator can run the node end-to-end from the UI |
| 10 | Observability, testing, docs, first stable release | v1.0 tag, signed APK, reproducible build, docs |

Phases are ordered so each one is **independently testable without the next**.
Notably, SMS send/receive (Phases 4–5) are built and verified *before* the
backend client (Phase 6): they're triggered from a debug UI button so we prove
the hard telephony layer locally before adding network complexity. The wire
protocol is *designed* in Phase 0 but *implemented* in Phase 6 — API-first, not
API-only-on-paper.

---

## Phase 0 — Foundations & decisions

**Goal.** Remove all ambiguity and all stock-scaffold cruft so that Phase 1
starts on solid ground. Freeze the decisions that are expensive to change later.

**What will be built.**
- Rename package/namespace/applicationId from `com.example.sms_gateway` to a
  real identity (proposed: `com.luno.gateway`); update app label to `Luno`.
- Resolve the [open decisions](#open-decisions-to-close-in-phase-0) below.
- Write and **freeze wire protocol v1** ([`docs/architecture.md`](docs/architecture.md#8-wire-protocol-api-first)):
  envelope, message types, error taxonomy, ack/idempotency semantics.
- Define the Pigeon interface surface on paper (host API, flutter API, which
  streams use `EventChannel`).
- Establish `minSdk`/`targetSdk` policy (proposed `minSdk 26` / Android 8.0,
  `targetSdk` = latest stable) and the FGS type decision (see pitfalls).
- Project hygiene: `analysis_options.yaml` rules, Kotlin lint/ktlint, a
  `CONTRIBUTING.md`, a `LICENSE` (this is open source — pick one now, e.g.
  Apache-2.0 or AGPL-3.0; flagged as an open decision), issue/PR templates.
- CI skeleton: `flutter analyze`, `flutter test`, Gradle `lint`/`assembleDebug`.

**Why it comes first.** Renaming a package and changing a wire protocol after
code exists is painful and error-prone. The protocol is the contract every
later phase and every future node platform depends on; it must be stable
before clients are written. `minSdk`/FGS-type decisions change which APIs and
code paths are even legal.

**Expected project structure (delta).** No new runtime code. Adds
`docs/`, `pigeons/` (empty, reserved), `LICENSE`, `CONTRIBUTING.md`,
`.github/`, and CI config. Package directories renamed.

**Dependencies.** None added yet (decisions recorded, not libraries pulled).

**Deliverables.** Renamed project that still builds; the four design docs
approved; frozen `protocol.md` section; CI running on every push.

**Verify before moving on.**
- `flutter run` launches the renamed app; `applicationId` is `com.luno.gateway`.
- CI is green on a trivial PR.
- Protocol v1 reviewed and explicitly signed off — no "we'll figure it out later"
  fields remain.
- Every open decision below is closed and recorded in this file.

---

## Phase 1 — Native skeleton & Pigeon bridge

**Goal.** Prove the Flutter↔Kotlin bridge and stand up the foreground service
shell that everything else will live inside — with nothing telephony-related yet.

**What will be built.**
- Pigeon schema + generated code; a trivial `HostApi.ping()` round-trip and a
  `FlutterApi`/`EventChannel` push from Kotlin to Dart ("hello from Kotlin").
- `LunoApplication`, a lightweight manual DI graph (`AgentGraph`).
- `GatewayForegroundService` with a persistent, low-importance notification and
  a declared `foregroundServiceType`. It does nothing yet but stay alive.
- `AgentController` — the object that will later coordinate transports,
  backend, and queue. For now it just owns the service lifecycle.
- Structured logging skeleton (`LunoLogger` → logcat sink) with redaction hooks.

**Why before the next.** Every capability (telemetry, SMS, backend) is exposed
to Dart through this bridge and runs inside this service. If the bridge or the
service lifecycle is shaky, everything built on top inherits the shakiness.
Getting a boring "hello" round-trip and a service that genuinely survives
backgrounding is the cheapest possible place to find bridge/lifecycle bugs.

**Expected structure.** Introduces `android/.../bridge/`, `agent/`, `di/`,
`logging/` and `lib/bridge/`, `lib/core/logging/`. See
[`docs/folder-structure.md`](docs/folder-structure.md).

**Dependencies.** `pigeon` (dev), `androidx.core`, `androidx.lifecycle-service`,
Kotlin coroutines. Nothing networking or telephony yet.

**Deliverables.** App with a running foreground service, a persistent
notification, and a demonstrable two-way bridge.

**Verify before moving on.**
- `HostApi.ping()` returns; a Kotlin-initiated event reaches Dart.
- Service survives the Activity being swiped away (visible via the persistent
  notification and logcat).
- No jank/ANR; bridge calls are off the main thread where they should be.

---

## Phase 2 — Read-only device telemetry

**Goal.** Read SIM info, battery, signal strength, and network state natively
and stream them to the dashboard. **Deliberately dangerous-permission-free
(mostly):** this hardens the bridge, the manager pattern, and the streaming
model using low-risk reads before we touch SMS.

**What will be built.**
- `SimInfoManager` (SubscriptionManager, multi-SIM aware),
  `BatteryMonitor` (BatteryManager / sticky broadcast),
  `SignalStrengthMonitor` (`TelephonyCallback` on API 31+, guarded fallback
  below), `NetworkMonitor` (`ConnectivityManager.NetworkCallback`).
- A `DeviceState` domain model and a single coalesced telemetry stream to Dart.
- A minimal debug dashboard screen rendering live values.

**Why before the next.** It validates the "native manager → domain model →
stream to UI" pattern that SMS and backend status will reuse, and it forces us
to confront `READ_PHONE_STATE`, multi-SIM subscription IDs, and API-level
differences early — while nothing can go out over a carrier yet.

**Expected structure.** Introduces `android/.../telephony/` and the
`model/DeviceState`. See folder-structure doc.

**Dependencies.** No new libraries.

**Deliverables.** Dashboard showing live SIM(s), signal, battery, connectivity.

**Verify before moving on.**
- Correct on a dual-SIM device and a single-SIM device.
- Correct across at least API 26, 31, and latest (behavior of the signal and
  subscription APIs changes at 29/31).
- Values update live (pull the SIM, toggle airplane mode, unplug charger) and
  survive permission being revoked (graceful degradation, no crash).

---

## Phase 3 — Domain core & durable persistence

**Goal.** Build the reliability spine: the durable outbox/inbox and the
transport abstraction — with **no real telephony and no backend yet.**

**What will be built.**
- Room database: `outbox`, `inbox`, `delivery_report`, `event_log` tables with
  status enums and idempotency keys.
- Repositories: `OutboxRepository`, `InboxRepository`, `DeviceStateRepository`,
  `LogRepository`.
- The `Transport` interface + `TransportCapability` + a `FakeTransport` that
  simulates send/deliver/receive for tests.
- The domain models and the normalized **error taxonomy** (transient vs
  permanent) that SMS result codes and backend errors will both map onto.

**Why before SMS.** SMS send/receive must write to a durable queue *before*
touching the radio (persist-before-act). Building the queue first means Phase 4
plugs `SmsManager` into an already-correct, already-tested persistence and
state-machine layer, rather than smearing DB logic through the telephony code.
The `FakeTransport` lets us test the entire message state machine with zero
hardware.

**Expected structure.** Introduces `android/.../data/` (db, repository),
`transport/` (interface + fake), expands `model/`.

**Dependencies.** `androidx.room` (+ KSP). Coroutines/Flow.

**Deliverables.** A tested message state machine driven by `FakeTransport`;
queue contents survive a process kill.

**Verify before moving on.**
- Enqueue → send → deliver transitions are correct and persisted.
- Killing the process mid-flight leaves the DB in a recoverable state; on
  restart the queue resumes without duplicating.
- Idempotency: replaying the same command ID does not double-enqueue.

---

## Phase 4 — SMS send

**Goal.** Send real SMS through the durable queue and track each message to a
final state, including multipart and multi-SIM.

**What will be built.**
- `SmsTransport` (implements `Transport`), `SmsSender`, `MultipartAssembler`
  for outbound splitting, and a `SentReportRouter` / `DeliveryReportRouter`
  wiring `PendingIntent` result codes back into the outbox state machine.
- Per-subscription sending (`SmsManager.createForSubscriptionId`).
- Result-code → error-taxonomy mapping (retryable vs terminal).
- A debug "send test SMS" control in the UI (no backend required).

**Why before receive/backend.** Sending is the higher-risk, higher-value path
and exercises the full outbox lifecycle (queue → send → sent → delivered/failed)
plus multi-SIM. Proving it locally, before the network, isolates telephony bugs
from protocol bugs.

**Expected structure.** Introduces `android/.../transport/sms/`.

**Dependencies.** No new libraries. Adds `SEND_SMS` permission + runtime
request flow.

**Deliverables.** UI-triggered send that reaches DELIVERED on a real device,
with delivery report captured; failures classified correctly.

**Verify before moving on.**
- Single-part and multipart (>160 char / concatenated) both deliver.
- Correct SIM is used on a dual-SIM device.
- Radio-off / no-service / invalid-number produce the right taxonomy outcome
  (retryable vs terminal), not a crash.
- Delivery report correlates back to the exact message part.

---

## Phase 5 — SMS receive

**Goal.** Capture inbound SMS, reassemble multipart, and persist to the inbox
durably before any acknowledgement.

**What will be built.**
- `SmsReceiver` (`BroadcastReceiver` on `SMS_RECEIVED`) using
  `goAsync()` + coroutine hand-off to the service (no work on the broadcast
  thread past the ANR budget).
- Inbound `MultipartAssembler` (concatenated PDU reassembly, with a timeout for
  never-completed multiparts).
- Persist to `inbox` (RECEIVED) immediately; report/ack comes later in Phase 6.

**Why after send, before backend.** Receiving is testable emulator-to-emulator
or device-to-device with no backend. Doing it before the backend means inbound
messages are already durably captured when we wire the protocol, so Phase 6 is
"drain the queue over the wire," not "invent capture and transport at once."

**Expected structure.** Expands `transport/sms/` with the receiver + inbound
assembler; adds `RECEIVE_SMS` (and, if we choose to read existing threads,
`READ_SMS`) permissions.

**Dependencies.** No new libraries.

**Deliverables.** Inbound SMS (single + multipart) reliably captured and
persisted even when the app UI is not open.

**Verify before moving on.**
- App-closed and screen-locked delivery still captured (service + manifest
  receiver).
- Multipart reassembles correctly and in order; partial/never-completed
  multiparts time out cleanly rather than leaking.
- No ANR from the receiver; heavy work is off the broadcast thread.

---

## Phase 6 — Backend connectivity

**Goal.** Implement the frozen wire protocol over WebSocket, authenticate the
node, run the heartbeat, and drain both queues over the wire — with REST as a
degraded fallback.

**What will be built.**
- `WebSocketClient` (OkHttp) with the connection state machine, `Heartbeat`,
  and `ReconnectPolicy` (exponential backoff + jitter, capped).
- `ProtocolCodec` (envelope encode/decode, version negotiation),
  `Command`/`Event` types, ack tracking.
- `PairingManager` + `DeviceCredentialStore` (enrollment → credential;
  credential presented in the WSS handshake).
- `RestClient` fallback for enrollment and for event delivery when the socket
  is persistently unavailable.
- Wire the real `SmsTransport` behind the protocol: backend `send_sms` command
  → outbox; outbox/inbox/delivery transitions → events to backend.

**Why here.** By now the two hard local subsystems (telephony + durable queue)
are proven. The backend phase becomes purely about the protocol and the
connection, which is where reconnect/idempotency subtleties live, and they're
easier to reason about when the layers beneath are already trustworthy.

**Expected structure.** Introduces `android/.../backend/` (`ws/`, `rest/`,
`protocol/`, `auth/`).

**Dependencies.** `okhttp`. A JSON codec (`kotlinx.serialization`, chosen for
minimalism and Kotlin-native codegen).

**Deliverables.** A node that pairs, connects, sends/receives protocol messages,
heartbeats, and reconnects — verified against any server that implements the Luno
protocol; a small stub server is enough to develop against.

**Verify before moving on.**
- Full round trip: backend command → SMS sent → delivery report event back.
- Inbound SMS surfaces as an event and is acked exactly once (dedupe verified).
- Kill the socket mid-exchange: reconnect + resync leaves no lost/dup messages.
- Heartbeat drives correct online/offline state on the backend.

---

## Phase 7 — Reliability & lifecycle

**Goal.** Survive reboots, process death, and long offline periods, and
reconcile cleanly every time.

**What will be built.**
- `BootReceiver` (`BOOT_COMPLETED`) → restart the service, re-auth, resync.
- WorkManager workers: `OutboxDrainWorker` (retry backoff), `ReconnectWorker`,
  `LogUploadWorker` — the safety net when the FGS is killed.
- Full idempotency + resync handshake on reconnect (last-acked cursors).
- Battery-optimization / OEM-autostart UX: detect, explain, deep-link the user
  to the right settings; surface a "health" indicator when the OS is throttling.

**Why here.** Reliability plumbing is only meaningful once there is real traffic
and a real connection to lose. Building it last-but-one lets us test it against
the actual failure modes rather than hypothetical ones.

**Expected structure.** Introduces `android/.../work/` and `receiver/BootReceiver`.

**Dependencies.** `androidx.work`.

**Deliverables.** A node that comes back by itself after reboot/kill/offline and
provably neither loses nor duplicates messages.

**Verify before moving on.**
- Reboot → node auto-reconnects and drains without user interaction (after the
  one-time first launch + battery exemption).
- Airplane mode for an hour → queued sends drain correctly on return.
- Force-stop + redeliver storm → no duplicate sends (idempotency holds).

---

## Phase 8 — Security hardening

**Goal.** Make "secure by default" real and verifiable against a written threat
model.

**What will be built.**
- Keystore-backed credential storage; `EncryptedSharedPreferences` /
  encrypted DB fields for message bodies at rest.
- WSS enforcement + optional certificate pinning.
- Backend-enforced + client-honored **rate limiting** and recipient allowlists
  (anti-spam, SIM-ban avoidance).
- Log redaction pass (no bodies/PII at info level), secure log upload.
- Runtime-permission revocation & Android "unused app" auto-reset handling;
  credential rotation and remote wipe/unpair.

**Why here.** Hardening is most effective once the real data flows exist to
protect; doing it earlier means guessing at the attack surface.

**Deliverables.** A completed threat-model checklist ([`docs/pitfalls.md`](docs/pitfalls.md#security-risks));
credentials never touch plaintext storage or logs.

**Verify before moving on.**
- Static scan: no secrets/PII in logs or plaintext prefs.
- Pulled credential is unusable off-device (Keystore-bound).
- Rate limits actually cap send throughput; allowlist rejects out-of-policy
  recipients.

---

## Phase 9 — Flutter dashboard

**Goal.** Give the operator a complete UI over the already-working native agent:
pairing, live status, logs, settings.

**What will be built.**
- Pairing/enrollment flow (QR or code), device status dashboard, log viewer,
  settings (SIM selection defaults, rate-limit display, battery-exemption
  helper), connection indicator.
- All fed by Pigeon host queries + `EventChannel` streams. No business logic in
  Dart — it renders native state and issues commands.

**Why late.** The UI is a window onto a system that already works headless.
Building it last means it reflects real, stable native state instead of
racing a moving target — and it keeps us honest about Flutter being UI-only.

**Expected structure.** Fills out `lib/features/*`, `lib/state/`.

**Dependencies.** A state solution (proposed Riverpod, or plain
`ValueNotifier`/streams given how thin the UI is — open decision).

**Deliverables.** An operator can pair and run a node entirely from the app.

**Verify before moving on.**
- Killing/reopening the UI never disturbs the running gateway.
- Every displayed value has a single native source of truth (no Dart-side
  duplication of gateway state).

---

## Phase 10 — Observability, testing, docs, release

**Goal.** Ship a trustworthy v1.0.

**What will be built.**
- Structured, throttled log upload; on-device ring-buffer log store.
- Test coverage pass: Kotlin unit (JUnit) + Robolectric, instrumentation for
  receivers/service, Dart widget tests, protocol contract tests against a
  backend stub. Documented manual test matrix for real-SIM paths.
- Docs: operator setup guide, OEM battery-exemption guide, protocol reference,
  self-hosting/backend-pairing guide, security notes.
- Release: signing config, reproducible build, signed APK, F-Droid metadata
  (Play Store is effectively closed to SMS-permission apps — see pitfalls),
  `v1.0.0` tag.

**Deliverables.** Tagged, signed, documented v1.0 with CI-enforced quality gates.

**Verify before shipping.**
- Full end-to-end on ≥2 physical devices across ≥2 OEM skins and ≥3 API levels.
- Cold-install → pair → send → receive → reboot → still-working, unassisted.
- Docs let a stranger self-host and pair a node without reading the source.

---

## What v1.0 explicitly does NOT include

Kept out of scope to protect the timeline and the "Android SMS, done right"
focus. The architecture leaves room for all of them:

- MMS, USSD, Voice transports (the `Transport` interface anticipates them).
- Linux/Windows GSM-modem nodes (separate runtimes speaking the same wire
  protocol — the protocol anticipates them; no shared code with Android).
- The backend/control plane and web dashboard (a separate concern; the node
  only speaks the wire protocol and is indifferent to how they're built).
- Any Play Store distribution promise.

---

## Decisions

### Locked (signed off 2026-07-14)

1. **License — Apache-2.0.** Permissive + explicit patent grant, best for
   adoption of infrastructure meant to be embedded and self-hosted widely.
2. **`minSdk` — 26 (Android 8.0).** Modern FGS + notification-channel semantics,
   ~95%+ of active devices, caps legacy branch cost. `targetSdk` = latest stable.
3. **Foreground service type — `specialUse`** with a documented justification (a
   persistent SMS gateway fits none of the narrow standard types). This
   reinforces the sideload/F-Droid distribution path (Play Store's SMS/Call-Log
   policy already excludes a general-purpose gateway). See
   [`docs/pitfalls.md`](docs/pitfalls.md#android-background-limitations).

### Recommended — confirm or defer (not blocking)

4. **Package identity** — `com.luno.gateway` (rename in Phase 0). Confirm.
5. **Wire format** — **JSON + `kotlinx.serialization`** for v1 (debuggable, easy
   for any backend and future non-Android nodes to implement). Revisit binary
   only if profiling demands it.
6. **DI** — **manual DI graph** (honors "minimal dependencies") over Hilt.
   Reconsider if the graph gets unwieldy.
7. **Flutter UI stack — chosen.** Riverpod (code-gen: `flutter_riverpod` +
   `riverpod_annotation`/`riverpod_generator`) for state, `go_router`
   (+ `go_router_builder`) for routing, `freezed` + `json_serializable` for
   models, `logger` for UI logging, `flutter_screenutil`/`flutter_svg`/
   `google_fonts` for presentation. Codegen via `build_runner`.
8. **Distribution** — **sideload + F-Droid** (follows from decision #3).

Until the four design docs and this plan are approved, **no implementation
starts** — that is the Step 7 gate.
