import 'package:flutter/widgets.dart';

/// Motion tokens. Subtle and consistent — transitions clarify state changes,
/// they don't decorate. Curves lean on the emphasized easing that Material 3
/// Expressive uses so movement feels physical without overshoot noise.
abstract final class LunoMotion {
  static const Duration fast = Duration(milliseconds: 120);
  static const Duration base = Duration(milliseconds: 220);
  static const Duration slow = Duration(milliseconds: 320);

  static const Curve emphasized = Cubic(0.2, 0.0, 0.0, 1.0);
  static const Curve standard = Curves.easeOutCubic;

  /// Honour the OS "remove animations" accessibility setting.
  static bool reduced(BuildContext context) =>
      MediaQuery.maybeOf(context)?.disableAnimations ?? false;
}
