---
scope: slug-wide (git diff main...HEAD)
completed: 2026-05-18
---

# Performance Review — brutalist-redesign

**Verdict:** Ship with caveats
**Reviewed:** slug-wide / `git diff main...HEAD`
**Files changed:** ~115 source files, +8 372 / -2 014

## Findings (12 total)
BLOCKER: 0 | HIGH: 3 | MED: 5 | LOW: 3 | NIT: 1

---

## Critical (HIGH)

### PERF-01 — N+1 tag queries per visible list item [HIGH] · Confidence: High

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt:188` and `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt:123`

**Issue:** Every item rendered inside the paged `LazyColumn` fires a `LaunchedEffect(id) { onLoadTags(id) }`. That effect calls `BookmarksViewModel.loadTagsForTweet(id)`, which issues a separate `SELECT` against `tweet_tags` / `tags` for each tweet. With a page size of 20 this is 20 DB round-trips per page load on top of the paging query itself — a textbook N+1 pattern.

**Evidence:**
```kotlin
// AllBookmarksScreen.kt:188 (same pattern in TwitterBookmarksScreen.kt:123)
items(count = items.itemCount, key = items.itemKey { idOf(it) }) { index ->
    val item = items[index]
    if (item != null) {
        val id = idOf(item)
        LaunchedEffect(id) { onLoadTags(id) }   // <-- 1 DB query per item
        val tags = tagsMap[id] ?: emptyList()
        ...
    }
}
```

**Repository-level confirmation:**
```kotlin
// BookmarksViewModel.kt:98-102
fun loadTagsForTweet(tweetId: String) {
    viewModelScope.launch {
        val tags = repository.getTagsForTweet(tweetId)  // SELECT from DB
        _tagsForTweet.value = _tagsForTweet.value + (tweetId to tags)
    }
}
```

**Impact:** 20 extra queries per page = 21 total queries per scroll page. Each query is fast in isolation on SQLite but each one competes for the single WAL writer and wakes the DB thread. On a 500-item list fully scrolled = 500 extra DB hits. Also causes a tagsMap StateFlow update per item, which triggers recomposition of every item currently on screen observing tagsMap.

**Fix:**
1. Add a `@Query` that returns all tag rows for a `List<String>` of tweetIds in a single IN-clause query.
2. Batch-load tags for the current page once in `PagingSource.load()` or via a `Flow<Map<String,List<String>>>` backed by `SELECT * FROM tweet_tags WHERE tweetId IN (...)`.
3. Alternatively, embed tags in the `TweetData` `@Relation` so they are loaded in the existing `@Transaction` query — zero extra DB calls.

---

### PERF-02 — `isDeleted()` calls a blocking DB query from a non-DB coroutine context during sync [HIGH] · Confidence: High

**Location:** `core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkRepository.kt:33`, invoked from `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:95,186` and `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:110`

**Issue:** `DeletedBookmarkRepository.isDeleted()` calls `dao.existsBlocking()` — a synchronous Room query that Room runs without a coroutine dispatcher. This is called:
- Inside `syncFromFirestore()` in a `forEach` loop over up to N Firestore tweets.
- Inside `tweetEntitiesChannel.consumeEach { it.data.forEach { ... } }` during the incremental Twitter sync — one blocking call per tweet per page.
- Inside `buildDatabase()` for Reddit on every fetched post (up to 800 posts × 8 pages).

Although the callers are on `Dispatchers.IO`, the blocking query inside a tight `forEach` without batching turns the tombstone check into a serial loop of synchronous DB reads. For 800 Reddit posts this is 800 sequential blocking SELECT queries during the sync phase.

**Evidence:**
```kotlin
// DeletedBookmarkRepository.kt:33
fun isDeleted(id: String): Boolean = dao.existsBlocking(id)

// DeletedBookmarkDao.kt:19
fun existsBlocking(id: String): Boolean  // no suspend, no Flow — runs synchronously

// RedditRepository.kt:108-110 — called in a filter{} inside the response callback
.filter { !deletedBookmarkRepository.isDeleted(it.data.name) }   // N blocking queries
```

**Impact:** Serialises all tombstone checks during sync. With 800 posts it adds significant latency to the first-time sync path. If WAL mode is not enabled, each query acquires a shared lock.

**Fix:**
1. Before the sync loop, load tombstone IDs once into a `HashSet<String>`: `val tombstoned = deletedBookmarkRepository.deletedIds().first().toHashSet()`
2. Replace `isDeleted(id)` with `id !in tombstoned` (O(1) Set lookup, zero DB round-trips in the loop).
3. Make `isDeleted()` a `suspend` function using `suspend fun isDeleted(id: String): Boolean = withContext(Dispatchers.IO) { dao.existsBlocking(id) }` as a minimum if the batch-fetch approach is not immediately viable.

---

### PERF-03 — TypeFilter chip state is wired but filtering is never applied for Reddit paging [HIGH] · Confidence: High

**Location:** `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:160`

**Issue:** `RedditRepository.pagingPostsData(filter: FilterState)` accepts a `FilterState` but ignores it entirely — the `@Suppress("UNUSED_PARAMETER")` annotation is a clear marker. The `_filter` StateFlow drives `flatMapLatest { state -> redditRepository.pagingPostsData(state) }` in `RedditViewModel`, so users can toggle type chips (ALL, ARTICLES, VIDEOS, etc.) but Reddit never applies a WHERE clause. The pager always returns `getPostsTombstoneAware()` unfiltered.

**Evidence:**
```kotlin
// RedditRepository.kt:160
fun pagingPostsData(@Suppress("UNUSED_PARAMETER") filter: FilterState): Flow<PagingData<RedditPostData>> = Pager(
    config = PagingConfig(pageSize = 20),
    pagingSourceFactory = { redditDao.getPostsTombstoneAware() }  // filter ignored
).flow
```

**Impact:** This is a functional hole (UI filter chips have no effect on Reddit), but it also means the Pager is recreated on every filter state change (because `flatMapLatest` fires a new `Pager`), yet always produces the same unfiltered result. Each Pager recreation invalidates the current PagingSource and forces a full reload — wasted work on every chip toggle.

**Fix:**
1. Add filtered queries to `RedditDao` keyed on `is_video`, `is_self`, and `thumbnail IS NOT NULL` that map to the `TypeFilter` values.
2. Branch inside `pagingPostsData` the same way `Repository.pagingTweetData(filter)` does for Twitter (tag-filter branching in `TweetDao`).
3. If DB-side filtering is deferred, at minimum do not create a new `Pager` when filter changes have no effect — hold a stable `PagingSourceFactory` and only swap it when the filter actually changes behaviour.

---

## Medium Severity

### PERF-04 — `deleted_bookmarks` table has no explicit index on `bookmarkId` beyond the PK; LEFT JOIN in hot paging queries does a PK lookup but `tweetEntity.id` join side has no `order` index [MED] · Confidence: Med

**Location:** `app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/5.json` (schema definition)

**Issue (two parts):**

**Part A:** The tombstone-aware queries in `TweetDao` and `RedditDao` use:
```sql
SELECT t.* FROM tweetEntity t
LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId
WHERE t.referenced = 0 AND d.bookmarkId IS NULL
ORDER BY t.`order` DESC
```
The `ORDER BY t.order DESC` column has **no index** on `tweetEntity`. The schema only indexes `author_id`. SQLite will perform a full table scan + sort for every page load unless it can use the PK walk. For a table with thousands of tweets, this means each paging call re-sorts the full result set.

**Part B:** `deleted_bookmarks.bookmarkId` is the PRIMARY KEY (TEXT), which is implicitly indexed. The LEFT JOIN is therefore efficient for the tombstone lookup side. This is fine.

**Fix for Part A:**
```sql
CREATE INDEX IF NOT EXISTS index_tweetEntity_order ON tweetEntity (`order` DESC);
CREATE INDEX IF NOT EXISTS index_reddit_posts_order ON reddit_posts (`order` DESC);
```
Add these to MIGRATION_5_6 (or the next migration). This converts the `ORDER BY order DESC` from a full-scan sort to an index-range scan, improving every paging load.

---

### PERF-05 — Reddit `searchPosts` uses leading-wildcard LIKE on three unindexed text columns [MED] · Confidence: High

**Location:** `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditDao.kt:67-74`

**Issue:**
```sql
WHERE title LIKE '%' || :query || '%'
OR selftext LIKE '%' || :query || '%'
OR subreddit LIKE '%' || :query || '%'
```
Leading wildcards (`'%...'`) prevent any B-tree index from being used. All three columns are unindexed text. Every search performs a full table scan over `reddit_posts` reading every row's `title`, `selftext`, and `subreddit`. For 800+ posts each containing selftext that can be thousands of characters, this is a significant I/O and CPU cost per keystroke if search is debounced.

**Fix:** Create a `FTS4`/`FTS5` virtual table over `reddit_posts (title, selftext, subreddit)` and rewrite the query using `MATCH`. Room supports `@Fts4` / `@Fts5` entity annotations. Alternatively index `subreddit` (already done) and use exact match for subreddit with a separate title/selftext FTS table.

---

### PERF-06 — `tagsMap: Map<String, List<String>>` flows through unstable data class `TwitterBookmarksUiState` causing full recomposition on every tag load [MED] · Confidence: Med

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt:55-59`, `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt:59-63`

**Issue:** `TwitterBookmarksUiState` and `AllBookmarksUiState` are annotated `@Immutable` but contain `Map<String, List<String>>` (a stdlib mutable-capable interface). The Compose compiler cannot prove this is stable and will treat any change to tagsMap as requiring full recomposition of the composable consuming it. Because `_tagsForTweet` is updated on every `loadTagsForTweet(id)` call (once per visible item, per PERF-01), the entire screen recomposes after each single tag fetch. With 20 items visible, that is 20 recompositions per page scroll.

**Evidence:**
```kotlin
@androidx.compose.runtime.Immutable       // declared but Map is not provably stable
data class TwitterBookmarksUiState(
    val loggedIn: Boolean = false,
    val isRefreshing: Boolean = false,
    val tagsMap: Map<String, List<String>> = emptyMap(),  // unstable field
)
```

**Fix:**
1. Change `Map<String, List<String>>` to `ImmutableMap<String, ImmutableList<String>>` from `kotlinx.collections.immutable`. Then `@Immutable` is accurate.
2. Combined with fixing PERF-01 (batch-load tags), the number of tagsMap updates drops from N to 1 per page.

---

### PERF-07 — `AsyncImage` in `CrumbsBookmarkCard` has no `placeholder`, no `error` fallback, and no `crossfade` [MED] · Confidence: High

**Location:** `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBookmarkCard.kt:94-101`

**Issue:**
```kotlin
AsyncImage(
    model = mediaUrl,
    contentDescription = null,
    modifier = Modifier.fillMaxWidth().height(200.dp),
    contentScale = ContentScale.Crop,
)
```
No `placeholder` means a blank 200dp-tall space appears until the image loads, causing visible layout shift inside the `LazyColumn`. No `error` means a failed load leaves blank space forever. No `crossfade` means abrupt appearance. More critically for performance: without a `placeholder`, Coil still allocates the target bitmap memory eagerly, and multiple 200dp cards loading simultaneously on first scroll will spike memory. No custom `ImageLoader` is registered in the Application class, meaning Coil uses its default disk-cache size (10% of available disk space capped at 250 MB) — acceptable but untuned.

**Fix:**
```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(mediaUrl)
        .crossfade(true)
        .build(),
    contentDescription = null,
    placeholder = painterResource(R.drawable.placeholder_image),
    error = painterResource(R.drawable.error_image),
    modifier = Modifier.fillMaxWidth().height(200.dp),
    contentScale = ContentScale.Crop,
)
```

---

### PERF-08 — Font loading: no `preloadFonts` call; FunnelDisplay + IBM Plex Mono loaded on first text frame during cold start [MED] · Confidence: Med

**Location:** `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTypography.kt:13-22`

**Issue:** Four font files (`funnel_display_semibold.ttf`, `ibm_plex_mono_bold.ttf`, `ibm_plex_mono_medium.ttf`, `ibm_plex_mono_regular.ttf`) are bundled. The comment in the file acknowledges "Blocking loading matches the offline-rendering NFR." Font loading from `res/font` via `FontFamily(Font(...))` is synchronous on first measure, which happens on the main thread during the first composition after `setContent`. For the SplashScreen, which renders `typography.displayHeadline` ("crumbs•") on first frame, the FunnelDisplay font is loaded on the main thread during the splash, potentially adding 10-40ms to the first frame.

**Fix:** Call `PreloadFonts` at Application startup or in `MainActivity.onCreate()` before `setContent` using the `androidx.compose.ui.text.googlefonts` `EagerFontRequest` API or simply pre-load via `typeface` warming. Alternatively, since the fonts are small (< 200KB each), this is LOW priority if profiling confirms < 16ms total.

---

## Low Severity

### PERF-09 — Splash screen adds a hard `delay(1000)` on the coroutine driving navigation [LOW] · Confidence: High

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/SplashRoute.kt:22`

**Issue:**
```kotlin
LaunchedEffect(isAccessTokenAvailable) {
    delay(1000)   // unconditional 1-second hold
    ...navigate...
}
```
This is an unconditional 1-second delay before navigation regardless of device speed or token availability. On a fast device or returning user with cached token, the app is ready in < 200ms but artificially waits 1s. Combined with `LoginRoute.kt:38,41` adding another 500ms + 1500ms delays, first-run flows hold the user on static screens for up to 3 seconds of synthetic wait.

**Fix:** Replace with a `LaunchedEffect` that waits until meaningful state resolves (e.g., token read completes), then navigate without artificial delay. A short animation-driven delay (e.g., 300ms) for branding is acceptable but 1000ms is excessive for the hot path.

---

### PERF-10 — `pagingTweetData()` (no-filter overload) creates a Pager that is never used after the redesign [LOW] · Confidence: High

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:202-209`

**Issue:**
```kotlin
private val pager = Pager(
    config = PagingConfig(pageSize = 20)
) {
    tweetDao.getTweets()   // uses the non-tombstone-aware, non-filter query
}

fun pagingTweetData() = pager.flow
```
This `Pager` is instantiated at `Repository` construction time (singleton scope) but `BookmarksViewModel.pagingFlow` only calls `pagingTweetData(FilterState)`, never `pagingTweetData()`. The `getTweets()` query that backs this pager also uses `getTweets()` which is not tombstone-aware (PERF-11 risk if ever re-enabled). Dead code holding a live Pager object with its associated `CoroutineScope` and `Channel` resources.

**Fix:** Remove `private val pager` and `fun pagingTweetData()` (no-arg variant).

---

### PERF-11 — `Pager` is recreated on every `FilterState` emission even when filter hasn't changed behaviour [LOW] · Confidence: Med

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:212-222`

**Issue:** `BookmarksViewModel.pagingFlow` uses `flatMapLatest` on `_filter`, which calls `repository.pagingTweetData(state)` on every emission. That function always creates `new Pager(...)`. If `_filter` emits identical states (e.g., on ViewModel re-init or tab switch), a new `Pager` is created, invalidating the previous `PagingSource` and forcing a full page-1 reload.

**Fix:** Cache the last `FilterState` in `pagingTweetData` and return the existing Pager if the filter's paging-relevant parameters haven't changed. Or use `distinctUntilChanged()` on `_filter` before `flatMapLatest`.

---

## NIT

### PERF-12 — 115 Roborazzi golden files committed; test suite runtime grows O(N) with goldens [NIT] · Confidence: Med

**Location:** `core/designsystem/src/test/screenshots/` (97 files), `app/src/test/screenshots/` (14 files), `feature/*/src/test/screenshots/` (4 files)

**Note:** This is test infrastructure, not runtime performance. 115 Roborazzi goldens run as JVM unit tests on the host. The current count adds roughly 20-40 seconds to a local `./gradlew test` run (depends on hardware). As the number of component variants grows, this will scale linearly. Not a user-visible issue but worth monitoring. Consider grouping slow screenshot tests under a separate Gradle task (`screenshotTests`) that can be excluded from the default `check` lifecycle, running only in CI on PR push rather than on every local build.

---

## Performance Health Summary

| Axis | Status |
|---|---|
| Algorithm complexity | PASS |
| Database efficiency | WARN — missing `order` indexes; N+1 tag queries; unused filter in Reddit |
| Memory management | PASS (no leaks found; Coil cache untuned but not dangerous) |
| I/O operations | WARN — blocking `existsBlocked()` in sync loops; artificial navigation delays |
| Caching strategy | PASS (Pager + `.cachedIn(viewModelScope)` correct) |
| Compose recomposition | WARN — unstable `Map<>` in UiState; N+1 tagsMap updates drive unnecessary recomposition |
| Paging / LazyColumn | PASS (keys present, tombstone-aware queries correct) |
| Font loading | WARN (no preload; first frame blocks on font decode) |
| Coil image loading | WARN (no placeholder/error, no custom ImageLoader) |
| Migration SQL | PASS (v4→v5 is a simple CREATE TABLE, no full-table scan) |
| Cold start / splash | WARN (1000ms artificial delay before navigation) |

## Quick Wins (Best ROI)

1. **PERF-02** — Replace `isDeleted()` loop calls with a single `Set<String>` prefetch before sync (~15 min, eliminates up to 800 sequential DB queries per Reddit sync).
2. **PERF-01** — Embed tags in the `TweetData` `@Relation` or batch-load via IN-clause (eliminates 20 DB queries per page; also fixes recomposition thrash).
3. **PERF-04** — Add `index_tweetEntity_order` and `index_reddit_posts_order` to next DB migration (5 min, improves every paging load with ORDER BY).
4. **PERF-03** — Honour `FilterState.type` in `RedditRepository.pagingPostsData` (fixes silent UX bug; no unnecessary Pager churn).
5. **PERF-09** — Remove or reduce the `delay(1000)` in SplashRoute (1 min change, saves 1s per cold start).
