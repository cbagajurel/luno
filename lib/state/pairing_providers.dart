import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../bridge/generated/luno_api.g.dart';
import 'bridge_providers.dart';

/// Whether this node is enrolled with a backend. Drives the router's pairing gate.
/// Native owns the credential; this mirrors it and flips on pair/unpair.
class PairingController extends AsyncNotifier<bool> {
  @override
  Future<bool> build() => ref.read(bridgeProvider).isPaired();

  Future<PairingResult> pair(String backendUrl, String pairingCode) async {
    final result = await ref.read(bridgeProvider).startPairing(backendUrl, pairingCode);
    if (result.ok) state = const AsyncData(true);
    return result;
  }

  Future<void> unpair() async {
    await ref.read(bridgeProvider).unpair();
    state = const AsyncData(false);
  }
}

final pairingProvider =
    AsyncNotifierProvider<PairingController, bool>(PairingController.new);
