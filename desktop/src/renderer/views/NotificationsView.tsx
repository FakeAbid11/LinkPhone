import { useMemo } from 'react';
import type { AppSnapshot } from '../../shared/types';
import { api } from '../api';
import { Badge, Button, Card, EmptyState, Notice, PageHeader } from '../components';
import type { Ui } from '../ui';
import { initials, timeAgo } from '../utils';

export function NotificationsView({ state, ui }: { state: AppSnapshot; ui: Ui }) {
  const connected = state.connection.connected;
  const supported = state.connection.capabilities.includes('notifications');
  const muted = useMemo(() => new Set(state.settings.mutePackages), [state.settings.mutePackages]);

  const apps = useMemo(() => {
    const packages = new Map<string, string>();
    for (const item of state.notifications) {
      packages.set(item.packageName, item.appName);
    }
    for (const pkg of state.settings.mutePackages) {
      if (!packages.has(pkg)) packages.set(pkg, pkg);
    }
    return [...packages.entries()];
  }, [state.notifications, state.settings.mutePackages]);

  if (!connected) {
    return (
      <>
        <PageHeader title="Notifications" subtitle="Mirror phone notifications on the PC." />
        <Card>
          <EmptyState
            title="Connect your phone first"
            body="Notification mirroring only works while LinkPhone is connected. Android also requires you to grant notification access on the phone."
          />
        </Card>
      </>
    );
  }

  if (!supported) {
    return (
      <>
        <PageHeader title="Notifications" subtitle="Mirror phone notifications on the PC." />
        <Card>
          <EmptyState
            title="Notification access is not granted"
            body="On the phone, open LinkPhone, tap Notification access, and enable LinkPhone in the system list. Then return here."
          />
          <Notice tone="warn" title="Android 15 and later">
            The system may hide one-time codes and other sensitive notification content from
            listener apps. LinkPhone cannot override that.
          </Notice>
        </Card>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Notifications"
        subtitle="Live mirror with per-app controls. Dismissing here dismisses the phone notification."
        actions={
          <Button variant="ghost" onClick={() => void ui.refresh()}>
            Refresh
          </Button>
        }
      />

      <div className="grid grid-2">
        <Card
          title="Live feed"
          subtitle={`${state.notifications.length} notification${state.notifications.length === 1 ? '' : 's'}`}
        >
          {state.notifications.length === 0 ? (
            <p className="muted">
              Nothing yet. New notifications appear here while the phone is connected.
            </p>
          ) : (
            <ul className="notification-list">
              {state.notifications.map((item) => (
                <li key={item.key} className="notification-item">
                  <span className="notification-avatar">{initials(item.appName)}</span>
                  <div className="notification-copy">
                    <div className="notification-top">
                      <strong>{item.title || item.appName}</strong>
                      <span>{timeAgo(item.time)}</span>
                    </div>
                    <span className="notification-app">{item.appName}</span>
                    {item.text && <p>{item.text}</p>}
                    {item.ongoing && <Badge tone="neutral">Ongoing</Badge>}
                    <div className="notification-actions">
                      <Button
                        small
                        variant="ghost"
                        onClick={() => void ui.run(() => api.dismissNotification(item.key))}
                      >
                        Dismiss
                      </Button>
                      <Button
                        small
                        variant="ghost"
                        onClick={() =>
                          void ui.run(
                            () => api.setPackageMuted(item.packageName, true),
                            `${item.appName} muted`,
                          )
                        }
                      >
                        Mute app
                      </Button>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card title="App filters" subtitle="Muted apps are not mirrored at all.">
          {apps.length === 0 ? (
            <p className="muted">App names appear after the first notification arrives.</p>
          ) : (
            <ul className="filter-list">
              {apps.map(([pkg, name]) => {
                const isMuted = muted.has(pkg);
                return (
                  <li key={pkg}>
                    <div>
                      <strong>{name}</strong>
                      <span className="mono small">{pkg}</span>
                    </div>
                    <Button
                      small
                      variant={isMuted ? 'secondary' : 'ghost'}
                      onClick={() =>
                        void ui.run(
                          () => api.setPackageMuted(pkg, !isMuted),
                          isMuted ? `${name} unmuted` : `${name} muted`,
                        )
                      }
                    >
                      {isMuted ? 'Unmute' : 'Mute'}
                    </Button>
                  </li>
                );
              })}
            </ul>
          )}
          <Notice tone="neutral" title="Privacy">
            Notification text crosses the LAN connection and is kept in memory only. It is not
            written to disk by LinkPhone.
          </Notice>
        </Card>
      </div>
    </>
  );
}
