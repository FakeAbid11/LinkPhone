## LinkPhone v0.5.1

A direct, LAN-only companion link between an Android phone and a Windows PC. This is a small fix for
screen mirroring on Android 14 and later.

### What's new

- **Fixed “Screen sharing failed: Must register a callback before starting capture.”**
  `MirrorController` now registers the `MediaProjection.Callback` before creating the virtual
  display, as Android 14+ requires. Approving screen capture should now start the mirror instead of
  failing immediately.
- Existing limits are unchanged: consent is required every session, capture stops on lock/rotation,
  DRM/secure windows are black, and audio is never captured.
- No protocol, permission, call, SMS, album, or updater changes.

### Which updates do I need?

- **Phone app only** for the mirroring fix. The Windows app can stay on v0.5.0/v0.4.x, though both
  apps being current is always recommended.
- Installed v0.4.0+ builds can update to this release in-app through the Windows updater.

### Download

- `LinkPhone-0.5.1.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.5.1.exe` — Windows portable app
- `LinkPhone-Setup-0.5.1.exe` — Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

### Known limitations

- Mirroring is screen mirroring, not app streaming, and has no audio.
- Taps require the Accessibility service; Android 14 single-app capture and letterboxed portrait
  video can still make tap coordinates inaccurate.
- Cellular call audio never reaches the PC; speaker control remains best effort.
- The Windows updater is unsigned and SHA-256 verified; portable builds update manually.

Built, unit-tested, linted, and E2E-tested in GitHub Actions. A real MediaProjection session still
needs the hardware checklist in `docs/testing.md`.
