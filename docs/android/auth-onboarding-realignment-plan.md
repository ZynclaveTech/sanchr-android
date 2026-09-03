# Android Auth + Onboarding Realignment Plan (iOS Parity)

Date: 2026-04-24
Branch: feat/android-m5-chats-ui
Status: DRAFT — plan only, no code in this commit

## 1. Target Flow

```
                        +-------------+
                        |   Splash    |  animated logo, warms DI + session read
                        +------+------+
                               |
                  authenticated?          (AppBootstrapViewModel)
                   /                \
                  yes                no
                   |                  |
          +--------+--------+   +-----+-------------------------+
          |   AppLockGate   |   |  Home (Login | Register tabs) |
          |  (biometric)    |   +-----+---------------+---------+
          +--------+--------+         |               |
                   |              Login(existing)   Register(new)
                   |                  |               |
                   |                  v               v
                   |            [phone entry]   [phone + displayName + avatar stub]
                   |                  \              /
                   |                   v            v
                   |                   +----+  +----+
                   |                        v  v
                   |                  +-----------+
                   |                  |   OTP     |  authServiceClient.verifyOtp
                   |                  +-----+-----+
                   |                        |
                   |          new user?     |    existing user?
                   |              \                 /
                   |               v               v
                   |         Onboarding        +----+
                   |        Welcome            |
                   |        Name               |
                   |        Avatar             |
                   |        ContactSync        |
                   |              \           /
                   v               v         v
                +----------------- Main -----------------+
```

State each action triggers (wire):
- Login.Continue -> `authServiceClient.register(RegisterRequest{phone, displayName="", password=bootstrap})` (matches iOS `AuthRepository.requestOTP` at AuthRepository.swift:61-86; see Section 5).
- Register.Continue -> `authServiceClient.register(RegisterRequest{phone, displayName, password=generated})`.
- OTP.Submit -> `authServiceClient.verifyOtp(VerifyOTPRequest)` -> AuthResponse persisted to SessionManager.
- OTP.Resend -> re-issue same Register.
- Onboarding.Avatar.Save -> ProfileService.UpdateProfile (encrypted, per iOS OnboardingViewModel.saveProfile OnboardingViewModel.swift:60-112).
- Onboarding.ContactSync.Finish -> request POST_NOTIFICATIONS + READ_CONTACTS; flip `has_completed_onboarding` DataStore key; navigate to Main.

## 2. Current vs Target Gap

| Screen                | iOS                                                     | Android today                                                | Gap                                                                |
|-----------------------|---------------------------------------------------------|--------------------------------------------------------------|--------------------------------------------------------------------|
| Splash                | SplashView.swift                                        | none                                                         | new SplashScreen + bootstrap gating                                |
| Login (phone only)    | LoginView.swift                                         | LoginScreen.kt (but acts as unified phone entry)             | rename/repurpose: existing-user branch, no name field              |
| Register (phone+name) | RegisterView.swift                                      | ProfileScreen.kt (post-phone)                                | combine phone+name on one screen per iOS; drop dedicated Profile   |
| OTP                   | OTPView.swift                                           | OtpScreen.kt                                                 | minor: shared between both paths, copy/styling parity              |
| AppLockGate           | AppLockGateView.swift                                   | none                                                         | scaffold with BiometricPrompt stub                                 |
| Onboarding Welcome    | OnboardingWelcomeStepView.swift                         | none                                                         | new screen in :feature:onboarding                                  |
| Onboarding Name       | OnboardingNameStepView.swift                            | partially inside ProfileScreen.kt                            | move + dedicated screen                                            |
| Onboarding Avatar     | OnboardingAvatarStepView.swift                          | none                                                         | new screen; image-picker scaffold only (see Section 7)             |
| Onboarding ContactSync| OnboardingContactSyncStepView.swift (wraps ContactSync) | PermissionsScreen.kt                                         | move to :feature:onboarding; repurpose existing permission logic   |
| AuthViewModel         | AuthViewModel.swift                                     | AuthViewModel.kt (owns permissions + registering pipeline)   | split: auth VM keeps login/register/otp; onboarding VM owns steps  |

## 3. Module Structure Change

Current:
- `:feature:auth` owns Login, Profile, OTP, Permissions, the post-OTP key-bootstrap pipeline.

Target:
- `:feature:auth` = Splash, Login, Register, OTP, AppLockGate. Owns `AuthViewModel` that ends at `AuthState.Done` after OTP-verify + key bootstrap. Registration pipeline (key gen, push, sender cert) remains in this VM — it is strictly auth-layer, not onboarding.
- `:feature:onboarding` (NEW) = Welcome, Name, Avatar, ContactSync; owns `OnboardingViewModel` and depends on `:core:datastore`, `:core:designsystem`, `:feature:profile` (for UpdateProfile use-case, future).
- `app` NavHost:
  - `auth` graph -> on Done, inspect `has_completed_onboarding`:
    - true -> navigate `main`
    - false -> navigate `onboarding`
  - `onboarding` graph -> on Finish, navigate `main`.

## 4. State Machine (updated AuthState)

```
sealed interface AuthState {
    data object Splash
    data object AppLocked                                   // biometric gate
    data class LoginPhone(countryCode, phone, isSubmitting) // existing user
    data class RegisterPhoneAndName(countryCode, phone, displayName, isSubmitting) // new user
    data class OtpEntry(phoneE164, displayName?, isNewUser, otp, isSubmitting)
    data class Registering(step: RegistrationStep, ctx)     // post-verify key bootstrap (unchanged)
    data class Done(isNewUser: Boolean)                     // caller routes to onboarding vs main
    data class Error(previousState, message)
}
```

Key deltas vs AuthState.kt today:
- Remove `ProfileEntry` (moved to onboarding module; only name-on-register remains inline in RegisterPhoneAndName).
- Remove `Permissions` from auth VM (moved to onboarding).
- Split `PhoneEntry` into `LoginPhone` vs `RegisterPhoneAndName` so the UI can diverge without a flag.
- Add `Splash`, `AppLocked`, `Done(isNewUser)` to capture the branch.
- `RegistrationStep` enum unchanged.

Onboarding state lives in `OnboardingViewModel` (new):
```
data class OnboardingUi(step: Step, displayName, selectedImageUri?, isSaving, error?)
enum class Step { WELCOME, NAME, AVATAR, CONTACT_SYNC }
```

## 5. Backend RPC Wiring

Wire-level reality confirmed by reading iOS `AuthRepository.requestOTP` (AuthRepository.swift:61-86) and `AuthService.login` (AuthService.swift:23-26):

- iOS **does not call the `Login` RPC at all**. Both its `login(phoneNumber:)` and `register(phoneNumber:displayName:)` paths dispatch `Sanchr_Auth_RegisterRequest` with a bootstrap password; the server's Register handler is responsible for treating a known phone as a login-OTP re-issuance. Login RPC (auth.proto:36) exists but is unused by the iOS client.
- Android today also calls `Register` for the single combined flow (AuthViewModel.kt:83-113). Good — Android is already wire-compatible with iOS. The realignment is pure UX and state, not transport.
- Login path on Android: `authServiceClient.register(RegisterRequest{phone, displayName="", password=bootstrap})`. Keep a stable `bootstrap` constant mirroring iOS `otpBootstrapPassword` — this is a shared secret convention with the backend's existing-user branch. **Flag OPEN-Q#1**.
- Register path on Android: unchanged from today — phone + name + freshly-generated password saved via `SessionManager.saveAccountPassword`.
- Verify path: unchanged (`verifyOtp` -> AuthResponse -> SessionManager).

## 6. Phased Execution Plan

Each phase ends with `./gradlew :app:check` green. Phases touch <=5 files.

**Phase 0 — Step-0 dead-code sweep (commit separately)**
Files:
- feature/auth/.../AuthState.kt (drop unused `previousState` fields if any)
- feature/auth/.../AuthViewModel.kt (prune any unreferenced helpers)
- feature/auth/src/test/.../AuthViewModelStateTest.kt (remove tests for removed states)
- feature/auth/.../PermissionsScreen.kt (drop `onPermissionsResult` diagnostic-only path)
Verify: `./gradlew :feature:auth:check`. Commit: `chore(auth): step-0 dead code sweep`.

**Phase 1 — Splash + AppLockGate scaffolding, new AuthState hierarchy**
Files:
- feature/auth/.../AuthState.kt (add Splash, AppLocked, LoginPhone, RegisterPhoneAndName, Done(isNewUser); keep old states as @Deprecated temporarily)
- feature/auth/.../SplashScreen.kt (new, animation mirrors SplashView.swift:12-102)
- feature/auth/.../AppLockGateScreen.kt (new, BiometricPrompt stub — see Section 7)
- feature/auth/.../navigation/AuthNavigation.kt (wire SPLASH_ROUTE, APP_LOCK_ROUTE)
- app/.../SanchrNavHost.kt (route Loading -> Splash instead of spinner)
Verify: app runs, shows splash, transitions to existing Login. No behavior change beyond splash.

**Phase 2 — Login vs Register split on home**
Files:
- feature/auth/.../LoginScreen.kt (become phone-only, existing-user CTA "Continue")
- feature/auth/.../RegisterScreen.kt (NEW: phone + displayName, matches RegisterView.swift)
- feature/auth/.../WelcomeLandingScreen.kt (NEW: two buttons, "I already have an account" + "Create account") OR add a toggle on LoginScreen — pick two-button landing (cleaner, matches iOS navigation stack).
- feature/auth/.../AuthViewModel.kt (add `onLoginSelected()` / `onRegisterSelected()` transitions)
- feature/auth/.../navigation/AuthNavigation.kt (register routes)
Verify: manual tap-test both buttons surface correct screen.

**Phase 3 — Wire login-path Register RPC (bootstrap password)**
Files:
- feature/auth/.../AuthViewModel.kt (new `submitLoginPhone()` invoking `register` with empty displayName + bootstrap password)
- proto/.../AuthServiceClient (verify no contract change needed)
- feature/auth/src/test/.../AuthViewModelLoginPathTest.kt (NEW)
Verify: integration test with mock AuthServiceClient; resolve OPEN-Q#1 before merging.

**Phase 4 — :feature:onboarding module creation + screens**
Files:
- feature/onboarding/build.gradle.kts (NEW module; depends on :core:designsystem, :core:datastore, :feature:profile)
- feature/onboarding/.../OnboardingViewModel.kt (NEW, mirrors OnboardingViewModel.swift)
- feature/onboarding/.../OnboardingWelcomeScreen.kt
- feature/onboarding/.../OnboardingNameScreen.kt
- feature/onboarding/.../OnboardingAvatarScreen.kt (image picker SCAFFOLD — see Section 7)
Verify: `./gradlew :feature:onboarding:check`. ContactSync screen lands in Phase 5 alongside nav.

**Phase 5 — ContactSync move + NavHost gating**
Files:
- feature/onboarding/.../OnboardingContactSyncScreen.kt (lifted from PermissionsScreen.kt)
- feature/onboarding/.../navigation/OnboardingNavigation.kt (NEW graph)
- core/datastore/.../SessionManager.kt (add `hasCompletedOnboardingFlow()` DataStore key)
- app/.../SanchrNavHost.kt (route post-Done to onboarding vs main based on flag)
- app/.../bootstrap/AppBootstrapViewModel.kt (extend StartDestination sealed class with Onboarding)
Verify: full manual flow for both new and returning users.

**Phase 6 — iOS parity polish (UI design-agent pass)**
Files: feature/auth screens + feature/onboarding screens (spacing, typography, copy strings, gradient usage). Match `SanchrExportColors`/`SanchrTypography` equivalents. Limit to 5 files per sub-agent; swarm.

**Phase 7 — Tests**
Files:
- feature/auth/src/test/.../AuthViewModelStateTest.kt (update for new states)
- feature/onboarding/src/test/.../OnboardingViewModelTest.kt (NEW)
- app/src/androidTest/.../AuthOnboardingIntegrationTest.kt (NEW, Compose + Hilt)
- feature/auth/src/test/.../SplashBootstrapTest.kt
Verify: `./gradlew testDebugUnitTest connectedDebugAndroidTest`.

**Phase 8 — Lint + type-check gate**
- `./gradlew ktlintCheck detekt :app:lintDebug` + fix. Tag code review.

## 7. Scope Cuts (explicitly NOT in this milestone)

- Avatar upload-to-server: Avatar screen captures a local `Uri` only. Backend `UpdateProfile` + encrypted avatar upload (ProfileDataSource parity with iOS ProfileUseCases.UploadAvatar) is a follow-up.
- Real contact import: OnboardingContactSyncScreen grants the permission and flips a local flag. Signal CDSv2-style encrypted contact discovery is out of scope.
- AppLockGate biometric flow: ship the scaffold + route; BiometricPrompt + SettingsRepository "lock enabled" toggle ships in a follow-up security milestone.
- SMS Retriever API / auto-fill OTP: parity with iOS (which doesn't have it either).

## 8. Risks & Open Questions

- **OPEN-Q#1 (blocker-for-Phase-3): Bootstrap password semantics.** iOS sends a constant bootstrap password on the login path (AuthRepository.swift:72 `Self.otpBootstrapPassword()`). Is the backend's Register handler genuinely idempotent for an existing phone, or does it rely on that specific sentinel password? Android currently generates a fresh random password every call (AuthViewModel.kt:85 `generateAccountPassword()`), which would reset the stored password server-side for existing users — potentially breaking future Login RPC usage.
  - Proposed resolution: read `backend-oss/crates/sanchr-auth-service/src/register.rs` (or equivalent) before Phase 3. If backend short-circuits on `phone already registered`, Android is safe; if it overwrites password, we need to either (a) mirror iOS's sentinel bootstrap password, or (b) add a dedicated RequestLoginOtp RPC. Flag as a likely **backend-oss commit** dependency.

- **OPEN-Q#2: Registration lock PIN UX.** iOS surfaces `showRegistrationLockPIN` alert on OTP verify. Android has no such path today.
  - Proposed resolution: add `RegistrationLockRequired` branch in OtpEntry handling in Phase 3. Minimal modal dialog.

- **OPEN-Q#3: `has_completed_onboarding` migration for existing installs.** Users who completed the current flow have no flag set; we'd re-onboard them.
  - Proposed resolution: in Phase 5, treat absent flag + non-empty `displayName` in SessionManager as "completed". Write the flag on first boot after upgrade.

- **OPEN-Q#4: Shared `AuthViewModel` across modules.** `:feature:onboarding` should NOT transitively depend on `:feature:auth`. The `Done(isNewUser)` signal should flow via NavHost callback, not a cross-module VM share.
  - Proposed resolution: onboarding has its own VM seeded from SessionManager (userId, phone) read via `:core:datastore`.

- **RISK#1: Compose navigation back-stack on Splash.** If Splash is a route, users can press Back onto it from Main. Fix: `popUpTo("splash") { inclusive = true }` on first real navigation.

- **RISK#2: AppLockGate triggering in the middle of the auth flow.** Biometric lock must gate only authenticated state. Ensure `AppBootstrapViewModel` only emits `AppLocked` when `sessionActive && lockEnabled`.

## 9. Exit Criteria

Reproducible manual smoke test (fresh install + upgrade install):

1. `./gradlew :app:check` green, ktlint + detekt clean.
2. Launch app -> Splash animates <=1.2s -> Welcome landing visible.
3. Tap "I already have an account" -> phone entry -> OTP -> Main within 4 taps after launch (6 taps total including typing).
4. Fresh install, tap "Create account" -> phone+name -> OTP -> Welcome -> Name -> Avatar -> ContactSync -> Main.
5. Kill + relaunch authenticated user -> Splash -> (AppLockGate if enabled, unlocked by biometric) -> Main, skipping auth and onboarding.
6. Log out from Settings -> routed back to Welcome landing; no stale state in AuthViewModel.
7. Integration test `AuthOnboardingIntegrationTest` covers both new-user and returning-user flows end-to-end with a fake `AuthServiceClient`.

## 10. Total Phases

9 phases (Phase 0 through Phase 8). Estimated 2 working days per phase with swarmed sub-agents on Phases 4, 6, 7.
