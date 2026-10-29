## LinkPhone v0.3.1

A direct, LAN-only companion link between an Android phone and a Windows PC. This release fixes the
Apps tab getting stuck on “Awaiting phone.”

### What's new

- Denying or cancelling Android’s screen-capture prompt now reports the cancellation to the PC, so
  the Apps tab returns to **Start mirroring** with an error instead of waiting forever.
- Dismissing the consent notification now reports “not approved” through a notification delete
  intent.
- Screen-capture startup failures (foreground-service rejection, MediaProjection errors) now send a
  `mirror.state` failure to the PC and post a phone alert.
- The PC clears the pending request on any mirror error and after 90 seconds if the phone never
  responds. Pressing Start and immediately failing no longer leaves the button disabled.
- Mirror setup is transactional: a failure after the projection is acquired closes the projection,
  ImageReader, and handler thread instead of leaking them.
- Stale mirror errors are cleared on disconnect.

No protocol, permission, or layout changes.

### Which updates do I need?

- **Both apps.** The phone now reports every consent outcome; the PC understands the failure and
  clears the request.
- Existing pairings and the signing identity are retained.

### Download

- `LinkPhone-0.3.1.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.3.1.exe` — Windows portable app
- `LinkPhone-Setup-0.3.1.exe` — Windows installer
- `SHA256SUMS.txt` — verify your downloads

### First run

1. Allow LinkPhone through Windows Firewall on **Private** networks.
2. Open the PC app and choose **Pair a phone**.
3. Install the APK, open LinkPhone, and follow the setup guide.
4. Scan the QR code and approve the pairing prompt on the PC.
5. Grant only the permissions you want; each feature explains itself first.

### Known limitations

- Mirroring is not app streaming and has no audio. It requires consent every session and stops on
  screen lock, rotation, or projection revocation; DRM/secure windows appear black.
- Remote taps require the LinkPhone Accessibility service. Android 14 single-app capture can make
  full-screen tap coordinates inaccurate.
- OEM task killers and Doze can still stop the background connection; allow battery optimization
  exemption and OEM autostart.
- SMS is sideload-only; MMS/RCS are not supported, and read sync still uses **Open on phone**.
- The Windows binaries are unsigned, so SmartScreen may warn.

Built, unit-tested, linted, and E2E-tested in GitHub Actions. Consent dialogs and real mirroring
still need the hardware checklist in `docs/testing.md`.
