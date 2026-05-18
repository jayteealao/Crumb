---
schema: sdlc/v1
type: review-command
slug: brutalist-redesign
review-scope: slug-wide
slice-slug: ""
review-command: correctness
status: complete
updated-at: 2026-05-18
metric-findings-total: 11
metric-findings-blocker: 0
metric-findings-high: 3
metric-findings-med: 4
metric-findings-low: 3
metric-findings-nit: 1
result: REQUEST_CHANGES
refs:
  review-master: 07-review.md
---

# Correctness Review — brutalist-redesign (slug-wide)

**Reviewed:** diff / main...HEAD
**Date:** 2026-05-18
**Files:** 380 changed, +16821 −9318

---

## 0) Scope, Intent, and Invariants

**What was reviewed:**
- All Kotlin source changes across the branch (app, core/data, feature/twitter, feature/reddit, core/designsystem)
- Focus areas: Room migration 4→5, DeletedBookmark tombstone system, SyncErrorBus, DebugDataInjector, sort/filter logic, new screens layer

**Intended behavior:**
- Soft-delete writes a tombstone to `deleted_bookmarks`; the tombstone gates both the live paging query and the next incremental sync fetch from re-inserting the item
- `SyncErrorBus` delivers a one-shot auth-error event; `HomeRoute` materialises it as a sticky banner; tapping the CTA re-launches the OAuth flow
- `DeletedBookmarkRepository.events` delivers a one-shot `SnackbarEvent.UndoableDelete`; user has one `SnackbarDuration.Short` window to UNDO; after that the tombstone persists
- Filter chips in `HomeScreen` drive `FilterState.type`; the active ViewModel's paging source rebuilds on change
- Room migration 4→5 adds `deleted_bookmarks`; `MIGRATION_4_5` is the sole migration step needed

**Must-hold invariants:**
1. **Tombstone key matches paging-query exclusion key** — `deleted_bookmarks.bookmarkId` must hold the same identifier used in the tombstone-aware DAO queries
2. **`isDeleted` called only from IO thread** — `DeletedBookmarkDao.existsBlocking` is a synchronous (non-suspend) Room query; calling it from the main thread causes an exception at runtime; calling it from a coroutine already on `Dispatchers.IO` is safe
3. **`onNewIntent` intent not double-processed** — `MainActivity.onCreate` calls `dispatchDebugIntent(intent)` with the launch intent; if the activity is later re-delivered the same intent via `onNewIntent`, `dispatchDebugIntent` runs twice on the same intent
4. **SyncErrorBus banner not dismissible** — once `twitterBanner`/`redditBanner` is set in state, there is no mechanism to clear it after the user reconnects; the banner persists for the session
5. **TypeFilter.valueOf must not throw outside runCatching** — `onTypeChipToggled` correctly uses `runCatching`, but an unknown chip ID silently falls back to `ALL` without any logging

**Key constraints:**
- `SyncErrorBus` has `extraBufferCapacity = 1`, `DROP_OLDEST` — a second 401 while the first banner is still showing silently drops the new event (acceptable only if banner already visible)
- Room DAO non-suspend `existsBlocking` is documented as "for use on IO threads only"
- Reddit `RedditPostEntity.id` is the short id (e.g., `abc123`); `RedditPost.name` is the full name (e.g., `t3_abc123`)

---

## 1) Executive Summary

**Merge Recommendation:** REQUEST_CHANGES

**Rationale:**
Three HIGH issues should be fixed before shipping. The most critical is CR-1: the Reddit tombstone-filter uses `it.data.name` (the full name `t3_abc123`) at sync time, but `softDelete` stores `bookmark.id` (the short id `abc123`), so deleted Reddit posts are never actually suppressed from the next sync and will re-appear after restart. CR-2 is a potential ANR: `existsBlocking` (a synchronous Room query) is called from inside coroutine lambdas in both Twitter and Reddit repository sync paths; while both call sites run on `Dispatchers.IO` today, this is fragile and one of them (`syncFromFirestore`) is launched on an unspecified dispatcher. CR-3 is a sticky banner UX correctness bug: once shown, the auth-error banner cannot be dismissed, so re-authenticating still shows a broken-state banner.

**Critical Issues (BLOCKER/HIGH):**
1. **CR-1**: Reddit tombstone ID mismatch — soft-deleted Reddit posts re-appear after next sync
2. **CR-2**: `existsBlocking` on possibly-main thread in `syncFromFirestore` — potential ANR/crash
3. **CR-3**: Auth-error banner has no dismiss path after reconnect

**Overall Assessment:**
- Correctness: Concerning (one data-integrity invariant broken for Reddit)
- Error Handling: Adequate (all network errors are caught; one swallowed `Throwable` category in reflective bridge is acceptable debug noise)
- Edge Case Coverage: Incomplete (empty tag list, banner lifetime, tombstone key consistency)
- Invariant Safety: Vulnerable (Reddit tombstone key invariant broken)

---

## 2) Findings Table

| ID | Severity | Confidence | Category | File:Line | Failure Scenario |
|----|----------|------------|----------|-----------|------------------|
| CR-1 | HIGH | High | Idempotency / Tombstone key mismatch | `RedditRepository.kt:110`, `AllBookmarksScreen.kt:309` | Soft-delete stores short id; sync filters on full name → deleted Reddit post re-appears after next sync/restart |
| CR-2 | HIGH | Med | Concurrency / Blocking IO in coroutine | `DeletedBookmarkDao.kt:18`, `Repository.kt:95`, `RedditRepository.kt:110` | `existsBlocking` called from `syncFromFirestore` launched with unspecified dispatcher in `scope.launch` — may run on main thread on some configurations → ANR |
| CR-3 | HIGH | High | State Transition / Missing dismiss | `HomeRoute.kt:57-98`, `HomeRoute.kt:140-145` | After user successfully re-authenticates, `twitterBanner`/`redditBanner` is never cleared → banner stays permanently for session |
| CR-4 | MED | High | Error Handling / Swallowed error | `Repository.kt:70-72` | `catch (e: Exception)` in `init` block swallows all errors from `syncFromFirestore`, including network failures — no user feedback |
| CR-5 | MED | High | State Transition / Filter type ignored for Reddit | `RedditRepository.kt:160-163` | `pagingPostsData(filter)` ignores `filter.type` and `filter.selectedTags` entirely; type-chip toggles on Reddit tab have no effect |
| CR-6 | MED | Med | Boundary / `SyncErrorEvent.Other` silently ignored | `HomeRoute.kt:96` | `SyncErrorEvent.Other` is handled with `Unit` — no banner, no log, no user feedback for non-Twitter/Reddit auth errors |
| CR-7 | MED | Med | Idempotency / Double debug-intent dispatch | `MainActivity.kt:18,31` | `dispatchDebugIntent(intent)` called in both `onCreate` and `onNewIntent`; a `singleTop` re-launch (e.g., Maestro's `launchApp`) triggers both → seed runs twice |
| CR-8 | LOW | High | Boundary / Tag editor uses `popupBookmark!!` unsafely | `AllBookmarksScreen.kt:318-333` | `showTagEditor && popupBookmark != null` guard at line 318 passes, but `popupBookmark!!.id` at line 329 could NPE if `onDismiss` is called concurrently and sets `popupBookmark = null` between the check and the access |
| CR-9 | LOW | Med | Boundary / `authorizationCode.split("code=").last()` fragile | `LoginRoute.kt:33` | If `authorizationCode` doesn't contain `"code="` (e.g., Reddit callback format), `split(...).last()` returns the whole string; if it contains multiple `"code="` occurrences only the last fragment is used |
| CR-10 | LOW | Med | Determinism / `SyncErrorBus` DROP_OLDEST on rapid 401s | `SyncErrorBus.kt:13-16` | Two rapid 401 events (e.g., Twitter and Reddit simultaneously) — second event drops the first if the first collector hasn't resumed yet; one source's banner may never show |
| CR-11 | NIT | High | Unused `isFetching` flag redundancy | `Repository.kt:48-52`, `RedditRepository.kt:41-42` | Both repos have both a `Mutex` and an `isFetching` boolean; the mutex alone is sufficient (boolean adds complexity without safety guarantees given coroutine preemption) |

**Findings Summary:**
- BLOCKER: 0
- HIGH: 3
- MED: 4
- LOW: 3
- NIT: 1

---

## 3) Findings (Detailed)

### CR-1: Reddit Tombstone ID Mismatch → Deleted Posts Re-Appear After Sync [HIGH]

**Location:**
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:110`
- `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt:309`

**Invariant Violated:**
- "Tombstone key must match the ID used in the tombstone-aware DAO queries" — `deleted_bookmarks.bookmarkId` is populated by `softDelete(bookmark.id, ...)`, where `bookmark.id` = `RedditPostEntity.id` = short id (e.g., `abc123`). But the sync-time tombstone check uses `it.data.name` which is the full Reddit name (`t3_abc123`).

**Evidence:**
```kotlin
// RedditRepository.kt:108-114
entitiesToInsert = data.data.children
    .filter { it.kind == "t3" }
    .filter { !deletedBookmarkRepository.isDeleted(it.data.name) }  // ← "t3_abc123"
    .map { thing -> ... thing.data.toEntity(order) }

// AllBookmarksScreen.kt:306-310 (Reddit soft-delete path)
BookmarkSource.Reddit -> redditViewModel.softDelete(bookmark.id)  // ← "abc123"

// AllBookmarksScreen.kt:337-366 (toBookmark mapping)
id = post.id,  // ← short id "abc123"
```

**Failure Scenario:**
1. User soft-deletes Reddit post `abc123` → tombstone stored with `bookmarkId = "abc123"`
2. Next sync fetches `t3_abc123` from Reddit API
3. `isDeleted("t3_abc123")` → returns `false` (no tombstone for the full name)
4. Post is re-inserted into DB and reappears in the paging feed

**Impact:**
- Tombstone mechanism is entirely broken for Reddit
- Deleted Reddit posts always reappear after the next sync or app restart

**Severity:** HIGH
**Confidence:** High
**Category:** Idempotency / Tombstone key invariant

**Fix:**
```diff
--- a/feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt
+++ b/feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt
@@ -108,7 +108,7 @@
                             entitiesToInsert = data.data.children
                                 .filter { it.kind == "t3" }
-                                .filter { !deletedBookmarkRepository.isDeleted(it.data.name) }
+                                .filter { !deletedBookmarkRepository.isDeleted(it.data.id) }
                                 .map { thing ->
```

---

### CR-2: `existsBlocking` Called from Coroutine with Unspecified Dispatcher → Potential ANR [HIGH]

**Location:**
- `core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkDao.kt:18`
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:95` (`syncFromFirestore`)
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:110`

**Invariant Violated:**
- Room non-suspend queries must not be called on the main thread — Room enforces this at runtime with `IllegalStateException: Cannot access database on the main thread since it may potentially lock the UI for a long period of time.`

**Evidence:**
```kotlin
// DeletedBookmarkDao.kt:18 — NOT suspend, must run on IO
fun existsBlocking(id: String): Boolean

// Repository.kt:61-103 — syncFromFirestore launched without explicit dispatcher
scope.launch(Dispatchers.IO) {   // outer launch is IO ✓
    ...
    syncFromFirestore()           // <- calls isDeleted → existsBlocking ✓ (OK here)
}

// Repository.kt:182-189 — inner launch inside consumeEach
tweetEntitiesChannel.consumeEach {
    it.data.forEach {
        val order = orderStart
        scope.launch(Dispatchers.IO) {             // ← explicit IO ✓
            if (!deletedBookmarkRepository.isDeleted(it.tweetEntity.id)) { ... }
        }
    }
}
```

The Twitter path is safe because both call sites specify `Dispatchers.IO`. However, `DeletedBookmarkRepository.isDeleted` is a public API that calls `existsBlocking` — its name is misleading (`isDeleted` sounds suspend-safe), and future callers may invoke it without ensuring IO dispatcher.

For Reddit, `RedditRepository.buildDatabase()` calls `isDeleted` at line 110 within `scope.launch(Dispatchers.IO)` — also currently safe.

The actual risk is that `isDeleted` has no compile-time enforcement (no `@WorkerThread` annotation), and `Repository.syncFromFirestore()` is a `suspend fun` (callable from any context), making the contract fragile.

**Failure Scenario:**
- If `syncFromFirestore()` is ever called from a coroutine running on the main dispatcher (e.g., someone calls `repository.syncFromFirestore()` from a `LaunchedEffect` default scope), `existsBlocking` will throw on Android 9+ strict mode or crash in Room.

**Severity:** HIGH
**Confidence:** Med (current call sites are safe; risk is contract fragility)

**Fix:**
Convert `existsBlocking` to a proper suspend query:
```diff
--- a/core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkDao.kt
+++ b/core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkDao.kt
@@ -17,6 +17,6 @@
     @Query("SELECT EXISTS(SELECT 1 FROM deleted_bookmarks WHERE bookmarkId = :id)")
-    fun existsBlocking(id: String): Boolean
+    suspend fun exists(id: String): Boolean
```
Then update `DeletedBookmarkRepository.isDeleted` to `suspend fun isDeleted` and all call sites to `withContext(Dispatchers.IO)` or remain on the IO coroutine already in use.

---

### CR-3: Auth-Error Banner Has No Dismiss After Successful Reconnect [HIGH]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt:57-98, 140-145`

**Invariant Violated:**
- "Banner reflects current auth state" — banner should clear once the user successfully re-authenticates.

**Evidence:**
```kotlin
// HomeRoute.kt:77-99 — only SET, never CLEAR
LaunchedEffect(Unit) {
    services.syncErrorBus.events.collect { event ->
        when (event) {
            is SyncErrorEvent.TwitterAuth401 -> twitterBanner = BannerState(...)
            is SyncErrorEvent.RedditAuth401  -> redditBanner = BannerState(...)
            is SyncErrorEvent.Other          -> Unit
        }
    }
}
// twitterBanner and redditBanner are never set back to null
```

**Failure Scenario:**
1. Twitter 401 fires → `twitterBanner` set
2. User taps RECONNECT, completes OAuth, `loginViewModel.isAccessTokenAvailable` becomes true
3. Banner is still visible because `twitterBanner` was never cleared
4. Even after a successful sync, the error banner persists for the entire session

**Severity:** HIGH
**Confidence:** High
**Category:** State Transition / Missing clear

**Fix:**
```diff
 LaunchedEffect(Unit) {
     services.tombstoneRepository.events.collect { event -> ... }
 }

+// Clear the twitter banner when the access token becomes available
+val twitterAccess by loginViewModel.isAccessTokenAvailable.collectAsState()
+val redditAccess by redditViewModel.isAccessTokenAvailable.collectAsState()
+
+LaunchedEffect(twitterAccess) { if (twitterAccess) twitterBanner = null }
+LaunchedEffect(redditAccess)  { if (redditAccess) redditBanner = null }
```

---

### CR-4: `syncFromFirestore` Errors Swallowed in `init` Block [MED]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:60-74`

**Evidence:**
```kotlin
init {
    scope.launch(Dispatchers.IO) {
        try {
            latestBookmarkInDatabase = tweetDao.getLatestBookmark()
            syncFromFirestore()
        } catch (e: Exception) {
            Timber.e(e, "Error in Repository init")  // logged but not propagated
        }
    }
}
```

**Failure Scenario:**
- Network failure during Firestore sync on startup → exception caught, logged, user sees nothing; app continues with stale local data
- No observable state (`isRefreshing` stays false) tells the UI a startup sync failed

**Severity:** MED (data staleness, not corruption)
**Confidence:** High
**Fix:** Expose a `startupSyncError: StateFlow<Exception?>` and surface it in the UI, or at minimum emit a `SyncErrorBus` event.

---

### CR-5: Reddit `pagingPostsData(filter)` Ignores FilterState Entirely [MED]

**Location:** `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:160-163`

**Evidence:**
```kotlin
fun pagingPostsData(@Suppress("UNUSED_PARAMETER") filter: FilterState): Flow<PagingData<RedditPostData>> = Pager(
    config = PagingConfig(pageSize = 20),
    pagingSourceFactory = { redditDao.getPostsTombstoneAware() }  // ← always the same query
).flow
```

Compare with Twitter's `pagingTweetData(filter)` which branches on `filter.selectedTags`.

**Failure Scenario:**
- User taps a type chip while on the Reddit tab → `redditViewModel.filter` updates → `_filter.flatMapLatest { state -> redditRepository.pagingPostsData(state) }` re-subscribes → but `pagingPostsData` always returns all posts; the filter has no effect

**Severity:** MED (UX correctness — chip looks active but does nothing for Reddit)
**Confidence:** High
**Fix:** Either implement tag filtering in `RedditDao` (add a `getPostsByTagsTombstoneAware` query) or remove the Reddit type chips from the UI until filtering is supported.

---

### CR-6: `SyncErrorEvent.Other` Silently Ignored — No User Feedback [MED]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt:96`

**Evidence:**
```kotlin
is SyncErrorEvent.Other -> Unit  // no banner, no log, no snackbar
```

**Failure Scenario:**
- A new sync error type is added and emitted via `SyncErrorBus.emit(SyncErrorEvent.Other("twitter", "Rate limited"))` → user gets no feedback

**Severity:** MED
**Confidence:** Med
**Fix:** Log at minimum; preferably show a generic banner or snackbar for `SyncErrorEvent.Other`:
```kotlin
is SyncErrorEvent.Other -> Timber.w("SyncErrorEvent.Other: source=${event.source} msg=${event.message}")
```

---

### CR-7: Debug Intent Double-Dispatch on `singleTop` Re-launch [MED]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/MainActivity.kt:18,28-31`

**Evidence:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    ...
    dispatchDebugIntent(intent)   // ← dispatch #1 with onCreate intent
    ...
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)             // replaces stored intent
    dispatchDebugIntent(intent)   // ← dispatch #2 with new intent
}
```

**Failure Scenario:**
- Maestro's `launchApp` with `arguments:` sends an intent to the already-running activity via `onNewIntent` — but if `onCreate` was also called (cold start), `dispatchDebugIntent` fires once in `onCreate` with the initial empty intent (no `debug_action` extra, so it exits early via the `?: return` guard — actually OK).
- The actual risk: a debug test runner calls `adb shell am start ... --es debug_action seed` when the activity is already in foreground (singleTop). This hits `onNewIntent` only — no double dispatch. The pattern is safe unless `launchApp` creates a new task, in which case `onCreate` sees the intent AND `onNewIntent` is never called. In that scenario dispatch is correct (single). Net: the pattern is safe but fragile and misleading.

**Severity:** MED (debug-only, but could cause double seeding of test data if the activity launch mode changes)
**Confidence:** Med

**Fix:**
In `onCreate`, skip dispatching if `savedInstanceState != null` (re-creation) or check `intent.flags` for `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (savedInstanceState == null) dispatchDebugIntent(intent)  // cold starts only
    ...
}
```

---

### CR-8: `popupBookmark!!` Unsafe Access After Guard in `AllBookmarksRoute` [LOW]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt:318-329`

**Evidence:**
```kotlin
if (showTagEditor && popupBookmark != null) {
    val current = (tagsMap[popupBookmark!!.id] ?: emptyList()).toImmutableList()
    TagEditorDialog(
        ...
        onSave = { tags ->
            bookmarksViewModel.saveTags(popupBookmark!!.id, tags.toList())  // ← second !!
```

**Failure Scenario:**
- Compose recomposition: `popupBookmark` is set to `null` by the `onDismiss` lambda between the guard check and the `!!` dereference. In practice Compose reads state atomically within a single frame, so this is low risk — but the `!!` assertions add fragility.

**Severity:** LOW
**Confidence:** High
**Fix:** Capture into an immutable local:
```kotlin
val bookmark = popupBookmark ?: return
if (showTagEditor) {
    val current = (tagsMap[bookmark.id] ?: emptyList()).toImmutableList()
    TagEditorDialog(..., onSave = { tags -> bookmarksViewModel.saveTags(bookmark.id, tags.toList()) })
}
```

---

### CR-9: `authorizationCode.split("code=").last()` Fragile Parsing [LOW]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/LoginRoute.kt:33`

**Evidence:**
```kotlin
LaunchedEffect(authorizationCode) {
    if (authorizationCode != null) {
        loginViewModel.getAccessToken(authorizationCode.split("code=").last())
    }
}
```

**Failure Scenario:**
- `authorizationCode = "abc123"` (already a raw code, no `"code="` prefix) → `split` returns `["abc123"]`, `last()` = `"abc123"` ✓ OK
- `authorizationCode = "state=xyz&code=abc&scope=read"` → `split("code=")` = `["state=xyz&", "abc&scope=read"]`, `last()` = `"abc&scope=read"` ← wrong code with trailing parameters
- Actually the deep link in `Crumbs.kt` extracts `code` from `{code}` URI template, so by the time it reaches `LoginRoute` it should already be the raw code. The `split("code=").last()` appears to be defensive but is actually incorrect for any value containing `&` after the code.

**Severity:** LOW
**Confidence:** Med
**Fix:** Trust the NavArgs extraction which already isolates the code, and remove the split:
```kotlin
loginViewModel.getAccessToken(authorizationCode)
```
Or if the raw query string really is passed, use `Uri.parse("?$authorizationCode").getQueryParameter("code") ?: authorizationCode`.

---

### CR-10: `SyncErrorBus` DROP_OLDEST May Silently Drop One Auth Error [LOW]

**Location:** `core/data/src/main/java/com/github/jayteealao/crumbs/data/SyncErrorBus.kt:13-16`

**Evidence:**
```kotlin
private val _events = MutableSharedFlow<SyncErrorEvent>(
    replay = 0,
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
```

**Failure Scenario:**
- Twitter sync and Reddit sync both fail at the same time → two events emitted back-to-back
- If the collector has not yet resumed after the first event, the second event drops the first (DROP_OLDEST drops the undelivered item at index 0)
- One source's auth-error banner never appears

**Severity:** LOW (both errors would likely recur on next sync attempt)
**Confidence:** Med
**Fix:** Use `extraBufferCapacity = 2` to buffer both events, or use `replay = 1` + check on subscribe.

---

### CR-11: Dual `isFetching` + `Mutex` Redundancy [NIT]

**Location:**
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:48-52`
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:41-42`

**Evidence:**
```kotlin
private val fetchMutex = Mutex()
private var isFetching = false   // redundant — mutex already serializes entry
```

The `fetchMutex` guarantees only one coroutine enters the critical section. The `isFetching` boolean is checked and set inside `fetchMutex.withLock`, making it safe but unnecessary.

**Severity:** NIT
**Confidence:** High
**Fix:** Remove `isFetching`; replace the guard with `if (mutex.isLocked) return`.

---

## 4) Invariants Coverage Analysis

| Invariant | Enforcement | Gaps |
|-----------|-------------|------|
| Tombstone key = paging exclusion key (Twitter) | ✅ `tweet.id` used consistently | None |
| Tombstone key = paging exclusion key (Reddit) | ❌ Broken | CR-1: sync checks `name`, softDelete stores `id` |
| `existsBlocking` on IO thread only | ⚠️ Fragile | CR-2: no compile-time enforcement; suspend callers could call from main |
| Banner cleared after re-auth | ❌ Missing | CR-3: no clear mechanism |
| Filter state drives Reddit paging | ❌ Missing | CR-5: filter param silently ignored |
| Migration 4→5 creates `deleted_bookmarks` | ✅ Good | DDL matches Room schema export |
| `SyncErrorEvent.Other` reported to user | ❌ Missing | CR-6: silently swallowed |

---

## 5) Edge Cases Coverage

| Edge Case | Handled? | Evidence |
|-----------|----------|----------|
| Empty tombstone list → no exclusion | ✅ Yes | LEFT JOIN with NULL check |
| Undo before snackbar expires | ✅ Yes | `SnackbarResult.ActionPerformed` check |
| Undo after snackbar expires | ✅ Yes | Tombstone persists until explicit undo; paging filter holds |
| Both Twitter and Reddit 401 simultaneously | ⚠️ Partial | CR-10: second event may drop |
| Reddit post deleted then soft-deleted | ❌ No | CR-1: tombstone key mismatch |
| Auth-error banner after re-auth | ❌ No | CR-3: no dismiss |
| Unknown type chip id | ✅ Yes | `runCatching { TypeFilter.valueOf(...) }.getOrDefault(ALL)` |
| Empty filter tags list | ✅ Yes | `if (filter.selectedTags.isNotEmpty())` gate in Twitter repo |
| `AllBookmarksRoute` with both sources disconnected | ✅ Yes | `EmptyState` shown |

---

## 6) Error Handling Assessment

**Good patterns found:**
- `refreshBookmarksInternal` uses `finally` to always reset `isFetching` and `_isRefreshing`
- `runCatching` wraps debug injector calls in `DebugIntentHandler`
- `scope.launch` wraps Firestore upload in Twitter repo (fire-and-forget with no silent crash)
- Migration uses `CREATE TABLE IF NOT EXISTS` — idempotent

**Gaps:**
- `Repository.init` swallows startup sync errors (CR-4)
- `SyncErrorEvent.Other` not forwarded to user (CR-6)
- `existsBlocking` unsafely named (CR-2)

---

## 7) Concurrency & Race Conditions

- Both Twitter and Reddit repos have a `Mutex` protecting re-entrant `buildDatabase()` calls — correct
- `MutableStateFlow` updates in ViewModels are thread-safe
- `MutableSharedFlow` in `SyncErrorBus` and `DeletedBookmarkRepository` use `tryEmit` which is non-blocking and safe from any thread
- `_tagsForTweet.value = _tagsForTweet.value + (tweetId to tags)` in `BookmarksViewModel.loadTagsForTweet` is a read-modify-write on a `StateFlow` value; in practice this runs only on the viewModelScope (single coroutine context) but could produce stale reads if two `loadTagsForTweet` calls overlap — low risk

---

## 8) Test Coverage Gaps

**Must add:**
- [ ] Reddit tombstone ID consistency (soft-delete `id`, sync filters on `id`) — covers CR-1
- [ ] Banner cleared when `isAccessTokenAvailable` transitions to true — covers CR-3
- [ ] `existsBlocking` called from IO (contract test) — covers CR-2

**Should add:**
- [ ] `onTypeChipToggled` with unknown id falls back to ALL (already covered by implementation; add explicit test)
- [ ] Reddit `pagingPostsData(filter)` with non-empty filter — would expose CR-5

---

## 9) Recommendations

### Must Fix (HIGH)
1. **CR-1**: Fix Reddit tombstone key — use `it.data.id` instead of `it.data.name` in sync filter
2. **CR-2**: Add `@WorkerThread` annotation to `existsBlocking` or convert to `suspend fun exists`
3. **CR-3**: Add `LaunchedEffect(twitterAccess)` / `LaunchedEffect(redditAccess)` to clear banners on re-auth

### Should Fix (MED)
4. **CR-5**: Implement Reddit filter or remove chips from Reddit tab
5. **CR-4**: Surface startup sync errors via `SyncErrorBus` or a separate state
6. **CR-6**: Log or show `SyncErrorEvent.Other`
7. **CR-7**: Skip `dispatchDebugIntent` in `onCreate` if `savedInstanceState != null`

### Consider (LOW/NIT)
8. **CR-8**: Remove `!!` in tag editor — use local val capture
9. **CR-9**: Simplify auth code extraction
10. **CR-10**: Increase `extraBufferCapacity` to 2 in `SyncErrorBus`
11. **CR-11**: Remove redundant `isFetching` boolean

---

*Review completed: 2026-05-18*
