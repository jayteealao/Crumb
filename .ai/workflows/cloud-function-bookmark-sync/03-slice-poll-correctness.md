---
schema: sdlc/v1
type: slice
slug: cloud-function-bookmark-sync
slice-slug: poll-correctness
status: defined
stage-number: 3
created-at: "2026-05-22T11:57:13Z"
updated-at: "2026-05-22T11:57:13Z"
complexity: m
depends-on: [daily-poll]
source: extension
source-ref: "06-verify-daily-poll.md (Issues Found, escalated 2026-05-22)"
extension-round: 1
tags: [cloud-functions, poll-engine, firestore-batch-cap, snowflake-id-comparison, migration-backfill, observability]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
  source: 06-verify-daily-poll.md
  plan: 04-plan-poll-correctness.md
  implement: 05-implement-poll-correctness.md
---

# Slice: poll-correctness

## Goal

Land the four `poll.ts` correctness fixes that the daily-poll verify surfaced so the function actually reaches the success path on a real bookmark corpus. After this slice, `dailyPoll` and `triggerPoll` produce `ok: true` with `lastPolledAt` advancing, the pending_delete diff respects the overlap boundary correctly even after migration drift, and silent finally-failure modes are visible in Cloud Logging.

## Why This Slice Exists

The daily-poll slice's verify (`06-verify-daily-poll.md`) ran the function end-to-end against the live deploy on `crumbs-a4fdb` and captured four concrete defects: two BLOCKERs (ISSUE-1 lexicographic-vs-numeric snowflake comparison, ISSUE-2 unbounded pdBatch), one HIGH (ISSUE-3 1,050/4,275 migration docs missing the `id` field), and one LOW (ISSUE-4 silent finally-failure visibility). Each defect is small in isolation, but the BLOCKERs cause the function to silently drop between writing the first 700+ tweets and finalizing `sync_status.lastPolledAt` — which leaves four user-observable acceptance criteria (AC4, AC5-debounce, AC6, AC7-server) unverifiable.

These cannot be addressed inside `daily-poll` because that slice is closed (`verified-escalated` per `00-index.md`); the verify-owned single-round fix loop was deliberately skipped by user choice so the defects could carry their own audit trail through plan + implement + verify rather than disappear into a quick patch. Hence this extension slice.

Without poll-correctness, the downstream `android-reader` slice cannot validate its Custom Tab + deep-link flow end-to-end against a function that actually completes a poll cycle.

## Scope

**In:**
- `functions/src/lib/poll.ts`:
  - **ISSUE-1 fix**: BigInt comparison for snowflake IDs. Replace the `tweet.id === latestIdInDb` equality at the stop-on-overlap point with a numeric `BigInt(tweet.id) <= BigInt(latestIdInDb)` "we've reached or passed the boundary" check. Replace the `orderBy("id", "desc").limit(1)` "find latest" with either (a) a `sync_status.latest_tweet_id` cache that the success path writes, or (b) a server-side scan that picks the BigInt max from the existing collection. Decide in the plan stage based on cost; option (a) is preferred for steady-state but option (b) is needed as a one-shot bootstrap.
  - **ISSUE-2 fix**: Wrap the pending_delete batch in the same 450-chunk loop pattern that the collection write batch uses (`poll.ts:399-406`). Apply identically at `poll.ts:437-458`. Also parallelize the per-doc `await docRef.get()` precondition reads via `Promise.all` chunks of 30 (or replace with a single `where(FieldPath.documentId(), "in", [...])` chunked query) so 3,000+ ids don't serially run for 300 seconds.
  - **ISSUE-4 fix**: Wrap the `finally`-block body in `Promise.race([…, timeoutPromise])` so it surfaces a `daily_poll_finally_failed` log line even when the surrounding runtime is about to be reaped. Treat the existing `try/catch` inside `finally` (poll.ts:469-488) as the seed — add a 5s timeout and a synchronous `console.error(JSON.stringify(...))` fallback that bypasses the firebase-functions logger's async flush path.
- `scripts/backfill-tweet-id-field.mjs` (new):
  - **ISSUE-3 fix**: One-shot Node script that reads every doc under `users/{uid}/tweets`, checks for the `id` field, writes `{ id: doc.id }` via `set({merge: true})` if missing. Idempotent. Run via `node scripts/backfill-tweet-id-field.mjs <uid>` with ADC credentials. Documented in the implement record's operator checklist alongside a guard rail: re-running after the BigInt comparison lands is also safe (no-op).
- Test additions to `functions/test/daily-poll.test.ts`:
  - One case proving `latestIdInDb` is selected by BigInt max, not lex sort, against a mixed-length-string fake corpus.
  - One case proving the pending_delete batch chunks correctly when `missingNow.length > 450` (asserts ≥ 2 batch commits + each chunk ≤ 450 ops).
  - One case proving stop-on-overlap fires on the numerically-correct boundary across a mixed-length-string corpus.
  - One case proving `daily_poll_finally_failed` is logged when the finally-block Firestore write throws.
- Updates to `06-verify-daily-poll.md` if re-verified in-slice (optional, leave to verify stage).

**Out (handled elsewhere):**
- Any change to deployed Cloud Function topology — handlers stay as-is, only the shared `runPoll` engine changes.
- Migration history beyond the `id` field — other missing fields (if any surface) are deferred to a separate extension.
- Backfilling other collections (`metrics`, `media`, `twitter_users`, `includes`, `textAnnotations`) — out of scope; daily-poll's poll engine writes those with the correct shape going forward.
- Re-running the verify automatically — this slice runs through plan → implement → verify like any other; verify is a separate command.

## Acceptance Criteria

- **Given** a `users/{uid}/tweets` collection with mixed 18-char (2017-era) and 19-char (2024+) snowflake string IDs, **when** the poll engine selects `latestIdInDb`, **then** the result is the numerically-largest (most recent) snowflake — not the lexicographically-largest one. (Closes ISSUE-1; replaces the broken comparison surfaced in the daily-poll verify.)
- **Given** a poll where stop-on-overlap fires after a numerically-correct match against the BigInt-largest stored id, **when** the diff phase runs, **then** `pending_delete` is set only on ids strictly above the boundary that were not echoed by X this poll. (Closes ISSUE-1's stop-on-overlap half; behavior matches the implement record's "overlap-aware diff" design but with correct numerical semantics.)
- **Given** a `missingNow` list with more than 500 entries, **when** the diff phase commits the pending_delete batch, **then** the writes complete cleanly across multiple chunked commits (each ≤ 450 ops) without throwing `INVALID_ARGUMENT: too many writes`. (Closes ISSUE-2.)
- **Given** a deployed `crumb-x-refresh-token-{uid}` and a corpus with at least one previously-stored tweet on either side of the new BigInt-max boundary, **when** `dailyPoll` or `triggerPoll` is invoked once, **then** the function returns within its timeout, writes `lastPolledAt` to the current Timestamp, clears `poll_lease`, and logs `daily_poll_completed` (or equivalent success line). (Closes the AC4 + AC6 user-observable acceptance criteria from daily-poll's slice file.)
- **Given** a clean first-call success that set `lastPolledAt`, **when** `triggerPoll` is invoked again within 60 seconds, **then** the response is `{ok: false, reason: "debounced", retryAfter: N}` and no X API call is made. (Closes AC5 debounce — gated on AC4 first.)
- **Given** an existing tweet T that was previously stored as present and is absent from X's response stream this poll, **when** the poll finishes, **then** `users/{uid}/tweets/{T}.pending_delete: true` is set. **When** T is later re-added to bookmarks and the next poll re-collects it, **then** `pending_delete: false` is restored. (Closes AC7-server pending_delete round-trip.)
- **Given** the migration backfill script runs against `users/{uid}/tweets`, **when** it completes, **then** every doc has an `id` field equal to its doc ID; subsequent re-runs of the backfill are no-ops; and `orderBy("id", "desc")` and `orderBy("__name__", "desc")` queries return identical doc orderings under BigInt-string interpretation. (Closes ISSUE-3.)
- **Given** the finally block's Firestore write throws (e.g., transient 5xx mid-write), **when** the function exits, **then** a `daily_poll_finally_failed` log line is captured in Cloud Logging with the throwing error's code/message and the lease state. (Closes ISSUE-4.)
- Jest cases land for each AC above; full suite stays green; lint + tsc + IAM verifier remain green; a fresh `/wf verify cloud-function-bookmark-sync daily-poll` re-run after this slice deploys produces `result: pass` (no remaining issues for the four AC the original verify deferred).

## Dependencies on Other Slices

- `daily-poll`: defines the `runPoll` engine + handlers + tests this slice modifies. Daily-poll's verify artifact (`06-verify-daily-poll.md`) is the source of every defect addressed here. This slice does NOT redo daily-poll's work — it patches the four named defects and adds the backfill script.
- Downstream impact on `android-reader`: the slice index records an updated `depends-on` edge from `android-reader → [auth-foundation, daily-poll, poll-correctness]`. The existing `03-slice-android-reader.md` is not touched; the plan stage of android-reader will pick up the new edge from the index.

## Risks

- **Bookmark history fetching cost.** With the BigInt comparison fix, the first poll after the migration backfill will correctly see "latest stored is the real latest" and exit fast via stop-on-overlap on the next incremental tick. But the FIRST poll under the broken behavior already fetched up to X's 800-bookmark cap. After the fix the function may re-page once more if the BigInt-max in the existing corpus is older than the oldest X-cached bookmark (i.e., no overlap at all). Mitigation: the BigInt-max in the function-written subset of the corpus IS recent (top of the 2024+ range), so overlap is essentially guaranteed.
- **`sync_status.latest_tweet_id` cache vs scan-on-demand.** If the plan picks the cached approach, the first-ever poll under the fix needs a one-shot population — easily done in the backfill script. If it picks scan-on-demand, the first poll's startup includes a full collection scan (~4,275 reads). Decide in plan; the scan-on-demand option is simpler but slower on cold start.
- **Backfill script idempotency under concurrent dailyPoll.** Backfill runs `set({merge: true})` with `{id: doc.id}`. If a concurrent dailyPoll commits to the same doc with different (current X) data, the merge composes correctly. No coordination required, but document the convention.
- **Finally-block timeout race.** The `Promise.race` with a 5s timeout inside finally prevents indefinite hangs but may mask a slow-but-eventually-successful Firestore write. Acceptable: the finally's job is to release the lease and write the status, not to guarantee the write lands — the next invocation can re-claim a stale lease via the existing `expires_at` mechanism. Document that semantics in the implement record.
- **Pending_delete re-flag oscillation.** The AC7-server round-trip (flag → re-bookmark → un-flag) is now testable. If the user re-bookmarks and the next poll's stop-on-overlap fires on a doc ABOVE the re-bookmarked tweet, the unflag won't fire that round. Mitigation: the BigInt comparison is now correct, so the boundary tweet itself is included in `seenIds`, and any tweet above it that was previously flagged should clear. Document the corner case in the implement notes.
- **Operator-side backfill execution.** Backfill is a one-shot run with project-owner ADC. If the user's session has gcloud impersonation set, the script may fail with a credential-type mismatch (same class of issue as the OAuth-bootstrap signBlob path during verify). Surface in the operator checklist.

## Open Questions for Plan

- Cached `sync_status.latest_tweet_id` vs scan-on-demand: decide in plan based on cold-start budget for triggerPoll vs simplicity.
- pdBatch read parallelization: `Promise.all` chunks of 30 vs `where(FieldPath.documentId(), "in", [...])` chunked at 30: decide in plan based on Firestore quota economics.
- Whether to run the backfill once globally (CI step) or per-user on demand (operator checklist item): decide in plan based on whether other users' corpora exist on `crumbs-a4fdb`.
