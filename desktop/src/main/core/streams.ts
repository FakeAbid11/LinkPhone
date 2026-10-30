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

interface OrphanStream {
  frames: BinaryFrame[];
  bytes: number;
  timer: NodeJS.Timeout;
}

/** What happened to a frame handed to {@link StreamRegistry.add}. */
export type FrameResult = 'accepted' | 'buffered' | 'dropped';

const MAX_ORPHAN_STREAMS = 8;
const MAX_ORPHAN_BYTES = 256 * 1024;
const ORPHAN_TTL_MS = 2_000;

export class StreamRegistry {
  private readonly pending = new Map<number, PendingStream>();
  /**
   * Frames that arrived before `create()` was called for their stream.
   *
   * The phone starts sending as soon as it answers `photo.fetch`, so its reply
   * and the first chunks can be dispatched in the same tick. Without this
   * buffer the first chunk was dropped and the transfer stalled until the
   * timeout, because the registry entry did not exist yet.
   */
  private readonly orphans = new Map<number, OrphanStream>();

  create(streamId: number, maxBytes: number, timeoutMs: number, description: string): Promise<Buffer> {
    if (this.pending.has(streamId)) {
      return Promise.reject(new Error('Duplicate stream id'));
    }
    const promise = new Promise<Buffer>((resolve, reject) => {
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
    // The executor above runs synchronously, so the entry exists by now.
    this.replay(streamId);
    return promise;
  }

  add(frame: BinaryFrame): FrameResult {
    const stream = this.pending.get(frame.streamId);
    if (!stream) {
      return this.hold(frame) ? 'buffered' : 'dropped';
    }
    if (frame.seq !== stream.expectedNext) {
      this.fail(frame.streamId, `Out-of-order ${stream.description}`);
      return 'dropped';
    }
    stream.expectedNext += 1;
    stream.size += frame.payload.length;
    if (stream.size > stream.maxBytes) {
      this.fail(frame.streamId, `${stream.description} is too large`);
      return 'dropped';
    }
    stream.chunks.push(Buffer.from(frame.payload));
    if (frame.last) {
      clearTimeout(stream.timer);
      this.pending.delete(frame.streamId);
      stream.resolve(Buffer.concat(stream.chunks, stream.size));
    }
    return 'accepted';
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
    for (const id of [...this.orphans.keys()]) this.dropOrphan(id);
  }

  /** Keeps the leading frames of a stream nobody has claimed yet. */
  private hold(frame: BinaryFrame): boolean {
    let orphan = this.orphans.get(frame.streamId);
    if (!orphan) {
      if (this.orphans.size >= MAX_ORPHAN_STREAMS) {
        const oldest = this.orphans.keys().next().value;
        if (oldest !== undefined) this.dropOrphan(oldest);
      }
      const timer = setTimeout(() => this.dropOrphan(frame.streamId), ORPHAN_TTL_MS);
      timer.unref?.();
      orphan = { frames: [], bytes: 0, timer };
      this.orphans.set(frame.streamId, orphan);
    }
    if (orphan.bytes + frame.payload.length > MAX_ORPHAN_BYTES) return false;
    orphan.bytes += frame.payload.length;
    orphan.frames.push(frame);
    return true;
  }

  private replay(streamId: number): void {
    const orphan = this.orphans.get(streamId);
    if (!orphan) return;
    this.dropOrphan(streamId);
    for (const frame of orphan.frames) this.add(frame);
  }

  private dropOrphan(streamId: number): void {
    const orphan = this.orphans.get(streamId);
    if (!orphan) return;
    clearTimeout(orphan.timer);
    this.orphans.delete(streamId);
  }

  private fail(streamId: number, message: string): void {
    const stream = this.pending.get(streamId);
    if (!stream) return;
    clearTimeout(stream.timer);
    this.pending.delete(streamId);
    stream.reject(new Error(message));
  }
}
