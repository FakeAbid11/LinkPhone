import { ipcMain } from 'electron';
import type { AppController } from './app';
import type { MirrorCodec, MirrorTouchWire, Settings } from '../shared/types';

function requireString(value: unknown, name: string, max = 1_000): string {
  if (typeof value !== 'string' || !value.trim() || value.length > max) {
    throw new Error(`Invalid ${name}`);
  }
  return value;
}

/** A normalized 0..1 coordinate on the mirrored screen. */
function requireFraction(value: unknown, name: string): number {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 1) {
    throw new Error(`Invalid ${name}`);
  }
  return value;
}

export function registerIpc(controller: AppController): void {
  ipcMain.handle('state', () => controller.getSnapshot());

  ipcMain.handle('pairing:create', () => controller.createPairingOffer());
  ipcMain.handle('pairing:cancel', () => controller.cancelPairing());
  ipcMain.handle('pairing:approve', (_event, id: unknown) =>
    controller.approvePairing(requireString(id, 'request id', 128)),
  );
  ipcMain.handle('pairing:reject', (_event, id: unknown) =>
    controller.rejectPairing(requireString(id, 'request id', 128)),
  );

  ipcMain.handle('device:remove', (_event, id: unknown) =>
    controller.removeDevice(requireString(id, 'device id', 128)),
  );
  ipcMain.handle('identity:reset', () => controller.resetIdentity());
  ipcMain.handle('check:network', () => controller.checkNetwork());

  ipcMain.handle('settings:update', (_event, patch: unknown) => {
    if (!patch || typeof patch !== 'object') throw new Error('Invalid settings');
    controller.updateSettings(patch as Partial<Settings>);
  });

  ipcMain.handle('sms:refresh', () => controller.refreshSms());
  ipcMain.handle('sms:send', (_event, address: unknown, body: unknown, subId: unknown) =>
    controller.sendSms(
      requireString(address, 'phone number', 40),
      requireString(body, 'message', 5_000),
      typeof subId === 'number' ? subId : null,
    ),
  );
  ipcMain.handle('sms:markRead', (_event, address: unknown) =>
    controller.markSmsRead(requireString(address, 'conversation address', 64)),
  );
  ipcMain.handle('sms:markAllRead', () => controller.markAllSmsRead());
  ipcMain.handle('sms:openOnPhone', (_event, address: unknown) =>
    controller.openSmsThreadOnPhone(
      requireString(address, 'conversation address', 64),
    ),
  );

  ipcMain.handle('notif:dismiss', (_event, key: unknown) =>
    controller.dismissNotification(requireString(key, 'notification key', 512)),
  );
  ipcMain.handle('notif:mute', (_event, pkg: unknown, muted: unknown) =>
    controller.setPackageMuted(requireString(pkg, 'package', 200), Boolean(muted)),
  );
  ipcMain.handle('notif:refresh', () => controller.refreshNotifications());

  ipcMain.handle('photos:refresh', () => controller.refreshPhotos());
  ipcMain.handle('photos:albums', () => controller.refreshPhotoAlbums());
  ipcMain.handle('photos:album', (_event, bucket: unknown) =>
    controller.selectPhotoAlbum(
      bucket == null ? null : requireString(bucket, 'photo album', 120),
    ),
  );
  ipcMain.handle('photos:more', () => controller.loadMorePhotos());
  ipcMain.handle('photos:thumb', (_event, id: unknown) =>
    controller.getThumbnail(requireString(id, 'photo id', 80)),
  );
  ipcMain.handle('photos:preview', (_event, id: unknown) =>
    controller.getPhotoPreview(requireString(id, 'photo id', 80)),
  );
  ipcMain.handle('photos:save', (_event, id: unknown) =>
    controller.savePhoto(requireString(id, 'photo id', 80)),
  );
  ipcMain.handle('photos:copy', (_event, id: unknown) =>
    controller.copyPhoto(requireString(id, 'photo id', 80)),
  );

  ipcMain.handle('call:dial', (_event, number: unknown, subId: unknown) => {
    const sim =
      typeof subId === 'number' && Number.isInteger(subId) && subId > 0 ? subId : null;
    return controller.dial(requireString(number, 'phone number', 30), sim);
  });
  ipcMain.handle('call:answer', () => controller.answerCall());
  ipcMain.handle('call:decline', () => controller.declineCall());
  ipcMain.handle('call:speaker', (_event, enabled: unknown) => {
    if (typeof enabled !== 'boolean') throw new Error('Invalid speaker state');
    return controller.setCallSpeaker(enabled);
  });
  ipcMain.handle('contacts:refresh', () => controller.refreshContacts());

  ipcMain.handle('clipboard:send', (_event, text: unknown) =>
    controller.sendClipboard(requireString(text, 'clipboard text', 32_000)),
  );
  ipcMain.handle('clipboard:sendPc', () => controller.sendPcClipboard());

  ipcMain.handle('handoff:open', (_event, url: unknown) =>
    controller.openUrl(requireString(url, 'url', 4_096)),
  );
  ipcMain.handle('handoff:openOnPhone', (_event, url: unknown) =>
    controller.openOnPhone(requireString(url, 'url', 4_096)),
  );
  ipcMain.handle('file:show', (_event, filePath: unknown) =>
    controller.showFile(requireString(filePath, 'file path', 1_024)),
  );

  ipcMain.handle('mirror:start', (_event, codec: unknown, fps: unknown) => {
    const value: MirrorCodec = codec === 'h264' ? 'h264' : 'jpeg';
    return controller.startMirror(value, fps === 60 ? 60 : 30);
  });
  ipcMain.handle('mirror:stop', () => controller.stopMirror());
  ipcMain.handle('mirror:keyframe', () => controller.requestMirrorKeyframe());
  ipcMain.handle('mirror:touch', (_event, touch: unknown) => {
    if (!touch || typeof touch !== 'object') throw new Error('Invalid touch');
    const raw = touch as Record<string, unknown>;
    const action: 'tap' | 'swipe' | null =
      raw.action === 'tap' ? 'tap' : raw.action === 'swipe' ? 'swipe' : null;
    if (!action) throw new Error('Invalid touch action');
    const durationMs =
      typeof raw.durationMs === 'number' && Number.isFinite(raw.durationMs)
        ? Math.round(raw.durationMs)
        : 60;
    const payload: MirrorTouchWire = {
      action,
      x: requireFraction(raw.x, 'touch x'),
      y: requireFraction(raw.y, 'touch y'),
      x2: action === 'swipe' ? requireFraction(raw.x2, 'touch x2') : null,
      y2: action === 'swipe' ? requireFraction(raw.y2, 'touch y2') : null,
      durationMs,
    };
    return controller.mirrorTouch(payload);
  });

  ipcMain.handle('app:openLogs', () => controller.openLogs());
  ipcMain.handle('app:openDocs', () => controller.openDocs());
  ipcMain.handle('updater:check', () => controller.checkForUpdates());
  ipcMain.handle('updater:download', () => controller.downloadUpdate());
  ipcMain.handle('updater:install', () => controller.installUpdate());
  ipcMain.handle('updater:openRelease', () => controller.openReleasePage());
  ipcMain.handle('app:refresh', () => controller.getSnapshot());
}
