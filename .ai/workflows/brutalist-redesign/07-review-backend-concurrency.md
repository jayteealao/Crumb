# Backend Concurrency Review — brutalist-redesign

**Verdict:** Ship with caveats
**Reviewed:** diff / git diff main...HEAD
**Date:** 2026-05-18
**Scope:** All changed Kotlin files in the diff (~380 files, 16 821 insertions)

---

## 0) Scope & Concurrency Model

**Concurrency model:**
- Runtime: Kotlin coroutines on Android (structured concurrency, single-UI-thread Compose)
- Database: Room (SQLite WAL mode)
- Transaction isolation: SQLite default (SERIALIZABLE for writes, but no explicit Room `@Transaction` on the multi-entity insert)
- Locking strategy: Two application-level `Mutex` objects (one per repository), no DB-level `FOR UPDATE`
- State sharing: `MutableStateFlow` (filter, isRefreshing, token availability), `MutableSharedFlow` (SyncErrorBus, snackbar events)

**Critical operations identified:**
- Soft-delete tombstone write + Paging3 filter-on-read
- Token refresh vs concurrent API calls (both Twitter and Reddit)
- Multi-entity insert (`insertTweetEntities`) — not wrapped in a Room `@Transaction`
- `isDeleted(id)` called on `Dispatchers.IO` from coroutine bodies — a blocking DAO call without the `suspend` keyword
- `CoroutineModule` provides a bare `CoroutineScope(Dispatchers.IO)` without a `Job` — leaks survive process death

---

## 1) Executive Summary

**Concurrency Safety:** RACES_DETECTED

The diff introduces two repositories (`Repository`, `RedditRepository`) guarded by `Mutex` objects, which correctly prevent duplicate fetch calls. However, the isFetching flag is read and reset inside separate `withLock` blocks (split-lock pattern), the injected `CoroutineScope` has no supervision and no cancellation handle, the `isDeleted` gate is a blocking DB call invoked from a coroutine without `Dispatchers.IO` guarantee at the call site, `insertTweetEntities` is not a Room `@Transaction`, and `collectAsState` is used in lieu of lifecycle-aware variants throughout the UI layer. None of these individually cause financial data corruption (this is a bookmarks app), but several can cause visible coherence bugs: phantom re-appearance of deleted items, duplicate API calls after the mutex bug, and silent coroutine leaks.

---

## 2) Findings Table

| ID | Severity | Confidence | Pattern | Location | Race / Bug |
|----|----------|------------|---------|----------|------------|
| CONC-1 | HIGH | High | Split-lock check-then-act | `Repository.kt:140-147`, `RedditRepository.kt:66-72` | `isFetching` flag read outside lock; second coroutine can slip through |
| CONC-2 | HIGH | High | Non-atomic multi-insert | `TweetDao.kt:56-67` | `insertTweetEntities` is not `@Transaction`; partial inserts visible to Paging3 |
| CONC-3 | HIGH | High | Blocking call in coroutine | `DeletedBookmarkRepository.kt:33`, called from `Repository.kt:95,186`, `RedditRepository.kt:110` | `existsBlocking` / `isDeleted` is a synchronous DAO — crashes or ANR if called on Main dispatcher |
| CONC-4 | HIGH | High | Unscoped coroutine scope (leaked) | `CoroutineModule.kt:14-15` | `CoroutineScope(Dispatchers.IO)` — no `SupervisorJob`, no lifecycle; launched coroutines never cancel |
| CONC-5 | MED | High | Tombstone/Paging race | `DeletedBookmarkRepository.kt:24-27`, `TweetDao.kt:73-94` | `softDelete` writes tombstone then emits snackbar; Paging3 re-queries before tombstone is committed — item can flicker back |
| CONC-6 | MED | High | Token refresh not gated | `Repository.kt:170-178`, `RedditRepository.kt:125-131` | 401 triggers `syncErrorBus.emit` AND `refreshAccessToken` in parallel; if two pages return 401, two refresh calls fire |
| CONC-7 | MED | Med | `collectAsState` without lifecycle | All Route composables | `collectAsState` collects even when the app is in the background; should be `collectAsStateWithLifecycle` |
| CONC-8 | MED | High | `DebugDataInjector` wipe vs active collectors | `DebugIntentHandler.kt:35-38`, `DebugDataInjector.kt:31-38` | `db.clearAllTables()` is called from `lifecycleScope.launch` while Room `InvalidationTracker` is actively emitting to Paging3 — can cause `CancellationException` inside PagingSource |
| CONC-9 | MED | Med | `orderStart` mutation from child `launch` | `Repository.kt:181-192` | `orderStart--` runs on the _parent_ dispatcher while child `launch(Dispatchers.IO)` blocks run concurrently; order values passed to children may be stale |
| CONC-10 | LOW | Med | `_tagsForTweet` read-modify-write | `BookmarksViewModel.kt:101` | `_tagsForTweet.value = _tagsForTweet.value + (tweetId to tags)` — not atomic; two simultaneous `loadTagsForTweet` calls can clobber each other |
| CONC-11 | LOW | Med | Snackbar single-flight | `HomeRoute.kt:101-119` | `SnackbarHostState.showSnackbar` queues; rapid back-to-back deletes queue multiple snackbars; the second one can "undo" the first item after it has already auto-committed |
| CONC-12 | NIT | High | `isFetching` is not `@Volatile` | `Repository.kt:52`, `RedditRepository.kt:42` | Accessed from multiple coroutines; without `@Volatile` or atomic wrapper, stale cache reads are theoretically possible on multi-core |

**Summary:** HIGH: 4 | MED: 5 | LOW: 2 | NIT: 1

---

## 3) Findings (Detailed)

---

### CONC-1 — Split-lock `isFetching` check [HIGH]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:140-147`
and `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:66-72`

**Pattern:** Check-Then-Act (TOCTOU inside separate `withLock` calls)

**Vulnerable code (Twitter Repository):**
```kotlin
private suspend fun refreshBookmarksInternal() {
    fetchMutex.withLock {       // Lock 1 — check + set
        if (isFetching) {
            return
        }
        isFetching = true
        _isRefreshing.value = true
    }
    // ... long fetch work ...
    finally {
        fetchMutex.withLock {   // Lock 2 — reset
            isFetching = false
            _isRefreshing.value = false
        }
    }
}
```

**Race scenario:**

```
Coroutine A            | Coroutine B (buildDatabase + refresh called together)
-----------------------|----------------------------------------------
acquires Lock 1        |
isFetching = false → sets true, releases |
                       | acquires Lock 1
                       | isFetching = true → returns early (correct)
A finishes fetch       |
acquires Lock 2        |
isFetching = false     |
releases Lock 2        |
                       | (B already returned — no problem here)
```

The current split is actually race-safe for the _skip_ case. The real problem is that `isFetching = false` in the `finally` block is not set inside `withLock` for Reddit's `buildDatabase` path (the Reddit version sets flag inside `fetchMutex.withLock` at line 66 but resets it at line 145 inside another `withLock` call after the entire `try` block). If the `finally` coroutine is cancelled between the two `withLock` calls, `isFetching` stays `true` forever and all subsequent refreshes are silently skipped.

**Fix:** Hold the mutex for the duration of the flag lifecycle using a single pattern:
```kotlin
if (!fetchMutex.tryLock()) return  // Already running
try {
    _isRefreshing.value = true
    // ... fetch ...
} finally {
    _isRefreshing.value = false
    fetchMutex.unlock()
}
```

---

### CONC-2 — Non-atomic multi-entity insert [HIGH]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/TweetDao.kt:56-67`

**Pattern:** Non-atomic multi-step write

**Vulnerable code:**
```kotlin
@Insert(onConflict = OnConflictStrategy.IGNORE)
fun insertTweetEntities(
    tweet: TweetEntity,
    tweetsReferenced: List<TweetEntity>,
    twitterUserEntity: List<TwitterUserEntity>,
    tweetPublicMetrics: TweetPublicMetrics,
    // ... 6 more parameters
)
```

Room generates one `INSERT` per parameter. Without `@Transaction`, Room does **not** wrap these in a single SQLite transaction. Paging3's `InvalidationTracker` fires after each individual insert. This means a `TweetData` join query (`getTweetsTombstoneAware`) can run between the `TweetEntity` insert and the `TweetPublicMetrics` insert, returning a partially hydrated row to the UI.

**Fix:**
```kotlin
@Transaction
@Insert(onConflict = OnConflictStrategy.IGNORE)
fun insertTweetEntities(...)
```

---

### CONC-3 — Blocking DAO call inside coroutine [HIGH]

**Location:** `core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkDao.kt:19`
Called at: `Repository.kt:95`, `Repository.kt:186`, `RedditRepository.kt:110`

**Pattern:** Blocking I/O in potentially wrong coroutine context

**Vulnerable code:**
```kotlin
// DeletedBookmarkDao.kt:19
fun existsBlocking(id: String): Boolean  // NOT suspend

// DeletedBookmarkRepository.kt:33
fun isDeleted(id: String): Boolean = dao.existsBlocking(id)

// Repository.kt:186 — called inside scope.launch(Dispatchers.IO)
if (!deletedBookmarkRepository.isDeleted(it.tweetEntity.id)) { ... }

// RedditRepository.kt:110 — called inside onSuccess {} callback
.filter { !deletedBookmarkRepository.isDeleted(it.data.name) }
```

The `onSuccess` lambda in `RedditRepository` runs on whichever dispatcher Retrofit delivers the response on (typically an OkHttp thread, not the Room-safe `Dispatchers.IO`). Room `@Query` executed synchronously on a non-IO thread causes a `java.lang.IllegalStateException: Cannot access database on the main thread` if Room's `allowMainThreadQueries()` is not set (and it is not set here). In practice this is called from `Dispatchers.IO` launch context in both repositories, so it does not currently crash — but it is fragile and will break if the call site ever moves.

**Fix:** Make `existsBlocking` a `suspend` function:
```kotlin
@Query("SELECT EXISTS(SELECT 1 FROM deleted_bookmarks WHERE bookmarkId = :id)")
suspend fun exists(id: String): Boolean
```
And update `DeletedBookmarkRepository.isDeleted` to `suspend fun isDeleted`.

---

### CONC-4 — Unscoped application-level `CoroutineScope` [HIGH]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/di/CoroutineModule.kt:14-15`

**Pattern:** Uncancellable coroutine scope (effectively GlobalScope)

**Vulnerable code:**
```kotlin
@Provides
fun providesCoroutineScope(): CoroutineScope {
    return CoroutineScope(Dispatchers.IO)   // No Job, no SupervisorJob
}
```

- **No `Job` / `SupervisorJob`**: An unhandled exception in any child coroutine cancels the entire scope. Because `Repository.init` and `buildDatabase()` launch into this scope, a single transient network exception can permanently kill all future syncs for the process lifetime.
- **No lifecycle**: This scope is `@Singleton` but has no `onDestroy` equivalent. All coroutines launched into it live until the process dies — they cannot be cancelled for testing, background-restriction compliance, or graceful shutdown.
- **Not `@Singleton`**: The `@Provides` annotation lacks `@Singleton`, meaning a new `CoroutineScope` is injected on every injection site, so `Repository` and `RedditRepository` get _different_ scopes.

**Fix:**
```kotlin
@Singleton
@Provides
fun providesCoroutineScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.IO)
```
Long-term: migrate to `@ApplicationScope` pattern from the Hilt coroutines guide.

---

### CONC-5 — Tombstone write / Paging3 re-query race [MED]

**Location:** `core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkRepository.kt:24-27`

**Pattern:** Write-then-read with InvalidationTracker window

**Vulnerable code:**
```kotlin
suspend fun softDelete(id: String, source: String) {
    dao.insert(DeletedBookmark(id, source, System.currentTimeMillis()))  // T1
    _events.tryEmit(SnackbarEvent.UndoableDelete(id, source))            // T2
}
```

Room's `InvalidationTracker` observes writes to the `deleted_bookmarks` table and invalidates `PagingSource` objects registered against the tables they join. The `getTweetsTombstoneAware` query joins `tweetEntity LEFT JOIN deleted_bookmarks`. The invaliation fires immediately at T1. Paging3 then issues a fresh `load()` — but because Room WAL allows concurrent readers, the new `PagingSource.load()` can race with the tombstone commit and read the row before it is visible. Result: the item appears to flicker back into the list for one frame, then disappears on the next invalidation cycle.

**Mitigation:** There is no simple fix at the DAO level without moving to a `@Transaction` on the soft-delete + immediate verify pattern, or using a `Flow<List<String>>` of deleted IDs that the PagingSource filters in-memory rather than via SQL join. The SQL JOIN approach is correct directionally; the flicker is a consequence of WAL isolation windows.

---

### CONC-6 — Token refresh not single-flighted [MED]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:170-178`
and `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:122-132`

**Pattern:** Multiple error responses → multiple refresh calls

**Vulnerable code (Twitter, ApiResponseExt.kt:53-57):**
```kotlin
}.suspendOnError {
    if (response.code() in 401..404) {
        onError()  // calls twitterAuthClient.refreshAccessToken(refreshToken)
    }
    if (response.code() == 429) { this@produce.close() }
}
```

`produceTweetResponseEntities` is a `produce` coroutine that paginates. Each page calls `onError()` on 401. If three pages all return 401 before the first refresh completes, `refreshAccessToken` is called three times concurrently. For Reddit, `scope.launch { redditAuthClient.refreshAccessToken(refreshToken) }` is fired from inside `onError` — an unreferenced fire-and-forget job with no deduplication.

Twitter's refresh is called via `twitterAuthClient.refreshAccessToken(refreshToken)` which internally does `scope.launch { ... }.join()` — this pattern is doubly problematic: it launches a new coroutine from inside a `produce` channel then suspends waiting for it, but the outer `fetchMutex` is already released by this point, meaning a second `refreshBookmarksInternal` call can overlap.

**Fix:** Gate token refresh behind its own `Mutex` or use `@Volatile` flag:
```kotlin
private val refreshMutex = Mutex()

private suspend fun refreshTokenOnce(refreshToken: String) {
    if (refreshMutex.isLocked) return
    refreshMutex.withLock {
        twitterAuthClient.refreshAccessToken(refreshToken)
    }
}
```

---

### CONC-7 — `collectAsState` without lifecycle awareness [MED]

**Location:** Multiple Route composables:
- `AllBookmarksScreen.kt:223-228`
- `HomeRoute.kt:62-63`
- `TwitterBookmarksScreen.kt:171-174`
- `RedditBookmarksScreen.kt:163-165`

**Pattern:** Collection continues during background/stopped lifecycle

**Vulnerable code (representative):**
```kotlin
val twitterLoggedIn by loginViewModel.isAccessTokenAvailable.collectAsState()
```

`collectAsState` does not honour Android lifecycle. When the app moves to background (e.g., during OAuth redirect to browser), these collectors remain active. For `StateFlow` this is harmless (no data lost, just wastes one active subscriber), but it means that any side-effectful downstream code (e.g., UI recomposition triggering `loadTagsForTweet` via `LaunchedEffect`) continues to fire. The correct replacement is `collectAsStateWithLifecycle` from `androidx.lifecycle:lifecycle-runtime-compose`.

**Fix:**
```kotlin
val twitterLoggedIn by loginViewModel.isAccessTokenAvailable
    .collectAsStateWithLifecycle()
```

---

### CONC-8 — `DebugDataInjector.run(wipe=true)` vs active Paging3 collectors [MED]

**Location:** `app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugIntentHandler.kt:35-38`
and `app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugDataInjector.kt:31-38`

**Pattern:** DB wipe concurrent with active collection

**Vulnerable code:**
```kotlin
// DebugIntentHandler.kt:35-38
activity.lifecycleScope.launch {
    runCatching { injector.run(wipe = wipe) }
}

// DebugDataInjector.kt:33
db.clearAllTables()  // Wipes ALL tables while Room InvalidationTracker is active
```

`db.clearAllTables()` is a Room method that issues `DELETE FROM` on every table inside a single transaction. During this, Room fires `InvalidationTracker` callbacks for all observed tables. Any active `PagingSource` (bound to `HomeRoute`'s composables via `collectAsLazyPagingItems`) will receive `invalidate()` and attempt a new `load()`. The load will see an empty database. This is the intended debug behavior, but the race window between the `clearAllTables` transaction and the subsequent `seedTwitter()` call (which is not transactional) means the Paging3 source may load during the empty window and display a blank list permanently until the user pulls to refresh.

This is debug-only code, so severity is accepted as MED rather than HIGH. It should at minimum call `seedTwitter/seedReddit` inside a single db transaction before returning.

---

### CONC-9 — `orderStart` mutation from concurrent child coroutines [MED]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:181-192`

**Pattern:** Shared mutable loop variable accessed from concurrent children

**Vulnerable code:**
```kotlin
var orderStart = orderOfLastBookmark + BUFFER
tweetEntitiesChannel.consumeEach {
    it.data.forEach {
        val order = orderStart   // Captures current value
        scope.launch(Dispatchers.IO) {
            if (!deletedBookmarkRepository.isDeleted(it.tweetEntity.id)) {
                saveTweetEntities(tweetEntitiesToOrderLens.modify(it) { order })
            }
        }
        orderStart--             // Decremented on caller dispatcher
    }
}
```

The `val order = orderStart` capture happens on the outer dispatcher (sequential), which is correct for capturing unique values. However, child `scope.launch` coroutines are fire-and-forget — `consumeEach` may return and the outer loop may overwrite `orderOfLastBookmark` (the class-level field updated by subsequent calls to `getLatestBookmark()`) before the children have finished writing. Because the children use the captured `order` val, the _order_ assignment itself is safe. The concern is that if `buildDatabase()` is called again before all launched children complete, `latestBookmarkInDatabase` and `orderOfLastBookmark` are re-read (lines 150-153), potentially returning stale values. This is a LOW-impact data ordering issue (items may get duplicate `order` values), not a data loss issue.

---

### CONC-10 — `_tagsForTweet` read-modify-write [LOW]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/BookmarksViewModel.kt:101`

**Pattern:** Read-modify-write on `MutableStateFlow`

**Vulnerable code:**
```kotlin
fun loadTagsForTweet(tweetId: String) {
    viewModelScope.launch {
        val tags = repository.getTagsForTweet(tweetId)
        _tagsForTweet.value = _tagsForTweet.value + (tweetId to tags)  // Non-atomic RMW
    }
}
```

Two concurrent `loadTagsForTweet("A")` calls can both read the same snapshot and both write back, with the second overwriting the first's result for a different tweet. However, `AllBookmarksScreen` calls `LaunchedEffect(id) { onLoadTags(id) }` with a stable key, so each tweet ID is loaded at most once per composition lifetime. In practice this race is very unlikely. Use `_tagsForTweet.update { it + (tweetId to tags) }` for correctness:

```kotlin
_tagsForTweet.update { it + (tweetId to tags) }
```

---

### CONC-11 — Snackbar queue accumulation on rapid deletes [LOW]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt:101-119`

**Pattern:** Stateful event queue with no deduplication

**Scenario:** User rapidly long-presses and deletes 3 items. `DeletedBookmarkRepository.softDelete` emits 3 `SnackbarEvent.UndoableDelete` events. `SyncErrorBus` has `extraBufferCapacity = 1, DROP_OLDEST` — but `DeletedBookmarkRepository._events` uses the same config. The second and third events drop the first. Only one snackbar is shown, but the item IDs that reach `showSnackbar` may be wrong if the buffer drops the first event but passes the third. This can result in "UNDO" restoring item C when the user intended to undo item A. This is a UX correctness bug rather than a data loss bug, but worth noting.

---

### CONC-12 — `isFetching` without `@Volatile` [NIT]

**Location:** `Repository.kt:52`, `RedditRepository.kt:42`

```kotlin
private var isFetching = false
```

On Android/JVM, field reads without synchronization may use a cached value from the CPU register. Since these are accessed from coroutines running on thread pool threads (Dispatchers.IO), the JVM memory model technically requires visibility guarantees. In practice, the `Mutex.withLock` provides a happens-before edge that covers this in the current code pattern, but this is an implicit dependency. Mark `@Volatile` for explicitness.

---

## 4) Concurrency Safety Analysis

| Component | Atomicity | Scope Hygiene | Dispatcher Safety | Risk |
|-----------|-----------|---------------|-------------------|------|
| `Repository` (Twitter) | Partial — no `@Transaction` on insert | Unscoped `CoroutineScope` (CONC-4) | IO-only launches ✓ | CONC-1,2,4,6,9 |
| `RedditRepository` | Same | Same | IO-only launches ✓ | CONC-1,4,6 |
| `DeletedBookmarkRepository` | Good — single DAO insert | n/a | Blocking DAO called from IO ⚠ | CONC-3,5 |
| `SyncErrorBus` | Good — tryEmit thread-safe | Singleton SharedFlow ✓ | n/a | Config correct |
| `BookmarksViewModel` | Partial — RMW on tagsMap | viewModelScope ✓ | viewModelScope on Main ✓ | CONC-10 |
| `HomeRoute` collectors | n/a | lifecycleScope ✓ | Compose Main ✓ | CONC-7,11 |
| `DebugDataInjector` | Not transactional | lifecycleScope ✓ | withContext(IO) ✓ | CONC-8 |

---

## 5) Recommendations

### Fix Before Release (HIGH)

1. **CONC-4** — Add `@Singleton` and `SupervisorJob` to `CoroutineModule.providesCoroutineScope`. 5 minutes. Risk without fix: single exception silently kills all background sync.

2. **CONC-2** — Add `@Transaction` to `TweetDao.insertTweetEntities`. 1 line. Risk without fix: partial inserts visible to Paging3 — transient corrupt rows in the list.

3. **CONC-3** — Make `DeletedBookmarkDao.existsBlocking` a `suspend` function named `exists`. Update call sites. 15 minutes. Risk without fix: potential `IllegalStateException` if call site dispatcher ever changes.

4. **CONC-1** — Refactor split-lock to single `tryLock`/`unlock` pattern in both repositories. 20 minutes. Risk without fix: `isFetching` stuck `true` after coroutine cancellation, silently disabling all future syncs.

### Fix Soon (MED)

5. **CONC-6** — Add refresh-token mutex / dedup in both `Repository` and `RedditRepository`. 30 minutes.

6. **CONC-7** — Replace all `collectAsState()` on ViewModels with `collectAsStateWithLifecycle()`. 10 minutes, mechanical change.

7. **CONC-9** — Join all child `scope.launch` jobs before returning from `refreshBookmarksInternal`, or switch to `coroutineScope { }` block. Prevents stale order reads on rapid back-to-back refresh calls.

### Address in Next Sprint (LOW)

8. **CONC-10** — Replace `_tagsForTweet.value = ... +` with `_tagsForTweet.update { it + ... }`.

9. **CONC-11** — Debounce rapid snackbar events or replace the SharedFlow buffer strategy with a `Channel(UNLIMITED)` for the snackbar event bus so events are not dropped.

### Backlog (NIT)

10. **CONC-12** — Annotate `isFetching` with `@Volatile` in both repositories.

---

## 6) False Positives & Disagreements

- **CONC-5 (tombstone/Paging flicker)**: Room's InvalidationTracker is documented to batch invalidations across transactions. In practice on WAL mode SQLite the flicker window is <16 ms. If manual testing shows no visible flicker, this can be deprioritized.
- **CONC-8 (DebugDataInjector)**: Debug-only, excluded from release builds by AGP source sets. Acceptable risk for test tooling.
- **CONC-9 (orderStart)**: The `order` field is cosmetic sort order, not a unique constraint. Duplicate `order` values cause visual sort instability, not data loss.

---

*Review completed: 2026-05-18*
