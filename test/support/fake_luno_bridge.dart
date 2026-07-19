import 'package:sms_gateway/bridge/generated/luno_api.g.dart';
import 'package:sms_gateway/bridge/luno_bridge.dart';

/// A configurable in-memory [LunoBridge] for widget/provider tests. Streams emit
/// their current value once (enough to drive snapshot-then-stream providers).
class FakeLunoBridge implements LunoBridge {
  FakeLunoBridge({
    this.paired = true,
    this.agentState = AgentRunState.running,
    this.connectionState = ConnectionState.ready,
    DeviceState? deviceState,
    this.outbox = const <OutboxEntry>[],
    this.inbox = const <InboundEntry>[],
    this.logs = const <LogEntry>[],
    this.phonePermission = PermissionStatus.granted,
    this.smsPermission = PermissionStatus.granted,
    this.receiveSmsPermission = PermissionStatus.granted,
    this.grantOnRequest = true,
    this.pairingResult,
  }) : deviceState =
           deviceState ??
           DeviceState(sims: const <SimInfo>[], signals: const <SignalInfo>[]);

  bool paired;
  AgentRunState agentState;
  ConnectionState connectionState;
  DeviceState deviceState;
  List<OutboxEntry> outbox;
  List<InboundEntry> inbox;
  List<LogEntry> logs;
  PermissionStatus phonePermission;
  PermissionStatus smsPermission;
  PermissionStatus receiveSmsPermission;

  /// When false, a request leaves the status untouched — the only way to
  /// exercise denial and blocked paths, which the old fake could not reach.
  bool grantOnRequest;
  PairingResult? pairingResult;

  final List<({String recipient, String body, int? subId})> sent = [];
  int unpairCalls = 0;
  int startAgentCalls = 0;
  int openAppSettingsCalls = 0;

  @override
  Future<String> ping(String message) async => message;

  @override
  Stream<int> get tickEvents => Stream.value(0);

  @override
  Future<void> startAgent() async => startAgentCalls++;

  @override
  Future<void> stopAgent() async {}

  @override
  Future<bool> isAgentRunning() async => agentState == AgentRunState.running;

  @override
  Future<void> requestNotificationPermission() async {}

  @override
  Stream<AgentRunState> get agentStateEvents => Stream.value(agentState);

  @override
  Future<DeviceState> getDeviceState() async => deviceState;

  @override
  Future<PermissionStatus> phonePermissionStatus() async => phonePermission;

  @override
  Future<PermissionStatus> requestPhonePermission() async =>
      phonePermission = _afterRequest(phonePermission);

  @override
  Stream<int> get deviceStateEvents => Stream.value(0);

  @override
  Future<PermissionStatus> smsPermissionStatus() async => smsPermission;

  @override
  Future<PermissionStatus> requestSmsPermission() async =>
      smsPermission = _afterRequest(smsPermission);

  @override
  Future<void> openAppSettings() async => openAppSettingsCalls++;

  /// Blocked never flips on request — that is the whole point of the state.
  PermissionStatus _afterRequest(PermissionStatus current) {
    if (current == PermissionStatus.blocked) return current;
    return grantOnRequest ? PermissionStatus.granted : current;
  }

  @override
  Future<String> sendSms(
    String recipient,
    String body, {
    int? subscriptionId,
  }) async {
    sent.add((recipient: recipient, body: body, subId: subscriptionId));
    return 'msg-${sent.length}';
  }

  @override
  Future<List<OutboxEntry>> getRecentOutbox() async => outbox;

  @override
  Stream<int> get outboxEvents => Stream.value(0);

  bool receiveSmsSupported = true;

  @override
  Future<bool> isReceiveSmsSupported() async => receiveSmsSupported;

  @override
  Future<PermissionStatus> receiveSmsPermissionStatus() async =>
      receiveSmsPermission;

  @override
  Future<PermissionStatus> requestReceiveSmsPermission() async =>
      receiveSmsPermission = _afterRequest(receiveSmsPermission);

  @override
  Future<List<InboundEntry>> getRecentInbox() async => inbox;

  @override
  Stream<int> get inboxEvents => Stream.value(0);

  @override
  Future<PairingResult> startPairing(
    String backendUrl,
    String pairingCode,
  ) async => pairingResult ?? PairingResult(ok: true, deviceId: 'dev-1');

  @override
  Future<bool> isPaired() async => paired;

  @override
  Future<void> unpair() async {
    unpairCalls++;
    paired = false;
  }

  @override
  Stream<ConnectionState> get connectionStateEvents =>
      Stream.value(connectionState);

  @override
  Future<List<LogEntry>> getRecentLogs() async => logs;

  @override
  Stream<int> get logEvents => Stream.value(0);
}
