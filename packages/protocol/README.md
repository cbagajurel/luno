# @luno-oss/protocol

Zero-dependency types and codecs for the Luno wire protocol — the contract
between a Luno node and any backend that drives it.

This package is the innermost layer of the Luno backend SDK (see
[`docs/backend-sdk.md`](../../docs/backend-sdk.md)). It contains **no business
logic and no I/O**: just the frame types, their canonical encoding, the pairing
payload format, and the enrolment error taxonomy. `@luno-oss/core` builds on it, and
client SDKs can depend on it for types alone without pulling in a server engine.

## Runtime neutrality

The package targets the intersection of Node 18+, Cloudflare Workers, Deno and
Bun: pure ESM, no `node:*` imports, no `Buffer`, no ambient I/O. That is enforced
mechanically rather than by convention — `tsconfig.json` compiles with
`"types": []` and `"lib": ["ES2022"]`, so reaching for a platform global fails
the build.

## Wire fidelity

The encoding mirrors the node's `kotlinx.serialization` configuration
(`encodeDefaults = true`, `explicitNulls = false`) exactly:

- **defaults are written** — `send_sms` always carries `deliveryReport`, and
  empty collections such as `sims`, `signals` and `transports` are present;
- **absent fields are omitted**, never serialized as `null`;
- **keys follow the node's declaration order**, so output is byte-identical.

`fixtures/frames.json` is the shared corpus that keeps this honest. Both this
package and the node's `ProtocolFixturesTest` decode every frame in it and assert
their re-encoding is byte-identical, so the two implementations cannot drift
apart without a test failing on one side.

## Forward compatibility

Decoding never throws. An unknown `(kind, type)` — a command from a newer
backend, an event from a newer node — comes back as `unsupported` with the
envelope intact, and structurally broken input comes back as `malformed` with the
raw text. Both are for the caller to quarantine and carry on (§8.5).

```ts
import { decodeFrame, encodeFrame, commandFrame } from '@luno-oss/protocol';

const result = decodeFrame(raw);
switch (result.status) {
  case 'ok':
    return handle(result.frame);
  case 'unsupported':
    return quarantine(result.envelope, result.reason);
  case 'malformed':
    return quarantine(result.raw, result.reason);
}

const wire = encodeFrame(
  commandFrame({ id, ts, deviceId, seq }, { type: 'send_sms', to, body }),
);
```

## Pairing

Parsing and building both QR forms, plus the enrolment DTOs and the rejection
taxonomy from [`docs/pairing.md`](../../docs/pairing.md):

```ts
import { buildPairingUri, enrollRejection, decodeEnrollRequest } from '@luno-oss/protocol';

buildPairingUri({ backendUrl, pairingCode, sessionId, label });
// luno://pair?v=1&u=https%3A%2F%2F…&c=ABCD-1234&s=ses_9f3&l=Acme

const { status, body } = enrollRejection('session_expired', 'code has expired');
```

Rejection statuses are drawn from the set the node treats as "code rejected", so
a backend can introduce a brand-new `error` token and an older node still reaches
the right conclusion from the HTTP status alone.

## Scripts

```
pnpm test        # vitest
pnpm typecheck   # src (runtime-neutral) and tests
pnpm build       # dual ESM/CJS + .d.ts via tsup
```
