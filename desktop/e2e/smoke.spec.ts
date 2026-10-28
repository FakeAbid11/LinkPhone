import { test, expect } from '@playwright/test';
import { _electron as electron } from 'playwright';
import os from 'node:os';
import path from 'node:path';
import fs from 'node:fs';

test('desktop app launches and renders the pairing screen', async () => {
  const userData = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-e2e-'));
  const app = await electron.launch({
    args: ['.'],
    env: {
      ...process.env,
      LINKPHONE_E2E: '1',
      LINKPHONE_USER_DATA: userData,
    },
  });

  const output: string[] = [];
  app.process().stderr?.on('data', (chunk: Buffer) => output.push(chunk.toString()));

  try {
    const window = await app.firstWindow();
    await expect(window).toHaveTitle('LinkPhone');
    await expect(window.getByRole('heading', { name: 'LinkPhone' })).toBeVisible();
  } catch (error) {
    console.error('Electron stderr:\n' + output.join(''));
    throw error;
  } finally {
    await app.close();
    fs.rmSync(userData, { recursive: true, force: true });
  }
});
