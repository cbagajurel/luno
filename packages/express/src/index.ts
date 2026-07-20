import type { IncomingMessage, Server } from "node:http";
import type { Duplex } from "node:stream";
import { encodeFrame } from "@luno-oss/protocol";
import type { DeviceRecord, HttpRequest, Luno } from "@luno-oss/core";
import { WebSocketServer, type WebSocket } from "ws";

/**
 * Minimal shape of an Express request the enroll handler reads. Declared
 * structurally so the package needs no `express` types at build time — the real
 * `Request` satisfies it, and so does a bare `http.IncomingMessage`.
 */
export interface ExpressLikeRequest extends IncomingMessage {
  originalUrl?: string;
  body?: unknown;
}

/** Minimal shape of the Express response the handler writes. */
export interface ExpressLikeResponse {
  status(code: number): ExpressLikeResponse;
  set(headers: Record<string, string>): ExpressLikeResponse;
  send(body: string): unknown;
}

export interface LunoExpressOptions {
  paths?: {
    /** default `/enroll` */ enroll?: string;
    /** default `/enroll/status` */ enrollStatus?: string;
  };
}

function bearer(header: string | undefined): string {
  const match = /^Bearer\s+(.+)$/i.exec(header ?? "");
  return match?.[1]?.trim() ?? "";
}

async function readRawBody(req: IncomingMessage): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(chunk as Buffer);
  return Buffer.concat(chunks).toString("utf8");
}

/**
 * Adapts a non-fetch-native request to the core's structural `HttpRequest`. This
 * is the seam a classic Node framework needs and a fetch-native one (Hono) does
 * not: Express has no `Request`/`Response`, so the body is read here — from
 * `req.body` if a JSON parser already populated it, otherwise straight off the
 * stream, so the adapter works with or without `express.json()` mounted.
 */
export function toHttpRequest(req: ExpressLikeRequest): HttpRequest {
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

type Handler = (req: ExpressLikeRequest, res: ExpressLikeResponse) => void;

/**
 * The node-facing enroll handlers, keyed by the path each answers. Register them
 * on any Express-compatible router; both call `luno.http.handle`, which dispatches
 * on the request path.
 */
export function lunoEnrollHandlers(
  luno: Luno,
  options: LunoExpressOptions = {},
): { path: string; handler: Handler }[] {
  const enrollPath = options.paths?.enroll ?? "/enroll";
  const enrollStatusPath = options.paths?.enrollStatus ?? "/enroll/status";

  const handler: Handler = (req, res) => {
    luno.http
      .handle(toHttpRequest(req))
      .then((result) =>
        res.status(result.status).set(result.headers).send(result.body),
      )
      .catch(() =>
        res
          .status(500)
          .set({ "content-type": "application/json" })
          .send('{"error":"internal"}'),
      );
  };

  return [
    { path: enrollPath, handler },
    { path: enrollStatusPath, handler },
  ];
}

/**
 * Registers `POST /enroll` and `POST /enroll/status` on an Express app or router.
 * `target.post(path, handler)` is the only method used, so any Express-compatible
 * router works.
 */
export function registerLunoEnroll(
  target: { post(path: string, handler: Handler): unknown },
  luno: Luno,
  options: LunoExpressOptions = {},
): void {
  for (const { path, handler } of lunoEnrollHandlers(luno, options)) {
    target.post(path, handler);
  }
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
  /** Handle upgrades on other paths (e.g. a dev HMR socket) instead of dropping them. */
  onOtherUpgrade?: (req: IncomingMessage, socket: Duplex, head: Buffer) => void;
}

/**
 * Attaches the node's `WS /ws` socket to an `http.Server` — the same server an
 * Express app listens on. The credential rides in `Authorization: Bearer`, is
 * checked before the upgrade (an unknown one is a 401 the node pauses on), and a
 * live socket is bridged to `luno.connections.open`; the core drives the whole §6
 * handshake and command dispatch. This bridge is plain `http` + `ws`, so it also
 * serves any other Node framework on the same server.
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
