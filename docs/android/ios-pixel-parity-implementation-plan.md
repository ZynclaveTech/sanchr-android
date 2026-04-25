# iOS Pixel-Perfect Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land iOS-pixel-perfect parity on Android's unauthenticated flow (Login + 4 onboarding screens) by adopting Afacad font, dropping the Login/Register split, and rebuilding screen visuals against iOS's exact tokens.

**Architecture:** Hybrid 7-phase rollout (foundation → architectural → components → per-screen polish). Each phase ≤5 files, gated on `./gradlew check` green, pushed to `feat/android-auth-onboarding-realignment` (PR #1).

**Tech Stack:** Kotlin / Jetpack Compose / Material 3 / Hilt / Compose variable fonts (API 26+) / Compose UI test artifact (for component tests). No new dependencies. Reads spec at `docs/android/ios-pixel-parity-spec.md` (commit `be376d1`).

**Working dir:** `/Users/soorajpandey/Projects/zynclave/sanchr/android/sanchr-android`. Branch: `feat/android-auth-onboarding-realignment`. Never use `noreply@anthropic.com`.

---

## Pre-flight

Before Task 1, confirm clean state:

- [ ] **Pre-1: Verify branch + clean tree**

Run:
```bash
cd /Users/soorajpandey/Projects/zynclave/sanchr/android/sanchr-android
git branch --show-current
git status --short | grep -v "^??" | head -5
```
Expected: `feat/android-auth-onboarding-realignment`. No staged or unstaged changes (untracked OK).

- [ ] **Pre-2: Verify baseline green**

Run:
```bash
./gradlew check 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`. If not, stop and fix root cause before starting H1.

---

## Phase H1 — Foundation: Afacad + tokens

**Files:**
- Create: `core/designsystem/src/main/res/font/afacad_variable.ttf` (binary, copied from iOS)
- Modify: `core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Type.kt`
- Modify: `core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Color.kt`
- Modify: `core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Spacing.kt`
- Modify: `core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Theme.kt`

### Task H1.1 — Copy Afacad variable font

- [ ] **Step 1: Copy font from iOS bundle**

Run:
```bash
mkdir -p core/designsystem/src/main/res/font
cp ../../ios/Sanchr-iOS/Resources/Fonts/Afacad-Variable.ttf core/designsystem/src/main/res/font/afacad_variable.ttf
ls -la core/designsystem/src/main/res/font/afacad_variable.ttf
file core/designsystem/src/main/res/font/afacad_variable.ttf | head -1
```
Expected: file exists, ~117KB, `TrueType Font data`.

- [ ] **Step 2: Verify R class regenerates**

Run:
```bash
./gradlew :core:designsystem:assembleDebug --rerun-tasks 2>&1 | tail -3
grep "afacad_variable" core/designsystem/build/generated/source/*/com/sanchr/core/designsystem/R.java 2>/dev/null | head -1
```
Expected: `BUILD SUCCESSFUL`. R.java contains `public static final int afacad_variable = 0x...`. If grep returns nothing, the resource didn't pick up — check the path.

### Task H1.2 — Wire FontFamily(Afacad) in `Type.kt`

- [ ] **Step 1: Read current `Type.kt`**

Run:
```bash
cat core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Type.kt
```
Note current `FontFamily` usage in each typography slot. Most likely `FontFamily.SansSerif` or `FontFamily.Default`.

- [ ] **Step 2: Add private `Afacad` FontFamily and apply to every Typography slot**

Edit `Type.kt`. Replace the file's typography body with (preserving existing sizes/weights/letterSpacings; only swap fontFamily):

```kotlin
package com.sanchr.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sanchr.core.designsystem.R

/**
 * Afacad variable font, copied from iOS at
 * `ios/Sanchr-iOS/Resources/Fonts/Afacad-Variable.ttf`. The Compose runtime
 * picks the closest weight axis on first composition. Available weights
 * Regular(400) / Medium(500) / SemiBold(600) / Bold(700).
 */
private val Afacad = FontFamily(
    Font(R.font.afacad_variable, FontWeight.Normal,    variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.afacad_variable, FontWeight.Medium,    variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.afacad_variable, FontWeight.SemiBold,  variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.afacad_variable, FontWeight.Bold,      variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

val SanchrTypography: Typography = Typography(
    displayLarge   = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.Bold,     fontSize = 48.sp),
    displayMedium  = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.SemiBold, fontSize = 36.sp),
    displaySmall   = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.SemiBold, fontSize = 30.sp),
    headlineLarge  = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    headlineSmall  = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleLarge     = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium    = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.5).sp),
    titleSmall     = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.Medium,   fontSize = 14.sp),
    bodyLarge      = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.Medium,   fontSize = 18.sp),
    bodyMedium     = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.Medium,   fontSize = 16.sp),
    bodySmall      = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.Normal,   fontSize = 14.sp),
    labelLarge     = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelMedium    = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelSmall     = TextStyle(fontFamily = Afacad, fontWeight = FontWeight.Normal,   fontSize = 12.sp),
)

/** "STEP X OF 3" eyebrow — 10sp, 2.5sp letterSpacing, primary color (applied at use site). */
val SanchrMicroEyebrowStyle: TextStyle = TextStyle(
    fontFamily = Afacad,
    fontWeight = FontWeight.Normal,
    fontSize = 10.sp,
    letterSpacing = 2.5.sp,
)
```

If the existing `Type.kt` has previously-touched values (Phase 6f-1 commit `fd9ea55` modified some sizes/weights), preserve those values exactly — this step only changes `fontFamily` and adds `SanchrMicroEyebrowStyle`. Re-read after edit.

- [ ] **Step 3: Verify compile**

Run:
```bash
./gradlew :core:designsystem:compileDebugKotlin 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

### Task H1.3 — Add iOS export color tokens to `Color.kt`

- [ ] **Step 1: Read current `Color.kt`**

Run:
```bash
cat core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Color.kt
```

- [ ] **Step 2: Add the 4 missing iOS-export color tokens**

In `Color.kt`, add (preserving existing `Sanchr*` color vals):

```kotlin
/* ---- iOS export tokens (parity with SanchrShared/DesignSystem/ExportComponents.swift:29-39) ---- */
val SanchrSurfaceLight       = Color(0xFFF2F2F7) // .secondarySystemBackground
val SanchrSurfaceDark        = Color(0xFF1C1C1E)
val SanchrSurfaceMutedLight  = Color(0x33767680) // .tertiarySystemFill
val SanchrSurfaceMutedDark   = Color(0x5C767680)
val SanchrSurfaceSoftLight   = Color(0xFFF2F2F7) // .systemGroupedBackground
val SanchrSurfaceSoftDark    = Color(0xFF000000)
val SanchrLineLight          = Color(0x4A3C3C43) // .separator
val SanchrLineDark           = Color(0x995454548)
```

If existing `Color.kt` already has overlapping names from Phase 6f-1, do not duplicate — verify and reference the existing token. The `0x995454548` looks malformed; correct to `0x99545458`. Re-read.

- [ ] **Step 3: Verify compile**

Run:
```bash
./gradlew :core:designsystem:compileDebugKotlin 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

### Task H1.4 — Add iOS literal-spacing tokens to `Spacing.kt`

- [ ] **Step 1: Read current `Spacing.kt`**

Run:
```bash
cat core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Spacing.kt
```

- [ ] **Step 2: Add iOS-literal spacing tokens**

Add to `SanchrSpacing` object (or equivalent):
```kotlin
/* ---- iOS-literal spacings used in LoginView + onboarding (use only when no semantic token fits) ---- */
val heroTopGap        = 54.dp  // LoginView spacer above hero
val heroBottomGap     = 44.dp  // LoginView gap hero -> phone
val cardCorner        = 28.dp  // logo squircle corner, hero card corner
val phoneFieldRadius  = 20.dp
val avatarPickerSize  = 148.dp
```

Re-read after edit.

- [ ] **Step 3: Verify compile**

Run:
```bash
./gradlew :core:designsystem:compileDebugKotlin 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

### Task H1.5 — Update `Theme.kt` to expose new color tokens via `MaterialTheme.colorScheme` adaptive accessors

- [ ] **Step 1: Read current `Theme.kt`**

Run:
```bash
cat core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Theme.kt
```

- [ ] **Step 2: Wire new tokens into the adaptive theme**

Decision: keep the new tokens addressable via a `LocalSanchrSurfaces` CompositionLocal so consumers can do `SanchrSurfaces.current.muted` etc., or expose adaptive top-level functions like `SanchrSurfaces.surface(isDark: Boolean)`. Pick the simpler one — top-level function approach. Add to `Theme.kt`:

```kotlin
@Stable
data class SanchrSurfaces(
    val surface: Color,
    val surfaceMuted: Color,
    val surfaceSoft: Color,
    val line: Color,
)

val LocalSanchrSurfaces = staticCompositionLocalOf {
    SanchrSurfaces(
        surface       = SanchrSurfaceLight,
        surfaceMuted  = SanchrSurfaceMutedLight,
        surfaceSoft   = SanchrSurfaceSoftLight,
        line          = SanchrLineLight,
    )
}

@Composable
fun SanchrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val surfaces = if (darkTheme) {
        SanchrSurfaces(SanchrSurfaceDark, SanchrSurfaceMutedDark, SanchrSurfaceSoftDark, SanchrLineDark)
    } else {
        SanchrSurfaces(SanchrSurfaceLight, SanchrSurfaceMutedLight, SanchrSurfaceSoftLight, SanchrLineLight)
    }
    CompositionLocalProvider(LocalSanchrSurfaces provides surfaces) {
        // existing MaterialTheme(...) call here
    }
}
```

Preserve the existing `MaterialTheme` setup. The `CompositionLocalProvider` wraps it.

- [ ] **Step 3: Verify compile**

Run:
```bash
./gradlew :core:designsystem:check 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

### Task H1.6 — Full check and commit H1

- [ ] **Step 1: Run full verification gate**

Run:
```bash
./gradlew check 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`, no `-x` exclusions.

- [ ] **Step 2: Commit + push**

Run:
```bash
git add core/designsystem/src/main/res/font/afacad_variable.ttf
git add core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Type.kt
git add core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Color.kt
git add core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Spacing.kt
git add core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Theme.kt
git diff --cached --stat
git commit -m "$(cat <<'EOF'
feat(core:designsystem): Afacad variable font + iOS export tokens (H1 foundation)

- Bundle Afacad-Variable.ttf (117KB) from iOS Resources/Fonts.
- Wire Afacad as the family for every typography slot.
- Add SanchrMicroEyebrowStyle (10sp, 2.5sp letter-spacing).
- Add SanchrSurface/SurfaceMuted/SurfaceSoft/Line tokens matching
  iOS SanchrExportColors (.secondarySystemBackground / .tertiarySystemFill /
  .systemGroupedBackground / .separator).
- Expose adaptive surfaces via LocalSanchrSurfaces CompositionLocal.
- Add iOS-literal spacing tokens (heroTopGap, heroBottomGap, cardCorner,
  phoneFieldRadius, avatarPickerSize).
EOF
)"
git push origin feat/android-auth-onboarding-realignment 2>&1 | tail -3
```
Expected: push fast-forwards.

---

## Phase H2 — Architectural: drop Sign up + delete Register

**Files:**
- Modify: `feature/auth/src/main/java/com/sanchr/feature/auth/AuthState.kt`
- Modify: `feature/auth/src/main/java/com/sanchr/feature/auth/AuthViewModel.kt`
- Modify: `feature/auth/src/main/java/com/sanchr/feature/auth/navigation/AuthNavigation.kt`
- Modify: `feature/auth/src/main/java/com/sanchr/feature/auth/LoginPhoneScreen.kt`
- Modify: `feature/auth/src/test/java/com/sanchr/feature/auth/AuthViewModelStateTest.kt`
- Delete: `feature/auth/src/main/java/com/sanchr/feature/auth/RegisterScreen.kt`

### Task H2.1 — Pre-edit grep audit

- [ ] **Step 1: Confirm zero external refs to soon-to-be-deleted symbols**

Run (per CLAUDE.md #10 — search separately for direct calls, type refs, test mocks, re-exports):
```bash
grep -rn "RegisterScreen\b" --include="*.kt" .
grep -rn "RegisterPhoneAndName\b" --include="*.kt" .
grep -rn "switchToLogin\|switchToRegister\b" --include="*.kt" .
grep -rn "REGISTER_ROUTE\b" --include="*.kt" .
grep -rn "submitRegister\b\|onRegisterChanged\b" --include="*.kt" .
```
Expected: every match lives inside `feature/auth/` or in `docs/`. If anything outside (e.g., `:app` NavHost, another feature) references these, STOP and report — deletion scope was wrong.

### Task H2.2 — Update tests first (TDD against the new contract)

- [ ] **Step 1: Read current `AuthViewModelStateTest.kt`**

Run:
```bash
cat feature/auth/src/test/java/com/sanchr/feature/auth/AuthViewModelStateTest.kt | head -80
```

- [ ] **Step 2: Delete tests for removed methods, add tests for new behavior**

Delete (find each by `@Test fun` annotation):
- `chooseLogin_fromHome_*` — state already gone
- `chooseRegister_*`
- `switchToRegister_*`
- `switchToLogin_*`
- `submitRegister_*`

Update the existing `submitOtp_*` tests to assert `AuthState.Done(isNewUser = ...)` instead of `AuthState.Done` (object).

Add these two new tests (place them after the existing `submitOtp` tests):
```kotlin
@Test
fun submitOtp_blankServerDisplayName_emitsIsNewUserTrue() = runTest {
    // Setup: mock authServiceClient.verifyOtp to return a response with
    // user.displayName = "" (or null). Then dispatch a valid OTP.
    coEvery { authServiceClient.verifyOtp(any()) } returns mockAuthResponseWithBlankName()
    vm.onOtpChanged("123456")
    vm.submitOtp()
    advanceUntilIdle()
    val final = vm.state.value
    assertIs<AuthState.Done>(final)
    assertTrue((final).isNewUser)
}

@Test
fun submitOtp_serverProvidedDisplayName_emitsIsNewUserFalse() = runTest {
    coEvery { authServiceClient.verifyOtp(any()) } returns mockAuthResponseWithName("Alice")
    vm.onOtpChanged("123456")
    vm.submitOtp()
    advanceUntilIdle()
    val final = vm.state.value
    assertIs<AuthState.Done>(final)
    assertFalse((final).isNewUser)
}

private fun mockAuthResponseWithBlankName(): AuthResponse =
    AuthResponse(
        accessToken = "tok",
        refreshToken = "ref",
        expiresIn = 3600L,
        deviceId = 1,
        user = User(id = "u-123", displayName = "", phoneNumber = "+15555550100"),
    )

private fun mockAuthResponseWithName(name: String): AuthResponse =
    AuthResponse(
        accessToken = "tok",
        refreshToken = "ref",
        expiresIn = 3600L,
        deviceId = 1,
        user = User(id = "u-123", displayName = name, phoneNumber = "+15555550100"),
    )
```

Adapt the mock helpers to match the actual `AuthResponse` and `User` constructors in the codebase (re-read `proto/.../auth/GrpcClient.kt` for shape).

- [ ] **Step 3: Run tests — they fail because `AuthState.Done` is still an `object`**

Run:
```bash
./gradlew :feature:auth:testDebugUnitTest --tests "com.sanchr.feature.auth.AuthViewModelStateTest" 2>&1 | tail -10
```
Expected: compile error or failed asserts on the new tests. That's the red phase.

### Task H2.3 — `AuthState.kt`: change `Done` to data class, drop `RegisterPhoneAndName`

- [ ] **Step 1: Read current `AuthState.kt`**

Run:
```bash
cat feature/auth/src/main/java/com/sanchr/feature/auth/AuthState.kt
```

- [ ] **Step 2: Apply changes**

Replace `data object Done : AuthState` with `data class Done(val isNewUser: Boolean) : AuthState`.

Remove `data class RegisterPhoneAndName(...)` entirely.

Update KDoc to remove references to the dropped state.

- [ ] **Step 3: Compile (will surface every consumer that needs updating)**

Run:
```bash
./gradlew :feature:auth:compileDebugKotlin 2>&1 | tail -20
```
Expected: failures in `AuthViewModel.kt`, `AuthNavigation.kt`, `LoginPhoneScreen.kt`. Note them — next tasks fix.

### Task H2.4 — `AuthViewModel.kt`: drop register methods + emit `Done(isNewUser)`

- [ ] **Step 1: Read full `AuthViewModel.kt`**

Run:
```bash
cat feature/auth/src/main/java/com/sanchr/feature/auth/AuthViewModel.kt
```

- [ ] **Step 2: Delete methods + update success branches**

Delete the function bodies for `onRegisterChanged`, `submitRegister`, `switchToLogin`, `switchToRegister`. Remove their declarations entirely.

In `submitOtp`'s success branch, before emitting `AuthState.Done`, compute `isNewUser`:
```kotlin
val serverDisplayName = response.user?.displayName.orEmpty()
val isNewUser = serverDisplayName.isBlank()
// existing saveDisplayName/saveDeviceId/saveSession ordering preserved (do not regress)
sessionManager.saveDisplayName(if (serverDisplayName.isNotBlank()) serverDisplayName else current.displayName)
sessionManager.saveDeviceId(response.deviceId.toString())
sessionManager.saveSession(/* ... */)
_state.value = AuthState.Done(isNewUser = isNewUser)
```

In `attemptFastLogin`'s success branch (path where `Login` RPC returns tokens), emit `AuthState.Done(isNewUser = false)` (returning user by definition).

In `attemptFastLogin`'s "already authenticated" early-out, emit `AuthState.Done(isNewUser = false)`.

- [ ] **Step 3: Compile**

Run:
```bash
./gradlew :feature:auth:compileDebugKotlin 2>&1 | tail -10
```
Expected: maybe one or two remaining errors in `AuthNavigation.kt` / `LoginPhoneScreen.kt` for the dropped state.

### Task H2.5 — `AuthNavigation.kt`: drop `REGISTER_ROUTE` + tighten `RouteTarget`

- [ ] **Step 1: Read current `AuthNavigation.kt`**

Run:
```bash
cat feature/auth/src/main/java/com/sanchr/feature/auth/navigation/AuthNavigation.kt
```

- [ ] **Step 2: Remove**

- The `const val REGISTER_ROUTE = "auth/register"` declaration.
- The `composable(REGISTER_ROUTE) { ... RegisterScreen(...) }` block.
- The `RegisterScreen` import.
- The `is AuthState.RegisterPhoneAndName -> RouteTarget(...)` arm in `toRouteTarget(...)`.

If `AuthFlowHost`'s `when` references `AuthState.Done` exhaustively, change to `is AuthState.Done -> ...` (now a data class, not object).

- [ ] **Step 3: Compile**

Run:
```bash
./gradlew :feature:auth:compileDebugKotlin 2>&1 | tail -10
```
Expected: green; only `LoginPhoneScreen.kt` remaining if the Sign-up link still exists.

### Task H2.6 — `LoginPhoneScreen.kt`: remove Sign up footer

- [ ] **Step 1: Read current file (focus on footer area)**

Run:
```bash
grep -n "switchToRegister\|Sign up\|New to Sanchr" feature/auth/src/main/java/com/sanchr/feature/auth/LoginPhoneScreen.kt
cat feature/auth/src/main/java/com/sanchr/feature/auth/LoginPhoneScreen.kt | head -200
```

- [ ] **Step 2: Delete the Sign up footer block**

Find the `SanchrTextButton(onClick = { viewModel.switchToRegister() })` block (or equivalent). Delete it entirely along with any surrounding `Spacer` and the parent `Row`/`Column` if it becomes empty. Privacy line stays as the only footer item.

- [ ] **Step 3: Compile**

Run:
```bash
./gradlew :feature:auth:compileDebugKotlin 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

### Task H2.7 — Delete `RegisterScreen.kt`

- [ ] **Step 1: Confirm zero remaining refs**

Run:
```bash
grep -rn "RegisterScreen\b" --include="*.kt" .
```
Expected: zero results.

- [ ] **Step 2: `git rm` it**

Run:
```bash
git rm feature/auth/src/main/java/com/sanchr/feature/auth/RegisterScreen.kt
```

### Task H2.8 — Run tests: should now PASS

- [ ] **Step 1: Run unit tests**

Run:
```bash
./gradlew :feature:auth:testDebugUnitTest --tests "com.sanchr.feature.auth.AuthViewModelStateTest" 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL`, all tests pass including the 2 new `isNewUser` tests.

### Task H2.9 — Full check and commit H2

- [ ] **Step 1: Full verification gate**

Run:
```bash
./gradlew check 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`, no `-x` exclusions.

- [ ] **Step 2: Commit + push**

Run:
```bash
git add feature/auth/src/main/java/com/sanchr/feature/auth/AuthState.kt
git add feature/auth/src/main/java/com/sanchr/feature/auth/AuthViewModel.kt
git add feature/auth/src/main/java/com/sanchr/feature/auth/navigation/AuthNavigation.kt
git add feature/auth/src/main/java/com/sanchr/feature/auth/LoginPhoneScreen.kt
git add feature/auth/src/test/java/com/sanchr/feature/auth/AuthViewModelStateTest.kt
git diff --cached --stat
git commit -m "$(cat <<'EOF'
refactor(feature:auth): drop Sign up + delete RegisterScreen (H2 architectural)

- AuthState.Done becomes data class with isNewUser flag.
- RegisterPhoneAndName state removed; LoginPhone is the single phone-only
  entry, matching iOS LoginView (which calls Register for both new and
  returning users via backend's existing-phone short-circuit at
  handlers.rs:297-305).
- AuthViewModel: drop onRegisterChanged/submitRegister/switchToLogin/
  switchToRegister. submitOtp emits Done(isNewUser = displayName.isBlank()).
- AuthNavigation: drop REGISTER_ROUTE constant, composable, RouteTarget arm.
- LoginPhoneScreen: remove "New to Sanchr? Sign up" footer link.
- Delete RegisterScreen.kt.
- Tests: drop register/switch tests, add 2 new isNewUser tests.
EOF
)"
git push origin feat/android-auth-onboarding-realignment 2>&1 | tail -3
```

---

## Phase H3 — Components in `:core:designsystem`

**Files:**
- Create: `core/designsystem/src/main/java/com/sanchr/core/designsystem/component/SanchrGradientButton.kt`
- Create: `core/designsystem/src/main/java/com/sanchr/core/designsystem/component/SanchrStepEyebrow.kt`
- Create: `core/designsystem/src/main/java/com/sanchr/core/designsystem/component/SanchrOnboardingProgress.kt`
- Create: `core/designsystem/src/test/java/com/sanchr/core/designsystem/component/ComponentBehaviorTest.kt`

### Task H3.1 — `SanchrGradientButton.kt`

- [ ] **Step 1: Create the component file**

```kotlin
package com.sanchr.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrIndigoDark

/**
 * iOS-parity gradient CTA button matching `LoginView.swift:217-250` /
 * `SanchrGradientButtonLabel` in `ExportComponents.swift:511-542`.
 *
 * Capsule shape, 64dp height, indigo->indigo-dark horizontal gradient,
 * shadow (20dp blur, 10dp y-offset, primary @ 22% alpha), scale 0.98 on press.
 */
@Composable
fun SanchrGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    trailingIcon: ImageVector? = Icons.AutoMirrored.Filled.ArrowForward,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        label = "SanchrGradientButton.scale",
    )
    val alpha = if (enabled) 1f else 0.58f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .scale(scale)
            .shadow(
                elevation = 20.dp,
                shape = CircleShape,
                ambientColor = SanchrIndigo500.copy(alpha = 0.22f),
                spotColor = SanchrIndigo500.copy(alpha = 0.22f),
            )
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(listOf(SanchrIndigo500, SanchrIndigoDark))
            )
            .clickable(
                enabled = enabled && !isLoading,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.height(24.dp),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.scale(if (enabled) 1f else 1f),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = alpha),
                )
                trailingIcon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = alpha),
                        modifier = Modifier.height(18.dp),
                    )
                }
            }
        }
    }
}
```

If `SanchrIndigo500` / `SanchrIndigoDark` aren't the exact existing token names, grep `core/designsystem/src/main/java/com/sanchr/core/designsystem/theme/Color.kt` for the indigo entries and use those.

- [ ] **Step 2: Verify compile**

Run:
```bash
./gradlew :core:designsystem:compileDebugKotlin 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

### Task H3.2 — `SanchrStepEyebrow.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.sanchr.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrMicroEyebrowStyle

/**
 * "STEP X OF Y" / "YOU'RE ALL SET" eyebrow text style.
 * Per `OnboardingNameStepView.swift:29-32`,
 * `OnboardingAvatarStepView.swift:29-32`, and
 * `OnboardingWelcomeStepView.swift:34-37`.
 *
 * 10sp Afacad Normal, 2.5sp letterSpacing, primary color.
 */
@Composable
fun SanchrStepEyebrow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = SanchrMicroEyebrowStyle,
        color = SanchrIndigo500,
        modifier = modifier,
    )
}
```

- [ ] **Step 2: Verify compile**

Run:
```bash
./gradlew :core:designsystem:compileDebugKotlin 2>&1 | tail -5
```

### Task H3.3 — `SanchrOnboardingProgress.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.sanchr.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.theme.SanchrIndigo500

/**
 * Onboarding progress indicator: N pills (default 3), filling left-to-right
 * up to currentStep. Per `OnboardingView.swift:6-19` (capsule 34x6dp,
 * spacing 8dp, active=primary, inactive=#E5E7EB).
 */
@Composable
fun SanchrOnboardingProgress(
    currentStep: Int,
    modifier: Modifier = Modifier,
    totalSteps: Int = 3,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        repeat(totalSteps) { index ->
            val isActive = index < currentStep
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (isActive) SanchrIndigo500 else Color(0xFFE5E7EB)),
            )
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run:
```bash
./gradlew :core:designsystem:compileDebugKotlin 2>&1 | tail -5
```

### Task H3.4 — Component behavior tests

- [ ] **Step 1: Verify Compose UI test artifact is wired**

Run:
```bash
grep -E "compose-ui-test-junit4|androidx.compose.ui:ui-test" core/designsystem/build.gradle.kts gradle/libs.versions.toml | head -5
```
If absent, add to `core/designsystem/build.gradle.kts`:
```kotlin
testImplementation(libs.androidx.compose.ui.test.junit4)
debugImplementation(libs.androidx.compose.ui.test.manifest)
```
Verify `libs.versions.toml` has the entries; if not, add. (May already be present from `:feature:auth` tests.)

- [ ] **Step 2: Create `ComponentBehaviorTest.kt`**

```kotlin
package com.sanchr.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ComponentBehaviorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun gradientButton_click_callbackFires_whenEnabled() {
        var clicked = false
        composeRule.setContent {
            SanchrGradientButton(text = "Continue", onClick = { clicked = true })
        }
        composeRule.onNodeWithText("Continue").performClick()
        assert(clicked) { "click callback should fire when enabled" }
    }

    @Test
    fun gradientButton_click_doesNotFire_whenDisabled() {
        var clicked = false
        composeRule.setContent {
            SanchrGradientButton(text = "Continue", enabled = false, onClick = { clicked = true })
        }
        composeRule.onNodeWithText("Continue").performClick()
        assertFalse(clicked, "click callback must not fire when disabled")
    }

    @Test
    fun gradientButton_loading_replacesContentWithSpinner() {
        composeRule.setContent {
            SanchrGradientButton(text = "Continue", isLoading = true, onClick = {})
        }
        // text should not be visible while loading
        composeRule.onNodeWithText("Continue").assertDoesNotExist()
    }

    @Test
    fun stepEyebrow_rendersText() {
        composeRule.setContent {
            SanchrStepEyebrow("STEP 1 OF 3")
        }
        composeRule.onNodeWithText("STEP 1 OF 3").assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Run component tests**

Run:
```bash
./gradlew :core:designsystem:testDebugUnitTest --tests "com.sanchr.core.designsystem.component.ComponentBehaviorTest" 2>&1 | tail -10
```
If Compose UI test requires `connectedAndroidTest` instead of unit test, run that:
```bash
./gradlew :core:designsystem:connectedDebugAndroidTest 2>&1 | tail -10
```
Expected: tests pass. If running without an emulator, the test class needs to live in `androidTest` not `test`.

If unit-test path doesn't work and no emulator is available, defer the runtime tests to manual smoke later — keep only static compile checks. Document in commit.

### Task H3.5 — Full check and commit H3

- [ ] **Step 1: Run full check**

Run:
```bash
./gradlew check 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`, no `-x` exclusions.

- [ ] **Step 2: Commit + push**

Run:
```bash
git add core/designsystem/src/main/java/com/sanchr/core/designsystem/component/
git add core/designsystem/src/test/java/com/sanchr/core/designsystem/component/
git diff --cached --stat
git commit -m "$(cat <<'EOF'
feat(core:designsystem): SanchrGradientButton + StepEyebrow + OnboardingProgress (H3)

iOS parity components for the unauth flow:
- SanchrGradientButton — capsule 64dp, indigo->indigoDark horizontal gradient,
  scale-on-press 0.98, loading spinner state. iOS ref:
  ExportComponents.swift:511-542 + LoginView.swift:217-250.
- SanchrStepEyebrow — 10sp Afacad Normal, 2.5sp letterSpacing, primary color.
  iOS ref: OnboardingNameStepView.swift:29-32.
- SanchrOnboardingProgress — N capsules (34x6dp, spacing 8dp), active=primary,
  inactive=#E5E7EB. iOS ref: OnboardingView.swift:6-19.

Behavior tests cover click-when-enabled, no-click-when-disabled, loading state,
and eyebrow text rendering.
EOF
)"
git push origin feat/android-auth-onboarding-realignment 2>&1 | tail -3
```

---

## Phase H4 — LoginPhone visual polish

**Files:**
- Modify: `feature/auth/src/main/java/com/sanchr/feature/auth/LoginPhoneScreen.kt`
- Modify: `core/designsystem/src/main/java/com/sanchr/core/designsystem/component/CountryCodePicker.kt` (or wherever the existing picker lives — verify path)

### Task H4.1 — Read iOS reference + current Android

- [ ] **Step 1: Re-read iOS LoginView.swift in full**

Run:
```bash
cat ../../ios/Sanchr-iOS/Features/Auth/Presentation/LoginView.swift
```
Pay attention to: every `Spacer().frame(height: N)` value, every `padding(...)`, the `phoneSection` structure (country menu inline with TextField in same RoundedCornerShape container), securityCard composition, gradient button setup.

- [ ] **Step 2: Read current Android `LoginPhoneScreen.kt`**

Run:
```bash
cat feature/auth/src/main/java/com/sanchr/feature/auth/LoginPhoneScreen.kt
```

### Task H4.2 — Embed CountryCodePicker chip inside the phone-field container

This is the key parity gap from the spec §6.1.

- [ ] **Step 1: Read current `CountryCodePicker.kt`**

Run:
```bash
find core feature -name "CountryCodePicker.kt" -type f
cat $(find core feature -name "CountryCodePicker.kt" -type f | head -1)
```

- [ ] **Step 2: Add an `internal` chip-only variant in the same file**

Add a second composable in `CountryCodePicker.kt`:
```kotlin
/**
 * Inline chip variant matching iOS `LoginView.swift:107-138` Menu button:
 * 88dp wide x 60dp tall, globe icon (18sp SemiBold, primary), code text
 * (16sp Medium), 1dp x 28dp `SanchrLine` divider on trailing edge.
 *
 * Tapping opens the same ModalBottomSheet picker as `CountryCodePicker`.
 */
@Composable
fun CountryCodePickerInlineChip(
    selected: Country,
    onSelected: (Country) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .width(88.dp)
            .height(60.dp)
            .clickable { sheetOpen = true },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = null,
                tint = SanchrIndigo500,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = selected.dialCode,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // 1x28dp trailing divider, matches iOS `SanchrExportColors.line`
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(1.dp)
                .height(28.dp)
                .background(LocalSanchrSurfaces.current.line)
        )
    }
    if (sheetOpen) {
        // existing modal sheet picker, reused
        CountryCodePickerSheet(selected = selected, onSelected = { onSelected(it); sheetOpen = false }, onDismiss = { sheetOpen = false })
    }
}
```

If `Country` data class lives in the same file, no import change needed. If `CountryCodePickerSheet` doesn't exist as a separate composable, factor the existing picker's bottom-sheet body out into one. Keep the public `CountryCodePicker` API unchanged.

- [ ] **Step 3: Verify compile**

Run:
```bash
./gradlew :core:designsystem:compileDebugKotlin 2>&1 | tail -5
```

### Task H4.3 — Rebuild `LoginPhoneScreen.kt` against iOS layout

- [ ] **Step 1: Rewrite the screen body**

Replace the current `LoginPhoneScreen` Composable body with the iOS-parity layout. Reference §6.1 in `docs/android/ios-pixel-parity-spec.md` for the exact rhythm. Critical points:

- Outer `Column` inside a `Scrollable` container, horizontal padding 28dp.
- `Spacer(Modifier.height(54.dp))` heroTopGap.
- Hero: `Image(painterResource(R.drawable.sanchr_logo))` 120x120dp, `clip(RoundedCornerShape(28.dp))`. Then 40dp gap, `Text("Welcome to Sanchr", style = MaterialTheme.typography.displayMedium)` colored `MaterialTheme.colorScheme.onBackground`. 14dp gap, subtitle `bodyMedium` colored `onSurfaceVariant`.
- 44dp gap.
- Phone section: column with label "Phone Number" (`labelLarge`), 14dp, then a `Row` with `CountryCodePickerInlineChip` + the `TextField("(555) 123-4567")` — both wrapped in a `Box` with `background(LocalSanchrSurfaces.current.surface, RoundedCornerShape(20.dp))` + `border(1.2.dp, errorOrLine, RoundedCornerShape(20.dp))` + `shadow(18.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.04f))`. The TextField height must match 60dp; whole row container resolves to ≥60dp. 14dp helper text "We'll send you a verification code" (`bodySmall`, `onSurfaceVariant`). If error, 10dp gap then error row.
- 18dp gap.
- securityCard: `Row(alignment = Top, spacing = 16dp)` with 52x52 RoundedCornerShape(14dp) tinted-square containing lock icon (18dp, primary), then column with `Text("End-to-End Encrypted", headlineSmall)` + 8dp + `Text(body, bodyMedium, onSurfaceVariant)`. Padding 18dp horizontal, 20dp vertical. Background RoundedCornerShape(24dp) + 1dp border at primary @ 0.12 (light) / 0.15 (dark).
- 28dp gap.
- `SanchrGradientButton(text = "Continue", trailingIcon = Icons.AutoMirrored.Filled.ArrowForward, enabled = isPhoneValid, isLoading = state is LoginPhone && state.isSubmitting)`
- 24dp gap.
- Privacy line `Text("By continuing, you agree to our Privacy Policy and Terms of Service", labelSmall, onSurfaceVariant)`, padding horizontal 16dp, multiline center.

Use the existing VM bindings (`onLoginPhoneChanged`, `submitLoginPhone`).

- [ ] **Step 2: Verify compile**

Run:
```bash
./gradlew :feature:auth:compileDebugKotlin 2>&1 | tail -5
```

### Task H4.4 — Full check and commit H4

- [ ] **Step 1: Run full check**

Run:
```bash
./gradlew check 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Manual smoke (optional but recommended)**

Build a debug APK and install on a device or emulator, then visually compare LoginPhone vs iOS screenshot. Note any visible deltas; fix inline before commit if minor; flag for follow-up if major.

- [ ] **Step 3: Commit + push**

Run:
```bash
git add core/designsystem/src/main/java/com/sanchr/core/designsystem/component/CountryCodePicker.kt
git add feature/auth/src/main/java/com/sanchr/feature/auth/LoginPhoneScreen.kt
git diff --cached --stat
git commit -m "$(cat <<'EOF'
feat(feature:auth): LoginPhone iOS-pixel parity rebuild (H4)

- Embed CountryCodePicker as inline chip inside the phone-field RoundedCornerShape(20)
  container with 1x28dp SanchrLine divider (iOS LoginView.swift:107-138).
- Hero rhythm: 54dp top, 120dp logo + 28dp corner, 40dp, displayMedium title,
  14dp, body subtitle. 44dp to phone section.
- Phone field: 60dp height, surface fill, 1.2dp line/error border,
  black/0.04f shadow at 18dp/10dp.
- Security card: 52dp tinted square with lock icon, headlineSmall + body,
  RoundedCornerShape(24dp) + primary/0.12 border.
- SanchrGradientButton applied as Continue CTA.
- Privacy line replaces the old footer (Sign-up already removed in H2).
EOF
)"
git push origin feat/android-auth-onboarding-realignment 2>&1 | tail -3
```

---

## Phase H5 — OnboardingWelcome + OnboardingName

**Files:**
- Modify: `feature/onboarding/src/main/java/com/sanchr/feature/onboarding/OnboardingNameScreen.kt`
- Modify: `feature/onboarding/src/main/java/com/sanchr/feature/onboarding/OnboardingWelcomeScreen.kt`
- (Optional) Modify: `feature/onboarding/src/main/java/com/sanchr/feature/onboarding/OnboardingViewModel.kt` only if a state transition needs adjusting; otherwise skip.

### Task H5.1 — Re-read iOS sources

Run:
```bash
cat ../../ios/Sanchr-iOS/Features/Onboarding/Presentation/OnboardingNameStepView.swift
cat ../../ios/Sanchr-iOS/Features/Onboarding/Presentation/OnboardingWelcomeStepView.swift
```

### Task H5.2 — Rebuild `OnboardingNameScreen.kt` against §6.2 spec

- [ ] **Step 1: Replace the body**

Layout rhythm (cite spec §6.2 in KDoc):
```
Column(horizontalAlignment = CenterHorizontally) {
  Spacer(Modifier.height(44.dp))                              // matches iOS Spacer(minLength: 44)
  Box(
    Modifier
      .size(108.dp)
      .clip(RoundedCornerShape(28.dp))
      .background(Brush.linearGradient(listOf(Color(0xFFEEF2FF), Color(0xFFECFEFF))))
  ) {
    Image(painterResource(R.drawable.sanchr_logo), null,
          modifier = Modifier.align(Alignment.Center).size(64.dp))
  }
  Spacer(Modifier.height(28.dp))
  SanchrStepEyebrow("STEP 1 OF 3")
  Spacer(Modifier.height(12.dp))
  Text("What's your name?", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
  Spacer(Modifier.height(8.dp))
  Text(
    "This is how people will see you on Sanchr.",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier.padding(horizontal = 40.dp),
  )
  Spacer(Modifier.height(30.dp))

  // Name TextField
  TextField(
    value = state.name,
    onValueChange = { if (it.length <= 40) viewModel.onNameChanged(it) },
    placeholder = { Text("Enter your name", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
    keyboardActions = KeyboardActions(onDone = { if (state.name.isNotBlank()) viewModel.submitName() }),
    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
    colors = TextFieldDefaults.colors(
      unfocusedContainerColor = LocalSanchrSurfaces.current.surfaceMuted,
      focusedContainerColor = LocalSanchrSurfaces.current.surfaceMuted,
      unfocusedIndicatorColor = Color.Transparent,
      focusedIndicatorColor = Color.Transparent,
    ),
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier
      .fillMaxWidth()
      .height(56.dp)
      .padding(horizontal = 20.dp),
  )

  Spacer(Modifier.weight(1f))

  Column(
    horizontalAlignment = CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(18.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp),
  ) {
    SanchrOnboardingProgress(currentStep = 1)
    SanchrGradientButton(
      text = "Continue",
      trailingIcon = null,
      enabled = state.name.isNotBlank(),
      isLoading = state.isSubmitting,
      onClick = viewModel::submitName,
    )
  }
}
.background(MaterialTheme.colorScheme.background)
```

Auto-focus the TextField on enter via `LaunchedEffect(Unit) { focusRequester.requestFocus() }`.

- [ ] **Step 2: Verify compile**

Run:
```bash
./gradlew :feature:onboarding:compileDebugKotlin 2>&1 | tail -5
```

### Task H5.3 — Rebuild `OnboardingWelcomeScreen.kt` against §6.5 spec

- [ ] **Step 1: Replace the body**

Layout rhythm (cite spec §6.5 in KDoc):
```
Column {
  Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    IconButton(onClick = viewModel::back) {
      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
    }
    Spacer(Modifier.weight(1f))
  }
  Spacer(Modifier.weight(1f))
  SanchrStepEyebrow("YOU'RE ALL SET")
  Spacer(Modifier.height(16.dp))
  // avatar preview
  if (state.avatarUri != null) {
    AsyncImage(
      model = state.avatarUri,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(96.dp).clip(CircleShape),
    )
  } else {
    Box(
      modifier = Modifier.size(96.dp).clip(CircleShape).background(SanchrPrimaryGradient),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = state.name.firstOrNull()?.uppercase() ?: "",
        style = MaterialTheme.typography.displayLarge.copy(color = Color.White),
      )
    }
  }
  Spacer(Modifier.height(16.dp))
  Text("Welcome, ${state.name}!", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
  Spacer(Modifier.height(4.dp))
  Text(
    "Your messages are end-to-end encrypted. Only you and the people you chat with can read them.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier.widthIn(max = 260.dp),
  )
  Spacer(Modifier.height(48.dp))
  if (!state.notificationsEnabled) {
    NotificationCard(onEnable = viewModel::requestNotificationPermission, modifier = Modifier.padding(horizontal = 32.dp))
    Spacer(Modifier.height(20.dp))
  }
  Spacer(Modifier.weight(1f))
  Column(
    verticalArrangement = Arrangement.spacedBy(24.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(bottom = 48.dp),
  ) {
    SanchrOnboardingProgress(currentStep = 3)
    state.errorMessage?.let { error ->
      Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    SanchrGradientButton(
      text = if (state.errorMessage != null) "Retry" else "Start Chatting",
      trailingIcon = null,
      isLoading = state.isSubmitting,
      onClick = viewModel::finishOnboarding,
    )
  }
}
```

`SanchrPrimaryGradient` is `Brush.linearGradient(listOf(SanchrIndigo500, SanchrCyan500))`. Define inline if not yet a token. Match iOS `SanchrGradients.primary`.

`NotificationCard` — inline private composable in same file, per spec §6.5:
```kotlin
@Composable
private fun NotificationCard(onEnable: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LocalSanchrSurfaces.current.surface)
            .padding(16.dp),
    ) {
        Icon(Icons.Filled.NotificationsActive, null, tint = SanchrIndigo500)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text("Enable Notifications", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
            Text("Know when you receive messages", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(SanchrIndigo500)
                .clickable(onClick = onEnable)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Enable", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run:
```bash
./gradlew :feature:onboarding:compileDebugKotlin 2>&1 | tail -5
```

### Task H5.4 — Full check and commit H5

- [ ] **Step 1: Run full check**

```bash
./gradlew check 2>&1 | tail -5
```

- [ ] **Step 2: Commit + push**

```bash
git add feature/onboarding/src/main/java/com/sanchr/feature/onboarding/OnboardingNameScreen.kt
git add feature/onboarding/src/main/java/com/sanchr/feature/onboarding/OnboardingWelcomeScreen.kt
git diff --cached --stat
git commit -m "$(cat <<'EOF'
feat(feature:onboarding): Welcome + Name iOS-pixel parity rebuild (H5)

- OnboardingNameScreen: 108dp gradient logo card, STEP 1 OF 3 eyebrow,
  displayMedium title, 56dp surfaceMuted TextField (40-char cap matching
  iOS), SanchrOnboardingProgress + SanchrGradientButton.
- OnboardingWelcomeScreen: chevron-back, YOU'RE ALL SET eyebrow, 96dp
  avatar (Coil AsyncImage or first-letter gradient fallback), 'Welcome,
  {name}!' displaySmall, E2EE tagline 260dp max width, NotificationCard
  inline if !notificationsEnabled, OnboardingProgress(3) + GradientButton
  ('Start Chatting' / 'Retry').
EOF
)"
git push origin feat/android-auth-onboarding-realignment 2>&1 | tail -3
```

---

## Phase H6 — OnboardingAvatar

**Files:**
- Modify: `feature/onboarding/src/main/java/com/sanchr/feature/onboarding/OnboardingAvatarScreen.kt`

### Task H6.1 — Re-read iOS source

```bash
cat ../../ios/Sanchr-iOS/Features/Onboarding/Presentation/OnboardingAvatarStepView.swift
```

### Task H6.2 — Rebuild against §6.3 spec

- [ ] **Step 1: Replace the body**

Critical: implement the dashed-stroke avatar circle.

Helper:
```kotlin
private fun Modifier.dashedCircleBorder(stroke: Dp, color: Color, dashOn: Dp, dashOff: Dp) =
    this.drawBehind {
        val strokePx = stroke.toPx()
        val dashPath = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
            floatArrayOf(dashOn.toPx(), dashOff.toPx()), 0f
        )
        drawCircle(
            color = color,
            radius = (size.minDimension - strokePx) / 2f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx, pathEffect = dashPath),
        )
    }
```

Layout (cite spec §6.3 in KDoc):
```
Column {
  Row(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
    IconButton(onClick = viewModel::back) {
      Icon(Icons.AutoMirrored.Filled.ArrowBack, ...)
    }
    Spacer(Modifier.weight(1f))
  }
  Spacer(Modifier.height(28.dp))
  SanchrStepEyebrow("STEP 2 OF 3")
  Spacer(Modifier.height(12.dp))
  Text("Add a photo", style = MaterialTheme.typography.displayMedium)
  Spacer(Modifier.height(8.dp))
  Text("Help your contacts recognize you instantly.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  Spacer(Modifier.height(30.dp))

  Box(
    contentAlignment = Alignment.BottomEnd,
    modifier = Modifier.size(148.dp).clickable { launchPicker() },
  ) {
    if (state.avatarUri != null) {
      AsyncImage(model = state.avatarUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(148.dp).clip(CircleShape))
    } else {
      Box(
        modifier = Modifier
          .size(148.dp)
          .clip(CircleShape)
          .background(LocalSanchrSurfaces.current.surfaceSoft)
          .dashedCircleBorder(stroke = 2.dp, color = SanchrIndigo500, dashOn = 8.dp, dashOff = 6.dp),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = SanchrIndigo500, modifier = Modifier.size(40.dp))
      }
    }
    // gradient camera/pencil badge
    Box(
      modifier = Modifier
        .size(38.dp)
        .clip(CircleShape)
        .background(Brush.horizontalGradient(listOf(SanchrIndigo500, Color(0xFF4F46E5)))),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = if (state.avatarUri == null) Icons.Filled.PhotoCamera else Icons.Filled.Edit,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(14.dp),
      )
    }
  }

  Spacer(Modifier.height(14.dp))
  Text(
    if (state.avatarUri == null) "Tap to choose photo" else "Tap to change photo",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  Spacer(Modifier.weight(1f))
  Column(
    verticalArrangement = Arrangement.spacedBy(18.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp),
  ) {
    SanchrOnboardingProgress(currentStep = 2)
    state.errorMessage?.let { error ->
      Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
    SanchrGradientButton(
      text = "Continue",
      trailingIcon = null,
      isLoading = state.isSubmitting,
      onClick = viewModel::submitAvatar,
    )
  }
}
```

`launchPicker()` is the existing `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())` from the prior commit. Preserve.

- [ ] **Step 2: Verify compile + check**

```bash
./gradlew :feature:onboarding:compileDebugKotlin 2>&1 | tail -5
./gradlew check 2>&1 | tail -5
```

- [ ] **Step 3: Commit + push**

```bash
git add feature/onboarding/src/main/java/com/sanchr/feature/onboarding/OnboardingAvatarScreen.kt
git diff --cached --stat
git commit -m "$(cat <<'EOF'
feat(feature:onboarding): Avatar iOS-pixel parity rebuild (H6)

- 148dp dashed-stroke avatar circle (2dp stroke, [8,6] dash, primary)
  over surfaceSoft fill, with 40dp '+' icon when empty.
- 38dp gradient badge (indigo->4F46E5) BottomEnd, camera.fill or pencil
  icon switching on avatarUri presence.
- 'Tap to choose / change photo' caption.
- STEP 2 OF 3 eyebrow + displayMedium title + body sub.
- SanchrOnboardingProgress(2) + SanchrGradientButton (loading on submit).
EOF
)"
git push origin feat/android-auth-onboarding-realignment 2>&1 | tail -3
```

---

## Phase H7 — OnboardingContactSync

**Files:**
- Modify: `feature/onboarding/src/main/java/com/sanchr/feature/onboarding/OnboardingContactSyncScreen.kt`

### Task H7.1 — Re-read iOS sources

```bash
cat ../../ios/Sanchr-iOS/Features/Onboarding/Presentation/OnboardingContactSyncStepView.swift
sed -n '1,250p' ../../ios/Sanchr-iOS/Features/Contacts/Presentation/ContactSyncView.swift
```

### Task H7.2 — Rebuild against §6.4 spec

- [ ] **Step 1: Replace the body**

Layout:
```
Scaffold(
  topBar = {
    TopAppBar(
      title = { Text("Sync Contacts", style = MaterialTheme.typography.headlineSmall) },
      navigationIcon = { IconButton(onClick = viewModel::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
      actions = {
        if (!state.isSyncing && !state.syncComplete) {
          TextButton(onClick = viewModel::skip) {
            Text("Skip", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = SanchrIndigo500)
          }
        }
      },
      colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
  },
  containerColor = MaterialTheme.colorScheme.background,
) { padding ->
  Column(
    horizontalAlignment = CenterHorizontally,
    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 28.dp).verticalScroll(rememberScrollState()),
  ) {
    Spacer(Modifier.height(24.dp))
    Box(contentAlignment = Alignment.BottomEnd) {
      Image(painterResource(R.drawable.sanchr_logo), null, modifier = Modifier.size(120.dp).clip(RoundedCornerShape(28.dp)))
      Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(SanchrCyan500).offset(x = 8.dp, y = 8.dp),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Filled.Sync, null, tint = Color.White, modifier = Modifier.size(15.dp))
      }
    }
    Spacer(Modifier.height(24.dp))
    Text("Find Your Friends", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(12.dp))
    Text(
      "Sync your contacts to see who's already on Sanchr and start secure conversations",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 8.dp),
    )
    Spacer(Modifier.height(24.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
      PermissionCard("Private & Secure",   "Your contacts are encrypted and never shared with third parties",      icon = Icons.Filled.Shield)
      PermissionCard("Instant Matching",   "Automatically find friends who are already using Sanchr",              icon = Icons.Filled.VerifiedUser)
      PermissionCard("No Spam, Ever",      "We won't send notifications to your contacts without your permission", icon = Icons.Filled.PanTool)
    }
    Spacer(Modifier.height(24.dp))
    SanchrGradientButton(
      text = if (state.isSyncing) "Syncing..." else "Sync All Contacts",
      trailingIcon = null,
      isLoading = state.isSyncing,
      onClick = viewModel::sync,
    )
    Spacer(Modifier.height(24.dp))
  }
}
```

`PermissionCard` private composable (per spec §6.4):
```kotlin
@Composable
private fun PermissionCard(title: String, subtitle: String, icon: ImageVector) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LocalSanchrSurfaces.current.surface)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LocalSanchrSurfaces.current.surfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = SanchrIndigo500, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(title,    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

The icons are best-fit Material equivalents of iOS SF Symbols:
- `shield.fill` → `Icons.Filled.Shield`
- `person.badge.shield.checkmark.fill` → `Icons.Filled.VerifiedUser`
- `hand.raised.fill` → `Icons.Filled.PanTool`

- [ ] **Step 2: Verify compile + check**

```bash
./gradlew :feature:onboarding:compileDebugKotlin 2>&1 | tail -5
./gradlew check 2>&1 | tail -5
```

- [ ] **Step 3: Commit + push**

```bash
git add feature/onboarding/src/main/java/com/sanchr/feature/onboarding/OnboardingContactSyncScreen.kt
git diff --cached --stat
git commit -m "$(cat <<'EOF'
feat(feature:onboarding): ContactSync iOS-pixel parity rebuild (H7, spec exit)

- TopAppBar with 'Sync Contacts' title, back chevron, Skip text-button
  (primary, SemiBold) shown when !syncing && !syncComplete.
- Hero: 120dp logo + 28dp corner + 40dp Cyan badge with sync icon offset
  (8,8).
- 'Find Your Friends' displayMedium, body subtitle.
- Three PermissionCards verbatim from ContactSyncView.swift:111-131:
  Private & Secure / Instant Matching / No Spam, Ever. Icon-on-tinted-square
  (44dp, RoundedCornerShape(14dp), surfaceMuted), labelLarge title +
  bodySmall subtitle, RoundedCornerShape(20dp) surface card.
- SanchrGradientButton 'Sync All Contacts' / 'Syncing...' loading state.
EOF
)"
git push origin feat/android-auth-onboarding-realignment 2>&1 | tail -3
```

---

## Post-H7 — manual smoke checklist

After H7 lands, run on a physical device or emulator (no automated UI tests):

- [ ] **New install + sign up flow**

Fresh install. Splash → LoginPhone (no Sign up footer) → Continue → OTP → Onboarding Name STEP 1 → Avatar STEP 2 → ContactSync STEP 3 → Welcome ("YOU'RE ALL SET") → Main. Each screen visually matches the iOS reference.

- [ ] **Returning user, cached creds**

Splash → straight to Main, no LoginPhone, no OTP, no onboarding.

- [ ] **Returning user, new device (no cached creds)**

Splash → LoginPhone → OTP → Main (skips onboarding because server returns displayName).

- [ ] **Logout → re-register**

Logout completes; next launch starts fresh new-user flow.

- [ ] **Light + dark mode**

Every screen renders correctly in both modes. Surface tokens, border colors, gradient buttons all visible.

If any visual delta is found, file as a follow-up commit on the same branch (or a new ticket in `docs/android/theme-parity-followup.md`).

---

## Self-review (run before declaring done)

- All 7 phases (H1-H7) have task lists with explicit step counts.
- Every Compose code block uses real Compose 1.6+ syntax (no stale `Material1` references).
- Every commit message ends with one or more iOS file:line citations (parity provenance).
- File budget per phase verified ≤5 (only H1 reaches 5; others lower).
- Tests landed in H2 (state-machine) + H3 (component behavior). UI-layer screens (H4-H7) covered by manual smoke per §7.
- No `noreply@anthropic.com` in any commit message template.
- Pre-flight checks force a clean tree before H1 starts.
- Each phase ends with `git push origin feat/android-auth-onboarding-realignment` so PR #1 picks up incrementally.

If a future task references a symbol not yet defined, fix that task inline.
