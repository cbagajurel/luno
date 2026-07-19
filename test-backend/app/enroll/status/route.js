import { getLuno } from '@/lib/luno.mjs';

export const dynamic = 'force-dynamic';

// POST /enroll/status — the node polls here while an enrolment awaits operator
// approval, so a wait never re-spends the pairing code. Same SDK router as
// /enroll; it dispatches on the path.
export async function POST(request) {
  const result = await getLuno().http.handle(request);
  return new Response(result.body, { status: result.status, headers: result.headers });
}
