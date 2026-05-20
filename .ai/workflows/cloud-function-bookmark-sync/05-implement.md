---
schema: sdlc/v1
type: implement-index
slug: cloud-function-bookmark-sync
status: in-progress
stage-number: 5
created-at: "2026-05-19T22:51:34Z"
updated-at: "2026-05-20T22:20:01Z"
slices-implemented: 3
slices-total: 6
metric-total-files-changed: 48
metric-total-lines-added: 2737
metric-total-lines-removed: 101
tags: [firebase-auth, credential-manager, google-sign-in, account-linking, android, hilt, robolectric, roborazzi, cloud-functions, typescript, jose, secret-manager, oauth-pkce, jest, firestore-rules, onschedule, oncall, twitter-api, firestore-transactions, lease, debounce, refresh-token-rotation, iam-verification]
refs:
  index: 00-index.md
  plan-index: 04-plan.md
next-command: wf-verify
next-invocation: "/wf verify cloud-function-bookmark-sync daily-poll"
---

# Implement Index

Master index for the six-slice implementation chain. Three slices implemented (`auth-foundation`, `functions-oauth`, `daily-poll`); three slices remain (`android-reader`, `pending-delete`, `cutover-migration`).

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

### `android-reader`, `pending-delete`, `cutover-migration` *(not implemented this round)*

Each remains in `defined` slice state with no plan yet. `android-reader` consumes the `triggerPoll` return-shape contract + the `users/{uid}/sync_status/state` doc path defined by `daily-poll`. `pending-delete` consumes the `pending_delete: true` server flag. `cutover-migration` consumes `lib/secrets.setRefreshToken` and is also responsible for deleting any orphan docs at the legacy `users/{uid}/twitter/...` subtree.

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

- **Option A (default):** `/wf verify cloud-function-bookmark-sync daily-poll` — execute the operator checklist (9 items), capture live evidence (twice-daily cron, debounce, in_progress lease, IAM verifier exit-0, pending-delete server flag). Run `/compact` first.
- **Option B:** `/wf plan cloud-function-bookmark-sync android-reader` — start the next slice's plan in parallel with operator-checklist execution.
- **Option C:** `/wf review cloud-function-bookmark-sync` — slug-wide review against `main...HEAD` covering all three implemented slices (`review-scope: slug-wide` per `00-index.md`).
