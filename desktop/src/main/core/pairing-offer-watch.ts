import { shouldRenewOffer } from './network';

/** Fast enough that a hotspot switch is invisible, slow enough to cost nothing. */
const RENEW_CHECK_INTERVAL_MS = 10_000;

export interface PairingOfferWatcherOptions {
  /** Called each tick. Must be cheap and socket-free — see `listLanAddresses`. */
  addresses: () => string[];
  /** Read each tick, not stored: the offer is replaced behind the watcher's back. */
  offerHost: () => string | undefined;
  /** Build a new code. May be async; an overlapping tick is dropped, not queued. */
  onStale: () => Promise<void> | void;
}

/**
 * Renews a pairing code whose address this PC no longer has.
 *
 * A class rather than five lines inside the controller because `app.ts` imports
 * Electron at module scope and cannot be loaded by the test runner — the same
 * reason `UpdateService` and `PairingApprovals` live here. The decision itself is
 * `shouldRenewOffer`; this only owns the timer and the re-entrancy guard.
 */
export class PairingOfferWatcher {
  private timer: NodeJS.Timeout | undefined;
  private busy = false;

  constructor(private readonly options: PairingOfferWatcherOptions) {}

  start(): void {
    if (this.timer) return;
    this.timer = setInterval(() => void this.check(), RENEW_CHECK_INTERVAL_MS);
    this.timer.unref?.();
  }

  stop(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = undefined;
  }

  /** Public so a test can drive one tick without touching timers. */
  async check(): Promise<void> {
    if (this.busy) return;
    if (!shouldRenewOffer(this.options.offerHost(), this.options.addresses())) return;
    this.busy = true;
    try {
      await this.options.onStale();
    } finally {
      // Cleared even when renewal throws, so one failure does not stop every
      // later tick from trying again.
      this.busy = false;
    }
  }
}
