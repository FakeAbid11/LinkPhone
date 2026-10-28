export const PROTOCOL_VERSION = 1;

export const TYPES = {
  HELLO: 'hello',
  AUTH_CHALLENGE: 'auth.challenge',
  AUTH_RESPONSE: 'auth.response',
  AUTH_OK: 'auth.ok',
  AUTH_ERROR: 'auth.error',
  PAIR_REQUEST: 'pair.request',
  PAIR_PENDING: 'pair.pending',
  PAIR_OK: 'pair.ok',
  PAIR_REJECTED: 'pair.rejected',
  PHONE_INFO: 'phone.info',
  SMS_LIST: 'sms.list',
  SMS_SEND: 'sms.send',
  SMS_OPEN_THREAD: 'sms.openThread',
  SMS_CHANGED: 'sms.changed',
  NOTIF_POST: 'notif.post',
  NOTIF_REMOVED: 'notif.removed',
  NOTIF_DISMISS: 'notif.dismiss',
  PHOTO_LIST: 'photo.list',
  PHOTO_FETCH: 'photo.fetch',
  PHOTO_STREAM_END: 'photo.streamEnd',
  CALL_STATE: 'call.state',
  CALL_DIAL: 'call.dial',
  CALL_ANSWER: 'call.answer',
  CALL_DECLINE: 'call.decline',
  CLIPBOARD_SET: 'clipboard.set',
  CLIPBOARD_FROM_PHONE: 'clipboard.fromPhone',
  HANDOFF_SHARE: 'handoff.share',
  HANDOFF_FILE: 'handoff.file',
  HANDOFF_FILE_END: 'handoff.fileEnd',
  HANDOFF_OPEN: 'handoff.open',
  MIRROR_START: 'mirror.start',
  MIRROR_STOP: 'mirror.stop',
  MIRROR_STATE: 'mirror.state',
  MIRROR_TOUCH: 'mirror.touch',
  PING: 'ping',
  PONG: 'pong',
} as const;

export const ERROR_CODES = {
  BAD_REQUEST: 'BAD_REQUEST',
  PERMISSION_DENIED: 'PERMISSION_DENIED',
  UNKNOWN_DEVICE: 'UNKNOWN_DEVICE',
  PAIRING_REQUIRED: 'PAIRING_REQUIRED',
  PAIRING_REJECTED: 'PAIRING_REJECTED',
  AUTH_FAILED: 'AUTH_FAILED',
  BUSY: 'BUSY',
  UNSUPPORTED: 'UNSUPPORTED',
  NOT_FOUND: 'NOT_FOUND',
  TOO_LARGE: 'TOO_LARGE',
  INTERNAL: 'INTERNAL',
} as const;

export const CAPABILITIES = {
  SMS: 'sms',
  SMS_OPEN_THREAD: 'sms.openThread',
  NOTIFICATIONS: 'notifications',
  PHOTOS: 'photos',
  PHOTOS_PARTIAL: 'photos.partial',
  CALLS: 'calls',
  CALLS_ANSWER: 'calls.answer',
  CALLS_CALLER_ID: 'calls.callerId',
  CALLS_SIM_SELECTION: 'calls.simSelection',
  MIRROR: 'mirror',
  MIRROR_INPUT: 'mirror.input',
  CLIPBOARD: 'clipboard',
  HANDOFF: 'handoff',
} as const;

export interface WireError {
  code: string;
  message: string;
}

export interface Envelope<T = unknown> {
  v: number;
  id: string;
  type: string;
  replyTo?: string;
  ok?: boolean;
  data?: T;
  error?: WireError;
  ts: number;
}

export interface HelloWire {
  deviceId: string;
  phoneName: string;
  platform: string;
  appVersion: string;
  publicKeySpki: string;
  protocol: number;
}

export interface PairRequestWire {
  token: string;
  deviceId: string;
  name: string;
  platform: string;
  appVersion: string;
  publicKeySpki: string;
}

export interface PairOkWire {
  pcId: string;
  pcName: string;
}

export interface SmsMessageWire {
  id: string;
  address: string;
  body: string;
  date: number;
  type: number;
  read: boolean;
  subId?: number | null;
}

export interface SmsListResultWire {
  messages: SmsMessageWire[];
}

export interface SmsSendResultWire {
  accepted: boolean;
  parts: number;
  subId?: number | null;
}

export interface NotificationWire {
  key: string;
  packageName: string;
  appName: string;
  title?: string | null;
  text?: string | null;
  subText?: string | null;
  category?: string | null;
  ongoing: boolean;
  time: number;
}

export interface NotificationRemovedWire {
  key: string;
}

export interface PhotoWire {
  id: string;
  name: string;
  mime: string;
  size: number;
  date: number;
  width: number;
  height: number;
  bucket?: string | null;
}

export interface PhotoListResultWire {
  photos: PhotoWire[];
}

export interface PhotoStreamMetaWire {
  streamId: number;
  photoId: string;
  kind: string;
  mime: string;
  size: number;
}

export interface CallStateWire {
  state: string;
  number?: string | null;
  contactName?: string | null;
  timestamp: number;
}

export interface ClipboardFromPhoneWire {
  text: string;
}

export interface HandoffShareWire {
  kind: string;
  text?: string | null;
  url?: string | null;
}

export interface HandoffFileMetaWire {
  streamId: number;
  name: string;
  mime: string;
  size: number;
}

export interface MirrorStateWire {
  active: boolean;
  width: number;
  height: number;
  message?: string | null;
}

export interface MirrorTouchWire {
  action: string;
  x: number;
  y: number;
  x2?: number | null;
  y2?: number | null;
  durationMs: number;
}

export interface SimInfoWire {
  subscriptionId: number;
  label: string;
  slotIndex: number;
}

export interface PhoneInfoWire {
  model: string;
  androidVersion: string;
  appVersion: string;
  capabilities: string[];
  subscriptions: SimInfoWire[];
}

export interface StreamEndWire {
  streamId: number;
}

export interface OkResultWire {
  ok: boolean;
}

export interface SmsChangedWire {
  timestamp: number;
}

export interface PairedDeviceInfo {
  id: string;
  name: string;
  platform: string;
  fingerprint: string;
  pairedAt: number;
  lastSeen?: number;
}

export interface Settings {
  notificationsToasts: boolean;
  autoClipboard: boolean;
  launchAtLogin: boolean;
  mutePackages: string[];
}

export interface SmsMessageUi extends SmsMessageWire {
  local?: boolean;
  pending?: boolean;
  failed?: boolean;
}

export interface SmsThreadUi {
  address: string;
  displayName: string;
  latest: number;
  unread: number;
  messages: SmsMessageUi[];
}

export interface NotificationUi {
  key: string;
  packageName: string;
  appName: string;
  title?: string | null;
  text?: string | null;
  subText?: string | null;
  category?: string | null;
  ongoing: boolean;
  time: number;
}

export interface PhotoUi extends PhotoWire {}

export interface HandoffUi {
  id: string;
  kind: 'text' | 'url' | 'image' | 'file';
  text?: string;
  url?: string;
  fileName?: string;
  mime?: string;
  savedPath?: string;
  time: number;
}

export interface PairingOffer {
  uri: string;
  qrDataUrl: string;
  token: string;
  host: string;
  port: number;
  fingerprint: string;
  expiresAt: number;
}

export interface PairingRequestUi {
  id: string;
  name: string;
  fingerprint: string;
  platform: string;
}

export interface ServerInfo {
  running: boolean;
  port: number;
  addresses: string[];
  fingerprint: string;
  pcName: string;
  error?: string;
}

export interface ConnectionInfo {
  connected: boolean;
  deviceId?: string;
  deviceName?: string;
  phoneModel?: string;
  phoneVersion?: string;
  appVersion?: string;
  capabilities: string[];
  subscriptions: SimInfoWire[];
  since?: number;
}

export interface AppSnapshot {
  version: string;
  server: ServerInfo;
  connection: ConnectionInfo;
  devices: PairedDeviceInfo[];
  pairing: {
    offer?: PairingOffer;
    request?: PairingRequestUi;
    error?: string;
  };
  settings: Settings;
  sms: {
    threads: SmsThreadUi[];
    loading: boolean;
    error?: string;
    refreshing: boolean;
  };
  notifications: NotificationUi[];
  photos: {
    items: PhotoUi[];
    loading: boolean;
    error?: string;
    partial: boolean;
  };
  calls: {
    state: CallStateWire;
    error?: string;
    answerSupported: boolean;
  };
  clipboard: {
    lastReceived?: string;
    lastSentAt?: number;
  };
  handoff: HandoffUi[];
  mirror: {
    state: MirrorStateWire;
    inputEnabled: boolean;
    error?: string;
  };
  logs: string[];
  lastError?: string;
}

export interface LinkPhoneApi {
  getState(): Promise<AppSnapshot>;
  onState(listener: (state: AppSnapshot) => void): () => void;
  onMirrorFrame(listener: (frame: { seq: number; data: Uint8Array }) => void): () => void;
  onToast(listener: (toast: { kind: string; message: string }) => void): () => void;
  createPairing(): Promise<void>;
  cancelPairing(): Promise<void>;
  approvePairing(id: string): Promise<void>;
  rejectPairing(id: string): Promise<void>;
  removeDevice(id: string): Promise<void>;
  resetIdentity(): Promise<void>;
  updateSettings(patch: Partial<Settings>): Promise<void>;
  refreshSms(): Promise<void>;
  sendSms(address: string, body: string, subId?: number | null): Promise<void>;
  markSmsRead(address: string): Promise<void>;
  markAllSmsRead(): Promise<void>;
  openSmsThreadOnPhone(address: string): Promise<void>;
  dismissNotification(key: string): Promise<void>;
  setPackageMuted(pkg: string, muted: boolean): Promise<void>;
  refreshPhotos(): Promise<void>;
  getThumbnail(id: string): Promise<string | null>;
  savePhoto(id: string): Promise<string>;
  copyPhoto(id: string): Promise<void>;
  dial(number: string, subId?: number | null): Promise<void>;
  answerCall(): Promise<void>;
  declineCall(): Promise<void>;
  sendClipboard(text: string): Promise<void>;
  sendPcClipboard(): Promise<void>;
  openUrl(url: string): Promise<void>;
  openOnPhone(url: string): Promise<void>;
  showFile(path: string): Promise<void>;
  startMirror(): Promise<void>;
  stopMirror(): Promise<void>;
  mirrorTouch(touch: MirrorTouchWire): Promise<void>;
  openLogs(): Promise<void>;
  openDocs(): Promise<void>;
  refresh(): Promise<void>;
}
