import https from 'node:https';
import { randomUUID } from 'node:crypto';
import { WebSocketServer, WebSocket } from 'ws';
import type { IncomingMessage } from 'node:http';
import type { Envelope, PairRequestWire, HelloWire } from '../shared/types';
import { ERROR_CODES, PROTOCOL_VERSION, TYPES } from '../shared/types';
import {
  buildAuthPayload,
  newChallenge,
  parsePhoneKey,
  verifyPhoneSignature,
} from './core/auth';
import { decodeBinaryFrame, type BinaryFrame } from './core/binary';
import { decodeEnvelope, encodeEnvelope, eventEnvelope } from './core/protocol';
import type { PairingManager } from './core/pairing';
import type { DeviceRecord, Store } from './core/store';
import type { PcCertificate } from './core/certs';
import type { Logger } from './core/logger';

type AuthResponseWire = { signature: string };

/**
 * What the phone is told when approval does not happen. The Android client
 * shows this string verbatim on its "Pairing failed" card, so each reason has to
 * say what to do next rather than collapse into "declined".
 */
const PAIRING_DECLINE_MESSAGES: Record<PairingDeclineReason, string> = {
  declined: 'Pairing was declined on the PC.',
  timeout: 'The approval request timed out on the PC. Scan the code again and approve it promptly.',
  cancelled: 'The pairing code was cancelled on the PC. Create a new code and scan it again.',
  superseded: 'A newer pairing request replaced this one.',
  disconnected: 'The PC lost the connection before the request was approved.',
};

interface PendingReply {
  resolve: (envelope: Envelope) => void;
  reject: (error: Error) => void;
  timer: NodeJS.Timeout;
}

/**
 * Node reports IPv4 peers as `::ffff:192.168.0.57` on a dual-stack listener.
 * Stripping the prefix keeps the log readable and lets a user compare it with
 * what `ipconfig` prints.
 */
function bareAddress(value: string | undefined): string {
  return (value ?? 'unknown').replace(/^::ffff:/, '');
}

export interface PairingRequestContext {
  id: string;
  sessionId: string;
  deviceId: string;
  name: string;
  platform: string;
  fingerprint: string;
  publicKeyPem: string;
  remoteAddress: string;
}

/** Why the PC stopped waiting for approval. Drives the message the phone shows. */
export type PairingDeclineReason =
  | 'declined'
  | 'timeout'
  | 'cancelled'
  | 'superseded'
  | 'disconnected';

export type PairingDecision =
  | { approved: true }
  | { approved: false; reason: PairingDeclineReason };

export interface ServerCallbacks {
  onPairingRequest(context: PairingRequestContext): Promise<PairingDecision>;
  onPaired(device: DeviceRecord): void;
  onAuthorized(session: PeerSession): void;
  onClosed(session: PeerSession, reason: string): void;
  /**
   * Every socket close, authorized or not. A pairing socket never authenticates,
   * so onClosed never fires for it — without this the host cannot tell that the
   * phone walked away from an open approval prompt.
   */
  onPairingSocketClosed(sessionId: string): void;
  onMessage(session: PeerSession, envelope: Envelope): void;
  onBinary(session: PeerSession, frame: BinaryFrame): void;
}

export interface ServerOptions {
  certificate: PcCertificate;
  store: Store;
  pairings: PairingManager;
  logger: Logger;
  callbacks: ServerCallbacks;
}

export class PeerSession {
  readonly id = randomUUID();
  readonly remoteAddress: string;
  authorized = false;
  /** Set once this socket's pairing has been committed, so a duplicate request cannot pair twice. */
  pairingCommitted = false;
  device: DeviceRecord | undefined;
  phoneName: string | undefined;
  connectedAt = Date.now();
  lastSeen = Date.now();

  pendingNonce: string | undefined;
  pendingDevice: DeviceRecord | undefined;
  challengeAt = 0;
  private pendingReplies = new Map<string, PendingReply>();
  private authFailures = 0;
  private messageWindowStart = Date.now();
  private messageCount = 0;
  private authTimer: NodeJS.Timeout | undefined;
  private closed = false;

  constructor(
    private readonly socket: WebSocket,
    private readonly server: LinkServer,
    remoteAddress: string,
  ) {
    this.remoteAddress = bareAddress(remoteAddress);
    this.authTimer = setTimeout(() => {
      if (!this.authorized) this.close(4008, 'Authentication timeout');
    }, 15_000);
    this.authTimer.unref?.();
  }

  get open(): boolean {
    return this.socket.readyState === WebSocket.OPEN;
  }

  /** True while the socket is usable: open and not yet cleaned up. */
  get alive(): boolean {
    return this.open && !this.closed;
  }

  send(envelope: Envelope): boolean {
    if (!this.open) return false;
    try {
      this.socket.send(encodeEnvelope(envelope));
      return true;
    } catch {
      return false;
    }
  }

  sendBinary(buffer: Buffer): boolean {
    if (!this.open) return false;
    try {
      this.socket.send(buffer, { binary: true });
      return true;
    } catch {
      return false;
    }
  }

  sendEvent(type: string, data?: unknown): boolean {
    return this.send(eventEnvelope(type, data));
  }

  request<T = unknown>(
    type: string,
    data?: unknown,
    timeoutMs = 15_000,
  ): Promise<Envelope<T>> {
    if (!this.authorized || !this.open) {
      return Promise.reject(new Error('Phone is not connected'));
    }
    const envelope: Envelope = {
      v: PROTOCOL_VERSION,
      id: randomUUID(),
      type,
      data,
      ts: Date.now(),
    };
    return new Promise<Envelope<T>>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingReplies.delete(envelope.id);
        reject(new Error('The phone did not respond in time'));
      }, timeoutMs);
      timer.unref?.();
      this.pendingReplies.set(envelope.id, {
        resolve: resolve as unknown as (value: Envelope) => void,
        reject,
        timer,
      });
      if (!this.send(envelope)) {
        clearTimeout(timer);
        this.pendingReplies.delete(envelope.id);
        reject(new Error('Phone is not connected'));
      }
    });
  }

  close(code = 1000, reason = 'closed'): void {
    if (this.closed) return;
    try {
      this.socket.close(code, reason.slice(0, 120));
    } catch {
      this.socket.terminate();
    }
    const timer = setTimeout(() => this.socket.terminate(), 800);
    timer.unref?.();
  }

  markAlive(): void {
    this.lastSeen = Date.now();
  }

  ping(): void {
    if (!this.open) return;
    try {
      this.socket.ping();
    } catch {
      this.socket.terminate();
    }
  }

  handleText(text: string): void {
    this.markAlive();
    const now = Date.now();
    if (now - this.messageWindowStart > 60_000) {
      this.messageWindowStart = now;
      this.messageCount = 0;
    }
    this.messageCount += 1;
    if (this.messageCount > 400) {
      this.close(4009, 'Too many messages');
      return;
    }

    let envelope: Envelope;
    try {
      envelope = decodeEnvelope(text);
    } catch (error) {
      this.server.log('Protocol', `Malformed message from phone: ${String(error)}`);
      this.close(4002, 'Malformed message');
      return;
    }

    if (envelope.replyTo) {
      const pending = this.pendingReplies.get(envelope.replyTo);
      if (!pending) return;
      clearTimeout(pending.timer);
      this.pendingReplies.delete(envelope.replyTo);
      if (envelope.ok === false) {
        const error = new Error(envelope.error?.message ?? 'Phone command failed');
        (error as Error & { code?: string }).code = envelope.error?.code;
        pending.reject(error);
      } else {
        pending.resolve(envelope);
      }
      return;
    }

    if (!this.authorized) {
      void this.server.handleUnauthenticated(this, envelope);
      return;
    }

    try {
      this.server.dispatchMessage(this, envelope);
    } catch (error) {
      this.server.log('Protocol', `Command failed: ${String(error)}`);
    }
  }

  handleBinary(buffer: Buffer): void {
    this.markAlive();
    if (!this.authorized) return;
    const frame = decodeBinaryFrame(buffer);
    if (!frame) {
      this.close(4002, 'Malformed binary frame');
      return;
    }
    try {
      this.server.dispatchBinary(this, frame);
    } catch (error) {
      this.server.log('Protocol', `Binary dispatch failed: ${String(error)}`);
    }
  }

  authenticate(device: DeviceRecord): void {
    this.authorized = true;
    this.device = device;
    this.phoneName = device.name;
    this.pendingNonce = undefined;
    this.pendingDevice = undefined;
    this.authFailures = 0;
    if (this.authTimer) clearTimeout(this.authTimer);
    this.authTimer = undefined;
  }

  recordAuthFailure(): number {
    this.authFailures += 1;
    return this.authFailures;
  }

  clearAuthTimer(): void {
    if (this.authTimer) clearTimeout(this.authTimer);
    this.authTimer = undefined;
  }

  cleanup(reason: string): void {
    if (this.closed) return;
    this.closed = true;
    if (this.authTimer) clearTimeout(this.authTimer);
    this.authTimer = undefined;
    for (const pending of this.pendingReplies.values()) {
      clearTimeout(pending.timer);
      pending.reject(new Error(`Phone disconnected: ${reason}`));
    }
    this.pendingReplies.clear();
    this.server.sessionClosed(this, reason);
  }
}

export class LinkServer {
  private readonly options: ServerOptions;
  private httpServer: https.Server | undefined;
  private wss: WebSocketServer | undefined;
  private active: PeerSession | undefined;
  private heartbeat: NodeJS.Timeout | undefined;
  private port = 0;
  private sessions = new Set<PeerSession>();

  constructor(options: ServerOptions) {
    this.options = options;
  }

  get activeSession(): PeerSession | undefined {
    return this.active;
  }

  get listeningPort(): number {
    return this.port;
  }

  log(tag: string, message: string): void {
    this.options.logger.log(tag, message);
  }

  async start(preferredPort: number, host: string): Promise<number> {
    const server = https.createServer({
      key: this.options.certificate.keyPem,
      cert: this.options.certificate.certPem,
      minVersion: 'TLSv1.2',
    });
    server.on('request', (_request, response) => {
      response.writeHead(404, { 'content-type': 'text/plain' });
      response.end('LinkPhone secure endpoint. Use the desktop app.');
    });
    const wss = new WebSocketServer({
      server,
      path: '/ws',
      maxPayload: 2 * 1024 * 1024,
      perMessageDeflate: false,
    });
    wss.on('connection', (socket, request) => this.accept(socket, request));
    wss.on('error', (error) => this.log('Server', `WebSocket error: ${error.message}`));
    server.on('error', (error) => this.log('Server', `TLS server error: ${error.message}`));
    // Inbound attempts used to leave no trace until an application frame arrived,
    // which made "the phone cannot connect" impossible to diagnose: a dropped SYN
    // and a failed TLS handshake both produced an empty log. These two listeners
    // are what let the log answer "did the packet arrive at all?".
    server.on('connection', (socket) => {
      this.log(
        'Server',
        `TCP connection from ${bareAddress(socket.remoteAddress)}:${socket.remotePort ?? 0}`,
      );
    });
    // A handshake that fails never reaches `ws`, so without this the client's
    // "certificate does not match" has no counterpart here.
    server.on('tlsClientError', (error, socket) => {
      this.log(
        'Server',
        `TLS handshake ended from ${bareAddress(socket.remoteAddress)}: ${error.message}`,
      );
    });
    this.httpServer = server;
    this.wss = wss;

    const candidatePorts =
      preferredPort === 0
        ? [0]
        : Array.from({ length: 10 }, (_, index) => preferredPort + index);
    let lastError: Error | undefined;
    for (const port of candidatePorts) {
      try {
        await new Promise<void>((resolve, reject) => {
          const onError = (error: Error) => {
            server.off('listening', onListening);
            reject(error);
          };
          const onListening = () => {
            server.off('error', onError);
            resolve();
          };
          server.once('error', onError);
          server.once('listening', onListening);
          server.listen(port, host);
        });
        this.port = (server.address() as { port: number }).port;
        this.heartbeat = setInterval(() => this.checkHeartbeats(), 25_000);
        this.heartbeat.unref?.();
        this.log('Server', `Listening on ${host}:${this.port}`);
        return this.port;
      } catch (error) {
        lastError = error as Error;
        if ((error as NodeJS.ErrnoException).code !== 'EADDRINUSE') break;
      }
    }
    throw lastError ?? new Error('Could not start the LinkPhone server');
  }

  async stop(): Promise<void> {
    if (this.heartbeat) clearInterval(this.heartbeat);
    this.heartbeat = undefined;
    for (const session of this.sessions) session.close(1001, 'Server stopping');
    this.sessions.clear();
    this.active = undefined;
    const wss = this.wss;
    const server = this.httpServer;
    this.wss = undefined;
    this.httpServer = undefined;
    if (wss) {
      for (const client of wss.clients) client.terminate();
      await new Promise<void>((resolve) => wss.close(() => resolve()));
    }
    if (server) {
      await new Promise<void>((resolve) => server.close(() => resolve()));
    }
  }

  dispatchMessage(session: PeerSession, envelope: Envelope): void {
    if (this.active !== session) return;
    if (envelope.type === TYPES.PING) {
      session.sendEvent(TYPES.PONG, { timestamp: Date.now() });
      return;
    }
    this.options.callbacks.onMessage(session, envelope);
  }

  dispatchBinary(session: PeerSession, frame: BinaryFrame): void {
    if (this.active !== session) return;
    this.options.callbacks.onBinary(session, frame);
  }

  sessionClosed(session: PeerSession, reason: string): void {
    this.sessions.delete(session);
    if (this.active === session) this.active = undefined;
    if (session.authorized) {
      this.log('Server', `Phone disconnected: ${reason}`);
      this.options.callbacks.onClosed(session, reason);
    } else {
      // Pairing sockets are never authorized, so onClosed never fires for them.
      this.options.callbacks.onPairingSocketClosed(session.id);
    }
  }

  async handleUnauthenticated(session: PeerSession, envelope: Envelope): Promise<void> {
    switch (envelope.type) {
      case TYPES.PAIR_REQUEST:
        await this.handlePairRequest(session, envelope);
        return;
      case TYPES.HELLO:
        this.handleHello(session, envelope);
        return;
      case TYPES.AUTH_RESPONSE:
        this.handleAuthResponse(session, envelope);
        return;
      default:
        this.sendAuthError(session, ERROR_CODES.PAIRING_REQUIRED, 'Pair or authenticate first');
    }
  }

  private handleHello(session: PeerSession, envelope: Envelope): void {
    const hello = envelope.data as HelloWire | undefined;
    if (!hello || typeof hello.deviceId !== 'string' || !hello.deviceId) {
      this.log('Auth', `Malformed hello from ${session.remoteAddress}`);
      this.sendAuthError(session, ERROR_CODES.BAD_REQUEST, 'Invalid hello message');
      session.close(4003, 'Bad hello');
      return;
    }
    // Logged before any rejection so the log can say what was attempted, not
    // only that it failed.
    this.log(
      'Auth',
      `Hello from ${session.remoteAddress} (device ${hello.deviceId.slice(0, 8)}…, ` +
        `app ${typeof hello.appVersion === 'string' ? hello.appVersion : '?'})`,
    );
    if (hello.protocol !== PROTOCOL_VERSION) {
      this.log(
        'Auth',
        `Refused hello from ${session.remoteAddress}: protocol ${String(hello.protocol)} != ${PROTOCOL_VERSION}`,
      );
      this.sendAuthError(
        session,
        ERROR_CODES.BAD_REQUEST,
        'This phone and PC use different LinkPhone protocol versions. Update both apps.',
      );
      session.close(4003, 'Protocol mismatch');
      return;
    }
    const device = this.options.store.findDevice(hello.deviceId);
    if (!device) {
      this.log('Auth', `Refused hello from ${session.remoteAddress}: unknown device`);
      this.sendAuthError(
        session,
        ERROR_CODES.UNKNOWN_DEVICE,
        'This PC does not recognize the phone. Pair it again.',
      );
      session.close(4004, 'Unknown device');
      return;
    }
    if (hello.publicKeySpki) {
      try {
        const key = parsePhoneKey(hello.publicKeySpki);
        if (key.fingerprint !== device.fingerprint) {
          throw new Error('Phone key does not match the paired key');
        }
      } catch {
        this.log(
          'Auth',
          `Refused hello from ${session.remoteAddress}: key does not match the paired key`,
        );
        this.sendAuthError(
          session,
          ERROR_CODES.AUTH_FAILED,
          'The phone key changed. Pair the phone again.',
        );
        session.close(4004, 'Phone key mismatch');
        return;
      }
    }

    if (this.active && this.active !== session) {
      if (this.active.device?.id === device.id) {
        this.active.close(4001, 'Replaced by a newer connection');
      } else {
        this.log(
          'Auth',
          `Refused hello from ${session.remoteAddress}: another phone is connected`,
        );
        this.sendAuthError(
          session,
          ERROR_CODES.BUSY,
          'Another phone is already connected to this PC.',
        );
        session.close(4005, 'Busy');
        return;
      }
    }

    const nonce = newChallenge();
    session.pendingNonce = nonce;
    session.pendingDevice = device;
    session.challengeAt = Date.now();
    session.sendEvent(TYPES.AUTH_CHALLENGE, {
      nonce,
      pcId: this.options.store.pcId,
      pcName: this.options.store.pcName,
    });
  }

  private handleAuthResponse(session: PeerSession, envelope: Envelope): void {
    const response = envelope.data as AuthResponseWire | undefined;
    const nonce = session.pendingNonce;
    const device = session.pendingDevice;
    if (!response || typeof response.signature !== 'string' || !nonce || !device) {
      this.sendAuthError(session, ERROR_CODES.AUTH_FAILED, 'Authentication challenge missing');
      session.close(4006, 'Auth failed');
      return;
    }
    if (Date.now() - session.challengeAt > 15_000) {
      this.sendAuthError(session, ERROR_CODES.AUTH_FAILED, 'Authentication challenge expired');
      session.close(4006, 'Auth expired');
      return;
    }
    const payload = buildAuthPayload(this.options.store.pcId, device.id, nonce);
    if (!verifyPhoneSignature(device.publicKeyPem, payload, response.signature)) {
      const failures = session.recordAuthFailure();
      this.sendAuthError(session, ERROR_CODES.AUTH_FAILED, 'The phone could not prove its identity');
      this.log('Auth', `Signature verification failed (${failures})`);
      if (failures >= 3) session.close(4006, 'Too many failures');
      return;
    }

    if (this.active && this.active !== session && this.active.device?.id !== device.id) {
      this.sendAuthError(session, ERROR_CODES.BUSY, 'Another phone is already connected.');
      session.close(4005, 'Busy');
      return;
    }

    this.active = session;
    session.authenticate(device);
    this.options.store.touchDevice(device.id);
    session.sendEvent(TYPES.AUTH_OK, {
      pcId: this.options.store.pcId,
      pcName: this.options.store.pcName,
      protocol: PROTOCOL_VERSION,
    });
    this.log('Auth', `${device.name} authenticated from ${session.remoteAddress}`);
    this.options.callbacks.onAuthorized(session);
  }

  private async handlePairRequest(session: PeerSession, envelope: Envelope): Promise<void> {
    const request = envelope.data as PairRequestWire | undefined;
    if (!request ||
      typeof request.token !== 'string' ||
      typeof request.deviceId !== 'string' ||
      typeof request.name !== 'string' ||
      typeof request.publicKeySpki !== 'string'
    ) {
      this.sendPairingError(session, ERROR_CODES.BAD_REQUEST, 'Invalid pairing request');
      return;
    }
    const deviceId = request.deviceId.trim();
    if (!deviceId || deviceId.length > 64) {
      this.sendPairingError(session, ERROR_CODES.BAD_REQUEST, 'Invalid phone identifier');
      return;
    }
    // The name is user-visible copy from another process, so it is trimmed and
    // capped. The token is never logged.
    this.log(
      'Pair',
      `Pair request from ${session.remoteAddress} (${request.name.trim().slice(0, 40) || 'unnamed'})`,
    );
    // A phone that is already connected may re-pair to replace its own entry,
    // for example after the PC identity was reset. A different phone is BUSY.
    if (this.active && this.active.device?.id !== deviceId) {
      this.log('Pair', `Refused pairing from ${session.remoteAddress}: another phone is connected`);
      this.sendPairingError(
        session,
        ERROR_CODES.BUSY,
        'Disconnect the phone already connected to this PC before pairing another.',
      );
      return;
    }
    const validation = this.options.pairings.validate(
      request.token,
      session.remoteAddress,
    );
    if (!validation.ok) {
      this.log('Pair', `Rejected pairing: ${validation.reason}`);
      this.sendPairingError(session, ERROR_CODES.PAIRING_REJECTED, validation.reason);
      return;
    }

    const name = request.name.trim().slice(0, 80) || 'Android phone';
    const platform = (request.platform || 'android').slice(0, 40);
    let key: { pem: string; fingerprint: string };
    try {
      key = parsePhoneKey(request.publicKeySpki);
    } catch (error) {
      this.sendPairingError(
        session,
        ERROR_CODES.BAD_REQUEST,
        error instanceof Error ? error.message : 'Invalid phone key',
      );
      return;
    }

    session.clearAuthTimer();
    session.sendEvent(TYPES.PAIR_PENDING);
    const context: PairingRequestContext = {
      id: randomUUID(),
      sessionId: session.id,
      deviceId,
      name,
      platform,
      fingerprint: key.fingerprint,
      publicKeyPem: key.pem,
      remoteAddress: session.remoteAddress,
    };

    let decision: PairingDecision = { approved: false, reason: 'declined' };
    try {
      decision = await Promise.race([
        this.options.callbacks.onPairingRequest(context),
        // Safety net only. The host answers with a real reason at 90 s; this
        // sits behind it so the reason survives instead of becoming a decline.
        new Promise<PairingDecision>((resolve) => {
          const timer = setTimeout(() => resolve({ approved: false, reason: 'timeout' }), 100_000);
          timer.unref?.();
        }),
      ]);
    } catch {
      decision = { approved: false, reason: 'declined' };
    }

    if (!decision.approved) {
      this.sendPairingError(
        session,
        ERROR_CODES.PAIRING_REJECTED,
        PAIRING_DECLINE_MESSAGES[decision.reason],
      );
      session.close(4007, `Pairing ${decision.reason}`);
      return;
    }

    // The prompt can outlive the socket it belongs to: the phone locks, loses
    // Wi-Fi, or the user approves a request whose phone is already gone. Do not
    // burn the code or store a device the phone will never hear about — leaving
    // the token valid lets them rescan without creating a new one.
    if (!session.alive) {
      this.log('Pair', `Pairing aborted: ${context.name} disconnected before approval`);
      return;
    }

    // A retry on the same socket joins the prompt already on screen, so both
    // waiters resume here. Only the first one may commit.
    if (session.pairingCommitted) return;
    session.pairingCommitted = true;

    const device: DeviceRecord = {
      id: context.deviceId,
      name: context.name,
      platform: context.platform,
      publicKeyPem: context.publicKeyPem,
      fingerprint: context.fingerprint,
      pairedAt: Date.now(),
    };
    this.options.pairings.consume();
    this.options.store.addDevice(device);
    session.sendEvent(TYPES.PAIR_OK, {
      pcId: this.options.store.pcId,
      pcName: this.options.store.pcName,
    });
    this.log('Pair', `Paired ${device.name} (${device.fingerprint.slice(0, 12)}…)`);
    this.options.callbacks.onPaired(device);
  }

  private sendAuthError(session: PeerSession, code: string, message: string): void {
    session.send({
      ...eventEnvelope(TYPES.AUTH_ERROR),
      error: { code, message },
    });
  }

  private sendPairingError(session: PeerSession, code: string, message: string): void {
    session.send({
      ...eventEnvelope(TYPES.PAIR_REJECTED),
      error: { code, message },
    });
  }

  private accept(socket: WebSocket, request: IncomingMessage): void {
    // Also covers a browser probe of https://<ip>:<port>/, which is the quickest
    // way for a user to prove the port is reachable from the phone.
    this.log(
      'Server',
      `WebSocket upgrade from ${bareAddress(request.socket.remoteAddress)} ${request.url ?? ''}`,
    );
    if (this.sessions.size >= 4) {
      socket.close(4005, 'Too many connections');
      return;
    }
    const session = new PeerSession(
      socket,
      this,
      request.socket.remoteAddress ?? 'unknown',
    );
    this.sessions.add(session);
    socket.on('pong', () => session.markAlive());
    socket.on('message', (data, isBinary) => {
      if (isBinary) {
        const buffer = Buffer.isBuffer(data)
          ? data
          : Array.isArray(data)
            ? Buffer.concat(data)
            : Buffer.from(data as ArrayBuffer);
        session.handleBinary(buffer);
      } else {
        session.handleText(Buffer.isBuffer(data) ? data.toString('utf8') : data.toString());
      }
    });
    socket.on('close', (_code, reason) => {
      session.cleanup(reason.toString() || 'connection closed');
    });
    socket.on('error', (error) => {
      this.log('Server', `Socket error: ${error.message}`);
      session.cleanup(error.message);
    });
  }

  private checkHeartbeats(): void {
    const now = Date.now();
    for (const session of this.sessions) {
      if (now - session.lastSeen > 75_000) {
        session.close(4008, 'Heartbeat timeout');
        continue;
      }
      session.ping();
    }
  }
}
