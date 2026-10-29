## LinkPhone v0.6.1

A direct, LAN-only companion link between an Android phone and a Windows PC. This release adds an
opt-in 60 fps mode to the hardware H.264 mirror.

### What's new

- The Apps tab now has a **Frame rate** choice: **30 fps (balanced)** or **60 fps (smoother)**.
- 30 fps remains the default and keeps the 6 Mbps H.264 stream. 60 fps raises the target to 12 Mbps.
- If the phone's encoder cannot handle the current size at 60 fps, LinkPhone stays at 30 fps and the
  Apps tab says so; mirroring still starts normally.
- The Session card already shows the measured frame rate alongside the codec and dimensions.
- JPEG/Compatible mode is unchanged and still limited to about 5 fps.

### Which updates do I need?

- **Phone + Windows v0.6.1** for the 60 fps choice. v0.6.0 continues to work at 30 fps.
- Updating only one side is safe: the PC keeps 30 fps and mirroring works.
- Installed v0.4.0+ Windows builds can update in-app; portable builds update manually.

### Download

- `LinkPhone-0.6.1.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.6.1.exe` — Windows portable app
- `LinkPhone-Setup-0.6.1.exe` — Windows installer (recommended for automatic updates)
- `SHA256SUMS.txt` — verify your downloads

### Known limitations

- 60 fps needs a 60 Hz+ display, a hardware encoder that supports the stream size at 60 fps, and a PC
  that can decode it; otherwise the session runs at 30 fps.
- 60 fps uses more bandwidth, battery, and thermal headroom than 30 fps.
- H.264 still depends on the phone's hardware encoder and the PC's WebCodecs decoder; unsupported
  devices stay on JPEG.
- Secure/DRM windows remain black, and mirroring still stops on lock or rotation.
- Reliable cellular speaker routing still requires the default-dialer/`InCallService` role, which
  LinkPhone deliberately does not take. Call audio never reaches the PC.
- The Windows updater is unsigned and SHA-256 verified; portable builds update manually.

Built, unit-tested, linted, and E2E-tested in GitHub Actions. Real 30/60 fps behavior needs the
hardware checklist in `docs/testing.md`.
