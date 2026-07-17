import { EventEmitter } from 'node:events';
import { randomBytes, randomUUID } from 'node:crypto';

const PAIRING_TTL_MS = 10 * 60 * 1000;
const MAX_EVENTS = 400;

// The Luno node treats these enroll status codes as "pairing code rejected"
// (RestClient.REJECTED_CODES). We reuse them so the app shows a clean error.
export const REJECT_CODES = { INVALID: 401, USED: 409, EXPIRED: 410 };

function createHub() {
  const emitter = new EventEmitter();
  emitter.setMaxListeners(0);

  const pairingCodes = new Map(); // code -> { code, createdAt, expiresAt, used, deviceId }
  const devices = new Map(); // deviceId -> device
  const byCredential = new Map(); // credential -> deviceId
  const events = []; // ring buffer of the most recent frames/actions

  const emitChange = () => emitter.emit('change');

  function recordEvent(entry) {
    const row = { id: randomUUID(), at: Date.now(), ...entry };
    events.push(row);
    if (events.length > MAX_EVENTS) events.splice(0, events.length - MAX_EVENTS);
    emitChange();
    return row;
  }

  function createPairingCode() {
    const code = String(Math.floor(100000 + Math.random() * 900000));
    const now = Date.now();
    pairingCodes.set(code, { code, createdAt: now, expiresAt: now + PAIRING_TTL_MS, used: false, deviceId: null });
    emitChange();
    return code;
  }

  function consumePairingCode(code, info) {
    const entry = pairingCodes.get(String(code || '').trim());
    if (!entry) throw { status: REJECT_CODES.INVALID, msg: 'invalid pairing code' };
    if (entry.used) throw { status: REJECT_CODES.USED, msg: 'pairing code already used' };
    if (Date.now() > entry.expiresAt) throw { status: REJECT_CODES.EXPIRED, msg: 'pairing code expired' };

    const deviceId = 'dev_' + randomBytes(6).toString('hex');
    const credential = randomBytes(32).toString('base64url');
    entry.used = true;
    entry.deviceId = deviceId;

    devices.set(deviceId, {
      deviceId,
      credential,
      info: info || null,
      state: 'ENROLLED',
      ws: null,
      pairedAt: Date.now(),
      connectedAt: null,
      lastSeen: null,
      heartbeat: null,
      status: null,
      resync: null,
      seq: 0,
    });
    byCredential.set(credential, deviceId);
    recordEvent({ deviceId, direction: 'system', kind: 'system', type: 'enrolled', payload: { info } });
    return { deviceId, credential };
  }

  const authorize = (credential) => {
    const id = byCredential.get(credential);
    return id ? devices.get(id) : null;
  };

  function attachSocket(device, ws) {
    device.ws = ws;
    device.state = 'CONNECTED';
    device.connectedAt = Date.now();
    device.lastSeen = Date.now();
    emitChange();
  }

  function detachSocket(device) {
    device.ws = null;
    device.state = 'DISCONNECTED';
    emitChange();
  }

  function setState(device, state) {
    device.state = state;
    device.lastSeen = Date.now();
    emitChange();
  }

  const touch = (device) => {
    device.lastSeen = Date.now();
  };

  function setHeartbeat(device, payload) {
    device.heartbeat = { ...payload, at: Date.now() };
    device.lastSeen = Date.now();
    emitChange();
  }

  function setStatus(device, payload) {
    device.status = { ...payload, at: Date.now() };
    device.lastSeen = Date.now();
    emitChange();
  }

  function onNodeAck(device, ackedId) {
    if (!ackedId) return;
    const cmd = [...events].reverse().find((e) => e.frameId === ackedId && e.direction === 'out');
    if (cmd) cmd.acked = true;
  }

  function sendCommand(deviceId, type, payload) {
    const device = devices.get(deviceId);
    if (!device) throw { status: 404, msg: 'unknown device' };
    if (!device.ws || device.ws.readyState !== 1) throw { status: 409, msg: 'device is offline' };

    const id = randomUUID();
    device.seq += 1;
    const envelope = {
      v: 1,
      kind: 'command',
      id,
      ts: new Date().toISOString(),
      deviceId,
      type,
      seq: device.seq,
      payload: payload || {},
    };
    device.ws.send(JSON.stringify(envelope));
    recordEvent({ deviceId, direction: 'out', kind: 'command', type, payload, frameId: id, acked: false });
    return { id };
  }

  function publicDevice(d) {
    return {
      deviceId: d.deviceId,
      info: d.info,
      state: d.state,
      online: !!(d.ws && d.ws.readyState === 1),
      pairedAt: d.pairedAt,
      connectedAt: d.connectedAt,
      lastSeen: d.lastSeen,
      heartbeat: d.heartbeat,
      status: d.status,
      resync: d.resync,
      seq: d.seq,
    };
  }

  function snapshot() {
    return {
      devices: [...devices.values()].map(publicDevice),
      events: events.slice(-120),
      pairingCodes: [...pairingCodes.values()]
        .filter((c) => !c.used && Date.now() < c.expiresAt)
        .map((c) => ({ code: c.code, expiresAt: c.expiresAt })),
    };
  }

  function subscribe(cb) {
    emitter.on('change', cb);
    return () => emitter.off('change', cb);
  }

  return {
    createPairingCode,
    consumePairingCode,
    authorize,
    attachSocket,
    detachSocket,
    setState,
    touch,
    setHeartbeat,
    setStatus,
    onNodeAck,
    recordEvent,
    sendCommand,
    snapshot,
    subscribe,
  };
}

// One hub per process, pinned to globalThis so the custom server (server.mjs)
// and Next's bundled route handlers share the exact same instance even when
// their module graphs differ.
export function getHub() {
  if (!globalThis.__LUNO_HUB__) globalThis.__LUNO_HUB__ = createHub();
  return globalThis.__LUNO_HUB__;
}
