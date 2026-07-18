import 'package:flutter/material.dart';

import '../tokens/colors.dart';

/// A compact four-bar signal indicator. [level] is 0–4; anything below 0 renders
/// as unknown (all bars faint).
class SignalBars extends StatelessWidget {
  const SignalBars({super.key, required this.level, this.height = 16});

  final int level;
  final double height;

  @override
  Widget build(BuildContext context) {
    final tone = switch (level) {
      >= 3 => context.semantic.positive.color,
      >= 1 => context.semantic.caution.color,
      _ => context.semantic.textFaint,
    };
    return SizedBox(
      height: height,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: List.generate(4, (i) {
          final on = level > i;
          return Container(
            width: 3.5,
            height: height * (0.4 + i * 0.2),
            margin: const EdgeInsets.only(left: 2),
            decoration: BoxDecoration(
              color: on ? tone : context.scheme.outlineVariant,
              borderRadius: const BorderRadius.all(Radius.circular(1.5)),
            ),
          );
        }),
      ),
    );
  }
}
