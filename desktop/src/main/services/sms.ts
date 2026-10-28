import type { PeerSession } from '../server';
import type { SmsListResultWire, SmsMessageUi, SmsThreadUi } from '../../shared/types';
import { TYPES } from '../../shared/types';
import { groupSmsThreads, mergeLocalSms } from '../core/sms';
import { normalizeDialNumber } from '../core/safety';
import type { Logger } from '../core/logger';

export interface SmsStatus {
  threads: SmsThreadUi[];
  loading: boolean;
  error?: string;
  refreshing: boolean;
}

export class SmsService {
  private messages: SmsMessageUi[] = [];
  private local: SmsMessageUi[] = [];
  private loading = false;
  private dirty = false;
  private error: string | undefined;
  private session: PeerSession | undefined;
  private debounce: NodeJS.Timeout | undefined;

  constructor(
    private readonly logger: Logger,
    private readonly onChange: () => void,
  ) {}

  onConnected(session: PeerSession): void {
    this.session = session;
    this.messages = [];
    this.local = [];
    this.error = undefined;
    this.loading = false;
    this.onChange();
  }

  onDisconnected(): void {
    this.session = undefined;
    this.loading = false;
    if (this.debounce) clearTimeout(this.debounce);
    this.debounce = undefined;
    this.onChange();
  }

  markDirty(session: PeerSession): void {
    if (session !== this.session) return;
    if (this.debounce) clearTimeout(this.debounce);
    this.debounce = setTimeout(() => {
      this.debounce = undefined;
      void this.refresh();
    }, 450);
  }

  async refresh(): Promise<void> {
    const session = this.session;
    if (!session) return;
    if (this.loading) {
      this.dirty = true;
      return;
    }
    this.loading = true;
    this.error = undefined;
    this.onChange();
    try {
      const response = await session.request<SmsListResultWire>(
        TYPES.SMS_LIST,
        { limit: 500 },
        25_000,
      );
      const data = response.data;
      if (!data || !Array.isArray(data.messages)) {
        throw new Error('The phone returned an invalid message list');
      }
      this.messages = data.messages.slice(0, 1_000);
      this.logger.log('Sms', `Loaded ${this.messages.length} messages`);
    } catch (error) {
      this.error = error instanceof Error ? error.message : 'Could not load messages';
      this.logger.log('Sms', `Refresh failed: ${this.error}`);
    } finally {
      this.loading = false;
      this.onChange();
      if (this.dirty) {
        this.dirty = false;
        void this.refresh();
      }
    }
  }

  async send(address: string, body: string, subId?: number | null): Promise<void> {
    const session = this.session;
    if (!session) throw new Error('Connect to your phone first');
    const destination = normalizeDialNumber(address);
    if (!destination) throw new Error('Destination must be a phone number');
    if (!body.trim()) throw new Error('Message is empty');
    if (body.length > 5_000) throw new Error('Message is too long');

    const response = await session.request<{ accepted: boolean; parts: number }>(
      TYPES.SMS_SEND,
      {
        address: destination,
        body,
        subId: typeof subId === 'number' && subId >= 0 ? subId : null,
      },
      30_000,
    );
    if (!response.data?.accepted) {
      throw new Error('The phone did not accept the message');
    }

    this.local.push({
      id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      address: destination,
      body,
      date: Date.now(),
      type: 2,
      read: true,
      subId: subId ?? null,
      local: true,
    });
    if (this.local.length > 300) this.local.shift();
    this.logger.log('Sms', 'Message handed to the phone for sending');
    this.onChange();
  }

  status(): SmsStatus {
    return {
      threads: groupSmsThreads(mergeLocalSms(this.messages, this.local)),
      loading: this.loading,
      error: this.error,
      refreshing: this.loading,
    };
  }
}
