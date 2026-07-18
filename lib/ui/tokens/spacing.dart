import 'package:flutter/widgets.dart';

/// Vertical room a scroll view must leave at the bottom so its last item clears
/// the floating glass nav (add the device's bottom safe-area on top of this).
const double kBottomNavClearance = 88;

/// The 4-point spacing scale. Formalizes the ad-hoc 4/8/12/16/24 already used
/// across screens so gaps and padding stay on a single rhythm.
abstract final class LunoSpacing {
  static const double xxs = 4;
  static const double xs = 8;
  static const double sm = 12;
  static const double md = 16;
  static const double lg = 20;
  static const double xl = 24;
  static const double xxl = 32;

  static const gapXs = SizedBox(height: xs, width: xs);
  static const gapSm = SizedBox(height: sm, width: sm);
  static const gapMd = SizedBox(height: md, width: md);
  static const gapLg = SizedBox(height: lg, width: lg);

  static const screen = EdgeInsets.all(md);
  static const card = EdgeInsets.all(md);
}

/// Corner radii. Cards are calm (md), sheets softer (lg), pills fully rounded.
abstract final class LunoRadius {
  static const double sm = 10;
  static const double md = 14;
  static const double lg = 20;
  static const double xl = 28;
  static const double pill = 999;

  static const BorderRadius card = BorderRadius.all(Radius.circular(md));
  static const BorderRadius field = BorderRadius.all(Radius.circular(sm));
  static const BorderRadius sheet = BorderRadius.vertical(
    top: Radius.circular(xl),
  );
  static const BorderRadius stadium = BorderRadius.all(Radius.circular(pill));
}
