import type { PeerSession } from '../server';
import type { MirrorCodec, MirrorStateWire, MirrorTouchWire } from '../../shared/types';
import { TYPES } from '../../shared/types';
import { TYPE_MIRROR, TYPE_VIDEO, type BinaryFrame } from '../core/binary';
import type { Logger } from '../core/logger';

export class MirrorService {
  private state: MirrorStateWire = { active: false, width: 0, height: 0, codec: 'jpeg' };
  private error: string | undefined;
  private session: PeerSession | undefined;

  constructor(
    private readonly logger: Logger,
    private readonly onChange: () => void,
    private readonly onFrame: (frame: BinaryFrame) => void,
  ) {}

  onConnected(session: PeerSession): void {
    this.session = session;
    this.state = { active: false, width: 0, height: 0, codec: 'jpeg' };
    this.error = undefined;
    this.onChange();
  }

  onDisconnected(): void {
    this.session = undefined;
    this.state = { active: false, width: 0, height: 0, codec: 'jpeg' };
    this.error = undefined;
    this.onChange();
  }

  async start(codec: MirrorCodec = 'jpeg'): Promise<void> {
    const session = this.requireSession();
    this.error = undefined;
    await session.request(TYPES.MIRROR_START, { codec }, 15_000);
    this.logger.log('Mirror', `Asked the phone for screen-sharing consent (${codec})`);
    this.onChange();
  }

  async requestKeyframe(): Promise<void> {
    const session = this.requireSession();
    if (!this.state.active || this.state.codec !== 'h264') return;
    await session.request(TYPES.MIRROR_KEYFRAME, {}, 6_000);
  }

  async stop(): Promise<void> {
    const session = this.requireSession();
    try {
      await session.request(TYPES.MIRROR_STOP, {}, 10_000);
    } finally {
      this.state = { active: false, width: 0, height: 0, codec: 'jpeg' };
      this.onChange();
    }
  }

  onPhoneState(data: MirrorStateWire | undefined): void {
    if (!data || typeof data.active !== 'boolean') return;
    this.state = {
      active: data.active,
      width: Number.isFinite(data.width) ? Math.max(0, data.width || 0) : 0,
      height: Number.isFinite(data.height) ? Math.max(0, data.height || 0) : 0,
      message: typeof data.message === 'string' ? data.message.slice(0, 300) : null,
      codec: data.codec === 'h264' ? 'h264' : 'jpeg',
    };
    if (data.message) this.error = data.message;
    if (data.active) this.error = undefined;
    this.logger.log(
      'Mirror',
      data.active
        ? `Mirroring active (${this.state.codec})`
        : `Mirroring stopped${data.message ? `: ${data.message}` : ''}`,
    );
    this.onChange();
  }

  onBinaryFrame(frame: BinaryFrame): void {
    if (!this.state.active) return;
    const expected = this.state.codec === 'h264' ? TYPE_VIDEO : TYPE_MIRROR;
    if (frame.type !== expected) return;
    this.onFrame(frame);
  }

  async touch(touch: MirrorTouchWire): Promise<void> {
    const session = this.requireSession();
    if (touch.action !== 'tap' && touch.action !== 'swipe') {
      throw new Error('Unknown touch action');
    }
    const valid = (value: number) => Number.isFinite(value) && value >= 0 && value <= 1;
    if (!valid(touch.x) || !valid(touch.y)) throw new Error('Invalid touch coordinates');
    if (touch.action === 'swipe' && (touch.x2 == null || touch.y2 == null || !valid(touch.x2) || !valid(touch.y2))) {
      throw new Error('Invalid swipe coordinates');
    }
    await session.request(
      TYPES.MIRROR_TOUCH,
      {
        action: touch.action,
        x: touch.x,
        y: touch.y,
        x2: touch.x2 ?? null,
        y2: touch.y2 ?? null,
        durationMs: Math.max(20, Math.min(1_500, Number(touch.durationMs) || 60)),
      },
      6_000,
    );
  }

  status(): { state: MirrorStateWire; error?: string } {
    return { state: this.state, error: this.error };
  }

  private requireSession(): PeerSession {
    if (!this.session) throw new Error('Connect to your phone first');
    return this.session;
  }
}
