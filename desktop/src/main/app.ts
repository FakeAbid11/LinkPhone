import fs from 'node:fs';
import path from 'node:path';
import {
  BrowserWindow,
  Notification,
  app,
  clipboard,
  nativeImage,
  shell,
} from 'electron';
import QRCode from 'qrcode';
import type { BinaryFrame } from './core/binary';
import { ensureCertificate, type PcCertificate } from './core/certs';
import { Logger } from './core/logger';
import { buildPairingUri, getLanAddresses } from './core/network';
import { runNetworkCheck } from './core/network-check';
import { PairingManager } from './core/pairing';
import { PairingApprovals } from './core/pairing-approvals';
import { normalizePhoneInfo } from './core/phone-info';
import { Store } from './core/store';
import { StreamRegistry } from './core/streams';
import {
  LinkServer,
  type PairingDecision,
  type PairingRequestContext,
  type PeerSession,
  type ServerCallbacks,
} from './server';
import { DiscoveryPublisher } from './discovery';
import { CallService } from './services/calls';
import { ClipboardService } from './services/clipboard';
import { HandoffService } from './services/handoff';
import { MirrorService } from './services/mirror';
import { NotificationService } from './services/notifications';
import { PhotoService } from './services/photos';
import { SmsService } from './services/sms';
import { GitHubUpdateClient } from './updater/github';
import { UpdateService } from './updater/UpdateService';
import {
  CAPABILITIES,
  TYPES,
  type AppSnapshot,
  type BatteryWire,
  type CallStateWire,
  type ConnectionInfo,
  type Envelope,
  type HandoffFileMetaWire,
  type HandoffShareWire,
  type MirrorCodec,
  type MirrorTouchWire,
  type NetworkCheckResult,
  type NotificationRemovedWire,
  type NotificationWire,
  type PairingOffer,
  type PairingRequestUi,
  type PhoneInfoWire,
  type Settings,
  type SimInfoWire,
  type UpdateMode,
} from '../shared/types';

export class AppController {
  private readonly logger: Logger;
  private readonly store: Store;
  private readonly pairings = new PairingManager();
  private readonly streams = new StreamRegistry();
  private readonly certDir: string;
  private cert!: PcCertificate;
  private server: LinkServer | undefined;
  private discovery: DiscoveryPublisher | undefined;
  private session: PeerSession | undefined;
  private window: BrowserWindow | undefined;
  private trayUpdater: ((snapshot: AppSnapshot) => void) | undefined;

  private sms!: SmsService;
  private notifications!: NotificationService;
  private photos!: PhotoService;
  private calls!: CallService;
  private clipboardService!: ClipboardService;
  private handoff!: HandoffService;
  private mirrorService!: MirrorService;
  private updates!: UpdateService;

  private pairingOffer: PairingOffer | undefined;
  private readonly approvals = new PairingApprovals({
    onChange: () => this.scheduleBroadcast(),
    onTimeout: (context) => {
      this.pairingError = `${context.name} was not approved in time. Scan the code again.`;
    },
    onSuperseded: () => {
      this.logger.log('Pair', 'A newer pairing request replaced the pending one');
    },
    onDisconnected: () => {
      this.pairingError =
        'The phone disconnected before the request was approved. Scan the code again.';
    },
  });
  private pairingError: string | undefined;
  private serverError: string | undefined;
  /** Last result of the on-demand connection check; never refreshed on its own. */
  private networkCheck: NetworkCheckResult | undefined;
  private networkCheckTask: Promise<void> | undefined;
  private lastError: string | undefined;
  private phoneInfo: PhoneInfoWire | undefined;
  /** False until the phone reports its feature set for this session. */
  private infoReceived = false;
  private batteryLevel: number | undefined;
  private batteryCharging: boolean | undefined;
  private capabilities: string[] = [];
  private subscriptions: SimInfoWire[] = [];
  private connectedAt: number | undefined;
  private broadcastTimer: NodeJS.Timeout | undefined;
  private autoClipboardTimer: NodeJS.Timeout | undefined;
  private pendingInstaller: string | undefined;
  private started = false;
  /** Stream ids already reported as dropped, so one failure logs once. */
  private readonly droppedStreams = new Set<number>();

  constructor(
    private readonly userDataDir: string,
    private readonly e2e: boolean,
  ) {
    this.logger = new Logger(path.join(userDataDir, 'logs', 'linkphone.log'));
    this.store = new Store(path.join(userDataDir, 'state.json'));
    this.certDir = path.join(userDataDir, 'certs');
    this.createServices();
    this.logger.log('App', `Starting LinkPhone ${app.getVersion()}`);
  }

  async start(): Promise<void> {
    if (this.started) return;
    this.started = true;
    // The updater is independent of the connection: it must still run when the
    // server cannot start, or the user has no way to install a fix.
    this.updates.start();
    try {
      this.cert = ensureCertificate(this.certDir);
      this.logger.log('TLS', `PC certificate ${this.cert.fingerprintHex.slice(0, 16)}…`);
      const nextServer = new LinkServer({
        certificate: this.cert,
        store: this.store,
        pairings: this.pairings,
        logger: this.logger,
        callbacks: this.serverCallbacks(),
      });
      const port = await nextServer.start(
        this.e2e ? 0 : 48921,
        this.e2e ? '127.0.0.1' : '0.0.0.0',
      );
      this.server = nextServer;
      this.serverError = undefined;
      this.logger.log('App', `Server listening on port ${port}`);

      if (!this.e2e) {
        this.discovery = new DiscoveryPublisher(this.logger);
        this.discovery.start(port, this.store.pcId, this.cert.fingerprintHex);
      }

      if (!this.e2e && this.store.devices.length === 0) {
        await this.createPairingOffer().catch((error: unknown) => {
          this.pairingError =
            error instanceof Error ? error.message : 'Could not create a pairing code';
        });
      }

      this.autoClipboardTimer = setInterval(() => {
        void this.clipboardService.maybeAutoSend(this.store.settings.autoClipboard);
      }, 1_200);
      this.autoClipboardTimer.unref?.();
      this.scheduleBroadcast();
    } catch (error) {
      this.serverError =
        error instanceof Error ? error.message : 'The LinkPhone server could not start';
      this.lastError = this.serverError;
      this.logger.log('App', `Startup failed: ${this.serverError}`);
      this.scheduleBroadcast();
    }
  }

  attachWindow(window: BrowserWindow): void {
    this.window = window;
    window.webContents.on('did-finish-load', () => this.pushState());
  }

  setTrayUpdater(updater: (snapshot: AppSnapshot) => void): void {
    this.trayUpdater = updater;
    this.pushState();
  }

  showWindow(): void {
    if (!this.window || this.window.isDestroyed()) return;
    if (this.window.isMinimized()) this.window.restore();
    this.window.show();
    this.window.focus();
  }

  private focusForAttention(): void {
    this.showWindow();
    if (this.window && !this.window.isFocused()) {
      this.window.flashFrame(true);
    }
  }

  snapshot(): AppSnapshot {
    const connection: ConnectionInfo = {
      connected: Boolean(this.session),
      infoReceived: this.infoReceived,
      deviceId: this.session?.device?.id,
      deviceName: this.session?.device?.name,
      phoneModel: this.phoneInfo?.model,
      phoneVersion: this.phoneInfo?.androidVersion,
      appVersion: this.phoneInfo?.appVersion,
      batteryLevel: this.batteryLevel,
      batteryCharging: this.batteryCharging,
      capabilities: [...this.capabilities],
      subscriptions: [...this.subscriptions],
      since: this.connectedAt,
    };
    const pendingApproval = this.approvals.current;
    const pairingRequest: PairingRequestUi | undefined = pendingApproval
      ? {
          id: pendingApproval.context.id,
          name: pendingApproval.context.name,
          fingerprint: pendingApproval.context.fingerprint,
          platform: pendingApproval.context.platform,
          expiresAt: pendingApproval.deadline,
        }
      : undefined;
    return {
      version: app.getVersion(),
      server: {
        running: Boolean(this.server),
        port: this.server?.listeningPort ?? 0,
        addresses: getLanAddresses(),
        fingerprint: this.cert?.fingerprintHex ?? '',
        pcName: this.store.pcName,
        error: this.serverError,
      },
      networkCheck: this.networkCheck,
      connection,
      devices: this.store.publicDevices(),
      pairing: {
        offer: this.pairingOffer,
        request: pairingRequest,
        error: this.pairingError,
      },
      settings: this.store.settings,
      sms: this.sms.status(),
      notifications: this.notifications.status(),
      photos: {
        ...this.photos.status(),
        partial: this.capabilities.includes(CAPABILITIES.PHOTOS_PARTIAL),
      },
      calls: {
        ...this.calls.status(),
        answerSupported: this.capabilities.includes(CAPABILITIES.CALLS_ANSWER),
        speakerSupported: this.capabilities.includes(CAPABILITIES.CALLS_SPEAKER),
      },
      clipboard: this.clipboardService.status(),
      handoff: this.handoff.list(),
      mirror: {
        ...this.mirrorService.status(),
        inputEnabled: this.capabilities.includes(CAPABILITIES.MIRROR_INPUT),
      },
      updates: this.updates.statusSnapshot(),
      logs: this.logger.lines(120),
      lastError: this.lastError,
    };
  }

  private createServices(): void {
    const onChange = () => this.scheduleBroadcast();
    this.sms = new SmsService(this.logger, this.store, onChange);
    this.notifications = new NotificationService(
      this.store,
      this.logger,
      onChange,
      (title, body) => this.notify(title, body),
    );
    this.photos = new PhotoService(
      this.logger,
      this.streams,
      () => app.getPath('pictures'),
      (buffer) => {
        const image = nativeImage.createFromBuffer(buffer);
        if (!image.isEmpty()) clipboard.writeImage(image);
      },
      onChange,
    );
    this.calls = new CallService(
      this.logger,
      onChange,
      (state) => this.onIncomingCall(state),
    );
    this.clipboardService = new ClipboardService(
      () => clipboard.readText(),
      (text) => clipboard.writeText(text),
      this.logger,
      onChange,
      (title, body) => this.notify(title, body),
    );
    this.handoff = new HandoffService(
      this.streams,
      () => app.getPath('downloads'),
      (url) => shell.openExternal(url),
      this.logger,
      (title, body) => this.notify(title, body),
      onChange,
    );
    this.mirrorService = new MirrorService(
      this.logger,
      onChange,
      (frame) => this.sendMirrorFrame(frame),
    );
    const mode: UpdateMode = this.e2e
      ? 'disabled'
      : process.env.PORTABLE_EXECUTABLE_DIR || process.env.PORTABLE_EXECUTABLE_FILE
        ? 'portable'
        : app.isPackaged
          ? 'installed'
          : 'disabled';
    this.updates = new UpdateService({
      mode,
      currentVersion: app.getVersion(),
      autoCheck: this.store.settings.autoCheckUpdates,
      updatesDir: path.join(this.userDataDir, 'updates'),
      client: mode === 'installed' ? new GitHubUpdateClient() : null,
      logger: this.logger,
      onChange,
      notify: (title, body) => this.notify(title, body),
      install: (installer) => this.requestInstall(installer),
    });
  }

  private requestInstall(installerPath: string): void {
    this.pendingInstaller = installerPath;
    app.quit();
  }

  takePendingInstaller(): string | undefined {
    const installer = this.pendingInstaller;
    this.pendingInstaller = undefined;
    return installer;
  }

  private serverCallbacks(): ServerCallbacks {
    return {
      onPairingRequest: (context) => this.requestPairingApproval(context),
      onPaired: (device) => {
        this.pairingOffer = undefined;
        this.pairingError = undefined;
        this.logger.log('Pair', `Paired with ${device.name}`);
        this.notify('Phone paired', `${device.name} is now paired with this PC.`);
        this.scheduleBroadcast();
      },
      onAuthorized: (session) => this.handleAuthorized(session),
      onClosed: (session, reason) => this.handleClosed(session, reason),
      onPairingSocketClosed: (sessionId) => this.handlePairingSocketClosed(sessionId),
      onMessage: (session, envelope) => this.handleMessage(session, envelope),
      onBinary: (session, frame) => this.handleBinary(session, frame),
    };
  }

  private handleAuthorized(session: PeerSession): void {
    this.session = session;
    this.phoneInfo = undefined;
    this.infoReceived = false;
    this.capabilities = [];
    this.subscriptions = [];
    this.connectedAt = Date.now();
    this.lastError = undefined;
    this.sms.onConnected(session);
    this.notifications.onConnected(session);
    this.photos.onConnected(session);
    this.calls.onConnected(session);
    this.clipboardService.onConnected(session);
    this.handoff.onConnected(session);
    this.mirrorService.onConnected(session);
    this.logger.log('App', `Phone connected: ${session.device?.name ?? 'unknown'}`);
    this.notify('Phone connected', `${session.device?.name ?? 'Your phone'} is connected.`);
    this.focusForAttention();
    this.scheduleBroadcast();
  }

  private handleClosed(session: PeerSession, reason: string): void {
    // A pairing socket never authenticates, so it never reaches this callback —
    // handlePairingSocketClosed owns the pending prompt instead.
    if (session !== this.session) return;
    this.session = undefined;
    this.phoneInfo = undefined;
    this.infoReceived = false;
    this.batteryLevel = undefined;
    this.batteryCharging = undefined;
    this.capabilities = [];
    this.subscriptions = [];
    this.connectedAt = undefined;
    this.sms.onDisconnected();
    this.notifications.onDisconnected();
    this.photos.onDisconnected();
    this.calls.onDisconnected();
    this.clipboardService.onDisconnected();
    this.handoff.onDisconnected();
    this.mirrorService.onDisconnected();
    this.streams.rejectAll('Phone disconnected');
    this.logger.log('App', `Phone disconnected: ${reason}`);
    // A reconnect closes the old socket with "Replaced by a newer connection";
    // saying "disconnected" there just before "connected" is noise.
    if (!/replaced by a newer connection/i.test(reason)) {
      this.notify('Phone disconnected', reason || 'The connection closed.');
    }
    this.scheduleBroadcast();
  }

  private handleMessage(session: PeerSession, envelope: Envelope): void {
    if (session !== this.session) return;
    switch (envelope.type) {
      case TYPES.PHONE_INFO: {
        if (!envelope.data || typeof envelope.data !== 'object') return;
        this.phoneInfo = normalizePhoneInfo(envelope.data);
        this.infoReceived = true;
        this.capabilities = this.phoneInfo.capabilities;
        this.subscriptions = this.phoneInfo.subscriptions;
        this.photos.setCapabilities(this.capabilities);
        this.logger.log(
          'Phone',
          `${this.phoneInfo.model} / Android ${this.phoneInfo.androidVersion} ` +
            `(app ${this.phoneInfo.appVersion})`,
        );
        this.scheduleBroadcast();
        return;
      }
      case TYPES.SMS_CHANGED:
        this.sms.markDirty(session);
        return;
      case TYPES.NOTIF_POST:
        this.notifications.add(session, envelope.data as NotificationWire | undefined);
        return;
      case TYPES.NOTIF_REMOVED:
        this.notifications.remove(
          session,
          (envelope.data as NotificationRemovedWire | undefined)?.key,
        );
        return;
      case TYPES.NOTIF_CHANGED:
        this.notifications.markDirty(session);
        return;
      case TYPES.PHONE_BATTERY: {
        const battery = envelope.data as BatteryWire | undefined;
        if (typeof battery?.level !== 'number') return;
        this.batteryLevel = Math.max(0, Math.min(100, Math.round(battery.level)));
        this.batteryCharging = Boolean(battery.charging);
        this.scheduleBroadcast();
        return;
      }
      case TYPES.CALL_STATE:
        this.calls.onPhoneState(envelope.data as CallStateWire | undefined);
        return;
      case TYPES.CLIPBOARD_FROM_PHONE: {
        const data = envelope.data as { text?: string } | undefined;
        this.clipboardService.receiveFromPhone(data?.text);
        return;
      }
      case TYPES.HANDOFF_SHARE:
        this.handoff.onShare(session, envelope.data as HandoffShareWire | undefined);
        return;
      case TYPES.HANDOFF_FILE:
        this.handoff.onFileMeta(session, envelope.data as HandoffFileMetaWire | undefined);
        return;
      case TYPES.MIRROR_STATE:
        this.mirrorService.onPhoneState(
          envelope.data as { active: boolean; width: number; height: number; message?: string } | undefined,
        );
        return;
      case TYPES.PONG:
      case TYPES.PHOTO_STREAM_END:
      case TYPES.HANDOFF_FILE_END:
        return;
      default:
        this.logger.log('Protocol', `Unknown phone event: ${envelope.type}`);
    }
  }

  private handleBinary(session: PeerSession, frame: BinaryFrame): void {
    if (session !== this.session) return;
    if (frame.type === 2 || frame.type === 4) {
      this.mirrorService.onBinaryFrame(frame);
      return;
    }
    if (this.streams.add(frame) === 'dropped') this.logDroppedFrame(frame);
  }

  /** A dropped frame used to be silent, which made a stalled transfer opaque. */
  private logDroppedFrame(frame: BinaryFrame): void {
    if (this.droppedStreams.has(frame.streamId)) return;
    this.droppedStreams.add(frame.streamId);
    if (this.droppedStreams.size > 64) {
      const oldest = this.droppedStreams.values().next().value;
      if (oldest !== undefined) this.droppedStreams.delete(oldest);
    }
    this.logger.log(
      'Stream',
      `Dropped a binary frame for unknown stream ${frame.streamId} (type ${frame.type})`,
    );
  }

  private sendMirrorFrame(frame: BinaryFrame): void {
    if (!this.window || this.window.isDestroyed()) return;
    this.window.webContents.send('mirror:frame', {
      type: frame.type,
      seq: frame.seq,
      flags: frame.flags,
      data: frame.payload,
    });
  }

  async createPairingOffer(): Promise<void> {
    if (this.e2e) return;
    const server = this.requireServer();
    const addresses = getLanAddresses();
    const host = addresses[0];
    if (!host) {
      this.pairingError =
        'No local network address found. Connect the PC to Wi-Fi or Ethernet.';
      this.scheduleBroadcast();
      throw new Error(this.pairingError);
    }
    const session = this.pairings.create();
    const uri = buildPairingUri({
      host,
      port: server.listeningPort,
      fingerprint: this.cert.fingerprintHex,
      token: session.token,
      pcName: this.store.pcName,
    });
    const qrDataUrl = await QRCode.toDataURL(uri, {
      errorCorrectionLevel: 'M',
      margin: 1,
      width: 360,
      color: { dark: '#0b0f19', light: '#ffffff' },
    });
    this.pairingOffer = {
      uri,
      qrDataUrl,
      token: session.token,
      host,
      port: server.listeningPort,
      fingerprint: this.cert.fingerprintHex,
      expiresAt: session.expiresAt,
    };
    this.pairingError = undefined;
    this.logger.log('Pair', `Created a pairing code for ${host}:${server.listeningPort}`);
    this.scheduleBroadcast();
  }

  cancelPairing(): void {
    // Without this the prompt outlives the code it belongs to, and the next scan
    // is answered "declined" because the stale entry is still sitting there.
    this.approvals.cancel();
    this.pairings.clear();
    this.pairingOffer = undefined;
    this.scheduleBroadcast();
  }

  private requestPairingApproval(context: PairingRequestContext): Promise<PairingDecision> {
    const decision = this.approvals.request(context);
    this.pairingError = undefined;
    this.logger.log('Pair', `Approval requested by ${context.name} (${context.remoteAddress})`);
    this.focusForAttention();
    return decision;
  }

  approvePairing(id: string): void {
    this.approvals.approve(id);
  }

  rejectPairing(id: string): void {
    this.approvals.reject(id);
  }

  private handlePairingSocketClosed(sessionId: string): void {
    this.approvals.socketClosed(sessionId);
  }

  removeDevice(id: string): void {
    this.store.removeDevice(id);
    const active = this.server?.activeSession;
    if (active?.device?.id === id) {
      active.close(4003, 'Device removed from the PC');
    }
    this.logger.log('Pair', 'Removed a paired device');
    this.scheduleBroadcast();
  }

  updateSettings(patch: Partial<Settings>): void {
    const sanitized: Partial<Settings> = {};
    if (typeof patch.notificationsToasts === 'boolean') {
      sanitized.notificationsToasts = patch.notificationsToasts;
    }
    if (typeof patch.autoClipboard === 'boolean') {
      sanitized.autoClipboard = patch.autoClipboard;
    }
    if (typeof patch.launchAtLogin === 'boolean') {
      sanitized.launchAtLogin = patch.launchAtLogin;
      try {
        app.setLoginItemSettings({ openAtLogin: patch.launchAtLogin });
      } catch (error) {
        this.logger.log(
          'App',
          `Could not update login item: ${error instanceof Error ? error.message : String(error)}`,
        );
      }
    }
    if (typeof patch.autoCheckUpdates === 'boolean') {
      sanitized.autoCheckUpdates = patch.autoCheckUpdates;
      this.updates.setAutoCheck(patch.autoCheckUpdates);
    }
    this.store.updateSettings(sanitized);
    this.scheduleBroadcast();
  }

  setPackageMuted(pkg: string, muted: boolean): void {
    if (!pkg || pkg.length > 200) throw new Error('Invalid app package');
    this.notifications.setMuted(pkg, Boolean(muted));
  }

  async refreshSms(): Promise<void> {
    await this.sms.refresh();
  }

  async sendSms(address: string, body: string, subId?: number | null): Promise<void> {
    await this.sms.send(address, body, subId);
  }

  markSmsRead(address: string): void {
    this.sms.markThreadRead(address);
  }

  markAllSmsRead(): void {
    this.sms.markAllRead();
  }

  async openSmsThreadOnPhone(address: string): Promise<void> {
    await this.sms.openThreadOnPhone(address);
  }

  async dismissNotification(key: string): Promise<void> {
    await this.notifications.dismiss(key);
  }

  async refreshNotifications(): Promise<void> {
    await this.notifications.refresh();
  }

  async refreshPhotos(): Promise<void> {
    await this.photos.refresh();
  }

  async refreshPhotoAlbums(): Promise<void> {
    await this.photos.refreshAlbums();
  }

  async selectPhotoAlbum(bucket: string | null): Promise<void> {
    await this.photos.selectAlbum(bucket);
  }

  async loadMorePhotos(): Promise<void> {
    await this.photos.loadMore();
  }

  async getThumbnail(id: string): Promise<string | null> {
    return this.photos.thumbnail(id);
  }

  async getPhotoPreview(id: string): Promise<string> {
    return this.photos.preview(id);
  }

  async savePhoto(id: string): Promise<string> {
    const saved = await this.photos.save(id);
    this.notify('Photo saved', saved);
    return saved;
  }

  async copyPhoto(id: string): Promise<void> {
    await this.photos.copy(id);
    this.notify('Photo copied', 'The image is on your clipboard.');
  }

  async dial(number: string, subId?: number | null): Promise<void> {
    await this.calls.dial(number, subId);
  }

  async refreshContacts(): Promise<void> {
    await this.calls.refreshContacts();
  }

  async answerCall(): Promise<void> {
    await this.calls.answer();
  }

  async declineCall(): Promise<void> {
    await this.calls.decline();
  }

  async setCallSpeaker(enabled: boolean): Promise<void> {
    await this.calls.setSpeaker(Boolean(enabled));
  }

  async sendClipboard(text: string): Promise<void> {
    await this.clipboardService.sendText(text);
  }

  async sendPcClipboard(): Promise<void> {
    await this.clipboardService.sendPcClipboard();
    this.notify('Clipboard sent', 'Text was sent to your phone.');
  }

  async openUrl(url: string): Promise<void> {
    await this.handoff.openUrl(url);
  }

  async openOnPhone(url: string): Promise<void> {
    await this.handoff.openOnPhone(url);
  }

  async startMirror(codec: MirrorCodec = 'jpeg', fps = 30): Promise<void> {
    await this.mirrorService.start(codec, fps);
  }

  async stopMirror(): Promise<void> {
    await this.mirrorService.stop();
  }

  async mirrorTouch(touch: MirrorTouchWire): Promise<void> {
    await this.mirrorService.touch(touch);
  }

  async requestMirrorKeyframe(): Promise<void> {
    await this.mirrorService.requestKeyframe();
  }

  async resetIdentity(): Promise<void> {
    this.logger.log('App', 'Resetting PC identity');
    this.approvals.cancel();
    this.pairingOffer = undefined;
    this.pairings.clear();
    const server = this.server;
    this.server = undefined;
    this.session = undefined;
    if (server) await server.stop();
    this.discovery?.stop();
    this.discovery = undefined;
    this.sms?.onDisconnected();
    this.notifications?.onDisconnected();
    this.photos?.onDisconnected();
    this.calls?.onDisconnected();
    this.clipboardService?.onDisconnected();
    this.handoff?.onDisconnected();
    this.mirrorService?.onDisconnected();
    this.streams.rejectAll('PC identity reset');
    this.store.resetIdentity();
    fs.rmSync(path.join(this.certDir, 'pc-key.pem'), { force: true });
    fs.rmSync(path.join(this.certDir, 'pc-cert.pem'), { force: true });
    this.cert = ensureCertificate(this.certDir);
    try {
      const nextServer = new LinkServer({
        certificate: this.cert,
        store: this.store,
        pairings: this.pairings,
        logger: this.logger,
        callbacks: this.serverCallbacks(),
      });
      this.server = nextServer;
      const port = await nextServer.start(
        this.e2e ? 0 : 48921,
        this.e2e ? '127.0.0.1' : '0.0.0.0',
      );
      this.server = nextServer;
      if (!this.e2e) {
        this.discovery = new DiscoveryPublisher(this.logger);
        this.discovery.start(port, this.store.pcId, this.cert.fingerprintHex);
        await this.createPairingOffer();
      }
      this.serverError = undefined;
    } catch (error) {
      this.serverError =
        error instanceof Error ? error.message : 'Could not restart after reset';
      this.lastError = this.serverError;
      this.logger.log('App', `Identity reset restart failed: ${this.serverError}`);
    }
    this.scheduleBroadcast();
  }

  /**
   * On-demand only: nothing runs on a timer, and nothing runs at start. The
   * check spawns PowerShell, which this app otherwise never does, so it waits to
   * be asked.
   */
  async checkNetwork(): Promise<void> {
    // Two clicks must not spawn two PowerShell pairs; the second joins the first.
    if (this.networkCheckTask) return this.networkCheckTask;
    this.networkCheckTask = this.performNetworkCheck().finally(() => {
      this.networkCheckTask = undefined;
    });
    return this.networkCheckTask;
  }

  private async performNetworkCheck(): Promise<void> {
    const peers = this.server?.inboundPeers;
    const result = await runNetworkCheck({
      port: this.server?.listeningPort ?? 0,
      serverRunning: Boolean(this.server),
      inboundPeers: peers?.addresses ?? [],
      lastInboundAt: peers?.lastAt,
      localAddresses: getLanAddresses(),
    });
    this.networkCheck = result;
    // The verdict only: the addresses are already in the Server log.
    this.logger.log('Net', `Network check: ${result.verdict}`);
    this.scheduleBroadcast();
  }

  async showFile(filePath: string): Promise<void> {
    const allowed = [
      path.join(app.getPath('pictures'), 'LinkPhone'),
      path.join(app.getPath('downloads'), 'LinkPhone'),
    ].map((directory) => `${path.resolve(directory)}${path.sep}`);
    const resolved = path.resolve(filePath);
    if (!allowed.some((prefix) => resolved.startsWith(prefix))) {
      throw new Error('File is outside the LinkPhone folders');
    }
    shell.showItemInFolder(resolved);
  }

  async openLogs(): Promise<void> {
    const directory = path.join(this.userDataDir, 'logs');
    fs.mkdirSync(directory, { recursive: true });
    await shell.openPath(directory);
  }

  async openDocs(): Promise<void> {
    await shell.openExternal('https://github.com/FakeAbid11/LinkPhone#readme');
  }

  async checkForUpdates(): Promise<void> {
    await this.updates.check();
  }

  async downloadUpdate(): Promise<void> {
    await this.updates.download();
  }

  installUpdate(): void {
    this.updates.install();
  }

  async openReleasePage(): Promise<void> {
    await shell.openExternal('https://github.com/FakeAbid11/LinkPhone/releases/latest');
  }

  getSnapshot(): AppSnapshot {
    return this.snapshot();
  }

  private requireServer(): LinkServer {
    if (!this.server) {
      throw new Error(this.serverError ?? 'The LinkPhone server is not running');
    }
    return this.server;
  }

  private onIncomingCall(state: CallStateWire): void {
    const who = state.contactName || state.number || 'Your phone is ringing';
    this.notify('Incoming call', `${who} — answer from LinkPhone or on your phone.`);
    this.focusForAttention();
  }

  private notify(title: string, body: string): void {
    const safeBody = body.replace(/\s+/g, ' ').slice(0, 300);
    if (this.window && !this.window.isDestroyed()) {
      this.window.webContents.send('toast', { kind: 'info', message: `${title}: ${safeBody}` });
    }
    if (this.e2e) return;
    if (this.window && !this.window.isDestroyed() && this.window.isFocused()) return;
    if (!Notification.isSupported()) return;
    try {
      const notification = new Notification({
        title,
        body: safeBody,
        silent: false,
      });
      notification.on('click', () => this.showWindow());
      notification.show();
    } catch (error) {
      this.logger.log('Notify', error instanceof Error ? error.message : String(error));
    }
  }

  private scheduleBroadcast(): void {
    if (this.broadcastTimer) return;
    this.broadcastTimer = setTimeout(() => {
      this.broadcastTimer = undefined;
      this.pushState();
    }, 120);
  }

  private pushState(): void {
    // Build the snapshot once, even with the window closed, so the tray keeps
    // reflecting the connection state while LinkPhone is minimised.
    const snapshot = this.snapshot();
    if (this.window && !this.window.isDestroyed()) {
      this.window.webContents.send('state', snapshot);
    }
    this.trayUpdater?.(snapshot);
  }

  async stop(): Promise<void> {
    this.updates.stop();
    if (this.autoClipboardTimer) clearInterval(this.autoClipboardTimer);
    this.autoClipboardTimer = undefined;
    if (this.broadcastTimer) clearTimeout(this.broadcastTimer);
    this.broadcastTimer = undefined;
    this.approvals.cancel();
    this.discovery?.stop();
    this.discovery = undefined;
    const server = this.server;
    this.server = undefined;
    if (server) await server.stop();
    this.logger.log('App', 'Stopped');
  }
}
