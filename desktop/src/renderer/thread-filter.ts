import type { SmsThreadUi } from '../shared/types';

export function filterThreads(threads: SmsThreadUi[], query: string): SmsThreadUi[] {
  const term = query.trim().toLowerCase();
  if (!term) return threads;
  return threads.filter((thread) => {
    const latest = thread.messages[thread.messages.length - 1]?.body ?? '';
    return (
      thread.displayName.toLowerCase().includes(term) ||
      thread.address.toLowerCase().includes(term) ||
      latest.toLowerCase().includes(term)
    );
  });
}
