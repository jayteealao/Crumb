---
schema: sdlc/v1
type: implement
slug: cloud-function-bookmark-sync
slice-slug: poll-correctness
status: complete
stage-number: 5
created-at: "2026-05-22T12:52:17Z"
updated-at: "2026-05-22T12:52:17Z"
metric-files-changed: 5
metric-lines-added: 537
metric-lines-removed: 32
metric-deviations-from-plan: 2
metric-review-fixes-applied: 0
commit-sha: "9409017"
tags: [cloud-functions, poll-engine, bigint-comparison, firestore-in-query, finally-block, migration-backfill, jest]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
  slice-def: 03-slice-poll-correctness.md
  plan: 04-plan-poll-correctness.md
  source: 06-verify-daily-poll.md
  siblings: [05-implement-auth-foundation.md, 05-implement-functions-oauth.md, 05-implement-daily-poll.md]
  verify: 06-verify-poll-correctness.md
next-command: wf-verify
next-invocation: "/wf verify cloud-function-bookmark-sync poll-correctness"
---

# Implement: poll-correctness

## Summary of Changes

Landed the four `poll.ts` correctness fixes the daily-poll verify surfaced: BigInt-aware boundary discovery + overlap comparison + pending_delete boundary, chunked + parallelized pending_delete diff via `where(FieldPath.documentId(), "in", [...])`, finally-block visibility hardening with a 5s Promise.race timeout + synchronous `console.error` JSON envelope fallback, and a `sync_status.latest_tweet_id` BigInt-string cache that the success-path patch maintains. Plus a one-shot ADC Node backfill script that seeds the new cache and writes the missing `id` field onto 1,050/4,275 legacy migration docs.

Code complete: lint + typecheck + 27 jest cases (23 existing + 4 new) all green locally. Live re-deploy of `dailyPoll` + `triggerPoll` and operator-driven backfill execution are deferred to verify.

## Files Changed

**New (1):**

- `scripts/firestore-migrate/backfill-tweet-id-field.mjs` — one-shot operator script (168 LOC). Inputs: `node backfill-tweet-id-field.mjs <uid> [--dry-run]` from `scripts/firestore-migrate/`. Walks `users/{uid}/tweets` in 500-doc pages ordered by `__name__`, batches `set({id: doc.id}, {merge: true})` for any doc missing the field (400-op batches), then computes the BigInt-max of all stored doc ids and writes it to `users/{uid}/sync_status/state.latest_tweet_id` via merge if absent or numerically smaller. Idempotent on re-run. Uses `admin.credential.applicationDefault()`; refuses on traversal-style uids; supports a `--dry-run` mode that logs the plan without writes.

**Modified (4):**

- `functions/src/lib/poll.ts` — five named changes inside `runPoll`:
  1. **Import + type extension** — added `FieldPath` to the `firebase-admin/firestore` import; extended `SyncStatusData` with optional `latest_tweet_id?: string` (lines 22, 80-86).
  2. **`latestIdInDb` discovery** — replaced the broken `database.collection("users/${uid}/tweets").orderBy("id", "desc").limit(1).select().get()` (which compared snowflake strings lexicographically) with `currentStatus.latest_tweet_id` read from the existing `statusSnap` (no extra Firestore read). The cache field is populated by the new backfill script + maintained by this function's success-path patch (lines ~282-291).
  3. **BigInt comparison helpers** — `isAtOrBelowBoundary(id, boundary)` + `isStrictlyAboveBoundary(id, boundary)` at module scope (lines ~99-122). Both wrap `BigInt(x)` in try/catch and fall back to string equality (overlap) / `false` (boundary) when the input is non-numeric. Production snowflakes always convert; the fallback only fires for test fixtures using synthetic ids.
  4. **Overlap + boundary call sites** — replaced `tweet.id === latestIdInDb` (overlap) and `id > latestIdInDb` (pending_delete boundary) with the helpers above.
  5. **pdBatch chunking + parallel `in` precondition reads** — replaced the unbounded `database.batch()` + serial `for (const id of missingNow) { await docRef.get(); ... }` block with: (a) chunk `missingNow` into 30-id arrays, (b) parallel `Promise.all(chunks.map(ids => collection.where(FieldPath.documentId(), "in", ids).select("deleted").get()))`, (c) build `deletedSet` from results, (d) push pdWrites for non-deleted ids, (e) commit in 450-op batches via the same idiom as the collection-write loop. Net: 3,068 candidate ids → ~103 `in` queries + ~7 batch commits instead of 3,068 serial `get`s + 1 oversized batch.
  6. **Success-path `latest_tweet_id` write** — between the pending_delete diff and the success return, compute the BigInt-max of `collected.tweet.id` against `latestIdInDb || 0n`. Store the result in the function-scoped `newLatestTweetId` if it's strictly larger. The finally-block success patch reads `newLatestTweetId` and merges it into the `statusRef.set` payload. Wrapped in try/catch for non-numeric defensive skip.
  7. **Finally observability** — refactored the existing `try { ... } catch { logger.error(...) }` into `try { await Promise.race([finallyWork(), timeout(5s)]) } catch { logger.error(...); console.error(JSON.stringify({severity, message, uid, code, where})) } finally { clearTimeout(handle) }`. The synchronous `console.error` (with `// eslint-disable-next-line no-console`) bypasses the firebase-functions logger's async flush so Cloud Logging stderr capture sees the line immediately under Gen 2 post-response CPU throttling.
- `functions/test/fakes/firestore.ts` — three additive extensions:
  1. `where("__name__", "in", [...])` filter support — the fake's chainable query now recognizes the literal `"__name__"` sentinel as a doc-id filter (mocked from `FieldPath.documentId()`) and handles `op === "in"` for both doc-id and regular fields.
  2. `failNextSet(reason, predicate?)` single-shot failure injection on the `FakeContext` — both `docRef.set()` and transaction `tx.set()` consult it before journaling. Test (m) uses this to throw on the success-path sync_status write without disturbing the prior lease-tx write.
  3. Internal `shouldFailSet(path, data)` helper at the top of `createFakeDb()`.
- `functions/test/daily-poll.test.ts` — five changes:
  1. Added `FieldPath: { documentId: () => "__name__" }` to the existing `jest.mock("firebase-admin/firestore", ...)` block.
  2. Test (b) updated to seed `sync_status.latest_tweet_id: "T1"` (previously relied on auto-discovery via the removed `orderBy("id", "desc")` query).
  3. New test (j) — BigInt boundary: 18-char id is numerically below a 19-char `latest_tweet_id` and is NOT flagged pending_delete (proves the fix vs broken lex compare).
  4. New test (k) — pending_delete diff chunks writes ≤ 450 ops per batch when `missingNow > 500` (asserts ≥ 2 batchCommit entries, each ≤ 450, total 600).
  5. New test (l) — pending_delete precondition: deleted-true docs are skipped via the chunked `in` query (asserts 2 documentId-`in` queryGet entries for 60 ids; T30 and T45 not flagged).
  6. New test (m) — finally-block visibility: `daily_poll_finally_failed` is logged synchronously via `console.error` when the sync_status set throws (filters out the firebase-functions logger's own console.error wrapping).
- `functions/eslint.config.js` — added `console: "readonly"` to the test files' globals list. The prior config had `no-console: "off"` for tests but `console` itself was not in globals, so test (m)'s `jest.spyOn(console, "error")` tripped `no-undef`.

## Shared Files (also touched by sibling slices)

- `functions/src/lib/poll.ts` — created by `daily-poll`. This slice surgically modifies `runPoll`'s internals; no signature change, no new export.
- `functions/test/fakes/firestore.ts` — created by `daily-poll`. Extensions here are additive; the 23 existing test cases stay green.
- `functions/test/daily-poll.test.ts` — created by `daily-poll`. New cases append to the existing describe block; one existing case (b) updated to seed the new cache field.
- `functions/eslint.config.js` — created by `functions-oauth`, extended by `daily-poll`. This round added one global.

## Notes on Design Choices

- **Cache field vs collection-scan on every poll.** The plan considered a hybrid where the function would compute BigInt-max on every cold start. PO chose the cached field (`sync_status.latest_tweet_id`) — backfill seeds it once, the success-path writes it on every poll. One extra field, no extra read, no scan cost. The backfill script is the one-shot bootstrap; afterward the cache is self-maintaining.
- **`where(FieldPath.documentId(), "in", [chunks of 30])` over parallel docRef.get().** Both are 30 reads. The `in` query is 1 RPC; the parallel-gets approach is 30 RPCs. Same billing, 30× fewer network round-trips per chunk.
- **Manual chunked `WriteBatch` over `BulkWriter`.** PO chose to keep the manual pattern. `BulkWriter` provides higher throughput but with non-atomic per-chunk semantics; the existing 450-chunk loop (poll.ts:399-406) is atomic per commit and matches the codebase idiom.
- **Synchronous `console.error` fallback over logger-only.** The firebase-functions logger buffers async, so on Gen 2 post-response CPU throttling its log lines can be lost when the runtime is being reaped. The synchronous `console.error` writes to stderr, captured immediately by Cloud Logging. Both lines fire on a finally failure; queryable via `jsonPayload.message:"daily_poll_finally_failed"`.
- **5s `Promise.race` timeout.** The race exists to surface visibility — not to guarantee the write lands. If a Firestore commit hangs beyond 5s, the log says `where:"timeout"` but the data may still land asynchronously. The next invocation re-claims a stale lease via the existing `expires_at` mechanism.
- **`isAtOrBelowBoundary` / `isStrictlyAboveBoundary` fallback semantics.** When BigInt parse fails (non-numeric input), the helpers fall back to string equality (overlap) or `false` (boundary). They deliberately do NOT fall back to lexicographic `<` / `>` — that's the original defect for mixed-length snowflakes. In production all real snowflake ids convert successfully; the fallback exists only for test fixtures with synthetic ids like `T1`.
- **`statusSnap` reuse for the `latest_tweet_id` read.** The earlier `statusSnap` (read at line ~262 for xUserId resolution) is reused for the cache lookup — no second Firestore get. The snap predates only the lease-tx write, which itself doesn't touch `latest_tweet_id`.
- **Backfill script colocated with `scripts/firestore-migrate/migrate.mjs`.** The script lives at `scripts/firestore-migrate/backfill-tweet-id-field.mjs` to reuse the existing `firebase-admin` install (the plan's literal path was `scripts/backfill-tweet-id-field.mjs`; see Deviation 1).

## Deviations from Plan

**Deviation 1 — backfill script path.** Plan called for `scripts/backfill-tweet-id-field.mjs` (top-level). Placed at `scripts/firestore-migrate/backfill-tweet-id-field.mjs` instead because `firebase-admin` is only installed under `scripts/firestore-migrate/node_modules/`. Top-level `scripts/` has no `package.json` and no resolvable node_modules. Operator invocation is `cd scripts/firestore-migrate && node backfill-tweet-id-field.mjs <uid>`. Matches the precedent set by the existing `migrate.mjs` exactly.

**Deviation 2 — added defensive try/catch helpers for non-numeric ids.** Plan assumed all ids are valid BigInt strings. Existing jest tests (b, c, f) use synthetic ids (`T1`, `T9`, `T0`…`T49`) that `BigInt()` rejects with `SyntaxError`. Rather than rewrite three existing tests to use numeric ids, the BigInt usage is wrapped in helpers (`isAtOrBelowBoundary`, `isStrictlyAboveBoundary`) and the success-path max-computation block is wrapped in a try/catch that no-ops on parse failure. Cost: ~20 LOC of defensive scaffolding. Benefit: no churn on the daily-poll test suite. Production behavior is identical because every real snowflake id converts cleanly.

## Anything Deferred

- Live re-deploy of `dailyPoll` + `triggerPoll` on `crumbs-a4fdb` — operator step in verify.
- Live execution of `backfill-tweet-id-field.mjs` against uid `6yPmdM14V3dPHLe3LO9XCfU4l9f1` — operator step in verify (dry-run first, then live).
- Re-running `/wf verify cloud-function-bookmark-sync daily-poll` to clear the four defects' user-observable AC (AC4 lastPolledAt advance, AC5 debounce, AC6 offline-only-server, AC7-server pending-delete round-trip).
- Android-side `FirestoreRepository.kt:63` `FieldPath.documentId()` ordering remains lexicographic — latent display-order bug for mixed-length corpora. Flagged forward to `android-reader` planning per the slice file's Risks section; out of scope here.

## Known Risks / Caveats

- **First post-fix poll's cache state.** The backfill script must run BEFORE the redeploy is exercised on a real bookmark fetch. Operator order: (1) dry-run backfill, (2) live backfill, (3) redeploy, (4) trigger one poll. If a poll fires between (3) and (4) without the cache present, `latestIdInDb` is undefined and the function fetches the full 800-bookmark page (correct fallback, but a one-time extra read cost).
- **Backfill operator credential type.** ADC must NOT be impersonated for `set({merge: true})` writes (same class of issue as the OAuth-bootstrap signBlob path during verify). Surface in the operator checklist below: run `gcloud auth application-default login` and confirm `gcloud auth list` shows a real account, not an impersonation target.
- **`Promise.race` finally-timeout masks slow-but-eventually-successful writes.** If a Firestore write takes >5s but eventually succeeds, the log says `where:"timeout"` even though the data lands. Acceptable: the next invocation re-claims the stale lease and reconciles; the visibility goal is met.
- **Pending_delete re-flag oscillation across an overlap boundary.** If a user re-bookmarks a tweet whose snowflake is below the current cache boundary, the stop-on-overlap fires above it and the re-bookmark doesn't clear `pending_delete: true` until the next poll where the tweet is paged in directly. With the BigInt comparison correct, this is a 1-poll lag, not a permanent stuck state.
- **Backfill script vs concurrent dailyPoll.** Twice-daily cron at 09:00 / 21:00 UTC. The script uses `set({merge: true}, {id: doc.id})` which composes correctly with the function's writes — no coordination required.
- **Test (m)'s `console.error` filter relies on exact message match.** The firebase-functions logger ALSO writes to `console.error` when running outside a Cloud Functions runtime, with a different `message:` shape (`"Error: daily_poll_finally_failed\n    at ..."` vs our synchronous `"daily_poll_finally_failed"`). The test picks the synchronous fallback by exact-string `message === "daily_poll_finally_failed"`. If the firebase-functions logger ever serializes its first arg verbatim without the `Error: ` prefix, the test would over-match; treat as a known fragile point.

## Operator Checklist for Verify

1. `cd scripts/firestore-migrate && gcloud auth application-default login` — direct ADC (no impersonation).
2. `node backfill-tweet-id-field.mjs 6yPmdM14V3dPHLe3LO9XCfU4l9f1 --dry-run` — confirm the planned write count.
3. `node backfill-tweet-id-field.mjs 6yPmdM14V3dPHLe3LO9XCfU4l9f1` — live run; capture stdout to `verify-evidence/poll-correctness/backfill.log`.
4. `firebase deploy --only functions:dailyPoll,functions:triggerPoll --project crumbs-a4fdb --force` — redeploy the two affected handlers.
5. Invoke `triggerPoll` via the Android client (or curl with an ID token) — capture response JSON.
6. `firebase firestore:get users/6yPmdM14V3dPHLe3LO9XCfU4l9f1/sync_status/state --project crumbs-a4fdb` — confirm `lastPolledAt` advanced, `poll_lease: null`, `lastError: null`, `latest_tweet_id` populated.
7. Re-invoke `triggerPoll` within 60s — confirm `{ok: false, reason: "debounced", retryAfter: N}`.
8. Manually un-bookmark a tweet in X, invoke `triggerPoll`, read the affected doc — confirm `pending_delete: true`. Re-bookmark, invoke again, read again — confirm `pending_delete: false`.

## Freshness Research

No new external research this round — the plan's freshness section (`04-plan-poll-correctness.md` §Freshness Research) covered everything: Firestore WriteBatch 500-op cap, `where(FieldPath.documentId(), "in", [...])` 30-value cap, JS BigInt for snowflake comparison, Cloud Functions Gen 2 finally-block semantics, ADC backfill pattern.

## Recommended Next Stage

- **Option A (default):** `/wf verify cloud-function-bookmark-sync poll-correctness` — run the operator checklist above, re-verify the four daily-poll AC, capture live evidence. Run `/compact` first to drop implementation context.
- **Option B:** `/wf verify cloud-function-bookmark-sync daily-poll` — re-verify the original daily-poll slice. The four issues that were escalated should now reach `result: pass` (`convergence: not-needed`). Useful when treating poll-correctness as a defect-fix loop for daily-poll rather than a standalone slice.
- **Option C:** `/wf review cloud-function-bookmark-sync` — slug-wide review against `main...HEAD` covering all four implemented slices.
