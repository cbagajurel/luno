import { commandFrame } from '@luno/protocol';
import { auditEvent, type CoreContext } from '../context';
import { isPresent } from '../domain/device';
import { LunoError } from '../domain/errors';
import type { DeviceRecord } from '../ports/store';

export interface DeviceView extends DeviceRecord {
  online: boolean;
}

export function devicesService(context: CoreContext) {
  const view = (device: DeviceRecord): DeviceView => ({
    ...device,
    online: isPresent(device.lastSeenAt, context.clock.now(), context.presenceTimeoutMs),
  });

  const command = (deviceId: string) => ({
    id: context.ids.newId('cmd'),
    ts: new Date(context.clock.now()).toISOString(),
    deviceId,
    seq: 0,
  });

  async function require(deviceId: string): Promise<DeviceRecord> {
    const device = await context.store.devices.findById(deviceId);
    if (!device) throw LunoError.notFound('device');
    return device;
  }

  return {
    async list(): Promise<DeviceView[]> {
      return (await context.store.devices.list()).map(view);
    },

    async get(deviceId: string): Promise<DeviceView> {
      return view(await require(deviceId));
    },

    /**
     * Revokes locally first, then tells the node. The order matters: if the
     * command cannot be delivered the credential is still dead, so a device that
     * was offline during revocation cannot reconnect and keep working.
     */
    async revoke(deviceId: string): Promise<void> {
      const device = await require(deviceId);
      const now = context.clock.now();
      await context.store.devices.update(deviceId, {
        status: 'revoked',
        revokedAt: now,
        phase: 'offline',
      });

      await context.sessions.deliver(deviceId, commandFrame(command(deviceId), { type: 'revoke' }));
      await context.sessions.unregister(deviceId, deviceId);
      await auditEvent(context, {
        deviceId,
        direction: 'system',
        kind: 'system',
        type: 'device.revoked',
        payload: { installId: device.installId },
      });
      await context.hooks.emit('device.revoked', { deviceId });
    },

    async requestStatus(deviceId: string): Promise<void> {
      await require(deviceId);
      const outcome = await context.sessions.deliver(
        deviceId,
        commandFrame(command(deviceId), { type: 'get_status' }),
      );
      if (!outcome.delivered) throw new LunoError('device_offline', 'device is not connected');
    },

    async updateConfig(
      deviceId: string,
      config: {
        heartbeatSec?: number;
        rateLimitPerMinute?: number;
        allowlist?: string[];
        credential?: string;
      },
    ): Promise<void> {
      await require(deviceId);
      const outcome = await context.sessions.deliver(
        deviceId,
        commandFrame(command(deviceId), { type: 'config_update', ...config }),
      );
      if (!outcome.delivered) throw new LunoError('device_offline', 'device is not connected');
    },
  };
}

export type DevicesService = ReturnType<typeof devicesService>;
