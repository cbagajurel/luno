import 'package:flutter/material.dart';

import '../tokens/colors.dart';
import '../tokens/spacing.dart';
import '../tokens/typography.dart';

/// Builds the full [ThemeData] for a brightness from the design tokens. Colours
/// are explicit (not seed-generated) so surfaces, contrast, and the teal accent
/// are exactly what the palette specifies.
ThemeData lunoTheme(Brightness brightness) {
  final isDark = brightness == Brightness.dark;
  final scheme = isDark ? _darkScheme : _lightScheme;
  final semantic = isDark ? LunoSemantic.dark : LunoSemantic.light;

  final textTheme = lunoTextTheme().apply(
    bodyColor: scheme.onSurface,
    displayColor: scheme.onSurface,
  );

  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    scaffoldBackgroundColor: scheme.surface,
    textTheme: textTheme,
    fontFamily: LunoFonts.sans,
    splashFactory: InkSparkle.splashFactory,
    extensions: [semantic],
    appBarTheme: AppBarTheme(
      backgroundColor: scheme.surface,
      surfaceTintColor: Colors.transparent,
      scrolledUnderElevation: 0,
      elevation: 0,
      centerTitle: false,
      titleTextStyle: textTheme.titleLarge,
      iconTheme: IconThemeData(color: scheme.onSurfaceVariant),
    ),
    cardTheme: CardThemeData(
      color: isDark ? LunoPalette.inkSurface : LunoPalette.paperLowest,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      shape: const RoundedRectangleBorder(borderRadius: LunoRadius.card),
    ),
    dividerTheme: DividerThemeData(
      color: scheme.outlineVariant,
      thickness: 1,
      space: 1,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(0, 48),
        padding: const EdgeInsets.symmetric(horizontal: LunoSpacing.lg),
        textStyle: textTheme.labelLarge,
        shape: const RoundedRectangleBorder(borderRadius: LunoRadius.field),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(0, 48),
        padding: const EdgeInsets.symmetric(horizontal: LunoSpacing.lg),
        textStyle: textTheme.labelLarge,
        side: BorderSide(color: scheme.outlineVariant),
        foregroundColor: scheme.onSurface,
        shape: const RoundedRectangleBorder(borderRadius: LunoRadius.field),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        textStyle: textTheme.labelLarge,
        foregroundColor: scheme.primary,
      ),
    ),
    chipTheme: ChipThemeData(
      backgroundColor: scheme.surfaceContainerHighest,
      side: BorderSide.none,
      labelStyle: textTheme.labelMedium,
      shape: const RoundedRectangleBorder(borderRadius: LunoRadius.stadium),
      padding: const EdgeInsets.symmetric(horizontal: LunoSpacing.sm, vertical: 2),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: scheme.surfaceContainerHigh,
      contentPadding:
          const EdgeInsets.symmetric(horizontal: LunoSpacing.md, vertical: LunoSpacing.sm),
      border: const OutlineInputBorder(
        borderRadius: LunoRadius.field,
        borderSide: BorderSide.none,
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: LunoRadius.field,
        borderSide: BorderSide(color: scheme.outlineVariant),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: LunoRadius.field,
        borderSide: BorderSide(color: scheme.primary, width: 1.5),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: LunoRadius.field,
        borderSide: BorderSide(color: scheme.error),
      ),
    ),
    listTileTheme: const ListTileThemeData(
      contentPadding: EdgeInsets.symmetric(horizontal: LunoSpacing.md),
      minVerticalPadding: LunoSpacing.sm,
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
    ),
    bottomSheetTheme: const BottomSheetThemeData(
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: scheme.inverseSurface,
      contentTextStyle: textTheme.bodyMedium?.copyWith(color: scheme.onInverseSurface),
      shape: const RoundedRectangleBorder(borderRadius: LunoRadius.card),
    ),
  );
}

const _darkScheme = ColorScheme(
  brightness: Brightness.dark,
  primary: LunoPalette.tealDark,
  onPrimary: Color(0xFF052B25),
  primaryContainer: LunoPalette.tealDeep,
  onPrimaryContainer: Color(0xFF99F6E6),
  secondary: Color(0xFF8B959B),
  onSecondary: LunoPalette.inkBg,
  secondaryContainer: LunoPalette.inkHigh,
  onSecondaryContainer: LunoPalette.inkTextHi,
  tertiary: LunoPalette.blueBright,
  onTertiary: Color(0xFF06122B),
  error: LunoPalette.redBright,
  onError: Color(0xFF2A0A0A),
  errorContainer: Color(0xFF4A1D1D),
  onErrorContainer: Color(0xFFFCA5A5),
  surface: LunoPalette.inkBg,
  onSurface: LunoPalette.inkTextHi,
  onSurfaceVariant: LunoPalette.inkTextMid,
  surfaceContainerLowest: LunoPalette.inkLowest,
  surfaceContainerLow: LunoPalette.inkLow,
  surfaceContainer: LunoPalette.inkSurface,
  surfaceContainerHigh: LunoPalette.inkHigh,
  surfaceContainerHighest: LunoPalette.inkHighest,
  outline: LunoPalette.inkOutline,
  outlineVariant: LunoPalette.inkDivider,
  shadow: Color(0xFF000000),
  scrim: Color(0xFF000000),
  inverseSurface: LunoPalette.inkTextHi,
  onInverseSurface: LunoPalette.inkSurface,
  inversePrimary: LunoPalette.tealLight,
);

const _lightScheme = ColorScheme(
  brightness: Brightness.light,
  primary: LunoPalette.tealLight,
  onPrimary: Color(0xFFFFFFFF),
  primaryContainer: LunoPalette.tealSoft,
  onPrimaryContainer: Color(0xFF04403A),
  secondary: Color(0xFF4A5157),
  onSecondary: Color(0xFFFFFFFF),
  secondaryContainer: Color(0xFFE1E6E8),
  onSecondaryContainer: Color(0xFF1A1F22),
  tertiary: LunoPalette.blue,
  onTertiary: Color(0xFFFFFFFF),
  error: LunoPalette.red,
  onError: Color(0xFFFFFFFF),
  errorContainer: Color(0xFFFBDCDC),
  onErrorContainer: Color(0xFF7A1414),
  surface: LunoPalette.paperBg,
  onSurface: LunoPalette.paperTextHi,
  onSurfaceVariant: LunoPalette.paperTextMid,
  surfaceContainerLowest: LunoPalette.paperLowest,
  surfaceContainerLow: LunoPalette.paperLow,
  surfaceContainer: LunoPalette.paperSurface,
  surfaceContainerHigh: LunoPalette.paperHigh,
  surfaceContainerHighest: LunoPalette.paperHighest,
  outline: LunoPalette.paperOutline,
  outlineVariant: LunoPalette.paperDivider,
  shadow: Color(0xFF000000),
  scrim: Color(0xFF000000),
  inverseSurface: LunoPalette.paperTextHi,
  onInverseSurface: LunoPalette.paperLowest,
  inversePrimary: LunoPalette.tealDark,
);
