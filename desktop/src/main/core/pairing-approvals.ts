import type { PairingDecision, PairingRequestContext } from '../server';

/** How long a pairing prompt stays up before it resolves as a timeout. */
export const PAIRING_APPROVAL_MS = 90_000;

interface Pending {
  context: PairingRequestContext;
  deadline: number;
  promise: Promise<PairingDecision>;
  resolve: (decision: PairingDecision) => void;
  timer: NodeJS.Timeout;
}

export interface PairingApprovalsOptions {
  /** Called whenever the prompt appears or goes away. */
  onChange?: () => void;
  onTimeout?: (context: PairingRequestContext) => void;
  onSuperseded?: (context: PairingRequestContext) => void;
  onDisconnected?: (context: PairingRequestContext) => void;
  now?: () => number;
}

/**
 * The pending pairing prompt, and every way it can end.
 *
 * This used to live in AppController, where a stale entry could not be cleared
 * when its socket died: the cleanup ran from the authorized-session close
 * callback, which a pairing socket never reaches. The entry then answered
 * "declined" to every later request until its own timer fired. Keeping the
 * bookkeeping here makes that behaviour testable without Electron.
 */
export class PairingApprovals {
  private pending: Pending | undefined;

  constructor(private readonly options: PairingApprovalsOptions = {}) {}

  /** The prompt currently on screen, if any. */
  get current(): { context: PairingRequestContext; deadline: number } | undefined {
    return this.pending;
  }

  request(context: PairingRequestContext): Promise<PairingDecision> {
    const pending = this.pending;
    if (pending) {
      // A retry over the connection that is already waiting joins the prompt
      // instead of stacking a second one.
      if (pending.context.sessionId === context.sessionId) return pending.promise;
      // A different phone: the newest request wins. Declining the newcomer is
      // what left people staring at "declined" with no dialog at all, because
      // the older entry could belong to a socket that is already gone.
      this.options.onSuperseded?.(pending.context);
      this.settle({ approved: false, reason: 'superseded' });
    }

    let resolve: (decision: PairingDecision) => void = () => undefined;
    const promise = new Promise<PairingDecision>((done) => {
      resolve = done;
    });
    const deadline = this.now() + PAIRING_APPROVAL_MS;
    const timer = setTimeout(() => {
      if (this.pending?.context.id !== context.id) return;
      this.options.onTimeout?.(context);
      this.settle({ approved: false, reason: 'timeout' });
    }, PAIRING_APPROVAL_MS);
    timer.unref?.();

    this.pending = { context, deadline, promise, resolve, timer };
    this.options.onChange?.();
    return promise;
  }

  approve(id: string): void {
    if (this.pending?.context.id !== id) {
      throw new Error('This pairing request is no longer active');
    }
    this.settle({ approved: true });
  }

  reject(id: string): void {
    if (this.pending?.context.id !== id) {
      throw new Error('This pairing request is no longer active');
    }
    this.settle({ approved: false, reason: 'declined' });
  }

  /** The code was cancelled, so the prompt that belongs to it goes too. */
  cancel(): void {
    if (!this.pending) return;
    this.settle({ approved: false, reason: 'cancelled' });
  }

  /** The phone left before the prompt was answered. */
  socketClosed(sessionId: string): void {
    const pending = this.pending;
    if (pending?.context.sessionId !== sessionId) return;
    this.options.onDisconnected?.(pending.context);
    this.settle({ approved: false, reason: 'disconnected' });
  }

  private settle(decision: PairingDecision): void {
    const pending = this.pending;
    if (!pending) return;
    clearTimeout(pending.timer);
    this.pending = undefined;
    pending.resolve(decision);
    this.options.onChange?.();
  }

  private now(): number {
    return this.options.now ? this.options.now() : Date.now();
  }
}
