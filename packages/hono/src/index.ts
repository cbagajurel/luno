import { encodeFrame } from "@luno-oss/protocol";
import type { Luno } from "@luno-oss/core";
import type { DeviceRecord, FrameSink, NodeSession } from "@luno-oss/core";
import { Hono } from "hono";
import type { UpgradeWebSocket, WSContext } from "hono/ws";

export interface LunoHonoOptions {
  luno: Luno;
  /**
   * The platform's WebSocket helper — the one piece that varies by runtime.
   * `createNodeWebSocket({ app }).upgradeWebSocket` on Node, or the `upgradeWebSocket`
   * from `hono/cloudflare-workers` / `hono/deno` / `hono/bun`. The same handler code
   * runs behind all of them, which is the point of building on Hono.
   */
  upgradeWebSocket: UpgradeWebSocket;
  paths?: {
    /** default `/enroll` */ enroll?: string;
    /** default `/enroll/status` */ enrollStatus?: string;
    /** default `/ws` */ ws?: string;
  };
}

type Env = { Variables: { lunoDevice: DeviceRecord } };

function bearer(header: string | undefined): string {
  const match = /^Bearer\s+(.+)$/i.exec(header ?? "");
  return match?.[1]?.trim() ?? "";
}

// A frame arrives as text on every runtime, but a socket may hand it back as bytes;
// decode defensively so a binary-framing client still parses.
function frameText(data: unknown): string {
  if (typeof data === "string") return data;
  if (data instanceof ArrayBuffer) return new TextDecoder().decode(data);
  if (ArrayBuffer.isView(data))
    return new TextDecoder().decode(data as Uint8Array);
  return String(data);
}

const WS_OPEN = 1;

function socketSink(ws: WSContext): FrameSink {
  return {
    async send(frame) {
      if (ws.readyState === WS_OPEN) ws.send(encodeFrame(frame));
    },
    async close(code, reason) {
      ws.close(code, reason);
    },
  };
}

/**
 * Builds a Hono app exposing the node-facing Luno surface: `POST /enroll`,
 * `POST /enroll/status`, and the `WS /ws` session socket. Serve it directly, or
 * mount it under a larger app with {@link registerLuno}.
 *
 * The REST routes hand `c.req.raw` — a real `Request` — straight to
 * `luno.http.handle`; the socket route authorises the bearer credential, then
 * bridges the socket to `luno.connections.open` and lets the core drive the whole
 * §6 handshake, acks and command dispatch.
 */
export function lunoHono(options: LunoHonoOptions): Hono<Env> {
  const { luno, upgradeWebSocket } = options;
  const enrollPath = options.paths?.enroll ?? "/enroll";
  const enrollStatusPath = options.paths?.enrollStatus ?? "/enroll/status";
  const wsPath = options.paths?.ws ?? "/ws";

  const app = new Hono<Env>();

  const handleEnroll = async (raw: Request): Promise<Response> => {
    const result = await luno.http.handle(raw);
    return new Response(result.body, {
      status: result.status,
      headers: result.headers,
    });
  };

  app.post(enrollPath, (c) => handleEnroll(c.req.raw));
  app.post(enrollStatusPath, (c) => handleEnroll(c.req.raw));

  app.get(
    wsPath,
    // Authorise before the upgrade so an unknown credential fails as an HTTP 401 —
    // which the node treats as "re-enroll required" and pauses on, rather than a
    // post-upgrade close it would reconnect through.
    async (c, next) => {
      const device = await luno.connections.authorize(
        bearer(c.req.header("authorization")),
      );
      if (!device) return c.text("unauthorized", 401);
      c.set("lunoDevice", device);
      await next();
    },
    upgradeWebSocket((c) => {
      const device = c.get("lunoDevice");
      let sessionPromise: Promise<NodeSession> | null = null;
      const open = (ws: WSContext) =>
        (sessionPromise ??= luno.connections.open(device, socketSink(ws)));

      return {
        onOpen: (_event, ws) => {
          open(ws);
        },
        onMessage: async (event, ws) => {
          const session = await open(ws);
          await session.receive(frameText(event.data));
        },
        onClose: async () => {
          if (sessionPromise) await (await sessionPromise).close();
        },
        onError: async () => {
          if (sessionPromise) await (await sessionPromise).close();
        },
      };
    }),
  );

  return app;
}

/** Mounts {@link lunoHono} onto an existing app, under `basePath` (default `/`). */
export function registerLuno(
  app: Hono,
  options: LunoHonoOptions,
  basePath = "/",
): void {
  app.route(basePath, lunoHono(options));
}
