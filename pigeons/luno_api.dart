// Source of the Flutter<->native bridge. Regenerate after editing:
//   dart run pigeon --input pigeons/luno_api.dart
import 'package:pigeon/pigeon.dart';

/// [blocked] means the system will not show a permission dialog at all, so
/// re-requesting is a no-op — only the app's settings page can resolve it. On
/// Android 15+ a sideloaded build lands here for SMS permissions until the user
/// taps "Allow restricted settings"; it is also where "Don't ask again" leads.
/// The two are indistinguishable through the public API and share one remedy.
enum PermissionStatus { granted, denied, blocked }

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

class SignalInfo {
  SignalInfo({required this.subscriptionId, this.dbm, required this.level});

  final int subscriptionId;
  final int? dbm;
  final int level;
}

class NetworkStatus {
  NetworkStatus({
    required this.connected,
    required this.validated,
    required this.transport,
    required this.metered,
  });

  final bool connected;
  final bool validated;
  final String transport;
  final bool metered;
}

class OutboxEntry {
  OutboxEntry({
    required this.id,
    required this.recipient,
    required this.status,
    this.lastError,
    required this.attempt,
    required this.createdAt,
    required this.partCount,
    required this.deliveredCount,
  });

  final String id;
  final String recipient;
  final String status;
  final String? lastError;
  final int attempt;
  final int createdAt;
  final int partCount;
  final int deliveredCount;
}

class InboundEntry {
  InboundEntry({
    required this.id,
    required this.sender,
    required this.body,
    this.subscriptionId,
    required this.receivedAt,
    required this.parts,
  });

  final String id;
  final String sender;
  final String body;
  final int? subscriptionId;
  final int receivedAt;
  final int parts;
}

class DeviceState {
  DeviceState({
    required this.sims,
    this.battery,
    required this.signals,
    this.network,
  });

  final List<SimInfo> sims;
  final BatteryStatus? battery;
  final List<SignalInfo> signals;
  final NetworkStatus? network;
}

/// [pending] means the backend accepted the code but its policy requires an
/// operator to approve this device before issuing a credential.
enum PairingOutcome { success, pending, failure }

class PairingResult {
  PairingResult({
    required this.outcome,
    this.deviceId,
    this.errorCode,
    this.message,
    this.retryAfterMs,
  });

  final PairingOutcome outcome;
  final String? deviceId;

  /// A stable machine code from the backend's pairing taxonomy (`session_expired`,
  /// `session_exhausted`, …). Unknown codes arrive verbatim so a backend can add
  /// reasons without an app release; render [message] when one isn't recognised.
  final String? errorCode;
  final String? message;

  /// How long to wait before re-checking a [PairingOutcome.pending] enrolment.
  final int? retryAfterMs;
}

enum PairingPayloadStatus { ok, unsupportedVersion, malformed }

/// A scanned QR payload after native parsing. Carries only enrolment *inputs* —
/// pairing policy stays on the backend and never rides in the code.
class PairingPayloadInfo {
  PairingPayloadInfo({
    required this.backendUrl,
    required this.pairingCode,
    this.sessionId,
    this.label,
    this.pin,
  });

  final String backendUrl;
  final String pairingCode;
  final String? sessionId;
  final String? label;
  final String? pin;
}

class PairingPayloadParse {
  PairingPayloadParse({required this.status, this.payload, this.reason});

  final PairingPayloadStatus status;
  final PairingPayloadInfo? payload;
  final String? reason;
}

/// An enrolment awaiting operator approval, held durably by native so the wait
/// survives the UI being closed.
class PendingPairing {
  PendingPairing({
    required this.enrollmentId,
    required this.backendUrl,
    required this.retryAfterMs,
    required this.startedAtMs,
    this.label,
  });

  final String enrollmentId;
  final String backendUrl;
  final int retryAfterMs;
  final int startedAtMs;
  final String? label;
}

class LogEntry {
  LogEntry({
    required this.timestampMs,
    required this.level,
    required this.tag,
    required this.message,
  });

  final int timestampMs;
  final String level;
  final String tag;
  final String message;
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
@HostApi()
abstract class LunoHostApi {
  String ping(String message);

  void startAgent();

  void stopAgent();

  bool isAgentRunning();

  void requestNotificationPermission();

  DeviceState getDeviceState();

  PermissionStatus phonePermissionStatus();

  @async
  PermissionStatus requestPhonePermission();

  PermissionStatus smsPermissionStatus();

  @async
  PermissionStatus requestSmsPermission();

  /// Opens this app's system settings page — the only route out of
  /// [PermissionStatus.blocked].
  void openAppSettings();

  String sendSms(String recipient, String body, int? subscriptionId);

  List<OutboxEntry> getRecentOutbox();

  /// False in `sendOnly` builds, which omit RECEIVE_SMS to install clean past
  /// Play Protect. Inbound capture is unavailable and its permission unrequestable.
  bool isReceiveSmsSupported();

  PermissionStatus receiveSmsPermissionStatus();

  @async
  PermissionStatus requestReceiveSmsPermission();

  List<InboundEntry> getRecentInbox();

  @async
  PairingResult startPairing(String backendUrl, String pairingCode);

  /// Parses a scanned QR payload without contacting the backend, so the UI can
  /// show what it is about to enrol with before committing.
  PairingPayloadParse parsePairingPayload(String raw);

  @async
  PairingResult startPairingFromPayload(String raw);

  /// Re-checks a pending enrolment. Null when nothing is pending.
  @async
  PairingResult? checkPairingApproval();

  PendingPairing? pendingPairing();

  void cancelPendingPairing();

  bool isPaired();

  void unpair();

  List<LogEntry> getRecentLogs();
}
