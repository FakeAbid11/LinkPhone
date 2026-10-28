import type { PeerSession } from '../server';
import type { NotificationUi, NotificationWire } from '../../shared/types';
import { TYPES } from '../../shared/types';
import type { Store } from '../core/store';
import type { Logger } from '../core/logger';

export class NotificationService {
  private items: NotificationUi[] = [];
  private session: PeerSession | undefined;

  constructor(
    private readonly store: Store,
    private readonly logger: Logger,
    private readonly onChange: () => void,
    private readonly toast: (title: string, body: string) => void,
  ) {}

  onConnected(session: PeerSession): void {
    this.session = session;
    this.items = [];
    this.onChange();
  }

  onDisconnected(): void {
    this.session = undefined;
    this.onChange();
  }

  add(session: PeerSession, data: NotificationWire | undefined): void {
    if (session !== this.session || !data) return;
    if (this.store.settings.mutePackages.includes(data.packageName)) return;
    if (typeof data.key !== 'string' || typeof data.packageName !== 'string') return;

    const item: NotificationUi = {
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

    this.items = [item, ...this.items.filter((existing) => existing.key !== item.key)]
      .sort((a, b) => b.time - a.time)
      .slice(0, 200);
    this.onChange();

    if (this.store.settings.notificationsToasts && item.title) {
      this.toast(item.appName, item.text ? `${item.title} — ${item.text}` : item.title);
    }
  }

  remove(session: PeerSession, key: string | undefined): void {
    if (session !== this.session || typeof key !== 'string') return;
    this.items = this.items.filter((item) => item.key !== key);
    this.onChange();
  }

  async dismiss(key: string): Promise<void> {
    const session = this.session;
    if (!session) throw new Error('Connect to your phone first');
    await session.request(TYPES.NOTIF_DISMISS, { key }, 8_000);
    this.items = this.items.filter((item) => item.key !== key);
    this.logger.log('Notif', 'Dismissed a notification on the phone');
    this.onChange();
  }

  setMuted(pkg: string, muted: boolean): void {
    const existing = new Set(this.store.settings.mutePackages);
    if (muted) existing.add(pkg);
    else existing.delete(pkg);
    this.store.updateSettings({ mutePackages: [...existing] });
    if (muted) {
      this.items = this.items.filter((item) => item.packageName !== pkg);
    }
    this.onChange();
  }

  list(): NotificationUi[] {
    return this.items;
  }
}
