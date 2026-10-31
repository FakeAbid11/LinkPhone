import { describe, expect, it } from 'vitest';
import { isSameSlash24, isValidIPv4 } from '../src/shared/ipv4';

describe('isValidIPv4', () => {
  it('accepts a LAN address and the usable boundaries', () => {
    expect(isValidIPv4('192.168.0.119')).toBe(true);
    expect(isValidIPv4('10.0.0.1')).toBe(true);
    expect(isValidIPv4('1.0.0.1')).toBe(true);
    expect(isValidIPv4('223.255.255.254')).toBe(true);
  });

  it('rejects the wrong number of octets', () => {
    expect(isValidIPv4('192.168.0')).toBe(false);
    expect(isValidIPv4('192.168.0.1.1')).toBe(false);
  });

  it('rejects an octet above 255', () => {
    expect(isValidIPv4('192.168.0.256')).toBe(false);
    expect(isValidIPv4('999.1.1.1')).toBe(false);
  });

  it('rejects a leading zero, which some tools read as octal', () => {
    expect(isValidIPv4('192.168.0.01')).toBe(false);
    expect(isValidIPv4('010.0.0.1')).toBe(false);
  });

  it('rejects this-network and loopback addresses', () => {
    expect(isValidIPv4('0.0.0.0')).toBe(false);
    expect(isValidIPv4('0.1.2.3')).toBe(false);
    expect(isValidIPv4('127.0.0.1')).toBe(false);
  });

  it('rejects link-local, multicast and broadcast', () => {
    expect(isValidIPv4('169.254.1.1')).toBe(false);
    expect(isValidIPv4('224.0.0.1')).toBe(false);
    expect(isValidIPv4('255.255.255.255')).toBe(false);
  });

  it('rejects whitespace instead of trimming it', () => {
    // What is validated is what gets pasted, so a stray space must fail here
    // rather than be silently removed and then pasted.
    expect(isValidIPv4(' 192.168.0.119')).toBe(false);
    expect(isValidIPv4('192.168.0.119 ')).toBe(false);
    expect(isValidIPv4('192.168 .0.1')).toBe(false);
    expect(isValidIPv4('')).toBe(false);
  });

  it('rejects a CIDR suffix, a port and a hostname', () => {
    expect(isValidIPv4('192.168.0.0/24')).toBe(false);
    expect(isValidIPv4('192.168.0.119:48921')).toBe(false);
    expect(isValidIPv4('localhost')).toBe(false);
    expect(isValidIPv4('desktop-pc.local')).toBe(false);
  });

  it('rejects PowerShell metacharacters', () => {
    expect(isValidIPv4("192.168.0.1'")).toBe(false);
    expect(isValidIPv4('$(whoami)')).toBe(false);
    expect(isValidIPv4('1.2.3.4;x')).toBe(false);
    expect(isValidIPv4('1.2.3.4|x')).toBe(false);
  });
});

describe('isSameSlash24', () => {
  it('matches addresses that share the first three octets', () => {
    expect(isSameSlash24('192.168.0.111', '192.168.0.1')).toBe(true);
  });

  it('separates addresses on different ranges', () => {
    expect(isSameSlash24('192.168.0.111', '10.8.0.1')).toBe(false);
    expect(isSameSlash24('192.168.0.111', '192.168.1.1')).toBe(false);
  });

  it('does not match a malformed value', () => {
    expect(isSameSlash24('192.168.0', '192.168.0.1')).toBe(false);
  });
});
