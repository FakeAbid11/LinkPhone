import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PairingManager } from '../src/main/core/pairing';

describe('pairing tokens', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-28T12:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('accepts the current token once', () => {
    const manager = new PairingManager();
    const session = manager.create();
    expect(manager.validate(session.token, '192.168.1.5')).toEqual({ ok: true });
    expect(manager.validate('wrong', '192.168.1.6').ok).toBe(false);
    manager.consume();
    const again = manager.validate(session.token, '192.168.1.5');
    expect(again.ok).toBe(false);
    if (!again.ok) expect(again.reason).toMatch(/already been used/i);
  });

  it('expires after five minutes', () => {
    const manager = new PairingManager();
    const session = manager.create();
    vi.advanceTimersByTime(5 * 60 * 1000 + 1);
    const result = manager.validate(session.token, '192.168.1.5');
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.reason).toMatch(/expired/i);
  });

  it('locks an IP after three incorrect attempts', () => {
    const manager = new PairingManager();
    const session = manager.create();
    expect(manager.validate('nope', '10.0.0.9').ok).toBe(false);
    expect(manager.validate('nope', '10.0.0.9').ok).toBe(false);
    expect(manager.validate('nope', '10.0.0.9').ok).toBe(false);
    const locked = manager.validate(session.token, '10.0.0.9');
    expect(locked.ok).toBe(false);
    if (!locked.ok) expect(locked.reason).toMatch(/too many/i);
    expect(manager.validate(session.token, '10.0.0.10').ok).toBe(true);
  });

  it('invalidates the previous code when a new one is created', () => {
    const manager = new PairingManager();
    const first = manager.create();
    const second = manager.create();
    expect(manager.validate(first.token, '10.0.0.2').ok).toBe(false);
    expect(manager.validate(second.token, '10.0.0.2').ok).toBe(true);
  });
});
