## LinkPhone v0.5.3

A direct, LAN-only companion link between an Android phone and a Windows PC. This release gives both
apps the finished LinkPhone mark: a phone and monitor joined by transfer arrows on the indigo plate.

### What's new

- New logo on the Windows app, installer, Start-menu/desktop shortcuts, taskbar window, and tray icon.
- The logo now appears in the PC sidebar and on the startup/fatal screens instead of the old inline
  SVG mark.
- New Android launcher icon (adaptive foreground, round icon, and Android 13+ themed monochrome layer)
  using the same mark.
- No protocol, permission, SMS, call, photo, mirroring, or updater behavior changed. The v0.5.2
  speaker-route handling is included.

### Which updates do I need?

- **Both apps** if you want the new branding. Functionally, either app can stay on v0.5.2.
- Installed v0.4.0+ Windows builds can update in-app; portable builds update manually.

### Download

- `LinkPhone-0.5.3.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.5.3.exe` — Windows portable app
- `LinkPhone-Setup-0.5.3.exe` — Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

### Known limitations

- Reliable cellular speaker routing still requires the default-dialer/`InCallService` role, which
  LinkPhone deliberately does not take.
- Call audio never reaches the PC.
- Mirroring remains experimental; the callback fix from v0.5.1 is included.
- The Windows updater is unsigned and SHA-256 verified; portable builds update manually.

Built, unit-tested, linted, and E2E-tested in GitHub Actions. Icon rendering on launchers, shortcuts,
and the tray still needs the hardware checklist in `docs/testing.md`.
