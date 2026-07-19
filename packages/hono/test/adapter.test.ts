import { serve } from '@hono/node-server';
import { createNodeWebSocket } from '@hono/node-ws';
import { PGlite } from '@electric-sql/pglite';
import { createLuno, memoryStore, type Luno, type LunoStore } from '@luno/core';
import { migrate, postgresStore, type Queryable } from '@luno/store-postgres';
import {
  FakeNode,
  enrollNode,
  fetchTransport,
  webSocketChannel,
} from '@luno/testing';
import { Hono } from 'hono';
import type { AddressInfo } from 'node:net';
import WebSocket from 'ws';
import { afterEach, describe, expect, it } from 'vitest';
import { registerLuno } from '../src/index';

const SECRET = 'phase-4-hono-adapter-secret';

async function pgliteStore(): Promise<LunoStore> {
  const db = new PGlite();
  const sql: Queryable = {
    async query(text, params) {
      const result = await db.query(text, params ? [...params] : []);
      return { rows: result.rows as Record<string, unknown>[] };
    },
  };
  await migrate(sql);
  return postgresStore(sql);
}

interface Running {
  luno: Luno;
  baseUrl: string;
  wsUrl: string;
  close: () => Promise<void>;
}

const teardown: Array<() => Promise<void>> = [];
afterEach(async () => {
  while (teardown.length) await teardown.pop()!();
}, 15_000);

async function start(store: LunoStore): Promise<Running> {
  const luno = createLuno({ store, secret: SECRET });
  const app = new Hono();
  const { injectWebSocket, upgradeWebSocket } = createNodeWebSocket({ app });
  registerLuno(app, { luno, upgradeWebSocket });

  const server = await new Promise<ReturnType<typeof serve>>((resolve) => {
    const s = serve({ fetch: app.fetch, port: 0 }, () => resolve(s));
  });
  injectWebSocket(server);
  const port = (server.address() as AddressInfo).port;

  const running: Running = {
    luno,
    baseUrl: `http://localhost:${port}`,
    wsUrl: `ws://localhost:${port}/ws`,
    close: () =>
      new Promise<void>((resolve) => {
        (server as { closeAllConnections?: () => void }).closeAllConnections?.();
        server.close(() => resolve());
      }),
  };
  teardown.push(running.close);
  return running;
}

async function connectNode(run: Running, credential: string, deviceId: string): Promise<FakeNode> {
  const ws = new WebSocket(run.wsUrl, { headers: { Authorization: `Bearer ${credential}` } });
  // Close the socket before the server, or server.close() blocks on the live connection.
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

async function pairAndConnect(run: Running): Promise<{ node: FakeNode; deviceId: string }> {
  const { code } = await run.luno.pairing.createSession({ backendUrl: run.baseUrl });
  const enrolled = await enrollNode(fetchTransport(run.baseUrl), code);
  const node = await connectNode(run, enrolled.credential, enrolled.deviceId);
  return { node, deviceId: enrolled.deviceId };
}

describe('@luno/hono adapter over a real socket', () => {
  it('runs a full lifecycle: enroll, handshake, send → delivered', async () => {
    const run = await start(memoryStore());
    const { node, deviceId } = await pairAndConnect(run);

    expect(node.isReady()).toBe(true);

    const message = await run.luno.sms.send({ deviceId, to: '+15550001111', body: 'hello node' });
    await node.waitFor(
      (frame) => frame.body.kind === 'command' && frame.body.command.type === 'send_sms',
    );
    const delivered = await waitForStatus(run, deviceId, message.id, 'delivered');
    expect(delivered.status).toBe('delivered');
  });

  it('reflects an inbound SMS the node reports', async () => {
    const run = await start(memoryStore());
    const { node, deviceId } = await pairAndConnect(run);

    await node.sendInboundSms({ from: '+15557778888', body: 'ping' });

    // The event ack the node awaited means the backend has recorded it.
    const events = await run.luno.events({ deviceId });
    expect(events.some((event) => event.type === 'sms_received')).toBe(true);
  });

  it('rejects an unknown credential with HTTP 401, before upgrading', async () => {
    const run = await start(memoryStore());
    const ws = new WebSocket(run.wsUrl, { headers: { Authorization: 'Bearer not-a-real-credential' } });

    const closeCode = await new Promise<number>((resolve, reject) => {
      ws.on('open', () => reject(new Error('socket should not have opened')));
      ws.on('unexpected-response', (_req, res) => resolve(res.statusCode ?? 0));
      ws.on('error', reject);
    });
    expect(closeCode).toBe(401);
  });

  it('composes with the durable Postgres store end to end', async () => {
    const run = await start(await pgliteStore());
    const { node, deviceId } = await pairAndConnect(run);

    const message = await run.luno.sms.send({ deviceId, to: '+15550002222', body: 'via postgres' });
    await waitForStatus(run, deviceId, message.id, 'delivered');

    // The credential lookup, the message and its status transitions all round-tripped
    // through Postgres — the adapter and the durable store compose unchanged.
    const persisted = await run.luno.sms.list(deviceId);
    expect(persisted.find((record) => record.id === message.id)?.status).toBe('delivered');
    expect(node.isReady()).toBe(true);
  });
});
