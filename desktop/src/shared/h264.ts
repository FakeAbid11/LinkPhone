export const VIDEO_TIMESTAMP_BYTES = 8;

export interface VideoPacket {
  ptsUs: number;
  data: Uint8Array;
}

export function readVideoPacket(payload: Uint8Array): VideoPacket | null {
  if (payload.length <= VIDEO_TIMESTAMP_BYTES) return null;
  const view = new DataView(payload.buffer, payload.byteOffset, payload.byteLength);
  const high = view.getUint32(0);
  const low = view.getUint32(4);
  const ptsUs = high * 4_294_967_296 + low;
  if (!Number.isFinite(ptsUs) || ptsUs < 0) return null;
  return { ptsUs, data: payload.subarray(VIDEO_TIMESTAMP_BYTES) };
}

export function splitAnnexB(data: Uint8Array): Uint8Array[] {
  const starts = startCodes(data);
  if (starts.length === 0) return [];
  return starts.map((start, index) => {
    const begin = start.offset + start.length;
    const end = index + 1 < starts.length ? starts[index + 1].offset : data.length;
    return data.subarray(begin, end);
  });
}

export function nalType(nal: Uint8Array): number {
  return nal.length === 0 ? -1 : nal[0] & 0x1f;
}

/** SPS + PPS in Annex B form, or null when the buffer has neither. */
export function extractConfig(data: Uint8Array): Uint8Array | null {
  const units = splitAnnexB(data).filter((nal) => {
    const type = nalType(nal);
    return type === 7 || type === 8;
  });
  if (units.length === 0) return null;
  return annexB(units);
}

/** `avc1.PPCCLL` from an SPS NAL (with or without a start code). */
export function codecString(data: Uint8Array): string | null {
  const sps =
    splitAnnexB(data).find((nal) => nalType(nal) === 7) ??
    (nalType(data) === 7 ? data : null);
  if (!sps || sps.length < 4) return null;
  return `avc1.${hex(sps[1])}${hex(sps[2])}${hex(sps[3])}`;
}

/** Ensures SPS/PPS precede a key frame without duplicating them. */
export function prependConfigIfMissing(accessUnit: Uint8Array, config: Uint8Array | null): Uint8Array {
  if (!config || config.length === 0) return accessUnit;
  if (splitAnnexB(accessUnit).some((nal) => nalType(nal) === 7)) return accessUnit;
  return concatBytes([config, accessUnit]);
}

function annexB(units: Uint8Array[]): Uint8Array {
  const parts: Uint8Array[] = [];
  for (const unit of units) {
    parts.push(new Uint8Array([0, 0, 0, 1]), unit);
  }
  return concatBytes(parts);
}

export function concatBytes(parts: Uint8Array[]): Uint8Array {
  const total = parts.reduce((sum, part) => sum + part.length, 0);
  const output = new Uint8Array(total);
  let offset = 0;
  for (const part of parts) {
    output.set(part, offset);
    offset += part.length;
  }
  return output;
}

function hex(value: number): string {
  return value.toString(16).padStart(2, '0').toUpperCase();
}

function startCodes(data: Uint8Array): Array<{ offset: number; length: number }> {
  const starts: Array<{ offset: number; length: number }> = [];
  let index = 0;
  while (index + 2 < data.length) {
    if (data[index] === 0 && data[index + 1] === 0 && data[index + 2] === 1) {
      const offset = index > 0 && data[index - 1] === 0 ? index - 1 : index;
      starts.push({ offset, length: index - offset + 3 });
      index += 3;
    } else {
      index += 1;
    }
  }
  return starts;
}
