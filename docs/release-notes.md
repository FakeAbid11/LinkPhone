## LinkPhone v0.5.2

A direct, LAN-only companion link between an Android phone and a Windows PC. This release makes the
call speaker control honest on phones that refuse cellular audio routing.

### What's new

- The Speaker toggle now tries **both** `AudioManager.setCommunicationDevice()` and the legacy
  speakerphone request, waits briefly, and verifies the reported route.
- It only shows **Speaker on** when the phone reports that the route is actually on. If the phone
  blocks it, the toggle stays off and the PC shows an explicit “phone blocked speaker control”
  error instead of a false success.
- Turning the speaker off also clears both route requests; ending the call resets them.
- Cellular routing remains controlled by Android Telecom/the default dialer. Even with the fallback,
  some phones will still refuse; use the phone's in-call screen or a headset in that case.

No protocol, permission, SMS, photo, mirroring, or updater changes.

### Which updates do I need?

- **Phone app only** for the speaker behavior. The Windows app can stay on v0.5.0/v0.5.1.
- Installed v0.4.0+ Windows builds can update in-app.

### Download

- `LinkPhone-0.5.2.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.5.2.exe` — Windows portable app
- `LinkPhone-Setup-0.5.2.exe` — Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

### Known limitations

- Reliable cellular speaker routing still requires the default-dialer/`InCallService` role, which
  LinkPhone deliberately does not take.
- Call audio never reaches the PC.
- Mirroring remains experimental; the callback fix from v0.5.1 is included.
- The Windows updater is unsigned and SHA-256 verified; portable builds update manually.

Built, unit-tested, linted, and E2E-tested in GitHub Actions. Speaker behavior varies by OEM and
still needs the hardware checklist in `docs/testing.md`.
