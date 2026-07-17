import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/generated/luno_api.g.dart';
import '../../state/logs_providers.dart';
import '../shared/status_ui.dart';

const _levels = ['ALL', 'DEBUG', 'INFO', 'WARN', 'ERROR'];

class LogsScreen extends ConsumerStatefulWidget {
  const LogsScreen({super.key});

  @override
  ConsumerState<LogsScreen> createState() => _LogsScreenState();
}

class _LogsScreenState extends ConsumerState<LogsScreen> {
  String _filter = 'ALL';

  @override
  Widget build(BuildContext context) {
    final logs = ref.watch(logsProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Logs'),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(48),
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Row(
              children: [
                for (final level in _levels)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ChoiceChip(
                      label: Text(level),
                      selected: _filter == level,
                      onSelected: (_) => setState(() => _filter = level),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
      body: logs.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('$e')),
        data: (all) {
          final rows = _filter == 'ALL'
              ? all
              : all.where((l) => l.level == _filter).toList();
          if (rows.isEmpty) {
            return const Center(child: Text('No log lines'));
          }
          return ListView.builder(
            itemCount: rows.length,
            itemBuilder: (_, i) => _LogTile(entry: rows[i]),
          );
        },
      ),
    );
  }
}

class _LogTile extends StatelessWidget {
  const _LogTile({required this.entry});

  final LogEntry entry;

  @override
  Widget build(BuildContext context) {
    final color = _levelColor(entry.level);
    return ListTile(
      dense: true,
      leading: _LevelBadge(level: entry.level, color: color),
      title: Text(entry.message, style: Theme.of(context).textTheme.bodyMedium),
      subtitle: Text('${entry.tag} · ${formatClock(entry.timestampMs)}',
          style: Theme.of(context).textTheme.labelSmall),
    );
  }

  static Color _levelColor(String level) => switch (level) {
        'ERROR' => Colors.red,
        'WARN' => Colors.orange,
        'INFO' => Colors.blue,
        _ => Colors.grey,
      };
}

class _LevelBadge extends StatelessWidget {
  const _LevelBadge({required this.level, required this.color});

  final String level;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 32,
      alignment: Alignment.center,
      child: Text(
        level.substring(0, 1),
        style: TextStyle(color: color, fontWeight: FontWeight.bold),
      ),
    );
  }
}
