import { test, expect } from '@playwright/test';
import { _electron as electron } from 'playwright';
import os from 'node:os';
import path from 'node:path';
import fs from 'node:fs';

test.skip(!process.env.LINKPHONE_EXE, 'Set LINKPHONE_EXE to test the packaged app');

test('packaged Windows build launches with its runtime dependencies', async () => {
  const executable = path.resolve(process.env.LINKPHONE_EXE as string);
  expect(fs.existsSync(executable)).toBe(true);

  const userData = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-packaged-'));
  const app = await electron.launch({
    executablePath: executable,
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
    await expect(window.getByRole('heading', { name: 'Connection' })).toBeVisible();
    await expect(window.getByText('LinkPhone', { exact: true }).first()).toBeVisible();
    await expect(window.getByRole('button', { name: 'Create pairing code' })).toBeEnabled();
  } catch (error) {
    console.error('Packaged app stderr:\n' + output.join(''));
    throw error;
  } finally {
    await app.close();
    fs.rmSync(userData, { recursive: true, force: true });
  }
});
