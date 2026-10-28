import { useEffect, useMemo, useRef, useState } from 'react';
import { CAPABILITIES, type AppSnapshot } from '../../shared/types';
import { api } from '../api';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Field,
  Notice,
  PageHeader,
  Select,
  Spinner,
  TextInput,
} from '../components';
import type { Ui } from '../ui';
import { initials } from '../utils';

export function CallsView({ state, ui }: { state: AppSnapshot; ui: Ui }) {
  const connected = state.connection.connected;
  const supported = state.connection.capabilities.includes('calls');
  const call = state.calls.state;
  const ringing = call.state === 'ringing';
  const inCall = call.state === 'offhook';
  const activeCall = ringing || inCall;
  const subscriptions = state.connection.subscriptions;
  const simSelectionSupported = state.connection.capabilities.includes(
    CAPABILITIES.CALLS_SIM_SELECTION,
  );
  const canSearchContacts = state.connection.capabilities.includes(CAPABILITIES.CONTACTS);
  const [number, setNumber] = useState('');
  const [subId, setSubId] = useState<number | null>(null);
  const [contactQuery, setContactQuery] = useState('');
  const contactsLoadedRef = useRef(false);
  const selectedSim = subscriptions.find((sim) => sim.subscriptionId === subId);
  const filteredContacts = useMemo(() => {
    const query = contactQuery.trim().toLowerCase();
    const list = query
      ? state.calls.contacts.filter(
          (contact) =>
            contact.name.toLowerCase().includes(query) ||
            contact.number.toLowerCase().includes(query) ||
            (contact.label ?? '').toLowerCase().includes(query),
        )
      : state.calls.contacts;
    return list.slice(0, 200);
  }, [state.calls.contacts, contactQuery]);

  useEffect(() => {
    if (subId !== null && !subscriptions.some((sim) => sim.subscriptionId === subId)) {
      setSubId(null);
    }
  }, [subscriptions, subId]);

  useEffect(() => {
    if (!connected || !canSearchContacts || contactsLoadedRef.current) return;
    contactsLoadedRef.current = true;
    void api.refreshContacts().catch(() => undefined);
  }, [connected, canSearchContacts]);

  useEffect(() => {
    if (!connected) contactsLoadedRef.current = false;
  }, [connected]);

  const pickContact = (value: string) => {
    setNumber(value);
    window.requestAnimationFrame(() => {
      document.getElementById('dial-number')?.focus();
    });
  };

  if (!connected) {
    return (
      <>
        <PageHeader title="Calls" subtitle="Control calls from the PC. Audio stays on the phone." />
        <Card>
          <EmptyState
            icon="phone"
            title="Connect your phone first"
            body="LinkPhone can show call state, place calls, and answer or decline where Android and the device manufacturer allow it."
          />
        </Card>
      </>
    );
  }

  if (!supported) {
    return (
      <>
        <PageHeader title="Calls" subtitle="Control calls from the PC. Audio stays on the phone." />
        <Card>
          <EmptyState
            icon="phone"
            title="Call permission is not granted"
            body="On the phone, grant Phone permission in LinkPhone. Answer/decline also needs the separate Answer calls permission; caller ID needs call-log access."
          />
          <Notice tone="warn" title="No call audio">
            A third-party Android app cannot capture cellular call audio. The PC is a remote
            control; use your phone's speakerphone or a headset for sound.
          </Notice>
        </Card>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Calls"
        subtitle="Call control only. Android does not allow a companion app to route call audio to the PC."
      />

      {!activeCall && state.calls.error && <Notice tone="bad">{state.calls.error}</Notice>}

      {activeCall && (
        <Card
          icon="phone"
          title="Current call"
          actions={
            <Badge tone={ringing ? 'warn' : 'good'}>{ringing ? 'Incoming' : 'In progress'}</Badge>
          }
        >
          <div className={`call-hero ${ringing ? 'is-ringing' : ''}`}>
            <span className="call-pulse" />
            <div>
              <strong>{call.contactName || call.number || 'Active call'}</strong>
              <span>
                {call.contactName && call.number
                  ? call.number
                  : ringing
                    ? 'Your phone is ringing'
                    : 'The call is active on your phone'}
              </span>
            </div>
          </div>

          <div className="call-actions">
            {ringing && (
              <>
                <Button
                  variant="primary"
                  disabled={!state.calls.answerSupported}
                  onClick={() => void ui.run(() => api.answerCall(), 'Answering on the phone')}
                >
                  Answer
                </Button>
                <Button
                  variant="danger"
                  disabled={!state.calls.answerSupported}
                  onClick={() => void ui.run(() => api.declineCall(), 'Call declined')}
                >
                  Decline
                </Button>
              </>
            )}
            {inCall && (
              <Button
                variant="danger"
                disabled={!state.calls.answerSupported}
                onClick={() => void ui.run(() => api.declineCall(), 'Call ended')}
              >
                End call
              </Button>
            )}
          </div>

          {ringing && !state.calls.answerSupported && (
            <Notice tone="warn" title="Answer/decline unavailable">
              Grant the Answer calls permission on the phone. Some manufacturers restrict
              programmatic call answering to the default dialer.
            </Notice>
          )}
          {state.calls.error && <Notice tone="bad">{state.calls.error}</Notice>}
        </Card>
      )}

      <div className="grid grid-2">
        <Card
          icon="phone"
          title="Dial a call"
          subtitle="The call is placed by the phone's dialer/telecom service."
        >
          <form
            className="dialer"
            onSubmit={(event) => {
              event.preventDefault();
              if (!number.trim()) return;
              void ui.run(
                () => api.dial(number, subId),
                selectedSim
                  ? `Calling on ${selectedSim.label}`
                  : 'Calling on the default SIM',
              );
            }}
          >
            <Field id="dial-number" label="Phone number" hint="Audio stays on the phone.">
              <TextInput
                id="dial-number"
                type="tel"
                inputMode="tel"
                autoComplete="tel"
                placeholder="+1 555 0100"
                value={number}
                onChange={(event) => setNumber(event.target.value)}
              />
            </Field>
            {subscriptions.length > 1 && simSelectionSupported && (
              <Field id="dial-sim" label="SIM for this call">
                <Select
                  id="dial-sim"
                  value={subId ?? ''}
                  onChange={(event) =>
                    setSubId(event.target.value ? Number(event.target.value) : null)
                  }
                >
                  <option value="">Default SIM</option>
                  {subscriptions.map((sim) => (
                    <option key={sim.subscriptionId} value={sim.subscriptionId}>
                      {sim.label} · SIM {sim.slotIndex + 1}
                    </option>
                  ))}
                </Select>
              </Field>
            )}
            {subscriptions.length > 1 && !simSelectionSupported && (
              <p className="muted small">
                SIM selection requires Android 11 or newer. This call uses the phone's default
                SIM.
              </p>
            )}
            <Button variant="primary" type="submit" icon="phone" disabled={!number.trim()}>
              Call from phone
            </Button>
          </form>
          <p className="muted small dialer-note">
            Emergency calls are handled by Android's telecom stack. LinkPhone does not place
            emergency calls itself.
          </p>
        </Card>

        <Card
          icon="users"
          title="Contacts"
          subtitle="Tap a contact to fill the dialer, then choose a SIM and call."
          actions={
            canSearchContacts ? (
              <Button
                small
                variant="ghost"
                icon="refresh"
                onClick={() => void api.refreshContacts().catch(() => undefined)}
                loading={state.calls.contactsLoading}
              >
                Refresh
              </Button>
            ) : undefined
          }
        >
          {!canSearchContacts ? (
            <Notice tone="warn" title="Contacts access">
              Grant Contacts on the phone (LinkPhone → Permissions), update the phone app to
              v0.1.3, and reconnect. You can still dial numbers manually.
            </Notice>
          ) : (
            <>
              <div className="contact-search-row">
                <TextInput
                  type="search"
                  aria-label="Search contacts"
                  placeholder="Search name, number or label"
                  value={contactQuery}
                  onChange={(event) => setContactQuery(event.target.value)}
                />
              </div>
              {state.calls.contactsError && <Notice tone="bad">{state.calls.contactsError}</Notice>}
              {state.calls.contactsLoading && state.calls.contacts.length === 0 ? (
                <Spinner label="Loading contacts…" />
              ) : filteredContacts.length === 0 ? (
                <p className="muted small">
                  {state.calls.contacts.length === 0
                    ? 'No contacts were shared. Check the Contacts permission on the phone.'
                    : 'No contacts match this search.'}
                </p>
              ) : (
                <div className="contact-list">
                  {filteredContacts.map((contact) => (
                    <button
                      key={`${contact.id}-${contact.number}`}
                      type="button"
                      className="contact-item"
                      onClick={() => pickContact(contact.number)}
                    >
                      <span className="contact-avatar">{initials(contact.name)}</span>
                      <span className="contact-copy">
                        <strong>{contact.name}</strong>
                        <span>
                          {contact.label ? `${contact.label} · ` : ''}
                          {contact.number}
                        </span>
                      </span>
                    </button>
                  ))}
                </div>
              )}
              <p className="muted small">
                Contacts are requested on demand and kept in memory only.
              </p>
            </>
          )}
        </Card>
      </div>
    </>
  );
}
