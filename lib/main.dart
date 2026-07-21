import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app/luno_app.dart';
import 'bridge/luno_bridge.dart';
import 'state/bridge_providers.dart';
import 'state/device_providers.dart';
import 'state/theme_providers.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  final bridge = LunoBridge();
  final receiveSmsSupported = await bridge.isReceiveSmsSupported();
  runApp(
    ProviderScope(
      overrides: [
        sharedPreferencesProvider.overrideWithValue(prefs),
        bridgeProvider.overrideWithValue(bridge),
        receiveSmsSupportedProvider.overrideWithValue(receiveSmsSupported),
      ],
      child: const LunoApp(),
    ),
  );
}
