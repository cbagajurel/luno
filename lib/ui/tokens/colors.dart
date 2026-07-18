import 'package:flutter/material.dart';

/// Raw palette — the single source of every colour in the app. Nothing outside
/// this file should hardcode a hex value; widgets read colours from the built
/// [ColorScheme] or the [LunoSemantic] theme extension.
///
/// Direction: "Graphite & Signal Teal" — neutral ink/graphite surfaces, one
/// restrained teal accent that reads as signal/connectivity, and semantic
/// status colours kept distinct from the brand.
abstract final class LunoPalette {
  // Brand
  static const tealLight = Color(0xFF0F766E);
  static const tealDark = Color(0xFF14B8A6);
  static const tealDeep = Color(0xFF0E3A34);
  static const tealSoft = Color(0xFFCFF3EE);

  // Dark graphite ramp
  static const inkBg = Color(0xFF0E1113);
  static const inkLowest = Color(0xFF0A0C0E);
  static const inkLow = Color(0xFF131619);
  static const inkSurface = Color(0xFF16191C);
  static const inkHigh = Color(0xFF1E2226);
  static const inkHighest = Color(0xFF262B30);
  static const inkOutline = Color(0xFF3A4247);
  static const inkDivider = Color(0xFF262B30);

  static const inkTextHi = Color(0xFFE7EAEC);
  static const inkTextMid = Color(0xFFA6ADB2);
  static const inkTextLo = Color(0xFF6E777D);

  // Light neutral ramp
  static const paperBg = Color(0xFFFBFBFA);
  static const paperLowest = Color(0xFFFFFFFF);
  static const paperLow = Color(0xFFF6F7F6);
  static const paperSurface = Color(0xFFF1F2F1);
  static const paperHigh = Color(0xFFECEEED);
  static const paperHighest = Color(0xFFE6E8E6);
  static const paperOutline = Color(0xFFC3C9C6);
  static const paperDivider = Color(0xFFE6E8E6);

  static const paperTextHi = Color(0xFF14181B);
  static const paperTextMid = Color(0xFF4A5157);
  static const paperTextLo = Color(0xFF808A90);

  // Semantic sources (info deliberately a true blue, never purple)
  static const blue = Color(0xFF2563EB);
  static const blueBright = Color(0xFF60A5FA);
  static const red = Color(0xFFDC2626);
  static const redBright = Color(0xFFF87171);
  static const green = Color(0xFF2E9E5B);
  static const greenBright = Color(0xFF34D399);
  static const amber = Color(0xFFC08401);
  static const amberBright = Color(0xFFFBBF24);
}

/// A four-part colour role for a status tone: the mark colour, the colour of
/// content drawn on it, and a tinted container pair for chips/cards.
@immutable
class ToneColors {
  const ToneColors({
    required this.color,
    required this.onColor,
    required this.container,
    required this.onContainer,
  });

  final Color color;
  final Color onColor;
  final Color container;
  final Color onContainer;

  static ToneColors lerp(ToneColors a, ToneColors b, double t) => ToneColors(
        color: Color.lerp(a.color, b.color, t)!,
        onColor: Color.lerp(a.onColor, b.onColor, t)!,
        container: Color.lerp(a.container, b.container, t)!,
        onContainer: Color.lerp(a.onContainer, b.onContainer, t)!,
      );
}

/// Semantic tones a status can map to. Kept small and meaning-first so screens
/// never reach for a raw [Colors] constant.
enum StatusTone { positive, caution, danger, info, neutral, brand }

/// Theme extension carrying everything the base [ColorScheme] doesn't model:
/// the semantic status tones, glass overlay colours, and the faint text tier.
@immutable
class LunoSemantic extends ThemeExtension<LunoSemantic> {
  const LunoSemantic({
    required this.positive,
    required this.caution,
    required this.danger,
    required this.info,
    required this.neutral,
    required this.brand,
    required this.textFaint,
    required this.glass,
    required this.glassBorder,
  });

  final ToneColors positive;
  final ToneColors caution;
  final ToneColors danger;
  final ToneColors info;
  final ToneColors neutral;
  final ToneColors brand;

  /// Third-tier text (timestamps, meta) below onSurfaceVariant.
  final Color textFaint;

  /// Base fill for a frosted surface (blended behind a blur) and its hairline.
  final Color glass;
  final Color glassBorder;

  ToneColors tone(StatusTone tone) => switch (tone) {
        StatusTone.positive => positive,
        StatusTone.caution => caution,
        StatusTone.danger => danger,
        StatusTone.info => info,
        StatusTone.neutral => neutral,
        StatusTone.brand => brand,
      };

  static const dark = LunoSemantic(
    positive: ToneColors(
      color: LunoPalette.greenBright,
      onColor: Color(0xFF04211A),
      container: Color(0xFF10362A),
      onContainer: Color(0xFFA7F3D0),
    ),
    caution: ToneColors(
      color: LunoPalette.amberBright,
      onColor: Color(0xFF241A02),
      container: Color(0xFF3A2E0A),
      onContainer: Color(0xFFFDE68A),
    ),
    danger: ToneColors(
      color: LunoPalette.redBright,
      onColor: Color(0xFF2A0A0A),
      container: Color(0xFF4A1D1D),
      onContainer: Color(0xFFFCA5A5),
    ),
    info: ToneColors(
      color: LunoPalette.blueBright,
      onColor: Color(0xFF06122B),
      container: Color(0xFF12294D),
      onContainer: Color(0xFFBFDBFE),
    ),
    neutral: ToneColors(
      color: Color(0xFF8B959B),
      onColor: LunoPalette.inkBg,
      container: LunoPalette.inkHighest,
      onContainer: LunoPalette.inkTextHi,
    ),
    brand: ToneColors(
      color: LunoPalette.tealDark,
      onColor: Color(0xFF052B25),
      container: LunoPalette.tealDeep,
      onContainer: Color(0xFF99F6E6),
    ),
    textFaint: LunoPalette.inkTextLo,
    glass: Color(0xFF16191C),
    glassBorder: Color(0x1FFFFFFF),
  );

  static const light = LunoSemantic(
    positive: ToneColors(
      color: LunoPalette.green,
      onColor: Color(0xFFFFFFFF),
      container: Color(0xFFD6F0E0),
      onContainer: Color(0xFF124B2C),
    ),
    caution: ToneColors(
      color: LunoPalette.amber,
      onColor: Color(0xFFFFFFFF),
      container: Color(0xFFFBECC9),
      onContainer: Color(0xFF6B4A02),
    ),
    danger: ToneColors(
      color: LunoPalette.red,
      onColor: Color(0xFFFFFFFF),
      container: Color(0xFFFBDCDC),
      onContainer: Color(0xFF7A1414),
    ),
    info: ToneColors(
      color: LunoPalette.blue,
      onColor: Color(0xFFFFFFFF),
      container: Color(0xFFDCE7FD),
      onContainer: Color(0xFF16337A),
    ),
    neutral: ToneColors(
      color: LunoPalette.paperTextLo,
      onColor: Color(0xFFFFFFFF),
      container: LunoPalette.paperHigh,
      onContainer: LunoPalette.paperTextHi,
    ),
    brand: ToneColors(
      color: LunoPalette.tealLight,
      onColor: Color(0xFFFFFFFF),
      container: LunoPalette.tealSoft,
      onContainer: Color(0xFF04403A),
    ),
    textFaint: LunoPalette.paperTextLo,
    glass: Color(0xFFFFFFFF),
    glassBorder: Color(0x14000000),
  );

  @override
  LunoSemantic copyWith({
    ToneColors? positive,
    ToneColors? caution,
    ToneColors? danger,
    ToneColors? info,
    ToneColors? neutral,
    ToneColors? brand,
    Color? textFaint,
    Color? glass,
    Color? glassBorder,
  }) {
    return LunoSemantic(
      positive: positive ?? this.positive,
      caution: caution ?? this.caution,
      danger: danger ?? this.danger,
      info: info ?? this.info,
      neutral: neutral ?? this.neutral,
      brand: brand ?? this.brand,
      textFaint: textFaint ?? this.textFaint,
      glass: glass ?? this.glass,
      glassBorder: glassBorder ?? this.glassBorder,
    );
  }

  @override
  LunoSemantic lerp(LunoSemantic? other, double t) {
    if (other == null) return this;
    return LunoSemantic(
      positive: ToneColors.lerp(positive, other.positive, t),
      caution: ToneColors.lerp(caution, other.caution, t),
      danger: ToneColors.lerp(danger, other.danger, t),
      info: ToneColors.lerp(info, other.info, t),
      neutral: ToneColors.lerp(neutral, other.neutral, t),
      brand: ToneColors.lerp(brand, other.brand, t),
      textFaint: Color.lerp(textFaint, other.textFaint, t)!,
      glass: Color.lerp(glass, other.glass, t)!,
      glassBorder: Color.lerp(glassBorder, other.glassBorder, t)!,
    );
  }
}

/// Sugar so widgets can write `context.semantic` / `context.scheme`.
extension LunoThemeContext on BuildContext {
  LunoSemantic get semantic => Theme.of(this).extension<LunoSemantic>()!;
  ColorScheme get scheme => Theme.of(this).colorScheme;
  TextTheme get text => Theme.of(this).textTheme;
}
