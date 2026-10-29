import fs from 'node:fs';
import https from 'node:https';
import type { IncomingMessage } from 'node:http';
import path from 'node:path';
import { normalizeVersion } from './version';

export interface ReleaseAsset {
  name: string;
  url: string;
  size?: number;
}

export interface ReleaseInfo {
  version: string;
  notes?: string;
  pageUrl: string;
  setup: ReleaseAsset;
  checksums: ReleaseAsset;
}

export interface UpdateClient {
  latestRelease(): Promise<ReleaseInfo | null>;
  readText(url: string, maxBytes?: number): Promise<string>;
  download(
    url: string,
    target: string,
    maxBytes: number,
    onProgress: (percent: number) => void,
  ): Promise<void>;
}

interface GitHubAsset {
  name?: unknown;
  browser_download_url?: unknown;
  size?: unknown;
}

interface GitHubRelease {
  tag_name?: unknown;
  body?: unknown;
  html_url?: unknown;
  draft?: unknown;
  prerelease?: unknown;
  assets?: unknown;
}

const MAX_JSON_BYTES = 2 * 1024 * 1024;
const MAX_DOWNLOAD_BYTES = 300 * 1024 * 1024;
const MAX_REDIRECTS = 5;

export class GitHubUpdateClient implements UpdateClient {
  constructor(
    private readonly apiUrl = 'https://api.github.com/repos/FakeAbid11/LinkPhone/releases/latest',
    private readonly userAgent = 'LinkPhone-Updater',
  ) {}

  async latestRelease(): Promise<ReleaseInfo | null> {
    const buffer = await this.request(this.apiUrl, MAX_JSON_BYTES);
    let data: GitHubRelease;
    try {
      data = JSON.parse(buffer.toString('utf8')) as GitHubRelease;
    } catch {
      throw new Error('GitHub returned an invalid update response');
    }
    if (!data || typeof data !== 'object' || data.draft === true || data.prerelease === true) {
      return null;
    }
    const version = typeof data.tag_name === 'string' ? normalizeVersion(data.tag_name) : null;
    if (!version) return null;
    const pageUrl = typeof data.html_url === 'string' ? data.html_url : '';
    if (!pageUrl) throw new Error('GitHub did not provide a release page');

    const assets = Array.isArray(data.assets) ? (data.assets as GitHubAsset[]) : [];
    const setupName = `LinkPhone-Setup-${version}.exe`;
    const setup = assets.find(
      (asset) => asset?.name === setupName && typeof asset.browser_download_url === 'string',
    );
    const checksums = assets.find(
      (asset) => asset?.name === 'SHA256SUMS.txt' && typeof asset.browser_download_url === 'string',
    );
    if (!setup || !checksums) {
      throw new Error('The latest release is missing the Windows installer or SHA256SUMS.txt');
    }
    return {
      version,
      notes: typeof data.body === 'string' ? data.body.slice(0, 4_000) : undefined,
      pageUrl,
      setup: {
        name: setupName,
        url: setup.browser_download_url as string,
        size: typeof setup.size === 'number' ? setup.size : undefined,
      },
      checksums: {
        name: 'SHA256SUMS.txt',
        url: checksums.browser_download_url as string,
        size: typeof checksums.size === 'number' ? checksums.size : undefined,
      },
    };
  }

  async readText(url: string, maxBytes = 128 * 1024): Promise<string> {
    return (await this.request(url, maxBytes)).toString('utf8');
  }

  async download(
    url: string,
    target: string,
    maxBytes: number,
    onProgress: (percent: number) => void,
  ): Promise<void> {
    const limit = Math.min(maxBytes, MAX_DOWNLOAD_BYTES);
    await fs.promises.mkdir(path.dirname(target), { recursive: true });
    const response = await this.open(url);
    const total = Number(response.headers['content-length']) || 0;
    let received = 0;

    try {
      await new Promise<void>((resolve, reject) => {
        const output = fs.createWriteStream(target);
        let settled = false;
        const fail = (error: Error) => {
          if (settled) return;
          settled = true;
          response.destroy();
          output.destroy();
          reject(error);
        };
        response.on('data', (chunk: Buffer) => {
          received += chunk.length;
          if (received > limit) {
            fail(new Error('The update installer is larger than expected'));
            return;
          }
          const percent = total > 0
            ? Math.min(99, Math.floor((received / total) * 100))
            : Math.min(99, Math.floor((received / limit) * 100));
          onProgress(Math.max(0, percent));
        });
        response.on('error', (error) => fail(error));
        output.on('error', (error) => fail(error));
        output.on('finish', () => {
          if (settled) return;
          settled = true;
          resolve();
        });
        response.pipe(output);
      });
      onProgress(100);
    } catch (error) {
      await fs.promises.rm(target, { force: true }).catch(() => undefined);
      throw error;
    }
  }

  private request(url: string, maxBytes: number): Promise<Buffer> {
    return this.open(url).then(
      (response) =>
        new Promise<Buffer>((resolve, reject) => {
          const chunks: Buffer[] = [];
          let size = 0;
          response.on('data', (chunk: Buffer) => {
            size += chunk.length;
            if (size > maxBytes) {
              response.destroy();
              reject(new Error('The update response is larger than expected'));
              return;
            }
            chunks.push(chunk);
          });
          response.on('error', reject);
          response.on('end', () => resolve(Buffer.concat(chunks)));
        }),
    );
  }

  private open(url: string, redirects = 0): Promise<IncomingMessage> {
    if (redirects > MAX_REDIRECTS) {
      return Promise.reject(new Error('Too many update redirects'));
    }
    let parsed: URL;
    try {
      parsed = new URL(url);
    } catch {
      return Promise.reject(new Error('Invalid update URL'));
    }
    if (parsed.protocol !== 'https:') {
      return Promise.reject(new Error('Update URLs must use HTTPS'));
    }
    return new Promise<IncomingMessage>((resolve, reject) => {
      const request = https.get(
        parsed,
        {
          headers: {
            'User-Agent': this.userAgent,
            Accept: 'application/vnd.github+json, application/octet-stream',
          },
        },
        (response) => {
          const status = response.statusCode ?? 0;
          if ([301, 302, 303, 307, 308].includes(status)) {
            response.resume();
            const location = response.headers.location;
            if (!location) {
              reject(new Error('GitHub redirected without a destination'));
              return;
            }
            this.open(new URL(location, parsed).toString(), redirects + 1).then(resolve, reject);
            return;
          }
          if (status !== 200) {
            response.resume();
            reject(new Error(`GitHub returned HTTP ${status}`));
            return;
          }
          resolve(response);
        },
      );
      request.setTimeout(30_000, () => request.destroy(new Error('The update request timed out')));
      request.on('error', reject);
    });
  }
}
