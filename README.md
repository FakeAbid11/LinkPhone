# LinkPhone

[![CI](https://github.com/FakeAbid11/LinkPhone/actions/workflows/ci.yml/badge.svg)](https://github.com/FakeAbid11/LinkPhone/actions/workflows/ci.yml)

Your Android phone and Windows PC, connected directly over your own Wi-Fi. Messages, notifications, photos, call control, clipboard handoff, and experimental screen mirroring — encrypted, LAN-only, no cloud account.

> **Status:** v0.1.0. Built and unit-tested in GitHub Actions; device behavior still needs acceptance testing on real hardware. See the feature/limitations tables before installing.

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

## Quick start

1. Install the PC app and the APK on the phone (allow install from your browser/file manager).
2. Put the phone and PC on the same Wi-Fi network. On the PC, allow LinkPhone through Windows Firewall for **Private** networks.
3. Open LinkPhone on the PC, choose **Pair a phone**, then scan the QR code with the phone app.
4. Approve the pairing prompt on the PC.
5. Grant the feature permissions you want on the phone. The PC can only use features you explicitly enable.

Full setup, permission explanations, battery guidance, and a device acceptance checklist are in the [README sections below](#phone-permissions) and in [docs/permissions.md](docs/permissions.md).

## What works

| Feature | Status | Notes |
|---|---|---|
| Secure pairing and reconnect | Implemented | QR pairing, pinned TLS, ECDSA device authentication, automatic reconnect |
| SMS read/send/receive | Implemented | Requires `READ_SMS`/`SEND_SMS`/`RECEIVE_SMS`; no MMS/RCS |
| Notification mirroring | Implemented | Per-app mute and dismiss; Android 15+ may redact sensitive content |
| Photos | Implemented | Recent camera roll, save to PC, copy image to clipboard |
| Call control | Limited | Dial/answer/decline and caller state; **call audio stays on the phone** |
| Clipboard and Continue on PC | Limited | PC→phone clipboard; phone→PC on explicit action only |
| Apps / screen mirroring | Experimental | Per-session consent, stops on lock, no audio, no keyboard |
| Background operation | Best effort | Ongoing notification and battery-optimization exemption required |

### What is not possible (and why)

- **Call audio on the PC.** Android reserves in-call audio capture for privileged/system apps. LinkPhone controls the call but cannot route the audio.
- **True Android app streaming.** There is no third-party API to run arbitrary apps on the PC. LinkPhone mirrors the phone screen and can inject taps/swipes.
- **Silent background clipboard sync from phone to PC.** Android 10+ only lets the focused app read the clipboard.
- **MMS/RCS, notification action buttons, mirrored calls on every OEM.** These are outside the supported surface.

## Phone permissions

Each feature is opt-in. LinkPhone asks at the point of use and explains why.

- **Notifications:** `POST_NOTIFICATIONS` (Android 13+) for the ongoing connection notification.
- **SMS:** `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`. These are restricted for Play Store distribution, so the app is sideloaded.
- **Calls:** `READ_PHONE_STATE`, `CALL_PHONE`, `ANSWER_PHONE_CALLS`; optional `READ_CALL_LOG` for caller ID, `READ_CONTACTS` for contact names.
- **Photos:** `READ_MEDIA_IMAGES` (Android 13+) or `READ_EXTERNAL_STORAGE` (Android 10–12). Android 14 may grant only selected photos.
- **Notification mirroring:** Notification access (special settings screen).
- **Screen mirroring:** MediaProjection consent each session, plus the Accessibility service for remote taps.
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
- Message bodies and notification text are not persisted by default on the PC; only settings, paired device public keys, and cached photo thumbnails are stored.
- The PC listens on the LAN. Anyone who can scan the pairing QR during its five-minute lifetime could attempt to pair, but must still be approved on the PC.

See [SECURITY.md](SECURITY.md) for the threat model and reporting.

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
cd desktop && npm install && npm run typecheck && npm test && npm run build
```

## Privacy

LinkPhone does not send data to any third-party service. The PC app only listens on your local network. Diagnostics never include message bodies; logs redact tokens and identifiers. See [docs/privacy.md](docs/privacy.md).

## License

MIT. See [LICENSE](LICENSE).
