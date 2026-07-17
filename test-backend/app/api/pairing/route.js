import { getHub } from '@/lib/hub.mjs';

export const dynamic = 'force-dynamic';

// POST /api/pairing — dashboard mints a short-lived pairing code for the node.
export async function POST() {
  const hub = getHub();
  const code = hub.createPairingCode();
  return Response.json({ code });
}
