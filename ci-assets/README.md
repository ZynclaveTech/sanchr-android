# ci-assets

## Purpose

Static, checked-in assets needed by CI workflows so they can exercise
paths that otherwise require secrets (release signing in particular). The
only file here today is a dummy keystore; anything added later must be
documented in this README with the same explicit "NOT FOR PRODUCTION"
disclosure.

## `dummy-upload.jks`

A 2048-bit RSA self-signed keystore with a **public** passphrase. Used
exclusively by the `assemble-release` CI job to let `./gradlew
:app:assembleRelease` progress past the signing gate in pull-request
validation — proving that R8 minification, resource shrinking, and APK
packaging all succeed. The resulting APK is **never uploaded anywhere**
and is discarded at end of job.

| Field | Value |
|---|---|
| File | `dummy-upload.jks` |
| Format | PKCS12 |
| Alias | `ci-dummy` |
| Store & key password | `ci-dummy-passphrase-not-for-production` |
| Key algorithm | RSA 2048 |
| Validity | 10 years from 2026-04-24 |
| SHA-256 fingerprint | `98:81:A9:4A:B5:EF:40:95:43:AE:CC:C1:86:92:6A:83:F6:C4:5C:A1:80:FD:0C:4E:9A:CF:79:8A:24:35:DF:F2` |

## What this file MUST NEVER be used for

- Signing any build uploaded to Play Console (any track).
- Signing any build handed to testers outside CI.
- Signing any build installed on any developer device.
- Any context where the resulting signature meaningfully asserts identity.

The real release upload keystore is env-var-injected on the release
machine and in the GitHub Actions `release` environment — see
`docs/android/release-signing.md`.

## If this keystore is ever compromised

Nothing to revoke. Its compromise is assumed — the passphrase is in this
file. Regenerate if the dummy needs to rotate for any reason:

```bash
cd ci-assets
rm dummy-upload.jks
keytool -genkeypair -v \
  -keystore dummy-upload.jks \
  -storetype PKCS12 \
  -alias ci-dummy \
  -keyalg RSA -keysize 2048 \
  -validity 3650 \
  -storepass "ci-dummy-passphrase-not-for-production" \
  -keypass "ci-dummy-passphrase-not-for-production" \
  -dname "CN=Sanchr CI Dummy, O=NotForProduction, L=CI, ST=CI, C=IN"
```

Update the fingerprint in the table above in the same commit.
