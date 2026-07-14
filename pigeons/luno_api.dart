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
}
