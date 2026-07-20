import {
  controlFrame,
  encodeFrame,
  eventFrame,
  type Control,
  type DeviceInfo,
  type EnrollRequest,
  type Event,
  type ProtocolFrame,
} from '@luno-oss/protocol';
import { createLuno, memoryStore, type LunoConfig, type LunoStore } from '../src';
import type { FrameSink } from '../src';

export const TEST_SECRET = 'test-secret-value-long-enough';

export const deviceInfo = (installId = 'install-1'): DeviceInfo => ({
  model: 'Pixel 8',
  manufacturer: 'Google',
  androidSdk: 34,
  appVersion: '1.0.0',
  installId,
  platform: 'android',
});

export const enrollRequest = (
  pairingCode: string,
  overrides: Partial<EnrollRequest> = {},
): EnrollRequest => ({
  pairingCode,
  nonce: 'nonce-1',
  protocolVersion: 1,
  deviceInfo: deviceInfo(),
  ...overrides,
});

/**
 * A deterministic clock and counter ids so assertions can be exact. Crypto stays
 * real — the digests are what the code under test actually depends on.
 */
export function harness(overrides: Partial<LunoConfig> = {}) {
  let now = 1_700_000_000_000;
  let counter = 0;
  const store: LunoStore = overrides.store ?? memoryStore();

  const luno = createLuno({
    store,
    secret: TEST_SECRET,
    clock: { now: () => now },
    ids: { newId: (prefix) => `${prefix}_${(counter += 1)}` },
    ...overrides,
  });

  return {
    luno,
    store,
    now: () => now,
    advance: (ms: number) => {
      now += ms;
    },
  };
}

export function recordingSink(): { sent: ProtocolFrame[]; closed: boolean[]; sink: FrameSink } {
  const sent: ProtocolFrame[] = [];
  const closed: boolean[] = [];
  return {
    sent,
    closed,
    sink: {
      send: async (frame) => {
        sent.push(frame);
      },
      close: async () => {
        closed.push(true);
      },
    },
  };
}

const nodeMeta = (seq: number) => ({
  id: `node_frame_${seq}`,
  ts: '2026-07-19T09:00:00Z',
  deviceId: 'dev_node',
  seq,
});

export const nodeControl = (control: Control, seq = 1): string =>
  encodeFrame(controlFrame(nodeMeta(seq), control));

export const nodeEvent = (event: Event, seq = 1, id?: string): string =>
  encodeFrame(eventFrame({ ...nodeMeta(seq), ...(id ? { id } : {}) }, event));

/** Drives the §6 handshake the way a real node does: negotiate, then resync. */
export async function handshake(session: { receive(raw: string): Promise<void> }): Promise<void> {
  await session.receive(nodeControl({ type: 'version_negotiate', supported: [1] }, 1));
  await session.receive(
    nodeControl({ type: 'resync', lastAckedInboundSeq: 0, outstandingOutboxIds: [] }, 2),
  );
}

/** Enrols a device and opens a connected session for it, the common test setup. */
export async function connectedDevice(
  context: ReturnType<typeof harness>,
  installId = 'install-1',
) {
  const { code } = await context.luno.pairing.createSession();
  const outcome = await context.luno.pairing.enroll(
    enrollRequest(code, { deviceInfo: deviceInfo(installId) }),
  );
  if (outcome.status !== 'approved') throw new Error(`enrol failed: ${outcome.status}`);

  const device = await context.luno.connections.authorize(outcome.credential);
  if (!device) throw new Error('credential did not authorize');

  const recorder = recordingSink();
  const session = await context.luno.connections.open(device, recorder.sink);
  return { device, session, recorder, credential: outcome.credential };
}
