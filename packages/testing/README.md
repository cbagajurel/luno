# @luno-oss/testing

The conformance kit for Luno backend implementations: a scriptable **fake node**
that speaks the real wire protocol, and the **store contract** every persistence
adapter must satisfy. Used to prove an adapter or a store works before a phone
ever touches it (see [`docs/backend-sdk.md`](../../docs/backend-sdk.md)).

## FakeNode

A stand-in for the Android node. It runs the real §6 handshake, acks events, and
answers commands with the same event flow as the agent — so if your backend
satisfies it, the phone will behave the same way. Drive it over any transport
through the `NodeChannel` abstraction.

**In-process, deterministic** — wire it straight to `@luno-oss/core`, no socket:

```ts
import { FakeNode, channelPair, enrollNode } from '@luno-oss/testing';

const node = new FakeNode({ deviceId });
const { node: nodeSide, backend: backendSide } = channelPair();
node.attach(nodeSide);
// … wire backendSide to luno.connections.open(device, sink) …
await node.handshake();          // resolves at READY
await node.sendInboundSms({ from: '+15550001111', body: 'hi' });
```

**Over a real socket** — validate an adapter end to end:

```ts
import { FakeNode, webSocketChannel, enrollNode, fetchTransport } from '@luno-oss/testing';

const { deviceId, credential } = await enrollNode(fetchTransport(baseUrl), pairingCode);
const ws = new WebSocket(`${wsUrl}`, { headers: { Authorization: `Bearer ${credential}` } });
const node = new FakeNode({ deviceId });
node.attach(webSocketChannel(ws));
await node.handshake();
```

`enrollNode` mirrors the node's real enrolment flow, including polling
`/enroll/status` when a backend gates on operator approval. It throws a classified
`EnrollError` (`code: 'invalid_code' | 'session_expired' | …`) on rejection.

The main entry pulls in **no test runner**, so a demo script or a running server
can import it (`test-backend/scripts/fake-node.mjs` does exactly this).

## Store conformance

Imported from the `/store` subpath, because it depends on vitest:

```ts
import { describeStoreConformance } from '@luno-oss/testing/store';

describeStoreConformance('my-postgres-store', () => new PostgresStore(pool));
```

The suite hammers `claim()` under real concurrency and asserts that a single-use
session admits exactly one device — the linearizability requirement the whole
"swap the database" promise depends on. A store that passes it is safe to drop
into `createLuno`.

## Scripts

```
pnpm test        # vitest — drives the FakeNode against the real core
pnpm build       # dual ESM/CJS + .d.ts via tsup
```
