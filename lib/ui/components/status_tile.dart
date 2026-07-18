import 'package:flutter/material.dart';

import '../tokens/colors.dart';
import '../tokens/spacing.dart';
import 'luno_card.dart';

/// The canonical information row: a tinted leading icon, a title, an optional
/// subtitle, and an optional trailing widget — inside a [LunoCard]. This one
/// component replaces the connection banner and every telemetry/loading/error
/// card that was a `Card > ListTile(icon + title + subtitle)`.
class StatusTile extends StatelessWidget {
  const StatusTile({
    super.key,
    required this.icon,
    required this.title,
    this.tone = StatusTone.neutral,
    this.subtitle,
    this.trailing,
    this.onTap,
    this.leadingBox = true,
  });

  final IconData icon;
  final String title;
  final StatusTone tone;
  final String? subtitle;
  final Widget? trailing;
  final VoidCallback? onTap;

  /// When true the icon sits in a tinted rounded square; otherwise it's bare.
  final bool leadingBox;

  @override
  Widget build(BuildContext context) {
    final colors = context.semantic.tone(tone);

    final Widget leading = leadingBox
        ? Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: colors.container,
              borderRadius: BorderRadius.circular(LunoRadius.sm),
            ),
            child: Icon(icon, size: 20, color: colors.color),
          )
        : Icon(icon, color: colors.color);

    return LunoCard(
      onTap: onTap,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          leading,
          const SizedBox(width: LunoSpacing.sm),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  title,
                  style: context.text.titleSmall,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                if (subtitle != null) ...[
                  const SizedBox(height: 2),
                  Text(
                    subtitle!,
                    style: context.text.bodySmall?.copyWith(
                      color: context.scheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ],
            ),
          ),
          if (trailing != null) ...[
            const SizedBox(width: LunoSpacing.sm),
            trailing!,
          ],
        ],
      ),
    );
  }
}
