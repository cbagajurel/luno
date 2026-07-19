import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/generated/luno_api.g.dart';
import '../../state/pairing_providers.dart';
import '../../ui/ui.dart';
import 'pairing_banner.dart';
import 'pairing_messages.dart';

/// The wait for an operator to approve this device, for backends whose pairing
/// policy requires it. Native holds the pending enrolment durably, so closing
/// the app and coming back resumes the same wait instead of spending another
/// pairing code — this view only polls and renders.
class PairingPendingView extends ConsumerStatefulWidget {
  const PairingPendingView({super.key, required this.pending});

  final PendingPairing pending;

  @override
  ConsumerState<PairingPendingView> createState() => _PairingPendingViewState();
}

class _PairingPendingViewState extends ConsumerState<PairingPendingView> {
  Timer? _timer;
  bool _checking = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _schedule();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _schedule() {
    _timer?.cancel();
    _timer = Timer(Duration(milliseconds: widget.pending.retryAfterMs), _check);
  }

  Future<void> _check() async {
    if (_checking || !mounted) return;
    setState(() {
      _checking = true;
      _error = null;
    });
    try {
      final result = await ref.read(pendingPairingProvider.notifier).check();
      if (!mounted) return;
      if (result?.outcome == PairingOutcome.failure) {
        final message = pairingFailureText(result!);
        // A denial clears the pending enrolment natively, so this view is about
        // to be replaced by the form — say why on the way out. Anything else
        // (a network blip) leaves the wait intact, so report it in place.
        if (result.errorCode == 'approval_denied') {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text(message)));
        } else {
          setState(() => _error = message);
        }
      }
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    } finally {
      if (mounted) {
        setState(() => _checking = false);
        _schedule();
      }
    }
  }

  Future<void> _cancel() async {
    _timer?.cancel();
    await ref.read(pendingPairingProvider.notifier).cancel();
  }

  @override
  Widget build(BuildContext context) {
    final target = widget.pending.label ?? widget.pending.backendUrl;
    return LunoCard(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Center(
            child: SizedBox(
              width: 36,
              height: 36,
              child: CircularProgressIndicator(strokeWidth: 3),
            ),
          ),
          const SizedBox(height: LunoSpacing.lg),
          Text(
            'Waiting for approval',
            textAlign: TextAlign.center,
            style: context.text.titleMedium,
          ),
          const SizedBox(height: LunoSpacing.xs),
          Text(
            '$target accepted the pairing code and is waiting for an operator to '
            'approve this device. You can close the app — the wait continues.',
            textAlign: TextAlign.center,
            style: context.text.bodySmall?.copyWith(
              color: context.scheme.onSurfaceVariant,
            ),
          ),
          if (_error != null) ...[
            const SizedBox(height: LunoSpacing.md),
            PairingErrorBanner(_error!),
          ],
          const SizedBox(height: LunoSpacing.xl),
          LunoButton(
            label: 'Check now',
            busyLabel: 'Checking…',
            icon: Icons.refresh_rounded,
            busy: _checking,
            expand: true,
            onPressed: _check,
          ),
          const SizedBox(height: LunoSpacing.sm),
          LunoButton(
            label: 'Cancel and start over',
            variant: LunoButtonVariant.text,
            expand: true,
            onPressed: _checking ? null : _cancel,
          ),
        ],
      ),
    );
  }
}
