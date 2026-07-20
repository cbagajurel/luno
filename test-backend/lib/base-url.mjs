import { networkInterfaces } from 'node:os';

// Hosts a phone on the same WiFi can never reach — if the dashboard is opened at
// one of these, the QR must not carry it.
const NON_ROUTABLE = new Set(['0.0.0.0', '127.0.0.1', 'localhost', '::', '::1']);

function firstLanIPv4() {
  for (const addrs of Object.values(networkInterfaces())) {
    for (const a of addrs || []) {
      if (a.family === 'IPv4' && !a.internal) return a.address;
    }
  }
  return null;
}

/**
 * The base URL to bake into a pairing QR — one another device can actually reach.
 *
 * An explicit override (a tunnel / public host) always wins. Otherwise we take the
 * host the browser used, and only when that is loopback/wildcard do we swap in the
 * machine's LAN IP: opening the dashboard at http://0.0.0.0:3000 would otherwise
 * mint a QR pointing at 0.0.0.0, which no phone can route to.
 */
export function resolveBaseUrl(request) {
  const override = process.env.LUNO_PUBLIC_URL || process.env.PUBLIC_BASE_URL;
  if (override) return override.trim().replace(/\/+$/, '');

  const url = new URL(request.url);
  if (NON_ROUTABLE.has(url.hostname)) {
    const lan = firstLanIPv4();
    if (lan) url.hostname = lan;
  }
  return url.origin;
}
