import { getLuno } from '@/lib/luno.mjs';
import { LunoError } from '@luno-oss/core';

export const dynamic = 'force-dynamic';

// POST /api/devices/:id/command — dashboard issues a backend->node command.
// Body: { type, payload }. Each type maps to a first-class SDK call rather than a
// raw frame, so the adapter carries no protocol logic of its own.
export async function POST(request, { params }) {
  const { id } = await params;
  const luno = getLuno();

  let body;
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: 'malformed json' }, { status: 400 });
  }

  const payload = body?.payload || {};

  try {
    switch (body?.type) {
      case 'send_sms': {
        const message = await luno.sms.send({
          deviceId: id,
          to: payload.to,
          body: payload.body,
          deliveryReport: payload.deliveryReport,
          ...(payload.subscriptionId != null ? { subscriptionId: payload.subscriptionId } : {}),
          ...(payload.ref ? { ref: payload.ref } : {}),
        });
        return Response.json({ ok: true, messageId: message.id, commandId: message.commandId });
      }
      case 'cancel_sms': {
        const message = await luno.sms.cancel(payload.messageId || payload.commandId);
        return Response.json({ ok: true, status: message.status });
      }
      case 'get_status':
        await luno.devices.requestStatus(id);
        return Response.json({ ok: true });
      case 'config_update':
        await luno.devices.updateConfig(id, payload);
        return Response.json({ ok: true });
      case 'revoke':
        await luno.devices.revoke(id);
        return Response.json({ ok: true });
      case 'wipe':
        await luno.devices.wipe(id);
        return Response.json({ ok: true });
      default:
        return Response.json({ error: `unknown command type: ${body?.type}` }, { status: 400 });
    }
  } catch (e) {
    if (e instanceof LunoError) return Response.json({ error: e.message }, { status: e.status });
    return Response.json({ error: e?.message || 'command failed' }, { status: 500 });
  }
}
