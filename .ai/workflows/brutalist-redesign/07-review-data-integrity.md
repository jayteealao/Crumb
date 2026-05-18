# Data Integrity Review — brutalist-redesign

**Scope:** slug-wide (`git diff main...HEAD`)
**Date:** 2026-05-18
**Reviewer:** Data Integrity Agent

## Summary

The diff introduces the `deleted_bookmarks` tombstone table (schema v5), soft-delete plumbing across both Twitter and Reddit repositories, a debug seed helper, and a Firestore backfill path. The schema migration itself is structurally sound, but several integrity gaps exist: the tombstone primary key is single-column (`bookmarkId` only), creating a latent reused-ID collision hazard; the multi-insert `insertTweetEntities` + `insertPollId` split is not wrapped in an explicit `@Transaction`, leaving a partial-write window; `existsBlocking` is called on IO-bound coroutines without a `withContext` guard; `clearAllTables()` in the debug seed wipes tombstones so a wipe+reseed would re-surface bookmarks the user deleted; and the Firestore upload is fire-and-forget so local and remote can silently diverge.

**Severity Breakdown:**
- BLOCKER: 0
- HIGH: 2
- MED: 3
- LOW: 2
- NIT: 1

**Merge Recommendation:** REQUEST_CHANGES

---

## Findings

### DATA-01: Tombstone PK is single-column (`bookmarkId` only) — cross-source ID collision risk [HIGH]

**Location:** `core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmark.kt:7-11`
`app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/5.json:958-986`

**Issue:**
`DeletedBookmark` uses only `bookmarkId` as its primary key. Twitter and Reddit use different ID namespaces — a Reddit `name` field (e.g. `t3_abc123`) and a Twitter snowflake ID. However both can share numeric-string representations, and nothing in the schema guarantees they are globally unique across sources. More concretely: `DeletedBookmarkDao.delete(id: String)` deletes by `bookmarkId` alone. If a Twitter tweet and a Reddit post happen to share the same `id` string, soft-deleting one will tombstone both in the filter query (`LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId`).

The `source` column exists in the row but is NOT part of the primary key, so a second `insert(REPLACE)` for the same `bookmarkId` from a different source silently overwrites `source`.

**Evidence:**
```kotlin
// DeletedBookmark.kt
@Entity(tableName = "deleted_bookmarks")
data class DeletedBookmark(
    @PrimaryKey val bookmarkId: String,   // ← single-column PK
    val source: String,                   // ← source stored but not part of PK
    val deletedAt: Long,
)
```
```sql
-- schema 5.json
PRIMARY KEY(`bookmarkId`)                 -- source NOT in PK
```
The LEFT JOIN in both `getTweetsTombstoneAware` and `getPostsTombstoneAware` matches on `bookmarkId` alone:
```sql
LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId
-- Reddit version:
LEFT JOIN deleted_bookmarks d ON p.id = d.bookmarkId
```

**Impact:**
- Soft-deleting a Reddit post with `id = "12345"` will suppress any Twitter tweet whose `id = "12345"` and vice-versa.
- `undoDelete(id)` deletes by `bookmarkId` only, so restoring one source's bookmark clears the tombstone for the other source too.
- `source` field is silently overwritten on REPLACE conflict.

**Fix:**
Make the PK composite `(bookmarkId, source)` and update all DAO queries and JOIN conditions accordingly:
```kotlin
@Entity(
    tableName = "deleted_bookmarks",
    primaryKeys = ["bookmarkId", "source"]
)
data class DeletedBookmark(
    val bookmarkId: String,
    val source: String,
    val deletedAt: Long,
)
```
```sql
-- Migration addition (new table or ALTER + recreate)
PRIMARY KEY(`bookmarkId`, `source`)

-- DAO delete
@Query("DELETE FROM deleted_bookmarks WHERE bookmarkId = :id AND source = :source")
suspend fun delete(id: String, source: String)

-- EXISTS check
@Query("SELECT EXISTS(SELECT 1 FROM deleted_bookmarks WHERE bookmarkId = :id AND source = :source)")
fun existsBlocking(id: String, source: String): Boolean

-- LEFT JOIN (twitter)
LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId AND d.source = 'TWITTER'
-- LEFT JOIN (reddit)
LEFT JOIN deleted_bookmarks d ON p.id = d.bookmarkId AND d.source = 'REDDIT'
```
This is also a schema change that would require a MIGRATION_5_6.

---

### DATA-02: `insertTweetEntities` + `insertPollId` split outside a transaction [HIGH]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/TweetDao.kt:55-67`
`feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:106-120`

**Issue:**
`saveTweetEntities` calls `tweetDao.insertTweetEntities(...)` (10 entity-type inserts batched by Room into one call) and then, in a separate call, `tweetEntities.pollIds?.let { tweetDao.insertPollId(it) }`. The `insertTweetEntities` method has no `@Transaction` annotation — Room's `@Insert` on a function with multiple parameters does **not** automatically wrap in a transaction. If the process crashes or an insert throws between the two calls, a tweet row exists with no poll row, and the poll FK reference is dangling.

**Evidence:**
```kotlin
// TweetDao.kt:55
@Insert(onConflict = OnConflictStrategy.IGNORE)  // ← no @Transaction
fun insertTweetEntities(
    tweet: TweetEntity,
    tweetsReferenced: List<TweetEntity>,
    ...
    mediaKeys: List<MediaKeys>
)
```
```kotlin
// Repository.kt:106-120
fun saveTweetEntities(tweetEntities: TweetEntities, uploadToFirestore: Boolean = true) {
    tweetDao.insertTweetEntities(...)          // ← call 1
    tweetEntities.pollIds?.let { tweetDao.insertPollId(it) }  // ← call 2 — separate
    ...
}
```

**Impact:**
A crash between the two calls leaves `tweetEntity` in the DB with a `PollIds` FK reference that was never written. `tweetEntity` will appear in paging queries but any join/relation that expects `PollIds` will be incomplete.

**Fix:**
Add `@Transaction` to `insertTweetEntities` in `TweetDao`, and include `pollIds` inside the same annotated function, or wrap the two calls in a `runInTransaction` block from `Repository`:
```kotlin
// Option A — DAO level (cleanest)
@Transaction
@Insert(onConflict = OnConflictStrategy.IGNORE)
fun insertTweetEntities(
    tweet: TweetEntity,
    ...
    mediaKeys: List<MediaKeys>,
    pollIds: PollIds?            // ← restore pollIds param
)

// Option B — Repository level
fun saveTweetEntities(tweetEntities: TweetEntities, ...) {
    db.runInTransaction {
        tweetDao.insertTweetEntities(...)
        tweetEntities.pollIds?.let { tweetDao.insertPollId(it) }
    }
}
```

---

### DATA-03: `existsBlocking` — non-suspend blocking DB call on coroutine thread [MED]

**Location:** `core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkDao.kt:19`
`core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkRepository.kt:33`
`feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:95,186`
`feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:110`

**Issue:**
`DeletedBookmarkDao.existsBlocking` is a plain (non-suspend) blocking Room query. It is called from `Repository.isDeleted` which is in turn called from inside `scope.launch(Dispatchers.IO)` coroutines. While `Dispatchers.IO` threads can technically execute blocking calls, this pattern:
1. Bypasses Room's strict-mode checks (which expect queries on background threads but via suspend/RxJava)
2. Will cause `StrictMode.disallowDiskReads()` violations on the main thread if any caller path ever runs on `Dispatchers.Main`
3. In `RedditRepository.buildDatabase` the `.filter { !deletedBookmarkRepository.isDeleted(...) }` is inside `response.onSuccess { }` — a lambda whose dispatcher is not guaranteed to be IO; with Sandwich, onSuccess runs on the OkHttp thread which is not necessarily `Dispatchers.IO`

**Evidence:**
```kotlin
// DeletedBookmarkDao.kt:19
fun existsBlocking(id: String): Boolean   // plain fun — blocks thread

// DeletedBookmarkRepository.kt:33
fun isDeleted(id: String): Boolean = dao.existsBlocking(id)

// RedditRepository.kt:110 — inside onSuccess lambda (OkHttp thread)
.filter { !deletedBookmarkRepository.isDeleted(it.data.name) }
```

**Fix:**
Convert `existsBlocking` to a suspend function and update all call sites:
```kotlin
@Query("SELECT EXISTS(SELECT 1 FROM deleted_bookmarks WHERE bookmarkId = :id)")
suspend fun exists(id: String): Boolean

// Repository.kt — already inside withContext(Dispatchers.IO) after this fix:
if (!deletedBookmarkRepository.isDeleted(it.tweetEntity.id)) { ... }
```
For `RedditRepository`, collect the IDs to filter outside the `onSuccess` lambda or make the filter a suspend operation.

---

### DATA-04: `clearAllTables()` in debug seed wipes `deleted_bookmarks` — tombstone lost on wipe+reseed [MED]

**Location:** `app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugDataInjector.kt:31-38`

**Issue:**
`DebugDataInjector.run(wipe = true)` calls `db.clearAllTables()`, which truncates every table including `deleted_bookmarks`. If a tester soft-deletes a bookmark, then triggers a wipe+seed, the tombstone is gone. On the next sync (or Firestore backfill) the deleted bookmark reappears because `isDeleted` will return false. This violates the tombstone-survives-resync invariant in debug flows, which can mask real bugs in the UI/sync contract.

**Evidence:**
```kotlin
// DebugDataInjector.kt:31
suspend fun run(wipe: Boolean) = withContext(Dispatchers.IO) {
    if (wipe) {
        db.clearAllTables()     // ← wipes deleted_bookmarks too
    }
    seedTwitter()
    seedReddit()
    ...
}
```

**Impact:**
Debug-only but means Maestro flows that validate tombstone suppression post-wipe will produce false-positive results.

**Fix:**
Either re-seed tombstone rows for any items that should remain deleted after wipe, or add a note/assertion in the debug seed that callers must not wipe when testing tombstone persistence. A pragmatic fix for debug:
```kotlin
if (wipe) {
    // Preserve tombstones across wipe — critical for tombstone-survives-resync tests
    val tombstones = db.deletedBookmarkDao().getAllIds().first()
    db.clearAllTables()
    // Optionally restore tombstones if debug scenario requires it
}
```

---

### DATA-05: Firestore upload is fire-and-forget — local/remote can silently diverge [MED]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:121-125`
`feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreRepository.kt:187-242`

**Issue:**
`saveTweetEntities` launches a detached `scope.launch(Dispatchers.IO)` to upload to Firestore. The upload result is silently swallowed (exception caught and logged only). There is no retry, no local flag marking the tweet as "pending Firestore upload", and no reconciliation. If Firestore is unavailable at upload time, the tweet exists locally but not in Firestore. The `syncFromFirestore` path only pulls tweets that are missing locally — it does not push locally-added tweets back to Firestore. Over time, the two stores drift.

**Evidence:**
```kotlin
// Repository.kt:121
if (uploadToFirestore) {
    scope.launch(Dispatchers.IO) {
        firestoreRepository.uploadTweet(tweetEntities)   // fire-and-forget
    }
}

// FirestoreRepository.kt:239
} catch (e: Exception) {
    Timber.e(e, "Error uploading tweet to Firestore")   // swallowed
}
```
The Firestore sync path checks for tweets missing locally but does not detect tweets missing from Firestore:
```kotlin
val missingIds = firestoreIds - localIds  // only pulls Firestore → local
```

**Impact:**
If Firestore upload silently fails, the backfill feature (which was the purpose of the recent Firestore commit) is incomplete. Bookmarks added on device A will not appear on device B even though the feature purports to provide multi-device sync.

**Fix (minimal):**
Add a `pendingFirestoreUpload: Int` column (or a separate pending-uploads table) to track unsent items. At sync time, also push locally-new items:
```kotlin
// At minimum, log a metric / schedule a retry:
scope.launch(Dispatchers.IO) {
    val success = runCatching { firestoreRepository.uploadTweet(tweetEntities) }.isSuccess
    if (!success) {
        // Mark for retry, or use WorkManager for reliable upload
    }
}
```

---

### DATA-06: Token refresh does not update access token in `Prefs` — sync proceeds with stale token [LOW]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:169-173`
`feature/twitter/src/main/java/com/github/jayteealao/twitter/services/TwitterAuthClientImpl.kt:117-128`

**Issue:**
When a 401 is encountered during sync, `onError` calls both `syncErrorBus.emit(...)` and `twitterAuthClient.refreshAccessToken(refreshToken)`. `TwitterAuthClientImpl.refreshAccessToken` returns the new `TokenResponse` but does **not** persist the new token to `Prefs`. The call site in `Repository` also does not persist it. The `produceTweetResponseEntities` channel has already been launched with the old `accessCode` — the sync call does not re-try with the new token.

**Evidence:**
```kotlin
// Repository.kt:170
onError = {
    syncErrorBus.emit(SyncErrorEvent.TwitterAuth401())
    twitterAuthClient.refreshAccessToken(refreshToken)  // return value discarded
}

// TwitterAuthClientImpl.kt:117
override suspend fun refreshAccessToken(refreshToken: String): TokenResponse? {
    var result: TokenResponse? = null
    scope.launch { result = ... }.join()
    return result   // ← result returned but caller ignores it
}
```
Compare with `AuthRepository.refreshAccessToken` (line 82) which correctly calls `authPref.setAccessAndRefreshToken(...)` — the in-sync-path call does not.

**Impact:**
After a 401 during sync, the new token is fetched but never stored. The next `buildDatabase` call reads the old (expired) token from `Prefs` and immediately 401s again.

**Fix:**
```kotlin
onError = {
    syncErrorBus.emit(SyncErrorEvent.TwitterAuth401())
    val newToken = twitterAuthClient.refreshAccessToken(refreshToken)
    if (newToken != null) {
        authPref.setAccessAndRefreshToken(newToken.accessToken!!, newToken.refreshToken!!)
    }
}
```

---

### DATA-07: `setAccessAndRefreshToken` writes two DataStore keys sequentially — window of inconsistency [LOW]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Prefs.kt:24-27`
`core/pref/src/main/java/com/github/jayteealao/pref/AuthPref.kt:22-24`

**Issue:**
`setAccessAndRefreshToken` calls `context.writeString(ACCESS_CODE, ...)` and then `context.writeString(REFRESH_CODE, ...)` as two sequential `DataStore.edit` operations. Between the two writes, a process kill or a coroutine cancellation leaves `ACCESS_CODE` updated but `REFRESH_CODE` still holding the old value. On restart, the app reads the new access token paired with the old refresh token, which is invalid.

**Evidence:**
```kotlin
// Prefs.kt:24
suspend fun setAccessAndRefreshToken(accessCode: String, refreshCode: String) {
    context.writeString(ACCESS_CODE, accessCode)    // edit #1
    context.writeString(REFRESH_CODE, refreshCode)  // edit #2 — separate transaction
}

// AuthPref.kt — each writeString is its own DataStore.edit call
suspend fun Context.writeString(key: String, value: String) {
    dataStore.edit { pref -> pref[stringPreferencesKey(key)] = value }
}
```

**Impact:**
Narrow window (process must die between the two `edit` suspensions) but non-zero risk on a low-memory device. Symptoms: app starts, sends valid access token, but has wrong/old refresh token — next refresh cycle fails.

**Fix:**
Write both keys atomically in a single `edit` block:
```kotlin
suspend fun setAccessAndRefreshToken(accessCode: String, refreshCode: String) {
    context.dataStore.edit { pref ->
        pref[stringPreferencesKey(ACCESS_CODE)] = accessCode
        pref[stringPreferencesKey(REFRESH_CODE)] = refreshCode
    }
}
```

---

### DATA-08: `MigrationTest` only checks row-count = 0; does not verify schema columns or data preservation [NIT]

**Location:** `app/src/androidTest/java/com/github/jayteealao/crumbs/db/MigrationTest.kt:23-38`

**Issue:**
The migration test validates that `deleted_bookmarks` exists and is empty after migration, but does not:
1. Assert the column names/types (e.g., that `source` and `deletedAt` are present and correct affinity)
2. Insert a row in v4 that should survive the migration in another table (e.g., a `tweetEntity`) and verify it is still present in v5 — confirming data preservation, not just table creation

**Evidence:**
```kotlin
db.query("SELECT count(*) FROM deleted_bookmarks").use { cursor ->
    assertTrue("deleted_bookmarks table missing after migration", cursor.moveToFirst())
    assertEquals(0, cursor.getInt(0))   // only checks empty table
}
```

**Impact:**
A future migration that accidentally drops a column in `deleted_bookmarks` would not be caught. The test gives false confidence about data preservation for pre-existing rows in other tables.

**Fix:**
```kotlin
// Assert schema
db.query("SELECT bookmarkId, source, deletedAt FROM deleted_bookmarks LIMIT 0").use { cursor ->
    assertEquals(3, cursor.columnCount)
}

// Assert data preservation: insert tweetEntity in v4 before migration, verify after
// (in a separate test or in the same test using helper.createDatabase + rawInsert)
```

---

## Critical Invariants Check

| Invariant | Status |
|---|---|
| Soft-deleted bookmark never re-appears after Firestore sync | PARTIAL — gated on `isDeleted`, but composite PK gap means cross-source collision possible |
| Tombstone survives `clearAllTables()` in debug | VIOLATED (debug only, DATA-04) |
| Tweet insert is all-or-nothing (including pollIds) | VIOLATED — split across two non-transactional calls (DATA-02) |
| Access + refresh token written atomically | VIOLATED — two sequential DataStore writes (DATA-07) |
| Firestore and local DB stay in sync | BEST-EFFORT only — no retry on upload failure (DATA-05) |
| `deleted_bookmarks` source column identifies owning platform | PARTIAL — stored but not enforced in PK or JOIN (DATA-01) |

---

## Transaction Analysis

| Operation | Location | Has Transaction? | Risk Level |
|---|---|---|---|
| `insertTweetEntities` (10 entity types) | `TweetDao.kt:55` | NO (@Insert only) | HIGH |
| `insertTweetEntities` + `insertPollId` | `Repository.kt:106-120` | NO | HIGH |
| `softDelete` (single row) | `DeletedBookmarkDao.kt:13` | YES (Room wraps single inserts) | OK |
| `undoDelete` (single delete) | `DeletedBookmarkDao.kt:16` | YES | OK |
| Firestore batch upload | `FirestoreRepository.kt:203` | YES (Firestore batch) | OK for Firestore |
| Token write (access + refresh) | `Prefs.kt:24-27` | NO (two DataStore edits) | LOW |

---

## Recommendations

### Immediate Actions (HIGH)
1. **DATA-01**: Change `deleted_bookmarks` PK to `(bookmarkId, source)` and update all DAO queries and LEFT JOIN conditions to filter by source. Requires migration to schema v6.
2. **DATA-02**: Add `@Transaction` to `insertTweetEntities` in `TweetDao` and move `pollIds` back inside the transactional scope.

### Short-term Improvements (MED)
3. **DATA-03**: Convert `existsBlocking` to `suspend fun exists(...)` and fix all call sites.
4. **DATA-04**: Document or handle tombstone-wipe interaction in debug seed; add a note in `DebugDataInjector` that `wipe=true` resets soft-delete state.
5. **DATA-05**: Add a `pending_firestore_upload` flag or use WorkManager for reliable Firestore upload; add `syncLocalToFirestore` call at sync completion.
6. **DATA-06**: Persist the new token from the in-sync 401 recovery path.

### Long-term Hardening (LOW)
7. **DATA-07**: Merge the two DataStore writes in `setAccessAndRefreshToken` into a single atomic `edit` block.
8. **DATA-08**: Strengthen `MigrationTest` to verify column presence and data preservation across migration.
