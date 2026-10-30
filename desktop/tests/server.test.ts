import fs from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { createHash, generateKeyPairSync, sign } from 'node:crypto';
import { WebSocket } from 'ws';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { ensureCertificate, type PcCertificate } from '../src/main/core/certs';
import { Logger } from '../src/main/core/logger';
import { PairingManager } from '../src/main/core/pairing';
import { Store } from '../src/main/core/store';
import { buildAuthPayload, parsePhoneKey } from '../src/main/core/auth';
import { decodeEnvelope, encodeEnvelope, eventEnvelope } from '../src/main/core/protocol';
import { LinkServer, type PairingDecision, type PeerSession, type ServerCallbacks } from '../src/main/server';
import { TYPES, type Envelope } from '../src/shared/types';

let tempRoot = '';
let certificate: PcCertificate;

beforeAll(() => {
  tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-server-'));
  certificate = ensureCertificate(path.join(tempRoot, 'certs'));
});

afterAll(() => {
  fs.rmSync(tempRoot, { recursive: true, force: true });
});

interface Harness {
  server: LinkServer;
  store: Store;
  pairings: PairingManager;
  logger: Logger;
  port: number;
  state: {
    authorized?: PeerSession;
    pairingRequests: number;
    messages: Envelope[];
    /** Sessions reported through onClosed — authorized only. */
    closed: string[];
    /** Sockets reported through onPairingSocketClosed — every unauthorized close. */
    pairingSocketClosed: string[];
  };
  clients: Client[];
}

function makePhone() {
  const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const der = publicKey.export({ type: 'spki', format: 'der' });
  return {
    privateKey,
    spkiBase64: der.toString('base64'),
    fingerprint: createHash('sha256').update(der).digest('hex'),
  };
}

async function setup(
  onPairingRequest: ServerCallbacks['onPairingRequest'] = async (): Promise<PairingDecision> => ({
    approved: true,
  }),
): Promise<Harness> {
  const directory = fs.mkdtempSync(path.join(tempRoot, 'case-'));
  const store = new Store(path.join(directory, 'state.json'));
  const pairings = new PairingManager();
  const logger = new Logger(path.join(directory, 'log.txt'));
  const harness: Harness = {
    server: undefined as unknown as LinkServer,
    store,
    pairings,
    logger,
    port: 0,
    state: { pairingRequests: 0, messages: [], closed: [], pairingSocketClosed: [] },
    clients: [],
  };
  const callbacks: ServerCallbacks = {
    onPairingRequest: async (context) => {
      harness.state.pairingRequests += 1;
      return onPairingRequest(context);
    },
    onPaired: () => undefined,
    onAuthorized: (session) => {
      harness.state.authorized = session;
    },
    onClosed: (session) => {
      harness.state.closed.push(session.id);
    },
    onPairingSocketClosed: (sessionId) => {
      harness.state.pairingSocketClosed.push(sessionId);
    },
    onMessage: (_session, envelope) => {
      harness.state.messages.push(envelope);
    },
    onBinary: () => undefined,
  };
  harness.server = new LinkServer({
    certificate,
    store,
    pairings,
    logger,
    callbacks,
  });
  harness.port = await harness.server.start(0, '127.0.0.1');
  return harness;
}

class Client {
  readonly ws: WebSocket;
  private queue: Envelope[] = [];
  private waiters: Array<(envelope: Envelope) => void> = [];

  constructor(port: number) {
    this.ws = new WebSocket(`wss://127.0.0.1:${port}/ws`, {
      rejectUnauthorized: false,
    });
    this.ws.on('message', (data) => {
      const envelope = decodeEnvelope(data.toString());
      const waiter = this.waiters.shift();
      if (waiter) waiter(envelope);
      else this.queue.push(envelope);
    });
    this.ws.on('error', () => undefined);
  }

  static async open(port: number): Promise<Client> {
    const client = new Client(port);
    await new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('connect timeout')), 5_000);
      client.ws.once('open', () => {
        clearTimeout(timer);
        resolve();
      });
      client.ws.once('error', (error) => {
        clearTimeout(timer);
        reject(error);
      });
    });
    return client;
  }

  send(type: string, data?: unknown): void {
    this.ws.send(encodeEnvelope(eventEnvelope(type, data)));
  }

  next(timeoutMs = 5_000): Promise<Envelope> {
    const queued = this.queue.shift();
    if (queued) return Promise.resolve(queued);
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('message timeout')), timeoutMs);
      this.waiters.push((envelope) => {
        clearTimeout(timer);
        resolve(envelope);
      });
    });
  }

  async waitFor(type: string, timeoutMs = 5_000): Promise<Envelope> {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      const envelope = await this.next(Math.max(50, deadline - Date.now()));
      if (envelope.type === type) return envelope;
    }
    throw new Error(`Did not receive ${type}`);
  }

  close(): void {
    this.ws.terminate();
  }
}

async function pairDevice(harness: Harness, client: Client, phone: ReturnType<typeof makePhone>) {
  const session = harness.pairings.create();
  client.send(TYPES.PAIR_REQUEST, {
    token: session.token,
    deviceId: 'phone-1',
    name: 'Test phone',
    platform: 'android',
    appVersion: '0.1.0',
    publicKeySpki: phone.spkiBase64,
  });
  const response = await client.waitFor(TYPES.PAIR_OK);
  expect(response.data).toMatchObject({ pcId: harness.store.pcId });
}

async function authenticate(harness: Harness, client: Client, phone: ReturnType<typeof makePhone>) {
  client.send(TYPES.HELLO, {
    deviceId: 'phone-1',
    phoneName: 'Test phone',
    platform: 'android',
    appVersion: '0.1.0',
    publicKeySpki: phone.spkiBase64,
    protocol: 1,
  });
  const challenge = await client.waitFor(TYPES.AUTH_CHALLENGE);
  const data = challenge.data as { nonce: string; pcId: string };
  const payload = buildAuthPayload(data.pcId, 'phone-1', data.nonce);
  const signature = sign('sha256', Buffer.from(payload, 'utf8'), {
    key: phone.privateKey,
    dsaEncoding: 'der',
  }).toString('base64');
  client.send(TYPES.AUTH_RESPONSE, { signature });
  const ok = await client.waitFor(TYPES.AUTH_OK);
  expect(ok.type).toBe(TYPES.AUTH_OK);
}

const activeHarnesses: Harness[] = [];

afterEach(async () => {
  for (const harness of activeHarnesses.splice(0)) {
    for (const client of harness.clients) client.close();
    await harness.server.stop();
  }
});

async function startHarness(
  onPairingRequest?: ServerCallbacks['onPairingRequest'],
): Promise<Harness> {
  const harness = await setup(onPairingRequest);
  activeHarnesses.push(harness);
  return harness;
}

/** A promise the test resolves by hand, so an approval can hang on purpose. */
function deferred<T>() {
  let resolve: (value: T) => void = () => undefined;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

/** Polls a condition instead of sleeping for a guessed duration. */
async function waitUntil(check: () => boolean, timeoutMs = 2_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (check()) return;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error('condition was not met in time');
}

function pairPayload(token: string, deviceId: string, spkiBase64: string) {
  return {
    token,
    deviceId,
    name: `Test phone ${deviceId}`,
    platform: 'android',
    appVersion: '0.1.0',
    publicKeySpki: spkiBase64,
  };
}

describe('LinkServer pairing and authentication', () => {
  it('pairs a phone and stores its public key', async () => {
    const harness = await startHarness();
    const client = await Client.open(harness.port);
    harness.clients.push(client);
    const phone = makePhone();

    await pairDevice(harness, client, phone);

    expect(harness.state.pairingRequests).toBe(1);
    expect(harness.store.devices).toHaveLength(1);
    expect(harness.store.devices[0].fingerprint).toBe(phone.fingerprint);
    expect(harness.pairings.current()).toBeUndefined();
  });

  it('rejects a mismatched pairing token without prompting the user', async () => {
    const harness = await startHarness();
    const client = await Client.open(harness.port);
    harness.clients.push(client);
    const phone = makePhone();
    harness.pairings.create();

    client.send(TYPES.PAIR_REQUEST, {
      token: 'wrong-token',
      deviceId: 'phone-1',
      name: 'Test phone',
      platform: 'android',
      appVersion: '0.1.0',
      publicKeySpki: phone.spkiBase64,
    });
    const rejected = await client.waitFor(TYPES.PAIR_REJECTED);
    expect(rejected.error?.code).toBe('PAIRING_REJECTED');
    expect(harness.state.pairingRequests).toBe(0);
    expect(harness.store.devices).toHaveLength(0);
  });

  it('honors a rejection from the desktop UI', async () => {
    const harness = await startHarness(
      async (): Promise<PairingDecision> => ({ approved: false, reason: 'declined' }),
    );
    const client = await Client.open(harness.port);
    harness.clients.push(client);
    const phone = makePhone();
    const session = harness.pairings.create();
    client.send(TYPES.PAIR_REQUEST, {
      token: session.token,
      deviceId: 'phone-1',
      name: 'Test phone',
      platform: 'android',
      appVersion: '0.1.0',
      publicKeySpki: phone.spkiBase64,
    });
    const rejected = await client.waitFor(TYPES.PAIR_REJECTED);
    expect(rejected.error?.code).toBe('PAIRING_REJECTED');
    expect(harness.store.devices).toHaveLength(0);
  });

  it('reports a pairing socket that closes before approval', async () => {
    const pending = deferred<PairingDecision>();
    const harness = await startHarness(() => pending.promise);
    const client = await Client.open(harness.port);
    harness.clients.push(client);
    const phone = makePhone();
    const session = harness.pairings.create();

    client.send(TYPES.PAIR_REQUEST, pairPayload(session.token, 'phone-1', phone.spkiBase64));
    await client.waitFor(TYPES.PAIR_PENDING);
    client.close();

    // This is what clears the prompt on the PC. Without it the stale entry makes
    // every later attempt look "declined" with no dialog.
    await waitUntil(() => harness.state.pairingSocketClosed.length === 1);
    // A phone that never authenticated must not look like a disconnect.
    expect(harness.state.closed).toHaveLength(0);
  });

  it('keeps the code when the phone leaves before approval', async () => {
    const pending = deferred<PairingDecision>();
    const harness = await startHarness(() => pending.promise);
    const client = await Client.open(harness.port);
    harness.clients.push(client);
    const phone = makePhone();
    const session = harness.pairings.create();

    client.send(TYPES.PAIR_REQUEST, pairPayload(session.token, 'phone-1', phone.spkiBase64));
    await client.waitFor(TYPES.PAIR_PENDING);
    client.close();
    await waitUntil(() => harness.state.pairingSocketClosed.length === 1);

    // Approving after the phone is gone must not burn the code or store a device
    // the phone will never hear about — the token stays valid for a rescan.
    pending.resolve({ approved: true });
    await new Promise((resolve) => setTimeout(resolve, 150));
    expect(harness.store.devices).toHaveLength(0);
    expect(harness.pairings.current()).toBeDefined();
  });

  it('tells the phone when approval timed out rather than saying it was declined', async () => {
    const harness = await startHarness(
      async (): Promise<PairingDecision> => ({ approved: false, reason: 'timeout' }),
    );
    const client = await Client.open(harness.port);
    harness.clients.push(client);
    const phone = makePhone();
    const session = harness.pairings.create();

    client.send(TYPES.PAIR_REQUEST, pairPayload(session.token, 'phone-1', phone.spkiBase64));
    const rejected = await client.waitFor(TYPES.PAIR_REJECTED);
    expect(rejected.error?.code).toBe('PAIRING_REJECTED');
    expect(rejected.error?.message).toMatch(/timed out/i);
  });

  it('tells the phone when the code was cancelled', async () => {
    const harness = await startHarness(
      async (): Promise<PairingDecision> => ({ approved: false, reason: 'cancelled' }),
    );
    const client = await Client.open(harness.port);
    harness.clients.push(client);
    const phone = makePhone();
    const session = harness.pairings.create();

    client.send(TYPES.PAIR_REQUEST, pairPayload(session.token, 'phone-1', phone.spkiBase64));
    const rejected = await client.waitFor(TYPES.PAIR_REJECTED);
    expect(rejected.error?.message).toMatch(/cancelled/i);
  });

  it('authenticates a paired phone and routes events', async () => {
    const harness = await startHarness();
    const phone = makePhone();
    const key = parsePhoneKey(phone.spkiBase64, phone.fingerprint);
    harness.store.addDevice({
      id: 'phone-1',
      name: 'Test phone',
      platform: 'android',
      publicKeyPem: key.pem,
      fingerprint: key.fingerprint,
      pairedAt: Date.now(),
    });

    const client = await Client.open(harness.port);
    harness.clients.push(client);
    await authenticate(harness, client, phone);

    expect(harness.state.authorized).toBeDefined();
    expect(harness.server.activeSession).toBeDefined();
    expect(harness.store.devices[0].lastSeen).toBeGreaterThan(0);

    client.send(TYPES.PHONE_INFO, {
      model: 'Test Phone',
      androidVersion: '15',
      appVersion: '0.1.0',
      capabilities: ['sms'],
      subscriptions: [],
    });
    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(harness.state.messages.some((message) => message.type === TYPES.PHONE_INFO)).toBe(
      true,
    );
  });

  it('rejects a bad signature', async () => {
    const harness = await startHarness();
    const phone = makePhone();
    const other = makePhone();
    const key = parsePhoneKey(phone.spkiBase64, phone.fingerprint);
    harness.store.addDevice({
      id: 'phone-1',
      name: 'Test phone',
      platform: 'android',
      publicKeyPem: key.pem,
      fingerprint: key.fingerprint,
      pairedAt: Date.now(),
    });

    const client = await Client.open(harness.port);
    harness.clients.push(client);
    client.send(TYPES.HELLO, {
      deviceId: 'phone-1',
      phoneName: 'Test phone',
      platform: 'android',
      appVersion: '0.1.0',
      publicKeySpki: phone.spkiBase64,
      protocol: 1,
    });
    const challenge = await client.waitFor(TYPES.AUTH_CHALLENGE);
    const data = challenge.data as { nonce: string; pcId: string };
    const payload = buildAuthPayload(data.pcId, 'phone-1', data.nonce);
    const signature = sign('sha256', Buffer.from(payload, 'utf8'), {
      key: other.privateKey,
      dsaEncoding: 'der',
    }).toString('base64');
    client.send(TYPES.AUTH_RESPONSE, { signature });
    const error = await client.waitFor(TYPES.AUTH_ERROR);
    expect(error.error?.code).toBe('AUTH_FAILED');
    expect(harness.state.authorized).toBeUndefined();
  });

  it('rejects an unknown phone', async () => {
    const harness = await startHarness();
    const client = await Client.open(harness.port);
    harness.clients.push(client);
    const phone = makePhone();
    client.send(TYPES.HELLO, {
      deviceId: 'unknown',
      phoneName: 'Unknown',
      platform: 'android',
      appVersion: '0.1.0',
      publicKeySpki: phone.spkiBase64,
      protocol: 1,
    });
    const error = await client.waitFor(TYPES.AUTH_ERROR);
    expect(error.error?.code).toBe('UNKNOWN_DEVICE');
  });

  it('rejects pairing while another phone is connected', async () => {
    const harness = await startHarness();
    const phone = makePhone();
    const key = parsePhoneKey(phone.spkiBase64, phone.fingerprint);
    harness.store.addDevice({
      id: 'phone-1',
      name: 'Test phone',
      platform: 'android',
      publicKeyPem: key.pem,
      fingerprint: key.fingerprint,
      pairedAt: Date.now(),
    });
    const first = await Client.open(harness.port);
    harness.clients.push(first);
    await authenticate(harness, first, phone);

    const second = await Client.open(harness.port);
    harness.clients.push(second);
    const other = makePhone();
    const token = harness.pairings.create();
    second.send(TYPES.PAIR_REQUEST, {
      token: token.token,
      deviceId: 'phone-2',
      name: 'Second phone',
      platform: 'android',
      appVersion: '0.1.0',
      publicKeySpki: other.spkiBase64,
    });
    const rejected = await second.waitFor(TYPES.PAIR_REJECTED);
    expect(rejected.error?.code).toBe('BUSY');
  });
});

/**
 * "The phone cannot connect" used to leave an empty log, because nothing was
 * recorded before the first application frame: a dropped SYN and a failed TLS
 * handshake looked identical. These tests pin the lines that make them
 * distinguishable.
 */
describe('LinkServer inbound diagnostics', () => {
  function logText(harness: Harness): string {
    return harness.logger.lines().join('\n');
  }

  it('records a TCP connection and a failed TLS handshake', async () => {
    const harness = await startHarness();
    const socket = net.createConnection(harness.port, '127.0.0.1');
    await new Promise<void>((resolve, reject) => {
      socket.once('connect', () => resolve());
      socket.once('error', reject);
    });
    // Not a TLS record, so the handshake fails before `ws` ever sees it.
    socket.write('GET / HTTP/1.1\r\n\r\n');

    await waitUntil(() => logText(harness).includes('TCP connection from 127.0.0.1'));
    await waitUntil(() => logText(harness).includes('TLS handshake ended from 127.0.0.1'));
    socket.destroy();
  });

  it('records a hello from an unknown device and why it was refused', async () => {
    const harness = await startHarness();
    const client = await Client.open(harness.port);
    harness.clients.push(client);
    const phone = makePhone();

    client.send(TYPES.HELLO, {
      deviceId: 'stranger',
      phoneName: 'Unknown phone',
      platform: 'android',
      appVersion: '0.8.4',
      publicKeySpki: phone.spkiBase64,
      protocol: 1,
    });
    const error = await client.waitFor(TYPES.AUTH_ERROR);
    expect(error.error?.code).toBe('UNKNOWN_DEVICE');

    await waitUntil(() => logText(harness).includes('Hello from 127.0.0.1'));
    await waitUntil(() => logText(harness).includes('unknown device'));
  });

  it('records why a second phone was refused', async () => {
    const harness = await startHarness();
    const phone = makePhone();
    const key = parsePhoneKey(phone.spkiBase64, phone.fingerprint);
    harness.store.addDevice({
      id: 'phone-1',
      name: 'Test phone',
      platform: 'android',
      publicKeyPem: key.pem,
      fingerprint: key.fingerprint,
      pairedAt: Date.now(),
    });
    const second = makePhone();
    const secondKey = parsePhoneKey(second.spkiBase64, second.fingerprint);
    harness.store.addDevice({
      id: 'phone-2',
      name: 'Second phone',
      platform: 'android',
      publicKeyPem: secondKey.pem,
      fingerprint: secondKey.fingerprint,
      pairedAt: Date.now(),
    });

    const first = await Client.open(harness.port);
    harness.clients.push(first);
    await authenticate(harness, first, phone);

    const other = await Client.open(harness.port);
    harness.clients.push(other);
    other.send(TYPES.HELLO, {
      deviceId: 'phone-2',
      phoneName: 'Second phone',
      platform: 'android',
      appVersion: '0.8.4',
      publicKeySpki: second.spkiBase64,
      protocol: 1,
    });
    const error = await other.waitFor(TYPES.AUTH_ERROR);
    expect(error.error?.code).toBe('BUSY');

    await waitUntil(() => logText(harness).includes('another phone is connected'));
  });

  it('records the pair request before approval and never the token', async () => {
    const approval = deferred<PairingDecision>();
    const harness = await startHarness(async () => approval.promise);
    const client = await Client.open(harness.port);
    harness.clients.push(client);
    const phone = makePhone();
    const session = harness.pairings.create();

    client.send(TYPES.PAIR_REQUEST, pairPayload(session.token, 'phone-1', phone.spkiBase64));
    await client.waitFor(TYPES.PAIR_PENDING);

    await waitUntil(() => logText(harness).includes('Pair request from 127.0.0.1'));
    // The token is a credential: it must never reach the log file.
    expect(logText(harness)).not.toContain(session.token);

    approval.resolve({ approved: true });
    const ok = await client.waitFor(TYPES.PAIR_OK);
    expect(ok.type).toBe(TYPES.PAIR_OK);
  });
});
