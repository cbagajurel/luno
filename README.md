# Luno (Android)

Luno turns an Android device into a secure, self-hosted SMS gateway node. The
phone runs a long-lived background agent that sends/receives SMS, reports
delivery status, and reports device health (SIM, signal, battery) to a
backend over WebSocket, with REST as a fallback.

This repository is the **Android app** (Flutter shell + native Android
telephony layer). The backend and dashboard live separately; the node is
**backend-agnostic** and only speaks the Luno wire protocol, so any server
(Firebase, Supabase, Express, Next, Nest, …) that implements it works — the
backend's tech stack is out of scope here.

Focus for this phase is **Android only**. Linux/Windows GSM-modem nodes,
macOS, and web dashboard are future-roadmap items and are not being built
here yet — see `CLAUDE.md` for the reasoning.

## Capabilities (target)

- Send / receive SMS
- Delivery reports
- SIM information, signal strength, battery status
- Heartbeat monitoring
- Secure authentication, pairing
- Automatic retries
- WebSocket connection with REST API fallback
- Auto-start after reboot (foreground service + boot receiver)

## Architecture

```
Backend (any Luno-protocol server)
     │ WebSocket / REST
Flutter (dashboard, pairing, settings, logs)
     │ Platform Channel / Pigeon
Native Android layer (SmsManager, TelephonyManager, SubscriptionManager,
                       BroadcastReceivers, ForegroundService, BootReceiver,
                       WorkManager)
     │
Android Telephony / SIM
     │
Recipient
```

Flutter is UI only — it does not talk to telephony APIs directly. All SMS
send/receive, SIM/signal/battery reads, and reboot persistence happen in the
native Android layer and are exposed to Flutter via Pigeon-generated
platform channels.

## Getting started

```
flutter pub get
flutter run
```

Requires a physical Android device or emulator with telephony/SIM support
for SMS features (the emulator can send SMS to another emulator instance for
local testing, but cannot use a real SIM).
