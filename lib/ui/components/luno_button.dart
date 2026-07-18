import 'package:flutter/material.dart';

import '../tokens/spacing.dart';

enum LunoButtonVariant { primary, secondary, tonal, text }

/// The one button. Carries a built-in [busy] state that swaps the icon for a
/// spinner and disables the press — folding the duplicated
/// `FilledButton.icon + CircularProgressIndicator` pattern from the pairing and
/// compose forms into a single component.
class LunoButton extends StatelessWidget {
  const LunoButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.variant = LunoButtonVariant.primary,
    this.icon,
    this.busy = false,
    this.busyLabel,
    this.expand = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final LunoButtonVariant variant;
  final IconData? icon;
  final bool busy;
  final String? busyLabel;
  final bool expand;

  @override
  Widget build(BuildContext context) {
    final effectiveOnPressed = busy ? null : onPressed;
    final text = busy ? (busyLabel ?? label) : label;

    final Widget? leading = busy
        ? SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: _spinnerColor(context),
            ),
          )
        : (icon == null ? null : Icon(icon, size: 20));

    final child = Row(
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (leading != null) ...[
          leading,
          const SizedBox(width: LunoSpacing.xs),
        ],
        Flexible(child: Text(text, overflow: TextOverflow.ellipsis)),
      ],
    );

    final button = switch (variant) {
      LunoButtonVariant.primary => FilledButton(
        onPressed: effectiveOnPressed,
        child: child,
      ),
      LunoButtonVariant.secondary => OutlinedButton(
        onPressed: effectiveOnPressed,
        child: child,
      ),
      LunoButtonVariant.tonal => FilledButton.tonal(
        onPressed: effectiveOnPressed,
        child: child,
      ),
      LunoButtonVariant.text => TextButton(
        onPressed: effectiveOnPressed,
        child: child,
      ),
    };

    return expand ? SizedBox(width: double.infinity, child: button) : button;
  }

  Color _spinnerColor(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return switch (variant) {
      LunoButtonVariant.primary => scheme.onPrimary,
      LunoButtonVariant.tonal => scheme.onSecondaryContainer,
      _ => scheme.primary,
    };
  }
}
