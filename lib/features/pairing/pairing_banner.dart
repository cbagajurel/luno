import 'package:flutter/material.dart';

import '../../ui/ui.dart';

/// The inline failure note shared by the enrol form, the QR scanner and the
/// approval wait, so every pairing surface reports problems the same way.
class PairingErrorBanner extends StatelessWidget {
  const PairingErrorBanner(this.message, {super.key});

  final String message;

  @override
  Widget build(BuildContext context) {
    final danger = context.semantic.danger;
    return Container(
      padding: const EdgeInsets.all(LunoSpacing.sm),
      decoration: BoxDecoration(
        color: danger.container,
        borderRadius: LunoRadius.field,
      ),
      child: Row(
        children: [
          Icon(
            Icons.error_outline_rounded,
            size: 18,
            color: danger.onContainer,
          ),
          const SizedBox(width: LunoSpacing.xs),
          Expanded(
            child: Text(
              message,
              style: context.text.bodySmall?.copyWith(
                color: danger.onContainer,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
