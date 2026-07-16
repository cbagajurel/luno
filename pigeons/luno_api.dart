// Source of the Flutter<->native bridge. Regenerate after editing:
//   dart run pigeon --input pigeons/luno_api.dart
import 'package:pigeon/pigeon.dart';

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

  bool hasPhonePermission();

  void requestPhonePermission();

  bool hasSmsPermission();

  void requestSmsPermission();

  String sendSms(String recipient, String body, int? subscriptionId);

  List<OutboxEntry> getRecentOutbox();
}
