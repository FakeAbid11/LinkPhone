import { describe, expect, it } from 'vitest';
import type { MirrorFrameWire } from '../src/shared/types';
import {
  H264StreamDecoder,
  type DecoderBackend,
  type DecoderChunk,
  type VideoDecoderConfigLike,
} from '../src/renderer/mirror/decoder';

const SPS = new Uint8Array([0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1f, 0x10]);
const PPS = new Uint8Array([0, 0, 0, 1, 0x68, 0xce, 0x3c, 0x80]);
const IDR = new Uint8Array([0, 0, 0, 1, 0x65, 0x88, 0x84, 0x00]);
const DELTA = new Uint8Array([0, 0, 0, 1, 0x41, 0x9a, 0x00]);
const FLAG_KEY = 2;
const FLAG_CONFIG = 4;

class FakeBackend implements DecoderBackend {
  configs: VideoDecoderConfigLike[] = [];
  chunks: DecoderChunk[] = [];
  queue = 0;
  resets = 0;
  closed = false;
  error: (error: Error) => void = () => undefined;

  configure(config: VideoDecoderConfigLike): void {
    this.configs.push(config);
  }

  decode(chunk: DecoderChunk): void {
    this.chunks.push(chunk);
    this.queue += 1;
  }

  reset(): void {
    this.resets += 1;
    this.queue = 0;
  }

  close(): void {
    this.closed = true;
  }

  get decodeQueueSize(): number {
    return this.queue;
  }
}

function payload(ptsUs: number, ...parts: Uint8Array[]): Uint8Array {
  const header = new Uint8Array(8);
  new DataView(header.buffer).setUint32(4, ptsUs);
  const size = parts.reduce((sum, part) => sum + part.length, 0);
  const output = new Uint8Array(8 + size);
  output.set(header, 0);
  let offset = 8;
  for (const part of parts) {
    output.set(part, offset);
    offset += part.length;
  }
  return output;
}

function frame(flags: number, data: Uint8Array, seq = 0): MirrorFrameWire {
  return { type: 4, seq, flags, data };
}

function harness() {
  const backends: FakeBackend[] = [];
  let keyframeRequests = 0;
  let fatal: string | null = null;
  const decoder = new H264StreamDecoder({
    width: 720,
    height: 1600,
    createBackend: (_output, error) => {
      const backend = new FakeBackend();
      backend.error = error;
      backends.push(backend);
      return backend;
    },
    draw: () => undefined,
    requestKeyframe: () => {
      keyframeRequests += 1;
    },
    onFatal: (message) => {
      fatal = message;
    },
  });
  return {
    decoder,
    backends,
    get keyframeRequests() {
      return keyframeRequests;
    },
    get fatal() {
      return fatal;
    },
  };
}

describe('H.264 stream decoder', () => {
  it('configures from SPS/PPS, waits for a key frame, then decodes deltas', () => {
    const { decoder, backends, keyframeRequests } = harness();

    decoder.push(frame(0, payload(1, DELTA)));
    expect(backends).toHaveLength(0);

    decoder.push(frame(FLAG_CONFIG, payload(0, SPS, PPS)));
    expect(backends).toHaveLength(1);
    expect(backends[0].configs[0]).toMatchObject({
      codec: 'avc1.42001F',
      optimizeForLatency: true,
      avc: { format: 'annexb' },
    });
    expect(backends[0].chunks).toHaveLength(0);
    expect(keyframeRequests).toBeGreaterThan(0);

    decoder.push(frame(0, payload(2, DELTA)));
    expect(backends[0].chunks).toHaveLength(0);

    decoder.push(frame(FLAG_KEY, payload(3, IDR)));
    expect(backends[0].chunks).toHaveLength(1);
    expect(backends[0].chunks[0].type).toBe('key');
    expect(Array.from(backends[0].chunks[0].data.slice(0, 3))).toEqual([0, 0, 0]);
    expect(backends[0].chunks[0].data[4]).toBe(0x67);

    decoder.push(frame(0, payload(4, DELTA)));
    expect(backends[0].chunks[1].type).toBe('delta');
  });

  it('recovers from a decode backlog by resetting and asking for a key frame', () => {
    const { decoder, backends, keyframeRequests } = harness();
    decoder.push(frame(FLAG_CONFIG, payload(0, SPS, PPS)));
    decoder.push(frame(FLAG_KEY, payload(1, IDR)));
    const backend = backends[0];
    backend.queue = 7;

    decoder.push(frame(0, payload(2, DELTA)));
    expect(backend.resets).toBe(1);
    expect(backend.chunks).toHaveLength(1);
    expect(keyframeRequests).toBeGreaterThanOrEqual(2);

    decoder.push(frame(FLAG_KEY, payload(3, IDR)));
    expect(backend.chunks).toHaveLength(2);
  });

  it('keeps presentation timestamps strictly increasing', () => {
    const { decoder, backends } = harness();
    decoder.push(frame(FLAG_CONFIG, payload(0, SPS, PPS)));
    decoder.push(frame(FLAG_KEY, payload(100, IDR)));
    decoder.push(frame(0, payload(50, DELTA)));
    expect(backends[0].chunks.map((chunk) => chunk.timestamp)).toEqual([100, 101]);
  });

  it('restarts once on a decoder error, then reports a fatal failure', () => {
    const { decoder, backends, fatal } = harness();
    decoder.push(frame(FLAG_CONFIG, payload(0, SPS, PPS)));
    decoder.push(frame(FLAG_KEY, payload(1, IDR)));

    backends[0].error(new Error('first failure'));
    expect(backends).toHaveLength(2);
    expect(fatal).toBeNull();

    backends[1].error(new Error('second failure'));
    expect(fatal).toContain('H.264 playback failed');
  });

  it('does not feed a codec config frame to the decoder', () => {
    const { decoder, backends } = harness();
    decoder.push(frame(FLAG_CONFIG, payload(0, SPS, PPS)));
    expect(backends[0].chunks).toHaveLength(0);
  });
});
