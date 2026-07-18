import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../tokens/motion.dart';
import 'states.dart';

/// Renders an [AsyncValue] through the shared loading / error / data states, so
/// every async section handles all three the same way. Callers supply only the
/// data builder; loading and error fall back to [LoadingState] / [ErrorState]
/// but can be overridden (e.g. to show skeletons or an inline card).
class AsyncView<T> extends StatelessWidget {
  const AsyncView({
    super.key,
    required this.value,
    required this.data,
    this.loading,
    this.error,
    this.loadingMessage,
    this.errorMessage,
    this.onRetry,
    this.skipLoadingOnReload = true,
  });

  final AsyncValue<T> value;
  final Widget Function(T data) data;
  final WidgetBuilder? loading;
  final Widget Function(Object error)? error;
  final String? loadingMessage;
  final String? errorMessage;
  final VoidCallback? onRetry;
  final bool skipLoadingOnReload;

  @override
  Widget build(BuildContext context) {
    final child = value.when(
      skipLoadingOnReload: skipLoadingOnReload,
      data: data,
      loading: () => loading?.call(context) ?? LoadingState(message: loadingMessage),
      error: (e, _) =>
          error?.call(e) ?? ErrorState(message: errorMessage ?? '$e', onRetry: onRetry),
    );

    if (LunoMotion.reduced(context)) return child;

    // Fade only across the loading/error/data phases; a stable key per phase
    // means live data updates replace in place without re-animating.
    final phase = value.isLoading ? 'loading' : (value.hasError ? 'error' : 'data');
    return AnimatedSwitcher(
      duration: LunoMotion.base,
      switchInCurve: LunoMotion.standard,
      transitionBuilder: (child, animation) =>
          FadeTransition(opacity: animation, child: child),
      child: KeyedSubtree(key: ValueKey(phase), child: child),
    );
  }
}
