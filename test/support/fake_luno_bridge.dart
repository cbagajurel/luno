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
    this.phonePermission = true,
    this.smsPermission = true,
    this.receiveSmsPermission = true,
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
  bool phonePermission;
  bool smsPermission;
  bool receiveSmsPermission;
  PairingResult? pairingResult;

  final List<({String recipient, String body, int? subId})> sent = [];
  int unpairCalls = 0;
  int startAgentCalls = 0;

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
  Future<bool> hasPhonePermission() async => phonePermission;

  @override
  Future<void> requestPhonePermission() async => phonePermission = true;

  @override
  Stream<int> get deviceStateEvents => Stream.value(0);

  @override
  Future<bool> hasSmsPermission() async => smsPermission;

  @override
  Future<void> requestSmsPermission() async => smsPermission = true;

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
  Future<bool> hasReceiveSmsPermission() async => receiveSmsPermission;

  @override
  Future<void> requestReceiveSmsPermission() async =>
      receiveSmsPermission = true;

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
