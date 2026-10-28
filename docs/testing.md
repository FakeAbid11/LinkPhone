# Testing

All compilation, unit tests, integration tests, packaging, and the desktop end-to-end test run in
GitHub Actions. The development machine used for this project does not build or run the apps
locally.

## CI jobs

| Job | Runner | What it does |
|---|---|---|
| Android | `ubuntu-latest`, JDK 17, Gradle 8.9 | JVM unit tests, lint, signed release APK if secrets are present, `apksigner verify` |
| Desktop | `windows-2022`, Node 20 | `tsc --noEmit`, Vitest, Vite build, electron-builder NSIS/portable, Playwright Electron smoke test |

### Android tests

`android/app/src/test/java/app/linkphone`:

- `PairingUriTest` — QR URI parsing, fingerprint/token/port validation, normalization.
- `CryptoTest` — SHA-256 known vector, hex/fingerprint formatting, canonical auth payload.
- `ProtocolCodecTest` — envelope round-trip, unknown fields, version rejection, size limit, binary
  frame header/type/chunk tests.
- `BackoffTest` — reconnect delay bounds, growth, reset, deterministic seed.
- `SafeInputTest` — dial numbers, URL scheme allowlist, filename sanitization, clipboard cap,
  normalized gesture coordinates.

Run in CI:

```bash
gradle -p android :app:testDebugUnitTest :app:lintDebug
```

### Desktop tests

`desktop/tests`:

- `protocol.test.ts` — envelope codec, correlation, errors, version/size rejection.
- `binary.test.ts` — photo/mirror/file frames, header validation, payload cap.
- `auth.test.ts` — P-256 key parsing, fingerprint binding, RSA rejection, DER signature verification,
  cross-device rejection.
- `pairing.test.ts` — token single use, five-minute expiry, per-IP lockout, replacement.
- `safety.test.ts` — dialing, URL allowlist, filename traversal, fingerprints, SMS grouping/merge.
- `calls.test.ts` — dial payload with/without a SIM, validation, connected-session requirement, and
  on-demand contact loading.
- `server.test.ts` — **real TLS/WebSocket integration**: pair a phone, store its key, reject bad
  tokens/rejections, authenticate with a real ECDSA signature, reject a bad signature/unknown phone,
  reject pairing while busy, route a phone event.
- `tls.test.ts` — certificate stability, exact pinned-certificate fingerprint, rejection of an
  untrusted self-signed certificate.
- `store.test.ts` — persistence, dedupe of muted packages, remove/reset, corrupt-file recovery,
  per-device SMS read-state ids (cap/remove/reset).
- `sms-service.test.ts` — local read marking, per-device persistence, mark-all, and the
  `sms.openThread` request payload.
- `e2e/messages-layout.spec.ts` — the conversation list and message history scroll independently at
  1180×780 and 960×640, and the composer stays in view.

Run:

```bash
cd desktop
npm ci
npm run typecheck
npm test
```

### Desktop E2E

`desktop/e2e/smoke.spec.ts` launches the built Electron app with an isolated `userData` directory,
waits for the Connection view and the LinkPhone brand, and fails with the main-process stderr on
error. It runs against the unpacked build before packaging.

```bash
cd desktop
npm run build
npx playwright test
```

## What CI does **not** prove

The following require real hardware and are **not** verified by this repository's CI:

- OEM permission behavior (Xiaomi, Samsung, Oppo, Vivo, Huawei, Pixel, etc.)
- Actual SMS arrival/sending, MMS behavior, dual-SIM selection, and whether the stock SMS app marks
  a conversation read when Open on phone opens it
- Real contact-provider behavior across accounts/OEMs and the contact permission flow
- Notification listener behavior on Android 15+ (redaction, rebind)
- Call answer/decline and caller ID on a real carrier
- MediaProjection consent, secure-window blacking, lock/rotation behavior
- Accessibility gesture injection
- Battery/Doze/OEM background killing
- Real Wi-Fi AP isolation, VPN interference, Windows firewall prompts
- SmartScreen behavior for the unsigned Windows binary

Do not treat a green CI run as proof that those work. Use the checklist below on hardware.

## Device acceptance checklist

Use two devices on the same Wi-Fi. Check each item and keep the diagnostics log if something fails.

### Install and launch

- [ ] Windows app starts, shows Connection, and does not prompt for admin rights.
- [ ] Windows Firewall prompts; allow **Private** networks.
- [ ] APK installs from the release (unknown sources allowed for the file manager).
- [ ] Phone app opens; version and identity fingerprint are visible.

### Pairing and reconnection

- [ ] PC creates a QR; the phone camera scans it.
- [ ] PC shows the phone name and fingerprint; Reject leaves both unpaired.
- [ ] Approve pairs; the PC shows Connected and the phone shows the ongoing notification.
- [ ] Wrong/expired code is rejected; three wrong attempts lock that phone IP briefly.
- [ ] Disconnect the phone, then reconnect: no QR is needed.
- [ ] Toggle Wi-Fi on the phone: it reconnects automatically.
- [ ] Restart the PC: the phone reconnects when the PC app runs.
- [ ] Reboot the phone with auto-connect on: the service returns.
- [ ] Reset PC identity: old phones can no longer connect; a new QR pairs.

### SMS

- [ ] Grant SMS; recent threads load.
- [ ] With 30+ conversations, the conversation list scrolls independently of the message history.
- [ ] Mark one thread read; its unread badge and the nav count clear. Mark all; all clear. Restart
      the PC app; the read state persists. A new incoming SMS becomes unread again.
- [ ] Open on phone: a notification appears on the phone; tapping it opens the stock SMS app and
      that app marks the conversation read. Denying notifications returns a clear error.
- [ ] Send a short message; it arrives on the recipient.
- [ ] Send a multi-part (>160 char) message; it arrives intact.
- [ ] Receive an SMS while connected; the PC refreshes.
- [ ] Dual SIM: choose a SIM if present; verify the correct SIM sent it.
- [ ] Deny SMS: Messages shows a permission explanation, not a crash.
- [ ] Note whether sent items appear in the stock SMS app (expected: may not).

### Notifications

- [ ] Grant notification access; a test notification appears on the PC.
- [ ] Dismiss from the PC; it disappears on the phone.
- [ ] Mute an app; its notifications stop appearing.
- [ ] Android 15+: check whether an OTP notification is redacted (expected: possible).
- [ ] Notifications received while disconnected are absent on reconnect (expected).

### Photos

- [ ] Grant full photos; recent images appear as thumbnails.
- [ ] Save an image; it opens from `Pictures\LinkPhone`.
- [ ] Copy an image; paste it into Paint/Word.
- [ ] Android 14: choose selected photos; only those appear, with a warning.
- [ ] Test a HEIC photo (transcoded to JPEG).

### Calls

- [ ] Grant phone + answer permissions.
- [ ] Incoming call shows on the PC with number/contact if call-log/contacts granted.
- [ ] Answer from the PC where supported; decline on a second call.
- [ ] Grant Contacts; search by name/number; tap a contact and confirm the number fills the dialer.
- [ ] Dial a number; the phone places the call.
- [ ] Dual SIM on Android 11+: select each SIM in the Calls tab and confirm the phone's in-call UI
      uses the selected SIM. Select **Default SIM** and confirm the system default is used.
- [ ] Single SIM: no selector is shown. Android 10: the explanatory note is shown.
- [ ] Confirm there is **no PC audio** (expected; use the phone speaker/headset).
- [ ] Note OEM-specific failures.

### Clipboard and handoff

- [ ] PC → phone: send text; paste on the phone.
- [ ] Phone → PC: add the Quick Settings tile, tap it while LinkPhone is focused; paste on the PC.
- [ ] Share a URL from a browser to LinkPhone; open it on the PC.
- [ ] Share an image; it is saved under `Downloads\LinkPhone`.
- [ ] Send a link back to the phone; the notification opens it.
- [ ] Auto-send PC clipboard (optional): copy text on the PC, then paste on the phone.

### Apps / mirroring

- [ ] Start mirroring; approve the notification; choose full screen or one app.
- [ ] Frames render at a usable rate.
- [ ] Tap/swipe with Accessibility enabled.
- [ ] Disable Accessibility: view-only mode, no remote input.
- [ ] Lock the phone: capture stops.
- [ ] Rotate the phone: the session stops with a restart message.
- [ ] Open a banking/DRM app: black window (expected).
- [ ] Stop mirroring from the PC and from the phone notification.

### Background

- [ ] Ongoing notification is visible and cannot be swiped away.
- [ ] Grant battery exemption; verify the connection survives 30+ minutes idle.
- [ ] OEM autostart settings applied; test after a reboot.
- [ ] Force-stop: the service stops and does not restart until the app is opened (expected).
- [ ] LinkPhone reports disconnection instead of pretending to be connected.

### Security and cleanup

- [ ] Remove the phone on the PC; the phone cannot reconnect until re-paired.
- [ ] Forget the PC on the phone; reconnect requires pairing.
- [ ] Reset PC identity; all phones are unpaired and the fingerprint changes.
- [ ] Revoke a permission; reconnect and confirm the PC capability changes.
- [ ] Uninstall the PC app: port closes, mDNS advertisement disappears.

## Reporting results

Open an issue with:

- Device model, Android version, OEM skin
- Windows version
- Which checklist item failed
- Diagnostics log from the phone (Share) and PC (`userData/logs/linkphone.log`)
- Whether the build was the release APK from GitHub Actions

Do not include SMS/notification content or screenshots with personal data.
