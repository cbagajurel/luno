import 'dart:async';

import 'package:flutter/material.dart';

import '../../bridge/generated/luno_api.g.dart'
    show
        BatteryStatus,
        DeviceState,
        NetworkStatus,
        OutboxEntry,
        SignalInfo,
        SimInfo;
import '../../bridge/luno_bridge.dart';

/// Temporary demo surface for M2–M5; replaced by the real dashboard in M17.
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
  StreamSubscription<int>? _deviceSub;
  StreamSubscription<int>? _outboxSub;

  String _pingResult = '(not called yet)';
  int? _lastTick;
  AgentRunState _agentState = AgentRunState.unknown;

  bool _hasPhonePermission = false;
  DeviceState? _deviceState;

  final TextEditingController _recipientController = TextEditingController();
  final TextEditingController _bodyController = TextEditingController();
  bool _hasSmsPermission = false;
  int? _selectedSubId;
  List<OutboxEntry> _outbox = const <OutboxEntry>[];

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
    _deviceSub = _bridge.deviceStateEvents.listen((_) => _refreshDeviceState());
    _outboxSub = _bridge.outboxEvents.listen((_) => _refreshOutbox());
    _refreshDeviceState();
    _refreshOutbox();
  }

  @override
  void dispose() {
    _tickSub?.cancel();
    _agentSub?.cancel();
    _deviceSub?.cancel();
    _outboxSub?.cancel();
    _recipientController.dispose();
    _bodyController.dispose();
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
    await _bridge.requestNotificationPermission();
    await _bridge.startAgent();
  }

  Future<void> _stopAgent() => _bridge.stopAgent();

  Future<void> _refreshDeviceState() async {
    final hasPermission = await _bridge.hasPhonePermission();
    final hasSms = await _bridge.hasSmsPermission();
    final state = await _bridge.getDeviceState();
    if (mounted) {
      setState(() {
        _hasPhonePermission = hasPermission;
        _hasSmsPermission = hasSms;
        _deviceState = state;
      });
    }
  }

  Future<void> _refreshOutbox() async {
    final rows = await _bridge.getRecentOutbox();
    if (mounted) setState(() => _outbox = rows);
  }

  Future<void> _sendTestSms() async {
    final recipient = _recipientController.text.trim();
    final body = _bodyController.text;
    if (recipient.isEmpty || body.isEmpty) return;
    if (!_hasSmsPermission) {
      await _bridge.requestSmsPermission();
      await _refreshDeviceState();
      if (!_hasSmsPermission) return;
    }
    await _bridge.sendSms(recipient, body, subscriptionId: _selectedSubId);
    _bodyController.clear();
    await _refreshOutbox();
  }

  @override
  Widget build(BuildContext context) {
    final isRunning = _agentState == AgentRunState.running;
    final battery = _deviceState?.battery;
    final sims = _deviceState?.sims ?? const <SimInfo>[];
    final signals = {
      for (final s in _deviceState?.signals ?? const <SignalInfo>[])
        s.subscriptionId: s,
    };
    return Scaffold(
      appBar: AppBar(title: const Text('Luno')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Center(child: _AgentStatusChip(state: _agentState)),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
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
          const Divider(height: 40),
          Text('Network', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          _NetworkCard(network: _deviceState?.network),
          const Divider(height: 40),
          Text('Battery', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          _BatteryCard(battery: battery),
          const Divider(height: 40),
          _SimSection(
            hasPermission: _hasPhonePermission,
            sims: sims,
            signals: signals,
            onGrant: () async {
              await _bridge.requestPhonePermission();
              await _refreshDeviceState();
            },
          ),
          const Divider(height: 40),
          _SendSmsSection(
            hasPermission: _hasSmsPermission,
            recipientController: _recipientController,
            bodyController: _bodyController,
            sims: sims,
            selectedSubId: _selectedSubId,
            onSubChanged: (v) => setState(() => _selectedSubId = v),
            onSend: _sendTestSms,
            onGrant: () async {
              await _bridge.requestSmsPermission();
              await _refreshDeviceState();
            },
            outbox: _outbox,
          ),
          const Divider(height: 40),
          const Text('HostApi.ping →'),
          Text(_pingResult, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 16),
          const Text('Native tick (EventChannel) →'),
          Text(
            _lastTick == null ? 'waiting…' : '$_lastTick',
            style: Theme.of(context).textTheme.headlineMedium,
          ),
        ],
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

class _NetworkCard extends StatelessWidget {
  const _NetworkCard({required this.network});

  final NetworkStatus? network;

  @override
  Widget build(BuildContext context) {
    final n = network;
    if (n == null || !n.connected) {
      return const Card(
        child: ListTile(
          leading: Icon(Icons.cloud_off),
          title: Text('Offline'),
        ),
      );
    }
    final online = n.validated;
    final title = online
        ? 'Online · ${n.transport}'
        : 'Connected, no internet · ${n.transport}';
    return Card(
      child: ListTile(
        leading: Icon(
          online ? Icons.cloud_done : Icons.cloud_queue,
          color: online ? Colors.green : Colors.orange,
        ),
        title: Text(title),
        subtitle: Text(n.metered ? 'metered' : 'unmetered'),
      ),
    );
  }
}

class _BatteryCard extends StatelessWidget {
  const _BatteryCard({required this.battery});

  final BatteryStatus? battery;

  @override
  Widget build(BuildContext context) {
    final b = battery;
    if (b == null) {
      return const Card(
        child: ListTile(
          leading: Icon(Icons.battery_unknown),
          title: Text('Battery status unavailable'),
        ),
      );
    }
    final level = b.levelPercent >= 0 ? '${b.levelPercent}%' : 'unknown';
    final source = b.plugged == 'NONE' ? 'on battery' : 'charging via ${b.plugged}';
    return Card(
      child: ListTile(
        leading: Icon(
          b.isCharging ? Icons.battery_charging_full : Icons.battery_full,
          color: b.isCharging ? Colors.green : null,
        ),
        title: Text('$level · ${b.isCharging ? 'charging' : 'discharging'}'),
        subtitle: Text('$source · health ${b.health}'),
      ),
    );
  }
}

class _SimSection extends StatelessWidget {
  const _SimSection({
    required this.hasPermission,
    required this.sims,
    required this.signals,
    required this.onGrant,
  });

  final bool hasPermission;
  final List<SimInfo> sims;
  final Map<int, SignalInfo> signals;
  final Future<void> Function() onGrant;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('SIMs', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        if (!hasPermission)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  const Text(
                    'Phone permission is needed to read SIM information.',
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: onGrant,
                    icon: const Icon(Icons.sim_card),
                    label: const Text('Grant phone permission'),
                  ),
                ],
              ),
            ),
          )
        else if (sims.isEmpty)
          const Card(
            child: ListTile(
              leading: Icon(Icons.sim_card_alert),
              title: Text('No active SIM detected'),
            ),
          )
        else
          ...sims.map((sim) => _SimTile(sim: sim, signal: signals[sim.subscriptionId])),
      ],
    );
  }
}

class _SimTile extends StatelessWidget {
  const _SimTile({required this.sim, this.signal});

  final SimInfo sim;
  final SignalInfo? signal;

  @override
  Widget build(BuildContext context) {
    final carrier = sim.carrierName.isNotEmpty ? sim.carrierName : 'Unknown carrier';
    final name = sim.displayName.isNotEmpty ? sim.displayName : carrier;
    return Card(
      child: ListTile(
        leading: Icon(sim.isEmbedded ? Icons.sim_card_download : Icons.sim_card),
        title: Text(name),
        subtitle: Text(
          'Carrier: $carrier\n'
          'Slot ${sim.slotIndex} · subId ${sim.subscriptionId} · '
          '${sim.isEmbedded ? 'eSIM' : 'physical'}\n'
          'Signal: ${_signalLabel(signal)}',
        ),
        trailing: Chip(label: Text(sim.simState)),
        isThreeLine: true,
      ),
    );
  }

  static String _signalLabel(SignalInfo? signal) {
    if (signal == null) return 'unknown';
    final bars = '${signal.level}/4';
    return signal.dbm != null ? '${signal.dbm} dBm ($bars)' : bars;
  }
}

class _SendSmsSection extends StatelessWidget {
  const _SendSmsSection({
    required this.hasPermission,
    required this.recipientController,
    required this.bodyController,
    required this.sims,
    required this.selectedSubId,
    required this.onSubChanged,
    required this.onSend,
    required this.onGrant,
    required this.outbox,
  });

  final bool hasPermission;
  final TextEditingController recipientController;
  final TextEditingController bodyController;
  final List<SimInfo> sims;
  final int? selectedSubId;
  final ValueChanged<int?> onSubChanged;
  final Future<void> Function() onSend;
  final Future<void> Function() onGrant;
  final List<OutboxEntry> outbox;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('Send test SMS', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        if (!hasPermission)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  const Text(
                    'SMS permission is needed to send messages.',
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: onGrant,
                    icon: const Icon(Icons.sms),
                    label: const Text('Grant SMS permission'),
                  ),
                ],
              ),
            ),
          )
        else
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  TextField(
                    controller: recipientController,
                    keyboardType: TextInputType.phone,
                    decoration: const InputDecoration(
                      labelText: 'Recipient number',
                      hintText: '+15551112222',
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: bodyController,
                    minLines: 1,
                    maxLines: 3,
                    decoration: const InputDecoration(labelText: 'Message'),
                  ),
                  if (sims.length > 1) ...[
                    const SizedBox(height: 8),
                    DropdownButton<int?>(
                      isExpanded: true,
                      value: selectedSubId,
                      hint: const Text('Default SIM'),
                      onChanged: onSubChanged,
                      items: [
                        const DropdownMenuItem<int?>(
                          value: null,
                          child: Text('Default SIM'),
                        ),
                        for (final sim in sims)
                          DropdownMenuItem<int?>(
                            value: sim.subscriptionId,
                            child: Text(
                              'Slot ${sim.slotIndex} · '
                              '${sim.carrierName.isNotEmpty ? sim.carrierName : 'SIM'}',
                            ),
                          ),
                      ],
                    ),
                  ],
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: onSend,
                    icon: const Icon(Icons.send),
                    label: const Text('Send'),
                  ),
                ],
              ),
            ),
          ),
        if (outbox.isNotEmpty) ...[
          const SizedBox(height: 12),
          Text('Recent', style: Theme.of(context).textTheme.labelLarge),
          const SizedBox(height: 4),
          ...outbox.map((e) => _OutboxTile(entry: e)),
        ],
      ],
    );
  }
}

class _OutboxTile extends StatelessWidget {
  const _OutboxTile({required this.entry});

  final OutboxEntry entry;

  @override
  Widget build(BuildContext context) {
    final (color, icon) = switch (entry.status) {
      'SENT' || 'DELIVERED' => (Colors.green, Icons.check_circle),
      'SENDING' || 'QUEUED' => (Colors.orange, Icons.schedule),
      'FAILED_TERMINAL' ||
      'FAILED_RETRYABLE' ||
      'UNDELIVERED' =>
        (Colors.red, Icons.error),
      _ => (Colors.grey, Icons.help),
    };
    return ListTile(
      dense: true,
      leading: Icon(icon, color: color),
      title: Text(entry.recipient),
      subtitle: entry.lastError != null ? Text(entry.lastError!) : null,
      trailing: Text(entry.status),
    );
  }
}
