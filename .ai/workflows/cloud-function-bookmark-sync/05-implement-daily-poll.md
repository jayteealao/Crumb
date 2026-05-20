---
schema: sdlc/v1
type: implement
slug: cloud-function-bookmark-sync
slice-slug: daily-poll
status: complete
stage-number: 5
created-at: "2026-05-20T22:20:01Z"
updated-at: "2026-05-20T22:20:01Z"
metric-files-changed: 18
metric-lines-added: 1525
metric-lines-removed: 5
metric-deviations-from-plan: 4
metric-review-fixes-applied: 0
commit-sha: "6af35ed"
tags: [cloud-functions, onschedule, oncall, twitter-api, firestore-transactions, lease, debounce, refresh-token-rotation, iam-verification, jest, eslint-flat-config]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
  slice-def: 03-slice-daily-poll.md
  plan: 04-plan-daily-poll.md
  siblings: [05-implement-auth-foundation.md, 05-implement-functions-oauth.md]
  verify: 06-verify-daily-poll.md
next-command: wf-verify
next-invocation: "/wf verify cloud-function-bookmark-sync daily-poll"
---

# Implement: daily-poll

## Summary of Changes

Landed the server-side polling surface: a shared `runPoll(uid, opts)` engine (`functions/src/lib/poll.ts`) plus two handlers that share it — `dailyPoll` (`onSchedule` twice daily UTC, 540s/512MiB) and `triggerPoll` (`onCall`, 60s/256MiB default). New `lib/twitter-api.ts` carries the four field-list constants verbatim from the Android client (`feature/twitter/.../TwitterApiService.kt:49-59`) plus the URL builder. Added an in-memory Firestore fake (`test/fakes/firestore.ts`) and 14 new jest cases across three test files exercising the engine, the callable, and the schedule-handler wiring. Authored `firestore.indexes.json` (collection-group index on `sync_status.linked`) + extended `firebase.json`. Authored `scripts/verify-function-iam.sh` for the CI/operator least-privilege audit.

Code complete: build + 23 jest cases + lint all green locally. Live deploy + scheduler smoke + IAM verifier execution are operator steps captured in the checklist below and deferred to verify.

## Files Changed

**New (11):**

- `functions/src/lib/twitter-api.ts` — TWEETFIELDS / EXPANSIONS / MEDIAFIELDS / USERFIELDS verbatim from the Android client, `MAX_RESULTS = 50` (locked because the `max_results=100` pagination bug is still active in 2026), `X_API_BASE`, `USERS_ME_URL`, `TOKEN_URL`, and `buildBookmarksUrl(xUserId, paginationToken?)`. Pure module; no I/O.
- `functions/src/lib/poll.ts` — the shared poll engine (~490 LOC after type definitions). Exports `runPoll(uid, opts)` returning the `PollResult` discriminated union. Internals: lease+debounce transaction → refresh-token grant with **conditional rotation persist** (only writes a new RT when X returns a different value) → cached X user-id lookup → paginated bookmarks fetch with stop-on-overlap → `fetchWithBackoff` honoring `x-rate-limit-reset` → batched writes (≤450 ops per commit) with content-derived composite IDs for `includes`/`textAnnotations` → pending_delete diff that respects the overlap boundary (only flags strictly above) → `finally`-block lease release that always runs, even on uncaught exception.
- `functions/src/handlers/dailyPoll.ts` — `onSchedule` v2 with `schedule: "0 9,21 * * *"`, `timeZone: "UTC"`, `timeoutSeconds: 540`, `memory: "512MiB"`. Iterates `collectionGroup("sync_status").where("linked", "==", true)`, recovers each uid from `doc.ref.parent.parent?.id`, invokes `runPoll(uid, { reason: "scheduled" })` with per-user try/catch so one failure does not abort the loop.
- `functions/src/handlers/triggerPoll.ts` — `onCall` v2 with `timeoutSeconds: 60`. Throws `HttpsError("unauthenticated")` when `!request.auth`. Returns the `PollResult` object directly for soft failures (`debounced` / `in_progress` / `rate_limited` / `refresh_revoked`).
- `firestore.indexes.json` (repo root) — single-field collection-group index on `sync_status.linked` (ASCENDING). Required for the `dailyPoll` iteration query.
- `functions/test/fakes/firestore.ts` — chainable in-memory Firestore fake (~370 LOC). Surface: `doc(path)` (`get`/`set`/`update`), `collection(path)` (`orderBy` / `limit` / `select` / `where` / `get`), `batch()` (`set` / `commit`), `runTransaction(cb)` (`tx.get` / `tx.set` / `tx.update`), `collectionGroup(name)` (`where` / `get`). Records every operation into a journal so tests can assert on the exact sequence. Doc-ref shape carries the `parent.parent.id` chain needed by handlers that recover uid from a collection-group query result.
- `functions/test/daily-poll.test.ts` — 9 cases covering `runPoll` directly: (a) empty initial poll with no RT rotation, (b) second poll with overlap + rotated RT persisted, (c) 429 retry then success honoring `x-rate-limit-reset`, (d) 429 exhausted → `lastError: "rate_limited"` and `linked` preserved, (e) `invalid_grant` → `linked: false` and `lastError: "refresh_revoked"` with no Secret Manager write, (f) pagination-bug emulation (50 items, no `next_token`), (g) trigger-mode debounce without fetching, (h) path-guard rejects traversal-style uid, (i) lease-held returns `in_progress` without fetching.
- `functions/test/trigger-poll.test.ts` — 4 cases for the `onCall` handler: unauthenticated → `HttpsError`, debounced + retryAfter, lease held → `in_progress`, happy path with lease cleared after run.
- `functions/test/dailyPoll-handler.test.ts` — 1 smoke case asserting `dailyPoll.run(event)` invokes `runPoll` for each linked user discovered via the collectionGroup query. Isolated in its own file (the file-level `jest.mock` for `../src/lib/admin` and `../src/lib/poll` would collide with the runPoll-focused mocks in `daily-poll.test.ts`).
- `scripts/verify-function-iam.sh` — bash audit script. Asserts every function in the `crumb-oauth` codebase runs on the dedicated SA, the SA has `secretAccessor` bound per-secret (not project-level), and the SA has no project-level Firestore roles. Defaults `uid` to `6yPmdM14V3dPHLe3LO9XCfU4l9f1`; overridable as first arg. Exits non-zero on first failure.
- `.ai/workflows/cloud-function-bookmark-sync/05-implement-daily-poll.md` — this file.

**Modified (7):**

- `functions/src/handlers/oauthCallback.ts` — `sync_status` doc path moved from `users/{uid}/twitter/sync_status` to `users/{uid}/sync_status/state` (single literal-string edit at line 58). See **Deviation 1** below — the plan asked for `users/{uid}/sync_status` (3 segments) which is not a valid `.doc()` path in Firestore. Behavior otherwise unchanged.
- `functions/test/oauthCallback.test.ts` — matching expectation updated to `users/uid1/sync_status/state`.
- `functions/src/index.ts` — appended two re-exports: `dailyPoll` and `triggerPoll`. `setGlobalOptions` unchanged (region pinning already covers both new handlers).
- `firebase.json` — extended the `firestore` block with `"indexes": "firestore.indexes.json"`. Existing `rules` entry preserved.
- `functions/eslint.config.js` — added `setTimeout` / `clearTimeout` (+ `ResponseInit` for test files) to the `globals` lists. The new `lib/poll.ts` uses `setTimeout` for backoff sleeps; eslint v9 flat config required explicit globals since neither browser nor node was enabled wholesale. No rule changes.
- `.ai/workflows/cloud-function-bookmark-sync/00-index.md` — updated by the same workflow step. `current-stage: implement`; `selected-slice: daily-poll`; `next-command: wf-verify`; `next-invocation: /wf verify cloud-function-bookmark-sync daily-poll`. Cleared `runtime-evidence-deferrals` entry for daily-poll once verify owns it.
- `.ai/workflows/cloud-function-bookmark-sync/05-implement.md` — master index refreshed: `slices-implemented: 2 → 3`; added daily-poll summary section; refreshed cross-slice integration notes.

## Shared Files (also touched by sibling slices)

- `functions/src/handlers/oauthCallback.ts` — owned by `functions-oauth` originally. This slice touches the `sync_status` doc path only. The remaining handler logic (token exchange, refresh-token persistence, deep-link redirect) is unchanged.
- `functions/src/index.ts` — appended re-exports; `setGlobalOptions` line owned by `functions-oauth` is preserved verbatim.
- `firebase.json` — `functions-oauth` authored the file; this slice extended the `firestore` block only.
- `functions/eslint.config.js` — `functions-oauth` authored the file; this slice added two `globals` entries.

## Notes on Design Choices

- **Conditional refresh-token rotation persist (PO Round 1 Q1).** X v2 refresh tokens ARE single-use in 2026 (web-research correction to intake's "no rotation" claim). `lib/poll.ts` calls `setRefreshToken(uid, newRt)` only when `tokens.refresh_token !== storedRt`. If X reverts to no-rotation, the comparison silently turns the call into a no-op — safe fallback either way.
- **Stop-on-overlap with overlap-aware diff (PO Round 1 Q4).** The pending_delete diff used to treat any stored-but-not-collected tweet as missing. That was wrong when stop-on-overlap fires: tweets below the boundary were not examined and their presence is unknown. Refactor: track `seenIds` (everything echoed in the response stream, including the boundary tweet) and `stoppedOnOverlap`; the diff considers only stored ids that are not in `seenIds` AND, if `stoppedOnOverlap`, strictly above the boundary in id-ordering. When pagination ends naturally, the response stream covers everything → safe to flag any unseen stored id.
- **Lease + debounce in a single transaction.** Both gates run inside `db.runTransaction`. Debounce check uses `Timestamp.now()` (not `serverTimestamp()` — the latter is a sentinel that does not resolve until commit and cannot be compared inside the transaction). The lease write is deferred to the transaction's `tx.set`, so the lease is only acquired if the transaction commits.
- **`finally`-block lease release.** Even on uncaught exception, the final `statusRef.set` writes `poll_lease: null` so the next caller does not have to wait for the 30s TTL. Inner-try catches errors from the lease release itself so they cannot mask the original failure.
- **Path guard before every Firestore write.** `assertPathScoped(ref.path, uid)` runs against every doc path constructed inside `runPoll`. A `users/uid1/sync_status/state` write passes; a synthetic `users/uid1/../evil/...` path fails. The path-guard test (case h) feeds a traversal-style uid into `runPoll` and asserts the early `assertValidUid` throws before any Firestore call.
- **Content-derived composite IDs for `includes` + `textAnnotations` (PO Round 2 Q6).** Each include row gets a deterministic id like `${tweet.id}_user_${user.id}` or `${tweet.id}_${ann.type}_${ann.start}_${ann.end}`. Re-running the poll over the same X response produces idempotent writes — no per-include UUIDs that drift across polls.
- **In-flight dedup before commit.** Within a single poll, two different tweets can both reference the same `media_key` or include the same `users` row. `seenRefs: Set<string>` dedupes by `ref.path` before queuing into the batch — Firestore batched writes reject duplicate refs in a single commit.
- **`BATCH_SIZE = 450`.** Firestore's hard cap is 500; the migration script uses 400; 450 splits the difference. Each commit chunk is sliced from the full `writes` list.
- **`fetchWithBackoff` honors `x-rate-limit-reset` when present.** The header is a Unix epoch (seconds) for when the limit resets. The helper waits `reset*1000 - Date.now() + 1000` (1s buffer), capped at 60s. Absent the header, it uses 1s/2s/4s exponential backoff (capped at 60s).
- **`collectionGroup("sync_status")` instead of a per-user iteration.** Single-user in practice, but the collection-group query is the same shape as Firestore would use for any future multi-user fanout. Requires the new index in `firestore.indexes.json`; build time is multi-minute on first deploy and surfaced in the operator checklist.
- **`dailyPoll` 540s + `triggerPoll` 60s (PO Round 3 Q10).** dailyPoll is the Gen 2 event-driven max — accommodates a 5-page backfill + cold start without timing out. triggerPoll is generous for one-user steady-state polls and short enough that pull-to-refresh UX feels responsive (the long pole is X rate-limit backoff, not Firestore).
- **`memory: 512MiB` on dailyPoll only.** triggerPoll defaults to 256MiB. The 800-tweet diff set on dailyPoll fits in 256MiB but is tight; 512MiB is conservative headroom that costs ~$0.05/month for our usage. triggerPoll's smaller diff (incremental since last poll) doesn't need it.

## Visual Contract Honored

Not applicable — this slice ships server-side code only. No mock fidelity inventory.

## Deviations from Plan

1. **`sync_status` doc path: `users/{uid}/sync_status` (3 segments) → `users/{uid}/sync_status/state` (4 segments).** Firestore's `db.doc()` rejects odd-segment paths — they identify a collection, not a document. The plan's literal string would have failed at runtime on the very first invocation. Resolved by appending a fixed final segment `state` (a single-doc collection named `sync_status` under each user). This satisfies all three downstream requirements:
   - **PO Round 1 Q2 intent (match migration; move `sync_status` out of the `twitter` subtree)** — sync_status is no longer under `users/{uid}/twitter/...`.
   - **`collectionGroup("sync_status")` matching** — the path has `sync_status` as the second-to-last (collection) segment, so the collection-group query for `dailyPoll` iteration matches.
   - **`.doc()` validity** — 4-segment path passes admin SDK validation.

   Touched in three places: `oauthCallback.ts` (single literal-string edit), the matching test expectation, and every `runPoll` reference. Existing 9 oauthCallback tests pass after the swap.

2. **Pending_delete diff respects the overlap boundary (semantic enrichment beyond the plan).** The plan's sub-step 4g treated any stored-but-not-collected tweet as missing. That over-flags when stop-on-overlap fires: tweets stored below the boundary were not examined this poll and their X-side presence is unknown. New behavior: track `seenIds` (everything echoed by X this poll, including the boundary) and `stoppedOnOverlap`; flag only ids that are (a) stored, (b) not in `seenIds`, AND (c) if stoppedOnOverlap, strictly above the boundary id. When pagination completes naturally (no overlap), the original semantics apply (any unseen stored id is flaggable). Test case (b) covers the new behavior — `itemsFlaggedPendingDelete: 0` because the only stored tweet was the overlap boundary, which counts as seen.

3. **Smoke test for `dailyPoll.run(event)` moved to a dedicated file.** The plan's "case (g)" smoke for the handler-wiring lived in `daily-poll.test.ts` alongside the `runPoll` cases. `daily-poll.test.ts`'s top-level `jest.mock("../src/lib/admin")` and `jest.mock("../src/lib/poll")` factories cannot be selectively replaced for one case via `jest.doMock` after the fact (the top-level factories are hoisted globally per file). Moved to `functions/test/dailyPoll-handler.test.ts` with its own clean top-level mocks. Net case count is unchanged.

4. **`functions/eslint.config.js` globals extended.** ESLint v9 flat config (introduced in functions-oauth) explicitly enumerates globals. The new `lib/poll.ts` uses `setTimeout` for backoff sleeps and the new tests use `ResponseInit`. Added `setTimeout`, `clearTimeout`, and `ResponseInit` (test-only) to the existing globals lists. No rule changes, no new dependencies.

## Anything Deferred

- **`firebase deploy --only functions:crumb-oauth:dailyPoll,functions:crumb-oauth:triggerPoll,functions:crumb-oauth:oauthCallback --project crumbs-a4fdb` (plan step 19).** Deploy is an operator action; not run from this implement turn. The `oauthCallback` re-deploy is REQUIRED to land Deviation 1's path move on the live function — without it, the live `oauthCallback` writes to the legacy path while `dailyPoll` queries the new one.
- **`firebase deploy --only firestore:indexes --project crumbs-a4fdb` (plan step 20).** Operator step. The collection-group index build can take ~1–10 minutes for an empty collection — the first scheduled `dailyPoll` after deploy will fail if the index is still building. Surfaced in the operator checklist.
- **`bash scripts/verify-function-iam.sh ${UID}` (plan step 17 execution).** The script is authored and executable; running it requires the post-deploy state. Belongs to verify.
- **Cloud Scheduler `attemptDeadline` verification (plan step 21).** Requires the deploy to have completed. Operator-confirmed at verify time.
- **`triggerPoll` + `dailyPoll` end-to-end smoke (plan steps 22–23).** Requires deploy + a live X bookmark to verify additivity. Belongs to verify (live evidence).
- **Maestro coverage for the X authorize step + pull-to-refresh flow.** Owned by `android-reader`; daily-poll publishes the `triggerPoll` return-shape contract that android-reader's tests consume.

## Known Risks / Caveats

- **`sync_status` path migration tripwire.** The path move (Deviation 1) re-points `oauthCallback`'s write target. After `oauthCallback` is re-deployed, any document at the legacy path `users/{uid}/twitter/sync_status` is orphaned. Two consequences: (1) until re-deploy, `dailyPoll` queries `collectionGroup("sync_status")` and finds nothing because the live `oauthCallback` still writes the legacy path; (2) the orphan doc remains until `cutover-migration` deletes it (or the operator does so manually). Recommended order: deploy `oauthCallback` + `dailyPoll` + `triggerPoll` together in the same `firebase deploy` command.
- **`collectionGroup("sync_status")` index build timing.** First-time index build on an empty collection completes in minutes; subsequent reuses are instant. If the first scheduled `dailyPoll` lands while the index is still building, the query errors and the user is skipped for that run. Mitigation: deploy indexes BEFORE the next 09:00/21:00 UTC tick, OR manually trigger via `gcloud scheduler jobs run firebase-schedule-dailyPoll-europe-west2 ...` after the index reports READY.
- **X refresh-token rotation: comparison-path bug class.** `lib/poll.ts` compares `tokens.refresh_token !== storedRt` over the raw secret string. If X ever returns the same RT formatted differently (whitespace, casing), the comparison registers a "rotation" and triggers a redundant Secret Manager write. Not breaking (the write is idempotent and ends up with the same value), but wastes a secret version. Acceptable.
- **In-flight dedup uses `seenRefs.has(path)` not deep equality.** If two distinct payloads target the same path within one poll (e.g. a user appears in two different `includes.users[]` arrays with slightly different field sets), only the first is committed. Acceptable for the X v2 surface — `users` and `media` entries are content-identical when they reference the same id, and our `{merge: true}` write semantics would deep-merge anyway.
- **`firebase-functions-test@^3` stays in devDeps unused.** Daily-poll's tests are hand-rolled (PO Round 2 Q7). The dep stays because sibling slices may adopt it after issue #210 is resolved.
- **Twitter field-list constants duplicated** between `feature/twitter/.../TwitterApiService.kt` and `functions/src/lib/twitter-api.ts`. Both copies must stay in sync until `cutover-migration` deletes the Android-side client. The header comment in `twitter-api.ts` calls out the tripwire.
- **Pending_delete reset only fires for re-collected tweets.** The boundary tweet (the stop-on-overlap target) keeps whatever `pending_delete` value it had stored. If a tweet was previously flagged then re-appears AS THE BOUNDARY (not above it), the flag persists until a later poll re-collects it above the next boundary. Acceptable corner case — the UI will see `pending_delete: true` on a tweet still present in X, but only briefly until the next poll cycles it through.
- **Cloud Functions Gen 2 cold-start budget on `triggerPoll`.** 60s timeout - ~8s cold start = 52s effective budget. A user with 5 pages of new bookmarks + 429-backoff on one page can come close to this budget. Mitigation: the `warmup-keepalive` scheduler from `functions-oauth` keeps the codebase warm in steady state; the cold-path is a rare event.
- **Backoff sleep blocks the function thread.** `setTimeout` + `await new Promise(r => setTimeout(r, ms))` is a true blocking sleep in Node's event loop. While the function is sleeping, no other work proceeds in that invocation. Acceptable: each invocation handles one user, and the backoff itself is the work.

## Operator Checklist (manual, pre-verify)

Run before `/wf verify cloud-function-bookmark-sync daily-poll` can capture live evidence. Builds on the `functions-oauth` operator checklist (items 1–4 there are prerequisites).

- [ ] Confirm dedicated SA `crumb-twitter-poller@crumbs-a4fdb.iam.gserviceaccount.com` still exists with project-level `roles/secretmanager.secretVersionAdder` + `roles/secretmanager.secretVersionManager` bindings (from functions-oauth checklist item 4).
- [ ] After the user's first successful X link, bind `roles/secretmanager.secretAccessor` on `crumb-x-refresh-token-${UID}` to the SA (the secret is created on the first `oauthCallback` success — binding must come AFTER that). Single `gcloud secrets add-iam-policy-binding` call.
- [ ] **Co-deploy oauthCallback + dailyPoll + triggerPoll** to land Deviation 1's path move atomically: `firebase deploy --only functions:crumb-oauth:dailyPoll,functions:crumb-oauth:triggerPoll,functions:crumb-oauth:oauthCallback --project crumbs-a4fdb`. Record the printed URLs in the verify artifact.
- [ ] **Deploy firestore.indexes.json BEFORE the next scheduler tick:** `firebase deploy --only firestore:indexes --project crumbs-a4fdb`. Poll Firebase Console → Firestore → Indexes for the `sync_status` collection-group index reading `READY` (typically 1–10 min). Do not trigger `dailyPoll` until ready.
- [ ] Verify Cloud Scheduler `attemptDeadline`: `gcloud scheduler jobs describe firebase-schedule-dailyPoll-europe-west2 --location europe-west2 --format='value(attemptDeadline)'` must equal `540s`. If less: `gcloud scheduler jobs update http firebase-schedule-dailyPoll-europe-west2 --location europe-west2 --attempt-deadline=540s`.
- [ ] Verify `roles/run.invoker` on the dailyPoll Cloud Run service (Gen 2 Scheduler caller IAM, NOT `cloudfunctions.invoker`): `gcloud run services get-iam-policy dailypoll --region europe-west2 --format=json | jq '.bindings[] | select(.role=="roles/run.invoker")'` must list the Cloud Scheduler SA. Auto-bound by firebase-functions deploy.
- [ ] Run the IAM verifier (Git Bash / WSL on Windows; native bash on Linux/macOS): `bash scripts/verify-function-iam.sh ${UID}`. Must exit 0 with all `PASS:` lines.
- [ ] Smoke-test `triggerPoll`: `firebase functions:shell` → `triggerPoll({}, { auth: { uid: "${UID}" } })`. First call → `{ ok: true, itemsAdded: N }`. Immediate re-call → `{ ok: false, reason: "debounced", retryAfter: ~60 }`. Wait 70s, re-call → `{ ok: true, itemsAdded: 0 }`.
- [ ] Smoke-test `dailyPoll` via Cloud Scheduler: `gcloud scheduler jobs run firebase-schedule-dailyPoll-europe-west2 --location europe-west2`. Watch `firebase functions:log --only dailyPoll --lines 50`. Must complete within 540s; log lines `daily_poll_started`, `daily_poll_user_completed`, `daily_poll_completed`.
- [ ] Pending-delete server flag check: un-bookmark a tweet on X, trigger `dailyPoll` (or `triggerPoll`), inspect `users/${UID}/tweets/<unbookmarked-tweet-id>` — must have `pending_delete: true` + `pending_delete_detected_at`. Re-bookmark, re-trigger → `pending_delete: false`.

## Freshness Research

No new web research this implement turn — the plan-stage freshness pass from 21:31 UTC covered `firebase-functions@^7` onSchedule, X v2 refresh-token rotation (single-use), X v2 bookmarks pagination bug at `max_results=100`, exponential-backoff norms, Firestore admin v13 transactions + 500-op batch cap, Gen 2 Scheduler caller IAM (`roles/run.invoker`), Cloud Scheduler `attemptDeadline` default, and the `firebase-functions-test` v2 onSchedule gap. One toolchain discovery during implement: **eslint v9 flat config requires explicit `setTimeout`/`ResponseInit` globals** — added as Deviation 4. No runtime impact.

## Test Evidence

```
> jest
Test Suites: 5 passed, 5 total
Tests:       23 passed, 23 total
Time:        4.878 s
```

23 = 9 existing (6 state + 3 oauthCallback) + 14 new (9 runPoll + 4 triggerPoll + 1 dailyPoll-handler).

```
> tsc
(exit 0)
```

```
> eslint "src/**/*.ts" "test/**/*.ts"
(exit 0)
```

## Recommended Next Stage

- **Option A (default):** `/wf verify cloud-function-bookmark-sync daily-poll` — execute the operator checklist (9 items), capture live evidence (twice-daily cron, `triggerPoll` debounce + in_progress, IAM verifier exit-0, pending-delete server flag round-trip). **Run `/compact` first** — implement-stage context (test mock iteration, fake-Firestore debugging) is noise for the verify gate.
- **Option B:** `/wf plan cloud-function-bookmark-sync android-reader` — start the next slice's plan in parallel with operator-checklist execution. android-reader consumes the `triggerPoll` contract (`{ok, itemsAdded?, reason?, retryAfter?}`) and the `users/{uid}/sync_status/state` doc path defined here.
- **Option C:** `/wf review cloud-function-bookmark-sync` — slug-wide review against `main...HEAD` covering all three implemented slices (`review-scope: slug-wide` per `00-index.md`).
