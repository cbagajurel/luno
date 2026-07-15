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
  names, small functions) instead. Add a comment *only* when it earns its place —
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
`folder-structure.md`, `milestones.md`, `pitfalls.md`). Build one milestone at a
time; don't skip ahead. The node is backend-agnostic and Flutter is UI-only (see
above).
