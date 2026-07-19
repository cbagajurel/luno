import { createLuno, memoryStore, type Luno } from '@luno/core';
import {
  FakeNode,
  channelPair,
  enrollNode,
  type EnrollTransport,
  type NodeChannel,
} from '@luno/testing';
import { describe, expect, it } from 'vitest';
import { bridgeSocket, lunoFetch, type WorkerWebSocket } from '../src/index';

const SECRET = 'phase-5-cloudflare-adapter-secret';

// Drives the Worker fetch handler as if it were a server, so enrolment exercises the
// real POST /enroll → luno.http.handle path with fetch-native Request/Response.
function fetchHandlerTransport(handler: (request: Request) => Promise<Response>): EnrollTransport {
  return {
    async post(path, body) {
      const response = await handler(
        new Request(`http://worker.local${path}`, {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify(body),
        }),
      );
      const text = await response.text();
      return { status: response.status, body: text ? JSON.parse(text) : null };
    },
  };
}

// A WebSocketPair server half, backed by an in-memory channel instead of workerd.
function workerSocketFromChannel(channel: NodeChannel): WorkerWebSocket {
  const listeners: Record<'message' | 'close' | 'error', Array<(event?: unknown) => void>> = {
    message: [],
    close: [],
    error: [],
  };
  channel.onMessage((raw) => listeners.message.forEach((fn) => fn({ data: raw })));
  channel.onClose(() => listeners.close.forEach((fn) => fn()));
  return {
    accept() {},
    send(data) {
      void channel.send(data);
    },
    close(code, reason) {
      void channel.close(code, reason);
    },
    addEventListener(type: 'message' | 'close' | 'error', listener: (event?: unknown) => void) {
      listeners[type].push(listener);
    },
  } as WorkerWebSocket;
}

function newLuno(): Luno {
  return createLuno({ store: memoryStore(), secret: SECRET });
}

describe('@luno/cloudflare fetch handler', () => {
  it('enrols through POST /enroll on the fetch-native handler', async () => {
    const luno = newLuno();
    const handler = lunoFetch({ luno });
    const { code } = await luno.pairing.createSession({ backendUrl: 'http://worker.local' });

    const enrolled = await enrollNode(fetchHandlerTransport(handler), code);
    expect(enrolled.deviceId).toBeTruthy();
    expect(await luno.connections.authorize(enrolled.credential)).not.toBeNull();
  });

  it('rejects a ws upgrade with an unknown credential (401) and a non-upgrade GET (426)', async () => {
    const handler = lunoFetch({ luno: newLuno() });

    const unauthorized = await handler(
      new Request('http://worker.local/ws', {
        headers: { upgrade: 'websocket', authorization: 'Bearer nope' },
      }),
    );
    expect(unauthorized.status).toBe(401);

    const notUpgrade = await handler(new Request('http://worker.local/ws'));
    expect(notUpgrade.status).toBe(426);
  });
});

describe('@luno/cloudflare bridgeSocket', () => {
  it('runs the §6 handshake and send → delivered over a WebSocketPair bridge', async () => {
    const luno = newLuno();
    const handler = lunoFetch({ luno });
    const { code } = await luno.pairing.createSession({ backendUrl: 'http://worker.local' });
    const enrolled = await enrollNode(fetchHandlerTransport(handler), code);

    const device = await luno.connections.authorize(enrolled.credential);
    expect(device).not.toBeNull();

    const { node, backend } = channelPair();
    const server = workerSocketFromChannel(backend);
    const fake = new FakeNode({ deviceId: enrolled.deviceId });
    fake.attach(node);

    bridgeSocket(luno, server, device!);
    await fake.handshake();
    expect(fake.isReady()).toBe(true);

    const message = await luno.sms.send({
      deviceId: enrolled.deviceId,
      to: '+15550001111',
      body: 'hello worker',
    });
    await fake.waitFor(
      (frame) => frame.body.kind === 'command' && frame.body.command.type === 'send_sms',
    );

    let delivered = false;
    for (let attempt = 0; attempt < 100 && !delivered; attempt += 1) {
      const found = (await luno.sms.list(enrolled.deviceId)).find((m) => m.id === message.id);
      delivered = found?.status === 'delivered';
      if (!delivered) await new Promise((resolve) => setTimeout(resolve, 10));
    }
    expect(delivered).toBe(true);
  });
});
