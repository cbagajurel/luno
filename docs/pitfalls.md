# Luno — Architectural Pitfalls & How We Avoid Them

> Companion to [`../plan.md`](../plan.md). This is the "what will bite us" doc.
> Each item states the mistake, why it's tempting, the consequence, and the
> decision we've already made to avoid it. Read this before writing code — most
> of these are cheap to design around now and expensive to fix later.

The theme: **an SMS gateway is an appliance that must run untouched for months
on a hostile OS (aggressive battery management) carrying sensitive data (SMS
bodies) through a scarce, abuse-prone resource (a carrier SIM).** Most pitfalls
below are a corner cut against one of those realities.

---

## Tight coupling

**Mistake: WebSocket/queue/telephony logic living in Dart.**
Tempting because Flutter has nice WebSocket and HTTP packages and it feels like
one language. Consequence: the connection and queue die whenever the Flutter
engine is torn down (backgrounding, low memory, no UI after reboot) — the exact
moments a gateway must keep working. **Decision:** the entire agent is native
Kotlin in the foreground service; Dart is UI-only. The acceptance test is
"kill the Dart process, gateway keeps working." (See
[`architecture.md §1`](architecture.md#1-responsibility-split-who-owns-what).)

**Mistake: transports coupled directly to the backend client.**
Writing `SmsSender` that calls `webSocket.send(...)` directly. Consequence:
adding MMS means editing the socket code; adding a second node platform is
impossible. **Decision:** transports implement a neutral `Transport` interface
and speak only in domain models; the `AgentController` bridges transports ↔
protocol. The wire protocol — not a class hierarchy — is the real extension
seam ([`architecture.md §12`](architecture.md#12-extensibility--the-two-axes)).

**Mistake: SMS specifics leaking into the queue/protocol.**
`OutboxEntity` with a `pdu` column, protocol with SMS-only fields. Consequence:
MMS/USSD don't fit; schema churn. **Decision:** domain models
(`OutboundMessage`, `InboundMessage`) and the envelope are transport-neutral;
SMS specifics stay inside `transport/sms/`.

**Mistake: the service being a god object.**
`GatewayForegroundService` doing orchestration, telephony, and networking.
**Decision:** the service owns only *process lifetime*; `AgentController` owns
orchestration and is unit-testable without a running service.

---

## Scalability issues

- **Protocol not versioned from day one.** Adding `v` later is a breaking
  migration across every node and the backend. **Decision:** `v` + additive-only
  field rules are in v1 ([`architecture.md §8.5`](architecture.md#85-versioning-rules)).
- **Assuming one node.** The backend addresses a *fleet*; every frame carries
  `deviceId`, and idempotency is per-device. Designed in, not bolted on.
- **Assuming one transport / one SIM.** Multi-SIM and multi-transport are
  first-class in the model (`subscriptionId`, `TransportRegistry`), so we don't
  refactor the core to add them.
- **Unbounded queues.** An offline node fills disk forever. **Decision:** max
  depth + oldest-first retention + surfaced warning
  ([`architecture.md §11`](architecture.md#11-offline-behavior)).

---

## Performance problems

- **Work on the BroadcastReceiver thread.** SMS receivers have a ~10s ANR
  budget; reassembling/persisting/reporting inline will ANR under load.
  **Decision:** `goAsync()` + immediate hand-off to the service/coroutine
  (M11).
- **Busy-polling battery/signal.** Drains the very battery we monitor.
  **Decision:** event-driven callbacks (`TelephonyCallback`,
  `NetworkCallback`, sticky battery) with debouncing, never polling loops.
- **Chatty status events.** Sending a full `device_status` every second floods
  the socket and radio. **Decision:** cheap periodic `heartbeat` +
  event-driven full status only on change
  ([`architecture.md §7.2`](architecture.md#72-heartbeat)).
- **Bridge chatter / main-thread work.** Marshalling large or frequent payloads
  across Pigeon on the main thread janks the UI. **Decision:** EventChannel for
  streams, coalesced snapshots, heavy work off-main-thread.

---

## Android background limitations

This is where SMS gateways go to die. Treat OS hostility as the default.

- **Foreground service type (Android 14+).** An FGS without a valid
  `foregroundServiceType` **crashes on start**. A persistent SMS gateway matches
  none of the narrow standard types cleanly. **Decision (Phase 0):**
  `specialUse` with a documented justification (fallback `dataSync`/
  `remoteMessaging`), which also reinforces sideload/F-Droid distribution.
- **Background FGS-start restrictions (Android 12+).** You generally can't start
  an FGS from the background. **Decision:** start it from a user action (first
  launch) and from allowed exemptions (`BOOT_COMPLETED`); WorkManager revives
  it within its allowed windows.
- **BOOT_COMPLETED is not guaranteed.** It is *not* delivered to apps that were
  force-stopped or never launched, and some OEMs delay/withhold it.
  **Decision:** require one manual first launch; treat auto-start as
  best-effort + WorkManager backstop; document OEM autostart whitelisting.
- **Doze & App Standby.** Defer network, alarms, and jobs when idle.
  **Decision:** the FGS + real-time socket is the primary path (exempt while
  foreground); WorkManager is the deferred safety net, and we never *depend* on
  exact timing.
- **WorkManager periodic floor = 15 min.** Don't design "reconnect every 30s"
  as a periodic job. **Decision:** reconnection is driven by the socket SM +
  `NetworkCallback`; WorkManager only backstops longer gaps.

---

## Battery optimization concerns

- **Assuming the FGS is immortal.** It is not; the OS and (especially) OEM
  skins kill it. **Decision:** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` prompt +
  a health indicator that detects throttling, and honest docs that reliability
  requires the exemption. We design for *recovery* (resync), not for *never
  being killed*.
- **Notification importance / dismissal.** Too-low importance or a
  user-dismissible notification gets the FGS reaped on some OEMs. **Decision:**
  an ongoing notification on a dedicated channel; document that dismissing it
  degrades reliability.
- **Wake locks abuse.** Holding a partial wake lock 24/7 to "stay alive" murders
  the battery and still won't beat OEM killers. **Decision:** no long-held wake
  locks; rely on the FGS + push/heartbeat model and accept brief latency after
  Doze windows.

---

## Multi-SIM challenges

- **Hardcoding a subscription id / assuming slot order.** Slots renumber; eSIM
  profiles appear/disappear. **Decision:** always resolve via
  `SubscriptionManager`; the protocol's `send_sms` carries an explicit
  `subscriptionId`, defaulting to the system default SMS sub when absent.
- **DSDS radio contention.** On dual-SIM-dual-standby, only one radio may be
  fully active at a time; signal/data for the standby SIM can be stale.
  **Decision:** report per-sub state honestly (including "unknown/standby") and
  don't assume both SIMs are equally live.
- **Per-SIM permissions & phone numbers.** MSISDN is frequently unavailable;
  some fields need carrier privileges. **Decision:** treat phone number as
  best-effort, never a key; key on `subscriptionId`.

---

## Permission changes across Android versions

- **SMS permissions are dangerous and moving.** Runtime-granted, revocable, and
  **auto-reset for unused apps** (Android 11+), plus app *hibernation*.
  Consequence: a silently-revoked `SEND_SMS` throws `SecurityException` mid-send
  months later. **Decision:** wrap every telephony call in taxonomy-aware error
  handling (§10), detect revocation, surface a re-grant prompt, and request
  exemption from auto-reset where the OS allows.
- **API-level branches.** `PhoneStateListener`→`TelephonyCallback` at 31;
  subscription/READ_PHONE_STATE restrictions at 29/30; `POST_NOTIFICATIONS`
  runtime at 33; FGS types at 34; `PendingIntent` mutability flags at 31.
  **Decision:** centralize version branches in the managers, and set `minSdk 26`
  to cap the legacy surface.

---

## OEM-specific restrictions

- **The dontkillmyapp problem.** Xiaomi/MIUI, Huawei/EMUI, Oppo/ColorOS, Vivo,
  OnePlus, and Samsung aggressively kill background apps and gate autostart
  behind hidden settings. Consequence: the gateway silently dies on exactly the
  cheap devices most likely to be used as gateways. **Decision:** don't pretend
  we can beat this from code. Instead: (1) detect the OEM and deep-link to its
  autostart/battery settings, (2) surface a persistent "reliability at risk"
  health state to the backend when we detect kills/gaps, (3) ship an OEM setup
  guide, (4) rely on resync so a killed node recovers losslessly when it returns.
- **Manufacturer SMS quirks.** Some OEMs delay `SMS_RECEIVED`, some carriers
  never send delivery reports. **Decision:** delivery tracking is time-bounded
  (`deliveryTimeout` → UNDELIVERED-unknown) so messages never hang forever.

---

## Security risks

Threat model checklist (M16 gates on this):

- **PII in logs.** Phone numbers and bodies are sensitive. **Decision:** a single
  central `Redaction` function; bodies never logged at info+; full bodies live
  only in encrypted DB fields ([`architecture.md §9`](architecture.md#9-logging-strategy)).
- **Credentials at rest.** A stolen/rooted phone must not yield a reusable
  credential. **Decision:** Keystore-bound credential, unusable off-device;
  optional mTLS upgrade path; remote `wipe`.
- **Cleartext transport / MITM.** **Decision:** WSS/HTTPS enforced (plain
  refused); optional certificate pinning with backup pins.
- **The node as a spam cannon / SIM ban.** A compromised backend or bug could
  blast SMS, getting the SIM blocked or the operator billed. **Decision:**
  **backend-authoritative rate limits + recipient allowlists, enforced
  client-side too** (`RateLimiter`), independent of backend trust. This is a
  *safety* control, not just a policy one.
- **Message bodies at rest.** **Decision:** encrypt outbox/inbox bodies with a
  Keystore-wrapped key.
- **Replay / duplicate commands.** A replayed `send_sms` must not double-send.
  **Decision:** idempotency keys everywhere; dedupe on command/event id.
- **Pairing abuse.** **Decision:** short-lived, single-use pairing codes; enroll
  over HTTPS; codes expire.
- **Residual risk (documented, not solved):** a rooted device with the app
  unlocked can access decrypted data in memory; we document this rather than
  claim false guarantees.

---

## Process & delivery pitfalls (meta)

- **Building the UI first.** Tempting (it's visible), but it races a moving
  native target and invites logic drift into Dart. **Decision:** UI is Phase 9,
  after the agent works headless.
- **Wiring the backend before telephony works.** Mixes protocol bugs with radio
  bugs. **Decision:** SMS send/receive (Phases 4–5) proven via a debug button
  *before* the socket (Phase 6).
- **Skipping the durable queue "for now."** Every "we'll add persistence later"
  becomes a lost-message incident. **Decision:** the queue (Phase 3) precedes
  the first real send.
- **Testing only on a Pixel emulator.** Hides every OEM and real-carrier
  behavior. **Decision:** the release gate (M18) requires real devices across
  OEM skins, API levels, and two SIMs.
- **Play Store distribution assumption.** Google's SMS/Call-Log policy
  effectively bars a general-purpose SMS gateway. **Decision:** plan for
  sideload + F-Droid from the start; don't architect around a store review that
  won't come.

---

## Summary: the five decisions that prevent the most pain

1. **Headless native agent; Flutter is a detachable control panel.**
2. **Persist before you act; at-least-once + idempotency keys.**
3. **Versioned, transport-neutral wire protocol as the real extension point.**
4. **Design for being killed (recovery + resync), not for immortality.**
5. **Backend-authoritative-but-client-enforced rate limits, and PII never in
   logs.**

Everything else in these documents is downstream of these five.
