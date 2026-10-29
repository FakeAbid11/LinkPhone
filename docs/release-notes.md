## LinkPhone v0.7.1

A direct, LAN-only companion link between an Android phone and a Windows PC. This release replaces
the crosshair cursor over the mirrored phone screen with the ordinary mouse pointer.

### What's new

- **The Apps tab now shows your normal mouse pointer.** The mirrored phone screen used to display a
  crosshair, which read as a targeting reticle rather than a mouse. It now behaves like every other
  surface in the app: the standard Windows arrow, with the same click-to-tap and drag-to-swipe
  gestures as before.
- Nothing else changed. Tapping, swiping, the codec and frame-rate pickers, the remote-input
  permissions, and every other feature behave exactly as they did in v0.7.0.

### Which updates do I need?

- **PC v0.7.1** for the new cursor. The Android app has no functional changes; the published APK is
  the same code as v0.7.0 under the new version number.
- Updating only one side is safe; the two apps are versioned independently.

### Download

- `LinkPhone-0.7.1.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.7.1.exe` — Windows portable app
- `LinkPhone-Setup-0.7.1.exe` — Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

Built, unit-tested, linted, and E2E-tested in GitHub Actions.

## LinkPhone v0.7.0

A direct, LAN-only companion link between an Android phone and a Windows PC. This release brings
the Windows in-app updater to the phone.

### What's new

- **The Android app can now update itself.** Home → Updates checks GitHub Releases shortly after
  launch and every six hours, downloads the release APK, verifies it, and offers **Install update**.
- The download is verified twice: against the release's `SHA256SUMS.txt` **and** against the
  signing certificate of the app you are already running. The checksum alone would not be enough,
  because the APK and the checksum file come from the same release.
- **Install update** hands the APK to Android's own package installer. Android shows the app name,
  the version, and the permissions, then replaces the app. LinkPhone cannot install anything
  without that system screen, and it cannot restart itself afterwards.
- The first install needs a one-time grant: **Settings → Install unknown apps → LinkPhone → Allow
  from this source**. The app explains this in a dialog before sending you there.
- The Updates card shows the current version, the check status, when the last check ran, the
  release notes, and a download progress bar. Automatic checks can be switched off; manual checks
  still work.
- A "Update ready" notification appears when a verified update is waiting to be installed.
- Debug builds never contact GitHub and say so in the card.

### Which updates do I need?

- **Phone v0.7.0** for the Android updater. v0.6.1 and earlier work fine and simply have no
  **Updates** card.
- The Windows app is unchanged and still updates in-app from v0.4.0 onwards.
- Updating only one side is safe; the two apps are versioned independently.

### Download

- `LinkPhone-0.7.0.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.7.0.exe` — Windows portable app
- `LinkPhone-Setup-0.7.0.exe` — Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

### Known limitations

- Android requires the "install unknown apps" grant for LinkPhone before an update can be installed,
  and Android (not LinkPhone) performs the install.
- An APK signed with a different key than your installed copy is refused. A debug-signed build
  cannot replace an official installation, and vice versa.
- The update download runs in the app process, not a foreground service. If the process is killed
  mid-download, the partial file is discarded and you check again.
- GitHub's unauthenticated API allows 60 requests per hour per IP, and mobile carrier NAT shares an
  IP between phones; a rate-limit message is possible.
- The Windows updater is unsigned and SHA-256 verified; portable builds update manually.

Built, unit-tested, linted, and E2E-tested in GitHub Actions. The Android install flow needs the
hardware checklist in `docs/testing.md`.

## LinkPhone v0.6.1

A direct, LAN-only companion link between an Android phone and a Windows PC. This release adds an
opt-in 60 fps mode to the hardware H.264 mirror.

### What's new

- The Apps tab now has a **Frame rate** choice: **30 fps (balanced)** or **60 fps (smoother)**.
- 30 fps remains the default and keeps the 6 Mbps H.264 stream. 60 fps raises the target to 12 Mbps.
- If the phone's encoder cannot handle the current size at 60 fps, LinkPhone stays at 30 fps and the
  Apps tab says so; mirroring still starts normally.
- The Session card already shows the measured frame rate alongside the codec and dimensions.
- JPEG/Compatible mode is unchanged and still limited to about 5 fps.

### Which updates do I need?

- **Phone + Windows v0.6.1** for the 60 fps choice. v0.6.0 continues to work at 30 fps.
- Updating only one side is safe: the PC keeps 30 fps and mirroring works.
- Installed v0.4.0+ Windows builds can update in-app; portable builds update manually.

### Download

- `LinkPhone-0.6.1.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.6.1.exe` — Windows portable app
- `LinkPhone-Setup-0.6.1.exe` — Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

### Known limitations

- 60 fps needs a 60 Hz+ display, a hardware encoder that supports the stream size at 60 fps, and a PC
  that can decode it; otherwise the session runs at 30 fps.
- 60 fps uses more bandwidth, battery, and thermal headroom than 30 fps.
- H.264 still depends on the phone's hardware encoder and the PC's WebCodecs decoder; unsupported
  devices stay on JPEG.
- Secure/DRM windows remain black, and mirroring still stops on lock or rotation.
- Reliable cellular speaker routing still requires the default-dialer/`InCallService` role, which
  LinkPhone deliberately does not take. Call audio never reaches the PC.
- The Windows updater is unsigned and SHA-256 verified; portable builds update manually.

Built, unit-tested, linted, and E2E-tested in GitHub Actions. Real 30/60 fps behavior needs the
hardware checklist in `docs/testing.md`.
