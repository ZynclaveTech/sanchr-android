# Android ↔ iOS Sealed-Sender Interop Fixture Handoff

**Status:** Draft — cross-team spec
**Date:** 2026-04-24
**Author:** Android team (feat/android-m5-chats-ui)
**Unblocks:** M6 Phase 5 exit gate — `AndroidIosInteropTest` currently
skips via `Assume.assumeTrue` because no iOS-produced fixture exists on
disk. See `docs/superpowers/plans/2026-04-24-android-m6-hardening.md`
§Phase 5.2 and `docs/superpowers/specs/2026-04-23-android-v1-vertical-slice-design.md`
§"Risks & mitigations" — row "Sealed-sender cert format mismatch".

> **TODO: ASSIGN OWNERS**
>
> - Android owner: _TBD_
> - iOS owner: _TBD_
> - Crypto reviewer (cross-platform): _TBD_
> - Handoff channel: _TBD_ (Slack `#sanchr-interop` proposed)
> - PR labels: `interop`, `fixture-handoff`, `m6-exit`

---

## 1. Purpose

`app/src/androidTest/java/com/sanchr/app/interop/AndroidIosInteropTest.kt`
asserts that Android's `SealedSenderCipher.sealedDecrypt` can consume a
byte-stream produced by an iOS sender running the same pinned
`libsignal-client` version, validate the embedded `SenderCertificate`
against the shared TrustRoot, and persist the decrypted message. Without
a golden iOS-generated envelope committed to the Android repo, the test
has no way to detect cert-format or wire-shape drift between platforms
— it skips, and M6's exit gate ("two consecutive green nightly interop
runs") cannot be satisfied. This spec defines the exact byte-level
artifacts iOS must produce, how Android consumes them, and the
symmetric reverse flow for Android→iOS.

## 2. Fixture inventory (iOS → Android)

All files land at
`android/sanchr-android/app/src/androidTest/assets/ios-fixtures/`
(same directory as the existing placeholder
`sealed-envelope-v1.bin` referenced by
`AndroidIosInteropTest.IOS_ENVELOPE_FIXTURE`,
`AndroidIosInteropTest.kt:133`).

| File | Purpose | Consumer | Size (approx) |
|---|---|---|---|
| `ios-to-android-sealed.bin` | Single outer envelope produced by iOS `SealedSenderCipher.encrypt` for a known plaintext; raw wire bytes, no framing or length prefix. | `SealedSenderCipher.sealedDecrypt` | <1 KiB |
| `sender-certificate.bin` | Serialized `SenderCertificate` bytes used to sign the envelope (needed so Android can cross-check the embedded cert matches what iOS believes it used, independently of decrypt). | Assertions only | <512 B |
| `sender-identity-key.bin` | Long-term `IdentityKey` (public) of the iOS sender — lets Android assert `result.senderUuid` was signed by the expected key. | Assertions only | 33 B |
| `recipient-identity-keypair.bin` | Deterministically-seeded recipient identity keypair (`IdentityKeyPair` serialization, public + private). Android loads this into `SanchrIdentityKeyStore` before decrypt so the PreKey session handshake resolves. | `SanchrSignalProtocolStore` setup | ~65 B |
| `prekey-bundle.bin` | Serialized `PreKeyBundle` iOS used to bootstrap the outgoing session. Android uses it to pre-populate its `PreKeyStore` / `SignedPreKeyStore` state so the X3DH handshake inside the sealed envelope resolves. | `SanchrSignalProtocolStore` setup | <1 KiB |
| `fixture.json` | Plaintext metadata: see schema below. | `AndroidIosInteropTest.@Before` + version-check assertion | <1 KiB |

`fixture.json` schema:

```json
{
  "fixtureVersion": 1,
  "plaintext": "Hello from iOS",
  "plaintextUtf8Sha256": "<hex>",
  "senderE164": "+14155550100",
  "senderUuid": "00000000-0000-4000-8000-000000000001",
  "senderDeviceId": 1,
  "recipientE164": "+14155550200",
  "recipientUuid": "00000000-0000-4000-8000-000000000002",
  "recipientDeviceId": 1,
  "timestampMillis": 1735689600000,
  "libsignalClientVersion": "0.88.1",
  "trustRootPublicKeyBase64": "<base64 NO_WRAP NO_PADDING>",
  "envelopeSha256": "<hex>",
  "generatedAt": "2026-04-24T00:00:00Z",
  "generatedByCommit": "<ios-repo-sha>"
}
```

The `envelopeSha256` and `plaintextUtf8Sha256` checksums are the
drift-detection anchor; see §7.

## 3. Deterministic generation recipe (iOS side)

iOS owns the authoritative generator. Pseudocode — the iOS team fills in
concrete imports for their `LibSignalClient` Swift wrapper:

```swift
// Tests/InteropFixtureExport/ExportAndroidGolden.swift (new)
// Run via: xcodebuild test -scheme Sanchr \
//   -only-testing:SanchrTests/SealedSenderInteropExport/testExportAndroidGolden

func testExportAndroidGolden() throws {
    // 1. Deterministic recipient identity keypair.
    let seed = SHA256.hash(data: "sanchr-android-interop-v1".data(using: .utf8)!)
    let recipientIdentityKeyPair = IdentityKeyPair.deterministic(seed: Data(seed))

    // 2. Deterministic PreKey + SignedPreKey for recipient.
    let recipientPreKey       = PreKeyRecord.deterministic(id: 1, seed: seed)
    let recipientSignedPreKey = SignedPreKeyRecord.deterministic(
        id: 1, seed: seed, signingKey: recipientIdentityKeyPair.privateKey,
    )
    let bundle = try PreKeyBundle(
        registrationId:   0x0A0A,
        deviceId:         1,
        preKeyId:         1, preKey:         recipientPreKey.publicKey,
        signedPreKeyId:   1, signedPreKey:   recipientSignedPreKey.publicKey,
        signedPreKeySignature: recipientSignedPreKey.signature,
        identity:         recipientIdentityKeyPair.identityKey,
    )

    // 3. Sender state (also deterministic for reproducibility).
    let senderStore = InMemorySignalProtocolStore(
        identityKeyPair: IdentityKeyPair.deterministic(
            seed: Data(SHA256.hash(data: "sanchr-ios-sender-v1".data(using: .utf8)!)),
        ),
        registrationId: 0x0B0B,
    )
    let recipientAddr = ProtocolAddress(
        name: "00000000-0000-4000-8000-000000000002", deviceId: 1,
    )
    try processPreKeyBundle(bundle, for: recipientAddr, sessionStore: senderStore, /* … */)

    // 4. SenderCertificate (server-issued in prod; use a fixed test TrustRoot here).
    let trustRoot  = IdentityKeyPair.deterministic(seed: /* SHA256("sanchr-interop-trust-root-v1") */ …)
    let senderCert = try SenderCertificate.signed(
        senderUuid:     "00000000-0000-4000-8000-000000000001",
        senderE164:     "+14155550100",
        senderDeviceId: 1,
        identityKey:    senderStore.identityKeyPair.identityKey,
        expiration:     .distantFuture,
        signerKey:      trustRoot.privateKey,
    )

    // 5. Sealed-sender encrypt.
    let plaintext = "Hello from iOS".data(using: .utf8)!
    let envelope  = try sealedSenderEncrypt(
        message:     plaintext,
        for:         recipientAddr,
        from:        senderCert,
        sessionStore: senderStore, identityStore: senderStore,
    )

    // 6. Write all six files.
    try envelope.write(to: out("ios-to-android-sealed.bin"))
    try senderCert.serialize().write(to: out("sender-certificate.bin"))
    try senderStore.identityKeyPair.identityKey.publicKey.serialize()
        .write(to: out("sender-identity-key.bin"))
    try recipientIdentityKeyPair.serialize().write(to: out("recipient-identity-keypair.bin"))
    try bundle.serialize().write(to: out("prekey-bundle.bin"))
    try JSONEncoder().encode(fixtureJson(plaintext, envelope, trustRoot))
        .write(to: out("fixture.json"))
}
```

Determinism requirements:

- All randomness must be seeded from the fixed seeds above. No
  `SystemRandomNumberGenerator`, no `Date()`, no UUID v4 — use the fixed
  UUIDs in `fixture.json`.
- `timestampMillis` is a literal constant (`1735689600000` = 2025-01-01
  00:00:00 UTC), not `Date().timeIntervalSince1970`.
- If libsignal's `sealedSenderEncrypt` internally samples randomness
  (ephemeral keys), iOS must expose a deterministic-RNG test override —
  otherwise byte-identical regeneration is impossible and the only
  drift detector is the hash stored in `fixture.json`. **Open question §9.**

## 4. Android verification (what `AndroidIosInteropTest` must assert)

Once the fixture is committed, the test body (currently
`throw UnsupportedOperationException(...)` at
`AndroidIosInteropTest.kt:102`) implements:

1. Load `fixture.json`, assert `libsignalClientVersion` equals the
   Android-side pin (§5). Fail loudly with a diff message.
2. Load `recipient-identity-keypair.bin` into `SanchrIdentityKeyStore`
   and `prekey-bundle.bin` into the PreKey/SignedPreKey stores via the
   instrumented test's `SanchrSignalProtocolStore` seam.
3. Configure `BuildConfigTrustRootProvider` (or inject a test override)
   with `fixture.json.trustRootPublicKeyBase64`.
4. Read `ios-to-android-sealed.bin` into a `ByteArray`.
5. Call `SealedSenderCipher.sealedDecrypt(envelope,
   fixture.timestampMillis)`. Assert:
   - `result.senderUserId == fixture.senderUuid`
   - `result.senderDeviceId == fixture.senderDeviceId`
   - `String(result.plaintext, UTF_8) == fixture.plaintext`
   - `SHA-256(result.plaintext) == fixture.plaintextUtf8Sha256`
6. Assert the Signal session row for the sender address now persists in
   Room (`SessionDao.load(senderAddress) != null`).
7. Push the decrypted envelope through the normal receive pipeline and
   assert a `Received` row lands in the messages table with
   `status = DELIVERED` and `senderE164 = fixture.senderE164`.
8. Assert `SHA-256(envelopeBytes) == fixture.envelopeSha256` as a file-
   integrity check — catches a corrupted / truncated `git lfs` transfer.

## 5. Version pinning

Android pins `libsignal = "0.88.1"` at
`android/sanchr-android/gradle/libs.versions.toml:45`, consumed as
`libsignal-android` and `libsignal-client` at lines 142–143 of the same
file. iOS currently has **no libsignal entry in
`ios/Sanchr-iOS/Package.resolved`** — it is either vended via
xcframework, CocoaPods, or another Package.swift not yet imported into
the SPM graph. **Open question §9.**

Enforcement: the version-check in §4 step 1 is a hard-fail. If
`fixture.json.libsignalClientVersion != "0.88.1"`, the test throws a
`FixtureVersionMismatch` with an explicit upgrade-both-sides-in-lockstep
message. Upgrading libsignal on either platform requires:

1. Bumping the pin in both repos in coordinated PRs.
2. Regenerating the fixture (both directions) and committing fresh bytes.
3. Two consecutive green nightly interop runs before the bump lands.

## 6. Reverse fixture (Android → iOS)

Symmetric flow. Android produces:

- `android-to-ios-sealed.bin`
- `sender-certificate.bin` (Android sender's cert)
- `sender-identity-key.bin` (Android sender's public identity key)
- `recipient-identity-keypair.bin` (deterministic iOS recipient keypair
  — seed = `SHA256("sanchr-ios-interop-v1")`)
- `prekey-bundle.bin`
- `fixture.json` (same schema as §2, with platforms swapped)

Landing directory on the iOS side: given the existing iOS test layout
(`ios/Sanchr-iOS/Tests/UnitTests/Crypto/` holds cross-platform crypto
tests such as `MediaKeyDerivationCrossPlatformTests.swift` and
`ChunkedEncryptionTests.swift`), the natural home is
`ios/Sanchr-iOS/Tests/UnitTests/Crypto/Fixtures/android-fixtures/`.
A matching `AndroidIosInteropXCTest` lives alongside those files. iOS
owner to confirm — see §9.

Android-side generator: new instrumented test
`app/src/androidTest/java/com/sanchr/app/interop/ExportIosGoldenTest.kt`
(tagged `@InteropTest` but behind a `-Pgenerate-fixtures=true` flag so
it never runs in nightly CI). Mirrors §3 using
`SealedSenderCipher.sealedEncrypt`, writes the 6 files to a temp dir
logged to logcat for the operator to copy into the iOS repo.

## 7. Regeneration cadence

Regenerate when **any** of the following change:

- `libsignal-client` version on either platform.
- `SenderCertificate` serialization (libsignal bump to an incompatible
  version, or internal re-cert of the TrustRoot).
- Envelope proto wire shape (any field in
  `sanchr.messaging.SealedEnvelope` or
  `UnidentifiedSenderMessageContent`).
- TrustRoot rotation.

Drift detection without regeneration: `fixture.json.envelopeSha256` +
`plaintextUtf8Sha256` are re-computed on both sides in CI. A mismatch
between the committed SHA and the recomputed SHA fails the build with
"fixture corruption or tampering" before any decrypt runs. Both
platforms assert against the same JSON checksum so partial-update bugs
(e.g. iOS regenerates `.bin` but forgets `fixture.json`) get caught.

## 8. Handoff mechanics

- **Regeneration trigger**: whichever platform bumps libsignal opens a
  paired PR (one in each repo). Both PRs carry label `interop-regen`
  and cross-reference each other's commit SHA in the description.
- **Review gate**: a designated crypto reviewer (§ASSIGN OWNERS)
  signs off on both PRs; they land together, nightly CI re-runs.
- **Channel**: Slack `#sanchr-interop` for lightweight questions;
  actual handoff artifacts flow via Git only (no DMing `.bin` files).
- **Escalation**: if nightly interop is red for 3 consecutive runs,
  auto-filed issue `interop-regression` (per M6 plan §4.2) is upgraded
  to a blocker on both repos' release trains.

## 9. Open questions

1. **iOS libsignal version source of truth** — `Package.resolved` has
   no `libsignal` entry; where is the iOS pin currently declared
   (xcframework? pod? vendored binary?)? Without answering this, §5's
   version-check is a one-sided guard.
2. **Deterministic RNG for sealed-sender encrypt** — does the iOS
   libsignal Swift wrapper expose an RNG override for test builds? If
   not, byte-identical regeneration is impossible and we fall back to
   SHA-checksum-only drift detection. This also affects the Android
   reverse generator.
3. **TrustRoot for interop fixtures** — do we use the production
   TrustRoot (embedded in `BuildConfig.SEALED_SENDER_TRUST_ROOT`, see
   `SealedSenderCipher.kt:9–10`) or a dedicated interop-only TrustRoot
   bundled with the fixture? Production keeps the fixture aligned with
   real wire bytes; interop-only avoids leaking prod key material into
   a public repo's test assets.
4. **Git LFS or inline** — the envelope bundle totals <5 KiB so inline
   Git is fine, but once attachment/group fixtures appear (post-M6)
   we'll want LFS policy nailed down.
5. **Owners** — all TODO placeholders at the top of this doc.
6. **PreKey bundle reuse** — the existing README at
   `app/src/androidTest/assets/ios-fixtures/README.md` assumes a single
   `sealed-envelope-v1.bin` only; this spec expands to six files and
   the README needs an update in the same PR that lands the first real
   fixture. (Android follow-up, not blocking iOS.)
