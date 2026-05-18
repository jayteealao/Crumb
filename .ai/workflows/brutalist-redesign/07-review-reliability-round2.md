---
review-command: reliability
review-round: 2
scope: slug-wide (git diff main...HEAD)
slug: brutalist-redesign
completed: 2026-05-18
verdict: APPROVE_WITH_COMMENTS
prior-round-ref: 07-review-reliability.md
---

# Reliability Review (Round 2) — brutalist-redesign

**Scope:** Re-verification of round-1 reliability fixes (B2, B3, H6, REL-05/06/07, CONC-6) plus drive-by checks against the new code paths introduced by `d417330` (refresh single-flight) and `0ff5431` (Coil ImageLoader).
**Reviewer:** Reliability Agent (round 2)
**Date:** 2026-05-18

---

## Summary

All four round-1 reliability blockers/highs are correctly remediated at the code level:

- **B2** — `SyncErrorBus` now uses `replay = 1`, `extraBufferCapacity = 0`, `DROP_OLDEST`. Cold-start emits before `HomeRoute` subscribes are correctly replayed to the late subscriber. The unit test `SyncErrorBusTest` exercises the replay-to-late-subscriber path explicitly.
- **B3** — `LoginViewModel.refreshToken()` replaces the `!!` with `?: false`. Latent NPE removed.
- **H6** — Banner CTA resolves the intent first, then wraps `startActivity(it)` in `try/catch(ActivityNotFoundException)` with a `"NO BROWSER FOUND"` snackbar fallback.
- **REL-05/06/07 + CONC-6** — Twitter and Reddit both gained `refreshTokenSingleFlight`, the refresh-first pattern is wired in, SnackbarBus buffer grew 1 → 16, and Twitter's path now actually persists the refreshed token via `Prefs.setAccessAndRefreshToken`.

However, the new code introduces three reliability concerns of its own, two MED and one LOW, all centered on the `refreshTokenSingleFlight` contract and one on the new Coil disk cache. Net verdict moves from REQUEST_CHANGES to **APPROVE_WITH_COMMENTS** — none of the new findings rise to BLOCKER or HIGH, but R2-REL-01 in particular should be tightened before the next refresh-storm scenario in production.

**Severity Breakdown:**
- BLOCKER: 0
- HIGH: 0
- MED: 2  (R2-REL-01 tryLock false-success; R2-REL-04 hasMore-on-non-401)
- LOW: 2  (R2-REL-02 banner-after-success race; R2-REL-05 Coil disk-cache directory creation)
- NIT: 1  (R2-REL-03 SnackbarEvent buffer collector still sequential)

**Merge Recommendation:** APPROVE_WITH_COMMENTS

---

## Round-1 Fix Validation

| ID | Round-1 severity | Round-1 commit | Round-2 status | File:Line | Notes |
|----|------------------|----------------|----------------|-----------|-------|
| B2 (REL-01) | BLOCKER | 9dfb119 | **Verified fixed** | `core/data/.../SyncErrorBus.kt:13-17` | `replay = 1`, `extraBufferCapacity = 0`, `DROP_OLDEST`. Cold-start emit pre-subscription correctly replays. |
| B3 (REL-02) | BLOCKER | 9dfb119 | **Verified fixed** | `feature/twitter/.../LoginViewModel.kt:46` | `!!` → `?: false`. Compiles cleanly; no other `!!` on this call site. |
| H6 (REL-03) | HIGH | 41aa8aa | **Verified fixed** | `app/.../HomeRoute.kt:164-183` | `try/catch(ActivityNotFoundException)` + snackbar fallback. `intent?.let { … }` correctly handles the `null` branch from `activeBanner?.source`. |
| REL-05 | MED | d417330 | **Verified fixed (with caveat R2-REL-01)** | `feature/twitter/.../Repository.kt:173-181, 293-316` | Refresh-first pattern in place; Twitter path persists tokens via `authPref.setAccessAndRefreshToken`. |
| REL-06 | MED | d417330 | **Verified fixed (with caveat R2-REL-04)** | `feature/reddit/.../RedditRepository.kt:122-140` | Single-flight refresh on 401, with inline comment pinning `hasMore = false` contract. |
| REL-07 | MED | d417330 | **Verified fixed (with caveat R2-REL-03)** | `core/data/.../SnackbarBus.kt:13-19` | Buffer grew 1 → 16 with `DROP_OLDEST` as overflow strategy. |
| CONC-6 | MED | d417330 | **Verified fixed** | `feature/twitter/.../Repository.kt:54, 293`; `feature/reddit/.../RedditRepository.kt:43, 219` | Per-repo `refreshMutex` collapses parallel 401 storms. |

---

## New Findings

### R2-REL-01: `refreshTokenSingleFlight` returns `true` on `tryLock()` busy without awaiting the in-flight refresh outcome [MED]

**Severity:** MED | **Confidence:** High

**Location:**
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:293-297`
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:219-223`

**Issue:**
Both implementations return `true` on `tryLock()` failure ("another refresh in flight, deferring"), and the caller treats `true` as "do not show the reconnect banner." This is unsound: the concurrent refresh holding the lock may itself fail. The current caller logic is:

```kotlin
val recovered = refreshTokenSingleFlight(refreshToken)
if (!recovered) {
    syncErrorBus.emit(SyncErrorEvent.TwitterAuth401())
}
```

If two paginations simultaneously hit a 401:
1. Pagination A acquires `refreshMutex`, calls `twitterAuthClient.refreshAccessToken`.
2. Pagination B's `tryLock()` fails → returns `true` → B suppresses the banner.
3. Pagination A's refresh fails (e.g. refresh token expired, network gone) → A returns `false` → A emits banner.

This is the happy case. The unsafe case:

1. Pagination A acquires `refreshMutex`, refresh fails → A emits banner.
2. Pagination B's `tryLock()` fails (A still holds the lock during its own failure-path Timber.e + cleanup) → B returns `true` → B suppresses its banner.
3. Net: banner is emitted exactly once. Good.

The actually-unsafe case:

1. Pagination A acquires `refreshMutex`, starts refresh.
2. Pagination B's `tryLock()` fails → returns `true` → B continues into its `consumeEach` loop using the **stale** access token from its own `combine(...).first()` snapshot taken at line 159-164 (Twitter) — the new token persisted by A is not re-read.
3. Pagination B's next API call 401s again → emits a fresh banner event (good) OR worse, infinite-loops if the API consistently returns 401 and `produceTweetResponseEntities` keeps calling `onError`.

Looking at `ApiResponseExt.kt:53-57`, `onError` fires on every 401 in the do-while pagination loop. Each 401 calls `refreshTokenSingleFlight`. After A completes, B's subsequent loop iteration's 401 → tryLock succeeds (A released) → B's own refresh succeeds → B's still-stale `accessCode` local in Repository.kt:159 is the variable used in the `Authorization: Bearer $accessCode` header on the next call. The new token is in DataStore, but the in-flight produce-channel coroutine never re-reads it.

**Evidence:**
```kotlin
// Repository.kt:293-297
private suspend fun refreshTokenSingleFlight(currentRefreshToken: String): Boolean {
    if (!refreshMutex.tryLock()) {
        Timber.d("refreshTokenSingleFlight: another refresh in flight, deferring")
        return true   // ← caller treats this as "recovered" without proof
    }
    ...
}
```

**Failure Scenario:**
1. Parallel Twitter + Firestore sync paths both hit 401.
2. First caller's refresh fails (e.g. refresh token revoked server-side).
3. Second caller's tryLock fails → returns `true` → banner suppressed for second caller's failure mode.
4. User sees one banner instead of two (cosmetic) — *but* second caller continues iterating with the stale token, the next 401 in its loop fires another `onError`, the cycle repeats.
5. In the worst case (token permanently revoked), the do-while loop will keep firing 401s until `token` becomes null, generating multiple `tryLock` → `return true` → no-op cycles and silently retrying with a dead token.

**Impact:**
- User impact: subtle; the banner does eventually appear (from the first caller). However the in-flight loop can spam refresh attempts and 401 calls.
- System impact: extra API calls during a refresh failure storm; potential rate-limit amplification (the very thing single-flight was meant to prevent).
- Recovery: works correctly on the next cold-fetch where `accessCode` is re-read at line 159-164.

**Fix:**
Have `refreshTokenSingleFlight` actually await the in-flight refresh's outcome rather than returning a speculative `true`. The standard pattern is a `Deferred<Boolean>` cached under the mutex:

```kotlin
@Volatile private var inFlight: Deferred<Boolean>? = null

private suspend fun refreshTokenSingleFlight(currentRefreshToken: String): Boolean {
    val existing = inFlight
    if (existing != null) return existing.await()
    return refreshMutex.withLock {
        val again = inFlight
        if (again != null) return@withLock again.await()
        val deferred = scope.async {
            try { doRefresh(currentRefreshToken) } finally { inFlight = null }
        }
        inFlight = deferred
        deferred.await()
    }
}
```

Or, simpler: have the caller re-read `accessCode` from `authPref` after `refreshTokenSingleFlight` returns true and pass the fresh token to the next API call. The current code uses a `val accessCode` captured once at the top of `refreshBookmarksInternal`, which means even the local-caller's own successful refresh doesn't propagate to the next iteration of `consumeEach`.

---

### R2-REL-02: Banner can emit even when the refresh would have succeeded if launched milliseconds later [LOW]

**Severity:** LOW | **Confidence:** Med

**Location:** `feature/twitter/.../Repository.kt:173-181` and the equivalent in `RedditRepository.kt:122-140`.

**Issue:**
The refresh-first flow only emits the banner if `refreshTokenSingleFlight` returns `false`. That return is `false` when:
- `tryLock()` succeeded **and** the underlying client returned a null/blank token, OR
- `tryLock()` succeeded **and** the client threw.

But `twitterAuthClient.refreshAccessToken(currentRefreshToken)` in `TwitterAuthClientImpl.kt:118-128` internally uses `scope.launch { … }.join()` and on a transient network exception swallows it via `.onException { Timber.d(message()) }` and returns `null`. Network blip → null → `false` → banner shown immediately. There's no retry/backoff before the banner is emitted, which makes the banner trigger on a single transient network failure during a refresh.

This is a regression in user-visible UX vs. the round-1 design intent: the rationale for "refresh-first" was to suppress the banner when the system can silently recover. A single transient refresh failure should arguably trigger a short retry-with-backoff before alerting the user.

**Fix:**
Either accept the single-shot semantics as documented or add a bounded retry (e.g. 3 attempts with 250/500/1000 ms exponential backoff) inside `refreshTokenSingleFlight` for network/exception failures, before returning `false`.

---

### R2-REL-03: SnackbarBus buffer expansion to 16 helps capacity but the collector is still sequential [NIT]

**Severity:** NIT | **Confidence:** High

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt:126-144`

**Issue:**
The buffer fix correctly addresses the round-1 concern about events being dropped between rapid soft-deletes. However, the collector at `HomeRoute.kt:127-143` processes events sequentially via `snackbarHostState.showSnackbar(...)` which suspends for the full `SnackbarDuration.Short` (~4s). Concretely:

- User long-presses item A → DELETE → SnackbarBus emits → collector starts showing snackbar (~4s blocking suspension).
- During those 4s the user long-presses items B, C, D, … → buffer fills up to 16 slots.
- All 16 will eventually be shown one-by-one over ~64 seconds. Each undo affordance refers to the corresponding item, but the user has long since moved on; tapping "UNDO" on a snackbar that appears 30s after the action is bad UX.

**Recommendation:**
This is intentional under the current design (per the inline comment) and is a pragmatic NIT. A follow-up could coalesce burst deletes into a single "5 items deleted, UNDO ALL" snackbar, but that is a UX redesign, not a reliability defect.

---

### R2-REL-04: Reddit `hasMore=false` exit on error works for 401 but the comment is misleading for transient non-401 [MED]

**Severity:** MED | **Confidence:** High

**Location:** `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:103, 122-140`

**Issue:**
The round-1 concern was: "does `hasMore=false` actually break the do-while loop on transient network errors, or only on 401?" Re-reading the code:

```kotlin
hasMore = false   // line 103 — set BEFORE the response.suspendOnSuccess branch

response.suspendOnSuccess {
    ...
    hasMore = after != null   // only set true on success
}.suspendOnError {
    ...
    // No hasMore mutation here
}
```

The contract works **because** `hasMore` is preset to `false` per iteration before the branches run, and only `suspendOnSuccess` flips it true. The inline comment at line 136-140 says:

> // Any error (401 or otherwise) breaks the pagination loop
> // — hasMore is already false here, but keeping the comment
> // pins the contract so future edits do not reintroduce a
> // runaway fetch on transient network errors.

This is correct, but it is fragile. If a future maintainer adds `.suspendOnException { hasMore = after != null }` or moves the `hasMore = false` initializer above the `do {`, the loop becomes infinite on a stream of transient errors. The comment is a contract, not an enforcement.

Additionally, the loop exits on the first transient error — meaning a single 503 from Reddit in the middle of pagination throws away all subsequent pages until the next `buildDatabase()` call. This is acceptable graceful degradation, but it means a flaky Reddit endpoint can leave the local DB perpetually behind. No retry/backoff is attempted at the page-fetch level.

**Fix:**
- Make the contract structural: replace the comment with a `break` statement inside the `.suspendOnError {}` block. Then `hasMore`'s default value matters less, and the intent is enforced at compile time:

  ```kotlin
  }.suspendOnError {
      Timber.e("Error fetching Reddit posts: ${message()}")
      if (statusCode.code == 401) {
          val recovered = refreshToken.isNotBlank() &&
              refreshTokenSingleFlight(refreshToken)
          if (!recovered) {
              syncErrorBus.emit(SyncErrorEvent.RedditAuth401())
          }
      }
      return@launch   // structural exit, not a soft-fall-through
  }
  ```

  (Sandwich's `suspendOnError` is a lambda over the `ApiResponse.Failure.Error` receiver; verify that `return@launch` from inside it is reachable — if not, set a `shouldBreak` flag and `break` outside the response chain.)

- Optionally add a single transient retry on 5xx with a short backoff before exiting.

---

### R2-REL-05: New Coil `DiskCache.Builder().directory(cacheDir.resolve("image_cache"))` does not handle storage-full at directory creation [LOW]

**Severity:** LOW | **Confidence:** Med

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/CrumbApplication.kt:22-39`

**Issue:**
The new ImageLoader configures a disk cache via `cacheDir.resolve("image_cache")`. Coil's `DiskCache` will create the directory lazily on first write. If the device is storage-full at the moment the first image write occurs, Coil throws/swallows internally (depending on version) and falls back to memory-only — Coil 2.x catches `IOException` during disk init and degrades to a memory-only loader, so the app does not crash. This is acceptable.

However, two subtleties:
1. The `crossfade(true)` then immediately `crossfade(180)` on lines 23-24 is redundant — the second call replaces the first. The first line is dead code. Not a defect, just a small clarity nit.
2. `respectCacheHeaders(false)` means Coil will serve stale images indefinitely even when the server says `no-cache`. For a brutalist feed of social-media thumbnails this is fine, but if profile avatars rotate this can show stale images for the full disk-cache lifetime.

**Fix:**
- Remove the redundant `crossfade(true)`.
- Verify that the chosen `respectCacheHeaders(false)` is intentional for Twitter/Reddit media URLs (it likely is — those URLs are content-addressed) and document it inline.
- Optional: wrap the `DiskCache.Builder().build()` in a `runCatching { ... }.getOrNull()` so that if Coil ever changes its tolerance, the ImageLoader still constructs.

---

## Dependency Analysis (unchanged from round 1)

External dependencies, single-points-of-failure, and timeout posture are unchanged on this branch. Refresh single-flight does not introduce new external dependencies; it adds an in-process mutex per repository.

---

## Error Handling Coverage (updated)

| Path | Has try/catch | Has timeout | Has fallback | Risk |
|------|--------------|-------------|--------------|------|
| Twitter sync (refreshBookmarksInternal) | OK (outer try/finally) + refresh-first | No | Refresh-then-banner | LOW |
| Reddit sync (buildDatabase) | OK (outer try/finally) + refresh-first | No | Refresh-then-banner | LOW |
| Banner CTA startActivity | OK (try/catch ActivityNotFoundException + snackbar) | n/a | OK | NONE |
| Token refresh (Twitter) | OK (try/catch in refreshTokenSingleFlight) | No | Banner via refresh-first | LOW (R2-REL-01) |
| Token refresh (Reddit) | OK (try/catch in refreshTokenSingleFlight) | No | Banner via refresh-first | LOW (R2-REL-01) |
| LoginViewModel.refreshToken | OK (?: false) | n/a | n/a | NONE |
| Coil image loading | OK (Coil internal fallback to memory-only) | n/a | OK | NIT |

---

## Recommendations

### Round-2 — Short-term (MED)

1. **R2-REL-01** — Replace `refreshTokenSingleFlight`'s `tryLock() → return true` with either a `Deferred`-cached in-flight refresh or have callers re-read `authPref.accessCode` after a successful refresh signal. Today's behavior is "trust me" on the busy-lock path; that contract leaks once parallel paginations are running.

2. **R2-REL-04** — Make the Reddit pagination-loop exit on error structural (`break`/`return@launch`) rather than relying on the `hasMore = false` precondition. Comment-as-contract is fragile.

### Round-2 — Long-term (LOW / NIT)

3. **R2-REL-02** — Consider a bounded retry-with-backoff inside `refreshTokenSingleFlight` before raising the banner, to absorb transient refresh failures.

4. **R2-REL-03** — Eventually coalesce burst soft-deletes into a single "N items deleted, UNDO ALL" snackbar instead of serializing 16.

5. **R2-REL-05** — Drop the redundant `crossfade(true)` overload and document `respectCacheHeaders(false)` intent in the ImageLoader builder.

---

## Round-2 Verdict

**APPROVE_WITH_COMMENTS.** All round-1 blockers and highs are verifiably fixed. The two new MED items (R2-REL-01, R2-REL-04) are post-merge follow-ups rather than gate-blockers — they involve hardening of correct-but-fragile contracts, not user-facing regressions in nominal flows. No new BLOCKER or HIGH findings emerged on the re-verification pass.
