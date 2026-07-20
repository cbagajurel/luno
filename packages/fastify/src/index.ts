import { encodeFrame } from "@luno-oss/protocol";
import type { DeviceRecord, HttpRequest, Luno } from "@luno-oss/core";
import type {
  FastifyInstance,
  FastifyPluginAsync,
  FastifyReply,
  FastifyRequest,
} from "fastify";
import fastifyWebsocket from "@fastify/websocket";
import type { WebSocket } from "ws";

export interface LunoFastifyOptions {
  luno: Luno;
  paths?: {
    /** default `/enroll` */ enroll?: string;
    /** default `/enroll/status` */ enrollStatus?: string;
    /** default `/ws` */ ws?: string;
  };
}

function bearer(header: string | undefined): string {
  const match = /^Bearer\s+(.+)$/i.exec(header ?? "");
  return match?.[1]?.trim() ?? "";
}

// Fastify parses a JSON body ahead of the handler, so the shim just hands it back.
function toHttpRequest(request: FastifyRequest): HttpRequest {
  return {
    method: request.method,
    url: request.url,
    async json() {
      return request.body ?? {};
    },
  };
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

type DeviceCarrier = FastifyRequest & { lunoDevice?: DeviceRecord };

/**
 * A Fastify plugin exposing the node-facing Luno surface: `POST /enroll`,
 * `POST /enroll/status`, and the `WS /ws` session socket. Register it with the
 * options object:
 *
 * ```ts
 * await app.register(lunoFastify, { luno });
 * ```
 *
 * It registers `@fastify/websocket` itself (idempotently guarded by the caller if
 * already present). The socket authorises the bearer credential in a
 * `preValidation` hook — before the upgrade — so an unknown credential is a 401 the
 * node pauses on, then bridges the socket to `luno.connections.open`.
 */
export const lunoFastify: FastifyPluginAsync<LunoFastifyOptions> = async (
  app,
  options,
) => {
  const { luno } = options;
  const enrollPath = options.paths?.enroll ?? "/enroll";
  const enrollStatusPath = options.paths?.enrollStatus ?? "/enroll/status";
  const wsPath = options.paths?.ws ?? "/ws";

  const enroll = async (request: FastifyRequest, reply: FastifyReply) => {
    const result = await luno.http.handle(toHttpRequest(request));
    return reply.code(result.status).headers(result.headers).send(result.body);
  };

  app.post(enrollPath, enroll);
  app.post(enrollStatusPath, enroll);

  if (!app.hasPlugin("@fastify/websocket")) {
    await app.register(fastifyWebsocket);
  }

  app.get(
    wsPath,
    {
      websocket: true,
      preValidation: async (request: FastifyRequest, reply: FastifyReply) => {
        const device = await luno.connections.authorize(
          bearer(request.headers.authorization),
        );
        if (!device) return reply.code(401).send("unauthorized");
        (request as DeviceCarrier).lunoDevice = device;
      },
    },
    (connection: unknown, request: FastifyRequest) => {
      // @fastify/websocket v11 passes the WebSocket directly; older versions pass a
      // stream whose `.socket` is the WebSocket — accept either.
      const ws = ((connection as { socket?: WebSocket }).socket ??
        connection) as WebSocket;
      const device = (request as DeviceCarrier).lunoDevice;
      if (!device) {
        ws.close(1008, "unauthorized");
        return;
      }
      void bridge(luno, ws, device).catch(() => ws.close());
    },
  );
};

/** Registers {@link lunoFastify} on an app. A thin convenience over `app.register`. */
export async function registerLuno(
  app: FastifyInstance,
  options: LunoFastifyOptions,
): Promise<void> {
  await app.register(lunoFastify, options);
}
