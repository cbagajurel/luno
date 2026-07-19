import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../tokens/colors.dart';
import '../tokens/elevation.dart';
import '../tokens/motion.dart';
import '../tokens/spacing.dart';
import 'glass_container.dart';

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

/// Bottom inset a scrollable should add so its last row scrolls clear of the
/// floating nav bar with a little air to spare. Under [Scaffold.extendBody] the
/// bar's height arrives as the body's bottom padding, so this only adds the
/// breathing room on top of it.
extension LunoNavClearance on BuildContext {
  double get navClearance =>
      MediaQuery.paddingOf(this).bottom + LunoSpacing.md;
}

/// A floating navigation bar. The selected destination expands into a tinted
/// pill with its label; the others stay as icons. The bar is a live frosted
/// surface, so hosts must set [Scaffold.extendBody] and let content scroll
/// behind it — otherwise there is nothing to frost.
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
    final float = math.max(bottomInset, LunoSpacing.sm);
    return Padding(
      padding: EdgeInsets.fromLTRB(
        LunoSpacing.xl,
        LunoSpacing.xs,
        LunoSpacing.xl,
        float,
      ),
      child: Align(
        alignment: Alignment.bottomCenter,
        heightFactor: 1,
        child: GlassContainer(
          borderRadius: LunoRadius.stadium,
          sigma: 18,
          padding: const EdgeInsets.all(6),
          shadows: LunoElevation.card(Theme.of(context).brightness),
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
