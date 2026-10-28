export function formatTime(timestamp: number): string {
  if (!Number.isFinite(timestamp)) return '';
  return new Date(timestamp).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatDateTime(timestamp: number): string {
  if (!Number.isFinite(timestamp)) return '';
  return new Date(timestamp).toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function timeAgo(timestamp: number): string {
  if (!Number.isFinite(timestamp)) return '';
  const seconds = Math.max(0, Math.round((Date.now() - timestamp) / 1000));
  if (seconds < 45) return 'just now';
  if (seconds < 3600) return `${Math.round(seconds / 60)} min ago`;
  if (seconds < 86_400) return `${Math.round(seconds / 3600)} h ago`;
  return `${Math.round(seconds / 86_400)} d ago`;
}

export function formatFingerprint(fingerprint: string): string {
  return fingerprint
    .replace(/[^0-9a-f]/gi, '')
    .toUpperCase()
    .match(/.{2}/g)
    ?.join(':') ?? fingerprint.toUpperCase();
}

export function formatExpiry(expiresAt: number): string {
  const remaining = Math.max(0, Math.floor((expiresAt - Date.now()) / 1000));
  const minutes = Math.floor(remaining / 60);
  const seconds = remaining % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

export function errorMessage(error: unknown): string {
  const raw = error instanceof Error ? error.message : String(error);
  return raw.replace(/^Error invoking remote method '[^']+':\s*(Error:\s*)?/, '');
}

export function initials(name: string): string {
  const trimmed = name.trim();
  if (!trimmed) return '?';
  const parts = trimmed.split(/\s+/);
  return (parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '');
}

export function capabilityLabel(capability: string): string {
  const labels: Record<string, string> = {
    sms: 'SMS',
    notifications: 'Notifications',
    photos: 'Photos',
    'photos.partial': 'Selected photos',
    calls: 'Calls',
    'calls.answer': 'Answer calls',
    'calls.callerId': 'Caller ID',
    mirror: 'Mirroring',
    'mirror.input': 'Remote taps',
    clipboard: 'Clipboard',
    handoff: 'Continue on PC',
  };
  return labels[capability] ?? capability;
}

export function truncate(value: string, max: number): string {
  return value.length > max ? `${value.slice(0, max - 1)}…` : value;
}
