import { describe, expect, it } from 'vitest';
import { batteryLabel, batteryTone } from '../src/renderer/utils';

describe('Phone battery', () => {
  it('shows the charge level', () => {
    expect(batteryLabel(87, false)).toBe('87%');
    expect(batteryLabel(0, false)).toBe('0%');
    expect(batteryLabel(100, false)).toBe('100%');
  });

  it('says when the phone is charging', () => {
    expect(batteryLabel(87, true)).toBe('87% · charging');
  });

  it('shows a dash when the phone cannot report a level', () => {
    expect(batteryLabel(undefined, false)).toBe('—');
    expect(batteryLabel(Number.NaN, false)).toBe('—');
  });

  it('keeps the level inside the possible range', () => {
    expect(batteryLabel(140, false)).toBe('100%');
    expect(batteryLabel(-4, false)).toBe('0%');
    expect(batteryLabel(12.6, false)).toBe('13%');
  });

  it('treats a charging phone as healthy', () => {
    expect(batteryTone(5, true)).toBe('good');
  });

  it('warns about a low battery', () => {
    expect(batteryTone(12, false)).toBe('bad');
    expect(batteryTone(15, false)).toBe('bad');
    expect(batteryTone(30, false)).toBe('warn');
    expect(batteryTone(31, false)).toBe('neutral');
    expect(batteryTone(90, false)).toBe('neutral');
    expect(batteryTone(undefined, false)).toBe('neutral');
  });
});
