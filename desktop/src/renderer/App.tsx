import {
  Component,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import type { AppSnapshot } from '../shared/types';
import { api } from './api';
import {
  Badge,
  Button,
  Icon,
  Logo,
  Modal,
  ToastStack,
  type IconName,
  type ToastItem,
} from './components';
import type { Ui } from './ui';
import { batteryLabel, batteryTone, errorMessage, formatFingerprint } from './utils';
import { AppsView } from './views/AppsView';
import { CallsView } from './views/CallsView';
import { ConnectView } from './views/ConnectView';
import { MessagesView } from './views/MessagesView';
import { NotificationsView } from './views/NotificationsView';
import { PhotosView } from './views/PhotosView';
import { SettingsView } from './views/SettingsView';

type ViewKey = 'connect' | 'messages' | 'notifications' | 'photos' | 'calls' | 'apps' | 'settings';

const NAV_GROUPS: Array<{
  label: string;
  items: Array<{ key: ViewKey; label: string; icon: IconName }>;
}> = [
  {
    label: 'Overview',
    items: [{ key: 'connect', label: 'Connection', icon: 'link' }],
  },
  {
    label: 'Phone',
    items: [
      { key: 'messages', label: 'Messages', icon: 'message' },
      { key: 'notifications', label: 'Notifications', icon: 'bell' },
      { key: 'photos', label: 'Photos', icon: 'image' },
      { key: 'calls', label: 'Calls', icon: 'phone' },
      { key: 'apps', label: 'Apps', icon: 'apps' },
    ],
  },
  {
    label: 'App',
    items: [{ key: 'settings', label: 'Settings', icon: 'settings' }],
  },
];

function AppShell() {
  const [state, setState] = useState<AppSnapshot | null>(null);
  const [fatal, setFatal] = useState<string | null>(null);
  const [view, setView] = useState<ViewKey>('connect');
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const [updateBannerHidden, setUpdateBannerHidden] = useState(false);
  const toastId = useRef(1);
  const mainRef = useRef<HTMLElement>(null);
  /** Counts pushed snapshots so a slow initial getState() cannot overwrite one. */
  const stateGeneration = useRef(0);

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
    const generation = stateGeneration.current;
    void api
      .getState()
      .then((snapshot) => {
        // A pushed snapshot may have arrived while this request was in flight;
        // that one is newer, so do not clobber it with the older reply.
        if (mounted && stateGeneration.current === generation) setState(snapshot);
      })
      .catch((error: unknown) => {
        if (mounted) setFatal(errorMessage(error));
      });
    const offState = api.onState((snapshot) => {
      stateGeneration.current += 1;
      setState(snapshot);
    });
    const offToast = api.onToast((toast) => {
      pushToast(toast.kind === 'error' ? 'error' : 'info', toast.message);
    });
    return () => {
      mounted = false;
      offState();
      offToast();
    };
  }, [pushToast]);

  const navigate = useCallback((next: ViewKey) => {
    setView(next);
    window.requestAnimationFrame(() => mainRef.current?.focus());
  }, []);

  useEffect(() => {
    if (!state || state.updates.phase !== 'ready') setUpdateBannerHidden(false);
  }, [state]);

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
  const notificationCount = state.notifications.items.length;
  const handoffCount = state.handoff.length;
  const request = state.pairing.request;
  const ringing = state.calls.state.state === 'ringing';
  const inCall = state.calls.state.state === 'offhook';
  const activeCall = ringing || inCall;
  const offline = !state.connection.connected && state.devices.length > 0;

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main">
        Skip to main content
      </a>

      <aside className="sidebar">
        <div className="brand">
          <Logo />
          <div>
            <strong>LinkPhone</strong>
            <span>v{state.version}</span>
          </div>
        </div>

        <nav className="nav" aria-label="Sections">
          {NAV_GROUPS.map((group) => (
            <div className="nav-group" key={group.label}>
              <span className="nav-group-label" aria-hidden="true">
                {group.label}
              </span>
              {group.items.map((item) => {
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
                    aria-current={view === item.key ? 'page' : undefined}
                    onClick={() => navigate(item.key)}
                  >
                    <Icon name={item.icon} size={18} />
                    <span className="nav-label">{item.label}</span>
                    {badge !== null && (
                      <span className="nav-badge" aria-label={`${badge} new`}>
                        {badge > 99 ? '99+' : badge}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          ))}
        </nav>

        <div className="sidebar-foot">
          <div className={`connect-state ${state.connection.connected ? 'is-online' : ''}`}>
            <span className="status-dot" />
            <div>
              <strong>
                {state.connection.connected
                  ? (state.connection.deviceName ?? 'Phone connected')
                  : 'Not connected'}
              </strong>
              <span>
                {state.connection.connected
                  ? `Encrypted · ${state.server.addresses[0] ?? 'LAN'}`
                  : 'LAN only · no cloud'}
              </span>
              {state.connection.connected && state.connection.batteryLevel !== undefined && (
                <span
                  className={`battery-chip battery-${batteryTone(
                    state.connection.batteryLevel,
                    state.connection.batteryCharging,
                  )}`}
                >
                  <Icon name="battery" size={12} />
                  {batteryLabel(state.connection.batteryLevel, state.connection.batteryCharging)}
                </span>
              )}
            </div>
          </div>
          <span className="sidebar-note">
            {state.server.running ? `Port ${state.server.port} · TLS pinning` : 'Server stopped'}
          </span>
        </div>
      </aside>

      <main
        className={`content ${view === 'messages' ? 'is-fill' : ''}`}
        id="main"
        ref={mainRef}
        tabIndex={-1}
      >
        {offline && (
          <div className="offline-banner" role="status">
            <span className="banner-icon">
              <Icon name="wifi" size={18} />
            </span>
            <div className="banner-copy">
              <strong>Phone offline</strong>
              <span>
                {state.devices[0]?.name ?? 'Your paired phone'} is not connected. Keep LinkPhone
                open on the phone and stay on the same Wi-Fi.
              </span>
            </div>
            <Button small variant="secondary" icon="link" onClick={() => navigate('connect')}>
              Connection
            </Button>
          </div>
        )}

        {state.updates.phase === 'ready' && !updateBannerHidden && (
          <div className="offline-banner" role="status">
            <span className="banner-icon">
              <Icon name="download" size={18} />
            </span>
            <div className="banner-copy">
              <strong>Update ready</strong>
              <span>
                LinkPhone {state.updates.version} is downloaded. Restart to install it.
              </span>
            </div>
            <Button
              small
              variant="primary"
              onClick={() =>
                void ui.run(() => api.installUpdate(), 'Restarting to install the update')
              }
            >
              Restart
            </Button>
            <Button small variant="ghost" onClick={() => setUpdateBannerHidden(true)}>
              Later
            </Button>
          </div>
        )}

        {activeCall && view !== 'calls' && (
          <div className="incoming-call" role={ringing ? 'alert' : 'status'}>
            <span className="incoming-icon">
              <Icon name="phone" size={20} />
            </span>
            <div className="incoming-copy">
              <strong>{ringing ? 'Incoming call' : 'Call in progress'}</strong>
              <span>
                {state.calls.state.contactName ||
                  state.calls.state.number ||
                  (ringing ? 'Your phone is ringing' : 'Active on your phone')}
              </span>
              <small>
                Audio stays on the phone.
                {!state.calls.answerSupported
                  ? ' Grant Answer calls on the phone to end it from here.'
                  : ''}
              </small>
            </div>
            <div className="incoming-actions">
              {ringing ? (
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
              ) : (
                <>
                  {state.calls.speakerSupported && (
                    <Button
                      small
                      variant={state.calls.state.speakerOn ? 'secondary' : 'ghost'}
                      icon="speaker"
                      onClick={() =>
                        void ui.run(
                          () => api.setCallSpeaker(!state.calls.state.speakerOn),
                          state.calls.state.speakerOn
                            ? 'Speaker off requested'
                            : 'Speaker on requested',
                        )
                      }
                    >
                      {state.calls.state.speakerOn ? 'Speaker on' : 'Speaker off'}
                    </Button>
                  )}
                  <Button
                    variant="danger"
                    disabled={!state.calls.answerSupported}
                    onClick={() =>
                      void ui.run(() => api.declineCall(), 'End request sent to the phone')
                    }
                  >
                    End call
                  </Button>
                </>
              )}
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

interface ErrorBoundaryState {
  error: string | null;
}

/**
 * Keeps a render error from leaving an empty window. React logs the failure
 * itself; this only gives the user a way back without restarting the app.
 */
class AppErrorBoundary extends Component<{ children: ReactNode }, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: unknown): ErrorBoundaryState {
    return { error: error instanceof Error ? error.message : String(error) };
  }

  render(): ReactNode {
    if (this.state.error) {
      return (
        <div className="fatal">
          <Logo size={52} />
          <h1>LinkPhone hit an unexpected error</h1>
          <p>{this.state.error}</p>
          <Button variant="primary" onClick={() => this.setState({ error: null })}>
            Try again
          </Button>
        </div>
      );
    }
    return this.props.children;
  }
}

export function App() {
  return (
    <AppErrorBoundary>
      <AppShell />
    </AppErrorBoundary>
  );
}
