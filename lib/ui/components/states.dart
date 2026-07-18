import 'package:flutter/material.dart';

import '../tokens/colors.dart';
import '../tokens/spacing.dart';
import 'luno_button.dart';

/// A centered loading state with an optional caption. One idiom for every
/// async section, replacing the three the app grew independently.
class LoadingState extends StatelessWidget {
  const LoadingState({super.key, this.message});

  final String? message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(
            width: 28,
            height: 28,
            child: CircularProgressIndicator(strokeWidth: 2.5),
          ),
          if (message != null) ...[
            const SizedBox(height: LunoSpacing.md),
            Text(
              message!,
              textAlign: TextAlign.center,
              style: context.text.bodyMedium?.copyWith(color: context.scheme.onSurfaceVariant),
            ),
          ],
        ],
      ),
    );
  }
}

/// A calm empty state: a muted icon, a title, and optional body + action.
class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    this.message,
    this.action,
  });

  final IconData icon;
  final String title;
  final String? message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(LunoSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 44, color: context.semantic.textFaint),
            const SizedBox(height: LunoSpacing.md),
            Text(title, textAlign: TextAlign.center, style: context.text.titleMedium),
            if (message != null) ...[
              const SizedBox(height: LunoSpacing.xs),
              Text(
                message!,
                textAlign: TextAlign.center,
                style: context.text.bodyMedium?.copyWith(color: context.scheme.onSurfaceVariant),
              ),
            ],
            if (action != null) ...[
              const SizedBox(height: LunoSpacing.lg),
              action!,
            ],
          ],
        ),
      ),
    );
  }
}

/// A centered error state with an optional retry action.
class ErrorState extends StatelessWidget {
  const ErrorState({super.key, required this.message, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(LunoSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.error_outline_rounded, size: 44, color: context.semantic.danger.color),
            const SizedBox(height: LunoSpacing.md),
            Text(
              message,
              textAlign: TextAlign.center,
              style: context.text.bodyMedium?.copyWith(color: context.scheme.onSurfaceVariant),
            ),
            if (onRetry != null) ...[
              const SizedBox(height: LunoSpacing.lg),
              LunoButton(
                label: 'Retry',
                variant: LunoButtonVariant.tonal,
                icon: Icons.refresh_rounded,
                onPressed: onRetry,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
