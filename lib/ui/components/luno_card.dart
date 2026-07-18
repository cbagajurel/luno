import 'package:flutter/material.dart';

import '../tokens/elevation.dart';
import '../tokens/spacing.dart';

/// The one solid surface primitive. A calm, softly-shadowed container with the
/// standard card radius; tappable when [onTap] is given. Replaces every ad-hoc
/// `Card(...)` across the screens.
class LunoCard extends StatelessWidget {
  const LunoCard({
    super.key,
    required this.child,
    this.padding = LunoSpacing.card,
    this.onTap,
    this.color,
    this.borderColor,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final VoidCallback? onTap;
  final Color? color;
  final Color? borderColor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final bg = color ?? theme.cardTheme.color ?? theme.colorScheme.surfaceContainer;

    final content = Padding(padding: padding, child: child);

    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: LunoRadius.card,
        boxShadow: LunoElevation.card(theme.brightness),
      ),
      child: Material(
        color: bg,
        clipBehavior: Clip.antiAlias,
        shape: RoundedRectangleBorder(
          borderRadius: LunoRadius.card,
          side: borderColor == null ? BorderSide.none : BorderSide(color: borderColor!),
        ),
        child: onTap == null
            ? content
            : InkWell(onTap: onTap, child: content),
      ),
    );
  }
}
