# @luno/express

An [Express](https://expressjs.com) adapter for [`@luno/core`](../core). It exposes
the node-facing surface — `POST /enroll`, `POST /enroll/status`, and the `WS /ws`
session socket — on a classic Node server.

Express is **not fetch-native**: it has no `Request`/`Response`, so this adapter
goes through the core's structural `HttpRequest` shim (reading the body from
`req.body` or straight off the stream) and bridges the socket with `ws`. That is
the seam a fetch-native adapter like [`@luno/hono`](../hono) never exercises — and
it is the same `ws` bridge the reference `test-backend` proved in production.

```ts
import { createServer } from 'node:http';
import express from 'express';
import { createLuno, memoryStore } from '@luno/core';
import { registerLunoEnroll, attachLunoWebSocket } from '@luno/express';

const luno = createLuno({ store: memoryStore(), secret: process.env.LUNO_SECRET! });

const app = express();
app.use(express.json());            // optional — the shim reads the raw body otherwise
registerLunoEnroll(app, luno);      // POST /enroll and /enroll/status

const server = createServer(app);
attachLunoWebSocket(server, luno);  // WS /ws
server.listen(3000);
```

Swap `memoryStore()` for [`@luno/store-postgres`](../store-postgres) to persist.

## The WebSocket lives on the http.Server, not on Express

Express doesn't speak WebSocket, so `attachLunoWebSocket` hooks the underlying
`http.Server`'s `upgrade` event. It authorises `Authorization: Bearer <credential>`
**before** the upgrade — an unknown credential is an HTTP 401 the node pauses on —
then bridges the socket to `luno.connections.open`, which drives the whole §6
handshake and command dispatch. Pass `onOtherUpgrade` to share the server with
another socket (a dev HMR channel, say). Because the bridge is plain `http` + `ws`,
it serves any Node framework on the same server, not only Express.

## Tests

`test/adapter.test.ts` boots a real Express server, enrols through the shim, drives
the `WS /ws` socket with the `@luno/testing` fake node, and runs pair → handshake →
send → delivered, plus the 401 path.

```
pnpm test
pnpm build
```
