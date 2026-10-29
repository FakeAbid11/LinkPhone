import fs from 'node:fs';
import path from 'node:path';
import type { Logger } from '../core/logger';
import type { UpdateMode, UpdateStatus } from '../../shared/types';
import {
  checksumsEqual,
  installerFileName,
  parseChecksum,
  sha256File,
} from './download';
import type { ReleaseInfo, UpdateClient } from './github';
import { compareVersions } from './version';

const MAX_INSTALLER_BYTES = 300 * 1024 * 1024;
const AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1_000;
const INITIAL_CHECK_DELAY_MS = 15_000;

export interface UpdateServiceOptions {
  mode: UpdateMode;
  currentVersion: string;
  autoCheck: boolean;
  updatesDir: string;
  client: UpdateClient | null;
  logger: Logger;
  onChange: () => void;
  notify: (title: string, body: string) => void;
  install: (installerPath: string) => void;
  now?: () => number;
}

export class UpdateService {
  private status: UpdateStatus;
  private readonly supported: boolean;
  private autoCheck: boolean;
  private release: ReleaseInfo | undefined;
  private installerPath: string | undefined;
  private checkInFlight: Promise<void> | undefined;
  private downloadInFlight: Promise<void> | undefined;
  private initialTimer: NodeJS.Timeout | undefined;
  private intervalTimer: NodeJS.Timeout | undefined;
  private stopped = false;
  private notifiedVersion: string | undefined;
  private lastProgressAt = 0;
  private lastProgress = -1;

  constructor(private readonly options: UpdateServiceOptions) {
    this.supported = options.mode === 'installed' && options.client !== null;
    this.autoCheck = options.autoCheck;
    this.status = {
      supported: this.supported,
      mode: options.mode,
      phase: this.supported ? 'idle' : 'unsupported',
      currentVersion: options.currentVersion,
    };
  }

  start(): void {
    if (!this.supported || this.stopped) return;
    void this.cleanupOldInstallers();
    if (this.autoCheck) this.scheduleAutoCheck();
  }

  stop(): void {
    this.stopped = true;
    this.clearTimers();
  }

  setAutoCheck(enabled: boolean): void {
    if (this.autoCheck === enabled) return;
    this.autoCheck = enabled;
    this.clearTimers();
    if (enabled && this.supported && !this.stopped) this.scheduleAutoCheck();
  }

  statusSnapshot(): UpdateStatus {
    return { ...this.status };
  }

  async check(): Promise<void> {
    if (!this.supported || !this.options.client) return;
    if (this.checkInFlight) return this.checkInFlight;
    if (this.downloadInFlight) return this.downloadInFlight;
    const running = this.runCheck().finally(() => {
      this.checkInFlight = undefined;
    });
    this.checkInFlight = running;
    return running;
  }

  async download(): Promise<void> {
    if (!this.supported || !this.options.client) return;
    if (this.downloadInFlight) return this.downloadInFlight;
    if (!this.release) throw new Error('No update is available');
    const running = this.runDownload(this.release).finally(() => {
      this.downloadInFlight = undefined;
    });
    this.downloadInFlight = running;
    return running;
  }

  install(): void {
    if (this.status.phase !== 'ready' || !this.installerPath) {
      throw new Error('No update is ready to install');
    }
    this.options.install(this.installerPath);
  }

  private scheduleAutoCheck(): void {
    this.clearTimers();
    this.initialTimer = setTimeout(() => {
      this.initialTimer = undefined;
      void this.check();
    }, INITIAL_CHECK_DELAY_MS);
    this.initialTimer.unref?.();
    this.intervalTimer = setInterval(() => {
      void this.check();
    }, AUTO_CHECK_INTERVAL_MS);
    this.intervalTimer.unref?.();
  }

  private clearTimers(): void {
    if (this.initialTimer) clearTimeout(this.initialTimer);
    if (this.intervalTimer) clearInterval(this.intervalTimer);
    this.initialTimer = undefined;
    this.intervalTimer = undefined;
  }

  private async runCheck(): Promise<void> {
    const client = this.options.client;
    if (!client) return;
    this.patch({ phase: 'checking', error: undefined });
    try {
      const release = await client.latestRelease();
      const checkedAt = this.now();
      if (!release) {
        this.release = undefined;
        this.patch({
          phase: 'up-to-date',
          checkedAt,
          version: undefined,
          notes: undefined,
          progress: undefined,
        });
        return;
      }
      if (compareVersions(release.version, this.options.currentVersion) <= 0) {
        this.release = undefined;
        this.patch({
          phase: 'up-to-date',
          checkedAt,
          version: undefined,
          notes: undefined,
          progress: undefined,
        });
        return;
      }
      this.release = release;
      if (this.status.phase === 'ready' && this.status.version === release.version) {
        this.patch({ phase: 'ready', checkedAt, error: undefined });
        return;
      }
      this.patch({
        phase: 'available',
        checkedAt,
        version: release.version,
        notes: release.notes?.slice(0, 4_000),
        progress: undefined,
        error: undefined,
      });
      await this.download();
    } catch (error) {
      this.fail(error);
    }
  }

  private async runDownload(release: ReleaseInfo): Promise<void> {
    const client = this.options.client;
    if (!client) return;
    const fileName = installerFileName(release.version);
    const target = path.join(this.options.updatesDir, fileName);
    const partial = `${target}.part`;
    this.lastProgressAt = 0;
    this.lastProgress = -1;
    this.patch({ phase: 'downloading', progress: 0, error: undefined });
    try {
      const checksumText = await client.readText(release.checksums.url);
      const expected = parseChecksum(checksumText, fileName);
      if (!expected) {
        throw new Error(`SHA256SUMS.txt does not list ${fileName}`);
      }
      await fs.promises.mkdir(this.options.updatesDir, { recursive: true });
      await client.download(release.setup.url, partial, MAX_INSTALLER_BYTES, (percent) => {
        this.updateProgress(percent);
      });
      const actual = await sha256File(partial);
      if (!checksumsEqual(actual, expected)) {
        throw new Error('The downloaded installer failed its SHA-256 check');
      }
      await fs.promises.rm(target, { force: true });
      await fs.promises.rename(partial, target);
      this.installerPath = target;
      this.patch({ phase: 'ready', progress: 100, checkedAt: this.now() });
      this.options.logger.log('Update', `Downloaded LinkPhone ${release.version}`);
      if (this.notifiedVersion !== release.version) {
        this.notifiedVersion = release.version;
        this.options.notify(
          'Update ready',
          `LinkPhone ${release.version} is downloaded. Restart to install it.`,
        );
      }
    } catch (error) {
      await fs.promises.rm(partial, { force: true }).catch(() => undefined);
      this.installerPath = undefined;
      this.fail(error);
    }
  }

  private updateProgress(percent: number): void {
    const value = Math.max(0, Math.min(100, Math.round(percent)));
    const now = this.now();
    if (value === this.lastProgress) return;
    if (value < 100 && now - this.lastProgressAt < 200) return;
    this.lastProgress = value;
    this.lastProgressAt = now;
    this.patch({ progress: value });
  }

  private fail(error: unknown): void {
    const message = error instanceof Error ? error.message : String(error);
    this.options.logger.log('Update', message);
    this.patch({
      phase: 'error',
      error: message.slice(0, 300),
      progress: undefined,
    });
  }

  private patch(partial: Partial<UpdateStatus>): void {
    this.status = { ...this.status, ...partial };
    this.options.onChange();
  }

  private async cleanupOldInstallers(): Promise<void> {
    try {
      const entries = await fs.promises.readdir(this.options.updatesDir);
      await Promise.all(
        entries
          .filter((name) => name.endsWith('.part') || /^LinkPhone-Setup-.*\.exe$/i.test(name))
          .map((name) =>
            fs.promises
              .rm(path.join(this.options.updatesDir, name), { force: true })
              .catch(() => undefined),
          ),
      );
    } catch {
      // The directory may not exist yet.
    }
  }

  private now(): number {
    return this.options.now ? this.options.now() : Date.now();
  }
}
