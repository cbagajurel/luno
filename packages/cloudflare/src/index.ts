import { encodeFrame } from '@luno-oss/protocol';
import type { DeviceRecord, Luno, NodeSession } from '@luno-oss/core';

/**
 * The Cloudflare `WebSocket` surface this adapter touches, declared structurally so
 * the package builds and unit-tests under plain Node — no `@cloudflare/workers-types`
 * and no workerd. The real runtime object satisfies it; so does an in-memory fake,
 * which is how the bridge is tested without a Worker.
 */
export interface WorkerWebSocket {
  accept(): void;
  send(data: string): void;
  close(code?: number, reason?: string): void;
  addEventListener(type: 'message', listener: (event: { data: unknown }) => void): void;
  addEventListener(type: 'close', listener: () => void): void;
  addEventListener(type: 'error', listener: () => void): void;
}

declare const WebSocketPair: { new (): { 0: WorkerWebSocket; 1: WorkerWebSocket } };

export interface LunoFetchOptions {
  luno: Luno;
  paths?: {
    /** default `/ws` */ ws?: string;
  };
}

function bearer(header: string | null | undefined): string {
  const match = /^Bearer\s+(.+)$/i.exec(header ?? '');
  return match?.[1]?.trim() ?? '';
}

// A frame is text, but a socket may surface bytes; decode defensively either way.
function frameText(data: unknown): string {
  if (typeof data === 'string') return data;
  if (data instanceof ArrayBuffer) return new TextDecoder().decode(data);
  if (ArrayBuffer.isView(data)) return new TextDecoder().decode(data as Uint8Array);
  return String(data);
}

/**
 * Bridges an accepted Worker/Durable-Object socket to `luno.connections.open`. This
 * is the whole §4 socketless story: the `FrameSink` doesn't care that the far end
 * is a `WebSocketPair` served by an isolate that comes and goes rather than a
 * long-lived Node socket — so the core's §6/§7 logic runs here unchanged.
 *
 * Exported (and Node-testable) on its own because the socket is the only part a
 * Worker can't exercise off-platform; drive it with an in-memory fake to prove the
 * handshake and command flow without workerd.
 */
export function bridgeSocket(luno: Luno, server: WorkerWebSocket, device: DeviceRecord): void {
  server.accept();

  let sessionPromise: Promise<NodeSession> | null = null;
  const ensure = (): Promise<NodeSession> =>
    (sessionPromise ??= luno.connections.open(device, {
      async send(frame) {
        server.send(encodeFrame(frame));
      },
      async close(code, reason) {
        server.close(code, reason);
      },
    }));

  ensure();

  server.addEventListener('message', (event) => {
    void ensure()
      .then((session) => session.receive(frameText(event.data)))
      .catch(() => {});
  });
  const end = () => {
    if (sessionPromise) void sessionPromise.then((session) => session.close()).catch(() => {});
  };
  server.addEventListener('close', end);
  server.addEventListener('error', end);
}

interface SocketResponseInit {
  status: number;
  webSocket: WorkerWebSocket;
}

/**
 * A Cloudflare Workers `fetch` handler for the node-facing surface. `POST /enroll`
 * and `/enroll/status` hand the fetch-native `Request` straight to
 * `luno.http.handle`; `GET /ws` with an `Upgrade: websocket` authorises the bearer
 * credential (a 401 before the upgrade), opens a `WebSocketPair`, bridges the
 * server half, and returns the client half with `101`.
 *
 * For anything beyond a single isolate — many nodes, hibernation, cross-request
 * survival — put {@link bridgeSocket} inside a Durable Object and route `/ws` to
 * it; the bridge is identical, only the object holding the socket changes.
 */
export function lunoFetch(options: LunoFetchOptions): (request: Request) => Promise<Response> {
  const { luno } = options;
  const wsPath = options.paths?.ws ?? '/ws';

  return async (request) => {
    const url = new URL(request.url);

    if (request.method === 'GET' && url.pathname === wsPath) {
      if (request.headers.get('upgrade')?.toLowerCase() !== 'websocket') {
        return new Response('expected a websocket upgrade', { status: 426 });
      }
      const device = await luno.connections.authorize(bearer(request.headers.get('authorization')));
      if (!device) return new Response('unauthorized', { status: 401 });

      const pair = new WebSocketPair();
      bridgeSocket(luno, pair[1], device);
      return new Response(null, { status: 101, webSocket: pair[0] } as unknown as SocketResponseInit);
    }

    const result = await luno.http.handle(request);
    return new Response(result.body, { status: result.status, headers: result.headers });
  };
}

/** The default-export shape a Worker entry module expects: `export default lunoWorker({ luno })`. */
export function lunoWorker(options: LunoFetchOptions): { fetch: (request: Request) => Promise<Response> } {
  return { fetch: lunoFetch(options) };
}
