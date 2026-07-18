import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/features/settings/settings_screen.dart';

import '../support/fake_luno_bridge.dart';
import '../support/pump.dart';

void main() {
  testWidgets('shows a grant action for a missing permission', (tester) async {
    final bridge = FakeLunoBridge(smsPermission: false);
    await tester.pumpWidget(wrapScreen(bridge, const SettingsScreen()));
    await tester.pumpAndSettle();

    expect(find.text('Send SMS'), findsOneWidget);
    expect(find.text('Grant'), findsWidgets);
  });

  testWidgets('sendOnly builds hide the un-grantable receive permission', (tester) async {
    final bridge = FakeLunoBridge(receiveSmsPermission: false)
      ..receiveSmsSupported = false;
    await tester.pumpWidget(wrapScreen(bridge, const SettingsScreen()));
    await tester.pumpAndSettle();

    expect(find.text('Send SMS'), findsOneWidget);
    expect(find.text('Receive SMS'), findsNothing);
  });

  testWidgets('unpair asks for confirmation then calls the bridge', (tester) async {
    final bridge = FakeLunoBridge();
    await tester.pumpWidget(wrapScreen(bridge, const SettingsScreen()));
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(find.text('Unpair node'), 300);
    await tester.tap(find.text('Unpair node'));
    await tester.pumpAndSettle();
    expect(find.text('Unpair node?'), findsOneWidget);

    // The dialog's confirm button is the only bare 'Unpair' (the tile is 'Unpair node').
    await tester.tap(find.text('Unpair'));
    await tester.pumpAndSettle();

    expect(bridge.unpairCalls, 1);
  });
}
