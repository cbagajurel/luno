# Luno — Milestones

> Companion to [`../plan.md`](../plan.md). Phases are themes; **milestones are
> the unit of work.** Each is independently testable and must be *finished*
> before the next begins (Step 7 rule). Milestones map onto phases but are
> finer-grained.

Legend for permissions: 🟢 normal · 🟡 special/appops · 🔴 dangerous (runtime).

**Status (2026-07-16): M1–M10 complete. Next up: M11.**

| # | Milestone | Phase | Status | Independently testable by |
|---|---|---|---|---|
| M1 | Project identity & hygiene | 0 | ✅ done | App builds under new package; CI green |
| M2 | Pigeon bridge: "hello from Kotlin" | 1 | ✅ done | Round-trip call + event visible |
| M3 | Foreground service that stays alive | 1 | ✅ done | Notification persists after swipe |
| M4 | SIM information | 2 | ✅ done | Live SIM list in UI, multi-SIM correct |
| M5 | Battery status | 2 | ✅ done | Live battery in UI, updates on unplug |
| M6 | Signal strength | 2 | ✅ done | Live signal in UI, updates in a Faraday-ish spot |
| M7 | Network state | 2 | ✅ done | Connectivity flips on airplane mode |
| M8 | Durable queue + transport interface | 3 | ✅ done | State machine tests w/ FakeTransport; survives kill |
| M9 | Send SMS (single-part) | 4 | ✅ done | UI button sends a real SMS, reaches SENT |
| M10 | Multipart + multi-SIM send + delivery reports | 4 | ✅ done | Long SMS from chosen SIM reaches DELIVERED |
| M11 | Receive SMS | 5 | ⬜ next | Inbound SMS captured with app closed |
| M12 | Wire protocol codec + connection SM | 6 | ⬜ todo | Codec round-trip tests; SM transitions |
| M13 | Pairing/auth + WebSocket connect | 6 | ⬜ todo | Node enrolls and reaches READY |
| M14 | Protocol wired to SMS + heartbeat | 6 | ⬜ todo | Backend command → SMS → events back |
| M15 | Boot + WorkManager + resync | 7 | ⬜ todo | Reboot/offline/kill → lossless recovery |
| M16 | Security hardening | 8 | ⬜ todo | Threat-model checklist passes |
| M17 | Flutter dashboard | 9 | ⬜ todo | Operator runs a node from the UI |
| M18 | Observability, tests, release | 10 | ⬜ todo | Signed v1.0 APK, docs, E2E on real devices |

---

## M1 — Project identity & hygiene

**Files:** rename `android/app/src/main/kotlin/com/example/sms_gateway/` →
`com/luno/gateway/`; edit `android/app/build.gradle.kts` (`namespace`,
`applicationId`), `AndroidManifest.xml` (`android:label="Luno"`), `MainActivity.kt`
package; add `LICENSE`, `CONTRIBUTING.md`, `.github/workflows/ci.yml`,
`docs/protocol` sign-off note in `plan.md`.
**Classes:** none new (rename only).
**Android APIs:** none.
**Permissions:** none.
**Flutter integration:** app title/theme already say "Luno"; verify.
**Backend integration:** none.
**Testing checklist:**
- [ ] `flutter run` launches; About shows `com.luno.gateway`.
- [ ] `flutter analyze` + Gradle `lint` clean; CI green on a trivial PR.
- [ ] No stray `com.example` references (`grep -r com.example`).
**Edge cases:** IDE run configs referencing the old package; `local.properties`;
Gradle caches holding old namespace (clean build).

---

## M2 — Pigeon bridge: "hello from Kotlin"

**Files:** `pigeons/luno_api.dart` (schema); generated
`lib/bridge/generated/*` + `android/.../bridge/generated/*`;
`lib/bridge/luno_bridge.dart`; `android/.../bridge/LunoHostApiImpl.kt`;
`android/.../bridge/FlutterEventBridge.kt`; register in `MainActivity.kt`.
**Classes:** `LunoHostApi` (Pigeon), `LunoHostApiImpl`, `FlutterEventBridge`,
`LunoBridge` (Dart wrapper).
**Android APIs:** Flutter platform channels (Pigeon), `EventChannel`.
**Permissions:** none.
**Flutter integration:** call `ping("hi")` → get reply; subscribe to an
`EventChannel` that native ticks every second → render count.
**Backend integration:** none.
**Testing checklist:**
- [ ] `HostApi.ping()` returns the expected transformed string.
- [ ] Native-initiated events arrive in Dart in order.
- [ ] Bridge calls don't block the main thread (no jank/ANR).
**Edge cases:** engine attach/detach on Activity recreate; event emitted before
Dart subscribes (buffer or drop policy defined); Pigeon codec version mismatch.

---

## M3 — Foreground service that stays alive

**Files:** `agent/GatewayForegroundService.kt`, `agent/ServiceNotification.kt`,
`agent/AgentController.kt` (skeleton), `di/AgentGraph.kt`; manifest `<service>`
with `foregroundServiceType`; `logging/LunoLogger.kt` + `LogSink` (logcat).
**Classes:** `GatewayForegroundService`, `ServiceNotification`, `AgentController`,
`AgentGraph`, `LunoLogger`.
**Android APIs:** `Service`/`LifecycleService`, `startForeground`,
`NotificationChannel`, `NotificationManager`.
**Permissions:** 🟢 `FOREGROUND_SERVICE`, 🟢 `POST_NOTIFICATIONS` (API 33+,
runtime), the specific 🟢 `FOREGROUND_SERVICE_*` subtype perm for the chosen type.
**Flutter integration:** `HostApi.startAgent()/stopAgent()`; UI shows service
running state via EventChannel.
**Backend integration:** none.
**Testing checklist:**
- [ ] Service enters foreground with a persistent, low-importance notification.
- [ ] Survives swiping the app from Recents (notification remains, logcat alive).
- [ ] Restarts per `START_STICKY` semantics after a system kill (observe).
- [ ] `POST_NOTIFICATIONS` denial handled (service still runs, prompt shown).
**Edge cases:** Android 14 FGS-type enforcement (wrong/missing type = crash);
notification dismissed by user on some OEMs; battery saver; starting FGS from
background contexts later (allowed here because user-initiated).

---

## M4 — SIM information

**Files:** `telephony/SimInfoManager.kt`, `model/SimInfo.kt`,
`model/DeviceState.kt`; bridge query + stream; `features/dashboard/`.
**Classes:** `SimInfoManager`, `SimInfo`, `DeviceState`.
**Android APIs:** `SubscriptionManager.getActiveSubscriptionInfoList()`,
`SubscriptionManager.OnSubscriptionsChangedListener`, `TelephonyManager`
(per-subId via `createForSubscriptionId`).
**Permissions:** 🔴 `READ_PHONE_STATE` (and 🔴 `READ_PHONE_NUMBERS` if we surface
the MSISDN, which is often unavailable anyway).
**Flutter integration:** dashboard renders each SIM: carrier, slot, subId, state.
**Backend integration:** feeds `device_status` later (not yet).
**Testing checklist:**
- [ ] Correct on single-SIM, dual-SIM, and eSIM devices.
- [ ] Live update on SIM insert/remove (`OnSubscriptionsChangedListener`).
- [ ] Graceful when permission denied and when no SIM present.
- [ ] Correct across API 26 / 29 / 31 / latest (subscription API changed at 29+).
**Edge cases:** phone number frequently `null`/empty; carrier privileges vs
permission; airplane mode hiding subscriptions; embedded (eSIM) profiles;
`READ_PHONE_STATE` restricted fields on Android 10+.

---

## M5 — Battery status

**Files:** `telephony/BatteryMonitor.kt`; extend `DeviceState`.
**Classes:** `BatteryMonitor`.
**Android APIs:** `BatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)`,
sticky `ACTION_BATTERY_CHANGED`, `BatteryManager.isCharging`.
**Permissions:** none.
**Flutter integration:** battery % + charging + health in dashboard.
**Backend integration:** part of heartbeat later.
**Testing checklist:**
- [ ] Level/charging update live on plug/unplug.
- [ ] Correct when charging over USB vs AC vs wireless.
- [ ] No busy-polling (event-driven or low-frequency sampling only).
**Edge cases:** emulator reports fixed values; some OEMs throttle the sticky
broadcast; very frequent broadcasts (debounce).

---

## M6 — Signal strength

**Files:** `telephony/SignalStrengthMonitor.kt`; extend `DeviceState`.
**Classes:** `SignalStrengthMonitor`.
**Android APIs:** `TelephonyManager.registerTelephonyCallback` +
`TelephonyCallback.SignalStrengthsListener` (API 31+); `PhoneStateListener`
(API <31, deprecated) fallback; `SignalStrength`/`CellSignalStrength`.
**Permissions:** 🔴 `READ_PHONE_STATE` (already granted in M4).
**Flutter integration:** dBm/level bars per SIM in dashboard.
**Backend integration:** part of heartbeat later.
**Testing checklist:**
- [ ] Updates as signal changes (walk into a weak-signal area / RF box).
- [ ] Per-subscription on multi-SIM.
- [ ] Correct callback vs listener path by API level; no deprecated path on 31+.
**Edge cases:** callbacks require a thread with a `Looper`/executor; rapid
updates (throttle); RSRP vs RSSI vs level differences across RATs (2G/3G/4G/5G);
values unavailable in airplane mode.

---

## M7 — Network state

**Files:** `telephony/NetworkMonitor.kt`; extend `DeviceState`.
**Classes:** `NetworkMonitor`.
**Android APIs:** `ConnectivityManager.registerDefaultNetworkCallback`,
`NetworkCapabilities` (validated internet, metered, transport type).
**Permissions:** 🟢 `ACCESS_NETWORK_STATE`.
**Flutter integration:** online/offline + transport (wifi/cellular) indicator.
**Backend integration:** drives the connection SM in M12+.
**Testing checklist:**
- [ ] Flips correctly on airplane mode, wifi off, cellular-only.
- [ ] Distinguishes "connected but no validated internet" (captive portal).
- [ ] Callback unregistered cleanly on service stop (no leak).
**Edge cases:** captive portals; VPNs; metered detection; rapid flapping
(debounce); callback fired before service fully initialized.

---

## M8 — Durable queue + transport interface

**Files:** `data/db/LunoDatabase.kt`, `dao/*`, `entity/*`;
`data/repository/OutboxRepository.kt`, `InboxRepository.kt`;
`transport/Transport.kt`, `TransportCapability.kt`, `TransportRegistry.kt`,
`transport/fake/FakeTransport.kt`; `model/OutboundMessage.kt`,
`InboundMessage.kt`, `SendHandle.kt`, `DomainError.kt`.
**Classes:** as above + status enums + idempotency-key helper (`util/Ids.kt`).
**Android APIs:** Room (SQLite), coroutines/Flow.
**Permissions:** none.
**Flutter integration:** debug screen shows queue depth (optional).
**Backend integration:** none — driven entirely by `FakeTransport`.
**Testing checklist:**
- [ ] Outbox state machine transitions match [`architecture.md §5`](architecture.md#5-sms-lifecycle-message-state-machines).
- [ ] Persist-before-act: crash between enqueue and send leaves recoverable state.
- [ ] Idempotency: replaying a command id does not double-enqueue.
- [ ] Dedupe: replaying an inbound id does not double-insert.
- [ ] Queue survives `kill -9`/process death (Robolectric + on-device).
**Edge cases:** DB migrations from day one (versioned schema); max queue depth
+ retention eviction; clock skew for `ts`; concurrent writers (single-writer
discipline or transactions); large bodies.

---

## M9 — Send SMS (single-part) — ✅ done

**Implemented:** `transport/sms/{SmsSender,SentReportRouter,SmsResultCodes,SmsTransport}.kt`
(single-part send via `SmsManager`, `sentIntent` `PendingIntent` correlated back to
the message, result codes mapped to the §10 error taxonomy);
`data/repository/OutboxDispatcher.kt` drives QUEUED→SENDING→SENT/FAILED through the
`TransportRegistry`; `SEND_SMS` runtime-permission flow (`MainActivity`,
`AgentHost`); Pigeon `sendSms`/`getRecentOutbox`/`hasSmsPermission`/
`requestSmsPermission` + `OutboxChannel`; Flutter debug "Send test SMS" control with a
recent-outbox status list. Unit tests: `SmsResultCodesTest`, `OutboxDispatcherTest`.
The real-device checkbox items below need a physical SIM to close out.

**Files:** `transport/sms/SmsTransport.kt`, `SmsSender.kt`,
`SentReportRouter.kt`; wire into `OutboxRepository`; debug send control.
**Classes:** `SmsTransport`, `SmsSender`, `SentReportRouter`.
**Android APIs:** `SmsManager.getDefault()` /
`SmsManager.createForSubscriptionId(subId)`, `sendTextMessage(...)` with a
`sentIntent` `PendingIntent`; result codes via `getResultCode()`.
**Permissions:** 🔴 `SEND_SMS` (runtime request flow).
**Flutter integration:** debug "send test SMS" (to number, body); status
streams QUEUED→SENDING→SENT/FAILED.
**Backend integration:** none yet.
**Testing checklist:**
- [ ] A real single-part SMS is sent and reaches SENT on a physical device.
- [ ] `sentIntent` result code correctly maps to SENT vs FAILED_* (taxonomy).
- [ ] Permission-denied path prompts, doesn't crash.
- [ ] Uses default SMS subscription when none specified.
**Edge cases:** radio off / no service → `TRANSIENT` retry; invalid number →
`TERMINAL`; airplane mode; `PendingIntent` mutability flags (API 31+ require
`FLAG_IMMUTABLE`); result delivered after process restart (durable correlation);
carrier length/encoding limits (GSM-7 vs UCS-2).

---

## M10 — Multipart + multi-SIM send + delivery reports — ✅ done

**Implemented:** `transport/sms/MultipartAssembler.kt` (`divideMessage`, GSM-7/UCS-2
aware) + `SmsSender.sendMultipart` (`sendMultipartTextMessage` with per-part sent
**and** delivery `PendingIntent`s, on the chosen SIM via `createForSubscriptionId`);
`SmsTransport` rolls all *sent* reports up into one `SendHandle` (all sent → SENT,
any failure → worst-case). Delivery: `DeliveryReportRouter` parses the TP-Status PDU
(`SmsDeliveryStatus.classify`) and emits `DeliveryReport`s on the new
`Transport.deliveryReports()` stream; a durable `outbox_part` table (DB **v2**,
`MIGRATION_1_2`) holds per-part state; `DeliveryTracker` folds reports in and rolls
the message up to DELIVERED/UNDELIVERED, with a `deliveryTimeout` flipping
never-reported parts to UNDELIVERED so nothing hangs (§7.3). SIM picker + per-part
"N/M delivered" shown in the debug UI. Tests: `OutboxDeliveryRollupTest`,
`DeliveryTrackerTest` (delivered / worst-case / timeout / untracked),
`SmsResultCodesTest` (delivery-status bands). Real dual-SIM + carrier delivery
reports still need physical devices to close out.

**Files:** `transport/sms/MultipartAssembler.kt` (outbound),
`DeliveryReportRouter.kt`; extend `SmsSender`.
**Classes:** `MultipartAssembler`, `DeliveryReportRouter`.
**Android APIs:** `SmsManager.divideMessage`, `sendMultipartTextMessage` with
`ArrayList<PendingIntent>` for sent + delivery; `createForSubscriptionId`.
**Permissions:** 🔴 `SEND_SMS` (already).
**Flutter integration:** SIM picker in the debug control; per-part status shown.
**Backend integration:** none yet (protocol carries `subscriptionId` later).
**Testing checklist:**
- [ ] >160-char message splits and every part reaches DELIVERED.
- [ ] Message rolls up to DELIVERED only when all parts delivered; worst-case
      otherwise.
- [ ] Correct physical SIM used on a dual-SIM device.
- [ ] Delivery report correlates to the exact part (unique request ids).
**Edge cases:** delivery reports arriving out of order / minutes later / never
(`deliveryTimeout`); some carriers never send delivery reports; UCS-2 shrinks
part size to 70 chars; one part fails while others succeed; per-part idempotency.

---

## M11 — Receive SMS

**Files:** `transport/sms/SmsReceiver.kt`, inbound path in
`MultipartAssembler.kt`; manifest `<receiver>`; `InboxRepository` wiring.
**Classes:** `SmsReceiver` (BroadcastReceiver).
**Android APIs:** `SMS_RECEIVED` action, `Telephony.Sms.Intents.getMessagesFromIntent`,
`goAsync()`, coroutine hand-off to the service.
**Permissions:** 🔴 `RECEIVE_SMS` (and 🔴 `READ_SMS` only if we read existing
threads — default: no).
**Flutter integration:** received-messages list (read-only mirror).
**Backend integration:** capture only; reporting happens in M14.
**Testing checklist:**
- [ ] Inbound single-part SMS captured with the app UI closed and screen locked.
- [ ] Multipart reassembles in order into one logical message.
- [ ] No ANR — heavy work off the broadcast thread (`goAsync` + timeout).
- [ ] Persisted to inbox before any further processing.
**Edge cases:** partial/never-completed multiparts (reassembly timeout, don't
leak); duplicate broadcasts; flash/class-0 SMS; data SMS (port-addressed);
receiver invoked while service is dead (start it); dedupe on
sender+timestamp+ref.

---

## M12 — Wire protocol codec + connection state machine

**Files:** `backend/protocol/Envelope.kt`, `Command.kt`, `Event.kt`, `Ack.kt`,
`Control.kt`, `ProtocolCodec.kt`; `agent/ConnectionStateMachine.kt`;
`backend/ws/ReconnectPolicy.kt`.
**Classes:** the protocol data classes, `ProtocolCodec`,
`ConnectionStateMachine`, `ReconnectPolicy`.
**Android APIs:** none (pure Kotlin); `kotlinx.serialization`.
**Permissions:** none.
**Flutter integration:** none (internal).
**Backend integration:** defines it; no live connection yet.
**Testing checklist:**
- [ ] Encode→decode round-trips every command/event/ack/control type.
- [ ] Unknown fields ignored; version negotiation picks correct `v`.
- [ ] Connection SM transitions (§6) covered by unit tests, incl. backoff+jitter.
- [ ] Backoff resets only after a *stable* READY.
**Edge cases:** malformed frames (quarantine, don't crash); forward/backward
compatibility (add a field, old code still parses); very large payloads;
duplicate seq handling; clock skew in `ts`.

---

## M13 — Pairing/auth + WebSocket connect

**Files:** `backend/auth/PairingManager.kt`, `DeviceCredentialStore.kt`;
`backend/ws/WebSocketClient.kt`; `backend/rest/RestClient.kt`;
`security/KeystoreManager.kt`; pairing UI in `features/pairing/`.
**Classes:** `PairingManager`, `DeviceCredentialStore`, `WebSocketClient`,
`RestClient`, `KeystoreManager`.
**Android APIs:** OkHttp (WS + HTTPS), Android Keystore,
`EncryptedSharedPreferences`.
**Permissions:** 🟢 `INTERNET`, 🟢 `ACCESS_NETWORK_STATE`.
**Flutter integration:** enter/scan pairing code; show connection state
(CONNECTING→READY) from the connection SM stream.
**Backend integration:** `POST /enroll`; WSS handshake with credential; needs a
stub or real backend.
**Testing checklist:**
- [ ] Enrollment stores a Keystore-bound credential (unreadable off-device).
- [ ] WSS connects and reaches AUTHENTICATED→READY with a valid credential.
- [ ] Invalid/expired credential → refused → re-enroll UX (no crash loop).
- [ ] WSS enforced (plain WS refused); optional cert pinning verified.
**Edge cases:** pairing code reuse/expiry; MITM (pinning); credential rotation
mid-session; captive portal blocking the handshake; token in memory not logged;
Keystore key invalidated (e.g., after biometric/lockscreen change) → recover.

---

## M14 — Protocol wired to SMS + heartbeat

**Files:** `agent/AgentController.kt` (full), `backend/ws/Heartbeat.kt`; connect
`OutboxRepository`/`InboxRepository` ↔ protocol events.
**Classes:** `AgentController` (orchestration), `Heartbeat`.
**Android APIs:** those from M9–M13 combined.
**Permissions:** union of prior.
**Flutter integration:** dashboard shows real end-to-end activity.
**Backend integration:** `send_sms` command → outbox → SMS → `sms_sent` +
`delivery_report` events; inbound → `sms_received`; periodic `heartbeat`.
**Testing checklist:**
- [ ] Backend `send_sms` results in a real SMS and correct events return.
- [ ] Inbound SMS surfaces as `sms_received`, acked exactly once (dedupe proven).
- [ ] Heartbeat drives backend online/offline correctly.
- [ ] Socket kill mid-exchange → no lost/dup messages after reconnect.
**Edge cases:** command redelivery (idempotency on command id); event storms
under backlog drain; heartbeat vs transport ping interaction; backend rejecting
out-of-policy recipients; ordering under reconnect.

---

## M15 — Boot + WorkManager + resync

**Files:** `receiver/BootReceiver.kt`; `work/OutboxDrainWorker.kt`,
`ReconnectWorker.kt`; resync logic in `AgentController`; manifest `<receiver>`.
**Classes:** `BootReceiver`, the workers, resync handler.
**Android APIs:** `BOOT_COMPLETED`, WorkManager (`OneTime`/`Periodic`,
constraints, backoff), `START_STICKY`.
**Permissions:** 🟢 `RECEIVE_BOOT_COMPLETED`, WorkManager-implied.
**Flutter integration:** "auto-start enabled" + health indicator.
**Backend integration:** resync handshake (§7.4) with last-acked cursors.
**Testing checklist:**
- [ ] Reboot → service auto-starts → re-auth → resync, unattended (after 1st
      launch + battery exemption).
- [ ] 1-hour airplane mode → queued sends drain correctly on return.
- [ ] Force-stop + redelivery storm → zero duplicate sends.
- [ ] WorkManager revives the link when the FGS was killed.
**Edge cases:** BOOT_COMPLETED not delivered to force-stopped/never-launched
apps; direct-boot (pre-unlock) storage; OEMs delaying/blocking boot receivers;
WorkManager 15-min periodic floor; Doze deferring workers; resync when backend
lost state too.

---

## M16 — Security hardening

**Files:** `security/CryptoBox.kt`, `RateLimiter.kt`; `logging/Redaction.kt`;
encrypted fields in entities; pinning in `WebSocketClient`; permission-revocation
handling across managers.
**Classes:** `CryptoBox`, `RateLimiter`, `Redaction`.
**Android APIs:** Keystore AEAD, `EncryptedSharedPreferences`, OkHttp
`CertificatePinner`, runtime-permission APIs, auto-reset/hibernation handling.
**Permissions:** no new; hardens existing.
**Flutter integration:** rate-limit + allowlist display; permission re-grant UX.
**Backend integration:** honors `config_update` rate-limit/allowlist; `revoke`/
`wipe`.
**Testing checklist:**
- [ ] No secrets/PII in logs or plaintext prefs (static scan + manual).
- [ ] Message bodies encrypted at rest; key Keystore-bound.
- [ ] Rate limit actually caps throughput; allowlist rejects bad recipients.
- [ ] `wipe` clears credential + queues; node returns to unpaired.
**Edge cases:** permission auto-reset for unused apps (Android 11+); app
hibernation; Keystore key invalidation; clock-based rate windows and skew;
pinning vs cert rotation (backup pins); root/tamper (document residual risk).

---

## M17 — Flutter dashboard

**Files:** flesh out `features/pairing`, `dashboard`, `messages`, `logs`,
`settings`; `state/*`; `bridge/native_events.dart`.
**Classes:** feature view models (Dart), providers/notifiers.
**Android APIs:** none new (consumes bridge).
**Permissions:** UI drives runtime-permission requests only.
**Flutter integration:** the whole point — pair, monitor, view logs, configure.
**Backend integration:** indirect via native.
**Testing checklist:**
- [ ] Operator pairs and runs a node entirely from the UI.
- [ ] Killing/reopening the UI never disturbs the running gateway (core test).
- [ ] Every displayed value has a single native source of truth (no Dart dup).
- [ ] Widget tests for each feature against faked bridge streams.
**Edge cases:** UI subscribing after streams started (snapshot-then-stream);
large log lists (virtualize); permission prompts mid-flow; theme/locale.

---

## M18 — Observability, tests, release

**Files:** `work/LogUploadWorker.kt`, `logging/LogSink` (Room + backend);
test suites (Kotlin/Robolectric/instrumentation/Dart/contract); `docs/*` guides;
signing config in `build.gradle.kts`; F-Droid metadata; CI release job.
**Classes:** `LogUploadWorker`, log sinks, test doubles.
**Android APIs:** WorkManager, Room; testing frameworks.
**Permissions:** none new.
**Flutter integration:** log viewer reads the ring buffer; "send logs" action.
**Backend integration:** throttled `log` events.
**Testing checklist:**
- [ ] E2E on ≥2 physical devices, ≥2 OEM skins, ≥3 API levels.
- [ ] Cold install → pair → send → receive → reboot → still working, unassisted.
- [ ] Reproducible signed APK; version tag `v1.0.0`.
- [ ] Docs let a stranger self-host + pair without reading source.
**Edge cases:** log volume/throttling; PII in uploaded logs (redaction verified);
OEM-specific failures documented (dontkillmyapp-class); signing key custody.

---

## How to run "independently testable"

- **M2–M8** need no backend and (M2–M3) no SIM: emulator is fine.
- **M9–M11** need a **real device or two emulator instances** (emulator↔emulator
  SMS works; real SIM/carrier does not on emulator). Budget two SIMs for
  multi-SIM (M10) and delivery-report testing.
- **M12–M14** need a **stub backend** (a tiny WS echo/dispatch server is enough)
  before a real backend exists; the frozen protocol (§8) is the contract, so any
  server — in any stack — that speaks it will do.
- **M15–M18** need real devices across OEMs — this is where reliability is truly
  proven and where OEM battery-killer behavior surfaces.
