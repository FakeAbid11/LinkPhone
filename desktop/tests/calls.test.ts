import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { Logger } from '../src/main/core/logger';
import { CallService } from '../src/main/services/calls';
import type { PeerSession } from '../src/main/server';
import { TYPES } from '../src/shared/types';

const dirs: string[] = [];

function harness() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-calls-'));
  dirs.push(dir);
  const requests: Array<{ type: string; data: unknown }> = [];
  const session = {
    request: async (type: string, data: unknown) => {
      requests.push({ type, data });
      if (type === TYPES.CONTACTS_LIST) {
        return {
          v: 1,
          id: 'reply',
          type: 'response',
          ok: true,
          data: {
            contacts: [
              { id: 'c1', name: 'Ada', number: '+15550101000', label: 'Mobile' },
            ],
          },
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
  const service = new CallService(
    new Logger(path.join(dir, 'log.txt')),
    () => undefined,
    () => undefined,
  );
  service.onConnected(session);
  return { service, requests };
}

afterEach(() => {
  for (const dir of dirs.splice(0)) fs.rmSync(dir, { recursive: true, force: true });
});

describe('call dialing', () => {
  it('includes a SIM only when one is selected', async () => {
    const { service, requests } = harness();

    await service.dial('+1 (555) 010-1000', 2);
    expect(requests[0]).toEqual({
      type: TYPES.CALL_DIAL,
      data: { number: '+1 (555) 010-1000', subId: 2 },
    });

    await service.dial('+15550101000', null);
    expect(requests[1]).toEqual({
      type: TYPES.CALL_DIAL,
      data: { number: '+15550101000' },
    });
  });

  it('sends answer, end, and speaker requests', async () => {
    const { service, requests } = harness();
    await service.answer();
    await service.decline();
    await service.setSpeaker(true);
    await service.setSpeaker(false);

    expect(requests.slice(0, 4)).toEqual([
      { type: TYPES.CALL_ANSWER, data: {} },
      { type: TYPES.CALL_DECLINE, data: {} },
      { type: TYPES.CALL_SPEAKER, data: { enabled: true } },
      { type: TYPES.CALL_SPEAKER, data: { enabled: false } },
    ]);
  });

  it('maps call state including speaker status', () => {
    const { service } = harness();
    service.onPhoneState({
      state: 'offhook',
      number: '+15550101000',
      timestamp: 1_700_000_000_000,
      speakerOn: true,
    });
    expect(service.status().state).toMatchObject({
      state: 'offhook',
      number: '+15550101000',
      speakerOn: true,
    });
  });

  it('loads contacts on demand', async () => {    const { service, requests } = harness();
    await service.refreshContacts();

    expect(requests.find((request) => request.type === TYPES.CONTACTS_LIST)?.data).toEqual({
      limit: 1000,
    });
    const contacts = service.status().contacts;
    expect(contacts).toHaveLength(1);
    expect(contacts[0]).toMatchObject({
      name: 'Ada',
      number: '+15550101000',
      label: 'Mobile',
    });
  });

  it('rejects an invalid phone number', async () => {
    const { service } = harness();
    await expect(service.dial('not a number')).rejects.toThrow(/valid phone/i);
  });

  it('requires a connected session', async () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-calls-'));
    dirs.push(dir);
    const service = new CallService(
      new Logger(path.join(dir, 'log.txt')),
      () => undefined,
      () => undefined,
    );
    await expect(service.dial('+15550101000')).rejects.toThrow(/Connect/i);
  });
});
