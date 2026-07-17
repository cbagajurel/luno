import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/pairing_providers.dart';

/// Reusable enrolment form: an editable backend URL (pre-filled with the last one
/// used this session) plus a pairing code. Shared by the full pairing screen and
/// the inline reconnect sheet so both stay in sync.
class PairingForm extends ConsumerStatefulWidget {
  const PairingForm({
    super.key,
    this.submitLabel = 'Pair',
    this.busyLabel = 'Pairing…',
    this.onPaired,
    this.autofocusCode = false,
  });

  final String submitLabel;
  final String busyLabel;

  /// Called after a successful enrol. When null, the router's pairing gate takes
  /// over (used by the full-screen flow); the reconnect sheet passes a pop.
  final VoidCallback? onPaired;
  final bool autofocusCode;

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
    _urlController = TextEditingController(text: ref.read(lastBackendUrlProvider));
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
      final result = await ref.read(pairingProvider.notifier).pair(
            _urlController.text.trim(),
            _codeController.text.trim(),
          );
      if (!mounted) return;
      if (result.ok) {
        widget.onPaired?.call();
      } else {
        setState(() => _error = result.message ?? result.errorCode ?? 'Pairing failed');
      }
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Form(
      key: _formKey,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextFormField(
            controller: _urlController,
            keyboardType: TextInputType.url,
            autocorrect: false,
            enabled: !_submitting,
            decoration: const InputDecoration(
              labelText: 'Backend URL',
              hintText: 'https://gateway.example.com',
              border: OutlineInputBorder(),
            ),
            validator: (v) =>
                (v == null || v.trim().isEmpty) ? 'Enter the backend URL' : null,
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: _codeController,
            enabled: !_submitting,
            autofocus: widget.autofocusCode,
            decoration: const InputDecoration(
              labelText: 'Pairing code',
              border: OutlineInputBorder(),
            ),
            validator: (v) =>
                (v == null || v.trim().isEmpty) ? 'Enter the pairing code' : null,
          ),
          if (_error != null) ...[
            const SizedBox(height: 16),
            Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: _submitting ? null : _submit,
            icon: _submitting
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.check),
            label: Text(_submitting ? widget.busyLabel : widget.submitLabel),
          ),
        ],
      ),
    );
  }
}

/// Opens the enrol form in a bottom sheet so a disconnected node can be pointed
/// at a backend and re-enrolled without leaving the current screen.
Future<void> showReconnectSheet(BuildContext context) {
  final messenger = ScaffoldMessenger.of(context);
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (sheetCtx) => Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        16,
        16,
        16 + MediaQuery.of(sheetCtx).viewInsets.bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Reconnect node', style: Theme.of(sheetCtx).textTheme.titleLarge),
          const SizedBox(height: 4),
          Text(
            'Enter the backend URL and a fresh pairing code to re-enrol this node.',
            style: Theme.of(sheetCtx).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          PairingForm(
            submitLabel: 'Reconnect',
            busyLabel: 'Reconnecting…',
            autofocusCode: true,
            onPaired: () {
              Navigator.of(sheetCtx).pop();
              messenger.showSnackBar(
                const SnackBar(content: Text('Re-enrolled — reconnecting…')),
              );
            },
          ),
        ],
      ),
    ),
  );
}
