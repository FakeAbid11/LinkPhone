import { useEffect, useState } from 'react';
import type {
  AppSnapshot,
  HandoffUi,
  NetworkCheckResult,
  NetworkCheckStatus,
} from '../../shared/types';
import { api } from '../api';
import {
  Badge,
  Button,
  Card,
  Modal,
  Notice,
  PageHeader,
} from '../components';
import type { Ui } from '../ui';
import {
  batteryLabel,
  batteryTone,
  capabilityLabel,
  formatDateTime,
  formatExpiry,
  formatFingerprint,
  timeAgo,
} from '../utils';

export function ConnectView({ state, ui }: { state: AppSnapshot; ui: Ui }) {
  const [confirmRemove, setConfirmRemove] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const [, setTick] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => setTick((value) => value + 1), 1_000);
    return () => clearInterval(timer);
  }, []);

  const offer = state.pairing.offer;
  const connection = state.connection;
  const expired = offer ? offer.expiresAt <= Date.now() : false;
  // Hoisted so the narrowing survives the JSX that reads it several times.
  const check = state.networkCheck;

  const copy = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      ui.toast('success', `${label} copied`);
    } catch {
      ui.toast('error', `Could not copy the ${label.toLowerCase()}. Select the text manually.`);
    }
  };

  const firewallCommand = `New-NetFirewallRule -DisplayName "LinkPhone" -Direction Inbound -Protocol TCP -LocalPort ${state.server.port || 48921} -Action Allow -Profile Private`;

  // Deliberately not an effect: the check spawns PowerShell, so it runs when the
  // user asks and never on mount.
  const runCheck = async () => {
    setChecking(true);
    try {
      await ui.run(() => api.checkNetwork());
    } finally {
      setChecking(false);
    }
  };

  // The identity block shows where the PC is reachable *now*. The pairing card
  // shows what the code actually carries. Those diverge when the PC changes
  // network while a code is on screen — and it is the code that goes stale, so
  // the two are computed separately rather than assumed equal.
  const currentAddress =
    state.server.addresses.length > 0
      ? `${state.server.addresses[0]}:${state.server.port}`
      : '';
  const offerAddress = offer ? `${offer.host}:${offer.port}` : '';
  const offerIsStale = Boolean(offer && currentAddress && offerAddress !== currentAddress);

  return (
    <>
      <PageHeader
        title="Connection"
        subtitle="Pair once over your own Wi-Fi. Everything stays on your local network."
        actions={
          <Button onClick={() => void ui.refresh()} variant="ghost" icon="refresh">
            Refresh
          </Button>
        }
      />

      <div className="grid grid-2">
        <Card
          icon="wifi"
          title="Your PC"
          actions={
            <Badge tone={connection.connected ? 'good' : 'neutral'}>
              {connection.connected ? 'Phone connected' : 'Waiting for phone'}
            </Badge>
          }
        >
          <div className={`status-hero ${connection.connected ? 'is-online' : ''}`}>
            <span className="status-dot" />
            <div>
              <strong>
                {connection.connected
                  ? connection.deviceName ?? 'Android phone'
                  : 'No phone connected'}
              </strong>
              <span>
                {connection.connected
                  ? `${connection.phoneModel ?? 'Android'} · Android ${connection.phoneVersion ?? '?'}`
                  : 'Open LinkPhone on your phone, on the same Wi-Fi network.'}
              </span>
            </div>
          </div>

          {connection.connected && (
            <div className="meta-grid">
              <div>
                <span className="meta-label">Connected since</span>
                <span>{connection.since ? timeAgo(connection.since) : '—'}</span>
              </div>
              <div>
                <span className="meta-label">Phone app</span>
                <span>{connection.appVersion ?? '—'}</span>
              </div>
              <div>
                <span className="meta-label">Phone battery</span>
                <Badge tone={batteryTone(connection.batteryLevel, connection.batteryCharging)}>
                  {batteryLabel(connection.batteryLevel, connection.batteryCharging)}
                </Badge>
              </div>
            </div>
          )}

          <div className="capabilities">
            {connection.capabilities.length === 0 ? (
              <span className="muted">
                {!connection.connected
                  ? 'Feature permissions are granted on the phone.'
                  : connection.infoReceived
                    ? 'No feature permissions were granted on the phone. Grant them in the LinkPhone app.'
                    : 'Waiting for the phone to report its permissions…'}
              </span>
            ) : (
              connection.capabilities.map((capability) => (
                <Badge key={capability} tone="accent">
                  {capabilityLabel(capability)}
                </Badge>
              ))
            )}
          </div>

          <div className="identity-facts">
            <div>
              <span className="meta-label">PC name</span>
              <div className="copy-row">
                <code>{state.server.pcName}</code>
                <Button
                  small
                  variant="ghost"
                  onClick={() => void copy(state.server.pcName, 'PC name')}
                >
                  Copy
                </Button>
              </div>
            </div>
            <div>
              <span className="meta-label">Address</span>
              <div className="copy-row">
                <code>{currentAddress || 'No LAN address found'}</code>
                {currentAddress && (
                  <Button
                    small
                    variant="ghost"
                    onClick={() => void copy(currentAddress, 'Address')}
                  >
                    Copy
                  </Button>
                )}
              </div>
            </div>
            <div>
              <span className="meta-label">Certificate fingerprint</span>
              <div className="copy-row">
                <code className="fingerprint">
                  {formatFingerprint(state.server.fingerprint) || 'Not available'}
                </code>
                {state.server.fingerprint && (
                  <Button
                    small
                    variant="ghost"
                    onClick={() => void copy(state.server.fingerprint, 'Fingerprint')}
                  >
                    Copy
                  </Button>
                )}
              </div>
            </div>
          </div>

          {state.server.error && (
            <Notice tone="bad" title="Server problem">
              {state.server.error}
            </Notice>
          )}
        </Card>

        <Card
          icon="qr"
          title="Pair a phone"
          subtitle="The code is single-use and expires after five minutes."
          actions={
            offer ? (
              <Button
                variant="ghost"
                small
                onClick={() => void ui.run(() => api.cancelPairing())}
              >
                Cancel code
              </Button>
            ) : undefined
          }
        >
          {!offer ? (
            <div className="pair-empty">
              <p>
                Open LinkPhone on your PC and phone, then create a one-time code.
                You will approve the phone here before anything is shared.
              </p>
              <Button
                variant="primary"
                onClick={() => void ui.run(() => api.createPairing())}
                disabled={!state.server.running}
              >
                Create pairing code
              </Button>
              {state.pairing.error && (
                <Notice tone="bad" title="Pairing">
                  {state.pairing.error}
                </Notice>
              )}
            </div>
          ) : (
            <>
              {expired && (
                <Notice tone="bad" title="Code expired">
                  Create a new code to pair another phone.
                </Notice>
              )}
              {/* Without this, anything that goes wrong while a code is on
                  screen is a five-second toast and then nothing. */}
              {state.pairing.error && (
                <Notice tone="bad" title="Pairing">
                  {state.pairing.error}
                </Notice>
              )}
              {offerIsStale && (
                <Notice tone="warn" title="This code points at a different address">
                  The code carries <code>{offerAddress}</code>, but the PC is now at{' '}
                  <code>{currentAddress}</code>. A phone that scans it will not reach this PC —
                  create a new code.
                </Notice>
              )}
              <div className={`qr-frame ${expired ? 'is-expired' : ''}`}>
                <img src={offer.qrDataUrl} alt="LinkPhone pairing QR code" />
              </div>
              <div className="pair-facts">
                <div>
                  {/* What the QR actually contains, so it can be compared with
                      ipconfig on the PC without decoding the image. */}
                  <span className="meta-label">Address in this code</span>
                  <div className="copy-row">
                    <code>{offerAddress}</code>
                    <Button
                      small
                      variant="ghost"
                      onClick={() => void copy(offerAddress, 'Address')}
                    >
                      Copy
                    </Button>
                  </div>
                </div>
                <div>
                  <span className="meta-label">Pairing code</span>
                  <div className="copy-row">
                    <code>{offer.token}</code>
                    <Button small variant="ghost" onClick={() => void copy(offer.token, 'Code')}>
                      Copy
                    </Button>
                  </div>
                </div>
                <div className="expiry-row">
                  {expired ? (
                    <Badge tone="bad">Code expired</Badge>
                  ) : (
                    <Badge tone="good">Expires in {formatExpiry(offer.expiresAt)}</Badge>
                  )}
                  <Button
                    small
                    variant="secondary"
                    onClick={() =>
                      void ui.run(async () => {
                        // Drop the current token first, or the approval dialog
                        // for the old code stays on screen next to the new one.
                        await api.cancelPairing();
                        await api.createPairing();
                      })
                    }
                  >
                    New code
                  </Button>
                </div>
              </div>
              <Notice tone="neutral">
                Scan the code with the phone app, then approve the request that appears here.
                Only one phone can be connected at a time.
              </Notice>
            </>
          )}
        </Card>
      </div>

      <div className="grid grid-2">
        <Card icon="phone" title="Paired phones" subtitle="Remove a device to revoke its access.">
          {state.devices.length === 0 ? (
            <p className="muted">No phones are paired yet.</p>
          ) : (
            <ul className="device-list">
              {state.devices.map((device) => (
                <li key={device.id}>
                  <div className="device-avatar">{device.name.slice(0, 1).toUpperCase()}</div>
                  <div className="device-copy">
                    <strong>{device.name}</strong>
                    <span>
                      {device.platform} · paired {formatDateTime(device.pairedAt)}
                      {device.lastSeen ? ` · seen ${timeAgo(device.lastSeen)}` : ''}
                    </span>
                    <code className="mono small">
                      {formatFingerprint(device.fingerprint).slice(0, 47)}…
                    </code>
                  </div>
                  <Button
                    small
                    variant="danger"
                    onClick={() => setConfirmRemove(device.id)}
                  >
                    Remove
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card
          icon="shield"
          title="Network checklist"
          subtitle="Most pairing failures are firewall or Wi-Fi isolation."
          actions={
            <Button
              variant="secondary"
              icon="shield"
              loading={checking}
              onClick={() => void runCheck()}
            >
              Check this PC
            </Button>
          }
        >
          {check ? (
            <>
              <Notice tone={toneFor(check.verdict)} title={verdictTitle(check.verdict)}>
                {verdictBody(check)}
              </Notice>
              <ul className="net-check">
                {check.items.map((entry) => (
                  <li key={entry.id}>
                    <Badge tone={toneFor(entry.status)}>{STATUS_LABELS[entry.status]}</Badge>
                    <div className="net-check-copy">
                      <strong>{entry.title}</strong>
                      <span>
                        {entry.detail}
                        {entry.at ? ` Last seen ${timeAgo(entry.at)}.` : ''}
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
              <p className="muted small">Checked {timeAgo(check.at)}.</p>
            </>
          ) : (
            <ol className="checklist">
              <li>Both devices are on the same Wi-Fi and the network allows device-to-device traffic.</li>
              <li>
                Windows Firewall allows LinkPhone on <strong>Private</strong> networks, TCP port{' '}
                <strong>{state.server.port || 48921}</strong>.
              </li>
              <li>VPN software on either device is disconnected (it can block LAN discovery).</li>
              <li>The PC shows a local address above; if it does not, connect it to Wi-Fi or Ethernet.</li>
            </ol>
          )}
          <div className="code-block">
            <code>{firewallCommand}</code>
            <Button small variant="ghost" onClick={() => void copy(firewallCommand, 'Command')}>
              Copy command
            </Button>
          </div>
          <p className="muted small">
            Run that command in an Administrator PowerShell window once. Guest Wi-Fi and
            "AP isolation" block direct connections and cannot be fixed by the app — and the
            check above cannot see them either, because no Windows API reports them.
          </p>
        </Card>
      </div>

      <div className="grid grid-2">
        <Card
          icon="alert"
          title="Platform limits"
          subtitle="These limits come from Android, not from LinkPhone."
        >
          <ul className="limit-list">
            <li>
              <strong>Call audio on the PC.</strong> Android reserves in-call audio for system
              apps. LinkPhone controls the call, but audio stays on the phone.
            </li>
            <li>
              <strong>True app streaming.</strong> There is no public API to run Android apps in a
              Windows window. The Apps tab mirrors the screen and can send taps.
            </li>
            <li>
              <strong>Silent clipboard sync.</strong> Android only lets the focused app read the
              clipboard. Use the phone's Quick Settings tile or share action to send text here.
            </li>
          </ul>
        </Card>

        <Card
          icon="clipboard"
          title="Clipboard"
          subtitle="Text handoff in both directions, with Android's limits."
        >
          <div className="clipboard-row">
            <Button
              variant="primary"
              icon="send"
              disabled={!connection.connected}
              onClick={() =>
                void ui.run(() => api.sendPcClipboard(), 'PC clipboard sent to the phone')
              }
            >
              Send PC clipboard to phone
            </Button>
            <span className="muted small">
              {state.clipboard.lastReceived
                ? 'Text from the phone is on the PC clipboard.'
                : 'Phone → PC requires the Quick Settings tile, the share action, or the phone button.'}
            </span>
          </div>
          <p className="muted small">
            Android blocks background clipboard reads on the phone, so the PC cannot pull text
            silently. PC → phone writes are best-effort; the phone posts a “Copied from PC”
            notification when the background write needs a tap to confirm.
          </p>
        </Card>
      </div>

      <HandoffCard items={state.handoff} ui={ui} />

      <Modal
        open={confirmRemove !== null}
        title="Remove this phone?"
        onClose={() => setConfirmRemove(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmRemove(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                const id = confirmRemove;
                setConfirmRemove(null);
                if (id) void ui.run(() => api.removeDevice(id), 'Phone removed');
              }}
            >
              Remove phone
            </Button>
          </>
        }
      >
        <p>
          The phone will lose access immediately. To reconnect it, pair it again with a new QR
          code.
        </p>
      </Modal>
    </>
  );
}

function HandoffCard({ items, ui }: { items: HandoffUi[]; ui: Ui }) {
  const copyText = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      ui.toast('success', 'Text copied');
    } catch {
      ui.toast('error', 'Could not copy the text');
    }
  };

  return (
    <Card
      icon="arrow"
      title="Continue on PC"
      subtitle="Links, text, and images sent from the phone's share sheet."
    >
      {items.length === 0 ? (
        <p className="muted">
          On your phone, share a link, text, or image and choose <strong>LinkPhone</strong>. It
          arrives here without going through a cloud service.
        </p>
      ) : (
        <ul className="handoff-list">
          {items.map((item) => (
            <li key={item.id} className={`handoff-item handoff-${item.kind}`}>
              <div className="handoff-copy">
                <div className="handoff-top">
                  <span className="handoff-kind">
                    {item.kind === 'url'
                      ? 'Link'
                      : item.kind === 'image'
                        ? 'Image'
                        : 'Text'}
                  </span>
                  <span className="muted small">{timeAgo(item.time)}</span>
                </div>
                {item.kind === 'url' ? (
                  <span className="handoff-link">{item.url}</span>
                ) : item.kind === 'image' ? (
                  <span>{item.fileName ?? 'Image saved to the PC'}</span>
                ) : (
                  <p className="handoff-text">{item.text}</p>
                )}
              </div>
              <div className="handoff-actions">
                {item.kind === 'url' && item.url && (
                  <>
                    <Button small variant="primary" onClick={() => void ui.run(() => api.openUrl(item.url ?? ''))}>
                      Open on PC
                    </Button>
                    <Button
                      small
                      variant="ghost"
                      onClick={() => void ui.run(() => api.openOnPhone(item.url ?? ''), 'Sent to phone')}
                    >
                      Send back
                    </Button>
                  </>
                )}
                {item.kind === 'text' && item.text && (
                  <Button small variant="secondary" onClick={() => void copyText(item.text ?? '')}>
                    Copy
                  </Button>
                )}
                {item.kind === 'image' && item.savedPath && (
                  <Button
                    small
                    variant="secondary"
                    onClick={() => void ui.run(() => api.showFile(item.savedPath ?? ''))}
                  >
                    Show file
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      <Notice tone="neutral">
        The PC never opens shared links automatically. Images are saved to
        Downloads\\LinkPhone. Links can be sent back to the phone, where tapping the notification
        opens them.
      </Notice>
    </Card>
  );
}

type Tone = 'neutral' | 'good' | 'warn' | 'bad';

const STATUS_LABELS: Record<NetworkCheckStatus, string> = {
  ok: 'OK',
  warn: 'Warning',
  bad: 'Blocked',
  unknown: 'Not checked',
};

/** `unknown` is deliberately grey: an unverifiable check must not look like a failure. */
function toneFor(status: NetworkCheckStatus): Tone {
  return status === 'ok' ? 'good' : status === 'warn' ? 'warn' : status === 'bad' ? 'bad' : 'neutral';
}

function verdictTitle(verdict: NetworkCheckStatus): string {
  if (verdict === 'ok') return 'This PC looks ready';
  if (verdict === 'warn') return 'One thing to check';
  if (verdict === 'bad') return 'This PC is blocking the connection';
  return 'The check could not run';
}

/** The first failing check already says what to do; do not paraphrase it. */
function verdictBody(result: NetworkCheckResult): string {
  const first =
    result.items.find((item) => item.status === 'bad') ??
    result.items.find((item) => item.status === 'warn');
  return first?.detail ?? 'Every check Windows can make passed.';
}
