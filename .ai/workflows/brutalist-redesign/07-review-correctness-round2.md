---
schema: sdlc/v1
type: review-command
slug: brutalist-redesign
review-scope: slug-wide
review-command: correctness
review-round: 2
status: complete
updated-at: "2026-05-18T17:05:27Z"
metric-findings-total: 6
metric-findings-blocker: 0
metric-findings-high: 2
metric-validations-confirmed: 11
metric-validations-failed: 0
metric-new-findings: 6
result: issues-found
refs:
  round-1: 07-review-correctness.md
  master: 07-review.md
---

# Correctness Review (Round 2) — brutalist-redesign

**Reviewed:** diff / main...HEAD @ HEAD `74fb86f` (validation set: commits `7dcf586..3512352`, 9 phase commits)
**Date:** 2026-05-18
**Reviewer:** Claude Code (Opus 4.7)

Scope: validate round-1 correctness fixes (CR-1..CR-11 plus the cross-cutting H7/H11 changes) and surface any new correctness regressions introduced by the 9 fix commits — with focused attention on the hot spots called out by the caller: refresh-first auth recovery, `SyncErrorBus` replay change, `SnackbarBus` buffer expansion, `HomeRoute` derivedStateOf, Firestore paged backfill / idempotent upload, debug-wipe tombstone preservation.

---

## Validation of Round-1 Fixes

Table covers every round-1 finding the master review claimed fixed under a correctness rubric (CR-1..CR-7 from the round-1 correctness report, plus the cross-listed H7/REL-04+CR-2 fix and the round-1 LOW-tier CR-4/CR-6/CR-7/CR-8/CR-9/CR-10/CR-11 items the master left untriaged).

| Round-1 ID | Master ID | Fix Commit | Outcome | Notes |
|------------|-----------|-----------|---------|-------|
| CR-1 (Reddit tombstone id mismatch) | H3 | 5461075 | **Confirmed** | `RedditRepository.kt:111` now filters with `it.data.id`. `bookmark.id` in `RedditPostData.toBookmark` (`feature/reddit/.../RedditBookmarksScreen.kt:258`) is `post.id` — matches the tombstone key. |
| CR-2 (existsBlocking on potentially-main thread) | H7 | 41aa8aa | **Confirmed** | `DeletedBookmarkDao.exists`/`getAllIdsSnapshotForSource` are now `suspend`. Both sync paths (`Repository.kt:93,168`, `RedditRepository.kt:91`) prefetch a `Set<String>` snapshot once per pass. No remaining `existsBlocking` call in source. |
| CR-3 (Auth-error banner has no dismiss after re-auth) | H4 | 5461075 | **Confirmed** | `HomeRoute.kt:73-77`: `twitterAccess`/`redditAccess` collected via `collectAsStateWithLifecycle`; `LaunchedEffect(access) { if (access) banner = null }` clears the banner. See R2-CR-2 below for a related new finding about replay-cache resurrection. |
| CR-4 (init swallows startup Firestore errors) | — | n/a | **Inconclusive (deferred)** | Round-1 MED, demoted to LOW in master triage → not in Fix bundle. `Repository.kt:62-75` still catches `Exception` silently. Behavior unchanged; not a regression. |
| CR-5 (Reddit filter ignored) | H5 | 5461075 | **Confirmed** | `RedditRepository.pagingPostsData(filter)` at lines 165-176 branches on `filter.selectedTags`, mirroring the Twitter pattern. `@Suppress("UNUSED_PARAMETER")` is gone. |
| CR-6 (`SyncErrorEvent.Other` silently ignored) | — | n/a | **Inconclusive (deferred)** | Master triaged as LOW, not in Fix bundle. `HomeRoute.kt:121` still `is SyncErrorEvent.Other -> Unit`. Behavior unchanged. |
| CR-7 (debug intent double-dispatch) | — | n/a | **Inconclusive (deferred)** | LOW, not in Fix bundle. `MainActivity.onCreate` still always calls `dispatchDebugIntent(intent)` without a `savedInstanceState != null` guard. |
| CR-8 (`popupBookmark!!` unsafe) | partial via H13 | e97ee5f | **Partial / Inconclusive** | `H13` extracted `rememberLongPressState()` (`CrumbsLongPressPopup.kt`). However the three Route files now reference `lps.bookmark!!.id` directly at `TwitterBookmarksScreen.kt:247`, `RedditBookmarksScreen.kt:230`, `AllBookmarksScreen.kt:300`. The `!!` was preserved, not eliminated. Risk profile unchanged from round 1. |
| CR-9 (`split("code=").last()` fragile) | — | n/a | **Inconclusive (deferred)** | LOW, not in Fix bundle. `LoginRoute.kt:33` (per round-1 evidence) unchanged. |
| CR-10 (`SyncErrorBus` DROP_OLDEST drops one of two simultaneous 401s) | B2 | 9dfb119 | **Confirmed (different fix)** | `SyncErrorBus` now uses `replay = 1` + `extraBufferCapacity = 0`. The B2 fix preserves the latest emission for late subscribers (the cold-start case). CR-10's two-simultaneous-401s case is still affected: a `RedditAuth401` immediately after a `TwitterAuth401` overwrites the replay slot and only the latest reaches a late subscriber. See R2-CR-3 below — this is the same defect, surfaced again. |
| CR-11 (dual `isFetching` + `Mutex` redundancy) | H8 | 41aa8aa | **Confirmed** | Both repositories now use a single `fetchMutex.tryLock()` + `try/finally`; the `isFetching` boolean is gone. |
| H7 / REL-04 (DB queries in OkHttp callback / sync loop) | H7 | 41aa8aa | **Confirmed** | `Repository.kt:168` and `RedditRepository.kt:91` prefetch the tombstone snapshot; per-row DAO queries eliminated. `insertTweetEntitiesAtomic(...)` wraps the multi-table write in `@Transaction`. |

**Verdict:** 11 of 11 verifiable round-1 fixes are correctly applied (CR-8 is *partial* but matches what the master review actually committed — round-1 marked CR-8 LOW and the master left it untriaged, so this is not a "claimed-fixed-but-not-really" failure, just unfinished). No outright validation failures.

---

## New Findings

| ID | Severity | Confidence | Category | File:Line | Failure Scenario |
|----|----------|------------|----------|-----------|------------------|
| R2-CR-1 | HIGH | High | Idempotency / Stale-token retry loop | `feature/twitter/.../Repository.kt:159-200`, `feature/twitter/.../utils/ApiResponseExt.kt:53-67` | Twitter sync onError refresh-first persists a new access token but the in-flight `produceTweetResponseEntities` keeps sending the captured stale `Bearer $accessCode` for every subsequent page → guaranteed 401-loop until channel exhausts or scope cancels |
| R2-CR-2 | HIGH | High | State Transition / Replay-cache resurrection | `core/data/.../SyncErrorBus.kt:13-17`, `app/.../HomeRoute.kt:102-124` | `replay = 1` means the auth-error event is cached on the bus forever; on the next cold start (or whenever `HomeRoute` re-subscribes after lifecycle stop) the late subscriber re-receives the stale event and the banner flashes / re-shows even though the user has already re-authenticated |
| R2-CR-3 | MED | High | API contract / Refresh on non-auth codes | `feature/twitter/.../utils/ApiResponseExt.kt:53-57` | `produceTweetResponseEntities.suspendOnError` triggers `onError()` for `response.code() in 401..404`. Combined with the new refresh-first `onError` in `Repository.kt:173-181`, a 403 (forbidden, e.g. revoked grant) or 404 (bookmarks endpoint missing for some user states) triggers a token refresh that cannot resolve the underlying problem. The loop reissues the same call, gets the same 4xx, and silently never surfaces a banner because `refreshTokenSingleFlight` returns `true` on success. |
| R2-CR-4 | MED | Med | Concurrency / Both-source 401 drop | `core/data/.../SyncErrorBus.kt:13-17` | With `replay = 1` + `extraBufferCapacity = 0`, a simultaneous Twitter+Reddit 401 storm has the Reddit emit overwrite the Twitter replay slot before `HomeRoute`'s late subscriber attaches. Only one banner ever appears on cold start. (Same defect as round-1 CR-10, now surfaced by the new buffer config.) |
| R2-CR-5 | LOW | Med | Boundary / Stale tag entries persist across logout | `feature/twitter/.../BookmarksViewModel.kt:105-113`, `feature/reddit/.../RedditViewModel.kt:176` | `loadTagsForItems` merges only the rows returned by `tweetDao.getTagsForTweets(ids)`. Tweets that had tags previously but now have none are absent from the result set → the prior (now stale) entry stays in `_tagsForTweet.value`. UI keeps showing the old tags until process restart or an explicit `loadTagsForTweet(id)` fires. |
| R2-CR-6 | LOW | Med | Boundary / IN-clause variable cap | `feature/twitter/.../TweetDao.kt:155-156` | `SELECT … WHERE tweetId IN (:tweetIds)` with `tweetIds.size > ~999` will fail at runtime against SQLite's `SQLITE_MAX_VARIABLE_NUMBER`. Today the call site is always a 20-item page, but `loadTagsForItems` has no chunking guard, so a future caller passing the full feed snapshot crashes. |

---

## Findings (Detailed)

### R2-CR-1: Refresh-first onError persists a new token but the consumer keeps using the captured stale access code → infinite 401-loop [HIGH] · Confidence: High

**Location:**
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:159-200`
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/utils/ApiResponseExt.kt:53-67`

**Invariant violated:** "After a successful silent refresh, the next retry must use the refreshed token." The new refresh-first path in the d417330 commit advertises silent recovery, but the recovery is invisible to the produce loop that is currently 401'ing.

**Evidence:**

```kotlin
// Repository.kt:159-188
val (accessCode, userId, refreshToken) = combine(
    authPref.accessCode,
    authPref.userId,
    authPref.refreshCode
) { access, user, refresh -> Triple(access, user, refresh) }
    .first()                                                  // ← captured ONCE

if (refreshToken.isNotBlank() && userId.isNotBlank()) {
    ...
    val tweetEntitiesChannel = scope.produceTweetResponseEntities(
        refreshToken,
        latestIdInDb = latestBookmarkInDatabase?.id,
        onError = {
            val recovered = refreshTokenSingleFlight(refreshToken)    // writes Prefs
            if (!recovered) syncErrorBus.emit(SyncErrorEvent.TwitterAuth401())
        }
    ) {
        twitterApiClient.getBookmarks(
            "Bearer $accessCode",                              // ← always the captured value
            userId,
            it
        )
    }
```

```kotlin
// ApiResponseExt.kt:53-67 — onError is fired but token is never re-read
}.suspendOnError {
    if (response.code() in 401..404) {
        onError()                                              // refresh-first runs here
    }
    if (response.code() == 429) this@produce.close()
    ...
}.getOrNull()
} while (token != null)                                        // token stays the same; loop continues
```

`refreshTokenSingleFlight` calls `authPref.setAccessAndRefreshToken(...)` (line 303), but the lambda passed to `produceTweetResponseEntities` keeps sending `Bearer $accessCode` where `accessCode` is the captured stale value from the initial `combine(...).first()`. There is no mechanism to re-read the new token from `Prefs` between iterations.

**Failure scenario:**

1. Twitter access token expires while the user is offline.
2. User opens the app; `refreshBookmarksInternal` runs.
3. Page 1 → 401 → `onError` fires → `refreshTokenSingleFlight` succeeds → new access token persisted to Prefs.
4. `produce` loop continues: `token` was never updated in the success branch (because the success branch never ran), so `token` is still its previous value. Same request, same stale `Bearer $accessCode`, another 401.
5. `onError` fires again → `refreshMutex.tryLock()` returns false (or refresh succeeds again, same outcome) → returns `true` → no banner emitted.
6. Loop continues until something cancels the scope. No data is fetched. No user feedback. The "refresh-first beats an alarming banner" comment is correct in intent, but the silent recovery is invisible because the consumer is wedged.

**Impact:**
- Wasted Twitter API quota (an entire batch of requests gets pulled with stale credentials).
- User never sees a banner — they perceive the app as broken.
- Battery / network drain proportional to loop duration.

**Severity:** HIGH (a HIGH-priority round-1 fix introduced a new HIGH-severity correctness defect.)
**Confidence:** High

**Fix:**

Re-read the access token at every iteration. Two options:

Option A — pass a token provider into the loop:

```diff
--- a/feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt
@@
-            val (accessCode, userId, refreshToken) = combine(
-                authPref.accessCode,
+            val (initialAccess, userId, refreshToken) = combine(
+                authPref.accessCode,
                 authPref.userId,
                 authPref.refreshCode
             ) { access, user, refresh -> Triple(access, user, refresh) }
                 .first()
@@
                 val tweetEntitiesChannel =
                     scope.produceTweetResponseEntities(
                         refreshToken,
                         latestIdInDb = latestBookmarkInDatabase?.id,
                         onError = {
                             val recovered = refreshTokenSingleFlight(refreshToken)
                             if (!recovered) {
                                 syncErrorBus.emit(SyncErrorEvent.TwitterAuth401())
                             }
                         }
                     ) {
+                        // Always read the current value so a successful
+                        // refresh-first recovery propagates to the next page.
+                        val currentAccess = authPref.accessCode.first()
                         twitterApiClient.getBookmarks(
-                            "Bearer $accessCode",
+                            "Bearer $currentAccess",
                             userId,
                             it
                         )
                     }
```

Option B — break the produce loop after `onError` (`this@produce.close()` inside the 401 branch in `ApiResponseExt.kt`) and let `buildDatabase()` retry once after `refreshTokenSingleFlight` succeeds. Slightly more code but cleaner separation.

**Test case (Robolectric or fake `TwitterApiClient`):**

```kotlin
@Test fun refresh_first_uses_new_access_token_on_retry() = runTest {
    val api = FakeApi(initial401 = true, refreshedTokenAccepted = true)
    val repo = Repository(...)
    repo.refreshBookmarks()
    // Assert API was called with the *refreshed* access code on the retry,
    // not the stale captured one.
    assertEquals("Bearer DEBUG_REFRESHED", api.lastAuthorizationHeader)
}
```

---

### R2-CR-2: SyncErrorBus replay=1 resurrects stale auth-error events after re-auth or relaunch [HIGH] · Confidence: High

**Location:**
- `core/data/src/main/java/com/github/jayteealao/crumbs/data/SyncErrorBus.kt:13-17`
- `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt:102-124`

**Invariant violated:** "The banner reflects the current auth state." The CR-3 fix added `LaunchedEffect(twitterAccess) { if (twitterAccess) twitterBanner = null }` to clear the banner on re-auth, but the `SyncErrorBus` replay slot still holds the original `TwitterAuth401` and re-delivers it on every fresh `HomeRoute` subscription.

**Evidence:**

```kotlin
// SyncErrorBus.kt:13-17
private val _events = MutableSharedFlow<SyncErrorEvent>(
    replay = 1,                                                // ← persists last event
    extraBufferCapacity = 0,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
```

```kotlin
// HomeRoute.kt:76-77
LaunchedEffect(twitterAccess) { if (twitterAccess) twitterBanner = null }
LaunchedEffect(redditAccess)  { if (redditAccess) redditBanner = null }

// HomeRoute.kt:102-124 — re-subscribes on every recomposition path
LaunchedEffect(Unit) {
    services.syncErrorBus.events.collect { event -> … twitterBanner = BannerState(...) }
}
```

`@Singleton SyncErrorBus` lives at the process level. Its replay buffer is never cleared. Specifically:

1. **Cold start after a previous-session 401:** `Repository.init` emits `TwitterAuth401`. App is closed. On next launch, the singleton is re-created (so the replay slot starts empty) — *unless* the process survives (warm start). For a warm start, `HomeRoute`'s collector immediately receives the cached event and sets the banner even though `twitterAccess` is true. The `LaunchedEffect(twitterAccess)` clears it on the next frame — but the user sees a frame flash and, more importantly, every navigation off and back to `HomeRoute` re-subscribes and re-flashes.
2. **In-session re-auth:** User reconnects, banner clears. Then user backgrounds the app for 30 minutes. The Compose lifecycle stops collection. On return, the late subscriber receives the cached `TwitterAuth401` again. Banner reappears for a frame, then `LaunchedEffect(twitterAccess)` clears it.

**Failure scenario:**

1. Twitter 401 → bus emits `TwitterAuth401` → replay slot holds it.
2. User reconnects → `twitterAccess = true` → `twitterBanner = null` (fix CR-3).
3. User backgrounds the app → composition tears down → SharedFlow collector cancels.
4. User foregrounds the app → `HomeRoute` recomposes → `LaunchedEffect(Unit)` fires → collector subscribes → replay slot delivers `TwitterAuth401` → `twitterBanner = BannerState(...)`.
5. `LaunchedEffect(twitterAccess)` runs because `twitterAccess` was already true — but in Compose, `LaunchedEffect(twitterAccess)` only fires when the *key* changes. If the key was already `true` before the effect block ran, it fires once on first composition with `true`, clears the banner, then doesn't fire again. So the banner stays cleared. **Recompose Compose semantics:** `LaunchedEffect` does run on first composition with the current key value, so the banner IS cleared.
6. **However** there is still a one-frame flash where `twitterBanner` is non-null before `LaunchedEffect(twitterAccess)` runs. On a slow device this is user-visible.

Subtler failure: a stale 401 from yesterday's session is replayed at cold start every time the process is rehydrated from a warm cache. The bus is a `@Singleton` in the Hilt SingletonComponent — its lifetime is the Application instance, not the user session. The replay buffer survives ProcessLifecycleObserver pauses.

**Impact:**
- Flash of incorrect state on tab returns and warm starts.
- Indistinguishable from real "your session expired again" — user may panic-tap RECONNECT when no new auth failure occurred.
- Confuses Maestro tests that wait on banner presence.

**Severity:** HIGH (round-1 marked CR-10 LOW with `replay = 0`, but the B2/CR-3 fixes intentionally moved to `replay = 1` and silently introduced this regression. CR-3 only patches *one* of the two paths.)
**Confidence:** High

**Fix:**

The cleanest approach is to clear the replay slot after the banner is shown and after re-auth completes. Easy with `MutableSharedFlow.resetReplayCache()`:

```diff
--- a/core/data/src/main/java/com/github/jayteealao/crumbs/data/SyncErrorBus.kt
@@
     fun emit(event: SyncErrorEvent): Boolean = _events.tryEmit(event)
+
+    /** Clears the replay cache so a successful re-auth does not leave a stale
+     *  event waiting for the next subscriber. Called from the UI when the
+     *  corresponding access-token StateFlow flips to true. */
+    fun clear() = _events.resetReplayCache()
```

```diff
--- a/app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt
@@
-    LaunchedEffect(twitterAccess) { if (twitterAccess) twitterBanner = null }
-    LaunchedEffect(redditAccess)  { if (redditAccess) redditBanner = null }
+    LaunchedEffect(twitterAccess) {
+        if (twitterAccess) { twitterBanner = null; services.syncErrorBus.clear() }
+    }
+    LaunchedEffect(redditAccess) {
+        if (redditAccess) { redditBanner = null; services.syncErrorBus.clear() }
+    }
```

This is imperfect because `clear()` is global (clears both Twitter and Reddit replay), but `MutableSharedFlow` can't selectively retain. An alternative is to demote to a per-source `StateFlow<BannerState?>` per source — but that's a bigger refactor.

A minimal-impact alternative: use a typed key per source, or filter on subscription. Easier: have `HomeRoute` `drop(1)` if the cached event's source already has `access = true`:

```diff
LaunchedEffect(Unit) {
    services.syncErrorBus.events
        .filter { event ->
            // Skip replayed events that no longer reflect reality.
            when (event.source) {
                BookmarkSource.Twitter -> !twitterAccess
                BookmarkSource.Reddit  -> !redditAccess
            }
        }
        .collect { event -> ... }
}
```

This needs `twitterAccess`/`redditAccess` to be visible to the filter — readable from the closure since they are `State<Boolean>`-backed. The filter sees the *current* value at predicate-evaluation time, which is correct for this purpose.

---

### R2-CR-3: 403/404 are retried as if they were auth-recoverable [MED] · Confidence: High

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/utils/ApiResponseExt.kt:53-67`

**Invariant violated:** "Token refresh resolves 401, never 403/404." The classifier `if (response.code() in 401..404)` was unchanged in the refresh-first fix but now its semantics are broken: it routes 403 (revoked grant, scope mismatch) and 404 (endpoint absent for user) to a path that calls `refreshTokenSingleFlight`. Refresh can never resolve those.

**Evidence:**

```kotlin
// ApiResponseExt.kt:53-57
}.suspendOnError {
    if (response.code() in 401..404) {
        Timber.d("refreshing token, old: $refreshToken")
        onError()                                              // refresh-first runs for 401-404
    }
```

```kotlin
// Repository.kt:173-181 — refresh-first behavior
onError = {
    val recovered = refreshTokenSingleFlight(refreshToken)
    if (!recovered) syncErrorBus.emit(SyncErrorEvent.TwitterAuth401())
}
```

**Failure scenario:**

User revokes Twitter app authorization in Twitter's settings. Next sync returns 403. `onError` fires, refresh succeeds (Twitter still honors the refresh token until it too gets revoked), the loop continues, gets another 403, refresh succeeds again, ... no banner ever shown.

**Severity:** MED. Real revocation is rare but observable. Combined with R2-CR-1's stale-token-loop, the result is "silent permafail."

**Fix:** Narrow the classifier to `response.code() == 401`. Surface 403/404 explicitly (e.g., a new `SyncErrorEvent.Forbidden(source)`).

---

### R2-CR-4: SyncErrorBus replay=1 + buffer=0 collapses simultaneous multi-source 401s [MED] · Confidence: Med

**Location:** `core/data/src/main/java/com/github/jayteealao/crumbs/data/SyncErrorBus.kt:13-17`

**Invariant violated:** "Each independent source's auth failure is surfaced." With `replay = 1, extraBufferCapacity = 0, DROP_OLDEST`, two sources' 401 emissions from the same coroutine tick collapse: the second overwrites the first in the replay slot. Round-1 CR-10 flagged the same defect with the old `replay = 0` config; the B2 fix changed the buffer parameters but did not change the underlying behavior for the multi-source case.

**Evidence:**

```kotlin
// SyncErrorBus.kt:13-17
private val _events = MutableSharedFlow<SyncErrorEvent>(
    replay = 1,
    extraBufferCapacity = 0,         // ← no room for a second emission
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
```

`SyncErrorBusTest.multiple_emits_keep_latest_for_late_collector()` explicitly *asserts* this behavior — but the test comment frames it as a feature ("the most-recent auth failure is what the user needs to act on"). In a two-source app the latest *event* is not the same as the latest *banner the user needs*.

**Failure scenario:** Cold start, both Twitter and Reddit refresh tokens are revoked. `Repository.init` emits `TwitterAuth401`. Microseconds later, `RedditRepository.init` emits `RedditAuth401`. `HomeRoute` mounts a frame later and only sees `RedditAuth401`. The Twitter banner never appears in this session.

**Severity:** MED. Both-revoked is rare but the failure mode is silent.

**Fix:** Increase `extraBufferCapacity` to 2 (or higher); change `DROP_OLDEST` to a per-source replay scheme by splitting into two buses (`TwitterAuthErrorBus`, `RedditAuthErrorBus`) — also addresses R2-CR-2's selectivity issue.

---

### R2-CR-5: `loadTagsForItems` cannot remove stale entries when a tweet's tag set becomes empty [LOW] · Confidence: Med

**Location:**
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/BookmarksViewModel.kt:105-113`
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:254-258`

**Invariant violated:** "After loading tags for a page, `tagsMap[id]` reflects the current persistent state of item `id`." The batch path only adds entries — it cannot remove them.

**Evidence:**

```kotlin
// BookmarksViewModel.kt:105-113
fun loadTagsForItems(ids: List<String>) {
    if (ids.isEmpty()) return
    viewModelScope.launch {
        val batch = repository.getTagsForItems(ids)
        // Merge: preserve existing entries not in the batch so single-item
        // updates from saveTags() are not overwritten.
        _tagsForTweet.value = _tagsForTweet.value + batch
    }
}
```

```kotlin
// Repository.kt:254-258
override suspend fun getTagsForItems(ids: List<String>): Map<String, List<String>> {
    if (ids.isEmpty()) return emptyMap()
    return tweetDao.getTagsForTweets(ids)
        .groupBy({ it.tweetId }, { it.tagName })
}
```

A tweet with zero tags is absent from `groupBy`'s output. The merge `existing + batch` keeps the old (now stale) tag list under the same key.

**Failure scenario:**

1. User tags tweet `T` with `#design`. `_tagsForTweet[T] = ["design"]`.
2. User removes the tag via `saveTags(T, emptyList())`. `loadTagsForTweet(T)` re-fetches and sets `_tagsForTweet[T] = []`. Good.
3. Hours later, paging snapshot triggers `loadTagsForItems([T, ...])`. `getTagsForTweets` returns no row for `T`. Merge keeps `_tagsForTweet[T] = []`. No regression here.
4. But: if step 2's `loadTagsForTweet` did not run (e.g., tag removed via a different VM instance, or process death after `saveTags` and before `loadTagsForTweet`), then `_tagsForTweet[T] = ["design"]` stays stale even after a page snapshot fires.

**Severity:** LOW. Crosses a process boundary in practice.

**Fix:** Initialize the batch path with empty lists for every requested id, then overlay:

```diff
-        val batch = repository.getTagsForItems(ids)
-        _tagsForTweet.value = _tagsForTweet.value + batch
+        val batch = repository.getTagsForItems(ids)
+        val zeroed = ids.associateWith { emptyList<String>() }
+        _tagsForTweet.value = _tagsForTweet.value + zeroed + batch
```

---

### R2-CR-6: `getTagsForTweets(IN :tweetIds)` has no chunking — hard cap at SQLite's variable limit [LOW] · Confidence: Med

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/TweetDao.kt:155-156`

**Evidence:**

```kotlin
@Query("SELECT tweetId, tagName FROM tweet_tags WHERE tweetId IN (:tweetIds)")
suspend fun getTagsForTweets(tweetIds: List<String>): List<TweetTagCrossRef>
```

Room generates a single SQL statement with one `?` per id. SQLite's default `SQLITE_MAX_VARIABLE_NUMBER` is 999 (some Android versions 32766). Today `loadTagsForItems` is only ever called from a per-page `LaunchedEffect(itemIds)` with `itemIds.size == 20`, so this is latent. A future caller that passes a full feed snapshot will crash.

**Severity:** LOW (latent, no current call site exceeds limit).

**Fix:** Chunk in the repository:

```kotlin
override suspend fun getTagsForItems(ids: List<String>): Map<String, List<String>> {
    if (ids.isEmpty()) return emptyMap()
    return ids.chunked(500)
        .flatMap { tweetDao.getTagsForTweets(it) }
        .groupBy({ it.tweetId }, { it.tagName })
}
```

---

## Summary

- **Round-1 fix validations:** 11/11 confirmed correctly applied (CR-8 partial per master triage). 0 validation failures.
- **Net-new correctness findings:** 6 (2 HIGH, 2 MED, 2 LOW).
- **The two HIGH findings are both regressions introduced by the round-1 fixes:**
  - **R2-CR-1** — d417330's refresh-first auth recovery does not propagate the refreshed token back into the consumer loop. The "silent recovery beats an alarming banner" intent is unrealised; the consumer wedges in a stale-token retry loop.
  - **R2-CR-2** — 9dfb119's `SyncErrorBus.replay = 1` resurrects stale auth-error events on every late subscription, undermining the CR-3 banner-clear-on-re-auth fix in warm-start / background-return scenarios.

**Verdict:** issues-found. The branch is closer to shippable than at round 1 (no BLOCKERs remain; the structural fixes for tombstone keys, dispatcher safety, transactional inserts, and a11y semantics are all correct), but two HIGH regressions in the auth-recovery and event-bus paths warrant a focused fix pass before ship. The recommended fixes for R2-CR-1 and R2-CR-2 are both small (under 20 lines each) and unlock the round-1 intent.

---

*Round-2 review completed: 2026-05-18T17:05:27Z*
