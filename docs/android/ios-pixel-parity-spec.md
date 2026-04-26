# Android — iOS Pixel-Perfect Parity Spec

**Date:** 2026-04-25
**Branch:** `feat/android-auth-onboarding-realignment` (PR #1)
**Scope:** Login + 4 onboarding screens. Every iOS visual token gets an Android equivalent.
**Approach:** Hybrid (foundation → architectural → components → per-screen polish). 7 phases.

---

## §1 Goals + non-goals

### Goals
- Pixel-perfect visual parity with iOS for the unauthenticated flow: `LoginView`, `OnboardingNameStepView` (STEP 1), `OnboardingAvatarStepView` (STEP 2), `OnboardingContactSyncStepView` (STEP 3), `OnboardingWelcomeStepView` (post-step "YOU'RE ALL SET").
- Architectural collapse of Android's Login/Register split into a single phone-only entry, matching iOS's `LoginView` (which acts for both new and returning users via the backend's existing-phone short-circuit).
- Adopt Afacad as the visible font family across all five screens.
- Each phase lands a green `./gradlew check` and is reviewable on its own; ≤5 files per phase.

### Non-goals
- Inter font (used elsewhere in iOS, not in scope here).
- iOS 26 Liquid Glass effects (not portable to Android idiomatically; deferred indefinitely).
- Reskinning the post-auth chats/settings screens (separate milestone).
- Backend changes (none required — `Register` handler at `backend/crates/sanchr-core/src/auth/handlers.rs:244-293` already supports the unified flow).
- Dynamic Type / accessibility text scaling beyond what Compose gives by default (separate accessibility pass).

---

## §2 Phased plan

Each phase is a separate commit, ≤5 files, gated on `./gradlew check` green and pushed to `feat/android-auth-onboarding-realignment` (PR #1).

| # | Title | Files | Visible |
|---|---|---|---|
| **H1** | Foundation: Afacad + tokens | font asset + `Type.kt` + `Color.kt` + `Spacing.kt` + `Theme.kt` | No |
| **H2** | Architectural: drop Sign up + Register screen | `AuthState.kt` + `AuthViewModel.kt` + `AuthNavigation.kt` + `LoginPhoneScreen.kt` + `AuthViewModelStateTest.kt` (delete `RegisterScreen.kt` in same commit) | Sign up gone, single phone entry |
| **H3** | Components in `:core:designsystem` | `SanchrGradientButton.kt` (new) + `SanchrStepEyebrow.kt` (new) + `SanchrOnboardingProgress.kt` (new) + Preview file (optional) | No |
| **H4** | LoginPhone visual polish | `LoginPhoneScreen.kt` + (helper extraction if needed) | Login matches iOS |
| **H5** | OnboardingWelcome + OnboardingName | `OnboardingWelcomeScreen.kt` + `OnboardingNameScreen.kt` + `OnboardingViewModel.kt` (only if state changes needed) | Both screens match iOS |
| **H6** | OnboardingAvatar | `OnboardingAvatarScreen.kt` | Avatar matches iOS |
| **H7** | OnboardingContactSync | `OnboardingContactSyncScreen.kt` | Sync matches iOS — spec exit |

### Per-phase exit gates
- `./gradlew :feature:auth:check` (or relevant module) green.
- `./gradlew check` full green, zero `-x` exclusions.
- Re-read each touched file twice (CLAUDE.md #9).
- iOS citation in KDoc for every new file or significant edit.

---

## §3 Architectural changes (Phase H2)

### 3.1 Final flow
```
Splash (500ms + fastLoginIfPossible)
  → if cached creds work: Main
  → else: LoginPhone (phone only, no Sign up link)
    → submit: Register(phone, displayName="", BOOTSTRAP_PASSWORD)
      → backend short-circuits if phone exists; OTP issued either way
    → OtpEntry → submitOtp
      → if hasCompletedProfileBasics (server returned displayName): Main
      → else: Onboarding (Name → Avatar → ContactSync → Welcome) → Main
```

### 3.2 `AuthState.kt` after H2
```kotlin
sealed interface AuthState {
    data object Splash : AuthState
    data class LoginPhone(val countryCode: String = "+1", val phone: String = "", val isSubmitting: Boolean = false) : AuthState
    data class OtpEntry(val phoneE164: String, val displayName: String, val otp: String = "", val isSubmitting: Boolean = false) : AuthState
    data class Registering(val step: RegistrationStep, val phoneE164: String, val displayName: String, val userId: String, val deviceId: Int) : AuthState
    data class Done(val isNewUser: Boolean) : AuthState
    data class Error(val previousState: AuthState, val message: String) : AuthState
}
```
Removed: `RegisterPhoneAndName`. The `OtpEntry.displayName` will always be `""` since iOS-parity puts name collection in onboarding, but the field is kept on the state for backward compat with the `submitOtp` success path.

### 3.3 `AuthViewModel.kt` changes
- Delete `onRegisterChanged`, `submitRegister`, `switchToLogin`, `switchToRegister`.
- `submitLoginPhone` now sends `displayName = ""` (was empty on login path, matched current behavior).
- `submitOtp` checks `response.user?.displayName` — if non-blank, treat as returning user and emit `Done(isNewUser = false)`; if blank, emit `Done(isNewUser = true)`.

### 3.4 `AuthNavigation.kt` changes
- Remove `REGISTER_ROUTE` constant + composable + `RouteTarget` arm.
- Remove `RegisterScreen` import.
- `AuthFlowHost.RouteTarget` exhaustive `when(state)` simplifies to: `Splash → SPLASH_ROUTE`, `LoginPhone → LOGIN_PHONE_ROUTE`, `OtpEntry → OTP_ROUTE`, `Registering → null (overlay)`, `Done → onAuthSuccess()`, `Error → null (inline)`.

### 3.5 `LoginPhoneScreen.kt` changes
- Remove the "New to Sanchr? Sign up" footer link (the entire `SanchrTextButton` block).
- Privacy line becomes the only footer item.

### 3.6 Test impact
`AuthViewModelStateTest.kt`:
- Delete: `chooseRegister*`, `switchToRegister*`, `switchToLogin*`, `submitRegister_*` (4-6 tests).
- Update: `submitOtp_validCode_*` to verify `Done(isNewUser = ...)` emission.
- Add: `submitOtp_blankDisplayName_emitsIsNewUserTrue`, `submitOtp_serverDisplayName_emitsIsNewUserFalse`.
Net: ~14 → ~12 tests.

---

## §4 Foundation tokens (Phase H1)

### 4.1 Font wiring

**Asset:** copy `ios/Sanchr-iOS/Resources/Fonts/Afacad-Variable.ttf` → `core/designsystem/src/main/res/font/afacad_variable.ttf` (117KB, no dep change). Variable font supplies Regular(400)/Medium(500)/SemiBold(600)/Bold(700) via single file. Compose 1.6+ supports variable fonts via `FontFamily(Font(R.font.afacad_variable, weight=FontWeight.Normal, variationSettings=FontVariation.Settings(FontVariation.weight(N))))`.

`Type.kt` adds:
```kotlin
private val Afacad = FontFamily(
    Font(R.font.afacad_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.afacad_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.afacad_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.afacad_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
```

Every existing `MaterialTheme.typography` style replaces `fontFamily = FontFamily.SansSerif` with `fontFamily = Afacad`. **No size/weight changes** (those landed in Phase 6f-1 commit `fd9ea55`).

### 4.2 New typography slots — iOS → Android mapping

| iOS token | iOS spec | Android slot | Android spec |
|---|---|---|---|
| `heroTitle` | 48pt bold | `displayLarge` | 48sp Bold Afacad |
| `displayTitle` | 36pt semibold | `displayMedium` | 36sp SemiBold Afacad ✅ existing |
| `screenTitle` | 30pt semibold | `displaySmall` | 30sp SemiBold Afacad ✅ existing |
| `sectionHeader` | 24pt semibold | `headlineMedium` | 24sp SemiBold Afacad |
| `cardTitle` | 20pt semibold | `headlineSmall` (or new `cardTitle`) | 20sp SemiBold Afacad |
| `bodyLarge` | 18pt medium | `bodyLarge` | 18sp Medium Afacad |
| `body` | 16pt medium | `bodyMedium` | 16sp Medium Afacad ✅ existing |
| `bodyBold` | 16pt semibold | `labelLarge` | 16sp SemiBold Afacad ✅ existing |
| `caption` | 14pt regular | `bodySmall` | 14sp Normal Afacad |
| `captionSmall` | 12pt regular | `labelSmall` | 12sp Normal Afacad |
| `micro` | 10pt regular | `labelSmall` (smaller) — OR new `microEyebrow` token | 10sp Normal Afacad, 2.5sp letterSpacing |
| `button` | 16pt semibold | `labelLarge` | (same as bodyBold) |

`microEyebrow` is new and gets a dedicated token (used by `SanchrStepEyebrow`).

### 4.3 New color tokens

| iOS export name | iOS resolves to | Android token | Light hex | Dark hex |
|---|---|---|---|---|
| `surface` | `.secondarySystemBackground` | `SanchrSurface` | `#F2F2F7` | `#1C1C1E` |
| `surfaceMuted` | `.tertiarySystemFill` | `SanchrSurfaceMuted` | `#76768033` | `#7676805C` |
| `surfaceSoft` | `.systemGroupedBackground` | `SanchrSurfaceSoft` | `#F2F2F7` | `#000000` |
| `line` | `.separator` | `SanchrLine` | `#3C3C434A` | `#54545899` |

These are added to `Color.kt` + the `lightColorScheme` / `darkColorScheme` mapping in `Theme.kt`. Existing `MaterialTheme.colorScheme.surface` keeps working but explicit access via `SanchrColors.surfaceMuted` etc. is preferred for the parity work.

Most other iOS color tokens already mapped in Phase 6f-1.

### 4.4 New spacing additions to `SanchrTheme.spacing`

iOS uses literal pt values heavily (54, 44, 28, 18, 14, 10). Add a richer scale:
```kotlin
object SanchrSpacing {
    val xxxs = 2.dp; val xxs = 4.dp; val xs = 8.dp; val sm = 12.dp
    val md = 16.dp; val lg = 20.dp; val xl = 24.dp; val xxl = 32.dp
    val xxxl = 40.dp; val xxxxl = 48.dp; val mega = 64.dp
    // Literal-from-iOS (use sparingly, only when no token fits):
    val heroTopGap = 54.dp     // LoginView spacer above hero
    val heroBottomGap = 44.dp  // LoginView gap hero → phone
    val cardCorner = 28.dp     // logo squircle, large hero card
    val phoneFieldRadius = 20.dp
    val avatarPickerSize = 148.dp
}
```

---

## §5 New components (Phase H3)

All in `core/designsystem/src/main/java/com/sanchr/core/designsystem/component/`. Each gets a `@Preview` composable in the same file.

### 5.1 `SanchrGradientButton`
```kotlin
@Composable
fun SanchrGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    trailingIcon: ImageVector? = Icons.Filled.ArrowForward,
)
```
- Capsule shape (corner = 999.dp).
- 64.dp height (iOS LoginView line 236).
- `Brush.horizontalGradient(listOf(SanchrIndigo500, SanchrIndigoDark))` — iOS uses leading→trailing.
- 12.dp horizontal arrangement spacing for icon + text.
- White text, `labelLarge` typography.
- Trailing arrow icon 18.sp Bold weight.
- Shadow: 20.dp blur, 10.dp y-offset, `SanchrIndigo500.copy(alpha=0.22f)`.
- On press: `Modifier.scale(animateFloatAsState(if (pressed) 0.98f else 1f))`. iOS `SanchrPrimaryCTA` pattern.
- Disabled state: `alpha=0.58f`, gradient unchanged.
- Loading state: replace text+icon with `CircularProgressIndicator(color = Color.White)`.

### 5.2 `SanchrStepEyebrow`
```kotlin
@Composable
fun SanchrStepEyebrow(
    text: String,
    modifier: Modifier = Modifier,
)
```
- 10.sp Afacad Normal.
- 2.5.sp letterSpacing.
- Color: `SanchrIndigo500` (primary).
- Used as: `SanchrStepEyebrow("STEP 1 OF 3")` or `SanchrStepEyebrow("YOU'RE ALL SET")`.

### 5.3 `SanchrOnboardingProgress`
```kotlin
@Composable
fun SanchrOnboardingProgress(
    currentStep: Int,
    totalSteps: Int = 3,
    modifier: Modifier = Modifier,
)
```
- HStack with 8.dp spacing.
- Each pill: 34.dp × 6.dp Capsule.
- Active: `SanchrIndigo500`. Inactive: `Color(0xFFE5E7EB)`.
- Per iOS `OnboardingView.swift:6-19`.

---

## §6 Per-screen specs

### 6.1 LoginPhone (Phase H4)

**iOS reference:** `LoginView.swift` complete file.

**Layout rhythm (vertical):**
```
[scroll content, horizontal padding 28dp]
  Spacer 54dp
  heroSection
    SanchrLogo 120×120dp, RoundedCornerShape(28dp), Image painterResource
    Spacer 40dp
    Text "Welcome to Sanchr" — displayMedium (36sp SemiBold), textPrimary, center
    Spacer 14dp
    Text "Encrypted. Synced. Secure." — bodyMedium (16sp Medium), textSecondary, center
  Spacer 44dp
  phoneSection
    Text "Phone Number" — labelLarge (16sp SemiBold), textPrimary
    Spacer 14dp
    HStack [country menu (88×60) | divider (1×28 line) | TextField (60dp, "(555) 123-4567")]
      background = SanchrSurface, RoundedCornerShape(20dp)
      stroke = if error: SanchrError.copy(0.35f), else: SanchrLine; lineWidth=1.2dp
      shadow: 18dp blur, 10dp y, black.copy(0.04f)
    Spacer 14dp
    Text "We'll send you a verification code" — bodySmall (14sp Normal), textSecondary
    [if error] Spacer 10dp; HStack [Icon error.fill 13sp Bold | Text(error) bodySmall], color=SanchrError
  Spacer 18dp
  securityCard
    HStack alignTop, 16dp gap
      RoundedCornerShape(14dp) 52×52dp, fill=SanchrIndigo500.copy(0.1f), Icon "lock.fill" 18sp SemiBold tint=SanchrIndigo500
      Column 8dp gap
        Text "End-to-End Encrypted" — headlineSmall (20sp SemiBold), textPrimary
        Text "Your messages are secured with military-grade encryption..." — bodyMedium (16sp Medium), textSecondary
      Spacer.weight(1f)
    padding 18dp horiz, 20dp vert
    background = RoundedCornerShape(24dp) fill=SanchrSurface
    overlay = RoundedCornerShape(24dp) stroke=SanchrIndigo500.copy(if dark 0.15f else 0.12f), lineWidth=1dp
  Spacer 28dp
  SanchrGradientButton(text="Continue", trailingIcon=ArrowForward, enabled=isPhoneValid, isLoading=isSubmitting)
  Spacer 24dp
  Text "By continuing, you agree to our Privacy Policy and Terms of Service" — labelSmall (12sp Normal), textSecondary, center, padding horiz 16dp
  bottom padding 24dp
background = SanchrBackground (system background)
```

**Country menu detail (parity gap):** Android's existing `CountryCodePicker` (landed in commit `a6fe7e9`) is structured differently from iOS's `LoginView` country menu. iOS embeds a `Menu` button (88dp wide × 60dp tall, globe icon 18sp SemiBold primary tint + country-code text bodyBold textPrimary) **inside** the same RoundedCornerShape(20) container as the phone TextField, separated by a 1×28dp `SanchrLine` vertical divider. H4 will replace the standalone `CountryCodePicker` chip with an `internal` redesigned chip that lives inside the phone field container, matching iOS's visual unit.

The chip's expanded picker (when tapped) keeps the existing ModalBottomSheet implementation since iOS's pop-up `Menu` has no Compose equivalent without Material 3 ExposedDropdownMenu (heavier surface).

### 6.2 OnboardingName (STEP 1, Phase H5)

**iOS reference:** `OnboardingNameStepView.swift`.

**Layout rhythm:**
```
Spacer minHeight 44dp
RoundedCornerShape(28dp) 108×108dp, fill = horizontal gradient(0xFFEEF2FF → 0xFFECFEFF)
  overlay: SanchrLogo Image 64×64dp, contentScale=Fit
padding bottom 28dp
SanchrStepEyebrow "STEP 1 OF 3"
padding bottom 12dp
Text "What's your name?" — displayMedium (36sp SemiBold Afacad), textPrimary
padding bottom 8dp
Text "This is how people will see you on Sanchr." — bodyMedium, textSecondary, center, padding horiz 40dp
padding bottom 30dp
TextField (placeholder "Enter your name", value=displayName)
  font bodyMedium, padding horiz 18dp, height 56dp
  background SanchrSurfaceMuted, RoundedCornerShape(18dp)
  capitalization Words, IME action Done (Continue)
  on input: trim to 40 chars
padding horiz screenHorizontal=20dp
Spacer (weight 1)
VStack 18dp
  SanchrOnboardingProgress(currentStep=1)
  SanchrGradientButton("Continue", trailingIcon=null, enabled=isNameValid, ...)
padding horiz 20dp
padding bottom 36dp
background = SanchrBackground
focus: name field auto-focuses on appear
```

**Note:** char limit Android = 40 to match iOS exactly (was 128). Backend tolerance is 128, so trimming to 40 client-side is iOS parity. Document the deviation from backend max in KDoc.

### 6.3 OnboardingAvatar (STEP 2, Phase H6)

**iOS reference:** `OnboardingAvatarStepView.swift`.

**Layout rhythm:**
```
HStack
  IconButton chevron.left 18sp SemiBold textSecondary, 40×40dp tap target
  Spacer.weight(1)
padding horiz 16dp (sectionHorizontal)
padding top 12dp
Spacer minHeight 28dp
SanchrStepEyebrow "STEP 2 OF 3"
padding bottom 12dp
Text "Add a photo" — displayMedium, textPrimary
padding bottom 8dp
Text "Help your contacts recognize you instantly." — bodyMedium, textSecondary
padding bottom 30dp
PhotosPicker {
  ZStack alignment=BottomEnd {
    if image: Image scaledToFill 148×148dp, clip=Circle
    else: Circle 148×148dp, strokeBorder = (SanchrIndigo500, lineWidth=2dp, dash=[8dp,6dp])
              background = Circle fill SanchrSurfaceSoft
              overlay: Icon "+" 40sp Light tint=SanchrIndigo500
    Circle 38×38dp fill = horizontal gradient(SanchrIndigo500 → 0xFF4F46E5)
      overlay: Icon (camera.fill if !image else pencil) 14sp SemiBold white
  }
}
padding top 14dp
Text (image ? "Tap to change photo" : "Tap to choose photo") — bodySmall, textSecondary
Spacer (weight 1)
VStack 18dp
  SanchrOnboardingProgress(currentStep=2)
  [if error] Text(error) — bodySmall SanchrError center padding horiz 20dp
  SanchrGradientButton("Continue", trailingIcon=null, isLoading=isSaving)
padding horiz 20dp
padding bottom 36dp
background = SanchrBackground
```

**Dashed-stroke implementation:** Compose has no first-class dashed `strokeBorder`. Use `Modifier.drawBehind { drawCircle(brush, style=Stroke(width=2.dp.toPx(), pathEffect=PathEffect.dashPathEffect(floatArrayOf(8f.dp.toPx(), 6f.dp.toPx())))) }`. Cite `OnboardingAvatarStepView.swift:55-58`.

### 6.4 OnboardingContactSync (STEP 3, Phase H7)

**iOS reference:** `OnboardingContactSyncStepView.swift` + `ContactSyncView.swift`.

iOS delegates to a generic `ContactSyncView` that handles permission request, sync progress, and sync results states. For Android H7 we keep our existing simpler structure (permission request only — no active sync UI yet) but match the iOS permission-request layout exactly:

```
SanchrCenteredHeader("Sync Contacts")
  leading: chevron.left IconButton
  trailing: TextButton "Skip" — labelLarge SemiBold SanchrIndigo500 (visible when not syncing)
ScrollView {
  VStack 24dp
    permissionRequestView
      ZStack alignment=BottomEnd
        SanchrLogo 120×120dp clip RoundedCornerShape(28dp)
        Circle 40×40dp fill=SanchrCyan500, overlay Icon "arrow.triangle.2.circlepath" 15sp Bold white, offset(8,8)
      padding top 8dp
      Text "Find Your Friends" — displayMedium textPrimary
      Text "Sync your contacts to see who's already on Sanchr and start secure conversations" — bodyMedium textSecondary center padding horiz 8dp
      VStack 12dp (3 cards verbatim from `ContactSyncView.swift:111-131`):
        permissionCard(icon="Icons.Filled.Shield",       title="Private & Secure",  subtitle="Your contacts are encrypted and never shared with third parties",      tint=SanchrIndigo500)
        permissionCard(icon="Icons.Filled.VerifiedUser", title="Instant Matching",  subtitle="Automatically find friends who are already using Sanchr",              tint=SanchrCyan500)
        permissionCard(icon="Icons.Filled.PanTool",      title="No Spam, Ever",     subtitle="We won't send notifications to your contacts without your permission", tint=Color(0xFF64748B))
}
footerActions: SanchrGradientButton("Sync All Contacts" or "Continue", isLoading=isSyncing)
background = SanchrBackground
```

**`permissionCard` inline component** (per `ContactSyncView.swift:138-161`):
```
HStack 12dp
  Box 44×44dp
    background SanchrSurfaceMuted, RoundedCornerShape(14dp)
    Icon(systemImage) 18sp SemiBold, tint=SanchrIndigo500 (always primary, NOT the per-card `tint` arg — iOS bug-compat)
  Column 2dp (xxxs)
    Text(title) — labelLarge textPrimary           // bodyBold = 16sp SemiBold
    Text(subtitle) — bodySmall textSecondary       // caption = 14sp Normal
  Spacer.weight(1)
padding 16dp
background SanchrSurface
RoundedCornerShape(20dp)                            // SanchrExportMetrics.cardRadius
```

The per-card `tint` arg in iOS is passed but the icon foreground hardcodes `.sanchrPrimary` — appears to be an iOS bug. Document and replicate to keep parity. The unused `tint` arg is preserved on the Android API for future fix.

### 6.5 OnboardingWelcome (post-step, Phase H5)

**iOS reference:** `OnboardingWelcomeStepView.swift` lines 15-130.

```
HStack
  IconButton chevron.left semibold textPrimary 8dp padding
  Spacer.weight(1)
padding horiz 16dp
Spacer (weight 1)
SanchrStepEyebrow "YOU'RE ALL SET"
padding bottom 16dp
[if avatarUri] Image scaledToFill 96×96dp clip Circle
[else] Circle 96×96dp fill=SanchrGradients.primary, overlay: Text(name.first.uppercase) — displayLarge (48sp Bold) white
padding bottom 16dp
Text "Welcome, ${name}!" — displaySmall (30sp SemiBold), textPrimary
padding bottom 4dp
Text "Your messages are end-to-end encrypted. Only you and the people you chat with can read them." — bodySmall textTertiary, center, maxWidth 260dp
padding bottom 48dp
[if !notificationsEnabled] notificationCard padding horiz 32dp padding bottom 20dp
Spacer (weight 1)
VStack 24dp
  SanchrOnboardingProgress(currentStep=3)
  [if error] Text(error) labelSmall SanchrError
  SanchrGradientButton(text = if error "Retry" else "Start Chatting", isLoading=isSaving)
padding horiz 32dp
padding bottom 48dp
background = SanchrBackground
on appear: requestNotificationPermission()
```

`notificationCard`:
```
HStack 12dp
  Icon "bell.badge.fill" title3 SanchrIndigo500
  Column 2dp
    Text "Enable Notifications" — labelLarge textPrimary
    Text "Know when you receive messages" — labelSmall textTertiary
  Spacer.weight(1)
  Button "Enable" — bodySmall SemiBold white
    padding horiz 16dp vert 8dp
    background SanchrIndigo500 RoundedCornerShape(8dp)
padding 16dp
background SanchrSurface RoundedCornerShape(card=16dp)
```

---

## §7 Testing strategy

### Foundation (H1)
- Unit test in `:core:designsystem/src/test/`: assert `Type.kt` typography slots reference `Afacad` family. Mock `R.font.afacad_variable`. Verify weights mapped correctly.
- Snapshot test (Compose Preview-based) deferred — Paparazzi is not currently in the project; not adding it for this spec.

### Components (H3)
- For each new component, a unit test using `runComposeUiTest` (Compose UI testing artifact) verifying:
  - `SanchrGradientButton`: enabled/disabled/loading states render correctly; click handler fires only when enabled.
  - `SanchrStepEyebrow`: text content + applied letterSpacing.
  - `SanchrOnboardingProgress`: correct number of pills active for given currentStep + totalSteps.

### Screens (H4-H7)
- Existing tests (`AuthViewModelStateTest.kt`, `OnboardingViewModelTest.kt`, `AppBootstrapViewModelTest.kt`) updated for state-machine changes only (H2 removes RegisterPhoneAndName).
- No new screen-level UI tests this milestone; manual smoke required after each phase.

### Manual smoke checklist (post-H7)
- New install + register: phone → OTP → Name → Avatar → ContactSync → Welcome → Main. Each screen visually matches iOS reference screenshot.
- Returning install with cached creds: Splash → Main (no OTP, no onboarding).
- Returning install on new device: Splash → LoginPhone → OTP → Main (skips onboarding because server returns displayName).
- Logout → re-register: full new-user flow re-runs, no stale state.
- Light + dark mode: every screen works.
- Tablet / large screen: spacing scales acceptably (no layout cap added in this spec).

---

## §8 Risks + open questions

- **Dynamic Type**: iOS uses `relativeTo: .body` so Afacad scales with system text size. Compose's `Font(...)` doesn't auto-scale by default — pages with capped widths (e.g., `padding horiz 40dp`) may overflow at largest user text size. Defer to a separate accessibility pass.
- **Variable font on API 26**: Compose 1.6+ supports `FontVariation.Settings` on API 26+ but verify on physical Android 8 device before merging H1.
- **`SanchrSurfaceMuted` / `SanchrSurfaceSoft` light/dark mapping**: chosen hexes are best-effort approximations of iOS's dynamic system colors. May look slightly different in real devices vs Compose preview. If flagged in smoke, swap to closest Material `colorScheme` token.
- **`backend` `display_name` empty handling**: confirmed by reading `handlers.rs` but never end-to-end tested. If H2 hits a 400/422 from backend, fall back to passing `displayName="Sanchr User"` and document.
- **Inter font**: iOS bundles `Inter-Variable.ttf` (876KB) and may use it on chat list / settings screens. Out of scope here. If a future milestone needs it, separate decision.
- **iOS 26 Liquid Glass**: iOS uses `sanchrGlass` modifier in some places. Android has no equivalent. Accept the deviation permanently.

---

## §9 Exit criteria

- All 7 phases landed with green `./gradlew check` between each.
- PR #1 updated; sole reviewer can step through each commit and validate.
- Manual smoke checklist (§7) passes on at least one physical device (Pixel or equivalent).
- `./scripts/sync-protos.sh` (or local equivalent) clean — no proto drift introduced.
- Spec document committed in this same repo at `docs/android/ios-pixel-parity-spec.md` (this file).

---

## Appendix — file inventory

### Files created
- `core/designsystem/src/main/res/font/afacad_variable.ttf` (H1)
- `core/designsystem/src/main/java/com/sanchr/core/designsystem/component/SanchrGradientButton.kt` (H3)
- `core/designsystem/src/main/java/com/sanchr/core/designsystem/component/SanchrStepEyebrow.kt` (H3)
- `core/designsystem/src/main/java/com/sanchr/core/designsystem/component/SanchrOnboardingProgress.kt` (H3)

### Files deleted
- `feature/auth/src/main/java/com/sanchr/feature/auth/RegisterScreen.kt` (H2)

### Files modified
- `core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Type.kt` (H1)
- `core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Color.kt` (H1)
- `core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Theme.kt` (H1)
- `core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Spacing.kt` (H1)
- `feature/auth/src/main/java/com/sanchr/feature/auth/AuthState.kt` (H2)
- `feature/auth/src/main/java/com/sanchr/feature/auth/AuthViewModel.kt` (H2)
- `feature/auth/src/main/java/com/sanchr/feature/auth/navigation/AuthNavigation.kt` (H2)
- `feature/auth/src/main/java/com/sanchr/feature/auth/LoginPhoneScreen.kt` (H2 + H4)
- `feature/auth/src/test/java/com/sanchr/feature/auth/AuthViewModelStateTest.kt` (H2)
- `feature/onboarding/src/main/java/com/sanchr/feature/onboarding/OnboardingNameScreen.kt` (H5)
- `feature/onboarding/src/main/java/com/sanchr/feature/onboarding/OnboardingWelcomeScreen.kt` (H5)
- `feature/onboarding/src/main/java/com/sanchr/feature/onboarding/OnboardingAvatarScreen.kt` (H6)
- `feature/onboarding/src/main/java/com/sanchr/feature/onboarding/OnboardingContactSyncScreen.kt` (H7)

### Out-of-scope iOS files referenced
- `ios/Sanchr-iOS/Features/Auth/Presentation/LoginView.swift` (1-277 — full file)
- `ios/Sanchr-iOS/Features/Onboarding/Presentation/OnboardingWelcomeStepView.swift`
- `ios/Sanchr-iOS/Features/Onboarding/Presentation/OnboardingNameStepView.swift`
- `ios/Sanchr-iOS/Features/Onboarding/Presentation/OnboardingAvatarStepView.swift`
- `ios/Sanchr-iOS/Features/Onboarding/Presentation/OnboardingContactSyncStepView.swift`
- `ios/Sanchr-iOS/Features/Contacts/Presentation/ContactSyncView.swift`
- `ios/Sanchr-iOS/SanchrShared/DesignSystem/{Colors,Typography,Spacing,Radius,Gradients,ExportComponents}.swift`
- `backend/crates/sanchr-core/src/auth/handlers.rs:244-293` (Register handler — confirms architectural assumptions)
