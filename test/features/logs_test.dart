import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/bridge/generated/luno_api.g.dart';
import 'package:sms_gateway/features/logs/logs_screen.dart';

import '../support/fake_luno_bridge.dart';
import '../support/pump.dart';

void main() {
  testWidgets('renders log lines and filters by level', (tester) async {
    final bridge = FakeLunoBridge(
      logs: [
        LogEntry(
          timestampMs: 0,
          level: 'INFO',
          tag: 'Agent',
          message: 'connected',
        ),
        LogEntry(
          timestampMs: 0,
          level: 'ERROR',
          tag: 'Sms',
          message: 'send failed',
        ),
      ],
    );

    await tester.pumpWidget(wrapScreen(bridge, const LogsScreen()));
    await tester.pumpAndSettle();

    expect(find.text('connected'), findsOneWidget);
    expect(find.text('send failed'), findsOneWidget);

    await tester.tap(find.widgetWithText(ChoiceChip, 'ERROR'));
    await tester.pumpAndSettle();

    expect(find.text('send failed'), findsOneWidget);
    expect(find.text('connected'), findsNothing);
  });
}
