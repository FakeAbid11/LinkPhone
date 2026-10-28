import fs from 'node:fs';
import path from 'node:path';
import type { PeerSession } from '../server';
import type {
  HandoffFileMetaWire,
  HandoffShareWire,
  HandoffUi,
} from '../../shared/types';
import { TYPES } from '../../shared/types';
import { isImageMime, isSafeHttpUrl, sanitizeFileName, truncateText } from '../core/safety';
import type { Logger } from '../core/logger';
import type { StreamRegistry } from '../core/streams';

const MAX_FILE_BYTES = 20 * 1024 * 1024;

export class HandoffService {
  private items: HandoffUi[] = [];
  private session: PeerSession | undefined;

  constructor(
    private readonly streams: StreamRegistry,
    private readonly downloadsDir: () => string,
    private readonly openExternal: (url: string) => Promise<void>,
    private readonly logger: Logger,
    private readonly toast: (title: string, body: string) => void,
    private readonly onChange: () => void,
  ) {}

  onConnected(session: PeerSession): void {
    this.session = session;
    this.items = [];
    this.onChange();
  }

  onDisconnected(): void {
    this.session = undefined;
    this.onChange();
  }

  onShare(session: PeerSession, data: HandoffShareWire | undefined): void {
    if (session !== this.session || !data) return;
    if (data.kind === 'url') {
      if (!isSafeHttpUrl(data.url ?? data.text)) return;
      const url = (data.url ?? data.text ?? '').trim();
      this.add({
        id: `handoff-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        kind: 'url',
        text: url,
        url,
        time: Date.now(),
      });
      this.toast('Link from phone', 'A link was sent to your PC.');
    } else {
      const text = truncateText(typeof data.text === 'string' ? data.text : '', 20_000);
      if (!text.trim()) return;
      this.add({
        id: `handoff-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        kind: 'text',
        text,
        time: Date.now(),
      });
      this.toast('Text from phone', 'Shared text is ready on your PC.');
    }
  }

  onFileMeta(session: PeerSession, meta: HandoffFileMetaWire | undefined): void {
    if (session !== this.session || !meta) return;
    if (typeof meta.streamId !== 'number') return;
    if (!isImageMime(meta.mime)) {
      this.rejectStream(meta.streamId, 'Only image files are accepted');
      return;
    }
    if (!Number.isFinite(meta.size) || meta.size <= 0 || meta.size > MAX_FILE_BYTES) {
      this.rejectStream(meta.streamId, 'File is too large');
      return;
    }
    const safeName = sanitizeFileName(meta.name, 'shared-image.jpg');
    const streamPromise = this.streams.create(
      meta.streamId,
      MAX_FILE_BYTES,
      45_000,
      'shared image',
    );

    streamPromise
      .then((buffer) => {
        const directory = path.join(this.downloadsDir(), 'LinkPhone');
        fs.mkdirSync(directory, { recursive: true });
        const target = path.join(directory, `${Date.now()}-${safeName}`);
        fs.writeFileSync(target, buffer, { mode: 0o644 });
        this.add({
          id: `handoff-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
          kind: 'image',
          fileName: safeName,
          mime: meta.mime,
          savedPath: target,
          time: Date.now(),
        });
        this.logger.log('Handoff', `Saved a shared image to ${target}`);
        this.toast('Image from phone', `Saved to ${path.basename(target)}`);
      })
      .catch((error) => {
        this.logger.log(
          'Handoff',
          `File transfer failed: ${error instanceof Error ? error.message : String(error)}`,
        );
      });
  }

  async openUrl(url: string): Promise<void> {
    if (!isSafeHttpUrl(url)) throw new Error('Only http(s) links can be opened');
    await this.openExternal(url.trim());
  }

  async openOnPhone(url: string): Promise<void> {
    if (!isSafeHttpUrl(url)) throw new Error('Only http(s) links can be sent to the phone');
    const session = this.session;
    if (!session) throw new Error('Connect to your phone first');
    await session.request(TYPES.HANDOFF_OPEN, { url: url.trim() }, 8_000);
    this.toast('Sent to phone', 'Open the notification on your phone to view the link.');
    this.logger.log('Handoff', 'Sent a link to the phone');
  }

  list(): HandoffUi[] {
    return this.items;
  }

  private add(item: HandoffUi): void {
    this.items = [item, ...this.items].slice(0, 50);
    this.onChange();
  }

  private rejectStream(streamId: number, reason: string): void {
    const pending = this.streams.has(streamId);
    this.logger.log('Handoff', `Rejected shared file: ${reason}${pending ? '' : ' (no stream)'}`);
  }
}
