import { describe, expect, it } from 'vitest';
import { connectedDevice, handshake, harness, nodeControl, nodeEvent } from './helpers';

const bodyOf = (frame: { body: unknown }) => frame.body as Record<string, unknown>;

describe('handshake', () => {
  it('answers version negotiation and marks the device authenticated', async () => {
    const context = harness();
    const { session, recorder, device } = await connectedDevice(context);

    await session.receive(nodeControl({ type: 'version_negotiate', supported: [1] }));

    const reply = recorder.sent.at(-1);
    expect(reply && bodyOf(reply)['kind']).toBe('control');
    expect(reply && (bodyOf(reply)['control'] as Record<string, unknown>)['selected']).toBe(1);
    expect((await context.store.devices.findById(device.id))?.phase).toBe('authenticated');
  });

  it('acks resync, which is what takes the node to READY', async () => {
    const context = harness();
    const { session, recorder, device } = await connectedDevice(context);

    await session.receive(
      nodeControl({ type: 'resync', lastAckedInboundSeq: 0, outstandingOutboxIds: [] }, 7),
    );

    const ack = recorder.sent.at(-1);
    expect(ack && bodyOf(ack)['kind']).toBe('ack');
    expect(ack && (bodyOf(ack)['ack'] as Record<string, unknown>)['ackedId']).toBe('node_frame_7');
    expect((await context.store.devices.findById(device.id))?.phase).toBe('ready');
  });

  it('closes a connection with no shared protocol version', async () => {
    const context = harness();
    const { session, recorder } = await connectedDevice(context);

    await session.receive(nodeControl({ type: 'version_negotiate', supported: [99] }));
    expect(recorder.closed).toHaveLength(1);
  });

  it('replies to an application ping', async () => {
    const context = harness();
    const { session, recorder } = await connectedDevice(context);

    await session.receive(nodeControl({ type: 'ping' }));
    const pong = recorder.sent.at(-1);
    expect(pong && (bodyOf(pong)['control'] as Record<string, unknown>)['type']).toBe('pong');
  });

  it('numbers outbound frames monotonically', async () => {
    const context = harness();
    const { session, recorder } = await connectedDevice(context);
    await handshake(session);

    const sequences = recorder.sent.map((frame) => frame.seq);
    expect(sequences).toEqual([...sequences].sort((a, b) => a - b));
    expect(new Set(sequences).size).toBe(sequences.length);
  });
});

describe('events', () => {
  it('acks every event so the node can drain its durable outbox', async () => {
    const context = harness();
    const { session, recorder } = await connectedDevice(context);

    await session.receive(
      nodeEvent({ type: 'heartbeat', queueDepth: 0 }, 3, 'evt_hb_1'),
    );

    const ack = recorder.sent.at(-1);
    expect(ack && (bodyOf(ack)['ack'] as Record<string, unknown>)['ackedId']).toBe('evt_hb_1');
  });

  it('surfaces an inbound SMS to the application', async () => {
    const context = harness();
    const received: unknown[] = [];
    context.luno.on('sms.received', (payload) => {
      received.push(payload);
    });

    const { session, device } = await connectedDevice(context);
    await session.receive(
      nodeEvent({
        type: 'sms_received',
        from: '+15551234567',
        body: 'pong',
        receivedAt: 1_700_000_000_000,
      }),
    );

    expect(received).toEqual([
      {
        deviceId: device.id,
        from: '+15551234567',
        body: 'pong',
        subscriptionId: null,
        receivedAt: 1_700_000_000_000,
        parts: 1,
      },
    ]);
  });

  it('records the last time a device was heard from', async () => {
    const context = harness();
    const { session, device } = await connectedDevice(context);
    context.advance(5_000);

    await session.receive(nodeEvent({ type: 'heartbeat', queueDepth: 2, battery: 80 }));

    const stored = await context.store.devices.findById(device.id);
    expect(stored?.lastSeenAt).toBe(context.now());
    expect((await context.luno.devices.get(device.id)).online).toBe(true);
  });

  it('treats a device that has gone quiet as offline', async () => {
    const context = harness();
    const { device } = await connectedDevice(context);
    context.advance(600_000);

    expect((await context.luno.devices.get(device.id)).online).toBe(false);
  });
});

describe('resilience', () => {
  it.each([
    ['malformed text', 'not a frame at all'],
    ['a frame from a newer peer', JSON.stringify({ v: 1, kind: 'event', id: 'e', ts: 't', deviceId: 'd', type: 'ussd_response', seq: 1, payload: {} })],
    ['an unexpected command', JSON.stringify({ v: 1, kind: 'command', id: 'c', ts: 't', deviceId: 'd', type: 'send_sms', seq: 1, payload: { to: '+1', body: 'x' } })],
  ])('quarantines %s without dropping the connection', async (_label, raw) => {
    const context = harness();
    const { session, recorder } = await connectedDevice(context);

    await expect(session.receive(raw)).resolves.toBeUndefined();
    expect(recorder.closed).toHaveLength(0);

    // Still healthy afterwards.
    await session.receive(nodeControl({ type: 'ping' }));
    expect(recorder.sent.at(-1)).toBeDefined();
  });

  it('marks the device offline when the socket closes', async () => {
    const context = harness();
    const offline: unknown[] = [];
    context.luno.on('device.offline', (payload) => {
      offline.push(payload);
    });

    const { session, device } = await connectedDevice(context);
    await session.close();
    await session.close();

    expect(offline).toHaveLength(1);
    expect((await context.store.devices.findById(device.id))?.phase).toBe('offline');
  });

  it('keeps a hook failure from breaking the frame loop', async () => {
    const context = harness();
    context.luno.on('device.heartbeat', () => {
      throw new Error('webhook exploded');
    });

    const { session, recorder } = await connectedDevice(context);
    await expect(
      session.receive(nodeEvent({ type: 'heartbeat', queueDepth: 0 }, 4, 'evt_x')),
    ).resolves.toBeUndefined();

    const ack = recorder.sent.at(-1);
    expect(ack && (bodyOf(ack)['ack'] as Record<string, unknown>)['ackedId']).toBe('evt_x');
  });
});

describe('authorization', () => {
  it('refuses an unknown or revoked credential', async () => {
    const context = harness();
    const { credential, device } = await connectedDevice(context);

    expect(await context.luno.connections.authorize('nonsense')).toBeNull();
    expect(await context.luno.connections.authorize('')).toBeNull();

    await context.luno.devices.revoke(device.id);
    expect(await context.luno.connections.authorize(credential)).toBeNull();
  });
});
