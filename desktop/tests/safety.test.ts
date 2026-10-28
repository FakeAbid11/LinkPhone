import { describe, expect, it } from 'vitest';
import {
  isImageMime,
  isSafeHttpUrl,
  normalizeAddress,
  normalizeDialNumber,
  normalizeFingerprint,
  sanitizeFileName,
  truncateText,
} from '../src/main/core/safety';
import { groupSmsThreads, mergeLocalSms } from '../src/main/core/sms';
import type { SmsMessageUi } from '../src/shared/types';

describe('input safety', () => {
  it('validates dial numbers', () => {
    expect(normalizeDialNumber('+1 (555) 010-1000')).toBe('+1 (555) 010-1000');
    expect(normalizeDialNumber('*#06#')).toBe('*#06#');
    expect(normalizeDialNumber('123')).toBe('123');
    expect(normalizeDialNumber('abc')).toBeNull();
    expect(normalizeDialNumber('+'.repeat(31))).toBeNull();
    expect(normalizeDialNumber('')).toBeNull();
  });

  it('only allows http(s) URLs', () => {
    expect(isSafeHttpUrl('https://example.com/path?q=1')).toBe(true);
    expect(isSafeHttpUrl('http://192.168.1.4:8080')).toBe(true);
    expect(isSafeHttpUrl('javascript:alert(1)')).toBe(false);
    expect(isSafeHttpUrl('file:///C:/Windows/System32')).toBe(false);
    expect(isSafeHttpUrl('intent://scan/#Intent;scheme=zxing;end')).toBe(false);
    expect(isSafeHttpUrl('https://exa mple.com')).toBe(false);
    expect(isSafeHttpUrl('')).toBe(false);
  });

  it('sanitizes file names', () => {
    expect(sanitizeFileName('../../evil.jpg', 'photo.jpg')).toBe('evil.jpg');
    expect(sanitizeFileName('..\\..\\evil.jpg', 'photo.jpg')).toBe('evil.jpg');
    expect(sanitizeFileName('my photo <1>.jpg', 'photo.jpg')).toBe('my photo _1_.jpg');
    expect(sanitizeFileName('', 'photo.jpg')).toBe('photo.jpg');
    expect(sanitizeFileName('...', 'photo.jpg')).toBe('photo.jpg');
  });

  it('normalizes fingerprints', () => {
    const lower = 'ab'.repeat(32);
    expect(normalizeFingerprint(lower.toUpperCase().match(/.{2}/g)?.join(':') ?? '')).toBe(lower);
    expect(normalizeFingerprint('nope')).toBeNull();
  });

  it('caps clipboard text and identifies images', () => {
    expect(truncateText('abcdef', 3)).toBe('abc');
    expect(isImageMime('image/jpeg')).toBe(true);
    expect(isImageMime('image/png')).toBe(true);
    expect(isImageMime('text/plain')).toBe(false);
    expect(isImageMime(null)).toBe(false);
  });

  it('normalizes phone addresses for grouping', () => {
    expect(normalizeAddress('+1 (555) 010-1000')).toBe('+15550101000');
    expect(normalizeAddress('15550101000')).toBe('15550101000');
    expect(normalizeAddress('MyBank')).toBe('mybank');
  });
});

function message(overrides: Partial<SmsMessageUi>): SmsMessageUi {
  return {
    id: Math.random().toString(),
    address: '+15550101000',
    body: 'hello',
    date: 1_000,
    type: 1,
    read: true,
    ...overrides,
  };
}

describe('SMS grouping', () => {
  it('groups by normalized address and sorts by latest', () => {
    const threads = groupSmsThreads([
      message({ id: 'a', address: '+1 (555) 010-1000', date: 1_000, body: 'first' }),
      message({ id: 'b', address: '+15550101000', date: 2_000, body: 'second', read: false }),
      message({ id: 'c', address: 'MyBank', date: 3_000, body: 'code' }),
    ]);
    expect(threads).toHaveLength(2);
    expect(threads[0].address).toBe('mybank');
    expect(threads[1].messages).toHaveLength(2);
    expect(threads[1].unread).toBe(1);
    expect(threads[1].messages[1].body).toBe('second');
  });

  it('skips drafts and duplicate ids', () => {
    const threads = groupSmsThreads([
      message({ id: 'a', type: 3, body: 'draft' }),
      message({ id: 'b', body: 'real' }),
      message({ id: 'b', body: 'real again' }),
    ]);
    expect(threads).toHaveLength(1);
    expect(threads[0].messages).toHaveLength(1);
    expect(threads[0].messages[0].body).toBe('real');
  });

  it('merges locally sent messages without duplicating them', () => {
    const remote = [message({ id: 'r', type: 2, body: 'sent', date: 5_000 })];
    const local = [
      message({ id: 'l1', type: 2, body: 'sent', date: 5_500, local: true }),
      message({ id: 'l2', type: 2, body: 'new', date: 6_000, local: true }),
    ];
    const merged = mergeLocalSms(remote, local);
    expect(merged).toHaveLength(2);
    expect(merged.some((item) => item.body === 'new')).toBe(true);
  });
});
