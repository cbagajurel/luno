import { parse } from 'node:url';
import { WebSocketServer } from 'ws';
import { encodeFrame } from '@luno/protocol';

// The device credential rides in `Authorization: Bearer <credential>`, exactly as
// the node's ConnectionManager sends it. @luno/core validates it (constant-time,
// by hash); an unknown credential is a 401, which the node treats as "re-enroll
// required" and pauses on rather than looping.
function bearer(req) {
  const match = /^Bearer\s+(.+)$/i.exec(req.headers['authorization'] || '');
  return match ? match[1].trim() : '';
}

// The core drives the whole §6 handshake, acks, resync and command dispatch; the
// adapter only bridges the socket to a FrameSink and pumps raw frames back in.
async function bridge(luno, ws, device) {
  const session = await luno.connections.open(device, {
    send: async (frame) => {
      if (ws.readyState === ws.OPEN) ws.send(encodeFrame(frame));
    },
    close: async (code, reason) => ws.close(code ?? 1000, reason ?? ''),
  });

  ws.on('message', (data) => {
    session.receive(data.toString()).catch((err) => console.error('[ws] receive', err));
  });
  ws.on('close', () => session.close().catch(() => {}));
  ws.on('error', () => session.close().catch(() => {}));
}

/**
 * Handles the node's WSS connection on `/ws` and leaves every other upgrade
 * (Next's HMR socket in dev) to `onOtherUpgrade`. Shared by the real server and
 * the integration test so both exercise the identical socket path.
 */
export function attachNodeWebSocket(server, luno, onOtherUpgrade = () => {}) {
  const wss = new WebSocketServer({ noServer: true });

  server.on('upgrade', async (req, socket, head) => {
    const { pathname } = parse(req.url);
    if (pathname !== '/ws') {
      onOtherUpgrade(req, socket, head);
      return;
    }

    const device = await luno.connections.authorize(bearer(req));
    if (!device) {
      socket.write('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n');
      socket.destroy();
      return;
    }
    wss.handleUpgrade(req, socket, head, (ws) => {
      bridge(luno, ws, device).catch((err) => {
        console.error('[ws] bridge', err);
        ws.close();
      });
    });
  });

  return wss;
}
