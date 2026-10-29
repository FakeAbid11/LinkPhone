# Architecture

LinkPhone is two apps and one encrypted LAN connection. There is no relay service, account, or
cloud database.

```
Android (Kotlin/Compose)                         Windows PC (Electron/React)
┌──────────────────────────┐   wss/TLS 1.2+   ┌──────────────────────────┐
│ Compose setup UI         │◄────────────────►│ React renderer           │
│ Foreground service       │  LAN, port 48921 │ Electron main            │
│ ├ SMS / notifications    │                  │ ├ TLS + WebSocket server │
│ ├ photos / calls         │                  │ ├ pairing + auth         │
│ ├ clipboard / handoff    │                  │ ├ services + IPC         │
│ └ mirror + accessibility │                  │ └ tray + notifications   │
└──────────┬───────────────┘                  └──────────────┬───────────┘
           │ AndroidKeyStore identity                        │ userData/
           │                                                 │  certs, state, logs
           └──────── mDNS `_linkphone._tcp` ─────────────────┘
```

## Components

### Phone

- **UI** (`app.linkphone.ui`): a small setup/companion app. It pairs, explains permissions,
  shows connection state and diagnostics, and hosts the transparent share/clipboard/mirror
  consent activities. The PC is the primary user interface.
- **Foreground service** (`service.LinkConnectionService`): owns the connection loop, the
  notification, SMS observer, call-state listener, photo/stream handling, mirror controller,
  and clipboard/handoff actions.
- **Core** (`core/`): protocol codec, binary frames, pairing URI, AndroidKeyStore identity,
  certificate pinning, mDNS discovery, preferences, backoff, validation.
- **Updater** (`core.update`): the in-app release checker. `UpdateVersion` and `UpdateChecksums` are
  pure helpers; `UpdateClient`/`GitHubUpdateClient` read the GitHub Releases API over OkHttp with
  manual HTTPS-only redirects; `UpdateEngine` is the platform-independent state machine
  (`idle → checking → available → downloading → ready`, plus `up-to-date`, `error`, `unsupported`);
  `UpdateInstaller` hands the verified APK to Android's package installer through a `FileProvider`.
  `AppUpdater` wires it to the app lifecycle, preferences, and notifications.
- **Features**: SMS provider/send/receiver, notification listener, MediaStore photos, telecom
  control, clipboard bridge + Quick Settings tile, share target, MediaProjection mirror,
  Accessibility gesture service.

### PC

- **Electron main** (`src/main`): TLS server, pairing/auth, session correlation, mDNS publish,
  services, persistence, file saving, clipboard, tray, OS notifications, IPC.
- **Renderer** (`src/renderer`): React views for connection/pairing, messages, notifications,
  photos, calls, mirroring, and settings. It never opens sockets directly; all network access is
  in the main process behind a narrow, validated IPC surface.
- **Core**: envelope/binary codecs, self-signed certificate generation, pairing token manager,
  ECDSA verification, stream registry, safety validation, atomic JSON store, logger.

## Trust boundaries

1. **LAN** is untrusted. An attacker on the same network can see the TLS server and attempt
   connections. They cannot complete authentication without the phone's AndroidKeyStore key.
2. **Pairing QR** is the out-of-band trust anchor. It carries the PC address, the SHA-256 of the
   PC certificate, and a single-use token. The phone pins the certificate before sending data.
3. **PC UI approval** is required before a phone is added to the paired set, even with a valid
   token.
4. **Renderer** is treated as less trusted than the main process. IPC validates types, lengths,
   and file paths; the renderer has no Node integration.
5. **Phone permissions** are the last boundary. A compromised PC can only use features the user
   has granted; Android still enforces each permission per operation.

## Connection lifecycle

### 1. PC startup

1. Load `state.json` from Electron's `userData` directory.
2. Load or create a self-signed RSA-2048 certificate (`certs/pc-key.pem`, `pc-cert.pem`),
   valid for ten years.
3. Start the TLS server on `0.0.0.0:48921` (tries 48921–48930 if busy).
4. Publish `_linkphone._tcp` over mDNS with TXT `id` and `fp`.
5. If no phone is paired, create a five-minute pairing code/QR.

### 2. Pairing

1. Phone scans `linkphone://pair?h=…&p=…&f=…&t=…&n=…` (or the user types the fields).
2. Phone connects with `wss://`, trusting only the certificate whose SHA-256 matches `f`.
3. Phone sends `pair.request` with the one-time token and its SPKI P-256 public key.
4. PC validates the token, rate-limits failures, and shows a consent dialog with the phone's key
   fingerprint. The request expires after 90 seconds.
5. On approval the PC stores the phone id/name/public key and sends `pair.ok`. The phone saves the
   PC id, address, and certificate fingerprint. The phone closes the pairing socket and
   reconnects normally.

### 3. Authentication

1. Phone sends `hello` with its device id, app version, and public key.
2. PC verifies the device and that the key matches the stored fingerprint, then sends a 32-byte
   `auth.challenge` nonce.
3. Phone signs `linkphone-auth-v1|<pcId>|<phoneId>|<nonceBase64>` with its non-exportable P-256
   key (ECDSA/SHA-256, DER) and sends `auth.response`.
4. PC verifies with Node's `crypto.verify`, marks the session authorized, and sends `auth.ok`.
5. Phone sends `phone.info` (model, Android version, capabilities, SIMs).

The phone also verifies that the challenge contains the PC id it paired with. The pinned TLS
certificate is the PC's identity; the signature is the phone's identity.

### 4. Session

- One active phone session; a new connection from the same device replaces the old socket.
- A second, different phone is refused with `BUSY`.
- PC commands use request/response correlation: each request has an id, the phone replies with
  `replyTo` and `ok`.
- Phone events (new SMS hint, notifications, call state, clipboard, handoff, mirror) are
  unsolicited envelopes.
- WebSocket pings every 20 s from the phone, plus server heartbeats every 25 s.

### 5. Reconnect

- Phone loop: attempt stored address → on failure run 6 s mDNS discovery → retry with exponential
  backoff (1 s up to 60 s, 20% jitter).
- Network availability triggers an immediate retry.
- Auth/protocol errors stop the loop and surface a clear message; a changed PC certificate
  requires re-pairing.
- Boot and package-update receivers restart the foreground service when paired and auto-connect
  is enabled.

## Android background strategy

- **Foreground service type `connectedDevice`**. Android requires an ongoing notification. The
  type has no six-hour timeout (unlike `dataSync`), and boot start is allowed for it.
- `START_STICKY`, a boot receiver, and a network callback improve recovery.
- `WifiLock` and `MulticastLock` are held while the service runs to keep Wi-Fi and mDNS usable.
- A WorkManager watchdog is deliberately not used: Android 12+ forbids starting a foreground
  service from a periodic background worker. A 15-minute "restart" would be unreliable and
  misleading.
- Force-stop disables restart until the user opens the app. Doze and OEM task killers can still
  stop the service; the app reports disconnection rather than bypassing system controls.

## Media and data channels

- **SMS/notifications/calls**: JSON envelopes only.
- **Photos**: the PC requests one image; the phone replies with a `photo.stream` metadata
  response, then sends 64 KiB binary chunks (frame type 1), ending with a `last` flag and a JSON
  stream-end event. The phone transcodes to JPEG (thumbnail 512 px, full 2048 px max) to keep
  HEIC and extreme sizes off the PC.
- **Handoff images**: same mechanism with frame type 3 and a 20 MB cap.
- **Mirroring**: the phone captures with MediaProjection. When both sides support it, a hardware AVC
  encoder writes ~720×1600 access units at 30 fps/6 Mbps (or opt-in 60 fps/12 Mbps) (binary type 4) that the PC decodes with
  WebCodecs; otherwise it falls back to ≤1280 px/≤5 fps JPEG (type 2). The PC draws frames to a canvas
  and sends normalized tap/swipe coordinates back.

## Persistence

| Location | Contents |
|---|---|
| PC `userData/state.json` | PC id/name, paired device public keys/fingerprints, settings, muted packages, per-device SMS read-state ids |
| PC `userData/certs/` | PC private key and certificate |
| PC `userData/logs/linkphone.log` | Connection/diagnostic events (no message bodies) |
| PC memory | SMS cache, notification feed, photo list, handoff list, contacts, mirror state |
| PC user folders | Only files the user explicitly saves (`Pictures\LinkPhone`, `Downloads\LinkPhone`) |
| Phone SharedPreferences | Phone id/name, paired PC details, auto-connect flag, update auto-check flag, last update-check timestamp |
| Phone `filesDir/updates` | The downloaded update APK awaiting installation (replaced on the next update) |
| AndroidKeyStore | Non-exportable phone private key |
| Phone memory | Current session, logs, notification filter state |

## Non-goals

LinkPhone does not implement call audio routing, app virtualization, MMS/RCS, cloud relay,
remote control outside an active screen-sharing session, or a replacement default SMS/dialer app.
See [feasibility.md](feasibility.md).
