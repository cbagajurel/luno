import 'package:flutter/material.dart';

import '../tokens/colors.dart';
import '../tokens/spacing.dart';
import 'glass_container.dart';

/// Opens a frosted-glass modal sheet with a drag handle, a title/subtitle
/// header, and keyboard-safe padding. Shared scaffold for the reconnect and
/// compose sheets, which previously re-implemented the same structure.
Future<T?> showLunoSheet<T>({
  required BuildContext context,
  required String title,
  String? subtitle,
  required WidgetBuilder builder,
}) {
  return showModalBottomSheet<T>(
    context: context,
    // Screens live inside a StatefulShellRoute branch navigator, which sits in the
    // shell Scaffold's *body* — a sheet pushed there paints under the floating nav
    // bar, barrier and all. The root navigator is above the whole shell.
    useRootNavigator: true,
    isScrollControlled: true,
    useSafeArea: true,
    backgroundColor: Colors.transparent,
    barrierColor: Colors.black.withValues(alpha: 0.45),
    builder: (context) =>
        _LunoSheet(title: title, subtitle: subtitle, builder: builder),
  );
}

class _LunoSheet extends StatelessWidget {
  const _LunoSheet({required this.title, this.subtitle, required this.builder});

  final String title;
  final String? subtitle;
  final WidgetBuilder builder;

  @override
  Widget build(BuildContext context) {
    final viewInsets = MediaQuery.viewInsetsOf(context).bottom;
    return GlassContainer(
      sigma: 18,
      borderRadius: LunoRadius.sheet,
      padding: EdgeInsets.fromLTRB(
        LunoSpacing.lg,
        LunoSpacing.sm,
        LunoSpacing.lg,
        LunoSpacing.lg + viewInsets,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Center(
            child: Container(
              width: 40,
              height: 4,
              margin: const EdgeInsets.only(bottom: LunoSpacing.md),
              decoration: BoxDecoration(
                color: context.scheme.onSurfaceVariant.withValues(alpha: 0.4),
                borderRadius: LunoRadius.stadium,
              ),
            ),
          ),
          Text(title, style: context.text.titleLarge),
          if (subtitle != null) ...[
            const SizedBox(height: LunoSpacing.xxs),
            Text(
              subtitle!,
              style: context.text.bodyMedium?.copyWith(
                color: context.scheme.onSurfaceVariant,
              ),
            ),
          ],
          const SizedBox(height: LunoSpacing.lg),
          builder(context),
        ],
      ),
    );
  }
}
