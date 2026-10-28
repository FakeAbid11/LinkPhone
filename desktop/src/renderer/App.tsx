import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { AppSnapshot } from '../shared/types';
import { api } from './api';
import {
  Badge,
  Button,
  Logo,
  Modal,
  ToastStack,
  type ToastItem,
} from './components';
import type { Ui } from './ui';
import { errorMessage, formatFingerprint } from './utils';
import { AppsView } from './views/AppsView';
import { CallsView } from './views/CallsView';
import { ConnectView } from './views/ConnectView';
import { MessagesView } from './views/MessagesView';
import { NotificationsView } from './views/NotificationsView';
import { PhotosView } from './views/PhotosView';
import { SettingsView } from './views/SettingsView';

type ViewKey = 'connect' | 'messages' | 'notifications' | 'photos' | 'calls' | 'apps' | 'settings';

const NAV: Array<{ key: ViewKey; label: string }> = [
  { key: 'connect', label: 'Connection' },
  { key: 'messages', label: 'Messages' },
  { key: 'notifications', label: 'Notifications' },
  { key: 'photos', label: 'Photos' },
  { key: 'calls', label: 'Calls' },
  { key: 'apps', label: 'Apps' },
  { key: 'settings', label: 'Settings' },
];

export function App() {
  const [state, setState] = useState<AppSnapshot | null>(null);
  const [fatal, setFatal] = useState<string | null>(null);
  const [view, setView] = useState<ViewKey>('connect');
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const toastId = useRef(1);

  const dismissToast = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const pushToast = useCallback(
    (kind: ToastItem['kind'], message: string) => {
      const id = toastId.current++;
      setToasts((current) => [
        ...current.slice(-3),
        { id, kind, message: message.slice(0, 400) },
      ]);
      window.setTimeout(() => {
        setToasts((current) => current.filter((toast) => toast.id !== id));
      }, 5_000);
    },
    [],
  );

  const refresh = useCallback(async () => {
    try {
      setState(await api.getState());
    } catch (error) {
      pushToast('error', errorMessage(error));
    }
  }, [pushToast]);

  const run = useCallback(
    async (action: () => Promise<unknown>, success?: string) => {
      try {
        await action();
        if (success) pushToast('success', success);
      } catch (error) {
        pushToast('error', errorMessage(error));
      }
    },
    [pushToast],
  );

  const ui: Ui = useMemo(
    () => ({
      toast: pushToast,
      run,
      refresh,
    }),
    [pushToast, run, refresh],
  );

  useEffect(() => {
    let mounted = true;
    void api
      .getState()
      .then((snapshot) => {
        if (mounted) setState(snapshot);
      })
      .catch((error: unknown) => {
        if (mounted) setFatal(errorMessage(error));
      });
    const offState = api.onState((snapshot) => setState(snapshot));
    const offToast = api.onToast((toast) => {
      pushToast(toast.kind === 'error' ? 'error' : 'info', toast.message);
    });
    return () => {
      mounted = false;
      offState();
      offToast();
    };
  }, [pushToast]);

  if (fatal) {
    return (
      <div className="fatal">
        <Logo size={52} />
        <h1>LinkPhone could not start</h1>
        <p>{fatal}</p>
        <Button
          variant="primary"
          onClick={() => {
            setFatal(null);
            void refresh();
          }}
        >
          Try again
        </Button>
      </div>
    );
  }

  if (!state) {
    return (
      <div className="fatal">
        <Logo size={52} />
        <h1>LinkPhone</h1>
        <span className="spinner" />
      </div>
    );
  }

  const unread = state.sms.threads.reduce((sum, thread) => sum + thread.unread, 0);
  const notificationCount = state.notifications.length;
  const handoffCount = state.handoff.length;
  const request = state.pairing.request;
  const ringing = state.calls.state.state === 'ringing';

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <Logo />
          <div>
            <strong>LinkPhone</strong>
            <span>v{state.version}</span>
          </div>
        </div>

        <nav className="nav" aria-label="Sections">
          {NAV.map((item) => {
            const badge =
              item.key === 'messages' && unread > 0
                ? unread
                : item.key === 'notifications' && notificationCount > 0
                  ? notificationCount
                  : item.key === 'connect' && handoffCount > 0
                    ? handoffCount
                    : null;
            return (
              <button
                key={item.key}
                type="button"
                className={`nav-item ${view === item.key ? 'is-active' : ''}`}
                onClick={() => setView(item.key)}
              >
                <span className="nav-dot" />
                <span>{item.label}</span>
                {badge !== null && <span className="nav-badge">{badge > 99 ? '99+' : badge}</span>}
              </button>
            );
          })}
        </nav>

        <div className="sidebar-foot">
          <div className={`connect-state ${state.connection.connected ? 'is-online' : ''}`}>
            <span className="status-dot" />
            <div>
              <strong>
                {state.connection.connected
                  ? state.connection.deviceName ?? 'Phone connected'
                  : 'Not connected'}
              </strong>
              <span>
                {state.connection.connected
                  ? `Encrypted · ${state.server.addresses[0] ?? 'LAN'}`
                  : 'LAN only · no cloud'}
              </span>
            </div>
          </div>
          <span className="sidebar-note">
            {state.server.running
              ? `Port ${state.server.port} · TLS pinning`
              : 'Server stopped'}
          </span>
        </div>
      </aside>

      <main className="content">
        {ringing && (
          <div className="incoming-call" role="alert">
            <div>
              <strong>Incoming call</strong>
              <span>
                {state.calls.state.contactName ||
                  state.calls.state.number ||
                  'Your phone is ringing'}
              </span>
              <small>Audio stays on the phone.</small>
            </div>
            <div className="incoming-actions">
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
            </div>
          </div>
        )}

        {view === 'connect' && <ConnectView state={state} ui={ui} />}
        {view === 'messages' && <MessagesView state={state} ui={ui} />}
        {view === 'notifications' && <NotificationsView state={state} ui={ui} />}
        {view === 'photos' && <PhotosView state={state} ui={ui} />}
        {view === 'calls' && <CallsView state={state} ui={ui} />}
        {view === 'apps' && <AppsView state={state} ui={ui} />}
        {view === 'settings' && <SettingsView state={state} ui={ui} />}
      </main>

      <Modal
        open={Boolean(request)}
        title="Pair this phone?"
        footer={
          <>
            <Button
              variant="ghost"
              onClick={() => {
                if (request) void ui.run(() => api.rejectPairing(request.id), 'Pairing rejected');
              }}
            >
              Reject
            </Button>
            <Button
              variant="primary"
              autoFocus
              onClick={() => {
                if (request) void ui.run(() => api.approvePairing(request.id), 'Phone approved');
              }}
            >
              Approve phone
            </Button>
          </>
        }
      >
        {request && (
          <div className="pair-approval">
            <p>
              <strong>{request.name}</strong> wants to pair with this PC.
            </p>
            <div className="fingerprint-box">
              <span className="meta-label">Phone key fingerprint</span>
              <code>{formatFingerprint(request.fingerprint)}</code>
            </div>
            <div className="approval-meta">
              <Badge tone="neutral">{request.platform}</Badge>
              <span className="muted small">
                Only approve if you just scanned the pairing QR code shown on this PC and the
                phone is yours.
              </span>
            </div>
          </div>
        )}
      </Modal>

      <ToastStack toasts={toasts} onDismiss={dismissToast} />
    </div>
  );
}
