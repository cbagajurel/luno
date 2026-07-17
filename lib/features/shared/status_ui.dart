import 'package:flutter/material.dart' hide ConnectionState;

import '../../bridge/luno_bridge.dart';

typedef StatusUi = ({String label, Color color, IconData icon});

StatusUi connectionUi(ConnectionState state) => switch (state) {
      ConnectionState.ready => (label: 'Ready', color: Colors.green, icon: Icons.cloud_done),
      ConnectionState.authenticated =>
        (label: 'Authenticated', color: Colors.lightGreen, icon: Icons.verified_user),
      ConnectionState.connected => (label: 'Connected', color: Colors.blue, icon: Icons.link),
      ConnectionState.connecting =>
        (label: 'Connecting…', color: Colors.orange, icon: Icons.sync),
      ConnectionState.reconnecting =>
        (label: 'Reconnecting…', color: Colors.orange, icon: Icons.sync_problem),
      ConnectionState.backingOff =>
        (label: 'Backing off…', color: Colors.orange, icon: Icons.timelapse),
      ConnectionState.disconnected =>
        (label: 'Disconnected', color: Colors.grey, icon: Icons.link_off),
      ConnectionState.offlineNoNetwork =>
        (label: 'Offline · no network', color: Colors.red, icon: Icons.cloud_off),
      ConnectionState.unknown => (label: 'Unknown', color: Colors.grey, icon: Icons.help),
    };

StatusUi agentUi(AgentRunState state) => switch (state) {
      AgentRunState.running => (label: 'Agent running', color: Colors.green, icon: Icons.check_circle),
      AgentRunState.stopped => (label: 'Agent stopped', color: Colors.grey, icon: Icons.stop_circle),
      AgentRunState.unknown => (label: 'Agent state unknown', color: Colors.orange, icon: Icons.help),
    };

StatusUi outboxStatusUi(String status) => switch (status) {
      'SENT' || 'DELIVERED' => (label: status, color: Colors.green, icon: Icons.check_circle),
      'SENDING' || 'QUEUED' => (label: status, color: Colors.orange, icon: Icons.schedule),
      'FAILED_TERMINAL' ||
      'FAILED_RETRYABLE' ||
      'UNDELIVERED' =>
        (label: status, color: Colors.red, icon: Icons.error),
      _ => (label: status, color: Colors.grey, icon: Icons.help),
    };

String formatClock(int epochMs) {
  final t = DateTime.fromMillisecondsSinceEpoch(epochMs);
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(t.hour)}:${two(t.minute)}:${two(t.second)}';
}
