## LinkPhone v0.3.0

A direct, LAN-only companion link between an Android phone and a Windows PC. This release adds
album browsing to Photos and hardens the Android background connection.

### What's new

**Photos**

- The PC Photos tab now has an album strip: **All photos** plus every image album the phone exposes
  (Camera, Screenshots, Downloads, WhatsApp, and others), with photo counts.
- Selecting an album filters the grid; **Load more** fetches older pages without duplicates.
- Album counts and covers reflect only the photos Android has shared. With Android 14 partial
  access, some albums may be missing or have smaller counts.
- The wire protocol adds an additive `photo.albums` request and `photos.albums` capability. Older
  phones still show the flat recent-photo grid; older PCs ignore the new fields.

**Background connection**

- Closing or swiping the phone app no longer deliberately stops the foreground service:
  `onTaskRemoved` re-asserts it and logs the event.
- A failed foreground-service start no longer calls `stopSelf()`; it retries once with the
  `connectedDevice` type and fails cleanly instead of killing a healthy connection.
- The app now persists whether the link should be up. **Disconnect** stays disconnected across
  screen returns, while boot/restart recovery only reconnects when the link was desired.
- Wi-Fi uses the low-latency lock, and a partial wake lock is held only while connecting or
  reconnecting. It is released as soon as the link is connected.
- Battery optimization still limits background networking on Android; the Background card offers
  the exemption, and diagnostics now log when it is missing.

### Which updates do I need?

- **Both apps** for album browsing. The background changes are phone-only.
- Existing pairings, settings, SMS read state, and the signing identity are retained.

### Download

- `LinkPhone-0.3.0.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.3.0.exe` — Windows portable app
- `LinkPhone-Setup-0.3.0.exe` — Windows installer
- `SHA256SUMS.txt` — verify your downloads

### First run

1. Allow LinkPhone through Windows Firewall on **Private** networks.
2. Open the PC app and choose **Pair a phone**.
3. Install the APK, open LinkPhone, and follow the setup guide.
4. Scan the QR code and approve the pairing prompt on the PC.
5. Grant only the permissions you want; each feature explains itself first.

### Known limitations

- OEM task killers can still kill a foreground service. v0.3.0 logs the event and recovers where
  Android allows; battery/autostart settings may still be required. Force-stop is intentionally
  unrecoverable until the app is opened again.
- Doze can restrict background networking even with a foreground service. Allow battery
  optimization exemption for reliable screen-off operation.
- Cellular call audio cannot be routed to the PC by a third-party app.
- True Android app streaming is not possible. **Apps** is screen mirroring with optional remote taps.
- SMS is sideload-only; MMS/RCS are not supported, and read sync still uses **Open on phone**.
- Mirrored notifications on Android 15+ may have sensitive content redacted.
- The Windows binaries are unsigned, so SmartScreen may warn.

Built, unit-tested, linted, and E2E-tested in GitHub Actions. Album counts/covers, swipe-to-background
behavior, Doze recovery, and OEM task killers still need the hardware checklist in
`docs/testing.md`.
