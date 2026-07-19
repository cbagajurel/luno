import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/pairing_providers.dart';
import '../../ui/ui.dart';
import 'pairing_form.dart';
import 'pairing_pending_view.dart';

class PairingScreen extends ConsumerWidget {
  const PairingScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // A pending enrolment outlives the UI, so the screen asks native what state
    // it is in rather than assuming a fresh start.
    final pending = ref.watch(pendingPairingProvider).value;
    final awaitingApproval = pending != null;

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(LunoSpacing.xl),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Center(
                    child: Container(
                      width: 64,
                      height: 64,
                      decoration: BoxDecoration(
                        color: context.semantic.brand.container,
                        borderRadius: BorderRadius.circular(18),
                      ),
                      child: Icon(
                        awaitingApproval
                            ? Icons.hourglass_top_rounded
                            : Icons.hub_rounded,
                        size: 32,
                        color: context.semantic.brand.color,
                      ),
                    ),
                  ),
                  const SizedBox(height: LunoSpacing.lg),
                  Text(
                    awaitingApproval
                        ? 'Almost there'
                        : 'Enrol with your backend',
                    textAlign: TextAlign.center,
                    style: context.text.headlineSmall,
                  ),
                  const SizedBox(height: LunoSpacing.xs),
                  Text(
                    awaitingApproval
                        ? 'This device has been submitted for approval.'
                        : 'Scan the pairing QR code from your Luno server, or enter its URL and code by hand.',
                    textAlign: TextAlign.center,
                    style: context.text.bodyMedium?.copyWith(
                      color: context.scheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: LunoSpacing.xl),
                  if (awaitingApproval)
                    PairingPendingView(pending: pending)
                  else
                    const LunoCard(child: PairingForm()),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
