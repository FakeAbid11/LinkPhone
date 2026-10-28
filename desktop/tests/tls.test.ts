import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import https from 'node:https';
import tls from 'node:tls';
import { createHash } from 'node:crypto';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { ensureCertificate, type PcCertificate } from '../src/main/core/certs';

let tempRoot = '';
let certificate: PcCertificate;
let server: https.Server;
let port = 0;

beforeAll(async () => {
  tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'linkphone-tls-'));
  certificate = ensureCertificate(path.join(tempRoot, 'certs'));
  server = https.createServer(
    { key: certificate.keyPem, cert: certificate.certPem, minVersion: 'TLSv1.2' },
    (_request, response) => {
      response.writeHead(200, { 'content-type': 'text/plain' });
      response.end('linkphone');
    },
  );
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  port = (server.address() as { port: number }).port;
});

afterAll(async () => {
  await new Promise<void>((resolve) => server.close(() => resolve()));
  fs.rmSync(tempRoot, { recursive: true, force: true });
});

describe('PC TLS certificate', () => {
  it('is self-signed, long-lived, and fingerprintable', () => {
    expect(certificate.fingerprintHex).toMatch(/^[0-9a-f]{64}$/);
    expect(new Date(certificate.validTo).getTime()).toBeGreaterThan(Date.now());
    const again = ensureCertificate(path.join(tempRoot, 'certs'));
    expect(again.fingerprintHex).toBe(certificate.fingerprintHex);
    expect(again.keyPem).toBe(certificate.keyPem);
  });

  it('presents exactly the pinned certificate to the phone', async () => {
    const fingerprint = await new Promise<string>((resolve, reject) => {
      const socket = tls.connect(
        {
          host: '127.0.0.1',
          port,
          ca: certificate.certPem,
          servername: 'LinkPhone PC',
          rejectUnauthorized: true,
          checkServerIdentity: () => undefined,
        },
        () => {
          const peer = socket.getPeerCertificate();
          if (!peer?.raw) {
            reject(new Error('No peer certificate'));
            return;
          }
          const actual = createHash('sha256').update(peer.raw).digest('hex');
          socket.end();
          resolve(actual);
        },
      );
      socket.once('error', reject);
    });
    expect(fingerprint).toBe(certificate.fingerprintHex);
  });

  it('is rejected by a client that does not trust the self-signed cert', async () => {
    await new Promise<void>((resolve, reject) => {
      const socket = tls.connect(
        {
          host: '127.0.0.1',
          port,
          servername: 'LinkPhone PC',
          rejectUnauthorized: true,
          checkServerIdentity: () => undefined,
        },
        () => {
          socket.destroy();
          reject(new Error('The connection should not have been trusted'));
        },
      );
      socket.once('error', () => resolve());
    });
  });
});
