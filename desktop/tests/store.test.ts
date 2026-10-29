import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { Store } from '../src/main/core/store';

const dirs: string[] = [];

function tempFile(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-store-'));
  dirs.push(dir);
  return path.join(dir, 'state.json');
}

afterEach(() => {
  for (const dir of dirs.splice(0)) fs.rmSync(dir, { recursive: true, force: true });
});

describe('settings store', () => {
  it('creates defaults and persists paired devices', () => {
    const file = tempFile();
    const store = new Store(file);
    expect(store.pcId).toMatch(/^[0-9a-f-]{36}$/);
    expect(store.pcName.length).toBeGreaterThan(0);
    expect(store.devices).toHaveLength(0);
    expect(store.settings.autoCheckUpdates).toBe(true);

    store.addDevice({
      id: 'phone-1',
      name: 'Pixel',
      platform: 'android',
      publicKeyPem: '-----BEGIN PUBLIC KEY-----\nkey\n-----END PUBLIC KEY-----',
      fingerprint: 'ab'.repeat(32),
      pairedAt: Date.now(),
    });
    store.updateSettings({ notificationsToasts: false, autoCheckUpdates: false, mutePackages: ['a', 'a', 'b'] });

    const reloaded = new Store(file);
    expect(reloaded.devices).toHaveLength(1);
    expect(reloaded.devices[0].name).toBe('Pixel');
    expect(reloaded.settings.notificationsToasts).toBe(false);
    expect(reloaded.settings.autoCheckUpdates).toBe(false);
    expect(reloaded.settings.mutePackages).toEqual(['a', 'b']);
  });

  it('removes devices and resets the identity', () => {
    const file = tempFile();
    const store = new Store(file);
    store.addDevice({
      id: 'phone-1',
      name: 'Pixel',
      platform: 'android',
      publicKeyPem: 'pem',
      fingerprint: 'cd'.repeat(32),
      pairedAt: Date.now(),
    });
    store.removeDevice('phone-1');
    expect(store.devices).toHaveLength(0);

    const oldId = store.pcId;
    store.resetIdentity();
    expect(store.pcId).not.toBe(oldId);
    expect(store.devices).toHaveLength(0);
  });

  it('persists per-device SMS read ids and clears them with the device', () => {
    const file = tempFile();
    const store = new Store(file);
    store.addDevice({
      id: 'phone-1',
      name: 'Pixel',
      platform: 'android',
      publicKeyPem: 'pem',
      fingerprint: 'ab'.repeat(32),
      pairedAt: Date.now(),
    });
    store.addReadSms('phone-1', ['m1', 'm2', 'm1']);
    store.addReadSms('phone-2', ['m3']);

    expect(store.readSmsFor('phone-1')).toEqual(['m1', 'm2']);
    const reloaded = new Store(file);
    expect(reloaded.readSmsFor('phone-1')).toEqual(['m1', 'm2']);
    expect(reloaded.readSmsFor('phone-2')).toEqual(['m3']);

    reloaded.removeDevice('phone-1');
    expect(reloaded.readSmsFor('phone-1')).toEqual([]);
    expect(new Store(file).readSmsFor('phone-1')).toEqual([]);

    reloaded.removeReadSms('phone-2');
    expect(reloaded.readSmsFor('phone-2')).toEqual([]);
  });

  it('caps stored read ids per device', () => {
    const store = new Store(tempFile());
    const ids = Array.from({ length: 5_005 }, (_, index) => `id-${index}`);
    store.addReadSms('phone-1', ids);
    const stored = store.readSmsFor('phone-1');
    expect(stored).toHaveLength(5_000);
    expect(stored[0]).toBe('id-5');
    expect(stored[stored.length - 1]).toBe('id-5004');
  });

  it('clears read ids on identity reset', () => {
    const store = new Store(tempFile());
    store.addReadSms('phone-1', ['a', 'b']);
    store.resetIdentity();
    expect(store.readSmsFor('phone-1')).toEqual([]);
  });

  it('backs up a corrupt store and starts fresh', () => {
    const file = tempFile();
    fs.writeFileSync(file, 'not json at all');
    const store = new Store(file);
    expect(store.devices).toHaveLength(0);
    const backups = fs
      .readdirSync(path.dirname(file))
      .filter((name) => name.includes('.corrupt-'));
    expect(backups.length).toBe(1);
  });
});
