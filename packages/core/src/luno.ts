import type { CoreContext } from './context';
import { DEFAULT_PRESENCE_TIMEOUT_MS } from './domain/device';
import { DEFAULT_PAIRING_POLICY, type PairingPolicy } from './domain/pairing-session';
import { hookBus, type HookBus } from './hooks';
import { httpRouter } from './http/router';
import type { Clock, CryptoPort, IdGenerator, Logger } from './ports/runtime';
import { silentLogger } from './ports/runtime';
import type { SessionRegistry } from './ports/sessions';
import type { LunoStore } from './ports/store';
import { localSessionRegistry } from './runtime/local-sessions';
import { systemClock, tokenIdGenerator, webCrypto } from './runtime/webcrypto';
import { connectionsService } from './services/connections';
import { devicesService } from './services/devices';
import { messagingService } from './services/messaging';
import { pairingService } from './services/pairing';

export interface LunoConfig {
  /** Persistence. Required — defaulting it would make losing data the easy path. */
  store: LunoStore;
  /**
   * Key for the pairing-code and credential digests. Rotating it invalidates
   * every issued code and credential, so treat it as durable deployment state.
   */
  secret: string;
  pairing?: Partial<PairingPolicy>;
  /** Advertised to nodes on enrolment; when absent they derive it from the enrolment host. */
  wsUrl?: string;
  presenceTimeoutMs?: number;
  pendingRetryAfterMs?: number;
  crypto?: CryptoPort;
  clock?: Clock;
  ids?: IdGenerator;
  sessions?: SessionRegistry;
  logger?: Logger;
}

const MIN_SECRET_LENGTH = 16;

export function createLuno(config: LunoConfig) {
  if (!config.secret || config.secret.length < MIN_SECRET_LENGTH) {
    throw new Error(`createLuno: secret must be at least ${MIN_SECRET_LENGTH} characters`);
  }

  const logger = config.logger ?? silentLogger;
  const crypto = config.crypto ?? webCrypto();
  const hooks: HookBus = hookBus((event, error) =>
    logger.log('error', `hook for ${event} threw`, { error: String(error) }),
  );

  const context: CoreContext = {
    store: config.store,
    crypto,
    clock: config.clock ?? systemClock,
    ids: config.ids ?? tokenIdGenerator(crypto),
    sessions: config.sessions ?? localSessionRegistry(),
    logger,
    hooks,
    secret: config.secret,
    policy: { ...DEFAULT_PAIRING_POLICY, ...config.pairing },
    presenceTimeoutMs: config.presenceTimeoutMs ?? DEFAULT_PRESENCE_TIMEOUT_MS,
    wsUrl: config.wsUrl ?? null,
    pendingRetryAfterMs: config.pendingRetryAfterMs ?? 5_000,
  };

  const pairing = pairingService(context);
  const messaging = messagingService(context);

  return {
    pairing,
    sms: messaging,
    devices: devicesService(context),
    connections: connectionsService(context, messaging),
    http: httpRouter(context, pairing),
    on: hooks.on,
    /** Audit trail: every frame in and out, plus lifecycle events. */
    events: (filter: { deviceId?: string; limit?: number } = {}) =>
      context.store.events.list({
        ...(filter.deviceId ? { deviceId: filter.deviceId } : {}),
        limit: filter.limit ?? 100,
      }),
  };
}

export type Luno = ReturnType<typeof createLuno>;
