import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/bridge/generated/luno_api.g.dart';
import 'package:sms_gateway/features/pairing/pairing_screen.dart';

import '../support/fake_luno_bridge.dart';
import '../support/pump.dart';

void main() {
  testWidgets('empty fields fail validation and do not pair', (tester) async {
    final bridge = FakeLunoBridge(paired: false);
    await tester.pumpWidget(wrapScreen(bridge, const PairingScreen()));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(FilledButton, 'Pair'));
    await tester.pumpAndSettle();

    expect(find.text('Enter the backend URL'), findsOneWidget);
    expect(find.text('Enter the pairing code'), findsOneWidget);
  });

  testWidgets('surfaces a backend pairing failure', (tester) async {
    final bridge = FakeLunoBridge(
      paired: false,
      pairingResult: PairingResult(ok: false, message: 'invalid code'),
    );
    await tester.pumpWidget(wrapScreen(bridge, const PairingScreen()));
    await tester.pumpAndSettle();

    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), 'https://gw.example.com');
    await tester.enterText(fields.at(1), 'ABC123');
    await tester.tap(find.widgetWithText(FilledButton, 'Pair'));
    await tester.pumpAndSettle();

    expect(find.text('invalid code'), findsOneWidget);
  });
}
