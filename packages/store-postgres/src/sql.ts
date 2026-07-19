/**
 * The entire database surface this store needs, declared structurally so it binds
 * to any Postgres driver without importing one. `pg.Pool`, a `pg.PoolClient`, a
 * `postgres`-js tagged wrapper, `@electric-sql/pglite`, Neon's serverless driver —
 * all expose a `query(text, params) => { rows }` that satisfies this, which is
 * what lets the same store run on a long-lived pool or an edge HTTP driver.
 */
export interface Queryable {
  query(text: string, params?: readonly unknown[]): Promise<{ rows: Record<string, unknown>[] }>;
}

/**
 * One idempotent statement per table. Run once at deploy (or on boot in dev). Kept
 * as literal DDL rather than a migration framework because the schema is small and
 * additive-only — new protocol fields become nullable columns, never rewrites.
 */
export const SCHEMA = `
CREATE TABLE IF NOT EXISTS luno_pairing_sessions (
  id               TEXT PRIMARY KEY,
  code_hash        TEXT NOT NULL,
  label            TEXT,
  created_at       BIGINT NOT NULL,
  created_by       TEXT,
  expires_at       BIGINT,
  max_enrollments  INTEGER,
  enrollments_used INTEGER NOT NULL DEFAULT 0,
  require_approval BOOLEAN NOT NULL DEFAULT FALSE,
  allow_replacement BOOLEAN NOT NULL DEFAULT FALSE,
  revoked_at       BIGINT,
  metadata         JSONB
);
CREATE INDEX IF NOT EXISTS luno_pairing_sessions_code_hash ON luno_pairing_sessions (code_hash);

CREATE TABLE IF NOT EXISTS luno_devices (
  id              TEXT PRIMARY KEY,
  session_id      TEXT,
  credential_hash TEXT NOT NULL,
  install_id      TEXT NOT NULL,
  info            JSONB NOT NULL,
  status          TEXT NOT NULL,
  phase           TEXT NOT NULL,
  paired_at       BIGINT NOT NULL,
  last_seen_at    BIGINT,
  revoked_at      BIGINT
);
CREATE INDEX IF NOT EXISTS luno_devices_credential_hash ON luno_devices (credential_hash);
CREATE INDEX IF NOT EXISTS luno_devices_install_id ON luno_devices (install_id);

CREATE TABLE IF NOT EXISTS luno_enrollments (
  id         TEXT PRIMARY KEY,
  session_id TEXT NOT NULL,
  install_id TEXT NOT NULL,
  info       JSONB NOT NULL,
  status     TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  decided_at BIGINT,
  device_id  TEXT
);

CREATE TABLE IF NOT EXISTS luno_messages (
  id              TEXT PRIMARY KEY,
  device_id       TEXT NOT NULL,
  recipient       TEXT NOT NULL,
  body            TEXT NOT NULL,
  subscription_id INTEGER,
  ref             TEXT,
  delivery_report BOOLEAN NOT NULL DEFAULT FALSE,
  status          TEXT NOT NULL,
  command_id      TEXT,
  node_message_id TEXT,
  parts           JSONB NOT NULL DEFAULT '[]'::jsonb,
  error           TEXT,
  created_at      BIGINT NOT NULL,
  updated_at      BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS luno_messages_device ON luno_messages (device_id, created_at);
CREATE INDEX IF NOT EXISTS luno_messages_command ON luno_messages (command_id);
CREATE INDEX IF NOT EXISTS luno_messages_node_message ON luno_messages (device_id, node_message_id);

CREATE TABLE IF NOT EXISTS luno_event_log (
  seq       BIGSERIAL PRIMARY KEY,
  id        TEXT NOT NULL,
  device_id TEXT,
  direction TEXT NOT NULL,
  kind      TEXT NOT NULL,
  type      TEXT NOT NULL,
  payload   JSONB,
  frame_id  TEXT,
  at        BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS luno_event_log_device ON luno_event_log (device_id, seq);
`;

/**
 * Applies {@link SCHEMA}. Safe to call repeatedly; every statement is
 * `IF NOT EXISTS`.
 *
 * Each statement runs on its own, not as one multi-statement string: a
 * parameterised driver (node-postgres with values, PGlite's `.query`) uses the
 * extended protocol, which rejects multiple commands in a single call. The DDL
 * contains no `;` inside a literal, so splitting on it is safe.
 */
export async function migrate(sql: Queryable): Promise<void> {
  for (const statement of SCHEMA.split(';')) {
    const trimmed = statement.trim();
    if (trimmed) await sql.query(trimmed);
  }
}
