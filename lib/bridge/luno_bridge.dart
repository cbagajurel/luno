import 'package:flutter/services.dart';

import 'generated/luno_api.g.dart';

/// The single Dart-side door to the native agent.
///
/// Wraps the Pigeon-generated [LunoHostApi] for request/response calls and
/// exposes native push streams (delivered over hand-written [EventChannel]s) as
/// typed Dart [Stream]s. Features must go through this wrapper and never touch
/// platform channels directly, keeping the Flutter<->native boundary auditable
/// in one place.
///
/// M2 slice: [ping] (round-trip) and [tickEvents] (native 1 Hz push).
class LunoBridge {
  LunoBridge({LunoHostApi? hostApi, EventChannel? tickChannel})
      : _hostApi = hostApi ?? LunoHostApi(),
        _tickChannel = tickChannel ?? const EventChannel(tickChannelName);

  /// Must match [FlutterEventBridge.CHANNEL_NAME] on the native side.
  static const String tickChannelName = 'com.luno.gateway/events/tick';

  final LunoHostApi _hostApi;
  final EventChannel _tickChannel;

  /// Round-trips [message] through the native HostApi and returns the
  /// transformed echo. Proves the Dart->Kotlin bridge is live and carries data.
  Future<String> ping(String message) => _hostApi.ping(message);

  /// Native-originated tick counter (~1 Hz). The native side emits only while
  /// this stream has a listener, and each `listen` restarts the count from 0.
  Stream<int> get tickEvents =>
      _tickChannel.receiveBroadcastStream().map((event) => event as int);
}
