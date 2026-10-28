import { test, expect } from '@playwright/test';
import { _electron as electron } from 'playwright';
import os from 'node:os';
import path from 'node:path';
import fs from 'node:fs';

test('layout, type scale, and keyboard focus hold at supported window sizes', async () => {
  const userData = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-ui-'));
  const app = await electron.launch({
    args: ['.'],
    env: {
      ...process.env,
      LINKPHONE_E2E: '1',
      LINKPHONE_USER_DATA: userData,
    },
  });

  try {
    const page = await app.firstWindow();
    const setSize = async (width: number, height: number) => {
      await app.evaluate(
        ({ BrowserWindow }, size) => {
          const win = BrowserWindow.getAllWindows()[0];
          win?.setSize(size.width, size.height);
        },
        { width, height },
      );
      await page.waitForTimeout(300);
    };

    const audit = async () =>
      page.evaluate(() => {
        const content = document.querySelector<HTMLElement>('.content');
        if (!content) throw new Error('content missing');
        const small: string[] = [];
        for (const element of Array.from(document.querySelectorAll<HTMLElement>('.app-shell *'))) {
          const text = (element.textContent ?? '').trim();
          if (!text || element.children.length > 0) continue;
          if (element.getClientRects().length === 0) continue;
          const size = parseFloat(getComputedStyle(element).fontSize);
          if (Number.isFinite(size) && size < 11) {
            small.push(`${element.className || element.tagName} ${size}px`);
          }
        }
        const grid = document.querySelector<HTMLElement>('.grid-2');
        const columns = grid
          ? getComputedStyle(grid)
              .gridTemplateColumns.split(' ')
              .filter((value) => value.trim().length > 0).length
          : 0;
        return {
          horizontalOverflow: content.scrollWidth - content.clientWidth,
          small,
          columns,
          headings: document.querySelectorAll('h1').length,
        };
      });

    await setSize(1440, 900);
    let result = await audit();
    expect(result.columns).toBe(2);
    expect(result.horizontalOverflow).toBeLessThanOrEqual(1);
    expect(result.small).toEqual([]);

    await setSize(1180, 780);
    result = await audit();
    expect(result.columns).toBe(2);
    expect(result.horizontalOverflow).toBeLessThanOrEqual(1);
    expect(result.small).toEqual([]);

    await setSize(960, 640);
    result = await audit();
    expect(result.columns).toBe(1);
    expect(result.horizontalOverflow).toBeLessThanOrEqual(1);
    expect(result.small).toEqual([]);

    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    const ring = await page.evaluate(() => {
      const element = document.activeElement as HTMLElement | null;
      if (!element) return null;
      const style = getComputedStyle(element);
      return {
        label: element.textContent?.trim() ?? '',
        style: style.outlineStyle,
        width: parseFloat(style.outlineWidth),
      };
    });
    expect(ring?.label).toContain('Connection');
    expect(ring?.style).toBe('solid');
    expect(ring?.width ?? 0).toBeGreaterThanOrEqual(2);

    await setSize(1180, 780);
    for (const name of ['Messages', 'Notifications', 'Photos', 'Calls', 'Apps', 'Settings']) {
      await page.getByRole('button', { name, exact: true }).click();
      await expect(page.getByRole('heading', { name, level: 1 })).toBeVisible();
      const view = await audit();
      expect(view.headings).toBe(1);
      expect(view.small).toEqual([]);
    }
  } finally {
    await app.close();
    fs.rmSync(userData, { recursive: true, force: true });
  }
});
