## LinkPhone v0.1.2

A direct, LAN-only companion link between an Android phone and a Windows PC.

### What's new

- **The conversation list scrolls independently.** The Messages layout now constrains its grid row,
  so long thread lists scroll in the left pane while the message history scrolls on the right.
- **Mark read / Mark all read.** Unread badges (threads and the Messages nav count) can be cleared
  in LinkPhone on the PC and are remembered per paired phone across restarts. A new SMS becomes
  unread again.
- **Open on phone.** For a selected thread, the PC asks the phone to show a notification. Tapping
  it opens the conversation in the stock SMS app, which marks it read. This is the Android-supported
  way to update the phone's read state without LinkPhone becoming your default SMS app.
- No new permissions. Existing pairings, settings, and the signing identity are retained.

### Which updates do I need?

- **PC only:** fixes the conversation-list scrolling and PC-local read tracking. The v0.1.1 phone
  app remains compatible.
- **Both apps:** required for **Open on phone**. The PC hides that button when the phone is older
  than v0.1.2. The APK is signed with the same key, so it installs over v0.1.1.

### Download

- `LinkPhone-0.1.2.apk` — Android 10+ companion (sideload; signed with the same personal release key)
- `LinkPhone-Portable-0.1.2.exe` — Windows portable app
- `LinkPhone-Setup-0.1.2.exe` — Windows installer
- `SHA256SUMS.txt` — verify your downloads

### First run

1. Allow LinkPhone through Windows Firewall on **Private** networks.
2. Open the PC app and choose **Pair a phone** (or reuse the existing pairing).
3. Install the APK, open LinkPhone, and scan the QR code.
4. Approve the pairing prompt on the PC.
5. Grant only the permissions you want on the phone.

### Known limitations

- **Phone read sync is not silent.** Android only lets the default SMS app write the SMS provider's
  read flag. **Open on phone** requires a tap on the LinkPhone notification; the stock SMS app then
  marks the conversation read. Behavior can vary by OEM/app.
- **Mark read is local to the PC** until you use Open on phone or read the thread on the phone.
- Cellular call audio cannot be routed to the PC by a third-party app. Calls are controlled from
  the PC; audio stays on the phone.
- True Android app streaming is not possible. **Apps** is experimental screen mirroring with
  optional remote taps; it requires consent every session and stops when the phone locks.
- SMS is available for sideloaded apps only; MMS/RCS are not supported. Sent messages may not
  appear in your stock SMS app's thread.
- Mirrored notifications on Android 15+ may have sensitive content redacted.
- Phone-to-PC clipboard is explicit (Quick Settings tile/share action), not automatic.
- Background operation requires an ongoing notification and, on many OEMs, battery/autostart
  settings.
- The Windows binaries are unsigned, so SmartScreen may warn.

Built and unit/E2E-tested in GitHub Actions. Actual SMS read behavior in the stock app, call
controls, notification-listener behavior, mirroring, and OEM background behavior require testing on
real hardware; see the device acceptance checklist in the README.
