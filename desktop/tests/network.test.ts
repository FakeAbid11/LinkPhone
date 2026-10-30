import os from 'node:os';
import { describe, expect, it } from 'vitest';
import { getLanAddresses, isPrivateIPv4, orderAddresses } from '../src/main/core/network';

describe('LAN address selection', () => {
  it('puts the default-route address first', () => {
    const ordered = orderAddresses(['10.1.2.3', '192.168.4.5', '8.8.8.8'], '8.8.8.8');
    expect(ordered[0]).toBe('8.8.8.8');
    expect(ordered).toHaveLength(3);
  });

  it('prefers private addresses when there is no default route', () => {
    const ordered = orderAddresses(['203.0.113.9', '10.1.2.3', '192.168.4.5'], null);
    expect(ordered.slice(0, 2).every((address) => isPrivateIPv4(address))).toBe(true);
    expect(ordered[2]).toBe('203.0.113.9');
  });

  it('ignores a preferred address that is not in the list', () => {
    const ordered = orderAddresses(['10.1.2.3', '192.168.4.5'], '172.16.0.1');
    expect(ordered).toHaveLength(2);
    expect(ordered.every((address) => isPrivateIPv4(address))).toBe(true);
  });

  it('does not mutate the caller array', () => {
    const input = ['192.168.4.5', '10.1.2.3'];
    orderAddresses(input, '10.1.2.3');
    expect(input).toEqual(['192.168.4.5', '10.1.2.3']);
  });

  it('reports exactly the external IPv4 addresses the OS reports', () => {
    const expected = new Set<string>();
    for (const entries of Object.values(os.networkInterfaces())) {
      for (const entry of entries ?? []) {
        if (entry.family !== 'IPv4' || entry.internal) continue;
        if (entry.address.startsWith('169.254.')) continue;
        expected.add(entry.address);
      }
    }
    const actual = getLanAddresses();
    expect(new Set(actual)).toEqual(expected);
    expect(new Set(actual).size).toBe(actual.length);
  });
});
