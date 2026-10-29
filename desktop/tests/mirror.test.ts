import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { TYPE_MIRROR, TYPE_VIDEO, type BinaryFrame } from '../src/main/core/binary';
import { Logger } from '../src/main/core/logger';
import { MirrorService } from '../src/main/services/mirror';
import type { PeerSession } from '../src/main/server';
import { TYPES } from '../src/shared/types';

const dirs: string[] = [];

function reply(data: unknown = { ok: true }) {
  return { v: 1, id: 'reply', type: 'response', ok: true, data, ts: Date.now() };
}

function frame(type = TYPE_MIRROR, seq = 1, flags = 0): BinaryFrame {
  return {
    type,
    streamId: 0,
    seq,
    flags,
    last: (flags & 1) === 1,
    keyframe: (flags & 2) === 2,
    codecConfig: (flags & 4) === 4,
    payload: Buffer.from(type === TYPE_VIDEO ? 'video' : 'jpeg'),
  };
}

function harness() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-mirror-'));
  dirs.push(dir);
  const requests: Array<{ type: string; data: unknown }> = [];
  const frames: BinaryFrame[] = [];
  const session = {
    request: async (type: string, data: unknown) => {
      requests.push({ type, data });
      return reply();
    },
  } as unknown as PeerSession;
  const service = new MirrorService(
    new Logger(path.join(dir, 'log.txt')),
    () => undefined,
    (next) => frames.push(next),
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
    await service.start('h264', 60);
    await service.stop();
    expect(requests).toEqual([
      { type: TYPES.MIRROR_START, data: { codec: 'jpeg', fps: 30 } },
      { type: TYPES.MIRROR_START, data: { codec: 'h264', fps: 60 } },
      { type: TYPES.MIRROR_STOP, data: {} },
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
      codec: 'jpeg',
    });
    expect(service.status().error).toBe('Screen sharing was not approved');

    service.onPhoneState({ active: true, width: 1080, height: 2400, codec: 'h264', fps: 60 });
    expect(service.status().error).toBeUndefined();
    expect(service.status().state.active).toBe(true);
    expect(service.status().state.codec).toBe('h264');
    expect(service.status().state.fps).toBe(60);

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

  it('forwards frames only for the active codec', () => {
    const { service, frames } = harness();
    const jpeg = frame(TYPE_MIRROR, 1);
    const video = frame(TYPE_VIDEO, 2, 2);

    service.onBinaryFrame(jpeg);
    service.onPhoneState({ active: true, width: 100, height: 200 });
    service.onBinaryFrame(jpeg);
    service.onBinaryFrame(video);
    expect(frames).toEqual([jpeg]);

    service.onPhoneState({ active: true, width: 100, height: 200, codec: 'h264' });
    service.onBinaryFrame(jpeg);
    service.onBinaryFrame(video);
    expect(frames).toEqual([jpeg, video]);
  });

  it('requests keyframes only for an active H.264 session', async () => {
    const { service, requests } = harness();
    await service.requestKeyframe();
    expect(requests).toHaveLength(0);

    service.onPhoneState({ active: true, width: 100, height: 200, codec: 'h264' });
    await service.requestKeyframe();
    expect(requests.at(-1)).toEqual({ type: TYPES.MIRROR_KEYFRAME, data: {} });

    service.onPhoneState({ active: true, width: 100, height: 200, codec: 'jpeg' });
    const before = requests.length;
    await service.requestKeyframe();
    expect(requests).toHaveLength(before);
  });
});
