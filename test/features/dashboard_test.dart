import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/bridge/generated/luno_api.g.dart';
import 'package:sms_gateway/bridge/luno_bridge.dart';
import 'package:sms_gateway/features/dashboard/dashboard_screen.dart';

import '../support/fake_luno_bridge.dart';
import '../support/pump.dart';

void main() {
  testWidgets('renders connection and agent state from the bridge', (tester) async {
    final bridge = FakeLunoBridge(
      connectionState: ConnectionState.ready,
      agentState: AgentRunState.running,
      deviceState: DeviceState(
        sims: const <SimInfo>[],
        signals: const <SignalInfo>[],
        battery: BatteryStatus(
          levelPercent: 82,
          isCharging: false,
          plugged: 'NONE',
          health: 'GOOD',
        ),
      ),
    );

    await tester.pumpWidget(wrapScreen(bridge, const DashboardScreen()));
    await tester.pumpAndSettle();

    expect(find.text('Ready'), findsOneWidget);
    expect(find.text('Agent running'), findsOneWidget);
    expect(find.textContaining('82%'), findsOneWidget);
    expect(find.text('No active SIM detected'), findsOneWidget);
  });

  testWidgets('offline connection shows the offline banner', (tester) async {
    final bridge = FakeLunoBridge(connectionState: ConnectionState.offlineNoNetwork);
    await tester.pumpWidget(wrapScreen(bridge, const DashboardScreen()));
    await tester.pumpAndSettle();

    expect(find.text('Offline · no network'), findsOneWidget);
  });
}
