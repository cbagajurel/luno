import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/generated/luno_api.g.dart';
import '../../state/bridge_providers.dart';
import '../../state/device_providers.dart';
import '../../ui/ui.dart';
import '../shared/restricted_settings_sheet.dart';
import '../shared/status_ui.dart';

Future<void> showComposeSheet(BuildContext context) {
  return showLunoSheet<void>(
    context: context,
    title: 'Compose SMS',
    subtitle: 'Send a message through this node.',
    builder: (_) => const _ComposeForm(),
  );
}

class _ComposeForm extends ConsumerStatefulWidget {
  const _ComposeForm();

  @override
  ConsumerState<_ComposeForm> createState() => _ComposeFormState();
}

class _ComposeFormState extends ConsumerState<_ComposeForm> {
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
      final status = await permissions.request(AppPermission.sms);
      perms = ref.read(permissionsProvider).value;
      if (perms == null || !perms.sms) {
        if (mounted) {
          setState(() => _sending = false);
          // Blocked means re-prompting is futile, so offer the settings route
          // rather than a snackbar the user can do nothing about.
          if (status == PermissionStatus.blocked) {
            await showRestrictedSettingsSheet(context, ref, AppPermission.sms);
          } else {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('SMS permission is required to send'),
              ),
            );
          }
        }
        return;
      }
    }

    await ref
        .read(bridgeProvider)
        .sendSms(recipient, body, subscriptionId: _subId);
    if (mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final sims =
        ref.watch(deviceStateProvider.select((d) => d.value?.sims)) ??
        const <SimInfo>[];

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LunoTextField(
          label: 'Recipient number',
          hint: '+15551112222',
          controller: _recipient,
          prefixIcon: Icons.person_outline_rounded,
          keyboardType: TextInputType.phone,
          inputFormatters: [
            FilteringTextInputFormatter.allow(RegExp(r'[0-9+\- ]')),
          ],
        ),
        const SizedBox(height: LunoSpacing.sm),
        LunoTextField(
          label: 'Message',
          controller: _body,
          minLines: 2,
          maxLines: 5,
        ),
        if (sims.isNotEmpty) ...[
          const SizedBox(height: LunoSpacing.sm),
          DropdownButtonFormField<int?>(
            initialValue: _subId,
            decoration: const InputDecoration(labelText: 'SIM'),
            onChanged: (v) => setState(() => _subId = v),
            items: [
              const DropdownMenuItem<int?>(
                value: null,
                child: Text('Default SIM'),
              ),
              for (final sim in sims)
                DropdownMenuItem<int?>(
                  value: sim.subscriptionId,
                  child: Text('${simLabel(sim)} · Slot ${sim.slotIndex}'),
                ),
            ],
          ),
        ],
        const SizedBox(height: LunoSpacing.lg),
        LunoButton(
          label: 'Send',
          busyLabel: 'Sending…',
          icon: Icons.send_rounded,
          busy: _sending,
          expand: true,
          onPressed: _send,
        ),
      ],
    );
  }
}
