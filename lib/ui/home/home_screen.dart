import 'dart:async';

import 'package:flutter/material.dart';

import '../../bridge/luno_bridge.dart';

/// Temporary M2 demo surface: exercises the native bridge end to end — a
/// `ping` round-trip and the live native tick stream. This gets replaced by the
/// real dashboard in M17; for now it exists to visibly prove the bridge works.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, LunoBridge? bridge}) : _bridge = bridge;

  final LunoBridge? _bridge;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late final LunoBridge _bridge = widget._bridge ?? LunoBridge();
  StreamSubscription<int>? _tickSub;

  String _pingResult = '(not called yet)';
  int? _lastTick;

  @override
  void initState() {
    super.initState();
    _sendPing();
    _tickSub = _bridge.tickEvents.listen((tick) {
      if (mounted) setState(() => _lastTick = tick);
    });
  }

  @override
  void dispose() {
    _tickSub?.cancel();
    super.dispose();
  }

  Future<void> _sendPing() async {
    try {
      final reply = await _bridge.ping('hi from Flutter');
      if (mounted) setState(() => _pingResult = reply);
    } catch (e) {
      if (mounted) setState(() => _pingResult = 'ping failed: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Luno')),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('HostApi.ping →'),
            Text(_pingResult, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 24),
            const Text('Native tick (EventChannel) →'),
            Text(
              _lastTick == null ? 'waiting…' : '$_lastTick',
              style: Theme.of(context).textTheme.headlineMedium,
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _sendPing,
        tooltip: 'Ping native',
        child: const Icon(Icons.wifi_tethering),
      ),
    );
  }
}
