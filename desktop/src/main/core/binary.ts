export const HEADER_SIZE = 20;
export const FRAME_VERSION = 1;
export const TYPE_PHOTO = 1;
export const TYPE_MIRROR = 2;
export const TYPE_FILE = 3;
export const TYPE_VIDEO = 4;
export const CHUNK_SIZE = 64 * 1024;
export const MAX_PAYLOAD = 1_900_000;

export const FLAG_LAST = 1;
export const FLAG_KEY_FRAME = 2;
export const FLAG_CODEC_CONFIG = 4;

export interface BinaryFrame {
  type: number;
  streamId: number;
  seq: number;
  flags: number;
  last: boolean;
  keyframe: boolean;
  codecConfig: boolean;
  payload: Buffer;
}

export function encodeBinaryFrame(
  type: number,
  streamId: number,
  seq: number,
  last: boolean,
  payload: Buffer,
  flags = 0,
): Buffer {
  if (type < TYPE_PHOTO || type > TYPE_VIDEO) throw new Error('Invalid binary frame type');
  if (payload.length > MAX_PAYLOAD) throw new Error('Binary frame payload too large');
  const header = Buffer.alloc(HEADER_SIZE);
  header.writeUInt8(FRAME_VERSION, 0);
  header.writeUInt8(type, 1);
  header.writeUInt16BE(0, 2);
  header.writeBigUInt64BE(BigInt(streamId), 4);
  header.writeUInt32BE(seq >>> 0, 12);
  header.writeUInt32BE(((last ? FLAG_LAST : 0) | flags) >>> 0, 16);
  return Buffer.concat([header, payload]);
}

export function decodeBinaryFrame(buffer: Buffer): BinaryFrame | null {
  if (buffer.length < HEADER_SIZE) return null;
  if (buffer.readUInt8(0) !== FRAME_VERSION) return null;
  const type = buffer.readUInt8(1);
  if (type < TYPE_PHOTO || type > TYPE_VIDEO) return null;
  const streamId = Number(buffer.readBigUInt64BE(4));
  const seq = buffer.readUInt32BE(12);
  const flags = buffer.readUInt32BE(16);
  return {
    type,
    streamId,
    seq,
    flags,
    last: (flags & FLAG_LAST) === FLAG_LAST,
    keyframe: (flags & FLAG_KEY_FRAME) === FLAG_KEY_FRAME,
    codecConfig: (flags & FLAG_CODEC_CONFIG) === FLAG_CODEC_CONFIG,
    payload: buffer.subarray(HEADER_SIZE),
  };
}
