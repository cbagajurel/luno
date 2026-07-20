import { EventEmitter } from 'node:events';
import { randomBytes } from 'node:crypto';
import { createLuno, memoryStore } from '@luno-oss/core';

// The node phase the core tracks (lowercase) → the label the dashboard renders.
const PHASE_LABEL = {
  offline: 'DISCONNECTED',
  connected: 'CONNECTED',
  authenticated: 'AUTHENTICATED',
  ready: 'READY',
};

/**
 * Everything the demo dashboard needs, projected from the SDK. The core owns all
 * state; this layer only *reads* it and caches the two things the SDK emits but
 * does not store — the latest heartbeat and device_status per device — plus the
 * plaintext pairing codes, which the core deliberately never persists (it keeps
 * only a hash). Holding freshly minted codes in memory for the countdown UI is a
 * demo concern that belongs in the adapter, not the engine.
 */
function createBackend() {
  const emitter = new EventEmitter();
  emitter.setMaxListeners(0);
  const change = () => emitter.emit('change');

  const heartbeats = new Map(); // deviceId -> last heartbeat payload
  const statuses = new Map(); // deviceId -> last device_status payload
  const connectedAt = new Map(); // deviceId -> ms
  const codes = new Map(); // sessionId -> { code, expiresAt }

  const luno = createLuno({
    store: memoryStore(),
    // A per-process secret is fine for a throwaway demo; a real deployment sets a
    // durable one so credentials survive a restart.
    secret: process.env.LUNO_SECRET || randomBytes(24).toString('base64url'),
    logger: {
      log: (level, message, fields) => {
        if (level === 'error') console.error('[luno]', message, fields ?? '');
      },
    },
  });

  luno.on('device.heartbeat', ({ deviceId, heartbeat }) => {
    heartbeats.set(deviceId, { ...heartbeat, at: Date.now() });
    change();
  });
  luno.on('device.status', ({ deviceId, status }) => {
    statuses.set(deviceId, { ...status, at: Date.now() });
    change();
  });
  luno.on('device.online', ({ deviceId }) => {
    connectedAt.set(deviceId, Date.now());
    change();
  });
  for (const event of [
    'device.offline',
    'device.enrolled',
    'device.revoked',
    'enrollment.pending',
    'sms.status',
    'sms.received',
  ]) {
    luno.on(event, change);
  }

  function rememberCode(session, code, qrUri = null) {
    codes.set(session.id, {
      code,
      qrUri,
      expiresAt: session.expiresAt,
      maxEnrollments: session.maxEnrollments,
      requireApproval: session.requireApproval,
    });
    change();
  }

  async function snapshot() {
    const now = Date.now();
    const devices = (await luno.devices.list()).map((d) => ({
      deviceId: d.id,
      info: d.info,
      state: d.status === 'revoked' ? 'REVOKED' : PHASE_LABEL[d.phase] ?? 'DISCONNECTED',
      online: d.online,
      connectedAt: connectedAt.get(d.id) ?? null,
      lastSeen: d.lastSeenAt,
      heartbeat: heartbeats.get(d.id) ?? null,
      status: statuses.get(d.id) ?? null,
    }));

    const events = (await luno.events({ limit: 200 })).slice().reverse();
    const acked = new Set(
      events.filter((e) => e.kind === 'ack' && e.frameId).map((e) => e.frameId),
    );
    const feed = events.map((e) => ({
      id: e.id,
      at: e.at,
      direction: e.direction,
      type: e.type,
      deviceId: e.deviceId,
      payload: e.payload,
      acked: e.direction === 'out' && e.frameId ? acked.has(e.frameId) : undefined,
    }));

    const pairingCodes = [...codes.values()]
      .filter((c) => c.expiresAt === null || c.expiresAt > now)
      .map((c) => ({
        code: c.code,
        qrUri: c.qrUri ?? null,
        expiresAt: c.expiresAt,
        maxEnrollments: c.maxEnrollments ?? null,
        requireApproval: c.requireApproval ?? false,
      }));

    const enrollments = (await luno.pairing.listEnrollments())
      .filter((e) => e.status === 'pending')
      .map((e) => ({
        id: e.id,
        installId: e.installId,
        info: e.info,
        createdAt: e.createdAt,
      }));

    return { devices, events: feed, pairingCodes, enrollments };
  }

  return {
    luno,
    rememberCode,
    refresh: change,
    snapshot,
    subscribe(cb) {
      emitter.on('change', cb);
      return () => emitter.off('change', cb);
    },
  };
}

// One instance per process, pinned to globalThis so Next's bundled route handlers
// and the custom server (server.mjs) share the exact same engine even though
// their module graphs differ.
export function getBackend() {
  if (!globalThis.__LUNO_BACKEND__) globalThis.__LUNO_BACKEND__ = createBackend();
  return globalThis.__LUNO_BACKEND__;
}

export const getLuno = () => getBackend().luno;
