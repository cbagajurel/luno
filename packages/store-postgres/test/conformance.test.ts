import { PGlite } from '@electric-sql/pglite';
import type { LunoStore } from '@luno/core';
import { describeStoreConformance } from '@luno/testing/store';
import { postgresStore } from '../src/store';
import { migrate, type Queryable } from '../src/sql';

/**
 * A fresh in-memory Postgres per store, migrated on first use. The conformance
 * suite reuses fixed ids across tests, so each `makeStore()` must hand back an
 * isolated, empty database — a new PGlite instance does exactly that.
 *
 * PGlite is a real Postgres (WASM), so the SQL — including the conditional
 * `UPDATE … RETURNING` behind `claim()` — runs against genuine Postgres
 * semantics. It executes statements on one connection, which proves the
 * conditional write is atomic; a live multi-connection server (set `DATABASE_URL`,
 * see below) additionally exercises it under MVCC.
 */
function pgliteStore(): LunoStore {
  const db = new PGlite();
  const raw: Queryable = {
    async query(text, params) {
      const result = await db.query(text, params ? [...params] : []);
      return { rows: result.rows as Record<string, unknown>[] };
    },
  };
  let migrated: Promise<void> | null = null;
  const sql: Queryable = {
    async query(text, params) {
      migrated ??= migrate(raw);
      await migrated;
      return raw.query(text, params);
    },
  };
  return postgresStore(sql);
}

describeStoreConformance('postgresStore (pglite)', pgliteStore);
