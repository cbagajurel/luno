import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/bridge/generated/luno_api.g.dart';
import 'package:sms_gateway/features/dashboard/dashboard_screen.dart';
import 'package:sms_gateway/features/settings/settings_screen.dart';

import '../support/fake_luno_bridge.dart';
import '../support/pump.dart';

OutboxEntry _entry({required String lastError}) => OutboxEntry(
  id: 'm1',
  recipient: '+9779800000000',
  status: 'FAILED_TERMINAL',
  lastError: lastError,
  attempt: 1,
  createdAt: 0,
  partCount: 1,
  deliveredCount: 0,
);

void main() {
  testWidgets('a blocked permission still offers Grant, plus an explanation', (
    tester,
  ) async {
    // The action must never be replaced: a cached "blocked" goes stale the moment
    // the user allows restricted settings, and only a live request can discover
    // that it became grantable.
    final bridge = FakeLunoBridge(smsPermission: PermissionStatus.blocked);
    await tester.pumpWidget(wrapScreen(bridge, const SettingsScreen()));
    await tester.pumpAndSettle();

    expect(find.text('Grant'), findsWidgets);
    expect(find.text('Fix in Settings'), findsNothing);
    expect(find.textContaining('allow restricted settings'), findsOneWidget);
  });

  testWidgets(
    'regression: once restricted settings is allowed, Grant prompts and succeeds',
    (tester) async {
      // Reproduces the reported bug — the tile used to strand the user on "Fix in
      // Settings" with no way to trigger the now-working system prompt.
      final bridge = FakeLunoBridge(smsPermission: PermissionStatus.blocked);
      await tester.pumpWidget(wrapScreen(bridge, const SettingsScreen()));
      await tester.pumpAndSettle();

      // The user allows restricted settings outside the app; the permission is
      // grantable again, which nothing observable told us.
      bridge.smsPermission = PermissionStatus.denied;

      await tester.tap(find.text('Grant').first);
      await tester.pumpAndSettle();

      expect(bridge.smsPermission, PermissionStatus.granted);
      expect(find.text('Send SMS is blocked'), findsNothing);
    },
  );

  testWidgets('a denied permission shows Grant with no blocked explanation', (
    tester,
  ) async {
    final bridge = FakeLunoBridge(smsPermission: PermissionStatus.denied);
    await tester.pumpWidget(wrapScreen(bridge, const SettingsScreen()));
    await tester.pumpAndSettle();

    expect(find.text('Grant'), findsWidgets);
    expect(find.textContaining('allow restricted settings'), findsNothing);
  });

  testWidgets('a request that comes back blocked escalates to the sheet', (
    tester,
  ) async {
    final bridge = FakeLunoBridge(
      smsPermission: PermissionStatus.denied,
      grantOnRequest: false,
    );
    await tester.pumpWidget(wrapScreen(bridge, const SettingsScreen()));
    await tester.pumpAndSettle();

    // The system refuses to prompt when actually asked.
    bridge.smsPermission = PermissionStatus.blocked;
    await tester.tap(find.text('Grant').first);
    await tester.pumpAndSettle();

    expect(find.text('Send SMS is blocked'), findsOneWidget);

    await tester.tap(find.text('Open app settings'));
    await tester.pumpAndSettle();

    expect(bridge.openAppSettingsCalls, 1);
  });

  testWidgets('permission-failed sends are surfaced, not swallowed', (
    tester,
  ) async {
    final bridge = FakeLunoBridge(
      outbox: [_entry(lastError: 'AUTH:sms_permission_revoked')],
    );
    await tester.pumpWidget(wrapScreen(bridge, const DashboardScreen()));
    await tester.pumpAndSettle();

    expect(find.textContaining('1 message failed'), findsOneWidget);
  });

  testWidgets('an unrelated failure does not raise the permission banner', (
    tester,
  ) async {
    final bridge = FakeLunoBridge(
      outbox: [_entry(lastError: 'TRANSIENT:radio_off')],
    );
    await tester.pumpWidget(wrapScreen(bridge, const DashboardScreen()));
    await tester.pumpAndSettle();

    expect(find.textContaining('message failed'), findsNothing);
  });
}
