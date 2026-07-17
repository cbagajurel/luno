import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'router.dart';
import 'theme.dart';

class LunoApp extends ConsumerWidget {
  const LunoApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return MaterialApp.router(
      title: 'Luno',
      debugShowCheckedModeBanner: false,
      theme: lunoTheme(Brightness.light),
      darkTheme: lunoTheme(Brightness.dark),
      routerConfig: ref.watch(routerProvider),
    );
  }
}
