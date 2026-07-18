import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../state/device_providers.dart';
import '../state/theme_providers.dart';
import '../ui/theme/luno_theme.dart';
import 'router.dart';

class LunoApp extends ConsumerStatefulWidget {
  const LunoApp({super.key});

  @override
  ConsumerState<LunoApp> createState() => _LunoAppState();
}

class _LunoAppState extends ConsumerState<LunoApp> {
  late final AppLifecycleListener _lifecycle;

  @override
  void initState() {
    super.initState();
    // Permissions can change while we're backgrounded (the user grants them in
    // system Settings). Re-read on resume so the UI reflects them in real time.
    _lifecycle = AppLifecycleListener(
      onResume: () => ref.read(permissionsProvider.notifier).refresh(),
    );
  }

  @override
  void dispose() {
    _lifecycle.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'Luno',
      debugShowCheckedModeBanner: false,
      theme: lunoTheme(Brightness.light),
      darkTheme: lunoTheme(Brightness.dark),
      themeMode: ref.watch(themeModeProvider),
      routerConfig: ref.watch(routerProvider),
    );
  }
}
