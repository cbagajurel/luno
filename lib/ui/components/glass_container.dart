import 'dart:ui';

import 'package:flutter/material.dart';

import '../tokens/colors.dart';

/// A bounded frosted-glass surface. Blur is clipped to [borderRadius] and cached
/// behind a [RepaintBoundary] so it stays cheap on lower-end devices. Reserved
/// for the few surfaces that float over content — nav, sheets, dialogs — never
/// whole screens.
class GlassContainer extends StatelessWidget {
  const GlassContainer({
    super.key,
    required this.child,
    this.borderRadius = const BorderRadius.all(Radius.circular(20)),
    this.sigma = 12,
    this.padding,
    this.showBorder = true,
    this.shadows,
  });

  final Widget child;
  final BorderRadius borderRadius;
  final double sigma;
  final EdgeInsetsGeometry? padding;
  final bool showBorder;
  final List<BoxShadow>? shadows;

  @override
  Widget build(BuildContext context) {
    final semantic = context.semantic;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final tint = semantic.glass.withValues(alpha: isDark ? 0.62 : 0.74);

    Widget content = Container(
      padding: padding,
      decoration: BoxDecoration(
        color: tint,
        borderRadius: borderRadius,
        border: showBorder ? Border.all(color: semantic.glassBorder) : null,
      ),
      child: child,
    );

    Widget glass = RepaintBoundary(
      child: ClipRRect(
        borderRadius: borderRadius,
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: sigma, sigmaY: sigma),
          child: content,
        ),
      ),
    );

    if (shadows != null) {
      glass = DecoratedBox(
        decoration: BoxDecoration(
          borderRadius: borderRadius,
          boxShadow: shadows,
        ),
        child: glass,
      );
    }
    return glass;
  }
}
