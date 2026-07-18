import 'package:flutter/material.dart';

import '../tokens/colors.dart';
import '../tokens/elevation.dart';
import '../tokens/motion.dart';
import '../tokens/spacing.dart';

class GlassNavItem {
  const GlassNavItem({
    required this.icon,
    required this.selectedIcon,
    required this.label,
  });

  final IconData icon;
  final IconData selectedIcon;
  final String label;
}

/// A floating navigation bar. The selected destination expands into a tinted
/// pill with its label; the others stay as icons. The bar is a solid tinted
/// surface (not a live blur) so it floats over scrolling content without
/// re-sampling the backdrop every frame.
class GlassNavigationBar extends StatelessWidget {
  const GlassNavigationBar({
    super.key,
    required this.items,
    required this.selectedIndex,
    required this.onSelect,
  });

  final List<GlassNavItem> items;
  final int selectedIndex;
  final ValueChanged<int> onSelect;

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.paddingOf(context).bottom;
    final semantic = context.semantic;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        LunoSpacing.xl,
        0,
        LunoSpacing.xl,
        LunoSpacing.xs + bottomInset,
      ),
      child: Align(
        alignment: Alignment.bottomCenter,
        heightFactor: 1,
        child: RepaintBoundary(
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: semantic.glass,
              borderRadius: LunoRadius.stadium,
              border: Border.all(color: semantic.glassBorder),
              boxShadow: LunoElevation.floating(Theme.of(context).brightness),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 6),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  for (var i = 0; i < items.length; i++)
                    _NavDestination(
                      item: items[i],
                      selected: i == selectedIndex,
                      onTap: () => onSelect(i),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _NavDestination extends StatelessWidget {
  const _NavDestination({
    required this.item,
    required this.selected,
    required this.onTap,
  });

  final GlassNavItem item;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final brand = context.semantic.brand;
    final reduced = LunoMotion.reduced(context);
    final iconColor = selected
        ? brand.onContainer
        : context.scheme.onSurfaceVariant;

    return Semantics(
      button: true,
      selected: selected,
      label: item.label,
      child: Material(
        color: Colors.transparent,
        shape: const StadiumBorder(),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: AnimatedContainer(
            duration: reduced ? Duration.zero : LunoMotion.base,
            curve: LunoMotion.emphasized,
            padding: EdgeInsets.symmetric(
              horizontal: selected ? LunoSpacing.md : LunoSpacing.sm,
              vertical: 10,
            ),
            decoration: BoxDecoration(
              color: selected ? brand.container : Colors.transparent,
              borderRadius: LunoRadius.stadium,
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  selected ? item.selectedIcon : item.icon,
                  size: 22,
                  color: iconColor,
                ),
                AnimatedSize(
                  duration: reduced ? Duration.zero : LunoMotion.base,
                  curve: LunoMotion.emphasized,
                  child: selected
                      ? Padding(
                          padding: const EdgeInsets.only(left: LunoSpacing.xs),
                          child: Text(
                            item.label,
                            style: context.text.labelLarge?.copyWith(
                              color: brand.onContainer,
                            ),
                          ),
                        )
                      : const SizedBox.shrink(),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
