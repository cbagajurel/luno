import 'package:flutter/material.dart';

/// Bundled typefaces (declared in pubspec `fonts:`). Geist for UI, Geist Mono
/// for log lines, ids, and numeric telemetry. Both are variable fonts, so
/// `fontWeight` maps onto the weight axis.
abstract final class LunoFonts {
  static const sans = 'Geist';
  static const mono = 'GeistMono';
}

/// The type scale, on Geist, with slightly tightened tracking on the larger
/// sizes for an editorial, premium feel. Colours are applied by the theme.
TextTheme lunoTextTheme() {
  const family = LunoFonts.sans;
  return const TextTheme(
    displayLarge: TextStyle(
        fontFamily: family, fontSize: 52, height: 1.06, letterSpacing: -1.2, fontWeight: FontWeight.w600),
    displayMedium: TextStyle(
        fontFamily: family, fontSize: 40, height: 1.08, letterSpacing: -0.8, fontWeight: FontWeight.w600),
    displaySmall: TextStyle(
        fontFamily: family, fontSize: 32, height: 1.12, letterSpacing: -0.5, fontWeight: FontWeight.w600),
    headlineLarge: TextStyle(
        fontFamily: family, fontSize: 28, height: 1.15, letterSpacing: -0.4, fontWeight: FontWeight.w600),
    headlineMedium: TextStyle(
        fontFamily: family, fontSize: 24, height: 1.2, letterSpacing: -0.3, fontWeight: FontWeight.w600),
    headlineSmall: TextStyle(
        fontFamily: family, fontSize: 20, height: 1.25, letterSpacing: -0.2, fontWeight: FontWeight.w600),
    titleLarge: TextStyle(
        fontFamily: family, fontSize: 18, height: 1.25, letterSpacing: -0.2, fontWeight: FontWeight.w600),
    titleMedium: TextStyle(
        fontFamily: family, fontSize: 15.5, height: 1.3, letterSpacing: -0.1, fontWeight: FontWeight.w600),
    titleSmall: TextStyle(
        fontFamily: family, fontSize: 13.5, height: 1.3, letterSpacing: 0, fontWeight: FontWeight.w600),
    bodyLarge: TextStyle(
        fontFamily: family, fontSize: 15.5, height: 1.45, letterSpacing: 0, fontWeight: FontWeight.w400),
    bodyMedium: TextStyle(
        fontFamily: family, fontSize: 14, height: 1.45, letterSpacing: 0, fontWeight: FontWeight.w400),
    bodySmall: TextStyle(
        fontFamily: family, fontSize: 12.5, height: 1.4, letterSpacing: 0.1, fontWeight: FontWeight.w400),
    labelLarge: TextStyle(
        fontFamily: family, fontSize: 14, height: 1.2, letterSpacing: 0.1, fontWeight: FontWeight.w600),
    labelMedium: TextStyle(
        fontFamily: family, fontSize: 12, height: 1.2, letterSpacing: 0.4, fontWeight: FontWeight.w600),
    labelSmall: TextStyle(
        fontFamily: family, fontSize: 11, height: 1.2, letterSpacing: 0.5, fontWeight: FontWeight.w600),
  );
}

/// A monospaced style for log rows, message ids, and dBm/numeric fields.
const TextStyle lunoMonoStyle = TextStyle(
  fontFamily: LunoFonts.mono,
  fontSize: 12.5,
  height: 1.4,
  letterSpacing: 0,
);
