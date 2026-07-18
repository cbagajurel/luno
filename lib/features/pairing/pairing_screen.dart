import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../ui/ui.dart';
import 'pairing_form.dart';

class PairingScreen extends ConsumerWidget {
  const PairingScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
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
                      child: Icon(Icons.hub_rounded, size: 32, color: context.semantic.brand.color),
                    ),
                  ),
                  const SizedBox(height: LunoSpacing.lg),
                  Text(
                    'Enrol with your backend',
                    textAlign: TextAlign.center,
                    style: context.text.headlineSmall,
                  ),
                  const SizedBox(height: LunoSpacing.xs),
                  Text(
                    'Point this node at your Luno server and enter the pairing code it issued.',
                    textAlign: TextAlign.center,
                    style: context.text.bodyMedium?.copyWith(color: context.scheme.onSurfaceVariant),
                  ),
                  const SizedBox(height: LunoSpacing.xl),
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
