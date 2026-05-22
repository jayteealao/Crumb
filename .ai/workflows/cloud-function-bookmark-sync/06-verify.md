---
schema: sdlc/v1
type: verify-index
slug: cloud-function-bookmark-sync
status: in-progress
stage-number: 6
created-at: "2026-05-20T06:38:36Z"
updated-at: "2026-05-22T21:28:40Z"
slices-verified: 6
slices-total: 7
tags: [firebase-auth, robolectric, roborazzi, cloud-functions, typescript, jose, secret-manager, oauth-pkce, jest, deferred-interactive, defects-surfaced, snowflake-id-comparison, bigint-comparison, finally-block, migration-backfill, android-reader, custom-tabs, deep-link, pkce-cv-claim, room-v9-v10, swipe-to-dismiss, brutalist-strikethrough, kotlinx-serialization-classpath, verify-owned-fixes]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
next-command: wf-review
next-invocation: "/wf review cloud-function-bookmark-sync"
---

# Verify Index

Master index for the seven-slice verification chain. Six slices verified (`auth-foundation`, `functions-oauth`, `daily-poll`, `poll-correctness`, `android-reader`, `pending-delete`); auth-foundation + functions-oauth + android-reader with `result: partial` + `interactive-verification: deferred`; daily-poll with `result: partial` + `convergence: escalated` (four defects, substantively closed by poll-correctness); poll-correctness with `result: partial` + `convergence: not-needed` (eight of nine AC met live; one AC deferred for an X.com UI interaction); pending-delete with `result: partial` + `convergence: converged` + `interactive-verification: deferred` (5/6 user-observable AC met live via Maestro on a real emulator; AC4 instrumented MigrationTest deferred for a pre-existing kotlinx-serialization classpath mismatch in `androidx.room:room-testing`). One slice remains to plan, implement, and verify (`cutover-migration`).

## Slice Verify Summaries

### `auth-foundation` *(verified — partial, runtime-evidence deferred)*

- **Result:** `partial`. `interactive-verification: deferred` per slice file Risks section.
- **Convergence:** `not-needed`. `metric-issues-found-initial: 0`, `fix-rounds-run: 0`.
- **Checks:** 4/4 pass (`:app:lintDebug`, `:app:testDebugUnitTest`, `:app:verifyRoborazziDebug`, `:app:assembleDebug`); BUILD SUCCESSFUL in 36s.
- **AC:** 2/5 met (code-only AC-3 + AC-4 met; user-observable AC-1, AC-2, AC-5 deferred via explicit mechanism).
- **Deferral mechanism:** AC-1 + AC-2 owed to the `android-reader` slice's live `sign_in_google.yaml` Maestro flow; AC-5 owed to the operator-side Console + SHA-1 checklist surfaced in the per-slice verify file.
- **Details:** [06-verify-auth-foundation.md](06-verify-auth-foundation.md).

### `functions-oauth` *(verified — partial, runtime-evidence deferred)*

- **Result:** `partial`. `interactive-verification: deferred` per slice file Risks section + the 12-item Operator Checklist authored in the implement record.
- **Convergence:** `not-needed`. `metric-issues-found-initial: 0`, `fix-rounds-run: 0`.
- **Checks:** 3/3 pass (`tsc`, `eslint`, `jest`); 9/9 jest cases green in 1.755s against commit `35493b9`.
- **AC:** 2/7 met (code-only AC-3 test suite + AC-4 unauthenticated rejection met; user-observable AC-1, AC-2, AC-5, AC-6, AC-7 deferred via the explicit operator-prereq + cross-slice-dependency mechanism).
- **Deferral mechanism:** AC-1/AC-7 owed to a live deploy + Cloud Logging cold/warm capture; AC-2 owed to deploy + the `android-reader` Custom Tab + deep-link round-trip; AC-5 owed to GCP IAM + Secret Manager seeding (checklist items 1-4); AC-6 owed to X portal `redirect_uri` registration (checklist item 6).
- **Details:** [06-verify-functions-oauth.md](06-verify-functions-oauth.md).

### `daily-poll` *(verified — partial, escalated, four defects surfaced)*

- **Result:** `partial`. `interactive-verification: required` (evidence WAS produced, just not full coverage). `convergence: escalated`.
- **Convergence:** `escalated`. `metric-issues-found-initial: 4`, `metric-issues-found-final: 4`, `fix-rounds-run: 0` — user opted to escalate via workflow-extension mechanism rather than apply patches in-stage.
- **Checks:** 4/4 pass (`tsc`, `eslint`, `jest 23/23`, live `verify-function-iam.sh` ALL CHECKS PASSED against the deployed 5-function set).
- **AC:** 3/6 met (AC5 in_progress lease, AC10 jest+IAM-live, AC4 partial). User-observable: 5; clean PASS: 1; PARTIAL: 2; BLOCKED: 2.
- **Bootstrap landed inline:** GCP project went from "no APIs enabled" to "five Cloud Functions deployed with least-privilege IAM" during this verify — Tier 0 (APIs) → Tier 4 (deploy + indexes + scheduler + invokers) all executed. Five working-tree code fixes applied along the way (functions/src/index.ts serviceAccount, functions/package.json @eslint/js peer-dep, firestore.indexes.json fieldOverrides format, scripts/verify-function-iam.sh datastore-role contract correction, new functions/scripts/oauth-bootstrap-local.mjs single-click handshake).
- **Defects surfaced:** ISSUE-1 lexicographic-vs-numeric snowflake ID comparison in `poll.ts:277-285,308`; ISSUE-2 unbounded `pdBatch` (Firestore 500-op cap) in `poll.ts:437-458`; ISSUE-3 migration docs missing `id` field (1050/4275); ISSUE-4 silent finally-failure visibility gap. ISSUE-1 + ISSUE-2 are BLOCKERs; ISSUE-3 is HIGH; ISSUE-4 is LOW.
- **Details:** [06-verify-daily-poll.md](06-verify-daily-poll.md).

### `poll-correctness` *(verified this round — partial, one AC deferred)*

- **Result:** `partial`. `interactive-verification: deferred` for the pending_delete round-trip AC only.
- **Convergence:** `not-needed`. `metric-issues-found-initial: 0`, `metric-issues-found-final: 0`, `fix-rounds-run: 0`.
- **Checks:** 7/7 pass — `npm run lint` clean, `tsc --noEmit` clean, `jest 27/27` (23 carry-over + 4 new j/k/l/m), `npm run build` succeeds, `bash scripts/verify-function-iam.sh` ALL CHECKS PASSED against the redeployed surface, `backfill --dry-run` clean, `backfill` idempotency clean.
- **Live evidence captured:** (1) backfill wrote `id` field onto 961 of 4,275 docs in 3 batches; seeded `sync_status.latest_tweet_id` to `2057500220078821465`; re-run was a clean no-op. (2) `firebase deploy --only functions:crumb-oauth:dailyPoll,functions:crumb-oauth:triggerPoll` succeeded with the new code. (3) Cloud Scheduler force-run of `dailyPoll` completed in ~10s; `daily_poll_completed` logged; `lastPolledAt` advanced to `2026-05-22T13:41:52Z`; lease cleared; no `daily_poll_finally_failed`. (4) `triggerPoll` via IAM-signed custom token + ID-token exchange: call-1 success-path in 10.4s; call-2 immediately after returned `{ok: false, reason: "debounced", retryAfter: 60}` — debounce contract proven.
- **AC:** 8/9 met (code-only AC for BigInt boundary, chunking, finally observability all jest-proven; user-observable AC4 success-path, AC5 debounce, AC6 server-side storage, AC9 IAM verifier exit-0, backfill idempotency all met live). AC7-server pending_delete round-trip deferred — requires manual un-bookmark + re-bookmark in X.com UI which CLI cannot drive.
- **Daily-poll defects closed:** all four issues from [06-verify-daily-poll.md](06-verify-daily-poll.md) (BLOCKER lexicographic snowflake compare, BLOCKER unbounded pdBatch, HIGH missing `id` field, LOW finally visibility) are substantively resolved end-to-end. The `convergence: escalated` state on daily-poll's verify can now be treated as cleared by this slice's live evidence.
- **New deferral on `00-index.md` runtime-evidence-deferrals:** one entry for `poll-correctness` pending_delete round-trip. Clearing event: `/wf-quick probe` after a manual X.com un-bookmark + re-bookmark cycle.
- **Details:** [06-verify-poll-correctness.md](06-verify-poll-correctness.md).

### `android-reader` *(verified this round — partial, runtime-evidence deferred)*

- **Result:** `partial`. `interactive-verification: deferred` per the user's gate triage decision.
- **Convergence:** `not-needed`. `metric-issues-found-initial: 0`, `metric-issues-found-final: 0`, `fix-rounds-run: 0`.
- **Checks:** 7/7 pass — functions ESLint clean, `tsc --noEmit` clean, jest 29/29 (2 new `cv` claim cases), `:app:lintDebug` + `:feature:twitter:lintDebug` clean, `:app:testDebugUnitTest` + `:feature:twitter:testDebugUnitTest` green (8 new Robolectric cases: 5 `SyncStatusRepositoryTest` + 3 `TwitterOAuthCoordinatorTest`), `:app:verifyRoborazziDebug` + `:feature:twitter:verifyRoborazziDebug` green vs. 7 committed PNG references, `:app:assembleDebug` UP-TO-DATE. Single gradle invocation BUILD SUCCESSFUL in 59s.
- **AC:** 2/8 met (code-only AC11 brutalist conformance + code-only AC bundle for PKCE `cv` round-trip, `runPoll` fan-out mock, `SyncStatusRepository`, `TwitterOAuthCoordinator`); 6 user-observable AC (AC1, AC2, AC2-live, AC5, AC8, NFR) deferred via a single operator-session reason.
- **Deferral mechanism:** Live device + emulator + `jayteealao@gmail.com` Google account + redeployed `mintOAuthState`/`oauthCallback` + live X account round-trip required. Operator runs four Maestro flows (`sign_in_google.yaml`, `connect_x_blocking.yaml`, `pull_to_refresh.yaml`, `reconnect_banner.yaml`) + the manual Custom Tab + deep-link round-trip + Cloud Logging timing capture after `firebase deploy --only functions:crumb-oauth:mintOAuthState,functions:crumb-oauth:oauthCallback`.
- **Cross-slice clearing scheduled:** the same operator probe pass also clears `auth-foundation` Maestro deferral and `functions-oauth` Custom Tab deferral. All three `cleared-by:` fields remain `null` until the probe writes evidence back.
- **Details:** [06-verify-android-reader.md](06-verify-android-reader.md).

### `pending-delete` *(verified this round — partial, converged, AC4 deferred)*

- **Result:** `partial`. `interactive-verification: deferred` for AC4 (instrumented MigrationTest).
- **Convergence:** `converged`. `metric-issues-found-initial: 4`, `metric-issues-found-final: 1`, `fix-rounds-run: 1` — three of four issues resolved by the single fix round; the fourth is a precise actionable infrastructure deferral.
- **Checks:** 5/6 pass — `:app:lintDebug`, `:feature:twitter:lintDebug`, `:feature:twitter:testDebugUnitTest` (4/4 SwipeHandlerTest + 4/4 Roborazzi captures), `:feature:twitter:verifyRoborazziDebug`, `:core:designsystem:verifyRoborazziDebug`, `:app:assembleDebug` all UP-TO-DATE vs commit `c0c9564`. Single composite gradle invocation `BUILD SUCCESSFUL in 3s`.
- **Live evidence captured:** Pixel `Medium_Phone_API_36.0` AVD booted; Maestro `pending_delete_swipe.yaml` exited SUCCESS in 13s after three verify-time fixes (DebugDataInjectorTest constructor arg + Maestro auth-wall handling + DebugDataInjector seedAuthTokens wiring). Three screenshots captured: strikethrough rendered on both seeded rows (AC1 + AC6); swipe-left-confirm removes row 1 and shows the brutalist DELETED/UNDO snackbar (AC2 Room/tombstone side); swipe-right-cancel removes the strikethrough on row 2 with the row staying in normal styling (AC3 Room side).
- **AC:** 5/6 met (AC1, AC2 Room+unit, AC3 Room+unit, AC5 partial, AC6 brutalist conformance); AC4 deferred for a kotlinx-serialization classpath mismatch in `androidx.room:room-testing` that fails ALL `MigrationTest` cases (not just 9→10) at the `createDatabase(name, version)` JSON-deserialization step. Independent code-path proof for AC4: `app/schemas/.../10.json` regenerated cleanly by KSP at implement-time, `:app:assembleDebug` SUCCESSFUL, projection extension proven by SwipeHandlerTest.
- **Verify-owned fixes landed:** (1) `app/src/androidTest/java/com/github/jayteealao/crumbs/debug/DebugDataInjectorTest.kt` — added the new `syncStatusRepository` constructor argument so the androidTest variant compiles; (2) `maestro/pending_delete_swipe.yaml` — rewrote to a single launchApp + the conditional `login-skip-auth` tap + `nav-tab-twitter` + three takeScreenshot evidence captures; (3) `app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugDataInjector.kt` — `seedPendingDelete()` now calls `seedAuthTokens()` so the post-skip-auth `loggedIn` gate passes and the feed actually renders the seeded rows.
- **New deferrals on `00-index.md` runtime-evidence-deferrals:** one entry for `pending-delete` AC4 (kotlinx-serialization classpath in androidTest). The implicit signed-in-Firestore round-trip for AC2/AC3 is owed to the same operator probe session that clears `android-reader` — no separate entry, by design.
- **Details:** [06-verify-pending-delete.md](06-verify-pending-delete.md).

### `cutover-migration` *(not verified this round)*

Remains in `defined` state (planning + implementation not yet done). Verification deferred to a per-slice `/wf verify` invocation after `/wf plan` + `/wf implement`.

## Cross-Slice Verify Notes

- **Five open deferrals on `00-index.md` runtime-evidence-deferrals.** `auth-foundation`, `functions-oauth`, `poll-correctness` (pending_delete server-side round-trip), `android-reader`, and `pending-delete` (AC4 instrumented MigrationTest) all have `cleared-by: null`. A single `/wf-quick probe cloud-function-bookmark-sync` invocation clears three of the five (auth-foundation + functions-oauth + android-reader) in one operator session. `poll-correctness` `pending_delete` stays open until the operator manually un-bookmarks + re-bookmarks a tweet in X.com (and AC2/AC3's signed-in Firestore round-trip rides along on the same session). `pending-delete` AC4 stays open until kotlinx-serialization is aligned in app/build.gradle.kts androidTest deps + the instrumented MigrationTest is re-run. `/wf ship` will HARD-BLOCK until all five clear.
- **Daily-poll defects block ship independently.** Even after the two deferrals clear, `/wf ship` would still need ISSUE-1 + ISSUE-2 (poll.ts defects) resolved — the function does not produce a complete poll cycle on a real bookmark history without those fixes.
- **Auth-foundation ↔ functions-oauth closure** (code-side, this verify round): the function-side allowlist gate at `firestore.rules` (`get(...).data.emails[request.auth.token.email] == true`) closes the forward dependency that `auth-foundation` could not close from the client side. Both slices' code-only AC are met; both slices' user-observable AC remain runtime-deferred.
- **BoM 34.13.0 baseline:** every later slice's verify inherits the BoM bump verified in `auth-foundation`. Compile-time confirmation: `:app:assembleDebug` UP-TO-DATE against the current branch. The functions/ codebase is independent of the Android BoM.
- **Functions toolchain pins** (`firebase-functions@^7`, `firebase-admin@^13`, `@google-cloud/secret-manager@^5`, `jose@^5`, ESLint 9 flat-config, Jest 30 + ts-jest 29) inherit through the rest of the slice chain: `daily-poll` will consume `lib/secrets.ts` + ESLint 9 + Jest 30; `cutover-migration` will consume the same Functions toolchain for `migrateXToken` / `disconnectX` callables.

## Recommended Next Stage

- **Option A (default):** `/wf review cloud-function-bookmark-sync` — review-scope is `slug-wide` per `00-index.md`. Single review pass against `git diff main...HEAD` covers all six verified slices. Soft warning surfaces for the five open deferrals (auth-foundation, functions-oauth, poll-correctness pending_delete round-trip, android-reader, pending-delete AC4).
- **Option B:** `/wf-quick probe cloud-function-bookmark-sync` — slug-wide runtime probe. One operator session captures live AC evidence for `android-reader` + clears the auth-foundation + functions-oauth + android-reader deferrals together; a separate manual X.com bookmark toggle clears the poll-correctness deferral (and inherently the pending-delete signed-in-Firestore round-trip).
- **Option C:** `/wf plan cloud-function-bookmark-sync cutover-migration` — start the final slice's plan in parallel with review.
- **Option D:** Align `kotlinx-serialization-core/json` in `app/build.gradle.kts` androidTest dependencies + re-run `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.github.jayteealao.crumbs.db.MigrationTest#migrate9To10_addsPendingDeleteColumn` — clears the pending-delete AC4 deferral mechanically. Out-of-scope for the slice itself but useful housekeeping before ship.
