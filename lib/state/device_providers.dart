import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../bridge/generated/luno_api.g.dart';
import 'bridge_providers.dart';

/// Snapshot-then-stream: the device-state channel replays the current telemetry
/// on subscribe and re-fires on every change; each tick re-queries the full state.
final deviceStateProvider = StreamProvider<DeviceState>((ref) {
  final bridge = ref.watch(bridgeProvider);
  return bridge.deviceStateEvents.asyncMap((_) => bridge.getDeviceState());
});

class Permissions {
  const Permissions({
    required this.phone,
    required this.sms,
    required this.receiveSms,
  });

  final bool phone;
  final bool sms;
  final bool receiveSms;
}

/// Runtime-permission snapshot. Native is the source of truth; after a request the
/// system dialog resolves asynchronously, so callers [refresh] once it settles.
class PermissionsController extends AsyncNotifier<Permissions> {
  @override
  Future<Permissions> build() => _read();

  Future<Permissions> _read() async {
    final bridge = ref.read(bridgeProvider);
    final results = await Future.wait([
      bridge.hasPhonePermission(),
      bridge.hasSmsPermission(),
      bridge.hasReceiveSmsPermission(),
    ]);
    return Permissions(phone: results[0], sms: results[1], receiveSms: results[2]);
  }

  Future<void> refresh() async {
    state = await AsyncValue.guard(_read);
  }

  Future<void> requestPhone() async {
    await ref.read(bridgeProvider).requestPhonePermission();
    await refresh();
  }

  Future<void> requestSms() async {
    await ref.read(bridgeProvider).requestSmsPermission();
    await refresh();
  }

  Future<void> requestReceiveSms() async {
    await ref.read(bridgeProvider).requestReceiveSmsPermission();
    await refresh();
  }
}

final permissionsProvider =
    AsyncNotifierProvider<PermissionsController, Permissions>(PermissionsController.new);
