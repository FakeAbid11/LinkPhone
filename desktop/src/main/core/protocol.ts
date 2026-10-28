import { randomUUID } from 'node:crypto';
import type { Envelope, WireError } from '../../shared/types';
import { PROTOCOL_VERSION } from '../../shared/types';

export const MAX_JSON_BYTES = 1_048_576;

export function encodeEnvelope(envelope: Envelope): string {
  return JSON.stringify(envelope);
}

export function decodeEnvelope(text: string): Envelope {
  if (Buffer.byteLength(text, 'utf8') > MAX_JSON_BYTES) {
    throw new Error('Message exceeds the JSON size limit');
  }
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch {
    throw new Error('Malformed JSON message');
  }
  if (!value || typeof value !== 'object') {
    throw new Error('Malformed message envelope');
  }
  const envelope = value as Partial<Envelope>;
  if (envelope.v !== PROTOCOL_VERSION) {
    throw new Error(`Unsupported protocol version ${String(envelope.v)}`);
  }
  if (typeof envelope.id !== 'string' || !envelope.id || envelope.id.length > 128) {
    throw new Error('Invalid message id');
  }
  if (typeof envelope.type !== 'string' || !envelope.type || envelope.type.length > 64) {
    throw new Error('Invalid message type');
  }
  return envelope as Envelope;
}

export function eventEnvelope(type: string, data?: unknown): Envelope {
  return {
    v: PROTOCOL_VERSION,
    id: randomUUID(),
    type,
    data,
    ts: Date.now(),
  };
}

export function responseEnvelope(request: Envelope, data?: unknown): Envelope {
  return {
    v: PROTOCOL_VERSION,
    id: randomUUID(),
    type: 'response',
    replyTo: request.id,
    ok: true,
    data,
    ts: Date.now(),
  };
}

export function failureEnvelope(
  request: Envelope,
  code: string,
  message: string,
): Envelope {
  return {
    v: PROTOCOL_VERSION,
    id: randomUUID(),
    type: 'response',
    replyTo: request.id,
    ok: false,
    error: { code, message } satisfies WireError,
    ts: Date.now(),
  };
}

export function errorOf(envelope: Envelope): WireError | undefined {
  return envelope.error;
}
