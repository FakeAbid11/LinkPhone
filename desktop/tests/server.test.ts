import fs from 'node:fs';
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
import { LinkServer, type PeerSession, type ServerCallbacks } from '../src/main/server';
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
  port: number;
  state: {
    authorized?: PeerSession;
    pairingRequests: number;
    messages: Envelope[];
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
  onPairingRequest: ServerCallbacks['onPairingRequest'] = async () => true,
): Promise<Harness> {
  const directory = fs.mkdtempSync(path.join(tempRoot, 'case-'));
  const store = new Store(path.join(directory, 'state.json'));
  const pairings = new PairingManager();
  const logger = new Logger(path.join(directory, 'log.txt'));
  const harness: Harness = {
    server: undefined as unknown as LinkServer,
    store,
    pairings,
    port: 0,
    state: { pairingRequests: 0, messages: [] },
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
    onClosed: () => undefined,
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
    const harness = await startHarness(async () => false);
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
