## LinkPhone v0.2.1

A direct, LAN-only companion link between an Android phone and a Windows PC. This release replaces
the bright teal palette with a calmer indigo/violet theme on both apps. Layout, behavior, protocol,
pairing, encryption, and permissions are unchanged.

### What's new

**Windows PC**

- New dark palette: `#0B0F19` canvas, `#141B2B` cards, `#8B93FF` accent, `#9DB8FF` secondary,
  and higher-contrast muted/caption text.
- The hardcoded teal and blue values are now driven by `--primary-rgb` / `--secondary-rgb` tokens,
  so future palette changes are one edit instead of a sweep.
- Updated logo, window background, pairing QR color, scrollbars, inputs, modal, and message bubbles.
- No layout or behavior changes; all v0.2.0 UI work remains.

**Android phone**

- Dark and light Compose schemes rebuilt in indigo/violet:
  - Dark: `#0B0F19` background, `#8B93FF` primary, `#9DB8FF` secondary.
  - Light: `#F7F8FC` background, `#4F46E5` primary, `#3A5FA8` secondary.
- Launcher icon accent and launch background updated; launch background now has a light/night
  resource so the first frame matches the system theme.
- QR scanner overlay uses the new dark glass color; white text stays for camera legibility.
- No layout, permission, or behavior changes.

### Which updates do I need?

- **Both apps** for a consistent palette.
- Existing pairings, settings, SMS read state, and the signing identity are retained.

### Download

- `LinkPhone-0.2.1.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.2.1.exe` — Windows portable app
- `LinkPhone-Setup-0.2.1.exe` — Windows installer
- `SHA256SUMS.txt` — verify your downloads

### First run

1. Allow LinkPhone through Windows Firewall on **Private** networks.
2. Open the PC app and choose **Pair a phone**.
3. Install the APK, open LinkPhone, and follow the setup guide.
4. Scan the QR code and approve the pairing prompt on the PC.
5. Grant only the permissions you want; each feature explains itself first.

### Known limitations

- Cellular call audio cannot be routed to the PC by a third-party app. Calls are controlled from
  the PC; audio stays on the phone.
- True Android app streaming is not possible. **Apps** is experimental screen mirroring with
  optional remote taps; it requires consent every session and stops when the phone locks.
- SMS is sideload-only; MMS/RCS are not supported. Sent messages may not appear in the stock SMS app,
  and read sync still uses **Open on phone** because Android reserves the read flag for the default
  SMS app.
- Mirrored notifications on Android 15+ may have sensitive content redacted.
- Phone-to-PC clipboard is explicit (Quick Settings tile/share action), not automatic.
- Background operation requires an ongoing notification and, on many OEMs, battery/autostart
  settings.
- The Windows binaries are unsigned, so SmartScreen may warn.

Built, unit-tested, linted, and E2E-tested in GitHub Actions. Palette contrast is asserted by unit
tests; light/dark rendering, the launcher icon, and camera-overlay legibility still need the hardware
checklist in `docs/testing.md`.
