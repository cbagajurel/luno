# @luno-oss/fastify

A [Fastify](https://fastify.dev) plugin for [`@luno-oss/core`](../core). It exposes the
node-facing surface — `POST /enroll`, `POST /enroll/status`, and the `WS /ws`
session socket — as one registerable plugin.

```ts
import Fastify from 'fastify';
import { createLuno, memoryStore } from '@luno-oss/core';
import { lunoFastify } from '@luno-oss/fastify';

const luno = createLuno({ store: memoryStore(), secret: process.env.LUNO_SECRET! });

const app = Fastify();
await app.register(lunoFastify, { luno });
await app.listen({ port: 3000 });
```

Like Express, Fastify is not fetch-native — the enroll routes go through the core's
`HttpRequest` shim (Fastify has already parsed the JSON body, so the shim just hands
it back). The socket uses `@fastify/websocket`; the plugin registers it for you if
you haven't already.

## Auth

The `WS /ws` route authorises `Authorization: Bearer <credential>` in a
`preValidation` hook — before the upgrade — so an unknown credential is a **401**
the node pauses on, not a post-upgrade close. The authorised device is stashed on
the request and picked up by the socket handler, which bridges to
`luno.connections.open` and lets the core drive the §6 handshake and command
dispatch.

## Tests

`test/adapter.test.ts` boots a real Fastify server, enrols, drives `WS /ws` with the
`@luno-oss/testing` fake node through pair → handshake → send → delivered, and checks
the 401 path.

```
pnpm test
pnpm build
```
