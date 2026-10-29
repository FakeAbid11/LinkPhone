## LinkPhone v0.4.0

A direct, LAN-only companion link between an Android phone and a Windows PC. This release adds a
built-in updater for the installed Windows build.

### What's new

- **In-app Windows updates.** The installed Setup build checks the official GitHub Releases page
  after launch and every six hours, downloads a newer Setup EXE in the background, and shows an
  **Update ready** banner with **Restart and install**.
- **Settings → Updates** shows the current version, check status, download progress, release notes,
  and a toggle for automatic checks. A **Check for updates** action is also in the tray menu.
- Downloads are verified against the release's `SHA256SUMS.txt` before the installer can run. A
  mismatched or truncated download is deleted and reported; it is never executed.
- **Portable builds** cannot replace their running EXE, so they show a **Download portable** link
  instead of automatic installation.
- The installer opens after LinkPhone has stopped its connection service; you can cancel it if
  needed.

### Security note

The Windows binaries are still unsigned. The updater uses HTTPS and SHA-256 from the same official
release, but it does not provide Authenticode publisher verification. Only install updates from this
repository's releases. If a Windows code-signing certificate is added later, the updater should be
switched to signature verification.

### Which updates do I need?

- **Both apps** for the same feature set; the updater itself is Windows-only.
- This is the first updater-enabled release, so existing installs must install v0.4.0 manually once.
  Later v0.4.x releases can update in-app.
- The v0.3.2 photo-save fixes are included.

### Download

- `LinkPhone-0.4.0.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.4.0.exe` — Windows portable app
- `LinkPhone-Setup-0.4.0.exe` — Windows installer (use this for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

### First run

1. Install the Windows Setup build (not the portable EXE) to enable automatic updates.
2. Allow LinkPhone through Windows Firewall on **Private** networks.
3. Open the PC app and choose **Pair a phone**.
4. Install the APK, open LinkPhone, and follow the setup guide.
5. Scan the QR code and approve the pairing prompt on the PC.

### Known limitations

- The updater downloads the full installer; there are no delta updates.
- The NSIS installer opens after restart; installation is not silent.
- Portable builds require a manual download/replacement.
- Doze, OEM task killers, and unsigned-build SmartScreen warnings still apply.
- Mirroring remains experimental and is not app streaming.
- SMS remains sideload-only with no MMS/RCS.

Built, unit-tested, linted, and E2E-tested in GitHub Actions. The updater's real install/upgrade path
still needs the manual Windows checklist in `docs/testing.md`; CI cannot install an update.
