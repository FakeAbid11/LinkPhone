## LinkPhone v0.1.1

A direct, LAN-only companion link between an Android phone and a Windows PC.

### What's new

- **Choose which SIM places a call.** The PC's Calls tab now shows a SIM selector for dual-SIM
  phones on Android 11 or newer. **Default SIM** keeps the phone's current system-default behavior.
- The phone maps the selected subscription to its Telecom `PhoneAccount` using the official
  `Extras`/`PhoneAccountHandle` API. No default-dialer role or hidden API is used.
- Android 10 shows an explanatory note instead: per-SIM dialing from the PC requires Android 11+.
- Existing v0.1.0 pairings, settings, and the signing identity are unchanged.

### Download

- `LinkPhone-0.1.1.apk` — Android 10+ companion (sideload; signed with the same personal release key)
- `LinkPhone-Portable-0.1.1.exe` — Windows portable app
- `LinkPhone-Setup-0.1.1.exe` — Windows installer
- `SHA256SUMS.txt` — verify your downloads

### First run

1. Allow LinkPhone through Windows Firewall on **Private** networks.
2. Open the PC app and choose **Pair a phone** (or reuse the existing pairing).
3. Install the APK, open LinkPhone, and scan the QR code.
4. Approve the pairing prompt on the PC.
5. Grant only the permissions you want on the phone.

### Known limitations

- Cellular call audio cannot be routed to the PC by a third-party app. Calls are controlled from
  the PC; audio stays on the phone.
- SIM selection uses the official Telecom API but OEM dialers may ignore the requested account.
  The phone reports an error instead of silently using another SIM.
- True Android app streaming is not possible. **Apps** is experimental screen mirroring with
  optional remote taps; it requires consent every session and stops when the phone locks.
- SMS is available for sideloaded apps only; MMS/RCS are not supported. Sent messages may not
  appear in your stock SMS app's thread.
- Mirrored notifications on Android 15+ may have sensitive content redacted.
- Phone-to-PC clipboard is explicit (Quick Settings tile/share action), not automatic.
- Background operation requires an ongoing notification and, on many OEMs, battery/autostart
  settings.
- The Windows binaries are unsigned, so SmartScreen may warn.

Built and unit/E2E-tested in GitHub Actions. Actual call routing, notification-listener behavior,
mirroring, and OEM background behavior require testing on real hardware; see the device acceptance
checklist in the README.
