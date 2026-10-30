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
import { formatFingerprint, timeAgo } from '../utils';

export function SettingsView({ state, ui }: { state: AppSnapshot; ui: Ui }) {
  const [confirmReset, setConfirmReset] = useState(false);

  const update = (patch: Parameters<typeof api.updateSettings>[0]) =>
    void ui.run(() => api.updateSettings(patch));

  const updates = state.updates;
  const updateLabel =
    updates.phase === 'checking'
      ? 'Checking…'
      : updates.phase === 'up-to-date'
        ? 'Up to date'
        : updates.phase === 'available'
          ? `Version ${updates.version ?? ''} available`
          : updates.phase === 'downloading'
            ? `Downloading ${updates.progress ?? 0}%`
            : updates.phase === 'ready'
              ? `Version ${updates.version ?? ''} ready`
              : updates.phase === 'error'
                ? 'Update failed'
                : updates.mode === 'portable'
                  ? 'Portable build'
                  : updates.mode === 'disabled'
                    ? 'Unavailable in this build'
                    : 'Not checked yet';
  const updateTone: 'good' | 'accent' | 'bad' | 'neutral' =
    updates.phase === 'up-to-date' || updates.phase === 'ready'
      ? 'good'
      : updates.phase === 'checking' || updates.phase === 'available' || updates.phase === 'downloading'
        ? 'accent'
        : updates.phase === 'error'
          ? 'bad'
          : 'neutral';

  return (
    <>
      <PageHeader
        title="Settings"
        subtitle="Preferences, security controls, and updates."
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

      <Card icon="download" title="Updates" subtitle="Checks the official GitHub Releases page. No telemetry.">
        <div className="settings-facts">
          <div>
            <span className="meta-label">Current version</span>
            <span>LinkPhone {state.version}</span>
          </div>
          <div>
            <span className="meta-label">Status</span>
            <Badge tone={updateTone}>{updateLabel}</Badge>
          </div>
          {updates.checkedAt && (
            <div>
              <span className="meta-label">Last checked</span>
              <span>{timeAgo(updates.checkedAt)}</span>
            </div>
          )}
        </div>

        {updates.supported ? (
          <>
            <Toggle
              checked={state.settings.autoCheckUpdates}
              onChange={(value) => update({ autoCheckUpdates: value })}
              label="Check for updates automatically"
              description="Checks shortly after launch and every six hours. Manual checks still work when this is off."
            />
            {updates.phase === 'error' && (
              <Notice tone="bad" title="Update check failed">
                {updates.error ?? 'Could not check for updates.'}
              </Notice>
            )}
            {updates.phase === 'downloading' && (
              <div className="update-progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={updates.progress ?? 0}>
                <span style={{ width: `${updates.progress ?? 0}%` }} />
              </div>
            )}
            {(updates.phase === 'available' || updates.phase === 'ready') && (
              <Notice tone="good" title={`LinkPhone ${updates.version ?? ''}`}>
                {updates.notes || 'A new version is available. Restart when it has finished downloading.'}
              </Notice>
            )}
            <div className="settings-actions">
              <Button
                variant="ghost"
                icon="refresh"
                disabled={updates.phase === 'checking' || updates.phase === 'downloading'}
                onClick={() => void ui.run(() => api.checkForUpdates())}
              >
                Check for updates
              </Button>
              {(updates.phase === 'available' || updates.phase === 'error') && updates.version && (
                <Button
                  variant="secondary"
                  icon="download"
                  onClick={() => void ui.run(() => api.downloadUpdate())}
                >
                  {updates.phase === 'error' ? 'Retry download' : 'Download update'}
                </Button>
              )}
              {updates.phase === 'ready' && (
                <Button
                  variant="primary"
                  icon="check"
                  onClick={() => void ui.run(() => api.installUpdate(), 'Restarting to install the update')}
                >
                  Restart and install
                </Button>
              )}
              <Button variant="ghost" icon="arrow" onClick={() => void ui.run(() => api.openReleasePage())}>
                Release notes
              </Button>
            </div>
            <Notice tone="warn" title="Unsigned build">
              Updates are verified with SHA-256 from the official release, but the Windows
              installer is not code-signed, so SmartScreen may warn.
            </Notice>
          </>
        ) : updates.mode === 'portable' ? (
          <>
            <p className="muted small">
              Automatic updates are unavailable in the portable build. Download the latest portable
              EXE and replace the current file.
            </p>
            <div className="settings-actions">
              <Button variant="primary" icon="download" onClick={() => void ui.run(() => api.openReleasePage())}>
                Download portable
              </Button>
            </div>
          </>
        ) : (
          <p className="muted small">Automatic updates are unavailable in this build.</p>
        )}
      </Card>

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
    </>
  );
}
