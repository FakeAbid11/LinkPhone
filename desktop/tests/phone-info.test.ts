import { describe, expect, it } from 'vitest';
import { normalizePhoneInfo } from '../src/main/core/phone-info';

describe('phone.info normalization', () => {
  it('coerces a non-string SIM label and defaults the slot', () => {
    const info = normalizePhoneInfo({
      model: 'Pixel',
      androidVersion: '15',
      appVersion: '0.8.1',
      capabilities: ['sms'],
      subscriptions: [{ subscriptionId: 3, label: 42, slotIndex: 'x' }],
    });
    expect(info.model).toBe('Pixel');
    expect(info.subscriptions).toEqual([{ subscriptionId: 3, label: 'SIM 1', slotIndex: 0 }]);
  });

  it('drops unusable SIM entries and caps the list at four', () => {
    const info = normalizePhoneInfo({
      subscriptions: [
        { subscriptionId: 'nope', label: 'broken', slotIndex: 0 },
        ...Array.from({ length: 9 }, (_, index) => ({
          subscriptionId: index + 1,
          label: `SIM ${index + 1}`,
          slotIndex: index,
        })),
      ],
    });
    expect(info.subscriptions).toHaveLength(4);
    expect(info.subscriptions.map((sim) => sim.subscriptionId)).toEqual([1, 2, 3, 4]);
    expect(info.subscriptions.every((sim) => typeof sim.label === 'string')).toBe(true);
  });

  it('keeps a negative subscription id, which Android uses for the invalid case', () => {
    const info = normalizePhoneInfo({
      subscriptions: [{ subscriptionId: -1, label: 'Default', slotIndex: 0 }],
    });
    expect(info.subscriptions).toEqual([
      { subscriptionId: -1, label: 'Default', slotIndex: 0 },
    ]);
  });

  it('caps capabilities and keeps only strings', () => {
    const info = normalizePhoneInfo({
      capabilities: [...Array.from({ length: 300 }, (_, index) => `cap${index}`), 7, null],
    });
    expect(info.capabilities).toHaveLength(40);
    expect(info.capabilities.every((item) => typeof item === 'string')).toBe(true);
  });

  it('survives junk payloads', () => {
    expect(normalizePhoneInfo(null).model).toBe('Android');
    expect(normalizePhoneInfo('garbage').subscriptions).toEqual([]);
    expect(normalizePhoneInfo({ model: { nested: true } }).model).toBe('Android');
    expect(normalizePhoneInfo({ capabilities: 'sms' }).capabilities).toEqual([]);
  });
});
