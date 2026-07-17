import 'package:flutter/material.dart' hide ConnectionState;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/generated/luno_api.g.dart';
import '../../bridge/luno_bridge.dart';
import '../../state/bridge_providers.dart';
import '../../state/connection_providers.dart';
import '../../state/device_providers.dart';
import '../shared/status_ui.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  Future<void> _startAgent(WidgetRef ref) async {
    final bridge = ref.read(bridgeProvider);
    await bridge.requestNotificationPermission();
    await bridge.startAgent();
  }

  Future<void> _stopAgent(WidgetRef ref) => ref.read(bridgeProvider).stopAgent();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final connection = ref.watch(connectionStateProvider);
    final agent = ref.watch(agentStateProvider);
    final device = ref.watch(deviceStateProvider);

    final agentState = agent.value ?? AgentRunState.unknown;
    final isRunning = agentState == AgentRunState.running;

    return Scaffold(
      appBar: AppBar(title: const Text('Dashboard')),
      body: RefreshIndicator(
        onRefresh: () => ref.read(deviceStateProvider.future),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _ConnectionBanner(state: connection.value ?? ConnectionState.unknown),
            const SizedBox(height: 16),
            _AgentControls(
              state: agentState,
              onStart: isRunning ? null : () => _startAgent(ref),
              onStop: isRunning ? () => _stopAgent(ref) : null,
            ),
            const SizedBox(height: 16),
            _SectionTitle('Network'),
            _NetworkCard(network: device.value?.network),
            const SizedBox(height: 16),
            _SectionTitle('Battery'),
            _BatteryCard(battery: device.value?.battery),
            const SizedBox(height: 16),
            _SectionTitle('SIMs'),
            _SimList(device: device.value),
          ],
        ),
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Text(text, style: Theme.of(context).textTheme.titleMedium),
      );
}

class _ConnectionBanner extends StatelessWidget {
  const _ConnectionBanner({required this.state});

  final ConnectionState state;

  @override
  Widget build(BuildContext context) {
    final ui = connectionUi(state);
    return Card(
      color: ui.color.withValues(alpha: 0.12),
      child: ListTile(
        leading: Icon(ui.icon, color: ui.color),
        title: Text(ui.label, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: const Text('Backend connection'),
      ),
    );
  }
}

class _AgentControls extends StatelessWidget {
  const _AgentControls({required this.state, this.onStart, this.onStop});

  final AgentRunState state;
  final VoidCallback? onStart;
  final VoidCallback? onStop;

  @override
  Widget build(BuildContext context) {
    final ui = agentUi(state);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Row(
              children: [
                Icon(ui.icon, color: ui.color),
                const SizedBox(width: 8),
                Text(ui.label),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: FilledButton.icon(
                    onPressed: onStart,
                    icon: const Icon(Icons.play_arrow),
                    label: const Text('Start'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: onStop,
                    icon: const Icon(Icons.stop),
                    label: const Text('Stop'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _NetworkCard extends StatelessWidget {
  const _NetworkCard({required this.network});

  final NetworkStatus? network;

  @override
  Widget build(BuildContext context) {
    final n = network;
    if (n == null || !n.connected) {
      return const Card(
        child: ListTile(leading: Icon(Icons.cloud_off), title: Text('Offline')),
      );
    }
    final online = n.validated;
    return Card(
      child: ListTile(
        leading: Icon(
          online ? Icons.cloud_done : Icons.cloud_queue,
          color: online ? Colors.green : Colors.orange,
        ),
        title: Text(online
            ? 'Online · ${n.transport}'
            : 'Connected, no internet · ${n.transport}'),
        subtitle: Text(n.metered ? 'metered' : 'unmetered'),
      ),
    );
  }
}

class _BatteryCard extends StatelessWidget {
  const _BatteryCard({required this.battery});

  final BatteryStatus? battery;

  @override
  Widget build(BuildContext context) {
    final b = battery;
    if (b == null) {
      return const Card(
        child: ListTile(
          leading: Icon(Icons.battery_unknown),
          title: Text('Battery status unavailable'),
        ),
      );
    }
    final level = b.levelPercent >= 0 ? '${b.levelPercent}%' : 'unknown';
    final source = b.plugged == 'NONE' ? 'on battery' : 'charging via ${b.plugged}';
    return Card(
      child: ListTile(
        leading: Icon(
          b.isCharging ? Icons.battery_charging_full : Icons.battery_full,
          color: b.isCharging ? Colors.green : null,
        ),
        title: Text('$level · ${b.isCharging ? 'charging' : 'discharging'}'),
        subtitle: Text('$source · health ${b.health}'),
      ),
    );
  }
}

class _SimList extends StatelessWidget {
  const _SimList({required this.device});

  final DeviceState? device;

  @override
  Widget build(BuildContext context) {
    final sims = device?.sims ?? const <SimInfo>[];
    if (sims.isEmpty) {
      return const Card(
        child: ListTile(
          leading: Icon(Icons.sim_card_alert),
          title: Text('No active SIM detected'),
          subtitle: Text('Grant phone permission in Settings if this is unexpected.'),
        ),
      );
    }
    final signals = {
      for (final s in device?.signals ?? const <SignalInfo>[]) s.subscriptionId: s,
    };
    return Column(
      children: [
        for (final sim in sims) _SimTile(sim: sim, signal: signals[sim.subscriptionId]),
      ],
    );
  }
}

class _SimTile extends StatelessWidget {
  const _SimTile({required this.sim, this.signal});

  final SimInfo sim;
  final SignalInfo? signal;

  @override
  Widget build(BuildContext context) {
    final carrier = sim.carrierName.isNotEmpty ? sim.carrierName : 'Unknown carrier';
    final name = sim.displayName.isNotEmpty ? sim.displayName : carrier;
    return Card(
      child: ListTile(
        leading: Icon(sim.isEmbedded ? Icons.sim_card_download : Icons.sim_card),
        title: Text(name),
        subtitle: Text(
          'Carrier: $carrier\n'
          'Slot ${sim.slotIndex} · subId ${sim.subscriptionId} · '
          '${sim.isEmbedded ? 'eSIM' : 'physical'}\n'
          'Signal: ${_signalLabel(signal)}',
        ),
        trailing: Chip(label: Text(sim.simState)),
        isThreeLine: true,
      ),
    );
  }

  static String _signalLabel(SignalInfo? signal) {
    if (signal == null) return 'unknown';
    final bars = '${signal.level}/4';
    return signal.dbm != null ? '${signal.dbm} dBm ($bars)' : bars;
  }
}
