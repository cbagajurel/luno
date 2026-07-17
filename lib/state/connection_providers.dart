import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../bridge/luno_bridge.dart';
import 'bridge_providers.dart';

/// Live §6 connection state; the native channel replays the current value on subscribe.
final connectionStateProvider = StreamProvider<ConnectionState>(
  (ref) => ref.watch(bridgeProvider).connectionStateEvents,
);

/// Live foreground-service run state.
final agentStateProvider = StreamProvider<AgentRunState>(
  (ref) => ref.watch(bridgeProvider).agentStateEvents,
);
