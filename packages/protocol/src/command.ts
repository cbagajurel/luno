import { compact, type JsonObject } from "./json";
import {
  booleanWithDefault,
  optionalInt,
  optionalString,
  requireString,
  stringArrayWithDefault,
} from "./internal/decode";

export const COMMAND_TYPES = {
  SEND_SMS: "send_sms",
  CANCEL_SMS: "cancel_sms",
  GET_STATUS: "get_status",
  CONFIG_UPDATE: "config_update",
  REVOKE: "revoke",
  WIPE: "wipe",
} as const;

/**
 * Commands the backend sends to a node (§8.2). `type` discriminates the union
 * here for ergonomics, but it is never serialized into the payload — it lives
 * once, on the envelope, exactly as on the node.
 */
export interface SendSmsCommand {
  type: typeof COMMAND_TYPES.SEND_SMS;
  to: string;
  body: string;
  subscriptionId?: number | null;
  deliveryReport?: boolean;
  ref?: string | null;
}

export interface CancelSmsCommand {
  type: typeof COMMAND_TYPES.CANCEL_SMS;
  commandId: string;
}

export interface GetStatusCommand {
  type: typeof COMMAND_TYPES.GET_STATUS;
}

export interface ConfigUpdateCommand {
  type: typeof COMMAND_TYPES.CONFIG_UPDATE;
  heartbeatSec?: number | null;
  rateLimitPerMinute?: number | null;
  allowlist?: string[] | null;
  credential?: string | null;
}

export interface RevokeCommand {
  type: typeof COMMAND_TYPES.REVOKE;
}

export interface WipeCommand {
  type: typeof COMMAND_TYPES.WIPE;
}

export type Command =
  | SendSmsCommand
  | CancelSmsCommand
  | GetStatusCommand
  | ConfigUpdateCommand
  | RevokeCommand
  | WipeCommand;

export function encodeCommand(command: Command): JsonObject {
  switch (command.type) {
    case COMMAND_TYPES.SEND_SMS:
      return compact({
        to: command.to,
        body: command.body,
        subscriptionId: command.subscriptionId,
        deliveryReport: command.deliveryReport ?? true,
        ref: command.ref,
      });
    case COMMAND_TYPES.CANCEL_SMS:
      return compact({ commandId: command.commandId });
    case COMMAND_TYPES.CONFIG_UPDATE:
      return compact({
        heartbeatSec: command.heartbeatSec,
        rateLimitPerMinute: command.rateLimitPerMinute,
        allowlist: command.allowlist,
        credential: command.credential,
      });
    case COMMAND_TYPES.GET_STATUS:
    case COMMAND_TYPES.REVOKE:
    case COMMAND_TYPES.WIPE:
      return {};
  }
}

export function decodeCommand(
  type: string,
  payload: JsonObject,
): Command | null {
  switch (type) {
    case COMMAND_TYPES.SEND_SMS:
      return {
        type: COMMAND_TYPES.SEND_SMS,
        to: requireString(payload, "to"),
        body: requireString(payload, "body"),
        subscriptionId: optionalInt(payload, "subscriptionId"),
        deliveryReport: booleanWithDefault(payload, "deliveryReport", true),
        ref: optionalString(payload, "ref"),
      };
    case COMMAND_TYPES.CANCEL_SMS:
      return {
        type: COMMAND_TYPES.CANCEL_SMS,
        commandId: requireString(payload, "commandId"),
      };
    case COMMAND_TYPES.CONFIG_UPDATE:
      return {
        type: COMMAND_TYPES.CONFIG_UPDATE,
        heartbeatSec: optionalInt(payload, "heartbeatSec"),
        rateLimitPerMinute: optionalInt(payload, "rateLimitPerMinute"),
        allowlist:
          payload["allowlist"] === undefined || payload["allowlist"] === null
            ? undefined
            : stringArrayWithDefault(payload, "allowlist", []),
        credential: optionalString(payload, "credential"),
      };
    case COMMAND_TYPES.GET_STATUS:
      return { type: COMMAND_TYPES.GET_STATUS };
    case COMMAND_TYPES.REVOKE:
      return { type: COMMAND_TYPES.REVOKE };
    case COMMAND_TYPES.WIPE:
      return { type: COMMAND_TYPES.WIPE };
    default:
      return null;
  }
}
