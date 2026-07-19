import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/generated/luno_api.g.dart';
import '../../state/pairing_providers.dart';
import '../../ui/ui.dart';
import 'pairing_banner.dart';
import 'pairing_messages.dart';
import 'pairing_scan_screen.dart';

/// Reusable enrolment form: an editable backend URL (pre-filled with the last one
/// used this session) plus a pairing code, with a shortcut into QR scanning.
/// Shared by the full pairing screen and the inline reconnect sheet so both stay
/// in sync.
class PairingForm extends ConsumerStatefulWidget {
  const PairingForm({
    super.key,
    this.submitLabel = 'Pair',
    this.busyLabel = 'Pairing…',
    this.onPaired,
    this.autofocusCode = false,
    this.showScanAction = true,
  });

  final String submitLabel;
  final String busyLabel;

  /// Called after a successful enrol. When null, the router's pairing gate takes
  /// over (used by the full-screen flow); the reconnect sheet passes a pop.
  final VoidCallback? onPaired;
  final bool autofocusCode;

  /// The reconnect sheet hides it — the scanner wants the whole screen.
  final bool showScanAction;

  @override
  ConsumerState<PairingForm> createState() => _PairingFormState();
}

class _PairingFormState extends ConsumerState<PairingForm> {
  late final TextEditingController _urlController;
  final _codeController = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _submitting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _urlController = TextEditingController(
      text: ref.read(lastBackendUrlProvider),
    );
  }

  @override
  void dispose() {
    _urlController.dispose();
    _codeController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final result = await ref
          .read(pairingProvider.notifier)
          .pair(_urlController.text.trim(), _codeController.text.trim());
      if (!mounted) return;
      switch (result.outcome) {
        // Pending hands off the same way as success: the pairing screen swaps
        // itself for the approval wait, and the sheet just closes behind it.
        case PairingOutcome.success:
        case PairingOutcome.pending:
          widget.onPaired?.call();
        case PairingOutcome.failure:
          setState(() => _error = pairingFailureText(result));
      }
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _scan() async {
    final paired = await Navigator.of(
      context,
    ).push<bool>(MaterialPageRoute(builder: (_) => const PairingScanScreen()));
    if (paired == true && mounted) widget.onPaired?.call();
  }

  @override
  Widget build(BuildContext context) {
    return Form(
      key: _formKey,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (widget.showScanAction) ...[
            LunoButton(
              label: 'Scan QR code',
              icon: Icons.qr_code_scanner_rounded,
              variant: LunoButtonVariant.tonal,
              expand: true,
              onPressed: _submitting ? null : _scan,
            ),
            const SizedBox(height: LunoSpacing.lg),
            const _OrDivider(),
            const SizedBox(height: LunoSpacing.lg),
          ],
          LunoTextField(
            label: 'Backend URL',
            hint: 'https://gateway.example.com',
            controller: _urlController,
            prefixIcon: Icons.dns_rounded,
            keyboardType: TextInputType.url,
            enabled: !_submitting,
            validator: (v) => (v == null || v.trim().isEmpty)
                ? 'Enter the backend URL'
                : null,
          ),
          const SizedBox(height: LunoSpacing.md),
          LunoTextField(
            label: 'Pairing code',
            controller: _codeController,
            prefixIcon: Icons.key_rounded,
            autofocus: widget.autofocusCode,
            enabled: !_submitting,
            validator: (v) => (v == null || v.trim().isEmpty)
                ? 'Enter the pairing code'
                : null,
          ),
          if (_error != null) ...[
            const SizedBox(height: LunoSpacing.md),
            PairingErrorBanner(_error!),
          ],
          const SizedBox(height: LunoSpacing.xl),
          LunoButton(
            label: widget.submitLabel,
            busyLabel: widget.busyLabel,
            icon: Icons.check_rounded,
            busy: _submitting,
            expand: true,
            onPressed: _submit,
          ),
        ],
      ),
    );
  }
}

class _OrDivider extends StatelessWidget {
  const _OrDivider();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const Expanded(child: Divider()),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: LunoSpacing.sm),
          child: Text(
            'or enter it manually',
            style: context.text.labelSmall?.copyWith(
              color: context.scheme.onSurfaceVariant,
            ),
          ),
        ),
        const Expanded(child: Divider()),
      ],
    );
  }
}

/// Opens the enrol form in a bottom sheet so a disconnected node can be pointed
/// at a backend and re-enrolled without leaving the current screen.
Future<void> showReconnectSheet(BuildContext context) {
  final messenger = ScaffoldMessenger.of(context);
  return showLunoSheet<void>(
    context: context,
    title: 'Reconnect node',
    subtitle:
        'Enter the backend URL and a fresh pairing code to re-enrol this node.',
    builder: (sheetCtx) => PairingForm(
      submitLabel: 'Reconnect',
      busyLabel: 'Reconnecting…',
      autofocusCode: true,
      showScanAction: false,
      onPaired: () {
        Navigator.of(sheetCtx).pop();
        messenger.showSnackBar(
          const SnackBar(content: Text('Re-enrolled — reconnecting…')),
        );
      },
    ),
  );
}
