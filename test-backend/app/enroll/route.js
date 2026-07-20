import { getLuno } from '@/lib/luno.mjs';

export const dynamic = 'force-dynamic';

// POST /enroll — the node's one-time pairing call. The whole thing is @luno-oss/core:
// a Next Request is already fetch-native, so it satisfies the SDK's HttpRequest
// as-is, and the router returns the exact body + status the node expects.
export async function POST(request) {
  const result = await getLuno().http.handle(request);
  return new Response(result.body, { status: result.status, headers: result.headers });
}
