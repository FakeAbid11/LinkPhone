import type { SmsMessageUi, SmsThreadUi } from '../../shared/types';
import { normalizeAddress } from './safety';

export function groupSmsThreads(messages: SmsMessageUi[], maxMessages = 1_000): SmsThreadUi[] {
  const seen = new Set<string>();
  const byAddress = new Map<string, SmsMessageUi[]>();

  for (const message of messages) {
    // An empty body is kept: MMS and provider placeholder rows have no text, and
    // dropping them removed the whole conversation from the list.
    if (!message.address) continue;
    if (message.type === 3) continue;
    if (seen.has(message.id)) continue;
    seen.add(message.id);
    const key = normalizeAddress(message.address);
    if (!key) continue;
    const list = byAddress.get(key);
    if (list) list.push(message);
    else byAddress.set(key, [message]);
  }

  const threads: SmsThreadUi[] = [];
  for (const [key, list] of byAddress) {
    const sorted = list.sort((a, b) => a.date - b.date).slice(-maxMessages);
    const latestMessage = sorted[sorted.length - 1];
    if (!latestMessage) continue;
    threads.push({
      address: key,
      displayName: latestMessage.address,
      latest: latestMessage.date,
      unread: sorted.filter((m) => m.type === 1 && !m.read).length,
      messages: sorted,
    });
  }

  return threads.sort((a, b) => b.latest - a.latest);
}

export function mergeLocalSms(
  remote: SmsMessageUi[],
  local: SmsMessageUi[],
): SmsMessageUi[] {
  const merged = [...remote];
  for (const item of local) {
    const duplicate = merged.some(
      (existing) =>
        normalizeAddress(existing.address) === normalizeAddress(item.address) &&
        existing.body === item.body &&
        Math.abs(existing.date - item.date) < 120_000,
    );
    if (!duplicate) merged.push(item);
  }
  return merged;
}

export function messageTypeLabel(type: number): string {
  switch (type) {
    case 1:
      return 'received';
    case 2:
      return 'sent';
    case 4:
      return 'outbox';
    case 5:
      return 'failed';
    case 6:
      return 'queued';
    default:
      return 'message';
  }
}
