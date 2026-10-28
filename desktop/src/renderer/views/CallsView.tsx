import { useState } from 'react';
import type { AppSnapshot } from '../../shared/types';
import { api } from '../api';
import { Badge, Button, Card, EmptyState, Notice, PageHeader } from '../components';
import type { Ui } from '../ui';

export function CallsView({ state, ui }: { state: AppSnapshot; ui: Ui }) {
  const connected = state.connection.connected;
  const supported = state.connection.capabilities.includes('calls');
  const call = state.calls.state;
  const ringing = call.state === 'ringing';
  const inCall = call.state === 'offhook';
  const [number, setNumber] = useState('');

  if (!connected) {
    return (
      <>
        <PageHeader title="Calls" subtitle="Control calls from the PC. Audio stays on the phone." />
        <Card>
          <EmptyState
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

      <Notice tone="warn" title="Audio stays on the phone">
        Use the phone's speakerphone or a Bluetooth headset. This is a platform restriction, not
        a missing feature.
      </Notice>

      <div className="grid grid-2">
        <Card
          title="Current call"
          actions={
            <Badge tone={ringing ? 'warn' : inCall ? 'good' : 'neutral'}>
              {ringing ? 'Incoming' : inCall ? 'In progress' : 'Idle'}
            </Badge>
          }
        >
          <div className={`call-hero ${ringing ? 'is-ringing' : ''}`}>
            <span className="call-pulse" />
            <div>
              <strong>
                {call.contactName || call.number || (ringing ? 'Incoming call' : 'No active call')}
              </strong>
              <span>
                {call.contactName && call.number
                  ? call.number
                  : ringing
                    ? 'Your phone is ringing'
                    : inCall
                      ? 'The call is active on your phone'
                      : 'Dial a number or wait for an incoming call'}
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
            {!ringing && !inCall && (
              <p className="muted small">
                Incoming calls appear here and as a PC notification while LinkPhone is connected.
              </p>
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

        <Card title="Dial a call" subtitle="The call is placed by the phone's dialer/telecom service.">
          <form
            className="dialer"
            onSubmit={(event) => {
              event.preventDefault();
              if (!number.trim()) return;
              void ui.run(() => api.dial(number), 'Calling from your phone');
            }}
          >
            <label className="field-label" htmlFor="dial-number">
              Phone number
            </label>
            <input
              id="dial-number"
              type="tel"
              inputMode="tel"
              placeholder="+1 555 0100"
              value={number}
              onChange={(event) => setNumber(event.target.value)}
            />
            <Button variant="primary" type="submit" disabled={!number.trim()}>
              Call from phone
            </Button>
          </form>
          <p className="muted small">
            Emergency calls are handled by Android's telecom stack. LinkPhone does not place
            emergency calls itself.
          </p>
        </Card>
      </div>
    </>
  );
}
