import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sms_gateway/state/bridge_providers.dart';

import 'fake_luno_bridge.dart';

/// Wraps a feature screen with the fake bridge injected via [bridgeProvider].
/// A plain MaterialApp (default theme) avoids google_fonts runtime fetching.
Widget wrapScreen(FakeLunoBridge bridge, Widget child) => ProviderScope(
      overrides: [bridgeProvider.overrideWithValue(bridge)],
      child: MaterialApp(home: child),
    );
