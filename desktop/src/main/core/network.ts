import os from 'node:os';

export function getLanAddresses(): string[] {
  const addresses: string[] = [];
  const interfaces = os.networkInterfaces();
  for (const entries of Object.values(interfaces)) {
    for (const entry of entries ?? []) {
      if (entry.family !== 'IPv4' && entry.family !== 4) continue;
      if (entry.internal) continue;
      if (entry.address.startsWith('169.254.')) continue;
      if (!addresses.includes(entry.address)) addresses.push(entry.address);
    }
  }
  return addresses.sort((a, b) => {
    const aPrivate = isPrivateIPv4(a) ? 0 : 1;
    const bPrivate = isPrivateIPv4(b) ? 0 : 1;
    return aPrivate - bPrivate || a.localeCompare(b);
  });
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
