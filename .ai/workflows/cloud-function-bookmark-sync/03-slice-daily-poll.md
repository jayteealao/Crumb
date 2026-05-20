---
schema: sdlc/v1
type: slice
slug: cloud-function-bookmark-sync
slice-slug: daily-poll
status: implemented
stage-number: 3
created-at: "2026-05-19T21:23:52Z"
updated-at: "2026-05-19T21:23:52Z"
complexity: l
depends-on: [functions-oauth]
tags: [cloud-functions, scheduler, twitter-api, firestore, callable, debounce, iam]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
  siblings:
    - 03-slice-auth-foundation.md
    - 03-slice-functions-oauth.md
    - 03-slice-android-reader.md
    - 03-slice-pending-delete.md
    - 03-slice-cutover-migration.md
  plan: 04-plan-daily-poll.md
  implement: 05-implement-daily-poll.md
---

# Slice: daily-poll

## Goal

Land the server-side polling surface: the daily `onSchedule` job and the on-demand `triggerPoll` callable both run the same shared poll logic, fetch the refresh token from Secret Manager, page X bookmarks incrementally, write to `users/{uid}/twitter/...` with deterministic IDs + `merge`, flag missing-since-last-poll items as `pending_delete: true`, update `sync_status`, and enforce a 60s debounce + 30s in-flight lease. Also lands `verify-function-iam.sh` as the CI gate on least-privilege IAM.

## Why This Slice Exists

Without this slice, the OAuth surface (`functions-oauth`) is a credential store with no consumer. This slice is also where the most operational subtlety lives: pagination bug avoidance (`max_results=50`), incremental stop-on-overlap, retry/backoff on 429, lease-based concurrency, structured `sync_status` updates, and the IAM verification gate that closes AC10. Isolating it keeps the auth surface unbloated and gives reviewers a single artifact for "the function actually moves data."

## Scope

**In:**
- `functions/src/lib/poll.ts` (or equivalent) — shared poll logic:
  - Read `users/{uid}/twitter/sync_status` and the most recent tweet IDs under `users/{uid}/twitter/tweets/*`.
  - Refresh access token via X (`POST /2/oauth2/token` with `grant_type=refresh_token`); on 401 → write `sync_status = {linked: false, lastError: "refresh_revoked", ...}` and return.
  - Page `GET /2/users/:id/bookmarks?max_results=50` with `tweet.fields` / `expansions` matching the existing `feature/twitter` shape; stop at first overlap or absent `next_token`.
  - Write each new tweet (+ sibling sub-collections: `metrics`, `media`, `twitter_users`, `includes`, `textAnnotations`) using deterministic IDs + `SetOptions.merge()`. **Hard-restrict** writes to paths under `users/{uid}/twitter/**`.
  - Diff: items present-last-poll-and-absent-now → set `pending_delete: true` on the existing doc (no delete).
  - Update `sync_status = {lastPolledAt, lastError, itemsAdded}` on success/failure.
  - On 429 → exponential backoff up to 3 retries; if exhausted → `lastError = "rate_limited"`, leave `linked: true`.
- Handlers:
  - `dailyPoll` (`onSchedule "0 9 * * *"`, region `europe-west2`) — iterates over `users/*/twitter/{linked: true}` (single-user in practice; loop guards for the future) and invokes the shared poll.
  - `triggerPoll` (`onCall`, region `europe-west2`) — requires `request.auth`; reads `sync_status.lastPolledAt`; if `now - lastPolledAt < 60s` returns `{ok: false, reason: "debounced", retryAfter: N}` without polling; otherwise claims a 30s lease via Firestore transaction on `sync_status.poll_lease` (second concurrent caller gets `{ok: false, reason: "in_progress"}`) and invokes the shared poll; returns `{ok: true, itemsAdded: N}`.
- `scripts/verify-function-iam.sh` — `gcloud iam` + `gcloud secrets get-iam-policy` assertions on:
  - SA exists, not the default App Engine SA.
  - Per-secret `secretAccessor` binding on `crumb-x-refresh-token-{uid}`, `crumb-oauth-state-secret`, `crumb-x-client-*`.
  - No project-level `roles/datastore.user` or `roles/datastore.owner` on the SA (Admin SDK bypass is the write mechanism).
- TypeScript tests:
  - `daily-poll.test.ts` — emulator + mocked `undici` against X. Cases: empty initial poll, second poll with overlap, 429 retry+success, 429 exhausted, refresh 401, pagination bug emulation (missing `next_token` mid-stream).
  - `trigger-poll.test.ts` — debounce window, in-flight lease, rejects unauthenticated.
- CI step running `verify-function-iam.sh` post-deploy.

**Out (handled by other slices):**
- Android-side pull-to-refresh wiring — `android-reader`.
- The `pending_delete` UI rendering (strikethrough + swipe) — `pending-delete`.
- `migrateXToken`, `disconnectX` callables — `cutover-migration`.

## Acceptance Criteria

- **Given** a `linked: true` user with at least one new bookmark in X since the last poll, **when** `dailyPoll` runs (or the equivalent `gcloud functions call dailyPoll`), **then** the new tweet appears under `users/{uid}/twitter/tweets/{tweetId}` and `sync_status.lastPolledAt` advances to `now()`. (Satisfies **AC4**.)
- **Given** `sync_status.lastPolledAt > 60s` ago, **when** `triggerPoll` is invoked authenticated, **then** the callable returns `{ok: true, itemsAdded: N}` and the same writes occur. **When** invoked again within 60s, **then** returns `{ok: false, reason: "debounced", retryAfter: N}` and does NOT poll. (Satisfies the debounce half of **AC5**.)
- **Given** two concurrent `triggerPoll` invocations within the same minute, **when** one claims the lease, **then** the other receives `{ok: false, reason: "in_progress"}` and does NOT poll.
- **Given** a tweet `T` present last poll and absent this poll, **when** the poll finishes, **then** the existing doc has `pending_delete: true` set; no doc is deleted. (Server half of **AC7**.)
- `daily-poll.test.ts` and `trigger-poll.test.ts` pass; `verify-function-iam.sh` passes in CI. (Satisfies **AC10**.)
- One manual operator verification: `gcloud functions call dailyPoll --region europe-west2` produces a `sync_status` write within 60s.

## Dependencies on Other Slices

- `functions-oauth`: the functions project, the dedicated SA, Secret Manager wiring, and an existing refresh token under `crumb-x-refresh-token-{uid}` are prerequisites.

## Risks

- **Pagination-bug regression on X** — `max_results=50` is the documented mitigation but X may revive the bug at lower page sizes. Mitigation: emulate the missing-`next_token` case in tests; partial pages are recoverable (next day's poll picks up the gap); the 800-history cap means steady-state is unaffected.
- **Hard-restrict path leak** — a bug that lets writes land outside `users/{uid}/twitter/**` would breach the security model. Mitigation: a write-path guard in `poll.ts` that asserts the doc path before every `set`; unit test that fakes a malformed input and asserts the guard throws.
- **Lease leak on crash** — function crashes mid-poll → 30s lease may release naturally via TTL on the lease field. Mitigation: include `expires_at` in the lease doc + lease-claim transaction rejects if expired.
- **Daily Scheduler clock skew** — `0 9 * * *` UTC is fixed; no DST shift. Mitigation: documented in plan; user can revise the cron in plan stage if desired.
- **Token refresh race with simultaneous `triggerPoll`** — daily Scheduler at 09:00 and a user pull-to-refresh at 09:00:05 both call X with the same refresh token. Mitigation: lease covers this; refresh token does not rotate per intake §Freshness Research, so even a double-refresh returns the same token.
