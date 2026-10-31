import { afterEach, describe, expect, it, vi } from 'vitest';
import { PairingOfferWatcher } from '../src/main/core/pairing-offer-watch';

afterEach(() => {
  vi.useRealTimers();
});

function watcher(options: {
  offerHost?: string;
  addresses?: string[];
  onStale?: () => Promise<void> | void;
}): { instance: PairingOfferWatcher; onStale: ReturnType<typeof vi.fn> } {
  const onStale = vi.fn(options.onStale ?? (() => undefined));
  const instance = new PairingOfferWatcher({
    addresses: () => options.addresses ?? [],
    offerHost: () => options.offerHost,
    onStale,
  });
  return { instance, onStale };
}

describe('pairing offer watcher', () => {
  it('renews when the address in the code is gone', async () => {
    const { instance, onStale } = watcher({
      offerHost: '192.168.0.111',
      addresses: ['192.168.43.7'],
    });

    await instance.check();

    expect(onStale).toHaveBeenCalledTimes(1);
  });

  it('does nothing while the address is still one of ours', async () => {
    const { instance, onStale } = watcher({
      offerHost: '192.168.0.111',
      addresses: ['10.8.0.2', '192.168.0.111'],
    });

    await instance.check();

    expect(onStale).not.toHaveBeenCalled();
  });

  it('does nothing when there is no code on screen', async () => {
    const { instance, onStale } = watcher({ addresses: ['192.168.43.7'] });

    await instance.check();

    expect(onStale).not.toHaveBeenCalled();
  });

  it('does nothing when the address list is momentarily empty', async () => {
    // Adapters vanish briefly during DHCP renegotiation; renewing then would
    // build no code at all.
    const { instance, onStale } = watcher({ offerHost: '192.168.0.111', addresses: [] });

    await instance.check();

    expect(onStale).not.toHaveBeenCalled();
  });

  it('renews once when two ticks overlap', async () => {
    let release: () => void = () => undefined;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const { instance, onStale } = watcher({
      offerHost: '192.168.0.111',
      addresses: ['192.168.43.7'],
      onStale: async () => {
        await gate;
      },
    });

    const first = instance.check();
    const second = instance.check();
    release();
    await Promise.all([first, second]);

    expect(onStale).toHaveBeenCalledTimes(1);
  });

  it('keeps working after a renewal fails', async () => {
    const onStale = vi
      .fn()
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce(undefined);
    const instance = new PairingOfferWatcher({
      addresses: () => ['192.168.43.7'],
      offerHost: () => '192.168.0.111',
      onStale,
    });

    await expect(instance.check()).rejects.toThrow('boom');
    await instance.check();

    expect(onStale).toHaveBeenCalledTimes(2);
  });

  it('renews on the interval once started', async () => {
    vi.useFakeTimers();
    const { instance, onStale } = watcher({
      offerHost: '192.168.0.111',
      addresses: ['192.168.43.7'],
    });

    instance.start();
    await vi.advanceTimersByTimeAsync(10_000);

    expect(onStale).toHaveBeenCalledTimes(1);
    instance.stop();
  });

  it('stops renewing after stop', async () => {
    vi.useFakeTimers();
    const { instance, onStale } = watcher({
      offerHost: '192.168.0.111',
      addresses: ['192.168.43.7'],
    });

    instance.start();
    instance.stop();
    await vi.advanceTimersByTimeAsync(30_000);

    expect(onStale).not.toHaveBeenCalled();
  });

  it('installs only one interval when started twice', async () => {
    vi.useFakeTimers();
    const { instance, onStale } = watcher({
      offerHost: '192.168.0.111',
      addresses: ['192.168.43.7'],
    });

    instance.start();
    instance.start();
    await vi.advanceTimersByTimeAsync(10_000);

    expect(onStale).toHaveBeenCalledTimes(1);
    instance.stop();
  });
});
