# Luno — pairing sessions

The contract between a Luno node and any backend that enrols it. This is the
spec the `@luno-oss/*` server SDKs implement; the Android node implements the client
half exactly as written here.

## The governing rule

**The node enforces no pairing policy.** It does not know whether a pairing
session expires, how many devices it admits, whether it was revoked, or whether
an operator must approve the device. It submits a code and renders the verdict.

Every policy therefore lives on the backend and is configurable without an app
release: expiry or no expiry, one device or many, auto-invalidate on use,
approval gates, device replacement, QR or typed code or both, arbitrary session
metadata. A node that second-guessed any of this would turn a server-side config
change into a client release, which is the failure mode this design exists to
avoid.

Two consequences worth stating explicitly:

- **Policy never travels in the pairing code.** A QR payload carries enrolment
  *inputs* only. If expiry rode in the payload, a stale QR could assert its own
  validity; instead a stale code is simply refused by the server that owns the
  truth.
- **Unknown verdicts survive.** A rejection code this build has never seen
  reaches the UI intact rather than being flattened into "invalid code", so a
  backend can add reasons unilaterally.

## Secure defaults

Defaults belong to the SDK, not the protocol — the node works with any of them.
The recommended defaults for `@luno-oss/*`: one device per session, short expiry,
session invalidated after a successful enrolment, no reuse unless explicitly
configured, and identical rules whether the session is delivered as a QR code or
a typed code.

## Storage

The backend stores only a hash of the pairing code. The plaintext code is
returned once at creation and is never retrievable again. The QR payload carries
the code — it *is* the enrolment bearer secret — and nothing else sensitive; in
particular it never carries a device credential.

## Endpoints

### `POST /enroll`

```jsonc
{
  "protocolVersion": 1,
  "pairingCode": "ABCD-1234",
  "nonce": "iA9…",          // fresh per attempt; reject replays
  "sessionId": "ses_9f3",   // optional, from the QR payload
  "publicKey": null,        // reserved for request signing / mTLS
  "deviceInfo": {
    "model": "Pixel 8",
    "manufacturer": "Google",
    "androidSdk": 34,
    "appVersion": "1.0.0",
    "installId": "b2c1…",   // stable per install, survives unpairing
    "platform": "android"
  }
}
```

`installId` is generated locally by the node — never a hardware identifier — and
is what a device-replacement policy should key on.

Backends validate: the session exists, the submitted code matches the stored
hash, the session has not expired (if expiry is enabled), has not been revoked,
has enrolments remaining, and satisfies any further policy the deployment
defines. All of it policy-driven, none of it assumed by the node.

**Success:**

```jsonc
{ "status": "approved", "deviceId": "dev_9", "credential": "…", "wsUrl": "wss://…/ws" }
```

`status` may be omitted — it defaults to `approved`, so a two-field
`{deviceId, credential}` response from an older server still works. `wsUrl` is
optional; the node derives it from the enrolment host when absent.

**Awaiting approval:**

```jsonc
{ "status": "pending", "enrollmentId": "enr_7", "retryAfterMs": 5000 }
```

The node persists this (sealed, Keystore-bound) and polls. `retryAfterMs` is
clamped to 1s–60s.

**Rejection** — any 4xx/5xx with:

```jsonc
{ "error": "session_expired", "message": "…" }
```

`error` must be a lowercase token (`[a-z0-9][a-z0-9_.:-]*`). Prose is treated as
a message, not a code, and the node falls back to classifying on the HTTP status
(400/401/403/409/410 → invalid code).

Recognised codes — a backend may send others, and the node will show the
accompanying `message`:

| code | meaning |
| --- | --- |
| `invalid_code` | no such session, or the code does not match |
| `session_expired` | session past its expiry |
| `session_exhausted` | enrolment limit reached |
| `session_revoked` | session revoked |
| `already_enrolled` | device registered and replacement not permitted |
| `approval_denied` | operator rejected the device |
| `policy_rejected` | refused by some other deployment policy |

### `POST /enroll/status`

```jsonc
{ "protocolVersion": 1, "enrollmentId": "enr_7", "nonce": "…" }
```

Returns the same shape as `/enroll`: `approved` with a credential, `pending`
again, or `denied`. It exists as a separate endpoint so that waiting for
approval never re-submits the pairing code — under a single-use policy a second
`/enroll` would be correctly rejected as exhausted.

**`enrollmentId` must be unguessable.** On approval this endpoint hands a device
credential to whoever presents the id, which makes it a bearer secret in
practice however it is described elsewhere — a sequential or otherwise
predictable value turns the approval gate into an enrolment bypass. Backends
should mint it from a CSPRNG with at least 128 bits of entropy. `@luno-oss/core`
uses 192.

A node that polls again after `approved` has, by definition, not received the
credential that was issued. Re-issuing on that poll is what makes a dropped
response recoverable; it invalidates the previous credential, so the repeat is
safe rather than a way to accumulate live credentials.

## QR payload

Two interchangeable forms, both versioned:

```
luno://pair?v=1&u=https%3A%2F%2Fgw.example.com&c=ABCD-1234&s=ses_9f3&l=Acme&p=sha256%2FAAAA
{"v":1,"u":"https://gw.example.com","c":"ABCD-1234","s":"ses_9f3","l":"Acme","p":"sha256/AAAA"}
```

| field | meaning |
| --- | --- |
| `v` | payload version; a newer version tells the operator to update rather than being guessed at |
| `u` | enrolment base URL (https; http only in debug builds, for LAN) |
| `c` | pairing code |
| `s` | session id — non-secret handle for lookup and audit |
| `l` | display label, shown on the confirm sheet before enrolling |
| `p` | optional SHA-256 SPKI pin, to bootstrap certificate pinning |

Parsing lives in `backend/auth/PairingPayload.kt` as pure Kotlin — no
`android.net.Uri` — so the format has one implementation, is unit-testable off
device, and is reusable by future desktop/IoT nodes.

## Device registration

On approval the backend registers the device, mints a device identity, issues a
credential, updates the session per its policy (decrement, invalidate, keep
open), and records an audit entry. Enrolment and session authentication stay
separate concerns: the credential is what the WSS handshake presents on every
later connect (§3), and it is stored Keystore-sealed, never logged.

## Extension points

Deliberately reserved so later work is additive rather than a redesign:

- `publicKey` on the enrol request — request signing, mTLS, public-key auth.
- `protocolVersion` on both requests — negotiated evolution.
- `sessionId` and `installId` — organisations, workspaces, bulk provisioning,
  device replacement and lifecycle, audit history.
- `status` — new enrolment outcomes beyond approved/pending/denied.
- `p` in the payload — pinning delivered at pairing time.
- The payload `v` field — new QR formats without breaking installed nodes.

Non-Android node types (desktop agents, IoT) implement this same contract; none
of it is Android-specific.
