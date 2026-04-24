# Android Security Checklist — v1.0.0 Internal Release

Date: 2026-04-24
Scope: M6 Phase 6.1 hardening review for the Sanchr Android client
(`android/sanchr-android/`). This is an evidence-backed sign-off document: each
item links to concrete source locations. Unchecked items are gaps that must be
closed before the v1.0.0 internal release, or explicitly risk-accepted by the
release manager.

## BLOCKERS (must fix before internal release)

- **FLAG_SECURE is not actually applied anywhere.** The Security settings
  screen advertises a "Screenshot Protection" toggle
  (`feature/settings/.../SecurityScreen.kt:145`, backed by
  `SettingsViewModel.setScreenshotProtection` at
  `feature/settings/.../SettingsViewModel.kt:359`), but there is **no call to
  `window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)` anywhere in the
  codebase** (confirmed by `rg "FLAG_SECURE|WindowManager.LayoutParams|setSecure"`
  returning only the UI subtitle string). OTP entry
  (`feature/auth/.../OtpScreen.kt`) and recovery-key screens are therefore
  **unprotected against screenshots and the recents-screen preview**. User
  toggle updates a StateFlow that nothing consumes at the Activity layer.
  Action: wire the toggle (and unconditionally for OTP + recovery-key screens)
  into `MainActivity.onCreate` / a `DisposableEffect` in the screen composables.

- **(RESOLVED) SQLCipher passphrase is never zeroed in memory.**
  `DatabasePassphraseProvider` now exposes only a block-scoped
  `withPassphrase { bytes -> ... }` API which zero-fills the plaintext buffer
  in a `finally` clause. `DatabaseModule.provideSanchrDatabase`
  (`core/database/.../SanchrDatabase.kt`) uses it to hand a `copyOf()` to
  `SupportOpenHelperFactory`. The factory-retained copy is the single
  unavoidable live plaintext (sqlcipher-android 4.6.1 stores the byte[] by
  reference and reuses it across WAL checkpoint reopens; zeroing it breaks
  the connection pool). No `clearPassphrase` ctor parameter is available in
  this SQLCipher version.

## Checklist

- [ ] **1. libsignal version pinned to the exact iOS version.**
  Android pins `libsignal = "0.88.1"` at
  `gradle/libs.versions.toml:45`. iOS, however, does **not** consume libsignal
  as a Swift package — it vendors the source + prebuilt `.a` under
  `ios/Sanchr-iOS/Vendor/LibSignal/` and references it in `project.yml:61-74`.
  `ios/Sanchr-iOS/Package.resolved` contains no `libsignal` / `LibSignalClient`
  entry, and the vendor directory has no `VERSION` file. Equivalence with
  Android's 0.88.1 cannot be verified from the checked-in artifacts.
  Follow-up: add `ios/Sanchr-iOS/Vendor/LibSignal/VERSION` pinning the upstream
  Signal release tag, and wire a CI check that asserts it matches
  `libs.versions.toml`'s `libsignal` version.

- [ ] **2. Sealed-sender cert format verified by golden fixture.**
  No `core/crypto/src/test/resources/` directory exists; `rg -i
  "golden|getResourceAsStream"` in `core/crypto/` returns nothing.
  `SealedSenderInteropContractTest`
  (`core/crypto/src/androidTest/java/com/sanchr/core/crypto/SealedSenderInteropContractTest.kt:32`)
  pulls fixtures from `BuildConfig.IOS_TEST_FIXTURE_*` Gradle properties and
  skips via `assumeTrue` when they are unset — which is the default for every
  local and CI build. There is no hermetic golden fixture checked in.
  Follow-up: generate a fixed iOS-produced sealed envelope + sender
  certificate, commit under `core/crypto/src/androidTest/assets/sealed-sender/`,
  and add a dedicated test that decrypts it without Gradle-property gating.

- [x] **3. At-rest: SQLCipher is the openHelperFactory for the message DB.**
  `core/database/.../SanchrDatabase.kt:40` imports
  `net.zetetic.database.sqlcipher.SupportOpenHelperFactory`; it is wired into
  the Room builder at `SanchrDatabase.kt:263`
  (`.openHelperFactory(...)` inside `passphraseProvider.withPassphrase { ... }`).
  Native lib is loaded via `System.loadLibrary("sqlcipher")` at
  `SanchrDatabase.kt:119`. Dependency declared in
  `core/database/build.gradle.kts:45` (`libs.sqlcipher.android`, pinned to
  `4.6.1` in `libs.versions.toml:46`).

- [x] **4. DB passphrase is Keystore-wrapped AND zeroed after use.**
  Wrapping is correct: AES-256-GCM under an AndroidKeyStore key, StrongBox
  preferred with TEE fallback, `setUnlockedDeviceRequired(true)` on P+
  (`core/database/.../DatabasePassphraseProvider.kt`). Wrapped blob stored in
  `EncryptedSharedPreferences`. Zeroing: the provider now exposes only
  `withPassphrase { ... }`, which `.fill(0)`-s the plaintext buffer in a
  `finally` clause. The only residual plaintext is the reference held
  internally by `SupportOpenHelperFactory` (documented upstream limitation of
  sqlcipher-android 4.6.1).

- [x] **5. Logout order is audited and each step is independent.**
  `domain/messaging/.../LogoutUseCase.kt:43-58` runs five steps under
  `runCatching` in the order: (1) `sessionManager.clearSession()` — flips
  `sessionActive` to false so UI navigates away and collectors cancel before
  DB teardown; (2) `stagedIdentityStore.clear()`; (3) `database.close()`;
  (4) `context.deleteDatabase("sanchr-database")` including `-journal` /
  `-wal`; (5) `databasePassphraseProvider.wipe()`. Rationale is documented in
  the KDoc block at `LogoutUseCase.kt:14-31`.

- [ ] **6. Session tokens in EncryptedSharedPreferences AND zeroed on logout.**
  Storage is correct: `SessionManager`
  (`core/datastore/.../SessionManager.kt:54-62`) uses
  `EncryptedSharedPreferences` with `AES256_SIV` keys + `AES256_GCM` values,
  backed by an AndroidKeyStore `MasterKey`. `clearSession()`
  (`SessionManager.kt:277-290`) removes `access_token`, `refresh_token`,
  `user_id`, `device_id`, `token_expiry`, `installation_id`, `account_password`,
  and `display_name` from the encrypted prefs.
  However, token *strings* returned by `getAccessToken()` /
  `getRefreshToken()` (`SessionManager.kt:73-76`) are standard `String`
  instances that callers cannot zero; no `char[]`/`ByteArray` API is exposed.
  `clearSession()` also does not call `clearSenderCertificate()`,
  `clearDeviceSecrets()`, or `clearBackupMaterial()` — those are separate
  methods. Not a plaintext leak on-disk (still EncryptedSharedPrefs), but
  in-memory token lifetime is not bounded.
  Follow-up: expose a `ByteArray` accessor for tokens and zero after use, OR
  explicitly risk-accept given that heap dumps on a locked device still
  require a keystore-gated unlock.

- [ ] **7. FLAG_SECURE on OTP + recovery-key screens.**
  See BLOCKERS. Grep for `FLAG_SECURE` returns one hit — a user-facing
  description string at `feature/settings/.../SecurityScreen.kt:145`. Zero
  call-sites apply the flag to a `Window`.

- [x] **8. ProGuard enabled + verified.**
  `app/build.gradle.kts:90-103` sets `isMinifyEnabled = true` and
  `isShrinkResources = true` for the `release` build type, with
  `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
  "proguard-rules.pro")`. Module keep rules live in
  `app/proguard-rules.pro` (header comment at lines 1-18 documents the scope
  decision for M6 Phase 2). CI assembles a release APK with the dummy
  keystore on every PR (`.github/workflows/android-ci.yml:69-94`), which
  exercises R8 end-to-end.

- [x] **9. Release signed with v1+v2+v3.**
  `app/build.gradle.kts:77-79` explicitly sets
  `enableV1Signing = true / enableV2Signing = true / enableV3Signing = true`
  on the `release` signing config. Key material is loaded from the
  `SANCHR_RELEASE_STORE_FILE` / `..._STORE_PASSWORD` / `..._KEY_ALIAS` /
  `..._KEY_PASSWORD` environment variables (`app/build.gradle.kts:62-65`). A
  guard task (`app/build.gradle.kts:214-243`) fails `assembleRelease` /
  `bundleRelease` / `packageRelease` with a pointer to
  `docs/android/release-signing.md` if any of the four vars is blank, closing
  the silent-unsigned-APK failure mode.

- [x] **10. `android:allowBackup="true"` is absent.**
  `app/src/main/AndroidManifest.xml:36` explicitly sets
  `android:allowBackup="false"`. No other manifest in the repo overrides it
  (confirmed via `rg allowBackup`).

- [x] **11. `android:usesCleartextTraffic` is not set.**
  `rg usesCleartextTraffic` returns no hits in
  `app/src/main/AndroidManifest.xml` or any library manifest — the attribute
  defaults to `false` on `targetSdk=35`
  (`app/build.gradle.kts:47`), and cleartext is further denied by the
  network-security-config (see item 12).

- [x] **12. Network security config is explicit.**
  `app/src/main/AndroidManifest.xml:42` sets
  `android:networkSecurityConfig="@xml/network_security_config"`. The config
  file (`app/src/main/res/xml/network_security_config.xml`) denies cleartext
  for `sanchr.com` + `api.sanchr.com` (`includeSubdomains="true"`) and for the
  `base-config`, trusts only the `system` store, and restricts the
  user-installed CA relaxation to `debug-overrides`. No production cleartext
  path.

- [x] **13. No third-party analytics SDKs in the release build.**
  `rg -i "firebase-analytics|mixpanel|segment|amplitude|crashlytics|firebase-perf"`
  across `**/*.gradle.kts` and `gradle/libs.versions.toml` returns zero
  dependency declarations. The only `crashlytics` reference is a prose mention
  inside `app/proguard-rules.pro:22` about keeping stack-trace attributes —
  not a dependency. Firebase is limited to `firebase-bom` + `firebase-messaging`
  (FCM, required for push) at `libs.versions.toml:28` and
  `app/build.gradle.kts:184-185`.

- [x] **14. Keystore handling: env-var-only in CI; no private key material
  checked in.**
  PR-validation jobs sign with the dummy keystore at
  `.github/workflows/android-ci.yml:91-94`, whose purpose and non-production
  scope are documented in `ci-assets/README.md` (including the explicit
  "What this file MUST NEVER be used for" section and the public passphrase).
  The real release keystore is injected via the four `SANCHR_RELEASE_*` env
  vars in the GitHub Actions `release` environment (referenced by
  `app/build.gradle.kts:62-80`) and documented in
  `docs/android/release-signing.md`. No production `.jks` or `.p12` is
  checked in.

## Summary

- **Checked:** 8 / 14 items (3, 5, 8, 9, 10, 11, 12, 13, 14 — nine actually).
- **Unchecked:** 6 items (1, 2, 4, 6, 7). Two are BLOCKERS (7 — FLAG_SECURE
  not wired; 4 — passphrase not zeroed). The remaining four are release-gate
  follow-ups.
- **BLOCKERS:** 2 (see top of document).
