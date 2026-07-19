# @luno/core

The framework-independent engine that implements the Luno protocol. All of the
business logic lives here; every platform adapter is a thin translation layer
over this package (see [`docs/backend-sdk.md`](../../docs/backend-sdk.md)).

It depends on `@luno/protocol` and nothing else. It imports no HTTP framework,
no database driver, and no platform global — everything it needs from the outside
world arrives through an injected port.

## Getting started

```ts
import { createLuno, memoryStore } from '@luno/core';

const luno = createLuno({
  store: memoryStore(),           // swap for @luno/store-postgres in production
  secret: process.env.LUNO_SECRET, // keys the pairing-code and credential digests
  pairing: { expiresInMs: 600_000, maxEnrollments: 1 },
});

const { code, qrUri } = await luno.pairing.createSession({
  backendUrl: 'https://gw.example.com',
  label: 'Acme',
});
// show `code` / render `qrUri` — the plaintext is never retrievable again

await luno.sms.send({ deviceId, to: '+9779800000000', body: 'hello', ref: 'order-42' });

luno.on('sms.received', async ({ deviceId, from, body }) => { /* your logic */ });
```

`secret` keys the HMAC that pairing codes and device credentials are stored
under. It is durable deployment state: rotating it invalidates every issued code
and credential.

## What an adapter has to do

Three things, none of which involve business logic:

```ts
// 1. the node-facing REST surface
const result = await luno.http.handle(request);   // { status, headers, body }

// 2. authenticate the WSS upgrade
const device = await luno.connections.authorize(bearerToken);

// 3. pump frames between the socket and the core
const session = await luno.connections.open(device, {
  send: async (frame) => socket.send(encodeFrame(frame)),
  close: async (code, reason) => socket.close(code, reason),
});
socket.on('message', (raw) => session.receive(String(raw)));
socket.on('close', () => session.close());
```

`luno.http.handle` returns a plain `{ status, headers, body }` rather than a
`Response`, because constructing one needs a global that Express does not have
and older Node did not either. Fetch-native adapters convert in one line, and
`toFetchHandler(luno.http, (body, init) => new Response(body, init))` does it for
you without the core ever touching a platform global.

## Ports

| Port | Purpose | Default |
| --- | --- | --- |
| `LunoStore` | persistence, as five narrow stores | `memoryStore()` (explicit) |
| `CryptoPort` | `randomBytes` + `hmacSha256` | `webCrypto()` |
| `Clock` | current time | `systemClock` |
| `IdGenerator` | prefixed ids | random tokens |
| `SessionRegistry` | who is connected, and where | `localSessionRegistry()` |
| `Logger` | structured logging | silent |

Presence is derived from `lastSeenAt` on read rather than tracked with a timer,
so the same code is correct on a long-running server and on a request-scoped
function that cannot hold one.

## The storage contract

`PairingSessionStore.claim()` is the one operation with a hard correctness
requirement: it **must be linearizable**. Concurrent callers for a session must
between them see at most `maxEnrollments` successes, which means one conditional
write — `UPDATE … WHERE enrollments_used < max_enrollments RETURNING`, a
Firestore transaction, or a CAS loop — never a read followed by a write.

A read-then-write implementation passes every sequential test and fails only
under load, in production, by admitting two devices to a single-use session. The
exported conformance suite exists to catch exactly that:

```ts
import { describeStoreConformance } from '@luno/core/test/store-conformance';

describeStoreConformance('my-store', () => myStore());
```

## Runtime neutrality

The package compiles with `"types": []` and `"lib": ["ES2022"]`, so `Buffer`,
`process` and any `node:*` import fail the build rather than being caught in
review. Web Crypto and `TextEncoder` are declared structurally where needed.
The result runs unchanged on Node 18+, Cloudflare Workers, Deno and Bun.

## Scripts

```
pnpm test        # vitest
pnpm typecheck   # src (runtime-neutral) and tests
pnpm build       # dual ESM/CJS + .d.ts via tsup
```
