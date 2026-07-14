# Luno — Folder Structure

> Companion to [`../plan.md`](../plan.md) and
> [`architecture.md`](architecture.md). Every folder is listed with a one-line
> reason. This is the *target* layout; it is grown phase by phase, not created
> all at once.

## Reconciling with what already exists

The repo is a stock `flutter create` scaffold with a few empty placeholder
folders. This plan **keeps** the good ones and renames the rest:

| Currently | Decision |
|---|---|
| `lib/core/native/native_bridge.dart` (stub, comments only) | Keep the idea, move under `lib/bridge/` |
| `lib/ui/home/home_screen.dart` | Becomes `lib/features/dashboard/` |
| `lib/features/`, `lib/platform/`, `lib/services/` (empty) | `features/` kept; `platform/` folded into `bridge/`; `services/` dropped (Dart has no long-lived services — that's native's job) |
| `android/.../com/example/sms_gateway/` | Renamed to `com/luno/gateway/` in Phase 0 |
| App label `sms_gateway` | Renamed to `Luno` |

Rationale for dropping `lib/services/`: a "service" in this architecture is a
native concept. Keeping a Dart `services/` folder invites exactly the mistake we
are designing against — business logic drifting into Dart.

---

## Repository root

```
sms_gateway/                 (repo name; app identity becomes com.luno.gateway)
├── plan.md                  Master roadmap
├── docs/                    Design docs (this folder)
│   ├── architecture.md
│   ├── folder-structure.md
│   ├── milestones.md
│   └── pitfalls.md
├── pigeons/                 Pigeon interface definitions (source of the bridge)
│   └── luno_api.dart        Single schema: HostApi + FlutterApi + data classes
├── lib/                     Flutter (UI only)
├── android/                 Native Android node (the actual agent)
├── test/                    Dart unit/widget tests
├── LICENSE  CONTRIBUTING.md  .github/   Open-source hygiene (Phase 0)
└── pubspec.yaml  analysis_options.yaml
```

**`pigeons/`** — Pigeon reads Dart interface definitions here and generates
type-safe Dart + Kotlin. Keeping the schema in one reviewed place makes the
Flutter↔native contract explicit and diffable. Generated output is committed
under `lib/bridge/generated/` and `android/.../bridge/generated/`.

---

## Flutter side — `lib/`

Thin by design. If a folder here starts holding gateway logic, that's a smell.

```
lib/
├── main.dart                App entrypoint (ProviderScope + ScreenUtilInit)
├── app/                     Root widget, routing, theme
│   ├── luno_app.dart        MaterialApp.router
│   ├── router.dart          go_router config (typed routes via go_router_builder)
│   └── theme.dart           ThemeData + google_fonts
├── bridge/                  The ONLY door to native
│   ├── generated/           Pigeon output (do not hand-edit)
│   ├── luno_bridge.dart     Thin wrapper over HostApi (command/query calls)
│   └── native_events.dart   EventChannel stream adapters (signal, logs, status)
├── core/                    Cross-cutting Dart utilities
│   ├── logging/             UI-side logging via `logger` (routes to native LunoLogger)
│   ├── result/             Result/Either types for bridge call outcomes
│   └── constants.dart
├── state/                   Riverpod providers (riverpod_generator, *.g.dart)
│   └── ...                  Holds ONLY UI state + mirrors of native streams
├── features/                One folder per screen/feature; UI + view models
│   ├── pairing/             Enrollment flow (QR/code), calls startPairing()
│   ├── dashboard/           Live device + transport + connection status
│   ├── messages/            Sent/received log view (read-only mirror)
│   ├── logs/                On-device log viewer
│   └── settings/            SIM defaults, battery-exemption helper, about
├── models/                  freezed + json_serializable (*.freezed.dart / *.g.dart)
└── gen/                     flutter_gen asset refs (optional; generated)

assets/                      SVGs/icons for flutter_svg (repo root; listed in pubspec)
```

- **`bridge/`** — the single, auditable boundary. Every native call goes
  through here; features never touch platform channels directly. `generated/`
  is Pigeon's output; the hand-written wrappers add ergonomics (Futures →
  Results, stream typing) without logic.
- **`core/`** — small shared helpers only. No gateway logic.
- **`state/`** — Riverpod providers (`riverpod_generator`) holding *UI* state and
  cached mirrors of native streams so widgets rebuild. Never the source of truth;
  native is. If the app is killed, nothing important is lost here.
- **`features/`** — vertical slices; each screen owns its widgets + a lean
  view model (Riverpod notifier) that reads `bridge`/`state`. This matches (and
  cleans up) the existing `ui/home` and empty `features/`.
- **`models/`** — `freezed` + `json_serializable` data classes mirroring
  protocol/native types so the UI has typed, immutable values to render. No
  behavior beyond (de)serialization.

**Flutter stack & codegen.** State = Riverpod (`flutter_riverpod` +
`riverpod_annotation`/`riverpod_generator`); routing = `go_router`
(+ `go_router_builder`); models = `freezed` + `json_serializable`; logging =
`logger`; presentation = `flutter_screenutil` (responsive), `flutter_svg`
(+ `assets/`), `google_fonts`. Generated files (`*.g.dart`, `*.freezed.dart`,
route/asset code) are produced by `build_runner` and committed. This stack lives
**only** in `lib/` — it never leaks into the native agent, which stays
dependency-minimal (see [`pitfalls.md`](pitfalls.md#tight-coupling)).

---

## Native Android side — `android/app/src/main/`

This is where the real system lives. Package root becomes
`com/luno/gateway/`.

```
android/app/src/main/
├── AndroidManifest.xml       Perms, <service>, <receiver> declarations
├── kotlin/com/luno/gateway/
│   ├── LunoApplication.kt     App-level init; builds the DI graph
│   ├── MainActivity.kt        FlutterActivity host; installs Pigeon impls
│   │
│   ├── di/                    Manual dependency graph (no Hilt by default)
│   │   └── AgentGraph.kt       Single composition root; wires everything
│   │
│   ├── bridge/                Flutter↔native boundary (native half)
│   │   ├── generated/          Pigeon output
│   │   ├── LunoHostApiImpl.kt   Implements Dart→Kotlin commands/queries
│   │   ├── FlutterEventBridge.kt Pushes native streams to Dart (EventChannel)
│   │   └── BridgeMappers.kt     Domain model ↔ Pigeon/DTO mapping
│   │
│   ├── agent/                 Orchestration & process lifetime
│   │   ├── GatewayForegroundService.kt  The 24/7 process; declares FGS type
│   │   ├── AgentController.kt   Coordinates backend+transports+queue+telemetry
│   │   ├── ServiceNotification.kt  Persistent low-importance notification
│   │   └── ConnectionStateMachine.kt  §6 connection SM
│   │
│   ├── transport/            Communication transports (extensibility axis A)
│   │   ├── Transport.kt        Interface (send/incoming/state/capabilities)
│   │   ├── TransportCapability.kt
│   │   ├── TransportRegistry.kt Holds active transports by id
│   │   ├── fake/FakeTransport.kt  Test double (Phase 3)
│   │   └── sms/
│   │       ├── SmsTransport.kt       Implements Transport for SMS
│   │       ├── SmsSender.kt          SmsManager send (per subId, multipart)
│   │       ├── SmsReceiver.kt        BroadcastReceiver: SMS_RECEIVED
│   │       ├── MultipartAssembler.kt In/outbound concatenated-SMS handling
│   │       ├── SentReportRouter.kt   sentIntent PendingIntent → outbox
│   │       └── DeliveryReportRouter.kt deliveryIntent → outbox
│   │       # future: mms/  ussd/  voice/  siblings of sms/
│   │
│   ├── telephony/            Read-only device state (extensibility-neutral)
│   │   ├── SimInfoManager.kt        SubscriptionManager, multi-SIM
│   │   ├── SignalStrengthMonitor.kt TelephonyCallback (API 31+) + fallback
│   │   ├── BatteryMonitor.kt        BatteryManager / sticky broadcast
│   │   └── NetworkMonitor.kt        ConnectivityManager.NetworkCallback
│   │
│   ├── backend/              Node↔server (the wire protocol client)
│   │   ├── ws/
│   │   │   ├── WebSocketClient.kt   OkHttp WS; owns socket lifecycle
│   │   │   ├── Heartbeat.kt         App-level heartbeat scheduler
│   │   │   └── ReconnectPolicy.kt   Exp backoff + jitter, capped
│   │   ├── rest/
│   │   │   └── RestClient.kt        Enrollment + degraded event fallback
│   │   ├── protocol/               API-first contract (§8)
│   │   │   ├── Envelope.kt
│   │   │   ├── Command.kt  Event.kt  Ack.kt  Control.kt
│   │   │   └── ProtocolCodec.kt     kotlinx.serialization encode/decode + version
│   │   └── auth/
│   │       ├── PairingManager.kt        Enrollment flow
│   │       └── DeviceCredentialStore.kt Keystore-backed credential storage
│   │
│   ├── data/                 Durability (the reliability spine)
│   │   ├── db/
│   │   │   ├── LunoDatabase.kt        Room database
│   │   │   ├── dao/  (OutboxDao, InboxDao, DeliveryDao, LogDao)
│   │   │   └── entity/ (OutboxEntity, InboxEntity, DeliveryEntity, LogEntity)
│   │   └── repository/
│   │       ├── OutboxRepository.kt     Outbound state machine + idempotency
│   │       ├── InboxRepository.kt      Inbound capture + dedupe
│   │       ├── DeviceStateRepository.kt Latest telemetry snapshot
│   │       └── LogRepository.kt        Ring-buffer log store
│   │
│   ├── work/                 Safety net when the FGS is killed
│   │   ├── OutboxDrainWorker.kt  Retry queued sends w/ backoff
│   │   ├── ReconnectWorker.kt    Re-establish backend link
│   │   └── LogUploadWorker.kt    Throttled log shipping
│   │
│   ├── receiver/             System BroadcastReceivers
│   │   └── BootReceiver.kt   BOOT_COMPLETED → restart service
│   │       # SmsReceiver lives under transport/sms (it IS a transport concern)
│   │
│   ├── security/
│   │   ├── KeystoreManager.kt  Keystore key create/wrap
│   │   ├── CryptoBox.kt        Encrypt/decrypt at-rest fields
│   │   └── RateLimiter.kt      Client-side enforcement of send limits
│   │
│   ├── logging/
│   │   ├── LunoLogger.kt       Structured logging facade
│   │   ├── LogSink.kt          Sink interface (logcat / Room / backend)
│   │   └── Redaction.kt        Central PII redaction (single source)
│   │
│   ├── config/
│   │   ├── AgentConfig.kt      Static + build config
│   │   └── RemoteConfig.kt     Backend-pushed policy (rate limits, heartbeat)
│   │
│   ├── model/                Kotlin domain models (transport-neutral)
│   │   ├── OutboundMessage.kt  InboundMessage.kt  SendHandle.kt
│   │   ├── DeviceState.kt  SimInfo.kt  TransportState.kt
│   │   └── DomainError.kt      The normalized error taxonomy (§10)
│   │
│   └── util/
│       ├── Result.kt   Backoff.kt   Clock.kt   Ids.kt (uuid/idempotency keys)
└── res/                      Notification icon, strings, etc.
```

### Why each native folder exists

- **`di/`** — one composition root so dependencies are explicit and testable;
  manual DI keeps the dependency list minimal (a stated principle).
- **`bridge/`** — isolates all Flutter-facing glue so the agent has no Flutter
  awareness beyond this folder. The agent could run with the bridge removed.
- **`agent/`** — owns the *process* and orchestration. `GatewayForegroundService`
  is the thing Android keeps alive; `AgentController` is the brain it hosts.
  Separating them means the brain is unit-testable without the service.
- **`transport/`** — the abstraction that makes new communication tech cheap.
  `sms/` is one sibling; `mms/`, `ussd/`, `voice/` slot in beside it later.
  `SmsReceiver` lives here (not in `receiver/`) because it's a transport detail,
  not a system-lifecycle concern.
- **`telephony/`** — read-only device sensors, kept separate from `transport/`
  because they describe the *device*, not a *channel*. Reused by every transport.
- **`backend/`** — the wire-protocol client. Split into `ws`/`rest`/`protocol`/
  `auth` so the protocol (the contract) is independent of the socket that
  carries it — you can test the codec without a network and swap transports.
- **`data/`** — the durable spine. Repositories own the state machines; DAOs/
  entities are pure persistence. Everything persists before it acts.
- **`work/`** — WorkManager jobs are the *safety net* for when the always-on
  service is nonetheless killed; they are not the primary path (the FGS is).
- **`receiver/`** — genuine system-event receivers (boot). Kept tiny; they only
  poke the service, never do work themselves.
- **`security/`, `logging/`, `config/`, `model/`, `util/`** — cross-cutting
  concerns given their own homes so they're not smeared across features:
  security is centralized (auditable), logging redaction is single-sourced
  (no PII leaks), models are transport-neutral (so the queue and protocol don't
  depend on SMS specifics).

### Manifest-declared components (for reference)
- `<service>` `GatewayForegroundService` with `android:foregroundServiceType`.
- `<receiver>` `BootReceiver` (`BOOT_COMPLETED`, `exported="true"`, direct-boot
  aware if we want pre-unlock start).
- `<receiver>` `SmsReceiver` (`SMS_RECEIVED`, requires `BROADCAST_SMS` perm on
  the sender = system, so effectively protected).
- Permissions grow phase by phase (see [`milestones.md`](milestones.md)); nothing
  dangerous is declared before the milestone that uses it.
