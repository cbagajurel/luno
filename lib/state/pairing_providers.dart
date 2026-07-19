import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../bridge/generated/luno_api.g.dart';
import '../bridge/luno_bridge.dart';
import 'bridge_providers.dart';

/// Whether this node is enrolled with a backend. Drives the router's pairing gate.
/// Native owns the credential; this mirrors it and flips on pair/unpair.
class PairingController extends AsyncNotifier<bool> {
  @override
  Future<bool> build() => ref.read(bridgeProvider).isPaired();

  Future<PairingResult> pair(String backendUrl, String pairingCode) => _enrol(
    backendUrl,
    (bridge) => bridge.startPairing(backendUrl, pairingCode),
  );

  Future<PairingResult> pairFromPayload(String raw, {String? backendUrl}) =>
      _enrol(backendUrl, (bridge) => bridge.startPairingFromPayload(raw));

  Future<PairingResult> _enrol(
    String? backendUrl,
    Future<PairingResult> Function(LunoBridge bridge) attempt,
  ) async {
    if (backendUrl != null && backendUrl.isNotEmpty) {
      ref.read(lastBackendUrlProvider.notifier).set(backendUrl);
    }
    final result = await attempt(ref.read(bridgeProvider));
    switch (result.outcome) {
      case PairingOutcome.success:
        state = const AsyncData(true);
      case PairingOutcome.pending:
        // Native persisted the pending enrolment; mirror it so the UI can wait.
        await ref.read(pendingPairingProvider.notifier).refresh();
      case PairingOutcome.failure:
        break;
    }
    return result;
  }

  /// Flips the gate once native reports a credential — used by the pending-approval
  /// wait, which enrols through a different call than [pair].
  void markPaired() => state = const AsyncData(true);

  Future<void> unpair() async {
    try {
      await ref.read(bridgeProvider).unpair();
      state = const AsyncData(false);
    } catch (_) {
      // Native still owns the credential; re-read the truth rather than leaving
      // the router gate on a stale value or letting the future go unhandled.
      state = await AsyncValue.guard(ref.read(bridgeProvider).isPaired);
    }
    await ref.read(pendingPairingProvider.notifier).refresh();
  }
}

final pairingProvider = AsyncNotifierProvider<PairingController, bool>(
  PairingController.new,
);

/// An enrolment the backend parked for operator approval. Native holds it
/// durably, so a device left waiting resumes after the UI is closed and
/// reopened rather than burning a second pairing code.
class PendingPairingController extends AsyncNotifier<PendingPairing?> {
  @override
  Future<PendingPairing?> build() => ref.read(bridgeProvider).pendingPairing();

  Future<void> refresh() async {
    state = await AsyncValue.guard(ref.read(bridgeProvider).pendingPairing);
  }

  /// Asks the backend for a decision. Returns null when nothing is pending.
  /// Whatever the answer, native stays the source of truth for what is still
  /// outstanding — a network blip must not look like a denial.
  Future<PairingResult?> check() async {
    final bridge = ref.read(bridgeProvider);
    final result = await bridge.checkPairingApproval();
    if (result?.outcome == PairingOutcome.success) {
      ref.read(pairingProvider.notifier).markPaired();
    }
    await refresh();
    return result;
  }

  Future<void> cancel() async {
    await ref.read(bridgeProvider).cancelPendingPairing();
    await refresh();
  }
}

final pendingPairingProvider =
    AsyncNotifierProvider<PendingPairingController, PendingPairing?>(
      PendingPairingController.new,
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
