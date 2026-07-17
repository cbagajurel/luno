import { getHub } from '@/lib/hub.mjs';

export const dynamic = 'force-dynamic';

function publicWsUrl(request) {
  if (process.env.PUBLIC_WS_URL) return process.env.PUBLIC_WS_URL;
  const host = request.headers.get('host');
  // Match the scheme the request arrived on. A TLS proxy (Railway/Render/Fly/
  // ngrok/cloudflared) sets x-forwarded-proto=https -> wss; direct LAN http has
  // no such header -> ws (the debug node accepts it).
  const proto = request.headers.get('x-forwarded-proto') || 'http';
  const wsProto = proto === 'https' ? 'wss' : 'ws';
  return `${wsProto}://${host}/ws`;
}

// POST /enroll  — the node's one-time pairing call (PairingManager -> RestClient).
// Body: { pairingCode, deviceInfo: { model, manufacturer, androidSdk, appVersion } }
// 2xx  -> { deviceId, credential, wsUrl }
// 4xx  -> node maps 400/401/403/409/410 to "pairing code rejected".
export async function POST(request) {
  const hub = getHub();

  let body;
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: 'malformed json' }, { status: 400 });
  }

  const code = body?.pairingCode;
  if (!code) return Response.json({ error: 'pairingCode required' }, { status: 400 });

  try {
    const { deviceId, credential } = hub.consumePairingCode(code, body?.deviceInfo || null);
    return Response.json({ deviceId, credential, wsUrl: publicWsUrl(request) });
  } catch (e) {
    return Response.json({ error: e.msg || 'enrollment failed' }, { status: e.status || 500 });
  }
}
