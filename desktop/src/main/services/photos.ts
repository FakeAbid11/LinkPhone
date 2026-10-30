import fs from 'node:fs';
import path from 'node:path';
import type { PeerSession } from '../server';
import type {
  PhotoAlbumListResultWire,
  PhotoAlbumWire,
  PhotoListResultWire,
  PhotoStreamMetaWire,
  PhotoUi,
} from '../../shared/types';
import { CAPABILITIES, ERROR_CODES, TYPES } from '../../shared/types';
import { sanitizeFileName } from '../core/safety';
import type { Logger } from '../core/logger';
import type { StreamRegistry } from '../core/streams';

const THUMB_MAX_BYTES = 2 * 1024 * 1024;
const FULL_MAX_BYTES = 12 * 1024 * 1024;
const PAGE_SIZE = 200;
const MAX_CACHED_THUMBS = 600;

export interface PhotoStatus {
  items: PhotoUi[];
  albums: PhotoAlbumWire[];
  activeAlbum: string | null;
  hasMore: boolean;
  loading: boolean;
  loadingMore: boolean;
  albumsLoading: boolean;
  albumsSupported: boolean;
  error?: string;
  albumsError?: string;
}

interface PhotoPage {
  photos: PhotoUi[];
  hasMore: boolean;
}

export class PhotoService {
  private items: PhotoUi[] = [];
  private albums: PhotoAlbumWire[] = [];
  private activeAlbum: string | null = null;
  private hasMore = false;
  private loading = false;
  private loadingMore = false;
  private albumsLoading = false;
  private albumsSupported = false;
  private error: string | undefined;
  private albumsError: string | undefined;
  private epoch = 0;
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
    this.albums = [];
    this.activeAlbum = null;
    this.hasMore = false;
    this.loading = false;
    this.loadingMore = false;
    this.albumsLoading = false;
    this.albumsSupported = false;
    this.error = undefined;
    this.albumsError = undefined;
    this.epoch += 1;
    this.thumbnails.clear();
    this.inFlight.clear();
    this.onChange();
  }

  onDisconnected(): void {
    this.session = undefined;
    this.loading = false;
    this.loadingMore = false;
    this.albumsLoading = false;
    this.items = [];
    this.albums = [];
    this.activeAlbum = null;
    this.hasMore = false;
    this.error = undefined;
    this.albumsError = undefined;
    this.epoch += 1;
    this.inFlight.clear();
    this.thumbnails.clear();
    this.onChange();
  }

  setCapabilities(capabilities: string[]): void {
    const supported = capabilities.includes(CAPABILITIES.PHOTOS_ALBUMS);
    if (supported === this.albumsSupported) return;
    this.albumsSupported = supported;
    if (!supported) {
      this.albums = [];
      this.activeAlbum = null;
      this.albumsError = undefined;
    }
    this.onChange();
    if (supported) void this.refreshAlbums();
  }

  async refresh(): Promise<void> {
    const session = this.session;
    if (!session) return;
    const epoch = ++this.epoch;
    this.loading = true;
    this.error = undefined;
    this.activeAlbum = null;
    this.items = [];
    this.hasMore = false;
    this.thumbnails.clear();
    this.onChange();
    try {
      const page = await this.fetchPage(session, null, null, null);
      if (epoch !== this.epoch) return;
      this.items = page.photos;
      this.hasMore = page.hasMore;
      this.logger.log('Photos', `Loaded ${this.items.length} recent photos`);
      if (this.albumsSupported) void this.refreshAlbums();
    } catch (error) {
      if (epoch !== this.epoch) return;
      this.error = error instanceof Error ? error.message : 'Could not load photos';
      this.logger.log('Photos', `Refresh failed: ${this.error}`);
    } finally {
      if (epoch === this.epoch) {
        this.loading = false;
        this.onChange();
      }
    }
  }

  async refreshAlbums(): Promise<void> {
    const session = this.session;
    if (!session || !this.albumsSupported) return;
    const epoch = this.epoch;
    this.albumsLoading = true;
    this.albumsError = undefined;
    this.onChange();
    try {
      const response = await session.request<PhotoAlbumListResultWire>(
        TYPES.PHOTO_ALBUMS,
        undefined,
        20_000,
      );
      const albums = response.data?.albums;
      if (!Array.isArray(albums)) throw new Error('The phone returned an invalid album list');
      if (epoch !== this.epoch) return;
      this.albums = albums
        .filter(
          (album): album is PhotoAlbumWire =>
            Boolean(album) &&
            typeof album.id === 'string' &&
            typeof album.name === 'string' &&
            typeof album.count === 'number',
        )
        .map((album) => ({
          ...album,
          coverId: typeof album.coverId === 'string' ? album.coverId : '',
          latest: typeof album.latest === 'number' ? album.latest : 0,
        }))
        .sort((a, b) => b.latest - a.latest);
      this.logger.log('Photos', `Loaded ${this.albums.length} albums`);
    } catch (error) {
      if (this.isUnsupported(error)) {
        this.albumsSupported = false;
        this.albums = [];
        this.activeAlbum = null;
      } else {
        this.albumsError =
          error instanceof Error ? error.message : 'Could not load photo albums';
        this.logger.log('Photos', `Album refresh failed: ${this.albumsError}`);
      }
    } finally {
      this.albumsLoading = false;
      this.onChange();
    }
  }

  async selectAlbum(bucket: string | null): Promise<void> {
    const session = this.session;
    if (!session) return;
    if (bucket !== null && !this.albumsSupported) {
      throw new Error('Album browsing needs the latest LinkPhone phone app');
    }
    const epoch = ++this.epoch;
    this.activeAlbum = bucket;
    this.loading = true;
    this.loadingMore = false;
    this.error = undefined;
    this.items = [];
    this.hasMore = false;
    this.thumbnails.clear();
    this.onChange();
    try {
      const page = await this.fetchPage(session, bucket, null, null);
      if (epoch !== this.epoch) return;
      this.items = page.photos;
      this.hasMore = page.hasMore;
    } catch (error) {
      if (epoch !== this.epoch) return;
      this.error = error instanceof Error ? error.message : 'Could not load this album';
      this.logger.log('Photos', `Album load failed: ${this.error}`);
    } finally {
      if (epoch === this.epoch) {
        this.loading = false;
        this.onChange();
      }
    }
  }

  async loadMore(): Promise<void> {
    const session = this.session;
    if (!session || !this.hasMore || this.loading || this.loadingMore) return;
    const last = this.items[this.items.length - 1];
    if (!last) return;
    const epoch = this.epoch;
    this.loadingMore = true;
    this.onChange();
    try {
      const page = await this.fetchPage(session, this.activeAlbum, last.date, last.id);
      if (epoch !== this.epoch) return;
      const seen = new Set(this.items.map((photo) => photo.id));
      this.items = [...this.items, ...page.photos.filter((photo) => !seen.has(photo.id))];
      this.hasMore = page.hasMore;
    } catch (error) {
      if (epoch !== this.epoch) return;
      this.error = error instanceof Error ? error.message : 'Could not load more photos';
      this.logger.log('Photos', `Load more failed: ${this.error}`);
    } finally {
      if (epoch === this.epoch) {
        this.loadingMore = false;
        this.onChange();
      }
    }
  }

  status(): PhotoStatus {
    return {
      items: this.items,
      albums: this.albums,
      activeAlbum: this.activeAlbum,
      hasMore: this.hasMore,
      loading: this.loading,
      loadingMore: this.loadingMore,
      albumsLoading: this.albumsLoading,
      albumsSupported: this.albumsSupported,
      error: this.error,
      albumsError: this.albumsError,
    };
  }

  async thumbnail(id: string): Promise<string | null> {
    const volume = this.items.find((photo) => photo.id === id)?.volume ?? null;
    const cacheKey = volume ? `${volume}:${id}` : id;
    const cached = this.thumbnails.get(cacheKey);
    if (cached) return cached;
    const running = this.inFlight.get(cacheKey);
    if (running) return running;
    const promise = this.fetchThumbnail(cacheKey, id, volume).finally(() =>
      this.inFlight.delete(cacheKey),
    );
    this.inFlight.set(cacheKey, promise);
    return promise;
  }

  private async fetchThumbnail(
    cacheKey: string,
    id: string,
    volume: string | null,
  ): Promise<string | null> {
    try {
      const buffer = await this.fetchImage(id, 'thumb', THUMB_MAX_BYTES, volume);
      const url = `data:image/jpeg;base64,${buffer.toString('base64')}`;
      this.thumbnails.set(cacheKey, url);
      while (this.thumbnails.size > MAX_CACHED_THUMBS) {
        const oldest = this.thumbnails.keys().next().value;
        if (oldest === undefined) break;
        this.thumbnails.delete(oldest);
      }
      return url;
    } catch (error) {
      this.logger.log(
        'Photos',
        `Thumbnail failed: ${error instanceof Error ? error.message : String(error)}`,
      );
      return null;
    }
  }

  private async fetchPage(
    session: PeerSession,
    bucket: string | null,
    beforeDate: number | null,
    beforeId: string | null,
  ): Promise<PhotoPage> {
    const data: Record<string, unknown> = { limit: PAGE_SIZE };
    if (bucket !== null) data.bucket = bucket;
    if (beforeDate !== null && beforeId !== null) {
      data.beforeDate = beforeDate;
      data.beforeId = beforeId;
    }
    const response = await session.request<PhotoListResultWire>(
      TYPES.PHOTO_LIST,
      data,
      25_000,
    );
    const result = response.data;
    if (!result || !Array.isArray(result.photos)) {
      throw new Error('The phone returned an invalid photo list');
    }
    return {
      photos: result.photos.filter(
        (photo): photo is PhotoUi => Boolean(photo) && typeof photo.id === 'string',
      ),
      hasMore: result.hasMore === true,
    };
  }

  private async fetchImage(
    id: string,
    kind: 'thumb' | 'full',
    maxBytes: number,
    volume: string | null,
  ): Promise<Buffer> {
    const session = this.session;
    if (!session) throw new Error('Connect to your phone first');
    const response = await session.request<PhotoStreamMetaWire>(
      TYPES.PHOTO_FETCH,
      volume ? { id, kind, volume } : { id, kind },
      25_000,
    );
    const meta = response.data;
    if (!meta || typeof meta.streamId !== 'number') {
      throw new Error('The phone did not start a photo transfer');
    }
    if (meta.size > maxBytes) throw new Error('Photo is too large to transfer');
    return this.streams.create(meta.streamId, maxBytes, 40_000, 'photo');
  }

  private isUnsupported(error: unknown): boolean {
    return (
      typeof error === 'object' &&
      error !== null &&
      'code' in error &&
      (error as { code?: string }).code === ERROR_CODES.UNSUPPORTED
    );
  }

  async save(id: string): Promise<string> {
    const item = this.items.find((photo) => photo.id === id);
    const buffer = await this.fetchImage(id, 'full', FULL_MAX_BYTES, item?.volume ?? null);
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
    const item = this.items.find((photo) => photo.id === id);
    const buffer = await this.fetchImage(id, 'full', FULL_MAX_BYTES, item?.volume ?? null);
    this.copyImage(buffer);
    this.logger.log('Photos', 'Copied a photo to the clipboard');
  }

  /** The full-size image for the preview modal, as a data URL. */
  async preview(id: string): Promise<string> {
    const item = this.items.find((photo) => photo.id === id);
    const buffer = await this.fetchImage(id, 'full', FULL_MAX_BYTES, item?.volume ?? null);
    return `data:image/jpeg;base64,${buffer.toString('base64')}`;
  }
}
