import type { ProtocolFrame } from '@luno-oss/protocol';
import { describe, expect, it } from 'vitest';
import { connectedDevice, handshake, harness, nodeEvent, recordingSink } from './helpers';

const commandsIn = (frames: ProtocolFrame[]): ProtocolFrame[] =>
  frames.filter((frame) => frame.body.kind === 'command');

describe('sending', () => {
  it('dispatches to a connected node and records the command id', async () => {
    const context = harness();
    const { session, recorder, device } = await connectedDevice(context);
    await handshake(session);

    const message = await context.luno.sms.send({ deviceId: device.id, to: '+1', body: 'hi' });

    expect(message.status).toBe('dispatched');
    expect(message.commandId).not.toBeNull();

    const sent = commandsIn(recorder.sent).at(-1);
    expect(sent?.id).toBe(message.commandId);
  });

  it('queues rather than failing when the node is offline', async () => {
    const context = harness();
    const { session, device } = await connectedDevice(context);
    await session.close();

    const message = await context.luno.sms.send({ deviceId: device.id, to: '+1', body: 'hi' });
    expect(message.status).toBe('pending');
  });

  it('refuses to send to a revoked device', async () => {
    const context = harness();
    const { device } = await connectedDevice(context);
    await context.luno.devices.revoke(device.id);

    await expect(
      context.luno.sms.send({ deviceId: device.id, to: '+1', body: 'hi' }),
    ).rejects.toThrow(/revoked/);
  });

  it('re-sends everything outstanding on resync, under the original command id', async () => {
    const context = harness();
    const { session, recorder, device, credential } = await connectedDevice(context);
    await session.close();

    const queued = await context.luno.sms.send({ deviceId: device.id, to: '+1', body: 'queued' });
    expect(commandsIn(recorder.sent)).toHaveLength(0);

    // A reconnect presents the credential it already holds; re-enrolling would be
    // a different device as far as the session policy is concerned.
    const returning = await context.luno.connections.authorize(credential);
    if (!returning) throw new Error('credential stopped working across reconnect');
    const rejoined = recordingSink();
    await handshake(await context.luno.connections.open(returning, rejoined.sink));

    const resent = commandsIn(rejoined.sent);
    expect(resent).toHaveLength(1);
    // Same id as the first attempt, so the node dedupes rather than double-sending.
    expect(resent[0]?.id).toBe(queued.commandId);
  });
});

describe('status tracking', () => {
  const setup = async () => {
    const context = harness();
    const { session, device } = await connectedDevice(context);
    await handshake(session);
    const message = await context.luno.sms.send({ deviceId: device.id, to: '+1', body: 'hi' });
    await session.receive(
      nodeEvent({ type: 'sms_accepted', commandId: message.commandId ?? '', messageId: 'node-msg-1' }),
    );
    return { context, session, message };
  };

  it('follows the message through to delivery', async () => {
    const { context, session, message } = await setup();
    expect((await context.luno.sms.get(message.id)).status).toBe('accepted');

    await session.receive(
      nodeEvent({ type: 'sms_sent', messageId: 'node-msg-1', parts: [{ index: 0, status: 'SENT' }] }),
    );
    expect((await context.luno.sms.get(message.id)).status).toBe('sent');

    await session.receive(
      nodeEvent({
        type: 'delivery_report',
        messageId: 'node-msg-1',
        part: 0,
        status: 'DELIVERED',
        at: 1_700_000_000_000,
      }),
    );
    expect((await context.luno.sms.get(message.id)).status).toBe('delivered');
  });

  it('takes the worst part outcome across a multipart message', async () => {
    const { context, session, message } = await setup();

    await session.receive(
      nodeEvent({
        type: 'sms_sent',
        messageId: 'node-msg-1',
        parts: [
          { index: 0, status: 'SENT' },
          { index: 1, status: 'FAILED', errorCode: 'NO_SERVICE' },
        ],
      }),
    );

    expect((await context.luno.sms.get(message.id)).status).toBe('failed');
  });

  it('waits for every part before calling a message delivered', async () => {
    const { context, session, message } = await setup();
    await session.receive(
      nodeEvent({
        type: 'sms_sent',
        messageId: 'node-msg-1',
        parts: [
          { index: 0, status: 'SENT' },
          { index: 1, status: 'SENT' },
        ],
      }),
    );

    await session.receive(
      nodeEvent({ type: 'delivery_report', messageId: 'node-msg-1', part: 0, status: 'DELIVERED', at: 1 }),
    );
    expect((await context.luno.sms.get(message.id)).status).toBe('sent');

    await session.receive(
      nodeEvent({ type: 'delivery_report', messageId: 'node-msg-1', part: 1, status: 'UNDELIVERED', at: 2 }),
    );
    expect((await context.luno.sms.get(message.id)).status).toBe('undelivered');
  });

  /**
   * Node events are at-least-once and can arrive out of order after a reconnect,
   * so replaying an earlier one must not walk the status backwards.
   */
  it('ignores stale and duplicated events', async () => {
    const { context, session, message } = await setup();
    await session.receive(
      nodeEvent({ type: 'sms_sent', messageId: 'node-msg-1', parts: [{ index: 0, status: 'SENT' }] }),
    );
    await session.receive(
      nodeEvent({ type: 'delivery_report', messageId: 'node-msg-1', part: 0, status: 'DELIVERED', at: 1 }),
    );
    expect((await context.luno.sms.get(message.id)).status).toBe('delivered');

    await session.receive(
      nodeEvent({ type: 'sms_accepted', commandId: message.commandId ?? '', messageId: 'node-msg-1' }),
    );
    await session.receive(
      nodeEvent({ type: 'sms_sent', messageId: 'node-msg-1', parts: [{ index: 0, status: 'SENT' }] }),
    );

    expect((await context.luno.sms.get(message.id)).status).toBe('delivered');
  });

  it('notifies the application as the status advances', async () => {
    const context = harness();
    const seen: string[] = [];
    context.luno.on('sms.status', ({ message }) => {
      seen.push(message.status);
    });

    const { session, device } = await connectedDevice(context);
    await handshake(session);
    const message = await context.luno.sms.send({ deviceId: device.id, to: '+1', body: 'hi' });
    await session.receive(
      nodeEvent({ type: 'sms_accepted', commandId: message.commandId ?? '', messageId: 'm1' }),
    );
    await session.receive(
      nodeEvent({ type: 'sms_sent', messageId: 'm1', parts: [{ index: 0, status: 'SENT' }] }),
    );

    expect(seen).toEqual(['accepted', 'sent']);
  });

  /**
   * A node may stream accepted → sent → delivery_report without waiting for acks.
   * sms_sent looks up the message that sms_accepted just wrote, so the session
   * must apply a burst in arrival order even when receive() is not awaited between
   * frames — which is how a real socket handler delivers them.
   */
  it('applies a burst of unawaited events in order', async () => {
    const context = harness();
    const { session, device } = await connectedDevice(context);
    await handshake(session);
    const message = await context.luno.sms.send({ deviceId: device.id, to: '+1', body: 'hi' });

    void session.receive(
      nodeEvent({ type: 'sms_accepted', commandId: message.commandId ?? '', messageId: 'm1' }, 10),
    );
    void session.receive(
      nodeEvent({ type: 'sms_sent', messageId: 'm1', parts: [{ index: 0, status: 'SENT' }] }, 11),
    );
    await session.receive(
      nodeEvent({ type: 'delivery_report', messageId: 'm1', part: 0, status: 'DELIVERED', at: 1 }, 12),
    );

    expect((await context.luno.sms.get(message.id)).status).toBe('delivered');
  });

  it('ignores an event about a message it does not know', async () => {
    const context = harness();
    const { session } = await connectedDevice(context);
    await handshake(session);

    await expect(
      session.receive(
        nodeEvent({ type: 'sms_sent', messageId: 'ghost', parts: [{ index: 0, status: 'SENT' }] }),
      ),
    ).resolves.toBeUndefined();
  });
});

describe('cancellation', () => {
  it('cancels a message the node has not sent yet', async () => {
    const context = harness();
    const { session, device } = await connectedDevice(context);
    await handshake(session);
    const message = await context.luno.sms.send({ deviceId: device.id, to: '+1', body: 'hi' });

    expect((await context.luno.sms.cancel(message.id)).status).toBe('cancelled');
  });

  it('refuses to cancel a message that has already gone out', async () => {
    const context = harness();
    const { session, device } = await connectedDevice(context);
    await handshake(session);
    const message = await context.luno.sms.send({ deviceId: device.id, to: '+1', body: 'hi' });

    await session.receive(
      nodeEvent({ type: 'sms_accepted', commandId: message.commandId ?? '', messageId: 'm1' }),
    );
    await session.receive(
      nodeEvent({ type: 'sms_sent', messageId: 'm1', parts: [{ index: 0, status: 'SENT' }] }),
    );

    await expect(context.luno.sms.cancel(message.id)).rejects.toThrow(/already sent/);
  });
});
