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
