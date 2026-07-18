import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sms_gateway/bridge/generated/luno_api.g.dart';
import 'package:sms_gateway/bridge/luno_bridge.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  // Pigeon channel for LunoHostApi.ping (name derived from the schema's
  // dartPackageName). Mirrors the native LunoHostApiImpl.ECHO_PREFIX transform
  // so the assertion checks the real Dart<->generated-codec path.
  const pingChannel = BasicMessageChannel<Object?>(
    'dev.flutter.pigeon.sms_gateway.LunoHostApi.ping',
    LunoHostApi.pigeonChannelCodec,
  );
  const echoPrefix = 'Luno-Kotlin echo: ';

  tearDown(() {
    messenger.setMockDecodedMessageHandler<Object?>(pingChannel, null);
  });

  test('ping returns the native-transformed echo', () async {
    messenger.setMockDecodedMessageHandler<Object?>(pingChannel, (
      message,
    ) async {
      final args = message! as List<Object?>;
      final arg = args[0]! as String;
      return <Object?>['$echoPrefix$arg']; // success reply is a 1-element list
    });

    final bridge = LunoBridge();
    expect(await bridge.ping('hi'), '${echoPrefix}hi');
  });

  test('ping propagates a native PlatformException', () async {
    messenger.setMockDecodedMessageHandler<Object?>(pingChannel, (
      message,
    ) async {
      return <Object?>['E_FAIL', 'boom', null]; // pigeon error reply shape
    });

    final bridge = LunoBridge();
    expect(bridge.ping('hi'), throwsA(isA<PlatformException>()));
  });
}
