import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../ui/ui.dart';

/// Bottom-nav shell over the four paired-mode branches; [StatefulNavigationShell]
/// preserves each tab's state across switches. The nav bar occupies its own strip
/// at the bottom (standard [Scaffold.bottomNavigationBar]); content sits above it,
/// so screens don't reserve any nav clearance themselves.
class HomeShell extends StatelessWidget {
  const HomeShell({super.key, required this.shell});

  final StatefulNavigationShell shell;

  static const _items = [
    GlassNavItem(
      icon: Icons.dashboard_outlined,
      selectedIcon: Icons.dashboard_rounded,
      label: 'Dashboard',
    ),
    GlassNavItem(
      icon: Icons.sms_outlined,
      selectedIcon: Icons.sms_rounded,
      label: 'Messages',
    ),
    GlassNavItem(
      icon: Icons.article_outlined,
      selectedIcon: Icons.article_rounded,
      label: 'Logs',
    ),
    GlassNavItem(
      icon: Icons.settings_outlined,
      selectedIcon: Icons.settings_rounded,
      label: 'Settings',
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: shell,
      bottomNavigationBar: GlassNavigationBar(
        items: _items,
        selectedIndex: shell.currentIndex,
        onSelect: (index) =>
            shell.goBranch(index, initialLocation: index == shell.currentIndex),
      ),
    );
  }
}
