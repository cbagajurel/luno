import 'package:flutter/material.dart';

class LunoScaffold extends StatelessWidget {
  const LunoScaffold({
    super.key,
    required this.title,
    required this.body,
    this.actions,
    this.bottom,
    this.floatingActionButton,
    this.titleWidget,
    this.extendBodyBehindNav = false,
  });

  final String title;
  final Widget body;
  final List<Widget>? actions;
  final PreferredSizeWidget? bottom;
  final Widget? floatingActionButton;
  final Widget? titleWidget;

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
