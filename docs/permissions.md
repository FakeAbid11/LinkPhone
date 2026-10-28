# Permissions and consent

LinkPhone asks for each capability at the point of use and explains why. Nothing is granted
silently. The PC can only use features the phone has granted; revoking a permission takes effect the
next time the feature is used (reconnect to refresh the capability list).

## Android permissions

| Permission | Android | Feature | When it is requested | If denied |
|---|---|---|---|---|
| `POST_NOTIFICATIONS` | 13+ | The required ongoing connection notification and the Open on phone prompt | Onboarding / permission card | The service still runs, but Android may hide its notification; Android 33+ also needs this for alerts and the Open on phone prompt |
| `READ_SMS` | all | Read SMS threads | Messages card | The PC cannot list messages |
| `RECEIVE_SMS` | all | New-SMS hint broadcast | Messages card | The PC still refreshes on connect and via the provider observer, but live hints stop |
| `SEND_SMS` | all | Send SMS | Messages card | Sending is refused |
| `READ_PHONE_STATE` | all | Call state, SIM list, and mapping a selected SIM to a call account | Calls card | No call-state events, caller SIM info, answer support checks, or SIM selection |
| `CALL_PHONE` | all | Place a call | Calls card | Dialing is refused |
| `ANSWER_PHONE_CALLS` | 26+ | Answer/decline and end a call | Calls card (optional group) | Answer/decline buttons are disabled |
| `READ_CALL_LOG` | all | Caller number on incoming calls | Calls card (optional) | Android 10+ hides the incoming number; the PC shows “Incoming call” |
| `READ_CONTACTS` | all | Contact name for a number | Calls card (optional) | The number is shown instead of a name |
| `READ_MEDIA_IMAGES` | 13+ | Camera-roll photos | Photos card | No photos |
| `READ_MEDIA_VISUAL_USER_SELECTED` | 14+ | Partial photo access | Photos card (requested with images) | Only full/partial access chosen in the system dialog |
| `READ_EXTERNAL_STORAGE` | 10–12 | Camera-roll photos | Photos card | No photos |
| `CAMERA` | all | Scan the pairing QR | Pair screen | Manual address/fingerprint/code entry is available |
| Notification access (special) | all | Mirror notifications, dismiss them | Notification access row | No notification mirroring |
| Accessibility service (special) | all | Send taps/swipes to a mirrored screen | Remote taps row | View-only mirroring |
| Battery optimization exemption (special) | all | Keep the foreground service alive | Background card | The service may be stopped more aggressively |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 23+ | Opens the exemption prompt | Background card | The app falls back to the system list |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION` | 28/34+/34+ | Connection and mirroring services | Manifest (normal) | Cannot start the services |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `WAKE_LOCK` | all | LAN socket, mDNS, keep-alive | Manifest (normal) | Connection fails or is unreliable |

### SMS and Google Play

`READ_SMS`, `RECEIVE_SMS`, and `SEND_SMS` are restricted permissions under Google Play policy for
most app categories. LinkPhone is distributed as a sideloaded APK, not through Play. Do not expect
to publish this build unchanged on Play.

LinkPhone does **not** request the default-SMS role (`ROLE_SMS`). That means:

- It can read the provider and send through `SmsManager`.
- Messages sent from the PC may not appear in the phone's stock SMS app or in LinkPhone's own list.
- It cannot write the provider's read flag. **Mark read** clears unread in LinkPhone on the PC;
  **Open on phone** posts a notification whose tap opens the conversation in the stock SMS app, so
  that app can mark it read. `POST_NOTIFICATIONS` is required for the prompt.
- MMS/RCS are not supported.

Taking the default-SMS role would require a complete SMS/MMS client and would replace the user's
existing messaging app. LinkPhone deliberately does not do that.

### Calls

- `ANSWER_PHONE_CALLS` is a runtime permission but OEMs may restrict programmatic answering to the
  default dialer. Some devices require settings changes or simply refuse; the PC reports the error.
- Caller ID is unavailable on Android 10+ without `READ_CALL_LOG` (or a call-screening role, which
  is not used).
- **SIM selection:** the PC can ask the phone to dial on a specific SIM on Android 11+. It uses
  `READ_PHONE_STATE` to map the chosen subscription to a call-capable Telecom `PhoneAccount` and
  passes the account to `placeCall`. Android 10 has no reliable public mapping, so calls use the
  system default and the selector is hidden. Some OEM dialers may ignore the requested account.
- Emergency calls are handled by Android's telecom stack; LinkPhone does not implement emergency
  dialing.
- Call **audio** cannot be captured by an ordinary app. See [feasibility.md](feasibility.md).

### Photos

On Android 14, the system may offer “Select photos”. LinkPhone declares
`READ_MEDIA_VISUAL_USER_SELECTED` so the picker appears, reports the partial state, and only lists
what the system shares. Grant full access in system settings for the full camera roll.

### Notifications

Notification access is granted in the system “Notification access” settings screen. The feed shows
what Android provides. On Android 15+ the system can redact one-time codes and other sensitive
content; LinkPhone cannot bypass that. Notification action buttons are not mirrored; only the
notification content and a dismiss action are exposed.

### Screen mirroring

- MediaProjection requires a visible consent activity **for every session**.
- The service declares the `mediaProjection` foreground-service type and starts it before capture.
- Capture stops when the screen locks, when another capture starts, or when the projection is
  revoked. Rotation ends the session because Android allows one virtual display per consent token.
- `FLAG_SECURE` windows, DRM content, and some banking apps appear black.
- Audio is never captured.
- Remote taps require the Accessibility service. LinkPhone only dispatches gestures sent by the
  paired PC. No window content is read.

### What LinkPhone never does

- No hidden APIs, no reflection into protected system services, no ADB, no root, no Shizuku.
- No bypassing permission dialogs, notification access, MediaProjection consent, or battery
  controls.
- No background clipboard reads. Android only allows the focused app to read the clipboard, so the
  phone sends clipboard text only after an explicit user action (Quick Settings tile, share,
  notification action, or the in-app button).
- No Google Play Services or Firebase dependency.

## Revoking access

- Remove the PC from the phone: **Forget PC** in LinkPhone.
- Remove the phone from the PC: Connection → Paired phones → Remove, or Settings → Reset PC
  identity.
- Revoke an Android permission in system settings; reconnect/reopen LinkPhone to refresh the
  capability list.
- Force-stop the phone app to stop the service; boot restart resumes when you open the app again.
