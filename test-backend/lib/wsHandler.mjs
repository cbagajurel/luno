import { randomUUID } from 'node:crypto';

const PROTOCOL_VERSION = 1;
const APP_PING_INTERVAL_MS = 25000;

/**
 * Validate the WSS upgrade the same way the node expects (§8, threat model): the
 * device credential rides in `Authorization: Bearer <credential>`. Unknown/absent
 * credential -> caller returns 401, which the node's ConnectionManager treats as
 * "re-enroll required" and pauses (it will not reconnect-loop).
 */
export function authorizeUpgrade(req, hub) {
  const header = req.headers['authorization'] || '';
  const match = /^Bearer\s+(.+)$/i.exec(header);
  if (!match) return { ok: false };
  const device = hub.authorize(match[1].trim());
  if (!device) return { ok: false };
  return { ok: true, device };
}

/**
 * Drive one node connection through the §6 handshake and then relay frames.
 *
 * Handshake (node's ConnectionManager reaches READY in three steps):
 *   1. node -> version_negotiate   ; we reply version_negotiate  -> node AUTHENTICATED
 *   2. node -> resync              ; we reply ack(resync.id)      -> node READY
 * After READY the node streams events; we ack every event frame by its stable id
 * so its durable outbox drains (unknown ids are a harmless no-op on the node).
 */
export function handleConnection(ws, device, hub) {
  hub.attachSocket(device, ws);
  hub.recordEvent({ deviceId: device.deviceId, direction: 'system', kind: 'system', type: 'socket_open' });

  let seq = 0;
  const send = (kind, type, payload) => {
    if (ws.readyState !== 1) return null;
    seq += 1;
    const id = randomUUID();
    ws.send(
      JSON.stringify({
        v: PROTOCOL_VERSION,
        kind,
        id,
        ts: new Date().toISOString(),
        deviceId: device.deviceId,
        type,
        seq,
        payload: payload || {},
      }),
    );
    return id;
  };
  const ack = (ackedId) => send('ack', 'ack', { ackedId });

  const pinger = setInterval(() => send('control', 'ping', {}), APP_PING_INTERVAL_MS);

  ws.on('message', (raw) => {
    let env;
    try {
      env = JSON.parse(raw.toString());
    } catch {
      return;
    }
    if (!env || typeof env !== 'object') return;
    hub.touch(device);
    routeFrame(env);
  });

  function routeFrame(env) {
    const { kind, type, id, payload } = env;
    switch (kind) {
      case 'control':
        return onControl(type, id, payload);
      case 'ack':
        hub.onNodeAck(device, payload?.ackedId);
        hub.recordEvent({ deviceId: device.deviceId, direction: 'in', kind: 'ack', type: 'ack', payload });
        return;
      case 'event':
        return onEvent(type, id, payload);
      default:
        return;
    }
  }

  function onControl(type, id, payload) {
    switch (type) {
      case 'version_negotiate':
        hub.setState(device, 'AUTHENTICATED');
        send('control', 'version_negotiate', { supported: [PROTOCOL_VERSION], selected: PROTOCOL_VERSION });
        return;
      case 'resync':
        device.resync = payload || null;
        hub.setState(device, 'READY');
        hub.recordEvent({ deviceId: device.deviceId, direction: 'in', kind: 'control', type: 'resync', payload });
        ack(id); // node goes READY on an ack OR a resync reply
        return;
      case 'ping':
        send('control', 'pong', {});
        return;
      case 'pong':
        return;
      default:
        return;
    }
  }

  function onEvent(type, id, payload) {
    if (type === 'heartbeat') hub.setHeartbeat(device, payload);
    if (type === 'device_status') hub.setStatus(device, payload);
    hub.recordEvent({ deviceId: device.deviceId, direction: 'in', kind: 'event', type, payload, frameId: id });
    ack(id);
  }

  ws.on('close', () => {
    clearInterval(pinger);
    hub.detachSocket(device);
    hub.recordEvent({ deviceId: device.deviceId, direction: 'system', kind: 'system', type: 'socket_close' });
  });

  ws.on('error', () => {
    clearInterval(pinger);
  });
}
