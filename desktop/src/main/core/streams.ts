import type { BinaryFrame } from './binary';

interface PendingStream {
  chunks: Buffer[];
  size: number;
  expectedNext: number;
  maxBytes: number;
  resolve: (buffer: Buffer) => void;
  reject: (error: Error) => void;
  timer: NodeJS.Timeout;
  description: string;
}

export class StreamRegistry {
  private readonly pending = new Map<number, PendingStream>();

  create(streamId: number, maxBytes: number, timeoutMs: number, description: string): Promise<Buffer> {
    if (this.pending.has(streamId)) {
      return Promise.reject(new Error('Duplicate stream id'));
    }
    return new Promise<Buffer>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(streamId);
        reject(new Error(`Timed out receiving ${description}`));
      }, timeoutMs);
      timer.unref?.();
      this.pending.set(streamId, {
        chunks: [],
        size: 0,
        expectedNext: 0,
        maxBytes,
        resolve,
        reject,
        timer,
        description,
      });
    });
  }

  add(frame: BinaryFrame): void {
    const stream = this.pending.get(frame.streamId);
    if (!stream) return;
    if (frame.seq !== stream.expectedNext) {
      this.fail(frame.streamId, `Out-of-order ${stream.description}`);
      return;
    }
    stream.expectedNext += 1;
    stream.size += frame.payload.length;
    if (stream.size > stream.maxBytes) {
      this.fail(frame.streamId, `${stream.description} is too large`);
      return;
    }
    stream.chunks.push(Buffer.from(frame.payload));
    if (frame.last) {
      clearTimeout(stream.timer);
      this.pending.delete(frame.streamId);
      stream.resolve(Buffer.concat(stream.chunks, stream.size));
    }
  }

  has(streamId: number): boolean {
    return this.pending.has(streamId);
  }

  rejectAll(reason: string): void {
    for (const [id, stream] of this.pending) {
      clearTimeout(stream.timer);
      stream.reject(new Error(reason));
      this.pending.delete(id);
    }
  }

  private fail(streamId: number, message: string): void {
    const stream = this.pending.get(streamId);
    if (!stream) return;
    clearTimeout(stream.timer);
    this.pending.delete(streamId);
    stream.reject(new Error(message));
  }
}
