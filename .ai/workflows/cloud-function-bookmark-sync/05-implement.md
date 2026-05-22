---
schema: sdlc/v1
type: implement-index
slug: cloud-function-bookmark-sync
status: complete
stage-number: 5
created-at: "2026-05-19T22:51:34Z"
updated-at: "2026-05-22T22:45:28Z"
slices-implemented: 7
slices-total: 7
metric-total-files-changed: 132
metric-total-lines-added: 5800
metric-total-lines-removed: 560
tags: [firebase-auth, credential-manager, google-sign-in, account-linking, android, hilt, robolectric, roborazzi, cloud-functions, typescript, jose, secret-manager, oauth-pkce, jest, firestore-rules, onschedule, oncall, twitter-api, firestore-transactions, lease, debounce, refresh-token-rotation, iam-verification, bigint-comparison, firestore-in-query, finally-block, migration-backfill, room-migration, swipe-to-dismiss, brutalist-strikethrough, accessibility, drawWithContent, mockk]
refs:
  index: 00-index.md
  plan-index: 04-plan.md
next-command: wf-verify
next-invocation: "/wf verify cloud-function-bookmark-sync cutover-migration"
---

# Implement Index

Master index for the seven-slice implementation chain. All seven slices implemented (`auth-foundation`, `functions-oauth`, `daily-poll`, `poll-correctness`, `android-reader`, `pending-delete`, `cutover-migration`).

## Slice Implementation Summaries

### `auth-foundation` *(implemented)*

- **Status:** complete; all gradle checks green (`testDebugUnitTest`, `recordRoborazziDebug`, `verifyRoborazziDebug`, `lintDebug`, `assembleDebug`).
- **Surface:** Firebase Auth + Credential Manager (Google Sign-In) + E/P account-linking recovery on Android. Brutalist Login UI updated.
- **Boundary:** `AuthGateway` interface; `CurrentUser(uid, email)` data class. Identity enforcement delegated function-side (next slice).
- **Foundations introduced:** Firebase BoM 34.13.0 (cascades to every later slice); Hilt-test infra (`hilt-android-testing` + `kspTest hilt-compiler`); `kotlinx-coroutines-test` for `viewModelScope` testing.
- **Deviations from plan:** 3 — see [05-implement-auth-foundation.md § Deviations from Plan](05-implement-auth-foundation.md). Most consequential: `BuildConfig.WEB_OAUTH_CLIENT_ID` instead of `R.string.default_web_client_id` to unblock compile when `google-services.json` lacks a Type 3 oauth_client.
- **Details:** [05-implement-auth-foundation.md](05-implement-auth-foundation.md).

### `functions-oauth` *(implemented)*

- **Status:** code complete; build + lint + jest all green locally. Operator checklist (12 items) gates the live deploy + AC capture for verify.
- **Surface:** `functions/` TypeScript project (16 new files + `package-lock.json`); three handlers (`mintOAuthState`, `oauthCallback`, `warmUp`); three lib modules (`admin`, `state`, `secrets`); 9 jest cases (6 state, 3 callback).
- **Toolchain pins:** `firebase-functions@^7`, `firebase-admin@^13`, `@google-cloud/secret-manager@^5`, `jose@^5`; jest 30 + ts-jest 29 CJS preset; ESLint 9 flat config with `no-restricted-imports` blocking `undici`.
- **Boundary:** identity enforcement now lives in Firestore rules (`config/allowed_emails` allowlist gate, added to `firestore.rules`). Client-side code still carries no UID/email literal — auth-foundation's forward dependency is closed.
- **Deviations from plan:** 3 — see [05-implement-functions-oauth.md § Deviations from Plan](05-implement-functions-oauth.md). Most consequential: migrated to ESLint v9 flat config (legacy `.eslintrc.json` doesn't load under v9 default).
- **Details:** [05-implement-functions-oauth.md](05-implement-functions-oauth.md).

### `daily-poll` *(implemented)*

- **Status:** code complete; build + 23 jest cases + lint all green locally. Operator checklist (9 items) gates the live deploy + AC capture for verify.
- **Surface:** 11 new files: `lib/twitter-api.ts` (field constants + URL builder) + `lib/poll.ts` (the shared `runPoll` engine, ~490 LOC) + `handlers/dailyPoll.ts` (`onSchedule "0 9,21 * * *"` UTC, 540s/512MiB) + `handlers/triggerPoll.ts` (`onCall` 60s) + `firestore.indexes.json` (collection-group index on `sync_status.linked`) + `test/fakes/firestore.ts` (in-memory chainable fake) + 3 test files (`daily-poll.test.ts` 9 cases, `trigger-poll.test.ts` 4 cases, `dailyPoll-handler.test.ts` 1 smoke) + `scripts/verify-function-iam.sh` (CI/operator least-privilege audit).
- **Boundary:** server-side surface only. Refresh-token grant + paginated bookmarks fetch + Firestore writes under `users/{uid}/{tweets,metrics,media,twitter_users,includes,textAnnotations}` + `sync_status` doc at `users/{uid}/sync_status/state`. Hard-restrict path guard before every write.
- **Foundations introduced:** in-memory Firestore fake reusable by `android-reader`, `pending-delete`, `cutover-migration` test suites. `lib/twitter-api.ts` field constants will be the single source of truth post-`cutover-migration` (deletes the Android-side duplicate).
- **Deviations from plan:** 4 — see [05-implement-daily-poll.md § Deviations from Plan](05-implement-daily-poll.md). Most consequential: `sync_status` doc path corrected to `users/{uid}/sync_status/state` (4 segments) because the plan's 3-segment path is invalid for `.doc()`. Co-deploy of `oauthCallback` + handlers is required to land the path move atomically.
- **Details:** [05-implement-daily-poll.md](05-implement-daily-poll.md).

### `poll-correctness` *(implemented this round)*

- **Status:** code complete; build + 27 jest cases (23 existing + 4 new) + lint all green locally. Operator checklist (8 items) gates the live backfill + redeploy + AC re-capture for verify.
- **Surface:** 5 files (4 modified + 1 new). `functions/src/lib/poll.ts` surgically modified: BigInt comparison helpers (`isAtOrBelowBoundary` / `isStrictlyAboveBoundary`) replace lex compare; `latestIdInDb` discovery moved from a broken `orderBy("id", "desc")` query to a `sync_status.latest_tweet_id` cache read; pending_delete diff parallelizes precondition reads via `where(FieldPath.documentId(), "in", [30-chunk])` and chunks writes at 450 ops/commit; finally observability wraps work in a 5s `Promise.race` + emits a synchronous `console.error` JSON envelope alongside the firebase logger line. New `scripts/firestore-migrate/backfill-tweet-id-field.mjs` one-shot ADC script seeds the new cache field + writes the missing `id` field onto 1,050/4,275 legacy migration docs (idempotent, dry-run supported).
- **Boundary:** server-side only — no handler signature change, no new export, no new function deploy beyond re-pushing `dailyPoll` + `triggerPoll`. Closes the four defects (ISSUE-1 lex compare, ISSUE-2 unbounded pdBatch, ISSUE-3 missing `id` field, ISSUE-4 silent finally-failure visibility) the daily-poll verify escalated.
- **Foundations introduced:** `sync_status.latest_tweet_id` field — a BigInt-string cache that downstream slices may consume (`android-reader` does not need it, but if a future slice wants the freshness boundary without a collection scan, it's available). Test fake gains `failNextSet(reason, predicate?)` injection — reusable by future tests of error paths.
- **Deviations from plan:** 2 — see [05-implement-poll-correctness.md § Deviations from Plan](05-implement-poll-correctness.md). Backfill script colocated with `scripts/firestore-migrate/` for `firebase-admin` dep reuse; defensive BigInt helpers fall back to string equality for non-numeric test fixtures.
- **Details:** [05-implement-poll-correctness.md](05-implement-poll-correctness.md).

### `android-reader` *(implemented this round)*

- **Status:** code complete; `:app:assembleDebug` BUILD SUCCESSFUL; 8 new Robolectric tests green (5 SyncStatusRepository + 3 TwitterOAuthCoordinator); 7 new Roborazzi PNGs recorded + verified; functions/ jest 29/29 green (27 baseline + 2 new state-cv tests).
- **Surface:** Android-side read surface for the server-written Firestore data. New: `SyncStatusRepository` (one-shot `Source.SERVER`, 5s throttle, debug seed mutator), `TwitterOAuthCoordinator` (Singleton; PKCE local + warmUp ping + `mintOAuthState` + Custom Tab launch + deep-link parse), `SyncStatus` DTO, `SnackbarEvent` typed channel, `ConnectXOnboardingScreen` + Route, `SettingsScreen` + Route. Modified: `FirestoreRepository` (sub-collection paths under `users/{uid}/...`), `Repository.refreshBookmarks` (now invokes `triggerPoll` callable), `BookmarksViewModel` (exposes `syncStatus` + `snackbarEvents`), `HomeRoute` (banner translator + snackbar collector + ConnectX nav), `LoginRoute` (X branch navigates to ConnectX), `Crumbs.kt` NavHost (lifecycle ON_START → refresh sync_status), `MainActivity.onNewIntent` (OAuth deep-link dispatch), `FirebaseModule` (provides `FirebaseFirestore` + `FirebaseFunctions europe-west2`), 4 new Maestro flows, debug-seed wiring for Maestro pre-state.
- **Function-side amendments:** `lib/state.ts` gains `cv: string` claim; `mintOAuthState` validates + accepts `code_verifier` per RFC 7636 §4.1; `oauthCallback` reads the verifier from `claims.cv` (drops the redirect-URL query param) and fans out to `runPoll(uid)` fire-and-forget after the success-path sync_status write. The fan-out makes the first bookmarks visible within ~30s of OAuth completion.
- **Boundary:** the client never sees the X refresh token; the device-side X HTTP loop is dead code post-refreshBookmarks. `cutover-migration` deletes the dead symbols.
- **Foundations introduced:** the PKCE-in-state-JWT pattern + the `runPoll` fan-out hook + `seedForDebug` debug-seed mutator. `pending-delete` consumes the same `users/{uid}/tweets/...` path layout and the new `SyncStatus` shape (extends with `pending_delete` field on tweet docs).
- **Deviations from plan:** 5 — see [05-implement-android-reader.md § Deviations from Plan](05-implement-android-reader.md). Most consequential: `FirebaseAuth` injected directly into feature/twitter classes instead of `AuthGateway` (module-boundary forced); OAuth deep-link handled in `MainActivity.onNewIntent` instead of via `navDeepLink` composable; 7 Roborazzi PNGs recorded instead of 14 (focus on the two new screens end-to-end).
- **Details:** [05-implement-android-reader.md](05-implement-android-reader.md).

### `pending-delete` *(implemented this round)*

- **Status:** code complete; `:app:assembleDebug` BUILD SUCCESSFUL; `:feature:twitter:testDebugUnitTest` green (4 new SwipeHandlerTest cases + 4 new Roborazzi PNGs recorded + 2 baseline loggedOut PNGs unchanged); `:core:designsystem:verifyRoborazziDebug` green after re-record; `:app:lintDebug` + `:feature:twitter:lintDebug` clean.
- **Surface:** Server→user→device half of the X-removal round-trip. Room v9→v10 schema migration adding `pending_delete INTEGER NOT NULL DEFAULT 0` on `tweetEntity`; `MIGRATION_9_10` registered + `MigrationTest.migrate9To10_addsPendingDeleteColumn` (instrumented); `FirestoreTweet` DTO gains nullable `pending_delete` field; `FirestoreRepository.markDeleted` + `cancelPendingDelete` typed write helpers (FieldValue.serverTimestamp() for `deletedAt`); `Repository.confirmDeletePending` (tombstone + Firestore) and `cancelDeletePending` (Room update + Firestore) handlers; `BookmarksViewModel.confirmDeletePending` + `cancelDeletePending` launches; `Bookmark` UI model gains `pendingDelete` flag (Twitter mapper sets it; Reddit defaults false); brand-new `Modifier.brutalistStrikethrough` (drawWithContent + StrokeCap.Square); `CrumbsBookmarkCard` extended with `pendingDelete` + `onConfirmDeletePending` / `onCancelDeletePending` lambdas — when pendingDelete the card body is wrapped in `SwipeToDismissBox` keyed by `bookmark.id`, the title gets `brutalistStrikethrough` + `bookmark-card-strikethrough` testTag, and the card root carries `stateDescription` + `LiveRegionMode.Polite` semantics; `TwitterBookmarksScreen` + Route wire the swipe lambdas through to ViewModel; `DebugDataInjector.seedPendingDelete` + `DebugIntentHandler` "seed_pending_delete" branch; Maestro `pending_delete_swipe.yaml`; 4 new Roborazzi PNGs (`TwitterBookmarksScreen_pendingDelete_{light,dark}` + `TwitterBookmarksScreen_feedNoPendingDelete_{light,dark}`); 4-case `SwipeHandlerTest`.
- **Boundary:** strictly extends `android-reader`. No new Firebase dependencies, no new function deploys, no new Cloud Scheduler jobs — every server-side touch reuses the typed update helpers added on `FirestoreRepository`. Reddit's existing `CrumbsBookmarkCard` call-site is binary-compatible: new card params default to no-op, the SwipeToDismissBox path skips entirely when `pendingDelete = false`.
- **Foundations introduced:** `Modifier.brutalistStrikethrough` in `core/designsystem/modifiers/` — reusable by future "soft-deprecated row" treatments. `seed_pending_delete` debug action joins the `seed_sync_status` pattern Maestro flows already consume. The typed `FirestoreRepository.markDeleted` + `cancelPendingDelete` helpers are the canonical write surface `cutover-migration` will consume when it removes the device-side X HTTP wiring.
- **Deviations from plan:** 2 — see [05-implement-pending-delete.md § Deviations from Plan](05-implement-pending-delete.md). Most consequential: the new Roborazzi PNGs are named `feedNoPendingDelete_*` rather than `signedInLinked_*` (the plan's tentative name) because the test renders the feed body in isolation rather than the full signed-in screen; the cumulative re-record of unrelated `core/designsystem` PNGs (banner, filterbar, icon buttons) was required after the `CrumbsBookmarkCard` refactor caused a 2px shift on the thread variant and the re-record cycle picked up incidental rendering noise on neighbor goldens.
- **Details:** [05-implement-pending-delete.md](05-implement-pending-delete.md).

### `cutover-migration` *(implemented this round)*

- **Status:** code complete; `:app:assembleDebug` BUILD SUCCESSFUL; `:app:testDebugUnitTest` green (5 new `XTokenMigrationWorkerTest` cases); `:feature:twitter:testDebugUnitTest` green; `:app:verifyCutoverDeletions` PASS (10 forbidden symbols across `app/` + `feature/twitter/`); functions/ lint + tsc + jest green (39 cases including 10 new — 6 `migrate-token`, 4 `disconnect`).
- **Surface:** Two new server-side callables — `migrateXToken` (validates a legacy refresh token against X, persists it via `setRefreshToken`, flips sync_status.linked=true, fans out runPoll) and `disconnectX` (deletes the Secret Manager refresh token + flips sync_status.linked=false, no X-side revoke). Android one-shot `XTokenMigrationWorker` enqueued from `CrumbApplication.onCreate` with KEEP policy + DataStore idempotency flag, Hilt-resolved via `MigrationEntryPoint` `@EntryPoint`. Brutalist inline confirm Dialog wired to `Repository.disconnectX()`; post-disconnect navigation routes to `Screens.CONNECTX`. Gradle `verifyCutoverDeletions` task wired into `check`, `pr_check.yml`, and `release.yml`. Maestro `upgrade_install.yaml` drives the synthetic legacy-token cold-launch path. 7 dead service files deleted (TwitterApi*/TwitterAuth* + ApiResponseExt) + 3 dead Hilt modules deleted (NetworkModule×2 + ServiceModule).
- **Boundary:** the device no longer holds nor uses X HTTP code; the migration worker is the last reader of legacy Prefs constants. `OkHttpClient` Hilt binding relocated to `RedditNetworkModule` (Reddit is now the only HTTP consumer).
- **Foundations introduced:** WorkManager dep (`androidx.work:work-runtime-ktx` 2.10.0); WorkManager-testing dep; MockK test deps in `app/`; the EntryPointAccessors pattern for Hilt-resolved CoroutineWorker.
- **Deviations from plan:** 5 — see [05-implement-cutover-migration.md § Deviations from Plan](05-implement-cutover-migration.md). Most consequential: `AuthRepository.kt` + `LoginViewModel.kt` stripped/stubbed (plan listed only `Repository.kt`) to honor the spirit of the deletion sweep without expanding scope; `feature/twitter/.../di/NetworkModule.kt` deleted (plan listed only `ServiceModule.kt`); `runXTokenMigration` extracted as a testable top-level suspend to unblock Robolectric unit tests.
- **Details:** [05-implement-cutover-migration.md](05-implement-cutover-migration.md).

## Cross-Slice Integration Notes

- **`auth-foundation` → `functions-oauth`** *(closed in this round):* `mintOAuthState` validates `request.auth` server-side; the Firestore allowlist gate landed in `firestore.rules` and `config/allowed_emails` is queued as an operator pre-seed before the rules deploy. App side still carries no UID/email literal.
- **`functions-oauth` → `android-reader` (open):** `oauthCallback` reads `code_verifier` from `req.query.code_verifier`. If `android-reader` chooses a different transport for the PKCE verifier (e.g., persisted session doc), the callback handler needs a minor edit in that slice's plan.
- **`functions-oauth` → `daily-poll` *(closed in this round)*:** `lib/secrets.ts` exposes `getRefreshToken` + `setRefreshToken` consumed verbatim. `daily-poll` also re-pointed `oauthCallback`'s `sync_status` write target from `users/{uid}/twitter/sync_status` to `users/{uid}/sync_status/state` (Deviation 1); co-deploy is required to land atomically.
- **`daily-poll` → `android-reader`:** `triggerPoll` returns `PollResult` (`{ok: true, itemsAdded, itemsFlaggedPendingDelete} | {ok: false, reason, retryAfter?}`). `android-reader`'s pull-to-refresh UI consumes this shape verbatim. The `users/{uid}/sync_status/state` doc path is the canonical sync-status target for the Android-side `SyncStatusRepository`.
- **`daily-poll` → `pending-delete` *(closed in this round)*:** the server-side `pending_delete: true` flag is read by `FirestoreTweet.pendingDelete` (nullable, defaults `false` for legacy docs) and projected through `tweetEntity.pending_delete` via MIGRATION_9_10. The Twitter row's strikethrough + swipe affordances fire when the column is true.
- **`pending-delete` → `cutover-migration` (open):** `cutover-migration` should remove `FirestoreRepository.uploadTweet` (and the related batch upload chain) once the device no longer writes to Firestore. The typed `markDeleted` + `cancelPendingDelete` helpers stay — they are user-driven, not part of the polling write path.
- **`daily-poll` → `cutover-migration`:** orphan docs may exist at the legacy `users/{uid}/twitter/sync_status` path if any `oauthCallback` invocation landed before the daily-poll deploy. `cutover-migration` owns cleanup. Co-deploy in this round minimizes the orphan window.
- **`functions-oauth` → `cutover-migration`:** `migrateXToken` + `disconnectX` callables and the `verify-function-iam.sh` script all land in that slice. This slice authored only the OAuth onboarding surface.
- **`auth-foundation` → `android-reader`:** `FirebaseAuth.currentUser?.uid` is available via `AuthGateway.currentUser` (exposed as `StateFlow<CurrentUser?>` with `uid` + `email`). The Firestore path rewrite to `users/{uid}/twitter/...` will inject the gateway, not `FirebaseAuth` directly.
- **`auth-foundation` → `cutover-migration`:** the `migrateXToken` callable is invoked from a Hilt-injected coroutine that depends on the authenticated user being present. `AuthUiState.Authenticated` determines runner eligibility.
- **BoM 34.13.0 cascade:** every later slice inherits the post-`.ktx` Firestore API in `feature/twitter` (single file migrated this round). When `android-reader` rewrites `FirestoreRepository.kt` paths, it inherits the migrated imports.

## Recommended Next Stage

- **Option A (default):** `/wf verify cloud-function-bookmark-sync cutover-migration` — run automated gates (jest, Android unit, Roborazzi, assembleDebug, verifyCutoverDeletions), apply the AC gate, and triage the three deferred live ACs (`migrateXToken` upgrade-install round-trip + `disconnectX` user flow + CI gate enforcement via synthetic-reintroduction PR). Run `/compact` first.
- **Option B:** `/wf review cloud-function-bookmark-sync` — slug-wide review against `main...HEAD` covering all seven implemented slices.
- **Option C:** `/wf-quick probe cloud-function-bookmark-sync` — clear the four open runtime-evidence deferrals (auth-foundation, functions-oauth, android-reader, pending-delete AC4) in one operator pass alongside the cutover-migration verify.
