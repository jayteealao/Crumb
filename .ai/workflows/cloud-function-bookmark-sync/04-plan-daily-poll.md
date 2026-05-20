---
schema: sdlc/v1
type: plan
slug: cloud-function-bookmark-sync
slice-slug: daily-poll
status: complete
stage-number: 4
created-at: "2026-05-20T21:31:20Z"
updated-at: "2026-05-20T21:31:20Z"
metric-files-to-touch: 14
metric-step-count: 24
has-blockers: false
revision-count: 0
tags: [cloud-functions, onschedule, oncall, twitter-api, firestore-transactions, lease, debounce, secret-manager, iam-verification, refresh-token-rotation]
stack-source: confirmed
refs:
  index: 00-index.md
  plan-index: 04-plan.md
  slice-def: 03-slice-daily-poll.md
  siblings:
    - 04-plan-auth-foundation.md
    - 04-plan-functions-oauth.md
    - 04-plan-android-reader.md
    - 04-plan-pending-delete.md
    - 04-plan-cutover-migration.md
  implement: 05-implement-daily-poll.md
next-command: wf-verify
next-invocation: "/wf verify cloud-function-bookmark-sync daily-poll"
---

# Plan: daily-poll

## Current State

`functions/` ships under codebase `crumb-oauth` (commit `35493b9`). Three handlers (`mintOAuthState`, `oauthCallback`, `warmUp`) plus shared `lib/{admin,state,secrets}.ts` are deployed-ready; `firestore.rules` carries the email-allowlist gate; ESLint v9 flat config + Jest 30/ts-jest 29 CJS + TypeScript strict + Node 20. All region-pinned to `europe-west2` via `setGlobalOptions` in `src/index.ts:3`. Operator prereqs (dedicated SA `crumb-twitter-poller`, per-secret IAM, Secret Manager seeds, X portal redirect_uri, Cloud Scheduler `warmup-keepalive`) are checklist-recorded but not yet executed live.

`functions/src/lib/secrets.ts` already exports the helpers daily-poll needs verbatim: `getRefreshToken(uid)`, `setRefreshToken(uid, token)` (idempotent add-then-disable-previous), `getXClientCredentials()`. `lib/admin.ts` exposes the singleton `db()` (with `preferRest: true`) + `app()`. **No new shared-lib helper is required for refresh-token rotation** — `setRefreshToken` handles create-on-NOT_FOUND, add-version, disable-previous in a single call.

There is no `functions/src/lib/poll.ts`, no `dailyPoll`, no `triggerPoll`, no `scripts/verify-function-iam.sh`. The existing Android-side X client lives at [feature/twitter/src/main/java/com/github/jayteealao/twitter/services/TwitterApiService.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/services/TwitterApiService.kt) and carries the four field-list constants (`TWEETFIELDS`, `EXPANSIONS`, `MEDIAFIELDS`, `USERFIELDS`) plus the bookmarks endpoint + pagination loop that daily-poll mirrors verbatim on the server side. The one-shot migration script at [scripts/firestore-migrate/migrate.mjs](scripts/firestore-migrate/migrate.mjs) wrote bookmarks to `users/{uid}/{tweets,metrics,media,twitter_users,includes,textAnnotations}` (no `twitter` namespace); daily-poll consumes that layout as the canonical write target (resolves a slice-spec drift via PO Round 1 Q2).

Plan-stage parallel research surfaced four material corrections to the slice file:
1. **X refresh tokens DO rotate in 2026** (single-use; the intake's "no rotation" assumption is wrong). Documented in §Freshness Research.
2. **X pagination bug at `max_results=100` is still active in 2026** — `max_results=50` locked.
3. **Slice path `users/{uid}/twitter/tweets/{id}` drifted from migration's `users/{uid}/tweets/{id}`** — PO chose to match migration; sync_status moves to `users/{uid}/sync_status`. Requires a small `oauthCallback` amendment.
4. **`firebase-functions-test` does NOT support `onSchedule` v2** ([issue #210](https://github.com/firebase/firebase-functions-test/issues/210), still open) — daily-poll's tests must hand-roll the schedule-event mock.

## Reuse Opportunities

- [functions/src/lib/secrets.ts](functions/src/lib/secrets.ts) → `getRefreshToken`, `setRefreshToken`, `getXClientCredentials` → **reuse as-is**. `setRefreshToken` is already idempotent and handles create-secret on NOT_FOUND.
- [functions/src/lib/admin.ts](functions/src/lib/admin.ts) → singleton `db()` with `preferRest: true` → **reuse as-is**. Daily-poll must NOT re-call `.settings()` on the returned Firestore instance.
- [functions/src/handlers/oauthCallback.ts](functions/src/handlers/oauthCallback.ts) → **structural reference**, not import. Mirror its lazy-import discipline (line 26-27), structured `logger.{info,warn,error}` calls with snake_case event names (lines 62-66), top-level try/catch sanitization (line 109), and the global `fetch` + `URLSearchParams` token-exchange pattern (line 39-56). Daily-poll's refresh-token grant call is a near-copy with `grant_type=refresh_token` swapped in.
- [scripts/firestore-migrate/migrate.mjs:42-105](scripts/firestore-migrate/migrate.mjs) → **batch-write pattern**: `let batch = db.batch(); ... ; batch.set(ref, data, { merge: false }); opsInBatch++; if (opsInBatch >= BATCH_SIZE) { await batch.commit(); batch = db.batch(); opsInBatch = 0; }`. Daily-poll mirrors this in `lib/poll.ts` with `BATCH_SIZE = 450` (Firestore cap is 500; the script uses 400 — 450 splits the difference and is documented in code).
- [feature/twitter/src/main/java/com/github/jayteealao/twitter/services/TwitterApiService.kt:49-59](feature/twitter/src/main/java/com/github/jayteealao/twitter/services/TwitterApiService.kt) → **the four field-list constants** (`TWEETFIELDS`, `EXPANSIONS`, `MEDIAFIELDS`, `USERFIELDS`) → **copy verbatim** into a new `functions/src/lib/twitter-api.ts` module. Until `cutover-migration` deletes the Android-side client, **both copies must stay in sync** — note added to Risks.
- [feature/twitter/src/main/java/com/github/jayteealao/twitter/utils/ApiResponseExt.kt:19-73](feature/twitter/src/main/java/com/github/jayteealao/twitter/utils/ApiResponseExt.kt) → **stop-on-overlap loop structurally reused** in TypeScript. Compute `latestIdInDb` as the most-recent doc-id under `users/{uid}/tweets` (descending by `id_str` field); page until the response contains `latestIdInDb` OR `meta.next_token` is absent.
- [functions/test/oauthCallback.test.ts](functions/test/oauthCallback.test.ts) → **mocking-pattern reference** (jest.mock of `../src/lib/{admin,secrets}`, hand-rolled `req`/`res`, `jest.spyOn(globalThis, "fetch")`). Daily-poll's tests adopt the same conventions per PO Round 2 Q7 (hand-rolled, no `firebase-functions-test` wrapper).
- [functions/jest.config.js](functions/jest.config.js), [functions/tsconfig.json](functions/tsconfig.json), [functions/eslint.config.js](functions/eslint.config.js) → **no changes**. Daily-poll's `src/**/*.ts` + `test/**/*.ts` inherit the existing flat-config rules (no-console, no-restricted-imports on undici, strict TS).
- [scripts/run-maestro.sh](scripts/run-maestro.sh) → **shell-script conventions**: `#!/usr/bin/env bash`, `set -euo pipefail`, `REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"`, `==>` banner echos. `verify-function-iam.sh` mirrors these.
- No reuse candidate for: `onSchedule` handler scaffolding, Firestore lease transactions, exponential backoff for 429, X bookmark URL builder, `verify-function-iam.sh` assertions. Daily-poll authors all from scratch.
- Android-side `withAuthRefreshSingleFlight` and the `LinkedHashMap` page collector are **NOT reusable** (coroutines + Mutex; daily-poll is single-instance per Gen 2 invocation — the cross-call mutex is the Firestore lease).

## Likely Files / Areas to Touch

**Modify (4 existing files):**

- [functions/src/index.ts](functions/src/index.ts) — append two re-exports: `export { dailyPoll } from "./handlers/dailyPoll"; export { triggerPoll } from "./handlers/triggerPoll";`. No `setGlobalOptions` change (region already pinned).
- [functions/src/handlers/oauthCallback.ts](functions/src/handlers/oauthCallback.ts) — **per PO Round 1 Q2**: change the `sync_status` doc path from `users/${claims.uid}/twitter/sync_status` (line 58, 88) to `users/${claims.uid}/sync_status`. Two literal-string edits. Behavior unchanged otherwise.
- [functions/test/oauthCallback.test.ts](functions/test/oauthCallback.test.ts) — update the matching expectation at line 127 (`expect(mockDoc).toHaveBeenCalledWith("users/uid1/twitter/sync_status")` → `expect(mockDoc).toHaveBeenCalledWith("users/uid1/sync_status")`). Single literal-string edit.
- [.ai/workflows/cloud-function-bookmark-sync/00-index.md](.ai/workflows/cloud-function-bookmark-sync/00-index.md) — clear the four resolved open questions from `open-questions:` (Daily Scheduler cron → `0 9,21 * * *` UTC; pending-delete auto-expiry → never auto-expires; HMAC state max-age → confirmed 10 min, not this slice; triggerPoll debounce → confirmed 60s); update `updated-at`; append `04-plan-daily-poll.md` to `workflow-files`; refresh `next-command`/`next-invocation`. (Carried by Step 24 below.)

**New (10 files):**

*Shared library (2):*
- `functions/src/lib/twitter-api.ts` — exports the four field-list constants verbatim from the Android client, the `MAX_RESULTS = 50` constant, the `BOOKMARKS_BASE = "https://api.x.com/2/users"` constant, and a `buildBookmarksUrl(xUserId, paginationToken?)` helper. Pure URL+constant construction; no I/O.
- `functions/src/lib/poll.ts` — the shared poll engine (~250 LOC). Exports `runPoll(uid: string, opts?: { reason?: "scheduled" | "trigger" }): Promise<PollResult>` where `PollResult = { ok: true; itemsAdded: number; itemsFlaggedPendingDelete: number } | { ok: false; reason: "refresh_revoked" | "rate_limited" | "no_refresh_token" | "missing_x_user_id" | "x_user_lookup_failed" | "in_progress" | "debounced"; retryAfter?: number }`. Internals: (a) lease+debounce transaction over `users/{uid}/sync_status`, (b) refresh-token grant with rotation detection + conditional `setRefreshToken`, (c) X user-id lookup + cache, (d) paginated bookmark fetch with `max_results=50` + stop-on-overlap, (e) 429/5xx exponential backoff (3 attempts, base 1s, honors `x-rate-limit-reset` Unix timestamp), (f) batched Firestore writes (`BATCH_SIZE = 450`, dedup by ref before commit), (g) content-derived composite IDs for includes/textAnnotations, (h) pending_delete diff + reset-on-reappearance, (i) lease-release in finally block (`poll_lease: null`).

*Handlers (2):*
- `functions/src/handlers/dailyPoll.ts` — `onSchedule` v2: `import { onSchedule } from "firebase-functions/v2/scheduler";`. Config: `{ schedule: "0 9,21 * * *", timeZone: "UTC", region: "europe-west2", timeoutSeconds: 540, memory: "512MiB" }`. Body: iterate `db().collection("users").where("twitter.linked", "==", true).select().get()` (cheap projection; single-user in practice) → for each uid, `await runPoll(uid, { reason: "scheduled" }).catch(logAndContinue)`. **Note:** `where("twitter.linked", "==", true)` does NOT work directly because `linked` lives on `users/{uid}/sync_status` after Step 2's path move; the iteration is actually `db().collectionGroup("sync_status").where("linked", "==", true).get()` returning `sync_status` docs whose parent ref is `users/{uid}`. Use `doc.ref.parent.parent!.id` to recover the uid.
- `functions/src/handlers/triggerPoll.ts` — `onCall` v2: `import { onCall, HttpsError } from "firebase-functions/v2/https";`. Config: `{ region: "europe-west2", timeoutSeconds: 60 }` (defaults to 256MiB memory). Body: if `!request.auth` → `throw new HttpsError("unauthenticated", "Sign-in required")`; else `await runPoll(request.auth.uid, { reason: "trigger" })`. **Returns** the `PollResult` shape (not throws) for `debounced` / `in_progress` / `rate_limited` / `refresh_revoked`.

*Tests (3):*
- `functions/test/fakes/firestore.ts` (~120 LOC, per PO Round 2 Q8) — exports `createFakeDb(): { db: FakeDb; journal: JournalEntry[] }` returning a chainable proxy that records every `doc(path)`, `collection(path)`, `doc().set(data, opts)`, `doc().get()`, `collection().select().get()`, `collection().where(...).get()`, `runTransaction(cb)`, `batch()`, `batch.set()`, `batch.commit()` call into the journal. Inspectable from tests: `expect(journal.filter(e => e.op === "set" && e.path === "users/uid1/tweets/T1")).toHaveLength(1)`. Supports a `seed(path, data)` helper for pre-populating reads.
- `functions/test/daily-poll.test.ts` — 6 cases against `lib/poll.ts` directly (avoiding `firebase-functions-test`'s v2 onSchedule gap): (a) empty initial poll (no `sync_status`, no tweets, no rotation), (b) second poll with overlap (stops at known id; `pending_delete` cleared on reappearance), (c) 429 retry then success (mocks `Retry-After: 1` then 200; uses `jest.useFakeTimers()` + `jest.advanceTimersByTimeAsync(1100)`), (d) 429 exhausted after 3 attempts → `lastError: "rate_limited"`, `linked: true` preserved, (e) refresh-token grant returns 400 `invalid_grant` → `sync_status.linked: false, lastError: "refresh_revoked"`, **no Secret Manager write**, (f) pagination-bug emulation — `data.length === 50` but `meta.next_token` absent → loop terminates gracefully, items still written, no error. Plus a 7th smoke test that invokes `dailyPoll.run(eventStub)` via `(dailyPoll as unknown as { run: (e: unknown) => Promise<void> }).run({ scheduleTime: "2026-05-20T09:00:00Z", jobName: "firebase-schedule-dailyPoll-europe-west2" })` and asserts `runPoll` was called for each linked user.
- `functions/test/trigger-poll.test.ts` — 4 cases: (a) unauthenticated → `HttpsError("unauthenticated")` (matches mintOAuthState pattern), (b) within 60s of `lastPolledAt` → `{ok: false, reason: "debounced", retryAfter: N}`, no fetch, no Firestore write past the transaction read, (c) concurrent invocation while lease held → `{ok: false, reason: "in_progress"}`, no fetch, (d) happy path → `{ok: true, itemsAdded: N}` + lease cleared.

*Operator script (1):*
- `scripts/verify-function-iam.sh` — bash script (per PO Round 3 Q11, deferred CI). Args: `$1 = uid` (defaults to `6yPmdM14V3dPHLe3LO9XCfU4l9f1`). Asserts: (1) function runtime SA equals `crumb-twitter-poller@crumbs-a4fdb.iam.gserviceaccount.com` (not `@appspot.gserviceaccount.com`); (2) `secretAccessor` binding present on each of `crumb-x-refresh-token-${UID}`, `crumb-oauth-state-secret`, `crumb-x-client-id`, `crumb-x-client-secret`; (3) **no** project-level `roles/datastore.user`, `roles/datastore.owner`, `roles/datastore.writer` on the SA. Returns exit 0 on all-pass, non-zero on first failure. Uses `gcloud` + `jq`.

*Workflow artifact (1, this stage):*
- `.ai/workflows/cloud-function-bookmark-sync/04-plan-daily-poll.md` — this file.

*Master plan + control (3, this stage):*
- `.ai/workflows/cloud-function-bookmark-sync/04-plan.md` — refresh `slices-planned: 2 → 3`, add daily-poll summary section, update cross-cutting concerns, refresh next-invocation.
- `.ai/workflows/cloud-function-bookmark-sync/00-index.md` — see "Modify" above.
- `.ai/workflows/cloud-function-bookmark-sync/po-answers.md` — append the 12-question plan-stage record.
- `.ai/workflows/INDEX.md` — touch `updated-at` only (no slug/branch/status change).

**Operator prereqs (manual, bundled checklist in `05-implement-daily-poll.md`):**

1. SA already exists from functions-oauth (`crumb-twitter-poller@crumbs-a4fdb.iam.gserviceaccount.com`).
2. Per-secret `secretAccessor` already bound on `crumb-oauth-state-secret`, `crumb-x-client-id`, `crumb-x-client-secret` (functions-oauth checklist).
3. **New for daily-poll:** project-level `roles/secretmanager.secretVersionAdder` + `roles/secretmanager.secretVersionManager` on the SA (already in functions-oauth checklist item 4 — verify still bound, since this slice exercises the add-version path on every rotated refresh).
4. **New:** after first successful link, `secretAccessor` binding on `crumb-x-refresh-token-${UID}` (the secret is created on the first oauthCallback success; binding must be applied at that point or daily-poll's `getRefreshToken` returns null).
5. **New:** verify the Cloud Scheduler-generated job for `dailyPoll` has `attemptDeadline >= 540s` to match the function's `timeoutSeconds`. Firebase CLI sets this automatically on deploy; verify with `gcloud scheduler jobs describe firebase-schedule-dailyPoll-europe-west2 --location europe-west2 --format='value(attemptDeadline)'`. If <540s, override with `gcloud scheduler jobs update http firebase-schedule-dailyPoll-europe-west2 --location europe-west2 --attempt-deadline=540s`.
6. **New:** verify `roles/run.invoker` (Gen 2; NOT `cloudfunctions.invoker`) bound on the `dailyPoll` Cloud Run service to the Cloud Scheduler SA. Auto-configured by firebase-functions on first deploy; verify with `gcloud run services get-iam-policy dailypoll --region europe-west2 --format=json | jq '.bindings[] | select(.role=="roles/run.invoker")'`.
7. **New:** post-first-deploy, run `bash scripts/verify-function-iam.sh ${UID}` and confirm exit 0.

## Proposed Change Strategy

Six phases. Each commits cleanly as an optional checkpoint though the slice is intended as a single PR.

1. **Path move (Phase 0).** Amend `oauthCallback.ts` + `oauthCallback.test.ts` to write `sync_status` to `users/{uid}/sync_status` instead of `users/{uid}/twitter/sync_status`. Two literal-string edits + one test expectation update. Run `npm test` — all 9 existing cases still green. This unblocks the path-layout decision before any new code lands.
2. **Twitter API constants module (Phase 1).** Author `lib/twitter-api.ts` with the four field-list constants + `MAX_RESULTS = 50` + `buildBookmarksUrl`. Pure exports; no I/O. `npm run build` passes.
3. **Shared poll engine (Phase 2).** Author `lib/poll.ts` with `runPoll(uid, opts)`. Internal phases inside the function: (a) lease+debounce transaction, (b) refresh-token grant + conditional rotation persist, (c) X user-id lookup + cache, (d) paginated fetch with backoff, (e) batched writes with content-derived composite IDs, (f) pending_delete diff, (g) finally-block lease release + `lastPolledAt` update. `npm run build` passes (no tests yet).
4. **Test infrastructure + unit tests (Phase 3).** Author `test/fakes/firestore.ts` first. Then `daily-poll.test.ts` (7 cases) and `trigger-poll.test.ts` (4 cases). All 11 new cases + 9 existing = 20 cases green. Coverage spot-checked; no gate.
5. **Handlers + index wiring (Phase 4).** Author `handlers/dailyPoll.ts` + `handlers/triggerPoll.ts`. Append re-exports to `src/index.ts`. `npm run build` passes. The 7th smoke case in daily-poll.test.ts now covers the schedule-event invocation path.
6. **IAM verifier + operator checklist (Phase 5).** Author `scripts/verify-function-iam.sh`. Document the 7-item operator checklist in `05-implement-daily-poll.md` (implement-stage artifact). End-of-slice: `firebase deploy --only functions:crumb-oauth:dailyPoll,functions:crumb-oauth:triggerPoll --project crumbs-a4fdb` + post-deploy `bash scripts/verify-function-iam.sh ${UID}` (operator manual).

**Strict invariants across phases:**

- **No `console.log`** (lint-banned). Use `logger.{info,warn,error}` with snake_case event names + field-allowlist payloads. Never log the refresh token, the access token, or full X response bodies.
- **No `undici` import** (lint-banned). Global `fetch` + `Response` + `URLSearchParams` only.
- **No `firebase-functions-test` wrapper.** Hand-rolled mocks per PO Round 2 Q7. `firebase-functions-test@^3` stays in devDeps (already there; sibling test files may adopt it later).
- **Path-guard before every Firestore write.** `lib/poll.ts` asserts every doc path starts with `users/${uid}/` before any `batch.set(...)` or `doc().set(...)`. A unit test feeds a malformed uid (containing `..` or `/`) and asserts the guard throws before any Firestore call.
- **Refresh-token rotation per PO Round 1 Q1:** only call `setRefreshToken` if `response.refresh_token !== currentStoredRt`. Comparison is over the raw secret string. If X starts returning the same token (no rotation), the write is skipped — safe no-op.
- **Region pinning preserved:** `setGlobalOptions({ region: "europe-west2" })` in `src/index.ts` covers both new handlers. Per-handler `region:` overrides ONLY for `timeoutSeconds`/`memory` (since both new handlers need non-default values).
- **Lease is always released in `finally`.** Even on uncaught exception, `lib/poll.ts` writes `poll_lease: null` so the next caller does not have to wait for the 30s TTL.

## Step-by-Step Plan

1. **Amend oauthCallback sync_status path.** Edit `functions/src/handlers/oauthCallback.ts` lines 58 and 88: replace `users/${claims.uid}/twitter/sync_status` with `users/${claims.uid}/sync_status` (two occurrences). No structural change.

2. **Update oauthCallback test expectation.** Edit `functions/test/oauthCallback.test.ts` line 127: replace `"users/uid1/twitter/sync_status"` with `"users/uid1/sync_status"`. Run `cd functions && npm test` — all 9 cases green.

3. **Author `functions/src/lib/twitter-api.ts`.** Verbatim from `feature/twitter/.../services/TwitterApiService.kt:49-59` (the Kotlin file has these as Kotlin string constants; copy the string values, not the syntax):
   ```ts
   export const TWEETFIELDS = "id,in_reply_to_user_id,lang,entities,created_at,attachments,author_id,context_annotations,conversation_id,public_metrics,referenced_tweets,text,edit_history_tweet_ids,edit_controls,note_tweet,reply_settings,possibly_sensitive";
   export const EXPANSIONS = "attachments.media_keys,attachments.poll_ids,author_id,entities.mentions.username,in_reply_to_user_id,referenced_tweets.id,referenced_tweets.id.author_id,edit_history_tweet_ids";
   export const MEDIAFIELDS = "alt_text,media_key,url,type,public_metrics,preview_image_url,height,duration_ms,width,variants";
   export const USERFIELDS = "id,profile_image_url,name,username,verified,verified_type,description,created_at,location";
   export const MAX_RESULTS = 50;
   export const X_API_BASE = "https://api.x.com/2";

   export function buildBookmarksUrl(xUserId: string, paginationToken?: string): string {
     const params = new URLSearchParams({
       "tweet.fields": TWEETFIELDS,
       expansions: EXPANSIONS,
       "media.fields": MEDIAFIELDS,
       "user.fields": USERFIELDS,
       max_results: String(MAX_RESULTS),
     });
     if (paginationToken) params.set("pagination_token", paginationToken);
     return `${X_API_BASE}/users/${encodeURIComponent(xUserId)}/bookmarks?${params}`;
   }

   export const USERS_ME_URL = `${X_API_BASE}/users/me`;
   export const TOKEN_URL = `${X_API_BASE}/oauth2/token`;
   ```
   Pure module; no I/O. `npm run build` passes.

4. **Author `functions/src/lib/poll.ts`.** Skeleton:
   ```ts
   import { logger } from "firebase-functions/v2";
   import { db } from "./admin";
   import { getRefreshToken, setRefreshToken, getXClientCredentials } from "./secrets";
   import { FieldValue, Timestamp } from "firebase-admin/firestore";
   import { buildBookmarksUrl, USERS_ME_URL, TOKEN_URL } from "./twitter-api";

   const BATCH_SIZE = 450;
   const DEBOUNCE_MS = 60_000;
   const LEASE_TTL_MS = 30_000;
   const MAX_RETRIES = 3;
   const BACKOFF_BASE_MS = 1_000;

   export type PollResult =
     | { ok: true; itemsAdded: number; itemsFlaggedPendingDelete: number }
     | { ok: false; reason: "refresh_revoked" | "rate_limited" | "no_refresh_token" | "missing_x_user_id" | "x_user_lookup_failed" | "in_progress" | "debounced"; retryAfter?: number };

   export async function runPoll(uid: string, opts: { reason?: "scheduled" | "trigger" } = {}): Promise<PollResult> { /* ... */ }
   ```

   **Sub-step 4a — Lease+debounce transaction (the gate):**
   ```ts
   const statusRef = db().doc(`users/${uid}/sync_status`);
   const claim = await db().runTransaction(async (tx) => {
     const snap = await tx.get(statusRef);
     const data = snap.data() ?? {};
     const now = Timestamp.now();
     // Debounce
     if (opts.reason === "trigger" && data.lastPolledAt) {
       const elapsedMs = now.toMillis() - (data.lastPolledAt as Timestamp).toMillis();
       if (elapsedMs < DEBOUNCE_MS) {
         return { kind: "debounced" as const, retryAfter: Math.ceil((DEBOUNCE_MS - elapsedMs) / 1000) };
       }
     }
     // Lease (applies to both scheduled and trigger)
     const lease = data.poll_lease as { holder: string; expires_at: Timestamp } | undefined;
     if (lease && lease.expires_at.toMillis() > now.toMillis()) {
       return { kind: "in_progress" as const };
     }
     const holder = `${opts.reason ?? "unknown"}_${now.toMillis()}_${Math.random().toString(36).slice(2, 10)}`;
     tx.set(statusRef, {
       poll_lease: { holder, acquired_at: now, expires_at: Timestamp.fromMillis(now.toMillis() + LEASE_TTL_MS) },
       updatedAt: FieldValue.serverTimestamp(),
     }, { merge: true });
     return { kind: "acquired" as const, holder };
   });
   if (claim.kind !== "acquired") {
     return { ok: false, reason: claim.kind, ...(claim.kind === "debounced" ? { retryAfter: claim.retryAfter } : {}) };
   }
   ```

   **Sub-step 4b — Refresh-token grant with conditional rotation persist (PO Round 1 Q1):**
   ```ts
   const storedRt = await getRefreshToken(uid);
   if (!storedRt) {
     await releaseLeaseWithError(uid, claim.holder, "no_refresh_token");
     return { ok: false, reason: "no_refresh_token" };
   }
   const { clientId, clientSecret } = await getXClientCredentials();
   const basicAuth = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
   const refreshResp = await fetch(TOKEN_URL, {
     method: "POST",
     headers: { "Content-Type": "application/x-www-form-urlencoded", Authorization: `Basic ${basicAuth}` },
     body: new URLSearchParams({ grant_type: "refresh_token", refresh_token: storedRt, client_id: clientId }),
   });
   if (!refreshResp.ok) {
     const body = await refreshResp.json().catch(() => ({})) as { error?: string };
     const errorCode = body.error ?? `http_${refreshResp.status}`;
     await releaseLeaseWithError(uid, claim.holder, errorCode === "invalid_grant" ? "refresh_revoked" : errorCode, { setUnlinked: errorCode === "invalid_grant" });
     return { ok: false, reason: errorCode === "invalid_grant" ? "refresh_revoked" : "rate_limited" };
   }
   const tokens = await refreshResp.json() as { access_token: string; refresh_token?: string };
   const accessToken = tokens.access_token;
   // Conditional rotation persist (PO Round 1 Q1: "Only persist on rotation"):
   if (tokens.refresh_token && tokens.refresh_token !== storedRt) {
     await setRefreshToken(uid, tokens.refresh_token);
     logger.info("daily_poll_rt_rotated", { uid });
   }
   ```

   **Sub-step 4c — X user-id lookup with cache (PO Round 1 Q3):**
   ```ts
   const statusSnap = await statusRef.get();
   let xUserId: string | undefined = statusSnap.data()?.xUserId;
   if (!xUserId) {
     const meResp = await fetch(USERS_ME_URL, { headers: { Authorization: `Bearer ${accessToken}` } });
     if (!meResp.ok) {
       await releaseLeaseWithError(uid, claim.holder, "x_user_lookup_failed");
       return { ok: false, reason: "x_user_lookup_failed" };
     }
     const me = await meResp.json() as { data: { id: string } };
     xUserId = me.data.id;
     // Persisted as part of the lease-release write at end of poll; here just keep in memory.
   }
   ```

   **Sub-step 4d — Paginated bookmark fetch with stop-on-overlap + 429 backoff:**
   ```ts
   const latestIdSnap = await db().collection(`users/${uid}/tweets`).orderBy("id", "desc").limit(1).select().get();
   const latestIdInDb: string | undefined = latestIdSnap.empty ? undefined : latestIdSnap.docs[0].id;

   const collected: Array<unknown> = [];
   let nextToken: string | undefined;
   let stop = false;
   do {
     const url = buildBookmarksUrl(xUserId, nextToken);
     const resp = await fetchWithBackoff(url, accessToken); // see sub-step 4e
     if (!resp) {
       await releaseLeaseWithError(uid, claim.holder, "rate_limited");
       return { ok: false, reason: "rate_limited" };
     }
     const json = await resp.json() as { data?: Array<{ id: string }>; includes?: unknown; meta?: { next_token?: string } };
     const page = json.data ?? [];
     for (const tweet of page) {
       if (latestIdInDb && tweet.id === latestIdInDb) { stop = true; break; }
       collected.push({ tweet, includes: json.includes });
     }
     nextToken = json.meta?.next_token;
   } while (!stop && nextToken);
   ```

   **Sub-step 4e — `fetchWithBackoff` helper (inline; reused for `/users/me` too):**
   ```ts
   async function fetchWithBackoff(url: string, accessToken: string): Promise<Response | null> {
     for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
       const resp = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } });
       if (resp.status !== 429 && resp.status < 500) return resp;
       if (attempt === MAX_RETRIES) return null;
       const resetHeader = resp.headers.get("x-rate-limit-reset");
       const waitMs = resetHeader
         ? Math.max(0, Number(resetHeader) * 1000 - Date.now() + 1000) // +1s buffer
         : BACKOFF_BASE_MS * Math.pow(2, attempt); // 1s, 2s, 4s
       const cappedMs = Math.min(waitMs, 60_000); // never sleep > 60s
       await new Promise((r) => setTimeout(r, cappedMs));
     }
     return null;
   }
   ```

   **Sub-step 4f — Batched writes with content-derived composite IDs (PO Round 2 Q6):**
   ```ts
   const writes: Array<[FirebaseFirestore.DocumentReference, Record<string, unknown>]> = [];
   const seenRefs = new Set<string>(); // dedup
   for (const { tweet, includes } of collected) {
     const tRef = db().doc(`users/${uid}/tweets/${tweet.id}`);
     writes.push([tRef, { ...tweet, pending_delete: false, updatedAt: FieldValue.serverTimestamp() }]);
     // Sibling: metrics
     if (tweet.public_metrics) writes.push([db().doc(`users/${uid}/metrics/${tweet.id}`), { ...tweet.public_metrics, updatedAt: FieldValue.serverTimestamp() }]);
     // includes: content-derived composite IDs
     for (const u of (includes?.users ?? [])) {
       writes.push([db().doc(`users/${uid}/twitter_users/${u.id}`), { ...u, updatedAt: FieldValue.serverTimestamp() }]);
       writes.push([db().doc(`users/${uid}/includes/${tweet.id}_user_${u.id}`), { tweetId: tweet.id, userId: u.id, kind: "user" }]);
     }
     for (const m of (includes?.media ?? [])) {
       writes.push([db().doc(`users/${uid}/media/${m.media_key}`), { ...m, updatedAt: FieldValue.serverTimestamp() }]);
       writes.push([db().doc(`users/${uid}/includes/${tweet.id}_media_${m.media_key}`), { tweetId: tweet.id, mediaKey: m.media_key, kind: "media" }]);
     }
     for (const r of (tweet.referenced_tweets ?? [])) {
       writes.push([db().doc(`users/${uid}/includes/${tweet.id}_ref_${r.id}`), { tweetId: tweet.id, referencedId: r.id, type: r.type, kind: "referenced_tweet" }]);
     }
     for (const ann of (tweet.entities?.annotations ?? [])) {
       writes.push([db().doc(`users/${uid}/textAnnotations/${tweet.id}_${ann.type}_${ann.start}_${ann.end}`), { tweetId: tweet.id, ...ann }]);
     }
   }
   // Dedup by path
   const deduped = writes.filter(([ref]) => {
     const p = ref.path; if (seenRefs.has(p)) return false; seenRefs.add(p); return true;
   });
   // Path-guard
   for (const [ref] of deduped) {
     if (!ref.path.startsWith(`users/${uid}/`)) throw new Error(`path_guard_violation: ${ref.path}`);
   }
   // Batch in 450-op chunks
   for (let i = 0; i < deduped.length; i += BATCH_SIZE) {
     const batch = db().batch();
     deduped.slice(i, i + BATCH_SIZE).forEach(([ref, data]) => batch.set(ref, data, { merge: true }));
     await batch.commit();
   }
   ```

   **Sub-step 4g — Pending-delete diff (PO Round 1 Q4: clear to false on reappearance):**
   ```ts
   const existingIdsSnap = await db().collection(`users/${uid}/tweets`).select().get();
   const existingIds = new Set(existingIdsSnap.docs.map((d) => d.id));
   const collectedIds = new Set(collected.map((c) => c.tweet.id));
   const missingNow = [...existingIds].filter((id) => !collectedIds.has(id));
   const pdBatch = db().batch();
   let flagged = 0;
   for (const id of missingNow) {
     // Skip tombstoned docs (pending-delete slice writes deleted: true)
     const docSnap = await db().doc(`users/${uid}/tweets/${id}`).get();
     if (docSnap.data()?.deleted === true) continue;
     pdBatch.set(db().doc(`users/${uid}/tweets/${id}`), { pending_delete: true, pending_delete_detected_at: Timestamp.now(), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
     flagged++;
   }
   if (flagged > 0) await pdBatch.commit();
   // Reappearance reset: pending_delete: false is already set inside the main writes loop above (every persisted tweet gets pending_delete: false explicitly)
   ```

   **Sub-step 4h — Finally: release lease + write sync_status:**
   ```ts
   try { /* sub-steps 4b-4g */ }
   finally {
     await statusRef.set({
       linked: true,
       lastPolledAt: FieldValue.serverTimestamp(),
       lastError: null,
       itemsAdded: collected.length,
       xUserId,
       poll_lease: null,
       updatedAt: FieldValue.serverTimestamp(),
     }, { merge: true });
   }
   return { ok: true, itemsAdded: collected.length, itemsFlaggedPendingDelete: flagged };
   ```
   `releaseLeaseWithError(uid, holder, errorCode, { setUnlinked? })` is a helper that writes `{ poll_lease: null, lastError: errorCode, updatedAt: serverTimestamp(), ...(setUnlinked ? { linked: false } : {}) }` with `{ merge: true }`. Lease release is the invariant — it MUST run on every exit path.

5. **`npm run build` after Step 4.** Must compile cleanly under strict TS. Fix any type errors before tests.

6. **Author `functions/test/fakes/firestore.ts`.** ~120 LOC. Skeleton:
   ```ts
   export type JournalEntry =
     | { op: "set"; path: string; data: Record<string, unknown>; merge: boolean }
     | { op: "get"; path: string }
     | { op: "batchCommit"; entries: Array<{ path: string; data: Record<string, unknown>; merge: boolean }> }
     | { op: "transactionStart" }
     | { op: "transactionCommit" }
     | { op: "queryGet"; path: string; orderBy?: string; limit?: number; whereField?: string; whereOp?: string; whereValue?: unknown };

   export interface FakeDb { /* doc(path), collection(path), batch(), runTransaction(cb) */ }

   export function createFakeDb(): { db: FakeDb; journal: JournalEntry[]; seed: (path: string, data: Record<string, unknown>) => void } {
     // Chainable proxy implementation
   }
   ```
   Seeded reads return `{ exists: true, data: () => seededValue, id: lastPathSegment }`; unseeded reads return `{ exists: false, data: () => undefined }`. Collection `.select().get()` returns `{ empty: boolean, docs: Array<{ id, ref: { path } }> }` filtered by parent-path match. `runTransaction(cb)` calls the callback with a `tx` object exposing `get()`, `set()`, `update()` recorded in the journal.

7. **Author `functions/test/daily-poll.test.ts`.** Mock setup:
   ```ts
   jest.mock("../src/lib/admin", () => ({ db: jest.fn(), app: jest.fn() }));
   jest.mock("../src/lib/secrets", () => ({
     getRefreshToken: jest.fn(),
     setRefreshToken: jest.fn(),
     getXClientCredentials: jest.fn(async () => ({ clientId: "c", clientSecret: "s" })),
   }));
   jest.mock("firebase-admin/firestore", () => ({
     FieldValue: { serverTimestamp: jest.fn(() => "<server-ts>") },
     Timestamp: {
       now: () => ({ toMillis: () => Date.now(), seconds: Math.floor(Date.now() / 1000) }),
       fromMillis: (ms: number) => ({ toMillis: () => ms, seconds: Math.floor(ms / 1000) }),
     },
   }));
   ```
   Each test injects a `createFakeDb()` instance per case via `(adminModule.db as jest.Mock).mockReturnValue(fake.db)`. Cases per Likely-Files §Tests (a)-(f) plus the schedule-event smoke (g).

8. **Author `functions/test/trigger-poll.test.ts`.** Same mock scaffolding as daily-poll.test.ts. Invoke handler via:
   ```ts
   await (triggerPoll as unknown as (request: unknown) => Promise<unknown>)({ auth: { uid: "uid1" }, data: {}, rawRequest: {} });
   ```
   Four cases: unauth, debounced, in_progress, happy path.

9. **Run `cd functions && npm test`.** All 20 cases green (9 existing + 11 new). Triage failures back into Steps 4-8.

10. **Run `cd functions && npm run lint`.** No errors. Special vigilance: `no-console` (use `logger`), `no-restricted-imports` (no `undici`), `no-implicit-coercion` (use explicit `Number()`/`String()`).

11. **Author `functions/src/handlers/dailyPoll.ts`:**
    ```ts
    import { onSchedule } from "firebase-functions/v2/scheduler";
    import { logger } from "firebase-functions/v2";

    export const dailyPoll = onSchedule(
      { schedule: "0 9,21 * * *", timeZone: "UTC", region: "europe-west2", timeoutSeconds: 540, memory: "512MiB" },
      async (event) => {
        const { db } = await import("../lib/admin");
        const { runPoll } = await import("../lib/poll");
        logger.info("daily_poll_started", { scheduleTime: event.scheduleTime, jobName: event.jobName });
        const linkedSnap = await db().collectionGroup("sync_status").where("linked", "==", true).get();
        for (const doc of linkedSnap.docs) {
          const uid = doc.ref.parent.parent?.id;
          if (!uid) continue;
          try {
            const result = await runPoll(uid, { reason: "scheduled" });
            logger.info("daily_poll_user_completed", { uid, result });
          } catch (e) {
            logger.error("daily_poll_user_failed", { uid, code: (e as Error).message });
          }
        }
        logger.info("daily_poll_completed", { userCount: linkedSnap.docs.length });
      },
    );
    ```
    **Note on `collectionGroup`:** Firestore's `collectionGroup("sync_status")` queries all collections named `sync_status` at any depth. After Step 1's path move, `sync_status` is at depth 2 (`users/{uid}/sync_status`); collectionGroup will return it. **Requires a single-field index on `sync_status.linked`** — declare in `firestore.indexes.json` (NEW file at repo root) and deploy via `firebase deploy --only firestore:indexes`. See Step 12.

12. **Create `firestore.indexes.json` at repo root:**
    ```json
    {
      "indexes": [
        {
          "collectionGroup": "sync_status",
          "queryScope": "COLLECTION_GROUP",
          "fields": [
            { "fieldPath": "linked", "order": "ASCENDING" }
          ]
        }
      ],
      "fieldOverrides": []
    }
    ```
    Update `firebase.json` to add `"firestore": { "rules": "firestore.rules", "indexes": "firestore.indexes.json" }`. Deploy via `firebase deploy --only firestore:indexes` (operator step). **Index build can take several minutes** — surface in operator checklist.

13. **Author `functions/src/handlers/triggerPoll.ts`:**
    ```ts
    import { onCall, HttpsError } from "firebase-functions/v2/https";

    export const triggerPoll = onCall(
      { region: "europe-west2", timeoutSeconds: 60 },
      async (request) => {
        if (!request.auth) throw new HttpsError("unauthenticated", "Sign-in required");
        const { runPoll } = await import("../lib/poll");
        return await runPoll(request.auth.uid, { reason: "trigger" });
      },
    );
    ```

14. **Append re-exports to `functions/src/index.ts`:**
    ```ts
    export { dailyPoll } from "./handlers/dailyPoll";
    export { triggerPoll } from "./handlers/triggerPoll";
    ```

15. **Run `cd functions && npm run build`.** Must compile clean.

16. **Re-run `cd functions && npm test`.** The schedule-event smoke case in `daily-poll.test.ts` (case g) now passes against the wired handler.

17. **Author `scripts/verify-function-iam.sh`:**
    ```bash
    #!/usr/bin/env bash
    set -euo pipefail

    PROJECT_ID="crumbs-a4fdb"
    SA_EMAIL="crumb-twitter-poller@${PROJECT_ID}.iam.gserviceaccount.com"
    UID_ARG="${1:-6yPmdM14V3dPHLe3LO9XCfU4l9f1}"

    fail() { echo "FAIL: $*" >&2; exit 1; }
    pass() { echo "PASS: $*"; }

    echo "==> Verifying function runtime SA"
    for FN in dailyPoll triggerPoll oauthCallback mintOAuthState warmUp; do
      RUNTIME_SA=$(gcloud functions describe "$FN" --gen2 --region=europe-west2 --project="$PROJECT_ID" --format='value(serviceConfig.serviceAccountEmail)' 2>/dev/null || echo "")
      [[ "$RUNTIME_SA" == "$SA_EMAIL" ]] || fail "$FN runtime SA is '$RUNTIME_SA', expected '$SA_EMAIL'"
      pass "$FN -> $SA_EMAIL"
    done

    echo "==> Verifying per-secret secretAccessor bindings"
    for SECRET in "crumb-x-refresh-token-${UID_ARG}" crumb-oauth-state-secret crumb-x-client-id crumb-x-client-secret; do
      gcloud secrets get-iam-policy "$SECRET" --project="$PROJECT_ID" --format=json \
        | jq -e --arg sa "serviceAccount:$SA_EMAIL" '.bindings[] | select(.role == "roles/secretmanager.secretAccessor") | .members[] | select(. == $sa)' >/dev/null \
        || fail "$SECRET missing secretAccessor for $SA_EMAIL"
      pass "$SECRET secretAccessor -> $SA_EMAIL"
    done

    echo "==> Verifying NO project-level Firestore roles on SA"
    BAD=$(gcloud projects get-iam-policy "$PROJECT_ID" --format=json \
      | jq --arg sa "serviceAccount:$SA_EMAIL" '
          [.bindings[]
           | select(.role == "roles/datastore.user" or .role == "roles/datastore.owner" or .role == "roles/datastore.writer")
           | select(.members[] | . == $sa)
           | .role]')
    [[ "$BAD" == "[]" ]] || fail "SA has unexpected Firestore roles: $BAD"
    pass "SA has no project-level Firestore roles"

    echo "==> ALL CHECKS PASSED"
    ```
    Runs in bash (WSL / Git Bash on Windows, or Linux CI). Document the WSL invocation in the operator checklist.

18. **Smoke-test the build.** `cd functions && npm run build && npm test && npm run lint`. All three green.

19. **Deploy functions (operator step at implement time):** `firebase deploy --only functions:crumb-oauth:dailyPoll,functions:crumb-oauth:triggerPoll,functions:crumb-oauth:oauthCallback --project crumbs-a4fdb`. Include `oauthCallback` because Step 1 amended the sync_status path; without re-deploy the live function still writes to the old path.

20. **Deploy firestore.indexes.json (operator step):** `firebase deploy --only firestore:indexes --project crumbs-a4fdb`. Wait for the index build to complete (poll Firebase Console → Firestore → Indexes; takes ~1-10 minutes for an empty collection-group).

21. **Verify Cloud Scheduler `attemptDeadline`:** `gcloud scheduler jobs describe firebase-schedule-dailyPoll-europe-west2 --location europe-west2 --format='value(attemptDeadline)'`. Must be `540s`. If less, `gcloud scheduler jobs update http firebase-schedule-dailyPoll-europe-west2 --location europe-west2 --attempt-deadline=540s`.

22. **Smoke-test triggerPoll (operator):** Via `firebase functions:shell` → `triggerPoll({}, { auth: { uid: "<your-uid>" } })`. Must return `{ ok: true, itemsAdded: N }` on first invocation; immediate re-invoke returns `{ ok: false, reason: "debounced", retryAfter: ~60 }`.

23. **Smoke-test dailyPoll (operator):** `gcloud scheduler jobs run firebase-schedule-dailyPoll-europe-west2 --location europe-west2`. Watch logs: `firebase functions:log --only dailyPoll --lines 50`. Must complete within 540s for the single-user use case.

24. **Update workflow control files:**
    - `00-index.md`: clear 4 of the 5 open questions (cron now `0 9,21 * * *`; pending-delete auto-expiry → "never"; HMAC max-age → 10 min confirmed by functions-oauth; triggerPoll debounce → 60s); add `04-plan-daily-poll.md` to `workflow-files`; update `updated-at`; set `next-command: wf-implement`, `next-invocation: "/wf implement cloud-function-bookmark-sync daily-poll"`. Keep the 5th open question (Maestro coverage for X authorize) — that's android-reader's problem.
    - `04-plan.md`: refresh `slices-planned: 2 → 3`, append daily-poll summary, refresh cross-cutting concerns + integration points + freshness research + recommended-next-stage.
    - `po-answers.md`: append the 12-question plan-stage block.
    - `.ai/workflows/INDEX.md`: touch `updated-at` only.

## Test / Verification Plan

### Automated checks

- **Lint:** `cd functions && npm run lint` — fails on any `console.log`, any `undici` import, any `any` type leak, any implicit coercion.
- **Build:** `cd functions && npm run build` — TypeScript strict ES2022/CommonJS.
- **Unit tests:** `cd functions && npm test` — 20 cases total (6 state + 3 oauthCallback existing + 7 daily-poll + 4 trigger-poll). All hand-rolled, no `firebase-functions-test` wrapper.
- **No Android-side gradle changes** — `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:verifyRoborazziDebug` remain UP-TO-DATE.

### Interactive verification (human-in-the-loop)

**Stack from [00-index.md](.ai/workflows/cloud-function-bookmark-sync/00-index.md)** (`stack.user-confirmed: true`): `platforms: [android, service]`, `testing: [junit, roborazzi, maestro, jest]`, `available-cli: [firebase, gcloud, android, lazylogcat, maestro]`. Source of truth.

This slice leans on `firebase` + `gcloud` CLIs + Cloud Logging — *not* Maestro, no Android touch. `lazylogcat` not applicable.

- **dailyPoll on schedule (AC: `dailyPoll` triggered by Cloud Scheduler writes new tweets + advances `sync_status.lastPolledAt`).**
  - Steps: `gcloud scheduler jobs run firebase-schedule-dailyPoll-europe-west2 --location europe-west2 --project crumbs-a4fdb`. Wait 60s. Then `firebase firestore:get users/${UID}/sync_status` — `lastPolledAt` field must be a recent timestamp; `linked` must be `true`; `lastError` must be `null`.
  - Companion: `firebase functions:log --only dailyPoll --lines 50` — must contain `daily_poll_started`, `daily_poll_user_completed`, `daily_poll_completed` lines in order. No `daily_poll_user_failed` rows.
  - Pass criteria: lastPolledAt advances; itemsAdded recorded; one new tweet doc appears under `users/${UID}/tweets/` (operator must have a fresh X bookmark to verify additivity).

- **triggerPoll debounce (AC: `triggerPoll` debounces at 60s).**
  - Steps: `firebase functions:shell` → `triggerPoll({}, { auth: { uid: "${UID}" } })` returns `{ ok: true, itemsAdded: N }`. Immediately re-invoke → `{ ok: false, reason: "debounced", retryAfter: ~60 }`. Wait 70s → re-invoke returns `{ ok: true, itemsAdded: 0 }`.
  - Pass criteria: both branches return expected shape; no fetch logs in the debounced case.

- **triggerPoll in-progress lease.**
  - Steps: simulate concurrent invocation by writing a fake lease directly: `firebase firestore:set users/${UID}/sync_status '{"poll_lease": {"holder": "test", "expires_at": "&lt;now+30s&gt;"}}'`. Invoke `triggerPoll` → `{ ok: false, reason: "in_progress" }`. Clean up: `firebase firestore:set users/${UID}/sync_status '{"poll_lease": null}' --merge`.
  - Pass criteria: lease-held call returns `in_progress` without touching X.

- **Pending-delete server flag (AC: tweet present last poll absent this poll → `pending_delete: true`).**
  - Steps: from X, un-bookmark a tweet. Trigger `dailyPoll` (or `triggerPoll`). Inspect the tweet's Firestore doc — `pending_delete: true`, `pending_delete_detected_at: <timestamp>`. Re-bookmark on X. Re-trigger. `pending_delete: false`.
  - Pass criteria: flag set when absent; flag cleared on reappearance.

- **IAM verification (AC10).**
  - Steps: `bash scripts/verify-function-iam.sh ${UID}` (run via WSL or Git Bash on Windows). Must exit 0.
  - Pass criteria: all assertions PASS; no FAIL output.

- **Cloud Scheduler `attemptDeadline` matches function timeout.**
  - Steps: `gcloud scheduler jobs describe firebase-schedule-dailyPoll-europe-west2 --location europe-west2 --format='value(attemptDeadline)'` → must equal `540s`.
  - Pass criteria: deadline ≥ function `timeoutSeconds`.

- **`roles/run.invoker` for Gen 2 Scheduler.**
  - Steps: `gcloud run services get-iam-policy dailypoll --region europe-west2 --format=json | jq '.bindings[] | select(.role=="roles/run.invoker")'` — must contain the Cloud Scheduler SA as member.
  - Pass criteria: binding present.

If a criterion needs tooling outside `stack:`: none. All tooling above is in `stack.available-cli`.

### Operator-confirmed prereqs

The slice's 7-item operator checklist (Step 24 + the Likely-Files prereqs block) is not automatable beyond `verify-function-iam.sh`. Implement-stage records explicit checkbox completion before declaring the slice ready for verify.

## Risks / Watchouts

- **X refresh-token rotation is single-use** (web research correction to intake). If we skip persisting a rotated RT after a successful exchange — even one time — the user is broken and must re-auth manually. Mitigation: `setRefreshToken` is idempotent (add-then-disable-previous); we persist conditionally on `response.refresh_token !== storedRt`. PO Round 1 Q1 picked the conditional-persist path. The risk is the comparison-path bug; covered by daily-poll.test.ts case (b) which mocks a rotated RT in the refresh response.
- **X pagination bug at `max_results=100` is still active in 2026** (web research). Locked to `max_results=50` in `lib/twitter-api.ts`. Even at 50, the slice's case (f) test emulates a missing `next_token` mid-stream to verify graceful loop termination.
- **`collectionGroup("sync_status")` requires a Firestore index** that is not auto-created. Mitigation: ship `firestore.indexes.json` and document the `firebase deploy --only firestore:indexes` step + the multi-minute index build wait in the operator checklist.
- **Path-layout drift between migration script and slice spec** resolved per PO Round 1 Q2 (match migration; move sync_status). Requires editing two literal strings in `oauthCallback.ts` + one expectation in its test (Steps 1-2 above). Without re-deploying `oauthCallback` after Step 1, live writes still go to the old path. **Step 19 explicitly re-deploys `oauthCallback`** to prevent a partial-state production system.
- **`firebase-functions-test` v2 onSchedule unsupported** ([issue #210](https://github.com/firebase/firebase-functions-test/issues/210), open since 2023, still open May 2026). Mitigation: tests invoke `runPoll(uid)` directly (the pure function) and use a single smoke case casting `dailyPoll.run(event)` for the wiring check. PO Round 2 Q7 confirmed hand-rolled.
- **Hard-restrict path leak.** A bug that writes outside `users/{uid}/**` breaches the security model. Mitigation: every Firestore write in `lib/poll.ts` goes through a path-guard (`if (!ref.path.startsWith(\`users/${uid}/\`)) throw`). Unit test: feed a uid containing `..` or `/` and assert the guard throws before any Firestore call.
- **Lease leak on uncaught exception.** If `lib/poll.ts` crashes between lease-claim and lease-release, the 30s TTL on `poll_lease.expires_at` is the recovery. Mitigation: `try { ... } finally { releaseLeaseWithError(...) }` wraps every code path between lease-claim and the final write; integration test (case g) asserts `poll_lease: null` after a thrown internal error.
- **Cron `0 9,21 * * *` UTC** (PO Round 3 Q9) doubles invocation frequency vs slice-spec default `0 9 * * *`. Cost negligible (<$0.05/month single-user). Documents on the runtime-evidence-deferrals at verify time.
- **`/users/me` round-trip per cold start.** First poll after deploy calls `/2/users/me` to resolve the X user id; cached on `sync_status.xUserId`. Cost: 1 extra HTTPS call per user lifetime, well below the 75/15min rate limit. PO Round 1 Q3 picked first-poll-cache.
- **Refresh-token grant 429.** X bookmarks rate limit (180/15min OAuth2 user context) is the daily limit; `/oauth2/token` has a separate rate limit. The 3-attempt exponential backoff covers both. Worst case → `lastError: "rate_limited"`, `linked: true` preserved, next poll picks up. Acceptable.
- **`firebase-functions-test@^3` stays in devDeps** even though daily-poll doesn't use it. Other slices may; uninstalling now would force a re-install later.
- **`crumb-x-refresh-token-${UID}` secretAccessor binding timing.** The secret is created on the FIRST successful `oauthCallback` (functions-oauth's `setRefreshToken`). The binding must be applied AFTER that — currently a manual `gcloud` step. Mitigation: bundle into the operator checklist; alternative is a `secrets.ts` change to auto-bind on createSecret (out of scope this slice — `iam.policies.set` requires project-level perms the SA shouldn't have).
- **Read cost for set-difference computation.** 1 read per tweet doc currently stored under `users/{uid}/tweets/`. For the 800-cap user, that's 800 reads/poll × 2 polls/day = 1600 reads/day. Free tier 50k/day; cost negligible. Document in §Performance.
- **Field-list constants duplicated** between `feature/twitter/.../TwitterApiService.kt` and `functions/src/lib/twitter-api.ts`. Until `cutover-migration` deletes the Android client, both copies must stay in sync. Mitigation: note in `lib/twitter-api.ts` header comment + add a slug-wide review check at `/wf review` time.
- **Cloud Functions Gen 2 cold-start.** `dailyPoll` 9-min budget eats cold-start cost easily. `triggerPoll` 60s budget could be tight on a 5-page backfill with a cold-start spike — operator UX is "wait 8-10s for first refresh." Acceptable per shape Q18 design (foreground pre-warm).
- **Cloud Scheduler `attemptDeadline` mismatch.** firebase-functions sets `attemptDeadline = timeoutSeconds` on deploy; if `attemptDeadline < timeoutSeconds`, Cloud Scheduler kills the request mid-flight while the function continues. Mitigation: explicit verify in Step 21 + operator checklist.
- **Path move ordering: oauthCallback amendment (Step 1) MUST precede daily-poll deploy.** Otherwise daily-poll's `dailyPoll` handler queries `collectionGroup("sync_status")` finds zero docs (since they're at `users/{uid}/twitter/sync_status`). Step 19 deploys both together.

## Dependencies on Other Slices

- **From `functions-oauth` (already shipped, commit `35493b9`):** the functions project, `lib/{admin,secrets}.ts`, dedicated SA `crumb-twitter-poller`, per-secret accessor bindings, project-level secretVersionAdder/Manager, X portal `redirect_uri` registration with `offline.access` scope, Cloud Scheduler `warmup-keepalive` job. All daily-poll prereqs except per-uid secret binding + new project-level role audit.
- **From `auth-foundation` (already shipped, commit `8f391f2`):** `request.auth.uid` populated on `triggerPoll` calls; `AuthGateway` not consumed function-side (server has direct uid via callable context).
- **Forward dependency on `android-reader`:** owns the `triggerPoll` call site, the `sync_status` repository (`SyncStatusRepository`), and the pull-to-refresh UI wiring. Daily-poll's `triggerPoll` return shape (`{ok, itemsAdded?, reason?, retryAfter?}`) is the contract — `android-reader` plan must consume this verbatim. Also owns the live Maestro `pull_to_refresh.yaml` flow that exercises the full triggerPoll round-trip.
- **Forward dependency on `pending-delete`:** consumes the `pending_delete: true` flag on tweet docs. Daily-poll writes `pending_delete: false` on every persisted tweet (reset-on-reappearance per PO Round 1 Q4). pending-delete slice's Room v9→v10 column + query (`WHERE pending_delete = 1`) reads this server-side flag.
- **Forward dependency on `cutover-migration`:** consumes `lib/secrets.setRefreshToken` (already exported by functions-oauth; daily-poll doesn't change the surface). Also responsible for deleting the orphaned `users/{uid}/twitter/...` subtree (functions-oauth's original sync_status path before Step 1's move; only relevant if oauthCallback was deployed pre-this-slice and wrote at least once to the old path — Step 19's combined deploy prevents this in practice).

## Assumptions

- The Firebase project ID is `crumbs-a4fdb` and the region is `europe-west2`. Confirmed by [scripts/firestore-migrate/migrate.mjs:22-23](scripts/firestore-migrate/migrate.mjs) and the functions-oauth plan.
- The user's UID is `6yPmdM14V3dPHLe3LO9XCfU4l9f1` (operator-managed; not committed). Used as the default arg for `verify-function-iam.sh`.
- The X (Twitter) developer portal app is configured as a confidential client with `offline.access` scope (functions-oauth operator checklist).
- Firebase CLI ≥ `14.0`, `gcloud` CLI, and `jq` are installed on the operator's machine. `bash` available (WSL or Git Bash on Windows).
- The X v2 OAuth `/oauth2/token` refresh-token grant returns the new refresh token in the `refresh_token` field of the JSON response (standard OAuth2 RFC 6749 §6).
- `firebase-functions@^7` `onSchedule` accepts `timeZone: "UTC"` and `timeoutSeconds: 540` (Gen 2 event-driven max). Verified against ScheduleOptions interface at implement time.
- `collectionGroup("sync_status")` queries the renamed sync_status path post-Step-1. Verified by integration test (case g).
- `firebase-functions-test@^3` does not support v2 onSchedule per issue #210. If this is fixed before this slice ships, tests can optionally adopt `test.wrap(dailyPoll)`. Sanity-checked at implement time.
- The X `/users/me` endpoint returns `{ data: { id: "..." } }` on success. Standard X v2 shape.
- Refresh-token rotation IS active (web research correction). If X reverts to no-rotation, the `tokens.refresh_token !== storedRt` guard turns the `setRefreshToken` call into a no-op — safe fallback.

## Blockers

- **None blocking the plan.** All discovery questions resolved (PO answered 12 across 3 rounds). Operator prereqs are checklist items at implement time, not blockers.

## Freshness Research

- **`firebase-functions@^7` `onSchedule` v2** (April 2026) — confirmed `ScheduleOptions` interface accepts `{ schedule, timeZone?, region?, timeoutSeconds?, memory?, retryCount?, maxRetrySeconds?, minBackoffSeconds?, maxBackoffSeconds?, maxDoublings? }`. `ScheduleEvent` has `{ jobName?, scheduleTime }`. No documented default for `timeZone` — must pass explicitly. Source: [firebase.google.com/docs/reference/functions/2nd-gen/node/firebase-functions.scheduler.scheduleoptions](https://firebase.google.com/docs/reference/functions/2nd-gen/node/firebase-functions.scheduler.scheduleoptions).
- **X v2 OAuth refresh-token rotation** — Single-use refresh tokens, 6-month validity. Must replace stored RT with the new one in the response. Source: [docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code](https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code) + [devcommunity.x.com community-confirmed behavior](https://devcommunity.x.com/t/twitter-api-refreshing-access-tokens/214281). **Material correction to intake's "no rotation" claim — propagated to Risks + Step 4b + slice implementation.**
- **X v2 bookmarks pagination bug at `max_results=100`** — Still active May 2026; pagination stops after ~3 pages with missing `meta.next_token`. Workaround: lower `max_results`. Slice locks to `50`. Source: [devcommunity.x.com/t/bookmarks-api-v2-stops-paginating-after-3-pages](https://devcommunity.x.com/t/bookmarks-api-v2-stops-paginating-after-3-pages-no-next-token-returned/257339).
- **X v2 bookmarks rate limit** — 180 requests / 15 minutes per OAuth2 user context. Headers: `x-rate-limit-limit`, `x-rate-limit-remaining`, `x-rate-limit-reset` (Unix seconds). Source: [docs.x.com/x-api/fundamentals/rate-limits](https://docs.x.com/x-api/fundamentals/rate-limits).
- **Exponential backoff for X 429** — Honor `x-rate-limit-reset` Unix timestamp when present; else 1s/2s/4s with cap; cap individual wait at 60s; cap total wait at function timeout - 30s. Hand-rolled (no library); avoids cold-start hit of `p-retry` / `axios-retry`. Source: [twitterapi.io/articles/handling-twitter-api-rate-limits-best-practices](https://twitterapi.io/articles/handling-twitter-api-rate-limits-best-practices).
- **Firestore admin v13 transactions** — `db.runTransaction(async (tx) => { const snap = await tx.get(ref); tx.set(ref, ..., {merge: true}); })`. Pessimistic locking; auto-retry on contention (default 5 attempts). Use `Timestamp.now()` (not `serverTimestamp()`) for in-transaction comparisons. Source: [firebase.google.com/docs/firestore/manage-data/transactions](https://firebase.google.com/docs/firestore/manage-data/transactions).
- **Firestore batch-write 500-op cap** — Confirmed unchanged. Each doc may appear at most once per batch. `batch.set(ref, data, { merge: true })` is the safe idiom. Daily-poll uses `BATCH_SIZE = 450` (headroom). Source: [oneuptime batch-writes guide](https://oneuptime.com/blog/post/2026-02-17-how-to-use-firestore-batch-writes-to-update-multiple-documents-atomically/view).
- **Cloud Functions Gen 2 timeouts** — HTTPS / callable max `3600s`; event-driven (onSchedule) max `540s`. Default `60s`. Source: [Cloud Functions Gen 2 docs discussion](https://discuss.google.dev/t/cloud-function-2nd-gen-nodejs-set-timeout/158864).
- **Cloud Scheduler `attemptDeadline`** — Default 180s for HTTP target; range 15s-30min. firebase-functions sets it to match `timeoutSeconds` on deploy. Verify post-deploy. Source: [Cloud Scheduler Job config](https://docs.cloud.google.com/scheduler/docs/overview).
- **Gen 2 Scheduler caller IAM** — `roles/run.invoker` on the Cloud Run service (not `cloudfunctions.invoker` — that's Gen 1). Auto-bound by firebase-functions deploy. Source: [Cloud Scheduler HTTP target auth](https://cloud.google.com/scheduler/docs/http-target-auth).
- **`firebase-functions-test@^3` v2 onSchedule gap** — Wrapper emits v1 ScheduledEvent shapes for v2 handlers. Still open May 2026. Workaround: factor pure logic out of the handler and test it directly. Source: [firebase/firebase-functions-test#210](https://github.com/firebase/firebase-functions-test/issues/210).
- **Jest `mockResolvedValueOnce` for sequential fetch mocks** + **`useFakeTimers` + `advanceTimersByTimeAsync` for backoff testing** — Standard pattern. Sources: [jestjs.io/docs/mock-function-api#mockfnmockresolvedvalueoncevalue](https://jestjs.io/docs/mock-function-api#mockfnmockresolvedvalueoncevalue), [jestjs.io/docs/jest-object#jestadvancetimersbytimeasyncmstorun](https://jestjs.io/docs/jest-object#jestadvancetimersbytimeasyncmstorun).

## Revision History

*(none yet — first plan write)*

## Recommended Next Stage

- **Option A (default):** `/wf implement cloud-function-bookmark-sync daily-poll` — execute the 24-step plan. **Run `/compact` first** to discard planning research from context (the PreCompact hook preserves workflow state).
- **Option B:** `/wf plan cloud-function-bookmark-sync android-reader` — plan the next slice before implementing. `android-reader` consumes the `triggerPoll` return shape and the `users/{uid}/sync_status` path defined here; planning it in parallel surfaces the deep-link `code_verifier` open question for resolution.
- **Option C:** `/wf plan cloud-function-bookmark-sync daily-poll <feedback>` — return to this plan with explicit corrections (directed-fix mode). E.g., flip back to a separate `poll_lease` doc, drop the conditional RT-rotation guard, or change `max_results`.
