import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../bridge/luno_bridge.dart';

/// The single Dart-side door to the native agent. Every provider that touches
/// native state depends on this; tests override it with a fake.
final bridgeProvider = Provider<LunoBridge>((ref) => LunoBridge());
