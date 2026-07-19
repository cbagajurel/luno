# @luno/hono

A [Hono](https://hono.dev) adapter for [`@luno/core`](../core). It exposes the
node-facing Luno surface — `POST /enroll`, `POST /enroll/status`, and the `WS /ws`
session socket — as Hono routes. Because Hono is fetch-native, the **same handler
runs on Node, Cloudflare Workers, Deno and Bun**; only the WebSocket helper differs
per runtime, and you pass that in.

The adapter is thin by design: the REST routes hand the real `Request` straight to
`luno.http.handle`, and the socket route authorises the bearer credential and then
bridges the socket to `luno.connections.open`. The core drives the entire §6
handshake, acks, resync and command dispatch — the adapter never parses a frame.

## Node

```ts
import { serve } from '@hono/node-server';
import { createNodeWebSocket } from '@hono/node-ws';
import { Hono } from 'hono';
import { createLuno, memoryStore } from '@luno/core';
import { registerLuno } from '@luno/hono';

const luno = createLuno({ store: memoryStore(), secret: process.env.LUNO_SECRET! });

const app = new Hono();
const { injectWebSocket, upgradeWebSocket } = createNodeWebSocket({ app });
registerLuno(app, { luno, upgradeWebSocket });

const server = serve({ fetch: app.fetch, port: 3000 });
injectWebSocket(server);
```

Swap `memoryStore()` for [`@luno/store-postgres`](../store-postgres) and it survives
a restart. Everything else stays the same — the adapter never touches the store.

## Other runtimes

The only change is where `upgradeWebSocket` comes from:

```ts
// Cloudflare Workers
import { upgradeWebSocket } from 'hono/cloudflare-workers';
// Deno
import { upgradeWebSocket } from 'hono/deno';
// Bun
import { createBunWebSocket } from 'hono/bun';
```

Then `lunoHono({ luno, upgradeWebSocket })` returns a `Hono` app you serve or mount
under a larger one with `registerLuno(app, options, basePath)`.

## Auth

An unknown device credential fails the `WS /ws` upgrade with an **HTTP 401** — the
authorisation runs before the socket is upgraded, so the node sees a 401 (which it
treats as "re-enroll required" and pauses on) rather than a post-upgrade close it
would reconnect through. Operator/API auth for your own dashboard routes is your
app's concern; this adapter only handles the node credential.

## Tests

`test/adapter.test.ts` boots the app on a real Node socket, drives it with the
`@luno/testing` fake node over `ws`, and runs the full pair → handshake → send →
delivered cycle — once on `memoryStore`, once on the durable Postgres store — plus
the 401 path.

```
pnpm test
pnpm build
```
