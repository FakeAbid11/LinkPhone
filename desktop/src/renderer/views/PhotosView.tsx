import { useEffect, useRef, useState } from 'react';
import { CAPABILITIES, type AppSnapshot, type PhotoUi } from '../../shared/types';
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
  const supported = state.connection.capabilities.includes(CAPABILITIES.PHOTOS);
  const albumsSupported =
    state.photos.albumsSupported ||
    state.connection.capabilities.includes(CAPABILITIES.PHOTOS_ALBUMS);
  const [preview, setPreview] = useState<PhotoUi | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [previewError, setPreviewError] = useState(false);
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
      setPreviewError(false);
      setSavedPath(null);
      return;
    }
    let cancelled = false;
    setPreviewUrl(null);
    setPreviewError(false);
    setSavedPath(null);
    // The grid uses 512 px thumbnails; the modal shows the full image.
    void api
      .getPhotoPreview(preview.id)
      .then((url) => {
        if (!cancelled) setPreviewUrl(url);
      })
      .catch(() => {
        if (!cancelled) setPreviewError(true);
      });
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
            icon="image"
            title="Connect your phone first"
            body="Photos are requested on demand from the phone. Nothing is uploaded anywhere."
          />
        </Card>
      </>
    );
  }

  if (!state.connection.infoReceived) {
    return (
      <>
        <PageHeader title="Photos" subtitle="Browse recent camera photos and save them to the PC." />
        <Card>
          <EmptyState
            icon="image"
            title="Waiting for the phone to report its permissions"
            body="The phone is connected but has not said which features it can provide yet. If this stays here, update the LinkPhone app on the phone and reconnect."
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
            icon="image"
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

  const selectAlbum = (bucket: string | null) => {
    setPreview(null);
    void ui.run(() => api.selectPhotoAlbum(bucket));
  };

  return (
    <>
      <PageHeader
        title="Photos"
        subtitle="Recent images from the phone, grouped by album and transcoded to JPEG for the PC."
        actions={
          <Button
            variant="ghost"
            icon="refresh"
            onClick={() => void ui.run(() => api.refreshPhotos())}
            loading={state.photos.loading}
          >
            Refresh
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
      {state.photos.albumsError && (
        <Notice tone="bad" title="Albums">
          {state.photos.albumsError}
        </Notice>
      )}

      {albumsSupported && state.photos.albums.length > 0 && (
        <>
          <div className="album-strip" role="group" aria-label="Photo albums">
            <button
              type="button"
              className={`album-chip ${state.photos.activeAlbum === null ? 'is-active' : ''}`}
              aria-pressed={state.photos.activeAlbum === null}
              onClick={() => selectAlbum(null)}
            >
              All photos
            </button>
            {state.photos.albums.map((album) => (
              <button
                key={album.id}
                type="button"
                className={`album-chip ${
                  state.photos.activeAlbum === album.id ? 'is-active' : ''
                }`}
                aria-pressed={state.photos.activeAlbum === album.id}
                onClick={() => selectAlbum(album.id)}
              >
                {album.name}
                <span className="album-count">{album.count}</span>
              </button>
            ))}
            {state.photos.albumsLoading && <span className="live-dot" aria-label="Refreshing" />}
          </div>
          {state.photos.partial && (
            <p className="muted small album-note">
              Albums reflect only the photos you shared with LinkPhone.
            </p>
          )}
        </>
      )}

      {state.photos.loading && state.photos.items.length === 0 ? (
        <Card>
          <Spinner label={state.photos.activeAlbum ? 'Reading this album…' : 'Reading the camera roll…'} />
        </Card>
      ) : state.photos.items.length === 0 ? (
        <Card>
          <EmptyState
            icon="image"
            title={state.photos.activeAlbum ? 'No photos in this album' : 'No photos found'}
            body={
              state.photos.activeAlbum
                ? 'This album has no photos available to LinkPhone. Try another album or refresh.'
                : 'LinkPhone shows recent images from the shared camera roll.'
            }
          />
        </Card>
      ) : (
        <>
          <div className="photo-grid">
            {state.photos.items.map((photo) => (
              <PhotoTile
                key={`${photo.volume ?? ''}:${photo.id}`}
                photo={photo}
                onOpen={() => setPreview(photo)}
              />
            ))}
          </div>
          {state.photos.hasMore && (
            <div className="photo-more">
              <Button
                variant="secondary"
                onClick={() => void ui.run(() => api.loadMorePhotos())}
                loading={state.photos.loadingMore}
              >
                Load more photos
              </Button>
            </div>
          )}
        </>
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
            <Button variant="secondary" icon="copy" onClick={() => void copy()} disabled={busy}>
              Copy image
            </Button>
            <Button variant="primary" icon="download" onClick={() => void save()} loading={busy}>
              Save to PC
            </Button>
          </>
        }
      >
        <div className="photo-preview">
          {previewUrl ? (
            <img src={previewUrl} alt={preview?.name ?? 'Photo preview'} />
          ) : previewError ? (
            <p className="muted">This photo could not be loaded from the phone.</p>
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
