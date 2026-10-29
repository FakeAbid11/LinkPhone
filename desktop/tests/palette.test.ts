import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

const css = fs.readFileSync(
  path.resolve(process.cwd(), 'src/renderer/styles.css'),
  'utf8',
);

function token(name: string): string {
  const match = css.match(new RegExp(`--${name}:\\s*(#[0-9a-fA-F]{6})\\s*;`));
  if (!match) throw new Error(`Missing CSS token --${name}`);
  return match[1];
}

function channels(color: string): [number, number, number] {
  return [
    parseInt(color.slice(1, 3), 16),
    parseInt(color.slice(3, 5), 16),
    parseInt(color.slice(5, 7), 16),
  ];
}

function linear(value: number): number {
  const unit = value / 255;
  return unit <= 0.03928 ? unit / 12.92 : ((unit + 0.055) / 1.055) ** 2.4;
}

function luminance(color: string): number {
  const [r, g, b] = channels(color).map(linear);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(first: string, second: string): number {
  const a = luminance(first);
  const b = luminance(second);
  const lighter = Math.max(a, b);
  const darker = Math.min(a, b);
  return (lighter + 0.05) / (darker + 0.05);
}

const pairs: Array<{ name: string; foreground: string; background: string; minimum: number }> = [
  { name: 'body text on background', foreground: 'text', background: 'bg', minimum: 4.5 },
  { name: 'body text on cards', foreground: 'text', background: 'surface', minimum: 4.5 },
  { name: 'secondary text on cards', foreground: 'muted', background: 'surface', minimum: 4.5 },
  { name: 'captions on cards', foreground: 'faint', background: 'surface', minimum: 4.5 },
  { name: 'accent text on cards', foreground: 'primary', background: 'surface', minimum: 4.5 },
  { name: 'primary button label', foreground: 'primary-ink', background: 'primary', minimum: 4.5 },
  { name: 'secondary accent on cards', foreground: 'secondary', background: 'surface', minimum: 4.5 },
];

describe('desktop palette', () => {
  it.each(pairs)('$name meets WCAG contrast', ({ foreground, background, minimum }) => {
    const ratio = contrast(token(foreground), token(background));
    expect(ratio, `${foreground} on ${background} was ${ratio.toFixed(2)}:1`).toBeGreaterThanOrEqual(
      minimum,
    );
  });

  it('keeps focus and primary aligned with the accent family', () => {
    expect(token('focus')).toBe(token('primary-hover'));
  });
});
