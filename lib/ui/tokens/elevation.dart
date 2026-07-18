import 'package:flutter/material.dart';

/// Soft, ambient shadows — separation comes from elevation and surface tint,
/// never from harsh borders. Values are intentionally low-spread with a small
/// downward offset so surfaces float rather than cut.
abstract final class LunoElevation {
  static List<BoxShadow> card(Brightness brightness) {
    final strong = brightness == Brightness.dark ? 0.44 : 0.10;
    final soft = brightness == Brightness.dark ? 0.30 : 0.05;
    return [
      BoxShadow(
        color: Colors.black.withValues(alpha: soft),
        blurRadius: 2,
        offset: const Offset(0, 1),
      ),
      BoxShadow(
        color: Colors.black.withValues(alpha: strong),
        blurRadius: 16,
        offset: const Offset(0, 6),
      ),
    ];
  }

  static List<BoxShadow> floating(Brightness brightness) {
    final strong = brightness == Brightness.dark ? 0.52 : 0.16;
    return [
      BoxShadow(
        color: Colors.black.withValues(alpha: strong),
        blurRadius: 28,
        offset: const Offset(0, 10),
      ),
    ];
  }
}
