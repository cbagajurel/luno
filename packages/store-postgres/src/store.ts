import type { DeviceInfo } from '@luno/protocol';
import type {
  ClaimOutcome,
  DevicePatch,
  DeviceRecord,
  EnrollmentRecord,
  EventLogRecord,
  LunoStore,
  MessagePart,
  MessagePatch,
  MessageRecord,
  PairingSessionRecord,
} from '@luno/core';
import type { Queryable } from './sql';

type Row = Record<string, unknown>;

const num = (value: unknown): number => Number(value);
const numOrNull = (value: unknown): number | null =>
  value === null || value === undefined ? null : Number(value);
const str = (value: unknown): string => value as string;
const strOrNull = (value: unknown): string | null => (value ?? null) as string | null;
// A boolean can arrive as a JS boolean (pg, pglite) or the char 't'/'f' from some drivers.
const bool = (value: unknown): boolean => value === true || value === 't' || value === 'true';
const parseJson = <T>(value: unknown): T =>
  (typeof value === 'string' ? JSON.parse(value) : value) as T;
const jsonParam = (value: unknown): string => JSON.stringify(value ?? null);

function toSession(row: Row): PairingSessionRecord {
  return {
    id: str(row.id),
    codeHash: str(row.code_hash),
    label: strOrNull(row.label),
    createdAt: num(row.created_at),
    createdBy: strOrNull(row.created_by),
    expiresAt: numOrNull(row.expires_at),
    maxEnrollments: numOrNull(row.max_enrollments),
    enrollmentsUsed: num(row.enrollments_used),
    requireApproval: bool(row.require_approval),
    allowReplacement: bool(row.allow_replacement),
    revokedAt: numOrNull(row.revoked_at),
    metadata: row.metadata == null ? null : parseJson<Record<string, string>>(row.metadata),
  };
}

function toDevice(row: Row): DeviceRecord {
  return {
    id: str(row.id),
    sessionId: strOrNull(row.session_id),
    credentialHash: str(row.credential_hash),
    installId: str(row.install_id),
    info: parseJson<DeviceInfo>(row.info),
    status: str(row.status) as DeviceRecord['status'],
    phase: str(row.phase) as DeviceRecord['phase'],
    pairedAt: num(row.paired_at),
    lastSeenAt: numOrNull(row.last_seen_at),
    revokedAt: numOrNull(row.revoked_at),
  };
}

function toEnrollment(row: Row): EnrollmentRecord {
  return {
    id: str(row.id),
    sessionId: str(row.session_id),
    installId: str(row.install_id),
    info: parseJson<DeviceInfo>(row.info),
    status: str(row.status) as EnrollmentRecord['status'],
    createdAt: num(row.created_at),
    decidedAt: numOrNull(row.decided_at),
    deviceId: strOrNull(row.device_id),
  };
}

function toMessage(row: Row): MessageRecord {
  return {
    id: str(row.id),
    deviceId: str(row.device_id),
    to: str(row.recipient),
    body: str(row.body),
    subscriptionId: numOrNull(row.subscription_id),
    ref: strOrNull(row.ref),
    deliveryReport: bool(row.delivery_report),
    status: str(row.status) as MessageRecord['status'],
    commandId: strOrNull(row.command_id),
    nodeMessageId: strOrNull(row.node_message_id),
    parts: parseJson<MessagePart[]>(row.parts),
    error: strOrNull(row.error),
    createdAt: num(row.created_at),
    updatedAt: num(row.updated_at),
  };
}

function toEvent(row: Row): EventLogRecord {
  return {
    id: str(row.id),
    deviceId: strOrNull(row.device_id),
    direction: str(row.direction) as EventLogRecord['direction'],
    kind: str(row.kind),
    type: str(row.type),
    payload: row.payload == null ? null : parseJson<unknown>(row.payload),
    frameId: strOrNull(row.frame_id),
    at: num(row.at),
  };
}

const TERMINAL_MESSAGE_STATUSES = ['delivered', 'undelivered', 'failed', 'cancelled'];

/**
 * A durable {@link LunoStore} over Postgres. It takes any {@link Queryable}, so it
 * is decoupled from the driver — a `pg.Pool` for a long-lived server, a serverless
 * HTTP driver on the edge, or `@electric-sql/pglite` in a test. Run {@link migrate}
 * once before use.
 *
 * `claim()` is a single conditional `UPDATE … RETURNING`, the linearizable form the
 * port requires; `@luno/testing`'s conformance suite asserts it admits exactly one
 * device to a single-use session under concurrency.
 */
export function postgresStore(sql: Queryable): LunoStore {
  const one = async (text: string, params: readonly unknown[]): Promise<Row | null> => {
    const { rows } = await sql.query(text, params);
    return rows[0] ?? null;
  };

  return {
    pairingSessions: {
      async create(record) {
        await sql.query(
          `INSERT INTO luno_pairing_sessions
             (id, code_hash, label, created_at, created_by, expires_at, max_enrollments,
              enrollments_used, require_approval, allow_replacement, revoked_at, metadata)
           VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12::jsonb)`,
          [
            record.id,
            record.codeHash,
            record.label,
            record.createdAt,
            record.createdBy,
            record.expiresAt,
            record.maxEnrollments,
            record.enrollmentsUsed,
            record.requireApproval,
            record.allowReplacement,
            record.revokedAt,
            record.metadata === null ? null : jsonParam(record.metadata),
          ],
        );
      },

      async findById(sessionId) {
        const row = await one(`SELECT * FROM luno_pairing_sessions WHERE id = $1`, [sessionId]);
        return row ? toSession(row) : null;
      },

      async findByCodeHash(codeHash) {
        const row = await one(`SELECT * FROM luno_pairing_sessions WHERE code_hash = $1 LIMIT 1`, [
          codeHash,
        ]);
        return row ? toSession(row) : null;
      },

      async claim({ sessionId, now }): Promise<ClaimOutcome> {
        const claimed = await one(
          `UPDATE luno_pairing_sessions
              SET enrollments_used = enrollments_used + 1
            WHERE id = $1
              AND revoked_at IS NULL
              AND (expires_at IS NULL OR expires_at > $2)
              AND (max_enrollments IS NULL OR enrollments_used < max_enrollments)
          RETURNING *`,
          [sessionId, now],
        );
        if (claimed) return { status: 'claimed', session: toSession(claimed) };

        // The conditional write matched nothing; re-read to say why. This read is
        // outside the atomic step and only classifies an already-decided miss.
        const row = await one(`SELECT * FROM luno_pairing_sessions WHERE id = $1`, [sessionId]);
        if (!row) return { status: 'not_found' };
        const session = toSession(row);
        if (session.revokedAt !== null) return { status: 'revoked', session };
        if (session.expiresAt !== null && session.expiresAt <= now) {
          return { status: 'expired', session };
        }
        return { status: 'exhausted', session };
      },

      async release(sessionId) {
        await sql.query(
          `UPDATE luno_pairing_sessions
              SET enrollments_used = GREATEST(enrollments_used - 1, 0)
            WHERE id = $1`,
          [sessionId],
        );
      },

      async revoke(sessionId, now) {
        await sql.query(
          `UPDATE luno_pairing_sessions SET revoked_at = $2 WHERE id = $1 AND revoked_at IS NULL`,
          [sessionId, now],
        );
      },

      async list() {
        const { rows } = await sql.query(
          `SELECT * FROM luno_pairing_sessions ORDER BY created_at`,
          [],
        );
        return rows.map(toSession);
      },
    },

    devices: {
      async create(record) {
        await sql.query(
          `INSERT INTO luno_devices
             (id, session_id, credential_hash, install_id, info, status, phase,
              paired_at, last_seen_at, revoked_at)
           VALUES ($1,$2,$3,$4,$5::jsonb,$6,$7,$8,$9,$10)`,
          [
            record.id,
            record.sessionId,
            record.credentialHash,
            record.installId,
            jsonParam(record.info),
            record.status,
            record.phase,
            record.pairedAt,
            record.lastSeenAt,
            record.revokedAt,
          ],
        );
      },

      async findById(deviceId) {
        const row = await one(`SELECT * FROM luno_devices WHERE id = $1`, [deviceId]);
        return row ? toDevice(row) : null;
      },

      async findByCredentialHash(credentialHash) {
        const row = await one(`SELECT * FROM luno_devices WHERE credential_hash = $1 LIMIT 1`, [
          credentialHash,
        ]);
        return row ? toDevice(row) : null;
      },

      async findByInstallId(installId) {
        const row = await one(`SELECT * FROM luno_devices WHERE install_id = $1 LIMIT 1`, [
          installId,
        ]);
        return row ? toDevice(row) : null;
      },

      async update(deviceId, patch: DevicePatch) {
        const { text, params } = buildUpdate('luno_devices', deviceId, [
          ['credential_hash', patch.credentialHash],
          ['status', patch.status],
          ['phase', patch.phase],
          ['last_seen_at', patch.lastSeenAt],
          ['revoked_at', patch.revokedAt],
          ['info', patch.info === undefined ? undefined : jsonParam(patch.info), '::jsonb'],
        ]);
        if (text) await sql.query(text, params);
      },

      async list() {
        const { rows } = await sql.query(`SELECT * FROM luno_devices ORDER BY paired_at`, []);
        return rows.map(toDevice);
      },
    },

    enrollments: {
      async create(record) {
        await sql.query(
          `INSERT INTO luno_enrollments
             (id, session_id, install_id, info, status, created_at, decided_at, device_id)
           VALUES ($1,$2,$3,$4::jsonb,$5,$6,$7,$8)`,
          [
            record.id,
            record.sessionId,
            record.installId,
            jsonParam(record.info),
            record.status,
            record.createdAt,
            record.decidedAt,
            record.deviceId,
          ],
        );
      },

      async findById(enrollmentId) {
        const row = await one(`SELECT * FROM luno_enrollments WHERE id = $1`, [enrollmentId]);
        return row ? toEnrollment(row) : null;
      },

      async update(enrollmentId, patch) {
        const { text, params } = buildUpdate('luno_enrollments', enrollmentId, [
          ['status', patch.status],
          ['decided_at', patch.decidedAt],
          ['device_id', patch.deviceId],
        ]);
        if (text) await sql.query(text, params);
      },

      async list() {
        const { rows } = await sql.query(`SELECT * FROM luno_enrollments ORDER BY created_at`, []);
        return rows.map(toEnrollment);
      },
    },

    messages: {
      async create(record) {
        await sql.query(
          `INSERT INTO luno_messages
             (id, device_id, recipient, body, subscription_id, ref, delivery_report, status,
              command_id, node_message_id, parts, error, created_at, updated_at)
           VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11::jsonb,$12,$13,$14)`,
          [
            record.id,
            record.deviceId,
            record.to,
            record.body,
            record.subscriptionId,
            record.ref,
            record.deliveryReport,
            record.status,
            record.commandId,
            record.nodeMessageId,
            jsonParam(record.parts),
            record.error,
            record.createdAt,
            record.updatedAt,
          ],
        );
      },

      async findById(messageId) {
        const row = await one(`SELECT * FROM luno_messages WHERE id = $1`, [messageId]);
        return row ? toMessage(row) : null;
      },

      async findByCommandId(commandId) {
        const row = await one(`SELECT * FROM luno_messages WHERE command_id = $1 LIMIT 1`, [
          commandId,
        ]);
        return row ? toMessage(row) : null;
      },

      async findByNodeMessageId(deviceId, nodeMessageId) {
        const row = await one(
          `SELECT * FROM luno_messages WHERE device_id = $1 AND node_message_id = $2 LIMIT 1`,
          [deviceId, nodeMessageId],
        );
        return row ? toMessage(row) : null;
      },

      async update(messageId, patch: MessagePatch) {
        const { text, params } = buildUpdate('luno_messages', messageId, [
          ['status', patch.status],
          ['command_id', patch.commandId],
          ['node_message_id', patch.nodeMessageId],
          ['error', patch.error],
          ['updated_at', patch.updatedAt],
          ['parts', patch.parts === undefined ? undefined : jsonParam(patch.parts), '::jsonb'],
        ]);
        if (text) await sql.query(text, params);
      },

      async listOutstanding(deviceId) {
        const { rows } = await sql.query(
          `SELECT * FROM luno_messages
            WHERE device_id = $1 AND status <> ALL($2::text[])
            ORDER BY created_at`,
          [deviceId, TERMINAL_MESSAGE_STATUSES],
        );
        return rows.map(toMessage);
      },

      async listByDevice(deviceId, limit) {
        const { rows } = await sql.query(
          `SELECT * FROM luno_messages WHERE device_id = $1 ORDER BY created_at DESC LIMIT $2`,
          [deviceId, limit],
        );
        return rows.map(toMessage);
      },
    },

    events: {
      async append(record) {
        await sql.query(
          `INSERT INTO luno_event_log (id, device_id, direction, kind, type, payload, frame_id, at)
           VALUES ($1,$2,$3,$4,$5,$6::jsonb,$7,$8)`,
          [
            record.id,
            record.deviceId,
            record.direction,
            record.kind,
            record.type,
            record.payload === null ? null : jsonParam(record.payload),
            record.frameId,
            record.at,
          ],
        );
      },

      async list({ deviceId, limit }) {
        // seq (a BIGSERIAL) orders events that share a millisecond `at`, so the
        // newest-first feed is stable even under a burst of same-tick appends.
        const { rows } = deviceId
          ? await sql.query(
              `SELECT * FROM luno_event_log WHERE device_id = $1 ORDER BY seq DESC LIMIT $2`,
              [deviceId, limit],
            )
          : await sql.query(`SELECT * FROM luno_event_log ORDER BY seq DESC LIMIT $1`, [limit]);
        return rows.map(toEvent);
      },
    },
  };
}

type Assignment = readonly [column: string, value: unknown, cast?: string];

/**
 * Builds a partial `UPDATE` from only the columns present in a patch, so an absent
 * key leaves its column untouched (a `null` value still writes `NULL`). Returns an
 * empty statement when nothing changed, which the caller skips.
 */
function buildUpdate(
  table: string,
  id: string,
  assignments: readonly Assignment[],
): { text: string; params: unknown[] } {
  const sets: string[] = [];
  const params: unknown[] = [];
  for (const [column, value, cast] of assignments) {
    if (value === undefined) continue;
    params.push(value);
    sets.push(`${column} = $${params.length}${cast ?? ''}`);
  }
  if (sets.length === 0) return { text: '', params: [] };
  params.push(id);
  return { text: `UPDATE ${table} SET ${sets.join(', ')} WHERE id = $${params.length}`, params };
}
