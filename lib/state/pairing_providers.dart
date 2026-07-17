import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../bridge/generated/luno_api.g.dart';
import 'bridge_providers.dart';

/// Whether this node is enrolled with a backend. Drives the router's pairing gate.
/// Native owns the credential; this mirrors it and flips on pair/unpair.
class PairingController extends AsyncNotifier<bool> {
  @override
  Future<bool> build() => ref.read(bridgeProvider).isPaired();

  Future<PairingResult> pair(String backendUrl, String pairingCode) async {
    ref.read(lastBackendUrlProvider.notifier).set(backendUrl);
    final result = await ref
        .read(bridgeProvider)
        .startPairing(backendUrl, pairingCode);
    if (result.ok) state = const AsyncData(true);
    return result;
  }

  Future<void> unpair() async {
    await ref.read(bridgeProvider).unpair();
    state = const AsyncData(false);
  }
}

final pairingProvider = AsyncNotifierProvider<PairingController, bool>(
  PairingController.new,
);

/// The last backend URL the user paired with, kept in memory to pre-fill the
/// reconnect / re-pair form. Native owns the durable credential; this is only a
/// UI convenience, seeded on [PairingController.pair].
class LastBackendUrl extends Notifier<String> {
  @override
  String build() => '';

  void set(String url) => state = url;
}

final lastBackendUrlProvider = NotifierProvider<LastBackendUrl, String>(
  LastBackendUrl.new,
);
