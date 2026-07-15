import 'package:flutter/services.dart';

import 'generated/luno_api.g.dart';

/// Mirror of the native `AgentState` enum. `unknown` covers any future/native
/// value the UI doesn't recognise, so an added state never crashes the stream.
enum AgentRunState { stopped, running, unknown }

AgentRunState _agentStateFromName(String name) {
  switch (name) {
    case 'STOPPED':
      return AgentRunState.stopped;
    case 'RUNNING':
      return AgentRunState.running;
    default:
      return AgentRunState.unknown;
  }
}

/// The single Dart-side door to the native agent.
///
/// Wraps the Pigeon-generated [LunoHostApi] for request/response calls and
/// exposes native push streams (delivered over hand-written [EventChannel]s) as
/// typed Dart [Stream]s. Features must go through this wrapper and never touch
/// platform channels directly, keeping the Flutter<->native boundary auditable
/// in one place.
///
/// M2 slice: [ping] (round-trip) and [tickEvents] (native 1 Hz push).
/// M3 slice: [startAgent]/[stopAgent]/[isAgentRunning] and [agentStateEvents]
/// (the foreground-service running state).
/// M4/M5 slice: [getDeviceState]/[hasPhonePermission]/[requestPhonePermission]
/// and [deviceStateEvents] (a signal to re-query coalesced telemetry).
class LunoBridge {
  LunoBridge({
    LunoHostApi? hostApi,
    EventChannel? tickChannel,
    EventChannel? agentStateChannel,
    EventChannel? deviceStateChannel,
  })  : _hostApi = hostApi ?? LunoHostApi(),
        _tickChannel = tickChannel ?? const EventChannel(tickChannelName),
        _agentStateChannel =
            agentStateChannel ?? const EventChannel(agentStateChannelName),
        _deviceStateChannel =
            deviceStateChannel ?? const EventChannel(deviceStateChannelName);

  /// Must match [FlutterEventBridge.CHANNEL_NAME] on the native side.
  static const String tickChannelName = 'com.luno.gateway/events/tick';

  /// Must match [AgentStateChannel.CHANNEL_NAME] on the native side.
  static const String agentStateChannelName =
      'com.luno.gateway/events/agent_state';

  /// Must match [DeviceStateChannel.CHANNEL_NAME] on the native side.
  static const String deviceStateChannelName =
      'com.luno.gateway/events/device_state';

  final LunoHostApi _hostApi;
  final EventChannel _tickChannel;
  final EventChannel _agentStateChannel;
  final EventChannel _deviceStateChannel;

  /// Round-trips [message] through the native HostApi and returns the
  /// transformed echo. Proves the Dart->Kotlin bridge is live and carries data.
  Future<String> ping(String message) => _hostApi.ping(message);

  /// Native-originated tick counter (~1 Hz). The native side emits only while
  /// this stream has a listener, and each `listen` restarts the count from 0.
  Stream<int> get tickEvents =>
      _tickChannel.receiveBroadcastStream().map((event) => event as int);

  /// Starts the gateway foreground service. User-initiated; safe to call when
  /// already running. Observe [agentStateEvents] for the resulting state.
  Future<void> startAgent() => _hostApi.startAgent();

  /// Stops the gateway foreground service.
  Future<void> stopAgent() => _hostApi.stopAgent();

  /// One-shot snapshot of whether the agent is running, for initial paint.
  Future<bool> isAgentRunning() => _hostApi.isAgentRunning();

  /// Prompts for the Android 13+ notification permission (no-op otherwise).
  Future<void> requestNotificationPermission() =>
      _hostApi.requestNotificationPermission();

  /// The agent's running state, pushed from native. Emits the current state
  /// immediately on subscribe (snapshot-then-stream), then on every change.
  Stream<AgentRunState> get agentStateEvents => _agentStateChannel
      .receiveBroadcastStream()
      .map((event) => _agentStateFromName(event as String));

  /// Current coalesced device telemetry (SIMs, battery, …). SIMs are empty
  /// without the phone permission or with no SIM; battery is null until read.
  Future<DeviceState> getDeviceState() => _hostApi.getDeviceState();

  /// Whether READ_PHONE_STATE is granted (needed to read SIM info).
  Future<bool> hasPhonePermission() => _hostApi.hasPhonePermission();

  /// Prompts for READ_PHONE_STATE. On grant, [deviceStateEvents] fires so the
  /// caller can re-query [getDeviceState].
  Future<void> requestPhonePermission() => _hostApi.requestPhonePermission();

  /// Fires whenever any device-telemetry slice changes (and once on subscribe).
  /// Carries only a revision counter — re-query [getDeviceState] for the data.
  Stream<int> get deviceStateEvents =>
      _deviceStateChannel.receiveBroadcastStream().map((event) => event as int);
}
