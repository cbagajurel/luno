import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Injected in `main` after preloading. Null when not overridden (e.g. widget
/// tests), in which case the theme defaults to system and isn't persisted.
final sharedPreferencesProvider = Provider<SharedPreferences?>((ref) => null);

/// The app's theme mode. Defaults to [ThemeMode.system]; a user override is
/// persisted locally (a pure UI preference — native remains the source of truth
/// for everything that matters).
class ThemeModeController extends Notifier<ThemeMode> {
  static const _key = 'luno_theme_mode';

  @override
  ThemeMode build() =>
      _decode(ref.read(sharedPreferencesProvider)?.getString(_key));

  Future<void> set(ThemeMode mode) async {
    state = mode;
    await ref.read(sharedPreferencesProvider)?.setString(_key, mode.name);
  }

  static ThemeMode _decode(String? value) => switch (value) {
    'light' => ThemeMode.light,
    'dark' => ThemeMode.dark,
    _ => ThemeMode.system,
  };
}

final themeModeProvider = NotifierProvider<ThemeModeController, ThemeMode>(
  ThemeModeController.new,
);
