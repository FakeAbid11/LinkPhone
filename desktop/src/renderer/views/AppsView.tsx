import { useEffect, useRef, useState } from 'react';
import type { AppSnapshot } from '../../shared/types';
import { api } from '../api';
import { Badge, Button, Card, EmptyState, Notice, PageHeader } from '../components';
import type { Ui } from '../ui';

export function AppsView({ state, ui }: { state: AppSnapshot; ui: Ui }) {
  const connected = state.connection.connected;
  const supported =
    state.connection.capabilities.includes('mirror') ||
    state.connection.capabilities.length === 0;
  const active = state.mirror.state.active;
  const [requested, setRequested] = useState(false);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const pointerStart = useRef<{ x: number; y: number } | null>(null);

  useEffect(() => {
    if (active) setRequested(false);
  }, [active]);

  useEffect(() => {
    const unsubscribe = api.onMirrorFrame(({ data }) => {
      const blob = new Blob([new Uint8Array(data)], { type: 'image/jpeg' });
      void createImageBitmap(blob)
        .then((bitmap) => {
          const canvas = canvasRef.current;
          if (!canvas) {
            bitmap.close();
            return;
          }
          if (canvas.width !== bitmap.width || canvas.height !== bitmap.height) {
            canvas.width = bitmap.width;
            canvas.height = bitmap.height;
          }
          const context = canvas.getContext('2d');
          context?.drawImage(bitmap, 0, 0);
          bitmap.close();
        })
        .catch(() => undefined);
    });
    return unsubscribe;
  }, []);

  const start = async () => {
    setRequested(true);
    await ui.run(() => api.startMirror(), 'Approve screen sharing on your phone');
  };

  const stop = async () => {
    setRequested(false);
    await ui.run(() => api.stopMirror(), 'Mirroring stopped');
  };

  const sendTouch = async (touch: {
    action: 'tap' | 'swipe';
    x: number;
    y: number;
    x2?: number;
    y2?: number;
    durationMs: number;
  }) => {
    try {
      await api.mirrorTouch(touch);
    } catch (error) {
      ui.toast('error', error instanceof Error ? error.message : String(error));
    }
  };

  if (!connected) {
    return (
      <>
        <PageHeader title="Apps" subtitle="Experimental screen mirroring and remote taps." />
        <Card>
          <EmptyState
            title="Connect your phone first"
            body="Screen mirroring needs an active LinkPhone connection and a fresh screen-capture approval on the phone for every session."
          />
        </Card>
      </>
    );
  }

  if (!supported) {
    return (
      <>
        <PageHeader title="Apps" subtitle="Experimental screen mirroring and remote taps." />
        <Card>
          <EmptyState
            title="Mirroring is unavailable"
            body="This phone did not report mirroring support. Update the phone app and reconnect."
          />
        </Card>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Apps"
        subtitle="Screen mirroring, not app virtualization. Android does not expose a public API for running apps on the PC."
        actions={
          active ? (
            <Button variant="danger" onClick={() => void stop()}>
              Stop mirroring
            </Button>
          ) : (
            <Button variant="primary" onClick={() => void start()} disabled={requested}>
              {requested ? 'Waiting for phone…' : 'Start mirroring'}
            </Button>
          )
        }
      />

      <Notice tone="warn" title="Experimental and consent-gated">
        Android requires approval for every capture session, stops capture when the screen locks,
        and shows secure or DRM windows as black. Rotation ends the session and requires a restart.
        Audio is never captured.
      </Notice>

      <div className="mirror-wrap">
        <div className={`mirror-stage ${active ? 'is-live' : ''}`}>
          <canvas
            ref={canvasRef}
            className="mirror-canvas"
            onPointerDown={(event) => {
              if (!active || !state.mirror.inputEnabled) return;
              const rect = event.currentTarget.getBoundingClientRect();
              pointerStart.current = {
                x: (event.clientX - rect.left) / rect.width,
                y: (event.clientY - rect.top) / rect.height,
              };
            }}
            onPointerUp={(event) => {
              const startPoint = pointerStart.current;
              pointerStart.current = null;
              if (!startPoint || !active || !state.mirror.inputEnabled) return;
              const rect = event.currentTarget.getBoundingClientRect();
              const x = (event.clientX - rect.left) / rect.width;
              const y = (event.clientY - rect.top) / rect.height;
              const distance = Math.hypot(x - startPoint.x, y - startPoint.y);
              if (distance < 0.012) {
                void sendTouch({ action: 'tap', x, y, durationMs: 60 });
              } else {
                void sendTouch({
                  action: 'swipe',
                  x: startPoint.x,
                  y: startPoint.y,
                  x2: x,
                  y2: y,
                  durationMs: Math.min(1_400, Math.round(220 + distance * 1_500)),
                });
              }
            }}
          />
          {!active && (
            <div className="mirror-overlay">
              <h3>No active mirror</h3>
              <p>
                Start mirroring, then approve the screen-sharing notification on your phone.
              </p>
              <div className="mirror-overlay-actions">
                <Button
                  variant="primary"
                  onClick={() => void start()}
                  disabled={requested}
                >
                  {requested ? 'Waiting for approval…' : 'Start mirroring'}
                </Button>
                {requested && (
                  <Button variant="ghost" onClick={() => setRequested(false)}>
                    Cancel request
                  </Button>
                )}
              </div>
            </div>
          )}
        </div>
        <div className="mirror-side">
          <Card title="Session">
            <div className="mirror-status">
              <Badge tone={active ? 'good' : 'neutral'}>
                {active ? 'Live' : requested ? 'Awaiting phone' : 'Idle'}
              </Badge>
              {active && (
                <span className="muted small">
                  {state.mirror.state.width} × {state.mirror.state.height} · low frame rate
                </span>
              )}
            </div>
            {state.mirror.state.message && (
              <Notice tone="warn">{state.mirror.state.message}</Notice>
            )}
            {state.mirror.error && <Notice tone="bad">{state.mirror.error}</Notice>}
            <p className="muted small">
              The phone shows a notification for every session. If the screen locks or rotates,
              start again from this tab.
            </p>
          </Card>

          <Card title="Remote input">
            <div className="input-status">
              <Badge tone={state.mirror.inputEnabled ? 'good' : 'warn'}>
                {state.mirror.inputEnabled ? 'Taps enabled' : 'View only'}
              </Badge>
            </div>
            <p className="muted small">
              {state.mirror.inputEnabled
                ? 'Click to tap, drag to swipe. Keyboard input is not supported.'
                : 'Enable the LinkPhone Accessibility service on the phone to send taps and swipes. LinkPhone only performs gestures from this PC.'}
            </p>
            {!state.mirror.inputEnabled && (
              <p className="muted small">
                Phone: LinkPhone → Permissions → Remote taps → open Accessibility settings.
              </p>
            )}
          </Card>

          <Card title="Why this is not app streaming">
            <p className="muted small">
              Running arbitrary Android apps in a Windows window requires privileged system
              integration. LinkPhone mirrors the display instead, which works with any app you
              can show on the phone. Some apps opt out of capture for privacy.
            </p>
          </Card>
        </div>
      </div>
    </>
  );
}
