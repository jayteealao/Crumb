---
schema: sdlc/v1
type: plan
slug: brutalist-redesign
slice-slug: behaviors
status: complete
stage-number: 4
created-at: "2026-05-17T21:19:04Z"
updated-at: "2026-05-17T21:19:04Z"
metric-files-to-touch: 32
metric-step-count: 18
has-blockers: false
revision-count: 0
tags: [behaviors, room, migration, soft-delete, snackbar, banner, filter, paging, core-data]
stack-source: confirmed
refs:
  index: 00-index.md
  plan-index: 04-plan.md
  slice-def: 03-slice-behaviors.md
  siblings:
    - 04-plan-toolchain.md
    - 04-plan-tokens.md
    - 04-plan-components.md
    - 04-plan-layouts.md
    - 04-plan-screens.md
  implement: 05-implement-behaviors.md
next-command: wf-implement
next-invocation: "/wf implement brutalist-redesign behaviors"
---

# Plan: behaviors

## Current State

Five slices have shipped (toolchain → tokens → components → layouts → screens, all `verified-partial`). Every UI affordance the brutalist redesign requires is now visible on screen but **inert behind the long-press popup, filter bar, and sync-error banner slot**. Sub-agent reports show:

- **Database**: `AppDatabase` at [app/src/main/java/com/github/jayteealao/crumbs/db/AppDatabase.kt:30](app/src/main/java/com/github/jayteealao/crumbs/db/AppDatabase.kt) is at `@Database(version = 4)` with 13 entities + `tweetDao()` + `redditDao()` accessors. Schema fixture `app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/4.json` exists (1009 lines, identity hash `318795277c1859db9a508ff1f07ecb38`). No `room-testing` artifact is wired; **no instrumentation migration test exists** (only `ExampleInstrumentedTest.kt` placeholder).
- **Migrations** live inline in [app/src/main/java/com/github/jayteealao/crumbs/di/DatabaseModule.kt](app/src/main/java/com/github/jayteealao/crumbs/di/DatabaseModule.kt) as anonymous `Migration(from, to)` objects (`MIGRATION_2_3`, `MIGRATION_3_4`), registered via `.addMigrations(...)` on the `Room.databaseBuilder` call.
- **Popup actions** in the three feed Routes:
  - `AllBookmarksRoute` ([AllBookmarksScreen.kt:213-331](app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt)) — TAG/OPEN/SHARE are wired; **DELETE logs only** (`Timber.d("...TODO behaviors")`).
  - `RedditBookmarksRoute` ([RedditBookmarksScreen.kt:152-275](feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreen.kt)) — same shape; DELETE stubbed.
  - `TwitterBookmarksRoute` ([TwitterBookmarksScreen.kt:161-287](feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt)) — TAG/OPEN/SHARE/**LOGOUT**. Twitter's 4th slot diverges from the slice spec; PO Round 1 Q4 = "Replace LOGOUT with DELETE" so this Route gets the spec-aligned action set and LOGOUT migrates to LoginScreen.
- **Filter state**: `HomeUiState` (HomeScreen.kt:19-24) has only `filterCount: Int = 0`. `HomeFilterChips` is a hardcoded `persistentListOf` of "ALL/ARTICLES/VIDEOS" (HomeScreen.kt:26-30). No `TypeFilter` enum, no `MutableStateFlow<TypeFilter?>`, no chip selection state, no overlay-shell wiring. `CrumbsFilterBar` slot's `onChipToggled` / `onSortClick` are `/* TODO behaviors slice */` no-ops.
- **Banner state**: `HomeScaffold` ([HomeScaffold.kt:35-42](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/HomeScaffold.kt)) currently has slots `topBar`, `filterBar` (nullable), `bottomBar`, `content` — **no `banner` slot**. `CrumbsBanner` ([CrumbsBanner.kt:36-86](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBanner.kt)) is stateless with kicker+detail+CTA signature; visual already brutalist. `BookmarksViewModel` + `RedditViewModel` expose **no banner / error state** — only `isRefreshing` on Twitter, `isAccessTokenAvailable` + `username` on Reddit. Sync errors today are `Timber.e` only ([Repository.kt:158](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt), [RedditRepository.kt:112-121](feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt)) with an auth-client refresh side-effect; **nothing reaches UI**.
- **Snackbar component**: `CrumbsSnackbar` ([CrumbsSnackbar.kt:40-78](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsSnackbar.kt)) is stateless `(message, actionLabel, onAction)` — caller owns timer/show/dismiss. Component-level golden coverage in `core/designsystem/src/test/.../components/SnackbarTest.kt` (4 goldens — `withAction_{light,dark}`, `noAction_{light,dark}`).
- **OverlayShell** ([OverlayShell.kt:43-51](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/OverlayShell.kt)) — `(visible, onDismiss, header, footer, body)` with testTags `overlay-shell` (line 59) + `overlay-shell-apply` (line 102). Ready for tag-multi-select bodies.
- **TagEditorDialog** ([TagEditorDialog.kt:54-60](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/TagEditorDialog.kt)) exists with `(isVisible, currentTags, availableTags, onDismiss, onSave)`. Already consumed by the three Routes' TAG action handlers; no rework needed for this slice — only the DELETE action is currently stubbed.
- **Tag store**: `TagEntity` ([feature/twitter/.../models/TagEntity.kt:30](feature/twitter/src/main/java/com/github/jayteealao/twitter/models/TagEntity.kt), `@Entity(tableName = "tags")` with PK `name: String`) + `TweetTagCrossRef` (composite PK + FK to `tweetEntity` only). `getAllTags(): List<TagEntity>` exposed via `BookmarksViewModel.allTags: StateFlow<List<String>>`. **No `Collection` entity exists** anywhere. PO Round 1 Q2 = "Reinterpret Collection as a tag-set facet" — so the Collection chip opens the **same** OverlayShell over the existing tag set; no new schema for collections.
- **Paging**: Twitter's `Repository.pagingTweetData(): Flow<PagingData<TweetData>>` ([Repository.kt:185-193](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt)) constructs `Pager(config, { tweetDao.getTweets() }).flow` as a property. Reddit's `RedditRepository.getPagingPosts()` builds the pager per-call. Neither uses `.cachedIn(...)`. Neither captures the `PagingSource` instance for manual invalidation — relying on Room's automatic `InvalidationTracker` instead. Both DAO queries (`tweetDao.getTweets()`, `redditDao.getPosts()`) observe their entity tables but **will not auto-invalidate on `deleted_bookmarks` writes** unless a JOIN is added or the filter shifts to `flatMapLatest`.
- **Cross-module module graph**: `app` → `feature/twitter`, `feature/reddit`, `core/designsystem`, `core/models`, `core/pref`. `feature/reddit` → `feature/twitter` (load-bearing cross-module `BookmarksViewModel` injection for tag state — flagged in `05-implement-screens.md`). Neither feature module currently depends on a sibling `core/data` module — that's the new module this slice introduces (PO Round 1 Q1).
- **Hilt graph**: All DAOs are provided in [app/src/main/java/com/github/jayteealao/crumbs/di/DatabaseModule.kt](app/src/main/java/com/github/jayteealao/crumbs/di/DatabaseModule.kt) (`@Singleton @Provides fun providesTweetDao(db) = db.tweetDao()`). New `DeletedBookmarkDao` follows the same pattern.
- **Sync hook points**:
  - Twitter: `Repository.refreshBookmarksInternal()` ([Repository.kt:158](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt) and the channel loop at lines 167-175), and the Firestore boot-time sync at `syncFromFirestore()` (lines 69-92). Both call `saveTweetEntities(...)` (line 94); the tombstone filter must gate that call.
  - Reddit: `RedditRepository.buildDatabase()` line 99-105 — the `.filter { it.kind == "t3" }.map { ... thing.data.toEntity(order) }` chain is the natural insertion point for the tombstone exclusion.
- **OAuth retry**: `LoginViewModel.authIntent()` + `RedditViewModel.authIntent()` are the canonical entry points used by `LoginRoute` ([LoginRoute.kt:59-60](app/src/main/java/com/github/jayteealao/crumbs/screens/login/LoginRoute.kt)). The banner CTA must mirror `context.startActivity(loginViewModel.authIntent())` — VMs **not touched**, only the new banner state and CTA wiring.
- **Test infra**:
  - Roborazzi 1.60.0 / Robolectric 4.16 confirmed in [gradle/libs.versions.toml](gradle/libs.versions.toml). `testOptions.unitTests.includeAndroidResources = true` on all 4 test-bearing modules. `roborazzi.compare.changeThreshold=0.05` at [gradle.properties:59](gradle.properties:59).
  - **`androidx.room:room-testing` is NOT declared** — slice adds it (libs catalog + `androidTestImplementation`).
  - **`kotlinx-coroutines-test` is NOT declared** — `coroutines = "1.5.2"` is dated. **Decision** (per freshness research): keep coroutines at 1.5.2 (toolchain churn too costly here); use `runBlocking { ... }` for the repository unit test (AC line 94) rather than `runTest`. Async correctness is asserted via Turbine-free direct StateFlow.value reads.
  - Component-level testTag inventory ready for any in-stage Compose UI tests: `snackbar`, `snackbar-action`, `banner`, `banner-cta`, `popup`, `popup-action-${id}`, `filter-bar`, `filter-bar-chip-${id}`, `filter-bar-sort`, `overlay-shell`, `overlay-shell-apply`.
- **Version code/name**: `app/build.gradle` lines 32-33 — `versionCode 2`, `versionName "1.1"`. Slice bumps to `3 / "2.0"`.

## Reuse Opportunities

| Candidate | Location | Match | Recommendation |
|-----------|----------|-------|----------------|
| Tag store (`tags` + `tweet_tags` + `TweetDao.getAllTags()`) | `feature/twitter/.../models/TagEntity.kt`, `TweetDao.kt:90-98` | Drives the Tags + Collection chip overlays | **Reuse as-is.** Both chip overlays read from `BookmarksViewModel.allTags`. Collection is a tag-set facet (PO Round 1 Q2). |
| `TagEditorDialog` | [TagEditorDialog.kt:54](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/TagEditorDialog.kt) | Already wired for TAG long-press action | **Reuse as-is.** No changes needed. |
| `OverlayShell` | [OverlayShell.kt:43](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/OverlayShell.kt) | Multi-select body host for Tags/Collection filter overlays | **Reuse as-is.** Body slot accepts the new `FilterTagOverlay(selectedTags, onTagToggled)`. |
| `CrumbsLongPressPopup` + per-action callback shape | [CrumbsLongPressPopup.kt:70](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsLongPressPopup.kt) | Hosts uniform 4-action set across all three Routes | **Reuse as-is.** PopupAction list now `[TAG, OPEN, SHARE, DELETE]` across all Routes; Twitter's `LOGOUT` migrates to LoginScreen. |
| `CrumbsSnackbar` | [CrumbsSnackbar.kt:40](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsSnackbar.kt) | "DELETED · UNDO" snackbar shown on delete | **Reuse as-is.** Render via `SnackbarHost(snackbar = { data -> CrumbsSnackbar(...) })` slot. |
| `SnackbarHostState` (Material3 1.4.0) | `androidx.compose.material3.SnackbarHostState` | Hosts the snackbar with timer + dismissal + TalkBack | **Adopt as backing.** Repository emits `SnackbarEvent`; HomeRoute (or per-tab Route) collects and calls `hostState.showSnackbar(visuals)` with `SnackbarDuration.Short` (≈4s; close enough to slice's 5s). `SnackbarResult.ActionPerformed` triggers undo. |
| `CrumbsBanner` | [CrumbsBanner.kt:36](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBanner.kt) | Sync-error banner above filter bar | **Reuse as-is.** Rendered in the new `HomeScaffold.banner` slot. |
| `LoginViewModel.authIntent()` / `RedditViewModel.authIntent()` | [LoginViewModel.kt:53](feature/twitter/.../screens/LoginViewModel.kt), `RedditViewModel.kt:62` | OAuth restart for banner CTA | **Reuse as-is.** Banner CTA calls `context.startActivity(authIntent())` exactly mirroring `LoginRoute.kt:59-60`. VMs untouched. |
| Existing `MIGRATION_2_3` / `MIGRATION_3_4` inline pattern in `DatabaseModule.kt` | [DatabaseModule.kt:21-114](app/src/main/java/com/github/jayteealao/crumbs/di/DatabaseModule.kt) | Template for `MIGRATION_4_5` | **Copy pattern.** New constant added at the bottom; appended to `.addMigrations(...)` call. |
| KSP `room.schemaLocation` argument | [app/build.gradle:42](app/build.gradle) | Schema export auto-wiring | **Reuse + add androidTest assets srcDir.** Slice adds `sourceSets.androidTest.assets.srcDir("$projectDir/schemas")` to make `MigrationTestHelper` discover the JSONs. (Alternative: adopt the `androidx.room` Gradle plugin per freshness research §1; **deferred** — keep the explicit srcDir + KSP arg pattern this slice to minimize toolchain churn.) |
| `BookmarksViewModel.logout()` / `RedditViewModel.logout()` | feature VMs | LOGOUT relocation target | **Reuse as-is.** New LoginScreen LOGOUT button(s) call these existing methods. |
| `AllBookmarksScreenTest.emptyState_connectAccountCta_invokesCallback` pattern | [AllBookmarksScreenTest.kt](app/src/test/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreenTest.kt) | Precedent for callback-wiring Compose UI tests | **Copy + adapt** for snackbar-callback, banner-CTA-callback, chip-toggle-callback in-stage in-process tests. |

No reuse candidate forces a breaking change. All existing component APIs are preserved; new behavior is additive plumbing into existing slots.

## Likely Files / Areas to Touch

**New module: `core/data`** (PO Round 1 Q1):
- `core/data/build.gradle` — new gradle module; `apply plugin: kotlin-android`, `dagger.hilt.android.plugin`, `org.jetbrains.kotlin.kapt` (or KSP), Room compile + ksp coordinates, depends on `core/models`.
- `core/data/src/main/AndroidManifest.xml` — empty library manifest.
- `core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmark.kt` — entity.
- `core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkDao.kt` — DAO interface.
- `core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkRepository.kt` — `@Singleton` class wrapping DAO + holding `MutableSharedFlow<SnackbarEvent>` + exposing `softDeleteBookmark` / `undoDelete` / `isDeleted` / `events`.
- `core/data/src/main/java/com/github/jayteealao/crumbs/data/SnackbarEvent.kt` — sealed interface for snackbar event variants (`UndoableDelete(id: String, source: BookmarkSource)`).
- `core/data/src/main/java/com/github/jayteealao/crumbs/data/TypeFilter.kt` — enum `ALL, ARTICLE, VIDEO, IMAGE, THREAD, TEXT` (PO Round 2 Q3).
- `core/data/src/main/java/com/github/jayteealao/crumbs/data/SyncErrorEvent.kt` — sealed interface emitted by feature repos when 4xx/5xx sync errors occur (for banner state).
- `core/data/src/main/java/com/github/jayteealao/crumbs/data/SyncErrorBus.kt` — `@Singleton` `MutableSharedFlow<SyncErrorEvent>` (replay=0, extraBufferCapacity=1, DROP_OLDEST) consumed by HomeRoute to flip per-tab banner state.
- `settings.gradle` — add `include ':core:data'`.

**Modified: AppDatabase + Hilt wiring (`app` module)**:
- `app/src/main/java/com/github/jayteealao/crumbs/db/AppDatabase.kt` — bump `version = 5`, add `DeletedBookmark::class` to the `entities` list, add `abstract fun deletedBookmarkDao(): DeletedBookmarkDao` accessor.
- `app/src/main/java/com/github/jayteealao/crumbs/di/DatabaseModule.kt` — add new `MIGRATION_4_5` constant (raw SQL `CREATE TABLE IF NOT EXISTS deleted_bookmarks (...)`), register via `.addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`, add `@Provides fun providesDeletedBookmarkDao(db: AppDatabase): DeletedBookmarkDao = db.deletedBookmarkDao()`.
- `app/build.gradle` — declare new `core/data` dependency (`implementation project(":core:data")`); bump `versionCode 3` + `versionName "2.0"` (lines 32-33); add `androidTestImplementation libs.room.testing`; add `sourceSets.androidTest.assets.srcDir("$projectDir/schemas")` so `MigrationTestHelper` finds the JSON fixtures.
- `gradle/libs.versions.toml` — add `room-testing = { module = "androidx.room:room-testing", version.ref = "room" }`.

**New: 5.json schema** (KSP-generated side-effect):
- `app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/5.json` — auto-emitted on next KSP run after the version bump. Slice commits the file.

**New: instrumentation migration test**:
- `app/src/androidTest/java/com/github/jayteealao/crumbs/db/MigrationTest.kt` — `@RunWith(AndroidJUnit4)` + `MigrationTestHelper` rule. Single test: open `4.json` fixture → run `MIGRATION_4_5` → validate against `5.json`, then assert `deleted_bookmarks` table exists by issuing a no-op `SELECT count(*) FROM deleted_bookmarks` against the migrated DB.

**Modified: feature/twitter sync filter + banner state**:
- `feature/twitter/build.gradle` — add `implementation project(":core:data")`.
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt` — **only acceptable touch**: inject `DeletedBookmarkRepository`; gate `saveTweetEntities(...)` calls in `refreshBookmarksInternal()` (line 158-175) and `syncFromFirestore()` (lines 69-92) on `!tombstone.isDeleted(id)`. Emit `SyncErrorEvent.TwitterAuth401` to `SyncErrorBus` from the existing 401/403 branch in `ApiResponseExt.kt` (line 53-67 inside the `suspendOnError` block). **Do NOT** alter OAuth client logic; the existing token-refresh side-effect stays. The diff inside `Repository.kt` must be exactly (a) the injected dep, (b) the `existsBlocking` filter call, (c) the `tryEmit` to `SyncErrorBus` on 4xx/5xx.
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/BookmarksViewModel.kt` — add `filterState: StateFlow<FilterState>`, `onTypeChipToggled(typeId: String)`, `onTagToggled(tag: String)`, `onAllTagsCleared()`; rewrite the paging exposure as `pagingFlowData: Flow<PagingData<TweetData>> = filterState.flatMapLatest { filter -> repository.pagingTweetData(filter).flow }.cachedIn(viewModelScope)`. Inject `DeletedBookmarkRepository` to dispatch undo callbacks. **Do NOT touch** `feature/twitter/.../screens/LoginViewModel.kt` (regression bar AC-R3 / slice spec line 71). `BookmarksViewModel.logout()` already exists — no change.

**Modified: feature/reddit sync filter + banner state**:
- `feature/reddit/build.gradle` — add `implementation project(":core:data")`.
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt` — same shape: inject `DeletedBookmarkRepository`; gate the post-insert `entitiesToInsert` chain at line 99-105 on `tombstone.isDeleted(id)`. Emit `SyncErrorEvent.RedditAuth401` to `SyncErrorBus` from the existing 401 branch at lines 112-121. **Same diff discipline as Twitter**.
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditViewModel.kt` — add per-tab `filterState`, chip toggle handlers; rewrite paging exposure with `flatMapLatest` + `cachedIn`; inject `DeletedBookmarkRepository`. Add a public `logout()` method that calls the existing auth-client revoke (Reddit has no `logout()` today; small additive method, scoped to LoginScreen integration). **Do NOT touch** OAuth client classes.

**New: app-level AllBookmarksViewModel**:
- `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksViewModel.kt` — `@HiltViewModel` holding `filterState: StateFlow<FilterState>` + chip toggle handlers + a combined paging stream that consumes Twitter's + Reddit's paging flows (`combine(...).flatMapLatest { ... }.cachedIn`). Why introduced: per-tab filter ownership (PO Round 2 Q2) demands a VM for the "All" tab that doesn't currently have one — `AllBookmarksRoute` today injects 3 VMs but holds no filter state. Cleanest fit. Inject `DeletedBookmarkRepository` for undo dispatch.

**Modified: `HomeScaffold` adds banner slot** (PO Round 1 Q3):
- `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/HomeScaffold.kt` — extend signature with `banner: (@Composable () -> Unit)? = null` parameter between `topBar` and `filterBar`. Internal `Column { topBar(); banner?.invoke(); filterBar?.invoke() }` order. Pass-through; call site wraps `AnimatedVisibility(visible = errorState != null)` for show/hide.
- `core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/layouts/HomeScaffoldTest.kt` — add `homeScaffold_withBanner_{light,dark}` Roborazzi goldens (+2 PNGs) exercising the new slot.

**Modified screens (3 Route rewrites + 1 LOGIN extension)**:
- `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt` (`AllBookmarksRoute` at lines 213-331) — replace DELETE Timber stub with `allBookmarksViewModel.softDelete(bookmark)`; collect `DeletedBookmarkRepository.events` and dispatch to a `SnackbarHostState.showSnackbar(...)` call; observe `allBookmarksViewModel.filterState` and pass into HomeScaffold filterBar slot in HomeRoute (lifted state).
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt` (`TwitterBookmarksRoute`) — replace LOGOUT action (4th popup slot) with DELETE wired to `bookmarksViewModel.softDelete(bookmark)`. Surface `filterState` to HomeRoute for lifting.
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreen.kt` (`RedditBookmarksRoute`) — replace DELETE Timber stub with `redditViewModel.softDelete(bookmark)`. Surface `filterState`.
- `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeScreen.kt` + `HomeRoute.kt` — extend `HomeUiState` with `bannerState: BannerState? = null` + `filterState: FilterState`; HomeRoute injects all three tab VMs, observes `SyncErrorBus` for banner triggers (active-tab-aware), and lifts `filterState`/`onChipToggled`/`onSortClick` from the currently-selected tab's VM into `HomeScaffold.filterBar`. New `HomeScaffold.banner` slot consumes `bannerState` rendered as `AnimatedVisibility { CrumbsBanner(...) }`.
- `app/src/main/java/com/github/jayteealao/crumbs/screens/login/LoginScreen.kt` + `LoginRoute.kt` — when `LoginUiState.twitterConnected = true`, render an additional `CrumbsButton(text = "LOGOUT TWITTER", style = Secondary, onClick = onLogoutTwitter)` next to the existing CONNECT TWITTER button (or in place of it). Symmetric for Reddit. New `LoginUiState` fields: `twitterConnected`, `redditConnected`. `LoginRoute` wires `onLogoutTwitter = { loginViewModel.logout() }` / `onLogoutReddit = { redditViewModel.logout() }`. testTags: `login-twitter-logout`, `login-reddit-logout`. **No** OAuth client modification.

**Modified: `app/build.gradle` versionCode/Name**:
- `app/build.gradle:32-33` — `versionCode 3`, `versionName "2.0"`.

**New tests**:
- `app/src/test/java/com/github/jayteealao/crumbs/data/DeletedBookmarkRepositoryTest.kt` — JVM unit test using in-memory Room (`Room.inMemoryDatabaseBuilder`) + a `FakeTwitterApiClient` returning a tombstoned id; asserts the next sync **skips** the tombstoned bookmark (AC line 94). Uses `runBlocking`.
- `app/src/test/java/com/github/jayteealao/crumbs/screens/HomeScreenTest.kt` — extend with `homeScreen_withSyncErrorBanner_{light,dark}` Roborazzi captures (+2 PNGs) showing `CrumbsBanner` in the new slot.
- `app/src/test/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreenTest.kt` — add `longPress_delete_emitsSnackbarEvent()` callback test asserting `popup-action-delete` tap fires the `onDelete` lambda with the expected bookmark id.
- `app/src/test/java/com/github/jayteealao/crumbs/screens/login/LoginScreenTest.kt` — add `logoutTwitter_invokesCallback()` + `logoutReddit_invokesCallback()` + `loggedIn_light` / `loggedIn_dark` Roborazzi goldens (+2 PNGs) with both providers connected.
- `feature/twitter/src/test/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreenTest.kt` — add `popupActions_includesDelete_notLogout()` assertion + (optional) `populatedFeed_withFilter_light` if test data fakes are introduced.
- `core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/BannerTest.kt` — already has 4 goldens; no additions needed.
- `core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/SnackbarTest.kt` — already has 4 goldens.
- `app/src/test/java/com/github/jayteealao/crumbs/testdata/Fakes.kt` — new shared fake builder (`fakeBookmark(id, source = Bookmark.Source.TWITTER, ...)`) for in-stage tests that need populated data.

**New goldens (≥4 PNGs)**:
- `app/src/test/screenshots/HomeScreen_withSyncErrorBanner_light.png`, `_dark.png` (+2).
- `app/src/test/screenshots/LoginScreen_loggedIn_light.png`, `_dark.png` (+2).
- `core/designsystem/src/test/screenshots/HomeScaffold_withBanner_light.png`, `_dark.png` (+2).
Total new PNGs: **6** (3 new × 2 themes).

**Files-to-touch tally**: ~32 source files (5 new in `core/data`, 1 modified entity AppDatabase, 1 modified Hilt module, 1 modified app build.gradle, 1 modified libs.versions.toml, 2 modified feature build.gradles, 2 modified feature repos, 2 modified feature VMs, 1 new AllBookmarksViewModel, 1 modified HomeScaffold, 1 modified HomeScaffoldTest, 4 modified screen sources, 1 modified LoginScreen + LoginRoute, 4 new/modified test files + 1 Fakes file + 1 androidTest MigrationTest), plus 6 new PNGs + 1 new exported `5.json` schema + `settings.gradle` module include.

## Proposed Change Strategy

Single atomic commit ([implement-stage contract](.ai/workflows/brutalist-redesign/05-implement.md)). Build out in this order: **(1)** create `core/data` module with the entity + DAO + repos + event types + filter enum; **(2)** wire the Hilt provider in `app/di/DatabaseModule.kt`; bump AppDatabase version; add `MIGRATION_4_5`; **(3)** add `room-testing` + androidTest assets srcDir; author `MigrationTest.kt`; run `./gradlew :app:connectedDebugAndroidTest` to validate migration before any UI work; **(4)** extend `HomeScaffold` with the `banner` slot and add its 2 Roborazzi goldens; **(5)** wire Twitter `Repository` + `BookmarksViewModel` filter/tombstone/banner-event paths; **(6)** wire Reddit `RedditRepository` + `RedditViewModel` (mirror Twitter); **(7)** introduce `AllBookmarksViewModel`; **(8)** rewrite `HomeRoute` to lift filter state + banner state from the active tab's VM; **(9)** replace popup DELETE stubs with `softDelete()` calls in all three Routes; replace Twitter's LOGOUT with DELETE; **(10)** add LOGOUT buttons to LoginScreen when authed; **(11)** wire `SnackbarHostState` consumption of `DeletedBookmarkRepository.events`; **(12)** bump `versionCode 3 / versionName "2.0"`; **(13)** record + verify all new Roborazzi goldens; **(14)** lint + assemble + test gates; **(15)** atomic commit.

**Locked design decisions** (from PO Rounds 1 + 2):

- **Tombstone module**: new `core/data` shared module hosts `DeletedBookmark`, `DeletedBookmarkDao`, `DeletedBookmarkRepository`, `SnackbarEvent`, `SyncErrorEvent`, `SyncErrorBus`, `TypeFilter`, `FilterState`. Depended on by `app`, `feature/twitter`, `feature/reddit`.
- **Collection filter**: reinterpreted as a tag-set facet — both Tags and Collection chips open the same `OverlayShell` over the existing `tags` table. No `Collection` entity. Collection chip is a curated multi-tag preset semantically; visually identical UX, different default tag selection (e.g., "favorites" tag = the canonical "collection" today).
- **Banner slot**: hoisted `banner: (@Composable () -> Unit)? = null` slot on `HomeScaffold` between `topBar` and `filterBar`. Call site wraps in `AnimatedVisibility(visible = bannerState != null)` so slot stays in tree at zero height when no error.
- **Popup actions**: uniform `[TAG, OPEN, SHARE, DELETE]` across all three Routes. Twitter's LOGOUT migrates to LoginScreen (per-provider LOGOUT button when authed).
- **LOGOUT relocation**: `LoginScreen` shows `CrumbsButton("LOGOUT TWITTER", Secondary)` next to / replacing `CONNECT TWITTER` when `twitterConnected = true`. Symmetric for Reddit. Reddit needs a small additive `RedditViewModel.logout()` method (no OAuth-client touch — just clears the cached access token via the existing pref store).
- **Filter state ownership**: each tab's VM (`BookmarksViewModel`, `RedditViewModel`, new `AllBookmarksViewModel`) holds its own `MutableStateFlow<FilterState>`. HomeRoute lifts the active tab's state into `HomeScaffold.filterBar`. Switching tabs preserves each tab's filter independently.
- **TypeFilter enum**: `ALL, ARTICLE, VIDEO, IMAGE, THREAD, TEXT`. Type queries map to existing schema fields — Twitter's `tweetEntity.referencedTweets` distinguishes THREAD; `tweetEntity.tweetMedia` joins distinguish VIDEO/IMAGE; ARTICLE is "has external URL with og:article"; TEXT is the no-media, no-URL fallback. Reddit's `redditPostEntity.kind` + `postHint` cover the same. DAOs gain `getTweetsByType(filter)` / `getPostsByType(filter)` variants.
- **AVD profile**: `Medium_Phone_API_36` (slice spec literal "Pixel 6 emulator API 34" is updated in verify documentation). Continuity with prior slices.
- **Snackbar**: backed by Material3 `SnackbarHostState` with custom slot rendering `CrumbsSnackbar`. `SnackbarDuration.Short` (≈4s; slice's 5s window is approximated within Material3's stock durations — implement may use `Indefinite` + a manual `delay(5000)` if the 5s requirement is strict).
- **Coroutines**: stay at 1.5.2 — no `kotlinx-coroutines-test` introduction this slice. Repository unit test uses `runBlocking`.
- **Schema test wiring**: explicit `sourceSets.androidTest.assets.srcDir("$projectDir/schemas")` + KSP arg, NOT the `androidx.room` Gradle plugin. (Plugin adoption deferred — small future-cleanup.)
- **Resplit**: slice ships as one. No internal resplit.

## Step-by-Step Plan

1. **Create new `core/data` module.**
   - `settings.gradle`: add `include ':core:data'`.
   - `core/data/build.gradle`: minimal android-library plugin block + `dagger.hilt.android.plugin` + KSP for Room; depend on `core/models`; declare Room + Hilt + kotlinx-collections-immutable + Compose-runtime (for `@Composable`-friendly types if any). Test deps same shape as `core/designsystem` (Roborazzi + Robolectric + JUnit).
   - `core/data/src/main/AndroidManifest.xml`: empty `<manifest package="com.github.jayteealao.crumbs.data" />`.
   - **Sanity-check**: `./gradlew :core:data:assembleDebug` should compile an empty module before adding sources.

2. **Author `core/data` source files.**
   - `DeletedBookmark.kt`:
     ```kotlin
     @Entity(tableName = "deleted_bookmarks")
     data class DeletedBookmark(
         @PrimaryKey val bookmarkId: String,
         val source: String,   // "twitter" | "reddit"
         val deletedAt: Long,
     )
     ```
   - `DeletedBookmarkDao.kt`:
     ```kotlin
     @Dao
     interface DeletedBookmarkDao {
       @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(t: DeletedBookmark)
       @Query("DELETE FROM deleted_bookmarks WHERE bookmarkId = :id") suspend fun delete(id: String)
       @Query("SELECT EXISTS(SELECT 1 FROM deleted_bookmarks WHERE bookmarkId = :id)") fun existsBlocking(id: String): Boolean
       @Query("SELECT bookmarkId FROM deleted_bookmarks") fun getAllIds(): Flow<List<String>>
     }
     ```
   - `SnackbarEvent.kt`: sealed `data class UndoableDelete(val id: String, val source: String)`.
   - `SyncErrorEvent.kt`: sealed `data class TwitterAuth401(val message: String)` / `RedditAuth401(val message: String)` / `Other(val source: String, val message: String)`.
   - `SyncErrorBus.kt`: `@Singleton class SyncErrorBus @Inject constructor() { private val _events = MutableSharedFlow<SyncErrorEvent>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST); val events = _events.asSharedFlow(); fun emit(e: SyncErrorEvent) = _events.tryEmit(e) }`.
   - `TypeFilter.kt`: `enum class TypeFilter { ALL, ARTICLE, VIDEO, IMAGE, THREAD, TEXT }`.
   - `FilterState.kt`: `data class FilterState(val type: TypeFilter = TypeFilter.ALL, val selectedTags: ImmutableSet<String> = persistentSetOf(), val selectedCollectionTags: ImmutableSet<String> = persistentSetOf())`.
   - `DeletedBookmarkRepository.kt`:
     ```kotlin
     @Singleton class DeletedBookmarkRepository @Inject constructor(
       private val dao: DeletedBookmarkDao,
     ) {
       private val _events = MutableSharedFlow<SnackbarEvent>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
       val events: SharedFlow<SnackbarEvent> = _events.asSharedFlow()
       suspend fun softDelete(id: String, source: String) {
         dao.insert(DeletedBookmark(id, source, System.currentTimeMillis()))
         _events.tryEmit(SnackbarEvent.UndoableDelete(id, source))
       }
       suspend fun undoDelete(id: String) { dao.delete(id) }
       fun isDeleted(id: String): Boolean = dao.existsBlocking(id)
       fun deletedIds(): Flow<List<String>> = dao.getAllIds()
     }
     ```

3. **Wire `core/data` into the existing Hilt + Room graph.**
   - `app/src/main/java/com/github/jayteealao/crumbs/db/AppDatabase.kt`: bump `@Database(version = 5, entities = [..., DeletedBookmark::class], exportSchema = true)`; add `abstract fun deletedBookmarkDao(): DeletedBookmarkDao`.
   - `app/src/main/java/com/github/jayteealao/crumbs/di/DatabaseModule.kt`: add `val MIGRATION_4_5 = object : Migration(4, 5) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS `deleted_bookmarks` (`bookmarkId` TEXT NOT NULL, `source` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`bookmarkId`))") } }`. Append `MIGRATION_4_5` to `.addMigrations(...)`. Add `@Singleton @Provides fun providesDeletedBookmarkDao(db: AppDatabase): DeletedBookmarkDao = db.deletedBookmarkDao()`.
   - `app/build.gradle`: `implementation project(":core:data")` (line ~95); `androidTestImplementation libs.room.testing` (line ~135); `sourceSets { androidTest { assets { srcDirs("$projectDir/schemas") } } }` (insert under `android { ... }` block).
   - `gradle/libs.versions.toml`: add the `room-testing` alias.
   - `feature/twitter/build.gradle` + `feature/reddit/build.gradle`: `implementation project(":core:data")`.
   - **Sanity-check**: `./gradlew :app:kspDebugKotlin` produces `app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/5.json`. Inspect to confirm `deleted_bookmarks` is present with correct schema hash.

4. **Author the migration instrumentation test.**
   - `app/src/androidTest/java/com/github/jayteealao/crumbs/db/MigrationTest.kt`:
     ```kotlin
     @RunWith(AndroidJUnit4::class)
     class MigrationTest {
       @get:Rule val helper = MigrationTestHelper(
         InstrumentationRegistry.getInstrumentation(),
         AppDatabase::class.java,
       )

       @Test fun migrate4To5() {
         helper.createDatabase(TEST_DB, 4).apply { close() }
         val db = helper.runMigrationsAndValidate(TEST_DB, 5, /* validateDroppedTables = */ true, MIGRATION_4_5)
         // Sanity: deleted_bookmarks is queryable.
         db.query("SELECT count(*) FROM deleted_bookmarks").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
       }

       companion object { const val TEST_DB = "migration-test" }
     }
     ```
   - **Validate**: `./gradlew :app:connectedDebugAndroidTest --tests "*MigrationTest"` on `Medium_Phone_API_36` (AVD booted via `android emulator start Medium_Phone_API_36`). Expected: PASS with `deleted_bookmarks` count = 0.

5. **Extend `HomeScaffold` with banner slot.**
   - `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/HomeScaffold.kt`: extend signature:
     ```kotlin
     @Composable fun HomeScaffold(
       topBar: @Composable () -> Unit,
       bottomBar: @Composable () -> Unit,
       modifier: Modifier = Modifier,
       banner: (@Composable () -> Unit)? = null,   // NEW slot
       filterBar: (@Composable () -> Unit)? = null,
       contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
       content: @Composable (PaddingValues) -> Unit,
     )
     ```
     Internal topBar slot becomes `Column { topBar(); banner?.invoke(); filterBar?.invoke() }`.
   - `core/designsystem/src/test/.../layouts/HomeScaffoldTest.kt`: add 2 tests `homeScaffold_withBanner_{light,dark}` that pass a `CrumbsBanner(kicker = "↳ Sync error", detail = "Twitter session expired", ctaLabel = "RECONNECT")` in the `banner` slot. testTag for the scaffold's outer Column already exists; add `home-scaffold-banner-slot` testTag on the new slot's wrapper for Maestro addressability.

6. **Add `core/data` dep + sync filter to `feature/twitter/.../data/Repository.kt`.**
   - Inject `DeletedBookmarkRepository` + `SyncErrorBus` via the existing constructor pattern.
   - In `refreshBookmarksInternal()`'s `tweetEntitiesChannel.consumeEach { batch -> batch.data.forEach { entity -> ... saveTweetEntities(...) } }` loop (around line 167-175): wrap the `saveTweetEntities` call with `if (!tombstone.isDeleted(entity.id)) { saveTweetEntities(...) }`.
   - In `syncFromFirestore()`'s `firestoreTweets.forEach { saveTweetEntities(...) }` loop (around line 80-92): same filter.
   - In `ApiResponseExt.kt` (lines 53-67) `suspendOnError` block: when `response.code() in 401..403`, also call `syncErrorBus.emit(SyncErrorEvent.TwitterAuth401(message))` before the existing `onError()` callback fires.
   - **No other touches** in `feature/twitter` source — diff discipline per slice spec line 49-50 + AC-R3 line 71.

7. **Add filter + paging + tombstone wiring to `BookmarksViewModel.kt`.**
   - Inject `DeletedBookmarkRepository`.
   - Add `private val _filter = MutableStateFlow(FilterState())` + `val filter: StateFlow<FilterState> = _filter.asStateFlow()`.
   - Rewrite `pagingFlowData(...)`:
     ```kotlin
     val pagingFlowData: Flow<PagingData<TweetData>> = _filter
       .flatMapLatest { filter -> repository.pagingTweetData(filter).flow }
       .cachedIn(viewModelScope)
     ```
   - Add handlers: `fun onTypeChipToggled(id: String) { _filter.update { it.copy(type = TypeFilter.valueOf(id.uppercase())) } }`, `fun onTagToggled(tag: String)`, `fun onAllTagsCleared()`.
   - Add `fun softDelete(bookmark: Bookmark) { viewModelScope.launch { tombstone.softDelete(bookmark.id, "twitter") } }`.
   - Add `fun undoDelete(id: String) { viewModelScope.launch { tombstone.undoDelete(id) } }`.
   - `pagingTweetData(filter)` on Repository: new method returning `Pager(config, pagingSourceFactory = { tweetDao.getTweetsByType(filter.type) })` — DAO grows a parameterized query that JOINs `deleted_bookmarks` so Room auto-invalidates on tombstone changes:
     ```sql
     SELECT t.* FROM tweetEntity t LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId
       WHERE d.bookmarkId IS NULL AND t.referenced = 0
         AND (:type = 'ALL' OR t.type = :type)
       ORDER BY `order` DESC
     ```
     (Note: `tweetEntity.type` may not exist as a column today; if absent, derive type via existing `referencedTweets`/`tweetMedia` joins. Implement-stage decides — surface as plan→implement note.)

8. **Mirror Twitter changes on Reddit side.**
   - `feature/reddit/.../data/RedditRepository.kt` inject `DeletedBookmarkRepository` + `SyncErrorBus`; filter the `entitiesToInsert` chain (line 99-105) on `!tombstone.isDeleted(thing.data.name)`; emit `SyncErrorEvent.RedditAuth401` from 401 branch (line 112-121).
   - `feature/reddit/.../screens/RedditViewModel.kt` add filter state, paging rewrite, `softDelete()`, `undoDelete()`, **and** a small additive `fun logout() { authClient.revokeToken(); _isAccessTokenAvailable.value = false; _username.value = "" }` — needed by the LoginScreen logout button (LOGOUT relocation). Reddit's `RedditAuthClient` must expose `revokeToken()` if it doesn't already; if not present, add an additive helper that clears the local pref store (no network call — Reddit's `revoke_token` endpoint not required for v2.0). Strictly local revoke = no OAuth-client modification beyond a thin pref-store wrapper.

9. **Author `AllBookmarksViewModel.kt`.**
   - `@HiltViewModel class AllBookmarksViewModel @Inject constructor(...)`. Inject Twitter's `Repository`, Reddit's `RedditRepository`, `DeletedBookmarkRepository`.
   - Hold `_filter: MutableStateFlow<FilterState>` (separate from per-tab filters; AllBookmarks has its own).
   - Combined paging: `val pagingFlow: Flow<PagingData<Bookmark>> = _filter.flatMapLatest { filter -> combine(twitter.pagingTweetData(filter).flow, reddit.pagingPostsData(filter).flow) { tw, rd -> /* interleave by order desc */ } }.cachedIn(viewModelScope)`. (Note: combining `PagingData` streams is non-trivial — interleaving requires custom logic; implement-stage decides between (a) two separate `LazyColumn` sections rather than a true interleave, or (b) a `MediatorPagingSource` wrapping both. Plan recommends (a) for simplicity; surface as implement-stage open question if (b) becomes necessary.)
   - Filter handlers + softDelete/undoDelete mirror per-tab VMs.

10. **Rewrite `HomeRoute.kt` to lift filter + banner state.**
    - Inject `BookmarksViewModel`, `RedditViewModel`, `AllBookmarksViewModel` via `hiltViewModel()`. Plus `SyncErrorBus`.
    - Hold `selectedTab: BottomNavTab` state (already does).
    - Resolve active tab → active VM → active `FilterState` flow. Pass into `HomeScaffold.filterBar` slot: chips list driven by current Type enum + tag set; `onChipToggled` dispatches to active VM.
    - Collect `syncErrorBus.events` in a `LaunchedEffect`. Maintain `var twitterBanner: BannerState? by remember { mutableStateOf(null) }` + same for reddit. When event arrives, set the matching banner. When user navigates to a tab + sync succeeds (observe `xxxViewModel.isRefreshing == false && lastError == null`), clear that tab's banner.
    - `HomeScaffold.banner` slot: when `selectedTab == TWITTER && twitterBanner != null`, render `AnimatedVisibility(visible = true) { CrumbsBanner(kicker = "ERR · RECONNECT TWITTER", detail = "Twitter sync failed. Tap to reconnect.", ctaLabel = "RECONNECT", onCta = { context.startActivity(loginViewModel.authIntent()) }) }`. Same shape for REDDIT.
    - testTags: `home-scaffold-banner-slot`, `banner` (from CrumbsBanner), `banner-cta`.

11. **Replace popup DELETE stubs across the three Routes.**
    - `AllBookmarksRoute` ([AllBookmarksScreen.kt:213-331](app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt)): action ids unchanged; DELETE handler swap from `Timber.d(...)` to `allBookmarksViewModel.softDelete(bookmark)`.
    - `RedditBookmarksRoute` ([RedditBookmarksScreen.kt:152-275](feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreen.kt)): DELETE swap to `redditViewModel.softDelete(bookmark)`.
    - `TwitterBookmarksRoute` ([TwitterBookmarksScreen.kt:161-287](feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt)): replace `PopupAction(id = "logout", ...)` with `PopupAction(id = "delete", label = "DELETE", ...)`. Handler: `bookmarksViewModel.softDelete(bookmark)`.

12. **Add LOGOUT buttons to LoginScreen.**
    - `LoginScreen` ([LoginScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/login/LoginScreen.kt)) stateless signature gains `onLogoutTwitter: () -> Unit = {}` + `onLogoutReddit: () -> Unit = {}`. `LoginUiState` gains `twitterConnected: Boolean = false`, `redditConnected: Boolean = false`. Body: when `uiState.twitterConnected`, render `CrumbsButton("LOGOUT TWITTER", Secondary, onClick = onLogoutTwitter)` below the existing CONNECT TWITTER button (or in place of it — implement-stage decides; spec says "next to or in place"); same shape for Reddit. testTags `login-twitter-logout`, `login-reddit-logout`.
    - `LoginRoute` ([LoginRoute.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/login/LoginRoute.kt)) collects `loginViewModel.isLoggedIn` + `redditViewModel.isAccessTokenAvailable` and projects to `LoginUiState`. Wires `onLogoutTwitter = { loginViewModel.logout() }` + `onLogoutReddit = { redditViewModel.logout() }`. No OAuth-client code change.

13. **Wire `SnackbarHostState` to `DeletedBookmarkRepository.events`.**
    - Cleanest host point: at the **`HomeRoute` level** (one snackbar host shared across all 3 tabs). Add `val snackbarHostState = remember { SnackbarHostState() }`. `LaunchedEffect(Unit) { tombstoneRepo.events.collect { event -> when (event) { is SnackbarEvent.UndoableDelete -> { val result = snackbarHostState.showSnackbar(message = "DELETED", actionLabel = "UNDO", duration = SnackbarDuration.Short); if (result == SnackbarResult.ActionPerformed) { /* dispatch undo to the right VM by source */ activeViewModel.undoDelete(event.id) } } } } }`.
    - Render `SnackbarHost(snackbarHostState, snackbar = { data -> CrumbsSnackbar(message = data.visuals.message, actionLabel = data.visuals.actionLabel, onAction = { data.performAction() }) })` as an overlay (e.g., aligned to bottom of `HomeScaffold`'s content area, or in the existing snackbar host slot of Material3's underlying Scaffold).
    - **Caveat on 5s requirement**: `SnackbarDuration.Short` is ~4 seconds per Material3 spec. Slice AC line 92 says 5s. If the implement reveals 4s is too short for the UX, swap to `SnackbarDuration.Indefinite` + an explicit `coroutineScope.launch { delay(5000); snackbarHostState.currentSnackbarData?.dismiss() }`. Surface as implement-stage open question; not plan-blocking.

14. **Bump version.**
    - `app/build.gradle:32-33` → `versionCode 3`, `versionName "2.0"`.

15. **Add new Roborazzi goldens.**
    - Record (in this order):
      - `./gradlew :core:designsystem:recordRoborazziDebug` → produces +2 PNGs (`HomeScaffold_withBanner_{light,dark}.png`).
      - `./gradlew :app:recordRoborazziDebug` → produces +4 PNGs (`HomeScreen_withSyncErrorBanner_{light,dark}.png` + `LoginScreen_loggedIn_{light,dark}.png`).
    - Inspect each new PNG visually for brutalist palette + correct slot composition.
    - Then verify: `./gradlew :core:designsystem:verifyRoborazziDebug :app:verifyRoborazziDebug :feature:twitter:verifyRoborazziDebug :feature:reddit:verifyRoborazziDebug` — green expected. **Existing 16 PNGs from the screens slice must remain unchanged** (zero diff) since this slice doesn't alter their underlying captures.

16. **Run gate suite.**
    - `./gradlew :app:lintDebug :feature:twitter:lintDebug :feature:reddit:lintDebug :core:data:lintDebug` — green.
    - `./gradlew :app:assembleDebug` — full app links with all behavior wiring + new core/data module.
    - `./gradlew :app:testDebugUnitTest :feature:twitter:testDebugUnitTest :feature:reddit:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:data:testDebugUnitTest` — runs new `DeletedBookmarkRepositoryTest` (AC line 94) + screen UI tests + golden suites.
    - `./gradlew :app:connectedDebugAndroidTest --tests "*MigrationTest"` — runs on `Medium_Phone_API_36` (AC lines 90-91). **Requires booted emulator** via `android emulator start Medium_Phone_API_36`.
    - `./gradlew :app:assembleDebug` then verify: `$env:ANDROID_HOME\build-tools\35.0.0\aapt.exe dump badging app\build\outputs\apk\debug\app-debug.apk | Select-String "versionCode='3'"` returns a match (AC line 99). Same for `versionName='2\.0'`.
    - `./gradlew :app:assembleRelease` — verifies release variant still builds with new module deps + Hilt graph (no debug-only paths leaking).

17. **Update slice's verify expectations.**
    - Implement-record's `## Anything Deferred` will list the 6 runtime-evidence-deferrals for interactive ACs (lines 92, 93, 95, 96, 97, 98 — all collapse onto maestro slice's emulator+Maestro evidence run alongside the 11 existing deferrals).
    - Implement-record cites that the slice spec's "Pixel 6 emulator API 34" (AC line 90) was substituted with `Medium_Phone_API_36` per PO Round 2 Q4 — note as a plan deviation.

18. **Atomic commit** with subject `feat(behaviors): wire long-press delete + filter + sync-error banner + Room migration to v5; introduce core/data module`. Body: per-area diff summary (new `core/data` module, AppDatabase v5 + MIGRATION_4_5, feature repo filter + banner-event wiring, AllBookmarksViewModel, HomeRoute filter/banner lifting, popup DELETE-for-LOGOUT swap on Twitter, LoginScreen LOGOUT buttons, SnackbarHostState integration, version bump to 2.0/3, 6 new Roborazzi goldens, 1 instrumentation MigrationTest). Do not push.

## Test / Verification Plan

### Automated checks

- **lint/typecheck**: `./gradlew :app:lintDebug :feature:twitter:lintDebug :feature:reddit:lintDebug :core:data:lintDebug` — green expected.
- **unit tests**: `./gradlew :app:testDebugUnitTest :feature:twitter:testDebugUnitTest :feature:reddit:testDebugUnitTest :core:data:testDebugUnitTest :core:designsystem:testDebugUnitTest` — runs `DeletedBookmarkRepositoryTest` (AC line 94), expanded `HomeScreenTest` (banner golden), expanded `LoginScreenTest` (loggedIn golden + logout callback), expanded `AllBookmarksScreenTest` (popup-delete callback), expanded `TwitterBookmarksScreenTest` (DELETE not LOGOUT assertion), expanded `HomeScaffoldTest` (banner slot golden).
- **Roborazzi**: `./gradlew :core:designsystem:verifyRoborazziDebug :app:verifyRoborazziDebug :feature:twitter:verifyRoborazziDebug :feature:reddit:verifyRoborazziDebug` — must pass at 5% changed-pixel + 1% RGB tolerance; existing 16 PNGs unchanged, +6 new PNGs match.
- **assembleDebug**: `./gradlew :app:assembleDebug` — full app builds with new module dep + Hilt graph.
- **assembleRelease**: `./gradlew :app:assembleRelease` — release variant builds. Confirms release build doesn't pick up any debug-only behavior wiring (none planned, but guard against accidents).
- **Migration test (instrumentation)**: `./gradlew :app:connectedDebugAndroidTest --tests "com.github.jayteealao.crumbs.db.MigrationTest"` on `Medium_Phone_API_36` — AC lines 90-91 close in-stage.
- **`aapt dump badging` gate**: assembleDebug + `aapt dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "versionCode='3' versionName='2\.0'"` returns a non-empty line. AC line 99 closes in-stage.
- **MaterialTheme grep gate** (workflow rule): `grep -rn "MaterialTheme\." --include="*.kt" core/data/src/main` returns zero matches (new module is data-only; should not have any UI imports).

### Interactive verification (human-in-the-loop)

Per confirmed `stack:` block — `platforms: [android]`, `testing: [junit, compose-ui-test]`, `cli-on-path: [android, lazylogcat]`. **Maestro is not on `cli-on-path`** — interactive ACs requiring gesture/timing flows register as runtime-evidence-deferrals cleared by the `maestro` slice (precedent: 11 existing deferrals on the workflow).

**AC line 90 — Room migration 4→5 runs cleanly on installed v4 DB. (automated — instrumentation test)**
- **Platform & tool**: Android — `Medium_Phone_API_36` AVD booted via `android-cli` skill.
- **Companion skills**: `android-cli` (`android emulator start Medium_Phone_API_36`); `lazylogcat` (`lazylogcat start --tag "Crumbs"` for catch of any unexpected error during test).
- **Steps**: (a) `./gradlew :app:assembleDebugAndroidTest`; (b) `./gradlew :app:connectedDebugAndroidTest --tests "*MigrationTest"`; (c) inspect `lazylogcat dump` for Room migration log lines.
- **Evidence capture**: test-pass status + lazylogcat slice → `.ai/workflows/brutalist-redesign/verify-evidence/behaviors/migration-test-output.log`.
- **Pass criteria**: instrumentation test passes; no Room migration errors in logcat; `app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/5.json` present in working tree.
- **Closes in-stage.**

**AC line 91 — migration test fixture exists. (automated)**
- Same instrumentation test as AC line 90. Closes in-stage.

**AC line 92 — long-press → Delete → card disappears 200ms + snackbar "DELETED · UNDO" 5s. (interactive)**
- **Platform & tool**: Android Maestro — **deferred to `maestro` slice**. Same precedent as toolchain AC4 / components AC-C6 / layouts AC-L5 / screens AC-S4 / AC-S7.
- **In-stage component-level coverage**: `AllBookmarksScreenTest.longPress_delete_emitsSnackbarEvent()` Compose UI test asserts the popup-action-delete callback fires. Component-level snackbar goldens already exist from components slice.
- **Register at verify-stage as `runtime-evidence-deferral`** with `cleared-by: slice/maestro`.

**AC line 93 — UNDO before timer → tombstone removed, card reappears. (interactive)**
- Maestro `delete_undo.yaml`. Deferred to maestro slice.
- In-stage: `DeletedBookmarkRepositoryTest.undoDelete_removesTombstone()` JVM unit test asserts the DAO round-trip.

**AC line 94 — snackbar timer expires → next sync filters out tombstoned id. (automated — repository unit test with fake API)**
- **Closes in-stage**. `DeletedBookmarkRepositoryTest.sync_filtersTombstonedIds()` uses an in-memory Room DB + a `FakeTwitterApiClient` returning a known-tombstoned id; asserts the `existsBlocking` filter prevents the insert.

**AC line 95 — Type filter chip tap → feed re-queries in 300ms. (interactive)**
- Maestro. Deferred.
- In-stage callback coverage: a small `HomeScreenTest.filterBar_typeChip_invokesCallback()` Compose UI test asserts the chip-tap dispatches to the active VM's `onTypeChipToggled`.

**AC line 96 — Tags chip → OverlayShell multi-select → APPLY filters feed. (interactive)**
- Maestro. Deferred.
- In-stage: a Compose UI test asserting that tapping `filter-bar-chip-tags` flips `OverlayShell` visibility (testTag `overlay-shell`); APPLY callback wiring asserted via the existing `overlay-shell-apply` tag.

**AC line 97 — forced Twitter 401 → CrumbsBanner appears within 1s. (interactive)**
- Maestro `sync_error.yaml`. Deferred.
- In-stage: `HomeScreenTest.homeScreen_withSyncErrorBanner_{light,dark}` Roborazzi goldens verify the banner-slot composition matches the brutalist visual contract.

**AC line 98 — banner CTA → OAuth flow initiates. (interactive)**
- Maestro. Deferred.
- In-stage: a callback assertion test on the banner's `onCta` lambda firing the expected `context.startActivity(...)` intent (asserted via `Robolectric.shadowOf(activity).peekNextStartedActivity()`).

**AC line 99 — `aapt dump badging` shows versionCode=3 versionName=2.0. (automated)**
- **Closes in-stage**. Adapter command at verify-stage:
  ```powershell
  ./gradlew :app:assembleDebug
  $env:ANDROID_HOME\build-tools\35.0.0\aapt.exe dump badging app\build\outputs\apk\debug\app-debug.apk |
    Select-String -Pattern "versionCode='3' versionName='2\.0'" -Quiet
  ```

### Compose UI / in-process tests (AC-coverage)

- `DeletedBookmarkRepositoryTest.sync_filtersTombstonedIds()` — AC line 94.
- `DeletedBookmarkRepositoryTest.undoDelete_removesTombstone()` — AC line 93 (data-layer half).
- `AllBookmarksScreenTest.longPress_delete_emitsSnackbarEvent()` — AC line 92 (callback wiring half).
- `LoginScreenTest.logoutTwitter_invokesCallback()` + `logoutReddit_invokesCallback()` — LOGOUT relocation regression.
- `TwitterBookmarksScreenTest.popupActions_includesDelete_notLogout()` — schema-level assertion that the action list no longer contains LOGOUT.
- `HomeScreenTest.filterBar_typeChip_invokesCallback()` — AC line 95 (callback wiring half).
- `HomeScreenTest.banner_ctaTap_invokesCallback()` — AC line 98 (callback wiring half).

## Risks / Watchouts

- **Cross-module `core/data` dep direction.** `feature/twitter` and `feature/reddit` both gain `implementation project(":core:data")`. If a future refactor wants the inverse direction (feature → core/data) for any reason — that's the current direction; preserved. Be alert for unintended transitive symbol exposure (e.g. `feature/reddit` shouldn't gain visibility into Twitter-specific types from `core/data` since none live there).
- **Room migration with a JOIN-driven paging query.** Step 7's `LEFT JOIN deleted_bookmarks` ensures Room's `InvalidationTracker` invalidates the paging source on tombstone changes. **Validation**: write a small in-memory Room test that inserts a TweetEntity + tombstone for that id + observes the Flow — assert the row drops out. If Room doesn't auto-invalidate (corner case), fall back to `MediatorPagingSource` or manual `pagingSource.invalidate()` from `softDelete()`.
- **`tweetEntity.type` column may not exist.** Step 7 assumes a `type` column on TweetEntity for the `WHERE :type = 'ALL' OR t.type = :type` clause. Confirm at implement-time; if absent, the Type filter for Twitter has to derive from existing columns (e.g., `JOIN tweetMedia ON ... GROUP BY t.id` to determine VIDEO/IMAGE, `JOIN tweetReferencedTweets` for THREAD). Surface as plan→implement open question.
- **`SnackbarDuration.Short` ≈ 4s vs spec's 5s.** Material3's stock short duration is 4 seconds. Slice AC line 92 says 5s. Cosmetic discrepancy; if the implement-stage decides 5s is strict, swap to `SnackbarDuration.Indefinite` + manual `delay(5000)`.
- **Reddit `RedditViewModel.logout()` doesn't exist.** Adding it is a tiny additive method (local pref clear + state reset). Make sure the implement doesn't accidentally touch the OAuth-client classes — only a small pref-store helper or DataStore clear.
- **AllBookmarks combined paging.** Interleaving two `PagingData` streams is non-trivial. Step 9 recommends two separate `LazyColumn` sections (Twitter section, Reddit section) for v2.0 rather than a true cross-source paged interleave. If implement decides on (b) `MediatorPagingSource`, that's a non-trivial new abstraction; pick (a) for simplicity.
- **OAuth banner CTA must mirror LoginRoute exactly.** Banner CTA fires `context.startActivity(loginViewModel.authIntent())`. Mirror: LoginRoute.kt:59-60. Do NOT introduce a parallel OAuth-launch helper; reuse the existing one.
- **`SyncErrorBus` lives in `core/data`.** Both feature repos emit; HomeRoute collects. A `MutableSharedFlow(replay=0)` will not replay any in-flight error to a new collector. If user backgrounds the app during an error → returns → the banner won't restore. Mitigation: HomeRoute observes `xxxViewModel.lastError: StateFlow<Throwable?>` (per-VM persistent state) in addition to the bus. Plan ships both: bus for the moment-of-failure UX; per-VM state for "is there a currently-unresolved error."
- **`MigrationTestHelper` schema-asset discovery.** Step 3 wires `sourceSets.androidTest.assets.srcDir("$projectDir/schemas")` so the helper finds `4.json` + `5.json`. If wiring fails, the test throws `IllegalStateException: Cannot find the schema file in the assets folder.` Mitigation: verify the wiring before authoring the test (`find app/build/intermediates -path "*androidTest*" -name "*.json"`).
- **`DROP_OLDEST` snackbar dedup.** A rapid double-delete fires two snackbars; with `extraBufferCapacity = 1, DROP_OLDEST`, only the second shows. This is intentional — second-delete should preempt first. Document in implement-record.
- **Twitter's `bookmarksViewModel.logout()` exists today.** LoginScreen's new LOGOUT button calls it. Confirm at implement-time that `logout()` clears both the access token *and* the cached user state so the LoginScreen UI flips back to `twitterConnected = false`. The existing implementation already does this (per sub-agent inspection); guard against accidentally introducing a stale-state UI.
- **Filter state on tab switch + back navigation.** Each tab's filter persists for the lifetime of its ViewModel. When `HomeRoute` is recomposed after a nav-stack rebuild, the VMs reset → filters reset. This is consistent with current paging behavior; no special handling needed.
- **`existsBlocking()` on the main thread.** The DAO query is `fun existsBlocking(id: String): Boolean` — synchronous Room query. Called from sync code paths (background coroutines). **Never call from the main thread.** Plan-step note: ensure all sync-path call sites are inside `withContext(Dispatchers.IO)` or already on a background dispatcher.
- **Reddit-tags FK bug is out of scope.** Sub-agent flagged that `tweet_tags`'s FK to `tweetEntity` means Reddit-tagged posts would violate FK on insert today. **This is a pre-existing v1.1 bug**, not in scope for this slice — flag in the implement-record's Caveats and possibly open a follow-up issue.

## Dependencies on Other Slices

- **`screens` (verified-partial)**: this slice's primary upstream. Long-press popup wiring, FilterBar slot, Route/Screen split, testTag scaffolding all consumed.
- **`layouts` (verified-partial)**: `HomeScaffold` slot API gains a banner slot (additive). `OverlayShell` consumed by filter chip multi-select bodies.
- **`components` (verified-partial)**: `CrumbsLongPressPopup`, `CrumbsSnackbar`, `CrumbsBanner`, `CrumbsFilterBar`, `TagEditorDialog`, `CrumbsButton` all consumed as-is.
- **`tokens` (verified-partial)**: implicit via components.
- **`toolchain` (verified-partial)**: implicit. New `core/data` module reuses the toolchain catalog pins.
- **`maestro` (deferred)** *consumes* this slice's wired behaviors. Six new runtime-evidence-deferrals register at verify-stage, all `cleared-by: slice/maestro`.

## Assumptions

- **`tweetEntity.type` column exists or can be derived from existing joins.** Verified inferentially via sub-agent's reading of `TweetDao.getTweets()` (no type predicate today); implement-stage validates.
- **`SnackbarDuration.Short` (~4s) is "close enough to 5s"** for v2.0 acceptance. If strict, swap to manual timer.
- **`Medium_Phone_API_36` AVD is bootable on the dev machine.** Prior slices ran against it; assume continuity.
- **Reddit posts can be soft-deleted** — Reddit's `redditPostEntity` is the entity gated by the tombstone filter. No FK or schema conflict.
- **`feature/reddit` gaining a `logout()` method is acceptable.** The method does NOT touch the OAuth client; only clears the local access-token cache. Behaviorally identical to "user uninstalls and reinstalls" minus the data wipe.
- **`AllBookmarksViewModel` introduction does not violate AC-R3.** AC-R3 says "OAuth flows and Firestore sync paths are not modified outside of import/theme adjustments". A new VM in `app/` that *reads* Twitter + Reddit paging flows + sources tombstone filter is additive plumbing on UI side — does not modify OAuth or Firestore.
- **Banner state observation on `SyncErrorBus` + per-VM `lastError`** is sufficient to satisfy AC line 97 ("banner appears within 1s"). 1s is a soft bound — `tryEmit` is synchronous, `Flow.collect` in `LaunchedEffect` is reactive within a recomposition frame; observed latency ≪ 1s.
- **Plan-record's 32 files-to-touch estimate is the floor.** Could grow by ~2-3 if implement reveals (a) the `tweetEntity.type` derivation needs a new DAO file or (b) the AllBookmarks combined-paging shape requires a MediatorPagingSource. Plan tolerates +5 files of drift; beyond that, surface to verify-stage as a deviation.
- **6 new Roborazzi PNGs is the floor.** May reach 8-10 if HomeScreen-with-active-filter or LoginScreen-with-partial-connection states diverge meaningfully. Plan recommends starting with 6 + adding states only if visually distinct.
- **Room Gradle Plugin adoption stays deferred.** The current `ksp { arg("room.schemaLocation", ...) }` pattern works for projects without spaces in the path (verified — `C:\Users\jayte\Documents\dev\Crumb` has no spaces). Migration to the plugin is a small follow-up cleanup, NOT in this slice's scope.

## Blockers

None. Upstream slices (toolchain, tokens, components, layouts, screens) ship all required APIs at `verified-partial`; web research surfaced no Room 2.8.x / Paging-Compose 3.3.x / Material3 1.4.0 / Compose 1.11.1 blockers; PO discovery resolved every cross-cutting design decision in 2 rounds (8 questions); module-graph constraint (cross-module DAO access) has a clean solution via the new `core/data` module.

## Freshness Research

Captured by parallel web-research sub-agent. Top-level takeaways that shape plan steps:

- **Room 2.8.4 `MigrationTestHelper`** with the `androidx.room` Gradle plugin auto-wires schemas into androidTest assets. ([Room releases](https://developer.android.com/jetpack/androidx/releases/room), [nowinandroid#604](https://github.com/android/nowinandroid/issues/604)). Plan defers plugin adoption (small future cleanup) — explicit `sourceSets.androidTest.assets.srcDir` + KSP arg pattern this slice.
- **Paging-Compose 3.3.6**: use `flatMapLatest { filter -> Pager(...).flow }.cachedIn(viewModelScope)` for filter→paging transitions; do NOT manually call `pagingSource.invalidate()` from filter changes. ([v3-transform docs](https://developer.android.com/topic/libraries/architecture/paging/v3-transform), [Ackee paging-3 mutable data](https://www.ackee.agency/blog/paging-3-with-mutable-data)). Plan's Step 7/8/9 follow this pattern.
- **Snackbar event flow**: `MutableSharedFlow(replay=0, extraBufferCapacity=1, BufferOverflow.DROP_OLDEST)` is the canonical 2026 pattern for one-shot UI events. ([MutableSharedFlow API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-mutable-shared-flow.html)). Tombstone repo uses exactly this; `tryEmit` is non-suspending under DROP_OLDEST.
- **Room migration 4→5** additive `CREATE TABLE`: manual `Migration` constant + `MigrationTestHelper.runMigrationsAndValidate` is the canonical path. `@AutoMigration` *is* applicable for additive tables but the explicit form gives precise SQL control for raw DAO reads.
- **`Intent.ACTION_SEND`** share sheet on Compose: `LocalContext.current.startActivity(Intent.createChooser(...))` remains canonical in 2026; not superseded by `ActivityResultLauncher`. Plan's Step 11 reuses the pattern already present in AllBookmarksRoute.
- **Soft-delete + undo**: tombstone write synchronous in repository; 5s timer is `SnackbarDuration.Short` in UI; undo via `SnackbarResult.ActionPerformed`. ([Sandeep Kella's pattern](https://medium.com/kotlin-android-chronicle/building-a-one-time-toast-snackbar-or-dialog-system-in-jetpack-compose-6f40d53433a5)). Plan's Step 13 follows.
- **`HomeScaffold.banner` slot**: hoist slot + call-site `AnimatedVisibility` is the cleaner pattern over embedding in `topBar`. ([Scaffold docs](https://developer.android.com/develop/ui/compose/components/scaffold)).
- **Room auto-invalidation through `InvalidationTracker`** triggers on observed tables. `LEFT JOIN deleted_bookmarks` in the paging query makes Room track BOTH tables — write to tombstone invalidates the paging source automatically. ([issuetracker #191806126](https://issuetracker.google.com/issues/191806126)). Plan's Step 7 leans on this.
- **`SnackbarHostState` + custom visuals slot**: backing the custom `CrumbsSnackbar` with Material3's host gives free TalkBack + back-gesture dismissal + `SnackbarResult.ActionPerformed` callback semantics. ([Material3 SnackbarHost](https://composables.com/material3/snackbarhost)).
- **`aapt dump badging` format**: regex `package: name='[^']+' versionCode='3' versionName='2\.0'` for the AC line 99 gate. ([composer/Apk.kt parser](https://github.com/gojuno/composer/blob/master/composer/src/main/kotlin/com/gojuno/composer/Apk.kt)).

No CVEs on Room 2.8.x, Paging-Compose 3.3.x, Compose-foundation 1.11.x, Material3 1.4.0 in 2026 bulletins. No version-bump blockers.

## Revision History

*(none — initial plan)*

## Recommended Next Stage

- **Option A (default):** `/wf implement brutalist-redesign behaviors` — plan is execution-ready; 18-step atomic commit with clear scope and 8 PO-locked decisions. **`/compact` recommended before proceeding** — planning research (4 sub-agent reports + 2 discovery rounds) is noise for implement.
- **Option B:** `/wf plan brutalist-redesign behaviors <feedback>` — revise this plan if any of the 8 PO Round 1/2 decisions feels wrong on second read (e.g. reconsider tombstone module location, reconsider the SnackbarDuration.Short approximation of the 5s window, reconsider per-tab filter independence).
- **Option C:** `/wf plan brutalist-redesign maestro` — start the next slice's plan in parallel. Maestro slice depends on this slice's behaviors landing first, but its plan can be drafted against this plan's testTag inventory + AC list now. Useful for unblocking maestro work as soon as behaviors implements.
- **Option D:** `/wf-quick probe brutalist-redesign` — single emulator+Maestro probe run to discharge the 11 existing runtime-evidence-deferrals (toolchain AC4 / tokens AC-K4 / components AC-C6 / layouts AC-L2 / AC-L5 / screens AC-S1 / AC-S2 / AC-S4 / AC-S6-nav / AC-S7) before behaviors ships. Not blocking, but consolidates prior verification debt.
