import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/dashboard/dashboard_screen.dart';
import '../features/home/home_shell.dart';
import '../features/logs/logs_screen.dart';
import '../features/messages/messages_screen.dart';
import '../features/pairing/pairing_screen.dart';
import '../features/settings/settings_screen.dart';
import '../features/splash/splash_screen.dart';
import '../state/pairing_providers.dart';

final routerProvider = Provider<GoRouter>((ref) {
  // Bridges the async pairing state into a Listenable the router can refresh on.
  // null = still resolving, true/false = known.
  final paired = ValueNotifier<bool?>(null);
  ref.onDispose(paired.dispose);
  ref.listen<AsyncValue<bool>>(
    pairingProvider,
    (_, next) => paired.value = next.value,
    fireImmediately: true,
  );

  return GoRouter(
    initialLocation: '/dashboard',
    refreshListenable: paired,
    redirect: (context, state) {
      final isPaired = paired.value;
      final loc = state.matchedLocation;
      if (isPaired == null) return loc == '/loading' ? null : '/loading';
      if (!isPaired) return loc == '/pairing' ? null : '/pairing';
      if (loc == '/pairing' || loc == '/loading') return '/dashboard';
      return null;
    },
    routes: [
      GoRoute(path: '/loading', builder: (_, _) => const SplashScreen()),
      GoRoute(path: '/pairing', builder: (_, _) => const PairingScreen()),
      StatefulShellRoute.indexedStack(
        builder: (context, state, shell) => HomeShell(shell: shell),
        branches: [
          StatefulShellBranch(
            routes: [GoRoute(path: '/dashboard', builder: (_, _) => const DashboardScreen())],
          ),
          StatefulShellBranch(
            routes: [GoRoute(path: '/messages', builder: (_, _) => const MessagesScreen())],
          ),
          StatefulShellBranch(
            routes: [GoRoute(path: '/logs', builder: (_, _) => const LogsScreen())],
          ),
          StatefulShellBranch(
            routes: [GoRoute(path: '/settings', builder: (_, _) => const SettingsScreen())],
          ),
        ],
      ),
    ],
  );
});
