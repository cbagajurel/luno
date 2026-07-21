import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sms_gateway/state/bridge_providers.dart';
import 'package:sms_gateway/state/device_providers.dart';
import 'package:sms_gateway/ui/theme/luno_theme.dart';

import 'fake_luno_bridge.dart';

/// Wraps a feature screen with the fake bridge injected via [bridgeProvider],
/// under the real Luno theme so the design-system components resolve their
/// tokens (fonts are bundled, so nothing is fetched at runtime).
Widget wrapScreen(
  FakeLunoBridge bridge,
  Widget child, {
  bool receiveSmsSupported = true,
}) => ProviderScope(
  overrides: [
    bridgeProvider.overrideWithValue(bridge),
    receiveSmsSupportedProvider.overrideWithValue(receiveSmsSupported),
  ],
  child: MaterialApp(theme: lunoTheme(Brightness.light), home: child),
);
