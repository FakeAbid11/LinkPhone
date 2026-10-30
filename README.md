# LinkPhone

[![CI](https://github.com/FakeAbid11/LinkPhone/actions/workflows/ci.yml/badge.svg)](https://github.com/FakeAbid11/LinkPhone/actions/workflows/ci.yml)

Your Android phone and Windows PC, connected directly over your own Wi-Fi. Messages, notifications, photos, call control, clipboard handoff, and experimental screen mirroring — encrypted, LAN-only, no cloud account.

> **Status:** v0.8.0. The Android and Windows apps compile, pass their unit/integration tests, and are packaged in GitHub Actions. They have **not** been tested on a physical phone in this environment; OEM permissions, calls, notifications, MediaProjection, the Android update installer, and background behavior require the device acceptance checklist. See [testing.md](docs/testing.md#device-acceptance-checklist) and the limitations below before installing.

Documentation: [feasibility & limits](docs/feasibility.md) · [architecture](docs/architecture.md) · [protocol](docs/protocol.md) · [permissions](docs/permissions.md) · [security](docs/security.md) · [troubleshooting](docs/troubleshooting.md) · [building](docs/building.md).

## Download

Grab the latest files from [Releases](https://github.com/FakeAbid11/LinkPhone/releases):

| File | What it is |
|---|---|
| `LinkPhone-<version>.apk` | Android companion, Android 10+ (API 29), sideloaded |
| `LinkPhone-Portable-<version>.exe` | Windows desktop app, no installation |
| `LinkPhone-Setup-<version>.exe` | Windows installer |
| `SHA256SUMS.txt` | Checksums for the above |

The Windows binaries are unsigned, so SmartScreen may warn. The APK is signed with a personal release key, not distributed through Google Play.

## Updates

**Windows.** The installed Setup build checks GitHub Releases after launch and every six hours,
downloads a newer Setup EXE in the background, verifies its SHA-256 checksum, and shows an **Update
ready** banner. **Restart and install** opens the installer after LinkPhone stops its connection
service. Automatic checks can be disabled in **Settings → Updates**; manual checks still work.

**Android.** The phone app has the same updater in **Home → Updates**. It checks GitHub Releases
shortly after launch and every six hours, downloads the release APK, verifies its SHA-256 checksum
**and** its signing certificate, and **Install update** hands it to Android's own package installer.
Automatic checks can be turned off in the Updates card; manual checks still work.

- Android requires a one-time grant: **Settings → Install unknown apps → LinkPhone → Allow from
  this source**. The app explains this in a dialog before sending you there. Android — not
  LinkPhone — performs the install, and the OS restarts the app afterwards.
- Only APKs signed with the same release key can install over an existing copy. A debug-signed
  build cannot replace an official installation, and an official APK cannot replace a debug build.
- Debug builds never contact GitHub at all.
- Portable builds cannot update in place; use the **Download portable** link in Settings.
- Update installers are unsigned. The updater verifies SHA-256 from the official release but cannot
  verify an Authenticode publisher signature. Only install updates from this repository's releases.
- v0.4.0 is the first updater-enabled Windows build and v0.7.0 the first updater-enabled Android
  build, so install them manually once. Later builds can update in-app.

## Quick start

1. Install the PC app and the APK on the phone (allow install from your browser/file manager).
2. Put the phone and PC on the same Wi-Fi network. On the PC, allow LinkPhone through Windows Firewall for **Private** networks.
3. Open LinkPhone on the PC, choose **Pair a phone**, then scan the QR code with the phone app.
4. Approve the pairing prompt on the PC.
5. Grant the feature permissions you want on the phone. The PC can only use features you explicitly enable.

If the PC shows no LAN address or the phone cannot connect, allow LinkPhone through Windows Firewall on **Private** networks (TCP port 48921, or the port shown in Settings) and check that the Wi-Fi is not a guest network with client isolation. Full permission explanations are in [permissions.md](docs/permissions.md); the device acceptance checklist is in [testing.md](docs/testing.md#device-acceptance-checklist).

## What is verified, and what is not

**Verified in CI on every pull request:**

- Android compiles, lint passes, JVM unit tests pass, and a signed release APK is produced and verified with `apksigner`.
- Desktop typechecks, Vitest unit tests pass, the TLS/WebSocket server is exercised by a real pairing + ECDSA authentication integration test, the certificate pinning behavior is tested, the Electron app launches and renders in a Playwright smoke test, and NSIS/portable EXEs are built.

**Not verified in CI (requires hardware):**

- Real SMS/MMS, carrier calls, caller ID, OEM answer/decline restrictions
- Notification-listener behavior on Android 15+ and OEM skins
- The live notification mirror and the phone battery indicator on real hardware
- MediaProjection consent, secure-window blacking, lock/rotation, Accessibility gestures
- Background survival under Doze and OEM task killers
- Wi-Fi AP isolation, VPNs, Windows Firewall prompts, SmartScreen
- The Android "install unknown apps" grant, the system package installer, and the actual app
  replacement

Do not interpret a green CI run as proof that those device-specific paths work. Run the checklist and report results.

## Uninstall and revoke

- PC: Connection → Paired phones → Remove, or Settings → Reset PC identity to revoke every phone and replace the certificates.
- Phone: Diagnostics → Forget PC, or revoke permissions in Android settings.
- Uninstalling the PC app leaves the Windows Firewall rule behind; remove it with
  `Remove-NetFirewallRule -DisplayName "LinkPhone"`.
- The PC app lives in the tray. Closing the window keeps the server running; choose Quit from the tray menu to stop it.

## What works

| Feature | Status | Notes |
|---|---|---|
| Secure pairing and reconnect | Implemented | QR pairing, pinned TLS, ECDSA device authentication, automatic reconnect |
| SMS read/send/receive | Implemented | Requires `READ_SMS`/`SEND_SMS`/`RECEIVE_SMS`; opening a thread clears PC-local unread; **Open on phone** syncs the stock SMS app; no MMS/RCS |
| Notification mirroring | Implemented | Live mirror of the phone's current notifications; a changed count is rewritten in place; per-app mute and dismiss; Android 15+ may redact sensitive content |
| Phone battery | Implemented | Charge level and charging state on the Connection tab and in the sidebar |
| Photos | Implemented | Album browsing with paging, save to PC, copy image to clipboard |
| Call control | Limited | Dial/answer/decline/end, caller state, contact search, SIM selection on Android 11+, and a best-effort speaker toggle; **call audio stays on the phone** |
| Clipboard and Continue on PC | Limited | PC→phone clipboard; phone→PC on explicit action only |
| Apps / screen mirroring | Experimental | Hardware H.264 ~720×1600 at 30 fps (opt-in 60 fps) where supported, JPEG fallback, per-session consent, stops on lock, no audio/keyboard |
| Android app updates | Implemented | In-app check and download; SHA-256 and signing-certificate verified; Android's installer performs the install; one-time "install unknown apps" grant |
| Background operation | Best effort | Ongoing notification and battery-optimization exemption required |

### What is not possible (and why)

- **Call audio on the PC.** Android reserves in-call audio capture for privileged/system apps. LinkPhone controls the call but cannot route the audio.
- **True Android app streaming.** There is no third-party API to run arbitrary apps on the PC. LinkPhone mirrors the phone screen and can inject taps/swipes.
- **Silent background clipboard sync from phone to PC.** Android 10+ only lets the focused app read the clipboard.
- **Silent phone-side SMS read sync.** Android only lets the default SMS app write the SMS provider's read flag. LinkPhone tracks read locally and offers **Open on phone**, which posts a notification; tapping it opens the conversation in the stock SMS app, which marks it read. LinkPhone does not take over your SMS app.
- **MMS/RCS, notification action buttons, mirrored calls on every OEM.** These are outside the supported surface.

## Phone permissions

Each feature is opt-in. LinkPhone asks at the point of use and explains why.

- **Notifications:** `POST_NOTIFICATIONS` (Android 13+) for the ongoing connection notification.
- **SMS:** `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`. These are restricted for Play Store distribution, so the app is sideloaded. LinkPhone does not take the default-SMS role; read state is tracked locally on the PC, and **Open on phone** uses a notification (requires `POST_NOTIFICATIONS`) to open the thread in your stock SMS app.
- **Calls:** `READ_PHONE_STATE`, `CALL_PHONE`, `ANSWER_PHONE_CALLS`; optional `READ_CALL_LOG` for caller ID, `READ_CONTACTS` for contact search and caller names. `MODIFY_AUDIO_SETTINGS` is a normal permission used for the best-effort speaker request.
- **Photos:** `READ_MEDIA_IMAGES` (Android 13+) or `READ_EXTERNAL_STORAGE` (Android 10–12). Android 14 may grant only selected photos.
- **Notification mirroring:** Notification access (special settings screen).
- **Screen mirroring:** MediaProjection consent each session, plus the Accessibility service for remote taps.
- **App updates:** `REQUEST_INSTALL_PACKAGES` lets the app ask Android to install a downloaded APK. It is not self-granting; the user enables *Allow from this source* for LinkPhone once in system settings.
- **Background:** battery-optimization exemption and, on some OEMs, an autostart toggle.

## Background operation

LinkPhone runs a foreground service with a persistent notification while connected. This is the mechanism Android provides for an ongoing connection; it cannot be hidden. To improve reliability:

1. Allow LinkPhone to ignore battery optimizations (button in the app).
2. On Xiaomi/Redmi, Oppo/Realme, Vivo/iQOO, OnePlus, Samsung, and Huawei devices, enable Autostart / "Allow background activity" for LinkPhone.
3. Do not force-stop the app; force-stop disables boot restart until you open it again.

Even with these steps, Android and OEM battery managers may stop the service. LinkPhone reconnects when it restarts and tells you when it is disconnected; it does not bypass these controls.

## Security model

- All traffic is TLS on your LAN. The phone pins the PC's self-signed certificate fingerprint from the QR code; the PC verifies signed challenges from the phone's non-exportable EC key.
- No cloud relay, no analytics, no accounts. Pairing tokens are single-use and expire.
- Message bodies and notification text are not persisted on the PC; only settings, paired device public keys, and SMS read-state message ids (no bodies) are written to disk. Photo thumbnails live in memory, and photos are saved only when you ask.
- The PC listens on the LAN. Anyone who can scan the pairing QR during its five-minute lifetime could attempt to pair, but must still be approved on the PC.

See [SECURITY.md](SECURITY.md) for the threat model and reporting.

## Repository layout

```
android/   Kotlin + Jetpack Compose app, Gradle build, JVM unit tests
desktop/   Electron main/preload, React renderer, Vitest tests, Playwright E2E
docs/      architecture, protocol, feasibility, permissions, security, testing, troubleshooting, building, privacy
.github/   CI and tagged release workflows
```

## Architecture

```
Android (Kotlin/Compose)                         Windows PC (Electron/React)
┌──────────────────────────┐   wss/TLS 1.2+   ┌──────────────────────────┐
│ UI + permissions         │◄────────────────►│ React UI                 │
│ Foreground service       │  LAN 48921       │ Electron main            │
│ ├ SMS / notifications    │                  │ ├ TLS + WebSocket server │
│ ├ photos / calls         │                  │ ├ pairing + auth         │
│ ├ clipboard / handoff    │                  │ ├ SMS/notif/photo/call   │
│ └ mirror + accessibility │                  │ └ clipboard/handoff/mirror│
└──────────────────────────┘                  └──────────────────────────┘
       │ mDNS (_linkphone._tcp)                         │
       └────────────────────────────────────────────────┘
```

## Building

CI builds everything; no local Android SDK is required. See [docs/building.md](docs/building.md) for the workflows, signing secrets, and how to produce artifacts. Tests run in GitHub Actions on every pull request.

```bash
# Android (CI)
gradle -p android :app:testDebugUnitTest :app:lintDebug :app:assembleRelease

# Desktop (CI, Windows)
cd desktop && npm ci && npm run typecheck && npm test && npm run build
```

## Privacy

LinkPhone does not send data to any third-party service. The PC app only listens on your local network. Diagnostics never include message bodies; logs redact tokens and identifiers. See [docs/privacy.md](docs/privacy.md).

## License

MIT. See [LICENSE](LICENSE).
