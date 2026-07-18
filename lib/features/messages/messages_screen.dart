import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/generated/luno_api.g.dart';
import '../../state/device_providers.dart';
import '../../state/messages_providers.dart';
import '../../ui/ui.dart';
import '../shared/status_ui.dart';
import 'compose_sheet.dart';

class MessagesScreen extends ConsumerWidget {
  const MessagesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return DefaultTabController(
      length: 2,
      child: LunoScaffold(
        title: 'Messages',
        actions: [
          IconButton(
            tooltip: 'Compose',
            icon: const Icon(Icons.edit_rounded),
            onPressed: () => showComposeSheet(context),
          ),
        ],
        bottom: const TabBar(tabs: [Tab(text: 'Sent'), Tab(text: 'Received')]),
        body: const TabBarView(children: [_SentTab(), _ReceivedTab()]),
      ),
    );
  }
}

const _listPadding = EdgeInsets.all(LunoSpacing.md);

class _SentTab extends ConsumerWidget {
  const _SentTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final outbox = ref.watch(outboxProvider);
    return AsyncView<List<OutboxEntry>>(
      value: outbox,
      data: (rows) => rows.isEmpty
          ? const EmptyState(
              icon: Icons.outbox_rounded,
              title: 'No sent messages yet',
              message: 'Messages you send from the backend appear here.',
            )
          : ListView.separated(
              padding: _listPadding,
              itemCount: rows.length,
              separatorBuilder: (_, _) => const _RowDivider(),
              itemBuilder: (_, i) => _OutboxRow(key: ValueKey(rows[i].id), entry: rows[i]),
            ),
    );
  }
}

class _ReceivedTab extends ConsumerWidget {
  const _ReceivedTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final inbox = ref.watch(inboxProvider);
    final sims = ref.watch(deviceStateProvider.select((d) => d.value?.sims)) ?? const <SimInfo>[];
    return AsyncView<List<InboundEntry>>(
      value: inbox,
      data: (rows) => rows.isEmpty
          ? const EmptyState(
              icon: Icons.inbox_rounded,
              title: 'No messages received yet',
              message: 'Incoming SMS captured by this node appear here.',
            )
          : ListView.separated(
              padding: _listPadding,
              itemCount: rows.length,
              separatorBuilder: (_, _) => const _RowDivider(),
              itemBuilder: (_, i) => _InboxRow(key: ValueKey(rows[i].id), entry: rows[i], sims: sims),
            ),
    );
  }
}

class _RowDivider extends StatelessWidget {
  const _RowDivider();

  @override
  Widget build(BuildContext context) => Divider(
        height: 1,
        indent: 52,
        color: context.scheme.outlineVariant.withValues(alpha: 0.6),
      );
}

class _MessageRow extends StatelessWidget {
  const _MessageRow({
    required this.icon,
    required this.tone,
    required this.title,
    required this.subtitle,
    this.trailing,
  });

  final IconData icon;
  final StatusTone tone;
  final String title;
  final String? subtitle;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final colors = context.semantic.tone(tone);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: LunoSpacing.sm),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: colors.container,
              borderRadius: BorderRadius.circular(LunoRadius.sm),
            ),
            child: Icon(icon, size: 18, color: colors.color),
          ),
          const SizedBox(width: LunoSpacing.sm),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: context.text.titleSmall, maxLines: 1, overflow: TextOverflow.ellipsis),
                if (subtitle != null) ...[
                  const SizedBox(height: 2),
                  Text(
                    subtitle!,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: context.text.bodySmall?.copyWith(color: context.scheme.onSurfaceVariant),
                  ),
                ],
              ],
            ),
          ),
          if (trailing != null) ...[
            const SizedBox(width: LunoSpacing.sm),
            trailing!,
          ],
        ],
      ),
    );
  }
}

class _OutboxRow extends StatelessWidget {
  const _OutboxRow({super.key, required this.entry});

  final OutboxEntry entry;

  @override
  Widget build(BuildContext context) {
    final ui = outboxStatusUi(entry.status);
    final parts = entry.partCount > 1
        ? '${entry.partCount} parts · ${entry.deliveredCount}/${entry.partCount} delivered'
        : null;
    return _MessageRow(
      icon: ui.icon,
      tone: ui.tone,
      title: entry.recipient,
      subtitle: entry.lastError ?? parts,
      trailing: StatusPill(label: entry.status, tone: ui.tone, dense: true),
    );
  }
}

class _InboxRow extends StatelessWidget {
  const _InboxRow({super.key, required this.entry, required this.sims});

  final InboundEntry entry;
  final List<SimInfo> sims;

  @override
  Widget build(BuildContext context) {
    final sim = simLabelForSub(entry.subscriptionId, sims);
    final parts = entry.parts > 1 ? '${entry.parts} parts' : null;
    final meta = [?sim, ?parts].join(' · ');
    return _MessageRow(
      icon: Icons.sms_rounded,
      tone: StatusTone.brand,
      title: entry.sender,
      subtitle: entry.body,
      trailing: meta.isEmpty
          ? null
          : Text(meta, style: context.text.labelSmall?.copyWith(color: context.semantic.textFaint)),
    );
  }
}
