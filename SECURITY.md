# Security policy

## Supported versions

| Version | Supported |
|---|---|
| 0.1.x | Yes |

## Reporting a vulnerability

Please report vulnerabilities privately through GitHub's **Security** tab →
**Report a vulnerability** (Security Advisories) on
<https://github.com/FakeAbid11/LinkPhone>.

Do not open a public issue for a vulnerability, and do not include SMS text, notification content,
photos, clipboard contents, or pairing tokens in a report.

Include:

- Affected component (Android APK, Windows app, protocol, build/release)
- Version or commit
- Reproduction steps or a proof of concept
- Impact and whether the phone/PC needs to be on the same LAN
- Any suggested fix

You can expect an acknowledgement within a few days. This is a personal open-source project with no
bug bounty.

## Security model in brief

- LAN-only, TLS 1.2+; the phone pins the PC certificate fingerprint from the pairing QR.
- The PC authenticates the phone with an ECDSA P-256 signed challenge using a non-exportable
  AndroidKeyStore key.
- Pairing requires a single-use, five-minute token **and** explicit approval on the PC.
- No cloud relay, accounts, telemetry, or third-party data services.
- Android permissions and MediaProjection consent remain the final authority.

Details: [docs/security.md](docs/security.md).

## Accepted risks

- The Windows binaries are unsigned; SmartScreen may warn. Verify the published SHA-256 checksums.
- The Android APK is sideloaded, not Play-distributed.
- Call audio cannot be captured by a third-party app; the PC is a control surface only.
- OEM battery managers may stop background operation.
- A compromised phone or PC account defeats the protections above.
- Screen capture cannot include `FLAG_SECURE`/DRM windows.

## Hardening checklist

- Pair only devices you own; approve pairing only when you initiated it.
- Keep the PC's `userData` directory and OS account protected; use full-disk encryption.
- Remove lost phones on the PC; use **Reset PC identity** if the PC key may have been exposed.
- Keep the APK signing key backed up and private. Never commit it.
- Update from this repository's releases only.
- Delete the Windows Firewall rule if you uninstall the PC app.
