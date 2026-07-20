import {
  ackFrame,
  controlFrame,
  decodeFrame,
  encodeFrame,
  eventFrame,
  type Command,
  type Event,
  type FrameMeta,
  type ProtocolFrame,
} from '@luno-oss/protocol';
import type { NodeChannel } from './channel';

export interface FakeNodeOptions {
  deviceId: string;
  /** Answer send_sms/get_status/revoke the way the real agent does. Default true. */
  autoAnswerCommands?: boolean;
  onLog?: (message: string) => void;
}

interface Waiter {
  predicate: (frame: ProtocolFrame) => boolean;
  resolve: (frame: ProtocolFrame) => void;
}

/**
 * A stand-in for the Android node that speaks the real wire protocol: it runs the
 * §6 handshake, acks events, and answers commands with the same event flow as the
 * agent. Drive it over any {@link NodeChannel} — an in-memory pipe for a
 * deterministic unit test, or a real socket to exercise an adapter end to end.
 *
 * It is both the test harness and a runnable demo of a conformant client, so a
 * backend author can watch a full pairing-to-delivery cycle without a phone.
 */
export class FakeNode {
  readonly deviceId: string;
  readonly received: ProtocolFrame[] = [];

  private channel: NodeChannel | null = null;
  private seq = 0;
  private counter = 0;
  private ready = false;
  private readonly autoAnswer: boolean;
  private readonly log: (message: string) => void;
  private readonly waiters = new Set<Waiter>();
  private readonly answered = new Set<string>();

  constructor(options: FakeNodeOptions) {
    this.deviceId = options.deviceId;
    this.autoAnswer = options.autoAnswerCommands ?? true;
    this.log = options.onLog ?? (() => {});
  }

  attach(channel: NodeChannel): this {
    this.channel = channel;
    channel.onMessage((raw) => this.onRaw(raw));
    channel.onClose(() => {
      this.ready = false;
    });
    return this;
  }

  private meta(): FrameMeta {
    this.counter += 1;
    return {
      id: `node-${this.deviceId}-${this.counter}`,
      ts: new Date(this.stableNow()).toISOString(),
      deviceId: this.deviceId,
      seq: (this.seq += 1),
    };
  }

  // A monotonic pseudo-clock keeps frame timestamps ordered without depending on
  // wall-clock resolution, which matters when many frames go out in one tick.
  private clockBase = 1_700_000_000_000;
  private stableNow(): number {
    this.clockBase += 1;
    return this.clockBase;
  }

  private write(frame: ProtocolFrame): void {
    if (!this.channel) throw new Error('FakeNode is not attached to a channel');
    void this.channel.send(encodeFrame(frame));
  }

  private onRaw(raw: string): void {
    const result = decodeFrame(raw);
    if (result.status !== 'ok') {
      this.log(`quarantined ${result.status}: ${result.reason}`);
      return;
    }
    const { frame } = result;
    this.received.push(frame);

    for (const waiter of [...this.waiters]) {
      if (waiter.predicate(frame)) {
        this.waiters.delete(waiter);
        waiter.resolve(frame);
      }
    }

    if (frame.body.kind === 'command' && this.autoAnswer) {
      this.answerCommand(frame.id, frame.body.command);
    }
  }

  /** Resolves with the next inbound frame that matches, checked against the backlog first. */
  waitFor(predicate: (frame: ProtocolFrame) => boolean): Promise<ProtocolFrame> {
    const existing = this.received.find(predicate);
    if (existing) return Promise.resolve(existing);
    return new Promise((resolve) => this.waiters.add({ predicate, resolve }));
  }

  private waitForControl(type: string): Promise<ProtocolFrame> {
    return this.waitFor(
      (frame) => frame.body.kind === 'control' && frame.body.control.type === type,
    );
  }

  /** Runs version_negotiate → resync and resolves once the backend takes us to READY. */
  async handshake(): Promise<void> {
    this.write(controlFrame(this.meta(), { type: 'version_negotiate', supported: [1] }));
    await this.waitForControl('version_negotiate');

    const resync = this.meta();
    this.write(
      controlFrame(resync, { type: 'resync', lastAckedInboundSeq: 0, outstandingOutboxIds: [] }),
    );
    // The backend takes the node to READY with an ack of the resync frame.
    await this.waitFor(
      (frame) => frame.body.kind === 'ack' && frame.body.ack.ackedId === resync.id,
    );
    this.ready = true;
    this.log('READY');
  }

  isReady(): boolean {
    return this.ready;
  }

  sendEvent(event: Event): string {
    const meta = this.meta();
    this.write(eventFrame(meta, event));
    return meta.id;
  }

  /** Sends an event and resolves once the backend acks it, as the durable outbox waits for. */
  async sendEventAwaitingAck(event: Event): Promise<void> {
    const id = this.sendEvent(event);
    await this.waitFor((frame) => frame.body.kind === 'ack' && frame.body.ack.ackedId === id);
  }

  heartbeat(queueDepth = 0): Promise<void> {
    return this.sendEventAwaitingAck({
      type: 'heartbeat',
      queueDepth,
      battery: 87,
      signals: [{ subscriptionId: 1, dbm: -91, level: 3 }],
      transports: ['sms'],
    });
  }

  sendInboundSms(input: { from: string; body: string; subscriptionId?: number }): Promise<void> {
    return this.sendEventAwaitingAck({
      type: 'sms_received',
      from: input.from,
      body: input.body,
      ...(input.subscriptionId !== undefined ? { subscriptionId: input.subscriptionId } : {}),
      receivedAt: this.stableNow(),
      parts: 1,
    });
  }

  private ack(ackedId: string): void {
    this.write(ackFrame(this.meta(), { ackedId }));
  }

  private answerCommand(frameId: string, command: Command): void {
    if (this.answered.has(frameId)) return; // commands are idempotent on their id
    this.answered.add(frameId);
    this.ack(frameId);
    this.log(`command ${command.type}`);

    switch (command.type) {
      case 'send_sms': {
        const messageId = `node-msg-${(this.counter += 1)}`;
        this.sendEvent({ type: 'sms_accepted', commandId: frameId, messageId });
        this.sendEvent({
          type: 'sms_sent',
          messageId,
          parts: [{ index: 0, status: 'SENT' }],
        });
        if (command.deliveryReport !== false) {
          this.sendEvent({
            type: 'delivery_report',
            messageId,
            part: 0,
            status: 'DELIVERED',
            at: this.stableNow(),
          });
        }
        return;
      }

      case 'get_status':
        this.sendEvent({
          type: 'device_status',
          battery: { levelPercent: 87, charging: true, plugged: 'usb', health: 'good' },
          network: { connected: true, validated: true, transport: 'wifi', metered: false },
          sims: [
            {
              subscriptionId: 1,
              slotIndex: 0,
              carrierName: 'FakeCarrier',
              displayName: 'SIM 1',
              embedded: false,
              simState: 'ready',
            },
          ],
        });
        return;

      case 'revoke':
      case 'wipe':
        this.ready = false;
        void this.channel?.close(1000, `${command.type} received`);
        return;

      default:
        return;
    }
  }

  close(): void {
    this.ready = false;
    void this.channel?.close();
  }
}
