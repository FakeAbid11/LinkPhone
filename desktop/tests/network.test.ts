import os from 'node:os';
import { describe, expect, it } from 'vitest';
import {
  getLanAddresses,
  isPrivateIPv4,
  isVirtualInterface,
  orderAddresses,
  shouldRenewOffer,
} from '../src/main/core/network';

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

/**
 * A VPN tunnel or a Hyper-V switch holds an ordinary RFC1918 address, so the
 * address alone cannot tell us the phone has no route to it. Before this, the
 * pairing QR could carry the virtual adapter's address and every connection
 * attempt was refused.
 */
describe('virtual adapter handling', () => {
  const aliases: Record<string, string> = {
    '172.20.0.1': 'vEthernet (WSL)',
    '192.168.0.111': 'Wi-Fi',
    '10.8.0.2': 'WireGuard Tunnel',
  };

  it('demotes a virtual adapter below the physical LAN', () => {
    const ordered = orderAddresses(['172.20.0.1', '192.168.0.111'], null, aliases);
    expect(ordered).toEqual(['192.168.0.111', '172.20.0.1']);
  });

  it('does not let a VPN default route win', () => {
    const ordered = orderAddresses(['10.8.0.2', '192.168.0.111'], '10.8.0.2', aliases);
    expect(ordered[0]).toBe('192.168.0.111');
  });

  it('still prefers a physical default route', () => {
    const ordered = orderAddresses(['10.8.0.2', '192.168.0.111'], '192.168.0.111', aliases);
    expect(ordered[0]).toBe('192.168.0.111');
  });

  it('keeps the previous ordering when adapter names are unknown', () => {
    // Both addresses are private, so the legacy rule is plain alphabetical order.
    expect(orderAddresses(['172.20.0.1', '192.168.0.111'], null)).toEqual([
      '172.20.0.1',
      '192.168.0.111',
    ]);
  });

  it('recognises virtual adapters by alias', () => {
    for (const name of [
      'vEthernet (Default Switch)',
      'Hyper-V Virtual Ethernet Adapter',
      'VMware Network Adapter VMnet8',
      'VirtualBox Host-Only Network',
      'Tailscale',
      'WireGuard Tunnel',
      'ZeroTier One [8056c2e2]',
      'WSL',
      'tun0',
    ]) {
      expect(isVirtualInterface(name), name).toBe(true);
    }
    for (const name of ['Wi-Fi', 'Ethernet', 'Ethernet 2', 'Wi-Fi 2']) {
      expect(isVirtualInterface(name), name).toBe(false);
    }
  });
});

describe('stale pairing offer', () => {
  it('does not renew when no code is on screen', () => {
    expect(shouldRenewOffer(undefined, ['192.168.0.111'])).toBe(false);
  });

  it('does not renew when the address list is momentarily empty', () => {
    // Adapters vanish briefly during DHCP renegotiation; a renewal then would
    // have nothing to build a code from.
    expect(shouldRenewOffer('192.168.0.111', [])).toBe(false);
  });

  it('does not renew while the code address is still ours', () => {
    expect(shouldRenewOffer('192.168.0.111', ['192.168.0.111'])).toBe(false);
  });

  it('does not renew when the address moved but is still present', () => {
    // A VPN adapter can take first place without the code ever going stale, and
    // invalidating a working code is worse than leaving it alone.
    expect(shouldRenewOffer('192.168.0.111', ['10.8.0.2', '192.168.0.111'])).toBe(false);
  });

  it('renews when the code address is gone', () => {
    expect(shouldRenewOffer('192.168.0.111', ['192.168.43.7'])).toBe(true);
  });
});
