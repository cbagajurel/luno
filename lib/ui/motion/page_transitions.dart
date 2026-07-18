import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../tokens/motion.dart';

/// A fade-through page for go_router: the outgoing page fades out as the new one
/// fades and lifts in slightly. Honours the reduce-motion setting by snapping.
CustomTransitionPage<T> fadeThroughPage<T>({
  required LocalKey key,
  required Widget child,
}) {
  return CustomTransitionPage<T>(
    key: key,
    transitionDuration: LunoMotion.base,
    reverseTransitionDuration: LunoMotion.fast,
    child: child,
    transitionsBuilder: (context, animation, secondaryAnimation, child) {
      if (LunoMotion.reduced(context)) return child;
      final curved = CurvedAnimation(parent: animation, curve: LunoMotion.emphasized);
      return FadeTransition(
        opacity: curved,
        child: SlideTransition(
          position: Tween<Offset>(begin: const Offset(0, 0.02), end: Offset.zero).animate(curved),
          child: child,
        ),
      );
    },
  );
}
