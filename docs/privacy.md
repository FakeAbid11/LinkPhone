# Privacy

LinkPhone is a LAN-only tool. It has no accounts, no telemetry, no analytics, no crash reporting,
and no cloud relay.

## What leaves the phone

Only to your PC, over the encrypted WebSocket on your local network:

- SMS threads you request from the PC (and new-message hints).
- Contacts you search for on the PC (names, labels, and dialable numbers), only after `READ_CONTACTS` is granted and only when the Calls tab requests them.
- Notifications from apps you chose to mirror (and per-app mute state on the PC).
- Photo thumbnails and full-size JPEGs you request.
- Call state, and the number/contact name when permitted.
- Clipboard text only when you explicitly send it.
- Content you share to LinkPhone from the share sheet.
- Screen frames only during an approved mirroring session.
- Device model, Android version, app version, permissions/capabilities, and SIM labels.

Nothing is sent to a server on the internet by LinkPhone. mDNS broadcasts the PC's service name,
PC id, and certificate fingerprint on the local network so the phone can find it; this is public
routing information, not message content.

The Windows app's updater is the one internet-facing exception: it requests the latest release
metadata and the installer/checksum files from the official GitHub Releases page. It sends no
device identifiers, account information, message content, or telemetry. GitHub sees the usual HTTP
request metadata (IP address, user agent, timestamps). Automatic checks can be disabled in Settings;
manual checks use the same feed.

## What is stored

| Data | Where | Retention |
|---|---|---|
| Paired PC address, id, certificate fingerprint, name | Phone app-private preferences | Until Forget PC or uninstall |
| Phone P-256 private key | AndroidKeyStore | Until app data is cleared/uninstalled |
| PC id/name, paired phone public keys, settings, muted apps | PC `state.json` | Until removed/reset/uninstall |
| SMS read-state message ids (no bodies), keyed by phone | PC `state.json` | Per-device; capped; cleared when the phone is removed or identity reset |
| PC TLS key/certificate | PC `userData/certs` | Until reset/uninstall |
| Connection logs (no content) | PC `userData/logs`, phone memory | Rotated/limited; PC log file rotates at 1 MB |
| SMS cache, notification feed, photo list, handoff list, contacts | Memory only | Until app quits/disconnects; contacts are fetched on demand and never written to disk |
| Photo thumbnails | PC memory | Not written to disk |
| Photos you save | `Pictures\LinkPhone` | Until you delete them |
| Shared images | `Downloads\LinkPhone` | Until you delete them |
| Clipboard | OS clipboard | Controlled by Android/Windows |

LinkPhone does not write SMS bodies or notification text to disk. The PC's `state.json` contains no
message history; it keeps only the ids of messages you marked read locally so unread badges survive
a restart. The phone does not cache messages; it reads the system provider on request. The Open on
phone notification contains only the conversation address, not message text.

## Permission use

See [permissions.md](permissions.md). Each permission is requested at the point of use with an
explanation. The PC receives a capability list, not raw permission grants. Revoking a permission
takes effect on the next operation/reconnect.

## Clipboard

Android 10+ prevents background clipboard reads. LinkPhone never attempts to read the clipboard in
the background. The phone sends text only after one of:

- the Quick Settings “Send clipboard to PC” tile,
- the LinkPhone share action,
- the in-app “Send clipboard” button, or
- the notification action, which opens a focused activity.

PC → phone writes are best-effort and may require the “Copied from PC” notification tap because of
OEM background restrictions. Optional auto-send from the PC reads the PC clipboard every 1.2 s while
enabled and connected; it is off by default.

## Screen capture

MediaProjection requires the phone user to approve each session. The system shows an indicator and
an ongoing notification. Capture stops when the screen locks or the user revokes it. LinkPhone does
not capture audio, does not read window contents through Accessibility, and does not bypass
`FLAG_SECURE`.

## Notification redaction

Android 15+ may redact sensitive notification content from listener apps. LinkPhone displays what
the system provides and cannot override it. Muted apps are filtered on the PC; the phone still
delivers their notifications to the listener unless the user revokes notification access.

## Diagnostics

Logs include timestamps, device names, addresses, event names, and error classes. They exclude SMS
and notification text, clipboard contents, photo data, tokens, and private keys. Sharing diagnostics
is a manual action; review the file before sending it.

## Third parties

No analytics, advertising, crash, or cloud SDKs. The app uses open-source libraries only (see
[security.md](security.md#supply-chain-and-distribution)). Windows notifications are rendered by the
OS; Android notifications are rendered by the system UI.

## Children and sensitive data

LinkPhone is a general-purpose personal utility. It can expose sensitive messages and photos to a
paired PC. Only pair devices you control, keep the PC account secure, and remove a phone if it is
lost. The app does not target children and collects no personal data itself.
