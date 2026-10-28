import { contextBridge } from 'electron';

contextBridge.exposeInMainWorld('linkphone', {
  version: '0.1.0',
});
