import 'package:flutter/material.dart';

import '../tokens/colors.dart';
import '../tokens/spacing.dart';
import 'glass_container.dart';
import 'luno_button.dart';

/// A frosted confirmation dialog. Returns true when the confirm action is
/// chosen. [destructive] tints the confirm button with the danger tone.
Future<bool> showLunoConfirm({
  required BuildContext context,
  required String title,
  required String message,
  String confirmLabel = 'Confirm',
  String cancelLabel = 'Cancel',
  bool destructive = false,
}) async {
  final result = await showDialog<bool>(
    context: context,
    barrierColor: Colors.black.withValues(alpha: 0.5),
    builder: (context) => _LunoDialog(
      title: title,
      message: message,
      confirmLabel: confirmLabel,
      cancelLabel: cancelLabel,
      destructive: destructive,
    ),
  );
  return result ?? false;
}

class _LunoDialog extends StatelessWidget {
  const _LunoDialog({
    required this.title,
    required this.message,
    required this.confirmLabel,
    required this.cancelLabel,
    required this.destructive,
  });

  final String title;
  final String message;
  final String confirmLabel;
  final String cancelLabel;
  final bool destructive;

  @override
  Widget build(BuildContext context) {
    final confirmColor = destructive ? context.semantic.danger.color : context.scheme.primary;
    return Dialog(
      backgroundColor: Colors.transparent,
      insetPadding: const EdgeInsets.symmetric(horizontal: LunoSpacing.xl),
      child: GlassContainer(
        sigma: 18,
        borderRadius: const BorderRadius.all(Radius.circular(LunoRadius.xl)),
        padding: const EdgeInsets.all(LunoSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(title, style: context.text.titleLarge),
            const SizedBox(height: LunoSpacing.xs),
            Text(
              message,
              style: context.text.bodyMedium?.copyWith(color: context.scheme.onSurfaceVariant),
            ),
            const SizedBox(height: LunoSpacing.xl),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                LunoButton(
                  label: cancelLabel,
                  variant: LunoButtonVariant.text,
                  onPressed: () => Navigator.of(context).pop(false),
                ),
                const SizedBox(width: LunoSpacing.xs),
                FilledButton(
                  onPressed: () => Navigator.of(context).pop(true),
                  style: FilledButton.styleFrom(backgroundColor: confirmColor),
                  child: Text(confirmLabel),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
