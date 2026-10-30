import { randomBytes } from 'node:crypto';
import { constantTimeEqual } from './auth';

export interface PairingSession {
  token: string;
  createdAt: number;
  expiresAt: number;
  used: boolean;
}

interface AttemptRecord {
  count: number;
  lockedUntil: number;
}

const PAIRING_TTL_MS = 5 * 60 * 1000;
const MAX_ATTEMPTS = 3;
const LOCKOUT_MS = 15 * 60 * 1000;

export class PairingManager {
  private session: PairingSession | undefined;
  private attempts = new Map<string, AttemptRecord>();

  create(): PairingSession {
    const now = Date.now();
    this.session = {
      token: randomBytes(16).toString('base64url'),
      createdAt: now,
      expiresAt: now + PAIRING_TTL_MS,
      used: false,
    };
    this.attempts.clear();
    return this.session;
  }

  current(): PairingSession | undefined {
    if (!this.session || this.session.used) return undefined;
    if (this.session.expiresAt <= Date.now()) return undefined;
    return this.session;
  }

  validate(token: string, ip: string): { ok: true } | { ok: false; reason: string } {
    const attempt = this.attempts.get(ip);
    if (attempt && attempt.lockedUntil > Date.now()) {
      return { ok: false, reason: 'Too many incorrect pairing attempts. Try again later.' };
    }
    const session = this.session;
    if (!session) {
      // Not a wrong guess: the PC has not shown a code yet. Counting it would
      // lock an innocent phone out for 15 minutes.
      return { ok: false, reason: 'Start pairing on the PC first.' };
    }
    if (session.used) {
      this.recordFailure(ip);
      return { ok: false, reason: 'This pairing code has already been used.' };
    }
    if (session.expiresAt <= Date.now()) {
      this.recordFailure(ip);
      return { ok: false, reason: 'This pairing code expired. Create a new one on the PC.' };
    }
    if (!constantTimeEqual(token, session.token)) {
      this.recordFailure(ip);
      return { ok: false, reason: 'Incorrect pairing code.' };
    }
    this.attempts.delete(ip);
    return { ok: true };
  }

  consume(): void {
    if (this.session) this.session.used = true;
  }

  clear(): void {
    this.session = undefined;
  }

  private recordFailure(ip: string): void {
    const record = this.attempts.get(ip) ?? { count: 0, lockedUntil: 0 };
    record.count += 1;
    if (record.count >= MAX_ATTEMPTS) {
      record.count = 0;
      record.lockedUntil = Date.now() + LOCKOUT_MS;
    }
    this.attempts.set(ip, record);
  }
}
