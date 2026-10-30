import { useEffect, useMemo, useRef, useState } from 'react';
import { CAPABILITIES, type AppSnapshot, type SmsThreadUi } from '../../shared/types';
import { api } from '../api';
import {
  Button,
  Card,
  EmptyState,
  Notice,
  PageHeader,
  Spinner,
  TextInput,
} from '../components';
import type { Ui } from '../ui';
import { filterThreads } from '../thread-filter';
import { formatDateTime, formatTime, initials, errorMessage, timeAgo } from '../utils';

export function MessagesView({ state, ui }: { state: AppSnapshot; ui: Ui }) {
  const connected = state.connection.connected;
  const supported = state.connection.capabilities.includes('sms');
  const threads = state.sms.threads;
  const totalUnread = threads.reduce((sum, thread) => sum + thread.unread, 0);
  const canOpenOnPhone = state.connection.capabilities.includes(
    CAPABILITIES.SMS_OPEN_THREAD,
  );
  const [selected, setSelected] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [subId, setSubId] = useState<number | null>(null);
  const [sending, setSending] = useState(false);
  const [query, setQuery] = useState('');
  const scrollRef = useRef<HTMLDivElement>(null);
  const loadedRef = useRef(false);
  const markingRef = useRef(new Set<string>());

  const filteredThreads = useMemo(() => filterThreads(threads, query), [threads, query]);

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
    const element = scrollRef.current;
    if (element) element.scrollTop = element.scrollHeight;
  }, [active?.address, active?.messages.length]);

  const send = async () => {
    if (!active || !draft.trim() || sending) return;
    setSending(true);
    const body = draft;
    setDraft('');
    try {
      // Omit subId unless the user picked a SIM, so the phone uses its
      // system-default subscription instead of whichever one we guessed.
      await api.sendSms(active.address, body, subId ?? undefined);
      ui.toast('success', 'Message sent to the phone');
    } catch (error) {
      // Put the text back: losing it because the phone refused is worse than
      // reading the error twice.
      setDraft((current) => (current ? current : body));
      ui.toast('error', errorMessage(error));
    } finally {
      setSending(false);
    }
  };

  const openThread = (thread: SmsThreadUi) => {
    setSelected(thread.address);
    if (thread.unread <= 0 || markingRef.current.has(thread.address)) return;
    markingRef.current.add(thread.address);
    void ui
      .run(() => api.markSmsRead(thread.address))
      .finally(() => markingRef.current.delete(thread.address));
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
        subtitle={`${threads.length} conversation${threads.length === 1 ? '' : 's'} on your phone · unread tracked on this PC`}
        actions={
          <>
            {totalUnread > 0 && (
              <Button
                variant="ghost"
                icon="check"
                onClick={() =>
                  void ui.run(
                    () => api.markAllSmsRead(),
                    'All conversations marked read on this PC',
                  )
                }
              >
                Mark all read
              </Button>
            )}
            <Button
              variant="ghost"
              icon="refresh"
              onClick={() => void ui.run(() => api.refreshSms())}
              loading={state.sms.refreshing}
            >
              Refresh
            </Button>
          </>
        }
      />

      {(!canOpenOnPhone || state.sms.error) && (
        <div className="messages-banner">
          {!canOpenOnPhone && (
            <p className="muted small messages-hint">
              Phone read sync needs the v0.1.2 phone app. Opening a conversation clears its unread
              badge in LinkPhone.
            </p>
          )}

          {state.sms.error && (
            <Notice tone="bad" title="Messages">
              {state.sms.error}
            </Notice>
          )}
        </div>
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
          <aside className="thread-list" aria-label="Conversations">
            <div className="thread-tools">
              <div className="thread-list-head">
                <span>Conversations</span>
                {state.sms.refreshing && <span className="live-dot" aria-label="Refreshing" />}
              </div>
              <div className="thread-search">
                <TextInput
                  id="thread-search"
                  type="search"
                  placeholder="Search conversations"
                  aria-label="Search conversations"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                />
              </div>
            </div>
            {filteredThreads.length === 0 ? (
              <p className="thread-search-empty">
                No conversations match “{query.trim()}”.
              </p>
            ) : (
              filteredThreads.map((thread) => (
                <button
                  key={thread.address}
                  type="button"
                  className={`thread-item ${active?.address === thread.address ? 'is-active' : ''}`}
                  aria-current={active?.address === thread.address ? 'true' : undefined}
                  onClick={() => openThread(thread)}
                >
                  <span className="thread-avatar">{initials(thread.displayName)}</span>
                  <span className="thread-copy">
                    <span className="thread-top">
                      <strong>{thread.displayName}</strong>
                      <span>{timeAgo(thread.latest)}</span>
                    </span>
                    <span className="thread-preview">
                      {thread.messages[thread.messages.length - 1]?.body || '(no text)'}
                    </span>
                  </span>
                  {thread.unread > 0 && (
                    <span className="unread" aria-label={`${thread.unread} unread`}>
                      {thread.unread}
                    </span>
                  )}
                </button>
              ))
            )}
          </aside>

          <section className="chat-panel">
            <header className="chat-head">
              <div>
                <strong>{active?.displayName}</strong>
                <span>{active?.messages.length ?? 0} messages</span>
              </div>
              <div className="chat-head-actions">
                <span className="muted small">
                  {active ? formatDateTime(active.latest) : ''}
                </span>
                {active && canOpenOnPhone && (
                  <Button
                    small
                    variant="secondary"
                    icon="arrow"
                    title="Opens this conversation in your phone's SMS app so that app can mark it read."
                    onClick={() =>
                      void ui.run(
                        () => api.openSmsThreadOnPhone(active.address),
                        'Tap the LinkPhone notification on your phone to open the SMS app',
                      )
                    }
                  >
                    Open on phone
                  </Button>
                )}
              </div>
            </header>

            <div
              className="chat-scroll"
              ref={scrollRef}
              role="log"
              aria-label={`Conversation with ${active?.displayName ?? 'contact'}`}
            >
              {active?.messages.map((message) => {
                const outgoing = message.type === 2 || message.type === 4 || message.type === 6;
                return (
                  <div
                    key={message.id}
                    className={`msg-row ${outgoing ? 'is-out' : 'is-in'}`}
                  >
                    <div className={`msg-bubble ${message.failed ? 'is-failed' : ''}`}>
                      {message.body ? (
                        <p>{message.body}</p>
                      ) : (
                        <p className="muted">(no text — MMS or unsupported message)</p>
                      )}
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
                        <option value="">Default SIM</option>
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
                  icon="send"
                  onClick={() => void send()}
                  loading={sending}
                  disabled={!draft.trim()}
                >
                  Send
                </Button>
              </div>
            </footer>
          </section>
        </div>
      )}
    </>
  );
}
