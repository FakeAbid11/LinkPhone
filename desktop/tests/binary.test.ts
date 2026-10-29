import { describe, expect, it } from 'vitest';
import {
  FLAG_CODEC_CONFIG,
  FLAG_KEY_FRAME,
  HEADER_SIZE,
  TYPE_FILE,
  TYPE_MIRROR,
  TYPE_PHOTO,
  TYPE_VIDEO,
  decodeBinaryFrame,
  encodeBinaryFrame,
} from '../src/main/core/binary';

describe('binary frames', () => {
  it('round-trips a photo chunk', () => {
    const payload = Buffer.from('jpeg-bytes');
    const encoded = encodeBinaryFrame(TYPE_PHOTO, 42, 7, true, payload);
    expect(encoded.length).toBe(HEADER_SIZE + payload.length);
    const decoded = decodeBinaryFrame(encoded);
    expect(decoded).not.toBeNull();
    expect(decoded?.type).toBe(TYPE_PHOTO);
    expect(decoded?.streamId).toBe(42);
    expect(decoded?.seq).toBe(7);
    expect(decoded?.last).toBe(true);
    expect(decoded?.payload.toString()).toBe('jpeg-bytes');
  });

  it('supports mirror and file frames', () => {
    const mirror = decodeBinaryFrame(
      encodeBinaryFrame(TYPE_MIRROR, 0, 0, false, Buffer.from([1, 2, 3])),
    );
    expect(mirror?.type).toBe(TYPE_MIRROR);
    const file = decodeBinaryFrame(
      encodeBinaryFrame(TYPE_FILE, 99, 1, false, Buffer.from([4, 5])),
    );
    expect(file?.type).toBe(TYPE_FILE);
  });

  it('carries H.264 keyframe and codec-config flags', () => {
    const key = decodeBinaryFrame(
      encodeBinaryFrame(
        TYPE_VIDEO,
        0,
        5,
        false,
        Buffer.from('access-unit'),
        FLAG_KEY_FRAME,
      ),
    );
    expect(key?.type).toBe(TYPE_VIDEO);
    expect(key?.keyframe).toBe(true);
    expect(key?.codecConfig).toBe(false);
    expect(key?.last).toBe(false);

    const config = decodeBinaryFrame(
      encodeBinaryFrame(
        TYPE_VIDEO,
        0,
        4,
        false,
        Buffer.from('sps-pps'),
        FLAG_CODEC_CONFIG,
      ),
    );
    expect(config?.codecConfig).toBe(true);
    expect(config?.keyframe).toBe(false);
  });

  it('rejects invalid headers and types', () => {
    expect(decodeBinaryFrame(Buffer.alloc(3))).toBeNull();
    const encoded = encodeBinaryFrame(TYPE_PHOTO, 1, 0, true, Buffer.from('x'));
    encoded.writeUInt8(9, 1);
    expect(decodeBinaryFrame(encoded)).toBeNull();
  });

  it('rejects an oversized payload', () => {
    expect(() =>
      encodeBinaryFrame(TYPE_PHOTO, 1, 0, true, Buffer.alloc(2_000_000)),
    ).toThrow(/too large/i);
  });
});
