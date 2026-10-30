import { contextBridge, ipcRenderer } from 'electron';
import type { IpcRendererEvent } from 'electron';
import type {
  AppSnapshot,
  LinkPhoneApi,
  MirrorFrameWire,
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
      frame: MirrorFrameWire,
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
  markSmsRead: (address) => ipcRenderer.invoke('sms:markRead', address),
  markAllSmsRead: () => ipcRenderer.invoke('sms:markAllRead'),
  openSmsThreadOnPhone: (address) => ipcRenderer.invoke('sms:openOnPhone', address),
  dismissNotification: (key) => ipcRenderer.invoke('notif:dismiss', key),
  refreshNotifications: () => ipcRenderer.invoke('notif:refresh'),
  setPackageMuted: (pkg, muted) => ipcRenderer.invoke('notif:mute', pkg, muted),
  refreshPhotos: () => ipcRenderer.invoke('photos:refresh'),
  refreshPhotoAlbums: () => ipcRenderer.invoke('photos:albums'),
  selectPhotoAlbum: (bucket) => ipcRenderer.invoke('photos:album', bucket),
  loadMorePhotos: () => ipcRenderer.invoke('photos:more'),
  getThumbnail: (id) => ipcRenderer.invoke('photos:thumb', id) as Promise<string | null>,
  getPhotoPreview: (id) => ipcRenderer.invoke('photos:preview', id) as Promise<string>,
  savePhoto: (id) => ipcRenderer.invoke('photos:save', id) as Promise<string>,
  copyPhoto: (id) => ipcRenderer.invoke('photos:copy', id),
  dial: (number, subId) => ipcRenderer.invoke('call:dial', number, subId),
  refreshContacts: () => ipcRenderer.invoke('contacts:refresh'),
  answerCall: () => ipcRenderer.invoke('call:answer'),
  declineCall: () => ipcRenderer.invoke('call:decline'),
  setCallSpeaker: (enabled) => ipcRenderer.invoke('call:speaker', enabled),
  sendClipboard: (text) => ipcRenderer.invoke('clipboard:send', text),
  sendPcClipboard: () => ipcRenderer.invoke('clipboard:sendPc'),
  openUrl: (url) => ipcRenderer.invoke('handoff:open', url),
  openOnPhone: (url) => ipcRenderer.invoke('handoff:openOnPhone', url),
  showFile: (filePath) => ipcRenderer.invoke('file:show', filePath),
  startMirror: (codec, fps) => ipcRenderer.invoke('mirror:start', codec, fps),
  stopMirror: () => ipcRenderer.invoke('mirror:stop'),
  mirrorTouch: (touch: MirrorTouchWire) => ipcRenderer.invoke('mirror:touch', touch),
  requestMirrorKeyframe: () => ipcRenderer.invoke('mirror:keyframe'),
  openLogs: () => ipcRenderer.invoke('app:openLogs'),
  openDocs: () => ipcRenderer.invoke('app:openDocs'),
  checkForUpdates: () => ipcRenderer.invoke('updater:check'),
  downloadUpdate: () => ipcRenderer.invoke('updater:download'),
  installUpdate: () => ipcRenderer.invoke('updater:install'),
  openReleasePage: () => ipcRenderer.invoke('updater:openRelease'),
  refresh: () => ipcRenderer.invoke('app:refresh') as Promise<AppSnapshot>,
};

contextBridge.exposeInMainWorld('linkphone', api);
