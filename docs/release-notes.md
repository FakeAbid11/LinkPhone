## LinkPhone v0.5.0

A direct, LAN-only companion link between an Android phone and a Windows PC. This release improves
call controls and SMS unread behavior.

### What's new

**Calls**

- **End call is available outside the Calls tab.** The global banner now appears for an active call
  as well as an incoming one, with **End call** and (where supported) a speaker toggle.
- The Calls tab keeps **End call** and adds a **Speaker off/on** button during an active call.
- **Speaker control is best effort.** Android normally reserves the cellular audio route for the
  Telecom/default dialer. LinkPhone requests the speaker device where the platform allows it and
  clearly says to use the phone's in-call screen or a headset if the route does not change.
- End/answer/decline still require the Answer calls permission; the UI explains this when missing.
- The success message after ending is now “End request sent to the phone,” not a premature “Call
  ended” before the phone reports idle.

**Messages**

- Clicking an unread conversation now marks it read automatically and clears its unread badge/nav
  count. The per-thread **Mark read** button was removed; **Mark all read** remains.
- This is still a PC-local read state, because Android only lets the default SMS app write the
  phone's read flag. Use **Open on phone** to hand the thread to the stock SMS app.

The v0.4.0 Windows updater continues to work; v0.4.0 installs can update to this release in-app.

### Which updates do I need?

- **Both apps** for the call speaker/end improvements and the consistent UI.
- SMS auto-read is PC-only.

### Download

- `LinkPhone-0.5.0.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.5.0.exe` — Windows portable app
- `LinkPhone-Setup-0.5.0.exe` — Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

### Known limitations

- Cellular call audio cannot be routed to the PC. Speaker toggling is best effort and may be ignored
  by the phone's telecom stack or OEM dialer.
- End call can be restricted to the default dialer on some devices; grant Answer calls and check the
  phone's in-call screen.
- The Windows updater remains unsigned and SHA-256 verified; portable builds update manually.
- Mirroring remains experimental and is not app streaming.
- SMS is sideload-only with no MMS/RCS.

Built, unit-tested, linted, and E2E-tested in GitHub Actions. Real call routing and per-OEM end-call
behavior still need the hardware checklist in `docs/testing.md`.
