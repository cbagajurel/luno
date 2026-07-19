import { compact, type JsonObject } from './json';
import {
  intWithDefault,
  objectArrayWithDefault,
  optionalInt,
  optionalObject,
  optionalString,
  requireBoolean,
  requireInt,
  requireObjectArray,
  requireString,
  stringArrayWithDefault,
} from './internal/decode';

export const EVENT_TYPES = {
  SMS_ACCEPTED: 'sms_accepted',
  SMS_SENT: 'sms_sent',
  DELIVERY_REPORT: 'delivery_report',
  SMS_RECEIVED: 'sms_received',
  DEVICE_STATUS: 'device_status',
  HEARTBEAT: 'heartbeat',
  LOG: 'log',
  ERROR: 'error',
} as const;

export interface PartSent {
  index: number;
  status: string;
  errorCode?: string | null;
}

export interface BatteryDto {
  levelPercent: number;
  charging: boolean;
  plugged: string;
  health: string;
}

export interface NetworkDto {
  connected: boolean;
  validated: boolean;
  transport: string;
  metered: boolean;
}

export interface SimDto {
  subscriptionId: number;
  slotIndex: number;
  carrierName: string;
  displayName: string;
  embedded: boolean;
  simState: string;
}

export interface SignalDto {
  subscriptionId: number;
  dbm?: number | null;
  level: number;
}

/** Events a node sends to the backend (§8.3). */
export interface SmsAcceptedEvent {
  type: typeof EVENT_TYPES.SMS_ACCEPTED;
  commandId: string;
  messageId: string;
}

export interface SmsSentEvent {
  type: typeof EVENT_TYPES.SMS_SENT;
  messageId: string;
  parts: PartSent[];
}

export interface DeliveryReportEvent {
  type: typeof EVENT_TYPES.DELIVERY_REPORT;
  messageId: string;
  part: number;
  status: string;
  at: number;
}

export interface SmsReceivedEvent {
  type: typeof EVENT_TYPES.SMS_RECEIVED;
  from: string;
  body: string;
  subscriptionId?: number | null;
  receivedAt: number;
  parts?: number;
}

export interface DeviceStatusEvent {
  type: typeof EVENT_TYPES.DEVICE_STATUS;
  battery?: BatteryDto | null;
  network?: NetworkDto | null;
  sims?: SimDto[];
}

export interface HeartbeatEvent {
  type: typeof EVENT_TYPES.HEARTBEAT;
  queueDepth: number;
  battery?: number | null;
  signals?: SignalDto[];
  transports?: string[];
}

export interface LogEvent {
  type: typeof EVENT_TYPES.LOG;
  level: string;
  tag: string;
  msg: string;
  at: number;
}

export interface ErrorEvent {
  type: typeof EVENT_TYPES.ERROR;
  code: string;
  message: string;
  ref?: string | null;
}

export type Event =
  | SmsAcceptedEvent
  | SmsSentEvent
  | DeliveryReportEvent
  | SmsReceivedEvent
  | DeviceStatusEvent
  | HeartbeatEvent
  | LogEvent
  | ErrorEvent;

const encodePartSent = (part: PartSent): JsonObject =>
  compact({ index: part.index, status: part.status, errorCode: part.errorCode });

const encodeBattery = (battery: BatteryDto): JsonObject =>
  compact({
    levelPercent: battery.levelPercent,
    charging: battery.charging,
    plugged: battery.plugged,
    health: battery.health,
  });

const encodeNetwork = (network: NetworkDto): JsonObject =>
  compact({
    connected: network.connected,
    validated: network.validated,
    transport: network.transport,
    metered: network.metered,
  });

const encodeSim = (sim: SimDto): JsonObject =>
  compact({
    subscriptionId: sim.subscriptionId,
    slotIndex: sim.slotIndex,
    carrierName: sim.carrierName,
    displayName: sim.displayName,
    embedded: sim.embedded,
    simState: sim.simState,
  });

const encodeSignal = (signal: SignalDto): JsonObject =>
  compact({ subscriptionId: signal.subscriptionId, dbm: signal.dbm, level: signal.level });

const decodePartSent = (o: JsonObject): PartSent => ({
  index: requireInt(o, 'index'),
  status: requireString(o, 'status'),
  errorCode: optionalString(o, 'errorCode'),
});

const decodeBattery = (o: JsonObject): BatteryDto => ({
  levelPercent: requireInt(o, 'levelPercent'),
  charging: requireBoolean(o, 'charging'),
  plugged: requireString(o, 'plugged'),
  health: requireString(o, 'health'),
});

const decodeNetwork = (o: JsonObject): NetworkDto => ({
  connected: requireBoolean(o, 'connected'),
  validated: requireBoolean(o, 'validated'),
  transport: requireString(o, 'transport'),
  metered: requireBoolean(o, 'metered'),
});

const decodeSim = (o: JsonObject): SimDto => ({
  subscriptionId: requireInt(o, 'subscriptionId'),
  slotIndex: requireInt(o, 'slotIndex'),
  carrierName: requireString(o, 'carrierName'),
  displayName: requireString(o, 'displayName'),
  embedded: requireBoolean(o, 'embedded'),
  simState: requireString(o, 'simState'),
});

const decodeSignal = (o: JsonObject): SignalDto => ({
  subscriptionId: requireInt(o, 'subscriptionId'),
  dbm: optionalInt(o, 'dbm'),
  level: requireInt(o, 'level'),
});

export function encodeEvent(event: Event): JsonObject {
  switch (event.type) {
    case EVENT_TYPES.SMS_ACCEPTED:
      return compact({ commandId: event.commandId, messageId: event.messageId });
    case EVENT_TYPES.SMS_SENT:
      return compact({ messageId: event.messageId, parts: event.parts.map(encodePartSent) });
    case EVENT_TYPES.DELIVERY_REPORT:
      return compact({
        messageId: event.messageId,
        part: event.part,
        status: event.status,
        at: event.at,
      });
    case EVENT_TYPES.SMS_RECEIVED:
      return compact({
        from: event.from,
        body: event.body,
        subscriptionId: event.subscriptionId,
        receivedAt: event.receivedAt,
        parts: event.parts ?? 1,
      });
    case EVENT_TYPES.DEVICE_STATUS:
      return compact({
        battery: event.battery ? encodeBattery(event.battery) : undefined,
        network: event.network ? encodeNetwork(event.network) : undefined,
        sims: (event.sims ?? []).map(encodeSim),
      });
    case EVENT_TYPES.HEARTBEAT:
      return compact({
        queueDepth: event.queueDepth,
        battery: event.battery,
        signals: (event.signals ?? []).map(encodeSignal),
        transports: event.transports ?? [],
      });
    case EVENT_TYPES.LOG:
      return compact({ level: event.level, tag: event.tag, msg: event.msg, at: event.at });
    case EVENT_TYPES.ERROR:
      return compact({ code: event.code, message: event.message, ref: event.ref });
  }
}

export function decodeEvent(type: string, payload: JsonObject): Event | null {
  switch (type) {
    case EVENT_TYPES.SMS_ACCEPTED:
      return {
        type: EVENT_TYPES.SMS_ACCEPTED,
        commandId: requireString(payload, 'commandId'),
        messageId: requireString(payload, 'messageId'),
      };
    case EVENT_TYPES.SMS_SENT:
      return {
        type: EVENT_TYPES.SMS_SENT,
        messageId: requireString(payload, 'messageId'),
        parts: requireObjectArray(payload, 'parts', decodePartSent),
      };
    case EVENT_TYPES.DELIVERY_REPORT:
      return {
        type: EVENT_TYPES.DELIVERY_REPORT,
        messageId: requireString(payload, 'messageId'),
        part: requireInt(payload, 'part'),
        status: requireString(payload, 'status'),
        at: requireInt(payload, 'at'),
      };
    case EVENT_TYPES.SMS_RECEIVED:
      return {
        type: EVENT_TYPES.SMS_RECEIVED,
        from: requireString(payload, 'from'),
        body: requireString(payload, 'body'),
        subscriptionId: optionalInt(payload, 'subscriptionId'),
        receivedAt: requireInt(payload, 'receivedAt'),
        parts: intWithDefault(payload, 'parts', 1),
      };
    case EVENT_TYPES.DEVICE_STATUS:
      return {
        type: EVENT_TYPES.DEVICE_STATUS,
        battery: optionalObject(payload, 'battery', decodeBattery),
        network: optionalObject(payload, 'network', decodeNetwork),
        sims: objectArrayWithDefault(payload, 'sims', [], decodeSim),
      };
    case EVENT_TYPES.HEARTBEAT:
      return {
        type: EVENT_TYPES.HEARTBEAT,
        queueDepth: requireInt(payload, 'queueDepth'),
        battery: optionalInt(payload, 'battery'),
        signals: objectArrayWithDefault(payload, 'signals', [], decodeSignal),
        transports: stringArrayWithDefault(payload, 'transports', []),
      };
    case EVENT_TYPES.LOG:
      return {
        type: EVENT_TYPES.LOG,
        level: requireString(payload, 'level'),
        tag: requireString(payload, 'tag'),
        msg: requireString(payload, 'msg'),
        at: requireInt(payload, 'at'),
      };
    case EVENT_TYPES.ERROR:
      return {
        type: EVENT_TYPES.ERROR,
        code: requireString(payload, 'code'),
        message: requireString(payload, 'message'),
        ref: optionalString(payload, 'ref'),
      };
    default:
      return null;
  }
}
