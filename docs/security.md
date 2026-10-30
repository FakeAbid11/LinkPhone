# Security model

## Assets

- SMS content and metadata
- Notification content (including one-time codes when Android allows them)
- Camera-roll photos and shared images
- Call state, numbers, and contact names
- Clipboard text
- Mirrored screen content
- The phone's private identity key
- The PC's TLS private key and paired-device list

## Adversaries and mitigations

| Adversary | Capability | Mitigations | Residual risk |
|---|---|---|---|
| LAN attacker | Can reach the PC's port, spoof mDNS, attempt connections | TLS with exact certificate pinning on the phone; PC authenticates the phone with a signed nonce; one-time pairing token; PC approval; rate limits | An attacker can cause connection noise/DoS; they cannot read or forge traffic without keys |
| Malicious app on the phone | Can read the phone's SharedPreferences if the device is rooted; can send intents | Private key is in AndroidKeyStore and non-exportable; the app validates incoming intents and share content; permissions gate each feature | On a rooted/compromised phone, app-private data and granted permissions are at risk |
| Malicious app on the PC | Can talk to IPC or the local server? | TLS + phone signature blocks LAN impersonation; renderer is sandboxed with a narrow IPC allowlist; unauthenticated sockets can only pair (needs token + PC approval) | Malware with the user's OS account can read the store, clipboard, and screen; no app can prevent that |
| Stolen phone | Has the paired state and can connect to the PC from the same LAN | Remove the phone on the PC; Reset PC identity; Android device encryption and lock screen | Until revoked, the phone can use its granted permissions; the PC does not require a fresh confirmation per connection |
| Stolen PC | Has the certificate and paired-device keys | Phone pins the certificate, so the thief cannot impersonate the PC with a new cert; reset identity on the phone (“Forget PC”); OS account security | The thief can read stored SMS/notifications only if the app is open and the phone reconnects; the store has no message history |
| Someone photographing the pairing QR | Gets address, public cert fingerprint, and token | Token expires in 5 minutes, single use, 3 attempts/IP; PC approval shows the phone fingerprint; pairing requires proximity to the PC screen | An attacker who scans the QR and gets the user to approve could pair; the approval dialog is the control |
| Same-LAN passive observer | Can see TLS metadata | TLS 1.2+; no plaintext; no cloud | Traffic volume/timing is visible; the self-signed cert is visible |

## Transport

- `wss://` over TLS 1.2+.
- PC certificate: self-signed RSA-2048, SHA-256, 10-year validity, generated on first run and stored
  with restrictive permissions in the app's `userData` directory.
- Phone pins the **SHA-256 of the exact leaf certificate DER** from the QR. Hostname verification is
  disabled because the IP address is only a route and the pin is the identity. A certificate
  rotation requires re-pairing.
- The PC never accepts plaintext HTTP/WebSocket. HTTP requests to the port get a 404.
- Messages are size-limited and validated on both sides.

## Pairing

- 128-bit random token, base64url, five-minute expiry, single use.
- Three failed attempts from one IP trigger a 15-minute lockout for that IP.
- PC shows the phone's computed P-256 fingerprint and requires explicit approval within 90 seconds.
- A phone can replace an existing device entry only through the same approval flow.
- Pairing is refused while a *different* phone is connected. A phone that is already
  paired may re-pair while it is connected, which is how a phone recovers after the PC
  identity was reset.

## Authentication

- Phone key: ECDSA P-256 generated in AndroidKeyStore with `setUserAuthenticationRequired(false)` so
  background reconnects work. The private key never leaves the keystore.
- PC verifies the SPKI is EC/P-256 and that its SHA-256 matches the stored fingerprint.
- Fresh 32-byte nonce per connection; the signature covers the protocol prefix, PC id, phone id, and
  nonce. Replay across connections fails.
- Signature encoding is DER (`SHA256withECDSA`), verified with Node `dsaEncoding: 'der'`.
- Challenges expire after 15 seconds; three failures close the session.
- The PC is authenticated by the pinned TLS certificate; the phone is authenticated by the
  signature. This is mutual authentication without client certificates (which are awkward with
  AndroidKeyStore).

## Authorization

- The PC can only invoke features the phone reports as currently granted. The phone re-checks
  Android permissions before each operation and returns `PERMISSION_DENIED`.
- URL handoff accepts only `http`/`https`.
- Touch injection validates normalized coordinates and gesture duration, and only the Accessibility
  service performs the gesture.
- File/image transfers enforce size limits, MIME allowlists, and sanitized filenames.
- PC file writes are restricted to `Pictures\LinkPhone` and `Downloads\LinkPhone`.

## Data at rest

| Data | Where | Protection |
|---|---|---|
| Phone private key | AndroidKeyStore | Hardware-backed where available; non-exportable |
| Paired PC details | Phone SharedPreferences | App-private; device encryption |
| PC private key | `userData/certs/pc-key.pem` | File mode 0600; OS account encryption recommended |
| Paired phones, settings, SMS read-state ids | `userData/state.json` | File mode 0600; public keys plus message ids (no SMS bodies) |
| Message/notification caches and contacts | PC memory | Not persisted by LinkPhone; contacts are fetched on demand with `READ_CONTACTS` |
| Photo thumbnails | PC memory | Not persisted |
| Saved photos/files | User-selected folders | Normal OS file permissions |
| Logs | `userData/logs/linkphone.log` | Connection events only; no message or notification bodies; tokens never logged |

The PC keeps the current SMS list and notification feed in memory so the UI can render them, and
sends that snapshot to the renderer on every state change while the phone is connected. Both are
cleared when the session ends; neither is written to disk.

The PC's state file is not separately encrypted. Use full-disk encryption and a locked OS account.
The phone side keeps only what it needs; it does not cache SMS bodies or notifications.

## Logging and privacy

- No analytics, telemetry, or crash reporting. The only outbound requests besides the phone's LAN
  connection are the Windows and Android update checks/downloads from the official GitHub Releases
  page. Disable automatic checks; manual checks use the same feed.
- Update installers are verified with the release's SHA-256 checksum before execution. They are not
  Authenticode-signed; see the supply-chain section.
- Logs contain connection events, device names, addresses, and error classes. They do not contain
  SMS bodies, notification text, clipboard contents, photo data, tokens, or private keys.
- The phone's log export is a plain text share; review it before sending.

## Supply chain and distribution

- Android release APKs are signed with a stable key stored **only** in GitHub Actions secrets. The
  key is generated once and backed up outside the repository. Losing it means future updates require
  uninstalling the app; leaking it allows malicious updates, so rotate and re-pair if exposed.
- No signing keys, keystores, PEM files, or tokens are committed. `.gitignore` blocks the common
  names.
- Workflows run on GitHub-hosted runners. Release assets include a SHA-256 checksum file.
- The Windows updater downloads the Setup EXE and `SHA256SUMS.txt` from the official GitHub
  release over HTTPS and verifies the SHA-256 before execution. It does not verify an Authenticode
  publisher signature because the build is unsigned.
- The **Android** updater verifies the downloaded APK against `SHA256SUMS.txt` **and** against the
  running app's signing certificate. The checksum alone would be insufficient: the APK and the
  checksum file come from the same release, so a compromised release could satisfy both. The
  certificate comparison accepts only the key the installed app is signed with today, or a key that
  descends from it through a legitimate forward rotation — never a retired key, so a downgrade to
  an old key is refused. The expected release signer digest is
  `FD:F3:D0:3E:A4:11:EF:50:3A:1C:5A:17:90:DE:A6:1A:38:80:EE:1C:72:4C:63:C1:49:7B:03:E6:C1:C6:A4:E3`;
  it is checked in CI with `apksigner verify --print-certs` rather than trusted blindly at runtime.
- An APK signed with a different key is refused with a message telling the user to install it
  manually. A debug-signed build therefore cannot silently take over an official installation.
- The Windows binaries are **not code-signed**; SmartScreen may warn. Verify the checksum from the
  release page and consider building from source. Do not accept updates from mirrors or third
  parties.
- The APK is **not** distributed through Google Play. Sideload only from this repository's releases.
- Third-party libraries: OkHttp, kotlinx.serialization, CameraX, ZXing core, AndroidX; Electron,
  React, ws, selfsigned (node-forge), bonjour-service, qrcode, Vite, Vitest, Playwright. No analytics
  or ad SDKs.

## Out of scope / accepted risks

- Rooted or compromised phones, debuggers attached to the app, and malicious OS-level software.
- An attacker who controls the PC's OS account.
- Screen capture of `FLAG_SECURE` content is blocked by Android, not by LinkPhone.
- Denial of service by flooding the LAN port (the app limits connections and message rates but
  cannot stop a network-level flood).
- Windows firewall prompts and SmartScreen/unsigned-binary warnings.
- Calls answered/declined differently by OEMs; call audio never reaches the PC.
- SMS sent without the default-SMS role may not appear in the stock thread.

## Reporting

Please use GitHub's private vulnerability reporting / Security Advisories on
<https://github.com/FakeAbid11/LinkPhone>. Do not open a public issue for a vulnerability. See
[../SECURITY.md](../SECURITY.md).
