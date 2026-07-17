import { getHub } from '@/lib/hub.mjs';

export const dynamic = 'force-dynamic';

// GET /api/stream — Server-Sent Events feed of the whole hub snapshot, pushed on
// every change (enrollment, connect, heartbeat, event, command). The dashboard
// mirrors this; it never polls.
export async function GET() {
  const hub = getHub();
  const encoder = new TextEncoder();
  let cleanup = () => { };

  const stream = new ReadableStream({
    start(controller) {
      const push = () => {
        try {
          controller.enqueue(encoder.encode(`data: ${JSON.stringify(hub.snapshot())}\n\n`));
        } catch {
          /* controller closed */
        }
      };
      push();
      const unsub = hub.subscribe(push);
      const keepAlive = setInterval(() => {
        try {
          controller.enqueue(encoder.encode(': keep-alive\n\n'));
        } catch {
          /* controller closed */
        }
      }, 20000);
      cleanup = () => {
        clearInterval(keepAlive);
        unsub();
      };
    },
    cancel() {
      cleanup();
    },
  });

  return new Response(stream, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
    },
  });
}
