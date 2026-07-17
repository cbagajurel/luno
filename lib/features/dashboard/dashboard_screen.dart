import 'package:flutter/material.dart' hide ConnectionState;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/generated/luno_api.g.dart';
import '../../bridge/luno_bridge.dart';
import '../../state/bridge_providers.dart';
import '../../state/connection_providers.dart';
import '../../state/device_providers.dart';
import '../pairing/pairing_form.dart';
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
    final permissions = ref.watch(permissionsProvider);

    final agentState = agent.value ?? AgentRunState.unknown;
    final isRunning = agentState == AgentRunState.running;
    final hasPhonePermission = permissions.value?.phone ?? true;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Dashboard'),
        actions: [
          IconButton(
            tooltip: 'Refresh',
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.read(permissionsProvider.notifier).refresh(),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          await ref.read(permissionsProvider.notifier).refresh();
          await ref.read(deviceStateProvider.future);
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _ConnectionBanner(
              state: connection.value ?? ConnectionState.unknown,
              onReconnect: () => showReconnectSheet(context),
            ),
            const SizedBox(height: 16),
            _AgentControls(
              state: agentState,
              onStart: isRunning ? null : () => _startAgent(ref),
              onStop: isRunning ? () => _stopAgent(ref) : null,
            ),
            const SizedBox(height: 16),
            _PermissionsCard(
              permissions: permissions,
              onGrant: (p) => ref.read(permissionsProvider.notifier).request(p),
              onRetry: () => ref.read(permissionsProvider.notifier).refresh(),
            ),
            _TelemetrySections(device: device, hasPhonePermission: hasPhonePermission),
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
        padding: const EdgeInsets.only(top: 16, bottom: 8),
        child: Text(text, style: Theme.of(context).textTheme.titleMedium),
      );
}

/// True when the connection isn't actively healthy or progressing, so a manual
/// reconnect / re-pair is worth offering (a dead credential otherwise loops here).
bool _needsReconnect(ConnectionState s) => switch (s) {
      ConnectionState.disconnected ||
      ConnectionState.reconnecting ||
      ConnectionState.backingOff ||
      ConnectionState.unknown =>
        true,
      _ => false,
    };

class _ConnectionBanner extends StatelessWidget {
  const _ConnectionBanner({required this.state, this.onReconnect});

  final ConnectionState state;
  final VoidCallback? onReconnect;

  @override
  Widget build(BuildContext context) {
    final ui = connectionUi(state);
    final showReconnect = onReconnect != null && _needsReconnect(state);
    return Card(
      color: ui.color.withValues(alpha: 0.12),
      child: ListTile(
        leading: Icon(ui.icon, color: ui.color),
        title: Text(ui.label, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: const Text('Backend connection'),
        trailing: showReconnect
            ? TextButton(onPressed: onReconnect, child: const Text('Reconnect'))
            : null,
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

/// Shows only the permissions that still need granting; renders nothing once all
/// are held. Surfaces its own loading/error so a failed read isn't silent.
class _PermissionsCard extends StatelessWidget {
  const _PermissionsCard({
    required this.permissions,
    required this.onGrant,
    required this.onRetry,
  });

  final AsyncValue<Permissions> permissions;
  final void Function(AppPermission) onGrant;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return permissions.when(
      skipLoadingOnReload: true,
      loading: () => const _LoadingCard('Checking permissions…'),
      error: (e, _) => _ErrorCard(message: 'Could not read permissions', onRetry: onRetry),
      data: (p) {
        final missing = p.missing;
        if (missing.isEmpty) return const SizedBox.shrink();
        final scheme = Theme.of(context).colorScheme;
        return Padding(
          padding: const EdgeInsets.only(top: 16),
          child: Card(
            color: scheme.errorContainer.withValues(alpha: 0.4),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.lock_outline),
                      const SizedBox(width: 8),
                      Text(
                        'Permissions needed',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Luno needs these to send, receive, and read SIM state.',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  const SizedBox(height: 8),
                  for (final p in missing)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                      leading: const Icon(Icons.cancel, color: Colors.grey),
                      title: Text(p.label),
                      subtitle: Text(p.rationale),
                      trailing: FilledButton.tonal(
                        onPressed: () => onGrant(p),
                        child: const Text('Grant'),
                      ),
                    ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

/// Network / Battery / SIM sections, driven by the device-telemetry stream with
/// its loading and error states rendered rather than collapsed to defaults.
class _TelemetrySections extends StatelessWidget {
  const _TelemetrySections({required this.device, required this.hasPhonePermission});

  final AsyncValue<DeviceState> device;
  final bool hasPhonePermission;

  @override
  Widget build(BuildContext context) {
    return device.when(
      skipLoadingOnReload: true,
      loading: () => const Padding(
        padding: EdgeInsets.only(top: 16),
        child: _LoadingCard('Reading device telemetry…'),
      ),
      error: (e, _) => const Padding(
        padding: EdgeInsets.only(top: 16),
        child: _ErrorCard(message: 'Device telemetry unavailable'),
      ),
      data: (d) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _SectionTitle('Network'),
          _NetworkCard(network: d.network),
          _SectionTitle('Battery'),
          _BatteryCard(battery: d.battery),
          _SectionTitle('SIMs'),
          if (!hasPhonePermission)
            const Card(
              child: ListTile(
                leading: Icon(Icons.sim_card_alert),
                title: Text('Phone permission needed'),
                subtitle: Text('Grant it above to list SIMs.'),
              ),
            )
          else
            _SimList(device: d),
        ],
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

  final DeviceState device;

  @override
  Widget build(BuildContext context) {
    final sims = device.sims;
    if (sims.isEmpty) {
      return const Card(
        child: ListTile(
          leading: Icon(Icons.sim_card_alert),
          title: Text('No active SIM detected'),
          subtitle: Text('Insert a SIM card, or check that it is enabled.'),
        ),
      );
    }
    final signals = {
      for (final s in device.signals) s.subscriptionId: s,
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
    return Card(
      child: ListTile(
        leading: Icon(sim.isEmbedded ? Icons.sim_card_download : Icons.sim_card),
        title: Text(simLabel(sim)),
        subtitle: Text(
          'Slot ${sim.slotIndex} · ${sim.isEmbedded ? 'eSIM' : 'physical'}\n'
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

class _LoadingCard extends StatelessWidget {
  const _LoadingCard(this.message);

  final String message;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: const SizedBox(
          width: 24,
          height: 24,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
        title: Text(message),
      ),
    );
  }
}

class _ErrorCard extends StatelessWidget {
  const _ErrorCard({required this.message, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Theme.of(context).colorScheme.errorContainer.withValues(alpha: 0.4),
      child: ListTile(
        leading: Icon(Icons.error_outline, color: Theme.of(context).colorScheme.error),
        title: Text(message),
        trailing: onRetry == null
            ? null
            : TextButton(onPressed: onRetry, child: const Text('Retry')),
      ),
    );
  }
}
