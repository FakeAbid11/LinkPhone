import { describe, expect, it } from 'vitest';
import { filterThreads } from '../src/renderer/thread-filter';
import type { SmsThreadUi } from '../src/shared/types';

function thread(
  displayName: string,
  address: string,
  body: string,
  unread = 0,
): SmsThreadUi {
  return {
    displayName,
    address,
    latest: 1_700_000_000_000,
    unread,
    messages: [
      {
        id: `${address}-1`,
        address,
        body,
        date: 1_700_000_000_000,
        type: 1,
        read: unread === 0,
      },
    ],
  };
}

const threads = [
  thread('Alice', '+15550100', 'Dinner at seven?', 2),
  thread('Bob', '+15550200', 'See you tomorrow'),
  thread('+15550300', '+15550300', 'Verification code 1234'),
];

describe('conversation search', () => {
  it('returns every thread for an empty query', () => {
    expect(filterThreads(threads, '')).toHaveLength(3);
    expect(filterThreads(threads, '   ')).toHaveLength(3);
  });

  it('matches display name, address, and latest message', () => {
    expect(filterThreads(threads, 'alice').map((item) => item.address)).toEqual(['+15550100']);
    expect(filterThreads(threads, '550300').map((item) => item.displayName)).toEqual([
      '+15550300',
    ]);
    expect(filterThreads(threads, 'verification').map((item) => item.address)).toEqual([
      '+15550300',
    ]);
  });

  it('is case insensitive and trims whitespace', () => {
    expect(filterThreads(threads, '  DINNER  ').map((item) => item.displayName)).toEqual([
      'Alice',
    ]);
  });

  it('returns nothing when no conversation matches', () => {
    expect(filterThreads(threads, 'nobody')).toEqual([]);
  });
});
