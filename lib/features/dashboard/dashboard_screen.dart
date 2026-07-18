import 'package:flutter/material.dart' hide ConnectionState;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/generated/luno_api.g.dart';
import '../../bridge/luno_bridge.dart';
import '../../state/bridge_providers.dart';
import '../../state/connection_providers.dart';
import '../../state/device_providers.dart';
import '../../ui/ui.dart';
import '../pairing/pairing_form.dart';
import '../shared/status_ui.dart';

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
    final connection = ref.watch(connectionStateProvider).value ?? ConnectionState.unknown;
    final agentState = ref.watch(agentStateProvider).value ?? AgentRunState.unknown;
    final device = ref.watch(deviceStateProvider);
    final permissions = ref.watch(permissionsProvider);
    final isRunning = agentState == AgentRunState.running;
    final hasPhonePermission = permissions.value?.phone ?? true;

    return LunoScaffold(
      title: 'Dashboard',
      actions: [
        IconButton(
          tooltip: 'Refresh',
          icon: const Icon(Icons.refresh_rounded),
          onPressed: () => ref.read(permissionsProvider.notifier).refresh(),
        ),
      ],
      body: RefreshIndicator(
        onRefresh: () async {
          await ref.read(permissionsProvider.notifier).refresh();
          await ref.read(deviceStateProvider.future);
        },
        child: ListView(
          padding: EdgeInsets.fromLTRB(
            LunoSpacing.md,
            LunoSpacing.md,
            LunoSpacing.md,
            LunoSpacing.md + kBottomNavClearance + MediaQuery.paddingOf(context).bottom,
          ),
          children: [
            _ConnectionBanner(
              state: connection,
              onReconnect: () => showReconnectSheet(context),
            ),
            const SizedBox(height: LunoSpacing.sm),
            _AgentControls(
              state: agentState,
              onStart: isRunning ? null : () => _startAgent(ref),
              onStop: isRunning ? () => _stopAgent(ref) : null,
            ),
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

class _ConnectionBanner extends StatelessWidget {
  const _ConnectionBanner({required this.state, this.onReconnect});

  final ConnectionState state;
  final VoidCallback? onReconnect;

  @override
  Widget build(BuildContext context) {
    final ui = connectionUi(state);
    final showReconnect = onReconnect != null && _needsReconnect(state);
    return StatusTile(
      icon: ui.icon,
      tone: ui.tone,
      title: ui.label,
      subtitle: 'Backend connection',
      trailing: showReconnect
          ? LunoButton(
              label: 'Reconnect',
              variant: LunoButtonVariant.text,
              onPressed: onReconnect,
            )
          : null,
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
    return LunoCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text('Gateway agent', style: context.text.titleSmall)),
              StatusPill(label: ui.label, tone: ui.tone, icon: ui.icon, dense: true),
            ],
          ),
          const SizedBox(height: LunoSpacing.md),
          Row(
            children: [
              Expanded(
                child: LunoButton(
                  label: 'Start',
                  icon: Icons.play_arrow_rounded,
                  onPressed: onStart,
                  expand: true,
                ),
              ),
              const SizedBox(width: LunoSpacing.sm),
              Expanded(
                child: LunoButton(
                  label: 'Stop',
                  variant: LunoButtonVariant.secondary,
                  icon: Icons.stop_rounded,
                  onPressed: onStop,
                  expand: true,
                ),
              ),
            ],
          ),
        ],
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
      loading: () => const SizedBox.shrink(),
      error: (e, _) => Padding(
        padding: const EdgeInsets.only(top: LunoSpacing.sm),
        child: StatusTile(
          icon: Icons.error_outline_rounded,
          tone: StatusTone.danger,
          title: 'Could not read permissions',
          trailing: LunoButton(
            label: 'Retry',
            variant: LunoButtonVariant.text,
            onPressed: onRetry,
          ),
        ),
      ),
      data: (p) {
        final missing = p.missing;
        if (missing.isEmpty) return const SizedBox.shrink();
        final caution = context.semantic.caution;
        return Padding(
          padding: const EdgeInsets.only(top: LunoSpacing.sm),
          child: LunoCard(
            color: caution.container,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(Icons.lock_outline_rounded, size: 20, color: caution.onContainer),
                    const SizedBox(width: LunoSpacing.xs),
                    Text(
                      'Permissions needed',
                      style: context.text.titleSmall?.copyWith(color: caution.onContainer),
                    ),
                  ],
                ),
                const SizedBox(height: LunoSpacing.xxs),
                Text(
                  'Luno needs these to send, receive, and read SIM state.',
                  style: context.text.bodySmall?.copyWith(color: caution.onContainer),
                ),
                const SizedBox(height: LunoSpacing.xs),
                for (final perm in missing)
                  PermissionTile(
                    label: perm.label,
                    rationale: perm.rationale,
                    granted: false,
                    onGrant: () => onGrant(perm),
                  ),
              ],
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
    return AsyncView<DeviceState>(
      value: device,
      loading: (_) => const Padding(
        padding: EdgeInsets.only(top: LunoSpacing.xxl),
        child: LoadingState(message: 'Reading device telemetry…'),
      ),
      error: (_) => const Padding(
        padding: EdgeInsets.only(top: LunoSpacing.sm),
        child: StatusTile(
          icon: Icons.error_outline_rounded,
          tone: StatusTone.danger,
          title: 'Device telemetry unavailable',
        ),
      ),
      data: (d) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SectionHeader('Network'),
          _networkTile(d.network),
          const SectionHeader('Battery'),
          _batteryTile(d.battery),
          const SectionHeader('SIMs'),
          if (!hasPhonePermission)
            const StatusTile(
              icon: Icons.sim_card_alert_rounded,
              tone: StatusTone.caution,
              title: 'Phone permission needed',
              subtitle: 'Grant it above to list SIMs.',
            )
          else
            _SimList(device: d),
        ],
      ),
    );
  }

  Widget _networkTile(NetworkStatus? n) {
    if (n == null || !n.connected) {
      return const StatusTile(
        icon: Icons.cloud_off_rounded,
        tone: StatusTone.neutral,
        title: 'Offline',
      );
    }
    final online = n.validated;
    return StatusTile(
      icon: online ? Icons.cloud_done_rounded : Icons.cloud_queue_rounded,
      tone: online ? StatusTone.positive : StatusTone.caution,
      title: online ? 'Online · ${n.transport}' : 'Connected, no internet · ${n.transport}',
      subtitle: n.metered ? 'metered' : 'unmetered',
    );
  }

  Widget _batteryTile(BatteryStatus? b) {
    if (b == null) {
      return const StatusTile(
        icon: Icons.battery_unknown_rounded,
        tone: StatusTone.neutral,
        title: 'Battery status unavailable',
      );
    }
    final level = b.levelPercent >= 0 ? '${b.levelPercent}%' : 'unknown';
    final source = b.plugged == 'NONE' ? 'on battery' : 'charging via ${b.plugged}';
    return StatusTile(
      icon: b.isCharging ? Icons.battery_charging_full_rounded : Icons.battery_full_rounded,
      tone: b.isCharging ? StatusTone.positive : StatusTone.neutral,
      title: '$level · ${b.isCharging ? 'charging' : 'discharging'}',
      subtitle: '$source · health ${b.health}',
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
      return const StatusTile(
        icon: Icons.sim_card_alert_rounded,
        tone: StatusTone.neutral,
        title: 'No active SIM detected',
        subtitle: 'Insert a SIM card, or check that it is enabled.',
      );
    }
    final signals = {for (final s in device.signals) s.subscriptionId: s};
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final sim in sims) ...[
          _SimTile(sim: sim, signal: signals[sim.subscriptionId]),
          const SizedBox(height: LunoSpacing.xs),
        ],
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
    final level = signal?.level ?? -1;
    final signalText = signal == null
        ? 'signal unknown'
        : (signal!.dbm != null ? '${signal!.dbm} dBm' : 'signal ${signal!.level}/4');
    return StatusTile(
      icon: sim.isEmbedded ? Icons.sim_card_download_rounded : Icons.sim_card_rounded,
      tone: StatusTone.brand,
      title: simLabel(sim),
      subtitle: 'Slot ${sim.slotIndex} · ${sim.isEmbedded ? 'eSIM' : 'physical'} · $signalText',
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SignalBars(level: level),
          const SizedBox(width: LunoSpacing.xs),
          StatusPill(label: sim.simState, dense: true),
        ],
      ),
    );
  }
}
