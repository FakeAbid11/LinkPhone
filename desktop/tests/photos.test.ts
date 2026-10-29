import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { Logger } from '../src/main/core/logger';
import { PhotoService } from '../src/main/services/photos';
import type { PeerSession } from '../src/main/server';
import type { StreamRegistry } from '../src/main/core/streams';
import { CAPABILITIES, TYPES } from '../src/shared/types';

const dirs: string[] = [];

function photo(id: string, bucket = 'Camera', date = 1_700_000_000_000) {
  return {
    id,
    name: `${id}.jpg`,
    mime: 'image/jpeg',
    size: 3,
    date,
    width: 10,
    height: 10,
    bucket,
  };
}

function reply(data: unknown) {
  return { v: 1, id: 'reply', type: 'response', ok: true, data, ts: Date.now() };
}

function harness(list?: (data: Record<string, unknown>) => unknown) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-photos-'));
  dirs.push(dir);
  const requests: Array<{ type: string; data: Record<string, unknown> }> = [];
  const session = {
    request: async (type: string, data: Record<string, unknown>) => {
      requests.push({ type, data: data ?? {} });
      if (type === TYPES.PHOTO_LIST) {
        if (list) return reply(list(data ?? {}));
        const bucket = typeof data?.bucket === 'string' ? data.bucket : null;
        if (bucket) return reply({ photos: [photo('c', 'Camera')], hasMore: false });
        if (data?.beforeDate) {
          return reply({ photos: [photo('b', 'Camera'), photo('d', 'Camera')], hasMore: false });
        }
        return reply({ photos: [photo('a'), photo('b')], hasMore: true });
      }
      if (type === TYPES.PHOTO_ALBUMS) {
        return reply({
          albums: [
            { id: 'v:external_primary:42', name: 'Camera', count: 2, coverId: 'b', latest: 1 },
          ],
        });
      }
      if (type === TYPES.PHOTO_FETCH) {
        return reply({
          streamId: 1,
          photoId: data?.id ?? 'a',
          kind: 'thumb',
          mime: 'image/jpeg',
          size: 3,
        });
      }
      return reply({ ok: true });
    },
  } as unknown as PeerSession;
  const streams = {
    create: async () => Buffer.from('abc'),
  } as unknown as StreamRegistry;
  const service = new PhotoService(
    new Logger(path.join(dir, 'log.txt')),
    streams,
    () => dir,
    () => undefined,
    () => undefined,
  );
  service.onConnected(session);
  return { service, requests, dir };
}

afterEach(() => {
  for (const dir of dirs.splice(0)) fs.rmSync(dir, { recursive: true, force: true });
});

describe('photo albums', () => {
  it('loads the first page and album list', async () => {
    const { service, requests } = harness();
    service.setCapabilities([CAPABILITIES.PHOTOS_ALBUMS]);
    await service.refresh();
    await service.refreshAlbums();

    const page = requests.find((item) => item.type === TYPES.PHOTO_LIST);
    expect(page?.data).toMatchObject({ limit: 200 });
    expect(page?.data.bucket).toBeUndefined();

    const status = service.status();
    expect(status.items.map((item) => item.id)).toEqual(['a', 'b']);
    expect(status.hasMore).toBe(true);
    expect(status.albums).toHaveLength(1);
    expect(status.albums[0].name).toBe('Camera');
    expect(status.albumsSupported).toBe(true);
  });

  it('filters by album and appends older pages without duplicates', async () => {
    const { service, requests } = harness();
    service.setCapabilities([CAPABILITIES.PHOTOS_ALBUMS]);
    await service.refresh();
    await service.loadMore();

    const more = requests.find((item) => item.type === TYPES.PHOTO_LIST && item.data.beforeDate);
    expect(more?.data).toMatchObject({ beforeDate: 1_700_000_000_000, beforeId: 'b' });
    expect(service.status().items.map((item) => item.id)).toEqual(['a', 'b', 'd']);

    await service.selectAlbum('v:external_primary:42');
    const selected = requests.filter((item) => item.type === TYPES.PHOTO_LIST).at(-1);
    expect(selected?.data.bucket).toBe('v:external_primary:42');
    expect(service.status().activeAlbum).toBe('v:external_primary:42');
    expect(service.status().items.map((item) => item.id)).toEqual(['c']);
    expect(service.status().hasMore).toBe(false);
  });

  it('loads album covers through the normal thumbnail cache', async () => {
    const { service, requests } = harness();
    const [first, second] = await Promise.all([
      service.thumbnail('a'),
      service.thumbnail('a'),
    ]);
    expect(first).toBe(second);
    expect(first).toContain('data:image/jpeg;base64,');
    expect(requests.filter((item) => item.type === TYPES.PHOTO_FETCH)).toHaveLength(1);
  });

  it('rejects album selection on an older phone', async () => {
    const { service } = harness();
    service.setCapabilities([]);
    await expect(service.selectAlbum('v:external_primary:42')).rejects.toThrow(
      /latest LinkPhone/i,
    );
  });

  it('surfaces an invalid photo list', async () => {
    const { service } = harness(() => ({ photos: 'not-an-array' }));
    await service.refresh();
    expect(service.status().error).toMatch(/invalid photo list/i);
    expect(service.status().items).toEqual([]);
  });
});
