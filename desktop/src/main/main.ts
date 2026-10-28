import path from 'node:path';
import {
  BrowserWindow,
  Menu,
  Tray,
  app,
  nativeImage,
} from 'electron';
import { AppController } from './app';
import { registerIpc } from './ipc';

const isE2E = process.env.LINKPHONE_E2E === '1';
if (process.env.LINKPHONE_USER_DATA) {
  app.setPath('userData', process.env.LINKPHONE_USER_DATA);
}
if (isE2E) {
  app.disableHardwareAcceleration();
}

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
}

let controller: AppController | undefined;
let tray: Tray | undefined;
let mainWindow: BrowserWindow | undefined;
let quitting = false;
let stopping = false;

function iconPath(): string {
  return path.join(app.getAppPath(), 'build', 'icon.png');
}

function createWindow(): BrowserWindow {
  const window = new BrowserWindow({
    width: 1180,
    height: 780,
    minWidth: 960,
    minHeight: 640,
    backgroundColor: '#0b1018',
    show: false,
    autoHideMenuBar: true,
    title: 'LinkPhone',
    icon: iconPath(),
    webPreferences: {
      preload: path.join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  window.once('ready-to-show', () => window.show());
  window.on('focus', () => window.flashFrame(false));
  window.on('close', (event) => {
    if (quitting || isE2E) return;
    event.preventDefault();
    window.hide();
  });
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  window.webContents.on('will-navigate', (event) => event.preventDefault());
  void window.loadFile(path.join(__dirname, '../renderer/index.html'));
  return window;
}

function createTray(): Tray {
  const image = nativeImage.createFromPath(iconPath());
  const trayInstance = new Tray(
    image.isEmpty() ? nativeImage.createEmpty() : image.resize({ width: 16, height: 16 }),
  );
  trayInstance.setToolTip('LinkPhone');
  trayInstance.on('click', () => controller?.showWindow());
  trayInstance.setContextMenu(
    Menu.buildFromTemplate([
      {
        label: 'Open LinkPhone',
        click: () => controller?.showWindow(),
      },
      {
        label: 'Pair a phone',
        click: () => {
          controller?.showWindow();
          void controller?.createPairingOffer().catch(() => undefined);
        },
      },
      { type: 'separator' },
      {
        label: 'Quit LinkPhone',
        click: () => {
          quitting = true;
          app.quit();
        },
      },
    ]),
  );
  return trayInstance;
}

function refreshTray(): void {
  if (!tray || !controller) return;
  const snapshot = controller.getSnapshot();
  const device = snapshot.connection.deviceName;
  tray.setToolTip(
    snapshot.connection.connected
      ? `LinkPhone — connected to ${device ?? 'phone'}`
      : 'LinkPhone — not connected',
  );
}

app.whenReady().then(async () => {
  app.setAppUserModelId('app.linkphone.desktop');
  Menu.setApplicationMenu(null);

  controller = new AppController(app.getPath('userData'), isE2E);
  await controller.start();
  registerIpc(controller);

  mainWindow = createWindow();
  controller.attachWindow(mainWindow);
  controller.setTrayUpdater(() => refreshTray());

  if (!isE2E) {
    tray = createTray();
  }

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      mainWindow = createWindow();
      controller?.attachWindow(mainWindow);
    } else {
      controller?.showWindow();
    }
  });
});

app.on('second-instance', () => {
  controller?.showWindow();
});

app.on('window-all-closed', () => {
  if (isE2E) {
    app.quit();
  }
  // Otherwise stay available in the tray.
});

app.on('before-quit', (event) => {
  quitting = true;
  if (stopping || !controller) return;
  event.preventDefault();
  stopping = true;
  void controller.stop().finally(() => app.quit());
});
