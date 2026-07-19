import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/generated/luno_api.g.dart';
import '../../state/logs_providers.dart';
import '../../ui/ui.dart';
import '../shared/status_ui.dart';

const _levels = ['ALL', 'DEBUG', 'INFO', 'WARN', 'ERROR'];

class LogsScreen extends ConsumerStatefulWidget {
  const LogsScreen({super.key});

  @override
  ConsumerState<LogsScreen> createState() => _LogsScreenState();
}

class _LogsScreenState extends ConsumerState<LogsScreen> {
  static const _pageSize = 30;

  String _filter = 'ALL';
  int _visible = _pageSize;
  final _scroll = ScrollController();

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  /// Reveal another page once the user nears the end, so the built row count
  /// stays bounded regardless of how many lines the ring buffer holds.
  void _onScroll() {
    if (_scroll.position.pixels >= _scroll.position.maxScrollExtent - 400) {
      setState(() => _visible += _pageSize);
    }
  }

  void _selectFilter(String level) {
    setState(() {
      _filter = level;
      _visible = _pageSize;
    });
    if (_scroll.hasClients) _scroll.jumpTo(0);
  }

  @override
  Widget build(BuildContext context) {
    final logs = ref.watch(logsProvider);
    return LunoScaffold(
      title: 'Logs',
      bottom: PreferredSize(
        preferredSize: const Size.fromHeight(48),
        child: SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.fromLTRB(
            LunoSpacing.md,
            0,
            LunoSpacing.md,
            LunoSpacing.xs,
          ),
          child: Row(
            children: [
              for (final level in _levels)
                Padding(
                  padding: const EdgeInsets.only(right: LunoSpacing.xs),
                  child: ChoiceChip(
                    label: Text(level),
                    selected: _filter == level,
                    showCheckmark: false,
                    onSelected: (_) => _selectFilter(level),
                  ),
                ),
            ],
          ),
        ),
      ),
      body: AsyncView<List<LogEntry>>(
        value: logs,
        data: (all) {
          final rows = _filter == 'ALL'
              ? all
              : all.where((l) => l.level == _filter).toList();
          if (rows.isEmpty) {
            return const EmptyState(
              icon: Icons.receipt_long_rounded,
              title: 'No log lines',
              message: 'Agent activity is recorded here as it happens.',
            );
          }
          final windowed = rows.length > _visible ? _visible : rows.length;
          return ListView.separated(
            controller: _scroll,
            padding: EdgeInsets.fromLTRB(
              LunoSpacing.md,
              LunoSpacing.xs,
              LunoSpacing.md,
              context.navClearance,
            ),
            itemCount: windowed,
            separatorBuilder: (_, _) => Divider(
              height: 1,
              color: context.scheme.outlineVariant.withValues(alpha: 0.5),
            ),
            itemBuilder: (_, i) => _LogRow(entry: rows[i]),
          );
        },
      ),
    );
  }
}

class _LogRow extends StatelessWidget {
  const _LogRow({required this.entry});

  final LogEntry entry;

  @override
  Widget build(BuildContext context) {
    final tone = logLevelTone(entry.level);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: LunoSpacing.xs),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          StatusPill(label: entry.level, tone: tone, dense: true),
          const SizedBox(width: LunoSpacing.sm),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(entry.message, style: context.text.bodyMedium),
                const SizedBox(height: 2),
                Text(
                  '${entry.tag} · ${formatClock(entry.timestampMs)}',
                  style: lunoMonoStyle.copyWith(
                    color: context.semantic.textFaint,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
