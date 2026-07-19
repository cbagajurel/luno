#!/usr/bin/env node
//
// A stand-in for the Android node, built on @luno/testing's FakeNode: it enrolls
// with a pairing code, connects over a real WebSocket, runs the exact §6
// handshake the ConnectionManager runs, heartbeats, and answers commands with the
// same event flow as the agent. Use it to demo the backend without a phone:
//
//   node scripts/fake-node.mjs http://localhost:3000 ABCD-1234
//
import { WebSocket } from 'ws';
import { FakeNode, enrollNode, fetchTransport, webSocketChannel } from '@luno/testing';

const [, , backendArg, pairingCode] = process.argv;
if (!backendArg || !pairingCode) {
  console.error('usage: node scripts/fake-node.mjs <backendUrl> <pairingCode>');
  process.exit(1);
}
const backendUrl = backendArg.replace(/\/+$/, '');
const log = (...a) => console.log(new Date().toLocaleTimeString([], { hour12: false }), ...a);

async function main() {
  log('POST', `${backendUrl}/enroll`);
  const { deviceId, credential, wsUrl } = await enrollNode(
    fetchTransport(backendUrl),
    pairingCode,
    { installId: `fake-${process.pid}`, sleep: (ms) => new Promise((r) => setTimeout(r, ms)) },
  );
  log('enrolled as', deviceId);

  // Over plain http the backend hands back wss://…; connect to ws:// instead.
  const connectUrl = backendUrl.startsWith('https')
    ? wsUrl ?? backendUrl.replace(/^https/, 'wss') + '/ws'
    : backendUrl.replace(/^http/, 'ws') + '/ws';
  log('connecting', connectUrl);

  const ws = new WebSocket(connectUrl, { headers: { Authorization: `Bearer ${credential}` } });
  const node = new FakeNode({ deviceId, onLog: (m) => log(m) });

  ws.on('open', async () => {
    node.attach(webSocketChannel(ws));
    await node.handshake();
    log('READY ✓  (heartbeats every 10s)');

    await node.heartbeat();
    const beat = setInterval(() => node.heartbeat().catch(() => {}), 10_000);
    const inbound = setInterval(() => {
      if (node.isReady()) {
        node.sendInboundSms({ from: '+15550001111', body: 'Reply Y to confirm' }).catch(() => {});
      }
    }, 30_000);
    ws.on('close', () => {
      clearInterval(beat);
      clearInterval(inbound);
    });
  });

  ws.on('close', () => {
    log('socket closed');
    process.exit(0);
  });
  ws.on('error', (e) => log('ws error', e.message));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
