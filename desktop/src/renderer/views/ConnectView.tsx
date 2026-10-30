import { useEffect, useState } from 'react';
import type { AppSnapshot, HandoffUi } from '../../shared/types';
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
  const [, setTick] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => setTick((value) => value + 1), 1_000);
    return () => clearInterval(timer);
  }, []);

  const offer = state.pairing.offer;
  const connection = state.connection;
  const expired = offer ? offer.expiresAt <= Date.now() : false;

  const copy = async (text: string, label: string) => {
    try {
      await navigator.clipboard.writeText(text);
      ui.toast('success', `${label} copied`);
    } catch {
      ui.toast('error', `Could not copy the ${label.toLowerCase()}. Select the text manually.`);
    }
  };

  const firewallCommand = `New-NetFirewallRule -DisplayName "LinkPhone" -Direction Inbound -Protocol TCP -LocalPort ${state.server.port || 48921} -Action Allow -Profile Private`;

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

          <div className="server-facts">
            <div>
              <span className="meta-label">PC address</span>
              <span>
                {state.server.addresses.length > 0
                  ? `${state.server.addresses[0]}:${state.server.port}`
                  : 'No LAN address found'}
              </span>
            </div>
            <div>
              <span className="meta-label">PC name</span>
              <span>{state.server.pcName}</span>
            </div>
            <div>
              <span className="meta-label">Certificate</span>
              <span className="mono">{formatFingerprint(state.server.fingerprint)}</span>
            </div>
          </div>

          {state.server.error && (
            <Notice tone="bad" title="Server problem">
              {state.server.error}
            </Notice>
          )}

          {!connection.connected && state.devices.length === 0 && (
            <Notice tone="neutral">
              Scan the QR code on the right from the LinkPhone Android app to pair.
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
              <div className={`qr-frame ${expired ? 'is-expired' : ''}`}>
                <img src={offer.qrDataUrl} alt="LinkPhone pairing QR code" />
              </div>
              <div className="pair-facts">
                <div>
                  <span className="meta-label">Pairing code</span>
                  <div className="copy-row">
                    <code>{offer.token}</code>
                    <Button small variant="ghost" onClick={() => void copy(offer.token, 'Code')}>
                      Copy
                    </Button>
                  </div>
                </div>
                <div>
                  <span className="meta-label">Address</span>
                  <div className="copy-row">
                    <code>
                      {offer.host}:{offer.port}
                    </code>
                    <Button
                      small
                      variant="ghost"
                      onClick={() => void copy(`${offer.host}:${offer.port}`, 'Address')}
                    >
                      Copy
                    </Button>
                  </div>
                </div>
                <div>
                  <span className="meta-label">Certificate fingerprint</span>
                  <div className="copy-row">
                    <code className="fingerprint">{formatFingerprint(offer.fingerprint)}</code>
                    <Button
                      small
                      variant="ghost"
                      onClick={() => void copy(offer.fingerprint, 'Fingerprint')}
                    >
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

        <Card icon="shield" title="Network checklist" subtitle="Most pairing failures are firewall or Wi-Fi isolation.">
          <ol className="checklist">
            <li>Both devices are on the same Wi-Fi and the network allows device-to-device traffic.</li>
            <li>
              Windows Firewall allows LinkPhone on <strong>Private</strong> networks, TCP port{' '}
              <strong>{state.server.port || 48921}</strong>.
            </li>
            <li>VPN software on either device is disconnected (it can block LAN discovery).</li>
            <li>The PC shows a local address above; if it does not, connect it to Wi-Fi or Ethernet.</li>
          </ol>
          <div className="code-block">
            <code>{firewallCommand}</code>
            <Button small variant="ghost" onClick={() => void copy(firewallCommand, 'Command')}>
              Copy command
            </Button>
          </div>
          <p className="muted small">
            Run that command in an Administrator PowerShell window once. Guest Wi-Fi and
            "AP isolation" block direct connections and cannot be fixed by the app.
          </p>
        </Card>
      </div>

      <Card icon="alert" title="Platform limits" subtitle="These limits come from Android, not from LinkPhone.">
        <details className="limit-details">
          <summary>What LinkPhone cannot do</summary>
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
        </details>
      </Card>

      <HandoffCard items={state.handoff} ui={ui} />

      <Card icon="clipboard" title="Clipboard" subtitle="Text handoff in both directions, with Android's limits.">
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
