## LinkPhone v0.8.5

A diagnosis release. Pairing, encryption and the transport are unchanged, so this is a drop-in
update over 0.8.4 — but a failed pairing now explains itself, and a code whose address has gone
stale can recover on its own.

### What's new

- **A failed pairing says what to do.** Every failure used to be the raw exception —
  "Failed to connect to /192.168.0.111:48921" — which names the layer but not the fix. Failures are
  now classified: refused, no route, timed out, wrong certificate, rejected by the PC, or an
  unreadable answer. Each gets one sentence and a short checklist. The original text is still there
  under **Technical detail**, with a **Copy detail** button.
- **The phone's address and the PC's are shown side by side.** When the phone cannot reach the PC,
  the card shows `This phone 192.168.1.23/24` next to `PC in the code 192.168.0.111`, so a subnet
  mismatch is visible instead of guessed at.
- **Pairing retries at the PC's real address.** If the code's address refuses the connection, the
  phone looks the PC up over mDNS by the certificate fingerprint carried in the QR and retries once
  at the address the PC actually holds. The fingerprint is unchanged, so the trust anchor still
  comes only from the QR, and the address that worked is the one that gets saved.
- **The PC log records every inbound attempt** — TCP connection, TLS handshake, hello, pair
  request. "The phone cannot connect" finally has an answer, including "nothing arrived at all",
  which is itself the diagnosis.
- **The Connection tab shows the address inside the code**, and warns when it no longer matches the
  PC's current address.

### What's fixed

- **The pairing code no longer advertises a VPN, Hyper-V, WSL or VMware adapter.** A virtual adapter
  holds an ordinary private address, so it could win the QR and be unreachable from the phone —
  the exact cause of an instant "unreachable" while both devices are on the same Wi-Fi.
- **Pairing failures reach Diagnostics.** They used to leave no trace on either side, which made a
  network problem look like an app bug.

If pairing still fails, see [troubleshooting](troubleshooting.md#failed-to-connect-to-192168011148921):
opening `https://<pc-ip>:<port>/` in the phone's browser separates a wrong address from a dropped
packet in one step.

### Downloads

- `LinkPhone-0.8.5.apk` - Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.8.5.exe` - Windows portable app
- `LinkPhone-Setup-0.8.5.exe` - Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` - checksums for the files above

## LinkPhone v0.8.4

A pairing fix. No pairing, encryption or transport change, so this is a drop-in update over 0.8.3.

### What's fixed

- **Pairing no longer fails as "declined" with no prompt.** If the phone dropped while the
  approval dialog was open — screen lock, a Wi-Fi blip, or the app going to the background — the
  PC kept the stale prompt, and every later scan was answered instantly with "Pairing was
  declined on the PC." and no dialog at all. The prompt is now cleared the moment the phone
  disconnects, and "New code" clears it too.
- **The approval dialog says what happened.** It shows a countdown, and when it ends the
  Connection tab explains why — timed out, cancelled, replaced by a newer request, or the phone
  disconnected — instead of the dialog simply disappearing.
- **Approving a request whose phone is gone no longer wastes the code.** The pairing code stays
  valid, so you can scan again without creating a new one.
- **A second phone no longer cancels the one in front of you.** The newest request replaces the
  pending prompt, and the older phone is told it was replaced.

If you are stuck on 0.8.3, quitting LinkPhone from the tray and starting it again clears the
stale prompt; updating to 0.8.4 fixes it for good.

### Downloads

- `LinkPhone-0.8.4.apk` - Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.8.4.exe` - Windows portable app
- `LinkPhone-Setup-0.8.4.exe` - Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` - checksums for the files above

## LinkPhone v0.8.3

A layout release for the Windows app. No pairing, encryption or transport change, so this is a
drop-in update over 0.8.2.

### What's new

- **Cards no longer stretch to match their neighbours.** A short card used to grow to the height
  of the tallest one beside it, which left large empty blocks under the Calls dialer and beside
  the notification feed.
- **One place for each fact.** The paired-phone list, the per-app mute control and the version
  number each appeared twice; the duplicate on Settings, inside every notification, and in the
  security card are gone. Settings also loses its "Firewall and network" card, which only told
  you to look at Connection.

### What's fixed

- **The message composer stays on screen.** The panel was sized from a fixed viewport offset
  that assumed no page header and no banner, so with a banner the composer dropped below the
  fold and a second scrollbar appeared.
- **The photo preview opens wide.** The wide-dialog style was being overridden, so full-size
  photos opened in the narrow dialog.
- **Mirroring draws a phone, not a strip.** A tall phone screen used to render letterboxed in a
  wide black box, and because taps are mapped across the whole box, they landed in the wrong
  place.
- **The sidebar keeps its status line on small windows.** At the minimum window height the
  connection state and port were clipped; the navigation list now scrolls instead.
- **Long shared text scrolls instead of stopping mid-sentence.** Clipboard and share items were
  cut off at a fixed height with no way to read the rest.
- **The Connection tab shows the address and fingerprint once,** with copy buttons, instead of
  repeating them in the pairing card — and no longer says "on the right" when the two columns
  have stacked.

### Downloads

- `LinkPhone-0.8.3.apk` - Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.8.3.exe` - Windows portable app
- `LinkPhone-Setup-0.8.3.exe` - Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` - checksums for the files above

## LinkPhone v0.8.2

A consistency and reliability release. No pairing, encryption or transport change, so this is a
drop-in update over 0.8.1.

### What's new

- **Both apps draw the same icons.** Windows and Android now use Material Symbols from a single
  source, so a feature looks the same on the PC and on the phone. The phone also stops bundling
  the old icon library, which shrinks the APK.
- **Android uses the Windows theme.** The phone always renders the same dark palette as the PC
  and no longer follows the wallpaper. "Match wallpaper colors" is gone from the About card, and
  a phone set to light mode still gets the LinkPhone dark theme.

### What's fixed

- **The enabled switch no longer draws outside its track.** A leftover rule from before the
  Material 3 redesign moved the knob past the end of the pill and off its vertical centre.
- **Notification access is no longer reported as missing.** When Android had not yet bound the
  notification listener, the PC claimed the permission had never been granted. It now waits for
  the listener and, if it stays unbound, says to turn notification access off and on.

### Downloads

- `LinkPhone-0.8.2.apk` - Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.8.2.exe` - Windows portable app
- `LinkPhone-Setup-0.8.2.exe` - Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` - checksums for the files above

## LinkPhone v0.8.1

A reliability release on top of 0.8.0. No pairing, encryption or transport change, so this is a
drop-in update for both apps.

### What's fixed

- **Phone details can no longer blank the PC window.** The model, SIM list and feature list the
  phone reports are now validated as they arrive. A malformed SIM entry used to reach the
  interface and replace the whole window with an error.
- **"Not reported yet" is no longer shown as "not granted".** When the phone has not sent its
  permissions, Messages, Notifications, Photos and Calls say so instead of claiming access was
  denied.
- **Enter no longer sends while you are composing.** Pressing Enter to commit an IME candidate
  (Chinese, Japanese, Korean) used to send a half-typed message. Shift+Enter still adds a line.
- **Dialogs keep keyboard focus.** Closing a dialog returns focus to the button that opened it,
  and the page behind a dialog is hidden from screen readers while it is open.
- **Input is checked at the boundary.** Touch coordinates are validated before they reach the
  phone, and pairing-code comparison no longer depends on the code's length.
- **A phone-side reporting failure no longer loops the connection.** If the phone cannot build
  its feature report, the session stays up and the PC shows the waiting state instead of
  reconnecting forever.

### Downloads

- `LinkPhone-0.8.1.apk` - Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.8.1.exe` - Windows portable app
- `LinkPhone-Setup-0.8.1.exe` - Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` - checksums for the files above

## LinkPhone v0.8.0

A design release. Both apps are rebuilt on Material 3: the Android companion uses the
Material 3 colour roles, type scale and shape system (with Material You on Android 12+), and
the Windows app adopts the same roles, tonal surfaces and component shapes. Nothing in the
pairing, encryption or transport protocol changed, so this is a drop-in update over 0.7.6.

### What's new

- **Android is Material 3.** The theme now uses a full Material 3 tonal palette around a blue
  seed, the canonical shape scale (4/8/12/16/28 dp) and the Material 3 type scale. Cards step up
  the surface-container ladder instead of drawing shadows, buttons are filled or tonal, and
  notices, permission rows and status pills use the container/on-container pairs.
- **Material You.** On Android 12 and later, LinkPhone follows the wallpaper-derived palette by
  default. The About card on the home screen has a "Match wallpaper colors" switch to fall back to
  the LinkPhone blue theme; the choice is remembered.
- **A new home screen.** The home screen has a large collapsing top app bar with the live
  connection state, an extended "Pair" floating action button, a redesigned connection card, and
  labelled quick actions.
- **The Windows app is Material 3 too.** The navigation rail is now a Material 3 drawer with
  pill-shaped destinations, the content sits on tonal surface containers, buttons are pills,
  text fields are filled with a focus underline, the toggle is a Material 3 switch, and dialogs,
  snackbars, chips and notices all use the Material 3 shapes and roles.

### What's fixed

- **Text stays legible at every window size.** All small labels are at least 11 px, and the
  two-column layout still collapses to one column on narrow windows.

### Downloads

- `LinkPhone-0.8.0.apk` - Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.8.0.exe` - Windows portable app
- `LinkPhone-Setup-0.8.0.exe` - Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` - checksums for the files above

## LinkPhone v0.7.6

A direct, LAN-only companion link between an Android phone and a Windows PC. A small follow-up to
0.7.5 that closes the last review findings which could be verified without a device.

### What's fixed

- **A photo transfer no longer loses its first chunk.** The phone starts sending as soon as it
  answers `photo.fetch`, so the reply and the first chunks can arrive together. The PC used to drop
  a chunk that had no stream yet, and the transfer then stalled until it timed out. The PC now
  holds those leading frames for two seconds and replays them once the stream is registered.
- **A thread that is only MMS is no longer invisible.** Rows the SMS provider cannot give a body
  for were filtered out on both sides, which hid the whole conversation. They now appear with
  "(no text)" instead of disappearing.
- **The photo preview is the real image.** The modal used to show the 512 px thumbnail; it now
  fetches the full-size JPEG, and says so when it cannot be loaded.
- **The "All photos" chip no longer shows an invented total.** It summed the album counts, which
  can disagree with the camera roll. The per-album counts stay.
- **Docs:** `docs/security.md` now states that the current SMS list and notification feed travel to
  the renderer in each state snapshot while the phone is connected, and are cleared when the
  session ends.
- A binary frame for an unknown stream is logged once instead of vanishing silently, so a stalled
  transfer is diagnosable.

### Which updates do I need?

- **Both.** The phone change (MMS-only threads) and the PC changes are independent, so updating one
  side is safe.

### Known issues

- Unchanged from v0.7.5: the JPEG mirror path still needs a device check for colour accuracy, sent
  SMS still report only "handed to the phone", and a downloaded Android update must be re-checked
  after the app restarts.

### Download

- `LinkPhone-0.7.6.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.7.6.exe` — Windows portable app
- `LinkPhone-Setup-0.7.6.exe` — Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

Built, unit-tested, linted, and E2E-tested in GitHub Actions.

## LinkPhone v0.7.5

A direct, LAN-only companion link between an Android phone and a Windows PC. This release is a
bug-fix pass over both apps: one data-loss bug, one reconnect loop, and a set of smaller
correctness problems found in a full read-through of the code.

### What's fixed

- **A crash can no longer un-pair your phone.** The PC saved `state.json` by truncating the live
  file and copying over it. A power loss in that window left an unreadable file, and the app
  answered by generating a new PC identity — after which every paired phone failed the identity
  check and stopped reconnecting with "The PC identity does not match the paired PC". The file is
  now replaced atomically, the previous copy is kept as `state.json.bak`, and a corrupt file is
  recovered from that backup instead of losing the identity.
- **A large SMS history no longer loops the connection.** The PC asks for up to 500 messages; on a
  phone with many long ones the reply passed the 1 MiB protocol limit, the PC closed the session,
  and the phone — treating that close as retryable — reconnected and asked again. The phone now
  trims the oldest messages until the reply fits.
- **The phone's clipboard is no longer echoed back to it.** With auto-send on, text sent from the
  phone to the PC was immediately sent back, posting a "Copied from PC" notification each time.
- **Messages use the SIM you expect.** The composer used to preselect the first SIM. It now starts
  on **Default SIM** and only sends a subscription when you pick one.
- **Caller ID no longer blocks the phone.** The contact lookup ran on the main thread inside the
  call-state callback, on every ring, answer and hang-up.
- **The mirrored screen is no longer cropped** on phones with a camera cutout, because the virtual
  display is sized from the real display instead of the app window.
- **Notifications:** no phantom "Phone disconnected" on every reconnect, and a notification that
  arrives while the list is refreshing is no longer overwritten by the older list.
- **The updater keeps working when the server cannot start**, and an installer that is already
  downloaded is reused instead of fetched again after a restart.
- Smaller fixes: a failed SMS send puts your text back in the composer; "New code" cancels the old
  pairing code first; a rejected image share says so instead of vanishing; the Quick Settings
  clipboard tile refreshes; a photo error surfaces as an error instead of an empty album; a phone
  that is already paired can re-pair while connected; and the Windows app no longer builds the
  whole snapshot twice per update.

### Which updates do I need?

- **Both.** The phone app and the PC app both changed. Updating one side is safe, but the fixes
  above are split between them.

### Known issues

- The JPEG mirror path — phones without a hardware H.264 encoder, and the PC's "Compatible" mode —
  has not been checked on a device for colour accuracy.
- Sent SMS are reported as handed to the phone; delivery reports are not read back yet.
- After Android restarts the app, a downloaded update has to be re-checked before it can be
  installed.

### Download

- `LinkPhone-0.7.5.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.7.5.exe` — Windows portable app
- `LinkPhone-Setup-0.7.5.exe` — Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

Built, unit-tested, linted, and E2E-tested in GitHub Actions. The device checklist in
`docs/testing.md` still applies to the phone-only paths.

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
