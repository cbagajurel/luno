export { compact } from './json';
export type { JsonObject, JsonPrimitive, JsonValue } from './json';

export { DecodeError } from './internal/decode';

export { PROTOCOL_VERSION, SUPPORTED_PROTOCOL_VERSIONS, negotiateVersion } from './version';

export {
  FRAME_KINDS,
  ackFrame,
  commandFrame,
  controlFrame,
  eventFrame,
} from './envelope';
export type { FrameBody, FrameKind, FrameMeta, ProtocolFrame, RawEnvelope } from './envelope';

export { ACK_TYPE, decodeAck, encodeAck } from './ack';
export type { Ack } from './ack';

export { COMMAND_TYPES, decodeCommand, encodeCommand } from './command';
export type {
  CancelSmsCommand,
  Command,
  ConfigUpdateCommand,
  GetStatusCommand,
  RevokeCommand,
  SendSmsCommand,
  WipeCommand,
} from './command';

export { EVENT_TYPES, decodeEvent, encodeEvent } from './event';
export type {
  BatteryDto,
  DeliveryReportEvent,
  DeviceStatusEvent,
  ErrorEvent,
  Event,
  HeartbeatEvent,
  LogEvent,
  NetworkDto,
  PartSent,
  SignalDto,
  SimDto,
  SmsAcceptedEvent,
  SmsReceivedEvent,
  SmsSentEvent,
} from './event';

export { CONTROL_TYPES, decodeControl, encodeControl } from './control';
export type {
  Control,
  PingControl,
  PongControl,
  ResyncControl,
  VersionNegotiateControl,
} from './control';

export { decodeFrame, encodeFrame, frameType } from './codec';
export type { DecodeResult } from './codec';

export {
  PAIRING_ERROR_CODES,
  PAIRING_REJECTION_CODES,
  REJECTED_HTTP_STATUSES,
  isRejectionCode,
  parseWireCode,
  rejectionHttpStatus,
} from './pairing/errors';
export type { PairingErrorCode, PairingRejectionCode } from './pairing/errors';

export {
  PAIRING_PAYLOAD_VERSION,
  PAIRING_SCHEME_PREFIX,
  buildPairingJson,
  buildPairingUri,
  parsePairingPayload,
} from './pairing/payload';
export type { PairingPayload, PairingPayloadResult } from './pairing/payload';

export {
  ENROLL_PROTOCOL_VERSION,
  ENROLL_STATUS,
  RETRY_AFTER_MAX_MS,
  RETRY_AFTER_MIN_MS,
  approvedEnrollResponse,
  clampRetryAfterMs,
  classifyEnrollFailure,
  decodeEnrollRequest,
  decodeEnrollResponse,
  decodeEnrollStatusRequest,
  deniedEnrollResponse,
  enrollRejection,
  pendingEnrollResponse,
} from './pairing/enroll';
export type {
  DeviceInfo,
  EnrollErrorBody,
  EnrollFailure,
  EnrollRequest,
  EnrollResponse,
  EnrollStatus,
  EnrollStatusRequest,
} from './pairing/enroll';
