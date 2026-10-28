## LinkPhone v0.2.0

A direct, LAN-only companion link between an Android phone and a Windows PC. This release is a
full interface overhaul on both sides. The protocol, pairing, encryption, and permission model are
unchanged.

### What's new

**Windows PC**

- Reworked navigation with icons, grouped sections, a clear active state, keyboard focus rings, and
  a skip link.
- The two-column layout now stays two columns at the default window size; the single-column
  breakpoint moved from 1180 px to 1040 px.
- Larger type throughout (no labels below 11 px) and higher-contrast secondary text.
- Shared form controls with labels, hints, and focus/error states replace the hand-styled inputs.
- Modals trap Tab focus, close with Escape, and restore focus to the button that opened them.
- Messages: conversation search, unread badges, compact action buttons, and a banner area that does
  not push the composer off screen.
- Calls: the idle screen no longer shows an empty current-call card; the dialer and contacts use the
  full height. Contacts search stays pinned above the list.
- Connection: live pairing-code expiry, one firewall command with a copy button, and platform limits
  collapsed into an expandable section.
- Notifications, Photos, and Apps gained consistent empty/loading states, icons, and focus styling.
- A persistent “Phone offline” banner appears when a paired phone disconnects.
- Settings is grouped by purpose, and the duplicated firewall command was removed.

**Android phone**

- New three-step first-run onboarding (welcome, permission overview, pair). It is skipped
  automatically for phones that are already paired.
- Home is now a dashboard: a connection status card, quick actions, grouped permissions,
  background operation, diagnostics, and about.
- Permissions explain why they are needed before the system prompt. After a permanent denial the
  action becomes **Open settings** instead of repeatedly failing.
- Light and dark themes follow the system, with an accessible teal palette, a defined type scale,
  and rounded shapes. Edge-to-edge insets are respected.
- Navigation survives rotation, and the system Back button returns from Pair or onboarding to Home.
- Pairing scrolls above the keyboard, validates input inline, and can paste a full pairing link
  from the clipboard. The QR scanner respects system bars, adds a torch toggle where available, and
  offers Retry plus Open app settings after a camera failure.
- Mirror consent, clipboard, and share screens use a consistent scrollable layout with system
  insets, so text is not clipped in landscape or with large fonts.

### Which updates do I need?

- **Both apps** for the redesigned experience. An older phone app still works with the new PC, and
  an older PC still works with the new phone, because no wire messages changed.
- Existing pairings, settings, SMS read state, and the signing identity are retained.

### Download

- `LinkPhone-0.2.0.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.2.0.exe` — Windows portable app
- `LinkPhone-Setup-0.2.0.exe` — Windows installer
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

Built, unit-tested, linted, and E2E-tested in GitHub Actions. Visual behavior on real devices
(permission dialogs, OEM skins, mirroring, rotation) still needs the hardware checklist in
`docs/testing.md`.
