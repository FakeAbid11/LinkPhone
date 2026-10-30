import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Logger } from '../src/main/core/logger';
import { ClipboardService } from '../src/main/services/clipboard';
import type { PeerSession } from '../src/main/server';
import { TYPES } from '../src/shared/types';

const dirs: string[] = [];

function harness(clipboardText: string) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-clipboard-'));
  dirs.push(dir);
  const requests: Array<{ type: string; data: unknown }> = [];
  const session = {
    request: async (type: string, data: unknown) => {
      requests.push({ type, data });
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
  const service = new ClipboardService(
    () => clipboardText,
    () => undefined,
    new Logger(path.join(dir, 'log.txt')),
    () => undefined,
    () => undefined,
  );
  service.onConnected(session);
  return { service, requests };
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-10-30T09:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
  for (const dir of dirs.splice(0)) fs.rmSync(dir, { recursive: true, force: true });
});

describe('clipboard handoff', () => {
  it('never echoes text the phone just sent back to the phone', async () => {
    const { service, requests } = harness('from phone');
    service.receiveFromPhone('from phone');
    // Past the 1.5 s throttle, so only the echo guard can stop the send.
    vi.advanceTimersByTime(2_000);

    await service.maybeAutoSend(true);

    expect(requests).toHaveLength(0);
    expect(service.status().lastReceived).toBe('from phone');
  });

  it('sends clipboard text the phone has not seen', async () => {
    const { service, requests } = harness('typed on the PC');

    await service.maybeAutoSend(true);

    expect(requests.map((request) => request.type)).toEqual([TYPES.CLIPBOARD_SET]);
    expect(requests[0].data).toEqual({ text: 'typed on the PC' });
  });

  it('does nothing while auto-send is off', async () => {
    const { service, requests } = harness('typed on the PC');

    await service.maybeAutoSend(false);

    expect(requests).toHaveLength(0);
  });

  it('skips an empty clipboard', async () => {
    const { service, requests } = harness('   ');

    await service.maybeAutoSend(true);

    expect(requests).toHaveLength(0);
  });
});
