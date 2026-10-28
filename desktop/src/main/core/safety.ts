const DIAL_PATTERN = /^[+0-9*#()\-\s]{1,30}$/;
const FILE_PATTERN = /[^A-Za-z0-9 ._()\-]/g;
const FINGERPRINT_PATTERN = /^[0-9a-f]{64}$/;

export function normalizeDialNumber(raw: string): string | null {
  const trimmed = raw.trim();
  if (!trimmed || trimmed.length > 30 || !DIAL_PATTERN.test(trimmed)) return null;
  if (!/[0-9*#]/.test(trimmed)) return null;
  return trimmed;
}

export function normalizeFingerprint(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const cleaned = raw.replace(/[:\s]/g, '').toLowerCase();
  return FINGERPRINT_PATTERN.test(cleaned) ? cleaned : null;
}

export function formatFingerprint(fingerprint: string): string {
  return fingerprint.toUpperCase().match(/.{2}/g)?.join(':') ?? fingerprint.toUpperCase();
}

export function isSafeHttpUrl(raw: string | null | undefined): boolean {
  if (!raw) return false;
  const value = raw.trim();
  if (!value || value.length > 4096) return false;
  if (/[\s\u0000-\u001f]/.test(value)) return false;
  try {
    const url = new URL(value);
    return (url.protocol === 'http:' || url.protocol === 'https:') && Boolean(url.hostname);
  } catch {
    return false;
  }
}

export function sanitizeFileName(raw: string | null | undefined, fallback: string): string {
  if (!raw) return fallback;
  const base = raw.split(/[\\/]/).pop() ?? '';
  const cleaned = base.replace(FILE_PATTERN, '_').replace(/^[. ]+|[. ]+$/g, '').slice(0, 120);
  return cleaned || fallback;
}

export function truncateText(text: string, max = 32_000): string {
  return text.length <= max ? text : text.slice(0, max);
}

export function isImageMime(mime: string | null | undefined): boolean {
  return Boolean(mime && mime.toLowerCase().startsWith('image/'));
}

export function normalizeAddress(address: string): string {
  const trimmed = address.trim();
  if (!trimmed) return trimmed;
  const digits = trimmed.replace(/[\s\-().]/g, '');
  if (/^\+?\d+$/.test(digits)) return digits;
  return trimmed.toLowerCase();
}
