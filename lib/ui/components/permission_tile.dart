import 'package:flutter/material.dart';

import '../tokens/colors.dart';
import '../tokens/spacing.dart';
import 'luno_button.dart';
import 'status_pill.dart';

/// A single permission row: state icon, label + rationale, and a grant action
/// when it isn't held. One component for both the dashboard's "needs granting"
/// card and the settings list (the latter passes [showGrantedState]).
class PermissionTile extends StatelessWidget {
  const PermissionTile({
    super.key,
    required this.label,
    required this.rationale,
    required this.granted,
    this.onGrant,
    this.showGrantedState = false,
  });

  final String label;
  final String rationale;
  final bool granted;
  final VoidCallback? onGrant;
  final bool showGrantedState;

  @override
  Widget build(BuildContext context) {
    final semantic = context.semantic;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: LunoSpacing.xs),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            granted ? Icons.check_circle_rounded : Icons.error_outline_rounded,
            size: 20,
            color: granted ? semantic.positive.color : semantic.caution.color,
          ),
          const SizedBox(width: LunoSpacing.sm),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: context.text.titleSmall),
                const SizedBox(height: 2),
                Text(
                  rationale,
                  style: context.text.bodySmall?.copyWith(color: context.scheme.onSurfaceVariant),
                ),
              ],
            ),
          ),
          const SizedBox(width: LunoSpacing.sm),
          if (granted)
            (showGrantedState
                ? const StatusPill(label: 'Granted', tone: StatusTone.positive, dense: true)
                : const SizedBox.shrink())
          else
            LunoButton(
              label: 'Grant',
              variant: LunoButtonVariant.tonal,
              onPressed: onGrant,
            ),
        ],
      ),
    );
  }
}
