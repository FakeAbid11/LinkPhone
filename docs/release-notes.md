## LinkPhone v0.1.0

A direct, LAN-only companion link between an Android phone and a Windows PC.

### Download

- `LinkPhone-0.1.0.apk` — Android 10+ companion (sideload; signed with a personal release key)
- `LinkPhone-Portable-0.1.0.exe` — Windows portable app
- `LinkPhone-Setup-0.1.0.exe` — Windows installer
- `SHA256SUMS.txt` — verify your downloads

### First run

1. Allow LinkPhone through Windows Firewall on **Private** networks.
2. Open the PC app and choose **Pair a phone**.
3. Install the APK, open LinkPhone, and scan the QR code.
4. Approve the pairing prompt on the PC.
5. Grant only the permissions you want on the phone.

### Known limitations

- Cellular call audio cannot be routed to the PC by a third-party app. Calls are controlled from the PC, audio stays on the phone.
- True Android app streaming is not possible. **Apps** is experimental screen mirroring with optional remote taps; it requires consent every session and stops when the phone locks.
- SMS is available for sideloaded apps only; MMS/RCS are not supported. Sent messages may not appear in your stock SMS app's thread.
- Mirrored notifications on Android 15+ may have sensitive content redacted.
- Phone-to-PC clipboard is explicit (Quick Settings tile/share action), not automatic.
- Background operation requires an ongoing notification and, on many OEMs, battery/autostart settings.
- The Windows binaries are unsigned, so SmartScreen may warn.

The APK and desktop app are built and unit/E2E-tested in GitHub Actions. Call controls, notification-listener behavior, mirroring, and OEM background behavior require testing on real hardware; see the device acceptance checklist in the README.
