import type {
  ClaimOutcome,
  DeviceRecord,
  EnrollmentRecord,
  EventLogRecord,
  LunoStore,
  MessageRecord,
  PairingSessionRecord,
} from '../ports/store';

const clone = <T>(value: T): T => structuredCloneish(value);

/**
 * Records are copied in and out so callers cannot mutate stored state by holding
 * a reference, which a real database would never allow and which would otherwise
 * hide aliasing bugs until a persistent store was swapped in.
 */
function structuredCloneish<T>(value: T): T {
  if (value === null || typeof value !== 'object') return value;
  if (Array.isArray(value)) return value.map(structuredCloneish) as unknown as T;
  const out: Record<string, unknown> = {};
  for (const [key, item] of Object.entries(value as Record<string, unknown>)) {
    out[key] = structuredCloneish(item);
  }
  return out as T;
}

/**
 * Reference store, and the one the conformance suite is written against. Useful
 * in tests and for a single-process deployment; anything durable wants a real
 * `@luno-oss/store-*`.
 */
export function memoryStore(): LunoStore {
  const sessions = new Map<string, PairingSessionRecord>();
  const devices = new Map<string, DeviceRecord>();
  const enrollments = new Map<string, EnrollmentRecord>();
  const messages = new Map<string, MessageRecord>();
  const events: EventLogRecord[] = [];

  return {
    pairingSessions: {
      async create(record) {
        sessions.set(record.id, clone(record));
      },

      async findById(sessionId) {
        const record = sessions.get(sessionId);
        return record ? clone(record) : null;
      },

      async findByCodeHash(codeHash) {
        for (const record of sessions.values()) {
          if (record.codeHash === codeHash) return clone(record);
        }
        return null;
      },

      async claim({ sessionId, now }): Promise<ClaimOutcome> {
        const record = sessions.get(sessionId);
        if (!record) return { status: 'not_found' };

        // Everything from here to the increment must run without awaiting, or two
        // concurrent enrolments could both observe the same enrollmentsUsed and
        // both be admitted to a single-use session.
        if (record.revokedAt !== null) return { status: 'revoked', session: clone(record) };
        if (record.expiresAt !== null && record.expiresAt <= now) {
          return { status: 'expired', session: clone(record) };
        }
        if (record.maxEnrollments !== null && record.enrollmentsUsed >= record.maxEnrollments) {
          return { status: 'exhausted', session: clone(record) };
        }

        record.enrollmentsUsed += 1;
        return { status: 'claimed', session: clone(record) };
      },

      async release(sessionId) {
        const record = sessions.get(sessionId);
        if (record && record.enrollmentsUsed > 0) record.enrollmentsUsed -= 1;
      },

      async revoke(sessionId, now) {
        const record = sessions.get(sessionId);
        if (record && record.revokedAt === null) record.revokedAt = now;
      },

      async list() {
        return [...sessions.values()].map(clone);
      },
    },

    devices: {
      async create(record) {
        devices.set(record.id, clone(record));
      },

      async findById(deviceId) {
        const record = devices.get(deviceId);
        return record ? clone(record) : null;
      },

      async findByCredentialHash(credentialHash) {
        for (const record of devices.values()) {
          if (record.credentialHash === credentialHash) return clone(record);
        }
        return null;
      },

      async findByInstallId(installId) {
        for (const record of devices.values()) {
          if (record.installId === installId) return clone(record);
        }
        return null;
      },

      async update(deviceId, patch) {
        const record = devices.get(deviceId);
        if (record) Object.assign(record, clone(patch));
      },

      async list() {
        return [...devices.values()].map(clone);
      },
    },

    enrollments: {
      async create(record) {
        enrollments.set(record.id, clone(record));
      },

      async findById(enrollmentId) {
        const record = enrollments.get(enrollmentId);
        return record ? clone(record) : null;
      },

      async update(enrollmentId, patch) {
        const record = enrollments.get(enrollmentId);
        if (record) Object.assign(record, clone(patch));
      },

      async list() {
        return [...enrollments.values()].map(clone);
      },
    },

    messages: {
      async create(record) {
        messages.set(record.id, clone(record));
      },

      async findById(messageId) {
        const record = messages.get(messageId);
        return record ? clone(record) : null;
      },

      async findByCommandId(commandId) {
        for (const record of messages.values()) {
          if (record.commandId === commandId) return clone(record);
        }
        return null;
      },

      async findByNodeMessageId(deviceId, nodeMessageId) {
        for (const record of messages.values()) {
          if (record.deviceId === deviceId && record.nodeMessageId === nodeMessageId) {
            return clone(record);
          }
        }
        return null;
      },

      async update(messageId, patch) {
        const record = messages.get(messageId);
        if (record) Object.assign(record, clone(patch));
      },

      async listOutstanding(deviceId) {
        const terminal = new Set(['delivered', 'undelivered', 'failed', 'cancelled']);
        return [...messages.values()]
          .filter((record) => record.deviceId === deviceId && !terminal.has(record.status))
          .sort((a, b) => a.createdAt - b.createdAt)
          .map(clone);
      },

      async listByDevice(deviceId, limit) {
        return [...messages.values()]
          .filter((record) => record.deviceId === deviceId)
          .sort((a, b) => b.createdAt - a.createdAt)
          .slice(0, limit)
          .map(clone);
      },
    },

    events: {
      async append(record) {
        events.push(clone(record));
      },

      async list({ deviceId, limit }) {
        return events
          .filter((record) => (deviceId ? record.deviceId === deviceId : true))
          .slice(-limit)
          .reverse()
          .map(clone);
      },
    },
  };
}
