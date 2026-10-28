import { test, expect } from '@playwright/test';
import { _electron as electron } from 'playwright';
import os from 'node:os';
import path from 'node:path';
import fs from 'node:fs';

test('desktop app launches, navigates, and keeps modal focus contained', async () => {
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
    const page = await app.firstWindow();
    const consoleErrors: string[] = [];
    page.on('console', (message) => {
      if (message.type() === 'error') consoleErrors.push(message.text());
    });

    await expect(page).toHaveTitle('LinkPhone');
    await expect(page.getByRole('heading', { name: 'Connection', level: 1 })).toBeVisible();
    await expect(page.getByText('LinkPhone', { exact: true }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Create pairing code' })).toBeEnabled();
    await expect(page.locator('h1')).toHaveCount(1);
    await expect(page.locator('nav[aria-label="Sections"]')).toBeVisible();

    const navConnection = page.getByRole('button', { name: 'Connection' });
    await expect(navConnection).toHaveAttribute('aria-current', 'page');
    await expect(navConnection.locator('svg.icon')).toBeVisible();

    await page.keyboard.press('Tab');
    await expect(page.getByRole('link', { name: 'Skip to main content' })).toBeFocused();

    await page.getByRole('button', { name: 'Settings' }).click();
    await expect(page.getByRole('heading', { name: 'Settings', level: 1 })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Settings' })).toHaveAttribute(
      'aria-current',
      'page',
    );

    const reset = page.getByRole('button', { name: 'Reset PC identity' });
    await reset.click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog).toHaveAttribute('aria-modal', 'true');

    for (let index = 0; index < 8; index += 1) {
      await page.keyboard.press('Tab');
    }
    const focusInside = await page.evaluate(() =>
      Boolean(document.querySelector('[role="dialog"]')?.contains(document.activeElement)),
    );
    expect(focusInside).toBe(true);

    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(reset).toBeFocused();

    await page.getByRole('button', { name: 'Messages' }).click();
    await expect(page.getByRole('heading', { name: 'Messages', level: 1 })).toBeVisible();

    expect(consoleErrors).toEqual([]);
  } catch (error) {
    console.error('Electron stderr:\n' + output.join(''));
    throw error;
  } finally {
    await app.close();
    fs.rmSync(userData, { recursive: true, force: true });
  }
});
