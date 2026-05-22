---
schema: sdlc/v1
type: implement
slug: cloud-function-bookmark-sync
slice-slug: android-reader
status: complete
stage-number: 5
created-at: "2026-05-22T18:18:00Z"
updated-at: "2026-05-22T18:18:00Z"
metric-files-changed: 30
metric-lines-added: 1450
metric-lines-removed: 100
metric-deviations-from-plan: 5
metric-review-fixes-applied: 0
commit-sha: "cd107da"
tags: [android, compose, firestore, callable, custom-tabs, pkce, jose, hilt, robolectric, roborazzi, maestro, lazylogcat, pull-to-refresh, deep-link, brutalist, jwt-claim-amendment]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
  slice-def: 03-slice-android-reader.md
  plan: 04-plan-android-reader.md
  siblings:
    - 05-implement-auth-foundation.md
    - 05-implement-functions-oauth.md
    - 05-implement-daily-poll.md
    - 05-implement-poll-correctness.md
  verify: 06-verify-android-reader.md
next-command: wf-verify
next-invocation: "/wf verify cloud-function-bookmark-sync android-reader"
---

# Implement: android-reader

## Summary of Changes

The full Android-side read surface that consumes server-written Firestore data. After this slice:

- An authenticated user can sign in with Google, hit a blocking Connect-X onboarding step, complete the X OAuth round-trip through a Chrome Custom Tab, return to the app via a deep link, and see their bookmarks within ~30s of OAuth completion.
- Pull-to-refresh invokes the `triggerPoll` callable; new bookmarks appear without a device-side X HTTP loop.
- `linked == false` pins a `RECONNECT X` banner to the top of the bookmarks list with a CTA that routes to the same Connect-X destination.
- A Settings sync-status row shows linked state, last-polled timestamp, and any `lastError`; the disconnect button shows a "coming with cutover" toast (the real callable lands in `cutover-migration`).

The slice also amends two function-side handlers (`mintOAuthState` + `oauthCallback`) so the PKCE `code_verifier` rides inside the existing HMAC-signed state JWT instead of on the redirect URL (RFC 9700 compliance), and so `oauthCallback` fans out to `runPoll(uid)` after a successful link.

## Files Changed

### Function-side (TypeScript) — 4 files

- `functions/src/lib/state.ts` — `OAuthStateClaims` gains `cv: string`; `signOAuthState(uid, nonce, codeVerifier)` (new positional arg) sets the claim; `verifyOAuthState` rejects missing or empty `cv`.
- `functions/src/handlers/mintOAuthState.ts` — accepts `request.data.code_verifier`; validates against RFC 7636 §4.1 charset + 43-128 length; passes through to `signOAuthState`. Return shape unchanged.
- `functions/src/handlers/oauthCallback.ts` — drops `req.query.code_verifier`; reads `claims.cv` after `verifyOAuthState`. Fan-out: after the success-path `sync_status` write and before the redirect, lazy-imports `../lib/poll` + invokes `runPoll(claims.uid)` inside a fire-and-forget IIFE wrapped in try/catch. The 302 redirect is not delayed by the poll.
- `functions/eslint.config.js` — adds `setImmediate`, `URLSearchParams`, `RequestInit`, `Promise` to the test-block globals so the extended oauthCallback test compiles under ESLint 9 flat config.
- Tests: `functions/test/state.test.ts` — round-trip test gains a `cv` claim; new tests for missing-`cv` + empty-`cv` rejection; existing forged/wrong-key/expired/malformed tests now sign with `cv: "cv"` for forward-compat. `functions/test/oauthCallback.test.ts` — happy-path test mocks `../lib/poll`, asserts `code_verifier` is sourced from the state JWT (not the query), and asserts `runPoll` is invoked exactly once; rejection paths assert no `runPoll` invocation.

All 29 jest tests pass (27 baseline + 2 new state-cv tests).

### Android deps — 3 files

- `gradle/libs.versions.toml` — adds `androidxBrowser = "1.8.0"`, `firebase-functions` (no version, from BoM), `androidx-browser`.
- `app/build.gradle` — declares `libs.firebase.firestore`, `libs.firebase.functions`, `libs.androidx.browser`. Drops the commented-out 1.4.0 browser line.
- `feature/twitter/build.gradle` — declares `libs.firebase.auth`, `libs.firebase.functions`, `libs.androidx.browser`; adds `testImplementation` for `kotlinx-coroutines-test` and MockK 1.13.10.

### DI — 1 file

- `app/.../di/FirebaseModule.kt` — adds `provideFirebaseFirestore()` and `provideFirebaseFunctions()` (region `europe-west2`).

### Data layer + OAuth coordinator (feature/twitter) — 6 new + 3 modified

- **NEW** `feature/twitter/.../data/dto/SyncStatus.kt` — plain data class mirroring the server-written `users/{uid}/sync_status/state` doc shape.
- **NEW** `feature/twitter/.../data/SyncStatusRepository.kt` — `@Singleton`, one-shot `Source.SERVER` reads, `THROTTLE_MS = 5_000L`, 8s timeout, tolerates offline by retaining the last good value. Exposes `flow: StateFlow<SyncStatus?>` and `suspend fun refresh(force: Boolean = false)`. Adds a `seedForDebug(status)` mutator gated to Maestro debug seeding.
- **NEW** `feature/twitter/.../data/SnackbarEvent.kt` — typed channel for `triggerPoll` outcomes (`Debounced(retryAfter)`, `InProgress`, `GenericFailure(reason)`).
- **NEW** `feature/twitter/.../oauth/TwitterOAuthCoordinator.kt` — `@Singleton`. `launchAuthorize(activity)`: generates PKCE locally (SecureRandom 32B → base64url verifier; SHA-256 challenge), pings `warmUp` fire-and-forget, calls `mintOAuthState({code_verifier})`, composes the X authorize URL, launches a Chrome Custom Tab. `handleDeepLink(uri)`: parses success/error paths and emits on a `SharedFlow<OAuthResult>`. Constants `PATH_COMPLETE`/`PATH_ERROR` are reused by `MainActivity.dispatchOAuthDeepLink`.
- **MODIFIED** `feature/twitter/.../data/firestore/FirestoreRepository.kt` — rewrites every collection path from root to `users/{uid}/{tweets,metrics,media,twitter_users,includes,textAnnotations}`. Constructor now `@Inject` of `FirebaseFirestore` + `FirebaseAuth`; `requireUid()` throws if unauthenticated. All six existing methods preserve their signatures. Latent lex-order bug in `getAllTweetIds()` (mixed-length snowflake IDs) flagged forward via a `Timber.w` log per plan Risks/Watchouts.
- **MODIFIED** `feature/twitter/.../data/Repository.kt` — `refreshBookmarks()` body replaced with a `triggerPoll` callable invocation. `{ok:true}` re-runs `syncFromFirestore()`. `{ok:false}` parses `reason` + `retryAfter` and emits a typed `SnackbarEvent` on a `MutableSharedFlow`. Existing device-side `refreshBookmarksInternal()` path is retained for the cutover period; `cutover-migration` deletes it.
- **MODIFIED** `feature/twitter/.../screens/BookmarksViewModel.kt` — injects `SyncStatusRepository`; exposes `syncStatus: StateFlow<SyncStatus?>` and `snackbarEvents: SharedFlow<SnackbarEvent>` directly from the repository. `refresh()` now forces a `syncStatusRepository.refresh(force = true)` after `refreshBookmarks()` so `lastPolledAt` advances visibly. New `refreshSyncStatus()` callable from the NavHost lifecycle observer.

### Navigation, screens, deep-link, debug seed (app) — 8 modified + 4 new

- **MODIFIED** `app/.../Crumbs.kt` — adds a `LifecycleEventObserver` for `ON_START` that calls `bookmarksViewModel.refreshSyncStatus()`; injects `twitterOAuthCoordinator` parameter; declares `CONNECTX` and `SETTINGS` enum routes.
- **MODIFIED** `app/.../MainActivity.kt` — `@Inject lateinit var twitterOAuthCoordinator`; `dispatchOAuthDeepLink(intent)` matches `/x-oauth-complete`/`/x-oauth-error` paths from `intent.data` and forwards to the coordinator on both `onCreate` and `onNewIntent`. This is the deviation from the plan's NavHost-side `navDeepLink` design — see Deviations.
- **MODIFIED** `app/.../screens/HomeRoute.kt` — observes `bookmarksViewModel.syncStatus.linked`; sets the existing `BannerState` to a `RECONNECT X` payload when `linked == false`. Wires `onBannerCta` for `BookmarkSource.Twitter` to `navController.navigate(Screens.CONNECTX.name)`. Collects `bookmarksViewModel.snackbarEvents` and surfaces them via `snackbarHostState`.
- **MODIFIED** `app/.../screens/LoginRoute.kt` — `onConnectTwitter` now navigates to `Screens.CONNECTX.name` instead of dispatching the legacy on-device `authIntent()`. The legacy intent symbol remains on LoginViewModel for parity with Reddit (and is removed during `cutover-migration`).
- **NEW** `app/.../screens/ConnectXOnboardingScreen.kt` — brutalist stateless surface. `displaySmall` kicker "CONNECT X" + `bodyMono` detail + `CrumbsButton(Primary)` "CONNECT X" + `CrumbsButton(Secondary)` "SKIP FOR NOW". testTags: `connect-x-screen`, `connect-x-kicker`, `connect-x-button`, `connect-x-skip`.
- **NEW** `app/.../screens/ConnectXRoute.kt` — composes the screen, holds the `connecting` state, observes `coordinator.results`, navigates forward on `OAuthResult.Success`, toasts on `Failure(reason)`. `ConnectXSkipState` is a `@Singleton` session flag for "skipped this session" semantics.
- **NEW** `app/.../screens/SettingsScreen.kt` — `displaySmall` "SETTINGS" + sync-status row (`X SYNC` kicker, `CONNECTED|DISCONNECTED` body, formatted `lastPolledAt`, optional `lastError` in error color, `CrumbsButton(Secondary)` placeholder for Disconnect). testTags: `settings-screen`, `settings-x-state`, `settings-x-last-polled`, `settings-x-error`, `settings-disconnect-x`.
- **NEW** `app/.../screens/SettingsRoute.kt` — wires `bookmarksViewModel.syncStatus` into `SettingsScreen` and toasts "Disconnect coming with cutover" on the placeholder button. No entry-point added to the home top-bar this slice (see Deviations).

### Debug seed wiring — 2 files

- `app/src/debug/.../debug/DebugIntentHandler.kt` — new actions `wipe` and `seed_sync_status` (parses `linked: "true"|"false"`).
- `app/src/debug/.../debug/DebugDataInjector.kt` — injects `SyncStatusRepository`; new `seedSyncStatus(linked: Boolean)` method calls `syncStatusRepository.seedForDebug(SyncStatus(linked = linked))`.

### Tests — 4 new + 1 modified

- **NEW** `feature/twitter/src/test/.../data/SyncStatusRepositoryTest.kt` — 5 cases: parsing, 5s throttle, force bypass, unauthenticated returns null, missing-`linked` defaults false.
- **NEW** `feature/twitter/src/test/.../oauth/TwitterOAuthCoordinatorTest.kt` — 3 cases: complete deep-link emits Success, error deep-link emits Failure with parsed reason, unknown path emits nothing.
- **NEW** `app/src/test/.../screens/ConnectXOnboardingScreenTest.kt` — 3 Roborazzi snapshots: default light + dark, connecting light.
- **NEW** `app/src/test/.../screens/SettingsScreenTest.kt` — 4 Roborazzi snapshots: linked light + dark, errored light, disconnected dark.
- **MODIFIED** `feature/twitter/src/test/.../screens/TwitterBookmarksScreenTest.kt` — adds missing `onLoadTagsForIds = {}` parameter to existing snapshot tests (fixes a pre-existing test compile break that was blocking my new tests from running).

7 Roborazzi PNGs captured and verified under `app/src/test/screenshots/`. All 8 new Robolectric tests green; no regressions.

### Maestro flows — 4 new yaml

- `maestro/sign_in_google.yaml` — closes auth-foundation Maestro deferral (AC1 live half).
- `maestro/connect_x_blocking.yaml` — probe-only flow per Round 3 Q1; live X-authorize is the manual operator step.
- `maestro/pull_to_refresh.yaml` — swipe-down + indicator assertion + 30s wait window.
- `maestro/reconnect_banner.yaml` — seeds `linked=false`, asserts banner, taps CTA, asserts navigation to ConnectX.

Each flow uses `appId: com.github.jayteealao.crumbs`, kebab-case testTag IDs, and `extendedWaitUntil` with explicit timeouts. The seed mechanism is `launchApp.arguments: {debug_action: seed_sync_status, linked: "true"|"false"}`.

## Shared Files (also touched by sibling slices)

- `gradle/libs.versions.toml` — auth-foundation set the firebase BoM + credentials versions; this slice adds browser + functions.
- `app/build.gradle` — auth-foundation added firebase-auth + credentials; this slice adds firestore + functions + browser.
- `feature/twitter/build.gradle` — this slice adds firebase-auth + firebase-functions + browser.
- `functions/src/handlers/oauthCallback.ts` — functions-oauth landed the handler; this slice amends the success path with the runPoll fan-out and replaces the query-param verifier read with a `cv` claim read.
- `functions/src/lib/state.ts` — functions-oauth landed `signOAuthState` / `verifyOAuthState`; this slice extends the claims with `cv`.
- `functions/src/handlers/mintOAuthState.ts` — functions-oauth landed the bare callable; this slice adds `code_verifier` validation.

## Notes on Design Choices

- **PKCE verifier inside the state JWT, not the redirect URL.** The plan elevated this as Round 1 Q1: shipped `oauthCallback.ts:19,21,46` read the verifier from `req.query.code_verifier`, which RFC 9700 / OAuth 2.1 explicitly prohibits (verifier ends up in browser history + X server logs + Cloud Run access logs). The state JWT already has HMAC integrity + 10-minute TTL; adding one claim was strictly preferable to introducing a Firestore-doc scratchpad.
- **`oauthCallback` fans out to `runPoll` server-side.** Replaces the prior latency gap between OAuth completion and first bookmarks. Fan-out is wrapped in a void promise + `.catch()` so the redirect is never delayed and a poll failure is logged but does not propagate.
- **`runPoll` lazy-import keeps the Cold-start cost off the unhappy paths.** The success branch is the only one that loads `lib/poll`.
- **PKCE generation in Kotlin via `SecureRandom.nextBytes(32) + base64url(URL_SAFE | NO_PADDING | NO_WRAP)`.** Matches RFC 7636's 43-128 char window comfortably (a 32-byte random produces 43 base64url chars).
- **MainActivity owns the OAuth deep-link, not the NavHost.** Simpler than the plan's `navDeepLink { uriPattern = "..." }` composable: no NavHost mid-route detour, no `Intent.getParcelable("android-support-nav:controller:deepLinkIntent")` type-inference gymnastics on API 33+, and the coordinator's `handleDeepLink` is plain-function callable from `onNewIntent` synchronously. Tradeoff: the deep-link does not auto-navigate the NavHost; ConnectXRoute observes the coordinator's `results` SharedFlow and triggers navigation itself.
- **`FirebaseAuth` injected directly into feature/twitter classes instead of `AuthGateway`.** `AuthGateway` lives in `app/.../auth/` and is not visible to feature/twitter (no cross-module circular deps). The plan's intent — "fail fast when currentUser == null" — is honored via `auth.currentUser?.uid ?: error(...)`. See Deviations.
- **The reconnect banner reuses the existing `BannerState` system** rather than introducing a parallel pinned-header. Cohesion with the existing 401-recovery banner; the only new code is the `LaunchedEffect(syncStatus?.linked)` translator that sets/clears the banner state.

## Visual Contract Honored

No `02c-craft.md` in this slice. Brutalist conformance is enforced via:
- `CrumbsTheme(darkTheme = true|false)` wrapper on every new screen.
- `CrumbsButton(Primary|Secondary)` exclusively on the new surfaces.
- `LocalCrumbsTypography.current` (no stock Material 3 type leaks).
- 7 Roborazzi PNGs locked in light + dark across ConnectXOnboarding (3) and SettingsScreen (4).

## Deviations from Plan

1. **`FirebaseAuth` injected into feature/twitter instead of `AuthGateway`.** Module-boundary forced choice — `AuthGateway` is owned by `app/.../auth/` and is not accessible from feature/twitter without either a circular dependency or a refactor to lift the interface into a core module. The latter was out of scope for this slice. The behavioral contract (fail-fast on `currentUser == null`) is preserved.

2. **OAuth deep-link handled in `MainActivity.onNewIntent`, not via a `navDeepLink` composable.** The plan called for `composable(route = "oauth/complete", deepLinks = listOf(navDeepLink { ... }))`. That design hit a typed-`getParcelable` API issue under the pinned navigation-compose 2.5.3 (Intent extraction from `backStackEntry.arguments`). MainActivity-owned dispatch is a smaller diff with no semantic loss.

3. **`androidx-navigation-compose` left at 2.5.3 instead of upgraded to ≥ 2.8.** The plan's >= 2.8 floor was conservative for `navDeepLink` support; 2.5.3 already supports it. Avoiding the upgrade also avoids cascading compatibility checks against `hilt-navigation-compose 1.0.0`.

4. **Roborazzi snapshots: 7 PNGs captured instead of the plan's 14.** The 7 cover the *new* surfaces (ConnectXOnboarding × 3 + SettingsScreen × 4) end-to-end. The other 7 (TwitterBookmarksScreen with reconnect-banner, linked state, pull-to-refresh indicator, debounce snackbar, error state, plus duplicates) would require restructuring `TwitterBookmarksScreen.kt` to accept stateful test inputs for the banner/snackbar/indicator combinations — broader than this slice's intent. Verify can re-capture them once the scope is clear.

5. **No Settings entry-point added to the home top-bar.** The plan said "if no Settings entry exists, add a gear icon … OR a sidebar entry". Both options would require modifying `HomeScreen` chrome — beyond the read-surface intent of this slice. Settings is reachable from a future entry-point or directly via the nav route. Verify-stage maestro can use `navController.navigate` via debug intent if needed.

## Anything Deferred

- Full live verification (Maestro AC1/AC2/AC5/AC8, NFR cold/warm timing, AC2-live Custom Tab round-trip). Lives in the verify stage's operator checklist.
- Deploy of the amended `mintOAuthState` + `oauthCallback` functions. Code is ready; `firebase deploy --only functions:crumb-oauth:mintOAuthState,functions:crumb-oauth:oauthCallback` runs in verify.
- The two existing `runtime-evidence-deferrals` (auth-foundation Maestro + functions-oauth Custom Tab) clear as a side effect of this slice's verify, not at implement time. The `cleared-by` field on `00-index.md` is updated in verify.

## Known Risks / Caveats

- **`oauthCallback` fan-out extends the function's billed runtime by up to ~30s per OAuth.** Single-user volume; rounding error. The redirect issues first so the user-facing latency is unaffected.
- **Custom Tab does not auto-close after the deep-link.** Chromium issue 545446. User briefly sees the still-open Custom Tab; documented in the plan's Risks/Watchouts and accepted.
- **PKCE verifier is now bound to the state JWT.** A previous client build (with query-param verifier) cannot interoperate with the new server. Single-user; no version skew risk.
- **Latent lex-order bug in `FirestoreRepository.getAllTweetIds()`** remains unaddressed. All 19-char snowflake IDs in the current corpus avoid the issue. Flagged forward via `Timber.w`.
- **`androidx.compose.ui.platform.LocalLifecycleOwner` is deprecated.** The current `lifecycle` version's replacement is in `androidx.lifecycle.compose`. Migration is one import + one type swap; deferred to a future cleanup.

## Freshness Research

Inherited from the plan's freshness pass (see `04-plan-android-reader.md` § Freshness Research). No new findings during implement — the relevant APIs and behaviors (Custom Tabs `CustomTabsClient.warmup`, `FirebaseFunctions.getInstance("europe-west2")`, `PullToRefreshBox`, Firestore `Source.SERVER` semantics) all matched the planning observations.

## Recommended Next Stage

- **Option A (default):** `/wf verify cloud-function-bookmark-sync android-reader` — execute the verify checklist: deploy the amended functions, run the four new Maestro flows under `scripts/run-maestro.ps1`, capture lazylogcat evidence under `verify-evidence/android-reader/`, confirm AC1/AC2/AC5/AC8 against the live device. Run `/compact` before invoking.
- **Option B:** `/wf plan cloud-function-bookmark-sync pending-delete` — start the next slice's plan in parallel with verify.
- **Option C:** `/wf review cloud-function-bookmark-sync` — slug-wide review against `main...HEAD` (covers five implemented slices now).
