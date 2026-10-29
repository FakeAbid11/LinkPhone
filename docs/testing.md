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
- `ThemeContrastTest` — WCAG contrast for the light and dark palette's body text, secondary text,
  primary text, button labels, outlines, and status-pill containers.
- `PhotoAlbumAccumulatorTest` — album counts, date-based covers, latest timestamps, and name fallback.
- `PhotoQueryTest` — MediaStore bucket ids, keyset args, zero-date fallback, and valid-row paging.
- `PhotoRepositoryTest` — sampling for large images and invalid dimensions.
- `ConnectionPolicyTest` — auto-start/boot gating on paired, auto-connect, and persisted intent.
- `AvcConfigTest` / `MirrorSizingTest` / `MirrorCodecChoiceTest` / `VideoFrameCodecTest` — Annex B
  SPS/PPS parsing, codec strings, 720×1600 sizing/step-down, codec negotiation, and type-4
  flags/timestamps.

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
- `calls.test.ts` — dial payload with/without a SIM, answer/end/speaker requests, speaker state
  mapping, validation, connected-session requirement, and on-demand contact loading.
- `server.test.ts` — **real TLS/WebSocket integration**: pair a phone, store its key, reject bad
  tokens/rejections, authenticate with a real ECDSA signature, reject a bad signature/unknown phone,
  reject pairing while busy, route a phone event.
- `tls.test.ts` — certificate stability, exact pinned-certificate fingerprint, rejection of an
  untrusted self-signed certificate.
- `store.test.ts` — persistence, dedupe of muted packages, remove/reset, corrupt-file recovery,
  per-device SMS read-state ids (cap/remove/reset).
- `sms-service.test.ts` — local read marking, per-device persistence, mark-all, and the
  `sms.openThread` request payload.
- `thread-filter.test.ts` — conversation search by name, address, and latest message, including
  case/whitespace handling and no-match results.
- `palette.test.ts` — parses the `:root` tokens in `styles.css` and asserts WCAG contrast for body,
  muted, caption, primary, secondary, and primary-button text.
- `photos.test.ts` — first page and album requests, selecting an album, keyset `beforeDate/beforeId`,
  paging/dedupe, unsupported phones, invalid responses, and thumbnail single-flight.
- `mirror.test.ts` — start/stop requests, codec payloads, denial/error state propagation, binary-frame
  gating, keyframe requests, and touch validation.
- `h264.test.ts` / `decoder.test.ts` — Annex B/SPS parsing, codec strings, keyframe gating, decode
  backlog recovery, and error fallback.
- `updater.test.ts` — version comparison, tag/checksum parsing, check/download/ready phases, progress
  and checksum rejection, portable/disabled no-ops, install gating, and auto-check timers.
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

`desktop/e2e/smoke.spec.ts` launches the built Electron app with an isolated `userData` directory
and checks the Connection view, brand, skip link, `aria-current` navigation, one `<h1>`, modal focus
trapping/Escape/focus restore, and that no console errors were logged. `ui-quality.spec.ts` checks
two-column layout at 1440 and 1180, single-column at 960, no horizontal overflow, no text below
11 px, a visible keyboard focus ring, and every view's heading. It fails with the main-process
stderr on error. Both run against the unpacked build before packaging.

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
- [ ] Phone app opens; a fresh install shows the three-step setup guide, and an already-paired
      phone skips it.
- [ ] Phone app opens; version and identity fingerprint are visible.
- [ ] Switch Android between light and dark mode; every screen remains readable.
- [ ] Confirm the indigo/violet accent is used consistently on the PC and phone.
- [ ] Windows shortcut, taskbar window, and tray show the new phone/monitor mark; the PC sidebar
      logo matches it.
- [ ] Android launcher, round icon, and (on Android 13+) themed icon show the same mark without
      clipped edges.

### Interface and accessibility

- [ ] PC: Tab reaches the skip link first; each nav item shows a focus ring and `aria-current`.
- [ ] PC: the Connection and Calls pages show two columns at the default window size and one column
      when the window is narrowed below 1040 px.
- [ ] PC: opening Reset PC identity and pressing Tab keeps focus inside the dialog; Escape closes it
      and returns focus to the button.
- [ ] PC: conversation search filters by name, number, and message text.
- [ ] Phone: rotate on Home, Pair, and a consent screen; the screen and entered text are retained.
- [ ] Phone: open Pair and focus a text field; the keyboard does not cover the field or the Pair
      button.
- [ ] Phone: deny a runtime permission once; the rationale appears again. Deny permanently and
      confirm the action changes to **Open settings**.
- [ ] Phone: TalkBack announces status as text, not color alone; touch targets are at least 48 dp.

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
- [ ] Click an unread conversation; it opens and its unread badge/nav count clear immediately.
- [ ] Confirm the per-thread **Mark read** button is gone and **Mark all read** still works.
- [ ] Restart the PC app; the click-marked read state persists. A new incoming SMS becomes unread again.
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
- [ ] The album strip lists Camera, Screenshots, Downloads, and an app album (for example WhatsApp).
- [ ] Select each album; only that album’s photos appear; counts in the strip match the phone.
- [ ] Open a large album and press **Load more** until the end; no duplicates and the button stops.
- [ ] Save an image; it opens from `Pictures\LinkPhone`.
- [ ] Save an image stored on an SD card/secondary volume.
- [ ] Save a large panorama and a HEIC image; the saved file opens.
- [ ] Copy an image; paste it into Paint/Word.
- [ ] Android 14: choose selected photos; only those appear, with a warning and reduced album counts.
- [ ] Test a HEIC photo (transcoded to JPEG).

### Calls

- [ ] Grant phone + answer permissions.
- [ ] Incoming call shows on the PC with number/contact if call-log/contacts granted.
- [ ] Answer from the PC where supported; decline on a second call.
- [ ] Start an active call, switch away from Calls; the global banner shows **Call in progress** and
      **End call**. End it from the banner and confirm the state returns to idle.
- [ ] In the Calls tab, use **End call**; confirm the success text says the request was sent, not
      that the call ended before the phone reports idle.
- [ ] Toggle **Speaker off/on** during an active call. Confirm the label updates only when the
      phone reports the route changed; otherwise the PC shows the explicit blocked error and stays
      off. Test Android 10, 12, and 14/15, with and without Bluetooth/headset.
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

- [ ] Start mirroring with **Auto (H.264)**; approve the notification; choose full screen or one app.
- [ ] Confirm it does **not** fail with “Must register a callback before starting capture.”
- [ ] H.264: the Session card shows **H.264**, roughly 720×1600, and about 30 fps; text is sharp.
- [ ] Switch to **Compatible (JPEG)** and start again: the Session card shows **JPEG** and ≤5 fps.
- [ ] Confirm the phone falls back to JPEG if H.264 is unavailable (Session card shows JPEG).
- [ ] Deny the capture prompt: the PC shows an error and returns to **Start mirroring**.
- [ ] Tap the consent notification, then press Back/Cancel: the PC shows a cancellation, not
      “Awaiting phone.”
- [ ] Dismiss the consent notification: the PC reports that consent was not approved.
- [ ] Ignore the consent notification: the PC clears the request after 90 seconds.
- [ ] Start while notifications are denied: the PC shows an immediate error, not “Awaiting phone.”
- [ ] Tap/swipe with Accessibility enabled; verify taps track the picture in both codecs.
- [ ] Disable Accessibility: view-only mode, no remote input.
- [ ] Lock the phone: capture stops.
- [ ] Rotate the phone: the session stops with a restart message.
- [ ] Open a banking/DRM app: black window (expected).
- [ ] Stall Wi-Fi briefly during H.264: the picture recovers after a fresh key frame.
- [ ] Old Windows build + new APK: mirroring still works in JPEG mode.
- [ ] New Windows build + v0.5.3 APK: the phone falls back to JPEG without errors.
- [ ] Stop mirroring from the PC and from the phone notification.

### Background

- [ ] Ongoing notification is visible and cannot be swiped away.
- [ ] Swipe LinkPhone from Recents: the notification stays and the PC remains connected.
- [ ] Screen off for 10+ minutes with battery exemption: the PC link survives; check for
      `Heartbeat timeout` in the PC log.
- [ ] Force Doze (`adb shell dumpsys deviceidle force-idle`) and confirm the app recovers when the
      network returns.
- [ ] Grant battery exemption; verify the connection survives 30+ minutes idle.
- [ ] OEM autostart settings applied; test after a reboot.
- [ ] Force-stop: the service stops and does not restart until the app is opened (expected).
- [ ] LinkPhone reports disconnection instead of pretending to be connected.

### Windows updates

- [ ] Install the Setup build (not portable) and confirm **Settings → Updates** shows the current version.
- [ ] Toggle automatic checks off; confirm no check starts after the next launch and manual checks still work.
- [ ] Publish a higher version; confirm auto-check finds it, downloads with progress, verifies the
      checksum, and shows the **Update ready** banner.
- [ ] Click **Restart and install**; confirm LinkPhone stops, the Setup wizard opens, and the app
      reports the new version after installation.
- [ ] Click **Later**; confirm the banner hides and Settings still shows **Restart and install**.
- [ ] Simulate an offline check; confirm a readable error and no installer.
- [ ] Simulate a checksum mismatch; confirm the file is rejected/deleted and never executed.
- [ ] Portable build: confirm automatic updates are unavailable and **Download portable** opens Releases.
- [ ] E2E/CDP launch: confirm the Updates card says automatic updates are unavailable and no network
      request is made.

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
