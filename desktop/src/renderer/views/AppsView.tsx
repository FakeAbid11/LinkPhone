import { useEffect, useRef, useState } from 'react';
import { CAPABILITIES, type AppSnapshot, type MirrorCodec } from '../../shared/types';
import { api } from '../api';
import { Badge, Button, Card, EmptyState, Field, Notice, PageHeader, Select } from '../components';
import { H264StreamDecoder } from '../mirror/decoder';
import { createWebCodecsBackend, h264Supported } from '../mirror/webcodecs';
import type { Ui } from '../ui';

type CodecPreference = 'auto' | 'jpeg';

export function AppsView({ state, ui }: { state: AppSnapshot; ui: Ui }) {
  const connected = state.connection.connected;
  const supported =
    state.connection.capabilities.includes('mirror') ||
    state.connection.capabilities.length === 0;
  const active = state.mirror.state.active;
  const [requested, setRequested] = useState(false);
  const [preference, setPreference] = useState<CodecPreference>('auto');
  const [fpsPreference, setFpsPreference] = useState<30 | 60>(30);
  const [fps, setFps] = useState(0);
  const [notice, setNotice] = useState<string | null>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const decoderRef = useRef<H264StreamDecoder | null>(null);
  const framesRef = useRef(0);
  const pointerStart = useRef<{ x: number; y: number } | null>(null);

  const canH264 =
    state.connection.capabilities.includes(CAPABILITIES.MIRROR_H264) && h264Supported();
  const requestedCodec: MirrorCodec = preference === 'auto' && canH264 ? 'h264' : 'jpeg';
  const requestedFps = requestedCodec === 'h264' ? fpsPreference : 30;
  const phoneCodec = state.mirror.state.codec ?? 'jpeg';
  const phoneFps = state.mirror.state.fps ?? null;

  useEffect(() => {
    if (active || state.mirror.error) setRequested(false);
  }, [active, state.mirror.error, state.mirror.state.message]);

  useEffect(() => {
    if (!requested) return;
    const timer = window.setTimeout(() => {
      setRequested(false);
      ui.toast('error', 'The phone did not approve screen sharing. Try again.');
    }, 90_000);
    return () => window.clearTimeout(timer);
  }, [requested, ui]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      setFps(framesRef.current);
      framesRef.current = 0;
    }, 1_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!active) {
      framesRef.current = 0;
      setFps(0);
    }
  }, [active]);

  useEffect(() => {
    if (!active || phoneCodec !== 'h264') {
      decoderRef.current?.close();
      decoderRef.current = null;
      return;
    }
    const decoder = new H264StreamDecoder({
      width: state.mirror.state.width,
      height: state.mirror.state.height,
      createBackend: createWebCodecsBackend,
      draw: (frame) => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const width = frame.displayWidth || state.mirror.state.width;
        const height = frame.displayHeight || state.mirror.state.height;
        if (canvas.width !== width || canvas.height !== height) {
          canvas.width = width;
          canvas.height = height;
        }
        canvas.getContext('2d')?.drawImage(frame as unknown as CanvasImageSource, 0, 0);
      },
      requestKeyframe: () => {
        void api.requestMirrorKeyframe().catch(() => undefined);
      },
      onFatal: (message) => {
        if (fpsPreference === 60) {
          setFpsPreference(30);
          setNotice(`${message} Restart at 30 fps or in Compatible mode.`);
        } else {
          setPreference('jpeg');
          setNotice(`${message} Restart mirroring in Compatible mode.`);
        }
        void api.stopMirror().catch(() => undefined);
      },
    });
    decoderRef.current = decoder;
    void api.requestMirrorKeyframe().catch(() => undefined);
    return () => {
      decoder.close();
      if (decoderRef.current === decoder) decoderRef.current = null;
    };
  }, [active, phoneCodec, state.mirror.state.height, state.mirror.state.width]);

  useEffect(() => {
    const unsubscribe = api.onMirrorFrame((frame) => {
      framesRef.current += 1;
      if (frame.type === 4) {
        decoderRef.current?.push(frame);
        return;
      }
      if (frame.type !== 2) return;
      const blob = new Blob([new Uint8Array(frame.data)], { type: 'image/jpeg' });
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
    setNotice(null);
    setRequested(true);
    try {
      await api.startMirror(requestedCodec, requestedFps);
    } catch (error) {
      setRequested(false);
      ui.toast('error', error instanceof Error ? error.message : String(error));
    }
  };

  const stop = async () => {
    setRequested(false);
    decoderRef.current?.close();
    decoderRef.current = null;
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
            icon="monitor"
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
            icon="monitor"
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
            <Button variant="danger" icon="x" onClick={() => void stop()}>
              Stop mirroring
            </Button>
          ) : (
            <Button
              variant="primary"
              icon="monitor"
              onClick={() => void start()}
              loading={requested}
            >
              Start mirroring
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
            role="img"
            aria-label="Mirrored phone screen"
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
                  icon="monitor"
                  onClick={() => void start()}
                  loading={requested}
                >
                  Start mirroring
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
          <Card icon="monitor" title="Session">
            <div className="mirror-status">
              <Badge tone={active ? 'good' : 'neutral'}>
                {active ? 'Live' : requested ? 'Awaiting phone' : 'Idle'}
              </Badge>
              {active && (
                <span className="muted small">
                  {phoneCodec === 'h264' ? 'H.264' : 'JPEG'} · {state.mirror.state.width} ×{' '}
                  {state.mirror.state.height}
                  {fps > 0 ? ` · ~${fps} fps` : ''}
                </span>
              )}
            </div>
            {!active && (
              <Field
                id="mirror-codec"
                label="Codec"
                hint={
                  canH264
                    ? 'Auto uses hardware H.264 when both apps support it.'
                    : 'H.264 is unavailable on this phone or PC; JPEG will be used.'
                }
              >
                <Select
                  id="mirror-codec"
                  value={preference}
                  disabled={requested}
                  onChange={(event) =>
                    setPreference(event.target.value === 'jpeg' ? 'jpeg' : 'auto')
                  }
                >
                  <option value="auto">Auto (H.264)</option>
                  <option value="jpeg">Compatible (JPEG)</option>
                </Select>
              </Field>
            )}
            {!active && (
              <Field
                id="mirror-fps"
                label="Frame rate"
                hint={
                  requestedCodec === 'h264'
                    ? '60 fps uses a higher bitrate and more battery; the phone falls back to 30 fps if it cannot encode it.'
                    : 'JPEG mode is limited to about 5 fps.'
                }
              >
                <Select
                  id="mirror-fps"
                  value={requestedCodec === 'h264' ? String(fpsPreference) : '30'}
                  disabled={requested || requestedCodec !== 'h264'}
                  onChange={(event) =>
                    setFpsPreference(event.target.value === '60' ? 60 : 30)
                  }
                >
                  <option value="30">30 fps (balanced)</option>
                  <option value="60">60 fps (smoother)</option>
                </Select>
              </Field>
            )}
            {active && requestedCodec === 'h264' && phoneCodec === 'jpeg' && (
              <Notice tone="warn">
                The phone did not start H.264; this session is using JPEG.
              </Notice>
            )}
            {active &&
              requestedCodec === 'h264' &&
              phoneCodec === 'h264' &&
              fpsPreference === 60 &&
              (phoneFps ?? 30) < 60 && (
                <Notice tone="warn">
                  60 fps is not available on this phone/encoder; this session is running at 30 fps.
                </Notice>
              )}
            {notice && <Notice tone="bad">{notice}</Notice>}
            {state.mirror.state.message && (
              <Notice tone="warn">{state.mirror.state.message}</Notice>
            )}
            {state.mirror.error && <Notice tone="bad">{state.mirror.error}</Notice>}
            <p className="muted small">
              The phone shows a notification for every session. If the screen locks or rotates,
              start again from this tab.
            </p>
          </Card>

          <Card icon="phone" title="Remote input">
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

          <Card icon="shield" title="Why this is not app streaming">
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
