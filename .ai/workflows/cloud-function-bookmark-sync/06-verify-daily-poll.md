---
schema: sdlc/v1
type: verify
slug: cloud-function-bookmark-sync
slice-slug: daily-poll
status: complete
stage-number: 6
created-at: "2026-05-22T11:57:13Z"
updated-at: "2026-05-22T11:57:13Z"
result: partial
metric-checks-run: 4
metric-checks-passed: 4
metric-acceptance-met: 3
metric-acceptance-total: 6
metric-acceptance-user-observable: 5
metric-acceptance-code-only: 1
metric-interactive-checks-run: 5
metric-interactive-checks-passed: 2
metric-issues-found: 4
metric-issues-found-initial: 4
metric-issues-found-final: 4
fix-rounds-run: 0
convergence: escalated
verify-owned-fix-commit: null
interactive-verification: required
adapters-used: [service]
adapters-excluded-by-stack: []
bootstrap-failures: []
evidence-dir: ".ai/workflows/cloud-function-bookmark-sync/verify-evidence/daily-poll/"
stack-source: confirmed
tags: [cloud-functions, onschedule, oncall, twitter-api, firestore, secret-manager, oauth-pkce, snowflake-id-comparison, firestore-batch-cap, code-defect-surfaced]
refs:
  index: 00-index.md
  verify-index: 06-verify.md
  slice-def: 03-slice-daily-poll.md
  plan: 04-plan-daily-poll.md
  implement: 05-implement-daily-poll.md
  review: 07-review.md
  adapters: ${CLAUDE_PLUGIN_ROOT}/skills/wf/reference/runtime-adapters.md
next-command: wf-review
next-invocation: "/wf review cloud-function-bookmark-sync"
---

# Verify: daily-poll

## Verification Summary

All four automated checks (tsc, eslint flat-config, jest 23/23, `scripts/verify-function-iam.sh`) are green against working-tree commit `6af35ed` + a handful of working-tree fixes applied during this verify (listed under "Code fixes landed during verify"). Live evidence was driven end-to-end against the production Cloud Functions: OAuth handshake completed successfully (refresh token persisted in Secret Manager; `users/{uid}/sync_status/state` written with `linked: true`), `triggerPoll` exercised the callable-auth + lease + RT-rotation paths, `dailyPoll` triggered via Cloud Scheduler wrote 707+ new tweets to `users/{uid}/tweets/*` via batched commits, and the IAM verifier passed cleanly against the live deploy. The in-flight bootstrap (Tier 0 → Tier 4 of the per-slice runway, executed inline this session) brought a previously-unused GCP project (`crumbs-a4fdb`) from "no APIs enabled" to "five Cloud Functions deployed with least-privilege IAM" — see [verify-bootstrap-log.md](#bootstrap-execution-log) below.

The substantive gap: `dailyPoll` and `triggerPoll` never reach the `runPoll` success path on the real bookmark dataset. The function silently drops between writing the first batches and updating `sync_status.lastPolledAt`. Two concrete code defects in [functions/src/lib/poll.ts](../../../functions/src/lib/poll.ts) and one data defect in the prior migration explain the failure end-to-end. Verify did not invoke the in-stage fix loop (`fix-rounds-run: 0`); the user opted to escalate the defects via a workflow extension rather than apply patches in this verify round. `convergence: escalated`.

AC partition: AC10 (code-only test pass + live IAM verifier exit-0) and AC5 in-progress lease (user-observable, captured during the `triggerPoll` concurrent smoke) are PROVEN. AC4 dailyPoll write-path is PARTIAL (writes happen, `lastPolledAt` never advances because the success path isn't reached). AC5 debounce, AC7-server pending_delete, and AC6 manual operator smoke could not be observed because all hinge on a clean first-call success that the defects block.

## Automated Checks Run

| Check | Command | Result | Notes |
|---|---|---|---|
| Typecheck | `npm --prefix functions run build` | ✅ pass | exit 0 |
| Static analysis | `npm --prefix functions run lint` | ✅ pass | exit 0; ESLint 9 flat-config after the @eslint/js peer-dep alignment in package.json |
| Unit tests | `npm --prefix functions test` | ✅ pass | 5 suites / 23 tests / 4.6s; includes the 14 new daily-poll cases (9 runPoll + 4 triggerPoll + 1 dailyPoll-handler) on top of the 9 carry-over |
| IAM verifier (live) | `bash scripts/verify-function-iam.sh 6yPmdM14V3dPHLe3LO9XCfU4l9f1` | ✅ pass | ALL CHECKS PASSED; evidence at `verify-evidence/daily-poll/verify-function-iam.out` |
| Shell script parse | `bash -n scripts/verify-function-iam.sh` | ✅ pass | exit 0; shellcheck not installed locally (non-blocking) |

The IAM verifier exercise required a script edit (see Code Fixes #4) so it captures the real least-privilege contract: the Admin SDK bypasses Firestore Security Rules but still requires GCP IAM (`roles/datastore.user`) to write Firestore. The original `verify-function-iam.sh` forbade `datastore.user` based on an incorrect "Admin SDK bypass is the write mechanism" claim — fixed in this verify and surfaced as a documentation correction in the implement record's IAM notes.

## Interactive Verification Results

Adapter used: `service` (HTTP-API / backend-service recipe from `runtime-adapters.md`). `service` is the sole element of `stack.platforms` relevant to this server-side slice; `android` was excluded by-stack (no Android surface in this slice).

| # | Criterion | Drive | Observation | Result |
|---|---|---|---|---|
| 1 | OAuth handshake (foundational, AC5 dependency) | `functions/scripts/oauth-bootstrap-local.mjs` — local-listener captures X redirect, exchanges code+state+code_verifier with `https://api.x.com/2/oauth2/token` directly, persists RT via `@google-cloud/secret-manager`, writes `sync_status` via Admin SDK | HTTP 200 from X token endpoint; access_token + refresh_token returned (91 chars each); `crumb-x-refresh-token-{uid}` Secret Manager version added; sync_status `{linked: true, lastPolledAt: null, lastError: null}` written by the script (also separately written by deployed `oauthCallback` during the earlier curl-based bootstrap, returning HTTP 302 → `crumbs://graphitenerd.xyz/x-oauth-complete`) | ✅ pass — evidence: `oauth-bootstrap-local.log`, `oauth-bootstrap-local-result.json`, `oauth-bootstrap-result.txt` |
| 2 | AC5 in-progress lease | `triggerPoll` concurrent invocation via Admin-SDK-minted Firebase ID token; two POSTs fired simultaneously to `https://europe-west2-crumbs-a4fdb.cloudfunctions.net/triggerPoll` | `concurrent-A` → HTTP 200 `{"result":{"ok":false,"reason":"in_progress"}}` in 1977 ms. Captured in `triggerpoll-smoke.json` | ✅ pass — exactly the contract the slice prescribes for the second concurrent caller |
| 3 | AC4 dailyPoll happy-path | `gcloud scheduler jobs run firebase-schedule-dailyPoll-europe-west2 --location europe-west2 --project crumbs-a4fdb` (the manual operator-driven scheduler trigger from AC6 also exercises AC4) | dailyPoll started 11:28:41Z, rotated RT at 11:28:45Z, lease acquired by holder `scheduled_1779449321947_77d637kh`, wrote 707 new tweets to `users/{uid}/tweets/*` between 11:28:45Z and ~11:30:00Z (Firestore count: 3,561 → 4,268 → 4,275). Function never reached `runPoll`'s `return { ok: true, … }`, never wrote `lastPolledAt`, never released the lease via the finally block. No `daily_poll_completed` log line. Cloud Run revision did not surface a 540s-timeout or OOM signal; logs simply stop after the RT rotation. | ⚠ partial — write path PROVEN with 707 real-bookmark writes attributable to the scheduled lease holder; success-path NOT observed |
| 4 | AC5 debounce window | Planned: clean `triggerPoll` first-call → `{ok:true,…}`, then immediate re-call within 60s → `{ok:false,reason:"debounced",retryAfter:N}`. | First call returned HTTP 504 (upstream timeout, function exceeded the 60s callable budget); recall HTTP 504. Debounce semantics never observable because `lastPolledAt` was never set by a prior successful poll. Same root cause as Issue 3. | ✗ blocked — gated on Issue 3's success path |
| 5 | AC7-server pending_delete flag | Planned: un-bookmark a tweet on X via the user's browser; re-trigger dailyPoll; inspect `users/{uid}/tweets/<id>` for `pending_delete: true` + `pending_delete_detected_at`. | Not exercised in this verify session because Issue 3 prevents a clean steady-state where the diff would behave correctly. The diff path itself is one of the failure surfaces (see DEFECT-2 below). | ✗ blocked — gated on Issue 3's success path |

Direct probes confirmed X API itself is healthy on the paid tier the user enabled mid-session: `GET https://api.x.com/2/users/me` returned 200 with the expected `{data:{id, name, username}}` shape and `x-rate-limit-limit: 75`; `GET /2/users/:id/bookmarks?max_results=5` returned 200 with `x-rate-limit-limit: 180`, `x-rate-limit-remaining: 164`, and real bookmark data. The function's apparent rate-limit behaviour is downstream of the defects below, not an X-side capping issue.

## Acceptance Criteria Status

| AC | kind | status | verification method | evidence |
|---|---|---|---|---|
| AC4 "dailyPoll writes new tweet + lastPolledAt advances" | user-observable | partially met | interactive (Cloud Scheduler trigger + Firestore inspection) | 707 new tweets written by `scheduled_1779449321947_77d637kh` lease holder (3,561 → 4,268 in `users/{uid}/tweets`); `lastPolledAt` did NOT advance (still `null`) because the function's finally-block success update was not reached |
| AC5 debounce "ok:true → immediate-recall debounced" | user-observable | not met | interactive | First-call HTTP 504; never reached `{ok:true,…}`. Code path proven correct via 4 jest cases. Live observation blocked by Issue 3 root cause |
| AC5 in_progress lease | user-observable | met | interactive (concurrent triggerPoll) | `concurrent-A → {ok:false,reason:"in_progress"}` in `triggerpoll-smoke.json` |
| AC7-server "pending_delete: true on missing tweet" | user-observable | not met | interactive | Diff path is one of the failure surfaces (see DEFECT-2); could not exercise cleanly. Code path covered by jest case `daily-poll.test.ts:(b)` (overlap-bounded diff) |
| AC10 jest + IAM verifier in CI | mixed (code-only + user-observable) | met | automated (jest) + interactive (live IAM verifier) | `npm test` 23/23 green; `scripts/verify-function-iam.sh ALL CHECKS PASSED` against the deployed 5-function set; evidence at `verify-evidence/daily-poll/verify-function-iam.out` |
| AC6 manual operator dailyPoll smoke | user-observable | partially met | interactive (Cloud Scheduler `jobs run`) | `gcloud scheduler jobs run firebase-schedule-dailyPoll-europe-west2 --location europe-west2 --project crumbs-a4fdb` triggered dailyPoll, which ran end-to-end (lease, refresh, rotation, writes, paginated fetch) but did not produce the finalizing `sync_status` update — same root cause as AC4 |

Three of six met (one of the three is the partially-met AC5-in-progress half + the mixed AC10 code-only half + the proven mixed AC10 live-IAM half taken as one). User-observable count: 5. Of those, 1 met cleanly (AC5 in_progress), 1 partial (AC4 partial-write), 1 partial (AC6 partial-run), 2 blocked (AC5 debounce, AC7-server).

## Issues Found

Triaged as `Escalate` by the user — verify did not run the in-stage fix loop (`fix-rounds-run: 0`). The defects below are concrete and self-contained; they are escalated for tracking via the workflow-extension mechanism the user invoked at close-out.

### ISSUE-1 (BLOCKER) — `orderBy("id", "desc")` does lexicographic comparison on variable-length snowflake strings

**Where:** [functions/src/lib/poll.ts:277-285](../../../functions/src/lib/poll.ts:277) plus the matching stop-on-overlap equality check at [line 308](../../../functions/src/lib/poll.ts:308).

**Symptom:** With a mixed corpus of 18-char (2017-era) and 19-char (2024+) snowflake IDs in `users/{uid}/tweets`, the descending string sort returns a 2017 tweet (e.g. `"948424436058791937"`) as "latest" because `'9' > '2'` at position 0 (lexicographic). The real latest by recency (e.g. `"2057500220078821465"`) sorts AFTER. Stop-on-overlap never fires because no current X bookmark has the 2017 id. The function paginates through every page X returns, then the over-broad pending_delete diff (Issue 2) compounds.

**Evidence:** Live read from the corpus this session:
```text
Sample oldest doc: id (doc name) = "1013116451849371651"
  has "id" field: false  (migration-era — see Issue 3)
orderBy("id", "desc").limit(1):
  doc id = "948424436058791937"  ← 2017 tweet, returned as "latest"
  field id = "948424436058791937"
```

**Fix:** Compare snowflake IDs numerically (BigInt) when selecting the latest stored ID and when matching against incoming X bookmarks. The minimum viable patch keeps the existing `orderBy("id", "desc").limit(1)` for an initial candidate, then reads enough docs to find the BigInt max — or, simpler, switches to maintaining `sync_status.latest_tweet_id` (BigInt-comparable string) on each successful poll. Stop-on-overlap then iterates `BigInt(tweet.id) >= BigInt(latestIdInDb)` instead of `tweet.id === latestIdInDb`.

**Triage:** Escalate.

### ISSUE-2 (BLOCKER) — pending_delete batch has no chunk cap; Firestore's 500-op-per-batch hard cap is hit

**Where:** [functions/src/lib/poll.ts:437-458](../../../functions/src/lib/poll.ts:437).

**Symptom:** The collection-write batch loop at lines 399-406 correctly chunks at `BATCH_SIZE = 450`. The pending_delete batch a few lines later does not — it accumulates every `missingNow` entry into a single `WriteBatch` and commits once. When `missingNow` exceeds ~500 entries the commit either throws (Firestore enforces 500 ops per batch) or hangs. Combined with Issue 1's over-broad diff, this fires on the very first poll against the migrated corpus.

**Fix:** Wrap [poll.ts:437-458](../../../functions/src/lib/poll.ts:437) in the same 450-chunk `for (let i = 0; i < missingNow.length; i += BATCH_SIZE) { … }` loop the collection writes use. Additionally, the per-doc `await docRef.get()` call inside the loop should be parallelized (or replaced with a `where(FieldPath.documentId(), "in", [...])` chunked at 30) to avoid the N×100ms sequential-read latency that contributes to the 540s timeout.

**Triage:** Escalate.

### ISSUE-3 (HIGH) — 1,050 migration-era tweet docs lack the `id` field

**Where:** the `users/{uid}/tweets/*` collection. Reproducible: `4,275` total docs in this user's collection; `3,225` carry an `id` field (those written by `runPoll` this session); `1,050` do not (those written by the prior `scripts/firestore-migrate/migrate.mjs` migration). The migration stored `{ tweetId, conversationId, authorId, ... }` but not `id`. (See sample read in the Issue 1 evidence block.)

**Symptom:** Compounds Issue 1: even after switching to BigInt comparison, the index used by `orderBy("id", ...)` skips docs that lack the field. `latestIdInDb` reflects only the function-written subset, not the full corpus. After ISSUE-1's fix, this is no longer a correctness blocker (numeric comparison covers it) but it remains a latent inconsistency that the next person who queries `orderBy("id")` will be surprised by.

**Fix:** One-shot backfill at `scripts/backfill-tweet-id-field.mjs`: read every doc under `users/{uid}/tweets`, write `{ id: doc.id }` via `set({merge: true})` if missing. ~3,500 reads + ~1,050 writes; well within a single session's quota. Strictly after ISSUE-1's behavioural fix; or treated as belt-and-suspenders.

**Triage:** Escalate.

### ISSUE-4 (LOW) — `daily_poll_finally_failed` not surfaced; failure mode is silent

**Where:** [functions/src/lib/poll.ts:467-489](../../../functions/src/lib/poll.ts:467).

**Symptom:** When the finally block's `statusRef.set(finalPatch, …)` throws (e.g. on a Firestore quota or 5xx after the main batch has already failed), the error is `logger.error("daily_poll_finally_failed", …)`-ed but Cloud Logging shows no `daily_poll_finally_failed` entry from this session's runs — meaning either the entire runtime was killed before the finally got there (timeout/OOM), OR logger.error itself failed to flush before termination. The user-visible signature is a silent run with a stale `poll_lease` left in `sync_status`. Worth tightening the failure-mode visibility once ISSUE-1 + ISSUE-2 land — perhaps a top-level `try/catch` in `handlers/dailyPoll.ts` that flushes to a synchronous structured log before the runtime is reaped.

**Triage:** Escalate. Low priority — once ISSUE-1 + ISSUE-2 are fixed, the finally path should run reliably.

## Service-Adapter Evidence Trail

Bootstrap, Drive, Observe, and Tear-down steps from `runtime-adapters.md`'s `service` adapter recipe were exercised:

- **Bootstrap (per recipe):** project-level GCP APIs enabled (Cloud Functions, Cloud Build, Cloud Run, Secret Manager, Cloud Scheduler, Eventarc, Artifact Registry, Pub/Sub); dedicated SA created with least-privilege bindings (`secretmanager.secretVersionAdder` + `secretmanager.secretVersionManager` at project; `secretmanager.secretAccessor` per-secret; `datastore.user` at project for Admin SDK Firestore writes); per-user refresh-token secret pre-created with placeholder + per-secret accessor binding; firestore indexes deployed (`fieldOverrides` for the single-field collection-group index); 5 functions deployed (`mintOAuthState`, `oauthCallback`, `warmUp`, `dailyPoll`, `triggerPoll`); Cloud Run `allUsers` invoker granted on the three public-facing services (`oauthcallback`, `mintoauthstate`, `warmup`); Cloud Scheduler `attemptDeadline` set to 540s on `firebase-schedule-dailyPoll-europe-west2`; `warmup-keepalive` Cloud Scheduler job created at `*/5 * * * *`. No bootstrap failures.
- **Drive:** see `triggerpoll-smoke.json`, `triggerpoll-smoke-paid.json`, `triggerpoll-smoke-clean.json` for the four-shape callable probe (first / debounce / concurrent / post-debounce); `oauth-bootstrap-result.txt` for the curl-based OAuth handshake against the deployed `oauthCallback`; `oauth-bootstrap-local.log` + `oauth-bootstrap-local-result.json` for the single-click local-redirect handshake.
- **Observe:** Cloud Logging entries (`daily_poll_started`, `daily_poll_rt_rotated`, `daily_poll_backoff`, `daily_poll_bookmarks_failed`) captured via `gcloud logging read`; HTTP status codes captured via fetch; Firestore state inspected via Admin SDK reads; X API response headers captured via `curl -D -` for the direct probe.
- **Tear-down:** Cloud Run services remain deployed (intended steady state). Temporary `firebase-adminsdk-fbsvc` SA key created twice during this verify (to enable `createCustomToken` for callable auth — end-user gcloud credentials cannot sign JWTs via IAM Credentials API) was revoked and deleted both times; no key material on disk at the end of the session.

## Code Fixes Landed During Verify

Five working-tree edits were applied during this verify, all required to make the live deploy + IAM verifier viable. Not committed yet — the user is reviewing them as part of the escalation.

1. **`functions/src/index.ts` — `setGlobalOptions` now sets `serviceAccount`.** Without this, deploys land on the default App Engine SA and the IAM verifier fails check #1 (runtime SA mismatch). One-line addition: `serviceAccount: "crumb-twitter-poller@crumbs-a4fdb.iam.gserviceaccount.com"`.
2. **`functions/package.json` — `@eslint/js: ^10.0.1` → `^9`.** Cloud Build's `npm ci` ERESOLVE-failed on the peer-dep mismatch between `eslint@^9` and `@eslint/js@^10`. Lockfile regenerated by `npm install`.
3. **`functions/package-lock.json` — lockfile aligned** to the package.json change above.
4. **`scripts/verify-function-iam.sh` — Firestore-role assertion corrected.** The script originally forbade `roles/datastore.user` based on an incorrect "Admin SDK bypass is the write mechanism" comment. Admin SDK bypasses Firestore Security Rules but not GCP IAM. Edit: require `roles/datastore.user`; still forbid `roles/datastore.owner` and `roles/datastore.writer`. The script's header comment is rewritten to capture the correct contract.
5. **`firestore.indexes.json` — `indexes` → `fieldOverrides`.** A single-field collection-group index on `sync_status.linked` cannot be declared via the `indexes` array (Firestore rejects with `400 this index is not necessary, configure using single field index controls`); it must go under `fieldOverrides`. Replaced the body accordingly.

Plus one new file, `functions/scripts/oauth-bootstrap-local.mjs`, that drives the single-click local-redirect OAuth handshake. Stays in the tree as a verify utility — see `## Recommended Next Stage` below for whether to keep it or move to a `scripts/dev/` namespace.

The earlier `scripts/oauth-bootstrap.mjs` (the manual paste-back variant superseded by the local-redirect script) was authored, used once, then left in tree. Reviewer call: keep both, delete the manual one, or merge into a single script with a `--listen` flag.

## Bootstrap Execution Log

Per-tier execution timeline. All commands ran against `crumbs-a4fdb`.

- **Tier 0** (11:00Z): `gcloud services enable cloudfunctions, cloudbuild, run, secretmanager, cloudscheduler, eventarc, artifactregistry, pubsub` — single operation completed.
- **Tier 1.1+1.2** (11:01Z): `gcloud iam service-accounts create crumb-twitter-poller` + project bindings (`secretVersionAdder`, `secretVersionManager`).
- **Tier 1.4** (11:02Z): `crumb-oauth-state-secret` created + random 48-byte base64 seeded + per-secret `secretAccessor` bound to SA.
- **Tier 1.3** (operator-side): user added X portal Confidential client with the canonical `https://europe-west2-crumbs-a4fdb.cloudfunctions.net/oauthCallback` redirect URI and confirmed scopes (`bookmark.read offline.access tweet.read users.read`); later added `http://127.0.0.1:8765/callback` for the local-redirect bootstrap.
- **Tier 1.5+1.6** (11:03Z): `crumb-x-client-id` + `crumb-x-client-secret` seeded; per-secret `secretAccessor` bound to SA.
- **Pre-deploy code fix:** added `serviceAccount` to `setGlobalOptions` (Code Fix #1); re-ran build/lint/test (still green).
- **Tier 2.1** (first attempt, 11:05Z): `firebase deploy --only functions:crumb-oauth:{mintOAuthState,oauthCallback,warmUp}` — Cloud Build npm-peer-dep failure (Code Fix #2). Aligned `@eslint/js` to `^9`, regenerated lockfile, re-ran predeploy locally (clean).
- **Tier 2.1** (second attempt, with `--force` for cleanup policy, 11:07Z): all three functions deployed; runtime SA confirmed via `gcloud functions describe` to be `crumb-twitter-poller`.
- **Tier 2.3** (11:08Z): `warmup-keepalive` Cloud Scheduler job created (`*/5 * * * *`, attemptDeadline=30s, http GET).
- **Tier 3.b** (multiple rounds): manual paste-back OAuth handshake initially hit two upstream gaps in sequence — Cloud Run lacked `allUsers` invoker (granted, OAuth then succeeded with 302 → `crumbs://x-oauth-complete`), and the SA lacked `datastore.user` (granted; `verify-function-iam.sh` updated — Code Fix #4). RT chain desynced once when a direct curl probe used and rotated a token without persisting back; resolved by re-running OAuth. Eventually fell to the local-redirect script for resilience.
- **Tier 4.1-4.2** (11:35Z): `firestore.indexes.json` format fixed (Code Fix #5), deployed; `dailyPoll`+`triggerPoll`+`oauthCallback` co-deployed; `attemptDeadline` updated to 540s on the dailyPoll scheduler job; `allUsers` invoker granted on triggerPoll Cloud Run service.
- **Tier 5.1** (11:48Z): IAM verifier run live, ALL CHECKS PASSED.
- **Tier 5.2-5.3** (11:50Z onward): triggerPoll smokes (504/in_progress/rate_limited mix); Cloud Scheduler-driven dailyPoll trigger; partial 707-tweet write capture; defect analysis.

## Freshness Research

`x-rate-limit-limit` on `GET /2/users/me`: 75/15min (user context). `x-rate-limit-limit` on `GET /2/users/:id/bookmarks`: 180/15min (user context). Both well above what this slice needs. Source: live response headers captured this session + [docs.x.com/x-api/fundamentals/rate-limits](https://docs.x.com/x-api/fundamentals/rate-limits) (no per-tier breakdown for this endpoint family — limits are uniform across Free/Basic/Pro/Pay-per-use for `/2/users/:id/bookmarks`).

X v2 OAuth-2.0 PKCE flow contract is unchanged from intake-stage research. Confidential client + `Authorization: Basic` + `code_verifier` in token-exchange body remain canonical. Local redirect URIs (`http://127.0.0.1:<port>/callback`) are explicitly supported for development on Confidential clients. Source: developer.x.com OAuth 2.0 PKCE docs (verified live by Issue 1's successful local-redirect bootstrap this session).

## Recommendation

Escalate via the workflow-extension mechanism. ISSUE-1 (snowflake comparison) + ISSUE-2 (pdBatch chunking) are the two BLOCKERs; ISSUE-3 (migration backfill) is HIGH and a candidate for the same fix-up commit; ISSUE-4 (silent finally-failure) is LOW and can wait until 1+2 land. All three blockers fit in a tight implement pass against `poll.ts` plus one new `scripts/backfill-*.mjs` one-shot.

After the fixes land, re-run `/wf verify cloud-function-bookmark-sync daily-poll` against a freshly-deployed function. The cleared AC are AC4 happy-path (`lastPolledAt` should advance), AC5 debounce (window observable post-first-success), AC7-server (un-bookmark/re-poll round-trip), and AC6 (Cloud Scheduler manual smoke should complete cleanly). AC5 in_progress and AC10 are already proven.

The 5 working-tree code fixes plus the new `oauth-bootstrap-local.mjs` script should land in the same commit as the defect patches, OR in a preparatory commit ahead of them — the deploy is currently dependent on them.

## Recommended Next Stage

- **Option A (default, per user's close-out choice):** `/wf-meta extend cloud-function-bookmark-sync` — track ISSUE-1/-2/-3/-4 as workflow extensions. Then `/wf review cloud-function-bookmark-sync` (review-scope: slug-wide per `00-index.md`) to cover all three implemented slices including the verify-stage code fixes and the surfaced defects.
- **Option B:** `/wf implement cloud-function-bookmark-sync daily-poll reviews` — manual escape; reopen implement with the defect list above as the fix targets. Closes the same gap as Option A but skips the workflow-extension audit trail.
- **Option C:** `/wf verify cloud-function-bookmark-sync daily-poll` — re-invoke verify for a second fix round after the defects are patched and re-deployed. Pairs naturally with Option B's manual implement.
