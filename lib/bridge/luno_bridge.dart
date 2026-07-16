import 'package:flutter/services.dart';

import 'generated/luno_api.g.dart';

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

/// The single Dart-side door to the native agent. Features go through this
/// wrapper and never touch platform channels directly.
class LunoBridge {
  LunoBridge({
    LunoHostApi? hostApi,
    EventChannel? tickChannel,
    EventChannel? agentStateChannel,
    EventChannel? deviceStateChannel,
    EventChannel? outboxChannel,
    EventChannel? inboxChannel,
  })  : _hostApi = hostApi ?? LunoHostApi(),
        _tickChannel = tickChannel ?? const EventChannel(tickChannelName),
        _agentStateChannel =
            agentStateChannel ?? const EventChannel(agentStateChannelName),
        _deviceStateChannel =
            deviceStateChannel ?? const EventChannel(deviceStateChannelName),
        _outboxChannel =
            outboxChannel ?? const EventChannel(outboxChannelName),
        _inboxChannel = inboxChannel ?? const EventChannel(inboxChannelName);

  // Channel names must match the native side.
  static const String tickChannelName = 'com.luno.gateway/events/tick';
  static const String agentStateChannelName =
      'com.luno.gateway/events/agent_state';
  static const String deviceStateChannelName =
      'com.luno.gateway/events/device_state';
  static const String outboxChannelName = 'com.luno.gateway/events/outbox';
  static const String inboxChannelName = 'com.luno.gateway/events/inbox';

  final LunoHostApi _hostApi;
  final EventChannel _tickChannel;
  final EventChannel _agentStateChannel;
  final EventChannel _deviceStateChannel;
  final EventChannel _outboxChannel;
  final EventChannel _inboxChannel;

  Future<String> ping(String message) => _hostApi.ping(message);

  Stream<int> get tickEvents =>
      _tickChannel.receiveBroadcastStream().map((event) => event as int);

  Future<void> startAgent() => _hostApi.startAgent();

  Future<void> stopAgent() => _hostApi.stopAgent();

  Future<bool> isAgentRunning() => _hostApi.isAgentRunning();

  Future<void> requestNotificationPermission() =>
      _hostApi.requestNotificationPermission();

  /// Emits the current state immediately on subscribe, then on every change.
  Stream<AgentRunState> get agentStateEvents => _agentStateChannel
      .receiveBroadcastStream()
      .map((event) => _agentStateFromName(event as String));

  Future<DeviceState> getDeviceState() => _hostApi.getDeviceState();

  Future<bool> hasPhonePermission() => _hostApi.hasPhonePermission();

  Future<void> requestPhonePermission() => _hostApi.requestPhonePermission();

  /// Fires on any telemetry change (and once on subscribe); carries only a
  /// revision counter — re-query [getDeviceState] for the data.
  Stream<int> get deviceStateEvents =>
      _deviceStateChannel.receiveBroadcastStream().map((event) => event as int);

  Future<bool> hasSmsPermission() => _hostApi.hasSmsPermission();

  Future<void> requestSmsPermission() => _hostApi.requestSmsPermission();

  /// Enqueues a send and returns the durable message id; observe [outboxEvents]
  /// and [getRecentOutbox] for the QUEUED→SENDING→SENT/FAILED progression.
  Future<String> sendSms(String recipient, String body, {int? subscriptionId}) =>
      _hostApi.sendSms(recipient, body, subscriptionId);

  Future<List<OutboxEntry>> getRecentOutbox() => _hostApi.getRecentOutbox();

  /// Ticks a revision counter on any outbox change; re-query [getRecentOutbox].
  Stream<int> get outboxEvents =>
      _outboxChannel.receiveBroadcastStream().map((event) => event as int);

  Future<bool> hasReceiveSmsPermission() => _hostApi.hasReceiveSmsPermission();

  Future<void> requestReceiveSmsPermission() =>
      _hostApi.requestReceiveSmsPermission();

  Future<List<InboundEntry>> getRecentInbox() => _hostApi.getRecentInbox();

  /// Ticks a revision counter when a message arrives; re-query [getRecentInbox].
  Stream<int> get inboxEvents =>
      _inboxChannel.receiveBroadcastStream().map((event) => event as int);
}
