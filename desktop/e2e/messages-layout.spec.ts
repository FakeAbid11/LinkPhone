import { test, expect } from '@playwright/test';
import { _electron as electron } from 'playwright';
import os from 'node:os';
import path from 'node:path';
import fs from 'node:fs';

test('conversation list and message history scroll at typical and small window sizes', async () => {
  const userData = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-layout-'));
  const app = await electron.launch({
    args: ['.'],
    env: {
      ...process.env,
      LINKPHONE_E2E: '1',
      LINKPHONE_USER_DATA: userData,
    },
  });

  try {
    const window = await app.firstWindow();
    const css = fs.readFileSync(
      path.resolve(process.cwd(), 'src/renderer/styles.css'),
      'utf8',
    );
    const threads = Array.from(
      { length: 60 },
      (_, index) => `
        <button class="thread-item">
          <span class="thread-avatar">A</span>
          <span class="thread-copy">
            <span class="thread-top"><strong>Conversation ${index}</strong><span>now</span></span>
            <span class="thread-preview">Preview ${index}</span>
          </span>
        </button>`,
    ).join('');
    const messages = Array.from(
      { length: 80 },
      (_, index) => `
        <div class="msg-row is-in">
          <div class="msg-bubble">
            <p>Message ${index} in a long conversation that must scroll.</p>
            <span class="msg-time">10:00</span>
          </div>
        </div>`,
    ).join('');

    await window.setContent(`<!doctype html>
      <html>
        <head><style>${css}</style></head>
        <body>
          <div class="app-shell">
            <aside class="sidebar"></aside>
            <main class="content">
              <header class="page-head">
                <div><h1>Messages</h1><p>60 conversations</p></div>
              </header>
              <div class="messages-layout">
                <aside class="thread-list">
                  <div class="thread-list-head"><span>Conversations</span></div>
                  ${threads}
                </aside>
                <section class="chat-panel">
                  <header class="chat-head"><div><strong>Conversation</strong><span>80 messages</span></div></header>
                  <div class="chat-scroll">${messages}</div>
                  <footer class="composer"><textarea rows="2"></textarea></footer>
                </section>
              </div>
            </main>
          </div>
        </body>
      </html>`);

    const checkSize = async (width: number, height: number) => {
      await app.evaluate(
        ({ BrowserWindow }, size) => {
          const win = BrowserWindow.getAllWindows()[0];
          win?.setSize(size.width, size.height);
        },
        { width, height },
      );
      await window.waitForTimeout(250);
      const result = await window.evaluate(() => {
        const list = document.querySelector<HTMLElement>('.thread-list');
        const chat = document.querySelector<HTMLElement>('.chat-scroll');
        const composer = document.querySelector<HTMLElement>('.composer');
        if (!list || !chat || !composer) throw new Error('Fixture elements missing');
        list.scrollTop = list.scrollHeight;
        chat.scrollTop = chat.scrollHeight;
        return {
          list: {
            scrollHeight: list.scrollHeight,
            clientHeight: list.clientHeight,
            scrollTop: list.scrollTop,
          },
          chat: {
            scrollHeight: chat.scrollHeight,
            clientHeight: chat.clientHeight,
            scrollTop: chat.scrollTop,
          },
          composerBottom: composer.getBoundingClientRect().bottom,
          viewportHeight: window.innerHeight,
        };
      });

      expect(result.list.scrollHeight).toBeGreaterThan(result.list.clientHeight);
      expect(result.list.scrollTop).toBeGreaterThan(0);
      expect(result.chat.scrollHeight).toBeGreaterThan(result.chat.clientHeight);
      expect(result.chat.scrollTop).toBeGreaterThan(0);
      expect(result.composerBottom).toBeLessThanOrEqual(result.viewportHeight + 1);
    };

    await checkSize(1180, 780);
    await checkSize(960, 640);
  } finally {
    await app.close();
    fs.rmSync(userData, { recursive: true, force: true });
  }
});
