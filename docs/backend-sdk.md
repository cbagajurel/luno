# Luno — Backend SDK architecture (proposal)

> Status: **approved; Phases 1–3 landed** (2026-07-19). `@luno/protocol` is
> cross-checked against the node's Kotlin codec over a shared fixture corpus;
> `@luno/core` implements pairing, enrolment, device management, the connection
> handshake and messaging behind injected ports; `@luno/testing` ships a
> scriptable fake node and the store conformance kit; and `test-backend` is now a
> thin adapter over `@luno/core`, validated end-to-end over a real socket.
> Phases 4–5 (a fetch-native adapter + a durable store, then the rest) outstanding.
> Companion to [`architecture.md`](architecture.md) (the node's side) and
> [`pairing.md`](pairing.md) (the enrolment contract).

## 1. The one-page version

The node already defines the entire contract: [`pairing.md`](pairing.md) for
enrolment, [`architecture.md`](architecture.md) §8 for the wire protocol. A
backend's job is to implement that contract. Today it is implemented once, in
`test-backend/lib/{hub,wsHandler}.mjs`, welded to Next.js.

This proposal extracts that implementation into a **framework-independent core**
and reduces every backend platform to a thin adapter:

```
packages/
  protocol/      @luno/protocol   zero-dependency wire types + codecs
  core/          @luno/core       all business logic; depends only on protocol
  testing/       @luno/testing    conformance suite every impl must pass
  store-*/       storage adapters (memory, postgres, firestore, d1, …)
  <platform>/    thin runtime adapters (express, hono, firebase, …)
```

Two decisions carry most of the weight:

- **The core's HTTP surface is a WHATWG `fetch` handler** —
  `(Request) => Promise<Response>`. Every modern runtime speaks it natively
  (Workers, Deno, Bun, Hono, Next, Node 18+), so most adapters collapse to
  ~30 lines of glue instead of a reimplemented route table.
- **Long-lived WebSockets are not universally available**, and pretending
  otherwise is the trap this design must avoid. See §4 — it is the single
  hardest constraint in the whole system, and the brief does not account for it.

---

## 2. What "framework-independent" actually costs

The brief says the core must not depend on Express, Nest, Firebase, and so on.
That is the easy half, and it is mostly a lint rule. The hard half is **runtime**
independence, which is a stronger and more invasive constraint:

| Assumption                   | Breaks on                            | Consequence for the core                 |
| ---------------------------- | ------------------------------------ | ---------------------------------------- |
| `node:crypto`                | Workers, Deno (partly)               | hashing/randomness must be a **port**    |
| `Buffer`                     | Workers, browsers                    | use `Uint8Array` + Web Crypto only       |
| `setInterval` for heartbeats | Functions (request-scoped), Workers  | timers must be a **port**, never ambient |
| in-process `Map` of sockets  | any multi-instance deploy            | session registry must be a **port**      |
| holding a socket open        | Firebase/Appwrite/Supabase Functions | see §4                                   |
| `process.env`                | Workers (uses bindings)              | config is injected, never read ambiently |

So the rule is not "no Express import". It is: **the core targets the
intersection of Node 18+, Workers, Deno and Bun — pure ESM TypeScript, Web
Crypto, `fetch` types, and no ambient I/O of any kind.** Everything a runtime
does differently becomes an explicit injected port. That intersection is
enforced mechanically (§11), not by discipline.

---

## 3. Layers and the dependency rule

```
      ┌────────────────────────────────────────────────────────┐
      │  ADAPTERS   @luno/express · firebase · hono · workers   │
      │  translate runtime ⇄ core. no business logic.           │
      └───────────────────────────▲────────────────────────────┘
                                  │ depends on
      ┌───────────────────────────┴────────────────────────────┐
      │  APPLICATION   services: pairing · enrolment · devices  │
      │                messaging · presence · sessions · audit  │
      │  ── declares PORTS (interfaces) it needs ──             │
      ├────────────────────────────────────────────────────────┤
      │  DOMAIN   entities, state machines, policy, invariants  │
      │  pure. no I/O. no async. fully unit-testable.           │
      └───────────────────────────▲────────────────────────────┘
                                  │ depends on
      ┌───────────────────────────┴────────────────────────────┐
      │  PROTOCOL   @luno/protocol — envelope, commands,        │
      │  events, acks, control, QR payload, version negotiation │
      │  zero dependencies. shared with client SDKs.            │
      └────────────────────────────────────────────────────────┘

      INFRASTRUCTURE (store-postgres, store-firestore, …)
      implements ports. depends inward. nothing depends on it.
```

Dependencies point inward, always. `@luno/core` imports `@luno/protocol` and
nothing else. Infrastructure and adapters are injected at composition time by
the application author, never imported by the core.

### Why `@luno/protocol` is its own package

It could live inside core. It shouldn't, for one decisive reason: **client SDKs
need the protocol types without the server engine.** A `@luno/react` dashboard
rendering a `device_status` payload, or a future non-Android node, wants the
envelope types and codecs — not pairing policy, storage ports, or session
management. Splitting it keeps the client story clean and makes the protocol
independently versionable, which matters because it is the one thing the Android
node actually depends on.

---

## 4. The constraint the brief misses: not every platform can hold a socket

The Luno protocol is built around a long-lived, stateful, bidirectional
connection — §6 connection state machine, §7.2 heartbeats, §7.4 resync. The
brief lists Firebase Functions, Supabase Edge Functions and Appwrite Functions
as target adapters. **Those runtimes are request-scoped and cannot hold a
WebSocket open.** No amount of clean architecture changes that; it is a property
of the platform, not of our code.

This does not sink the design, but it must be designed for explicitly rather
than discovered during implementation:

| Platform                         | Can hold a node socket?   | How the adapter works                                                  |
| -------------------------------- | ------------------------- | ---------------------------------------------------------------------- |
| Node (Express/Nest/Fastify/Hono) | yes                       | `ws` server in-process; sessions in memory + pub/sub if multi-instance |
| Cloudflare Workers               | yes — **Durable Objects** | one DO per device; the natural fit, DO _is_ the session                |
| Deno Deploy / Supabase Edge      | partly                    | `Deno.upgradeWebSocket` works, but instances are ephemeral and evicted |
| Bun                              | yes                       | native WS server                                                       |
| Firebase Functions               | **no**                    | needs Cloud Run for the socket, _or_ HTTP fallback                     |
| Appwrite Functions               | **no**                    | HTTP fallback only                                                     |
| Vercel / Next serverless         | **no**                    | HTTP fallback, or a separate socket service                            |

The architecture accommodates this with two moves:

1. **`SessionRegistry` is a port**, with two shapes of implementation — _local_
   (the socket lives in this process) and _routed_ (the socket lives elsewhere;
   delivery goes through a broker). The core never assumes which.
2. **A documented HTTP fallback transport.** The node's design already
   anticipates this — `architecture.md` calls REST the fallback, and §11 says
   "after a threshold, REST fallback may flush critical events." Formalising it
   as a long-poll/SSE command channel is what makes socketless platforms
   first-class rather than second-class.

**This is a protocol-level decision and therefore touches the Android node**,
which is why it is flagged here as an open question (§13) rather than assumed.
The alternative — shipping `@luno/firebase` that silently only supports
outbound-when-connected — would be a worse outcome discovered later.

---

## 5. Package inventory

| Package                  | Depends on     | Contains                                                                                                                                                                                                                                         |
| ------------------------ | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `@luno/protocol`         | —              | envelope/command/event/ack/control types, codecs, `DecodeResult`, version negotiation, QR payload parse/build, pairing DTOs, error taxonomy                                                                                                      |
| `@luno/core`             | protocol       | domain + application services + port interfaces + `createLuno()` + fetch router                                                                                                                                                                  |
| `@luno/testing`          | protocol, core | **Built.** Scriptable fake node (real §6 handshake, over any transport) on the main entry — vitest-free so demos/servers can import it; store conformance on `/store`. Protocol fixtures stayed in `@luno/protocol` next to the codec they check |
| ~~`@luno/store-memory`~~ | —              | **Folded into `@luno/core` as `memoryStore()`.** A separate package would depend on core while core's own tests depend on it, and that build cycle buys nothing; keeping it inside also means `createLuno` works the moment it is installed      |
| `@luno/store-postgres`   | core           | Postgres/Supabase (`pg` or `postgres`)                                                                                                                                                                                                           |
| `@luno/store-firestore`  | core           | Firestore                                                                                                                                                                                                                                        |
| `@luno/store-d1`         | core           | Cloudflare D1 + KV/DO                                                                                                                                                                                                                            |
| `@luno/express`          | core           | `fetch`↔express bridge + `ws` upgrade handling                                                                                                                                                                                                   |
| `@luno/hono`             | core           | near-zero: Hono is already fetch-native                                                                                                                                                                                                          |
| `@luno/cloudflare`       | core           | Worker entry + Durable Object session                                                                                                                                                                                                            |
| `@luno/firebase`         | core           | Functions HTTPS + Cloud Run socket variant                                                                                                                                                                                                       |
| `@luno/nest`             | core           | DI module wrapping the same fetch handler                                                                                                                                                                                                        |
| `@luno/supabase`         | core           | Edge Function entry                                                                                                                                                                                                                              |

`@luno/protocol` and `@luno/core` are the deliverables. Everything else is
small, and several are near-trivial once the fetch surface exists.

---

## 6. The core's public API

Designed so a backend developer never reads the protocol spec. Composition is
explicit — every dependency injected, nothing ambient:

```ts
import { createLuno } from "@luno/core";
import { PostgresStore } from "@luno/store-postgres";

const luno = createLuno({
  store: new PostgresStore(pool),
  pairing: {
    expiresIn: "10m",
    maxEnrollments: 1,
    invalidateOnUse: true,
    requireApproval: false,
  },
  onEvent: {
    "sms.received": async (msg) => {
      /* your business logic */
    },
    "device.online": async (d) => {},
  },
});
```

Operator-facing API — the surface a dashboard or backend service calls:

```ts
const session = await luno.pairing.createSession({
  label: "Acme",
  createdBy: user.id,
});
session.code; // plaintext, returned exactly once
session.qrPayload; // luno://pair?v=1&…

await luno.devices.list({ ownerId });
await luno.devices.revoke(deviceId);

const msg = await luno.sms.send({
  deviceId,
  to: "+9779…",
  body: "hi",
  ref: "order-42",
});
msg.id; // track through sms_accepted → sms_sent → delivery_report
await luno.sms.get(msg.id);
```

Protocol-facing API — what adapters call, and the _only_ thing they call:

```ts
await luno.http.handle(request); // all REST: /enroll, /enroll/status
await luno.connections.authorize(credential); // WSS upgrade check
const session = await luno.connections.open(device, sink);
await session.receive(rawFrame); // drives the §6 state machine
await session.close();
```

**Refinement made during implementation:** `handle` returns a plain
`{ status, headers, body }` rather than a `Response`. Constructing a `Response`
requires a global that Express does not have at all, and that older Node lacked —
so returning one would have made the "collapse the adapters" trick work for
fetch-native platforms while quietly excluding the others. A fetch adapter
converts in one line, and `toFetchHandler(router, makeResponse)` takes the
constructor as an argument so the core never reaches for a platform global.

`sink` is a `FrameSink` — `{ send(frame): Promise<void> }`. That one interface is
the entire transport abstraction: a `ws` socket, a Durable Object, an SSE
stream, or a long-poll buffer all satisfy it identically. The core does not know
which, and the §6/§7 logic is written once against it.

---

## 7. Ports

Narrow and purpose-built, not one god-interface. Grouped:

**Storage** — `PairingSessionStore`, `DeviceStore`, `MessageStore`,
`EventLogStore`, `EnrollmentStore`. Each is a handful of methods.

**Runtime** — `Clock`, `IdGenerator`, `RandomSource`, `Crypto` (hash/verify/
constant-time compare), `Timers`, `Logger`. Injecting `Clock` and `RandomSource`
makes expiry, backoff and code generation deterministic under test, which is
worth far more than it costs.

**Distribution** — `SessionRegistry` (who is connected, where), `PubSub`
(cross-instance command routing), `RateLimiter`.

### Storage atomicity is a contract, not an implementation detail

The highest-risk operation in the system is consuming a pairing session. Under
`maxEnrollments: 1`, two nodes submitting the same code concurrently must
produce exactly one enrolment. A naive `get()` then `put()` port lets every
store implementation race, and the bug appears only under load, in production,
as a duplicate device.

So the port does not expose read-modify-write. It exposes the atomic operation
and states the requirement:

```ts
interface PairingSessionStore {
  /**
   * Atomically claim one enrolment slot. MUST be linearizable: concurrent
   * callers with the same sessionId yield at most `maxEnrollments` successes.
   * Postgres: SELECT … FOR UPDATE / UPDATE … WHERE remaining > 0 RETURNING.
   * Firestore: runTransaction. D1: transaction. KV-only: CAS loop.
   */
  claim(sessionId: string, now: number): Promise<ClaimResult>;
}
```

Every store implementation must pass a concurrency suite in `@luno/testing`
that hammers `claim()` in parallel and asserts the invariant. That suite is what
makes "swap the database" a real claim rather than an aspiration.

---

## 8. Authentication — two axes, deliberately split

The brief says authentication should be abstracted out of the core. That is
right for one axis and wrong for the other, and conflating them would be a
security regression:

**Axis 1 — operator/API-consumer auth** (a human or service calling
`luno.sms.send`). **Belongs to the adapter.** Firebase Auth, Supabase Auth, JWT,
OAuth, cookies, API keys — the adapter authenticates, then passes an
authenticated `Principal` into the core. The core does authorization (does this
principal own this device?), never authentication.

**Axis 2 — node/device credential auth** (a Luno node presenting its credential
on the WSS handshake). **Belongs to the core.** It is protocol-defined
(`architecture.md` §3), it is security-critical, and it involves constant-time
comparison, credential hashing, rotation and revocation. Pushing it into seven
adapters means writing it seven times and getting it subtly wrong in at least
one. The core owns it; the adapter only supplies the transport-level plumbing.

```ts
const principal = await myAuth(req);          // adapter's job
await luno.sms.send({ principal, deviceId, … }); // core authorizes

const device = await luno.connections.authorize(bearerToken); // core's job
```

---

## 9. Conformance kit (`@luno/testing`)

With one core and N adapters and M stores, the thing that actually prevents
ecosystem drift is an executable contract:

- **Store contract suite** — run against any `*Store`, including the
  concurrency/atomicity assertions from §7.
- **Protocol fixtures** — golden frames shared with the node's Kotlin tests, so
  both sides decode byte-identical payloads.
- **Fake node driver** — a scriptable client that performs the full §6
  handshake (`version_negotiate` → `resync` → READY), sends events, and asserts
  acks. `test-backend/scripts/fake-node.mjs` already does a version of this and
  is the obvious seed.
- **Adapter suite** — boots an adapter, runs the fake node against it end to
  end. A new adapter is "done" when it passes.

---

## 10. Extension strategy

New capability that is protocol-level (a new command, a new transport like MMS)
lands in `@luno/protocol` + `@luno/core` once, and every adapter inherits it
with no code change. That is the whole point of the split, and the test for
whether a change is in the right place: **if adding a feature requires touching
more than one adapter, it belongs in the core.**

Client SDKs (`@luno/react`, `@luno/flutter`, `@luno/go`, …) talk to the
developer's backend, never to a node directly — the backend holds the privileged
position and all node credentials. They consume `@luno/protocol` for types only.

---

## 11. Mechanics

- **pnpm workspaces** + TypeScript project references; `tsup` for dual ESM/CJS
  builds; `vitest`; `changesets` for coordinated versioning.
- **Enforcement, not etiquette**: an ESLint `no-restricted-imports` rule bans
  `node:*` and framework imports inside `core`/`protocol`, plus a CI job that
  type-checks the core against Workers and Deno lib types. The dependency rule
  fails the build rather than relying on review.
- **Location**: `packages/` per your instruction. Worth noting a future
  `@luno/flutter` is a _pub_ package, not npm — if that lands we'd want
  `packages/npm/` and `packages/dart/`. Cheap to decide now, annoying later.

---

## 12. Suggested phasing

Deliberately not seven adapters at once. Each phase ends with something
provable against the real Android node:

1. ~~**`@luno/protocol`** — types + codecs + QR payload, cross-checked against the
   Kotlin implementation with shared fixtures.~~ **Done.** 91 TS tests; the node's
   `ProtocolFixturesTest` reads the same `fixtures/frames.json` and asserts the
   same byte-identical round-trip, so the two codecs cannot drift silently.
2. ~~**`@luno/core`** — domain, ports, pairing/enrolment/session services, the
   fetch router, `@luno/store-memory`.~~ **Done.** 73 tests, including an
   exported store conformance suite that asserts `claim()` linearizability under
   real concurrency, and an end-to-end smoke run (pair → connect → handshake →
   send → delivered) against the built bundles on real Web Crypto.
3. ~~**`@luno/testing`** + port `test-backend` onto the SDK.~~ **Done.** The fake
   node drives the real core with no mocks; `test-backend` is now a thin adapter
   (`/enroll` → `luno.http.handle`, `/ws` → `connections.open`, dashboard →
   services). Validated over a genuine loopback socket: pair → handshake → send →
   delivered → inbound → revoke, plus a real `server.mjs` run driven by the
   fake-node CLI. The physical phone is the user's final confirmation; the wire
   path it exercises is now covered by an automated equivalent.
4. **One socket adapter** and **one store** proving the abstractions across a
   genuine boundary — my recommendation is Hono (fetch-native, runs almost
   everywhere) plus Postgres.
5. **Remaining adapters**, each gated on the conformance suite.

Phase 3 is the one that matters. Until a real device pairs against the extracted
core, the abstraction is unvalidated.

---

## 13. Resolved decisions

1. **HTTP fallback transport — designed now, built later.** The fallback
   transport is specified and its protocol surface reserved, but v1 implements
   sockets only. Concretely: `FrameSink` (§6) is defined so a long-poll/SSE
   buffer satisfies it without change, and `SessionRegistry` distinguishes
   _local_ from _routed_ sessions from day one. No Android node work in v1.
   §8.5's versioning rules make additive fields non-breaking, so the fallback
   lands later without a redesign — which is the property this deferral rests on.
2. **Hono proves the design first**, fetch-native, so the same handler then runs
   on Workers, Deno, Bun and Node unchanged.
3. **`packages/`**, per instruction. If a `@luno/flutter` pub package ever
   lands, revisit the `packages/npm/` + `packages/dart/` split then.
4. **`test-backend` gets ported onto the SDK**, becoming a thin Next.js adapter
   over `@luno/core`. The debug dashboard keeps working, and a physical phone
   pairing exactly as it does today is the acceptance test for the extraction.
