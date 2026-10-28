import { describe, expect, it } from 'vitest';
import {
  decodeEnvelope,
  encodeEnvelope,
  eventEnvelope,
  failureEnvelope,
  responseEnvelope,
} from '../src/main/core/protocol';
import { TYPES } from '../src/shared/types';

describe('envelope codec', () => {
  it('round-trips an event', () => {
    const envelope = eventEnvelope(TYPES.PING, { hello: 'world' });
    const decoded = decodeEnvelope(encodeEnvelope(envelope));
    expect(decoded.type).toBe(TYPES.PING);
    expect(decoded.id).toBe(envelope.id);
    expect(decoded.data).toEqual({ hello: 'world' });
  });

  it('builds correlated responses and failures', () => {
    const request = eventEnvelope(TYPES.SMS_LIST, { limit: 10 });
    const response = responseEnvelope(request, { messages: [] });
    expect(response.replyTo).toBe(request.id);
    expect(response.ok).toBe(true);
    const failure = failureEnvelope(request, 'PERMISSION_DENIED', 'No SMS permission');
    expect(failure.replyTo).toBe(request.id);
    expect(failure.ok).toBe(false);
    expect(failure.error?.code).toBe('PERMISSION_DENIED');
  });

  it('rejects unsupported protocol versions', () => {
    const envelope = { ...eventEnvelope(TYPES.PING), v: 2 };
    expect(() => decodeEnvelope(JSON.stringify(envelope))).toThrow(/protocol version/i);
  });

  it('rejects malformed messages', () => {
    expect(() => decodeEnvelope('not json')).toThrow(/Malformed JSON/i);
    expect(() => decodeEnvelope(JSON.stringify({ v: 1 }))).toThrow(/Invalid message id/i);
    expect(() => decodeEnvelope(JSON.stringify({ v: 1, id: 'x' }))).toThrow(
      /Invalid message type/i,
    );
  });

  it('rejects oversized messages', () => {
    const huge = 'x'.repeat(1_048_600);
    expect(() => decodeEnvelope(JSON.stringify(eventEnvelope(TYPES.PING, huge)))).toThrow(
      /size limit/i,
    );
  });
});
