import { PGlite } from '@electric-sql/pglite';
import type { DeviceInfo } from '@luno/protocol';
import { describe, expect, it } from 'vitest';
import { postgresStore } from '../src/store';
import { migrate, type Queryable } from '../src/sql';

async function freshStore() {
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

const info: DeviceInfo = {
  model: 'Pixel 7',
  manufacturer: 'Google',
  androidSdk: 34,
  appVersion: '0.1.0',
  installId: 'install-1',
  platform: 'android',
};

describe('postgresStore hydration', () => {
  it('round-trips a device, preserving nested info and numeric timestamps', async () => {
    const store = await freshStore();
    await store.devices.create({
      id: 'dev_1',
      sessionId: 'ses_1',
      credentialHash: 'cred-hash',
      installId: 'install-1',
      info,
      status: 'active',
      phase: 'connected',
      pairedAt: 1_700_000_000_123,
      lastSeenAt: null,
      revokedAt: null,
    });

    const found = await store.devices.findByCredentialHash('cred-hash');
    expect(found?.id).toBe('dev_1');
    expect(found?.pairedAt).toBe(1_700_000_000_123);
    expect(typeof found?.pairedAt).toBe('number');
    expect(found?.info).toEqual(info);
    expect(found?.lastSeenAt).toBeNull();

    await store.devices.update('dev_1', { phase: 'ready', lastSeenAt: 1_700_000_001_000 });
    const updated = await store.devices.findById('dev_1');
    expect(updated?.phase).toBe('ready');
    expect(updated?.lastSeenAt).toBe(1_700_000_001_000);
    // An absent patch key must leave its column untouched.
    expect(updated?.status).toBe('active');
  });

  it('stores and reads back message parts as JSON', async () => {
    const store = await freshStore();
    await store.messages.create({
      id: 'msg_1',
      deviceId: 'dev_1',
      to: '+15550001111',
      body: 'hello',
      subscriptionId: 1,
      ref: 'ref-1',
      deliveryReport: true,
      status: 'pending',
      commandId: 'cmd_1',
      nodeMessageId: null,
      parts: [],
      error: null,
      createdAt: 1,
      updatedAt: 1,
    });

    await store.messages.update('msg_1', {
      status: 'sent',
      nodeMessageId: 'node-msg-9',
      parts: [{ index: 0, status: 'SENT', errorCode: null }],
      updatedAt: 2,
    });

    const found = await store.messages.findByNodeMessageId('dev_1', 'node-msg-9');
    expect(found?.id).toBe('msg_1');
    expect(found?.parts).toEqual([{ index: 0, status: 'SENT', errorCode: null }]);
    expect(found?.subscriptionId).toBe(1);
    expect(found?.deliveryReport).toBe(true);
  });

  it('orders the event feed newest-first, stable within a millisecond', async () => {
    const store = await freshStore();
    for (const id of ['evt_a', 'evt_b', 'evt_c']) {
      await store.events.append({
        id,
        deviceId: 'dev_1',
        direction: 'in',
        kind: 'event',
        type: 'heartbeat',
        payload: { seq: id },
        frameId: null,
        at: 1_700_000_000_000,
      });
    }

    const feed = await store.events.list({ deviceId: 'dev_1', limit: 10 });
    expect(feed.map((event) => event.id)).toEqual(['evt_c', 'evt_b', 'evt_a']);
    expect(feed[0]?.payload).toEqual({ seq: 'evt_c' });
  });
});
