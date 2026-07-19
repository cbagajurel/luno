import { createLuno, memoryStore, type Luno } from '@luno/core';
import { encodeFrame, type ProtocolFrame } from '@luno/protocol';
import { describe, expect, it, vi } from 'vitest';
import {
  FakeNode,
  channelPair,
  enrollNode,
  fetchTransport,
  type EnrollTransport,
} from '../src';

/**
 * Drives the real @luno/core over an in-memory channel, exactly as an adapter
 * would over a socket. If the FakeNode can pair, handshake, send and receive
 * against the core with no mocks, it is a faithful stand-in for the phone — which
 * is the whole point of shipping it.
 */
const backend = (): Luno => createLuno({ store: memoryStore(), secret: 'testing-secret-value-1234' });

/** An enroll transport that calls the core's HTTP router directly — no server needed. */
function inProcessTransport(luno: Luno): EnrollTransport {
  return {
    async post(path, body) {
      const result = await luno.http.handle({
        method: 'POST',
        url: `https://backend.test${path}`,
        json: async () => body,
      });
      return { status: result.status, body: JSON.parse(result.body) };
    },
  };
}

// The core hands the sink a ProtocolFrame; the channel carries the wire text.
const toWire = (frame: ProtocolFrame): string => encodeFrame(frame);

async function pairAndConnect(luno: Luno, installId = 'install-1'): Promise<FakeNode> {
  const { code } = await luno.pairing.createSession();
  const { deviceId, credential } = await enrollNode(inProcessTransport(luno), code, { installId });

  const node = new FakeNode({ deviceId });
  const { node: nodeSide, backend: backendSide } = channelPair();
  node.attach(nodeSide);

  const device = await luno.connections.authorize(credential);
  if (!device) throw new Error('credential did not authorize');
  const session = await luno.connections.open(device, {
    send: async (frame) => backendSide.send(toWire(frame)),
    close: async () => backendSide.close(),
  });
  backendSide.onMessage((raw) => session.receive(raw));
  backendSide.onClose(() => session.close());
  return node;
}

describe('FakeNode against the real core', () => {
  it('pairs, handshakes, and reaches READY', async () => {
    const luno = backend();
    const node = await pairAndConnect(luno);

    await node.handshake();
    expect(node.isReady()).toBe(true);
    expect((await luno.devices.get(node.deviceId)).online).toBe(true);
  });

  it('carries a send_sms all the way to delivered', async () => {
    const luno = backend();
    const node = await pairAndConnect(luno);
    await node.handshake();

    const message = await luno.sms.send({ deviceId: node.deviceId, to: '+15551234567', body: 'hi' });

    // The FakeNode auto-answers accepted → sent → delivery_report; wait on the
    // application-visible terminal state rather than sleeping.
    await vi.waitFor(async () => {
      expect((await luno.sms.get(message.id)).status).toBe('delivered');
    });
  });

  it('surfaces an inbound SMS to the backend', async () => {
    const luno = backend();
    const received: string[] = [];
    luno.on('sms.received', ({ body }) => {
      received.push(body);
    });

    const node = await pairAndConnect(luno);
    await node.handshake();
    await node.sendInboundSms({ from: '+15550001111', body: 'Reply Y to confirm' });

    expect(received).toEqual(['Reply Y to confirm']);
  });

  it('answers get_status with a device snapshot', async () => {
    const luno = backend();
    const snapshots: unknown[] = [];
    luno.on('device.status', ({ status }) => {
      snapshots.push(status);
    });

    const node = await pairAndConnect(luno);
    await node.handshake();
    await luno.devices.requestStatus(node.deviceId);

    await vi.waitFor(() => expect(snapshots).toHaveLength(1));
  });

  it('replays queued commands to a node that connects after they were sent', async () => {
    const luno = backend();
    const { code } = await luno.pairing.createSession();
    const { deviceId, credential } = await enrollNode(inProcessTransport(luno), code);

    // Queue while the device has never connected, then bring it online.
    const queued = await luno.sms.send({ deviceId, to: '+1', body: 'queued' });
    expect((await luno.sms.get(queued.id)).status).toBe('pending');

    const node = new FakeNode({ deviceId });
    const { node: nodeSide, backend: backendSide } = channelPair();
    node.attach(nodeSide);
    const device = await luno.connections.authorize(credential);
    if (!device) throw new Error('credential did not authorize');
    const session = await luno.connections.open(device, {
      send: async (frame) => backendSide.send(toWire(frame)),
      close: async () => backendSide.close(),
    });
    backendSide.onMessage((raw) => session.receive(raw));

    await node.handshake();
    await vi.waitFor(async () => {
      expect((await luno.sms.get(queued.id)).status).toBe('delivered');
    });
  });

  it('disconnects on revoke', async () => {
    const luno = backend();
    const node = await pairAndConnect(luno);
    await node.handshake();

    await luno.devices.revoke(node.deviceId);
    await vi.waitFor(() => expect(node.isReady()).toBe(false));
  });
});

describe('enrollNode error handling', () => {
  it('throws a classified error for a bad code', async () => {
    const luno = backend();
    await expect(enrollNode(inProcessTransport(luno), 'ZZZZ-9999')).rejects.toMatchObject({
      code: 'invalid_code',
    });
  });

  it('polls a pending enrolment through to approval', async () => {
    const luno = createLuno({
      store: memoryStore(),
      secret: 'testing-secret-value-1234',
      pairing: { requireApproval: true },
    });
    const { code } = await luno.pairing.createSession();

    luno.on('enrollment.pending', ({ enrollment }) => luno.pairing.approveEnrollment(enrollment.id));

    const result = await enrollNode(inProcessTransport(luno), code, { maxPolls: 5 });
    expect(result.credential).toBeTruthy();
  });

  it('builds a transport with a post method', () => {
    expect(fetchTransport('https://gw.example.com/')).toHaveProperty('post');
  });
});
