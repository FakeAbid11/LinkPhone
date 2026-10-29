export function normalizeVersion(value: string): string | null {
  const match = value.trim().replace(/^v/i, '').match(/^(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$/);
  if (!match) return null;
  return `${Number(match[1])}.${Number(match[2])}.${Number(match[3])}`;
}

function parts(value: string): [number, number, number] | null {
  const normalized = normalizeVersion(value);
  if (!normalized) return null;
  const [major, minor, patch] = normalized.split('.').map(Number);
  return [major, minor, patch];
}

export function compareVersions(left: string, right: string): number {
  const a = parts(left);
  const b = parts(right);
  if (!a || !b) throw new Error(`Invalid version: ${!a ? left : right}`);
  for (let index = 0; index < 3; index += 1) {
    if (a[index] !== b[index]) return a[index] > b[index] ? 1 : -1;
  }
  return 0;
}

export function isNewerVersion(candidate: string, current: string): boolean {
  return compareVersions(candidate, current) > 0;
}
