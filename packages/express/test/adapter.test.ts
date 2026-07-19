import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { createLuno, memoryStore, type Luno } from '@luno/core';
import { FakeNode, enrollNode, fetchTransport, webSocketChannel } from '@luno/testing';
import express from 'express';
import WebSocket from 'ws';
import { afterEach, describe, expect, it } from 'vitest';
import { attachLunoWebSocket, registerLunoEnroll } from '../src/index';

const SECRET = 'phase-5-express-adapter-secret';

interface Running {
  luno: Luno;
  baseUrl: string;
  wsUrl: string;
}

const teardown: Array<() => Promise<void>> = [];
afterEach(async () => {
  while (teardown.length) await teardown.pop()!();
}, 15_000);

async function start(): Promise<Running> {
  const luno = createLuno({ store: memoryStore(), secret: SECRET });
  const app = express();
  app.use(express.json());
  registerLunoEnroll(app, luno);

  const server: Server = createServer(app);
  attachLunoWebSocket(server, luno);

  await new Promise<void>((resolve) => server.listen(0, resolve));
  const port = (server.address() as AddressInfo).port;

  teardown.push(
    () =>
      new Promise<void>((resolve) => {
        (server as { closeAllConnections?: () => void }).closeAllConnections?.();
        server.close(() => resolve());
      }),
  );

  return { luno, baseUrl: `http://localhost:${port}`, wsUrl: `ws://localhost:${port}/ws` };
}

async function connectNode(run: Running, credential: string, deviceId: string): Promise<FakeNode> {
  const ws = new WebSocket(run.wsUrl, { headers: { Authorization: `Bearer ${credential}` } });
  teardown.push(
    () =>
      new Promise<void>((resolve) => {
        if (ws.readyState === WebSocket.CLOSED) return resolve();
        ws.on('close', () => resolve());
        ws.close();
      }),
  );
  await new Promise<void>((resolve, reject) => {
    ws.on('open', () => resolve());
    ws.on('error', reject);
  });
  const node = new FakeNode({ deviceId });
  node.attach(webSocketChannel(ws as unknown as Parameters<typeof webSocketChannel>[0]));
  await node.handshake();
  return node;
}

async function waitForStatus(run: Running, deviceId: string, id: string, status: string) {
  for (let attempt = 0; attempt < 100; attempt += 1) {
    const message = (await run.luno.sms.list(deviceId)).find((record) => record.id === id);
    if (message?.status === status) return message;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error(`message ${id} never reached ${status}`);
}

describe('@luno/express adapter over a real socket', () => {
  it('enrolls through the HttpRequest shim, then runs send → delivered', async () => {
    const run = await start();
    const { code } = await run.luno.pairing.createSession({ backendUrl: run.baseUrl });
    const enrolled = await enrollNode(fetchTransport(run.baseUrl), code);
    const node = await connectNode(run, enrolled.credential, enrolled.deviceId);
    expect(node.isReady()).toBe(true);

    const message = await run.luno.sms.send({
      deviceId: enrolled.deviceId,
      to: '+15550001111',
      body: 'hello express',
    });
    await node.waitFor(
      (frame) => frame.body.kind === 'command' && frame.body.command.type === 'send_sms',
    );
    const delivered = await waitForStatus(run, enrolled.deviceId, message.id, 'delivered');
    expect(delivered.status).toBe('delivered');
  });

  it('rejects an unknown credential with HTTP 401, before upgrading', async () => {
    const run = await start();
    const ws = new WebSocket(run.wsUrl, { headers: { Authorization: 'Bearer nope' } });
    const status = await new Promise<number>((resolve, reject) => {
      ws.on('open', () => reject(new Error('socket should not have opened')));
      ws.on('unexpected-response', (_req, res) => resolve(res.statusCode ?? 0));
      ws.on('error', reject);
    });
    expect(status).toBe(401);
  });
});
