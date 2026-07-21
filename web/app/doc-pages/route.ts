import { getDocPages } from '@components/get-doc-pages'

// The command palette's page list is served as static JSON rather than passed
// through the root layout as props. Two reasons: it keeps ~50 entries out of
// every page's RSC payload, and props that large break the Next dev server's
// inline flight chunks (production is unaffected, which makes it a nasty one to
// catch). The palette fetches this once, on first open.
export const dynamic = 'force-static'

export async function GET(): Promise<Response> {
  return Response.json(await getDocPages())
}
