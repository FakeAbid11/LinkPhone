import type {
  DecoderBackend,
  DecoderChunk,
  VideoDecoderConfigLike,
  VideoFrameLike,
} from './decoder';

interface WebCodecsDecoder {
  configure(config: unknown): void;
  decode(chunk: unknown): void;
  reset(): void;
  close(): void;
  readonly decodeQueueSize: number;
}

interface WebCodecsChunkInit {
  type: 'key' | 'delta';
  timestamp: number;
  data: Uint8Array;
}

type DecoderConstructor = new (init: {
  output: (frame: VideoFrameLike) => void;
  error: (error: unknown) => void;
}) => WebCodecsDecoder;

type ChunkConstructor = new (init: WebCodecsChunkInit) => unknown;

const globals = globalThis as unknown as {
  VideoDecoder?: DecoderConstructor;
  EncodedVideoChunk?: ChunkConstructor;
};

export function h264Supported(): boolean {
  return typeof globals.VideoDecoder === 'function';
}

export function createWebCodecsBackend(
  output: (frame: VideoFrameLike) => void,
  error: (error: Error) => void,
): DecoderBackend {
  const Decoder = globals.VideoDecoder;
  const Chunk = globals.EncodedVideoChunk;
  if (!Decoder || !Chunk) {
    throw new Error('WebCodecs H.264 decoding is not available on this PC');
  }
  const decoder = new Decoder({
    output,
    error: (cause) => error(cause instanceof Error ? cause : new Error(String(cause))),
  });
  return {
    configure: (config: VideoDecoderConfigLike) => decoder.configure(config),
    decode: (chunk: DecoderChunk) =>
      decoder.decode(
        new Chunk({
          type: chunk.type,
          timestamp: chunk.timestamp,
          data: chunk.data,
        }),
      ),
    reset: () => decoder.reset(),
    close: () => decoder.close(),
    get decodeQueueSize() {
      return decoder.decodeQueueSize;
    },
  };
}
