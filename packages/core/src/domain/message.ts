export const MESSAGE_STATUSES = [
  'pending',
  'dispatched',
  'accepted',
  'sent',
  'delivered',
  'undelivered',
  'failed',
  'cancelled',
] as const;

export type MessageStatus = (typeof MESSAGE_STATUSES)[number];

/**
 * Progress ranks, not a transition table. Node events are at-least-once and can
 * arrive out of order — a `delivery_report` may land before the `sms_sent` that
 * logically precedes it, and any event may be redelivered after a reconnect.
 * Ranking makes every update idempotent and order-insensitive: status only ever
 * moves forward, so replaying an old event is a no-op rather than a regression.
 */
const RANK: Record<MessageStatus, number> = {
  pending: 0,
  dispatched: 1,
  accepted: 2,
  sent: 3,
  delivered: 4,
  undelivered: 4,
  failed: 4,
  cancelled: 4,
};

const TERMINAL = new Set<MessageStatus>(['delivered', 'undelivered', 'failed', 'cancelled']);

export const isTerminalStatus = (status: MessageStatus): boolean => TERMINAL.has(status);

/** The status after observing `next`, which is `current` whenever `next` is stale. */
export function advanceStatus(current: MessageStatus, next: MessageStatus): MessageStatus {
  if (isTerminalStatus(current)) return current;
  return RANK[next] > RANK[current] ? next : current;
}

/** A message may only be cancelled while the node could still act on it. */
export const isCancellable = (status: MessageStatus): boolean =>
  status === 'pending' || status === 'dispatched' || status === 'accepted';

/**
 * Rolls per-part outcomes into one message status, worst-part-wins, mirroring
 * the node's own multipart rule (§5): every part must succeed for the message to
 * have succeeded.
 */
export function rollupPartStatuses(
  parts: ReadonlyArray<{ status: string }>,
  success: MessageStatus,
  failure: MessageStatus,
): MessageStatus {
  if (parts.length === 0) return success;
  const ok = parts.every((part) => {
    const status = part.status.toUpperCase();
    return status === 'SENT' || status === 'DELIVERED' || status === 'OK';
  });
  return ok ? success : failure;
}
