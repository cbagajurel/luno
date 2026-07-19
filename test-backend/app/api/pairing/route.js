import { getBackend } from '@/lib/luno.mjs';

export const dynamic = 'force-dynamic';

// POST /api/pairing — dashboard mints a short-lived pairing code for the node.
// The SDK returns the plaintext once (it stores only a hash); the adapter holds
// it in memory so the countdown card can keep showing it.
export async function POST(request) {
  const backend = getBackend();
  const origin = new URL(request.url).origin;

  const { code, session, qrUri } = await backend.luno.pairing.createSession({
    backendUrl: origin,
    label: 'Luno test backend',
  });
  backend.rememberCode(session, code);

  return Response.json({ code, sessionId: session.id, qrUri });
}
