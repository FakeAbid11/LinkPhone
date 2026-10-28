import type { PeerSession } from '../server';
import type {
  CallStateWire,
  ContactListResultWire,
  ContactWire,
} from '../../shared/types';
import { TYPES } from '../../shared/types';
import { normalizeDialNumber } from '../core/safety';
import type { Logger } from '../core/logger';

const VALID_STATES = new Set(['idle', 'ringing', 'offhook', 'unknown']);

export class CallService {
  private state: CallStateWire = { state: 'idle', timestamp: Date.now() };
  private error: string | undefined;
  private session: PeerSession | undefined;
  private contacts: ContactWire[] = [];
  private contactsLoading = false;
  private contactsError: string | undefined;

  constructor(
    private readonly logger: Logger,
    private readonly onChange: () => void,
    private readonly onIncoming: (state: CallStateWire) => void,
  ) {}

  onConnected(session: PeerSession): void {
    this.session = session;
    this.state = { state: 'idle', timestamp: Date.now() };
    this.error = undefined;
    this.contacts = [];
    this.contactsLoading = false;
    this.contactsError = undefined;
    this.onChange();
  }

  onDisconnected(): void {
    this.session = undefined;
    this.state = { state: 'idle', timestamp: Date.now() };
    this.contacts = [];
    this.contactsLoading = false;
    this.contactsError = undefined;
    this.onChange();
  }

  onPhoneState(data: CallStateWire | undefined): void {
    if (!data || typeof data.state !== 'string' || !VALID_STATES.has(data.state)) return;
    const previous = this.state.state;
    this.state = {
      state: data.state,
      number: typeof data.number === 'string' ? data.number.slice(0, 40) : null,
      contactName: typeof data.contactName === 'string' ? data.contactName.slice(0, 120) : null,
      timestamp: Number.isFinite(data.timestamp) ? data.timestamp : Date.now(),
    };
    this.error = undefined;
    this.logger.log('Calls', `Call state: ${this.state.state}`);
    this.onChange();
    if (data.state === 'ringing' && previous !== 'ringing') {
      this.onIncoming(this.state);
    }
  }

  async dial(number: string, subId?: number | null): Promise<void> {
    const session = this.requireSession();
    const destination = normalizeDialNumber(number);
    if (!destination) throw new Error('Enter a valid phone number');
    const payload: { number: string; subId?: number } = { number: destination };
    if (typeof subId === 'number' && Number.isInteger(subId) && subId > 0) {
      payload.subId = subId;
    }
    await session.request(TYPES.CALL_DIAL, payload, 15_000);
    this.logger.log(
      'Calls',
      payload.subId
        ? `Asked the phone to place a call on SIM ${payload.subId}`
        : 'Asked the phone to place a call on the default SIM',
    );
  }

  async refreshContacts(): Promise<void> {
    const session = this.requireSession();
    if (this.contactsLoading) return;
    this.contactsLoading = true;
    this.contactsError = undefined;
    this.onChange();
    try {
      const response = await session.request<ContactListResultWire>(
        TYPES.CONTACTS_LIST,
        { limit: 1_000 },
        20_000,
      );
      const data = response.data;
      if (!data || !Array.isArray(data.contacts)) {
        throw new Error('The phone returned an invalid contact list');
      }
      this.contacts = data.contacts
        .filter(
          (contact): contact is ContactWire =>
            Boolean(contact) &&
            typeof contact.id === 'string' &&
            typeof contact.name === 'string' &&
            typeof contact.number === 'string',
        )
        .map((contact) => ({
          id: contact.id.slice(0, 64),
          name: contact.name.slice(0, 120),
          number: contact.number.slice(0, 40),
          label: typeof contact.label === 'string' ? contact.label.slice(0, 40) : null,
        }))
        .slice(0, 2_000);
      this.logger.log('Calls', `Loaded ${this.contacts.length} contacts`);
    } catch (error) {
      this.contactsError =
        error instanceof Error ? error.message : 'Could not load contacts';
      this.logger.log('Calls', `Contacts refresh failed: ${this.contactsError}`);
    } finally {
      this.contactsLoading = false;
      this.onChange();
    }
  }

  async answer(): Promise<void> {
    const session = this.requireSession();
    await session.request(TYPES.CALL_ANSWER, {}, 15_000);
    this.logger.log('Calls', 'Asked the phone to answer');
  }

  async decline(): Promise<void> {
    const session = this.requireSession();
    await session.request(TYPES.CALL_DECLINE, {}, 15_000);
    this.logger.log('Calls', 'Asked the phone to decline/end');
  }

  status(): {
    state: CallStateWire;
    error?: string;
    contacts: ContactWire[];
    contactsLoading: boolean;
    contactsError?: string;
  } {
    return {
      state: this.state,
      error: this.error,
      contacts: this.contacts,
      contactsLoading: this.contactsLoading,
      contactsError: this.contactsError,
    };
  }

  private requireSession(): PeerSession {
    if (!this.session) throw new Error('Connect to your phone first');
    return this.session;
  }
}
