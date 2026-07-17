import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

const _seed = Color(0xFF6750A4);

ThemeData lunoTheme(Brightness brightness) {
  final base = ThemeData(brightness: brightness);
  return ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(seedColor: _seed, brightness: brightness),
    textTheme: GoogleFonts.interTextTheme(base.textTheme),
  );
}
