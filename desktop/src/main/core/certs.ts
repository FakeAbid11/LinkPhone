import fs from 'node:fs';
import path from 'node:path';
import { X509Certificate, createHash } from 'node:crypto';

interface SelfSignedPems {
  private: string;
  public: string;
  cert: string;
}

const selfsigned = require('selfsigned') as {
  generate: (
    attrs: Array<{ name: string; value: string }>,
    options?: Record<string, unknown>,
  ) => SelfSignedPems;
};

export interface PcCertificate {
  keyPem: string;
  certPem: string;
  fingerprintHex: string;
  subject: string;
  validTo: string;
}

export function ensureCertificate(directory: string): PcCertificate {
  fs.mkdirSync(directory, { recursive: true });
  const keyPath = path.join(directory, 'pc-key.pem');
  const certPath = path.join(directory, 'pc-cert.pem');

  try {
    if (fs.existsSync(keyPath) && fs.existsSync(certPath)) {
      const keyPem = fs.readFileSync(keyPath, 'utf8');
      const certPem = fs.readFileSync(certPath, 'utf8');
      const cert = new X509Certificate(certPem);
      if (new Date(cert.validTo).getTime() > Date.now() + 24 * 60 * 60 * 1000) {
        return describe(keyPem, certPem);
      }
    }
  } catch {
    // Fall through and regenerate a valid certificate.
  }

  const pems = selfsigned.generate(
    [{ name: 'commonName', value: 'LinkPhone PC' }],
    {
      keySize: 2048,
      days: 3650,
      algorithm: 'sha256',
      extensions: [{ name: 'basicConstraints', cA: false }],
    },
  );
  fs.writeFileSync(keyPath, pems.private, { mode: 0o600 });
  fs.writeFileSync(certPath, pems.cert, { mode: 0o644 });
  return describe(pems.private, pems.cert);
}

function describe(keyPem: string, certPem: string): PcCertificate {
  const certificate = new X509Certificate(certPem);
  return {
    keyPem,
    certPem,
    fingerprintHex: createHash('sha256').update(certificate.raw).digest('hex'),
    subject: 'LinkPhone PC',
    validTo: certificate.validTo,
  };
}
