---
review-command: backend-concurrency
review-round: 2
slug: brutalist-redesign
review-scope: slug-wide
base: main
head: feat/brutalist-redesign
date: 2026-05-18
verdict: ship-with-followups
related:
  round-1: 07-review-backend-concurrency.md
  parent: 07-review.md
fixes-validated: [H8, H9, H10, H7, CONC-6, CONC-7]
fixes-passed: [H8, H9, H10, H7, CONC-7]
fixes-passed-with-concerns: [CONC-6]
new-findings: 5
new-blocker: 0
new-high: 0
new-med: 2
new-low: 2
new-nit: 1
---

# Backend Concurrency Review — Round 2

**Reviewed:** `git diff main...HEAD` on `feat/brutalist-redesign`
**Date:** 2026-05-18
**Round 1 reference:** `07-review-backend-concurrency.md`
**Round 1 claimed fixes:** H8, H9, H10, H7, CONC-6, CONC-7

---

## 0) Validation Summary

| Round-1 ID | Claim                                               | Commit    | Result                  |
|------------|-----------------------------------------------------|-----------|-------------------------|
| H8 (CONC-1)| `fetchMutex.tryLock()` + `try/finally` both repos   | `41aa8aa` | **PASS**                |
| H9 (CONC-2)| `@Transaction @Insert` on `TweetDao`                | `41aa8aa` | **PASS**                |
| H10 (CONC-4)| `SupervisorJob + ExceptionHandler` + `@Singleton` | `41aa8aa` | **PASS**                |
| H7 (CONC-3)| `existsBlocking` → suspend, prefetch snapshot       | `41aa8aa` | **PASS**                |
| CONC-6     | `refreshTokenSingleFlight` mutex both repos         | `d417330` | **PASS-with-concern** (R2-CONC-1) |
| CONC-7     | `collectAsStateWithLifecycle` in `HomeRoute.kt`     | `4d9634c` | **PASS**                |

**New round-2 findings:** 5 (0 BLOCKER, 0 HIGH, 2 MED, 2 LOW, 1 NIT).

**Verdict:** Ship with follow-ups. The core round-1 hardening landed correctly. Three latent issues remain that were either out-of-scope for round 1 or introduced by the fixes themselves.

---

## 1) Per-Fix Validation Detail

### H8 — Split-lock `isFetching` → `fetchMutex.tryLock()` [PASS]

**Twitter** (`feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:141-206`):
```kotlin
private suspend fun refreshBookmarksInternal() {
    if (!fetchMutex.tryLock()) {
        Timber.d("buildDatabase: Already fetching, skipping")
        return
    }
    _isRefreshing.value = true
    try {
        // ... full fetch body ...
    } finally {
        _isRefreshing.value = false
        fetchMutex.unlock()
    }
}
```

**Reddit** (`feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:64-155`): same `tryLock` → `try/finally { fetchMutex.unlock() }` pattern, with `tryLock` invoked inside `scope.launch(Dispatchers.IO) { ... }`.

Validation against the round-1 concern (cancellation orphaning the flag):
- The `isFetching` Boolean is **completely gone** in both repositories. The mutex itself is the single source of truth for "another fetch in flight".
- `try/finally { fetchMutex.unlock() }` releases the lock on every exit path: normal return, thrown exception, and coroutine cancellation (because `CancellationException` propagates through `finally`).
- `_isRefreshing.value = false` is also inside the same `finally`, so the UI refresh-spinner cannot stick.

**Caveat — `tryLock`/`unlock` matching:** Kotlin's `Mutex.tryLock`/`unlock` is **owner-less** by default (no `owner` argument). If the same coroutine somehow re-entered `refreshBookmarksInternal` from inside the `try` block (e.g. via a re-entrant API call back into the repository), `tryLock` would return `false` (correct skip), but no deadlock risk exists. The current call graph has no re-entry path, so this is safe.

**Result:** PASS. Round-1 split-lock vulnerability is closed.

---

### H9 — `@Transaction @Insert` on TweetDao [PASS]

`feature/twitter/src/main/java/com/github/jayteealao/twitter/data/TweetDao.kt:55-103`:

```kotlin
@Transaction
@Insert(onConflict = OnConflictStrategy.IGNORE)
fun insertTweetEntities(tweet: TweetEntity, ...)

@Transaction
fun insertTweetEntitiesAtomic(tweet: TweetEntity, ..., pollIds: PollIds?) {
    insertTweetEntities(...)
    pollIds?.let { insertPollId(it) }
}
```

`Repository.saveTweetEntities()` calls `insertTweetEntitiesAtomic` (line 110). Both annotations are present:
- `@Transaction` on the generated multi-`@Insert` ensures Room wraps the per-parameter INSERT statements in one SQLite transaction.
- The outer `@Transaction` on the default-method wrapper extends the transaction window to also cover the optional `pollIds` insert.

Paging3's `InvalidationTracker` fires once at COMMIT, not once per child insert — partially-hydrated rows are no longer observable.

**Result:** PASS. Atomic write achieved end-to-end including the `PollIds` sub-entity.

---

### H10 — `SupervisorJob` + `CoroutineExceptionHandler` on injected scope [PASS]

`app/src/main/java/com/github/jayteealao/crumbs/di/CoroutineModule.kt`:
```kotlin
@Singleton
@Provides
fun providesCoroutineScope(): CoroutineScope {
    val handler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Uncaught exception in application coroutine scope")
    }
    return CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
}
```

All three round-1 sub-bugs are fixed:
- `@Singleton` annotation ensures the same scope is shared by Twitter `Repository`, `RedditRepository`, and `FirestoreRepository`.
- `SupervisorJob()` means one child's failure no longer cancels siblings.
- `CoroutineExceptionHandler` ensures uncaught exceptions in fire-and-forget `scope.launch { }` blocks (e.g. `firestoreRepository.uploadTweet` at `Repository.kt:125`) reach Timber instead of being silently swallowed by the default handler.

**Result:** PASS.

---

### H7 — `existsBlocking` → suspend, prefetch snapshot [PASS]

- `DeletedBookmarkDao.exists(id, source)` is now `suspend` (line 19).
- `DeletedBookmarkDao.getAllIdsSnapshotForSource(source)` returns a one-shot snapshot (line 22).
- `DeletedBookmarkRepository.deletedIdsSnapshot(source)` (line 31) returns `Set<String>`.
- Both repositories prefetch the snapshot **once per sync pass** and gate inserts on `Set.contains`:
  - Twitter: `Repository.kt:93` (`syncFromFirestore`) and `Repository.kt:168` (`refreshBookmarksInternal`).
  - Reddit: `RedditRepository.kt:91` (inside `buildDatabase`).

The N+1 per-row DAO query (~800 calls per Reddit sync) is eliminated, and the latent main-thread-DB crash risk on the OkHttp callback thread is gone — the gate is now an in-memory `Set.contains`.

**Result:** PASS.

---

### CONC-6 — Token refresh single-flight [PASS-with-concern]

Both repositories now have a per-repo `refreshMutex` and a `refreshTokenSingleFlight()` helper. Twitter (`Repository.kt:293-316`) and Reddit (`RedditRepository.kt:219-239`) follow the same shape:
```kotlin
private suspend fun refreshTokenSingleFlight(currentRefreshToken: String): Boolean {
    if (!refreshMutex.tryLock()) {
        Timber.d("refreshTokenSingleFlight: another refresh in flight, deferring")
        return true   // <-- the concern
    }
    return try {
        val response = authClient.refreshAccessToken(currentRefreshToken)
        if (!response.accessToken.isNullOrBlank()) { /* persist */ true } else false
    } catch (e: Exception) { false }
    finally { refreshMutex.unlock() }
}
```

This correctly collapses N parallel 401s into one network call. **However**, the deferring branch returns `true` unconditionally, which feeds into a new round-2 finding documented below as **R2-CONC-1**.

**Result:** PASS structurally; new MED finding raised on the optimistic-defer semantics.

---

### CONC-7 — `collectAsStateWithLifecycle` in `HomeRoute.kt` [PASS]

`app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt:71-74`:
```kotlin
val twitterFilter by bookmarksViewModel.filter.collectAsStateWithLifecycle()
val redditFilter  by redditViewModel.filter.collectAsStateWithLifecycle()
val twitterAccess by loginViewModel.isAccessTokenAvailable.collectAsStateWithLifecycle()
val redditAccess  by redditViewModel.isAccessTokenAvailable.collectAsStateWithLifecycle()
```

Import on line 19: `import androidx.lifecycle.compose.collectAsStateWithLifecycle`. All four StateFlow collectors at the route are now lifecycle-aware.

**Caveat** (out of round-1 scope but flagged below as R2-CONC-4): `tagsForTweet` inside `TwitterBookmarksScreen.kt:174` and `RedditBookmarksScreen.kt:160` still use plain `collectAsState()`. Round-1 finding CONC-7 listed only `HomeRoute.kt`/`AllBookmarksScreen.kt`/`TwitterBookmarksScreen.kt`/`RedditBookmarksScreen.kt`. The route-level subscriptions are migrated; the feed-screen `tagsMap` collectors are not.

**Result:** PASS for the claimed location. Two screen-level call sites remain (LOW finding R2-CONC-4).

---

## 2) New Round-2 Findings

### R2-CONC-1 — Optimistic deferral returns `true` while in-flight refresh may still fail [MED]

**Location:**
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:293-297`
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:219-223`

**Pattern:** Optimistic single-flight join

**Vulnerable code:**
```kotlin
if (!refreshMutex.tryLock()) {
    Timber.d("refreshTokenSingleFlight: another refresh in flight, deferring")
    return true   // <-- assumes the in-flight refresh will succeed
}
```

**Race scenario:**

```
t0  Caller A: 401, calls refreshTokenSingleFlight()
    A acquires refreshMutex, starts twitterAuthClient.refreshAccessToken(rt)
t1  Caller B: 401, calls refreshTokenSingleFlight()
    B fails tryLock(), returns TRUE immediately
t2  B's onError callback: !recovered == false → NO banner emitted.
    B returns to its pagination loop and tries the next page with the
    same stale access token (still in memory; Prefs not yet written).
t3  A's network call returns 401 (refresh token also expired).
    A.refreshTokenSingleFlight returns FALSE.
    A's onError callback: !recovered == true → emits TwitterAuth401 banner.
```

**Result:** The user sees a banner that came from A's caller, but B has already silently failed to recover and produced no signal. In the Reddit case (`RedditRepository.kt:130-135`), B's `suspendOnError` branch decides whether to `syncErrorBus.emit(RedditAuth401)` based purely on the `true`/`false` return. With `return true`, B suppresses the banner even though its own request will then fail again with 401 on retry — the next page fetch picks the same now-known-bad token.

The shape is **not** harmful to data (no double-spend, no lost insert), but it is **observably wrong** in the dual-401 case:
- Banner is shown only for caller A.
- Caller B keeps paginating against a known-bad token until it also fails (and `hasMore = false` on the second 401 stops it). One extra wasted request per concurrent caller after the first.
- More importantly: if the in-flight refresh succeeds on a slow network, B's deferred-`true` return is correct only if B re-fetches `authPref.accessCode` from Prefs **before** issuing its next request. Reddit's loop captures `accessToken = redditPrefs.accessToken.first()` once at the top of `buildDatabase` (line 82) and reuses it for every page. **The refreshed token will not be picked up until the next `buildDatabase` call.**

**Severity:** MED — UX/observability bug, not data corruption.
**Confidence:** High — code path is direct.

**Fix:**

Option 1 (preferred — wait for the in-flight refresh result):
```kotlin
private suspend fun refreshTokenSingleFlight(refreshToken: String): Boolean {
    return refreshMutex.withLock {   // queues instead of skipping
        // re-check: another caller may have just refreshed
        val current = authPref.accessCode.first()
        if (current != accessTokenAtCallTime) return@withLock true
        val resp = authClient.refreshAccessToken(refreshToken)
        if (!resp?.accessToken.isNullOrBlank()) {
            authPref.setAccessAndRefreshToken(resp.accessToken, resp.refreshToken)
            true
        } else false
    }
}
```

Option 2 (cheaper — `Deferred<Boolean>` cache):
Replace `refreshMutex` with a `@Volatile var refreshJob: Deferred<Boolean>?` so deferred callers `.await()` the same result. Concrete pattern:
```kotlin
private suspend fun refreshTokenSingleFlight(rt: String): Boolean {
    val existing = synchronized(this) { refreshJob }
    if (existing != null) return existing.await()
    val job = scope.async(Dispatchers.IO) { /* refresh + persist */ }
    synchronized(this) { refreshJob = job }
    return try { job.await() } finally { synchronized(this) { refreshJob = null } }
}
```

Option 3 (cheapest — accept the limitation, document it):
Keep `return true` on `tryLock` failure, but flip the in-loop token capture from "read once at top" to "re-read from Prefs on every retry after a 401". This pushes correctness into the caller.

---

### R2-CONC-2 — Firestore `uploadTweet` early-return leaks batch on partial commit [MED]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreRepository.kt:222-240`

**Pattern:** Early-return without rollback handling

**Vulnerable code:**
```kotlin
val tweetRef = db.collection(TWEETS_COLLECTION).document(tweetId)
val existingSnapshot = tweetRef.get().await()
val isFirstWrite = !existingSnapshot.exists()

val batch = db.batch()
batch.set(tweetRef, FirestoreTweet.fromTweetEntity(tweetEntities.tweetEntity), SetOptions.merge())

if (!isFirstWrite) {
    batch.commit().await()   // <-- if this throws, control falls through to outer catch
    return@withContext
}
// ... else: keep building batch and commit at end ...
```

**Concern raised:** does the early return leak if `batch.commit().await()` throws?

**Analysis:**
- A Firestore `WriteBatch` is a local object. It holds an in-memory list of operations; there is nothing to leak on the JVM side — the batch is GC-eligible the moment the function frame returns. There is no `close()`/`release()`.
- The outer `try { ... } catch (e: Exception) { Timber.e(...) }` (lines 214, 272-274) swallows the throw. The caller (`Repository.saveTweetEntities` at line 126: `scope.launch(Dispatchers.IO) { firestoreRepository.uploadTweet(tweetEntities) }`) is fire-and-forget — it never observes the failure either.
- **Local-state hazard:** Because the parent-doc `set` with `SetOptions.merge()` is the only op in the batch on a `!isFirstWrite` path, and because Firestore considers a doc to "exist" once any merge set has been committed even with no fields changed, there is no inconsistency at the database level on retry — the next sync will see `existingSnapshot.exists() == true` again and take the same early-return branch.
- **Cross-replica race:** `tweetRef.get().await()` then `batch.commit().await()` is **not atomic**. Two concurrent syncs both seeing `isFirstWrite = true` will each fan out the full child-collection set (users / metrics / media / includes / textAnnotations), creating duplicate child docs with random doc-ids. Round-1 noted CONC-6 (refresh dedup) but not this Firestore TOCTOU; round-2 surfaces it explicitly.
- The deterministic parent `tweetRef.set(merge)` is collision-safe (same key → idempotent), but `db.collection(USERS_COLLECTION).document()` (no key) **always allocates a fresh random ID** (line 244). Two concurrent first-writes → two complete sets of user/metrics/media child docs.

**Severity:** MED — read costs and storage bloat on race, but no functional corruption (downstream reads use `whereIn("tweetId", ...)` and pick up either set).
**Confidence:** High — code path is direct; race window is the round-trip between `get()` and `commit()`.

**Fix:**

Option 1 (use deterministic child doc IDs):
```kotlin
val userRef = db.collection(USERS_COLLECTION).document("${tweetId}_${user.id}")
batch.set(userRef, FirestoreUser.fromTwitterUserEntity(user), SetOptions.merge())
```
Same pattern for `metrics` (1:1 with tweet → use `tweetId`), `media` (composite of `tweetId_mediaKey`), `includes`/`textAnnotations` (composite of `tweetId_index` or content hash).

Option 2 (use Firestore transaction):
Replace the `get().await()` + batch with `db.runTransaction { tx -> ... }`. Transaction body re-runs on optimistic-lock conflict.

The `batch.commit()` `try/catch` at the outer scope is correct as-is — there is nothing to leak in the JVM sense — but the consequence of a failed commit is currently invisible to the caller (fire-and-forget upload). Consider returning `Result<Unit>` so `Repository.saveTweetEntities` can re-queue on transient failure (round-1 DATA-05 deferred this — flagging continuity).

---

### R2-CONC-3 — `_tagsForTweet.value = _tagsForTweet.value + batch` still vulnerable to lost updates [LOW]

**Location:**
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/BookmarksViewModel.kt:101, 111`
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditViewModel.kt:172, 180`

**Pattern:** Read-modify-write on `MutableStateFlow`

**Vulnerable code:**
```kotlin
fun loadTagsForItems(ids: List<String>) {
    if (ids.isEmpty()) return
    viewModelScope.launch {
        val batch = repository.getTagsForItems(ids)
        _tagsForTweet.value = _tagsForTweet.value + batch    // ❌ non-atomic RMW
    }
}
```

Round-1 CONC-10 raised this for the single-item `loadTagsForTweet`. Round 1's fix triage routed CS-10/MAINT-05/CONC-7 to commit `4d9634c`, but `_tagsForTweet` RMW was deferred (it is not listed in the round-1 patch table). The H14 batch path (commit `e97ee5f`) **introduced two more RMW sites** — the new `loadTagsForItems` in both VMs.

**Race scenario:**

```
t0  AllBookmarksScreen LaunchedEffect(twitterIds) → bookmarksViewModel.loadTagsForItems(idsT)
t0  AllBookmarksScreen LaunchedEffect(redditIds)  → redditViewModel.loadTagsForItems(idsR)  (different VM, different StateFlow)
```

The two VMs do **not** share `_tagsForTweet`, so cross-VM races are not possible. Within a single VM:
- `loadTagsForItems` is keyed off `LaunchedEffect(itemIds)` where `itemIds` is the page-snapshot. Compose only re-runs the effect when `itemIds` value-changes.
- However, if `loadTagsForTweet(id)` (still present at line 98-103 of `BookmarksViewModel`) is called from a long-press popup save (which dispatches `saveTags` then a follow-up `loadTagsForTweet`), and a page boundary refresh fires `loadTagsForItems(newIds)` concurrently, both coroutines can read the same `_tagsForTweet.value` snapshot and clobber each other. The second writer's `+ batch` includes only its own keys; the first writer's single `(id to tags)` entry can be lost if the batch does not contain `id`.

This is the same theoretical concern raised in round-1 CONC-10 plus the new batch site. The atomic fix is one line in each call site:
```kotlin
_tagsForTweet.update { it + batch }
```

**Severity:** LOW (very narrow race window, both writers eventually converge on subsequent re-load).
**Confidence:** Med.

**Fix:** Replace all four `_tagsForTweet.value = _tagsForTweet.value + ...` sites with `_tagsForTweet.update { it + ... }`. The `update` extension on `MutableStateFlow` is CAS-based and atomic.

---

### R2-CONC-4 — `collectAsState` (not lifecycle-aware) on `tagsMap` in feature screens [LOW]

**Location:**
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt:174` — `val tagsMap by bookmarksViewModel.tagsForTweet.collectAsState()`
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreen.kt:160` — `val tagsMap by redditViewModel.tagsForTweet.collectAsState()`

Round-1 CONC-7 listed these screens in its scope. Round-1 patch (commit `4d9634c`) migrated `HomeRoute.kt` but left these two collectors un-migrated. Twitter and Reddit screens still keep an active `tagsMap` subscriber while the app is backgrounded.

**Severity:** LOW (StateFlow has no replay loss; only wasted subscription).
**Confidence:** High.

**Fix:** Mechanical replacement, same import already present in the module:
```kotlin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
val tagsMap by bookmarksViewModel.tagsForTweet.collectAsStateWithLifecycle()
```

---

### R2-CONC-5 — `getLatestBookmark()` is non-suspend, called from coroutines [NIT]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/TweetDao.kt:132-133`
```kotlin
@Query("SELECT * FROM tweetEntity WHERE referenced = false ORDER BY `order` DESC LIMIT 1")
fun getLatestBookmark(): TweetEntity?
```

Called at:
- `Repository.kt:65` (inside `scope.launch(Dispatchers.IO) { ... }` — safe by dispatcher)
- `Repository.kt:152` (inside `refreshBookmarksInternal` which can be called from `refreshBookmarks` on whichever dispatcher the caller is on)

Same shape as the round-1 `existsBlocking` finding (H7) — fragile to dispatcher changes. Today every call path is on `Dispatchers.IO`, but Room only enforces "not on Main" at runtime, not at compile time. Marking it `suspend` removes the latent risk.

**Severity:** NIT.
**Confidence:** High.

**Fix:**
```kotlin
@Query("SELECT * FROM tweetEntity WHERE referenced = false ORDER BY `order` DESC LIMIT 1")
suspend fun getLatestBookmark(): TweetEntity?
```
And update the two call sites to drop the implicit IO requirement (they are already inside coroutines).

---

## 3) Round-1 Findings Not Re-Reviewed

These were either out-of-scope for round 2 (no claimed fix) or already validated in round 1:

| ID    | Status (per `07-review.md`)                      |
|-------|--------------------------------------------------|
| CONC-5 (tombstone/Paging flicker) | Defaulted-defer (MED) |
| CONC-8 (DebugDataInjector wipe race) | Fixed at `32e01af` (debug-only) |
| CONC-9 (orderStart) | Deferred with rationale ("already gated by fetchMutex") |
| CONC-10 (single-item RMW) | Subsumed by R2-CONC-3 (multi-call-site batch RMW) |
| CONC-11 (snackbar) | Buffer raised to 16 slots at `d417330` |
| CONC-12 (`@Volatile`) | Subsumed — `isFetching` no longer exists (H8) |

---

## 4) Recommendations

### Should Fix (MED) — 2 findings

1. **R2-CONC-1** — `refreshTokenSingleFlight` deferred-`true` semantics.
   - Pick Option 1 (`refreshMutex.withLock` + re-check Prefs) or Option 2 (`Deferred<Boolean>` cache). Effort: 20 min.
   - Risk if unfixed: Silent failure on the second concurrent 401; one wasted page-fetch per concurrent caller after the first; refreshed token only picked up on next `buildDatabase` invocation.

2. **R2-CONC-2** — Firestore `uploadTweet` non-deterministic child-doc IDs.
   - Pick deterministic IDs (Option 1) for all child collections. Effort: 30 min.
   - Risk if unfixed: Duplicate child docs on concurrent first-write race; inflated read costs and Firestore storage.

### Backlog (LOW + NIT) — 3 findings

3. **R2-CONC-3** — Switch `_tagsForTweet` RMW to `.update { ... }`. 4 call sites, mechanical. Effort: 5 min.
4. **R2-CONC-4** — Migrate two remaining `collectAsState` calls to `collectAsStateWithLifecycle`. Effort: 2 min.
5. **R2-CONC-5** — Mark `TweetDao.getLatestBookmark()` `suspend`. Effort: 5 min.

---

## 5) Verdict

**Ship with follow-ups.**

All six round-1 claimed fixes landed correctly. The mutex+try/finally pattern, the `@Transaction` annotation, the supervisor scope, the suspend-DAO migration, the refresh single-flight, and the lifecycle-aware collectors are all in place and verifiable in the diff.

The five round-2 findings are non-blocking:
- R2-CONC-1 and R2-CONC-2 are observable bugs (UX wrong on dual-401; duplicate Firestore child docs on race) but not data-corruption.
- R2-CONC-3 / -4 / -5 are hygiene cleanups that the round-1 patches almost-but-not-quite swept.

The concurrency posture is materially better than at round-1 entry:
- Atomicity: **Protected** (was: Violated). `@Transaction` covers the multi-entity insert.
- Idempotency: **Mostly Ensured** (was: Missing on Firestore). Deterministic parent doc with merge; child docs still TOCTOU-vulnerable (R2-CONC-2).
- Locking: **Correct** (was: Suboptimal split-lock). Single `tryLock` + try/finally.
- Async correctness: **Correct** (was: Missing lifecycle). Route-level collectors migrated; two feature-screen collectors remain (R2-CONC-4).
- Token refresh: **Mostly Correct** (was: Missing). Single-flight via mutex; optimistic-defer caveat (R2-CONC-1).

Recommend merging the round-1 fixes and queueing R2-CONC-1 + R2-CONC-2 as a small follow-up before any multi-device or production-load scenario.

---

*Review completed: 2026-05-18*
