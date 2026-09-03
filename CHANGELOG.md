# Changelog

All notable changes to the Sanchr Android app are documented in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Bump `app/version.properties` and create a matching
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` in the same
commit that adds a release entry below.

## [Unreleased]

### Changed
- Protos resynced to backend `1305d39`: `GetUserProfiles`, `profile_key_version`, `current_pin`, `silent`; `profile_key` fields are reserved server-side.
- The stored theme mode (System / Light / Dark) is applied at the activity root; the launch window and splash follow it.
- Launcher icon is the navy composition shared with iOS; the in-app mark is transparent.

### Fixed
- Push token registration no longer overwrites the server device id (key bundles uploaded as device 0).
- An expired access token now refreshes once, in flight, on the first 401; the rotated refresh token is stored.
- Notification taps open the conversation or call; notifications use the brand status icon.

## [1.0.0] — 2026-04-24

Initial internal-track release (Milestone M6).

### Added
- End-to-end encrypted 1:1 text messaging against the Sanchr backend,
  interop-verified with the iOS client (`libsignal-client` 0.88.1 on both
  platforms, shared sealed-sender certificate format).
- Phone-number registration + SMS OTP flow; per-account password is
  generated client-side and persisted in `EncryptedSharedPreferences`.
- Chats list with search, pull-to-refresh backed by a background `SyncWorker`,
  and a "new chat by phone" lookup via the backend `LookupUser` RPC.
- Chat detail screen with typing indicators delivered via the realtime
  channel, read-receipt dispatch with random batching, and untrusted-identity
  failure surfacing.
- Reactive logout that wipes the SQLCipher database, staged identity
  material, session tokens, and the Keystore-wrapped DB passphrase before
  navigating back to the auth graph.

### Security
- SQLCipher at-rest encryption with AndroidKeystore-wrapped passphrase,
  zeroed after DB open.
- Sealed-sender messaging closes the social-graph metadata leak; paper-audit
  Phase 4 sub-phases 1-6 all landed pre-M6.
- R8 release minification configured with exhaustive keep-rules for Hilt,
  Room, Protobuf, gRPC, libsignal, SQLCipher, and WorkManager (see
  `app/proguard-rules.pro`).
- Release signing env-var-gated — no key material in the repository; see
  `docs/android/release-signing.md`.

### Known issues
- `:core:callengine` (WebRTC) is compiled in but not wired — 1:1 voice
  calling lands in M7. Release APK carries the library but no UI exposes it.
- AGP 8.7.3's bundled R8 emits benign `kotlin metadata parse` warnings on
  Kotlin-2.1-compiled classes; functional output is correct. Silenced in a
  post-M6 AGP bump.

[Unreleased]: https://github.com/zynclave/sanchr/compare/android-v1.0.0...HEAD
[1.0.0]: https://github.com/zynclave/sanchr/releases/tag/android-v1.0.0
