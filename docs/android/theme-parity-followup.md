# Theme Parity Followup (Phase 6f-cont)

Phase 6f-1 landed targeted parity fixes in `:core:designsystem` for colors (light
+ dark tinted neutrals, secondary-text / border shades), typography (body Medium,
button 16sp SemiBold, displayMedium 36sp SemiBold), and shapes (added CornerCard
= 20dp mapped onto Material `large`). This document tracks the remaining iOS
theme gaps that were identified during the audit but deferred out of the ≤5-file
fix commit.

Every row cites the authoritative iOS source so the next pass can land without a
re-audit. Effort legend: S = <1h, M = 1-4h, L = >4h.

---

## 1. Afacad / Inter font assets missing

- **iOS ref**: `ios/Sanchr-iOS/SanchrShared/DesignSystem/Typography.swift` lines
  64-69 — uses `UIFont(name: "Afacad", ...)` with `.rounded` fallback.
- **Android state**: `AfacadFontFamily = FontFamily.SansSerif` and
  `InterFontFamily = FontFamily.SansSerif` (see `Type.kt` lines 38-39). Both are
  system sans-serif; the weight changes landed in Phase 6f-1 help, but the
  typeface itself does not match.
- **Fix**: Drop the 8 `.ttf` files (`afacad_{regular,medium,semibold,bold}`,
  `inter_{regular,medium,semibold,bold}`) into
  `core/designsystem/src/main/res/font/` and replace the two `FontFamily`
  declarations per the TODO already in `Type.kt`.
- **Effort**: M (requires licensing check + asset drop + verify on a low-RAM
  device).
- **Why deferred**: binary assets are out of scope for a code-parity pass and
  touch licensing.

## 2. `SanchrExportColors` dynamic system tokens

- **iOS ref**: `ios/.../DesignSystem/ExportComponents.swift` lines 29-39 —
  `background = .systemBackground`, `surface = .secondarySystemBackground`,
  `textPrimary = .label`, `line = .separator`.
- **Android state**: No equivalent of iOS's adaptive `UIColor.systemBackground`
  family; Android hardcodes light/dark values in `SanchrLightColorScheme` /
  `SanchrDarkColorScheme`.
- **Fix**: Acceptable divergence. iOS uses dynamic system colors for
  accessibility (Increase Contrast, Smart Invert); Android's `colorScheme`
  resolves via `darkTheme` param and is the idiomatic approach. Document — do
  not change.
- **Effort**: S (documentation only).
- **Why deferred**: not a bug; platform-idiomatic.

## 3. Shadow elevation parity

- **iOS ref**: `ios/.../DesignSystem/Shadows.swift` lines 11-20 — `CardShadow` =
  `black.opacity(0.06)` light / `black.opacity(0.3)` dark, radius 8, y = 2.
- **Android state**: Features use Material3 `Card` defaults (tonal elevation +
  Material shadow curve) which produce a visibly different falloff.
- **Fix**: Add `Modifier.sanchrCardShadow()` extension that wraps
  `Modifier.shadow(elevation = 8.dp, ambientColor, spotColor)` using parity
  alphas. Consider tying into `SanchrCard` in
  `core/designsystem/component/SanchrCard.kt`.
- **Effort**: M (1 new extension + audit `SanchrCard` + one consumer site).
- **Why deferred**: requires touching a consumer composable (≤5 file budget
  consumed by color/type/shape/plan-doc).

## 4. `cardRadius` rollout to consumer cards — DONE (Phase 6f-2)

- **iOS ref**: `ios/.../DesignSystem/ExportComponents.swift` line 9
  (`cardRadius = 20`).
- **Android state**: Phase 6f-1 exposed `SanchrShapeTokens.CornerCard` and
  pointed Material `Shapes.large` at it, so `Card(...)` without an explicit
  `shape` now matches iOS. Phase 6f-2 swept `feature/*/**/*.kt` for explicit
  card shapes that bypassed the default.
- **Audit result (Phase 6f-2)**: a single feature-side `Card` declared an
  explicit non-CornerCard shape with card semantics:
  - `feature/auth/.../LoginPhoneScreen.kt` line 242 — E2E security info card
    was `SanchrShapeTokens.CornerMedium` (12dp); swapped to
    `SanchrShapeTokens.CornerCard` (20dp) to match iOS LoginView.
- **Sites left untouched (verified non-card semantics)**:
  - `feature/auth/.../RegisterScreen.kt` lines 230, 289 — `OutlinedTextField`
    inputs; CornerMedium (12dp) is correct for fields.
  - `feature/auth/.../LoginPhoneScreen.kt` line 230 — `OutlinedTextField`
    input; CornerMedium correct.
  - `feature/chats/.../NewChatBottomSheet.kt` line 67 — `Surface` inline
    error pill inside a sheet; not a card.
  - `feature/chats/.../ChatDetailScreen.kt` line 544 — image message bubble;
    16dp matches the bubble radius family (`BubbleSent/Received` use 16dp),
    not the card system.
  - `feature/calls/.../ActiveCallScreen.kt` line 574 — "End-to-end encrypted"
    badge pill (`Surface`, not `Card`); chip-style affordance.
  - `feature/calls/.../ActiveCallScreen.kt` lines 542, 546 — local/remote
    video tiles at 12dp; not card surfaces.
  - `feature/settings/.../AppearanceScreen.kt` `ThemePreviewCard`,
    `ChatSettingsScreen.kt`, `EncryptionKeysScreen.kt` — small selection
    tiles / option chips at 12dp; intentionally not card-radius.
- **Effort**: M (delivered).

## 5. Tracking / letter-spacing specifics

- **iOS ref**: `ios/.../DesignSystem/Typography.swift` lines 157-159 —
  `conversationNameTracking = -0.5`, `sectionLabelTracking = +0.5`.
- **Android state**: Phase 6f-1 applied `-0.5.sp` letterSpacing to
  `titleMedium`. `sectionLabel` tracking (+0.5) is already on `labelMedium`.
  Gap: `conversationPreview` / `filterTab` / `chatListTitle` currently share
  generic bodyMedium/titleLarge slots without custom tracking. iOS treats these
  as distinct roles.
- **Fix**: Introduce a `SanchrTextStyles` object exposing Figma-exact named
  styles (`conversationPreview`, `filterTab`, `chatListTitle`) so consumers
  stop mapping onto generic Material slots. Mirrors the iOS
  `SanchrTypography.conversationPreview` pattern.
- **Effort**: L (new API surface + migrate call sites + tests).
- **Why deferred**: adds a new public API; needs design review.

## 6. Chat-specific adaptive colors not exposed as Material tokens

- **iOS ref**: `ios/.../DesignSystem/Colors.swift` lines 43-109 (chat bubbles,
  e2eeBanner, groupSender, previewText, statusOnline/Away/Offline, security
  event amber set).
- **Android state**: Raw color values exist (e.g. `SanchrCyan500`,
  `SanchrWarningLight`) but there is no named adaptive accessor like iOS's
  `Color.sanchrPreviewText(scheme)`. Features reconstruct these ad-hoc.
- **Fix**: Add a `SanchrChatColors` object (or composition local) providing
  light/dark-aware chat palette members that mirror the iOS
  `Color.sanchr*(scheme)` functions.
- **Effort**: M-L (12+ named tokens, each with light/dark resolution).
- **Why deferred**: large API addition, affects chat feature styling broadly.

## 7. Status bar + navigation bar tinting

- **iOS ref**: SwiftUI handles this implicitly via
  `.preferredColorScheme` (`Theme.swift` line 64) which tints system chrome.
- **Android state**: `Theme.kt` lines 45-52 only sets `statusBarColor` to
  `colorScheme.background`. Navigation bar color is not set, and
  `isAppearanceLightNavigationBars` is not toggled.
- **Fix**: In the `SideEffect` block, also set
  `window.navigationBarColor = colorScheme.background.toArgb()` and
  `controller.isAppearanceLightNavigationBars = !darkTheme`.
- **Effort**: S (4 lines + manual QA).
- **Why deferred**: needs on-device verification across API 24+ (some OEMs
  ignore nav bar tinting); safer as its own commit.

## 8. `SanchrGlass` modifier (iOS 26 liquid glass)

- **iOS ref**: `ios/.../DesignSystem/ExportComponents.swift` lines 41-66.
- **Android state**: No equivalent. Android 12+ has `RenderEffect.blur` which
  could approximate.
- **Fix**: Not recommended for parity. Liquid glass is an iOS-26 design
  language; mirroring it on Android violates Material guidelines.
- **Effort**: N/A.
- **Why deferred**: platform divergence is correct here.

---

## Suggested sequencing

1. Ticket 7 (status/nav bar, S) — lowest risk, ships alone.
2. Ticket 4 (cardRadius rollout, M) — DONE in Phase 6f-2.
3. Ticket 3 (shadow parity, M) — unlocks card visual parity fully.
4. Ticket 1 (font assets, M) — pending licensing.
5. Ticket 6 (chat color tokens, M-L) — coordinate with chat feature refactor.
6. Ticket 5 (named text styles, L) — largest API addition; save for dedicated
   phase.

Tickets 2 and 8 are document-only (no code change).
