# @luno/store-postgres

A durable [`LunoStore`](../core/src/ports/store.ts) backed by Postgres. Drop it into
`createLuno` and every device, pairing session, message and audit event survives a
restart — the in-memory `memoryStore()` does not.

It is **driver-agnostic**: it takes any `{ query(text, params) => { rows } }`, so the
same store runs on `pg` (a long-lived pool), a serverless HTTP driver (Neon, Xata),
or `@electric-sql/pglite` in a test. Nothing here imports a driver or a `node:`
module.

```ts
import { Pool } from 'pg';
import { createLuno } from '@luno/core';
import { postgresStore, migrate } from '@luno/store-postgres';

const pool = new Pool({ connectionString: process.env.DATABASE_URL });
await migrate(pool);                       // once, at deploy or boot

const luno = createLuno({
  store: postgresStore(pool),
  secret: process.env.LUNO_SECRET,
});
```

`migrate` applies the schema (see [`src/sql.ts`](src/sql.ts)); every statement is
`IF NOT EXISTS`, so calling it repeatedly is safe. The schema is additive-only —
new protocol fields land as nullable columns, never rewrites.

## Why it's safe to swap in

The whole "swap the database" promise rests on `claim()` being linearizable: a
single-use pairing session must admit **exactly one** device even when two enrol at
the same instant. This store expresses `claim()` as one conditional
`UPDATE … RETURNING`, and it passes `@luno/core`'s store conformance suite —
including the test that fires 60 concurrent claims and asserts the limit holds:

```ts
import { describeStoreConformance } from '@luno/testing/store';
describeStoreConformance('postgresStore', () => postgresStore(pool));
```

The package's own tests run that suite against **PGlite** (a real Postgres compiled
to WASM), so the SQL is exercised against genuine Postgres semantics with no server
to stand up. PGlite runs one connection, which proves the conditional write is
atomic; point the suite at a live server for multi-connection MVCC coverage too.

## Scripts

```
pnpm test        # conformance + hydration, against PGlite
pnpm build       # dual ESM/CJS + .d.ts via tsup
```
