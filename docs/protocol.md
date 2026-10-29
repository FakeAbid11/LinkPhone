# Protocol v1

The protocol is a JSON control channel plus binary media frames over a single WebSocket, protected
by TLS 1.2 or newer.

## Envelope

```json
{
  "v": 1,
  "id": "a5d1…",
  "type": "sms.list",
  "replyTo": null,
  "ok": null,
  "data": { "limit": 500 },
  "error": null,
  "ts": 1793200000000
}
```

- `v` is the protocol major version. Version 1 is required; anything else is rejected.
- `id` is a UUID and must be unique per sender.
- `replyTo` is set on responses to a request id.
- `ok` is `true`/`false` on responses. Errors use `{"code": "...", "message": "..."}`.
- `data` is the type-specific payload.
- `ts` is the sender's Unix time in milliseconds.
- Unknown fields are ignored for forward compatibility.
- Maximum JSON message size: **1 MiB**.

## Pairing URI

```
linkphone://pair?h=192.168.1.20&p=48921&f=<64 hex>&t=<22 char base64url>&n=<PC name>
```

| Param | Meaning |
|---|---|
| `h` | PC IPv4 address or hostname (IPv4 recommended) |
| `p` | TCP port (48921 by default) |
| `f` | SHA-256 of the PC certificate DER, hex (colons accepted) |
| `t` | 16-byte one-time token, base64url without padding; expires in 5 minutes |
| `n` | Optional URL-encoded PC name |

The token is single-use. Three incorrect attempts from one IP lock pairing for that IP for 15
minutes.

## Authentication

Phone key: ECDSA P-256 in AndroidKeyStore, SPKI exported at pairing. The PC stores the SPKI PEM and
SHA-256 fingerprint.

Canonical string:

```
linkphone-auth-v1|<pcId>|<phoneId>|<nonceBase64>
```

The phone signs the UTF-8 bytes with `SHA256withECDSA` and sends the **DER** signature as base64.
The nonce is 32 random bytes from the PC, fresh per connection, and accepted for 15 seconds. The
server verifies with Node `crypto.verify('sha256', payload, { key, dsaEncoding: 'der' }, signature)`.

## Message types

| Type | Direction | Payload |
|---|---|---|
| `pair.request` | phone → PC | `token, deviceId, name, platform, appVersion, publicKeySpki` |
| `pair.pending` | PC → phone | none (approval UI is waiting) |
| `pair.ok` | PC → phone | `pcId, pcName` |
| `pair.rejected` | PC → phone | `error {code, message}` |
| `hello` | phone → PC | `deviceId, phoneName, platform, appVersion, publicKeySpki, protocol` |
| `auth.challenge` | PC → phone | `nonce, pcId, pcName` |
| `auth.response` | phone → PC | `signature` (base64 DER) |
| `auth.ok` | PC → phone | `pcId, pcName, protocol` |
| `auth.error` | PC → phone | `error {code, message}` |
| `phone.info` | phone → PC | `model, androidVersion, appVersion, capabilities[], subscriptions[]` |
| `sms.list` | PC → phone | request `{limit}`; response `{messages[]}` |
| `sms.send` | PC → phone | `address, body, subId?`; response `{accepted, parts, subId}` |
| `sms.openThread` | PC → phone | `{address}` — posts a tappable notification that opens the conversation in the stock SMS app; it does not write the SMS provider |
| `sms.changed` | phone → PC | `{timestamp}` — PC should refresh `sms.list` |
| `contacts.list` | PC → phone | request `{limit}`; response `{contacts[]}` with `id, name, number, label?` — fetched on demand with `READ_CONTACTS` |
| `notif.post` | phone → PC | `key, packageName, appName, title?, text?, subText?, category?, ongoing, time` |
| `notif.removed` | phone → PC | `{key}` |
| `notif.dismiss` | PC → phone | `{key}` |
| `photo.list` | PC → phone | request `{limit, bucket?, beforeDate?, beforeId?}`; response `{photos[], hasMore}`. `bucket` is an opaque album id; `beforeDate`/`beforeId` form a keyset page |
| `photo.albums` | PC → phone | response `{albums[]}` with `id, name, count, coverId, latest`; requires the `photos.albums` capability |
| `photo.fetch` | PC → phone | `{id, kind: "thumb"\|"full", volume?}`; response `PhotoStreamMeta` then binary type 1 |
| `call.state` | phone → PC | `state (idle\|ringing\|offhook\|unknown), number?, contactName?, timestamp, speakerOn?` |
| `call.dial` | PC → phone | `{number, subId?}` |
| `call.answer` | PC → phone | none |
| `call.decline` | PC → phone | none — declines a ringing call or ends an active one |
| `call.speaker` | PC → phone | `{enabled: boolean}` — best-effort cellular speaker route; may be rejected/ignored by the phone |
| `clipboard.set` | PC → phone | `{text}` |
| `clipboard.fromPhone` | phone → PC | `{text}` — explicit user action only |
| `handoff.share` | phone → PC | `kind (url\|text), text?, url?` |
| `handoff.file` | phone → PC | `streamId, name, mime, size` then binary type 3 |
| `handoff.fileEnd` | phone → PC | `{streamId}` |
| `handoff.open` | PC → phone | `{url}` — phone posts a tappable notification |
| `mirror.start` | PC → phone | none — phone posts a consent notification |
| `mirror.stop` | PC → phone | none |
| `mirror.state` | phone → PC | `active, width, height, message?` |
| `mirror.touch` | PC → phone | `action (tap\|swipe), x, y, x2?, y2?, durationMs` (0..1 normalized) |
| `ping` / `pong` | both | `{timestamp}` (WebSocket pings are also used) |

`subscriptions[]` entries: `subscriptionId, label, slotIndex`.

`call.dial.subId` is the subscription to use for the outgoing call. Omit it to use the phone's
system-default SIM. A specific SIM requires Android 11+; the phone resolves the id to a call-capable
Telecom `PhoneAccount` and never silently falls back to a different SIM. Android 10 ignores the
selector at the UI level because the public subscription-to-account mapping does not exist there.

`photos[]` entries: `id, name, mime, size, date, width, height, bucket?`. Paging is newest-first by
`(date, id)`; pass the last item's `date` and `id` as `beforeDate`/`beforeId` to fetch the next page.

`albums[]` entries: `id` (opaque; volume + MediaStore bucket or a name fallback), `name`, `count`,
`coverId`, `latest` (epoch ms). Counts and covers reflect only photos the app can currently read.

`PhotoStreamMeta`: `streamId, photoId, kind, mime, size`.

## Binary frames

20-byte header, big endian:

| Offset | Size | Field |
|---|---|---|
| 0 | 1 | version (`1`) |
| 1 | 1 | type: `1` photo, `2` mirror, `3` handoff file |
| 2 | 2 | reserved (0) |
| 4 | 8 | stream id (uint64) |
| 12 | 4 | sequence (uint32, starts at 0) |
| 16 | 4 | flags (bit 0 = last chunk) |
| 20 | … | payload |

- Photo/file chunks are 64 KiB except the last. The last chunk sets the flag; a JSON
  `photo.streamEnd` / `handoff.fileEnd` event follows.
- Mirror frames are one binary message each, `last=false`, sequence increasing; dimensions come from
  `mirror.state`.
- Max binary message: 2 MiB; max payload: 1.9 MiB.
- Out-of-order or oversized streams are rejected by the PC stream registry.
- Stream ids are generated by the phone (photos/handoff) and are positive.

## Flows

### Pairing

```
phone                         PC
  │── wss connect, pin cert ──▶│
  │── pair.request(token,key)─▶│ validate token, show approval
  │◀───── pair.pending ────────│
  │                            │ user approves
  │◀────── pair.ok ────────────│ store device
  │── close, reconnect ───────▶│
```

### Authenticated session

```
phone                         PC
  │── hello(deviceId,key) ────▶│ lookup device
  │◀── auth.challenge(nonce) ──│
  │── auth.response(sig) ─────▶│ verify signature
  │◀────── auth.ok ────────────│ session active
  │── phone.info ─────────────▶│ capabilities
  │◀── sms.list / photo.fetch ─│ requests
  │── response / stream ──────▶│
```

## Error codes

`BAD_REQUEST`, `PERMISSION_DENIED`, `UNKNOWN_DEVICE`, `PAIRING_REQUIRED`,
`PAIRING_REJECTED`, `AUTH_FAILED`, `BUSY`, `UNSUPPORTED`, `NOT_FOUND`, `TOO_LARGE`,
`INTERNAL`.

`BUSY` means another phone is connected or pairing is already in progress. The phone retries on a
`BUSY` authentication error; other authentication errors stop the reconnect loop.

## Limits and compatibility

- Phone identity keys: P-256 only. StrongBox is not required.
- TLS: server certificate is self-signed RSA-2048, SHA-256, valid 10 years. The phone pins the
  exact leaf certificate and ignores hostname verification because the IP is only a route.
- PC protocol major version 1. The phone reports its app version but the protocol is the
  compatibility gate.
- File names and URLs are validated on both sides. Only `http`/`https` URLs may cross.
- No plaintext fallback exists.

## Changing the protocol

Bump `PROTOCOL_VERSION` for breaking changes, update both codecs and the tests, and add a row to
this document. Additive optional fields are safe because both decoders ignore unknown keys.
