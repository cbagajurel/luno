// Pigeon interface definition — the single, reviewed source of the
// Flutter<->native contract. Pigeon reads this file and generates type-safe
// Dart and Kotlin. Do not edit the generated output by hand; edit this schema
// and re-run:
//
//   dart run pigeon --input pigeons/luno_api.dart
//
// M2 scope: a trivial round-trip (`ping`) proving the Dart->Kotlin HostApi
// bridge. Native->Dart push (the 1 Hz tick) is delivered over a hand-written
// EventChannel (see FlutterEventBridge.kt / LunoBridge.tickEvents), not Pigeon,
// so the EventChannel subscribe/cancel lifecycle is explicit.
import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/bridge/generated/luno_api.g.dart',
    kotlinOut:
        'android/app/src/main/kotlin/com/luno/gateway/bridge/generated/LunoApi.g.kt',
    kotlinOptions: KotlinOptions(package: 'com.luno.gateway.bridge.generated'),
    dartPackageName: 'sms_gateway',
  ),
)
/// Dart -> Kotlin commands/queries. Implemented natively by [LunoHostApiImpl].
@HostApi()
abstract class LunoHostApi {
  /// Round-trip smoke test: the native side echoes [message] back inside a
  /// recognizable envelope so the caller can prove the bridge is live and that
  /// data crosses it intact. Returns the transformed string.
  String ping(String message);

  /// Starts the gateway foreground service (M3). User-initiated, so the FGS
  /// start is allowed even on Android 12+. Idempotent: starting a running
  /// agent is a no-op. The running state arrives via the agent-state
  /// EventChannel, not as a return value.
  void startAgent();

  /// Requests the gateway foreground service stop and leave the foreground.
  void stopAgent();

  /// Snapshot of whether the agent is currently running, for the initial UI
  /// paint before the agent-state stream has emitted.
  bool isAgentRunning();

  /// Prompts for POST_NOTIFICATIONS (Android 13+) so the persistent
  /// notification is visible. No-op on older versions or if already granted.
  /// The agent runs regardless; this only affects notification visibility.
  void requestNotificationPermission();
}
