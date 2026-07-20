import type { DeviceInfo } from '@luno-oss/protocol';
import type { ConnectionPhase, DeviceStatus } from '../domain/device';
import type { MessageStatus } from '../domain/message';

export interface PairingSessionRecord {
  id: string;
  codeHash: string;
  label: string | null;
  createdAt: number;
  createdBy: string | null;
  /** null means the session never expires. */
  expiresAt: number | null;
  /** null admits unlimited enrolments. */
  maxEnrollments: number | null;
  enrollmentsUsed: number;
  requireApproval: boolean;
  allowReplacement: boolean;
  revokedAt: number | null;
  metadata: Record<string, string> | null;
}

export type ClaimOutcome =
  | { status: 'claimed'; session: PairingSessionRecord }
  | { status: 'not_found' }
  | { status: 'revoked' | 'expired' | 'exhausted'; session: PairingSessionRecord };

export interface PairingSessionStore {
  create(record: PairingSessionRecord): Promise<void>;
  findById(sessionId: string): Promise<PairingSessionRecord | null>;
  findByCodeHash(codeHash: string): Promise<PairingSessionRecord | null>;

  /**
   * Atomically take one enrolment slot.
   *
   * **This operation MUST be linearizable.** Concurrent callers for the same
   * session must between them see at most `maxEnrollments` `claimed` outcomes —
   * a read-then-write implementation is incorrect and will, under load, admit
   * two devices to a single-use session. Implementations must express it as one
   * conditional write:
   *
   * - Postgres/MySQL — `UPDATE … SET enrollments_used = enrollments_used + 1
   *   WHERE id = $1 AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at
   *   > $2) AND (max_enrollments IS NULL OR enrollments_used < max_enrollments)
   *   RETURNING *`, then re-read to classify a miss.
   * - Firestore/D1 — inside `runTransaction` / a transaction.
   * - KV-only stores — a compare-and-swap retry loop on a version field.
   *
   * The predicate is deliberately data-level (compare the record's own
   * `revokedAt`, `expiresAt` and `enrollmentsUsed` against `now`) and carries no
   * business policy: how long a session lives and how many devices it admits are
   * decided by the domain at creation and baked into the record.
   *
   * `@luno-oss/core`'s store conformance suite asserts this under real concurrency.
   */
  claim(input: { sessionId: string; now: number }): Promise<ClaimOutcome>;

  /** Hand a claimed slot back when enrolment fails after the claim succeeded. */
  release(sessionId: string): Promise<void>;

  revoke(sessionId: string, now: number): Promise<void>;
  list(): Promise<PairingSessionRecord[]>;
}

export interface DeviceRecord {
  id: string;
  sessionId: string | null;
  credentialHash: string;
  installId: string;
  info: DeviceInfo;
  status: DeviceStatus;
  phase: ConnectionPhase;
  pairedAt: number;
  lastSeenAt: number | null;
  revokedAt: number | null;
}

export type DevicePatch = Partial<
  Pick<DeviceRecord, 'credentialHash' | 'status' | 'phase' | 'lastSeenAt' | 'revokedAt' | 'info'>
>;

export interface DeviceStore {
  create(record: DeviceRecord): Promise<void>;
  findById(deviceId: string): Promise<DeviceRecord | null>;
  /** Credentials are looked up by hash, so the plaintext never needs comparing. */
  findByCredentialHash(credentialHash: string): Promise<DeviceRecord | null>;
  findByInstallId(installId: string): Promise<DeviceRecord | null>;
  update(deviceId: string, patch: DevicePatch): Promise<void>;
  list(): Promise<DeviceRecord[]>;
}

export type EnrollmentStatus = 'pending' | 'approved' | 'denied' | 'completed';

export interface EnrollmentRecord {
  id: string;
  sessionId: string;
  installId: string;
  info: DeviceInfo;
  status: EnrollmentStatus;
  createdAt: number;
  decidedAt: number | null;
  deviceId: string | null;
}

export interface EnrollmentStore {
  create(record: EnrollmentRecord): Promise<void>;
  findById(enrollmentId: string): Promise<EnrollmentRecord | null>;
  update(
    enrollmentId: string,
    patch: Partial<Pick<EnrollmentRecord, 'status' | 'decidedAt' | 'deviceId'>>,
  ): Promise<void>;
  list(): Promise<EnrollmentRecord[]>;
}

export interface MessagePart {
  index: number;
  status: string;
  errorCode: string | null;
}

export interface MessageRecord {
  id: string;
  deviceId: string;
  to: string;
  body: string;
  subscriptionId: number | null;
  ref: string | null;
  deliveryReport: boolean;
  status: MessageStatus;
  /** Envelope id of the `send_sms` frame, which is what the node acks and resyncs on. */
  commandId: string | null;
  /** The node's own message id, learned from `sms_accepted`. */
  nodeMessageId: string | null;
  parts: MessagePart[];
  error: string | null;
  createdAt: number;
  updatedAt: number;
}

export type MessagePatch = Partial<
  Pick<MessageRecord, 'status' | 'commandId' | 'nodeMessageId' | 'parts' | 'error' | 'updatedAt'>
>;

export interface MessageStore {
  create(record: MessageRecord): Promise<void>;
  findById(messageId: string): Promise<MessageRecord | null>;
  findByCommandId(commandId: string): Promise<MessageRecord | null>;
  findByNodeMessageId(deviceId: string, nodeMessageId: string): Promise<MessageRecord | null>;
  update(messageId: string, patch: MessagePatch): Promise<void>;
  /** Non-terminal messages for a device, oldest first — the resync working set. */
  listOutstanding(deviceId: string): Promise<MessageRecord[]>;
  listByDevice(deviceId: string, limit: number): Promise<MessageRecord[]>;
}

export interface EventLogRecord {
  id: string;
  deviceId: string | null;
  direction: 'in' | 'out' | 'system';
  kind: string;
  type: string;
  payload: unknown;
  frameId: string | null;
  at: number;
}

export interface EventLogStore {
  append(record: EventLogRecord): Promise<void>;
  list(filter: { deviceId?: string; limit: number }): Promise<EventLogRecord[]>;
}

/** The full persistence surface, injected as one bundle at composition time. */
export interface LunoStore {
  pairingSessions: PairingSessionStore;
  devices: DeviceStore;
  enrollments: EnrollmentStore;
  messages: MessageStore;
  events: EventLogStore;
}
