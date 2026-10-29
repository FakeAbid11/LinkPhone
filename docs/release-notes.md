## LinkPhone v0.6.0

A direct, LAN-only companion link between an Android phone and a Windows PC. This release replaces the
slow JPEG mirror with hardware H.264 when both apps support it, and keeps JPEG as the compatible
fallback.

### What's new

- **Hardware H.264 screen mirroring:** about 720×1600, 30 fps, 6 Mbps, encoded with MediaCodec and
  decoded on the PC with WebCodecs. Text is sharper and motion is much smoother than the old ≤5 fps
  JPEG stream.
- **Automatic fallback:** if the phone has no hardware AVC encoder or the PC cannot decode the stream,
  mirroring uses the existing JPEG path. The Apps tab also has an explicit **Compatible (JPEG)** mode.
- **Fast recovery:** the PC requests IDR keyframes after a decode backlog or a Wi-Fi stall, and SPS/PPS
  are carried with keyframes so playback can resync.
- **Backward compatible:** an old Windows build with a new APK gets JPEG; a new Windows build with a
  v0.5.x APK also gets JPEG. Consent, lock/rotation stops, secure-window blackouts, and remote taps are
  unchanged.
- Session status now shows the active codec, dimensions, and measured frame rate.

No audio, keyboard input, clipboard-inside-the-app, or app virtualization was added.

### Which updates do I need?

- **Both apps** for H.264 mirroring. The Windows app must be v0.6.0 to decode H.264; the phone app
  must be v0.6.0 to encode it.
- Updating only one side is safe: mirroring falls back to JPEG.
- Installed v0.4.0+ Windows builds can update in-app; portable builds update manually.

### Download

- `LinkPhone-0.6.0.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.6.0.exe` — Windows portable app
- `LinkPhone-Setup-0.6.0.exe` — Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

### Known limitations

- H.264 depends on the phone's hardware encoder and the PC's WebCodecs decoder; unsupported devices
  stay on JPEG.
- H.264 uses more CPU/battery/thermal headroom than the JPEG fallback.
- Secure/DRM windows remain black, and mirroring still stops on lock or rotation.
- Reliable cellular speaker routing still requires the default-dialer/`InCallService` role, which
  LinkPhone deliberately does not take.
- Call audio never reaches the PC.
- The Windows updater is unsigned and SHA-256 verified; portable builds update manually.

Built, unit-tested, linted, and E2E-tested in GitHub Actions. H.264 encoding/decoding and real Wi-Fi
behavior need the hardware checklist in `docs/testing.md`.
