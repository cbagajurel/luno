import 'package:flutter/material.dart';

/// A consistent screen shell: a flat, transparent-tinted app bar over a body,
/// with optional actions, a tab bar, and a floating action button. Keeps every
/// screen's chrome identical so only the content differs.
class LunoScaffold extends StatelessWidget {
  const LunoScaffold({
    super.key,
    required this.title,
    required this.body,
    this.actions,
    this.bottom,
    this.floatingActionButton,
    this.titleWidget,
    this.extendBodyBehindNav = true,
  });

  final String title;
  final Widget body;
  final List<Widget>? actions;
  final PreferredSizeWidget? bottom;
  final Widget? floatingActionButton;
  final Widget? titleWidget;

  /// Lets content scroll under a floating bottom nav; screens add bottom padding
  /// via [MediaQuery] so the last item clears the bar.
  final bool extendBodyBehindNav;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      extendBody: extendBodyBehindNav,
      appBar: AppBar(
        title: titleWidget ?? Text(title),
        actions: actions,
        bottom: bottom,
      ),
      floatingActionButton: floatingActionButton,
      body: SafeArea(top: false, bottom: false, child: body),
    );
  }
}
