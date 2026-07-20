import { getBackend } from '@/lib/luno.mjs';
import { resolveBaseUrl } from '@/lib/base-url.mjs';

export const dynamic = 'force-dynamic';

// POST /api/pairing — dashboard mints a short-lived pairing code for the node.
// Enrolment policy (expiry, seat count, approval) is the SDK's, not ours: we just
// forward the operator's choices as `policy` and let @luno-oss/core apply and enforce
// them. The SDK also hands back the scannable payload (qrUri/qrJson) built from the
// same session, so we never hand-roll a luno://pair string here.
//
// The SDK returns the plaintext code once (it stores only a hash); the adapter holds
// it — with its qrUri — in memory so the dashboard card can render the QR and count
// down.
export async function POST(request) {
  const backend = getBackend();
  const origin = resolveBaseUrl(request);

  const body = await request.json().catch(() => ({}));
  const policy = normalizePolicy(body);

  const { code, session, qrUri, qrJson } = await backend.luno.pairing.createSession({
    backendUrl: origin,
    label: typeof body.label === 'string' && body.label.trim() ? body.label.trim() : 'Luno test backend',
    policy,
  });
  backend.rememberCode(session, code, qrUri);

  return Response.json({
    code,
    sessionId: session.id,
    qrUri,
    qrJson,
    expiresAt: session.expiresAt,
    maxEnrollments: session.maxEnrollments,
    requireApproval: session.requireApproval,
  });
}

// Only pass through the fields the operator actually set; anything omitted falls to
// the SDK's secure defaults (10-min expiry, single seat, no approval, no replacement).
function normalizePolicy(body) {
  const policy = {};

  if ('expiresInMs' in body) {
    policy.expiresInMs = body.expiresInMs === null ? null : Number(body.expiresInMs);
  }
  if ('maxEnrollments' in body) {
    policy.maxEnrollments = body.maxEnrollments === null ? null : Number(body.maxEnrollments);
  }
  if (typeof body.requireApproval === 'boolean') {
    policy.requireApproval = body.requireApproval;
  }
  if (typeof body.allowReplacement === 'boolean') {
    policy.allowReplacement = body.allowReplacement;
  }

  return policy;
}
