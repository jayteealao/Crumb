---
review: migrations
round: 2
scope: slug-wide (git diff main...HEAD)
slug: brutalist-redesign
branch: feat/brutalist-redesign
base: main
completed: 2026-05-18
id-prefix: R2-MIG
---

# Database Migration Safety Review (Round 2) — brutalist-redesign

**Scope:** slug-wide validation of round-1 migration findings + new migrations added during the fix sequence.
**Database:** SQLite via Room 2.8.4 (single-device Android).
**Migrations now in scope:** MIGRATION_4_5 (round 1), MIGRATION_5_6 (H15 fix), MIGRATION_6_7 (PERF-04 fix), MIGRATION_7_8 (MIG-03 fix).
**Reviewer:** Migration Safety Agent (round 2).

## Summary

The three migrations added during the fix sequence are additive, small, and consistent with Room's expectations. `MIGRATION_5_6` correctly rebuilds the `deleted_bookmarks` table for the composite-PK fix with safe data preservation; `MIGRATION_6_7` adds two indexes on `order` columns to back the PERF-04 sort; `MIGRATION_7_8` adds two indexes to back the FK-index fix (MIG-03). All three are registered in `DatabaseModule.addMigrations(...)` in the correct order. The schema JSONs through 8.json are present and structurally consistent with the entity annotations.

Two residual gaps remain: (1) `MIGRATION_7_8` is **not covered** by any `MigrationTest` case (only 4→5, 5→6, 6→7 are tested), so the index-creation SQL in production is exercised by Room's identity-hash check only — if the index name or column name diverges from what KSP emits, the failure mode is a runtime `IllegalStateException` rather than a CI failure; (2) the `PRAGMA foreign_key_check` inside `migrate6To7_indexesOrderColumns` runs **after** the migration and after `PRAGMA foreign_keys = ON`, which means it validates the post-migration FK graph but does not verify FK integrity at any other point in the migration sequence — in this branch's specific case the test still has signal because the v6 starting point is empty, but it should not be treated as a general-purpose FK integrity assertion.

**Migrations Reviewed:** 4 (MIGRATION_4_5, 5_6, 6_7, 7_8)
**Affected Tables:** `deleted_bookmarks` (rebuild), `tweetEntity` (index add), `reddit_posts` (index add), `pollIds` (index add), `mediaKeys` (index add)
**Estimated Downtime Risk:** None (SQLite single-device).

**Severity Breakdown:**
- BLOCKER: 0
- HIGH: 0
- MED: 2
- LOW: 1
- NIT: 1

**Round-1 → Round-2 carry-over:**
- MIG-01 (H21): **RESOLVED** — confirmed schema files are structurally consistent KSP output.
- MIG-03: **RESOLVED** — MIGRATION_7_8 adds the indexes; entity annotations + schema JSON 8.json + DAO line up.
- MIG-04: **RESOLVED for parent doc** — `document(tweetId)` + `SetOptions.merge()`; **residual** on sub-collection idempotency under concurrent first-writes (carried as R2-MIG-02).

**Merge Recommendation:** APPROVE_WITH_COMMENTS

---

## Findings

### R2-MIG-01: `MIGRATION_7_8` is not exercised by `MigrationTest` [MED]

**Severity:** MED | **Confidence:** High

**Location:** `app/src/androidTest/java/com/github/jayteealao/crumbs/db/MigrationTest.kt:24-114` + `app/src/main/java/com/github/jayteealao/crumbs/di/DatabaseModule.kt:163-168`

**Issue:**
`MigrationTest` defines three cases: `migrate4To5_createsDeletedBookmarksTable`, `migrate5To6_compositePkAndDataSurvives`, `migrate6To7_indexesOrderColumns`. There is **no** `migrate7To8_*` case. `MIGRATION_7_8` adds two indexes (`index_pollIds_tweetId`, `index_mediaKeys_tweet_id`) and is registered in `DatabaseModule.addMigrations(...)` alongside the others, but its SQL is only validated by Room's runtime identity-hash comparison against `8.json`. If the index name or column name in the `CREATE INDEX IF NOT EXISTS` strings drifts from what KSP emits for the `@Index("tweetId")` / `@Index("tweet_id")` annotations, the failure mode is `IllegalStateException: Migration didn't properly handle ...` at *user* device upgrade time, not at CI time.

**Evidence:**
- `MigrationTest` lacks any case for 7→8.
- `DatabaseModule.addMigrations(...)` includes `MIGRATION_7_8`.
- The annotation-generated index names follow Room's convention `index_<table>_<column>` — currently matched by the migration's `index_pollIds_tweetId` (tweetId) and `index_mediaKeys_tweet_id` (tweet_id). These are correct, but only inspection (and Room's runtime check) catches drift.

**Impact:**
A future edit to `MIGRATION_7_8` that mistypes the index name would break upgrades from any device on schema v7 — but unit-test CI would stay green.

**Fix:**
Add a `migrate7To8_indexesPollIdsAndMediaKeys` test mirroring the 6→7 pattern:

```kotlin
@Test
fun migrate7To8_indexesPollIdsAndMediaKeys() {
    helper.createDatabase(TEST_DB, 7).apply { close() }
    val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

    val expectedIndexes = setOf("index_pollIds_tweetId", "index_mediaKeys_tweet_id")
    val foundIndexes = mutableSetOf<String>()
    db.query(
        "SELECT name FROM sqlite_master WHERE type='index' AND name IN ('index_pollIds_tweetId', 'index_mediaKeys_tweet_id')"
    ).use { cursor ->
        while (cursor.moveToNext()) foundIndexes += cursor.getString(0)
    }
    assertEquals(expectedIndexes, foundIndexes)
    db.close()
}
```

`runMigrationsAndValidate(..., validateDroppedTables = true)` also re-stamps the identity hash against `8.json`, so this single test gives both index-presence and hash-stability coverage.

---

### R2-MIG-02: `uploadTweet()` sub-collection writes are not idempotent under concurrent first-writes [MED]

**Severity:** MED | **Confidence:** Med

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreRepository.kt:222-274`

**Issue:**
The MIG-04 fix correctly addressed the parent-document race:
```kotlin
val tweetRef = db.collection(TWEETS_COLLECTION).document(tweetId)   // deterministic
batch.set(tweetRef, FirestoreTweet.fromTweetEntity(...), SetOptions.merge())
```
Two concurrent `uploadTweet(sameTweet)` calls collapse to one `tweets/{tweetId}` doc — good.

However, the fanout to sub-collections is gated by a non-atomic existence check:
```kotlin
val existingSnapshot = tweetRef.get().await()
val isFirstWrite = !existingSnapshot.exists()
if (!isFirstWrite) {
    batch.commit().await()
    return@withContext   // skip sub-collections entirely
}
// ... else: write users/metrics/media/includes/textAnnotations with `document()` (auto-id)
```

The `document()` calls for users/metrics/media/includes/textAnnotations use Firestore's auto-generated random IDs — not deterministic keys. The `isFirstWrite` guard prevents fanout on the second+ upload of an already-known tweet, but if two concurrent uploads both observe `existingSnapshot.exists() == false` (because the parent doc has not yet been committed), both will fan out the sub-collections. Result: **duplicate user/metric/media/include/textAnnotation docs** in Firestore.

The window is the duration between `tweetRef.get().await()` and `batch.commit().await()` — typically tens to hundreds of milliseconds. Sync code paths that call `uploadTweet` concurrently (e.g., parallel `scope.launch` per fetched tweet in a single sync pass) are at risk.

**Evidence:**
```kotlin
// FirestoreRepository.kt:223
val existingSnapshot = tweetRef.get().await()
val isFirstWrite = !existingSnapshot.exists()
// ...
tweetEntities.twitterUserEntity.forEach { user ->
    val userRef = db.collection(USERS_COLLECTION).document()   // auto-id — no dedup
    batch.set(userRef, FirestoreUser.fromTwitterUserEntity(user))
}
```
Sub-collections do not use `merge()` or deterministic keys.

**Impact:**
Bloated Firestore storage on concurrent sync, slow `whereIn` queries downstream, and reads inflated by duplicate documents (which `getTweetEntitiesByIds` then groups by `tweetId` — so the local DB stays clean, but Firestore quota burns).

**Fix:**
Use deterministic keys for sub-documents too, with `merge()`:
```kotlin
tweetEntities.twitterUserEntity.forEach { user ->
    val userRef = db.collection(USERS_COLLECTION).document(user.id)
    batch.set(userRef, FirestoreUser.fromTwitterUserEntity(user), SetOptions.merge())
}

val metricsRef = db.collection(METRICS_COLLECTION).document(tweetId)
batch.set(metricsRef, FirestoreMetrics.fromTweetPublicMetrics(...), SetOptions.merge())

tweetEntities.tweetMediaEntity.forEach { media ->
    val mediaRef = db.collection(MEDIA_COLLECTION).document(media.mediaKey)
    batch.set(mediaRef, FirestoreMedia.fromTweetMediaEntity(media), SetOptions.merge())
}
// ... and so on for includes / textAnnotations using their natural keys
```
This makes the full `uploadTweet` idempotent without an existence check — concurrent first-writes collapse at every level.

---

### R2-MIG-03: `MIGRATION_5_6` relies on Room's implicit transaction — not self-evident from source [LOW]

**Severity:** LOW | **Confidence:** Med

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/di/DatabaseModule.kt:185-204`

**Issue:**
The four-step recreate-and-rename pattern in `MIGRATION_5_6` (CREATE → INSERT OR IGNORE → DROP → RENAME) is atomic only because Room wraps each `Migration.migrate(db)` call in an outer transaction. The migration body itself does not call `db.beginTransaction()`. This is correct under the current Room runtime, but the contract is invisible to a future contributor.

See the parallel finding R2-DATA-01 in the data-integrity round 2 review.

**Fix:**
Add a one-line comment, or make the transaction explicit with `db.beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()`.

---

### R2-MIG-04: `migrate6To7_indexesOrderColumns` `PRAGMA foreign_key_check` is not a general FK integrity assertion [NIT]

**Severity:** NIT | **Confidence:** High

**Location:** `app/src/androidTest/java/com/github/jayteealao/crumbs/db/MigrationTest.kt:101-111`

**Issue:**
The test does:
```kotlin
db.execSQL("PRAGMA foreign_keys = ON")
db.query("PRAGMA foreign_key_check").use { cursor ->
    assertTrue("PRAGMA foreign_key_check should return no rows", cursor.count == 0)
}
```
Two concerns:

1. Turning FKs on **after** the migration only validates the post-migration FK graph. The migration itself ran with FKs disabled (Room's default for migrations to avoid intermediate FK violations during multi-step recreates). The pragma check on a freshly-created-from-v6 (i.e., empty) database is trivially passing — there are no rows to violate any FK.
2. `PRAGMA foreign_key_check` returns rows describing violations. The test asserts `cursor.count == 0`. SQLite's `PRAGMA foreign_key_check` does enumerate rows, so `cursor.count` is meaningful here — but Android's `SupportSQLiteDatabase.query()` returns a `Cursor` whose `count` property is the row count. If the underlying implementation returns -1 before `moveToFirst()` (uncommon but possible on some Cursor subclasses), the assertion would silently pass without iterating. Safer: `assertFalse("no FK violations", cursor.moveToNext())`.

**Impact:**
False sense of FK validation. The migration is genuinely safe (it only creates indexes — no row movement could orphan), so this is purely about test rigor.

**Fix:**
1. Use `cursor.moveToNext() == false` to assert emptiness explicitly.
2. If future migrations move data between tables with FK constraints, add a pre-population step in the test before `runMigrationsAndValidate` so the FK check has actual rows to validate.

---

## Migration Analysis (Round 2)

| Migration | Tables | Operation | SQLite Lock | Reversible? | Test Coverage | Risk |
|---|---|---|---|---|---|---|
| MIGRATION_4_5 | `deleted_bookmarks` | CREATE TABLE | Exclusive (negligible) | No (drop on rollback) | `migrate4To5_*` | LOW (additive) |
| MIGRATION_5_6 | `deleted_bookmarks` | CREATE_new + INSERT OR IGNORE + DROP + RENAME | Exclusive (relies on Room outer txn) | No | `migrate5To6_compositePkAndDataSurvives` | LOW — data preserved |
| MIGRATION_6_7 | `tweetEntity`, `reddit_posts` | CREATE INDEX × 2 | Exclusive (fast on SQLite) | No | `migrate6To7_indexesOrderColumns` | LOW |
| MIGRATION_7_8 | `pollIds`, `mediaKeys` | CREATE INDEX × 2 | Exclusive (fast on SQLite) | No | **MISSING (R2-MIG-01)** | LOW (but untested) |

---

## Schema JSON Provenance

- `5.json` (identityHash `21c8e471c121d8f64cdd9d59dde7f300`) — single-PK `deleted_bookmarks`.
- `6.json` (identityHash `e43065d914bfdbe905f81b7f7181da83`) — composite-PK `deleted_bookmarks(bookmarkId, source)`.
- `7.json` (identityHash `dc74823285f5686a60f7cf385eb7ec8c`) — adds `index_tweetEntity_order` + `index_reddit_posts_order`.
- `8.json` (identityHash `c9660b6ecb966166c37a3cea999d21d7`) — adds `index_pollIds_tweetId` + `index_mediaKeys_tweet_id`.

Hash chain progresses monotonically as expected. No collisions; no hand-edits visible (createSql/indices structurally aligned with annotation positions in source).

**Round-1 MIG-01 closure:** The `5.json` identity hash matches what Room expects because (a) `MIGRATION_4_5` is registered and runs, re-stamping the v5 hash on devices upgrading from v4; and (b) the v5 hash itself is structurally consistent with the entity definitions in source as of `e97ee5f`. The PR notes confirm a clean `git status` after KSP regen, so the file is byte-stable against a fresh build at the same Room version.

---

## Validation of Round-1 Claimed Fixes

### MIG-01 (commit `7dcf586`) — 5.json byte-stable via KSP regen

**Verified:** PASS (delegated to commit note). 5.json structure consistent with v4 entities + the v5 additive `deleted_bookmarks` table.

### MIG-03 (commit `32e01af`) — FK indexes on pollIds and mediaKeys

**Verified:** PASS.

| Source | Index name | Column |
|---|---|---|
| `PollIds` entity annotation: `indices = [Index("tweetId")]` | KSP-emitted: `index_pollIds_tweetId` | `tweetId` |
| MIGRATION_7_8 SQL: `CREATE INDEX IF NOT EXISTS \`index_pollIds_tweetId\` ON \`pollIds\` (\`tweetId\`)` | `index_pollIds_tweetId` | `tweetId` |
| `8.json` indices array: `"name": "index_pollIds_tweetId", "columnNames": ["tweetId"]` | matches | matches |
| `MediaKeys` entity annotation: `indices = [Index("tweet_id")]` | KSP-emitted: `index_mediaKeys_tweet_id` | `tweet_id` |
| MIGRATION_7_8 SQL: `CREATE INDEX IF NOT EXISTS \`index_mediaKeys_tweet_id\` ON \`mediaKeys\` (\`tweet_id\`)` | `index_mediaKeys_tweet_id` | `tweet_id` |
| `8.json` indices array: `"name": "index_mediaKeys_tweet_id", "columnNames": ["tweet_id"]` | matches | matches |

All three sources (entity annotation, migration SQL, schema JSON) agree exactly. Room's runtime identity check at user-device upgrade from v7 to v8 will pass. The "expected vs found" diff failure mode is not present.

### MIG-04 (commit `32e01af`) — Firestore upload uses `document(tweetId)` + `SetOptions.merge()`

**Verified:** PARTIAL — parent doc is fully idempotent; sub-collection fanout is racey under concurrent first-writes (carried as R2-MIG-02).

---

## Backwards Compatibility Analysis

**Additive-only changes** across all four migrations:
- 4→5: adds `deleted_bookmarks` table.
- 5→6: rebuilds `deleted_bookmarks` with composite PK — preserves all existing rows via `INSERT OR IGNORE`.
- 6→7: adds two indexes (no schema-shape change to columns/PKs).
- 7→8: adds two more indexes.

No column dropped. No column type changed. No NOT NULL added without DEFAULT on existing rows. Pre-existing user data survives the full 4→8 chain intact.

---

## Rollback Analysis

- **Reversible migrations:** 0 (Room/SQLite does not provide automatic rollback; this is normal for Android).
- **Irreversible migrations:** 4 (a rollback would lose tombstones from 5→6 and lose indexes from 6→7 and 7→8 — indexes are derivable but tombstones are not).
- **Risk:** Mobile downgrade is not a supported scenario; Room throws on a stored version higher than the compiled version unless `fallbackToDestructiveMigration` is set (it is not — correct).

---

## Recommendations

1. **R2-MIG-01 (MED):** Add `migrate7To8_indexesPollIdsAndMediaKeys` to `MigrationTest`. Closes a real CI gap for the FK-index migration.
2. **R2-MIG-02 (MED):** Switch `uploadTweet` sub-collection writes to deterministic keys + `SetOptions.merge()`. Eliminates duplicate-doc bloat under concurrent first-writes.
3. **R2-MIG-03 (LOW):** Add a clarifying comment to `MIGRATION_5_6` about Room's implicit transaction wrapper, or make the wrapper explicit.
4. **R2-MIG-04 (NIT):** Tighten the FK-check assertion in `migrate6To7_indexesOrderColumns` to `assertFalse(cursor.moveToNext())` for explicit iteration.

None of these block the merge. The round-1 migration HIGH (MIG-01) and the MED items (MIG-03, MIG-04) are addressed in code; the residual MED items above are forward-looking hardening rather than regressions.

---

## Deployment Checklist (Round 2)

- [x] No column dropped or type changed across the 4→8 migration chain
- [x] All four migrations registered in `DatabaseModule.addMigrations(...)`
- [x] Schemas 4.json through 8.json exported and structurally consistent with entity annotations
- [x] `MigrationTest` covers 4→5, 5→6, 6→7
- [ ] **`MigrationTest` does not cover 7→8** (R2-MIG-01)
- [x] FK indexes present on `pollIds.tweetId` and `mediaKeys.tweet_id` (MIG-03 resolved)
- [x] Parent Firestore doc upload is idempotent (MIG-04 partial)
- [ ] Sub-collection Firestore writes not yet idempotent under concurrent first-writes (R2-MIG-02)
