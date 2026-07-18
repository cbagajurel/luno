import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../bridge/generated/luno_api.g.dart';
import 'bridge_providers.dart';
import 'stream_throttle.dart';

/// A busy agent writes many log lines per second, and each native tick drives a
/// full `getRecentLogs()` round-trip + list deserialize. Throttle the ticks so
/// the refetch runs at most a few times per second (latest wins), keeping the
/// buffer live without flooding the platform channel or rebuilding on every line.
const _logRefetchWindow = Duration(milliseconds: 400);

/// Recent agent log lines (newest first), already phone-number-redacted by native.
/// Re-queried, throttled, on each native log tick.
final logsProvider = StreamProvider<List<LogEntry>>((ref) {
  final bridge = ref.watch(bridgeProvider);
  return bridge.logEvents
      .throttleTrailing(_logRefetchWindow)
      .asyncMap((_) => bridge.getRecentLogs());
});
