---
review: architecture
round: 2
scope: slug-wide (git diff main...HEAD)
slug: brutalist-redesign
completed: 2026-05-18
---

# Architecture Review Round 2 — brutalist-redesign

**Scope:** Validate round-1 architecture fix claims and surface any new architectural issues introduced by the fixes themselves.
**Date:** 2026-05-18

## Summary

Round-1 BLOCKER/HIGH architecture findings B1, H11, H12, CS-10, and MAINT-05 are correctly addressed at the surface they targeted. However:

1. The B1 fix introduces a **semantic boundary violation**: `TagRepository` was extracted to `core/data` as an interface, but the Hilt `@Binds` wires it to the Twitter `Repository` for every consumer including Reddit. Reddit tag CRUD now writes into the Twitter-owned `tweet_tags` cross-ref table, whose FK references `tweetEntity(id) ON DELETE CASCADE`. Tagging any Reddit post should trip the foreign-key constraint at runtime. This is a *worse* coupling than the round-1 cross-feature ViewModel import, because it is invisible at the Gradle module graph but produces a runtime data-integrity failure.
2. **H23 is regressing.** Twitter `Repository` grew from 241 → 325 lines. The new `refreshTokenSingleFlight` adds an 8th responsibility (mutex-guarded OAuth refresh + token persistence) inside the same class. The Repository now also implements `TagRepository`, so its public surface explicitly fans out to a new consumer (Reddit) without an interface seam.
3. **H24 is regressing.** `DatabaseModule` in `app/di` grew by a third migration in this round (`MIGRATION_5_6`) plus two more landed in MED bundles (`MIGRATION_6_7`, `MIGRATION_7_8`). All five migrations and the entity list in `AppDatabase` now live in `app/` despite the schema being almost entirely feature-owned.

These two architectural debts are explicitly deferred per round-1 triage, but should be flagged as *actively growing* rather than stable. Round-2 also surfaces three new MED findings: bus location, all-bookmarks tag routing, and the new dual-Repository token-refresh logic that should likely be shared.

**Severity Breakdown:**
- BLOCKER: 0
- HIGH: 1 (R2-ARCH-001 — TagRepository binding produces FK violation for Reddit)
- MED: 4 (Repository growth, bus location, AllBookmarks tag routing, duplicated refresh single-flight)
- LOW: 1 (DatabaseModule migration list ownership creep)

**Key Metrics:**
- Round-1 BLOCKER architecture findings closed: 1/1 (B1 surface, but see R2-ARCH-001)
- Round-1 HIGH architecture findings closed at this layer: 2/2 (H11, H12)
- God-object Repository line count: 241 → 325 (+35%)
- DatabaseModule migration count: 2 → 5
- Cross-feature compile-time imports: 0 (clean)

---

## Validation of Round-1 Claimed Fixes

### B1 — feature/reddit → feature/twitter ViewModel dependency [PASS at module level, FAIL at semantic level]

**Module-level evidence (PASS):**
- `feature/reddit/build.gradle:53-56` lists only `core/pref`, `core/models`, `core/designsystem`, `core/data` — no `feature/twitter` dep.
- `Grep "com.github.jayteealao.twitter"` over `feature/reddit/` returns no matches.
- `feature/reddit/.../RedditViewModel.kt:38-44` owns its own `tagsForTweet` / `allTags` StateFlows backed by an injected `TagRepository`.

**Semantic-level evidence (FAIL — see R2-ARCH-001):**
The new `TagRepository` interface in `core/data` is correct as an abstraction, but the Hilt binding (`app/di/TagRepositoryModule.kt:17`) wires every `TagRepository` injection point to the Twitter `Repository` — the same class that owns the Twitter `TweetDao`. RedditViewModel's `tagRepository.saveTags("redditPostId", ...)` calls into Twitter's `Repository.saveTags`, which inserts a `TweetTagCrossRef(tweetId = redditPostId)` row. The cross-ref table's FK is `FOREIGN KEY(tweetId) REFERENCES tweetEntity(id) ON DELETE CASCADE`. Reddit IDs are not present in `tweetEntity`, so the insert should fail with `SQLITE_CONSTRAINT_FOREIGNKEY` (Room enables FK enforcement by default; there is no override in `DatabaseModule.provideAppDatabase`).

### H11 — BookmarkSource duplication [PASS]

- `core/data/BookmarkSource.kt` is **deleted** (verified by file-not-found + `git log` showing the file last touched in commit `e97ee5f`).
- `Grep "import com.github.jayteealao.crumbs.data.BookmarkSource"` returns 0 source-file matches; the only hits are inside `.ai/workflows/` review artifacts referencing the old name.
- `SyncErrorEvent.source`, `BannerState.source`, and `SnackbarEvent.UndoableDelete.source` all now use the typed enum from `core/models`. `DeletedBookmarkRepository.softDelete/undoDelete/isDeleted/deletedIdsSnapshot` take `BookmarkSource` and convert to lowercase strings at the Room boundary.
- `HomeRoute.kt:107` and `:115` use `BookmarkSource.Twitter` / `BookmarkSource.Reddit` enum cases; the `when` arms over the bus events are exhaustive.

### H12 — DeletedBookmarkRepository UI surface [PASS]

- `core/data/SnackbarBus.kt` exists as a `@Singleton` mirroring `SyncErrorBus` (`@Inject constructor`, `MutableSharedFlow` with `replay = 0`, `extraBufferCapacity = 16`, `DROP_OLDEST`, `suspend fun emit`).
- `core/data/DeletedBookmarkRepository.kt` now declares only persistence-facing methods (`softDelete`, `undoDelete`, `isDeleted`, `deletedIdsSnapshot`, `deletedIds`); its private `_events: MutableSharedFlow<SnackbarEvent>` field and the public `events: SharedFlow<SnackbarEvent>` property are gone. `softDelete` delegates to `snackbarBus.emit(...)`.
- `HomeRoute.kt:127` subscribes to `services.snackbarBus.events` directly via the new `HomeServicesViewModel`, not via the repository.

### CS-10 — derivedStateOf in HomeRoute [PASS, correctly applied]

- `HomeRoute.kt:82-100` wraps **two** computed values in `derivedStateOf`:
  - `activeFilter` — resolves the active tab's filter from one of `twitterFilter`/`redditFilter` via a `when`.
  - `activeBanner` — same pattern for `twitterBanner`/`redditBanner`.
- Both are read-only derivations over inputs (one mutable state field `selectedTab` + two collected StateFlows). The pattern collapses transitive recompositions correctly: a Reddit filter change cannot invalidate readers of `activeFilter` while the Twitter tab is selected.
- The two `mutableStateOf<BannerState?>` (twitterBanner/redditBanner) and `selectedTab` are still plain `mutableStateOf` (correctly — they are write targets). `derivedStateOf` is not misapplied to those.

### MAINT-05 — RedditPostData.toBookmark dedup [PASS]

- `feature/reddit/.../RedditBookmarksScreen.kt:244-273` owns the canonical `RedditPostData.toBookmark` extension.
- `app/.../AllBookmarksScreen.kt:42-44` imports `com.github.jayteealao.reddit.screens.toBookmark` and additionally `com.github.jayteealao.twitter.screens.toBookmark` for the Twitter side.
- The previous in-file duplicate of `RedditPostData.toBookmark` in `AllBookmarksScreen.kt` has been removed; the comment block at lines 314-317 documents the deletion and points to the single source of truth.

---

## New Findings

### R2-ARCH-001 — TagRepository Hilt binding ties Reddit tag CRUD to Twitter-owned tables [HIGH]
**Severity:** HIGH | **Confidence:** High

**Location:**
- `app/src/main/java/com/github/jayteealao/crumbs/di/TagRepositoryModule.kt:13-17`
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditViewModel.kt:43, 169-196`
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:239-281`
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditDao.kt:51-61` (consumer side)

**Issue:**
B1's fix extracted `TagRepository` as an interface in `core/data`, which is the right abstraction. However, the only `@Binds` provider in the codebase wires `TagRepository` to the Twitter `Repository` class. Reddit injects the interface and receives a Twitter Repository instance. Every Reddit tag operation flows through `Repository.saveTags(id, tags)` → `addTagToTweet(id, tagName)` → `tweetDao.insertTweetTag(TweetTagCrossRef(redditId, tagName))`.

`TweetTagCrossRef` is defined with `FOREIGN KEY(tweetId) REFERENCES tweetEntity(id) ON DELETE CASCADE` (see `MIGRATION_3_4` at `DatabaseModule.kt:65-74`). Reddit post IDs are not stored in `tweetEntity`. Room enables FK enforcement by default and `DatabaseModule.provideAppDatabase` does not override it. The insert should throw `android.database.sqlite.SQLiteConstraintException: FOREIGN KEY constraint failed` at runtime the first time a user taps **Save** in the Reddit tag editor.

The Reddit feed query already reads from this same `tweet_tags` table via `RedditDao.getPostsByTagsTombstoneAware` (`feature/reddit/.../RedditDao.kt:55`), so the on-disk schema is shared *by design* — the intention is to use one tag table across both sources. But the FK constraint inherited from migration v3→v4 makes the writes unreachable for Reddit.

**Evidence:**
```kotlin
// app/di/TagRepositoryModule.kt
@Binds @Singleton
abstract fun bindTagRepository(impl: Repository): TagRepository   // Twitter Repository

// feature/reddit/.../RedditViewModel.kt:190-196
fun saveTags(id: String, tags: List<String>) {
    viewModelScope.launch {
        tagRepository.saveTags(id, tags)   // -> Twitter Repository
        loadTagsForTweet(id)
        loadAllTags()
    }
}

// feature/twitter/.../Repository.kt:239-244 (override in TagRepository impl)
override suspend fun addTagToTweet(tweetId: String, tagName: String) {
    tweetDao.insertTag(TagEntity(tagName))
    tweetDao.insertTweetTag(TweetTagCrossRef(tweetId, tagName))  // FK to tweetEntity
}

// app/di/DatabaseModule.kt:65-74 (MIGRATION_3_4)
FOREIGN KEY(`tweetId`) REFERENCES `tweetEntity`(`id`) ON DELETE CASCADE
```

**Impact:**
- Reddit tag *save* path is broken at runtime (FK violation throws).
- Even if FK enforcement were disabled, semantic coupling is unsound: deleting the only Twitter row that happens to share an ID with a Reddit post (impossible today because IDs are differently shaped, but the type system permits it) would cascade-delete a Reddit-only tag row.
- The B1 fix moved the *compile-time* cross-feature coupling into a *runtime* one. Architecturally this is a regression for testability: the interface in `core/data` falsely advertises a source-agnostic capability that the only implementation cannot satisfy.

**Fix (any one):**
1. **Drop the `tweet_tags` FK to `tweetEntity` in a new migration** and rename it to `bookmark_tags` (composite ref keyed by `(bookmarkId, source)` mirroring R1's `deleted_bookmarks` fix). The DAO + repository move to `core/data`.
2. **Split `TagRepository` into `TweetTagRepository` (in feature/twitter) and `RedditTagRepository` (in feature/reddit)**, each owning its own cross-ref table. Lose the interface; each ViewModel injects its concrete repo. Trades the abstraction for honesty.
3. **Promote the tag DAO + storage to core/data** behind the existing interface and let it own a `bookmark_tags` table with composite PK. Twitter and Reddit lose tag DAO methods.

Option 3 is the architecturally cleanest and pairs naturally with the H24 follow-up.

**Refactoring steps for option 3:**
1. Add new entity `BookmarkTagCrossRef(bookmarkId, source, tagName)` in `core/data`.
2. Add migration vN→vN+1 to copy `tweet_tags` rows into the new table with `source = 'twitter'` and drop the old table.
3. Move `getTagsForTweet`, `getTagsForTweets`, `getAllTags`, `insertTweetTag`, `deleteTweetTag` from `TweetDao` into a new `BookmarkTagDao` in `core/data`.
4. Implement `TagRepository` in `core/data` using the new DAO; remove the `TagRepository` override from `Repository`.
5. RedditViewModel + BookmarksViewModel continue to inject `TagRepository` unchanged.

---

### R2-ARCH-002 — Twitter Repository continues to accrete responsibilities (H23 regressing) [MED]
**Severity:** MED | **Confidence:** High

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt`

**Issue:**
The H23 deferral was justified as "multi-week refactor — captured for follow-up workflow." Round-2 fixes added net responsibilities to the same class rather than holding it stable:

- **+ `refreshTokenSingleFlight` (lines 283-316).** Mutex-guarded OAuth token refresh + Prefs persistence. Pulled from `LoginViewModel` into the Repository so the sync loop can recover silently. This is the 8th distinct responsibility (was 7 in round 1) and the `refreshMutex` is now a *second* mutex inside the same class (after `fetchMutex`).
- **+ `TagRepository` interface implementation (lines 239-281).** Repository explicitly fans out to Reddit consumers via this interface, broadening fan-in.
- **+ `_isRefreshing` StateFlow exposure (lines 56-57).** UI-observable state surface added; `BookmarksViewModel.isRefreshing` (line 38) just re-exports it.

Line count grew **241 → 325 (+35%)**. Constructor parameter count stayed at 8 but the public surface widened.

The H23 ticket noted: "Testing sync logic requires wiring up OAuth, Firestore, and tag storage." That blast radius is now *larger* — testing sync also requires wiring the token-refresh mutex and the Reddit-facing interface impl.

**Evidence:**
```kotlin
@Singleton
class Repository @Inject constructor(
    private val tweetDao: TweetDao,           // 1. DAO
    private val authPref: Prefs,              // 2. Prefs
    private val twitterApiClient: TwitterApiClient,
    private val twitterAuthClient: TwitterAuthClient,
    private val firestoreRepository: FirestoreRepository,
    private val deletedBookmarkRepository: DeletedBookmarkRepository,
    private val syncErrorBus: SyncErrorBus,
    private val scope: CoroutineScope
) : TagRepository {                            // 3. Tag interface impl (NEW)
    private val fetchMutex = Mutex()           // 4. Sync mutex
    private val refreshMutex = Mutex()         // 5. Refresh mutex (NEW)
    private val _isRefreshing = MutableStateFlow(false)  // 6. UI state (NEW)
    // + Firestore sync, OAuth refresh, paging factories, tombstone gating
}
```

**Impact:**
Each round-2 fix that touched this file added at least one new field or method. The H23 follow-up workflow's surface area is now substantially larger than when the deferral was made. Recommend capturing this as an updated H23 scope note before the follow-up workflow begins.

**Fix:**
Acknowledge in the H23 follow-up scope:
- Extract `TwitterTokenRefreshService` to encapsulate `refreshTokenSingleFlight` + `refreshMutex`. Inject into both `Repository` and `LoginViewModel`.
- Pair the H23 split with R2-ARCH-001 fix option 3 to remove the `TagRepository` impl from `Repository` as part of the same refactor.

---

### R2-ARCH-003 — SyncErrorBus and SnackbarBus live in core/data despite being UI-event channels [MED]
**Severity:** MED | **Confidence:** High

**Location:**
- `core/data/src/main/java/com/github/jayteealao/crumbs/data/SyncErrorBus.kt`
- `core/data/src/main/java/com/github/jayteealao/crumbs/data/SnackbarBus.kt`
- `core/data/src/main/java/com/github/jayteealao/crumbs/data/SnackbarEvent.kt`
- `core/data/src/main/java/com/github/jayteealao/crumbs/data/SyncErrorEvent.kt`

**Issue:**
Both buses are `@Singleton` classes whose sole purpose is to surface state to the UI: `SyncErrorEvent` produces banner copy, `SnackbarEvent.UndoableDelete` produces a snackbar with an undo action. Their consumers are exclusively in `app/.../HomeRoute.kt`. The producers are the feature repositories, which inject from `core/data` correctly. But the events and buses themselves are presentation concerns: the very same round-1 finding ARCH-008 flagged `BannerState` in `core/data` as a layer violation. SyncErrorBus and SnackbarBus are the same pattern.

The round-1 ARCH-008 finding (`BannerState` in `core/data`) was triaged "Defer" in the MED bundle. R2 makes the case stronger because two more presentation-only types (the buses) now also live in the data module.

**Evidence:**
```kotlin
// core/data/SnackbarEvent.kt - data layer types modeling UI events
sealed interface SnackbarEvent {
    data class UndoableDelete(val id: String, val source: BookmarkSource) : SnackbarEvent
}

// core/data/SyncErrorEvent.kt - "Twitter session expired" is UI copy
data class TwitterAuth401(val message: String = "Twitter session expired") : SyncErrorEvent
```

`SnackbarEvent` only has one case (`UndoableDelete`) and it exists solely to drive `snackbarHostState.showSnackbar(...)` in HomeRoute. `SyncErrorEvent` is closer to a domain event (auth failure) but its only consumer maps it to `BannerState` for display.

**Impact:**
- Cross-cutting "events" module pulls UI semantics into `core/data`.
- Testing the data layer in isolation requires the UI-event types, even though no data-layer operation reads from the bus (only writes).
- `core/data` cannot be reused by a non-Compose presentation (e.g. a Wear OS surface) without dragging the Snackbar/Banner notion along.

**Fix:**
Move both buses and their event types to a new `core/ui-events` module (or `app/events/`). `core/data` keeps a thin one-way write port:

```kotlin
// core/data/UiEventSink.kt
interface UiEventSink {
    fun emitSyncError(event: SyncErrorEvent)
    fun emitSnackbar(event: SnackbarEvent)
}
```

Implementations live in the UI-events module, Hilt-bound to the sink in `app/di`. Repositories inject the sink, not the bus. This also enables headless tests of repositories with a no-op sink.

---

### R2-ARCH-004 — `refreshTokenSingleFlight` duplicated across Twitter and Reddit repositories [MED]
**Severity:** MED | **Confidence:** High

**Location:**
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:283-316`
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:219-238`

**Issue:**
The MED-bundle fix (commit `d417330`) added `refreshTokenSingleFlight` to both Twitter and Reddit repositories. The two implementations are structurally identical: `tryLock()` → call provider-specific `refreshAccessToken(token)` → if non-null write back via prefs → `unlock`. Each carries its own `refreshMutex: Mutex`.

This is a shotgun-surgery seam: a future fix to the single-flight semantics (e.g. timeout, backoff, exponential retry) must be edited in two places. The two providers differ only in (a) which `AuthClient` to call and (b) which `Prefs` to write to.

**Evidence (paraphrased side-by-side):**
```kotlin
// Twitter
private suspend fun refreshTokenSingleFlight(currentRefreshToken: String): Boolean {
    if (!refreshMutex.tryLock()) return true
    return try {
        val tokenResponse = twitterAuthClient.refreshAccessToken(currentRefreshToken)
        val access = tokenResponse?.accessToken
        val refresh = tokenResponse?.refreshToken
        if (!access.isNullOrBlank() && !refresh.isNullOrBlank()) {
            authPref.setAccessAndRefreshToken(access, refresh); true
        } else false
    } catch (e: Exception) { false } finally { refreshMutex.unlock() }
}

// Reddit — same shape, different types
```

**Impact:**
- Drift risk: a Twitter-only fix can leave Reddit broken.
- Test surface doubles: each repo needs its own concurrent-401-storm test.

**Fix:**
Extract a generic single-flight helper to `core/data` (or a new `core/auth`):

```kotlin
// core/data/SingleFlight.kt
class SingleFlight {
    private val mutex = Mutex()
    suspend fun <T> run(block: suspend () -> T?): T? {
        if (!mutex.tryLock()) return null  // caller treats null as "deferred to peer"
        return try { block() } finally { mutex.unlock() }
    }
}
```

Or, more directly, a `TokenRefresher` interface with a `Twitter` and `Reddit` impl, both backed by a shared abstract base class that owns the mutex. Pairs naturally with the H23 follow-up.

---

### R2-ARCH-005 — AllBookmarksRoute routes Reddit tag CRUD through Twitter ViewModel [MED]
**Severity:** MED | **Confidence:** High

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt:235-236, 259-260, 307`

**Issue:**
`AllBookmarksRoute` shows both Twitter and Reddit bookmarks. When the user long-presses a Reddit card and chooses TAG, the tag editor opens with tags sourced from `bookmarksViewModel.tagsForTweet` (Twitter VM) and `bookmarksViewModel.allTags`. Saving invokes `bookmarksViewModel.saveTags(...)` regardless of `bookmark.source`. This mirrors the R2-ARCH-001 problem at a different layer: even with the FK issue fixed, the route always asks the Twitter VM to handle Reddit tags.

Delete is correctly source-routed (lines 289-291) — the same pattern was not applied to tag save.

**Evidence:**
```kotlin
// AllBookmarksScreen.kt:235-236
val tagsMap by bookmarksViewModel.tagsForTweet.collectAsState()  // Twitter-only
val allTags by bookmarksViewModel.allTags.collectAsState()

// AllBookmarksScreen.kt:259-260
onLoadTags = { id -> bookmarksViewModel.loadTagsForTweet(id) },       // Twitter
onLoadTagsForIds = { ids -> bookmarksViewModel.loadTagsForItems(ids) },// Twitter

// AllBookmarksScreen.kt:289-291 (delete is source-aware)
when (bookmark.source) {
    BookmarkSource.Twitter -> bookmarksViewModel.softDelete(bookmark.id)
    BookmarkSource.Reddit  -> redditViewModel.softDelete(bookmark.id)
}

// AllBookmarksScreen.kt:307 (save tags is NOT source-aware)
onSave = { tags ->
    bookmarksViewModel.saveTags(lps.bookmark!!.id, tags.toList())  // always Twitter
    lps.dismiss()
}
```

**Impact:**
- Inconsistent behavior between same screens (delete works, tag save silently misroutes).
- Reddit tags entered from the All screen never appear in the Reddit tab and vice versa (until R2-ARCH-001 is fixed at the storage layer).

**Fix:**
Route by source like delete already does. Better: collapse tag state to a single source via R2-ARCH-001 option 3 (shared `core/data` tag store) so this route's tag UI does not need to multiplex by source at all.

```kotlin
onSave = { tags ->
    when (lps.bookmark!!.source) {
        BookmarkSource.Twitter -> bookmarksViewModel.saveTags(lps.bookmark!!.id, tags.toList())
        BookmarkSource.Reddit  -> redditViewModel.saveTags(lps.bookmark!!.id, tags.toList())
    }
    lps.dismiss()
}
```

---

### R2-ARCH-006 — DatabaseModule migration list growth (H24 regressing) [LOW]
**Severity:** LOW | **Confidence:** High

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/di/DatabaseModule.kt`

**Issue:**
The H24 deferral noted that `AppDatabase` living in `app/` means `core/data` cannot be used without `app/` providing the DB. Round-2 fixes added **three more migrations** to `DatabaseModule.kt`:

- `MIGRATION_5_6` (this round) — composite PK on `deleted_bookmarks` (a `core/data` entity).
- `MIGRATION_6_7` (MED bundle) — feed-order indexes on `tweetEntity` and `reddit_posts`.
- `MIGRATION_7_8` (MED bundle) — FK indexes on `pollIds` and `mediaKeys`.

`MIGRATION_5_6` is the most striking: it migrates a `core/data` entity (`DeletedBookmark`) but the migration code lives in `app/di/DatabaseModule.kt`. `core/data` now has a runtime requirement that only `app` can satisfy.

**Evidence:**
```kotlin
// DatabaseModule.kt:126
.addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)

// DatabaseModule.kt:185-204 — migrates a core/data entity from app/
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // recreate deleted_bookmarks (a core/data entity) with composite PK
        ...
    }
}
```

**Impact:**
LOW today (correctness is fine) but the H24 follow-up scope has expanded: relocating `AppDatabase` to `core/database` now also requires extracting six migration definitions, three of which touch entities owned by different modules. The longer this deferral runs, the more migrations accrete in the wrong layer.

**Fix (H24 follow-up):**
Co-locate each migration with the module that owns the modified entity:
- `MIGRATION_4_5`, `MIGRATION_5_6` → `core/data` (touches `deleted_bookmarks`).
- `MIGRATION_2_3`, `MIGRATION_3_4` (tags), `MIGRATION_6_7` (`tweetEntity.order`), `MIGRATION_7_8` (`pollIds`/`mediaKeys`) → `feature/twitter` (or shared `core/database`).
- Reddit migration → `feature/reddit`.
- A `core/database` module assembles the list and provides the DB.

---

## Specific Concerns From Reviewer Brief

### "Does TagRepository leak feature-specific types via the BookmarkSource enum?"
**No.** The interface signature is:
```kotlin
interface TagRepository {
    suspend fun getTagsForTweet(id: String): List<String>
    suspend fun getTagsForItems(ids: List<String>): Map<String, List<String>>
    suspend fun getAllTags(): List<String>
    suspend fun saveTags(id: String, tags: List<String>)
    suspend fun addTagToTweet(id: String, tagName: String)
    suspend fun removeTagFromTweet(id: String, tagName: String)
}
```
No `BookmarkSource` in the interface; only `String` IDs and tag names. The interface itself is clean.

However, method names (`getTagsForTweet`, `addTagToTweet`) **leak the original Twitter origin** as a naming convention — they take a Reddit ID just fine but read as Twitter-specific. Minor (NIT-level) — rename `*Tweet` → `*Bookmark` for clarity if the R2-ARCH-001 refactor is taken.

### "Were derivedStateOf wrappers applied to the right state?"
**Yes.** `activeFilter` and `activeBanner` are pure derivations over `selectedTab` + collected StateFlows. The mutable state (`twitterBanner`, `redditBanner`, `selectedTab`) is left as plain `mutableStateOf` (correct). No state is obscured.

### "Is the God-object getting worse?"
**Yes.** Repository.kt: 241 → 325 lines. New responsibilities (refreshTokenSingleFlight, isRefreshing StateFlow, TagRepository implementation) added in round-2 fixes. See R2-ARCH-002.

### "Does the new migration code in DatabaseModule make H24 worse?"
**Yes, marginally.** Three new migrations were added in this round, one of which touches a `core/data` entity. See R2-ARCH-006.

### "Should SyncErrorBus and SnackbarBus live in a presentation/UI-events module?"
**Yes.** See R2-ARCH-003.

---

## Recommendations

### Immediate (HIGH)
1. **R2-ARCH-001** — Resolve TagRepository binding via shared core tag store OR split per source. Required before any flow that lets users tag Reddit posts ships.

### Should fix (MED)
2. **R2-ARCH-005** — Source-route tag save in `AllBookmarksRoute` (mirror the delete pattern). Quick win regardless of R2-ARCH-001 outcome.
3. **R2-ARCH-003** — Move `SyncErrorBus`/`SnackbarBus` + event types to a UI-events module; expose a write-only sink to `core/data`.
4. **R2-ARCH-004** — Extract shared single-flight token refresh helper. Pairs naturally with H23 follow-up.

### Note for H23/H24 follow-up workflows
5. **R2-ARCH-002** — H23 scope has grown. Updated checklist for the follow-up: extract sync, OAuth refresh (incl. R2-ARCH-004), tag impl (incl. R2-ARCH-001 option 3), Firestore backup, paging factories.
6. **R2-ARCH-006** — H24 follow-up must also move six migrations into the correct owning modules.

---

## Metrics

| Metric                            | Round 1 | Round 2 | Threshold | Status |
|-----------------------------------|---------|---------|-----------|--------|
| Cross-feature compile-time imports| 1       | 0       | 0         | PASS   |
| Duplicate type definitions        | 1       | 0       | 0         | PASS   |
| God objects (>5 responsibilities) | 1       | 1+      | 0         | FAIL (worse) |
| Layer violations (data ↔ UI events)| 1 (BannerState) | 3 (BannerState + 2 buses) | 0 | FAIL (worse) |
| Max constructor params            | 8       | 8       | <6        | WARN   |
| Repository.kt LOC                 | 241     | 325     | <500      | PASS   |
| DatabaseModule migrations in app/ | 2       | 5       | 0 (move to feature) | FAIL (worse) |
| Round-1 architecture findings closed | n/a  | 3/3 (B1, H11, H12) at surface; B1 has hidden semantic regression | | PARTIAL |

*Round 2 review completed: 2026-05-18*
