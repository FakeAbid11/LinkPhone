import { generateKeyPairSync, createHash, sign } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import {
  buildAuthPayload,
  constantTimeEqual,
  newChallenge,
  parsePhoneKey,
  verifyPhoneSignature,
} from '../src/main/core/auth';
import { normalizeFingerprint } from '../src/main/core/safety';

function makePhoneKey() {
  const { publicKey, privateKey } = generateKeyPairSync('ec', {
    namedCurve: 'prime256v1',
  });
  const der = publicKey.export({ type: 'spki', format: 'der' });
  const spkiBase64 = der.toString('base64');
  const fingerprint = createHash('sha256').update(der).digest('hex');
  return { privateKey, spkiBase64, fingerprint };
}

describe('phone authentication', () => {
  it('parses a P-256 phone key and computes its fingerprint', () => {
    const { spkiBase64, fingerprint } = makePhoneKey();
    const key = parsePhoneKey(spkiBase64, fingerprint);
    expect(key.fingerprint).toBe(fingerprint);
    expect(normalizeFingerprint(key.fingerprint)).toBe(fingerprint);
    expect(key.pem).toContain('BEGIN PUBLIC KEY');
  });

  it('rejects a key whose claimed fingerprint differs', () => {
    const { spkiBase64 } = makePhoneKey();
    const wrong = '0'.repeat(64);
    expect(() => parsePhoneKey(spkiBase64, wrong)).toThrow(/fingerprint/i);
  });

  it('rejects RSA keys', () => {
    const { publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
    const spki = publicKey.export({ type: 'spki', format: 'der' }).toString('base64');
    expect(() => parsePhoneKey(spki)).toThrow(/ECDSA/i);
  });

  it('verifies a DER ECDSA signature over the canonical payload', () => {
    const { privateKey, spkiBase64, fingerprint } = makePhoneKey();
    const key = parsePhoneKey(spkiBase64, fingerprint);
    const nonce = newChallenge();
    const payload = buildAuthPayload('pc-1', 'phone-1', nonce);
    const signature = sign('sha256', Buffer.from(payload, 'utf8'), {
      key: privateKey,
      dsaEncoding: 'der',
    }).toString('base64');

    expect(verifyPhoneSignature(key.pem, payload, signature)).toBe(true);
    expect(verifyPhoneSignature(key.pem, `${payload}-replay`, signature)).toBe(false);
    expect(verifyPhoneSignature(key.pem, payload, 'not-a-signature')).toBe(false);
  });

  it('rejects a signature from a different phone', () => {
    const first = makePhoneKey();
    const second = makePhoneKey();
    const key = parsePhoneKey(first.spkiBase64, first.fingerprint);
    const nonce = newChallenge();
    const payload = buildAuthPayload('pc-1', 'phone-1', nonce);
    const signature = sign('sha256', Buffer.from(payload, 'utf8'), {
      key: second.privateKey,
      dsaEncoding: 'der',
    }).toString('base64');
    expect(verifyPhoneSignature(key.pem, payload, signature)).toBe(false);
  });

  it('compares secrets without leaking their length', () => {
    expect(constantTimeEqual('same-token', 'same-token')).toBe(true);
    expect(constantTimeEqual('same-token', 'other-token')).toBe(false);
    expect(constantTimeEqual('short', 'a-much-longer-token')).toBe(false);
    expect(constantTimeEqual('', '')).toBe(true);
    expect(constantTimeEqual('', 'x')).toBe(false);
  });
});
