# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

This repo is the Android app for **Luno**, a self-hosted SMS gateway agent.
An Android phone runs a long-lived background service that sends/receives
SMS and reports device health (SIM, signal, battery, delivery status) to a
backend over WebSocket (REST as fallback) — any server that speaks the Luno
protocol; the backend's tech stack is out of scope for this repo. Flutter is the
dashboard UI only — it never talks to telephony APIs directly.

**Scope decision: Android only, for now.** Linux/Windows "nodes" would
require external USB GSM modems and AT-command integration (no shared code
with the Android telephony layer); macOS has no cellular hardware path at
all. Both are explicitly out of scope until the Android app is solid. Don't
add Linux/Windows/macOS platform code or dependencies without being asked.

The backend and dashboard are separate concerns (separate repositories), not
part of this codebase. The node is **backend-agnostic**: it prescribes no server
technology (Firebase, Supabase, Express, Next, Nest, … all work equally), it
hardcodes no endpoints (they're runtime config set at pairing), and it depends
only on the versioned wire protocol. How the backend is built is not our concern
— the node's job is: receive a command, act on the radio, report what happened.

## State of the repo

**Progress: M1–M17 complete, plus the pairing-session system (2026-07-19).**
[`docs/milestones.md`](docs/milestones.md) covered the initial build-out (M1–M18)
and is kept for history; work is no longer gated on its running order.

**Pairing sessions (2026-07-19).** The node is now a pure *pairing client*: it
submits a code — typed or scanned — and renders the backend's verdict, enforcing
no expiry, usage limit, revocation or approval rule of its own, so every
enrolment policy is backend config rather than an app release. New/changed:
`backend/auth/{PairingPayload,PendingEnrollment,SealedValueStore,InstallId}.kt`,
a policy-driven `PairingError` taxonomy with forward-compatible unknown codes,
`RestClient` enroll v2 (`nonce`, `installId`, `sessionId`, reserved `publicKey`,
`status: approved|pending|denied`) plus `POST /enroll/status`, and QR pairing
(versioned `luno://pair` / JSON payload parsed natively; `mobile_scanner` is a
viewfinder only). See [`docs/pairing.md`](docs/pairing.md) — that file is the
contract the `@luno/*` server SDKs implement.

Done so far:

- **M1** package renamed to `com.luno.gateway`; LICENSE/CONTRIBUTING/CI.
- **M2** Pigeon bridge (`LunoHostApi.ping` round-trip + native tick `EventChannel`).
- **M3** foreground service (`GatewayForegroundService`) that survives swipe + agent-state bridge.
- **M4** SIM information via `SubscriptionManager` (live updates, multi-SIM).
- **M5** battery status; telemetry coalesced into one `DeviceState` stream.
- **M6** per-SIM signal strength (`TelephonyCallback` 31+ / `PhoneStateListener` fallback).
- **M7** network state (default network callback: validated/transport/metered).
- **M8** durable Room outbox/inbox + `Transport` interface with `FakeTransport`.
- **M9** single-part SMS send: `transport/sms/{SmsSender,SentReportRouter,SmsResultCodes,SmsTransport}`,
  `OutboxDispatcher` (QUEUED→SENDING→SENT/FAILED), `SEND_SMS` runtime flow, Pigeon
  `sendSms`/`getRecentOutbox` + `OutboxChannel`, and a Flutter debug send control.
- **M10** multipart + multi-SIM send + delivery reports: `MultipartAssembler`,
  `SmsSender.sendMultipart`, sent-report rollup in `SmsTransport`; `DeliveryReportRouter`
  - `Transport.deliveryReports()`; durable per-part `outbox_part` table (Room **v2**,
    `MIGRATION_1_2`) + `DeliveryTracker` rollup to DELIVERED/UNDELIVERED with a
    delivery-timeout. Real dual-SIM + carrier delivery reports still need a physical device.
- **M11** receive SMS: manifest `SmsReceiver` (SMS_RECEIVED, `goAsync` capture),
  `MultipartAssembler.reassemble` + pure `SmsReceiver.buildInbound`, `RECEIVE_SMS` runtime
  flow, Pigeon `getRecentInbox` + `InboxChannel`, read-only received-messages list.
  Capture-only (backend reporting is M14).
- **M12** wire protocol codec + connection SM (pure Kotlin, `kotlinx.serialization`):
  `backend/protocol/{Envelope,Command,Event,Ack,Control,ProtocolCodec}` (§8 DTOs +
  single (kind,type)→serializer registry, `DecodeResult.Ok|Unsupported|Malformed`,
  forward-compat/quarantine, `ProtocolVersion.negotiate`); `agent/ConnectionStateMachine`
  (pure §6 transition table); `backend/ws/ReconnectPolicy` (capped backoff + full jitter,
  reset only after a stable READY). No live connection yet (M13). Wire DTOs are decoupled
  from domain models — mapping is M14.
- **M13** pairing/auth + live WSS: `backend/auth/{PairingManager,DeviceCredentialStore}`
  (Keystore-bound credential, `POST /enroll` via `backend/rest/RestClient`),
  `backend/ws/{WebSocketClient,ConnectionManager}` (OkHttp WSS, single-consumer loop driving
  the §6 SM: version_negotiate→AUTHENTICATED→resync→READY, 401/403 pauses instead of looping),
  `security/KeystoreManager`; pairing UI.
- **M14** protocol wired to SMS + heartbeat: `agent/{AgentController(full),CommandRouter,
EventKeys,DeviceStatusMapper}`, `backend/ws/{EventPublisher,Heartbeat}`. Backend `send_sms`
  → durable outbox → SMS → `sms_accepted`/`sms_sent`/`delivery_report`; inbound inbox →
  `sms_received`; every node→backend event is at-least-once (stable id, buffered-until-acked,
  resent on READY — in-memory; durable resync is M15). `ConnectionManager` now sends
  events/acks (one monotonic seq) and forwards backend acks.
- **M15** boot + WorkManager + resync: `receiver/BootReceiver` (BOOT_COMPLETED →
  start FGS when paired), `work/AgentWatchdogWorker` (+ pure `WatchdogDecision`) — a
  periodic backstop that revives the FGS or drains the outbox headless when a
  background FGS-start is disallowed. **Durable resync (§7.4):** new `event_outbox`
  table (Room **v3**, `MIGRATION_2_3`) makes `EventPublisher` durable — reliable events
  persist under their stable id, resend from disk on each READY, clear on ack, and
  survive process death (closing M14's in-memory gap); the `sms_received` ack follow-up
  is keyed off a persisted `correlationId`. `ProtocolCodec.encode/decodeEventPayload`;
  resync now carries real `outstandingOutboxIds`
  (`OutboxRepository.observeOutstandingCommandIds`) + `lastAckedInboundSeq`.
- **M16** security hardening: `logging/Redaction` (central phone-number masker wired as
  `LunoLogger`'s redactor); `security/CryptoBox` seals PII at rest with a Keystore key
  (`luno_data_key`) — outbox/inbox/event-payload bodies + numbers sealed on write,
  opened on read (no schema change; dedup keys stay plaintext); `security/RateLimiter`
  - `security/PolicyStore` enforce client-side backend-authoritative rate-limit +
    allowlist in `CommandRouter` (reject → `error`, no enqueue); `revoke`/`wipe` do a full
    node reset (credential + queues + policy cleared, disconnect, cancel watchdog);
    `SmsTransport` maps revoked `SEND_SMS` to `AUTH`; `security/Pinning` + optional
    `CertificatePinner` on `WebSocketClient` is a cert-pinning seam (off by default).
- **M17** Flutter dashboard (UI-only): replaced the demo `ui/home` with `main.dart`
  (`ProviderScope` + `ScreenUtilInit`), `app/{luno_app,theme,router}` (Material 3 +
  `google_fonts`; `go_router` `isPaired` gate + `StatefulShellRoute` bottom nav),
  `state/*` (manual Riverpod snapshot-then-stream mirrors; native = sole source of
  truth), and `features/{pairing,dashboard,messages,logs,settings}`. One new native
  seam for the in-app log viewer: `logging/RingBufferLogSink` + Pigeon `getRecentLogs`
  /`LogEntry` + `bridge/LogChannel` (`events/logs`). 13 Dart tests via a
  `FakeLunoBridge`. QR pairing / default-SIM / battery-exemption + on-device pass → M18.

## Commands

```
flutter pub get              # install dependencies
flutter run --flavor full    # run on a connected device/emulator
flutter build apk --flavor full      # complete gateway (send + receive)
flutter build apk --flavor sendOnly  # outbound only; installs past Play Protect
flutter build appbundle --flavor full  # AAB, for Play Store upload
flutter analyze              # static analysis (uses analysis_options.yaml / flutter_lints)
flutter test                 # run tests
flutter test test/some_test.dart   # run a single test file
```

**Always pass a `--flavor`, and pass it to the *subcommand*** — `flutter build apk
--flavor full`, not `flutter build --flavor full` (bare `flutter build` rejects the
option outright: "Could not find an option named --flavor").

Omitting the flavor is the trap: `flutter build apk` does **not** fail. It builds
every variant and leaves a `build/app/outputs/apk/release/app-release.apk` that
declares `RECEIVE_SMS` — a `full` build under a neutral name. Sideloading it trips
Play Protect exactly as if you had asked for `full`. Name the flavor and use the
flavored output (`app-full-release.apk` / `app-sendOnly-release.apk`) so the
artifact says what it is.

`full` declares `RECEIVE_SMS` and is the complete node; `sendOnly` omits it so the
APK installs without Play Protect's enhanced-fraud-protection warning. See
[`docs/play-protect.md`](docs/play-protect.md).

SMS send/receive requires a real device or two emulator instances (emulator
SMS can target another emulator's port but not a real SIM/carrier).

## Architecture (target design)

```
Backend (any Luno-protocol server)  <—WebSocket/REST—>  Flutter (UI: dashboard, pairing, settings, logs)
                                          │  Platform Channel / Pigeon
                                    Native Android layer:
                                      SmsManager, TelephonyManager,
                                      SubscriptionManager, BroadcastReceivers,
                                      ForegroundService, BootReceiver, WorkManager
                                          │
                                    Android Telephony / SIM
```

Key architectural rule: **Flutter is UI-only.** All telephony operations
(sending/reading SMS, reading SIM/signal/battery state, surviving reboot)
belong in the native Android layer (Kotlin, under `android/app/src/main/`)
and are exposed to Dart through Pigeon-generated platform channels — not
through community Flutter SMS plugins. This is a deliberate choice to keep
the telephony layer directly on top of Android's own APIs.

When implementing new capabilities, the native Android side owns:

- `ForegroundService` for the 24/7 agent process
- `BootReceiver` for auto-start after reboot
- `WorkManager` for retry/backoff scheduling
- `BroadcastReceivers` for incoming SMS and delivery reports
- `TelephonyManager` / `SubscriptionManager` for SIM, signal, multi-SIM info

Flutter owns pairing/auth UI, device status display, settings, and log
viewing — it calls into the native layer and renders what comes back, it
does not implement retry/queueing/telephony logic itself.

## Code conventions

- **Don't add code comments by default.** Write self-explanatory code (clear
  names, small functions) instead. Add a comment _only_ when it earns its place —
  e.g. a non-obvious "why", a genuine gotcha, an API/OS quirk, or a
  deliberate-looking-wrong decision. No restating what the code already says, no
  section-header banners, no doc blocks on every class/function.
- This applies to Kotlin and Dart alike. When in doubt, leave the comment out.

## Commit conventions

- **Never** add a `Co-Authored-By` trailer (no "Co-Authored-By: Claude" or any
  other) to commit messages.
- Prefer a **concise one-line subject**; add a body only when the change genuinely
  needs explaining.
- Scope commits by concern (one logical change per commit) and commit when the
  user asks.

## Design docs

Architecture and roadmap live in `plan.md` and `docs/` (`architecture.md`,
`folder-structure.md`, `milestones.md`, `pairing.md`, `pitfalls.md`). The node is
backend-agnostic and Flutter is UI-only (see above).
