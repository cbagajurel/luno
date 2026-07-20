import { getBackend } from '@/lib/luno.mjs';

export const dynamic = 'force-dynamic';

// POST /api/enrollments/:id — operator's verdict on a device waiting for approval
// (created when a session has requireApproval). The node is polling /enroll/status
// and picks up the decision on its next poll; approve/deny don't emit an SDK hook,
// so we nudge the dashboard's SSE stream ourselves.
export async function POST(request, { params }) {
  const backend = getBackend();
  const { id } = await params;
  const { action } = await request.json().catch(() => ({}));

  try {
    if (action === 'approve') {
      await backend.luno.pairing.approveEnrollment(id);
    } else if (action === 'deny') {
      await backend.luno.pairing.denyEnrollment(id);
    } else {
      return Response.json({ error: 'action must be "approve" or "deny"' }, { status: 400 });
    }
  } catch (err) {
    return Response.json({ error: err?.message || 'could not update enrolment' }, { status: 409 });
  }

  backend.refresh();
  return Response.json({ ok: true });
}
