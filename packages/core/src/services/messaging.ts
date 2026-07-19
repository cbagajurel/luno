import { commandFrame, type Event, type ProtocolFrame } from '@luno/protocol';
import { auditEvent, type CoreContext } from '../context';
import { LunoError } from '../domain/errors';
import { advanceStatus, isCancellable, rollupPartStatuses } from '../domain/message';
import type { DeliveryOutcome } from '../ports/sessions';
import type { MessagePart, MessageRecord } from '../ports/store';

export interface SendSmsInput {
  deviceId: string;
  to: string;
  body: string;
  subscriptionId?: number;
  /** Caller's correlation id, echoed back on every event about this message. */
  ref?: string;
}

const DELIVERY_TERMINAL = new Set(['DELIVERED', 'UNDELIVERED', 'FAILED']);

export function messagingService(context: CoreContext) {
  const timestamp = (): string => new Date(context.clock.now()).toISOString();

  function frameFor(message: MessageRecord): ProtocolFrame {
    return commandFrame(
      {
        id: message.commandId ?? message.id,
        ts: timestamp(),
        deviceId: message.deviceId,
        // Placeholder: the connected session stamps its own monotonic sequence as
        // the frame goes out, because seq is per-connection and this layer has no
        // connection to count against.
        seq: 0,
      },
      {
        type: 'send_sms',
        to: message.to,
        body: message.body,
        ...(message.subscriptionId !== null ? { subscriptionId: message.subscriptionId } : {}),
        deliveryReport: true,
        ...(message.ref !== null ? { ref: message.ref } : {}),
      },
    );
  }

  async function dispatch(message: MessageRecord): Promise<DeliveryOutcome> {
    const outcome = await context.sessions.deliver(message.deviceId, frameFor(message));
    if (outcome.delivered) {
      await context.store.messages.update(message.id, {
        status: advanceStatus(message.status, 'dispatched'),
        updatedAt: context.clock.now(),
      });
      await auditEvent(context, {
        deviceId: message.deviceId,
        direction: 'out',
        kind: 'command',
        type: 'send_sms',
        frameId: message.commandId,
        payload: { messageId: message.id },
      });
    }
    return outcome;
  }

  async function patchStatus(message: MessageRecord, next: MessageRecord['status']): Promise<void> {
    const status = advanceStatus(message.status, next);
    if (status === message.status) return;
    await context.store.messages.update(message.id, {
      status,
      updatedAt: context.clock.now(),
    });
    const updated = await context.store.messages.findById(message.id);
    if (updated) await context.hooks.emit('sms.status', { message: updated });
  }

  return {
    async send(input: SendSmsInput): Promise<MessageRecord> {
      const device = await context.store.devices.findById(input.deviceId);
      if (!device) throw LunoError.notFound('device');
      if (device.status !== 'active') {
        throw new LunoError('forbidden', 'device has been revoked');
      }

      const now = context.clock.now();
      const message: MessageRecord = {
        id: context.ids.newId('msg'),
        deviceId: input.deviceId,
        to: input.to,
        body: input.body,
        subscriptionId: input.subscriptionId ?? null,
        ref: input.ref ?? null,
        status: 'pending',
        // Fixed for the message's whole life: it is the idempotency key the node
        // dedupes on, so a resync re-sends this exact id rather than a new one.
        commandId: context.ids.newId('cmd'),
        nodeMessageId: null,
        parts: [],
        error: null,
        createdAt: now,
        updatedAt: now,
      };
      await context.store.messages.create(message);

      // An offline node is not an error: the message stays pending and goes out
      // on the next resync, which is what makes the queue survive a flat battery.
      await dispatch(message);
      return (await context.store.messages.findById(message.id)) ?? message;
    },

    /** Re-sends everything the node has not finished, on every reconnect (§7.4). */
    async redispatchOutstanding(deviceId: string): Promise<number> {
      const outstanding = await context.store.messages.listOutstanding(deviceId);
      let sent = 0;
      for (const message of outstanding) {
        const outcome = await dispatch(message);
        if (outcome.delivered) sent += 1;
      }
      return sent;
    },

    async applyEvent(deviceId: string, event: Event): Promise<void> {
      switch (event.type) {
        case 'sms_accepted': {
          const message = await context.store.messages.findByCommandId(event.commandId);
          if (!message) return;
          await context.store.messages.update(message.id, {
            nodeMessageId: event.messageId,
            updatedAt: context.clock.now(),
          });
          await patchStatus(message, 'accepted');
          return;
        }

        case 'sms_sent': {
          const message = await context.store.messages.findByNodeMessageId(deviceId, event.messageId);
          if (!message) return;
          const parts: MessagePart[] = event.parts.map((part) => ({
            index: part.index,
            status: part.status,
            errorCode: part.errorCode ?? null,
          }));
          await context.store.messages.update(message.id, {
            parts,
            updatedAt: context.clock.now(),
          });
          await patchStatus(message, rollupPartStatuses(parts, 'sent', 'failed'));
          return;
        }

        case 'delivery_report': {
          const message = await context.store.messages.findByNodeMessageId(deviceId, event.messageId);
          if (!message) return;

          const parts = [...message.parts];
          const index = parts.findIndex((part) => part.index === event.part);
          if (index === -1) {
            parts.push({ index: event.part, status: event.status, errorCode: null });
          } else {
            parts[index] = { index: event.part, status: event.status, errorCode: null };
          }
          await context.store.messages.update(message.id, {
            parts,
            updatedAt: context.clock.now(),
          });

          const settled = parts.every((part) => DELIVERY_TERMINAL.has(part.status.toUpperCase()));
          if (settled) {
            await patchStatus(message, rollupPartStatuses(parts, 'delivered', 'undelivered'));
          }
          return;
        }

        case 'sms_received':
          await context.hooks.emit('sms.received', {
            deviceId,
            from: event.from,
            body: event.body,
            subscriptionId: event.subscriptionId ?? null,
            receivedAt: event.receivedAt,
            parts: event.parts ?? 1,
          });
          return;

        default:
          return;
      }
    },

    async cancel(messageId: string): Promise<MessageRecord> {
      const message = await context.store.messages.findById(messageId);
      if (!message) throw LunoError.notFound('message');
      if (!isCancellable(message.status)) {
        throw new LunoError('conflict', `message is already ${message.status}`);
      }

      await context.sessions.deliver(
        message.deviceId,
        commandFrame(
          { id: context.ids.newId('cmd'), ts: timestamp(), deviceId: message.deviceId, seq: 0 },
          { type: 'cancel_sms', commandId: message.commandId ?? message.id },
        ),
      );
      await context.store.messages.update(messageId, {
        status: 'cancelled',
        updatedAt: context.clock.now(),
      });
      return (await context.store.messages.findById(messageId)) ?? message;
    },

    async get(messageId: string): Promise<MessageRecord> {
      const message = await context.store.messages.findById(messageId);
      if (!message) throw LunoError.notFound('message');
      return message;
    },

    list: (deviceId: string, limit = 50): Promise<MessageRecord[]> =>
      context.store.messages.listByDevice(deviceId, limit),
  };
}

export type MessagingService = ReturnType<typeof messagingService>;
