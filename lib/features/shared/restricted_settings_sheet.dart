import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/device_providers.dart';
import '../../ui/components/luno_bottom_sheet.dart';
import '../../ui/components/luno_button.dart';
import '../../ui/tokens/colors.dart';
import '../../ui/tokens/spacing.dart';

/// Recovery route for a permission the system refuses to prompt for.
///
/// Android 15+ treats SMS permissions as hard-restricted for apps that were not
/// installed from an app store: the toggle is greyed out and the runtime request
/// is auto-denied without ever showing a dialog. Nothing in the app can lift
/// that — only the user, from this app's settings page. The same screen also
/// resolves an ordinary "Don't ask again", so one flow covers both.
Future<void> showRestrictedSettingsSheet(
  BuildContext context,
  WidgetRef ref,
  AppPermission permission,
) {
  return showLunoSheet<void>(
    context: context,
    title: '${permission.label} is blocked',
    subtitle:
        'Android is not showing the permission prompt, so granting it '
        'from here has no effect.',
    builder: (context) => _RestrictedSettingsBody(ref: ref),
  );
}

class _RestrictedSettingsBody extends StatelessWidget {
  const _RestrictedSettingsBody({required this.ref});

  final WidgetRef ref;

  static const _steps = [
    'Open this app\'s settings with the button below.',
    'Tap the ⋮ menu (top-right) and choose "Allow restricted settings".',
    'Go to Permissions → SMS and switch it to Allow.',
  ];

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var i = 0; i < _steps.length; i++) ...[
          _Step(number: i + 1, text: _steps[i]),
          const SizedBox(height: LunoSpacing.sm),
        ],
        const SizedBox(height: LunoSpacing.xs),
        Text(
          'If the ⋮ menu has no "Allow restricted settings" item, the permission '
          'was only denied — reopen it from Permissions and choose Allow.',
          style: context.text.bodySmall?.copyWith(
            color: context.scheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: LunoSpacing.lg),
        LunoButton(
          label: 'Open app settings',
          onPressed: () {
            Navigator.of(context).pop();
            ref.read(permissionsProvider.notifier).openAppSettings();
          },
        ),
      ],
    );
  }
}

class _Step extends StatelessWidget {
  const _Step({required this.number, required this.text});

  final int number;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 22,
          height: 22,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: context.semantic.brand.color.withValues(alpha: 0.16),
            borderRadius: LunoRadius.stadium,
          ),
          child: Text(
            '$number',
            style: context.text.labelSmall?.copyWith(
              color: context.semantic.brand.color,
            ),
          ),
        ),
        const SizedBox(width: LunoSpacing.sm),
        Expanded(child: Text(text, style: context.text.bodyMedium)),
      ],
    );
  }
}
