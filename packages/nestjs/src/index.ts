import "reflect-metadata";
import type { IncomingMessage, Server } from "node:http";
import type { Duplex } from "node:stream";
import { Controller, Inject, Module, Post, Req, Res } from "@nestjs/common";
import type {
  DynamicModule,
  InjectionToken,
  OptionalFactoryDependency,
} from "@nestjs/common";
import { encodeFrame } from "@luno/protocol";
import type { DeviceRecord, HttpRequest, Luno } from "@luno/core";
import { WebSocketServer, type WebSocket } from "ws";

/** DI token the module binds the `Luno` instance to; inject it to reach the engine. */
export const LUNO = Symbol.for("@luno/nestjs:LUNO");

interface RequestLike extends IncomingMessage {
  originalUrl?: string;
  body?: unknown;
}

interface ResponseLike {
  status(code: number): ResponseLike;
  set(headers: Record<string, string>): ResponseLike;
  send(body: string): unknown;
}

async function readRawBody(req: IncomingMessage): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(chunk as Buffer);
  return Buffer.concat(chunks).toString("utf8");
}

function toHttpRequest(req: RequestLike): HttpRequest {
  return {
    method: req.method ?? "GET",
    url: req.originalUrl ?? req.url ?? "/",
    async json() {
      if (
        req.body !== undefined &&
        req.body !== null &&
        typeof req.body === "object"
      ) {
        return req.body;
      }
      if (typeof req.body === "string")
        return req.body ? JSON.parse(req.body) : {};
      const raw = await readRawBody(req);
      return raw ? JSON.parse(raw) : {};
    },
  };
}

/**
 * The node-facing REST surface as a Nest controller. It resolves `Luno` from the
 * container (the {@link LUNO} token), so the engine is wired the Nest way — through
 * DI — rather than reached for as a global.
 */
@Controller()
export class LunoController {
  constructor(@Inject(LUNO) private readonly luno: Luno) {}

  @Post("enroll")
  enroll(@Req() req: RequestLike, @Res() res: ResponseLike): Promise<void> {
    return this.handle(req, res);
  }

  @Post("enroll/status")
  enrollStatus(
    @Req() req: RequestLike,
    @Res() res: ResponseLike,
  ): Promise<void> {
    return this.handle(req, res);
  }

  private async handle(req: RequestLike, res: ResponseLike): Promise<void> {
    const result = await this.luno.http.handle(toHttpRequest(req));
    res.status(result.status).set(result.headers).send(result.body);
  }
}

export interface LunoModuleOptions {
  luno: Luno;
}

export interface LunoModuleAsyncOptions {
  useFactory: (...args: never[]) => Luno | Promise<Luno>;
  inject?: Array<InjectionToken | OptionalFactoryDependency>;
  imports?: DynamicModule["imports"];
}

/**
 * Wires the Luno engine into a Nest app. `forRoot` binds an instance you built;
 * `forRootAsync` builds it from a factory that can inject config, so the engine
 * composes with `ConfigService` and the rest of the container. Either way the
 * {@link LunoController} is registered and {@link LUNO} is exported for your own
 * providers to inject.
 */
@Module({})
export class LunoModule {
  static forRoot(options: LunoModuleOptions): DynamicModule {
    return {
      module: LunoModule,
      controllers: [LunoController],
      providers: [{ provide: LUNO, useValue: options.luno }],
      exports: [LUNO],
    };
  }

  static forRootAsync(options: LunoModuleAsyncOptions): DynamicModule {
    return {
      module: LunoModule,
      imports: options.imports ?? [],
      controllers: [LunoController],
      providers: [
        {
          provide: LUNO,
          useFactory: options.useFactory,
          inject: options.inject ?? [],
        },
      ],
      exports: [LUNO],
    };
  }
}

function bearer(header: string | undefined): string {
  const match = /^Bearer\s+(.+)$/i.exec(header ?? "");
  return match?.[1]?.trim() ?? "";
}

async function bridge(
  luno: Luno,
  ws: WebSocket,
  device: DeviceRecord,
): Promise<void> {
  const session = await luno.connections.open(device, {
    async send(frame) {
      if (ws.readyState === ws.OPEN) ws.send(encodeFrame(frame));
    },
    async close(code, reason) {
      ws.close(code ?? 1000, reason ?? "");
    },
  });

  ws.on("message", (data: unknown) => {
    void session.receive(String(data)).catch(() => {});
  });
  ws.on("close", () => void session.close().catch(() => {}));
  ws.on("error", () => void session.close().catch(() => {}));
}

export interface LunoWebSocketOptions {
  /** default `/ws` */ path?: string;
  onOtherUpgrade?: (req: IncomingMessage, socket: Duplex, head: Buffer) => void;
}

/**
 * Attaches the node's `WS /ws` socket to Nest's underlying HTTP server
 * (`app.getHttpServer()`). The protocol is raw frames, not the socket.io/event
 * shape a `@WebSocketGateway` imposes, so bridging the raw server is both simpler
 * and correct here. Authorises `Authorization: Bearer` before the upgrade (401 on
 * failure) and bridges to `luno.connections.open`.
 */
export function attachLunoWebSocket(
  server: Server,
  luno: Luno,
  options: LunoWebSocketOptions = {},
): WebSocketServer {
  const path = options.path ?? "/ws";
  const wss = new WebSocketServer({ noServer: true });

  server.on("upgrade", (req, socket, head) => {
    const pathname = new URL(req.url ?? "/", "http://localhost").pathname;
    if (pathname !== path) {
      options.onOtherUpgrade?.(req, socket, head);
      return;
    }
    luno.connections
      .authorize(bearer(req.headers.authorization))
      .then((device) => {
        if (!device) {
          socket.write(
            "HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n",
          );
          socket.destroy();
          return;
        }
        wss.handleUpgrade(req, socket, head, (ws) => {
          void bridge(luno, ws, device).catch(() => ws.close());
        });
      })
      .catch(() => socket.destroy());
  });

  return wss;
}
