import dgram from 'node:dgram';
import os from 'node:os';

/**
 * The local address the OS would use for an outbound route, or null when there
 * is none. `connect()` on a UDP socket only resolves the route — no packet is
 * sent — so this works offline and on CI runners without a default gateway.
 */
function defaultRouteAddress(): string | null {
  const socket = dgram.createSocket('udp4');
  // dgram reports failures asynchronously; an unhandled 'error' would crash the
  // main process, so the listener stays attached until the socket closes.
  socket.on('error', () => undefined);
  try {
    socket.connect(53, '8.8.8.8');
    const { address } = socket.address();
    return typeof address === 'string' && /^\d{1,3}(\.\d{1,3}){3}$/.test(address)
      ? address
      : null;
  } catch {
    return null;
  } finally {
    try {
      socket.close();
    } catch {
      // Already closed.
    }
  }
}

/**
 * Adapter names that never carry the LAN the phone is on.
 *
 * A VPN tunnel and a Hyper-V/WSL/VMware switch both hold a perfectly ordinary
 * RFC1918 address, so the address alone cannot tell us the phone has no route to
 * it. Matching is anchored where the vendor name starts the alias, and open
 * where the vendor name can appear after a prefix ("Hyper-V Virtual Ethernet
 * Adapter #2").
 */
const VIRTUAL_INTERFACE_PATTERNS: RegExp[] = [
  /^vEthernet/i,
  /hyper-?v/i,
  /^VMware/i,
  /^VirtualBox/i,
  /^vboxnet/i,
  /^tailscale/i,
  /^wireguard/i,
  /^wg\d/i,
  /^zerotier/i,
  /^zt[a-z0-9]+/i,
  /^wsl/i,
  /^docker/i,
  /^br-/i,
  /^utun/i,
  /^tun\d/i,
  /^tap\d/i,
  /^npcap/i,
  /^loopback/i,
  /^bluetooth/i,
];

export function isVirtualInterface(name: string): boolean {
  const trimmed = name.trim();
  if (!trimmed) return false;
  return VIRTUAL_INTERFACE_PATTERNS.some((pattern) => pattern.test(trimmed));
}

/**
 * Default-route address first, then physical RFC1918, then virtual adapters,
 * then the rest. The pairing QR can only carry one host, and a VPN or Hyper-V
 * adapter would otherwise win — which is how a code ends up pointing at an
 * address that refuses every connection.
 *
 * `interfaceNames` maps an address to the adapter alias that owns it. It is
 * optional so existing callers keep the previous ordering exactly.
 */
export function orderAddresses(
  addresses: string[],
  preferred?: string | null,
  interfaceNames?: Record<string, string>,
): string[] {
  const isVirtual = (address: string): boolean => {
    const name = interfaceNames?.[address];
    return Boolean(name && isVirtualInterface(name));
  };
  const rank = (address: string): number => {
    if (isVirtual(address)) return 2;
    return isPrivateIPv4(address) ? 0 : 1;
  };
  // A preferred address on a virtual adapter routes to the internet but not to
  // the phone, so it must not be preferred.
  const usablePreferred = preferred && !isVirtual(preferred) ? preferred : null;

  return [...addresses].sort((a, b) => {
    if (usablePreferred) {
      if (a === usablePreferred && b !== usablePreferred) return -1;
      if (b === usablePreferred && a !== usablePreferred) return 1;
    }
    const byRank = rank(a) - rank(b);
    return byRank !== 0 ? byRank : a.localeCompare(b);
  });
}

export function getLanAddresses(): string[] {
  const addresses: string[] = [];
  const interfaceNames: Record<string, string> = {};
  const interfaces = os.networkInterfaces();
  for (const [name, entries] of Object.entries(interfaces)) {
    for (const entry of entries ?? []) {
      if (entry.family !== 'IPv4') continue;
      if (entry.internal) continue;
      if (entry.address.startsWith('169.254.')) continue;
      if (!addresses.includes(entry.address)) {
        addresses.push(entry.address);
        interfaceNames[entry.address] = name;
      }
    }
  }
  return orderAddresses(addresses, defaultRouteAddress(), interfaceNames);
}

export function isPrivateIPv4(address: string): boolean {
  const parts = address.split('.').map(Number);
  if (parts.length !== 4 || parts.some((part) => !Number.isInteger(part))) return false;
  if (parts[0] === 10) return true;
  if (parts[0] === 192 && parts[1] === 168) return true;
  if (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) return true;
  return false;
}

export function buildPairingUri(input: {
  host: string;
  port: number;
  fingerprint: string;
  token: string;
  pcName: string;
}): string {
  const params = new URLSearchParams({
    h: input.host,
    p: String(input.port),
    f: input.fingerprint,
    t: input.token,
    n: input.pcName.slice(0, 80),
  });
  return `linkphone://pair?${params.toString()}`;
}
