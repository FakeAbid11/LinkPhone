import { contextBridge, ipcRenderer } from 'electron';
import type { IpcRendererEvent } from 'electron';
import type {
  AppSnapshot,
  LinkPhoneApi,
  MirrorTouchWire,
  Settings,
} from '../shared/types';

const api: LinkPhoneApi = {
  getState: () => ipcRenderer.invoke('state') as Promise<AppSnapshot>,
  onState: (listener) => {
    const handler = (_event: IpcRendererEvent, state: AppSnapshot) => listener(state);
    ipcRenderer.on('state', handler);
    return () => ipcRenderer.removeListener('state', handler);
  },
  onMirrorFrame: (listener) => {
    const handler = (
      _event: IpcRendererEvent,
      frame: { seq: number; data: Uint8Array },
    ) => listener(frame);
    ipcRenderer.on('mirror:frame', handler);
    return () => ipcRenderer.removeListener('mirror:frame', handler);
  },
  onToast: (listener) => {
    const handler = (
      _event: IpcRendererEvent,
      toast: { kind: string; message: string },
    ) => listener(toast);
    ipcRenderer.on('toast', handler);
    return () => ipcRenderer.removeListener('toast', handler);
  },
  createPairing: () => ipcRenderer.invoke('pairing:create'),
  cancelPairing: () => ipcRenderer.invoke('pairing:cancel'),
  approvePairing: (id) => ipcRenderer.invoke('pairing:approve', id),
  rejectPairing: (id) => ipcRenderer.invoke('pairing:reject', id),
  removeDevice: (id) => ipcRenderer.invoke('device:remove', id),
  resetIdentity: () => ipcRenderer.invoke('identity:reset'),
  updateSettings: (patch: Partial<Settings>) => ipcRenderer.invoke('settings:update', patch),
  refreshSms: () => ipcRenderer.invoke('sms:refresh'),
  sendSms: (address, body, subId) => ipcRenderer.invoke('sms:send', address, body, subId),
  dismissNotification: (key) => ipcRenderer.invoke('notif:dismiss', key),
  setPackageMuted: (pkg, muted) => ipcRenderer.invoke('notif:mute', pkg, muted),
  refreshPhotos: () => ipcRenderer.invoke('photos:refresh'),
  getThumbnail: (id) => ipcRenderer.invoke('photos:thumb', id) as Promise<string | null>,
  savePhoto: (id) => ipcRenderer.invoke('photos:save', id) as Promise<string>,
  copyPhoto: (id) => ipcRenderer.invoke('photos:copy', id),
  dial: (number, subId) => ipcRenderer.invoke('call:dial', number, subId),
  answerCall: () => ipcRenderer.invoke('call:answer'),
  declineCall: () => ipcRenderer.invoke('call:decline'),
  sendClipboard: (text) => ipcRenderer.invoke('clipboard:send', text),
  sendPcClipboard: () => ipcRenderer.invoke('clipboard:sendPc'),
  openUrl: (url) => ipcRenderer.invoke('handoff:open', url),
  openOnPhone: (url) => ipcRenderer.invoke('handoff:openOnPhone', url),
  showFile: (filePath) => ipcRenderer.invoke('file:show', filePath),
  startMirror: () => ipcRenderer.invoke('mirror:start'),
  stopMirror: () => ipcRenderer.invoke('mirror:stop'),
  mirrorTouch: (touch: MirrorTouchWire) => ipcRenderer.invoke('mirror:touch', touch),
  openLogs: () => ipcRenderer.invoke('app:openLogs'),
  openDocs: () => ipcRenderer.invoke('app:openDocs'),
  refresh: () => ipcRenderer.invoke('app:refresh'),
};

contextBridge.exposeInMainWorld('linkphone', api);
