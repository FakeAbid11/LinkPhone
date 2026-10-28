import os from 'node:os';
import { Bonjour } from 'bonjour-service';
import type { Logger } from './core/logger';

export class DiscoveryPublisher {
  private bonjour: Bonjour | undefined;
  private service: ReturnType<Bonjour['publish']> | undefined;

  constructor(private readonly logger: Logger) {}

  start(port: number, pcId: string, fingerprint: string): void {
    try {
      this.bonjour = new Bonjour();
      const hostname = os.hostname().replace(/\.local$/i, '');
      this.service = this.bonjour.publish({
        name: `LinkPhone-${hostname}`.slice(0, 63),
        type: 'linkphone',
        port,
        txt: { id: pcId, fp: fingerprint },
      });
      this.logger.log('Discovery', `Advertising _linkphone._tcp on port ${port}`);
    } catch (error) {
      this.logger.log(
        'Discovery',
        `mDNS advertising failed: ${error instanceof Error ? error.message : String(error)}`,
      );
    }
  }

  stop(): void {
    try {
      this.service?.stop(() => undefined);
    } catch {
      // Ignore shutdown races.
    }
    try {
      this.bonjour?.destroy();
    } catch {
      // Ignore shutdown races.
    }
    this.service = undefined;
    this.bonjour = undefined;
  }
}
