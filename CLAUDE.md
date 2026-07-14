# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

This repo is the Android app for **Luno**, a self-hosted SMS gateway agent.
An Android phone runs a long-lived background service that sends/receives
SMS and reports device health (SIM, signal, battery, delivery status) to a
NestJS backend over WebSocket (REST as fallback). Flutter is the dashboard
UI only — it never talks to telephony APIs directly.

**Scope decision: Android only, for now.** Linux/Windows "nodes" would
require external USB GSM modems and AT-command integration (no shared code
with the Android telephony layer); macOS has no cellular hardware path at
all. Both are explicitly out of scope until the Android app is solid. Don't
add Linux/Windows/macOS platform code or dependencies without being asked.

The backend (NestJS) and dashboard packages are separate repositories, not
part of this codebase.

## State of the repo

This is currently a stock `flutter create` scaffold (`lib/main.dart` is the
default counter-app template, `applicationId`/`namespace` is still
`com.example.sms_gateway`). No telephony integration, Pigeon interface,
networking, or backend communication exists yet — all of it is greenfield.

## Commands

```
flutter pub get              # install dependencies
flutter run                  # run on a connected device/emulator
flutter build apk            # build a release APK
flutter analyze              # static analysis (uses analysis_options.yaml / flutter_lints)
flutter test                 # run tests
flutter test test/some_test.dart   # run a single test file
```

SMS send/receive requires a real device or two emulator instances (emulator
SMS can target another emulator's port but not a real SIM/carrier).

## Architecture (target design)

```
NestJS backend  <—WebSocket/REST—>  Flutter (UI: dashboard, pairing, settings, logs)
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
