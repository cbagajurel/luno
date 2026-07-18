import 'package:flutter/material.dart';

import '../tokens/colors.dart';
import '../tokens/spacing.dart';

/// A compact status chip in a semantic tone. Used for connection/agent/outbox
/// states, log levels, and SIM state — replacing the bespoke chips and badges.
class StatusPill extends StatelessWidget {
  const StatusPill({
    super.key,
    required this.label,
    this.tone = StatusTone.neutral,
    this.icon,
    this.dense = false,
  });

  final String label;
  final StatusTone tone;
  final IconData? icon;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    final colors = context.semantic.tone(tone);
    final textStyle =
        (dense ? context.text.labelSmall : context.text.labelMedium)?.copyWith(
          color: colors.onContainer,
          letterSpacing: 0.2,
        );
    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: dense ? LunoSpacing.xs : LunoSpacing.sm,
        vertical: dense ? 2 : 4,
      ),
      decoration: BoxDecoration(
        color: colors.container,
        borderRadius: LunoRadius.stadium,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: dense ? 12 : 14, color: colors.onContainer),
            const SizedBox(width: 4),
          ],
          Text(label, style: textStyle),
        ],
      ),
    );
  }
}

/// A minimal coloured dot for inline status, when a full pill is too heavy.
class StatusDot extends StatelessWidget {
  const StatusDot(this.tone, {super.key, this.size = 8});

  final StatusTone tone;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: context.semantic.tone(tone).color,
        shape: BoxShape.circle,
      ),
    );
  }
}
