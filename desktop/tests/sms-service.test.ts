import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { Logger } from '../src/main/core/logger';
import { Store } from '../src/main/core/store';
import { SmsService } from '../src/main/services/sms';
import type { PeerSession } from '../src/main/server';
import { TYPES } from '../src/shared/types';

const dirs: string[] = [];

const messages = [
  {
    id: 'a',
    address: '+15550101000',
    body: 'First unread',
    date: 1_000,
    type: 1,
    read: false,
  },
  {
    id: 'b',
    address: '+15550101000',
    body: 'Already read',
    date: 2_000,
    type: 1,
    read: true,
  },
  {
    id: 'c',
    address: 'Other',
    body: 'Short code',
    date: 3_000,
    type: 1,
    read: false,
  },
];

function harness() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-sms-service-'));
  dirs.push(dir);
  const store = new Store(path.join(dir, 'state.json'));
  const requests: Array<{ type: string; data: unknown }> = [];
  const session = {
    device: { id: 'phone-1' },
    request: async (type: string, data: unknown) => {
      requests.push({ type, data });
      if (type === TYPES.SMS_LIST) {
        return {
          v: 1,
          id: 'reply',
          type: 'response',
          ok: true,
          data: { messages },
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
  const service = new SmsService(
    new Logger(path.join(dir, 'log.txt')),
    store,
    () => undefined,
  );
  return { dir, store, session, service, requests };
}

function unread(service: SmsService, address: string): number | undefined {
  return service.status().threads.find((thread) => thread.address === address)?.unread;
}

afterEach(() => {
  for (const dir of dirs.splice(0)) fs.rmSync(dir, { recursive: true, force: true });
});

describe('SMS read state', () => {
  it('marks one thread read locally and persists it per device', async () => {
    const { dir, store, session, service } = harness();
    service.onConnected(session);
    await service.refresh();

    expect(unread(service, '+15550101000')).toBe(1);
    service.markThreadRead('+1 (555) 010-1000');
    expect(unread(service, '+15550101000')).toBe(0);
    expect(unread(service, 'other')).toBe(1);
    expect(store.readSmsFor('phone-1')).toEqual(['a']);

    const reloaded = new SmsService(
      new Logger(path.join(dir, 'log2.txt')),
      store,
      () => undefined,
    );
    reloaded.onConnected(session);
    await reloaded.refresh();
    expect(unread(reloaded, '+15550101000')).toBe(0);
  });

  it('marks all loaded inbound messages read', async () => {
    const { store, session, service } = harness();
    service.onConnected(session);
    await service.refresh();

    service.markAllRead();
    expect(service.status().threads.every((thread) => thread.unread === 0)).toBe(true);
    expect(store.readSmsFor('phone-1')).toEqual(expect.arrayContaining(['a', 'c']));
  });

  it('opens the thread on the phone and marks it read locally', async () => {
    const { session, service, requests } = harness();
    service.onConnected(session);
    await service.refresh();

    await service.openThreadOnPhone('+1 (555) 010-1000');
    expect(requests.find((request) => request.type === TYPES.SMS_OPEN_THREAD)?.data).toEqual({
      address: '+15550101000',
    });
    expect(unread(service, '+15550101000')).toBe(0);
  });
});
