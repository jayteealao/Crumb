---
schema: sdlc/v1
type: plan
slug: cloud-function-bookmark-sync
slice-slug: poll-correctness
status: complete
stage-number: 4
created-at: "2026-05-22T12:14:20Z"
updated-at: "2026-05-22T12:14:20Z"
metric-files-to-touch: 5
metric-step-count: 14
has-blockers: false
revision-count: 0
tags: [cloud-functions, poll-engine, bigint-comparison, firestore-in-query, finally-block, migration-backfill, jest]
stack-source: confirmed
refs:
  index: 00-index.md
  plan-index: 04-plan.md
  slice-def: 03-slice-poll-correctness.md
  source: 06-verify-daily-poll.md
  siblings: [04-plan-auth-foundation.md, 04-plan-functions-oauth.md, 04-plan-daily-poll.md]
  implement: 05-implement-poll-correctness.md
next-command: wf-implement
next-invocation: "/wf implement cloud-function-bookmark-sync poll-correctness"
---

# Plan: poll-correctness

Fix the four defects surfaced by `06-verify-daily-poll.md` so `dailyPoll` and `triggerPoll` reach the success path on a mixed-length snowflake corpus. Internally consistent BigInt comparison, chunked + parallelized pending_delete diff, sync_status `latest_tweet_id` cache, and visibility-hardened finally block. Plus a one-shot backfill script seeding the `id` field on legacy migration docs and the new cache.

## Current State

The poll engine (`functions/src/lib/poll.ts`, 490 LOC) was implemented in slice `daily-poll` (commit `6af35ed`). It exports `runPoll(uid, opts)`, consumed by `dailyPoll` (onSchedule) and `triggerPoll` (onCall) handlers in `functions/src/handlers/`. The verify pass found:

- **Line 277-285** — `database.collection("users/${uid}/tweets").orderBy("id", "desc").limit(1).select().get()` returns the *lexicographically*-largest doc id, not the numerically-largest. For 19-char (2024+) and 18-char (2017-era) snowflake strings, `'9' > '2'` at position 0 means an 18-char tweet from 2017 sorts AFTER a 19-char tweet from 2024 — `latestIdInDb` is wrong.
- **Line 308** — `if (latestIdInDb && tweet.id === latestIdInDb)` — string equality. Right operator but the operand is wrong (downstream of the broken `latestIdInDb`).
- **Line 429** — `if (latestIdInDb && id > latestIdInDb)` — string `>` comparison in the pending_delete diff. Same class of bug as line 308; would misclassify "above the boundary" for mixed-length ids even if `latestIdInDb` were correct.
- **Line 437-458** — `pdBatch = database.batch()` with no chunking. For `missingNow.length > 500` (verify saw 3,068 candidates), the single batch exceeds the Firestore 500-op cap → `INVALID_ARGUMENT`. Also: the precondition `await docRef.get()` (line 443) is serial inside `for (const id of missingNow)`, meaning 3,000+ sequential RPCs.
- **Line 467-489** — finally block uses `logger.error("daily_poll_finally_failed", ...)` (firebase-functions logger). The logger buffers async; when the surrounding runtime is being reaped (post-response throttling per Gen 2 docs), the line may never flush. Verify observed silent finally-failures correlated with missing IAM, but logs only surfaced after the SA was granted `roles/datastore.user`.

Existing artifacts this slice consumes verbatim:
- `BATCH_SIZE = 450` constant + the 450-chunked WriteBatch loop pattern at poll.ts:399-406.
- `fetchWithBackoff()`, `releaseLeaseWithError()`, `assertPathScoped()`, `assertValidUid()` helpers in poll.ts.
- `lib/secrets.ts` (`getRefreshToken`, `setRefreshToken`).
- `lib/admin.ts` (`db()` singleton, `preferRest: true`).
- `firebase-functions/v2` logger import.
- `functions/test/fakes/firestore.ts` (353 LOC hand-rolled fake) with `.seed()`, `.journal[]`, full WriteBatch + collectionGroup + `where("__name__", "in")` support.
- `firestore.indexes.json` — only `sync_status.linked` collection-group field override today; this slice does not need a new index (`sync_status.latest_tweet_id` is a top-level field read by uid, no query).
- `tsconfig.json target: "ES2022"` — BigInt literals (`1n`) are fine; `BigInt()` constructor works in any case.

Other consumers of tweet-id ordering across the codebase:
- **`feature/twitter/.../FirestoreRepository.kt:63`** uses `FieldPath.documentId()` on `users/{uid}/tweets`. This is the Android-side display-order read. It will continue to misorder mixed-length tweets in the reader UI (a 2017 tweet appears at the top because `'9' > '2'`). **This is a pre-existing latent bug; not in scope for poll-correctness.** Note in Risks for a future follow-up via `android-reader`. The fix here only changes how `runPoll` *selects* its boundary; Android still reads docs by `FieldPath.documentId()` and that comparison remains lexicographic. No client breakage.

## Reuse Opportunities

- `functions/src/lib/poll.ts` BATCH_SIZE + chunked WriteBatch pattern (poll.ts:399-406) → **reuse as-is** for the new pdBatch chunking. Same idiom, same fake-supported `batchCommit` journal entries the existing tests rely on.
- `releaseLeaseWithError()` (poll.ts:127-144) → **reuse as-is**. The finally-fix only modifies the success-path write + the catch-side observability; lease-release plumbing stays.
- `assertPathScoped()` / `assertValidUid()` → **reuse as-is** in the backfill script (`set({merge: true})` writes to `users/{uid}/tweets/{id}` must stay path-scoped).
- `lib/admin.ts.db()` and Secret Manager helpers → not needed by the backfill script (it uses ADC + `firebase-admin/app` initializeApp).
- `functions/test/fakes/firestore.ts` `.seed()` + `.journal` queries → **reuse as-is** for all four new test cases. No fake extension needed: it already supports `WriteBatch` + `select()` + `where(FieldPath.documentId(), "in", ...)` per the daily-poll suite.
- `scripts/firestore-migrate/migrate.mjs` (referenced in cross-cutting concerns) → **reuse the pattern**, not the code. The backfill script is a fresh `.mjs` file but follows the same ADC + `firebase-admin/app` initializeApp + project-id arg convention.

## Likely Files / Areas to Touch

- `functions/src/lib/poll.ts` (modified) — ISSUE-1 (latest-id discovery + overlap equality), ISSUE-1b (pending_delete boundary), ISSUE-2 (pdBatch chunking + `in`-query precondition read), ISSUE-4 (finally observability), plus a new write to `sync_status.latest_tweet_id` on the success path.
- `functions/test/daily-poll.test.ts` (modified) — four new test cases per the slice file's AC; existing cases inherit the new cache-write but assertions on `lastPolledAt` / `poll_lease: null` are unaffected.
- `functions/test/fakes/firestore.ts` (no change expected) — verify the fake's `where(FieldPath.documentId(), "in", ...)` path returns docs in input order; if it's missing, add a small extension. *Reading the file in Phase 0 will confirm.*
- `scripts/backfill-tweet-id-field.mjs` (new, ~80 LOC) — one-shot Node script: ADC creds, single UID via CLI arg, idempotent `set({merge: true}, {id: doc.id})` writes + sync_status latest_tweet_id seed. Documented in implement record's operator checklist.
- `00-index.md` (modified) — bump `current-stage: plan`, set `selected-slice: poll-correctness`, advance progress for plan stage. Add `04-plan-poll-correctness.md` to `workflow-files`.

## Proposed Change Strategy

Three internal areas inside poll.ts; one new script; four new test cases. All changes are surgical — no architectural reshuffle, no new library dependency.

**Area 1 — BigInt-aware boundary discovery & comparison.** Replace the `orderBy("id", "desc").limit(1)` query at line 277-285 with a single doc-get on `users/{uid}/sync_status/state`, reading the new field `latest_tweet_id` (string). Hybrid bootstrap is NOT taken — per PO decision, the backfill script seeds the cache once, then poll-on-success writes the new max. Replace `tweet.id === latestIdInDb` (line 308) and `id > latestIdInDb` (line 429) with `BigInt(tweet.id) <= BigInt(latestIdInDb)` and `BigInt(id) > BigInt(latestIdInDb)` respectively. On success-path (just before the success return at line 462), compute `newLatest = BigInt-max(latestIdInDb ?? 0, ...collected.map(c => c.tweet.id))` and merge `latest_tweet_id: newLatest.toString()` into the finally-block success patch (line 475-484). Keep the field as a string in Firestore (BigInt is not a Firestore-supported type).

**Area 2 — Chunked + parallel pending_delete diff.** Replace the serial `for (const id of missingNow) { await docRef.get(); ... }` block with: (a) chunk `missingNow` into 30-id arrays, (b) parallel `Promise.all(chunks.map(chunk => database.collection("users/${uid}/tweets").where(FieldPath.documentId(), "in", chunk).select("deleted").get()))`, (c) build `deletedSet` from results, (d) iterate `missingNow` building `WriteBatch` in 450-chunks (same idiom as line 399), (e) `await batch.commit()` each chunk. Net effect: 3,068 candidate ids → ~103 `in` queries + ~7 batch commits instead of 3,068 serial `get`s + 1 oversized batch.

**Area 3 — Finally-block observability.** Wrap the existing finally body (lines 469-485) in `Promise.race([finallyWork(), timeoutAfter(5000)])`. In the catch, emit BOTH `logger.error(...)` AND a synchronous `console.error(JSON.stringify({severity:"ERROR",msg:"daily_poll_finally_failed",uid,code,where:"timeout|throw"}))` line that bypasses the firebase-functions logger's async flush. Cloud Logging picks up `console.error` via stderr capture immediately; the JSON envelope makes it queryable via the same `jsonPayload.msg` filter.

**Area 4 — Backfill script.** `scripts/backfill-tweet-id-field.mjs` — `node scripts/backfill-tweet-id-field.mjs <uid>` with ADC. Scans `users/{uid}/tweets`, for each doc missing `id` field writes `{id: doc.id}` via `set({merge: true})`. Also reads/maintains `users/{uid}/sync_status/state.latest_tweet_id` — at the end, computes `BigInt-max` of all doc ids, writes via `{merge:true}` if absent or smaller. Idempotent: re-run is a no-op for both fields. Dry-run flag (`--dry-run`) prints the plan; default is execute. Logs every 100 writes.

**Area 5 — Four new test cases** in `daily-poll.test.ts`. See Test Plan below for shapes.

## Step-by-Step Plan

1. **Read `functions/test/fakes/firestore.ts`** in full (353 LOC). Confirm `where(FieldPath.documentId(), "in", [...])` returns docs in input order with the matching shape (`{doc.id, data()}`); confirm `select("deleted")` returns the field. If gaps surface, extend the fake in this step (small additive change; existing tests must still pass).
2. **Add `latest_tweet_id` to the sync_status TypeScript shape.** Locate the type for the sync_status doc (likely an inline `Record<string, unknown>` in poll.ts; if there's an exported type elsewhere, update it). Add optional `latest_tweet_id?: string` field.
3. **Implement BigInt-aware `latestIdInDb` read.** Replace poll.ts:277-285 with a single `await statusRef.get()` (statusRef is already defined at line 152 in `runPoll`) and `latestIdInDb = (statusSnap.data()?.latest_tweet_id as string | undefined)`. Drop the `.collection(...).orderBy(...).limit(1).select().get()` query entirely. The `statusRef.get()` already happens earlier in the function for lease/xUserId — verify whether the result can be reused (avoid a second get); if not, keep the second get (one extra read per poll is cheap and the code is clearer).
4. **Update overlap comparison.** Replace `tweet.id === latestIdInDb` (line 308) with `latestIdInDb && BigInt(tweet.id) <= BigInt(latestIdInDb)`. When the condition fires, record the boundary tweet in `seenIds` (already does so at line 310) and set `stop = true` + `stoppedOnOverlap = true`. Behavioral note: the `<=` (not `<`) means the boundary tweet itself IS treated as "still present" — matches the existing intent at line 308.
5. **Update pending_delete boundary.** Replace `id > latestIdInDb` (line 429) with `BigInt(id) > BigInt(latestIdInDb)`. Identical semantics on equal-length ids; correct semantics on mixed-length.
6. **Write success-path `latest_tweet_id` update.** Inside the finally-block success branch (between lines 478 and 483), if `collected.length > 0` compute `const newLatest = collected.reduce((max, {tweet}) => (BigInt(tweet.id) > max ? BigInt(tweet.id) : max), latestIdInDb ? BigInt(latestIdInDb) : 0n)`; if `newLatest > 0n && (!latestIdInDb || newLatest > BigInt(latestIdInDb))` add `finalPatch.latest_tweet_id = newLatest.toString()`.
7. **Chunked + parallel pdBatch precondition reads.** Replace the inner read loop in poll.ts:437-458. New structure: (a) `const CHUNK = 30; const idChunks = chunk(missingNow, CHUNK);` (b) `const snaps = await Promise.all(idChunks.map(ids => database.collection(\`users/${uid}/tweets\`).where(FieldPath.documentId(), "in", ids).select("deleted").get()));` (c) `const deletedSet = new Set<string>(); snaps.forEach(snap => snap.docs.forEach(d => { if (d.data()?.deleted === true) deletedSet.add(d.id); }));`. Add a small inline `chunk<T>(arr, size)` helper near the top of poll.ts (it's used twice now — the existing 450-chunk loop can be refactored to use it OR keep the inline loop; prefer the helper for consistency, but a tiny cosmetic refactor only).
8. **Chunked pdBatch writes.** Replace the unbounded `pdBatch = database.batch()` block with: `const pdWrites: Array<[DocumentReference, Record<string, unknown>]> = []; for (const id of missingNow) { if (deletedSet.has(id)) continue; const docRef = database.doc(\`users/${uid}/tweets/${id}\`); assertPathScoped(docRef.path, uid); pdWrites.push([docRef, {pending_delete: true, pending_delete_detected_at: Timestamp.now(), updatedAt: FieldValue.serverTimestamp()}]); } for (let i = 0; i < pdWrites.length; i += BATCH_SIZE) { const batch = database.batch(); for (const [ref, data] of pdWrites.slice(i, i + BATCH_SIZE)) batch.set(ref, data, {merge: true}); await batch.commit(); } flaggedCount = pdWrites.length;`. Matches the existing chunked pattern at line 399.
9. **Finally observability hardening.** Refactor the existing try/catch inside finally (lines 469-488) into `try { await Promise.race([finallyWork(), new Promise<never>((_, rej) => setTimeout(() => rej(new Error("finally_timeout_5s")), 5000))]); } catch (e) { logger.error("daily_poll_finally_failed", {uid, code: (e as Error).message}); console.error(JSON.stringify({severity:"ERROR",message:"daily_poll_finally_failed",uid,code:(e as Error).message,where:(e as Error).message === "finally_timeout_5s" ? "timeout" : "throw"})); }` where `finallyWork()` is an arrow IIFE wrapping the existing `if (pollFailed) {...} else {...}` block.
10. **Add four new jest cases to `daily-poll.test.ts`** — see Test Plan. Follow the existing `(a)..(i)` pattern: seed via `ctx.seed()`, mock fetch via `.mockResolvedValueOnce(...)`, assert via `ctx.journal.filter(...)` + return-shape `.toEqual()`.
11. **Write `scripts/backfill-tweet-id-field.mjs`.** Structure: parse `process.argv[2]` as uid (fail fast if missing or empty), `initializeApp({projectId: process.env.GOOGLE_CLOUD_PROJECT ?? "crumbs-a4fdb"})`, `const db = getFirestore()`, iterate `db.collection(\`users/${uid}/tweets\`).get()` in `.docs`, for each `if (!doc.data().id) await doc.ref.set({id: doc.id}, {merge: true})` (sequential is fine for one-shot script, can parallelize via `Promise.all` chunks of 30 if perf matters). After loop, compute `BigInt-max` of all `doc.id` and read `sync_status/state.latest_tweet_id`; if absent or numerically smaller, write `{latest_tweet_id: newMax.toString()}` via `{merge: true}`. Log progress every 100 docs. Support `--dry-run`. Path-scope assertion: refuse to run if uid is empty or contains `/`.
12. **Run the full test suite locally** (`cd functions && npm test`). Expect all existing 9 cases + 4 new cases = 13 passing. Document any unexpected failure as a sub-step before continuing.
13. **Run lint + typecheck** (`cd functions && npm run lint && npx tsc --noEmit`). Resolve any new type errors from BigInt usage (the `select("deleted").get()` typing path may need explicit `as { deleted?: boolean } | undefined` casts inside the fake — confirm in Phase 1).
14. **Update `00-index.md` and `04-plan.md`.** Bump `slices-planned: 3 → 4`, add poll-correctness summary block, update `implementation-order` and `Recommended Next Stage`. Set `00-index.md.current-stage: plan`, `progress.plan: in-progress` → mark poll-correctness planned, `next-invocation: /wf implement cloud-function-bookmark-sync poll-correctness`.

## Test / Verification Plan

### Automated checks
- **lint:** `cd functions && npm run lint` — must pass; no `no-unused-vars` or `@typescript-eslint/no-explicit-any` regressions.
- **typecheck:** `cd functions && npx tsc --noEmit` — must pass; verify BigInt arithmetic types correctly (`BigInt(string)` returns `bigint`; comparisons require both sides be `bigint`).
- **jest:** `cd functions && npm test` — must pass all 13 cases (9 existing + 4 new). Existing cases must still pass: the `latest_tweet_id` writes are additive (existing assertions on `lastPolledAt`, `poll_lease: null`, `lastError: null` still hold).

### Four new test cases (in `daily-poll.test.ts`)

| # | Case | Setup | Expect |
|---|------|-------|--------|
| j | "(j) BigInt latest-id discovery — 18-char id sorts before 19-char id" | Seed `users/uid1/sync_status/state` with no `latest_tweet_id`. Pre-write the backfill effect: seed `users/uid1/tweets/{823456789012345678}` (18-char, 2017) AND `users/uid1/tweets/{1812345678901234567}` (19-char, 2024). Manually seed `sync_status.latest_tweet_id = "1812345678901234567"` (cache populated by backfill). Mock fetch: token + bookmarks `{data: [{id: "1923456789012345678", ...}, {id: "1812345678901234567", ...}]}` (newer-than-cache tweet first, then boundary). | `result.itemsAdded === 1` (only the newer is collected); journal shows `set` on `sync_status/state` merging `latest_tweet_id: "1923456789012345678"`; stop-on-overlap fires AT the boundary, not at the lex-greater 18-char tweet (the 18-char id is below boundary numerically so should NOT have caused early stop if encountered). |
| k | "(k) Pending_delete chunking — 600 missingNow ids split across 2+ batches of ≤450" | Seed 700 tweets under `users/uid1/tweets/T1..T700` (string ids; for this case length doesn't matter — covered by case j). Seed `sync_status.latest_tweet_id` = `"T700"`. Mock fetch returns empty bookmarks page (no overlap, full diff path). | Journal contains ≥2 `batchCommit` entries for pending_delete writes; each chunk's batched ops ≤ 450; `flaggedCount === 700`; no `INVALID_ARGUMENT` thrown. |
| l | "(l) Pending_delete `in`-query precondition — deleted tweets skipped" | Seed 60 tweets `T1..T60` under `users/uid1/tweets/`. Pre-set `T30.deleted = true`, `T45.deleted = true`. Seed `sync_status.latest_tweet_id = "T60"`. Mock fetch returns empty bookmarks page. | Journal contains 2 `queryGet` entries with `FieldPath.documentId() in [...]` shape (chunks of 30); `flaggedCount === 58` (60 − 2 deleted); journal `set` entries for pending_delete do NOT include T30 or T45. |
| m | "(m) Finally-failure visibility — daily_poll_finally_failed logged on Firestore throw" | Setup happy-path bookmarks fetch + token. Inject a throwing `set()` on `sync_status/state` (extend the fake's `set` to allow `.failNext(reason)`-style injection — small additive helper if missing). Spy on `console.error` (NOT logger.error; the synchronous path is what we're asserting). | `result === undefined` (the throw inside finally propagates after `try` returns); `consoleSpy.calls[0][0]` parses as JSON with `severity:"ERROR"`, `message:"daily_poll_finally_failed"`, `uid:"uid1"`, `code:<string>`, `where:"throw"`. |

If the fake doesn't support `failNext`-style injection for case (m), add a minimal `__failNextSet(path, reason)` helper in Phase 1 (additive; won't break existing cases).

### Interactive verification (human-in-the-loop)

Per the confirmed `stack:` block in `00-index.md` (`testing: [junit, roborazzi, maestro, jest]`, `observability: [lazylogcat]`), the platform for this slice is `service` (functions). No Android-side verification is needed for poll-correctness — the slice is server-only, and the user-observable AC (AC4 lastPolledAt advance, AC5 debounce, AC7 round-trip) are exercised by a re-run of `/wf verify cloud-function-bookmark-sync daily-poll` after this slice deploys.

- **What to verify:** end-to-end deployed poll cycle on `crumbs-a4fdb` produces `ok: true`, advances `lastPolledAt`, sets `latest_tweet_id`, and the pending_delete round-trip works on a corpus with mixed-length ids.
- **Platform & tool:** service adapter — `gcloud functions call` / `firebase deploy --only functions:dailyPoll,triggerPoll` / `gcloud logging read` via terminal. No GUI driver.
- **Companion skills:** none from `stack.available-skills` directly applicable; lazylogcat is android-only.
- **Steps (operator checklist, to land in implement record):**
  1. Run backfill script: `gcloud auth application-default login` → `node scripts/backfill-tweet-id-field.mjs 6yPmdM14V3dPHLe3LO9XCfU4l9f1` (dry-run first, then live). Confirm logs show `X docs missing id, Y backfilled, latest_tweet_id seeded: <19-char-id>`.
  2. Deploy functions: `firebase deploy --only functions:dailyPoll,functions:triggerPoll --project crumbs-a4fdb --force`.
  3. Invoke triggerPoll via `functions/scripts/oauth-bootstrap-local.mjs`'s ID-token mechanism OR a fresh device call. Capture response JSON.
  4. Read `users/{uid}/sync_status/state` via `firebase firestore:get` — confirm `lastPolledAt` advanced, `poll_lease: null`, `lastError: null`, `latest_tweet_id: <bigint-max-string>`.
  5. Re-invoke triggerPoll within 60s — confirm `{ok: false, reason: "debounced"}`.
  6. Manually un-bookmark a tweet in the X UI, invoke triggerPoll, read the affected doc — confirm `pending_delete: true`. Re-bookmark, invoke triggerPoll, read again — confirm `pending_delete: false`.
- **Evidence capture:** under `.ai/workflows/cloud-function-bookmark-sync/verify-evidence/daily-poll/` (re-use existing folder per service-adapter convention). Files: `poll-correctness-deploy.log`, `poll-correctness-triggerpoll.json`, `poll-correctness-firestore-state.json`, `poll-correctness-pending-delete-round-trip.json`.
- **Pass criteria:** all six steps green; jest suite still green after the slice's verify; `verify-function-iam.sh` still ALL CHECKS PASSED post-redeploy.

Re-running daily-poll's verify is the recommended way to re-test the user-observable AC after this slice. The verify-owned single-round fix loop will close (`convergence: not-needed`) if all four AC reach pass — which is the entire point of this slice.

## Risks / Watchouts

- **Cache-bootstrap dependency on backfill script execution.** The success path of the FIRST post-fix poll requires `sync_status.latest_tweet_id` to be present, or `latestIdInDb` is `undefined` and the function fetches the full bookmarks page from scratch (≤ 800 tweets, all collected). This is correct behavior (no overlap means no early stop) but it's a 1-shot extra read cost. The backfill script seeds the cache, so under the documented operator order (backfill → redeploy → poll) the first poll is cheap. Mitigation: document the order in the operator checklist; the verify will be the operational proof.
- **Backfill script vs concurrent dailyPoll race.** If the operator runs the backfill while `dailyPoll` is mid-flight (twice-daily cron at 09:00 / 21:00 UTC), the script's `set({merge: true}, {id: doc.id})` composes correctly with the function's writes. No coordination required. Document the convention in the operator checklist.
- **`Promise.race` finally-timeout masks slow-but-eventually-successful writes.** The 5s timeout is a safety net to surface the visibility-gap defect. If a Firestore write takes >5s but eventually succeeds, the log says "timeout" but the data lands. Acceptable: the next invocation re-claims a stale lease via the existing `expires_at` mechanism, and the next success-path success-write reconciles `lastPolledAt`. Document the semantics in the implement record.
- **Backfill operator credential mismatch.** If the user's `gcloud` session has impersonation set (similar to the OAuth-bootstrap signBlob issue), the script may fail with a credential-type mismatch. Surface in the operator checklist: "run `gcloud auth application-default login` to ensure direct ADC, not impersonated."
- **`where(FieldPath.documentId(), "in", [...])` cap.** Hard limit is 30 IDs per query. The chunk size is fixed at 30; do not raise. Confirmed via web research.
- **BigInt serialization to Firestore.** Firestore does NOT support BigInt as a stored type. Always convert to string at write time (`newLatest.toString()`) and convert back at read time (`BigInt(stored)`). No accidental `JSON.stringify` of a BigInt — that throws. Lint or runtime tests will catch any drift.
- **Android display order is still lexicographic.** `FirestoreRepository.kt:63` reads `users/{uid}/tweets` with `.orderBy(FieldPath.documentId())` — this is unchanged. With mixed-length ids in the corpus, the Android reader will show 2017 tweets at the top until `android-reader` (or a future slice) introduces a BigInt-aware client-side sort. **Not in scope for poll-correctness.** Flag to `android-reader` planning as a forward concern.
- **Existing test cases must still pass.** The new `latest_tweet_id` write inside the finally success patch is additive; existing assertions filter by `path === "users/uid1/sync_status/state"` and inspect specific keys (`lastError`, `poll_lease`, `lastPolledAt`). Adding a key shouldn't break those — but if any assertion uses `.toEqual()` on the full object, it will. Phase 0 step 1 (reading the fake) doubles as a check on the assertion style; Phase 12 confirms all 9 existing cases stay green.
- **`statusRef.get()` double-fetch.** Step 3 considers reusing the earlier `statusSnap` for `latest_tweet_id` to avoid a second read. The earlier `statusSnap` lives inside the lease transaction (poll.ts:158) — by the time the post-overlap code runs, the transaction has committed and `statusSnap.data()` is from BEFORE the lease was written. Reading `latest_tweet_id` from that snapshot is safe (the field doesn't change inside the transaction). Prefer reuse if possible; if it complicates control flow, do the extra read (1 doc-get is ~$0.06/million, negligible).

## Dependencies on Other Slices

- **Depends on `daily-poll` (verified-escalated)** — defines `runPoll`, the handlers, the test fake, the `sync_status` doc shape, and the deployed function topology. This slice modifies `runPoll` internals only; no handler signature change, no new export, no new function deploy beyond re-pushing the existing two.
- **Downstream: `android-reader`** — its plan inherits a new `depends-on` edge from the index (`[auth-foundation, daily-poll, poll-correctness]`). When android-reader plans, it will see this slice's `latest_tweet_id` field (which it doesn't need to read), the corrected `pending_delete` semantics (which the reader's UI WILL eventually surface), and the still-lexicographic doc-id ordering on the Firestore read (a latent display-order bug for android-reader to consider in its own scope).
- **Sibling plans (`04-plan-auth-foundation.md`, `04-plan-functions-oauth.md`, `04-plan-daily-poll.md`)** — cohesion check ran during plan stage:
  - `auth-foundation`: no shared files. No conflict.
  - `functions-oauth`: shares `functions/src/lib/` namespace but no overlapping file. `lib/secrets.ts` (functions-oauth) is consumed by poll.ts read-only — unchanged. No conflict.
  - `daily-poll`: this slice modifies the same `poll.ts` and `daily-poll.test.ts` daily-poll created. Daily-poll is sealed (`status: verified-escalated`); poll-correctness is the proper way to extend it. No file-write contention. No conflict.

## Assumptions

- The verify-stage code fixes that landed in the working tree during daily-poll's verify (the 5 files: `functions/src/index.ts` serviceAccount, `functions/package.json` @eslint/js peer-dep, `firestore.indexes.json` fieldOverrides, `scripts/verify-function-iam.sh` datastore-role contract, `functions/scripts/oauth-bootstrap-local.mjs`) are committed BEFORE this slice's implement stage begins, OR are part of the same branch state when this slice's implement runs. Either way, no plan step depends on them being pristine.
- The deployed function topology stays the same: 5 Cloud Functions on `crumbs-a4fdb` (mintOAuthState, oauthCallback, warmUp, dailyPoll, triggerPoll). This slice re-deploys `dailyPoll` and `triggerPoll` only; the OAuth surface is untouched.
- The `sync_status/state` doc exists for the target uid (`6yPmdM14V3dPHLe3LO9XCfU4l9f1`) — it does, written by `oauthCallback` and updated by `dailyPoll` already. The new `latest_tweet_id` field is a merge add, not a new doc create.
- The X portal account stays on pay-per-use during this slice's verify (the rate limits at the lower tier blocked the prior verify's smoke runs). User confirmed in the verify session.
- BigInt is available at runtime (Node 22 ≥ Node 12 — yes), and `tsconfig.json target: "ES2022"` allows literal `0n` (yes, confirmed in test infra audit).

## Blockers

None. All open questions from the slice file have answers (PO discovery round 1 + 2 above); all `stack:` tooling is confirmed; no missing capability requires routing back to shape.

## Freshness Research

- **Firestore WriteBatch hard cap: 500 ops per commit** (2026-05, verified). Each `set`/`update`/`delete` = 1 op. `serverTimestamp()` no longer counts as 2 (legacy issue fixed). BulkWriter is the alternative for 3,000+ ops but introduces non-atomic semantics — rejected by PO; keep manual chunked batches per the existing line 399 pattern. Source: [Firebase Firestore quotas](https://firebase.google.com/docs/firestore/quotas).
- **`where(FieldPath.documentId(), "in", [...])` cap: 30 values** (2026-05). Increased from 10 in late 2021. `not-in` still 10. Billing: one read per matching doc. Vs `Promise.all(30 × docRef.get())`: same read cost, 30× fewer RPCs (1 vs 30). Adopted. Source: [Firestore query data](https://cloud.google.com/firestore/docs/query-data/queries).
- **JS BigInt for snowflake comparison** (2026-05). Arbitrary precision; `BigInt("1234567890123456789") < BigInt("1234567890123456790")` works. Snowflakes fit in u64 (max ~1.8e19) — well within BigInt range. Don't mix BigInt with Number in comparisons (`1n < 2.5` throws). `BigInt()` constructor works at any TS target; `1n` literal needs ES2020+. Project is on ES2022 — fine. Sub-microsecond per call; 800/poll is <1ms total.
- **Cloud Functions Gen 2 finally-block semantics** (2026-05, current). CPU is throttled to ~0 after the handler's promise resolves. `await` cleanup BEFORE returning the handler's promise — don't fire-and-forget. The existing `finally` IS inside `runPoll` which the handler awaits, so cleanup runs in scope; the visibility gap is the firebase-functions logger's async flush, not the runtime reaping. Synchronous `console.error` is the correct fallback because stderr is captured immediately. Sources: [Cloud Run always-on CPU](https://cloud.google.com/blog/topics/developers-practitioners/use-cloud-run-always-cpu-allocation-background-work), [Firebase Functions #1222](https://github.com/firebase/firebase-functions/issues/1222).
- **Backfill pattern for <5k docs:** local Node script + ADC matches the repo's existing `scripts/firestore-migrate/migrate.mjs` precedent. No CI deploy overhead; one-shot operator-run is right-sized.

## Revision History

*(none yet)*

## Recommended Next Stage

- **Option A (default):** `/wf implement cloud-function-bookmark-sync poll-correctness` — execute the 14-step plan. Run `/compact` before invoking to drop planning research from context (the PreCompact hook will preserve workflow state).
- **Option B:** `/wf plan cloud-function-bookmark-sync android-reader` — plan the next slice in parallel before implementing poll-correctness. android-reader inherits the new `depends-on` edge and the new `latest_tweet_id` field surfaces a discussion point for its display-order handling.
- **Option C:** `/wf plan cloud-function-bookmark-sync poll-correctness <feedback>` — return to this plan with directed corrections (e.g., switch backfill to a callable, switch to BulkWriter, drop the 5s finally timeout).
