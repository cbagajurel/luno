import 'package:flutter/material.dart' hide ConnectionState;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/luno_bridge.dart';
import '../../state/connection_providers.dart';
import '../../state/device_providers.dart';
import '../../state/pairing_providers.dart';
import '../shared/status_ui.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  Future<void> _confirmUnpair(BuildContext context, WidgetRef ref) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Unpair node?'),
        content: const Text(
          'This clears the backend credential and disconnects. '
          'Queued messages and history are unaffected.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Unpair')),
        ],
      ),
    );
    if (ok == true) {
      await ref.read(pairingProvider.notifier).unpair();
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final permissions = ref.watch(permissionsProvider);
    final connection = ref.watch(connectionStateProvider).value ?? ConnectionState.unknown;
    final connUi = connectionUi(connection);

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        children: [
          const _SectionHeader('Permissions'),
          permissions.when(
            loading: () => const ListTile(title: Text('Checking permissions…')),
            error: (e, _) => ListTile(title: Text('$e')),
            data: (p) => Column(
              children: [
                _PermissionTile(
                  label: 'Phone / SIM',
                  granted: p.phone,
                  onGrant: () => ref.read(permissionsProvider.notifier).requestPhone(),
                ),
                _PermissionTile(
                  label: 'Send SMS',
                  granted: p.sms,
                  onGrant: () => ref.read(permissionsProvider.notifier).requestSms(),
                ),
                _PermissionTile(
                  label: 'Receive SMS',
                  granted: p.receiveSms,
                  onGrant: () => ref.read(permissionsProvider.notifier).requestReceiveSms(),
                ),
                ListTile(
                  trailing: TextButton.icon(
                    onPressed: () => ref.read(permissionsProvider.notifier).refresh(),
                    icon: const Icon(Icons.refresh),
                    label: const Text('Refresh'),
                  ),
                ),
              ],
            ),
          ),
          const Divider(),
          const _SectionHeader('Backend'),
          ListTile(
            leading: Icon(connUi.icon, color: connUi.color),
            title: const Text('Connection'),
            subtitle: Text(connUi.label),
          ),
          ListTile(
            leading: const Icon(Icons.link_off),
            title: const Text('Unpair node'),
            subtitle: const Text('Clear credential and disconnect'),
            onTap: () => _confirmUnpair(context, ref),
          ),
          const Divider(),
          const _SectionHeader('About'),
          const ListTile(
            leading: Icon(Icons.info_outline),
            title: Text('Luno'),
            subtitle: Text('Self-hosted SMS gateway node'),
          ),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
      child: Text(
        text.toUpperCase(),
        style: Theme.of(context).textTheme.labelMedium?.copyWith(
              color: Theme.of(context).colorScheme.primary,
              fontWeight: FontWeight.bold,
            ),
      ),
    );
  }
}

class _PermissionTile extends StatelessWidget {
  const _PermissionTile({
    required this.label,
    required this.granted,
    required this.onGrant,
  });

  final String label;
  final bool granted;
  final Future<void> Function() onGrant;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(
        granted ? Icons.check_circle : Icons.cancel,
        color: granted ? Colors.green : Colors.grey,
      ),
      title: Text(label),
      subtitle: Text(granted ? 'Granted' : 'Not granted'),
      trailing: granted
          ? null
          : TextButton(onPressed: onGrant, child: const Text('Grant')),
    );
  }
}
