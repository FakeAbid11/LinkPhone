# Building, signing, and releasing

The authoritative build is GitHub Actions. This repository was developed on a low-end Windows
machine and deliberately does **not** build or run locally; CI is the source of truth for
compilation, tests, packaging, and release artifacts.

## Toolchain

| Part | Version |
|---|---|
| JDK | 17 (Temurin) |
| Gradle | 8.9 (installed by `gradle/actions/setup-gradle`; no wrapper jar is committed) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 + Compose compiler plugin |
| compileSdk / targetSdk | 35 |
| minSdk | 29 (Android 10) |
| Compose BOM | 2024.12.01 |
| Node | 20 in CI |
| Electron | 33.2.1 |
| electron-builder | 25.1.8 |
| React / Vite / Vitest | 18.3.1 / 5.4.11 / 2.1.8 |
| Playwright | 1.49.1 |

## CI

`.github/workflows/ci.yml` runs on every pull request and on `main`:

- **android** (`ubuntu-latest`): decodes the signing keystore if configured, runs
  `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleRelease`, verifies the APK with
  `apksigner`, and uploads it as `LinkPhone-Android`.
- **desktop** (`windows-2022`): `npm ci` (from the committed `package-lock.json`),
  `npm run typecheck`, `npm test`, `npm run build`, `npx playwright test`, `npm run dist:win`,
  a packaged-EXE smoke test, checksums, and artifact upload.

Failure reports (test/lint/Playwright) are uploaded for seven days when a job fails.

## Signing the APK

Create one stable release keystore **outside the repository**:

```bash
mkdir -p ~/.linkphone-signing
keytool -genkeypair -v \
  -storetype PKCS12 \
  -keystore ~/.linkphone-signing/linkphone-release.jks \
  -alias linkphone \
  -keyalg RSA -keysize 4096 -sigalg SHA256withRSA \
  -validity 10000 \
  -dname "CN=LinkPhone, O=LinkPhone" \
  -storepass "$YOUR_PASSWORD" -keypass "$YOUR_PASSWORD"
```

Back up the keystore and password. Then set four repository secrets:

```bash
base64 -w0 ~/.linkphone-signing/linkphone-release.jks \
  | gh secret set ANDROID_KEYSTORE_BASE64 --repo FakeAbid11/LinkPhone
gh secret set ANDROID_KEYSTORE_PASSWORD --repo FakeAbid11/LinkPhone --body "$YOUR_PASSWORD"
gh secret set ANDROID_KEY_ALIAS --repo FakeAbid11/LinkPhone --body linkphone
gh secret set ANDROID_KEY_PASSWORD --repo FakeAbid11/LinkPhone --body "$YOUR_PASSWORD"
```

- CI writes the decoded keystore to `$RUNNER_TEMP` and exports `ANDROID_KEYSTORE_PATH`.
- `android/app/build.gradle.kts` creates the release signing config only if the file exists.
- Without the secret, CI builds a debug-signed APK and labels it `debug-signed`. The release workflow
  **refuses** to publish without the key.
- Never commit the keystore, password, or base64 blob. `.gitignore` blocks common names, but verify
  before staging.
- Losing the key means future updates cannot install over the old app; users must uninstall first.
- A leaked key allows malicious updates. Rotate the key and publish a fresh signing identity if that
  happens.

## Releases

`.github/workflows/release.yml` runs on tags matching `v*`:

1. Android: requires the signing secrets, runs tests/lint, builds with the tag's version name, and
   uploads `LinkPhone-<version>.apk`.
2. Desktop: aligns `package.json` with the tag, typechecks/tests/E2E, packages NSIS + portable EXE.
3. Publish: downloads both artifacts, writes `SHA256SUMS.txt`, and creates the GitHub Release with
   `gh release create`.

```bash
git tag v0.1.3
git push origin v0.1.3
```

The tag must be `v` + the semantic version used for the desktop package. The release notes live in
[release-notes.md](release-notes.md).

## Local development (optional)

CI is the supported build path, but the projects are ordinary Gradle/Node projects:

### Android

Requirements: JDK 17, Android SDK with platform 35 and build-tools, Gradle 8.9 (or generate a wrapper
with `gradle wrapper --gradle-version 8.9`).

```bash
gradle -p android :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Pass the signing environment variables to produce a signed release, or use `assembleDebug` for a
debug APK. Do not commit `local.properties`.

### Desktop

```bash
cd desktop
npm ci
npm run typecheck
npm test
npm run build
npx playwright test
npm run start
```

The npm lockfile is committed; use `npm install` only when deliberately changing dependencies,
then commit the updated lockfile.

`npm run dist:win` packages the app into `desktop/release/`. The Windows build is unsigned.

The Windows updater reads the release's `LinkPhone-Setup-<version>.exe` and `SHA256SUMS.txt` from
GitHub Releases. Keep both assets attached to every published release; the portable EXE is not used
for automatic updates. No `latest.yml` or electron-updater metadata is required.

The Android updater reads the release's `LinkPhone-<version>.apk` and `SHA256SUMS.txt` from the same
release. **Both assets must stay attached**, and the APK must be signed with the stable release key
whose SHA-256 digest is

```
FD:F3:D0:3E:A4:11:EF:50:3A:1C:5A:17:90:DE:A6:1A:38:80:EE:1C:72:4C:63:C1:49:7B:03:E6:C1:C6:A4:E3
```

CI prints that digest with `apksigner verify --print-certs`; check it after every release, because
an APK signed with any other key is refused by the in-app updater (that is the intended behavior,
not a bug). A release APK built without the signing secret falls back to the debug key and will
also be refused.

`kotlinx-serialization-json` must stay at 1.7.x while the project is on Kotlin 2.0.21; bumping to
Kotlin 2.1+ requires `kotlinx-serialization-json` 1.8.0 or newer.

### Full CI commands

```bash
# Android
gradle -p android :app:testDebugUnitTest :app:lintDebug :app:assembleRelease

# Desktop
cd desktop
npm ci && npm run typecheck && npm test && npm run build && npx playwright test
```

## Artifacts

- `LinkPhone-<version>.apk` — release-signed Android APK
- `LinkPhone-Portable-<version>.exe` — portable Windows app
- `LinkPhone-Setup-<version>.exe` — NSIS installer
- `SHA256SUMS.txt` — checksums for the release files

GitHub Actions artifacts expire; release assets do not.

## Repository layout

```
android/   Kotlin/Compose app, Gradle build, JVM tests
desktop/   Electron main/preload/renderer, Vitest tests, Playwright E2E, builder config
docs/      architecture, protocol, feasibility, permissions, security, testing, troubleshooting
.github/   CI and release workflows
shared/    (types live in desktop/src/shared)
```

## Troubleshooting CI

- **Android lint fails:** read `android/app/build/reports/lint-results-debug.html` from the failure
  artifact. Fix the code rather than disabling checks wholesale.
- **APK unsigned:** `ANDROID_KEYSTORE_BASE64` is missing or the password/alias is wrong. The CI log
  should say `release` or `debug-signed` in the artifact name.
- **Desktop typecheck:** run `npm run typecheck` and fix strict errors; the Electron main build uses
  CommonJS while the renderer uses ESM/Vite.
- **Playwright Electron smoke test:** it launches `win-unpacked`/the project with `electron .` and
  requires the Vite renderer build. If it fails, the job prints the Electron stderr.
- **electron-builder:** NSIS/portable packaging runs on `windows-2022`. The app icon is
  `desktop/build/icon.png` (1024×1024). Signing is intentionally disabled.
