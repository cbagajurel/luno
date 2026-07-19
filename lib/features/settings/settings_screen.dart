import 'package:flutter/material.dart' hide ConnectionState;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/generated/luno_api.g.dart';
import '../../bridge/luno_bridge.dart';
import '../../state/connection_providers.dart';
import '../../state/device_providers.dart';
import '../../state/pairing_providers.dart';
import '../../state/theme_providers.dart';
import '../../ui/ui.dart';
import '../shared/restricted_settings_sheet.dart';
import '../pairing/pairing_form.dart';
import '../shared/status_ui.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  Future<void> _confirmUnpair(BuildContext context, WidgetRef ref) async {
    final ok = await showLunoConfirm(
      context: context,
      title: 'Unpair node?',
      message:
          'This clears the backend credential and disconnects. '
          'Queued messages and history are unaffected.',
      confirmLabel: 'Unpair',
      destructive: true,
    );
    if (ok) {
      await ref.read(pairingProvider.notifier).unpair();
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final permissions = ref.watch(permissionsProvider);
    final connection =
        ref.watch(connectionStateProvider).value ?? ConnectionState.unknown;
    final connUi = connectionUi(connection);

    return LunoScaffold(
      title: 'Settings',
      body: ListView(
        padding: EdgeInsets.fromLTRB(
          LunoSpacing.md,
          0,
          LunoSpacing.md,
          context.navClearance,
        ),
        children: [
          const SectionHeader('Appearance'),
          const _ThemeToggle(),
          SectionHeader(
            'Permissions',
            trailing: LunoButton(
              label: 'Refresh',
              variant: LunoButtonVariant.text,
              icon: Icons.refresh_rounded,
              onPressed: () => ref.read(permissionsProvider.notifier).refresh(),
            ),
          ),
          permissions.when(
            skipLoadingOnReload: true,
            loading: () => const LunoCard(child: LoadingState()),
            error: (e, _) => StatusTile(
              icon: Icons.error_outline_rounded,
              tone: StatusTone.danger,
              title: 'Could not read permissions',
              subtitle: '$e',
            ),
            data: (p) => LunoCard(
              child: Column(
                children: [
                  for (final perm in AppPermission.values)
                    if (p.supports(perm))
                      PermissionTile(
                        label: perm.label,
                        rationale: perm.rationale,
                        granted: p.has(perm),
                        blocked: p.isBlocked(perm),
                        showGrantedState: true,
                        // Always try the real prompt; the sheet is the fallback
                        // only when the live attempt is itself blocked.
                        onGrant: () async {
                          final status = await ref
                              .read(permissionsProvider.notifier)
                              .request(perm);
                          if (status == PermissionStatus.blocked &&
                              context.mounted) {
                            await showRestrictedSettingsSheet(
                              context,
                              ref,
                              perm,
                            );
                          }
                        },
                      ),
                ],
              ),
            ),
          ),
          const SectionHeader('Backend'),
          StatusTile(
            icon: connUi.icon,
            tone: connUi.tone,
            title: 'Connection',
            subtitle: connUi.label,
          ),
          const SizedBox(height: LunoSpacing.xs),
          StatusTile(
            icon: Icons.sync_rounded,
            tone: StatusTone.brand,
            title: 'Reconnect / re-pair',
            subtitle: 'Re-enrol with a backend URL and pairing code',
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => showReconnectSheet(context),
          ),
          const SizedBox(height: LunoSpacing.xs),
          StatusTile(
            icon: Icons.link_off_rounded,
            tone: StatusTone.danger,
            title: 'Unpair node',
            subtitle: 'Clear credential and disconnect',
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => _confirmUnpair(context, ref),
          ),
          const SectionHeader('About'),
          const StatusTile(
            icon: Icons.hub_rounded,
            tone: StatusTone.brand,
            title: 'Luno',
            subtitle: 'Self-hosted SMS gateway node',
          ),
        ],
      ),
    );
  }
}

class _ThemeToggle extends ConsumerWidget {
  const _ThemeToggle();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mode = ref.watch(themeModeProvider);
    return LunoCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Theme', style: context.text.titleSmall),
                    const SizedBox(height: 2),
                    Text(
                      'System follows your device setting.',
                      style: context.text.bodySmall?.copyWith(
                        color: context.scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: LunoSpacing.sm),
          SizedBox(
            width: double.infinity,
            child: SegmentedButton<ThemeMode>(
              showSelectedIcon: false,
              style: SegmentedButton.styleFrom(
                textStyle: context.text.labelLarge,
                shape: const RoundedRectangleBorder(
                  borderRadius: LunoRadius.field,
                ),
              ),
              segments: const [
                ButtonSegment(value: ThemeMode.system, label: Text('System')),
                ButtonSegment(value: ThemeMode.light, label: Text('Light')),
                ButtonSegment(value: ThemeMode.dark, label: Text('Dark')),
              ],
              selected: {mode},
              onSelectionChanged: (selection) =>
                  ref.read(themeModeProvider.notifier).set(selection.first),
            ),
          ),
        ],
      ),
    );
  }
}
