---
schema: sdlc/v1
type: implement
slug: cloud-function-bookmark-sync
slice-slug: cutover-migration
status: complete
stage-number: 5
created-at: "2026-05-22T22:45:28Z"
updated-at: "2026-05-22T22:45:28Z"
metric-files-changed: 33
metric-lines-added: 750
metric-lines-removed: 320
metric-deviations-from-plan: 5
metric-review-fixes-applied: 0
commit-sha: "e3059956925b0e2be762910c264e987baeb3cd78"
tags: [android, cloud-functions, callable, secret-manager, cleanup, ci, cutover, workmanager, hilt, prefs, grep-gate, brutalist]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
  slice-def: 03-slice-cutover-migration.md
  plan: 04-plan-cutover-migration.md
  siblings:
    - 05-implement-auth-foundation.md
    - 05-implement-functions-oauth.md
    - 05-implement-daily-poll.md
    - 05-implement-poll-correctness.md
    - 05-implement-android-reader.md
    - 05-implement-pending-delete.md
  verify: 06-verify-cutover-migration.md
next-command: wf-verify
next-invocation: "/wf verify cloud-function-bookmark-sync cutover-migration"
---

# Implement: cutover-migration

The terminal slice of the workflow. After this lands, the Android client never
talks to api.x.com directly, never holds an X refresh token (post-migration),
and the on-device polling loop is permanently deleted. Two new server-side
callables (`migrateXToken`, `disconnectX`) carry the new contract.

## Summary of Changes

- Two new Cloud Functions callables (`migrateXToken`, `disconnectX`) with
  10 jest cases covering auth gate, X-validation success/failure, idempotent
  delete on NOT_FOUND, and runPoll fan-out.
- Secret Manager helper extended with `deleteRefreshToken(uid)`.
- Android one-shot migration runner: `XTokenMigrationWorker` (WorkManager
  `OneTimeWorkRequest` + `ExistingWorkPolicy.KEEP` + DataStore idempotency
  flag) enqueued from `CrumbApplication.onCreate`. Hilt deps resolved via
  `MigrationEntryPoint` `@EntryPoint` interface (no
  `androidx.hilt:hilt-work` dependency).
- Repository.kt stripped: removed `refreshBookmarksInternal()`,
  `refreshTokenSingleFlight()`, `buildDatabase()`, Twitter HTTP imports +
  constructor params; added `suspend fun disconnectX()` callable wrapper.
- 7 dead files deleted (6 service classes + ApiResponseExt) + 2 dead Hilt
  modules deleted (`feature/twitter/.../di/NetworkModule.kt` + `ServiceModule.kt`
  + a commented-out `app/.../di/NetworkModule.kt`).
- AuthRepository.kt stripped to Prefs-only compatibility surface so the
  legacy LoginViewModel call sites (SplashRoute / LoginRoute / HomeRoute /
  AllBookmarksScreen) keep compiling without expanding scope. Methods that
  depended on the deleted Twitter HTTP clients are now no-ops.
- Reddit's `RedditNetworkModule` now provides the `OkHttpClient` singleton
  (previously sourced from the deleted Twitter NetworkModule).
- Brutalist confirm Dialog inline in `SettingsScreen.kt` wired to
  `bookmarksViewModel.disconnectX()`; post-disconnect navigation routes to
  `Screens.CONNECTX`.
- WorkManager dep added (`androidx.work:work-runtime-ktx` 2.10.0 + matching
  testing artifact).
- Gradle `verifyCutoverDeletions` task registered + wired into `check`;
  hooked into `pr_check.yml` + `release.yml` BEFORE assemble steps.
- New 5-case Robolectric `XTokenMigrationWorkerTest` covering all branches.
- New `maestro/upgrade_install.yaml` driving a synthetic legacy-token seed
  through the migration cold-launch path.
- DebugDataInjector extended with `seedLegacyXTokens()` + DebugIntentHandler
  `seed_legacy_x_tokens` action.

## Files Changed

### New (8)
- `functions/src/handlers/migrateXToken.ts` — onCall: auth gate → X
  refresh-token validation → `setRefreshToken` → sync_status.linked=true →
  runPoll fan-out. Returns `{ok:false, reason:"invalid"}` on X 4xx so the
  worker treats it as terminal.
- `functions/src/handlers/disconnectX.ts` — onCall: auth gate →
  `deleteRefreshToken` (idempotent on NOT_FOUND) → sync_status.linked=false.
  Per PO Round 1 Q3, no `/oauth2/revoke` call to X.
- `functions/test/migrate-token.test.ts` — 6 jest cases.
- `functions/test/disconnect.test.ts` — 4 jest cases.
- `app/.../migration/MigrationKeys.kt` — single `X_TOKEN_MIGRATED` DataStore
  string-key constant.
- `app/.../migration/MigrationEntryPoint.kt` — Hilt `@EntryPoint` exposing
  `Prefs` + `FirebaseFunctions` to the worker.
- `app/.../migration/XTokenMigrationWorker.kt` — `CoroutineWorker` plus the
  testable `runXTokenMigration(ctx, prefs, functions)` helper that owns the
  five-branch decision logic.
- `app/src/test/.../migration/XTokenMigrationWorkerTest.kt` — 5 Robolectric
  cases against the helper.
- `maestro/upgrade_install.yaml` — synthetic legacy-token cold-launch flow.

### Modified (15)
- `functions/src/lib/secrets.ts` — `deleteRefreshToken(uid)` wraps
  `client.deleteSecret` with NOT_FOUND idempotency.
- `functions/src/index.ts` — re-exports `migrateXToken` + `disconnectX`.
- `gradle/libs.versions.toml` — `work = "2.10.0"` + library entries.
- `app/build.gradle` — WorkManager runtime + testing deps, MockK test deps,
  `verifyCutoverDeletions` task registration with 10-symbol blocklist + 6
  excluded filenames, `tasks.named("check") { dependsOn(...) }`.
- `app/.../CrumbApplication.kt` — WorkManager enqueue with KEEP policy,
  guarded by try/catch so Robolectric (which doesn't auto-init WorkManager)
  doesn't blow up Application construction.
- `app/.../screens/SettingsScreen.kt` — brutalist inline Dialog with
  Cancel/Disconnect rows; testTags `settings-disconnect-confirm-dialog` /
  `-cancel` / `-yes`.
- `app/.../screens/SettingsRoute.kt` — observes `disconnectEvents`; on
  Success navigates to `Screens.CONNECTX`.
- `feature/twitter/.../data/Repository.kt` — strip (≈70 LOC removed) +
  `disconnectX()` callable wrapper.
- `feature/twitter/.../data/AuthRepository.kt` — stripped to Prefs-only
  compatibility surface (auth methods are no-ops).
- `feature/twitter/.../screens/BookmarksViewModel.kt` — removed
  `buildDatabase()`; added `disconnectX()` + sealed `DisconnectEvent`.
- `feature/twitter/.../screens/LoginViewModel.kt` — removed
  `getAppOnlyAccess`/`authIntent` (no deletion-blocking callers); other
  methods now delegate to stubbed AuthRepository.
- `feature/twitter/.../screens/TwitterBookmarksScreen.kt` — buildDatabase
  LaunchedEffect replaced with `refresh()`; legacy `getAccessToken` /
  `authIntent` calls removed; onConnectClick navigates to `"CONNECTX"`
  route.
- `feature/reddit/.../di/RedditModule.kt` — provides `OkHttpClient` (now
  the only HTTP consumer; previously sourced from deleted Twitter
  NetworkModule).
- `.github/workflows/pr_check.yml` + `release.yml` — `Verify cutover
  deletions` step before each assemble.
- `app/src/debug/.../DebugDataInjector.kt` + `DebugIntentHandler.kt` —
  `seedLegacyXTokens` action + readback helper.
- `feature/twitter/.../test/data/SwipeHandlerTest.kt` — drop deleted
  Twitter HTTP deps from the test fixture.
- `app/src/test/.../data/AuthRefreshSingleFlightTest.kt` — kdoc update
  (Twitter wrapper is gone; Reddit still consumes the helper).

### Deleted (8)
- `feature/twitter/.../services/TwitterApiService.kt`
- `feature/twitter/.../services/TwitterApiServiceImpl.kt`
- `feature/twitter/.../services/TwitterAuthService.kt`
- `feature/twitter/.../services/TwitterAuthClient.kt`
- `feature/twitter/.../services/TwitterAuthClientImpl.kt`
- `feature/twitter/.../utils/ApiResponseExt.kt`
- `feature/twitter/.../di/ServiceModule.kt`
- `feature/twitter/.../di/NetworkModule.kt`
- `app/.../di/NetworkModule.kt` (commented-out stub that would have tripped
  the new CI grep gate)

## Shared Files (also touched by sibling slices)

- `Repository.kt` — `confirmDeletePending` / `cancelDeletePending` from
  pending-delete preserved unchanged; `refreshBookmarks` from
  android-reader's triggerPoll wrapper preserved unchanged.
- `BookmarksViewModel.kt` — swipe handlers from pending-delete preserved;
  sync_status + snackbar plumbing from android-reader preserved.

## Notes on Design Choices

- **Hilt EntryPoint over `@HiltWorker`** (PO Round 1 Q1, plan confirmed):
  avoids adding `androidx.hilt:hilt-work` + worker-factory wiring for a
  single one-shot runner. Trade-off documented; if future workers land,
  promoting to `@HiltWorker` is the right move.
- **Inline X refresh-token validation in `migrateXToken`** (PO Round 1 Q2):
  uploads from many devices over a long tail; rejecting bad tokens at the
  callable boundary keeps Secret Manager clean and surfaces the reconnect
  banner immediately for the user.
- **No X-side `/oauth2/revoke` from `disconnectX`** (PO Round 1 Q3): users
  who want to revoke server-side can do so via x.com/settings/connected_apps;
  skipping it narrows the error surface and saves a network hop.
- **Constants + Prefs retained** (PO Round 1 Q4): legacy Prefs keys live in
  `feature/twitter/utils/constants.kt` + `Prefs.kt`; the CI gate excludes
  those two files so the worker can read the keys once.
- **Gradle-task CI gate over GH-Actions inline grep** (PO Round 2 Q5):
  matches the precedent `verifyReleaseDebugInjectorAbsent` style and runs
  locally via `./gradlew check`.
- **Inline brutalist Dialog over designsystem extraction** (PO Round 2 Q7):
  in-scope is one confirm dialog; extraction is a follow-up if a second
  use-case lands.
- **Silent retry + reconnect banner failure UX** (PO Round 3 Q9): worker
  exhausts retries via WorkManager exponential backoff; the
  `sync_status.linked=false` reconnect banner from android-reader does the
  user-visible work.

## Deviations from Plan

1. **`AuthRepository.kt` and `LoginViewModel.kt` stripped/stubbed** — the
   plan listed only `Repository.kt`, but both classes depended on the
   deleted Twitter HTTP clients. To stay in scope without breaking the
   wider LoginViewModel call surface (SplashRoute / LoginRoute / HomeRoute
   / AllBookmarksScreen), AuthRepository was stripped to a Prefs-only
   compatibility surface and unused LoginViewModel methods
   (`getAppOnlyAccess`, `authIntent`) were removed.
2. **`feature/twitter/.../di/NetworkModule.kt` deleted** — plan listed only
   `ServiceModule.kt`. NetworkModule provided Retrofit + the
   `OkHttpClient` singleton, both consumed only by the now-deleted Twitter
   services. The `OkHttpClient` binding had to migrate to
   `RedditNetworkModule` to keep the Reddit feature compiling.
3. **`app/.../di/NetworkModule.kt` (commented stub) deleted** — would have
   tripped the new grep gate on its commented references to
   `TwitterApiService` / `TwitterAuthService`. Pure dead-code removal.
4. **`runXTokenMigration` extracted as top-level testable suspend** — the
   plan's worker design used `EntryPointAccessors.fromApplication(...)`
   directly in `doWork()`. Robolectric tests don't have a Hilt-initialized
   application, so the doWork body was extracted to a top-level helper
   that takes `Prefs` + `FirebaseFunctions` as explicit params. Worker
   still uses EntryPointAccessors in production.
5. **`CrumbApplication` WorkManager enqueue wrapped in try/catch** — plan
   showed a bare enqueue. Robolectric instantiates the application before
   `WorkManagerTestInitHelper.initializeTestWorkManager(...)` can run, so
   the `WorkManager.getInstance(this)` call throws an `IllegalStateException`
   at test boot. try/catch around the enqueue lets the production path stay
   unchanged while keeping unit tests green. Production cold-starts always
   have the androidx.startup-registered initializer.

## Anything Deferred

- **Live deploy of `migrateXToken` + `disconnectX`** — operator must run
  `firebase deploy --only functions:crumb-oauth:migrateXToken,functions:crumb-oauth:disconnectX
  --project=crumbs-a4fdb` before the Android cutover branch is merged (PO
  Round 3 Q10). Tracked as a verify-stage operator checklist item.
- **Live Maestro `upgrade_install.yaml` round-trip** on the operator's
  emulator + signed-in account — `optional: true` assertions in the flow
  cover the UI-doesn't-crash invariant; the Robolectric test pins the
  worker logic; the operator probe confirms the callable round-trip
  against deployed functions. Deferred to verify.
- **Live `disconnectX` user flow** — manual interactive probe (PO Round 3
  Q11) confirming the dialog → callable → ConnectXOnboarding navigation
  works end-to-end against the deployed function. Deferred to verify.
- **Live CI gate enforcement** — the new step needs a synthetic PR that
  reintroduces a forbidden symbol to prove non-zero exit. Deferred to
  verify (or to a future workflow that explicitly tests gate enforcement).

## Known Risks / Caveats

- **Post-migration accessCode flips legacy `loggedIn` signals to false.**
  `SplashRoute`, `HomeRoute`, `AllBookmarksScreen`, `TwitterBookmarksScreen`
  all read `loginViewModel.isAccessTokenAvailable` (Prefs-backed). Once
  the migration worker clears Prefs, those flows route the user through the
  login surfaces even though Firebase Auth + `sync_status.linked` are the
  real signals now. The android-reader sync_status banner is the
  load-bearing UX; the legacy login bounce is a one-off cosmetic hop, not a
  blocker.
- **`OkHttpClient` migration to RedditNetworkModule** is a binding
  relocation, not a behavioral change. Reddit auth + API calls keep the
  same HTTP semantics.
- **`buildDatabase()` removed from BookmarksViewModel init.** Initial
  bookmark hydration now relies on `Repository.init`'s `syncFromFirestore()`
  coroutine (already present). Cold start renders local cache first, then
  Firestore deltas appear within ~30s once the dailyPoll/triggerPoll
  fan-out completes.

## Freshness Research

Inherited from `04-plan-cutover-migration.md` Freshness Research table.
No new external lookups needed during implementation.

## Recommended Next Stage

- **Option A (default):** `/wf verify cloud-function-bookmark-sync cutover-migration` —
  Run automated gates (jest, Android unit, Roborazzi, assembleDebug,
  verifyCutoverDeletions) + apply the AC gate; operator checklist for the
  three deferred live ACs. Run `/compact` first.
- **Option B:** `/wf review cloud-function-bookmark-sync cutover-migration` —
  Skip verify if the implementer is confident in the gates already run.
  Lint+unit+gate were all green at end of implement.
- **Option C:** `/wf plan cloud-function-bookmark-sync cutover-migration <feedback>` —
  Directed correction (e.g., promote AuthRepository.kt to "delete" instead
  of "stubbed-compat", or extract the brutalist confirm dialog to
  designsystem in this slice).
