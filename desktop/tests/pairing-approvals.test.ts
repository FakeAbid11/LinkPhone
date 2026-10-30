import { afterEach, describe, expect, it, vi } from 'vitest';
import { PAIRING_APPROVAL_MS, PairingApprovals } from '../src/main/core/pairing-approvals';
import type { PairingRequestContext } from '../src/main/server';

function context(overrides: Partial<PairingRequestContext> = {}): PairingRequestContext {
  return {
    id: 'request-1',
    sessionId: 'session-1',
    deviceId: 'phone-1',
    name: 'Test phone',
    platform: 'android',
    fingerprint: 'a'.repeat(64),
    publicKeyPem: 'pem',
    remoteAddress: '192.168.1.5',
    ...overrides,
  };
}

afterEach(() => {
  vi.useRealTimers();
});

describe('pairing approvals', () => {
  it('approves the request on screen and clears it', async () => {
    const approvals = new PairingApprovals();
    const promise = approvals.request(context());
    expect(approvals.current?.context.id).toBe('request-1');

    approvals.approve('request-1');

    await expect(promise).resolves.toEqual({ approved: true });
    expect(approvals.current).toBeUndefined();
  });

  it('reports a rejection as declined', async () => {
    const approvals = new PairingApprovals();
    const promise = approvals.request(context());

    approvals.reject('request-1');

    await expect(promise).resolves.toEqual({ approved: false, reason: 'declined' });
  });

  it('refuses to answer a request that is no longer on screen', () => {
    const approvals = new PairingApprovals();
    approvals.request(context());
    approvals.reject('request-1');

    expect(() => approvals.approve('request-1')).toThrow(/no longer active/i);
    expect(() => approvals.reject('request-1')).toThrow(/no longer active/i);
  });

  it('joins a retry from the same socket instead of stacking a second prompt', async () => {
    const approvals = new PairingApprovals();
    const first = approvals.request(context());
    const second = approvals.request(context({ id: 'request-2' }));

    // Same connection asking again: one prompt, and both callers settle together.
    expect(approvals.current?.context.id).toBe('request-1');
    approvals.approve('request-1');

    await expect(first).resolves.toEqual({ approved: true });
    await expect(second).resolves.toEqual({ approved: true });
  });

  it('lets a second phone replace the pending request', async () => {
    const superseded: string[] = [];
    const approvals = new PairingApprovals({
      onSuperseded: (context) => superseded.push(context.id),
    });
    const first = approvals.request(context());
    const second = approvals.request(
      context({ id: 'request-2', sessionId: 'session-2', deviceId: 'phone-2' }),
    );

    // This is the regression: the newcomer used to be declined and the old
    // prompt stayed on screen, so the user saw no dialog at all.
    await expect(first).resolves.toEqual({ approved: false, reason: 'superseded' });
    expect(superseded).toEqual(['request-1']);
    expect(approvals.current?.context.id).toBe('request-2');

    approvals.approve('request-2');
    await expect(second).resolves.toEqual({ approved: true });
  });

  it('clears the prompt when the code is cancelled', async () => {
    const approvals = new PairingApprovals();
    const promise = approvals.request(context());

    approvals.cancel();

    await expect(promise).resolves.toEqual({ approved: false, reason: 'cancelled' });
    expect(approvals.current).toBeUndefined();
  });

  it('clears the prompt when its socket closes', async () => {
    const disconnected: string[] = [];
    const approvals = new PairingApprovals({
      onDisconnected: (context) => disconnected.push(context.sessionId),
    });
    const promise = approvals.request(context());

    // A different socket closing must not touch this prompt.
    approvals.socketClosed('session-9');
    expect(approvals.current?.context.id).toBe('request-1');

    approvals.socketClosed('session-1');

    await expect(promise).resolves.toEqual({ approved: false, reason: 'disconnected' });
    expect(disconnected).toEqual(['session-1']);
    expect(approvals.current).toBeUndefined();
  });

  it('times out on its own', async () => {
    vi.useFakeTimers();
    const timedOut: string[] = [];
    const approvals = new PairingApprovals({ onTimeout: (context) => timedOut.push(context.id) });
    const promise = approvals.request(context());

    vi.advanceTimersByTime(PAIRING_APPROVAL_MS);

    await expect(promise).resolves.toEqual({ approved: false, reason: 'timeout' });
    expect(timedOut).toEqual(['request-1']);
    expect(approvals.current).toBeUndefined();
  });

  it('does not time out a prompt that replaced an earlier one', async () => {
    vi.useFakeTimers();
    const approvals = new PairingApprovals();
    approvals.request(context());
    const replacement = approvals.request(
      context({ id: 'request-2', sessionId: 'session-2' }),
    );

    // The first timer is cleared when it is superseded, so advancing past the
    // deadline must not resolve the replacement.
    vi.advanceTimersByTime(PAIRING_APPROVAL_MS - 1);
    expect(approvals.current?.context.id).toBe('request-2');

    approvals.approve('request-2');
    await expect(replacement).resolves.toEqual({ approved: true });
  });
});
