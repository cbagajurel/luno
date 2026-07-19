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

  testWidgets('a known rejection code renders its own copy', (tester) async {
    final bridge = FakeLunoBridge(
      paired: false,
      pairingResult: PairingResult(
        outcome: PairingOutcome.failure,
        errorCode: 'session_expired',
        message: 'expired',
      ),
    );
    await tester.pumpWidget(wrapScreen(bridge, const PairingScreen()));
    await tester.pumpAndSettle();

    await _submitForm(tester);

    expect(
      find.text(
        'This pairing code has expired. Generate a fresh one from your backend.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('an unrecognised code falls back to the backend message', (
    tester,
  ) async {
    final bridge = FakeLunoBridge(
      paired: false,
      pairingResult: PairingResult(
        outcome: PairingOutcome.failure,
        errorCode: 'quota_exceeded_for_org',
        message: 'Your workspace is out of device slots.',
      ),
    );
    await tester.pumpWidget(wrapScreen(bridge, const PairingScreen()));
    await tester.pumpAndSettle();

    await _submitForm(tester);

    expect(find.text('Your workspace is out of device slots.'), findsOneWidget);
  });

  testWidgets('a pending enrolment shows the approval wait, not the form', (
    tester,
  ) async {
    final bridge = FakeLunoBridge(paired: false, pending: _pending());
    await tester.pumpWidget(wrapScreen(bridge, const PairingScreen()));
    await _settleOntoWait(tester);

    expect(find.text('Waiting for approval'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Pair'), findsNothing);
  });

  testWidgets('cancelling a pending enrolment returns to the form', (
    tester,
  ) async {
    final bridge = FakeLunoBridge(paired: false, pending: _pending());
    await tester.pumpWidget(wrapScreen(bridge, const PairingScreen()));
    await _settleOntoWait(tester);

    await tester.tap(find.text('Cancel and start over'));
    await tester.pumpAndSettle();

    expect(bridge.cancelPendingCalls, 1);
    expect(find.widgetWithText(FilledButton, 'Pair'), findsOneWidget);
  });

  testWidgets('a pending result parks the user on the approval wait', (
    tester,
  ) async {
    final bridge = FakeLunoBridge(
      paired: false,
      pairingResult: PairingResult(
        outcome: PairingOutcome.pending,
        retryAfterMs: 60000,
      ),
    );
    await tester.pumpWidget(wrapScreen(bridge, const PairingScreen()));
    await tester.pumpAndSettle();

    // Native persists the pending enrolment as part of accepting the code.
    bridge.pending = _pending();
    await _submitForm(tester, settle: false);
    await _settleOntoWait(tester);

    expect(find.text('Waiting for approval'), findsOneWidget);
  });
}

/// The approval wait spins indefinitely, so it never reaches a settled frame —
/// pump a couple of frames instead of waiting for one.
Future<void> _settleOntoWait(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 50));
}

PendingPairing _pending() => PendingPairing(
  enrollmentId: 'enr-1',
  backendUrl: 'https://gw.example.com',
  retryAfterMs: 60000,
  startedAtMs: 0,
);

Future<void> _submitForm(WidgetTester tester, {bool settle = true}) async {
  final fields = find.byType(TextFormField);
  await tester.enterText(fields.at(0), 'https://gw.example.com');
  await tester.enterText(fields.at(1), 'ABC123');
  await tester.tap(find.widgetWithText(FilledButton, 'Pair'));
  if (settle) await tester.pumpAndSettle();
}
