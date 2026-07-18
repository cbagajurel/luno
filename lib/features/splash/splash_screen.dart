import 'package:flutter/material.dart';

import '../../ui/ui.dart';

class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const _LunoMark(),
            const SizedBox(height: LunoSpacing.lg),
            Text('Luno', style: context.text.headlineMedium),
            const SizedBox(height: LunoSpacing.xs),
            Text(
              'Self-hosted SMS gateway',
              style: context.text.bodyMedium?.copyWith(
                color: context.scheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: LunoSpacing.xxl),
            SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(
                strokeWidth: 2.5,
                color: context.scheme.primary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// The brand mark: a rounded teal tile with a signal glyph. Used on splash and
/// pairing so the app has a consistent identity before the shell loads.
class _LunoMark extends StatelessWidget {
  const _LunoMark();

  @override
  Widget build(BuildContext context) {
    final brand = context.semantic.brand;
    return Container(
      width: 72,
      height: 72,
      decoration: BoxDecoration(
        color: brand.container,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Icon(Icons.hub_rounded, size: 36, color: brand.color),
    );
  }
}
