## LinkPhone v0.1.3

A direct, LAN-only companion link between an Android phone and a Windows PC.

### What's new

- **Contact search on the PC.** Grant Contacts on the phone and the Calls tab gains a searchable
  contact list. Tap a contact to fill the dialer, choose a SIM if needed, and call.
- Contacts are queried on the phone only when the Calls tab asks for them, sent over the encrypted
  LAN connection, and kept in memory on the PC — never written to disk. No cloud service is involved.
- Adds the `contacts` capability and `contacts.list` command. The PC hides the picker for an older
  phone app; manual dialing still works.
- No new permissions are declared: `READ_CONTACTS` was already part of the optional Calls
  permission set and is still user-granted on the phone.
- Existing pairings, settings, and the signing identity are retained.

### Which updates do I need?

- **Both apps** for the contact picker: the phone exposes contacts and the PC displays them.
- The rest of v0.1.2 (SIM selection, SMS scrolling/read state, Open on phone) continues to work.

### Download

- `LinkPhone-0.1.3.apk` — Android 10+ companion (sideload; same personal release key)
- `LinkPhone-Portable-0.1.3.exe` — Windows portable app
- `LinkPhone-Setup-0.1.3.exe` — Windows installer
- `SHA256SUMS.txt` — verify your downloads

### First run

1. Allow LinkPhone through Windows Firewall on **Private** networks.
2. Open the PC app and choose **Pair a phone** (or reuse the existing pairing).
3. Install the APK, open LinkPhone, and scan the QR code.
4. Approve the pairing prompt on the PC.
5. Grant only the permissions you want on the phone; grant Contacts for the picker.

### Known limitations

- Contact access requires `READ_CONTACTS`; without it the PC cannot search contacts and caller
  names are unavailable.
- Contacts with no phone number are not listed. Some account/OEM providers may expose different
  labels.
- Phone read sync is not silent: Android only lets the default SMS app write the read flag.
  **Open on phone** requires a tap on the LinkPhone notification; the stock SMS app then marks the
  thread read.
- Cellular call audio cannot be routed to the PC by a third-party app. Calls are controlled from
  the PC; audio stays on the phone.
- True Android app streaming is not possible. **Apps** is experimental screen mirroring with
  optional remote taps; it requires consent every session and stops when the phone locks.
- SMS is sideload-only; MMS/RCS are not supported. Sent messages may not appear in the stock SMS app.
- Mirrored notifications on Android 15+ may have sensitive content redacted.
- Phone-to-PC clipboard is explicit (Quick Settings tile/share action), not automatic.
- Background operation requires an ongoing notification and, on many OEMs, battery/autostart
  settings.
- The Windows binaries are unsigned, so SmartScreen may warn.

Built and unit/E2E-tested in GitHub Actions. Actual contact-provider behavior, calls, notifications,
mirroring, and OEM background behavior require testing on real hardware; see the device acceptance
checklist in the README.
