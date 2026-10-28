# Troubleshooting

Start with the diagnostics log on the phone (LinkPhone → Diagnostics → Share) and the PC log
(Settings → Open logs, or `%APPDATA%\LinkPhone\logs\linkphone.log`). The logs contain connection
events and error classes, not message contents.

## Pairing and connection

### The QR code will not scan

- Increase screen brightness; hold the phone 15–30 cm away.
- The QR expires after five minutes. Create a new code.
- If the camera is blocked, use **Enter details manually** and type the address, port, certificate
  fingerprint, and pairing code from the PC.
- Grant camera permission in system settings if the scanner shows only a permission screen.

### “No local network address found”

The PC has no private IPv4 address. Connect it to Wi-Fi or Ethernet. If only a VPN adapter is
present, disconnect the VPN. LinkPhone ignores loopback and link-local (`169.254.x.x`) addresses.

### Firewall blocks the connection

Allow inbound TCP **48921** (or the port shown in Settings) on **Private** networks. Run the
copyable command from Connection → Network checklist in an Administrator PowerShell:

```powershell
New-NetFirewallRule -DisplayName "LinkPhone" -Direction Inbound -Protocol TCP -LocalPort 48921 -Action Allow -Profile Private
```

If Windows classifies the network as Public, change it to Private or add a rule for Public only if
you understand the exposure. Do not expose the port to the internet.

### Guest Wi-Fi / AP isolation

Many guest, hotel, and corporate networks block device-to-device traffic even on the same subnet.
LinkPhone cannot fix that. Use a normal home network or a phone hotspot (PC connected to the phone's
hotspot) and pair there.

### VPN on the phone or PC

VPN apps often route or block LAN traffic. Turn the VPN off on both devices while pairing and
connecting, or configure split tunneling for the local subnet.

### “This PC does not recognize the phone”

The PC's paired-device store was reset or the phone's id changed. Pair again. If the PC still shows
an old device, remove it first in Connection → Paired phones.

### “This pairing code has already been used” / “expired”

Create a new code on the PC. A code is single-use and expires after five minutes. Three incorrect
attempts from one IP cause a 15-minute lockout for that IP.

### “PC certificate changed. Pair this phone again.”

The PC's certificate or identity was reset (Settings → Reset PC identity), or the stored details
changed. Forget the PC on the phone, remove the phone on the PC, and pair again. Do not ignore this
message: certificate pinning is what prevents impersonation.

### “Another phone is already connected”

Only one phone can be active. Disconnect the other phone (its notification has a Disconnect action)
or remove it on the PC, then retry.

### It disconnects when the screen is off / after a while

Android and OEMs may stop background services. Do all of the following:

1. Allow the ongoing notification (Android 13+).
2. Allow LinkPhone to ignore battery optimizations (Background card).
3. Enable Autostart and unrestricted background activity in the OEM settings:
   - Xiaomi/Redmi: Autostart, Battery saver → No restrictions
   - Samsung: remove from Sleeping apps, allow background activity
   - Oppo/Realme/OnePlus: Auto launch, background activity
   - Vivo/iQOO: Autostart, high background power usage
   - Huawei: protected apps
4. Do not force-stop the app. Force-stop disables boot restart until you open it again.
5. Keep the phone and PC on the same Wi-Fi; some routers drop idle clients.

The service is a foreground service because Android requires it for an ongoing connection. There is
no hidden or battery-exempt API that does better without system privileges.

## SMS

- **Conversation list does not scroll:** fixed in v0.1.2; update the PC app. The conversation list
  and message history now have independent scroll areas.
- **No threads:** grant READ_SMS, then reconnect or reopen Messages. The provider may be empty.
- **Mark read only clears LinkPhone:** expected. Android only lets the default SMS app write the
  phone's read flag. Use **Open on phone**; a notification appears on the phone, and tapping it
  opens the conversation in your stock SMS app, which marks it read. Allow notifications on the
  phone, and note that OEM SMS apps may behave differently.
- **Open on phone is missing:** update the phone app to v0.1.2 and grant SMS. The PC hides the button
  when the phone does not advertise `sms.openThread`; the PC can still mark read locally.
- **Sent but not in the stock app:** expected without the default-SMS role. LinkPhone reports what
  it handed to the phone; delivery reports are not implemented.
- **Sending fails:** check SEND_SMS, network/carrier service, and the destination format. Short
  codes and alphanumeric senders may reject replies.
- **MMS/RCS missing:** not supported. LinkPhone reads the SMS provider only.
- **Dual SIM:** choose the SIM in the composer. Android 12+ supports per-subscription sending;
  older versions use the default SIM.

## Notifications

- **Nothing mirrored:** grant Notification access for LinkPhone in system settings, then reconnect.
  On Android 15+, the listener may need to be rebound; reopening LinkPhone usually does it.
- **OTP text missing:** Android 15+ can redact sensitive notification content. This is a system
  privacy feature; LinkPhone cannot bypass it.
- **Some apps never appear:** they may post no text, or use custom notification layouts that expose
  nothing readable.
- **Dismiss does nothing:** ongoing/system notifications may not be cancelable; notification access
  may have been revoked.
- **Muted app still appears:** mute the app from its notification, or check Settings; mute is stored
  on the PC.

## Photos

- **No photos:** grant Photos access. On Android 14, “Select photos” grants only the chosen images;
  grant full access in system settings for the full camera roll.
- **Thumbnail is slow:** the phone generates JPEG thumbnails on demand; scroll to load lazily.
- **Save fails:** check free disk space in `Pictures`; the target is `Pictures\LinkPhone`.
- **HEIC looks different:** the phone transcodes to JPEG, so the saved copy is a JPEG, not the
  original HEIC file.

## Calls

- **No caller number:** Android 10+ requires READ_CALL_LOG for the incoming number. Grant it and
  reconnect; without it the PC shows “Incoming call”.
- **Answer/decline fails:** grant ANSWER_PHONE_CALLS. Some OEMs restrict programmatic answering to
  the default dialer; use the phone notification in that case.
- **No audio on the PC:** expected and unavoidable for a third-party app. Use the phone's
  speakerphone or a headset. See [feasibility.md](feasibility.md).
- **Dial fails:** grant CALL_PHONE and ensure a SIM is active. Emergency calls are handled by
  Android's telecom stack.
- **No SIM selector when dialing:** the selector only appears when the phone reports two or more
  active SIMs **and** `calls.simSelection` (Android 11+). Grant Phone permission, reopen LinkPhone
  so it refreshes `phone.info`, and reconnect. On Android 10 the official public mapping is
  unavailable; calls use the system default SIM. Some OEM dialers ignore the requested account even
  on Android 11+; the PC surfaces an error rather than silently choosing another SIM.
- **No contacts in the picker:** grant Contacts (READ_CONTACTS) on the phone, update the phone app
  to v0.1.3, reopen LinkPhone, and reconnect so it reports the `contacts` capability. Contacts with
  no phone number are not listed. The list is requested on demand and is not stored on the PC.

## Clipboard and Continue on PC

- **PC → phone paste fails:** the phone writes the clipboard in the background, which some OEMs
  restrict. Open the “Copied from PC” notification and tap it to copy while LinkPhone is focused.
- **Phone → PC automatic sync does not exist:** Android only allows the focused app to read the
  clipboard. Use the Quick Settings tile, the share sheet, or the in-app button.
- **Tile missing:** add “Send clipboard to PC” from the Quick Settings edit screen.
- **Shared link will not open:** only `http`/`https` URLs are allowed. The PC does not auto-open
  links; click Open on PC.
- **Shared image missing:** check `Downloads\LinkPhone`; only images up to 20 MB are accepted.

## Screen mirroring

- **Black screen:** `FLAG_SECURE`/DRM apps opt out of capture; banking apps commonly do this.
- **Stops on lock/rotation:** Android ends the projection on lock and allows one virtual display per
  consent token. Start mirroring again from the Apps tab.
- **No tap/swipe:** enable the LinkPhone Accessibility service (phone → Permissions → Remote taps).
  The PC shows “View only” until it is enabled.
- **Low frame rate:** expected; the phone sends ≤5 fps at ≤1280 px to limit CPU/battery use.
- **Consent notification never appears:** allow notifications on the phone. The consent must be
  given for every session; there is no persistent capture permission.
- **Mirroring stops when the PC window is in the background:** the phone keeps capturing until you
  stop it; frames are simply not visible. Stop it from the notification to save battery.

## PC app

- **SmartScreen warning:** the Windows build is unsigned. Verify the SHA-256 from the release page,
  or build from source. Do not run a binary from an untrusted mirror.
- **Blank window / renderer error:** update Windows/WebView2 (Electron bundles Chromium), restart the
  app, and check the log. A GPU driver issue can be bypassed with `--disable-gpu` for diagnostics.
- **Port already in use:** LinkPhone tries 48921–48930. If all are taken, close the conflicting app
  or change the firewall rule to the port shown in Settings.
- **Closing the window did not quit:** LinkPhone lives in the tray. Use the tray menu → Quit.
- **Start with Windows:** toggle it in Settings; Windows may still block startup apps.

## Reset and cleanup

- Remove a phone: Connection → Paired phones → Remove.
- Remove all pairings and the PC key: Settings → Reset PC identity. Every phone must pair again.
- Forget the PC on the phone: Diagnostics → Forget PC.
- Uninstall the PC app; the firewall rule remains until you delete it:
  `Remove-NetFirewallRule -DisplayName "LinkPhone"`.
- Uninstall the APK; Android removes its private data.

## Still stuck?

Collect:

- Phone: Diagnostics → Share (log)
- PC: Settings → Open logs
- Device model, Android version, OEM, Windows version
- The exact error message

Open an issue at <https://github.com/FakeAbid11/LinkPhone/issues>. Do not post SMS or notification
content; the logs do not contain it.
