import fs from 'node:fs';
import path from 'node:path';
import type { PeerSession } from '../server';
import type {
  PhotoListResultWire,
  PhotoStreamMetaWire,
  PhotoUi,
} from '../../shared/types';
import { TYPES } from '../../shared/types';
import { sanitizeFileName } from '../core/safety';
import type { Logger } from '../core/logger';
import type { StreamRegistry } from '../core/streams';

const THUMB_MAX_BYTES = 2 * 1024 * 1024;
const FULL_MAX_BYTES = 12 * 1024 * 1024;

export interface PhotoStatus {
  items: PhotoUi[];
  loading: boolean;
  error?: string;
}

export class PhotoService {
  private items: PhotoUi[] = [];
  private loading = false;
  private error: string | undefined;
  private session: PeerSession | undefined;
  private readonly thumbnails = new Map<string, string>();
  private readonly inFlight = new Map<string, Promise<string | null>>();

  constructor(
    private readonly logger: Logger,
    private readonly streams: StreamRegistry,
    private readonly picturesDir: () => string,
    private readonly copyImage: (buffer: Buffer) => void,
    private readonly onChange: () => void,
  ) {}

  onConnected(session: PeerSession): void {
    this.session = session;
    this.items = [];
    this.thumbnails.clear();
    this.inFlight.clear();
    this.error = undefined;
    this.onChange();
  }

  onDisconnected(): void {
    this.session = undefined;
    this.loading = false;
    this.inFlight.clear();
    this.onChange();
  }

  async refresh(): Promise<void> {
    const session = this.session;
    if (!session) return;
    this.loading = true;
    this.error = undefined;
    this.onChange();
    try {
      const response = await session.request<PhotoListResultWire>(
        TYPES.PHOTO_LIST,
        { limit: 200 },
        25_000,
      );
      const data = response.data;
      if (!data || !Array.isArray(data.photos)) {
        throw new Error('The phone returned an invalid photo list');
      }
      this.items = data.photos
        .filter((photo) => photo && typeof photo.id === 'string')
        .slice(0, 300);
      this.thumbnails.clear();
      this.logger.log('Photos', `Loaded ${this.items.length} recent photos`);
    } catch (error) {
      this.error = error instanceof Error ? error.message : 'Could not load photos';
      this.logger.log('Photos', `Refresh failed: ${this.error}`);
    } finally {
      this.loading = false;
      this.onChange();
    }
  }

  status(): PhotoStatus {
    return { items: this.items, loading: this.loading, error: this.error };
  }

  async thumbnail(id: string): Promise<string | null> {
    const cached = this.thumbnails.get(id);
    if (cached) return cached;
    const running = this.inFlight.get(id);
    if (running) return running;
    const promise = this.fetchThumbnail(id)
      .finally(() => this.inFlight.delete(id));
    this.inFlight.set(id, promise);
    return promise;
  }

  private async fetchThumbnail(id: string): Promise<string | null> {
    try {
      const buffer = await this.fetchImage(id, 'thumb', THUMB_MAX_BYTES);
      const url = `data:image/jpeg;base64,${buffer.toString('base64')}`;
      this.thumbnails.set(id, url);
      return url;
    } catch (error) {
      this.logger.log('Photos', `Thumbnail failed: ${error instanceof Error ? error.message : String(error)}`);
      return null;
    }
  }

  private async fetchImage(id: string, kind: 'thumb' | 'full', maxBytes: number): Promise<Buffer> {
    const session = this.session;
    if (!session) throw new Error('Connect to your phone first');
    const response = await session.request<PhotoStreamMetaWire>(
      TYPES.PHOTO_FETCH,
      { id, kind },
      25_000,
    );
    const meta = response.data;
    if (!meta || typeof meta.streamId !== 'number') {
      throw new Error('The phone did not start a photo transfer');
    }
    if (meta.size > maxBytes) throw new Error('Photo is too large to transfer');
    return this.streams.create(meta.streamId, maxBytes, 40_000, 'photo');
  }

  async save(id: string): Promise<string> {
    const item = this.items.find((photo) => photo.id === id);
    const buffer = await this.fetchImage(id, 'full', FULL_MAX_BYTES);
    const directory = path.join(this.picturesDir(), 'LinkPhone');
    fs.mkdirSync(directory, { recursive: true });
    const base = sanitizeFileName(
      (item?.name ?? 'photo').replace(/\.[^.]+$/, ''),
      'photo',
    );
    const fileName = `${base}-${Date.now()}.jpg`;
    const target = path.join(directory, fileName);
    fs.writeFileSync(target, buffer, { mode: 0o644 });
    this.logger.log('Photos', `Saved a photo to ${target}`);
    return target;
  }

  async copy(id: string): Promise<void> {
    const buffer = await this.fetchImage(id, 'full', FULL_MAX_BYTES);
    this.copyImage(buffer);
    this.logger.log('Photos', 'Copied a photo to the clipboard');
  }
}
