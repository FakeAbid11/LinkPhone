## LinkPhone v0.3.2

A direct, LAN-only companion link between an Android phone and a Windows PC. This release fixes
“Photo not found or could not be decoded” when saving or copying an image.

### What's new

- Photo fetches now carry the MediaStore volume name, so images stored on an SD card or a
  non-primary volume resolve to the correct URI instead of the primary-volume URI.
- Full-size decoding now tries Android’s `ImageDecoder` first and falls back to `BitmapFactory`.
  This handles more formats (including HEIC where the platform supports it) and applies orientation
  automatically.
- The bitmap sampling calculation was corrected so very large photos are decoded near 2048 px
  instead of peaking at 3000–4000 px, which could run out of memory on lower-end phones.
- Diagnostics now log the photo id and volume when a decode fails.
- Thumbnails, previews, Save to PC, and Copy image all use the same volume-aware request. Older PCs
  omit the volume and still work for primary-storage photos.

No permission or UI changes.

### Which updates do I need?

- **Both apps.** The PC must send the photo volume; the phone uses it to resolve the image.
- Existing pairings and the signing identity are retained.

### Download

- `LinkPhone-0.3.2.apk` — Android 10+ companion (sideload; same release key)
- `LinkPhone-Portable-0.3.2.exe` — Windows portable app
- `LinkPhone-Setup-0.3.2.exe` — Windows installer
- `SHA256SUMS.txt` — verify your downloads

### First run

1. Allow LinkPhone through Windows Firewall on **Private** networks.
2. Open the PC app and choose **Pair a phone**.
3. Install the APK, open LinkPhone, and follow the setup guide.
4. Scan the QR code and approve the pairing prompt on the PC.
5. Grant only the permissions you want; each feature explains itself first.

### Known limitations

- If the photo was deleted, moved, or its access was revoked after the list loaded, refresh and try
  again. Android may also reject corrupt or cloud-backed files that have only a cached thumbnail.
- HEIC/AVIF support ultimately depends on the phone’s platform codecs; if the full image cannot be
  decoded, LinkPhone reports the failure instead of saving a truncated file.
- Mirroring remains experimental and is not app streaming; see the v0.3.1 notes.
- The Windows binaries are unsigned, so SmartScreen may warn.

Built, unit-tested, linted, and E2E-tested in GitHub Actions. Saving SD-card, HEIC, and very large
photos still needs the hardware checklist in `docs/testing.md`.
