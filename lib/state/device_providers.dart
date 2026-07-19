import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../bridge/generated/luno_api.g.dart';
import 'bridge_providers.dart';

/// Snapshot-then-stream device telemetry. Also re-subscribes whenever permissions
/// change, so newly-granted access (phone → SIMs, etc.) shows up without a manual
/// refresh — the native channel replays the current state on every re-subscribe.
final deviceStateProvider = StreamProvider<DeviceState>((ref) {
  ref.watch(permissionsProvider);
  final bridge = ref.watch(bridgeProvider);
  return bridge.deviceStateEvents.asyncMap((_) => bridge.getDeviceState());
});

/// A runtime permission the node needs, with copy for the UI to render.
enum AppPermission {
  phone('Phone / SIM', 'Read SIM cards, signal, and carrier info'),
  sms('Send SMS', 'Send outgoing text messages'),
  receiveSms('Receive SMS', 'Capture incoming text messages');

  const AppPermission(this.label, this.rationale);

  final String label;
  final String rationale;
}

class Permissions {
  const Permissions(
    this.statuses, {
    this.supported = const {...AppPermission.values},
  });

  final Map<AppPermission, PermissionStatus> statuses;

  /// What this build can actually ask for. `sendOnly` builds omit RECEIVE_SMS from
  /// the manifest, so [AppPermission.receiveSms] is absent and must not be surfaced
  /// as missing — it is never grantable.
  final Set<AppPermission> supported;

  PermissionStatus statusOf(AppPermission p) =>
      statuses[p] ?? PermissionStatus.denied;

  bool has(AppPermission p) => statusOf(p) == PermissionStatus.granted;
  bool supports(AppPermission p) => supported.contains(p);

  /// Re-requesting is futile — only the system settings page can resolve it.
  bool isBlocked(AppPermission p) => statusOf(p) == PermissionStatus.blocked;

  bool get phone => has(AppPermission.phone);
  bool get sms => has(AppPermission.sms);
  bool get receiveSms => has(AppPermission.receiveSms);

  bool get allGranted => supported.every(has);

  List<AppPermission> get missing => [
    for (final p in AppPermission.values)
      if (supports(p) && !has(p)) p,
  ];
}

/// Runtime-permission snapshot. Native is the source of truth; a request fires the
/// system dialog then re-reads, and [refresh] re-reads on demand (e.g. app resume,
/// after the user toggles a permission in system Settings).
class PermissionsController extends AsyncNotifier<Permissions> {
  @override
  Future<Permissions> build() => _read();

  Future<Permissions> _read() async {
    final bridge = ref.read(bridgeProvider);
    final phone = await bridge.phonePermissionStatus();
    final sms = await bridge.smsPermissionStatus();
    final receiveSms = await bridge.receiveSmsPermissionStatus();
    final receiveSupported = await bridge.isReceiveSmsSupported();
    return Permissions(
      {
        AppPermission.phone: phone,
        AppPermission.sms: sms,
        AppPermission.receiveSms: receiveSms,
      },
      supported: {
        AppPermission.phone,
        AppPermission.sms,
        if (receiveSupported) AppPermission.receiveSms,
      },
    );
  }

  Future<void> refresh() async {
    state = await AsyncValue.guard(_read);
  }

  /// Returns the real outcome — the native request now completes only after the
  /// user answers, so callers can branch on [PermissionStatus.blocked] instead of
  /// re-polling into a stale value.
  Future<PermissionStatus> request(AppPermission permission) async {
    final bridge = ref.read(bridgeProvider);
    final status = switch (permission) {
      AppPermission.phone => await bridge.requestPhonePermission(),
      AppPermission.sms => await bridge.requestSmsPermission(),
      AppPermission.receiveSms => await bridge.requestReceiveSmsPermission(),
    };
    await refresh();
    return status;
  }

  Future<void> openAppSettings() => ref.read(bridgeProvider).openAppSettings();
}

final permissionsProvider =
    AsyncNotifierProvider<PermissionsController, Permissions>(
      PermissionsController.new,
    );
