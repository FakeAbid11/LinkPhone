import { describe, expect, it } from 'vitest';
import {
  codecString,
  concatBytes,
  extractConfig,
  prependConfigIfMissing,
  readVideoPacket,
  splitAnnexB,
  VIDEO_TIMESTAMP_BYTES,
} from '../src/shared/h264';

const SPS = new Uint8Array([0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1f, 0x10]);
const PPS = new Uint8Array([0, 0, 0, 1, 0x68, 0xce, 0x3c, 0x80]);
const IDR = new Uint8Array([0, 0, 0, 1, 0x65, 0x88, 0x84, 0x00]);

function packet(ptsUs: number, data: Uint8Array): Uint8Array {
  const header = new Uint8Array(VIDEO_TIMESTAMP_BYTES);
  new DataView(header.buffer).setUint32(4, ptsUs);
  return concatBytes([header, data]);
}

describe('H.264 helpers', () => {
  it('splits three- and four-byte Annex B start codes', () => {
    const data = concatBytes([
      new Uint8Array([0, 0, 0, 1, 0x67, 0x42]),
      new Uint8Array([0, 0, 1, 0x68, 0xce]),
      new Uint8Array([0, 0, 0, 1, 0x65, 0x88]),
    ]);
    const units = splitAnnexB(data);
    expect(units).toHaveLength(3);
    expect(units[0][0]).toBe(0x67);
    expect(units[1][0]).toBe(0x68);
    expect(units[2][0]).toBe(0x65);
  });

  it('derives the WebCodecs codec string from the SPS', () => {
    expect(codecString(concatBytes([SPS, PPS]))).toBe('avc1.42001F');
    expect(codecString(new Uint8Array([0x67, 0x64, 0x00, 0x28]))).toBe('avc1.640028');
    expect(codecString(PPS)).toBeNull();
  });

  it('extracts SPS/PPS and keeps Annex B framing', () => {
    const config = extractConfig(concatBytes([SPS, IDR, PPS]));
    expect(config).not.toBeNull();
    const units = splitAnnexB(config as Uint8Array);
    expect(units.map((unit) => unit[0])).toEqual([0x67, 0x68]);
  });

  it('prepends SPS/PPS to a key frame only when missing', () => {
    const config = extractConfig(concatBytes([SPS, PPS]));
    const withConfig = prependConfigIfMissing(IDR, config);
    expect(splitAnnexB(withConfig).map((unit) => unit[0])).toEqual([0x67, 0x68, 0x65]);

    const alreadyConfigured = concatBytes([SPS, IDR]);
    expect(prependConfigIfMissing(alreadyConfigured, config)).toBe(alreadyConfigured);
  });

  it('reads the 8-byte presentation timestamp', () => {
    const parsed = readVideoPacket(packet(123_456, IDR));
    expect(parsed?.ptsUs).toBe(123_456);
    expect(Array.from(parsed?.data ?? [])).toEqual(Array.from(IDR));
    expect(readVideoPacket(new Uint8Array(8))).toBeNull();
  });
});
