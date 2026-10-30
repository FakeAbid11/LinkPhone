import { afterEach, describe, expect, it, vi } from 'vitest';
import type { BinaryFrame } from '../src/main/core/binary';
import { StreamRegistry } from '../src/main/core/streams';

function frame(streamId: number, seq: number, payload: string, last = false): BinaryFrame {
  return {
    type: 1,
    streamId,
    seq,
    flags: last ? 1 : 0,
    last,
    keyframe: false,
    codecConfig: false,
    payload: Buffer.from(payload),
  };
}

afterEach(() => {
  vi.useRealTimers();
});

describe('binary stream registry', () => {
  it('resolves a stream when the last chunk arrives', async () => {
    const registry = new StreamRegistry();
    const received = registry.create(1, 1_024, 1_000, 'photo');

    expect(registry.add(frame(1, 0, 'ab'))).toBe('accepted');
    expect(registry.add(frame(1, 1, 'cd', true))).toBe('accepted');

    expect((await received).toString()).toBe('abcd');
    expect(registry.has(1)).toBe(false);
  });

  it('rejects an out-of-order chunk instead of waiting for the timeout', async () => {
    const registry = new StreamRegistry();
    const received = registry.create(2, 1_024, 1_000, 'photo');
    received.catch(() => undefined);

    registry.add(frame(2, 0, 'ab'));
    expect(registry.add(frame(2, 2, 'cd'))).toBe('dropped');

    await expect(received).rejects.toThrow(/Out-of-order/);
  });

  it('rejects a stream that grows past its limit', async () => {
    const registry = new StreamRegistry();
    const received = registry.create(3, 3, 1_000, 'photo');
    received.catch(() => undefined);

    expect(registry.add(frame(3, 0, 'abcd'))).toBe('dropped');

    await expect(received).rejects.toThrow(/too large/);
  });

  it('refuses a duplicate stream id', async () => {
    const registry = new StreamRegistry();
    const first = registry.create(4, 1_024, 1_000, 'photo');
    first.catch(() => undefined);

    await expect(registry.create(4, 1_024, 1_000, 'photo')).rejects.toThrow(/Duplicate/);
    registry.rejectAll('test cleanup');
  });

  it('replays the chunks that arrived before the stream was registered', async () => {
    const registry = new StreamRegistry();

    // The phone starts sending as soon as it answers photo.fetch, so the first
    // chunks can be dispatched before the reply has been turned into a stream.
    expect(registry.add(frame(7, 0, 'first'))).toBe('buffered');
    expect(registry.add(frame(7, 1, 'second', true))).toBe('buffered');

    const received = registry.create(7, 1_024, 1_000, 'photo');

    expect((await received).toString()).toBe('firstsecond');
  });

  it('keeps a replayed stream open until its last chunk', async () => {
    const registry = new StreamRegistry();
    expect(registry.add(frame(8, 0, 'first'))).toBe('buffered');

    const received = registry.create(8, 1_024, 1_000, 'photo');
    expect(registry.has(8)).toBe(true);

    expect(registry.add(frame(8, 1, 'second', true))).toBe('accepted');

    expect((await received).toString()).toBe('firstsecond');
    expect(registry.has(8)).toBe(false);
  });

  it('drops a frame that cannot be buffered', () => {
    const registry = new StreamRegistry();
    const huge = 'x'.repeat(300 * 1_024);

    expect(registry.add(frame(9, 0, huge))).toBe('dropped');
  });

  it('forgets a buffered stream after its TTL', async () => {
    vi.useFakeTimers();
    const registry = new StreamRegistry();

    expect(registry.add(frame(11, 0, 'ab'))).toBe('buffered');
    vi.advanceTimersByTime(2_500);

    const received = registry.create(11, 1_024, 1_000, 'photo');
    received.catch(() => undefined);
    vi.advanceTimersByTime(1_100);

    await expect(received).rejects.toThrow(/Timed out/);
  });
});
