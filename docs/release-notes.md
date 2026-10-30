## LinkPhone v0.7.4

A direct, LAN-only companion link between an Android phone and a Windows PC. This release brings the
phone app fully onto the Material 3 component system.

### What's new

- **Dialogs now match the app.** Android draws a dialog on the `surfaceContainerHigh` colour, and the
  theme never defined that colour — so the "Forget this PC?", install-permission and permission
  prompts sat on Material's stock grey instead of LinkPhone's own surface. They now use the app's
  palette in both the light and the dark theme.
- **The rest of the Material 3 surface set is defined too** — the surface container levels, the dim
  and bright surfaces, the inverse colours a snackbar would use, and the scrim. Anything Material 3
  the app adds later now inherits the LinkPhone palette instead of falling back to Google's defaults.
- **The switch tracks match the surrounding card** in both themes.
- **The QR scanner uses the palette** instead of two hard-coded colours.
- **Corners follow one shape scale** instead of four separate values, and the four remaining type
  styles are defined so nothing falls back to Roboto.
- **Screen readers read a settings row as one item** instead of three, and a permission button
  announces what it grants ("Grant Messages" rather than just "Grant").
- The look is unchanged apart from corners moving by one or two pixels. The Windows app is unchanged.

### Which updates do I need?

- **Phone v0.7.4** for the corrected colours and accessibility. The PC app needs nothing.
- Updating only one side is safe; the two apps are versioned independently.

### Download

- `LinkPhone-0.7.4.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.7.4.exe` — Windows portable app
- `LinkPhone-Setup-0.7.4.exe` — Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

Built, unit-tested, linted, and E2E-tested in GitHub Actions. The palette checks run in CI; the
visual check needs the device checklist in `docs/testing.md`.

## LinkPhone v0.7.3

A direct, LAN-only companion link between an Android phone and a Windows PC. This release turns the
Android first-run guide into a working setup wizard.

### What's new

- **Step 2 of the phone setup now grants the permissions instead of describing them.** Every feature
  gets a row that says what the permission is for and its own **Grant** button, which shows that
  feature's real Android dialog.
- **One "Grant all" button** asks for every runtime permission in turn, so a new install can be
  ready in a few taps. When nothing is missing the button becomes **Continue**.
- **Special access is separated and labelled.** Notification access, Remote taps and battery
  optimisation open a system screen rather than a popup, and the page says so instead of leaving you
  hunting for a dialog.
- **A permission you declined twice points at the app settings.** Android stops showing a prompt after
  two denials, so that row's button changes to **Open settings** instead of silently doing nothing.
- **The whole flow looks like the rest of the app.** It uses the same top bar and cards as Home, the
  buttons stay pinned to the bottom so the primary action is never below the fold, and pages slide
  instead of jumping.
- Nothing else changed. The Windows app is unchanged.

### Which updates do I need?

- **Phone v0.7.3** for the new setup wizard. The PC app needs nothing.
- Updating only one side is safe; the two apps are versioned independently.

### Download

- `LinkPhone-0.7.3.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.7.3.exe` — Windows portable app
- `LinkPhone-Setup-0.7.3.exe` — Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

Built, unit-tested, linted, and E2E-tested in GitHub Actions. The permission prompts need the device
checklist in `docs/testing.md`.

## LinkPhone v0.7.2

A direct, LAN-only companion link between an Android phone and a Windows PC. This release makes the
notification mirror live and shows the phone's battery on the PC.

### What's new

- **The Notifications tab now mirrors the phone live.** It used to be an event log: it showed only
  the notifications that arrived while the PC was connected, and it had no way to ask the phone what
  was on screen. The phone now reports its current notification set, so the PC shows what the phone
  has right now — including notifications that arrived while the PC was away.
- **A notification whose number changed is rewritten in place.** When a messaging app updates its
  notification from "3 unread" to "4 unread", the card on the PC updates to "4 unread" instead of
  sitting there with the old count.
- **A Windows popup appears only for a genuinely new notification.** A changed count updates the
  card quietly, so a busy counter does not fill the screen with popups.
- **The Refresh button now really refreshes.** It asks the phone for its current notifications
  instead of re-drawing what the PC already had.
- **The phone's battery level is shown on the PC.** The Connection tab reports the charge level and
  whether the phone is charging, and a small chip in the sidebar keeps it visible from every tab.
  A low battery is coloured amber, and red under 15 %.
- Nothing else changed. Messages, photos, calls, mirroring, and the updaters behave exactly as they
  did in v0.7.1.

### Which updates do I need?

- **PC v0.7.2** for the live notifications and the battery indicator. The Android app needs
  **v0.7.2** too, because the phone has to be able to report its current notifications and its
  battery level; a v0.7.1 phone keeps working but the PC falls back to the old one-way feed.
- Updating only one side is safe; the two apps are versioned independently.

### Download

- `LinkPhone-0.7.2.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.7.2.exe` — Windows portable app
- `LinkPhone-Setup-0.7.2.exe` — Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

Built, unit-tested, linted, and E2E-tested in GitHub Actions. The live notification mirror and the
battery indicator need the hardware checklist in `docs/testing.md`.

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
