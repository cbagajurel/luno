import { createServer } from 'node:http';
import { parse } from 'node:url';
import { networkInterfaces } from 'node:os';
import next from 'next';
import { WebSocketServer } from 'ws';
import { getHub } from './lib/hub.mjs';
import { authorizeUpgrade, handleConnection } from './lib/wsHandler.mjs';

const dev = process.env.NODE_ENV !== 'production';
const hostname = '0.0.0.0';
const port = parseInt(process.env.PORT || '3000', 10);

const app = next({ dev, hostname, port });
const handle = app.getRequestHandler();

await app.prepare();

const upgradeHandler = app.getUpgradeHandler();

const hub = getHub();
const wss = new WebSocketServer({ noServer: true });

const server = createServer((req, res) => {
  handle(req, res, parse(req.url, true));
});

server.on('upgrade', (req, socket, head) => {
  const { pathname } = parse(req.url);

  if (pathname === '/ws') {
    const auth = authorizeUpgrade(req, hub);
    if (!auth.ok) {
      socket.write('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n');
      socket.destroy();
      return;
    }
    wss.handleUpgrade(req, socket, head, (ws) => handleConnection(ws, auth.device, hub));
    return;
  }

  // Everything else (Next's HMR socket in dev) belongs to Next.
  upgradeHandler(req, socket, head);
});

function lanAddresses() {
  const out = [];
  for (const addrs of Object.values(networkInterfaces())) {
    for (const a of addrs || []) {
      if (a.family === 'IPv4' && !a.internal) out.push(a.address);
    }
  }
  return out;
}

server.listen(port, hostname, () => {
  console.log(`\n  Luno test backend`);
  console.log(`  local    http://localhost:${port}`);
  for (const ip of lanAddresses()) {
    console.log(`  network  http://${ip}:${port}   (same-WiFi devices)`);
  }
  console.log(`\n  POST /enroll   node enrollment`);
  console.log(`  ws   /ws       node connection`);
  console.log(
    `\n  Note: the node requires https/wss. For a phone over LAN, front this with a\n` +
      `  TLS tunnel (cloudflared/ngrok) or use a debug build that allows cleartext.\n`,
  );
});
