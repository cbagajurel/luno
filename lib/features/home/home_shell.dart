import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../ui/ui.dart';

/// Bottom-nav shell over the four paired-mode branches; [StatefulNavigationShell]
/// preserves each tab's state across switches. [Scaffold.extendBody] lets content
/// scroll behind the floating glass bar (which is what it frosts) and reports the
/// bar's height to the body as bottom padding — screens add that back to their
/// scrollables via `context.navClearance`.
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
      extendBody: true,
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
