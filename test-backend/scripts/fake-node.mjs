#!/usr/bin/env node
//
// A stand-in for the Android node: enrolls with a pairing code, connects over
// WebSocket, runs the exact §6 handshake the real ConnectionManager runs, sends
// heartbeats, and answers commands with the same event flow as the real agent.
//
// Use it to demo the backend end-to-end without a phone:
//   node scripts/fake-node.mjs http://localhost:3000 123456
//
import { WebSocket } from 'ws';
import { randomUUID } from 'node:crypto';

const [, , backendArg, pairingCode] = process.argv;
if (!backendArg || !pairingCode) {
  console.error('usage: node scripts/fake-node.mjs <backendUrl> <pairingCode>');
  process.exit(1);
}
const backendUrl = backendArg.replace(/\/+$/, '');

const deviceInfo = {
  model: 'Pixel 7 (fake)',
  manufacturer: 'Google',
  androidSdk: 34,
  appVersion: '0.1.0-sim',
};

const log = (...a) => console.log(new Date().toLocaleTimeString([], { hour12: false }), ...a);

async function main() {
  log('POST', `${backendUrl}/enroll`);
  const res = await fetch(`${backendUrl}/enroll`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ pairingCode, deviceInfo }),
  });
  if (!res.ok) {
    const t = await res.text();
    log('enroll failed', res.status, t);
    process.exit(1);
  }
  const { deviceId, credential, wsUrl } = await res.json();
  log('enrolled as', deviceId);

  // Locally the backend hands back wss://…; over plain http we connect to ws://.
  const connectUrl = backendUrl.startsWith('https')
    ? wsUrl
    : backendUrl.replace(/^http/, 'ws') + '/ws';
  log('connecting', connectUrl);

  const ws = new WebSocket(connectUrl, { headers: { Authorization: `Bearer ${credential}` } });
  let seq = 0;
  let state = 'CONNECTING';
  let hbTimer = null;

  const send = (kind, type, payload) => {
    seq += 1;
    const id = randomUUID();
    ws.send(
      JSON.stringify({ v: 1, kind, id, ts: new Date().toISOString(), deviceId, type, seq, payload: payload || {} }),
    );
    return id;
  };
  const ack = (ackedId) => send('ack', 'ack', { ackedId });

  ws.on('open', () => {
    state = 'CONNECTED';
    log('socket open → version_negotiate');
    send('control', 'version_negotiate', { supported: [1] });
  });

  ws.on('message', (raw) => {
    const env = JSON.parse(raw.toString());
    const { kind, type, id, payload } = env;

    if (kind === 'control' && type === 'version_negotiate' && state === 'CONNECTED') {
      state = 'AUTHENTICATED';
      log('← version_negotiate → resync');
      send('control', 'resync', { lastAckedInboundSeq: 0, outstandingOutboxIds: [] });
      return;
    }
    if (state === 'AUTHENTICATED' && (kind === 'ack' || (kind === 'control' && type === 'resync'))) {
      state = 'READY';
      log('READY ✓  (heartbeats every 10s)');
      startHeartbeat();
      return;
    }
    if (kind === 'control' && type === 'ping') {
      send('control', 'pong', {});
      return;
    }
    if (kind === 'command') {
      handleCommand(type, id, payload);
      return;
    }
    if (kind === 'ack') return;
  });

  function startHeartbeat() {
    const emit = () =>
      send('event', 'heartbeat', {
        queueDepth: 0,
        battery: 87,
        signals: [{ subscriptionId: 1, dbm: -91, level: 3 }],
        transports: ['sms'],
      });
    emit();
    hbTimer = setInterval(emit, 10000);
  }

  function handleCommand(type, frameId, payload) {
    log('← command', type, JSON.stringify(payload));
    ack(frameId);
    if (type === 'send_sms') {
      const messageId = randomUUID();
      send('event', 'sms_accepted', { commandId: frameId, messageId });
      setTimeout(() => {
        send('event', 'sms_sent', { messageId, parts: [{ index: 0, status: 'ok' }] });
        if (payload?.deliveryReport !== false) {
          setTimeout(
            () => send('event', 'delivery_report', { messageId, part: 0, status: 'delivered', at: Date.now() }),
            1200,
          );
        }
      }, 700);
    } else if (type === 'get_status') {
      send('event', 'device_status', {
        battery: { levelPercent: 87, charging: true, plugged: 'usb', health: 'good' },
        network: { connected: true, validated: true, transport: 'wifi', metered: false },
        sims: [
          {
            subscriptionId: 1,
            slotIndex: 0,
            carrierName: 'FakeCarrier',
            displayName: 'SIM 1',
            embedded: false,
            simState: 'ready',
          },
        ],
      });
    } else if (type === 'revoke' || type === 'wipe') {
      log(`${type} received → resetting, disconnecting`);
      if (hbTimer) clearInterval(hbTimer);
      setTimeout(() => ws.close(), 300);
    }
  }

  // Simulate an inbound SMS every 30s so sms_received shows up in the feed.
  const inbound = setInterval(() => {
    if (state !== 'READY') return;
    send('event', 'sms_received', {
      from: '+15550001111',
      body: 'Reply Y to confirm',
      subscriptionId: 1,
      receivedAt: Date.now(),
      parts: 1,
    });
  }, 30000);

  ws.on('close', () => {
    if (hbTimer) clearInterval(hbTimer);
    clearInterval(inbound);
    log('socket closed');
    process.exit(0);
  });
  ws.on('error', (e) => log('ws error', e.message));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
