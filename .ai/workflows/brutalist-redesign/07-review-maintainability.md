---
command: /review maintainability
session_slug: brutalist-redesign
date: 2026-05-18
scope: slug-wide (git diff main...HEAD)
target: brutalist-redesign branch
paths:
  - core/designsystem/src/main/.../components/
  - core/designsystem/src/main/.../layouts/
  - core/data/src/main/.../
  - app/src/main/.../screens/
  - feature/twitter/src/main/.../screens/
  - feature/reddit/src/main/.../screens/
  - app/build.gradle, core/*/build.gradle, gradle/libs.versions.toml
---

# Maintainability Review Report

**Reviewed:** slug-wide / `git diff main...HEAD`
**Date:** 2026-05-18
**Reviewer:** Claude Code

---

## 0) Scope, Intent, and Conventions

**What was reviewed:**
- Scope: Full branch diff against `main`
- Files: ~380 changed files; focus on ~60 Kotlin source files and 8 Gradle files
- Lines: +16 821 inserted, −9 318 removed

**Intent (from workflow artifacts):**
- Rebuild UI surface to a brutalist design system (sharp borders, ink tokens, monospace typography)
- Introduce a `core/designsystem` module with reusable components and layout shells
- Wire `SyncErrorBus`, `DeletedBookmarkRepository`, and `FilterState` as shared cross-feature data
- Add Maestro smoke-test suite and Roborazzi screenshot tests

**Team conventions (inferred):**
- State fully hoisted in Screen composables; Route composables own ViewModel injection
- `ImmutableList` / `ImmutableSet` from kotlinx-collections-immutable for Compose stability
- Design tokens via `LocalCrumbsColors`, `LocalCrumbsTypography`, `LocalCrumbsSpacing`, `LocalCrumbsStroke`
- One `@Preview` per variant (light/dark); preview data is private, production-only

**Review focus:**
- Cohesion: Does each module have a clear purpose?
- Coupling: Are dependencies minimal and directional?
- Complexity: Are functions/classes easy to understand?
- Naming: Are names intent-revealing?
- Change amplification: How easy is it to add a third bookmark source?

---

## 1) Executive Summary

**Merge Recommendation:** APPROVE_WITH_COMMENTS

**Rationale:**
The redesign is architecturally sound: the Screen/Route split is clean, design tokens are composable, and ViewModel responsibilities are focused. The issues below are genuine friction points rather than blockers — most can be addressed as follow-up tickets without blocking the merge.

**Top Maintainability Issues:**
1. **MAINT-01**: Long-press popup actions duplicated verbatim across three Route composables — adding a fourth action requires three identical edits.
2. **MAINT-02**: `RedditBookmarksRoute` imports and consumes `BookmarksViewModel` (Twitter feature) for tag state — cross-feature coupling with no abstraction layer.
3. **MAINT-03**: Hilt dependency version `2.59.2` and several library coordinates hard-coded in every `build.gradle`; not referenced by `libs.versions.toml`.

**Overall Assessment:**
- Cohesion: Good (components and data classes focused; layouts well-scoped)
- Coupling: Mixed (cross-module ViewModel sharing is load-bearing but undocumented; `BannerState.source` is an untyped String while `SyncErrorEvent` is sealed)
- Complexity: Manageable (no function exceeds ~120 lines; nesting depth stays ≤3 in components)
- Consistency: Good (naming and preview patterns are uniform; minor `FilterMode` branch dead-code)
- Change Amplification: Moderate (adding a third source still requires edits in 5+ files and 3 duplicate popup blocks)

---

## 2) Module Structure Analysis

| Module | Est. lines (prod) | Responsibilities | Cohesion | Key Dependencies | Verdict |
|--------|-------------------|-----------------|----------|-----------------|---------|
| `CrumbsBookmarkCard.kt` | ~165 prod + 110 preview | Render one bookmark (all content types) | ✅ Focused | `Bookmark`, design tokens | Good |
| `CrumbsFilterBar.kt` | ~90 prod + 35 preview | Filter-chip row + count badge + sort slot | ✅ Focused | design tokens | Good |
| `CrumbsLongPressPopup.kt` | ~180 prod + 40 preview | Context menu anchored at finger offset | ✅ Focused | design tokens | Good |
| `TagEditorDialog.kt` | ~180 prod + 10 preview | Inline tag autocomplete dialog | ✅ Focused; holds local state | design tokens | Acceptable |
| `HomeScaffold.kt` | ~60 prod + 55 preview | Scaffold shell with slot API | ✅ Focused | `LocalCrumbsColors` | Good |
| `OverlayShell.kt` | ~75 prod + 30 preview | Bottom-sheet overlay shell | ✅ Focused | design tokens, `BackHandler` | Good |
| `OnboardingShell.kt` | ~75 prod + 45 preview | Pager-based onboarding wrapper | ✅ Focused | `HorizontalPager`, tokens | Good |
| `FilterState.kt` | ~15 | Filter data class + `isEmpty` | ✅ Focused | `ImmutableSet` | Good |
| `SyncErrorBus.kt` | ~22 | SharedFlow event bus | ✅ Focused | coroutines | Good |
| `DeletedBookmarkRepository.kt` | ~36 | Soft-delete + SnackbarEvent bus | ⚠️ Two concerns: persistence + event emission | `DeletedBookmarkDao` | Acceptable |
| `BookmarksViewModel.kt` | ~124 | Paging, filter state, tag CRUD, refresh, logout | ⚠️ Wide responsibility set | `Repository`, `FilterState` | Acceptable |
| `RedditViewModel.kt` | ~167 | Auth flow + paging + filter + tag CRUD + logout | ⚠️ Auth + data concerns mixed | `RedditAuthClient`, `RedditRepository`, `RedditApiService`, `RedditPrefs` | MED concern |
| `HomeRoute.kt` | ~173 | Event collection + tab routing + popup dispatch | ⚠️ Three `LaunchedEffect` + banner wiring | 5 ViewModels | Acceptable |
| `AllBookmarksScreen.kt` | ~280 | Screen + Route + `RedditPostData.toBookmark` mapping | ⚠️ Production + mapping in same file | cross-module models | MED concern |
| `TwitterBookmarksScreen.kt` | ~360 | Screen + Route + `TweetData.toBookmark` + popup | ⚠️ Route popup is ~80 lines inline | cross-module models | MED concern |
| `app/build.gradle` | ~155 | App module build config | ⚠️ Inline hard-coded versions | many | MED concern |

---

## 3) Coupling Analysis

### Dependency Direction (broadly correct)

```
app (screens, routes)
  → feature/twitter, feature/reddit
  → core/designsystem
  → core/data, core/models, core/pref

feature/reddit → feature/twitter    ← MAINT-02
```

**Cross-layer violations:**
- `RedditBookmarksRoute` imports `BookmarksViewModel` from `feature/twitter` to read tag state. This is a feature→feature dependency, documented in a code comment but carrying real coupling risk.

### Hidden Coupling
- `BannerState.source: String` is compared against `BookmarkSource.TWITTER` / `BookmarkSource.REDDIT` constants in `HomeRoute`. The `when` branch at line 141 will silently fall through `else -> Unit` if a new source string is added. Compare with `SyncErrorEvent` which is correctly sealed.

---

## 4) Findings Table

| ID | Severity | Confidence | Category | File:Line | Issue |
|----|----------|------------|----------|-----------|-------|
| MAINT-01 | HIGH | High | Duplication | `TwitterBookmarksScreen.kt:218-268`, `RedditBookmarksScreen.kt:207-257`, `AllBookmarksScreen.kt:262-315` | Long-press popup action list verbatim in all three Route composables |
| MAINT-02 | HIGH | High | Coupling | `RedditBookmarksScreen.kt:44,159,165,197,271` | `RedditBookmarksRoute` imports and drives `BookmarksViewModel` from `feature/twitter` for tag ops |
| MAINT-03 | MED | High | Config Duplication | `app/build.gradle:107-108`, `core/data/build.gradle:42-43`, `feature/twitter/build.gradle:71-72`, `feature/reddit/build.gradle:69-70` | Hilt version `2.59.2` hard-coded in 4 modules; not in `libs.versions.toml` |
| MAINT-04 | MED | High | Naming / Type Safety | `core/data/src/main/.../BannerState.kt:4` | `BannerState.source: String` — untyped; compared via stringly-typed constants elsewhere |
| MAINT-05 | MED | High | Duplication | `AllBookmarksScreen.kt:337-366`, `RedditBookmarksScreen.kt:279-320` | `RedditPostData.toBookmark()` duplicated as private extension in both files |
| MAINT-06 | MED | Med | Cohesion | `RedditViewModel.kt:37-42` | ViewModel injects 4 dependencies (`RedditAuthClient`, `RedditApiService`, `RedditPrefs`, `RedditRepository`); auth flow lives in VM rather than repository |
| MAINT-07 | MED | Med | Dead Code / Complexity | `CrumbsFilterBar.kt:105-108` | `FilterMode` `when` branches are identical — `Single` and `Multi` call `onChipToggled(chip.id)` with no behavioural difference |
| MAINT-08 | LOW | High | Config Duplication | `app/build.gradle:117-119`, `feature/twitter/build.gradle:81-83` | Coil `2.2.2` + Commit451 coil-transformations hard-coded in both `app` and `twitter` modules; coil version in `core/designsystem` uses `2.5.0` (mismatch) |
| MAINT-09 | LOW | High | Naming | `BookmarksViewModel.kt:47` | `pagingFlowData(order: String = "default")` — parameter `order` is never read; the function just returns `pagingFlow` |
| MAINT-10 | LOW | Med | Comments (what vs. why) | `RedditBookmarksScreen.kt:157-158` | Comment explains WHAT ("Cross-module Twitter VM consumed for tag state") but only partially WHY; no ticket reference |
| MAINT-11 | NIT | High | Magic Numbers | `CrumbsFilterBar.kt:64`, `AllBookmarksScreen.kt:113,135` | Heights `34.dp` in filter bar, paddings `16.dp` / `8.dp` and `6.dp` inline rather than from `LocalCrumbsSpacing` |
| MAINT-12 | NIT | High | Config | `feature/reddit/build.gradle:49-51` | `core-ktx:1.8.0`, `appcompat:1.5.1`, `material:1.7.0` added at top — likely leftover from module creation boilerplate; app doesn't need material XML views |

---

## 5) Findings (Detailed)

### MAINT-01: Long-press popup actions duplicated across three Route composables [HIGH]

**Locations:**
- `feature/twitter/src/main/.../screens/TwitterBookmarksScreen.kt:218-268`
- `feature/reddit/src/main/.../screens/RedditBookmarksScreen.kt:207-257`
- `app/src/main/.../screens/AllBookmarksScreen.kt:262-315`

**Evidence (Twitter Route, representative):**
```kotlin
// TwitterBookmarksScreen.kt:218-268
actions = persistentListOf(
    PopupAction(id = "tag",  label = "TAG",    hint = "Add",    icon = Icons.Default.LocalOffer, isPrimary = true,  onClick = { showTagEditor = true }),
    PopupAction(id = "open", label = "OPEN",   hint = "Url",    icon = Icons.Default.Language,                     onClick = { /* open intent */ }),
    PopupAction(id = "share",label = "SHARE",  hint = "Link",   icon = Icons.Default.Share,                        onClick = { /* share intent */ }),
    PopupAction(id = "delete",label = "DELETE",hint = "Remove", icon = Icons.Default.Delete,    isDanger = true,   onClick = { bookmarksViewModel.softDelete(bookmark.id); popupBookmark = null }),
)
```
The same four-action list (TAG / OPEN / SHARE / DELETE) is copy-pasted into all three Route composables. The only differences are the `softDelete` call target and a Timber tag string.

**Impact:**
- Adding a fifth action (e.g., ARCHIVE) requires edits in three separate files.
- Changing an action label or icon propagates three change sites.
- `defaultPopupActions()` already exists as a factory in `CrumbsLongPressPopup.kt` (line 266) but is not used by any Route.

**Change scenario:**
> Q: Add an "ARCHIVE" quick action to all bookmark lists.
> A: Must locate and update three Route composables identically. Easy to miss one.

**Smallest Fix:**
Extract a shared `bookmarkPopupActions(...)` factory in `core/designsystem` (or a shared util) that accepts lambdas for source-specific operations:

```kotlin
// In CrumbsLongPressPopup.kt or a new BookmarkPopupActions.kt
fun bookmarkPopupActions(
    onTag: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
): ImmutableList<PopupAction> = persistentListOf(
    PopupAction(id = "tag",    label = "TAG",    hint = "Add",    icon = Icons.Default.LocalOffer, isPrimary = true, onClick = onTag),
    PopupAction(id = "open",   label = "OPEN",   hint = "Url",    icon = Icons.Default.Language,                    onClick = onOpen),
    PopupAction(id = "share",  label = "SHARE",  hint = "Link",   icon = Icons.Default.Share,                       onClick = onShare),
    PopupAction(id = "delete", label = "DELETE", hint = "Remove", icon = Icons.Default.Delete, isDanger = true,     onClick = onDelete),
)
```

Each Route then calls `bookmarkPopupActions(onTag = { showTagEditor = true }, onOpen = { ... }, ...)`.

**Benefit:** Adding or renaming an action becomes a single-site change.

---

### MAINT-02: `RedditBookmarksRoute` imports Twitter's `BookmarksViewModel` [HIGH]

**Location:** `feature/reddit/src/main/.../screens/RedditBookmarksScreen.kt:44,159,165,197,271`

**Evidence:**
```kotlin
// Line 44
import com.github.jayteealao.twitter.screens.BookmarksViewModel

// Line 159 — function signature
fun RedditBookmarksRoute(
    ...
    bookmarksViewModel: BookmarksViewModel = hiltViewModel(), // Twitter VM in Reddit feature
)

// Line 165
val tagsMap by bookmarksViewModel.tagsForTweet.collectAsState()
val allTags by bookmarksViewModel.allTags.collectAsState()

// Line 197
onLoadTags = { id -> bookmarksViewModel.loadTagsForTweet(id) },

// Line 271
bookmarksViewModel.saveTags(popupBookmark!!.id, tags.toList())
```

**Issue:**
`feature/reddit` has a compile-time dependency on `feature/twitter` solely for tag operations. Tags are a domain concept that belongs in `core/data`, not in the Twitter feature module. The comment acknowledges this as "load-bearing coupling" but provides no remediation path.

**Impact:**
- Removing or refactoring the Twitter feature module breaks Reddit compilation.
- Tag operations in Reddit tests must instantiate a Twitter ViewModel.
- Adding a third source (e.g., Pocket) would need to import Twitter VM again, or the pattern must be broken at that point anyway.

**Change scenario:**
> Q: Extract Twitter to a separately deliverable feature.
> A: Reddit still compiles against it; you cannot separate them.

**Smallest Fix:**
Move `tagsForTweet`, `allTags`, `loadTagsForTweet`, and `saveTags` into a new `TagRepository` (or a `TagViewModel`) in `core/data`. Both `BookmarksViewModel` and `RedditViewModel` would inject this shared VM / repository instead.

**Estimated effort:** 1–2 hours to extract + wire.

---

### MAINT-03: Hilt version hard-coded in four `build.gradle` files [MED]

**Locations:**
- `app/build.gradle:107-108`
- `core/data/build.gradle:42-43`
- `feature/twitter/build.gradle:71-72`
- `feature/reddit/build.gradle:69-70`

**Evidence (representative):**
```groovy
// Repeated in all four files:
implementation "com.google.dagger:hilt-android:2.59.2"
ksp "com.google.dagger:hilt-compiler:2.59.2"
```

`libs.versions.toml` contains no `hilt` version entry and no `hilt` library alias. Additionally:
- `feature/reddit/build.gradle:66` declares `"androidx.hilt:hilt-navigation-compose:1.1.0"` inline while `libs.versions.toml` already has `hiltNavigationCompose` pointing to `1.0.0` — a silently mismatched version.

**Impact:** Upgrading Hilt requires touching four files. The mismatch on `hilt-navigation-compose` means Reddit uses a different version than the rest of the app.

**Smallest Fix:**
```toml
# libs.versions.toml
hilt = "2.59.2"
hiltNavigationCompose = "1.1.0"

[libraries]
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
```

Then replace all four inline declarations with `implementation(libs.hilt.android)` / `ksp(libs.hilt.compiler)`.

---

### MAINT-04: `BannerState.source` is an untyped `String` [MED]

**Location:** `core/data/src/main/.../data/BannerState.kt:4`

**Evidence:**
```kotlin
data class BannerState(
    val source: String,  // compared against BookmarkSource.TWITTER / BookmarkSource.REDDIT
    ...
)
```

`HomeRoute.kt:141` pattern-matches on `activeBanner?.source`:
```kotlin
when (activeBanner?.source) {
    BookmarkSource.TWITTER -> context.startActivity(loginViewModel.authIntent())
    BookmarkSource.REDDIT  -> context.startActivity(redditViewModel.authIntent())
    else -> Unit  // silently ignored if a typo or new source is added
}
```

`SyncErrorEvent` (same package) is a sealed interface and handles this correctly. `BannerState` bypasses that safety.

**Smallest Fix:**
Either reuse `SyncErrorEvent` source identity, or change `BannerState.source` to reference the sealed type:
```kotlin
// Option A: use an enum/sealed type
data class BannerState(
    val source: BookmarkSourceType, // an enum in core/data
    ...
)

// Option B: remove source from BannerState and let callers specialise the CTA lambda
data class BannerState(
    val kicker: String,
    val detail: String,
    val ctaLabel: String,
    val onCta: () -> Unit,   // source-specific action captured at creation site
)
```

Option B eliminates the `when` switch entirely and makes `BannerState` self-contained.

---

### MAINT-05: `RedditPostData.toBookmark()` duplicated in two files [MED]

**Locations:**
- `app/src/main/.../screens/AllBookmarksScreen.kt:337-366`
- `feature/reddit/src/main/.../screens/RedditBookmarksScreen.kt:279-320`

**Evidence:** Both files contain a private `fun RedditPostData.toBookmark(tags: List<String>): Bookmark` with identical logic (thumbnail exclusion list, content-type detection, `createdUtc * 1000` epoch conversion).

Note: `TweetData.toBookmark()` is correctly extracted as a public top-level extension in `TwitterBookmarksScreen.kt` and documented as such. The Reddit equivalent was not given the same treatment.

**Smallest Fix:**
Move `RedditPostData.toBookmark()` to `feature/reddit` as a public top-level extension, mirroring the Twitter approach:
```kotlin
// RedditBookmarksScreen.kt — make public, remove from AllBookmarksScreen.kt
fun RedditPostData.toBookmark(tags: List<String> = emptyList()): Bookmark { ... }
```

---

### MAINT-06: `RedditViewModel` mixes auth flow with paging / filter state [MED]

**Location:** `feature/reddit/src/main/.../screens/RedditViewModel.kt:37-167`

**Evidence:**
```kotlin
@HiltViewModel
class RedditViewModel @Inject constructor(
    private val redditRepository: RedditRepository,
    private val redditAuthClient: RedditAuthClient,   // auth
    private val redditApiService: RedditApiService,   // also auth (getUser)
    private val redditPrefs: RedditPrefs              // also auth (accessToken)
) : ViewModel() {
    // Auth state
    private val _isAccessTokenAvailable = MutableStateFlow(false)
    private val _username = MutableStateFlow("")

    // Paging / filter (data)
    private val _filter = MutableStateFlow(FilterState())
    val pagingFlow: Flow<PagingData<RedditPostData>> ...

    // Auth operations
    fun authIntent() = redditAuthClient.getAuthIntent()
    fun getAccessToken(code: String) { ... }
    private fun fetchUsername() { ... }
    private fun checkAccessToken() { ... }

    // Data operations
    fun onTypeChipToggled(...)
    fun onTagToggled(...)
    fun softDelete(...)
    fun undoDelete(...)
    fun logout() { ... }  // also auth
}
```

This mirrors `BookmarksViewModel` which is narrower (no auth handling). `RedditViewModel` manages auth setup, username resolution, paging, filtering, soft-delete, and logout. If Reddit auth is refactored (e.g., to support PKCE refresh), the same class that owns paging must be modified.

**Smallest Fix (low risk):**
Extract auth-related state and functions into a `RedditAuthViewModel` (similar to Twitter's `LoginViewModel`), which already exists in the Twitter module. Both the Route and `HomeRoute` would inject the smaller scoped VM. No structural changes needed to `RedditRepository`.

---

### MAINT-07: `FilterMode` `when` branches are dead divergence [MED]

**Location:** `core/designsystem/src/main/.../components/CrumbsFilterBar.kt:104-109`

**Evidence:**
```kotlin
.clickable {
    when (mode) {
        FilterMode.Single -> onChipToggled(chip.id)  // both branches identical
        FilterMode.Multi  -> onChipToggled(chip.id)
    }
}
```

Both `Single` and `Multi` call `onChipToggled(chip.id)` with no behavioural difference. The `FilterMode` sealed interface was presumably introduced to support multi-select in the future, but as written it adds cognitive overhead — a reader must inspect both branches to verify they are the same.

**Smallest Fix:**
```diff
- when (mode) {
-     FilterMode.Single -> onChipToggled(chip.id)
-     FilterMode.Multi  -> onChipToggled(chip.id)
- }
+ onChipToggled(chip.id)
```

If multi-select semantics are intended later, add the diverging logic at that point and document the intent with a TODO referencing a ticket.

---

### MAINT-08: Coil version mismatch across modules [LOW]

**Locations:**
- `app/build.gradle:117` — `io.coil-kt:coil-compose:2.2.2`
- `feature/twitter/build.gradle:81` — `io.coil-kt:coil-compose:2.2.2`
- `core/designsystem/build.gradle:62` — `io.coil-kt:coil-compose:2.5.0`

`core/designsystem` uses coil 2.5.0; `app` and `feature/twitter` pin 2.2.2 separately. At runtime the higher version wins via Gradle conflict resolution, but the intent is unclear and the older pinned versions create update friction.

**Smallest Fix:**
Add `coil = "2.5.0"` to `libs.versions.toml`, define a `coil-compose` library alias, and replace inline declarations in all three modules.

---

### MAINT-09: Unused `order` parameter on `pagingFlowData()` [LOW]

**Location:** `feature/twitter/src/main/.../screens/BookmarksViewModel.kt:47`

**Evidence:**
```kotlin
fun pagingFlowData(order: String = "default"): Flow<PagingData<TweetData>> = pagingFlow
```

`order` is declared but never read. The function unconditionally returns `pagingFlow`. `RedditViewModel` has the same pattern at line 120 (no-param version, which is fine). The Twitter version suggests a planned sort feature that was never wired.

**Smallest Fix:** Remove the `order` parameter until sort is implemented:
```kotlin
fun pagingFlowData(): Flow<PagingData<TweetData>> = pagingFlow
```

---

### MAINT-10: Cross-module coupling comment explains what, not why [LOW]

**Location:** `feature/reddit/src/main/.../screens/RedditBookmarksScreen.kt:157-158`

**Evidence:**
```kotlin
// Cross-module Twitter VM consumed for tag state — load-bearing coupling
// that survives the brutalist rewrite. See implement-screens artifact.
bookmarksViewModel: BookmarksViewModel = hiltViewModel(),
```

The comment accurately describes the situation but does not explain why tags weren't moved to a shared module during this rewrite, what the plan is to fix it, or reference a follow-up ticket. Future contributors reading this will have the same question.

**Smallest Fix:**
```kotlin
// TODO(TICKET-NNN): Tags are owned by BookmarksViewModel (feature/twitter) because
// there is no shared TagRepository in core/data yet. This creates a compile-time
// dependency between feature/reddit and feature/twitter. Extract TagRepository
// before adding a third bookmark source.
```

---

### MAINT-11: Magic numbers inline rather than from spacing tokens [NIT]

**Locations:**
- `CrumbsFilterBar.kt:64` — `.height(34.dp)` (filter bar height not in tokens)
- `AllBookmarksScreen.kt:113,135` — `.padding(horizontal = 16.dp, vertical = 8.dp)` / `padding(16.dp)`
- `CrumbsBookmarkCard.kt:99` — `.height(200.dp)` (media preview height)

Design token system exposes spacing (`LocalCrumbsSpacing`) but some layout-specific sizes are inlined. These do not cause bugs but make global spacing adjustments (e.g., tablet density changes) require grep-and-replace rather than a single token change.

---

### MAINT-12: Leftover boilerplate dependencies in `feature/reddit` [NIT]

**Location:** `feature/reddit/build.gradle:49-51`

```groovy
implementation 'androidx.core:core-ktx:1.8.0'
implementation 'androidx.appcompat:appcompat:1.5.1'
implementation 'com.google.android.material:material:1.7.0'
```

These are pinned at pre-BOM versions (core-ktx 1.8.0 dates to 2022) and are likely scaffolding from module creation. `material:1.7.0` (XML Material Design) is unused in a Compose-only module. Pruning them removes three transitive dependency pulls.

---

## 6) Change Amplification Analysis

### Scenario 1: Add a third bookmark source (e.g., Pocket)

**Files that would need changes:**
1. `core/data` — new `SyncErrorEvent.PocketAuth401`, `BookmarkSource.POCKET` constant
2. `core/designsystem/components/CrumbsBottomNav.kt` — new `BottomNavTab`
3. `HomeRoute.kt` — new `activeFilter` branch, new `LaunchedEffect` arm, new `when` arm in banner CTA
4. `app/screens/HomeScreen.kt` — new tab case
5. `AllBookmarksScreen.kt` — new paging section
6. New `feature/pocket` module
7. **Three Route composables** with duplicated popup action lists (MAINT-01)
8. **`BannerState.source` string constant** (MAINT-04, silent fallthrough risk)

**Assessment:** Moderate-to-high amplification. MAINT-01 and MAINT-04 both add unnecessary change sites. If fixed, the list reduces to 5 predictable files.

### Scenario 2: Change all popup action labels to title-case

**Files:** 3 Route composables (`TwitterBookmarksScreen.kt`, `RedditBookmarksScreen.kt`, `AllBookmarksScreen.kt`).

**Assessment:** Pure change amplification from MAINT-01.

### Scenario 3: Upgrade Hilt from 2.59.2 to 2.61

**Files:** `app/build.gradle`, `core/data/build.gradle`, `feature/twitter/build.gradle`, `feature/reddit/build.gradle` — 4 identical edits.

**Assessment:** Eliminates with MAINT-03.

---

## 7) Positive Observations

- **Screen/Route split is clean and consistent.** All four screens follow the same contract: `Screen` is a pure composable accepting typed `UiState`; `Route` owns ViewModel injection and side effects. This makes screenshot testing straightforward.
- **`ImmutableList`/`ImmutableSet` used correctly throughout.** Compose stability annotations (`@Immutable`) are present on all `UiState` data classes.
- **`SyncErrorBus` and `DeletedBookmarkRepository` are correctly scoped** as `@Singleton` with `SharedFlow` semantics — no sticky state across reconnects.
- **Design token locals are used uniformly.** No hardcoded colors or typography styles were found in production composables.
- **Preview data is private.** All sample `Bookmark` values are `private val`; no test fixtures leaked into production symbols.
- **`HomeServicesViewModel` pattern is clever** — holding `SyncErrorBus` and `DeletedBookmarkRepository` at the route scope to survive tab switches without duplicating `LaunchedEffect` in each feature screen.
- **`FilterMode` sealed interface is the right abstraction** even if the current implementation is a no-op; the shape is correct for future multi-select.
- **`verifyReleaseDebugInjectorAbsent` Gradle task** is a nice guard against debug code leaking to release.

---

## 8) Recommendations

### Must Address (HIGH findings)

1. **MAINT-01** — Extract `bookmarkPopupActions(...)` factory.
   - Action: Create shared factory in `core/designsystem`; replace 3 inline lists.
   - Effort: ~30 min.

2. **MAINT-02** — Extract `TagRepository` (or `TagViewModel`) to `core/data`.
   - Action: Move tag state + operations out of `BookmarksViewModel`; inject into both Reddit and Twitter Routes.
   - Effort: ~2 hours.

### Should Fix (MED findings)

3. **MAINT-03** — Add Hilt to `libs.versions.toml`; resolve `hilt-navigation-compose` version drift.
   - Effort: ~20 min.

4. **MAINT-04** — Make `BannerState.source` typed (enum or absorbed CTA lambda).
   - Effort: ~30 min.

5. **MAINT-05** — Make `RedditPostData.toBookmark()` a public top-level extension in `feature/reddit`.
   - Effort: ~15 min.

6. **MAINT-06** — Extract Reddit auth state into `RedditAuthViewModel`.
   - Effort: ~1 hour.

7. **MAINT-07** — Collapse identical `FilterMode` branches.
   - Effort: 5 min.

### Consider (LOW / NIT findings)

8. **MAINT-08** — Centralise Coil version in `libs.versions.toml`. Effort: ~10 min.
9. **MAINT-09** — Remove unused `order` parameter. Effort: 5 min.
10. **MAINT-10** — Add ticket reference to cross-module coupling comment. Effort: 2 min.
11. **MAINT-11** — Register filter bar height and card media height in spacing/dimension tokens.
12. **MAINT-12** — Remove leftover appcompat/material XML deps from `feature/reddit`.

---

## 9) Refactor Cost/Benefit

| Finding | Effort | Benefit | Risk | Recommendation |
|---------|--------|---------|------|----------------|
| MAINT-01 | 30 min | High — single change site for popup actions | Low | Do now |
| MAINT-02 | 2 h | High — decouples Reddit from Twitter | Low | Follow-up ticket |
| MAINT-03 | 20 min | Med — consistent version management | None | Do now |
| MAINT-04 | 30 min | Med — prevents silent banner fallthrough | None | Do now |
| MAINT-05 | 15 min | Med — removes duplicate mapping logic | None | Do now |
| MAINT-06 | 1 h | Med — cleaner VM responsibilities | Low | Follow-up ticket |
| MAINT-07 | 5 min | Low — reduces reader confusion | None | Do now |
| MAINT-08 | 10 min | Low — dependency hygiene | None | Do now |
| MAINT-09 | 5 min | Low — removes dead API surface | None | Do now |
| MAINT-10 | 2 min | Low — navigability | None | Do now |
| MAINT-11–12 | 15 min | NIT | None | Opportunistic |

**Total for HIGH + MED quick fixes (MAINT-01, 03, 04, 05, 07, 08, 09, 10):** ~2 hours
**Deferred (MAINT-02, 06):** ~3 hours in follow-up tickets

---

## 10) Conventions & Consistency

### Naming

| Category | Observed Pattern | Consistency | Notes |
|----------|-----------------|-------------|-------|
| Composable files | `Crumbs<Component>.kt` | ✅ Consistent | All design-system components prefixed |
| Screen files | `<Name>Screen.kt` + `<Name>Route.kt` | ✅ Consistent | |
| ViewModel state flows | `_camelCase` private + public `camelCase` | ✅ Consistent | |
| Route composable params | `@Composable fun XRoute(... vm = hiltViewModel())` | ✅ Consistent | |
| Data module types | `FilterState`, `BannerState`, `SyncErrorEvent` | ⚠️ Mixed | `BannerState.source: String` vs sealed event approach |

### Architecture Patterns

| Pattern | Usage | Consistency |
|---------|-------|-------------|
| Screen/Route split | Used in all 4 screens | ✅ Consistent |
| State hoisting | All UI state in UiState data classes | ✅ Consistent |
| Event buses (SharedFlow) | `SyncErrorBus`, `DeletedBookmarkRepository.events` | ✅ Consistent |
| Design token access via LocalComposition | All production composables | ✅ Consistent |
| Tag management | Split between Twitter VM and Reddit Route | ⚠️ Inconsistent (MAINT-02) |

---

## 11) False Positives & Disagreements Welcome

1. **MAINT-01 (popup duplication):** If popup actions are expected to diverge significantly per source in a near-term follow-up (e.g., Reddit gets a "crosspost" action), the current duplication is intentional scaffolding. The factory approach still works — just pass source-specific actions as additional optional slots.

2. **MAINT-02 (cross-module VM):** If `feature/reddit` is slated for deletion or merger with `feature/twitter` into a single `feature/bookmarks` module, the coupling is temporary and not worth the extraction cost now.

3. **MAINT-06 (RedditViewModel cohesion):** Android's `ViewModel` scope makes splitting auth state across two VMs more complex than it first appears (both must share `@HiltViewModel` scope). If the intent is simplicity over correctness at this stage, MAINT-06 is acceptable.

---

*Review completed: 2026-05-18*
*Session: brutalist-redesign*
