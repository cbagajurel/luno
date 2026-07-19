import 'package:flutter/material.dart';

import '../tokens/colors.dart';
import '../tokens/spacing.dart';
import 'luno_button.dart';
import 'status_pill.dart';

/// A single permission row: state icon, label + rationale, and an action when it
/// isn't held. One component for both the dashboard's "needs granting" card and
/// the settings list (the latter passes [showGrantedState]).
///
/// [blocked] adds an explanation but deliberately does **not** replace the Grant
/// action. The blocked hint goes stale the moment the user allows restricted
/// settings — nothing observable changes — so the button must stay able to fire a
/// real system prompt. Callers escalate to the recovery sheet only when an actual
/// attempt comes back blocked.
class PermissionTile extends StatelessWidget {
  const PermissionTile({
    super.key,
    required this.label,
    required this.rationale,
    required this.granted,
    this.blocked = false,
    this.onGrant,
    this.showGrantedState = false,
  });

  final String label;
  final String rationale;
  final bool granted;
  final bool blocked;
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
            switch ((granted, blocked)) {
              (true, _) => Icons.check_circle_rounded,
              (false, true) => Icons.lock_outline_rounded,
              (false, false) => Icons.error_outline_rounded,
            },
            size: 20,
            color: granted
                ? semantic.positive.color
                : (blocked ? semantic.danger.color : semantic.caution.color),
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
                  style: context.text.bodySmall?.copyWith(
                    color: context.scheme.onSurfaceVariant,
                  ),
                ),
                if (!granted && blocked) ...[
                  const SizedBox(height: 2),
                  Text(
                    'Android blocked the last prompt. If nothing appears, '
                    'allow restricted settings first.',
                    style: context.text.bodySmall?.copyWith(
                      color: semantic.danger.color,
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(width: LunoSpacing.sm),
          if (granted)
            (showGrantedState
                ? const StatusPill(
                    label: 'Granted',
                    tone: StatusTone.positive,
                    dense: true,
                  )
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
