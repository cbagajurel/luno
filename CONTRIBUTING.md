# Contributing to Luno

Thanks for your interest in Luno — a self-hosted, backend-agnostic SMS gateway
agent for Android. This document describes how we work so contributions land
smoothly.

## Ground rules

Luno has a few load-bearing architectural rules. Read `CLAUDE.md`, `plan.md`,
and the `docs/` design docs before writing code — they explain the *why*.

1. **Flutter is UI-only.** No telephony, sockets, queue, or retry logic in Dart.
   Anything that must survive the UI process dying lives in native Kotlin under
   `android/app/src/main/`.
2. **Native over plugins.** The agent calls Android telephony APIs directly; no
   community SMS plugins. Third-party libraries are allowed only where they are
   load-bearing infrastructure (OkHttp, Room). The Flutter UI layer may use a
   conventional code-gen stack (Riverpod, go_router, freezed).
3. **Backend-agnostic.** The node prescribes no server technology and hardcodes
   no endpoints. It depends only on the versioned wire protocol.
4. **Durability before delivery.** Persist to disk *before* acting or acking.
5. **Android only, for now.** Don't add Linux/Windows/macOS platform code or
   dependencies without being asked.

## Milestone-by-milestone

Work is organized into independently testable milestones (`docs/milestones.md`).
Each milestone must be *finished and verified* before the next begins — don't
skip ahead. Every PR should map to a milestone (or a well-scoped slice of one)
and satisfy that milestone's testing checklist.

## Development

```
flutter pub get              # install dependencies
flutter run                  # run on a connected device/emulator
flutter analyze              # static analysis (must be clean)
flutter test                 # run tests
flutter build apk            # build a release APK
```

Gradle checks (run from `android/`):

```
./gradlew lint               # Android lint (must be clean)
./gradlew assembleDebug      # debug build
```

SMS send/receive requires a real device or two emulator instances — emulator
SMS can target another emulator's port but not a real SIM/carrier.

## Before you open a PR

- `flutter analyze` is clean.
- `flutter test` passes.
- Android `lint` is clean and the app builds.
- The relevant milestone's testing checklist is satisfied.
- No stray `com.example` references (`grep -r com.example`).

CI runs these same checks on every push and pull request.

## Commit conventions

- **Never** add a `Co-Authored-By` trailer.
- Prefer a **concise one-line subject**; add a body only when the change
  genuinely needs explaining.
- Scope commits by concern — one logical change per commit.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE).
