---
scope: slug-wide (git diff main...HEAD) — round 2
round: 2
completed: 2026-05-18
prior-review: 07-review-performance.md
---

# Performance Review — brutalist-redesign · Round 2

**Verdict:** Ship — H14, H7, H5, PERF-04, and PERF-07 all land cleanly. Two new MED findings and three LOWs surface; PERF-06's deferral rationale is partially incorrect but the runtime cost is small enough to keep the deferral.

**Reviewed:** slug-wide / `git diff main...HEAD`, focused on round-1 claimed fixes plus the perf-adjacent commits `3512352` (Firestore paged cursor) and `32e01af` (idempotent upload).

## Round-1 Fix Validation

| Round-1 ID | Status | Notes |
|---|---|---|
| H14 / PERF-01 | **Confirmed fixed** | New `TweetDao.getTagsForTweets(ids: IN)` + `TagRepository.getTagsForItems()` + per-screen `LaunchedEffect(itemIds)`. Per-item `LaunchedEffect(id)` deleted from all three screens. See `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/TweetDao.kt:155-156`, `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:254-258`, `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt:88-103`, `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt:80-87`, `feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreen.kt:71-78`. |
| H7 / PERF-02 | **Confirmed fixed** | `DeletedBookmarkDao.exists` is now `suspend`; per-sync-pass `deletedIdsSnapshot(source)` is loaded into a `Set<String>` once and the loop does `id !in set`. Verified at `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:168,93` and `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt` (uses the same prefetch pattern). |
| H5 / PERF-03 | **Confirmed fixed** | `RedditRepository.pagingPostsData(filter)` now branches to `getPostsByTagsTombstoneAware(filter.selectedTags.toList())` when tags are present; `@Suppress("UNUSED_PARAMETER")` is gone. See `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:165-176`. **Caveat:** see R2-PERF-01 — the Reddit tag query joins through `tweet_tags`, which has a FK constraint to `tweetEntity(id)`, so Reddit tags can never actually persist. |
| PERF-04 | **Confirmed fixed** | `@Entity(indices = [..., Index("order")])` on `TweetEntity` (`feature/twitter/.../models/Tweet.kt:40`) and on `RedditPostEntity` (`feature/reddit/.../models/RedditModels.kt:62-67`). `MIGRATION_6_7` (`app/src/main/java/com/github/jayteealao/crumbs/di/DatabaseModule.kt:178-183`) creates both indexes; schema v7 (`app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/7.json`) confirms `index_tweetEntity_order` and `index_reddit_posts_order` are present. |
| PERF-07 | **Confirmed fixed** | `CrumbApplication` now implements `ImageLoaderFactory` with `crossfade(180)` + bounded memory (20%) + disk (2%) caches. See `app/src/main/java/com/github/jayteealao/crumbs/CrumbApplication.kt:13-39`. The original AsyncImage call sites still lack explicit `placeholder` / `error` painters, but the bounded ImageLoader + crossfade is the primary win and the placeholder gap is now cosmetic, not perf. |
| PERF-06 | **Deferred — rationale partly wrong** | See R2-PERF-02. The "Compose `@Immutable` annotation already covers it" claim is incorrect — the annotation is a *promise*, not a *proof*. The compiler treats `Map<>`-typed fields inside an `@Immutable` class as still requiring `equals()`-based stability checks at recomposition. The runtime impact today is small (one batch tag-load per page change, not per item), so the deferral remains acceptable, but the rationale should be corrected. |

## Findings Round 2 (5 total)

BLOCKER: 0 | HIGH: 0 | MED: 2 | LOW: 3 | NIT: 0

---

### R2-PERF-01 — Reddit tag query joins `tweet_tags` (Twitter-owned table) with a FK constraint that makes Reddit tag insertion impossible [MED] · Confidence: High

**Location:** `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditDao.kt:52-61`, `feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditViewModel.kt:190-196`, schema `app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/7.json` (tweet_tags FK to tweetEntity)

**Issue:** H5's fix for Reddit tag filtering reuses the Twitter-owned `tweet_tags` table:

```kotlin
// RedditDao.kt:52-61
@Query("""
    SELECT p.* FROM reddit_posts p
    LEFT JOIN deleted_bookmarks d ON p.id = d.bookmarkId AND d.source = 'reddit'
    INNER JOIN tweet_tags tt ON tt.tweetId = p.id        // <— tweet_tags table
    WHERE d.bookmarkId IS NULL
      AND tt.tagName IN (:tagNames)
    GROUP BY p.id
    ORDER BY p.`order` DESC
""")
fun getPostsByTagsTombstoneAware(tagNames: List<String>): PagingSource<Int, RedditPostData>
```

But `tweet_tags` has a `FOREIGN KEY(tweetId) REFERENCES tweetEntity(id) ON DELETE CASCADE` (schema v7). Reddit post ids do not exist in `tweetEntity`, so any attempt to insert `TweetTagCrossRef(redditPostId, tag)` would fail the FK check. `RedditViewModel.saveTags` calls `tagRepository.saveTags(id, tags)` (line 192), which — because `TagRepository` is implemented only by the Twitter `Repository` — inserts into `tweet_tags` and **will throw `SQLiteConstraintException`** on every Reddit save attempt.

**Performance impact (per the brief's lens):**
- The Reddit tag-filter pager is wired to a join that *can never produce rows*. The query itself is cheap (empty result set), but every chip toggle still tears down the previous Pager and rebuilds with `INNER JOIN tweet_tags` — no perf benefit, no correctness gain.
- Worse, in practice the user sees the filter behave like a "clear all results" button rather than a no-op, which will drive support friction.

**Severity rationale:** MED because it is a functional hole that masquerades as a perf fix. It is not BLOCKER because Reddit tag *display* (`loadTagsForItems(redditIds)` → `getTagsForItems`) returns empty maps cleanly without crashing, and Reddit tag *save* is presumably untested in this branch (no Reddit-side save path test in `feature/reddit/src/androidTest/`).

**Fix:**
1. Either drop the Reddit tag UI affordance entirely until a Reddit-owned cross-ref table exists, or
2. Add a generic `bookmark_tags(bookmarkId, source, tagName)` table in `core/data` that both features share via `TagRepository`. Removes the implicit Twitter-only coupling that B1 was supposed to eliminate.

---

### R2-PERF-02 — PERF-06 deferral rationale is incorrect; `Map<String, List<String>>` on `@Immutable` class is *not* covered by the annotation [MED] · Confidence: High

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt:55-59` (UiState data class), `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt:59-63`, fix-status note for PERF-06 in `.ai/workflows/brutalist-redesign/07-review.md:259`

**Issue:** The deferral note says "*UI state classes are already `@Immutable` annotated so Compose treats them as stable*". This conflates two different things:

- **`@Immutable` annotation:** asks the Compose compiler to trust the class is stable *without* checking its field types. The compiler accepts this *as a promise from the author*.
- **Runtime stability inference:** still inspects field types at composition. If a field is typed `kotlin.collections.Map`, Compose's stability inference flags the whole class as unstable at runtime *regardless of the annotation*, because the stdlib `Map` interface has mutable subtypes.

The net effect is that `@Immutable` on `TwitterBookmarksUiState` is technically a contract violation. In practice it works because:
1. Compose's stable-skipping heuristic is *not* the only thing that drives recomposition — the actual emitted instance is a new `data class` copy with a new identity, so any field change re-renders the consuming composable anyway.
2. After H14, `_tagsForTweet` is only updated once per page-snapshot batch (not per item), so even pessimistic re-renders cost ~1 per page, not ~20.

**Severity rationale:** MED because:
- The deferral rationale is technically wrong and will mislead future maintainers who think `@Immutable` is sufficient.
- The runtime cost in this codebase is now small after H14, so the deferral *decision* remains defensible — just on different grounds (low ROI, not "already covered").

**Fix:** Update the deferral note in `07-review.md` to say something like:

> Deferred: `@Immutable` is a promise, not a stability proof; stdlib `Map<>` fields make the class unstable at runtime. After H14 the tag-load fan-out is one batch per page-snapshot, so the recomposition cost is bounded and the ROI of migrating to `ImmutableMap` is low. Revisit if profiling shows tag-snapshot churn on the hot path.

If migration is desired, swap `Map<String, List<String>>` → `kotlinx.collections.immutable.ImmutableMap<String, ImmutableList<String>>` and convert at the VM boundary (`_tagsForTweet`). The kotlinx-collections-immutable dep is already on the build path (`BookmarksViewModel.kt:13` imports `persistentSetOf`/`toPersistentSet`).

---

### R2-PERF-03 — Coil disk cache at 2% may be too small for an image-heavy bookmark feed [LOW] · Confidence: Med

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/CrumbApplication.kt:30-35`

**Issue:**
```kotlin
.diskCache {
    DiskCache.Builder()
        .directory(cacheDir.resolve("image_cache"))
        .maxSizePercent(0.02)                          // 2% of free space
        .build()
}
```

Coil's default `maxSizePercent` is **0.02 (2%)** of available disk and Coil's default is *also* capped at 250 MB by default — but the explicit `Builder()` here removes that cap and ties the cache solely to free-space %. On a device with 5 GB free, this is 100 MB. On a device with 500 MB free (common on cheap devices), this is 10 MB — about 40-50 thumbnails before evictions cycle.

The 20% memory cache is fine for an image-list app (Coil's default is 25%, slightly higher but materially equivalent).

**Severity rationale:** LOW because Coil will simply re-fetch evicted images; no correctness break. But the symptom is "user scrolls a feed of 200 bookmarks, scrolls back up, sees the spinner re-spinning" — a visible user-facing perf regression on storage-constrained devices.

**Fix:**
```kotlin
.diskCache {
    DiskCache.Builder()
        .directory(cacheDir.resolve("image_cache"))
        .maxSizePercent(0.02)
        .maxSizeBytes(150L * 1024 * 1024)              // floor at 150 MB
        .build()
}
```
Or use `.maxSizePercent(0.05)` to match the per-device shape better. Profile before tuning.

**Memory cache (20%):** appropriate. Compose `AsyncImage` with `ContentScale.Crop` decodes at the displayed bitmap size, so even a feed of 100 200dp tiles will fit comfortably under 20% of process memory.

---

### R2-PERF-04 — `getTagsForTweets(IN-clause)` parameter count is unbounded; risks SQLITE_MAX_VARIABLE_NUMBER (999) on large page snapshots [LOW] · Confidence: High

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/TweetDao.kt:155-156`, screen call sites `AllBookmarksScreen.kt:88-103`, `TwitterBookmarksScreen.kt:80-87`, `RedditBookmarksScreen.kt:71-78`

**Issue:** The batch query

```kotlin
@Query("SELECT tweetId, tagName FROM tweet_tags WHERE tweetId IN (:tweetIds)")
suspend fun getTagsForTweets(tweetIds: List<String>): List<TweetTagCrossRef>
```

is called from each screen's `LaunchedEffect(itemIds)`. The `itemIds` list is built from the paged snapshot:

```kotlin
val itemIds = remember(pagedBookmarks?.itemCount) {
    val count = pagedBookmarks?.itemCount ?: 0
    (0 until count).mapNotNull { pagedBookmarks?.peek(it)?.tweet?.id }
}
```

`pagedBookmarks?.itemCount` grows as the user scrolls — every page (20 items) appended bumps `itemCount`, triggers the `remember` re-derivation, and fires a fresh batch query with the entire accumulated snapshot. Once a user has scrolled past page 50 (1 000 items loaded into the Pager's in-memory cache), the query sends 1 000 bind parameters.

**Android/SQLite bind-variable limit:** Android-bundled SQLite (`androidx.sqlite`) compiles with `SQLITE_MAX_VARIABLE_NUMBER=999` historically. AOSP raised it to 32 766 on API 32+, but older devices (API 28-31, which are still common) will throw `SQLiteException: too many SQL variables` once `tweetIds.size > 999`.

**Severity rationale:** LOW because:
- The current bookmark count is small (~hundreds, per `Repository.BUFFER = 250`).
- The throw is recoverable via try/catch and degrades gracefully (tags don't render).

But it will eventually bite at scale on older devices.

**Fix:** Chunk the IN-clause at the repository boundary:
```kotlin
override suspend fun getTagsForItems(ids: List<String>): Map<String, List<String>> {
    if (ids.isEmpty()) return emptyMap()
    return ids.chunked(900)
        .flatMap { tweetDao.getTagsForTweets(it) }
        .groupBy({ it.tweetId }, { it.tagName })
}
```

Also worth gating on visible-window IDs rather than the entire Pager snapshot — see R2-PERF-05.

---

### R2-PERF-05 — Per-page-snapshot tag refetch redundantly re-queries already-loaded ids [LOW] · Confidence: Med

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt:89-103`, `feature/twitter/.../TwitterBookmarksScreen.kt:81-87`, `feature/reddit/.../RedditBookmarksScreen.kt:72-78`

**Issue:** Each `LaunchedEffect(itemIds)` keys on the full id-list. Every time Paging3 appends a page, `itemCount` jumps (20 → 40 → 60 → ...) and the effect refires with the *entire accumulated* list, not just the newly appended ids. So scrolling 10 pages issues 10 batch queries of sizes 20, 40, 60, ..., 200 — total ≈ 1 100 ids fetched across 10 round-trips, when only 200 unique ids exist.

This is correctness-equivalent (the VM merges by key) but the redundant `groupBy` allocations and DB round-trips compound as the user scrolls.

**Severity rationale:** LOW. Each batch query is fast (< 5 ms on a warm SQLite), and `_tagsForTweet.value = _tagsForTweet.value + batch` is idempotent. Worth flagging because the round-1 fix optimised the *per-item* N+1 but accidentally created a *per-page-snapshot* O(pageCount × pageSize) pattern that the IN-clause query masks well.

**Fix:** Track the previously-batched set and only fetch the delta:
```kotlin
val fetchedIds = remember { mutableStateOf(emptySet<String>()) }
val itemIds = ...
LaunchedEffect(itemIds) {
    val delta = itemIds - fetchedIds.value
    if (delta.isNotEmpty()) {
        onLoadTagsForIds(delta.toList())
        fetchedIds.value = fetchedIds.value + delta
    }
}
```
Or move tag-fetch into `PagingSource` itself via `@Relation` (originally suggested in PERF-01 round 1, fix 3) — that eliminates this whole class of issues.

---

## Specific Concerns From The Brief

### Coil memory cache at 20% — appropriate?

**Verdict:** Yes. Coil's documented default is 25%; 20% is conservative and reasonable for a list-heavy app sharing memory with `Compose` snapshot state, Paging3 caches, and Room's page cache. The bookmark feed thumbnails decode at the displayed 200dp height (Coil downsamples automatically) so even 100 visible tiles fit comfortably under 20% of process memory budget on a 256 MB heap. **No change needed.**

### Coil disk cache at 2% — too small?

**Verdict:** Borderline. On modern devices with 10+ GB free, 2% = 200 MB which is fine. On a budget device with 1 GB free, 2% = 20 MB which is ~80 thumbnails before eviction. See R2-PERF-03 for a `maxSizeBytes` floor suggestion.

### IN-clause parameter limit on Android SQLite

**Verdict:** Real risk at scale on API < 32. See R2-PERF-04. Today's bookmark counts are small so the bug is latent, but the screen-level call passes the *entire* loaded snapshot rather than a per-page delta, which means the parameter count grows monotonically with scroll depth.

### Room migrations on main thread at startup?

**Verdict:** Not on main thread in this codebase. Room runs migrations on whatever thread first touches `getOpenHelper().writableDatabase`. Both repositories trigger first access via `init { scope.launch(Dispatchers.IO) { … tweetDao.getLatestBookmark() … } }` (`feature/twitter/.../Repository.kt:62-76` and the analogous Reddit init). The `CoroutineModule` provides a single-threaded `Dispatchers.IO`-backed scope, and the singleton DB injection is lazy. So MIGRATION_6_7 (two `CREATE INDEX` statements) and MIGRATION_7_8 (two more index creates) execute on the IO pool. On a large DB (>10k tweetEntity rows) the index build is ~50-200 ms — measurable but off the main thread, so no first-frame jank.

**Caveat:** if anything in `app/di/` ever changes to read the DB *synchronously* during Hilt graph creation (e.g., a `@Provides` that does `db.tweetDao().getAll()`), migrations would block whatever thread that runs on. Worth a comment in `DatabaseModule.provideAppDatabase` warning future authors to keep DB access lazy.

### PERF-06 deferral rationale correctness

**Verdict:** Rationale is incorrect; see R2-PERF-02. The runtime impact is small enough that the deferral *decision* remains acceptable, but the explanation in `07-review.md:259` should be corrected.

### Firestore paged cursor (3512352) × existing-tweet upload check (32e01af) under concurrent syncs

**Verdict:** Safe in single-process, single-`Repository`-singleton execution.

- `Repository` is `@Singleton` and `fetchMutex.tryLock()` collapses parallel `buildDatabase()` calls into one in-flight sync.
- `FirestoreRepository.uploadTweet` uses `tweetRef.get().await()` then `batch.set(... SetOptions.merge())`. If two `uploadTweet(sameId)` calls *do* race (e.g., via concurrent `saveTweetEntities`), both will see `isFirstWrite = !existingSnapshot.exists()`. If they race on a *new* tweet:
  1. Both see `!exists()` → both treat themselves as first write → both fan out sub-collections (users/metrics/media/includes/text-annotations).
  2. Sub-collection writes use `db.collection(...).document()` which auto-generates a doc id, so both writes succeed → **child documents are duplicated**.
  3. The parent tweet doc uses the deterministic `tweetId` key + `SetOptions.merge()` so it collapses correctly.

This is a real read-modify-write race in the *child* collections, but the `fetchMutex` in `Repository` plus the fact that incremental sync only uploads new tweets (those past `latestIdInDb`) means in practice the two callers can't be on the same id. Worth a callout in code comments rather than a finding because the existing per-tweet `Repository` mutex protects the *Twitter sync* path; the Firestore `uploadTweet` is only called from inside `saveTweetEntities`, which is invoked under `fetchMutex.withLock`.

`getAllTweetIds` (the paged cursor) is read-only and idempotent; concurrent calls produce identical Sets so the page interaction is benign.

---

## Performance Health Summary

| Axis | Round 1 | Round 2 |
|---|---|---|
| Algorithm complexity | PASS | PASS |
| Database efficiency | WARN | **PASS** — `order` indexes added; batch tag query; tombstone snapshot |
| Memory management | PASS | PASS (Coil now bounded) |
| I/O operations | WARN | **PASS** — blocking DAO eliminated; sync prefetches tombstones once |
| Caching strategy | PASS | PASS |
| Compose recomposition | WARN | WARN (PERF-06 deferral rationale wrong; runtime cost low — see R2-PERF-02) |
| Paging / LazyColumn | PASS | WARN (R2-PERF-04, R2-PERF-05 — snapshot-wide IN refetch on scroll) |
| Font loading | WARN | WARN (no change since R1 — PERF-08 still open) |
| Coil image loading | WARN | **PASS** (custom ImageLoader; R2-PERF-03 is a tunable LOW) |
| Cold start / splash | WARN | WARN (no change since R1 — PERF-09 still open) |

## Quick Wins (Round 2)

1. **R2-PERF-04** — chunk `getTagsForItems` at 900 ids per call (3 line change in `Repository.getTagsForItems`; prevents future `SQLiteException` on API < 32 at scale).
2. **R2-PERF-05** — only fetch tags for newly-appended ids (delta tracking in each screen's `LaunchedEffect`; eliminates redundant batch queries during scroll).
3. **R2-PERF-02** — correct the deferral note for PERF-06 in `07-review.md` so future maintainers don't re-derive the wrong rationale.
4. **R2-PERF-01** — decide on Reddit tag strategy. Either remove the affordance (one-line UI toggle) or add a `core/data/bookmark_tags` table with composite `(bookmarkId, source, tagName)` PK to break the implicit `tweet_tags`-only coupling. The Reddit chip-toggle is currently a "clear results" button under the user's nose.

## Merge Recommendation

**APPROVE_WITH_COMMENTS** — round-1 perf fixes land correctly. R2-PERF-01 (Reddit tag persistence broken) is the most surprising finding and is technically a correctness regression hiding inside a perf fix; consider triaging it as MED-Fix in the next implement pass. The remaining round-2 findings (R2-PERF-02..05) are improvements rather than blockers.

*Round 2 review completed: 2026-05-18*
