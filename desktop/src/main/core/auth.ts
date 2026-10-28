import {
  createHash,
  createPublicKey,
  randomBytes,
  timingSafeEqual,
  verify,
} from 'node:crypto';
import { normalizeFingerprint } from './safety';

export const AUTH_PREFIX = 'linkphone-auth-v1';

export function newChallenge(): string {
  return randomBytes(32).toString('base64');
}

export function buildAuthPayload(
  pcId: string,
  phoneId: string,
  nonceBase64: string,
): string {
  return `${AUTH_PREFIX}|${pcId}|${phoneId}|${nonceBase64}`;
}

export interface PhonePublicKey {
  pem: string;
  fingerprint: string;
}

export function parsePhoneKey(
  spkiBase64: string,
  claimedFingerprint?: string,
): PhonePublicKey {
  if (!spkiBase64 || spkiBase64.length > 2_000) {
    throw new Error('Invalid phone public key');
  }
  const der = Buffer.from(spkiBase64, 'base64');
  if (der.length < 60 || der.length > 512) {
    throw new Error('Invalid phone public key length');
  }
  const key = createPublicKey({ key: der, format: 'der', type: 'spki' });
  if (key.asymmetricKeyType !== 'ec') {
    throw new Error('Phone key must use ECDSA');
  }
  const curve = key.asymmetricKeyDetails?.namedCurve;
  if (curve !== 'prime256v1' && curve !== 'secp256r1') {
    throw new Error('Phone key must use P-256');
  }
  const fingerprint = createHash('sha256').update(der).digest('hex');
  if (claimedFingerprint) {
    const claimed = normalizeFingerprint(claimedFingerprint);
    if (claimed !== fingerprint) {
      throw new Error('Phone key fingerprint does not match');
    }
  }
  return {
    pem: key.export({ type: 'spki', format: 'pem' }).toString(),
    fingerprint,
  };
}

export function verifyPhoneSignature(
  publicKeyPem: string,
  payload: string,
  signatureBase64: string,
): boolean {
  try {
    const signature = Buffer.from(signatureBase64, 'base64');
    if (signature.length < 8 || signature.length > 256) return false;
    const key = createPublicKey(publicKeyPem);
    return verify(
      'sha256',
      Buffer.from(payload, 'utf8'),
      { key, dsaEncoding: 'der' },
      signature,
    );
  } catch {
    return false;
  }
}

export function constantTimeEqual(left: string, right: string): boolean {
  const a = Buffer.from(left);
  const b = Buffer.from(right);
  if (a.length !== b.length) return false;
  return timingSafeEqual(a, b);
}
