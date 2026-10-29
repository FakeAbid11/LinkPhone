import crypto from 'node:crypto';
import fs from 'node:fs';

export function parseChecksum(text: string, fileName: string): string | null {
  for (const line of text.split(/\r?\n/)) {
    const match = line.trim().match(/^([a-fA-F0-9]{64})\s+\*?(.+?)\s*$/);
    if (!match) continue;
    const name = match[2].replace(/^.*[\\/]/, '');
    if (name === fileName) return match[1].toLowerCase();
  }
  return null;
}

export function sha256File(filePath: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    const stream = fs.createReadStream(filePath);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.on('error', reject);
    stream.on('end', () => resolve(hash.digest('hex')));
  });
}

export function checksumsEqual(actual: string, expected: string): boolean {
  if (!/^[a-f0-9]{64}$/i.test(actual) || !/^[a-f0-9]{64}$/i.test(expected)) return false;
  const left = Buffer.from(actual, 'hex');
  const right = Buffer.from(expected, 'hex');
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

export function installerFileName(version: string): string {
  if (!/^\d+\.\d+\.\d+$/.test(version)) throw new Error('Invalid update version');
  return `LinkPhone-Setup-${version}.exe`;
}
