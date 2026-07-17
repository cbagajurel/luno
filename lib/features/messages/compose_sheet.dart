import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/generated/luno_api.g.dart';
import '../../state/bridge_providers.dart';
import '../../state/device_providers.dart';
import '../shared/status_ui.dart';

Future<void> showComposeSheet(BuildContext context) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => const _ComposeSheet(),
  );
}

class _ComposeSheet extends ConsumerStatefulWidget {
  const _ComposeSheet();

  @override
  ConsumerState<_ComposeSheet> createState() => _ComposeSheetState();
}

class _ComposeSheetState extends ConsumerState<_ComposeSheet> {
  final _recipient = TextEditingController();
  final _body = TextEditingController();
  int? _subId;
  bool _sending = false;

  @override
  void dispose() {
    _recipient.dispose();
    _body.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    final recipient = _recipient.text.trim();
    final body = _body.text;
    if (recipient.isEmpty || body.isEmpty) return;

    setState(() => _sending = true);
    final permissions = ref.read(permissionsProvider.notifier);
    var perms = ref.read(permissionsProvider).value;
    if (perms == null || !perms.sms) {
      await permissions.request(AppPermission.sms);
      perms = ref.read(permissionsProvider).value;
      if (perms == null || !perms.sms) {
        if (mounted) {
          setState(() => _sending = false);
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('SMS permission is required to send')),
          );
        }
        return;
      }
    }

    await ref.read(bridgeProvider).sendSms(recipient, body, subscriptionId: _subId);
    if (mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final sims = ref.watch(deviceStateProvider).value?.sims ?? const <SimInfo>[];
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;

    return Padding(
      padding: EdgeInsets.fromLTRB(16, 16, 16, 16 + bottomInset),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Compose SMS', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 16),
          TextField(
            controller: _recipient,
            keyboardType: TextInputType.phone,
            decoration: const InputDecoration(
              labelText: 'Recipient number',
              hintText: '+15551112222',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _body,
            minLines: 2,
            maxLines: 5,
            decoration: const InputDecoration(
              labelText: 'Message',
              border: OutlineInputBorder(),
            ),
          ),
          if (sims.isNotEmpty) ...[
            const SizedBox(height: 12),
            DropdownButtonFormField<int?>(
              initialValue: _subId,
              decoration: const InputDecoration(
                labelText: 'SIM',
                border: OutlineInputBorder(),
              ),
              onChanged: (v) => setState(() => _subId = v),
              items: [
                const DropdownMenuItem<int?>(value: null, child: Text('Default SIM')),
                for (final sim in sims)
                  DropdownMenuItem<int?>(
                    value: sim.subscriptionId,
                    child: Text('${simLabel(sim)} · Slot ${sim.slotIndex}'),
                  ),
              ],
            ),
          ],
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: _sending ? null : _send,
            icon: _sending
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.send),
            label: Text(_sending ? 'Sending…' : 'Send'),
          ),
        ],
      ),
    );
  }
}
