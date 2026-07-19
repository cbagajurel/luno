import type { PairingPolicy } from './domain/pairing-session';
import type { HookBus } from './hooks';
import type { Clock, CryptoPort, IdGenerator, Logger } from './ports/runtime';
import type { SessionRegistry } from './ports/sessions';
import type { LunoStore } from './ports/store';

/** Everything the services share, resolved once by `createLuno`. */
export interface CoreContext {
  store: LunoStore;
  crypto: CryptoPort;
  clock: Clock;
  ids: IdGenerator;
  sessions: SessionRegistry;
  logger: Logger;
  hooks: HookBus;
  secret: string;
  policy: PairingPolicy;
  presenceTimeoutMs: number;
  wsUrl: string | null;
  pendingRetryAfterMs: number;
}

export const auditEvent = (
  context: CoreContext,
  entry: {
    deviceId: string | null;
    direction: 'in' | 'out' | 'system';
    kind: string;
    type: string;
    payload?: unknown;
    frameId?: string | null;
  },
): Promise<void> =>
  context.store.events.append({
    id: context.ids.newId('evt'),
    deviceId: entry.deviceId,
    direction: entry.direction,
    kind: entry.kind,
    type: entry.type,
    payload: entry.payload ?? null,
    frameId: entry.frameId ?? null,
    at: context.clock.now(),
  });
