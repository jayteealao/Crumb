---
schema: sdlc/v1
type: implement-index
slug: cloud-function-bookmark-sync
status: in-progress
stage-number: 5
created-at: "2026-05-19T22:51:34Z"
updated-at: "2026-05-22T18:18:00Z"
slices-implemented: 5
slices-total: 7
metric-total-files-changed: 83
metric-total-lines-added: 4724
metric-total-lines-removed: 233
tags: [firebase-auth, credential-manager, google-sign-in, account-linking, android, hilt, robolectric, roborazzi, cloud-functions, typescript, jose, secret-manager, oauth-pkce, jest, firestore-rules, onschedule, oncall, twitter-api, firestore-transactions, lease, debounce, refresh-token-rotation, iam-verification, bigint-comparison, firestore-in-query, finally-block, migration-backfill]
refs:
  index: 00-index.md
  plan-index: 04-plan.md
next-command: wf-verify
next-invocation: "/wf verify cloud-function-bookmark-sync android-reader"
---

# Implement Index

Master index for the seven-slice implementation chain. Five slices implemented (`auth-foundation`, `functions-oauth`, `daily-poll`, `poll-correctness`, `android-reader`); two slices remain (`pending-delete`, `cutover-migration`).

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

### `pending-delete`, `cutover-migration` *(not implemented this round)*

Each remains in `defined` slice state with no plan yet. `pending-delete` consumes the `pending_delete: true` server flag and the path layout `android-reader` established. `cutover-migration` consumes `lib/secrets.setRefreshToken` and is also responsible for deleting any orphan docs at the legacy `users/{uid}/twitter/...` subtree, plus removing the dead device-side X HTTP wiring left in `Repository.kt` by this slice.

## Cross-Slice Integration Notes

- **`auth-foundation` → `functions-oauth`** *(closed in this round):* `mintOAuthState` validates `request.auth` server-side; the Firestore allowlist gate landed in `firestore.rules` and `config/allowed_emails` is queued as an operator pre-seed before the rules deploy. App side still carries no UID/email literal.
- **`functions-oauth` → `android-reader` (open):** `oauthCallback` reads `code_verifier` from `req.query.code_verifier`. If `android-reader` chooses a different transport for the PKCE verifier (e.g., persisted session doc), the callback handler needs a minor edit in that slice's plan.
- **`functions-oauth` → `daily-poll` *(closed in this round)*:** `lib/secrets.ts` exposes `getRefreshToken` + `setRefreshToken` consumed verbatim. `daily-poll` also re-pointed `oauthCallback`'s `sync_status` write target from `users/{uid}/twitter/sync_status` to `users/{uid}/sync_status/state` (Deviation 1); co-deploy is required to land atomically.
- **`daily-poll` → `android-reader`:** `triggerPoll` returns `PollResult` (`{ok: true, itemsAdded, itemsFlaggedPendingDelete} | {ok: false, reason, retryAfter?}`). `android-reader`'s pull-to-refresh UI consumes this shape verbatim. The `users/{uid}/sync_status/state` doc path is the canonical sync-status target for the Android-side `SyncStatusRepository`.
- **`daily-poll` → `pending-delete`:** the server-side `pending_delete: true` flag is set on tweet docs whose ids are stored locally but absent from the X response stream (above the overlap boundary when stop-on-overlap fires; everywhere otherwise). `pending-delete`'s Room v9→v10 column + query reads this flag.
- **`daily-poll` → `cutover-migration`:** orphan docs may exist at the legacy `users/{uid}/twitter/sync_status` path if any `oauthCallback` invocation landed before the daily-poll deploy. `cutover-migration` owns cleanup. Co-deploy in this round minimizes the orphan window.
- **`functions-oauth` → `cutover-migration`:** `migrateXToken` + `disconnectX` callables and the `verify-function-iam.sh` script all land in that slice. This slice authored only the OAuth onboarding surface.
- **`auth-foundation` → `android-reader`:** `FirebaseAuth.currentUser?.uid` is available via `AuthGateway.currentUser` (exposed as `StateFlow<CurrentUser?>` with `uid` + `email`). The Firestore path rewrite to `users/{uid}/twitter/...` will inject the gateway, not `FirebaseAuth` directly.
- **`auth-foundation` → `cutover-migration`:** the `migrateXToken` callable is invoked from a Hilt-injected coroutine that depends on the authenticated user being present. `AuthUiState.Authenticated` determines runner eligibility.
- **BoM 34.13.0 cascade:** every later slice inherits the post-`.ktx` Firestore API in `feature/twitter` (single file migrated this round). When `android-reader` rewrites `FirestoreRepository.kt` paths, it inherits the migrated imports.

## Recommended Next Stage

- **Option A (default):** `/wf verify cloud-function-bookmark-sync android-reader` — deploy the amended `mintOAuthState` + `oauthCallback`, run the four Maestro flows under `scripts/run-maestro.ps1`, capture lazylogcat evidence, confirm AC1/AC2/AC5/AC8 against the live device. Run `/compact` first. This verify also closes the open `auth-foundation` + `functions-oauth` runtime-evidence-deferrals.
- **Option B:** `/wf plan cloud-function-bookmark-sync pending-delete` — start the next slice's plan in parallel with verify.
- **Option C:** `/wf review cloud-function-bookmark-sync` — slug-wide review against `main...HEAD` covering all five implemented slices.
