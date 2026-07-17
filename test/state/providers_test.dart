import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/bridge/generated/luno_api.g.dart';
import 'package:sms_gateway/state/bridge_providers.dart';
import 'package:sms_gateway/state/logs_providers.dart';
import 'package:sms_gateway/state/messages_providers.dart';

import '../support/fake_luno_bridge.dart';

void main() {
  ProviderContainer containerWith(FakeLunoBridge bridge) {
    final container = ProviderContainer(
      overrides: [bridgeProvider.overrideWithValue(bridge)],
    );
    addTearDown(container.dispose);
    return container;
  }

  test('logsProvider yields the current snapshot on a revision tick', () async {
    final bridge = FakeLunoBridge(
      logs: [LogEntry(timestampMs: 0, level: 'INFO', tag: 'T', message: 'hi')],
    );
    final container = containerWith(bridge);
    container.listen(logsProvider, (_, _) {}); // keep alive during the await
    final logs = await container.read(logsProvider.future);
    expect(logs, hasLength(1));
    expect(logs.single.message, 'hi');
  });

  test('outboxProvider re-queries the bridge on a tick', () async {
    final bridge = FakeLunoBridge(
      outbox: [
        OutboxEntry(
          id: 'a',
          recipient: '+1',
          status: 'QUEUED',
          attempt: 0,
          createdAt: 0,
          partCount: 1,
          deliveredCount: 0,
        ),
      ],
    );
    final container = containerWith(bridge);
    container.listen(outboxProvider, (_, _) {});
    final rows = await container.read(outboxProvider.future);
    expect(rows.single.status, 'QUEUED');
  });
}
