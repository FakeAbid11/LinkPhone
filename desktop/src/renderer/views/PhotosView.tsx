import { useEffect, useRef, useState } from 'react';
import type { AppSnapshot, PhotoUi } from '../../shared/types';
import { api } from '../api';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Modal,
  Notice,
  PageHeader,
  Spinner,
} from '../components';
import type { Ui } from '../ui';
import { formatDateTime } from '../utils';

export function PhotosView({ state, ui }: { state: AppSnapshot; ui: Ui }) {
  const connected = state.connection.connected;
  const supported = state.connection.capabilities.includes('photos');
  const [preview, setPreview] = useState<PhotoUi | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [savedPath, setSavedPath] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const loadedRef = useRef(false);

  useEffect(() => {
    if (!connected || !supported || loadedRef.current) return;
    loadedRef.current = true;
    void ui.run(() => api.refreshPhotos());
  }, [connected, supported, ui]);

  useEffect(() => {
    if (!connected || !supported) loadedRef.current = false;
  }, [connected, supported]);

  useEffect(() => {
    if (!preview) {
      setPreviewUrl(null);
      setSavedPath(null);
      return;
    }
    let cancelled = false;
    setPreviewUrl(null);
    setSavedPath(null);
    void api
      .getThumbnail(preview.id)
      .then((url) => {
        if (!cancelled) setPreviewUrl(url);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [preview]);

  if (!connected) {
    return (
      <>
        <PageHeader title="Photos" subtitle="Browse recent camera photos and save them to the PC." />
        <Card>
          <EmptyState
            title="Connect your phone first"
            body="Photos are requested on demand from the phone. Nothing is uploaded anywhere."
          />
        </Card>
      </>
    );
  }

  if (!supported) {
    return (
      <>
        <PageHeader title="Photos" subtitle="Browse recent camera photos and save them to the PC." />
        <Card>
          <EmptyState
            title="Photo access is not granted"
            body="On the phone, grant Photos permission in LinkPhone. On Android 14 you can still choose to share only selected photos."
          />
        </Card>
      </>
    );
  }

  const save = async () => {
    if (!preview) return;
    setBusy(true);
    try {
      const saved = await api.savePhoto(preview.id);
      setSavedPath(saved);
      ui.toast('success', 'Photo saved to Pictures\\LinkPhone');
    } catch (error) {
      ui.toast('error', error instanceof Error ? error.message : String(error));
    } finally {
      setBusy(false);
    }
  };

  const copy = async () => {
    if (!preview) return;
    setBusy(true);
    try {
      await api.copyPhoto(preview.id);
      ui.toast('success', 'Photo copied to the clipboard');
    } catch (error) {
      ui.toast('error', error instanceof Error ? error.message : String(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Photos"
        subtitle="Recent camera-roll images, transcoded to JPEG for the PC."
        actions={
          <Button
            variant="ghost"
            onClick={() => void ui.run(() => api.refreshPhotos())}
            disabled={state.photos.loading}
          >
            {state.photos.loading ? 'Loading…' : 'Refresh'}
          </Button>
        }
      />

      {state.photos.partial && (
        <Notice tone="warn" title="Selected photos only">
          Android is sharing only the photos you selected. Grant full photo access in system
          settings if you want the whole camera roll.
        </Notice>
      )}
      {state.photos.error && (
        <Notice tone="bad" title="Photos">
          {state.photos.error}
        </Notice>
      )}

      {state.photos.loading && state.photos.items.length === 0 ? (
        <Card>
          <Spinner label="Reading the camera roll…" />
        </Card>
      ) : state.photos.items.length === 0 ? (
        <Card>
          <EmptyState
            title="No photos found"
            body="LinkPhone shows the most recent 200 images from the shared camera roll."
          />
        </Card>
      ) : (
        <div className="photo-grid">
          {state.photos.items.map((photo) => (
            <PhotoTile key={photo.id} photo={photo} onOpen={() => setPreview(photo)} />
          ))}
        </div>
      )}

      <Modal
        open={preview !== null}
        wide
        title={preview?.name ?? 'Photo'}
        onClose={() => setPreview(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setPreview(null)}>
              Close
            </Button>
            {savedPath && (
              <Button
                variant="secondary"
                onClick={() => void ui.run(() => api.showFile(savedPath))}
              >
                Show in folder
              </Button>
            )}
            <Button variant="secondary" onClick={() => void copy()} disabled={busy}>
              Copy image
            </Button>
            <Button variant="primary" onClick={() => void save()} disabled={busy}>
              {busy ? 'Working…' : 'Save to PC'}
            </Button>
          </>
        }
      >
        <div className="photo-preview">
          {previewUrl ? (
            <img src={previewUrl} alt={preview?.name ?? 'Photo preview'} />
          ) : (
            <Spinner label="Loading preview…" />
          )}
        </div>
        {preview && (
          <div className="photo-meta">
            <div>
              <span className="meta-label">Captured</span>
              <span>{formatDateTime(preview.date)}</span>
            </div>
            <div>
              <span className="meta-label">Dimensions</span>
              <span>
                {preview.width} × {preview.height}
              </span>
            </div>
            <div>
              <span className="meta-label">Album</span>
              <span>{preview.bucket ?? 'Camera'}</span>
            </div>
            <Badge tone="neutral">JPEG copy</Badge>
          </div>
        )}
        {savedPath && (
          <Notice tone="good" title="Saved">
            <span className="mono small">{savedPath}</span>
          </Notice>
        )}
      </Modal>
    </>
  );
}

function PhotoTile({ photo, onOpen }: { photo: PhotoUi; onOpen: () => void }) {
  const ref = useRef<HTMLButtonElement>(null);
  const [visible, setVisible] = useState(false);
  const [url, setUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin: '320px' },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!visible) return;
    let cancelled = false;
    void api
      .getThumbnail(photo.id)
      .then((value) => {
        if (!cancelled) {
          if (value) setUrl(value);
          else setFailed(true);
        }
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, [visible, photo.id]);

  return (
    <button
      ref={ref}
      type="button"
      className="photo-tile"
      onClick={onOpen}
      aria-label={`Open ${photo.name}`}
    >
      {url ? (
        <img src={url} alt={photo.name} loading="lazy" />
      ) : (
        <span className="photo-placeholder">
          {failed ? 'Preview unavailable' : <span className="spinner" />}
        </span>
      )}
      <span className="photo-caption">{formatDateTime(photo.date)}</span>
    </button>
  );
}
