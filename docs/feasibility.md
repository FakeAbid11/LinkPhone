# Feasibility, platform limits, and scope

This document records what Android and Windows actually allow, what LinkPhone v0.7.0 implements, and
what would require a different product or system privileges.

## Summary

| Requested feature | v0.7.0 | Honest limit |
|---|---|---|
| Secure pair/reconnect | Implemented | QR + pinned TLS + ECDSA challenge; one phone at a time |
| SMS view/send | Implemented | Sideload only; unread is PC-local; Open on phone hands the thread to the stock SMS app (one tap); no MMS/RCS; no default-SMS role, so sent items may not enter stock threads |
| Calls from PC | Limited | Dial/answer/decline/end, caller state, contact search, SIM choice on Android 11+, and a best-effort speaker toggle where OEM permits; **no call audio on PC**; caller ID needs call-log access |
| Photos | Implemented | All MediaStore image albums with paging; JPEG transcode; Android 14 partial access respected |
| Notification mirroring | Implemented | Per-app mute, dismiss; Android 15+ may redact sensitive content; no action buttons |
| Apps streaming | Experimental substitute | Hardware H.264 mirroring up to ~720×1600 at 30 fps (or opt-in 60 fps) where the phone and PC support it, with JPEG fallback; tap/swipe only; no app virtualization; consent each session; stops on lock |
| Clipboard/handoff | Limited | PC→phone works; phone→PC explicit; “Continue on PC” via share sheet |
| Background operation | Best effort | Foreground service, boot restart, locks; OEM/Doze/force-stop can still stop it |
| Windows updates | Implemented | Installed NSIS build only; portable is manual; SHA-256 verified, unsigned |
| Android updates | Implemented | In-app check and download on release builds; SHA-256 **and** signing-certificate verified; Android performs the install; the user must allow "install unknown apps" once; debug builds never contact GitHub |

## Android version requirements

- **Minimum:** Android 10 (API 29). This gives scoped MediaStore, `loadThumbnail`, and a modern
  foreground-service model.
- **Target/compile:** API 35 (Android 15).
- **Tested in CI:** compilation, JVM unit tests, lint. **Not tested on a physical device** in this
  environment; see [testing.md](testing.md) for the acceptance checklist.
- **Android 6–9:** not supported. Some APIs (foreground-service types, scoped storage behavior) and
  the Compose/AndroidX baseline assume API 29+.

## Feature-by-feature

### Secure pairing and reconnection — implemented

A phone and PC on the same LAN can pair with a QR code. The phone pins the PC's self-signed
certificate from the QR; the PC stores the phone's P-256 public key and verifies a signed nonce on
every reconnect. There is no cloud dependency. One-time tokens, rate limits, and explicit PC
approval prevent unsolicited pairing.

Limits: mDNS can be blocked by AP isolation or some VPNs; QR/manual entry is the fallback. A lost
phone should be removed on the PC. A compromised PC can use whatever the phone granted.

### SMS — implemented, with Android constraints

`READ_SMS` gives access to the standard `content://sms` provider for a sideloaded app. `SmsManager`
sends single- and multi-part SMS. `SMS_RECEIVED` is used only as a refresh hint; the provider is the
source of truth.

Limits:
- Google Play restricts SMS permissions; this app is distributed outside Play.
- Without the default-SMS role, sent messages are not written into the stock app's thread. The PC
  keeps a local sent copy; the phone's provider does not.
- **Read state:** only the app receiving `SMS_DELIVER_ACTION` (the default SMS app) may write the
  provider's read flag. LinkPhone keeps a per-device read-state set on the PC and offers **Open on
  phone**, which posts a notification; tapping it opens the conversation in the stock SMS app,
  which marks it read. This is a one-tap handoff, not a silent write, and LinkPhone does not take
  the default-SMS role.
- MMS and RCS require a default messaging app and additional APIs; out of scope.
- Dual-SIM sending uses `createForSubscriptionId` on Android 12+; older versions use the default
  SIM.
- Group MMS threads are not supported (they appear as individual MMS messages, which are not read).

### Calls — control only

What works:
- Call state (`idle`, `ringing`, `offhook`) via `PhoneStateListener`.
- Incoming number when `READ_CALL_LOG` is granted; contact name when `READ_CONTACTS` is granted.
- Placing a call with `TelecomManager.placeCall()` and `CALL_PHONE` (works from a service; unlike
  `ACTION_CALL`, which is subject to background activity-start restrictions).
- Answer/decline/end with `ANSWER_PHONE_CALLS` and `TelecomManager.acceptRingingCall()`/`endCall()`.
- A **best-effort speaker toggle** using `AudioManager.setCommunicationDevice()` on Android 12+,
  plus the deprecated `isSpeakerphoneOn` request as an OEM fallback on all supported versions. The app
  waits briefly and verifies the reported route; if the phone still reports the old route, it keeps
  the toggle off and returns an explicit error. Telecom/default dialer ownership means this may still
  be refused entirely.
- Choosing a SIM for an outgoing call on Android 11+: the PC sends the subscription id; the phone
  finds the matching call-capable Telecom `PhoneAccount` using
  `TelephonyManager.getSubscriptionId(handle)` and passes `EXTRA_PHONE_ACCOUNT_HANDLE` to
  `placeCall`. Android 10 has no reliable public subscription-to-account mapping, so the selector is
  hidden and calls use the system default. Some OEM dialers may ignore the requested account.
- Searching contacts on the PC with `READ_CONTACTS`: the phone queries the contacts provider on
  demand and returns names, labels, and dialable numbers. The PC filters them locally and fills the
  dialer; no contact data is written to disk on either side.

What does **not** work and cannot be fixed by a normal app:
- **Call audio.** `AudioRecord` with `VOICE_CALL` requires the signature/privileged
  `CAPTURE_AUDIO_OUTPUT`. `AudioPlaybackCapture` captures only `USAGE_MEDIA`/`GAME`/`UNKNOWN` and
  excludes `VOICE_COMMUNICATION`. VoLTE/VoWiFi audio is also protected. The PC cannot be a
  speakerphone for a cellular call.
- Speaker routing is **not guaranteed**: Android does not expose reliable cellular call-route
  control to a non-default-dialer companion. LinkPhone labels the control best effort and tells the
  user to fall back to the phone's in-call screen or a headset.
- Answer/decline/end reliability. These APIs are deprecated; OEMs vary. Some devices require the
  default-dialer role for full in-call control. LinkPhone does not take that role because a buggy
  `InCallService` would break the user's ability to answer calls at all.
- Caller ID without call-log access. Android 10+ blanked the incoming number for ordinary listeners.

Alternative considered: implement a full `InCallService` and ask for the default-dialer role. That
is a much larger product with a high risk of breaking calls; rejected for v1.

### Photos — implemented

MediaStore gives the image library, grouped by album. Thumbnails use `ContentResolver.loadThumbnail`;
full images are decoded with a sample size and transcoded to JPEG (max 2048 px) so the PC does not
need HEIC support and memory stays bounded. The PC requests 200 photos per page and appends older
pages with a date/id keyset; album counts and covers come from one metadata pass on the phone. “Save
to PC” writes to `Pictures\LinkPhone`; “Copy image” writes to the PC clipboard.

Limits: Android 14 partial access limits the list to selected photos, so album counts and covers are
also partial. Very large images are sampled. The PC's photo list is a snapshot; refresh or “Load
more” to update.

### Notifications — implemented

A `NotificationListenerService` receives posted/removed notifications and forwards title, text, app
label, category, and time. The PC can mute an app or dismiss a notification (`cancelNotification`).

Limits: Android 15+ hides sensitive content (OTP codes) from listeners. Action buttons are not
mirrored because firing another app's `PendingIntent` can be blocked by trampoline restrictions and
is not reliably representable. Ongoing/system notifications may not be dismissible. Notifications
posted while the service is dead are lost; reconnect and new notifications work.

### Apps — experimental screen mirroring, not app streaming

There is no public Android API that runs arbitrary apps inside a Windows window. Microsoft's Phone
Link app streaming relies on device-vendor integration and special privileges. LinkPhone uses
`MediaProjection` to mirror the display and an Accessibility service to inject taps/swipes.

Limits:
- Consent for every session; the service must be foreground with the `mediaProjection` type.
- Capture stops on screen lock, on a new capture, or when the projection is revoked.
- One virtual display per token; rotation ends the session.
- `FLAG_SECURE`/DRM windows are black.
- H.264 is used when the phone exposes a hardware AVC surface encoder and the PC has WebCodecs;
  otherwise the fallback is JPEG at ≤5 fps and ≤1280 px. H.264 targets ~720×1600 at 30 fps/6 Mbps
  by default; the opt-in 60 fps mode doubles the bitrate (12 Mbps) and needs a 60 Hz display plus a
  hardware encoder that supports the size at 60 fps, otherwise it falls back to 30 fps. Both cost more
  battery/thermal headroom than JPEG.
- No audio, no keyboard input, no clipboard inside the mirrored app beyond normal phone behavior.
- Accessibility gestures are single strokes; the service must be manually enabled.

This is labeled **Experimental** everywhere. A proper scrcpy-like product needs ADB or root; that
would violate the request to avoid hidden APIs and elevated system access.

### Clipboard and Continue on PC — explicit, not silent

- **PC → phone:** the PC sends text; the phone writes it and posts a “Copied from PC” notification
  that can re-copy it while the app is focused.
- **Phone → PC:** Android 10+ allows clipboard reads only for the focused app or default IME, so
  LinkPhone provides a Quick Settings tile, a share target, and an in-app action. There is no
  silent background clipboard sync in this direction.
- **Continue on PC:** the phone share sheet sends links, text, or images to the PC. The PC shows them
  in Connection → Continue on PC; links open only after a click. “Send back” posts a tappable link
  notification on the phone. This is an explicit handoff, not automatic app continuity.

### App updates — implemented on the phone, with Android's own installer

The phone app can check GitHub Releases for a newer version, download the release APK, verify it,
and hand it to Android's package installer. The Windows app has had this since v0.4.0; the Android
app gets the same flow in v0.7.0.

What works:
- A check runs shortly after launch and every six hours, and on demand from **Home → Updates**. The
  interval is persisted, so a process restart does not spend another request from GitHub's
  unauthenticated quota (60 requests/hour per IP, and carrier NAT shares an IP between phones).
- The download is streamed to `filesDir/updates/`, verified against `SHA256SUMS.txt`, and then
  checked against the running app's signing certificate.
- The APK is handed to Android's own package installer through a `FileProvider` content URI. Android
  shows the app name, version, and permissions, then replaces the app. LinkPhone cannot install
  anything without that system screen.

Limits:
- **The user must allow "install unknown apps" for LinkPhone once.** The permission is
  per-app and revocable; the app explains it in a dialog before sending you to the settings screen.
- **Android performs the install, not the app.** The OS kills the process when the update is
  applied; the app cannot restart itself.
- An APK signed with a different key is refused. A debug-signed build therefore cannot silently
  replace an official installation, and vice versa.
- The download runs in an app-scoped coroutine, not a foreground service. If the process dies
  mid-download the partial file is discarded and the user taps **Check for updates** again. A
  separate `dataSync` foreground service would add a permission, hit Android 15's six-hour
  `dataSync` cap, and cannot be started from the background on API 31+.
- GitHub's unauthenticated API rate limit can be reached on a shared mobile IP; the error message
  says so and a later retry works.
- Debug builds never contact GitHub at all.

### Background operation — best effort

Android's supported mechanism for an ongoing LAN connection is a foreground service. LinkPhone uses
the `connectedDevice` type, an ongoing notification, `START_STICKY`, boot restart, and Wi-Fi/multicast
locks. It does not use a periodic WorkManager watchdog because Android 12+ blocks starting a
foreground service from background workers; a “watchdog” would look useful but fail when needed.

OEM task killers, Doze, data-saver, and force-stop can still stop the service. Force-stop disables
restart until the user opens the app. The app shows “Disconnected” rather than pretending. Users can
grant the battery exemption and OEM autostart settings; see [permissions.md](permissions.md).

## Rejected approaches

| Approach | Why not |
|---|---|
| Cloud relay / Firebase | The request requires LAN-only with no third-party servers; also adds account and privacy surface |
| ADB / scrcpy integration | Requires USB debugging or wireless debugging and ADB; not a normal permission-granted app |
| Root / Shizuku / hidden APIs | Violates the explicit no-hidden-APIs constraint |
| Full default SMS/dialer app | Would require a complete MMS/RCS/InCallService implementation and replace the user's apps |
| Capturing call audio | Signature/privileged permission; impossible for a sideloaded ordinary app |
| In-app VM for Android apps | Requires system virtualization/vendor integration; impossible as a third-party app |
| Silent phone→PC clipboard | Android 10+ focus restriction; a background read is blocked, and workarounds would be evasions |

## Sources

- Foreground service types and boot restrictions: <https://developer.android.com/develop/background-work/services/fgs/service-types>
- Foreground service changes in Android 15: <https://developer.android.com/about/versions/15/changes/foreground-service-types>
- Media projection consent, FGS type, stop-on-lock: <https://developer.android.com/media/grow/media-projection>
- Partial photo/video access: <https://developer.android.com/about/versions/14/changes/partial-photo-video-access>
- Android 10 privacy changes (clipboard, incoming number): <https://developer.android.com/about/versions/10/privacy/changes>
- Audio playback capture usages: <https://developer.android.com/media/platform/av-capture>
- SMS provider/default role background: <https://android-developers.googleblog.com/2013/10/getting-your-sms-apps-ready-for-kitkat.html>
- `TelecomManager.placeCall` / `acceptRingingCall`: <https://developer.android.com/reference/android/telecom/TelecomManager>
