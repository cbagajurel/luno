import { createServer } from 'node:http';
import { parse } from 'node:url';
import { networkInterfaces } from 'node:os';
import next from 'next';
import { getLuno } from './lib/luno.mjs';
import { attachNodeWebSocket } from './lib/ws.mjs';

const dev = process.env.NODE_ENV !== 'production';
const hostname = '0.0.0.0';
const port = parseInt(process.env.PORT || '3000', 10);

const app = next({ dev, hostname, port });
const handle = app.getRequestHandler();

await app.prepare();

const upgradeHandler = app.getUpgradeHandler();

const server = createServer((req, res) => {
  handle(req, res, parse(req.url, true));
});

// `/ws` is the node's connection; everything else (Next's HMR socket) is Next's.
attachNodeWebSocket(server, getLuno(), (req, socket, head) => upgradeHandler(req, socket, head));

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
