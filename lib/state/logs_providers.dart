import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../bridge/generated/luno_api.g.dart';
import 'bridge_providers.dart';

/// Recent agent log lines (newest first), already phone-number-redacted by native.
/// Re-queried on each native log tick.
final logsProvider = StreamProvider<List<LogEntry>>((ref) {
  final bridge = ref.watch(bridgeProvider);
  return bridge.logEvents.asyncMap((_) => bridge.getRecentLogs());
});
