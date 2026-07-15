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

/// One active SIM subscription, mirrored from the native domain model
/// (`model/SimInfo.kt`) at the bridge boundary. The MSISDN is intentionally not
/// carried (see M4 notes: it needs READ_PHONE_NUMBERS and is usually null).
class SimInfo {
  SimInfo({
    required this.subscriptionId,
    required this.slotIndex,
    required this.carrierName,
    required this.displayName,
    required this.isEmbedded,
    required this.simState,
  });

  final int subscriptionId;
  final int slotIndex;
  final String carrierName;
  final String displayName;
  final bool isEmbedded;
  final String simState;
}

/// Battery snapshot (M5), mirrored from `model/BatteryStatus.kt`.
class BatteryStatus {
  BatteryStatus({
    required this.levelPercent,
    required this.isCharging,
    required this.plugged,
    required this.health,
  });

  final int levelPercent;
  final bool isCharging;
  final String plugged;
  final String health;
}

/// Coalesced device telemetry (M4+), mirrored from `model/DeviceState.kt`. One
/// query and one stream carry all read-only device state; later milestones add
/// fields (signal, network) without new channels.
class DeviceState {
  DeviceState({required this.sims, this.battery});

  final List<SimInfo> sims;
  final BatteryStatus? battery;
}

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

  /// Current coalesced device telemetry (M4 SIMs, M5 battery, …). SIMs are
  /// empty without the phone permission or with no SIM; battery is null until
  /// the first reading — never throws.
  DeviceState getDeviceState();

  /// Whether READ_PHONE_STATE (needed to read SIM info) is granted.
  bool hasPhonePermission();

  /// Prompts for READ_PHONE_STATE. On grant, native starts SIM monitoring and
  /// the sim-change EventChannel emits, so the UI can re-query [getSimInfo].
  void requestPhonePermission();
}
