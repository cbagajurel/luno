import 'package:flutter/material.dart' hide ConnectionState;

import '../../bridge/generated/luno_api.g.dart';
import '../../bridge/luno_bridge.dart';
import '../../ui/tokens/colors.dart';

/// A status mapped to a display label, a semantic [StatusTone], and an icon.
/// The tone resolves to real colours through the theme at render time, so no
/// screen ever reaches for a raw [Colors] constant.
typedef StatusUi = ({String label, StatusTone tone, IconData icon});

/// A human label for a SIM: carrier first, falling back to the OS display name,
/// then the slot. Used anywhere we'd otherwise leak a raw subscription id.
String simLabel(SimInfo sim) {
  if (sim.carrierName.isNotEmpty) return sim.carrierName;
  if (sim.displayName.isNotEmpty) return sim.displayName;
  return 'Slot ${sim.slotIndex}';
}

/// Resolves a (possibly null) subscription id to a SIM label using the current
/// device SIMs. Returns null when it can't be mapped so callers can hide it.
String? simLabelForSub(int? subscriptionId, List<SimInfo> sims) {
  if (subscriptionId == null) return null;
  for (final sim in sims) {
    if (sim.subscriptionId == subscriptionId) {
      return '${simLabel(sim)} · Slot ${sim.slotIndex}';
    }
  }
  return null;
}

StatusUi connectionUi(ConnectionState state) => switch (state) {
      ConnectionState.ready =>
        (label: 'Ready', tone: StatusTone.positive, icon: Icons.cloud_done_rounded),
      ConnectionState.authenticated =>
        (label: 'Authenticated', tone: StatusTone.positive, icon: Icons.verified_user_rounded),
      ConnectionState.connected =>
        (label: 'Connected', tone: StatusTone.info, icon: Icons.link_rounded),
      ConnectionState.connecting =>
        (label: 'Connecting…', tone: StatusTone.caution, icon: Icons.sync_rounded),
      ConnectionState.reconnecting =>
        (label: 'Reconnecting…', tone: StatusTone.caution, icon: Icons.sync_problem_rounded),
      ConnectionState.backingOff =>
        (label: 'Backing off…', tone: StatusTone.caution, icon: Icons.timelapse_rounded),
      ConnectionState.disconnected =>
        (label: 'Disconnected', tone: StatusTone.neutral, icon: Icons.link_off_rounded),
      ConnectionState.offlineNoNetwork =>
        (label: 'Offline · no network', tone: StatusTone.danger, icon: Icons.cloud_off_rounded),
      ConnectionState.unknown =>
        (label: 'Unknown', tone: StatusTone.neutral, icon: Icons.help_rounded),
    };

StatusUi agentUi(AgentRunState state) => switch (state) {
      AgentRunState.running =>
        (label: 'Agent running', tone: StatusTone.positive, icon: Icons.check_circle_rounded),
      AgentRunState.stopped =>
        (label: 'Agent stopped', tone: StatusTone.neutral, icon: Icons.stop_circle_rounded),
      AgentRunState.unknown =>
        (label: 'Agent state unknown', tone: StatusTone.caution, icon: Icons.help_rounded),
    };

StatusUi outboxStatusUi(String status) => switch (status) {
      'SENT' || 'DELIVERED' =>
        (label: status, tone: StatusTone.positive, icon: Icons.check_circle_rounded),
      'SENDING' || 'QUEUED' =>
        (label: status, tone: StatusTone.caution, icon: Icons.schedule_rounded),
      'FAILED_TERMINAL' || 'FAILED_RETRYABLE' || 'UNDELIVERED' =>
        (label: status, tone: StatusTone.danger, icon: Icons.error_rounded),
      _ => (label: status, tone: StatusTone.neutral, icon: Icons.help_rounded),
    };

/// The semantic tone for a log level — the single source that folds the logs
/// screen's old inline colour switch into the shared tone system.
StatusTone logLevelTone(String level) => switch (level) {
      'ERROR' => StatusTone.danger,
      'WARN' => StatusTone.caution,
      'INFO' => StatusTone.info,
      _ => StatusTone.neutral,
    };

String formatClock(int epochMs) {
  final t = DateTime.fromMillisecondsSinceEpoch(epochMs);
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(t.hour)}:${two(t.minute)}:${two(t.second)}';
}
