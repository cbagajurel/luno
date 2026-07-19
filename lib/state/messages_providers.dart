import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../bridge/generated/luno_api.g.dart';
import 'bridge_providers.dart';

/// Recent outbox rows (newest first). Re-queried on each native outbox tick.
final outboxProvider = StreamProvider<List<OutboxEntry>>((ref) {
  final bridge = ref.watch(bridgeProvider);
  return bridge.outboxEvents.asyncMap((_) => bridge.getRecentOutbox());
});

/// Recent inbound rows (newest first). Re-queried on each native inbox tick.
final inboxProvider = StreamProvider<List<InboundEntry>>((ref) {
  final bridge = ref.watch(bridgeProvider);
  return bridge.inboxEvents.asyncMap((_) => bridge.getRecentInbox());
});

/// The error code SmsTransport records when the radio rejects a send because
/// SEND_SMS is not held. Stored by OutboxRepository as "${errorClass}:${code}".
const smsPermissionRevokedCode = 'sms_permission_revoked';

/// True when a queued send died purely because the permission was missing.
///
/// Such a send is classified AUTH, which is not retryable, so the outbox marks
/// it FAILED_TERMINAL and it would otherwise disappear with no visible cause —
/// the worst failure mode for a gateway.
final smsBlockedSendsProvider = Provider<int>((ref) {
  final outbox = ref.watch(outboxProvider).value ?? const <OutboxEntry>[];
  return outbox
      .where((e) => e.lastError?.contains(smsPermissionRevokedCode) ?? false)
      .length;
});
