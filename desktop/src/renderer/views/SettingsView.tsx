import { useState } from 'react';
import type { AppSnapshot } from '../../shared/types';
import { api } from '../api';
import {
  Badge,
  Button,
  Card,
  Modal,
  Notice,
  PageHeader,
  Toggle,
} from '../components';
import type { Ui } from '../ui';
import { formatDateTime, formatFingerprint, timeAgo } from '../utils';

export function SettingsView({ state, ui }: { state: AppSnapshot; ui: Ui }) {
  const [confirmReset, setConfirmReset] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState<string | null>(null);

  const update = (patch: Parameters<typeof api.updateSettings>[0]) =>
    void ui.run(() => api.updateSettings(patch));

  return (
    <>
      <PageHeader
        title="Settings"
        subtitle="Preferences, paired devices, and security controls."
      />

      <div className="grid grid-2">
        <Card icon="settings" title="Behavior">
          <Toggle
            checked={state.settings.notificationsToasts}
            onChange={(value) => update({ notificationsToasts: value })}
            label="Desktop notifications"
            description="Show a Windows notification when the phone sends a mirrored notification while LinkPhone is in the background."
          />
          <Toggle
            checked={state.settings.autoClipboard}
            onChange={(value) => update({ autoClipboard: value })}
            label="Auto-send PC clipboard"
            description="Checks the PC clipboard while connected and sends changes to the phone. The reverse direction is always explicit, because Android blocks background clipboard reads."
          />
          <Toggle
            checked={state.settings.launchAtLogin}
            onChange={(value) => update({ launchAtLogin: value })}
            label="Start LinkPhone with Windows"
            description="Keeps the tray app running so the phone can reconnect after a reboot."
          />
          <p className="muted small">
            LinkPhone is a tray app. Closing the window keeps the connection alive; use Quit from
            the tray menu to stop it.
          </p>
        </Card>

        <Card icon="shield" title="Server & security">
          <div className="settings-facts">
            <div>
              <span className="meta-label">Status</span>
              <Badge tone={state.server.running ? 'good' : 'bad'}>
                {state.server.running ? `Listening on ${state.server.port}` : 'Stopped'}
              </Badge>
            </div>
            <div>
              <span className="meta-label">PC name</span>
              <span>{state.server.pcName}</span>
            </div>
            <div>
              <span className="meta-label">Local addresses</span>
              <span>{state.server.addresses.join(', ') || 'None detected'}</span>
            </div>
            <div>
              <span className="meta-label">TLS certificate</span>
              <span className="mono small">{formatFingerprint(state.server.fingerprint)}</span>
            </div>
            <div>
              <span className="meta-label">App version</span>
              <span>LinkPhone {state.version} · protocol 1</span>
            </div>
          </div>
          {state.server.error && <Notice tone="bad">{state.server.error}</Notice>}
          <div className="settings-actions">
            <Button variant="ghost" icon="clipboard" onClick={() => void ui.run(() => api.openLogs())}>
              Open logs
            </Button>
            <Button variant="ghost" icon="arrow" onClick={() => void ui.run(() => api.openDocs())}>
              Documentation
            </Button>
            <Button
              variant="danger"
              icon="shield"
              onClick={() => setConfirmReset(true)}
            >
              Reset PC identity
            </Button>
          </div>
        </Card>
      </div>

      <div className="grid grid-2">
        <Card icon="phone" title="Paired phones" subtitle="Only one phone can be connected at a time.">
          {state.devices.length === 0 ? (
            <p className="muted">No phones are paired.</p>
          ) : (
            <ul className="device-list">
              {state.devices.map((device) => (
                <li key={device.id}>
                  <div className="device-copy">
                    <strong>{device.name}</strong>
                    <span>
                      {device.platform} · paired {formatDateTime(device.pairedAt)}
                      {device.lastSeen ? ` · seen ${timeAgo(device.lastSeen)}` : ''}
                    </span>
                    <code className="mono small">
                      {formatFingerprint(device.fingerprint)}
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
          <p className="muted small">
            Removing a phone revokes its key immediately. Pair it again to reconnect.
          </p>
        </Card>

        <Card icon="wifi" title="Firewall and network">
          <p className="muted small">
            Allow inbound TCP {state.server.port || 48921} on Private networks. The full checklist
            and the PowerShell command are on the Connection tab. If the PC and phone are on
            different VLANs or guest Wi-Fi, direct LAN connections may be blocked.
          </p>
          <Notice tone="neutral" title="Unsigned app">
            The Windows build is not code-signed. SmartScreen may warn the first time you run it.
            Verify the SHA-256 checksum from the release page.
          </Notice>
        </Card>
      </div>

      <Modal
        open={confirmReset}
        title="Reset the PC identity?"
        onClose={() => setConfirmReset(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmReset(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                setConfirmReset(false);
                void ui.run(() => api.resetIdentity(), 'PC identity reset');
              }}
            >
              Reset everything
            </Button>
          </>
        }
      >
        <p>
          This deletes all paired phones, replaces the TLS certificate, and creates a new PC
          identity. Every phone must pair again with a new QR code. Use it if a device was lost or
          the key may have been exposed.
        </p>
      </Modal>

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
              Remove
            </Button>
          </>
        }
      >
        <p>The phone loses access immediately and must be paired again.</p>
      </Modal>
    </>
  );
}
