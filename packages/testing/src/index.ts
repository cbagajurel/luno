export { FakeNode } from './fake-node';
export type { FakeNodeOptions } from './fake-node';

export { channelPair, webSocketChannel } from './channel';
export type { NodeChannel, WebSocketLike } from './channel';

export {
  EnrollError,
  defaultDeviceInfo,
  enrollNode,
  fetchTransport,
} from './enroll';
export type { EnrollResult, EnrollTransport } from './enroll';

// The store conformance suite lives at `@luno/testing/store`, not here: it pulls
// in vitest, and this entry must stay importable from a plain demo script or a
// running server that has no test runner in scope.
