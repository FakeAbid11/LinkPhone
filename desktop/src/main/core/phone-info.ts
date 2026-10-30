import type { PhoneInfoWire, SimInfoWire } from '../../shared/types';

/**
 * `phone.info` arrives from another process across the LAN, so every field is
 * untrusted. The renderer puts `subscriptions[].label` straight into JSX: a
 * non-string there throws inside React and the error boundary replaces the
 * whole window, so the shape is pinned here instead.
 */
const MAX_CAPABILITIES = 40;
const MAX_SUBSCRIPTIONS = 4;

function text(value: unknown, max: number): string {
  return typeof value === 'string' ? value.trim().slice(0, max) : '';
}

function normalizeSim(raw: unknown): SimInfoWire | null {
  if (!raw || typeof raw !== 'object') return null;
  const sim = raw as Record<string, unknown>;
  const id = sim.subscriptionId;
  // Android reports INVALID_SUBSCRIPTION_ID as -1, so the sign is not checked.
  if (typeof id !== 'number' || !Number.isFinite(id)) return null;
  const slot =
    typeof sim.slotIndex === 'number' && Number.isFinite(sim.slotIndex)
      ? Math.min(9, Math.max(0, Math.trunc(sim.slotIndex)))
      : 0;
  const label = text(sim.label, 60);
  return {
    subscriptionId: Math.trunc(id),
    label: label || `SIM ${slot + 1}`,
    slotIndex: slot,
  };
}

export function normalizePhoneInfo(raw: unknown): PhoneInfoWire {
  const data = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>;
  const capabilities = Array.isArray(data.capabilities)
    ? data.capabilities
        .slice(0, 200)
        .filter((item): item is string => typeof item === 'string')
        .map((item) => item.slice(0, 64))
        .slice(0, MAX_CAPABILITIES)
    : [];
  const subscriptions = Array.isArray(data.subscriptions)
    ? data.subscriptions
        .slice(0, 32)
        .map(normalizeSim)
        .filter((item): item is SimInfoWire => item !== null)
        .slice(0, MAX_SUBSCRIPTIONS)
    : [];
  return {
    model: text(data.model, 80) || 'Android',
    androidVersion: text(data.androidVersion, 40),
    appVersion: text(data.appVersion, 40),
    capabilities,
    subscriptions,
  };
}
