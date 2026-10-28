import fs from 'node:fs';
import path from 'node:path';

export class Logger {
  private buffer: string[] = [];
  private listener: ((line: string) => void) | undefined;

  constructor(private readonly filePath: string) {
    fs.mkdirSync(path.dirname(filePath), { recursive: true });
    this.rotateIfNeeded();
  }

  log(tag: string, message: string): void {
    const safe = message.replace(/\s+/g, ' ').trim().slice(0, 500);
    const line = `${new Date().toISOString()} [${tag}] ${safe}`;
    this.buffer.push(line);
    if (this.buffer.length > 300) this.buffer.shift();
    try {
      fs.appendFileSync(this.filePath, `${line}\n`);
    } catch {
      // Logging must never break the app.
    }
    this.listener?.(line);
  }

  lines(limit = 150): string[] {
    return this.buffer.slice(-limit);
  }

  onLine(listener: (line: string) => void): void {
    this.listener = listener;
  }

  private rotateIfNeeded(): void {
    try {
      if (fs.existsSync(this.filePath) && fs.statSync(this.filePath).size > 1_000_000) {
        const rotated = `${this.filePath}.1`;
        fs.rmSync(rotated, { force: true });
        fs.renameSync(this.filePath, rotated);
      }
    } catch {
      // Rotation is best effort.
    }
  }
}
