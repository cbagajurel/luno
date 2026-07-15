import 'dart:async';

import 'package:flutter/material.dart';

import '../../bridge/luno_bridge.dart';

/// Temporary M2/M3 demo surface: exercises the native bridge end to end — a
/// `ping` round-trip, the live native tick stream, and the M3 foreground-service
/// controls (start/stop + running state). This gets replaced by the real
/// dashboard in M17; for now it exists to visibly prove the agent works.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, LunoBridge? bridge}) : _bridge = bridge;

  final LunoBridge? _bridge;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late final LunoBridge _bridge = widget._bridge ?? LunoBridge();
  StreamSubscription<int>? _tickSub;
  StreamSubscription<AgentRunState>? _agentSub;

  String _pingResult = '(not called yet)';
  int? _lastTick;
  AgentRunState _agentState = AgentRunState.unknown;

  @override
  void initState() {
    super.initState();
    _sendPing();
    _tickSub = _bridge.tickEvents.listen((tick) {
      if (mounted) setState(() => _lastTick = tick);
    });
    _agentSub = _bridge.agentStateEvents.listen((state) {
      if (mounted) setState(() => _agentState = state);
    });
  }

  @override
  void dispose() {
    _tickSub?.cancel();
    _agentSub?.cancel();
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

  Future<void> _startAgent() async {
    // Ask for the notification permission first so the persistent notification
    // is visible; the agent starts either way.
    await _bridge.requestNotificationPermission();
    await _bridge.startAgent();
  }

  Future<void> _stopAgent() => _bridge.stopAgent();

  @override
  Widget build(BuildContext context) {
    final isRunning = _agentState == AgentRunState.running;
    return Scaffold(
      appBar: AppBar(title: const Text('Luno')),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _AgentStatusChip(state: _agentState),
            const SizedBox(height: 12),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                FilledButton.icon(
                  onPressed: isRunning ? null : _startAgent,
                  icon: const Icon(Icons.play_arrow),
                  label: const Text('Start agent'),
                ),
                const SizedBox(width: 12),
                OutlinedButton.icon(
                  onPressed: isRunning ? _stopAgent : null,
                  icon: const Icon(Icons.stop),
                  label: const Text('Stop agent'),
                ),
              ],
            ),
            const Divider(height: 48),
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

class _AgentStatusChip extends StatelessWidget {
  const _AgentStatusChip({required this.state});

  final AgentRunState state;

  @override
  Widget build(BuildContext context) {
    final (label, color, icon) = switch (state) {
      AgentRunState.running => ('Agent running', Colors.green, Icons.check_circle),
      AgentRunState.stopped => ('Agent stopped', Colors.grey, Icons.stop_circle),
      AgentRunState.unknown => ('Agent state unknown', Colors.orange, Icons.help),
    };
    return Chip(
      avatar: Icon(icon, color: color, size: 20),
      label: Text(label),
    );
  }
}
