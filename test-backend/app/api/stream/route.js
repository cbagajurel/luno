import { getBackend } from '@/lib/luno.mjs';

export const dynamic = 'force-dynamic';

// GET /api/stream — Server-Sent Events feed of the projected backend snapshot,
// pushed on every SDK change (enrollment, connect, heartbeat, event, command).
// The dashboard mirrors this; it never polls.
export async function GET() {
  const backend = getBackend();
  const encoder = new TextEncoder();
  let cleanup = () => {};

  const stream = new ReadableStream({
    start(controller) {
      let pushing = false;
      let again = false;

      // Snapshot is async; coalesce overlapping change bursts so a heartbeat storm
      // can't queue a hundred reads.
      const push = async () => {
        if (pushing) {
          again = true;
          return;
        }
        pushing = true;
        try {
          const snap = await backend.snapshot();
          controller.enqueue(encoder.encode(`data: ${JSON.stringify(snap)}\n\n`));
        } catch {
          /* controller closed */
        } finally {
          pushing = false;
          if (again) {
            again = false;
            void push();
          }
        }
      };

      void push();
      const unsub = backend.subscribe(() => void push());
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
