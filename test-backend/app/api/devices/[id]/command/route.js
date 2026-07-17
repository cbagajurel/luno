import { getHub } from '@/lib/hub.mjs';

export const dynamic = 'force-dynamic';

const ALLOWED = new Set(['send_sms', 'cancel_sms', 'get_status', 'config_update', 'revoke', 'wipe']);

// POST /api/devices/:id/command — dashboard issues a backend->node command.
// Body: { type, payload }
export async function POST(request, { params }) {
  const { id } = await params;
  const hub = getHub();

  let body;
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: 'malformed json' }, { status: 400 });
  }

  if (!ALLOWED.has(body?.type)) {
    return Response.json({ error: `unknown command type: ${body?.type}` }, { status: 400 });
  }

  try {
    const res = hub.sendCommand(id, body.type, body.payload || {});
    return Response.json({ ok: true, ...res });
  } catch (e) {
    return Response.json({ error: e.msg || 'command failed' }, { status: e.status || 500 });
  }
}
