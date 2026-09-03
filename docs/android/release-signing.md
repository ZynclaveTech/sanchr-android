# Android release signing

Introduced in M6 Phase 1. This document is the single source of truth for how
`sanchr-android` produces a signed release APK/AAB. No key material or
passphrase ever lives in the repository — everything below is driven by
environment variables (locally) or GitHub secrets (CI).

## 1. Generate the upload keystore

Once per project lifetime. The same upload key is used for every internal,
beta, and production release; **rotation requires coordinating with Play App
Signing** so treat this step as a ceremony, not a convenience.

```bash
# Canonical parameters for Sanchr v1. Validity must be ≥ 25 years per Play
# policy; 30 years gives headroom for later rotation planning.
keytool -genkeypair \
  -v \
  -keystore android/sanchr-android/keystore/sanchr-upload.jks \
  -storetype PKCS12 \
  -alias sanchr-upload \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10950 \
  -dname "CN=Sanchr, O=Zynclave, L=Bengaluru, ST=Karnataka, C=IN"
```

You will be prompted twice for the store password and once for the key
password. **Use a passphrase manager**; do not reuse any other production
secret.

After generation, capture the upload-key SHA-256 fingerprint — Play App Signing
asks for it at enrollment:

```bash
keytool -list -v \
  -keystore android/sanchr-android/keystore/sanchr-upload.jks \
  -alias sanchr-upload \
  | grep -A1 "SHA256:"
```

Record the fingerprint in §5 below.

## 2. Environment variable contract

The Gradle release build reads four variables. All four must be set; the
`packageRelease` / `assembleRelease` / `bundleRelease` guard in
`app/build.gradle.kts` aborts the build with a clear message otherwise.

| Variable | Meaning | Example |
|---|---|---|
| `SANCHR_RELEASE_STORE_FILE` | Absolute path to the `.jks` file | `/Users/you/.config/sanchr/sanchr-upload.jks` |
| `SANCHR_RELEASE_STORE_PASSWORD` | Keystore password from step 1 | `******` |
| `SANCHR_RELEASE_KEY_ALIAS` | Alias used in step 1 | `sanchr-upload` |
| `SANCHR_RELEASE_KEY_PASSWORD` | Key password from step 1 | `******` |

### Local workflow

Prefer `direnv` or `~/.gradle/gradle.properties` over inlining these into your
shell profile — they leak less on screen-share and in process listings.

Example `.envrc` (gitignored by direnv's convention):

```bash
export SANCHR_RELEASE_STORE_FILE="$HOME/.config/sanchr/sanchr-upload.jks"
export SANCHR_RELEASE_STORE_PASSWORD="$(security find-generic-password -w -s sanchr-release-store)"
export SANCHR_RELEASE_KEY_ALIAS="sanchr-upload"
export SANCHR_RELEASE_KEY_PASSWORD="$(security find-generic-password -w -s sanchr-release-key)"
```

### CI workflow

GitHub Actions stores the four values as encrypted repository secrets of the
same names. The `assemble-release` job base64-decodes a fifth secret
(`SANCHR_RELEASE_STORE_BASE64`) into a tempfile and points
`SANCHR_RELEASE_STORE_FILE` at it. See Phase 4 of the M6 plan.

## 3. Producing a signed APK / AAB

With the four env vars set:

```bash
cd android/sanchr-android
./gradlew :app:assembleRelease   # APK
./gradlew :app:bundleRelease     # AAB (required for Play upload)
```

Outputs:

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab`
- `app/build/outputs/mapping/release/mapping.txt` — **upload this to Play
  Console** (Deobfuscation files) every release.

Verify the signature before uploading:

```bash
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

The printed SHA-256 must match the fingerprint recorded in §5.

## 4. Without env vars

Running a release build without the four env vars set produces:

```
> Task :app:packageRelease FAILED

Release signing config is not populated.
Set all four environment variables before running a release build:
  • SANCHR_RELEASE_STORE_FILE     (absolute path to the .jks keystore)
  • SANCHR_RELEASE_STORE_PASSWORD
  • SANCHR_RELEASE_KEY_ALIAS
  • SANCHR_RELEASE_KEY_PASSWORD
See docs/android-release-signing.md for generation + rotation steps.
```

This is deliberate. Silent fallback to unsigned output (AGP's default
behaviour) gets missed in CI and leads to broken Play uploads later.

## 5. Record of record

Updated when the upload key is generated or rotated.

| Field | Value |
|---|---|
| Upload-key SHA-256 fingerprint | _TBD (record after §1 ceremony)_ |
| Keystore format | PKCS12 |
| Key algorithm / size | RSA / 4096 |
| Validity | 30 years from generation date |
| Generated on | _TBD (YYYY-MM-DD)_ |
| Play App Signing enrolled | _TBD_ |
| Last rotation | n/a |

## 6. Device matrix (populated during M6 Phase 6 manual smoke)

| Device | Android version | Release channel | Signer verified | Smoke pass |
|---|---|---|---|---|
| _TBD Pixel 7_ | _TBD_ | internal | ☐ | ☐ |
| _TBD Galaxy A14_ | _TBD_ | internal | ☐ | ☐ |

## 7. Play Console upload evidence (populated during M6 Phase 6)

| Release | versionCode | Track | Date | Screenshot |
|---|---|---|---|---|
| 1.0.0 | 1 | internal | _TBD_ | `docs/android/evidence/play-internal-1.png` |
