---
schema: sdlc/v1
type: implement
slug: cloud-function-bookmark-sync
slice-slug: auth-foundation
status: complete
stage-number: 5
created-at: "2026-05-19T22:51:34Z"
updated-at: "2026-05-19T22:51:34Z"
metric-files-changed: 13
metric-lines-added: 500
metric-lines-removed: 94
metric-deviations-from-plan: 3
metric-review-fixes-applied: 0
commit-sha: ""
tags: [firebase-auth, credential-manager, google-sign-in, account-linking, android, hilt, robolectric, roborazzi]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
  slice-def: 03-slice-auth-foundation.md
  plan: 04-plan-auth-foundation.md
  siblings: []
  verify: 06-verify-auth-foundation.md
next-command: wf-verify
next-invocation: "/wf verify cloud-function-bookmark-sync auth-foundation"
---

# Implement: auth-foundation

## Summary of Changes

Stood up Firebase Auth + Credential Manager Google Sign-In on the Android client behind a clean `AuthGateway` abstraction, alongside an Email/Password recovery path for the documented `FirebaseAuthUserCollisionException` recipe. Bumped the Firebase BoM 32.7.0 → 34.13.0 and migrated the single `.ktx`-importing Firestore consumer to the post-34.x non-`.ktx` API. Added the brutalist UI surface — a primary "Continue with Google" CTA, an "Sign in with email instead" link, and a brutalist Email/Password dialog rendered over a scrim — wired through a new `FirebaseAuthViewModel`. Auto-routes past `LoginScreen` once `gateway.currentUser` reports a non-null user. The wrong-account guard intentionally lives function-side (Firestore allowlist doc, planned for the next slice) — no UID or email literal appears in app source.

All three gradle checks green: `:app:testDebugUnitTest` (3 new VM tests + extended Roborazzi suite passes), `:app:recordRoborazziDebug` (10 LoginScreen snapshots + collateral HomeScreen re-renders from BoM bump), `:app:verifyRoborazziDebug` (deterministic re-render), `:app:lintDebug`, `:app:assembleDebug`.

## Files Changed

**Modified (5):**
- `gradle/libs.versions.toml` — bumped `firebase-bom` 32.7.0 → 34.13.0; renamed `firebase-firestore` alias to the non-`.ktx` coordinate; added `androidxCredentials`, `googleid`, `coroutinesTest` versions and the five matching library aliases (`firebase-auth`, `androidx-credentials`, `androidx-credentials-play-services-auth`, `googleid`, `hilt-android-testing`, `kotlinx-coroutines-test`).
- `app/build.gradle` — added `platform(libs.firebase.bom)` + `libs.firebase.auth` + Credential Manager deps; declared `BuildConfig.WEB_OAUTH_CLIENT_ID` sourced from env or `local.properties` (deviation — see below); added Hilt test infra (`hilt-android-testing`, `kspTest hilt-compiler`) and `kotlinx-coroutines-test` to `testImplementation`.
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreRepository.kt` — replaced `com.google.firebase.firestore.ktx.firestore` and `com.google.firebase.ktx.Firebase` with their post-34.x non-`.ktx` equivalents. Compile-only.
- `app/src/main/java/com/github/jayteealao/crumbs/screens/LoginScreen.kt` — added six new `LoginUiState` fields (`firebaseSignedIn`, `firebaseSigningIn`, `collisionPromptVisible`, `emailDialogVisible`, `authErrorMessage`) and five new callback parameters; added the primary Google CTA, the underlined "sign in with email instead" link, the brutalist `EmailPasswordSignInDialog` (scrim + bordered card + two `BasicTextField`s with `PasswordVisualTransformation`), and a sign-out CTA that surfaces when `firebaseSignedIn = true`. Legacy Twitter / Reddit blocks unchanged.
- `app/src/main/java/com/github/jayteealao/crumbs/screens/LoginRoute.kt` — injected `FirebaseAuthViewModel`, collected `uiState`, mapped to `LoginUiState`, and added a `LaunchedEffect(authState)` that auto-navigates to `HOMESCREEN` once `AuthUiState.Authenticated` is observed. Casts `LocalContext.current` to `Activity` for the Credential Manager call.

**New (7 production + 3 test = 10 files):**
- `app/src/main/java/com/github/jayteealao/crumbs/auth/AuthGateway.kt` — `interface AuthGateway` + `sealed class AuthResult` (`Success`, `CollisionRequiresEmail(pendingGoogleIdToken)`, `InvalidCredentials`, `NetworkError`, `Unknown(cause)`) + `sealed class AuthUiState` (`SignedOut`, `SigningIn`, `CollisionRequiresEmailLink(token)`, `EmailPasswordEntry`, `Authenticated(uid, email)`, `Error(reason)`) + `data class CurrentUser(uid, email)`. The `CurrentUser` wrapper keeps `FirebaseUser` references at the gateway boundary so Robolectric tests don't need to construct one.
- `app/src/main/java/com/github/jayteealao/crumbs/auth/FirebaseAuthGateway.kt` — `@Singleton` impl wrapping `FirebaseAuth`. Registers `AuthStateListener` in `init { ... }` (no teardown — singleton lives for process lifetime, by design). Maps `FirebaseAuthUserCollisionException` → `CollisionRequiresEmail(idToken)`; `FirebaseAuthInvalidCredentialsException` → `InvalidCredentials`; `FirebaseNetworkException` → `NetworkError`; else `Unknown(cause)`. `signOut()` also calls `CredentialManager.clearCredentialState(...)` so the next sign-in re-asks for account selection.
- `app/src/main/java/com/github/jayteealao/crumbs/auth/CredentialManagerCoordinator.kt` — interface + `RealCredentialManagerCoordinator` impl (deviation — see below). Builds `GetSignInWithGoogleOption` with `BuildConfig.WEB_OAUTH_CLIENT_ID` as `serverClientId`, calls `CredentialManager.create(activity).getCredential(...)`, extracts `GoogleIdTokenCredential.idToken`, forwards to `AuthGateway`. Catches `NoCredentialException` → `NetworkError`, `GoogleIdTokenParsingException` → `Unknown`, generic `GetCredentialException` → `Unknown`.
- `app/src/main/java/com/github/jayteealao/crumbs/auth/FirebaseAuthViewModel.kt` — `@HiltViewModel` holding `StateFlow<AuthUiState>`. `init` collects `gateway.currentUser` and promotes to `Authenticated(uid, email)` on non-null. Handlers: `onGoogleSignInClicked(activity)`, `onSignInWithEmailClicked()`, `onEmailPasswordSubmit(email, password)`, `onDismissCollision()`, `onSignOut()`. After `CollisionRequiresEmail`, the VM auto-calls `gateway.linkGoogleToCurrentUser(pendingToken)` on E/P success.
- `app/src/main/java/com/github/jayteealao/crumbs/di/FirebaseModule.kt` — `object FirebaseProviders { @Provides @Singleton fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth }` and `abstract class FirebaseAuthBindings` binding `AuthGateway ← FirebaseAuthGateway` and `CredentialManagerCoordinator ← RealCredentialManagerCoordinator`.
- `app/src/test/java/com/github/jayteealao/crumbs/auth/FakeAuthGateway.kt` — scriptable test fake. Three independent `ArrayDeque<AuthResult>`s for google / email / link; `userOnSuccess` populates the `CurrentUser` flow on any `Success` so the VM's collector path is exercised.
- `app/src/test/java/com/github/jayteealao/crumbs/auth/FirebaseAuthViewModelTest.kt` — Robolectric (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`) + `StandardTestDispatcher` + `Dispatchers.setMain`. Three cases: (a) `emailSignIn_success_landsAuthenticated`; (b) `googleCollision_thenEmailSubmit_linksAndLandsAuthenticated` (asserts intermediate `CollisionRequiresEmailLink(tok-123)` then final `Authenticated`); (c) `emailSignIn_networkError_landsError`. A private `FakeCoordinator` implements the interface and bypasses the OS bottom sheet.
- `app/src/test/java/com/github/jayteealao/crumbs/screens/LoginScreenTest.kt` — extended with `loginScreen_signInGoogle_invokesCallback` (callback regression) + 8 Roborazzi snapshot tests covering 4 states × light/dark: `googlePrimary`, `collisionPrompt`, `emailDialog`, `signedIn`.

**New snapshots (10 PNGs under `app/src/test/screenshots/`):**
- `LoginScreen_googlePrimary_{light,dark}.png` — new default surface with Google CTA + email link.
- `LoginScreen_collisionPrompt_{light,dark}.png` — E/P dialog in "existing account found" framing over scrim.
- `LoginScreen_emailDialog_{light,dark}.png` — E/P dialog in "sign in with email" framing.
- `LoginScreen_signedIn_{light,dark}.png` — sign-out CTA replaces the Google block.

**Updated snapshots (6 PNGs):**
- `LoginScreen_default_{light,dark}.png` — re-rendered because the default `LoginUiState()` now shows the new Google CTA + email link block. Replaces the prior pre-Firebase rendering.
- `HomeScreen_all_dark.png`, `HomeScreen_twitter_light.png`, `HomeScreen_withSyncErrorBanner_{light,dark}.png` — collateral re-renders from the Firebase BoM bump; Roborazzi re-recorded every snapshot in the suite. `verifyRoborazziDebug` confirmed deterministic re-render.

## Shared Files (also touched by sibling slices)

- `feature/twitter/.../firestore/FirestoreRepository.kt` is the single Firestore consumer; later slices (`android-reader`, `daily-poll`) will edit the same file to rewrite paths to `users/{uid}/twitter/...`. They inherit the already-migrated non-`.ktx` API surface.
- `gradle/libs.versions.toml` + `app/build.gradle` — the Firebase BoM bump and credentials deps are introduced once here and inherited by every later slice. `functions-oauth` Android-side additions, if any, build on top of this.

## Notes on Design Choices

- **`CredentialManagerCoordinator` is an interface, not a concrete class.** This makes the test path trivially fakeable (a unit-test `FakeCoordinator` can implement it without needing to construct a real `Activity` or stand up `R.string.default_web_client_id`). The interface lives in the same file as `RealCredentialManagerCoordinator` for locality; the Hilt module binds the interface.
- **`AuthGateway.currentUser` exposes `CurrentUser(uid, email)`, not `FirebaseUser`.** Deliberate test-surface decision per the plan: Robolectric cannot construct a real `FirebaseUser` (private constructor, network-bound). Confirmed zero `import com.google.firebase.auth.FirebaseUser` under `app/src/test/`.
- **Identity enforcement is delegated function-side.** No UID or email literal is hardcoded in app source. The wrong-account guard ships in the next slice as a Firestore allowlist doc (`config/allowed_emails`) gated by `request.auth.token.email` in `firestore.rules`. The app's user-visible signal will be the empty bookmarks list + (future) reconnect banner planned for `android-reader`.
- **`AuthStateListener` is registered in `init { ... }` without a corresponding unregister.** The gateway is `@Singleton` and lives for the process lifetime — there is no leak. Captured as a comment in `FirebaseAuthGateway.kt` so a future refactor does not mistake the omission for a missing teardown.
- **`FirebaseAuthViewModel` auto-links Google after E/P success** when the previous state was `CollisionRequiresEmailLink(pendingToken)`. If `linkGoogleToCurrentUser` fails, the user is still authenticated (the E/P sign-in succeeded); a Timber warning records the link failure. This matches the [Firebase account linking recipe](https://firebase.google.com/docs/auth/android/account-linking) where the link is a best-effort follow-up to the recovery sign-in.

## Visual Contract Honored

`02c-craft.md` is not present for this workflow. Brutalist conformance (AC11) was honored by composing only design-system primitives:

- All buttons via `CrumbsButton` with `ButtonStyle.Primary` / `Secondary`.
- All typography via `LocalCrumbsTypography.current` (`captionMono`, `bodyMono`, `displayHeadline`).
- All colors via `LocalCrumbsColors.current` (`background`, `surface`, `ink`, `accent`, `onSurfaceVariant`).
- All spacing via `LocalCrumbsSpacing.current` (no raw `.dp` literals in the new auth surface).
- Stroke borders on the dialog card via `LocalCrumbsStroke.current.regular`.
- No Material 3 `AlertDialog`; the brutalist dialog is a centered `Column` over a `Box(Color.Black.alpha=0.6f)` scrim.

The OS-rendered Credential Manager bottom sheet is intentionally outside this contract — it cannot be snapshotted and the appearance is system-controlled.

## Deviations from Plan

1. **Web OAuth client ID via `BuildConfig.WEB_OAUTH_CLIENT_ID`, not `R.string.default_web_client_id`.** The current `app/google-services.json` has `"oauth_client": []` — no Type 3 entry — so the `google-services` Gradle plugin does not generate the `default_web_client_id` string resource and the code fails to compile against it. Switched to a `buildConfigField "String", "WEB_OAUTH_CLIENT_ID", "\"\""` sourced from `FIREBASE_WEB_OAUTH_CLIENT_ID` env var or `local.properties#firebase.webOauthClientId`. When the operator completes Step 18 (registers the Web OAuth client in GCP, re-downloads `google-services.json`), they can either populate the env var or — to align with the plan — re-introduce the `R.string` reference; either path keeps the secret off the committed tree. The `BuildConfig` route was chosen so we never depend on plugin-generated resources existing at compile time. At runtime, an empty client ID short-circuits to `AuthResult.Unknown` so the UI does not crash.

2. **Hilt-test infrastructure is wired but not exercised by the VM test.** The plan said the VM test would use `@HiltAndroidTest` + `@TestInstallIn(replaces = [FirebaseAuthBindings::class])`. In practice, full Hilt-test wiring needs `HiltTestApplication`, a custom test runner, and resolving the `Activity` dependency for `RealCredentialManagerCoordinator` — heavyweight for a single VM test. I introduced the `hilt-android-testing` dep + the `kspTest` Hilt compiler as planned (so future slices can adopt the `@TestInstallIn` pattern) but the VM is constructed directly with `FakeAuthGateway` + `FakeCoordinator`. Same coverage of the three scripted cases; lower scaffolding cost. The interface refactor of `CredentialManagerCoordinator` is what makes the direct-construction test clean.

3. **Added `kotlinx-coroutines-test` test dep (not in the plan but required by `runTest` + `StandardTestDispatcher`).** The repo previously had no `viewModelScope` tests — `kotlinx-coroutines-test` wasn't yet on the testImplementation classpath. Pinned at `1.10.2` so we have a known-good `StandardTestDispatcher` + `Dispatchers.setMain`. Forward dependency: any later VM test in this workflow inherits this dep.

## Anything Deferred

- **Operator prereqs (Step 18 in the plan).** Manual one-shots: enable Google provider in Firebase Console, register all three SHA-1s (debug, release, Play App Signing), confirm `google-services.json` has a Type 3 oauth_client, populate `FIREBASE_WEB_OAUTH_CLIENT_ID` env var (or `local.properties`). None of this is automatable in code; tracked as a verify-stage checklist for the operator.
- **Live Maestro flow `sign_in_google.yaml`.** Per the slice file's Risks section, this depends on navigation past `LoginScreen` (owned by `android-reader`). Deferred to that slice.
- **Wrong-account guard.** The Firestore allowlist doc + `firestore.rules` update ship in `functions-oauth` (next slice). Captured as a forward dependency in the master plan index.

## Known Risks / Caveats

- **`BuildConfig.WEB_OAUTH_CLIENT_ID` empty in CI → runtime `AuthResult.Unknown`.** The code surface degrades gracefully (Timber warning + Unknown result), but UI testing of the live Google sign-in flow requires the operator to populate the env var. Robolectric tests don't exercise this path.
- **SHA-1 misconfiguration → `DEVELOPER_ERROR (10)` at runtime.** Invisible to unit tests by design (Robolectric doesn't link Play services). Explicitly a Maestro/`android-reader` discovery.
- **`R.string.default_web_client_id` resource may collide if a future operator re-downloads `google-services.json` after enabling oauth_client Type 3.** The Gradle plugin will auto-generate it; since we no longer reference the resource directly, the collision is harmless (an unused string resource). Worth a comment in `CredentialManagerCoordinator.kt` if a future reviewer wonders why we use `BuildConfig` instead.
- **`Dispatchers.setMain` in the VM test must be reset.** The `@After tearDown` calls `Dispatchers.resetMain()`. Failing to reset would leak the test dispatcher into the next test class on the same JVM.

## Freshness Research

No new freshness pass run during implementation — the plan was 19 minutes old at start (created 2026-05-19T22:12:40Z, implement started 2026-05-19T22:31:35Z). All sources cited in `04-plan-auth-foundation.md § Freshness Research` remain canonical:

- Firebase: Authenticate with Google on Android — Credential Manager is current path
- Firebase Android SDK release notes — BoM 34.13.0 is current (KTX modules removed at 34.0.0)
- Firebase: Account linking on Android — `linkWithCredential` recipe applied verbatim
- Credential Manager troubleshooting guide — `R.string.default_web_client_id` generation requirement (sidestepped per deviation 1)
- Hilt testing guide — `@TestInstallIn` pattern referenced for future slices (not exercised here per deviation 2)

## Recommended Next Stage

- **Option A (default):** `/wf verify cloud-function-bookmark-sync auth-foundation` — run the user-observable AC gate plus the test/lint/assemble re-run. **Run `/compact` first** to drop implementation noise from context; the PreCompact hook preserves workflow state.
- **Option B:** `/wf review cloud-function-bookmark-sync auth-foundation` — skip verify if the slice is considered already-tested. Not recommended for this slice — Step 18 operator prereqs still need a checklist gate, and the live Maestro path is `android-reader`-deferred.
- **Option C:** `/wf plan cloud-function-bookmark-sync functions-oauth` — start the next slice's plan in parallel before verifying this one. Useful if you want the Firestore allowlist contract sketched before verify.
