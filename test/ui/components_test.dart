import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/ui/ui.dart';

Widget _host(Widget child) => MaterialApp(
      theme: lunoTheme(Brightness.light),
      home: Scaffold(body: Center(child: child)),
    );

void main() {
  group('LunoButton', () {
    testWidgets('busy shows a spinner, hides the icon, and blocks presses',
        (tester) async {
      var pressed = 0;
      await tester.pumpWidget(_host(
        LunoButton(
          label: 'Send',
          busy: true,
          busyLabel: 'Sending…',
          icon: Icons.send,
          onPressed: () => pressed++,
        ),
      ));

      expect(find.text('Sending…'), findsOneWidget);
      expect(find.byIcon(Icons.send), findsNothing);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);

      await tester.tap(find.byType(LunoButton));
      await tester.pump();
      expect(pressed, 0, reason: 'busy button must not fire onPressed');
    });

    testWidgets('idle button fires onPressed and shows its icon', (tester) async {
      var pressed = 0;
      await tester.pumpWidget(_host(
        LunoButton(label: 'Start', icon: Icons.play_arrow, onPressed: () => pressed++),
      ));

      expect(find.byIcon(Icons.play_arrow), findsOneWidget);
      await tester.tap(find.byType(LunoButton));
      await tester.pump();
      expect(pressed, 1);
    });
  });

  group('AsyncView', () {
    testWidgets('renders loading, then data', (tester) async {
      await tester.pumpWidget(_host(
        const AsyncView<int>(
          value: AsyncValue.loading(),
          data: _intText,
        ),
      ));
      expect(find.byType(CircularProgressIndicator), findsOneWidget);

      await tester.pumpWidget(_host(
        const AsyncView<int>(value: AsyncData(42), data: _intText),
      ));
      expect(find.text('value: 42'), findsOneWidget);
    });

    testWidgets('renders an error with a working retry', (tester) async {
      var retried = 0;
      await tester.pumpWidget(_host(
        AsyncView<int>(
          value: AsyncError(StateError('boom'), StackTrace.empty),
          data: _intText,
          errorMessage: 'Could not load',
          onRetry: () => retried++,
        ),
      ));
      expect(find.text('Could not load'), findsOneWidget);
      await tester.tap(find.text('Retry'));
      await tester.pump();
      expect(retried, 1);
    });
  });

  testWidgets('GlassContainer renders its child without blur errors',
      (tester) async {
    await tester.pumpWidget(_host(
      const GlassContainer(child: Text('glass')),
    ));
    expect(find.text('glass'), findsOneWidget);
    expect(find.byType(BackdropFilter), findsOneWidget);
  });
}

Widget _intText(int value) => Text('value: $value');
