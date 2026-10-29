import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { Logger } from '../src/main/core/logger';
import { Store } from '../src/main/core/store';
import { NotificationService } from '../src/main/services/notifications';
import type { PeerSession } from '../src/main/server';
import type { NotificationWire } from '../src/shared/types';
import { TYPES } from '../src/shared/types';

const dirs: string[] = [];

function wire(
  key: string,
  packageName: string,
  title: string,
  text: string,
  time: number,
): NotificationWire {
  return { key, packageName, appName: 'Messenger', title, text, ongoing: false, time };
}

function harness(options?: { notifications?: NotificationWire[] }) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-notifications-'));
  dirs.push(dir);
  const store = new Store(path.join(dir, 'state.json'));
  const toasts: Array<{ title: string; body: string }> = [];
  const requests: Array<{ type: string; data: unknown }> = [];
  let notifications = options?.notifications ?? [];
  let failList = false;
  const session = {
    device: { id: 'phone-1' },
    request: async (type: string, data: unknown) => {
      requests.push({ type, data });
      if (type === TYPES.NOTIF_LIST) {
        if (failList) throw new Error('The phone did not answer');
        return {
          v: 1,
          id: 'reply',
          type: 'response',
          ok: true,
          data: { notifications },
          ts: Date.now(),
        };
      }
      return {
        v: 1,
        id: 'reply',
        type: 'response',
        ok: true,
        data: { ok: true },
        ts: Date.now(),
      };
    },
  } as unknown as PeerSession;
  const service = new NotificationService(
    new Logger(path.join(dir, 'log.txt')),
    store,
    () => undefined,
    (title, body) => toasts.push({ title, body }),
  );
  return {
    dir,
    store,
    session,
    service,
    toasts,
    requests,
    setNotifications: (next: NotificationWire[]) => {
      notifications = next;
    },
    setFailList: (value: boolean) => {
      failList = value;
    },
  };
}

afterEach(() => {
  for (const dir of dirs.splice(0)) fs.rmSync(dir, { recursive: true, force: true });
});

describe('Live notification mirror', () => {
  it('shows the phone notifications on connect without popping old ones up', async () => {
    const { session, service, toasts } = harness({
      notifications: [wire('a', 'com.messenger', 'Ada', '1 new message', 1_000)],
    });
    service.onConnected(session);
    await service.refresh();

    expect(service.status().items.map((item) => item.key)).toEqual(['a']);
    expect(service.status().items[0].text).toBe('1 new message');
    expect(toasts).toHaveLength(0);
  });

  it('rewrites a notification in place when its number changes', async () => {
    const { session, service, toasts } = harness();
    service.onConnected(session);
    await service.refresh();

    service.add(session, wire('a', 'com.messenger', 'Ada', '1 new message', 1_000));
    expect(service.status().items).toHaveLength(1);
    expect(toasts).toHaveLength(1);

    service.add(session, wire('a', 'com.messenger', 'Ada', '4 new messages', 2_000));
    expect(service.status().items).toHaveLength(1);
    expect(service.status().items[0].text).toBe('4 new messages');
    expect(toasts).toHaveLength(1);
  });

  it('re-reads the phone when the notifications change', async () => {
    const { session, service, requests, setNotifications } = harness();
    service.onConnected(session);
    await service.refresh();
    const before = requests.length;

    setNotifications([wire('a', 'com.messenger', 'Ada', '4 new messages', 1_000)]);
    service.markDirty(session);
    await new Promise((resolve) => setTimeout(resolve, 400));

    expect(requests.length).toBeGreaterThan(before);
    expect(requests.some((entry) => entry.type === TYPES.NOTIF_LIST)).toBe(true);
    expect(service.status().items[0].text).toBe('4 new messages');
  });

  it('drops a notification the phone no longer has', async () => {
    const { session, service, setNotifications } = harness({
      notifications: [wire('a', 'com.messenger', 'Ada', '1 new message', 1_000)],
    });
    service.onConnected(session);
    await service.refresh();
    expect(service.status().items).toHaveLength(1);

    setNotifications([]);
    await service.refresh();
    expect(service.status().items).toHaveLength(0);
  });

  it('keeps the last list when the phone cannot answer', async () => {
    const { session, service, setFailList } = harness({
      notifications: [wire('a', 'com.messenger', 'Ada', '1 new message', 1_000)],
    });
    service.onConnected(session);
    await service.refresh();
    expect(service.status().items).toHaveLength(1);

    setFailList(true);
    await service.refresh();
    expect(service.status().items).toHaveLength(1);
    expect(service.status().error).toContain('did not answer');
  });

  it('never mirrors a muted app', async () => {
    const { session, service, store } = harness({
      notifications: [
        wire('a', 'com.messenger', 'Ada', '1 new message', 1_000),
        wire('b', 'com.spam', 'Ad', 'buy now', 2_000),
      ],
    });
    service.onConnected(session);
    service.setMuted('com.spam', true);
    await service.refresh();

    expect(service.status().items.map((item) => item.packageName)).toEqual(['com.messenger']);
    expect(store.settings.mutePackages).toContain('com.spam');
  });

  it('removes a notification that was dismissed on the phone', async () => {
    const { session, service } = harness({
      notifications: [wire('a', 'com.messenger', 'Ada', '1 new message', 1_000)],
    });
    service.onConnected(session);
    await service.refresh();

    service.remove(session, 'a');
    expect(service.status().items).toHaveLength(0);
  });

  it('drops the cards of a muted app but keeps them quiet when unmuted', async () => {
    const { session, service, toasts } = harness({
      notifications: [wire('a', 'com.spam', 'Ad', 'buy now', 1_000)],
    });
    service.onConnected(session);
    await service.refresh();
    expect(service.status().items).toHaveLength(1);

    service.setMuted('com.spam', true);
    expect(service.status().items).toHaveLength(0);

    service.setMuted('com.spam', false);
    await service.refresh();
    expect(service.status().items).toHaveLength(1);
    expect(toasts).toHaveLength(0);
  });
});
