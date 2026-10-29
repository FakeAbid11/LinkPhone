import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { Logger } from '../src/main/core/logger';
import { MirrorService } from '../src/main/services/mirror';
import type { PeerSession } from '../src/main/server';
import type { BinaryFrame } from '../src/main/core/binary';
import { TYPES } from '../src/shared/types';

const dirs: string[] = [];

function reply(data: unknown = { ok: true }) {
  return { v: 1, id: 'reply', type: 'response', ok: true, data, ts: Date.now() };
}

function harness() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-mirror-'));
  dirs.push(dir);
  const requests: Array<{ type: string; data: unknown }> = [];
  const frames: Array<{ payload: Buffer; seq: number }> = [];
  const session = {
    request: async (type: string, data: unknown) => {
      requests.push({ type, data });
      return reply();
    },
  } as unknown as PeerSession;
  const service = new MirrorService(
    new Logger(path.join(dir, 'log.txt')),
    () => undefined,
    (payload, seq) => frames.push({ payload, seq }),
  );
  service.onConnected(session);
  return { service, requests, frames };
}

afterEach(() => {
  for (const dir of dirs.splice(0)) fs.rmSync(dir, { recursive: true, force: true });
});

describe('mirror service', () => {
  it('requests consent and stop over the session', async () => {
    const { service, requests } = harness();
    await service.start();
    await service.stop();
    expect(requests.map((item) => item.type)).toEqual([
      TYPES.MIRROR_START,
      TYPES.MIRROR_STOP,
    ]);
  });

  it('surfaces a denied or failed session and clears it on reconnect', () => {
    const { service } = harness();
    service.onPhoneState({
      active: false,
      width: 1080,
      height: 2400,
      message: 'Screen sharing was not approved',
    });
    expect(service.status().state).toMatchObject({
      active: false,
      width: 1080,
      height: 2400,
      message: 'Screen sharing was not approved',
    });
    expect(service.status().error).toBe('Screen sharing was not approved');

    service.onPhoneState({ active: true, width: 1080, height: 2400 });
    expect(service.status().error).toBeUndefined();
    expect(service.status().state.active).toBe(true);

    service.onDisconnected();
    expect(service.status().state.active).toBe(false);
    expect(service.status().error).toBeUndefined();
  });

  it('accepts valid touches and rejects invalid ones', async () => {
    const { service, requests } = harness();
    await service.touch({ action: 'tap', x: 0.5, y: 0.5, durationMs: 60 });
    expect(requests.at(-1)).toEqual({
      type: TYPES.MIRROR_TOUCH,
      data: {
        action: 'tap',
        x: 0.5,
        y: 0.5,
        x2: null,
        y2: null,
        durationMs: 60,
      },
    });

    const before = requests.length;
    await expect(service.touch({ action: 'tap', x: 1.2, y: 0.5, durationMs: 60 })).rejects.toThrow(
      /coordinates/i,
    );
    await expect(service.touch({ action: 'swipe', x: 0.5, y: 0.5, durationMs: 60 })).rejects.toThrow(
      /swipe/i,
    );
    expect(requests).toHaveLength(before);
  });

  it('forwards binary frames only while the session is live', () => {
    const { service, frames } = harness();
    const frame = {
      type: 2,
      streamId: 0,
      seq: 1,
      last: false,
      payload: Buffer.from('jpeg'),
    } as BinaryFrame;

    service.onBinaryFrame(frame);
    expect(frames).toHaveLength(0);

    service.onPhoneState({ active: true, width: 100, height: 200 });
    service.onBinaryFrame(frame);
    expect(frames).toEqual([{ payload: frame.payload, seq: 1 }]);
  });
});
