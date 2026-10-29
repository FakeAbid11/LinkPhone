import type { PeerSession } from '../server';
import type { NotificationListResultWire, NotificationUi, NotificationWire } from '../../shared/types';
import { TYPES } from '../../shared/types';
import type { Store } from '../core/store';
import type { Logger } from '../core/logger';

export interface NotificationStatus {
  items: NotificationUi[];
  error?: string;
  refreshing: boolean;
}

const MAX_ITEMS = 100;
const REFRESH_DELAY_MS = 300;

/**
 * A live mirror of the phone's notifications. The phone pushes every change and
 * the PC re-reads the full set, so a notification whose count changed is
 * rewritten in place rather than kept as a stale card.
 */
export class NotificationService {
  private items: NotificationUi[] = [];
  private seen = new Set<string>();
  private loading = false;
  private queue: Promise<unknown> = Promise.resolve();
  private error: string | undefined;
  private session: PeerSession | undefined;
  private debounce: NodeJS.Timeout | undefined;
  /** The first sync after connecting must not pop up old notifications. */
  private primed = false;

  constructor(
    private readonly store: Store,
    private readonly logger: Logger,
    private readonly onChange: () => void,
    private readonly toast: (title: string, body: string) => void,
  ) {}

  onConnected(session: PeerSession): void {
    this.session = session;
    this.items = [];
    this.seen.clear();
    this.error = undefined;
    this.loading = false;
    this.primed = false;
    if (this.debounce) clearTimeout(this.debounce);
    this.debounce = undefined;
    this.onChange();
    void this.refresh();
  }

  onDisconnected(): void {
    this.session = undefined;
    this.loading = false;
    this.primed = false;
    this.seen.clear();
    if (this.debounce) clearTimeout(this.debounce);
    this.debounce = undefined;
    this.onChange();
  }

  /** The phone said its notifications moved; re-read them shortly. */
  markDirty(session: PeerSession): void {
    if (session !== this.session) return;
    if (this.debounce) clearTimeout(this.debounce);
    this.debounce = setTimeout(() => {
      this.debounce = undefined;
      void this.refresh();
    }, REFRESH_DELAY_MS);
  }

  /** Loads run one after another so two of them never fight over the list. */
  async refresh(): Promise<void> {
    const run = this.queue.then(() => this.load());
    this.queue = run.catch(() => undefined);
    await run;
  }

  private async load(): Promise<void> {
    const session = this.session;
    if (!session) return;
    this.loading = true;
    this.error = undefined;
    this.onChange();
    try {
      const response = await session.request<NotificationListResultWire>(
        TYPES.NOTIF_LIST,
        undefined,
        15_000,
      );
      const data = response.data;
      if (!data || !Array.isArray(data.notifications)) {
        throw new Error('The phone returned an invalid notification list');
      }
      this.apply(data.notifications);
      this.primed = true;
      this.logger.log('Notif', `Synced ${this.items.length} notifications`);
    } catch (error) {
      this.error = error instanceof Error ? error.message : 'Could not load notifications';
      this.logger.log('Notif', `Refresh failed: ${this.error}`);
    } finally {
      this.loading = false;
      this.onChange();
    }
  }

  /** A notification was posted. Known keys are rewritten in place. */
  add(session: PeerSession, data: NotificationWire | undefined): void {
    if (session !== this.session || !data) return;
    const item = this.toItem(data);
    if (!item) return;
    this.items = [item, ...this.items.filter((existing) => existing.key !== item.key)]
      .sort((a, b) => b.time - a.time)
      .slice(0, MAX_ITEMS);
    this.surface(item);
    this.onChange();
  }

  remove(session: PeerSession, key: string | undefined): void {
    if (session !== this.session || typeof key !== 'string') return;
    this.items = this.items.filter((item) => item.key !== key);
    this.seen.delete(key);
    this.onChange();
  }

  async dismiss(key: string): Promise<void> {
    const session = this.session;
    if (!session) throw new Error('Connect to your phone first');
    await session.request(TYPES.NOTIF_DISMISS, { key }, 8_000);
    this.items = this.items.filter((item) => item.key !== key);
    this.seen.delete(key);
    this.logger.log('Notif', 'Dismissed a notification on the phone');
    this.onChange();
  }

  setMuted(pkg: string, muted: boolean): void {
    const existing = new Set(this.store.settings.mutePackages);
    if (muted) existing.add(pkg);
    else existing.delete(pkg);
    this.store.updateSettings({ mutePackages: [...existing] });
    if (muted) {
      // The keys stay in `seen`, so unmuting does not re-pop old notifications.
      this.items = this.items.filter((item) => item.packageName !== pkg);
    }
    this.onChange();
  }

  status(): NotificationStatus {
    return { items: this.items, error: this.error, refreshing: this.loading };
  }

  /** Replaces the mirrored set with what the phone reports right now. */
  private apply(notifications: NotificationWire[]): void {
    const next: NotificationUi[] = [];
    for (const wire of notifications) {
      const item = this.toItem(wire);
      if (!item) continue;
      next.push(item);
    }
    next.sort((a, b) => b.time - a.time);
    this.items = next.slice(0, MAX_ITEMS);
    // Keys stay in `seen` even when the phone drops them, so a notification
    // that comes back does not pop a second toast. `remove()` forgets them.
    for (const item of this.items) this.surface(item);
  }

  /** Shows a card and pops a toast only for a notification the PC has not seen. */
  private surface(item: NotificationUi): void {
    if (this.seen.has(item.key)) return;
    this.seen.add(item.key);
    if (!this.primed) return;
    if (!this.store.settings.notificationsToasts || !item.title) return;
    this.toast(item.appName, item.text ? `${item.title} — ${item.text}` : item.title);
  }

  private toItem(data: NotificationWire): NotificationUi | null {
    if (typeof data.key !== 'string' || typeof data.packageName !== 'string') return null;
    if (this.store.settings.mutePackages.includes(data.packageName)) return null;
    return {
      key: data.key.slice(0, 512),
      packageName: data.packageName.slice(0, 200),
      appName: typeof data.appName === 'string' ? data.appName.slice(0, 100) : data.packageName,
      title: typeof data.title === 'string' ? data.title.slice(0, 300) : null,
      text: typeof data.text === 'string' ? data.text.slice(0, 2_000) : null,
      subText: typeof data.subText === 'string' ? data.subText.slice(0, 300) : null,
      category: typeof data.category === 'string' ? data.category.slice(0, 80) : null,
      ongoing: Boolean(data.ongoing),
      time: Number.isFinite(data.time) ? data.time : Date.now(),
    };
  }
}
