# @luno-oss/nestjs

A [NestJS](https://nestjs.com) module for [`@luno-oss/core`](../core). It wires the
engine into the DI container and exposes the node-facing surface: `POST /enroll`,
`POST /enroll/status`, and the `WS /ws` session socket.

```ts
import { Module } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { createLuno, memoryStore } from '@luno-oss/core';
import { LunoModule, attachLunoWebSocket } from '@luno-oss/nestjs';

const luno = createLuno({ store: memoryStore(), secret: process.env.LUNO_SECRET! });

@Module({ imports: [LunoModule.forRoot({ luno })] })
class AppModule {}

const app = await NestFactory.create(AppModule);
await app.listen(3000);
attachLunoWebSocket(app.getHttpServer(), luno);
```

`forRootAsync({ useFactory, inject })` builds the engine from a factory instead, so
it composes with `ConfigService` and the rest of the container. The `LUNO` token is
exported for your own providers to inject the engine.

## Why the socket bypasses `@WebSocketGateway`

A Nest gateway imposes the socket.io/event message shape (`@SubscribeMessage`).
The Luno protocol is **raw frames**, so the socket attaches to Nest's underlying
HTTP server (`app.getHttpServer()`) instead — `attachLunoWebSocket` authorises the
bearer credential before the upgrade (401 on failure) and bridges to
`luno.connections.open`. The engine drives the §6 handshake and command dispatch;
Nest owns the REST controller and DI.

## Tests

`test/adapter.test.ts` bootstraps a real Nest app (engine resolved through DI),
enrols, drives `WS /ws` with the `@luno-oss/testing` fake node through pair → handshake
→ send → delivered, and checks the 401 path.

```
pnpm test
pnpm build
```
