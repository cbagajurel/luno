import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/generated/luno_api.g.dart';
import '../../state/messages_providers.dart';
import '../shared/status_ui.dart';
import 'compose_sheet.dart';

class MessagesScreen extends ConsumerWidget {
  const MessagesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Messages'),
          bottom: const TabBar(tabs: [Tab(text: 'Sent'), Tab(text: 'Received')]),
        ),
        body: const TabBarView(children: [_SentTab(), _ReceivedTab()]),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: () => showComposeSheet(context),
          icon: const Icon(Icons.edit),
          label: const Text('Compose'),
        ),
      ),
    );
  }
}

class _SentTab extends ConsumerWidget {
  const _SentTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final outbox = ref.watch(outboxProvider);
    return outbox.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => _ErrorView(message: '$e'),
      data: (rows) => rows.isEmpty
          ? const _EmptyView(icon: Icons.outbox, text: 'No sent messages yet')
          : ListView.builder(
              itemCount: rows.length,
              itemBuilder: (_, i) => _OutboxTile(entry: rows[i]),
            ),
    );
  }
}

class _ReceivedTab extends ConsumerWidget {
  const _ReceivedTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final inbox = ref.watch(inboxProvider);
    return inbox.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => _ErrorView(message: '$e'),
      data: (rows) => rows.isEmpty
          ? const _EmptyView(icon: Icons.inbox, text: 'No messages received yet')
          : ListView.builder(
              itemCount: rows.length,
              itemBuilder: (_, i) => _InboxTile(entry: rows[i]),
            ),
    );
  }
}

class _OutboxTile extends StatelessWidget {
  const _OutboxTile({required this.entry});

  final OutboxEntry entry;

  @override
  Widget build(BuildContext context) {
    final ui = outboxStatusUi(entry.status);
    final parts = entry.partCount > 1
        ? '${entry.partCount} parts · ${entry.deliveredCount}/${entry.partCount} delivered'
        : null;
    final subtitle = entry.lastError ?? parts;
    return ListTile(
      leading: Icon(ui.icon, color: ui.color),
      title: Text(entry.recipient),
      subtitle: subtitle != null ? Text(subtitle) : null,
      trailing: Text(entry.status, style: Theme.of(context).textTheme.labelSmall),
    );
  }
}

class _InboxTile extends StatelessWidget {
  const _InboxTile({required this.entry});

  final InboundEntry entry;

  @override
  Widget build(BuildContext context) {
    final parts = entry.parts > 1 ? ' · ${entry.parts} parts' : '';
    return ListTile(
      leading: const Icon(Icons.sms_outlined),
      title: Text(entry.sender),
      subtitle: Text(entry.body),
      trailing: Text('subId ${entry.subscriptionId ?? '—'}$parts',
          style: Theme.of(context).textTheme.labelSmall),
      isThreeLine: entry.body.length > 40,
    );
  }
}

class _EmptyView extends StatelessWidget {
  const _EmptyView({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return ListView(
      children: [
        const SizedBox(height: 96),
        Icon(icon, size: 48, color: Theme.of(context).disabledColor),
        const SizedBox(height: 12),
        Center(child: Text(text, style: Theme.of(context).textTheme.bodyLarge)),
      ],
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Text(message, textAlign: TextAlign.center),
      ),
    );
  }
}
