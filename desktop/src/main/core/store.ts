import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import type { PairedDeviceInfo, Settings } from '../../shared/types';

export interface DeviceRecord {
  id: string;
  name: string;
  platform: string;
  publicKeyPem: string;
  fingerprint: string;
  pairedAt: number;
  lastSeen?: number;
}

interface AppData {
  version: 1;
  pcId: string;
  pcName: string;
  devices: DeviceRecord[];
  settings: Settings;
  /** Message ids marked read locally, keyed by paired phone device id. */
  readSms: Record<string, string[]>;
}

const MAX_READ_SMS_IDS_PER_DEVICE = 5_000;

const DEFAULT_SETTINGS: Settings = {
  notificationsToasts: true,
  autoClipboard: false,
  launchAtLogin: false,
  autoCheckUpdates: true,
  mutePackages: [],
};

export class Store {
  readonly data: AppData;
  private readonly filePath: string;

  constructor(filePath: string) {
    this.filePath = filePath;
    fs.mkdirSync(path.dirname(filePath), { recursive: true });
    this.data = loadData(filePath);
  }

  get pcId(): string {
    return this.data.pcId;
  }

  get pcName(): string {
    return this.data.pcName;
  }

  get devices(): DeviceRecord[] {
    return this.data.devices;
  }

  get settings(): Settings {
    return this.data.settings;
  }

  publicDevices(): PairedDeviceInfo[] {
    return this.data.devices.map((device) => ({
      id: device.id,
      name: device.name,
      platform: device.platform,
      fingerprint: device.fingerprint,
      pairedAt: device.pairedAt,
      lastSeen: device.lastSeen,
    }));
  }

  findDevice(id: string): DeviceRecord | undefined {
    return this.data.devices.find((device) => device.id === id);
  }

  addDevice(device: DeviceRecord): void {
    this.data.devices = this.data.devices.filter((existing) => existing.id !== device.id);
    this.data.devices.push(device);
    this.save();
  }

  removeDevice(id: string): void {
    this.data.devices = this.data.devices.filter((device) => device.id !== id);
    delete this.data.readSms[id];
    this.save();
  }

  readSmsFor(deviceId: string): string[] {
    return [...(this.data.readSms[deviceId] ?? [])];
  }

  addReadSms(deviceId: string, ids: string[]): void {
    const valid = ids.filter((id) => typeof id === 'string' && id.length > 0);
    if (!deviceId || valid.length === 0) return;
    const set = new Set(this.data.readSms[deviceId] ?? []);
    for (const id of valid) set.add(id);
    this.data.readSms[deviceId] = [...set].slice(-MAX_READ_SMS_IDS_PER_DEVICE);
    this.save();
  }

  removeReadSms(deviceId: string): void {
    if (!this.data.readSms[deviceId]) return;
    delete this.data.readSms[deviceId];
    this.save();
  }

  touchDevice(id: string): void {
    const device = this.findDevice(id);
    if (!device) return;
    device.lastSeen = Date.now();
    this.save();
  }

  updateSettings(patch: Partial<Settings>): void {
    this.data.settings = {
      ...this.data.settings,
      ...patch,
      mutePackages: Array.isArray(patch.mutePackages)
        ? [...new Set(patch.mutePackages.filter((item) => typeof item === 'string'))]
        : this.data.settings.mutePackages,
    };
    this.save();
  }

  resetIdentity(): void {
    this.data.pcId = randomUUID();
    this.data.devices = [];
    this.data.readSms = {};
    this.save();
  }

  save(): void {
    const json = JSON.stringify(this.data, null, 2);
    const temp = `${this.filePath}.tmp`;
    const backup = `${this.filePath}.bak`;
    fs.writeFileSync(temp, json, { mode: 0o600 });
    // Keep the last good copy: a crash between writing the temp file and
    // replacing the live one must not destroy the PC identity or the paired
    // phones. Without this, a truncated state.json silently re-generates a new
    // pcId and every phone has to pair again.
    try {
      if (fs.existsSync(this.filePath)) fs.copyFileSync(this.filePath, backup);
    } catch {
      // A missing backup must never block the write.
    }
    try {
      // rename() swaps the file in one step; copyFileSync truncates the
      // destination first, which is the window this is closing.
      fs.renameSync(temp, this.filePath);
    } catch {
      // Some Windows setups (indexer, antivirus) hold the target briefly.
      fs.copyFileSync(temp, this.filePath);
      fs.rmSync(temp, { force: true });
    }
  }
}

function loadData(filePath: string): AppData {
  const defaults = (): AppData => ({
    version: 1,
    pcId: randomUUID(),
    pcName: defaultPcName(),
    devices: [],
    settings: { ...DEFAULT_SETTINGS, mutePackages: [] },
    readSms: {},
  });

  if (!fs.existsSync(filePath)) {
    const data = defaults();
    fs.writeFileSync(filePath, JSON.stringify(data, null, 2), { mode: 0o600 });
    return data;
  }

  let raw: string | undefined;
  try {
    raw = fs.readFileSync(filePath, 'utf8');
  } catch {
    raw = undefined;
  }
  const parsed = raw === undefined ? null : parseData(raw);
  if (parsed) return parsed;

  // Unreadable or invalid: keep the bytes for diagnosis, then prefer the last
  // good snapshot over discarding the PC identity and the paired phones.
  try {
    fs.copyFileSync(filePath, `${filePath}.corrupt-${Date.now()}`);
  } catch {
    // Ignore backup failure.
  }

  const recovered = readDataFile(`${filePath}.bak`);
  if (recovered) {
    fs.writeFileSync(filePath, JSON.stringify(recovered, null, 2), { mode: 0o600 });
    return recovered;
  }

  const data = defaults();
  fs.writeFileSync(filePath, JSON.stringify(data, null, 2), { mode: 0o600 });
  return data;
}

/** Parses and normalizes a state file, or null when it is not usable. */
function parseData(raw: string): AppData | null {
  try {
    const parsed = JSON.parse(raw) as Partial<AppData>;
    if (!parsed || parsed.version !== 1 || typeof parsed.pcId !== 'string') {
      return null;
    }
    const devices = Array.isArray(parsed.devices)
      ? parsed.devices.filter(
          (device): device is DeviceRecord =>
            Boolean(device) &&
            typeof device.id === 'string' &&
            typeof device.name === 'string' &&
            typeof device.publicKeyPem === 'string' &&
            typeof device.fingerprint === 'string',
        )
      : [];
    const settings: Settings = {
      ...DEFAULT_SETTINGS,
      ...(parsed.settings ?? {}),
      mutePackages: Array.isArray(parsed.settings?.mutePackages)
        ? parsed.settings.mutePackages.filter((item): item is string => typeof item === 'string')
        : [],
    };
    const readSms: Record<string, string[]> = {};
    if (parsed.readSms && typeof parsed.readSms === 'object') {
      for (const [deviceId, value] of Object.entries(parsed.readSms)) {
        if (!deviceId || !Array.isArray(value)) continue;
        readSms[deviceId] = value
          .filter((id): id is string => typeof id === 'string' && id.length > 0)
          .slice(-MAX_READ_SMS_IDS_PER_DEVICE);
      }
    }
    return {
      version: 1,
      pcId: parsed.pcId,
      pcName: typeof parsed.pcName === 'string' && parsed.pcName.trim()
        ? parsed.pcName
        : defaultPcName(),
      devices,
      settings,
      readSms,
    };
  } catch {
    return null;
  }
}

function readDataFile(filePath: string): AppData | null {
  try {
    if (!fs.existsSync(filePath)) return null;
    return parseData(fs.readFileSync(filePath, 'utf8'));
  } catch {
    return null;
  }
}

function defaultPcName(): string {
  const hostname = os.hostname().replace(/\.local$/i, '').trim();
  return hostname || 'My PC';
}
