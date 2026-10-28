import { useEffect, useMemo, useRef, useState } from 'react';
import type { AppSnapshot } from '../../shared/types';
import { api } from '../api';
import { Button, Card, EmptyState, Notice, PageHeader, Spinner } from '../components';
import type { Ui } from '../ui';
import { formatDateTime, formatTime, initials, timeAgo } from '../utils';

export function MessagesView({ state, ui }: { state: AppSnapshot; ui: Ui }) {
  const connected = state.connection.connected;
  const supported = state.connection.capabilities.includes('sms');
  const threads = state.sms.threads;
  const [selected, setSelected] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [subId, setSubId] = useState<number | null>(null);
  const [sending, setSending] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const loadedRef = useRef(false);

  const active = useMemo(
    () => threads.find((thread) => thread.address === selected) ?? threads[0],
    [threads, selected],
  );

  useEffect(() => {
    if (active && active.address !== selected) setSelected(active.address);
  }, [active, selected]);

  useEffect(() => {
    if (!connected || !supported || loadedRef.current) return;
    loadedRef.current = true;
    void ui.run(() => api.refreshSms());
  }, [connected, supported, ui]);

  useEffect(() => {
    if (!connected || !supported) loadedRef.current = false;
  }, [connected, supported]);

  useEffect(() => {
    if (subId === null && state.connection.subscriptions.length > 0) {
      setSubId(state.connection.subscriptions[0].subscriptionId);
    }
  }, [state.connection.subscriptions, subId]);

  useEffect(() => {
    const element = scrollRef.current;
    if (element) element.scrollTop = element.scrollHeight;
  }, [active?.address, active?.messages.length]);

  const send = async () => {
    if (!active || !draft.trim() || sending) return;
    setSending(true);
    const body = draft;
    setDraft('');
    await ui.run(
      () => api.sendSms(active.address, body, subId ?? undefined),
      'Message sent to the phone',
    );
    setSending(false);
  };

  if (!connected) {
    return (
      <>
        <PageHeader title="Messages" subtitle="Read and send SMS from your PC." />
        <Card>
          <EmptyState
            title="Connect your phone first"
            body="Messages stay on the phone. LinkPhone reads them over the encrypted LAN connection and only when you have granted SMS access."
          />
        </Card>
      </>
    );
  }

  if (!supported) {
    return (
      <>
        <PageHeader title="Messages" subtitle="Read and send SMS from your PC." />
        <Card>
          <EmptyState
            title="SMS permission is not granted"
            body="On the phone, open LinkPhone, grant Messages access, then return here. Android treats SMS as a sensitive permission, so it is never granted silently."
          />
          <Notice tone="warn" title="Sideload only">
            LinkPhone reads the standard Android SMS provider. It is not distributed through
            Google Play and does not support MMS or RCS.
          </Notice>
        </Card>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Messages"
        subtitle={`${threads.length} conversation${threads.length === 1 ? '' : 's'} on your phone`}
        actions={
          <Button
            variant="ghost"
            onClick={() => void ui.run(() => api.refreshSms())}
            disabled={state.sms.refreshing}
          >
            {state.sms.refreshing ? 'Refreshing…' : 'Refresh'}
          </Button>
        }
      />

      {state.sms.error && (
        <Notice tone="bad" title="Messages">
          {state.sms.error}
        </Notice>
      )}

      {threads.length === 0 ? (
        <Card>
          {state.sms.loading ? (
            <Spinner label="Loading messages…" />
          ) : (
            <EmptyState
              title="No messages found"
              body="LinkPhone only reads existing SMS threads. New messages will appear here as they arrive."
            />
          )}
        </Card>
      ) : (
        <div className="messages-layout">
          <aside className="thread-list">
            <div className="thread-list-head">
              <span>Conversations</span>
              {state.sms.refreshing && <span className="live-dot" />}
            </div>
            {threads.map((thread) => (
              <button
                key={thread.address}
                type="button"
                className={`thread-item ${active?.address === thread.address ? 'is-active' : ''}`}
                onClick={() => setSelected(thread.address)}
              >
                <span className="thread-avatar">{initials(thread.displayName)}</span>
                <span className="thread-copy">
                  <span className="thread-top">
                    <strong>{thread.displayName}</strong>
                    <span>{timeAgo(thread.latest)}</span>
                  </span>
                  <span className="thread-preview">
                    {thread.messages[thread.messages.length - 1]?.body ?? ''}
                  </span>
                </span>
                {thread.unread > 0 && <span className="unread">{thread.unread}</span>}
              </button>
            ))}
          </aside>

          <section className="chat-panel">
            <header className="chat-head">
              <div>
                <strong>{active?.displayName}</strong>
                <span>{active?.messages.length ?? 0} messages</span>
              </div>
              <span className="muted small">
                {active ? formatDateTime(active.latest) : ''}
              </span>
            </header>

            <div className="chat-scroll" ref={scrollRef}>
              {active?.messages.map((message) => {
                const outgoing = message.type === 2 || message.type === 4 || message.type === 6;
                return (
                  <div
                    key={message.id}
                    className={`msg-row ${outgoing ? 'is-out' : 'is-in'}`}
                  >
                    <div className={`msg-bubble ${message.failed ? 'is-failed' : ''}`}>
                      <p>{message.body}</p>
                      <span className="msg-time">
                        {formatTime(message.date)}
                        {message.local ? ' · sent by LinkPhone' : ''}
                        {message.failed ? ' · failed' : ''}
                      </span>
                    </div>
                  </div>
                );
              })}
              {active && active.messages.length === 0 && (
                <p className="muted">No messages in this thread.</p>
              )}
            </div>

            <footer className="composer">
              <textarea
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                placeholder="Write a message…"
                rows={2}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault();
                    void send();
                  }
                }}
              />
              <div className="composer-row">
                <div className="composer-meta">
                  {state.connection.subscriptions.length > 1 && (
                    <label className="inline-field">
                      <span>SIM</span>
                      <select
                        value={subId ?? ''}
                        onChange={(event) => setSubId(Number(event.target.value) || null)}
                      >
                        {state.connection.subscriptions.map((subscription) => (
                          <option
                            key={subscription.subscriptionId}
                            value={subscription.subscriptionId}
                          >
                            {subscription.label}
                          </option>
                        ))}
                      </select>
                    </label>
                  )}
                  <span className="muted small">
                    Sent through your phone. Sent items may not appear in its stock SMS app.
                  </span>
                </div>
                <Button
                  variant="primary"
                  onClick={() => void send()}
                  disabled={!draft.trim() || sending}
                >
                  {sending ? 'Sending…' : 'Send'}
                </Button>
              </div>
            </footer>
          </section>
        </div>
      )}
    </>
  );
}
