import {
  codecString,
  extractConfig,
  prependConfigIfMissing,
  readVideoPacket,
} from '../../shared/h264';
import type { MirrorFrameWire } from '../../shared/types';

const FLAG_KEY_FRAME = 2;
const FLAG_CODEC_CONFIG = 4;
const QUEUE_LIMIT = 6;
const KEYFRAME_REQUEST_MS = 500;
const ERROR_WINDOW_MS = 10_000;

export interface VideoDecoderConfigLike {
  codec: string;
  optimizeForLatency?: boolean;
  codedWidth?: number;
  codedHeight?: number;
  avc?: { format: 'annexb' | 'avc' };
}

export interface VideoFrameLike {
  readonly displayWidth: number;
  readonly displayHeight: number;
  close(): void;
}

export interface DecoderChunk {
  type: 'key' | 'delta';
  timestamp: number;
  data: Uint8Array;
}

export interface DecoderBackend {
  configure(config: VideoDecoderConfigLike): void;
  decode(chunk: DecoderChunk): void;
  reset(): void;
  close(): void;
  readonly decodeQueueSize: number;
}

export interface H264DecoderOptions {
  width: number;
  height: number;
  createBackend: (
    output: (frame: VideoFrameLike) => void,
    error: (error: Error) => void,
  ) => DecoderBackend;
  draw: (frame: VideoFrameLike) => void;
  requestKeyframe: () => void;
  onFatal: (message: string) => void;
}

/** WebCodecs H.264 player with keyframe gating and a bounded decode queue. */
export class H264StreamDecoder {
  private backend: DecoderBackend | null = null;
  private config: Uint8Array | null = null;
  private codec: string | null = null;
  private waitingForKeyframe = true;
  private lastPtsUs = -1;
  private lastKeyframeRequest = 0;
  private errorCount = 0;
  private lastErrorAt = 0;
  private closed = false;

  constructor(private readonly options: H264DecoderOptions) {}

  push(frame: MirrorFrameWire): void {
    if (this.closed) return;
    const packet = readVideoPacket(frame.data);
    if (!packet) return;

    if ((frame.flags & FLAG_CODEC_CONFIG) !== 0) {
      const config = extractConfig(packet.data);
      if (config) {
        this.config = config;
        this.codec = codecString(packet.data) ?? this.codec;
        this.configure();
      }
      return;
    }

    if (!this.config) {
      const config = extractConfig(packet.data);
      if (config) {
        this.config = config;
        this.codec = codecString(packet.data) ?? this.codec;
      }
    }
    if (!this.backend) this.configure();
    if (!this.backend) return;

    const key = (frame.flags & FLAG_KEY_FRAME) !== 0;
    if (key) {
      this.waitingForKeyframe = false;
      this.decode('key', prependConfigIfMissing(packet.data, this.config), packet.ptsUs);
      return;
    }
    if (!this.waitingForKeyframe) {
      this.decode('delta', packet.data, packet.ptsUs);
    }
  }

  close(): void {
    if (this.closed) return;
    this.closed = true;
    try {
      this.backend?.close();
    } catch {
      // The decoder may already be closed after an error.
    }
    this.backend = null;
  }

  private configure(): void {
    if (this.closed || this.backend || !this.codec) return;
    try {
      const backend = this.options.createBackend(
        (frame) => {
          if (this.closed) {
            frame.close();
            return;
          }
          try {
            this.options.draw(frame);
          } finally {
            frame.close();
          }
        },
        (error) => this.handleError(error),
      );
      backend.configure({
        codec: this.codec,
        optimizeForLatency: true,
        codedWidth: this.options.width || undefined,
        codedHeight: this.options.height || undefined,
        avc: { format: 'annexb' },
      });
      this.backend = backend;
      this.waitingForKeyframe = true;
      this.requestKeyframe();
    } catch (error) {
      this.fatal(`Could not start H.264 playback: ${message(error)}`);
    }
  }

  private decode(type: 'key' | 'delta', data: Uint8Array, ptsUs: number): void {
    const backend = this.backend;
    if (!backend) return;
    if (backend.decodeQueueSize > QUEUE_LIMIT) {
      this.recover();
      return;
    }
    const timestamp = Math.max(ptsUs, this.lastPtsUs + 1);
    this.lastPtsUs = timestamp;
    try {
      backend.decode({ type, timestamp, data });
    } catch (error) {
      this.handleError(error instanceof Error ? error : new Error(String(error)));
    }
  }

  private recover(): void {
    const backend = this.backend;
    if (!backend || !this.config || !this.codec) return;
    try {
      backend.reset();
      backend.configure({
        codec: this.codec,
        optimizeForLatency: true,
        codedWidth: this.options.width || undefined,
        codedHeight: this.options.height || undefined,
        avc: { format: 'annexb' },
      });
      this.waitingForKeyframe = true;
      this.requestKeyframe(true);
    } catch {
      this.restart();
    }
  }

  private restart(): void {
    try {
      this.backend?.close();
    } catch {
      // Ignore a decoder that is already closed.
    }
    this.backend = null;
    this.waitingForKeyframe = true;
    this.lastKeyframeRequest = 0;
    this.configure();
  }

  private handleError(error: Error): void {
    if (this.closed) return;
    const now = Date.now();
    if (now - this.lastErrorAt > ERROR_WINDOW_MS) this.errorCount = 0;
    this.lastErrorAt = now;
    this.errorCount += 1;
    if (this.errorCount <= 1) {
      this.restart();
      return;
    }
    this.fatal(`H.264 playback failed: ${error.message || 'decoder error'}`);
  }

  private requestKeyframe(force = false): void {
    const now = Date.now();
    if (!force && now - this.lastKeyframeRequest < KEYFRAME_REQUEST_MS) return;
    this.lastKeyframeRequest = now;
    this.options.requestKeyframe();
  }

  private fatal(message: string): void {
    if (this.closed) return;
    this.closed = true;
    try {
      this.backend?.close();
    } catch {
      // Ignore.
    }
    this.backend = null;
    this.options.onFatal(message);
  }
}

function message(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
