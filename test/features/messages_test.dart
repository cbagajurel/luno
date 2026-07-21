import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/bridge/generated/luno_api.g.dart';
import 'package:sms_gateway/features/messages/messages_screen.dart';

import '../support/fake_luno_bridge.dart';
import '../support/pump.dart';

void main() {
  testWidgets('sent tab renders outbox rows (snapshot after late subscribe)', (
    tester,
  ) async {
    final bridge = FakeLunoBridge(
      outbox: [
        OutboxEntry(
          id: 'a',
          recipient: '+15551112222',
          status: 'SENT',
          attempt: 1,
          createdAt: 0,
          partCount: 1,
          deliveredCount: 1,
        ),
      ],
    );

    await tester.pumpWidget(wrapScreen(bridge, const MessagesScreen()));
    await tester.pumpAndSettle();

    expect(find.text('+15551112222'), findsOneWidget);
    expect(find.text('SENT'), findsOneWidget);
  });

  testWidgets('empty received tab shows the empty state', (tester) async {
    final bridge = FakeLunoBridge();
    await tester.pumpWidget(wrapScreen(bridge, const MessagesScreen()));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Received'));
    await tester.pumpAndSettle();

    expect(find.text('No messages received yet'), findsOneWidget);
  });

  testWidgets('sendOnly build never offers a received tab', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        FakeLunoBridge(),
        const MessagesScreen(),
        receiveSmsSupported: false,
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Received'), findsNothing);
    expect(find.text('Sent'), findsNothing);
    expect(find.text('No sent messages yet'), findsOneWidget);
  });
}
