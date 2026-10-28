import type { PeerSession } from '../server';
import { TYPES } from '../../shared/types';
import { truncateText } from '../core/safety';
import type { Logger } from '../core/logger';

export class ClipboardService {
  private session: PeerSession | undefined;
  private lastReceived: string | undefined;
  private lastSentAt = 0;
  private lastSentText = '';

  constructor(
    private readonly readClipboard: () => string,
    private readonly writeClipboard: (text: string) => void,
    private readonly logger: Logger,
    private readonly onChange: () => void,
    private readonly toast: (title: string, body: string) => void,
  ) {}

  onConnected(session: PeerSession): void {
    this.session = session;
    this.onChange();
  }

  onDisconnected(): void {
    this.session = undefined;
    this.onChange();
  }

  async sendText(text: string): Promise<void> {
    const session = this.session;
    if (!session) throw new Error('Connect to your phone first');
    const value = truncateText(text, 32_000);
    if (!value.trim()) throw new Error('Clipboard text is empty');
    await session.request(TYPES.CLIPBOARD_SET, { text: value }, 8_000);
    this.lastSentAt = Date.now();
    this.lastSentText = value;
    this.logger.log('Clipboard', 'Sent text to the phone');
    this.onChange();
  }

  async sendPcClipboard(): Promise<void> {
    const text = this.readClipboard();
    if (!text.trim()) throw new Error('The PC clipboard has no text');
    await this.sendText(text);
  }

  receiveFromPhone(text: string | undefined): void {
    if (typeof text !== 'string') return;
    const value = truncateText(text, 32_000);
    this.lastReceived = value;
    this.writeClipboard(value);
    this.logger.log('Clipboard', 'Received text from the phone');
    this.toast('Clipboard', 'Text from your phone is now on the PC clipboard.');
    this.onChange();
  }

  async maybeAutoSend(enabled: boolean): Promise<void> {
    if (!enabled || !this.session) return;
    const text = this.readClipboard();
    if (!text || !text.trim()) return;
    if (text === this.lastSentText) return;
    if (Date.now() - this.lastSentAt < 1_500) return;
    try {
      await this.sendText(text);
    } catch (error) {
      this.logger.log('Clipboard', `Auto-send failed: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  status(): { lastReceived?: string; lastSentAt?: number } {
    return { lastReceived: this.lastReceived, lastSentAt: this.lastSentAt || undefined };
  }
}
