/**
 * The wire between a fake node and a backend, as raw frame text. Kept this
 * narrow so one driver runs unchanged in-process (wired straight to
 * `connections.open`) and over a real socket — the same abstraction as the
 * core's FrameSink, seen from the other end.
 */
export interface NodeChannel {
  send(raw: string): void | Promise<void>;
  onMessage(handler: (raw: string) => void): void;
  onClose(handler: () => void): void;
  close(code?: number, reason?: string): void | Promise<void>;
}

/** One end of an in-memory pipe. Whatever you write here surfaces on the peer's `onMessage`. */
interface PipeEnd extends NodeChannel {
  deliver(raw: string): void;
  markClosed(): void;
}

function pipeEnd(): PipeEnd {
  let onMessage: (raw: string) => void = () => {};
  let onClose: () => void = () => {};
  let peer: PipeEnd | null = null;
  let closed = false;

  const end: PipeEnd = {
    send(raw) {
      if (!closed) peer?.deliver(raw);
    },
    onMessage(handler) {
      onMessage = handler;
    },
    onClose(handler) {
      onClose = handler;
    },
    close() {
      if (closed) return;
      closed = true;
      peer?.markClosed();
      onClose();
    },
    deliver(raw) {
      if (!closed) onMessage(raw);
    },
    markClosed() {
      if (closed) return;
      closed = true;
      onClose();
    },
  };

  Object.defineProperty(end, '__setPeer', {
    value: (other: PipeEnd) => {
      peer = other;
    },
    enumerable: false,
  });
  return end;
}

/**
 * A back-to-back pair of channels. `node` goes to the FakeNode; feed everything
 * the backend sends to `backend` and it arrives on the node, and vice versa — no
 * socket, no async scheduling surprises, fully deterministic in a test.
 */
export function channelPair(): { node: NodeChannel; backend: NodeChannel } {
  const a = pipeEnd();
  const b = pipeEnd();
  (a as unknown as { __setPeer: (p: PipeEnd) => void }).__setPeer(b);
  (b as unknown as { __setPeer: (p: PipeEnd) => void }).__setPeer(a);
  return { node: a, backend: b };
}

/**
 * Minimal structural view of a `ws` WebSocket — declared rather than imported so
 * this package stays free of Node/`ws` types while still adapting a real socket
 * when a test supplies one.
 */
export interface WebSocketLike {
  send(data: string): void;
  close(code?: number, reason?: string): void;
  on(event: 'message', handler: (data: unknown) => void): void;
  on(event: 'close', handler: () => void): void;
}

/** Adapts a live `ws` socket to a NodeChannel, for driving an adapter over a real connection. */
export function webSocketChannel(socket: WebSocketLike): NodeChannel {
  return {
    send: (raw) => socket.send(raw),
    onMessage: (handler) => socket.on('message', (data) => handler(String(data))),
    onClose: (handler) => socket.on('close', handler),
    close: (code, reason) => socket.close(code, reason),
  };
}
