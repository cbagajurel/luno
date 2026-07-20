# @luno-oss/cloudflare

A [Cloudflare Workers](https://workers.cloudflare.com) adapter for
[`@luno-oss/core`](../core). It proves the §4 **socketless/edge path**: a Worker isolate
doesn't hold a long-lived socket the way a Node server does, yet the core's
`FrameSink` doesn't care — the same §6/§7 logic runs behind a `WebSocketPair`.

```ts
import { createLuno } from '@luno-oss/core';
import { lunoWorker } from '@luno-oss/cloudflare';
// import your durable store, e.g. a D1/KV-backed LunoStore

const luno = createLuno({ store, secret: env.LUNO_SECRET });
export default lunoWorker({ luno });
```

`lunoFetch({ luno })` handles it directly if you route yourself:

- `POST /enroll` and `/enroll/status` → `luno.http.handle(request)` (a Worker
  `Request` is fetch-native, so no shim — same as [`@luno-oss/hono`](../hono)).
- `GET /ws` with `Upgrade: websocket` → authorises the bearer credential (a **401**
  before the upgrade), opens a `WebSocketPair`, bridges the server half with
  `bridgeSocket`, and returns the client half with `101`.

## Durable Objects

A bare Worker handles one socket per isolate, which the isolate lifecycle can cut
short. For many nodes, hibernation, or cross-request survival, put `bridgeSocket`
inside a **Durable Object** and route `/ws` to it — the bridge is identical, only
the object that holds the socket changes. That is the point of factoring
`bridgeSocket` out: it is the entire socket integration, and it is runtime-neutral.

## Tests

The socket is the only part a Worker can't exercise off-platform, so `bridgeSocket`
is exported and tested on its own: `test/adapter.test.ts` drives it with an
in-memory fake `WebSocketPair` and the `@luno-oss/testing` fake node through the §6
handshake and send → delivered — no workerd required. The enroll path is tested by
calling the real fetch handler with a fetch-native `Request`, plus the 401/426
guards. For a full on-workerd run, wire the handler into `wrangler dev`.

```
pnpm test
pnpm build
```
