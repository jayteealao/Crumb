---
schema: sdlc/v1
type: verify-index
slug: cloud-function-bookmark-sync
status: in-progress
stage-number: 6
created-at: "2026-05-20T06:38:36Z"
updated-at: "2026-05-22T11:57:13Z"
slices-verified: 3
slices-total: 6
tags: [firebase-auth, robolectric, roborazzi, cloud-functions, typescript, jose, secret-manager, oauth-pkce, jest, deferred-interactive, defects-surfaced, snowflake-id-comparison]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
next-command: wf-meta-extend
next-invocation: "/wf-meta extend cloud-function-bookmark-sync"
---

# Verify Index

Master index for the six-slice verification chain. Three slices verified (`auth-foundation`, `functions-oauth`, `daily-poll`); the first two with `result: partial` + `interactive-verification: deferred`; the third with `result: partial` + `convergence: escalated` (live evidence captured, four code/data defects surfaced for tracking). Three slices remain to plan, implement, and verify.

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

### `android-reader`, `pending-delete`, `cutover-migration` *(not verified this round)*

Each remains in `defined` (slice) or unplanned state per the master implement index. Verification deferred to per-slice `/wf verify` invocations.

## Cross-Slice Verify Notes

- **Two open deferrals on `00-index.md` runtime-evidence-deferrals.** Both `auth-foundation` and `functions-oauth` have `cleared-by: null` entries. The `daily-poll` verify this round substantially clears the `functions-oauth` operator-prereq checklist (deployed SA + secrets + per-secret IAM + redirect URI + warmup scheduler + invoker grants + IAM verifier ALL PASS live) — that deferral could be cleared via `/wf-quick probe` once `android-reader` lands the live Custom Tab + deep-link round-trip. `auth-foundation` still owes its Google sign-in + collision-link Maestro coverage to `android-reader`. `/wf ship` will HARD-BLOCK until both clear.
- **Daily-poll defects block ship independently.** Even after the two deferrals clear, `/wf ship` would still need ISSUE-1 + ISSUE-2 (poll.ts defects) resolved — the function does not produce a complete poll cycle on a real bookmark history without those fixes.
- **Auth-foundation ↔ functions-oauth closure** (code-side, this verify round): the function-side allowlist gate at `firestore.rules` (`get(...).data.emails[request.auth.token.email] == true`) closes the forward dependency that `auth-foundation` could not close from the client side. Both slices' code-only AC are met; both slices' user-observable AC remain runtime-deferred.
- **BoM 34.13.0 baseline:** every later slice's verify inherits the BoM bump verified in `auth-foundation`. Compile-time confirmation: `:app:assembleDebug` UP-TO-DATE against the current branch. The functions/ codebase is independent of the Android BoM.
- **Functions toolchain pins** (`firebase-functions@^7`, `firebase-admin@^13`, `@google-cloud/secret-manager@^5`, `jose@^5`, ESLint 9 flat-config, Jest 30 + ts-jest 29) inherit through the rest of the slice chain: `daily-poll` will consume `lib/secrets.ts` + ESLint 9 + Jest 30; `cutover-migration` will consume the same Functions toolchain for `migrateXToken` / `disconnectX` callables.

## Recommended Next Stage

- **Option A (default, per user's daily-poll close-out choice):** `/wf-meta extend cloud-function-bookmark-sync` — track ISSUE-1/-2/-3/-4 from the daily-poll verify as workflow extensions before any review.
- **Option B:** `/wf review cloud-function-bookmark-sync` — review-scope is `slug-wide` per `00-index.md`. Single review pass against `git diff main...HEAD` now covers all three verified slices. Soft warning will surface for two open deferrals plus the four daily-poll defects.
- **Option C:** `/wf implement cloud-function-bookmark-sync daily-poll reviews` — manual escape; reopen implement with the daily-poll defect list as the fix targets.
- **Option D:** `/wf-quick probe cloud-function-bookmark-sync` — slug-wide runtime probe; only useful AFTER the daily-poll defects are fixed AND `android-reader` lands. Premature today.
