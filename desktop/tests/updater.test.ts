import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Logger } from '../src/main/core/logger';
import { UpdateService } from '../src/main/updater/UpdateService';
import { parseChecksum, checksumsEqual, installerFileName } from '../src/main/updater/download';
import { compareVersions, isNewerVersion, normalizeVersion } from '../src/main/updater/version';
import type { ReleaseInfo, UpdateClient } from '../src/main/updater/github';

const dirs: string[] = [];

afterEach(() => {
  vi.useRealTimers();
  for (const dir of dirs.splice(0)) fs.rmSync(dir, { recursive: true, force: true });
});

function tempDir(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-update-'));
  dirs.push(dir);
  return dir;
}

function release(version = '0.4.1'): ReleaseInfo {
  return {
    version,
    notes: 'Release notes',
    pageUrl: 'https://example.test/release',
    setup: {
      name: `LinkPhone-Setup-${version}.exe`,
      url: 'https://example.test/setup.exe',
    },
    checksums: { name: 'SHA256SUMS.txt', url: 'https://example.test/SHA256SUMS.txt' },
  };
}

function fakeClient(
  content = 'installer-bytes',
  version = '0.4.1',
): { client: UpdateClient; latestRelease: ReturnType<typeof vi.fn>; checksum: string } {
  const checksum = crypto.createHash('sha256').update(content).digest('hex');
  const latestRelease = vi.fn(async () => release(version));
  const client: UpdateClient = {
    latestRelease,
    readText: async (url) => {
      if (url.endsWith('SHA256SUMS.txt')) {
        return `${checksum}  LinkPhone-Setup-${version}.exe\n`;
      }
      return '';
    },
    download: async (_url, target, _maxBytes, onProgress) => {
      await fs.promises.writeFile(target, content);
      onProgress(50);
      onProgress(100);
    },
  };
  return { client, latestRelease, checksum };
}

function service(
  client: UpdateClient | null,
  overrides: Partial<ConstructorParameters<typeof UpdateService>[0]> = {},
) {
  const dir = tempDir();
  const install = vi.fn();
  const notify = vi.fn();
  const instance = new UpdateService({
    mode: 'installed',
    currentVersion: '0.4.0',
    autoCheck: false,
    updatesDir: path.join(dir, 'updates'),
    client,
    logger: new Logger(path.join(dir, 'log.txt')),
    onChange: () => undefined,
    notify,
    install,
    now: () => 1_700_000_000_000,
    ...overrides,
  });
  return { instance, install, notify, dir };
}

describe('update versions', () => {
  it('normalizes tags and compares numerically', () => {
    expect(normalizeVersion('v0.4.1')).toBe('0.4.1');
    expect(normalizeVersion('0.4.0-beta.1')).toBe('0.4.0');
    expect(normalizeVersion('nope')).toBeNull();
    expect(compareVersions('0.4.0', '0.3.9')).toBe(1);
    expect(compareVersions('0.3.9', '0.4.0')).toBe(-1);
    expect(compareVersions('0.4.0', '0.4.0')).toBe(0);
    expect(isNewerVersion('0.4.1', '0.4.0')).toBe(true);
  });

  it('parses release checksums and validates installer names', () => {
    const text = `${'a'.repeat(64)}  LinkPhone-Setup-0.4.1.exe\n${'b'.repeat(64)} *other.exe\n`;
    expect(parseChecksum(text, 'LinkPhone-Setup-0.4.1.exe')).toBe('a'.repeat(64));
    expect(parseChecksum(text, 'missing.exe')).toBeNull();
    expect(checksumsEqual('a'.repeat(64), 'a'.repeat(64))).toBe(true);
    expect(checksumsEqual('a'.repeat(64), 'b'.repeat(64))).toBe(false);
    expect(installerFileName('0.4.1')).toBe('LinkPhone-Setup-0.4.1.exe');
  });
});

describe('update service', () => {
  it('finds, downloads, verifies, and installs a newer release', async () => {
    const { client } = fakeClient();
    const { instance, install, notify } = service(client);

    await instance.check();
    const status = instance.statusSnapshot();
    expect(status.phase).toBe('ready');
    expect(status.version).toBe('0.4.1');
    expect(status.progress).toBe(100);
    expect(status.supported).toBe(true);
    expect(notify).toHaveBeenCalledTimes(1);

    instance.install();
    expect(install).toHaveBeenCalledTimes(1);
    expect(install.mock.calls[0][0]).toMatch(/LinkPhone-Setup-0\.4\.1\.exe$/);
  });

  it('reports up to date without downloading', async () => {
    const { client } = fakeClient('installer-bytes', '0.4.0');
    const { instance } = service(client);
    await instance.check();
    expect(instance.statusSnapshot().phase).toBe('up-to-date');
  });

  it('rejects a mismatched checksum and leaves no installer', async () => {
    const { client } = fakeClient();
    client.readText = async () => `${'0'.repeat(64)}  LinkPhone-Setup-0.4.1.exe\n`;
    const { instance } = service(client);
    await instance.check();
    expect(instance.statusSnapshot().phase).toBe('error');
    expect(instance.statusSnapshot().error).toMatch(/SHA-256/i);
    expect(() => instance.install()).toThrow(/No update/i);
  });

  it('does not check on portable or disabled builds', async () => {
    const { client, latestRelease } = fakeClient();
    const portable = service(client, { mode: 'portable' }).instance;
    await portable.check();
    expect(portable.statusSnapshot().phase).toBe('unsupported');
    expect(latestRelease).not.toHaveBeenCalled();

    const disabled = service(null, { mode: 'disabled' }).instance;
    await disabled.check();
    expect(disabled.statusSnapshot().mode).toBe('disabled');
  });

  it('schedules auto-checks and stops them', async () => {
    vi.useFakeTimers();
    const { client, latestRelease } = fakeClient();
    const { instance } = service(client, { autoCheck: true });
    instance.start();
    await vi.advanceTimersByTimeAsync(15_000);
    expect(latestRelease).toHaveBeenCalledTimes(1);
    instance.stop();
    await vi.advanceTimersByTimeAsync(6 * 60 * 60 * 1_000);
    expect(latestRelease).toHaveBeenCalledTimes(1);
  });

  it('auto-downloads after a manual check even when auto-check is off', async () => {
    const { client } = fakeClient();
    const { instance } = service(client, { autoCheck: false });
    await instance.check();
    expect(instance.statusSnapshot().phase).toBe('ready');
  });
});
