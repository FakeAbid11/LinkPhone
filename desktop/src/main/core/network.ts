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
 * Default-route address first, then RFC1918, then the rest. The pairing QR can
 * only carry one host, and a VPN or Hyper-V adapter would otherwise win.
 */
export function orderAddresses(addresses: string[], preferred?: string | null): string[] {
  return [...addresses].sort((a, b) => {
    if (preferred) {
      if (a === preferred && b !== preferred) return -1;
      if (b === preferred && a !== preferred) return 1;
    }
    const aPrivate = isPrivateIPv4(a) ? 0 : 1;
    const bPrivate = isPrivateIPv4(b) ? 0 : 1;
    return aPrivate - bPrivate || a.localeCompare(b);
  });
}

export function getLanAddresses(): string[] {
  const addresses: string[] = [];
  const interfaces = os.networkInterfaces();
  for (const entries of Object.values(interfaces)) {
    for (const entry of entries ?? []) {
      if (entry.family !== 'IPv4') continue;
      if (entry.internal) continue;
      if (entry.address.startsWith('169.254.')) continue;
      if (!addresses.includes(entry.address)) addresses.push(entry.address);
    }
  }
  return orderAddresses(addresses, defaultRouteAddress());
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
