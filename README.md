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

## Backend SDK (`@luno-oss/*`)

The server side of the protocol ships as npm packages, so you do not have to
implement pairing, the WebSocket session or the event/ack semantics yourself.

| Package                                                                            | Purpose                                             |
| ---------------------------------------------------------------------------------- | --------------------------------------------------- |
| [`@luno-oss/core`](https://www.npmjs.com/package/@luno-oss/core)                     | Framework-independent engine                        |
| [`@luno-oss/protocol`](https://www.npmjs.com/package/@luno-oss/protocol)             | Zero-dependency wire types and codecs               |
| [`@luno-oss/testing`](https://www.npmjs.com/package/@luno-oss/testing)               | Conformance suites and a scriptable fake node       |
| [`@luno-oss/hono`](https://www.npmjs.com/package/@luno-oss/hono)                     | Hono adapter (Node, Workers, Deno, Bun)             |
| [`@luno-oss/express`](https://www.npmjs.com/package/@luno-oss/express)               | Express adapter                                     |
| [`@luno-oss/fastify`](https://www.npmjs.com/package/@luno-oss/fastify)               | Fastify plugin                                      |
| [`@luno-oss/nestjs`](https://www.npmjs.com/package/@luno-oss/nestjs)                 | NestJS module                                       |
| [`@luno-oss/cloudflare`](https://www.npmjs.com/package/@luno-oss/cloudflare)         | Cloudflare Workers adapter                          |
| [`@luno-oss/store-postgres`](https://www.npmjs.com/package/@luno-oss/store-postgres) | Durable Postgres store                              |

```
npm install @luno-oss/core @luno-oss/hono hono
```

Sources live in [`packages/`](packages/); releases are documented in
[`docs/releasing.md`](docs/releasing.md).

## Getting started

```
flutter pub get
flutter run --flavor full
```

Requires a physical Android device or emulator with telephony/SIM support
for SMS features (the emulator can send SMS to another emulator instance for
local testing, but cannot use a real SIM).

### Build flavors

A `--flavor` is required. `full` is the complete node (send + receive).
`sendOnly` drops the `RECEIVE_SMS` permission so the APK installs without
Google Play Protect's "this app can request access to sensitive data" warning,
at the cost of inbound capture.

Installing the `full` flavor via `adb install`, managed Google Play, or the Play
Store avoids that warning too — it only applies to installs from a browser,
messaging app, or file manager. See [`docs/play-protect.md`](docs/play-protect.md).
