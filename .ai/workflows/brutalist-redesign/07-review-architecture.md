---
review: architecture
scope: slug-wide (git diff main...HEAD)
slug: brutalist-redesign
completed: 2026-05-18
---

# Architecture Review — brutalist-redesign

**Scope:** All source-code changes introduced on this branch vs. `main`
**Date:** 2026-05-18

## Summary

The branch introduces a well-structured multi-module Android (MVVM + Repository + Hilt) codebase. The new `core/models`, `core/designsystem`, `core/data`, and `core/pref` modules form a clean foundation layer. Dependency direction is generally correct (features → core, never core → features). Two violations rise to BLOCKER/HIGH:

1. `feature/reddit` directly imports `BookmarksViewModel` from `feature/twitter` — a cross-feature dependency that should not exist in a modular architecture.
2. `BookmarkSource` is defined in two places (`core/data` and `core/models`), and callers are split between them, creating a silent type-identity divergence.

Additional HIGH-severity concerns include the monolithic `Repository` (Twitter) that merges sync, fetch, OAuth, tag management, and Firestore backup into one class, and the `AppDatabase` living in `app` while DAOs are scattered across feature modules (no Database module in `core`).

**Architectural Style:** Modular Android, layered (core → feature → app), MVVM, Hilt DI

**Severity Breakdown:**
- BLOCKER: 1 (cross-feature module import at compile scope)
- HIGH: 4 (BookmarkSource duplication, god Repository, AppDatabase ownership, DeletedBookmarkRepository dual-responsibility)
- MED: 3 (no convention plugins, debug source-set cross-reference fragility, BannerState in core/data)
- LOW: 2 (reflective DebugIntentHandler bridge, filter/chip state duplication)
- NIT: 1 (media3 version skew between app and designsystem)

**Key Metrics:**
- Circular dependencies: 0 (confirmed)
- God objects (>5 responsibilities): 1 (`feature/twitter/data/Repository.kt`)
- Cross-feature imports: 1 (`feature/reddit` → `feature/twitter`)
- Duplicate type definitions: 1 (`BookmarkSource`)
- Layer violations: 1 (AppDatabase entity list pulls feature-module models into app layer)

---

## Architectural Map

```
app
 ├── screens/*Route          (presentation, Navigation)
 ├── db/AppDatabase          (Room DB — lives in wrong layer)
 ├── di/DatabaseModule       (Hilt wiring)
 └── debug/DebugDataInjector (debug source set)

feature/twitter
 ├── screens/BookmarksViewModel, LoginViewModel, TwitterBookmarksScreen
 └── data/Repository        (sync + OAuth + Firestore + tags — GOD OBJECT)

feature/reddit
 ├── screens/RedditViewModel, RedditBookmarksScreen  ← imports BookmarksViewModel (twitter)
 └── data/RedditRepository

core/data        → (Room, Hilt)           ← no Compose, no feature deps
core/models      → (Compose runtime only)
core/designsystem → (Compose + core:models)
core/pref        → (DataStore, Hilt)
```

**Expected dependency direction:**
```
app → feature/* → core/*
feature/* → core/* (never feature/A → feature/B)
core/designsystem → core/models (OK)
core/data → (no feature deps — OK)
```

---

## Findings

---

### ARCH-001 — feature/reddit imports feature/twitter ViewModel [BLOCKER]
**Severity:** BLOCKER | **Confidence:** High

**Location:** `feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreen.kt:44`

**Issue:**
`RedditBookmarksRoute` injects `BookmarksViewModel` from `feature/twitter` to borrow the tag-management state (`tagsForTweet`, `allTags`, `saveTags`). This creates a compile-time dependency from `feature/reddit` to `feature/twitter`, meaning Reddit cannot be built or tested without Twitter. This violates the core principle that feature modules must not depend on each other.

**Evidence:**
```kotlin
// feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreen.kt:44
import com.github.jayteealao.twitter.screens.BookmarksViewModel

@Composable
fun RedditBookmarksRoute(
    ...
    bookmarksViewModel: BookmarksViewModel = hiltViewModel(),  // cross-feature injection
) {
    val tagsMap by bookmarksViewModel.tagsForTweet.collectAsState()
    val allTags by bookmarksViewModel.allTags.collectAsState()
    ...
    bookmarksViewModel.saveTags(popupBookmark!!.id, tags.toList())
}
```
Confirmed by `feature/reddit/build.gradle:57`:
```groovy
implementation(project(":feature:twitter"))
```

**Impact:**
- Any change to `BookmarksViewModel`'s public API in `feature/twitter` can break Reddit builds.
- Cannot add/test Reddit in isolation.
- Coupling blast radius: every Reddit consumer transitively depends on Twitter.

**Fix:**
Move tag-management state into a shared ViewModel in `core/data` or `app`, or expose a `TagRepository` interface in `core/data` and have each feature module's ViewModel implement its own tag state backed by the shared repository:

```kotlin
// core/data: new TagRepository (interface + Room impl already there via TweetDao)
// feature/twitter and feature/reddit each inject TagRepository directly
// No cross-feature ViewModel dependency needed
```

Alternatively, promote `TagRepository` + a `SharedTagViewModel` to `app` scope and pass tag state as parameters through route composables.

---

### ARCH-002 — BookmarkSource defined in two modules [HIGH]
**Severity:** HIGH | **Confidence:** High

**Location:**
- `core/data/src/main/java/com/github/jayteealao/crumbs/data/BookmarkSource.kt` (object with String constants)
- `core/models/src/main/java/com/github/jayteealao/crumbs/models/Bookmark.kt:35` (enum class `BookmarkSource`)

**Issue:**
Two incompatible types share the same name. `core/data.BookmarkSource` is a string-constant object (`TWITTER = "twitter"`); `core/models.BookmarkSource` is a sealed enum with `Twitter`/`Reddit` cases. Callers import from different sources:
- `HomeRoute.kt`, `Repository.kt`, `RedditRepository.kt` import `crumbs.data.BookmarkSource`
- `AllBookmarksScreen.kt`, `RedditBookmarksScreen.kt`, `TwitterBookmarksScreen.kt`, `CrumbsBookmarkCard.kt` import `crumbs.models.BookmarkSource`

This means `SyncErrorEvent.source: String` uses the data-layer constants, while the UI card rendering uses the enum — any comparison across the boundary is type-unsafe.

**Evidence:**
```kotlin
// core/data/BookmarkSource.kt
object BookmarkSource {
    const val TWITTER = "twitter"
    const val REDDIT = "reddit"
}

// core/models/Bookmark.kt
enum class BookmarkSource {
    Twitter,
    Reddit;
}
```
```kotlin
// HomeRoute.kt — data-layer import
import com.github.jayteealao.crumbs.data.BookmarkSource
when (event.source) {
    BookmarkSource.TWITTER -> ...   // String "twitter"
}

// AllBookmarksScreen.kt — models import
import com.github.jayteealao.crumbs.models.BookmarkSource
when (bookmark.source) {
    BookmarkSource.Twitter -> ...   // enum case
}
```

**Impact:**
- Divergent string/enum representations of the same concept across the codebase.
- Brittle: a `source` field comparison between the two types silently always returns false.
- Adds cognitive overhead for every new contributor.

**Fix:**
Keep only `core/models.BookmarkSource` (the enum). Remove `core/data/BookmarkSource.kt`. Update `SyncErrorEvent` to use the enum or use its `name.lowercase()` for string storage. Update all string-constant imports:

```kotlin
// SyncErrorEvent.kt
data class TwitterAuth401(...) : SyncErrorEvent {
    override val source: BookmarkSource = BookmarkSource.Twitter  // enum, not string
}
```

---

### ARCH-003 — Twitter Repository is a God Object [HIGH]
**Severity:** HIGH | **Confidence:** High

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt`

**Issue:**
`Repository` owns at least 7 distinct responsibilities:
1. OAuth token refresh (`twitterAuthClient.refreshAccessToken`)
2. API bookmark fetching (`twitterApiClient.getBookmarks`)
3. Incremental sync orchestration (pagination, ordering, mutex)
4. Firestore backup upload/download (`firestoreRepository`)
5. Soft-delete delegation (`deletedBookmarkRepository.softDelete`)
6. Tag CRUD (`addTagToTweet`, `removeTagFromTweet`, `saveTags`, `getAllTags`)
7. Paged query surface (`pagingTweetData`, `pagingTweetData(filter)`)

The class is 279 lines and has a 250-BUFFER constant embedded, a background `CoroutineScope` injected directly, and init-block side effects.

**Evidence:**
```kotlin
@Singleton
class Repository @Inject constructor(
    private val tweetDao: TweetDao,
    private val authPref: Prefs,
    private val twitterApiClient: TwitterApiClient,
    private val twitterAuthClient: TwitterAuthClient,
    private val firestoreRepository: FirestoreRepository,
    private val deletedBookmarkRepository: DeletedBookmarkRepository,
    private val syncErrorBus: SyncErrorBus,
    private val scope: CoroutineScope         // 8 constructor params
)
```

**Impact:**
- Testing sync logic requires wiring up OAuth, Firestore, and tag storage.
- Any change to tag handling or Firestore sync risks touching fetch logic.
- Fan-in: `BookmarksViewModel` is the only consumer but the class surface is too broad.

**Fix — suggested split:**
```
TwitterSyncRepository   — OAuth + API fetch + incremental pagination + SyncErrorBus
TwitterBookmarkStore    — DAO read/write + tag CRUD (can be extracted partially to core/data)
FirestoreBackupRepository — upload/download to Firestore (already partially done)
```
Tag operations could move into a `TagRepository` in `core/data` (DAOs for tags already live there through `TweetDao`), solving ARCH-001 simultaneously.

---

### ARCH-004 — AppDatabase lives in `app`, not `core` [HIGH]
**Severity:** HIGH | **Confidence:** High

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/db/AppDatabase.kt`

**Issue:**
`AppDatabase` is declared in the `app` module and references entity classes from `feature/twitter` and `feature/reddit` directly. This means the database schema is coupled to `app`, and `core/data`'s `DeletedBookmarkDao` can only be provided by `app/di/DatabaseModule`. Any future extraction of a feature to a standalone module is blocked.

The dependency flows:
```
app/AppDatabase → feature/twitter/models (TweetEntity, TagEntity, ...)
app/AppDatabase → feature/reddit/models (RedditPostEntity)
app/di/DatabaseModule → provides DeletedBookmarkDao (from core/data) — via app
```

**Impact:**
- `core/data` module cannot be used without `app` providing the DB.
- Adding a new feature with its own entities requires editing `app/AppDatabase`.
- 13 feature-model imports in `app/AppDatabase.kt`.

**Fix:**
Move `AppDatabase` to a new `core/database` module (or `core/data` with a clear sub-package). Use Room's multi-module support: each feature module provides its own `@Database` fragment or simply declares `@Entity` classes and lets `core/database` aggregate them with a build-time entities list via `autoMigrations` or explicit list. `DatabaseModule` moves to the same module.

---

### ARCH-005 — DeletedBookmarkRepository has dual responsibility (persistence + event bus) [HIGH]
**Severity:** HIGH | **Confidence:** Med

**Location:** `core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkRepository.kt`

**Issue:**
`DeletedBookmarkRepository` manages two orthogonal concerns: persisting tombstone records to Room (`insert`, `delete`, `existsBlocking`, `deletedIds`) and emitting snackbar UI events (`_events: MutableSharedFlow<SnackbarEvent>`). A repository should not know about UI event channels. The snackbar event bus should live in either the ViewModel or a separate `SnackbarBus` (paralleling `SyncErrorBus`).

**Evidence:**
```kotlin
class DeletedBookmarkRepository @Inject constructor(private val dao: DeletedBookmarkDao) {
    private val _events = MutableSharedFlow<SnackbarEvent>(...)
    val events: SharedFlow<SnackbarEvent> = _events.asSharedFlow()

    suspend fun softDelete(id: String, source: String) {
        dao.insert(DeletedBookmark(id, source, System.currentTimeMillis()))
        _events.tryEmit(SnackbarEvent.UndoableDelete(id, source))  // UI concern in data layer
    }
}
```

**Impact:**
- UI layer (`HomeRoute`) listens to a repository event bus, coupling presentation to data layer internals.
- Testing snackbar UX requires instantiating Room + DAO.
- Parallel pattern: `SyncErrorBus` is a dedicated `@Singleton` — snackbar events should follow the same pattern.

**Fix:**
Extract a `SnackbarBus @Singleton` mirroring `SyncErrorBus`. `DeletedBookmarkRepository` emits only to it (or doesn't emit at all; the ViewModel observes the bus). `HomeRoute` collects from `SnackbarBus`, not from the repository.

---

### ARCH-006 — No Gradle convention plugins; 6 build files with duplicated config [MED]
**Severity:** MED | **Confidence:** High

**Location:** All module `build.gradle` files

**Issue:**
Every `build.gradle` repeats identical blocks: `compileSdk 35`, `minSdk 24`, `targetSdk 35`, `JavaVersion.VERSION_17` source/target compat, `jvmTarget = JVM_17`, and Hilt dependency declarations. The `plugins {}` block includes empty but commented plugin IDs. `feature/twitter` and `feature/reddit` even duplicate media3 dependencies independently.

The `settings.gradle` references `includeBuild("plugins")`, hinting at a convention plugin directory, but no convention plugin is actually applied — it's unused infrastructure.

**Evidence (duplication across 6 files):**
```groovy
// Appears verbatim in app, core/data, core/pref, feature/twitter, feature/reddit
compileOptions {
    sourceCompatibility JavaVersion.VERSION_17
    targetCompatibility JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}
```

**Impact:**
- SDK bump (e.g., minSdk 26) requires editing 6 files.
- Hilt version pinned inline as a string (`"2.59.2"`) in 4 modules instead of `libs.versions.toml`.

**Fix:**
Implement convention plugins in the existing `plugins/` included-build directory:
```
plugins/
  android-library-convention.gradle.kts
  android-hilt-convention.gradle.kts
  android-compose-convention.gradle.kts
```
Each module applies one-liner: `id("crumbs.android.library")`.

Also add hilt to `libs.versions.toml`:
```toml
[versions]
hilt = "2.59.2"
[libraries]
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
```

---

### ARCH-007 — Debug source-set cross-reference in androidTest is fragile [MED]
**Severity:** MED | **Confidence:** Med

**Location:** `app/src/androidTest/java/com/github/jayteealao/crumbs/debug/DebugDataInjectorTest.kt`

**Issue:**
`DebugDataInjectorTest` (in `androidTest` source set) directly references `DebugDataInjector` (in `debug` source set). This compiles only because AGP merges `debug` + `androidTest` into `debugAndroidTest`. The class comment acknowledges this ("Cross-source-set test"). If the test were ever moved to `test` (unit test), or if AGP changes its source-set merge behavior, the reference silently breaks with a class-not-found error at compile or runtime.

**Evidence:**
```kotlin
// app/src/androidTest/java/.../DebugDataInjectorTest.kt:37
injector = DebugDataInjector(
    context = ctx,
    db = db,
    twitterPrefs = Prefs(ctx),
    redditPrefs = RedditPrefs(ctx),
)
```
`DebugDataInjector` only exists at `app/src/debug/`.

**Impact:**
- Breaks `releaseAndroidTest` assembly (DebugDataInjector not in release variant).
- Future AGP versions may change source-set resolution order.

**Fix:**
Keep the test but add a `@Ignore` guard or move it to an explicit `debugAndroidTest` source set directory (`app/src/debugAndroidTest/java/...`) so the intent is unambiguous and the test is excluded from release instrumented test runs.

---

### ARCH-008 — BannerState in core/data couples data layer to UI presentation [MED]
**Severity:** MED | **Confidence:** Med

**Location:** `core/data/src/main/java/com/github/jayteealao/crumbs/data/BannerState.kt`

**Issue:**
`BannerState` holds UI-presentation strings (`kicker`, `detail`, `ctaLabel`) and lives in `core/data`. A data module should not know about banner copy or CTA labels. These are presentation concerns that belong in the app or feature layer.

**Evidence:**
```kotlin
// core/data/BannerState.kt
data class BannerState(
    val source: String,
    val kicker: String,     // "ERR · RECONNECT TWITTER"
    val detail: String,     // "Twitter session expired. Tap to reconnect."
    val ctaLabel: String,   // "RECONNECT"
)
```
The data content is produced in `HomeRoute.kt` (presentation layer):
```kotlin
twitterBanner = BannerState(
    source = BookmarkSource.TWITTER,
    kicker = "ERR · RECONNECT TWITTER",
    ...
)
```

**Impact:**
- `core/data` now carries a dependency on UI-copy concepts.
- Internationalisation of banner strings requires touching the data module.

**Fix:**
Move `BannerState` to the `app` module (or `feature/*/screens` packages). `core/data` should emit only typed events (`SyncErrorEvent`); the presentation layer maps them to `BannerState`. This is already half-done (`SyncErrorEvent` exists in `core/data`) — `BannerState` just needs to follow.

---

### ARCH-009 — Reflective DebugIntentHandler bridge in production code path [LOW]
**Severity:** LOW | **Confidence:** High

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/MainActivity.kt:42`

**Issue:**
`MainActivity.dispatchDebugIntent` uses `Class.forName` + reflection to call `DebugIntentHandler.handleIntent`. The try/catch on `ClassNotFoundException` silently eats the error in release. While this achieves the intended debug-only dispatch without a release dependency, it bypasses type safety, breaks refactoring tooling (rename of `DebugIntentHandler` or `handleIntent` will not update the string constant), and incurs a Class.forName lookup on every `onCreate` and `onNewIntent` call.

**Evidence:**
```kotlin
private fun dispatchDebugIntent(intent: Intent?) {
    if (intent == null) return
    try {
        val cls = Class.forName("com.github.jayteealao.crumbs.debug.DebugIntentHandler")
        val method = cls.getMethod("handleIntent", ComponentActivity::class.java, Intent::class.java)
        method.invoke(null, this, intent)
    } catch (_: ClassNotFoundException) { }
    catch (_: Throwable) { }  // swallows all reflective failures including debug errors
}
```

**Impact:**
- LOW in practice because the `verifyReleaseDebugInjectorAbsent` Gradle task provides a safety net.
- Refactoring tools will not follow the string class name.

**Alternative:**
Use a build-config boolean + a no-op stub in the release source set:
```kotlin
// app/src/release/java/.../DebugIntentHandler.kt (stub)
object DebugIntentHandler {
    @JvmStatic fun handleIntent(activity: ComponentActivity, intent: Intent?) = Unit
}
```
Then call directly without reflection. This preserves type safety and is a standard Android pattern.

---

### ARCH-010 — FilterState/chip id duplication between app and features [LOW]
**Severity:** LOW | **Confidence:** Med

**Location:**
- `core/data/FilterState.kt` + `TypeFilter.kt` (authoritative)
- `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeScreen.kt:35-42` (hard-coded chip id strings)

**Issue:**
`HomeFilterChips` uses string IDs (`"all"`, `"article"`, `"video"`, etc.) that must manually match `TypeFilter` enum names (lowercased). There is no compile-time enforcement:
```kotlin
internal val HomeFilterChips: ImmutableList<FilterChipItem> = persistentListOf(
    FilterChipItem("all", "ALL"),
    FilterChipItem("article", "ARTICLES"),  // TypeFilter has no ARTICLE — only ALL, TEXT, etc.
    ...
)
```
`BookmarksViewModel.onTypeChipToggled` does `TypeFilter.valueOf(typeId.uppercase())` with a `getOrDefault(TypeFilter.ALL)` — mismatches silently fall back to ALL.

**Fix:**
Generate chip items from `TypeFilter.entries`:
```kotlin
val HomeFilterChips = TypeFilter.entries.map { FilterChipItem(it.name.lowercase(), it.displayName()) }
```
Or at minimum assert in a unit test that every chip id resolves to a known `TypeFilter`.

---

### ARCH-011 — media3 version differs between app and core/designsystem [NIT]
**Severity:** NIT | **Confidence:** High

**Location:**
- `app/build.gradle:128-129`: `media3-exoplayer:1.0.0-beta02`, `media3-ui:1.0.0-beta02`
- `core/designsystem/build.gradle:63-66`: `media3-exoplayer:1.2.0`, `media3-exoplayer-dash:1.2.0`, `media3-exoplayer-hls:1.2.0`, `media3-ui:1.2.0`

Two different versions of media3 will be resolved at runtime via Gradle's dependency resolution (highest wins = 1.2.0). This is harmless today but will generate confusing Lint/AGP warnings and may surface API incompatibilities if the app module uses beta APIs.

**Fix:** Add `media3` to `libs.versions.toml` and reference it from both modules.

---

## Recommendations

### Immediate (BLOCKER/HIGH)

1. **ARCH-001 — Break feature/reddit → feature/twitter dependency**
   - Move tag logic to a `TagRepository` in `core/data` or a `SharedTagViewModel` in `app`.
   - Remove `implementation(project(":feature:twitter"))` from `feature/reddit/build.gradle`.
   - Estimated effort: 1–2 days.

2. **ARCH-002 — Consolidate BookmarkSource**
   - Delete `core/data/BookmarkSource.kt`.
   - Update all `import crumbs.data.BookmarkSource` references to `crumbs.models.BookmarkSource`.
   - Update `SyncErrorEvent` to use the enum.
   - Estimated effort: 2–3 hours.

3. **ARCH-003 — Split Twitter Repository**
   - Extract `TwitterSyncService` (fetch + OAuth), `TagRepository` (to `core/data`), keep `TwitterBookmarkStore` as thin DAO wrapper.
   - Estimated effort: 1 day.

4. **ARCH-004 — Move AppDatabase to core/database**
   - Create `core/database` module (or sub-package inside `core/data`).
   - Entity list lives alongside database declaration.
   - Estimated effort: half day.

5. **ARCH-005 — Extract SnackbarBus from DeletedBookmarkRepository**
   - New `@Singleton SnackbarBus` in `core/data` (pattern: same as `SyncErrorBus`).
   - Estimated effort: 1–2 hours.

### Medium-term (MED)

6. **ARCH-006 — Implement convention plugins** — reduces per-module boilerplate by ~40 lines each.
7. **ARCH-007 — Move DebugDataInjectorTest to explicit `debugAndroidTest` source set.**
8. **ARCH-008 — Move BannerState to app/screens.**

### Low priority (LOW/NIT)

9. **ARCH-009 — Replace reflection in MainActivity with release-stub pattern.**
10. **ARCH-010 — Generate HomeFilterChips from TypeFilter.entries.**
11. **ARCH-011 — Unify media3 version in libs.versions.toml.**

---

## Dependency Graph (as-built)

```
app
 ├── feature:twitter
 │    ├── core:pref
 │    ├── core:models
 │    ├── core:designsystem → core:models
 │    └── core:data
 ├── feature:reddit
 │    ├── core:pref
 │    ├── core:models
 │    ├── core:designsystem
 │    ├── core:data
 │    └── feature:twitter  ← ARCH-001 BLOCKER
 ├── core:pref
 ├── core:models
 ├── core:designsystem
 └── core:data
```

## Metrics

| Metric                       | Value | Threshold | Status |
|------------------------------|-------|-----------|--------|
| Circular dependencies        | 0     | 0         | PASS   |
| Cross-feature imports        | 1     | 0         | FAIL   |
| Duplicate type definitions   | 1     | 0         | FAIL   |
| God objects (>5 resp.)       | 1     | 0         | FAIL   |
| Layer violations (app↔core)  | 0     | 0         | PASS   |
| Convention plugins used      | 0     | 1+        | WARN   |
| Max constructor params       | 8 (Repository) | <6 | WARN |
| Max file size (LOC)          | ~370 (Repository) | <500 | PASS |

*Review completed: 2026-05-18*
