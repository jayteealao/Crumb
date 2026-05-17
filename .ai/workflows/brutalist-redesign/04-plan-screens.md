---
schema: sdlc/v1
type: plan
slug: brutalist-redesign
slice-slug: screens
status: complete
stage-number: 4
created-at: "2026-05-17T16:15:10Z"
updated-at: "2026-05-17T16:15:10Z"
metric-files-to-touch: 26
metric-step-count: 16
has-blockers: false
revision-count: 0
tags: [screens, brutalist, route-screen-split, pager-migration, roborazzi, app, feature-twitter, feature-reddit]
stack-source: confirmed
refs:
  index: 00-index.md
  plan-index: 04-plan.md
  slice-def: 03-slice-screens.md
  siblings:
    - 04-plan-toolchain.md
    - 04-plan-tokens.md
    - 04-plan-components.md
    - 04-plan-layouts.md
  implement: 05-implement-screens.md
next-command: wf-verify
next-invocation: "/wf verify brutalist-redesign screens"
---

# Plan: screens

## Current State

The Crumb app today has 8 screens that need to compose the new brutalist component + layout shells produced by upstream slices. None of them currently use `HomeScaffold` / `OverlayShell` / `OnboardingShell` (those shells landed in the previous slice as additive APIs with no callers). Sub-agent 1 found:

- **`SplashScreen.kt`** ([app/.../SplashScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/SplashScreen.kt)) — 51 LOC. Full-bleed `Box` with `remember(isLoggedIn)` + `LaunchedEffect` delay, navigates to Onboarding/Login/Home.
- **`OnboardingScreen.kt`** ([app/.../onboarding/OnboardingScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/onboarding/OnboardingScreen.kt)) — 214 LOC. Currently `CrumbsScaffold` wraps `com.google.accompanist.pager.HorizontalPager(count = pages.size, ...)` + `rememberPagerState()` (Accompanist API). Inline `HorizontalPagerIndicator` from Accompanist.
- **`LoginScreen.kt`** ([app/.../login/LoginScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/login/LoginScreen.kt)) — 85 LOC. Full-bleed `Box` + `GradientImage` background; `hiltViewModel()` for both `LoginViewModel` (Twitter) and `RedditViewModel`. `collectAsState()` on auth Flows. Accepts optional `authorizationCode` nav arg for OAuth callback.
- **`HomeScreen.kt`** ([app/.../HomeScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/HomeScreen.kt)) — 87 LOC. `CrumbsScaffold(topBar = { CrumbsTopBar(...) }, bottomBar = { CrumbsBottomNav(...) })` + local `remember` for `selectedTab`, `isSearchActive`, `searchQuery`. Content slot dispatches to one of 4 tabs.
- **`AllBookmarksScreen.kt`** ([app/.../AllBookmarksScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt)) — 359 LOC. Box + LazyColumn; 3 injected ViewModels; uses paging. **Has `MaterialTheme.typography.titleMedium` references** (~lines 103, ~180) — must convert to `LocalCrumbsTypography.current.*` per workflow's MaterialTheme posture rule.
- **`MapViewScreen.kt`** ([app/.../MapViewScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/MapViewScreen.kt)) — 21 LOC. Full-bleed Box, no state. Becomes brutalist "COMING SOON" placeholder.
- **`TwitterBookmarksScreen.kt`** ([feature/twitter/.../TwitterBookmarksScreen.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt)) — 330 LOC. `PullToRefreshBox` + LazyColumn. `hiltViewModel()` for `BookmarksViewModel` + `LoginViewModel`. Hard rewrite per Round-1 PO answer.
- **`RedditBookmarksScreen.kt`** ([feature/reddit/.../RedditBookmarksScreen.kt](feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreen.kt)) — 289 LOC. LazyColumn in Box. `hiltViewModel()` for `RedditViewModel` **AND** the cross-module `BookmarksViewModel` from `feature/twitter` (for tag state — load-bearing coupling that must survive the rewrite).

**Layout shells available** (from layouts slice — verified-partial):
- `HomeScaffold(topBar, filterBar = null, bottomBar, content)` at `core/designsystem/layouts/HomeScaffold.kt`
- `OverlayShell(visible, onDismiss, header, body, footer)` — not consumed in this slice; behaviors slice owns
- `OnboardingShell(pages: ImmutableList<@Composable () -> Unit>, pagerState, header, footerCtaText, onFooterCtaClick)` at `core/designsystem/layouts/OnboardingShell.kt`

**Brutalist components available** (from components slice — verified-partial):
- `CrumbsTopBar(kickerText, wordmark = "crumbs•", searchQuery, onSearchQueryChange, isSearchActive, onSearchActiveChange)` ([CrumbsTopBar.kt:50](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsTopBar.kt:50))
- `CrumbsBottomNav(selectedTab: BottomNavTab, onTabSelected)` ([CrumbsBottomNav.kt:42](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBottomNav.kt:42))
- `CrumbsFilterBar(count, chips: ImmutableList<FilterChipItem>, selectedChipIds: Set<String>, onChipToggled, sortLabel, onSortClick, mode = FilterMode.Single)` ([CrumbsFilterBar.kt:47](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsFilterBar.kt:47))
- `CrumbsBookmarkCard(bookmark, onCardClick, onLongPress: (Offset) -> Unit, ...)` — widened `onLongPress` signature from components slice
- `CrumbsLongPressPopup(visible, onDismiss, actions: ImmutableList<PopupAction>, anchorOffsetPx, ...)` ([CrumbsLongPressPopup.kt:70](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsLongPressPopup.kt:70))
- `EmptyState(title, message, actionText, onActionClick)` ([EmptyState.kt:26](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/EmptyState.kt:26))
- `LoadingCard`, `UserProfileDisplay`, `CrumbsButton(style = ButtonStyle.Primary/Secondary/Ghost)`, `CrumbsBanner`, `CrumbsSnackbar` — all present.

**Edge-to-edge is live in MainActivity** as of the layouts slice (`enableEdgeToEdge()` at [MainActivity.kt:onCreate](app/src/main/java/com/github/jayteealao/crumbs/MainActivity.kt)). Screens that don't yet consume insets currently render TopBar partially under the status bar — this slice fixes that across all 8 screens.

**Test infrastructure status:**
- `core/designsystem/build.gradle` applies the Roborazzi plugin + has the test-dep bundle (template).
- `app/build.gradle`, `feature/twitter/build.gradle`, `feature/reddit/build.gradle` do **not** apply Roborazzi today and have only `testImplementation 'junit:junit:4.13.2'` test deps.
- Only boilerplate `ExampleUnitTest.kt` files exist in the 3 modules' `src/test/` trees.
- `roborazzi.compare.changeThreshold=0.05` already set at [gradle.properties:59](gradle.properties:59).
- `TestTheme.kt` ([core/designsystem/src/test/.../TestTheme.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/TestTheme.kt)) provides `TestCrumbsTheme` wrapper.
- Zero existing `HiltAndroidTest`, `HiltAndroidRule`, `Fake*ViewModel`, or `PreviewParameterProvider` patterns in the repo.
- Roborazzi 1.60.0 + Robolectric 4.16 versions confirmed in `gradle/libs.versions.toml`.

**Navigation graph** ([Crumbs.kt](app/src/main/java/com/github/jayteealao/crumbs/Crumbs.kt), 88 LOC): 4 routes — `SPLASHSCREEN`, `ONBOARDING`, `LOGINSCREEN` (with optional `code` nav arg for OAuth deep-link), `HOMESCREEN/{refreshed}` (Boolean nav arg). `popUpTo(SPLASHSCREEN, inclusive=true)` clears back stack at Onboarding/Login. Screen rewrites must preserve these route semantics — non-goal to change navigation graph.

## Reuse Opportunities

| Candidate | Location | Match | Recommendation |
|-----------|----------|-------|----------------|
| `HomeScaffold` slot API | [layouts/HomeScaffold.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/HomeScaffold.kt) | Exact fit for `HomeScreen` rewrite | **Reuse as-is.** Pass topBar/filterBar/bottomBar/content slots. |
| `OnboardingShell` slot API | [layouts/OnboardingShell.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/OnboardingShell.kt) | Drop-in for `OnboardingScreen` (kills Accompanist pager) | **Reuse as-is.** Shell already wraps the Compose-native `HorizontalPager`. |
| `CrumbsBookmarkCard.onLongPress(Offset)` | components slice | Long-press surface for AllBookmarks + feature feeds | **Reuse as-is.** Capture the `Offset` and pass to `CrumbsLongPressPopup.anchorOffsetPx`. |
| `CrumbsLongPressPopup` | [components/CrumbsLongPressPopup.kt:70](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsLongPressPopup.kt:70) | Anchored popup for the 4-action menu | **Reuse as-is.** Actions wired but handlers log via Timber (behaviors slice activates). |
| `EmptyState` | [components/EmptyState.kt:26](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/EmptyState.kt:26) | AllBookmarks empty state ("NO CRUMBS YET" + CONNECT-ACCOUNT) | **Reuse as-is.** Pass `actionText = "CONNECT AN ACCOUNT"` + `onActionClick = { navController.navigate(LOGINSCREEN) }`. |
| `LoadingCard` | components slice | Skeleton state while paging loads | **Reuse as-is.** Compose inside `LazyColumn` based on `LoadState.Loading`. |
| `UserProfileDisplay` | components slice | LoginScreen per-provider profile chips | **Reuse as-is.** Drive from `username` / `isAccessTokenAvailable` Flows. |
| `CrumbsButton(style = ButtonStyle.Primary)` | components slice | CONNECT TWITTER / CONNECT REDDIT CTAs | **Reuse as-is.** |
| `CrumbsFilterBar` empty/inert wiring | components slice | HomeScreen filterBar slot (per Round-2 PO answer) | **Reuse with no-op handlers.** `chips = persistentListOf(typeChip("ALL"), typeChip("ARTICLES"), typeChip("VIDEOS"))`, `selectedChipIds = emptySet()`, `onChipToggled = { /* TODO behaviors */ }`. |
| `core/designsystem/build.gradle` Roborazzi config | [core/designsystem/build.gradle](core/designsystem/build.gradle) | Template for `app`/`feature/*/` Roborazzi enablement | **Copy plugin id + test-dep bundle** to 3 module build files (per Round-1 Q2). |
| `HomeScaffoldTest` capture pattern | [core/designsystem/src/test/.../layouts/HomeScaffoldTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/layouts/HomeScaffoldTest.kt) | Roborazzi test boilerplate: `@RunWith(RobolectricTestRunner)` + `@GraphicsMode(NATIVE)` + `@Config(sdk = [34])` + `createAndroidComposeRule<ComponentActivity>()` + `CrumbsTheme(darkTheme = …)` + `captureRoboImage("src/test/screenshots/...")` | **Copy + adapt** per-screen test file. |
| `OnboardingShellTest` lazy-layout pattern | [core/designsystem/src/test/.../layouts/OnboardingShellTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/layouts/OnboardingShellTest.kt) | `mainClock.autoAdvance = false` + `waitForIdle()` for Pager + LazyColumn determinism | **Copy** for OnboardingScreenTest + every feed-screen test. |
| `BookmarksViewModel` / `RedditViewModel` Flows | feature modules | State source for `MyScreen(uiState)` stateless params | **Read-only.** Route wrappers project Flows into a `UiState` data class; ViewModels themselves untouched. |
| Existing route signatures in `Crumbs.kt` | [Crumbs.kt](app/src/main/java/com/github/jayteealao/crumbs/Crumbs.kt) | Nav graph routes + arg types | **Reuse as-is.** Routes call `XxxRoute(...)` instead of `XxxScreen(...)`; route-name strings and arg types unchanged. |

No reuse candidate forces a breaking signature change. Route/Screen split is additive on top of existing screen composables.

## Likely Files / Areas to Touch

**Modified source files (8 screen rewrites):**
- `app/src/main/java/com/github/jayteealao/crumbs/screens/SplashScreen.kt` — full rewrite as brutalist wordmark + edge-to-edge.
- `app/src/main/java/com/github/jayteealao/crumbs/screens/onboarding/OnboardingScreen.kt` — full rewrite. Compose `OnboardingShell`. **Remove all `com.google.accompanist.pager.*` imports.** Migrate to `androidx.compose.foundation.pager.HorizontalPager` (handled by `OnboardingShell` internally; OnboardingScreen passes pages + pager state).
- `app/src/main/java/com/github/jayteealao/crumbs/screens/login/LoginScreen.kt` — full rewrite as full-bleed brutalist (Round-1 PO answer). Remove `GradientImage` reference (or keep as background paper texture — confirm during implement if `GradientImage` still exists post-components-slice).
- `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeScreen.kt` — full rewrite to compose `HomeScaffold` with `CrumbsFilterBar` in filterBar slot.
- `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt` — full rewrite. **MaterialTheme.typography.* references → LocalCrumbsTypography.current.*** (workflow rule). Compose feed + empty state + long-press popup integration.
- `app/src/main/java/com/github/jayteealao/crumbs/screens/MapViewScreen.kt` — full rewrite as brutalist "COMING SOON" placeholder.
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt` — hard rewrite to match Option D mock 1:1 (Round-1 PO answer). Keep ViewModel wiring + `PullToRefreshBox`; replace internal `LazyColumn` body + card composable.
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreen.kt` — hard rewrite. Preserves cross-module `BookmarksViewModel` injection for tag state.

**New source files (8 route wrappers):**
- `app/src/main/java/com/github/jayteealao/crumbs/screens/SplashRoute.kt` — `@Composable fun SplashRoute(navController: NavController)`. Bridges Hilt + state to `SplashScreen(uiState, onEvent)`.
- `app/src/main/java/com/github/jayteealao/crumbs/screens/onboarding/OnboardingRoute.kt`
- `app/src/main/java/com/github/jayteealao/crumbs/screens/login/LoginRoute.kt`
- `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt`
- `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksRoute.kt`
- `app/src/main/java/com/github/jayteealao/crumbs/screens/MapViewRoute.kt` (trivial — Map has no VM today)
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksRoute.kt`
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditBookmarksRoute.kt`

**New UiState data classes (8 — one per screen, inlined in the matching Route file or its sibling):**
- `data class SplashUiState(val isLoggedIn: Boolean?)`
- `data class OnboardingUiState(val currentPage: Int)` (pager state hoisted from VM if applicable, else local `remember`)
- `data class LoginUiState(val twitterConnected: Boolean, val redditConnected: Boolean, val redditUsername: String)`
- `data class HomeUiState(val selectedTab: BottomNavTab, val isSearchActive: Boolean, val searchQuery: String)`
- `data class AllBookmarksUiState(val bookmarks: LazyPagingItems<Bookmark>, val tagsForBookmark: Map<String, List<String>>, val isRefreshing: Boolean)` (or pass `LazyPagingItems` directly — see Risks)
- `data class MapViewUiState` — trivial / empty
- `data class TwitterBookmarksUiState(...)` — paged tweets, tags, refreshing flag
- `data class RedditBookmarksUiState(...)` — paged reddit posts, tags

(UiState classes may end up as `class` or top-level params on the stateless Screen — pick the one that minimizes recomposition. See Step 5 below.)

**Modified files (build configuration — 3 modules + nav graph):**
- `app/build.gradle` — apply `id 'io.github.takahirom.roborazzi' version '1.60.0'`; add `testImplementation` deps for roborazzi-core, roborazzi-compose, roborazzi-junit-rule, robolectric, compose-ui-test-junit4, activity-compose (template: copy from `core/designsystem/build.gradle`).
- `feature/twitter/build.gradle` — same plugin + dep bundle add.
- `feature/reddit/build.gradle` — same plugin + dep bundle add.
- `app/src/main/java/com/github/jayteealao/crumbs/Crumbs.kt` — replace `SplashScreen(...)` / `OnboardingScreen(...)` / `LoginScreen(...)` / `HomeScreen(...)` route calls with `SplashRoute(...)` / `OnboardingRoute(...)` / `LoginRoute(...)` / `HomeRoute(...)`. Nav route strings and arg types unchanged.

**New test files (8 Roborazzi screen tests):**
- `app/src/test/java/com/github/jayteealao/crumbs/screens/SplashScreenTest.kt` — 2 goldens.
- `app/src/test/java/com/github/jayteealao/crumbs/screens/onboarding/OnboardingScreenTest.kt` — 2 goldens.
- `app/src/test/java/com/github/jayteealao/crumbs/screens/login/LoginScreenTest.kt` — 2 goldens (light/dark).
- `app/src/test/java/com/github/jayteealao/crumbs/screens/HomeScreenTest.kt` — 2 goldens.
- `app/src/test/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreenTest.kt` — 4 goldens (populated × 2 + empty × 2 — empty state is its own meaningful state per slice spec).
- `app/src/test/java/com/github/jayteealao/crumbs/screens/MapViewScreenTest.kt` — 2 goldens.
- `feature/twitter/src/test/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreenTest.kt` — 2 goldens (populated only — refresh/error states deferred to behaviors slice).
- `feature/reddit/src/test/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreenTest.kt` — 2 goldens.

**New goldens (≥18 PNGs):** total breakdown — 2+2+2+2+(2+2)+2+2+2 = 18 minimum. AllBookmarks empty-vs-populated × 2 themes makes 18 the floor; if Twitter/Reddit feeds also need an empty-state variant, +4 more = 22. **Initial commit ships 18; additional state variants land if implement-stage reveals meaningful divergence.**

## Proposed Change Strategy

Single atomic commit per the implement-stage contract. Build out in the order: (1) enable Roborazzi on 3 modules + smoke-test with a single trivial golden, (2) route/screen split + brutalist rewrite per screen, leaving feature screens for last, (3) record + verify all goldens in one pass, (4) update NavHost to call Routes, (5) `assembleDebug` + `lintDebug` + `verifyRoborazziDebug` gates.

**Locked design decisions** (from PO discovery Rounds 1+2):
- **Route/Screen split applied to all 8 screens** — stateless `XxxScreen(uiState, onEvent)` + Hilt-injecting `XxxRoute(navController)` wrapper. Zero Hilt test infra added.
- **Roborazzi tests in 3 modules** — `app/src/test/`, `feature/twitter/src/test/`, `feature/reddit/src/test/`. Plugin + dep bundle added to each module's `build.gradle`.
- **TwitterBookmarksScreen + RedditBookmarksScreen = hard rewrite** to Option D mock 1:1; ViewModel signatures preserved (cross-module Reddit→Twitter `BookmarksViewModel` reuse intact).
- **LoginScreen = full-bleed brutalist** (no `OnboardingShell` wrapper).
- **Roborazzi tolerance = component-matching:** `roborazzi.compare.changeThreshold=0.05` (already set globally) + `SimpleImageComparator(maxDistance = 0.01f)` per test class.
- **AC-S1 fidelity diff = maintainer-driven manual diff** at verify-stage (runtime-evidence-deferral; same precedent as tokens AC-K4 / toolchain AC6).
- **HomeScreen.filterBar slot = CrumbsFilterBar with empty/inert chips** (3 type chips visible, no selection state, no-op handlers — behaviors slice activates).
- **No resplit** — slice ships as one.

## Step-by-Step Plan

1. **Enable Roborazzi on 3 modules.** For each of `app/build.gradle`, `feature/twitter/build.gradle`, `feature/reddit/build.gradle`:
   - Add `id 'io.github.takahirom.roborazzi' version '1.60.0'` to the `plugins { }` block.
   - Add `testImplementation` lines for: `libs.roborazziCore`, `libs.roborazziCompose`, `libs.roborazziJunitRule`, `libs.robolectric`, `libs.composeUiTestJunit4`, `libs.activityCompose`. Copy from `core/designsystem/build.gradle` lines 70–76 verbatim.
   - **Sanity-check:** run `./gradlew :app:assembleDebug :feature:twitter:assembleDebug :feature:reddit:assembleDebug` after the build-gradle changes; should still compile (Roborazzi plugin alone doesn't change source).

2. **Smoke-test Roborazzi on `app/` with a trivial golden.** Add a temporary `app/src/test/java/com/github/jayteealao/crumbs/RoborazziSmokeTest.kt` that renders `CrumbsTheme { Text("smoke") }` and captures to `app/src/test/screenshots/smoke.png`. Run `./gradlew :app:recordRoborazziDebug`. Confirm PNG is produced. **Delete the smoke test before commit** — purely a local checkpoint. Repeat for `feature/twitter/` + `feature/reddit/` (or skip if `app/` works; the plugin config is identical).

3. **Refactor `SplashScreen` to Route/Screen split** + brutalist visuals.
   - Move existing content into `SplashScreen(uiState: SplashUiState, onTimeout: () -> Unit)` stateless composable. Body: full-bleed `Box(fillMaxSize)` with `Modifier.background(LocalCrumbsColors.current.background).windowInsetsPadding(WindowInsets.safeDrawing)`. Centered wordmark "crumbs•" in `LocalCrumbsTypography.current.displayHeadline` with accent bullet.
   - Add `SplashRoute(navController: NavController)` that owns the `LaunchedEffect(...) { delay(1.seconds); navController.navigate(...) }` logic. Pass `SplashUiState(isLoggedIn = …)` derived from existing auth-check helper (if any) or from a Hilt-injected `AuthSessionRepository.isLoggedIn()` flow.
   - testTag `splash-screen` on the root Box; `splash-wordmark` on the centered Text.

4. **Refactor `OnboardingScreen` to Route/Screen split** + compose `OnboardingShell` + remove Accompanist.
   - `OnboardingScreen(pages: ImmutableList<@Composable () -> Unit>, pagerState: PagerState, onSkip: () -> Unit, onDone: () -> Unit)` stateless. Body: `OnboardingShell(pages = pages, pagerState = pagerState, footerCtaText = if (pagerState.currentPage < pages.size - 1) "NEXT" else "DONE", onFooterCtaClick = { ... })`.
   - 4 brutalist pages — each page is `@Composable { Column(...) { Text(kicker, mono); Text(title, displaySmall); Text(body, bodyMono) } }` per Option D mock.
   - `OnboardingRoute(navController: NavController)` owns `rememberPagerState(pageCount = { 4 })`, `rememberCoroutineScope()`, and the navigate-to-Login on done.
   - **Delete all `com.google.accompanist.pager.*` imports from this file.** `OnboardingShell` already uses Compose-native pager internally — no caller pager API used here.
   - testTags: `onboarding-screen`, `onboarding-page-{0..3}`, `onboarding-skip`, `onboarding-cta`.

5. **Refactor `LoginScreen` to Route/Screen split** + full-bleed brutalist.
   - `LoginScreen(uiState: LoginUiState, onConnectTwitter: () -> Unit, onConnectReddit: () -> Unit, onSkipAuth: () -> Unit = {})` stateless. Body: `Column(fillMaxSize, windowInsetsPadding(WindowInsets.safeDrawing).background(LocalCrumbsColors.current.background)) { Text(kicker = "WELCOME", typography.kicker); Spacer; Text("CONNECT YOUR ACCOUNTS", typography.displaySmall); Spacer; UserProfileDisplay(...); CrumbsButton(text = "CONNECT TWITTER", style = ButtonStyle.Primary, onClick = onConnectTwitter); UserProfileDisplay(...); CrumbsButton(text = "CONNECT REDDIT", style = ButtonStyle.Primary, onClick = onConnectReddit) }`.
   - `LoginRoute(navController: NavController, authorizationCode: String? = null)` injects `LoginViewModel + RedditViewModel = hiltViewModel()`, collects `isLoggedIn` / `isAccessTokenAvailable` / `username` Flows into `LoginUiState`, fires `getAccessToken(authorizationCode)` if the deep-link arg is non-null. OAuth handlers (`signInWithTwitter`, `redditViewModel.authIntent()`) **untouched** at the VM layer — only the screen composable changes (regression-safe per AC-R3 / slice spec line 71).
   - testTags: `login-screen`, `login-twitter-cta`, `login-reddit-cta`, `login-twitter-profile`, `login-reddit-profile`.
   - **Confirm `GradientImage` references**: if `GradientImage` still exists post-components-slice, replace with a brutalist paper background (`Box(background = LocalCrumbsColors.current.background)`). If it was already deleted, the import is gone and nothing to do.

6. **Refactor `HomeScreen` to Route/Screen split** + compose `HomeScaffold` + wire FilterBar empty state.
   - `HomeScreen(uiState: HomeUiState, onTabSelected, onSearchQueryChange, onSearchActiveChange, tabContent: @Composable (BottomNavTab, PaddingValues) -> Unit)` stateless. Body:
     ```
     HomeScaffold(
       topBar = { CrumbsTopBar(kickerText = "CRUMBS", wordmark = "crumbs•", searchQuery = uiState.searchQuery, onSearchQueryChange = onSearchQueryChange, isSearchActive = uiState.isSearchActive, onSearchActiveChange = onSearchActiveChange) },
       filterBar = { CrumbsFilterBar(count = 0, chips = persistentListOf(FilterChipItem("all", "ALL"), FilterChipItem("articles", "ARTICLES"), FilterChipItem("videos", "VIDEOS")), selectedChipIds = emptySet(), onChipToggled = { /* TODO behaviors slice */ }, sortLabel = "RECENT", onSortClick = { /* TODO behaviors slice */ }, mode = FilterMode.Single) },
       bottomBar = { CrumbsBottomNav(selectedTab = uiState.selectedTab, onTabSelected = onTabSelected) }
     ) { padding -> tabContent(uiState.selectedTab, padding) }
     ```
   - `HomeRoute(navController: NavController, refreshed: Boolean)` owns the tab-dispatch table — when `selectedTab == TWITTER`, render `TwitterBookmarksRoute(navController)`; when `REDDIT`, render `RedditBookmarksRoute(navController)`; when `ALL`, render `AllBookmarksRoute(navController, padding)`; when `MAP`, render `MapViewRoute(padding)`. Padding is passed through `tabContent`'s `(BottomNavTab, PaddingValues) -> Unit` slot.
   - testTags: `home-screen`, `home-tab-content`.

7. **Refactor `AllBookmarksScreen` to Route/Screen split** + brutalist feed + empty state + long-press popup.
   - **First — convert `MaterialTheme.typography.titleMedium` references to `LocalCrumbsTypography.current.<closest equivalent>`** (likely `titleSection` or `bodyMono`; choose by visual fit). Workflow rule: zero `MaterialTheme.*` references survive in the diff.
   - `AllBookmarksScreen(uiState: AllBookmarksUiState, onCardClick: (Bookmark) -> Unit, onLongPress: (Bookmark, Offset) -> Unit, onConnectAccountClick: () -> Unit, onPopupAction: (PopupAction, Bookmark) -> Unit, onPopupDismiss: () -> Unit, contentPadding: PaddingValues)` stateless.
   - Empty state: when `uiState.bookmarks.itemCount == 0 && !uiState.bookmarks.loadState.refresh.isLoading`, render `EmptyState(title = "NO CRUMBS YET", message = "Connect an account to start saving bookmarks.", actionText = "CONNECT AN ACCOUNT", onActionClick = onConnectAccountClick)`.
   - Populated state: `LazyColumn(contentPadding = contentPadding) { items(uiState.bookmarks.itemCount) { i -> uiState.bookmarks[i]?.let { CrumbsBookmarkCard(it, onCardClick = { onCardClick(it) }, onLongPress = { offset -> onLongPress(it, offset) }) } }; … LoadState handling using LoadingCard for loading rows }`.
   - Long-press popup: hoist `var popupBookmark: Bookmark? by remember { mutableStateOf(null) }` + `var popupAnchor: Offset by remember { mutableStateOf(Offset.Zero) }` at screen scope; the `onLongPress` callback sets both. Render `CrumbsLongPressPopup(visible = popupBookmark != null, onDismiss = onPopupDismiss, actions = persistentListOf(PopupAction.Open, PopupAction.Share, PopupAction.EditTags, PopupAction.Delete), anchorOffsetPx = popupAnchor)`. Action lambdas inside each `PopupAction` log via `Timber.d("AllBookmarks long-press action: $it on ${popupBookmark}")` and call `onPopupAction(it, popupBookmark!!)` — behaviors slice swaps these for real handlers.
   - `AllBookmarksRoute(navController, contentPadding)` injects the 3 ViewModels, collects paging + tags + refreshing Flows into `AllBookmarksUiState`, owns the navigate-to-Login on empty-state CTA tap.
   - testTags: `all-bookmarks-screen`, `all-bookmarks-empty`, `all-bookmarks-feed`, `all-bookmarks-connect-cta`.

8. **Refactor `MapViewScreen` to Route/Screen split** + brutalist COMING SOON.
   - `MapViewScreen(contentPadding: PaddingValues)` stateless. Body: `Column(fillMaxSize.padding(contentPadding).windowInsetsPadding(WindowInsets.safeDrawing), Alignment.CenterHorizontally, Arrangement.Center) { Text("MAP", typography.kicker, color = colors.onSurfaceVariant); Text("COMING SOON", typography.displaySmall, color = colors.ink); Spacer(16.dp); Box(Modifier.size(width = 240.dp, height = 160.dp).border(BorderStroke(stroke.regular, colors.ink))) /* ink-stroked panel */ }`. No map SDK.
   - `MapViewRoute(contentPadding: PaddingValues)` is trivial — just calls `MapViewScreen(contentPadding)`. No VM.
   - testTags: `map-view-screen`, `map-view-coming-soon`.

9. **Refactor `TwitterBookmarksScreen` (feature/twitter/) to Route/Screen split** + hard brutalist rewrite.
   - `TwitterBookmarksScreen(uiState: TwitterBookmarksUiState, onCardClick, onLongPress, onPopupAction, onPopupDismiss, onRefresh, contentPadding: PaddingValues)` stateless. Body:
     - Outer `PullToRefreshBox(isRefreshing = uiState.isRefreshing, onRefresh = onRefresh)` — kept for functional pull-to-refresh.
     - Inside: `LazyColumn(contentPadding = contentPadding)` with `CrumbsBookmarkCard` rows, `LoadingCard` for loading state, `EmptyState` for empty (or feed-specific empty copy: "NO TWITTER BOOKMARKS YET").
     - Long-press popup wired same as AllBookmarks.
     - **No own TopBar / BottomBar** — this screen sits inside `HomeScaffold.content` rendered by `HomeRoute`.
     - Replace any inline `Color(0xFF…)` literal or `MaterialTheme.*` reference with `LocalCrumbsColors.current.*` / `LocalCrumbsTypography.current.*`.
   - `TwitterBookmarksRoute(navController, twitterAuthCode: String? = null, contentPadding: PaddingValues)` injects `BookmarksViewModel + LoginViewModel = hiltViewModel()`, collects `pagingFlowData()` + `isRefreshing` + `tagsForTweet` into UiState, fires `loginViewModel.continueOAuth(twitterAuthCode)` when non-null.
   - testTags: `twitter-bookmarks-screen`, `twitter-bookmarks-feed`, `twitter-bookmarks-empty`.

10. **Refactor `RedditBookmarksScreen` (feature/reddit/) to Route/Screen split** + hard brutalist rewrite.
    - Pattern mirrors Twitter screen. Stateless `RedditBookmarksScreen(uiState: RedditBookmarksUiState, ...)`.
    - `RedditBookmarksRoute(navController, redditAuthCode: String? = null, contentPadding: PaddingValues)` injects **both** `RedditViewModel + BookmarksViewModel = hiltViewModel()` (cross-module — preserves Reddit→Twitter coupling for tag state); collects `pagingFlowData()` (Reddit) + `tagsForTweet` (Twitter VM, used here for Reddit post tags as the existing code already does), `isAccessTokenAvailable`, `username` into UiState.
    - **No own TopBar / BottomBar** — sits inside `HomeScaffold.content`.
    - Replace any `MaterialTheme.*` / hardcoded `Color(0xFF…)` references.
    - testTags: `reddit-bookmarks-screen`, `reddit-bookmarks-feed`, `reddit-bookmarks-empty`.

11. **Update NavHost in `Crumbs.kt`** to call `XxxRoute(...)` for the 4 top-level routes (Splash, Onboarding, Login, Home). Route name strings + nav arg types unchanged. `HomeRoute` internally dispatches the tab screens.

12. **Add 8 Roborazzi test files.** Each follows this template (matching the `HomeScaffoldTest` / `OnboardingShellTest` pattern with the lazy-layout discipline for feed screens):
    ```kotlin
    @RunWith(RobolectricTestRunner::class)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    class XxxScreenTest {
      @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

      @Test fun xxxScreen_default_light() {
        composeTestRule.mainClock.autoAdvance = false   // for feed screens with LazyColumn / Pager
        composeTestRule.setContent {
          CrumbsTheme(darkTheme = false) {
            XxxScreen(uiState = fakeUiState(...), onEvent = { /* no-op */ })
          }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
          "src/test/screenshots/XxxScreen_default_light.png",
          roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(imageComparator = SimpleImageComparator(maxDistance = 0.01f)))
        )
      }

      @Test fun xxxScreen_default_dark() { /* darkTheme = true */ }
    }
    ```
    For `AllBookmarksScreenTest`, add `xxxScreen_empty_{light,dark}` variants with `bookmarks.itemCount = 0`.
    For paging-driven feeds, hand-roll a small fake `LazyPagingItems<Bookmark>` (use `flowOf(PagingData.from(listOf(fakeBookmark1, fakeBookmark2))).collectAsLazyPagingItems()` in the test composition).

13. **Record + verify Roborazzi goldens.** Run sequentially:
    - `./gradlew :app:recordRoborazziDebug` → 12 PNGs at `app/src/test/screenshots/`.
    - `./gradlew :feature:twitter:recordRoborazziDebug` → 2 PNGs at `feature/twitter/src/test/screenshots/`.
    - `./gradlew :feature:reddit:recordRoborazziDebug` → 2 PNGs at `feature/reddit/src/test/screenshots/`.
    - Visually inspect each PNG for sanity: brutalist palette + edge-to-edge inset handling correct + no chrome bleed-through from Material defaults.
    - Then `./gradlew :app:verifyRoborazziDebug :feature:twitter:verifyRoborazziDebug :feature:reddit:verifyRoborazziDebug` to confirm record passes verify against itself.

14. **AC-S3 grep gate.** Run `grep -r "com.google.accompanist.pager" --include="*.kt"` from repo root. Expected output: **zero matches** (after `OnboardingScreen` rewrite). If any match remains in feature modules' classpaths (transitive), confirm via `./gradlew :app:dependencies | grep accompanist-pager` — if Accompanist Pager is still on the classpath but no source imports it, this is fine; the grep gate is source-level only.

15. **Lint + assemble gates.**
    - `./gradlew :app:lintDebug :feature:twitter:lintDebug :feature:reddit:lintDebug` — green expected.
    - `./gradlew :app:assembleDebug` — confirms full app builds with all 8 screens migrated + NavHost wired to Routes.
    - `./gradlew :app:testDebugUnitTest :feature:twitter:testDebugUnitTest :feature:reddit:testDebugUnitTest` — runs the new Roborazzi screen tests + existing `ExampleUnitTest`s.

16. **Atomic commit** with subject `feat(screens): rewrite all 8 screens to brutalist; migrate off Accompanist Pager; add screen-level Roborazzi suite`. Body: per-screen LOC delta, list of new test files + golden count, NavHost route-call update, the MaterialTheme→LocalCrumbs* migration in AllBookmarks/feature screens, Roborazzi plugin enablement on 3 modules. Do not push.

## Test / Verification Plan

### Automated checks

- **lint/typecheck**: `./gradlew :app:lintDebug :feature:twitter:lintDebug :feature:reddit:lintDebug` — green expected; new code follows established patterns.
- **unit tests**: `./gradlew :app:testDebugUnitTest :feature:twitter:testDebugUnitTest :feature:reddit:testDebugUnitTest` — runs the 8 new Roborazzi screen test classes (~18 captures) + ExampleUnitTest stubs.
- **Roborazzi gate**: `./gradlew :app:verifyRoborazziDebug :feature:twitter:verifyRoborazziDebug :feature:reddit:verifyRoborazziDebug` — must pass at 5% changed-pixel + 1% RGB tolerance.
- **build gate**: `./gradlew :app:assembleDebug` — full app links with all 8 screens migrated + NavHost route-call updates + Roborazzi plugins added to all 3 modules.
- **AC-S3 grep gate** (Accompanist Pager removal): `grep -r "com.google.accompanist.pager" --include="*.kt"` returns zero matches. Closes AC-S3 entirely within this slice.
- **MaterialTheme grep gate** (workflow rule): `grep -rn "MaterialTheme\." --include="*.kt" app/src/main/java feature/twitter/src/main/java feature/reddit/src/main/java` returns zero matches in screen files (theme files like `CrumbsTheme.kt` may legitimately reference `MaterialTheme` if it wraps one — re-confirm at implement time).

### Interactive verification (human-in-the-loop)

Per workflow's confirmed `stack: [android]` and `stack.testing: [junit, compose-ui-test]` (with `stack.available-skills: [android-cli, lazylogcat, edge-to-edge, adaptive, styles, testing-setup]`):

**AC-S1 — ≥95% match to Option D mock at Pixel 6 light + dark** (8 screens × 2 themes):

- **Platform & tool**: Android — Maintainer-driven manual diff (per Round-2 PO answer). For each screen, the maintainer opens the rendered Roborazzi PNG side-by-side with the corresponding Option D source (`Crumbs-handoff/option-d-screens.jsx` rendered in a browser, or the `verify-*.jpg` reference screenshots in the handoff bundle) and visually adjudicates ≥95% match.
- **Companion skills**: none required for the diff itself; `android-cli` for optionally booting the app on Medium_Phone_API_36 emulator if maintainer wants live-render confirmation; `lazylogcat` if any Compose rendering warnings need capture.
- **Steps**: (a) inspect each of the ~18 Roborazzi PNGs under `app/src/test/screenshots/` + `feature/*/src/test/screenshots/`; (b) compare against the corresponding Option D HTML mock in a 412×920 desktop viewport; (c) for each screen×theme pair, record subjective match estimate; (d) sign off if all ≥95%, else flag specifically which pixel-region differs and route back to implement.
- **Evidence capture**: maintainer's sign-off comment in the verify artifact + optional comparison screenshots committed under `.ai/workflows/brutalist-redesign/verify-evidence/screens/`.
- **Pass criteria**: all 8 screens × 2 themes meet the ≥95% subjective bar. **Same precedent as tokens AC-K4 (handoff diff) + toolchain AC6 (visual diff of regenerated goldens).**
- **Register at verify-stage as `runtime-evidence-deferral`** since maintainer is the canonical adjudicator and the diff is not automatable in this repo.

**AC-S2 — Maestro happy-path end-to-end flow:**

- **Platform & tool**: Android Maestro — **deferred to the dedicated `maestro` slice**. Same precedent as toolchain AC4, components AC-C6, layouts AC-L5. The maestro slice writes `maestro/happy_path.yaml` against the live testTag scaffolding this slice ships.
- **Register at verify-stage as `runtime-evidence-deferral`** with `cleared-by: <maestro-slice-implement-commit>`.

**Remaining interactive ACs from slice spec:**

- AC (line 67 — manual visual review on Pixel 6 emulator): collapses onto AC-S1's maintainer manual diff — `android-cli` can boot the emulator and `installDebug` the app for live-render confirmation if the PNG diff alone isn't enough. Deferred to verify-stage with the same deferral entry.
- AC (line 68 — MapView shows "COMING SOON" + no maps SDK linked): closes within this slice — automated `grep -r "com.google.android.gms.maps\|com.mapbox" --include="*.kt" --include="*.gradle"` returns zero matches AND Roborazzi golden for `MapViewScreen` shows the "COMING SOON" treatment.
- AC (line 69 — AllBookmarks empty state CONNECT-AN-ACCOUNT button navigates to LoginScreen): closes within this slice via a non-Roborazzi Compose UI test in `AllBookmarksScreenTest.kt`:
  ```kotlin
  @Test fun emptyState_connectAccountCta_invokesCallback() {
    var cb = false
    composeTestRule.setContent { CrumbsTheme { AllBookmarksScreen(uiState = emptyUiState, onConnectAccountClick = { cb = true }, ...) } }
    composeTestRule.onNodeWithTag("all-bookmarks-connect-cta").performClick()
    assertTrue(cb)
  }
  ```
- AC (line 70 — long-press popup appears with 4 actions): closes within this slice via a Compose UI test that finds a card by testTag, calls `performTouchInput { longClick() }`, asserts `CrumbsLongPressPopup` becomes visible by testTag `crumbs-long-press-popup` (already a tag from components slice). 4-action count is verified by `onAllNodesWithTag("popup-action-row").assertCountEquals(4)`.
- AC (line 71 — OAuth flows fire unchanged from LoginScreen): closes via `git diff` line-count of `LoginViewModel.kt` / `RedditViewModel.kt` showing **zero changes** since slice baseline. **Regression check is grep-level** — no functional test required at this stage.

### Compose UI tests (in-process, AC-coverage)

- `AllBookmarksScreenTest.emptyState_connectAccountCta_invokesCallback()` — AC line 69.
- `AllBookmarksScreenTest.longPress_opensPopupWith4Actions()` — AC line 70.
- (Optional) `LoginScreenTest.connectTwitter_invokesCallback()` / `connectReddit_invokesCallback()` — defensive regression check for OAuth callback wiring.

## Risks / Watchouts

- **`LazyPagingItems` in Roborazzi tests**. Paging-Compose 3.3.6 requires a `Flow<PagingData<T>>.collectAsLazyPagingItems()` call inside the composable. In tests, hand-rolling `flowOf(PagingData.from(listOf(fakeBookmark1, ...)))` works but pagination state (`loadState.refresh`, `loadState.append`) must be a real `LoadState.NotLoading(endOfPaginationReached = true)`. Web-research note: `PagingData.from(list)` constructs exactly that. If goldens render mid-load, add `composeTestRule.waitUntil(timeoutMillis = 2000) { items.itemCount > 0 }`.
- **`Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` vs `Modifier.statusBarsPadding()` confusion**. With `enableEdgeToEdge()` live (from layouts slice), full-bleed screens that need to avoid the status bar should use `safeDrawing` (covers status + nav + IME + cutouts), not `statusBarsPadding()` alone. `HomeScaffold` already consumes status-bar inset internally via its `Column { topBar; filterBar }` wrapper; screens inside `HomeScaffold.content` should `Modifier.consumeWindowInsets(padding)` to prevent double-padding if they add further inset modifiers.
- **NavHost route wiring drift**. Replacing 4 route-call sites (Splash/Onboarding/Login/Home) in `Crumbs.kt` is a 4-line edit, but if a route arg type changes accidentally, deep-link callbacks for OAuth (`?code={code}`) break. **Mitigation**: keep nav arg types byte-identical; only the function-name token changes (`SplashScreen` → `SplashRoute`).
- **Cross-module `BookmarksViewModel` injection in `RedditBookmarksRoute`**. Hilt resolves both VMs through `hiltViewModel()`. If a future refactor moves `BookmarksViewModel` out of `feature/twitter` or into a Reddit-specific tag VM, this Route would break silently at runtime (DI graph). **Mitigation**: leave the existing cross-module pattern intact in this slice — explicit comment in `RedditBookmarksRoute` flagging the coupling. Behaviors slice may revisit.
- **Roborazzi tests cannot drive `LaunchedEffect` for nav timers**. `SplashScreenTest` will render the splash visual but cannot exercise `LaunchedEffect { delay(...); navController.navigate(...) }`. **Mitigation**: test the stateless `SplashScreen` composable (no nav effect) for the visual; the timer + navigation lives in `SplashRoute` and is verified via Maestro happy-path in the maestro slice, not here.
- **PullToRefreshBox in feature screens with `enableEdgeToEdge()`**. Material3's `PullToRefreshBox` has its own inset behavior; combined with `HomeScaffold.content` padding, the refresh indicator may double-pad. **Mitigation**: pass the `HomeScaffold` `PaddingValues` into the screen's LazyColumn `contentPadding`, NOT to `PullToRefreshBox`'s modifier. PullToRefresh wraps the LazyColumn, not the other way around.
- **TestTag name churn risk for Maestro slice**. The maestro slice's flows will assert on testTag names this slice introduces (`home-screen`, `all-bookmarks-feed`, `twitter-bookmarks-screen`, etc.). **Discipline**: treat the testTag names listed in Steps 3–10 as public-API-stable from this slice forward. Renaming any of them later breaks Maestro flows. Reviewers should call out tag renames in any future PR.
- **`AllBookmarksScreen` empty-state navigation in Compose UI test**. `onConnectAccountClick` lambda fires `navController.navigate(LOGINSCREEN)`. In the in-process Compose UI test (Step 12), the navController is unavailable — the test asserts only that the callback fired, not that navigation occurred. Maestro slice covers the navigation half.
- **`androidx.paging:paging-compose:3.3.6` and `LazyPagingItems<Bookmark>` parameter stability**. Passing `LazyPagingItems` directly as a Composable parameter triggers recomposition every paging cycle. **Mitigation**: pass `LazyPagingItems` only into the stateless Screen at the call site from the Route — do NOT include it as a field on `AllBookmarksUiState` data class (because data-class fields get key-equality on every emit). Concretely: `AllBookmarksScreen(items: LazyPagingItems<Bookmark>, otherState: AllBookmarksUiState, ...)` rather than packing `items` into UiState.
- **Maintainer manual diff scope creep**. Maintainer must diff 8 screens × 2 themes = 16 image pairs against the Option D mocks. Each pair takes ~1 min for a careful subjective ≥95% read = ~20-30 min total. **Mitigation**: register the deferral with explicit screen-by-screen sign-off checklist in the verify artifact, so partial-diff progress is tracked.

## Dependencies on Other Slices

- **`layouts` (verified-partial)**: this slice's primary dependency. Consumes `HomeScaffold` slot API (HomeScreen) + `OnboardingShell` slot API (OnboardingScreen). `OverlayShell` not consumed in this slice — behaviors slice owns it.
- **`components` (verified-partial)**: every screen consumes brutalist atomic components. `CrumbsBookmarkCard.onLongPress(Offset)` widening from components slice flows through to AllBookmarks + Twitter + Reddit screens.
- **`tokens` (verified-partial)**: implicit via components. `LocalCrumbsColors.current.*`, `LocalCrumbsTypography.current.*`, `LocalCrumbsSpacing.current.*` referenced throughout.
- **`toolchain` (verified-partial)**: implicit. Roborazzi 1.60.0 plugin enablement on 3 new modules reuses the catalog pins from toolchain slice.
- **`behaviors` (deferred)** *consumes* this slice's testTag scaffolding + UiState surface area + popup-action `TODO()` stubs. The slice spec's OUT list says behaviors slice owns: filter chip selection logic, long-press action handlers (open/share/edit-tags/delete), soft-delete + tombstone wiring, sync-error banner trigger.
- **`maestro` (deferred)** *consumes* this slice's testTags for AC-S2 happy-path + behavior-specific flows. Same emulator+Maestro evidence run will discharge layouts AC-L5 + toolchain AC4 + components AC-C6 + this slice's AC-S2.

## Assumptions

- **Route/Screen split is universally applied**. Every screen — even simple ones like Splash + MapView — gets the split. Trivial Routes (Splash, MapView) are 5–10 LOC wrappers; the consistency value (test infra rules + future ViewModel injection without churn) justifies the small redundancy.
- **`GradientImage` is acceptable to remove** if it still exists. The components slice may have already deleted it as part of orphan cleanup; LoginScreen rewrite drops the reference either way. If `GradientImage` is still imported elsewhere, a grep at implement time will catch.
- **`feature/twitter` and `feature/reddit` ViewModels are byte-stable across this slice**. Slice spec line 71 (AC-R3) + intake non-goal — confirmed by `git diff --stat feature/twitter/src/main/java/com/github/jayteealao/twitter/BookmarksViewModel.kt feature/reddit/src/main/java/com/github/jayteealao/reddit/RedditViewModel.kt` showing zero source-line changes at slice end.
- **Reddit's cross-module `BookmarksViewModel` injection is preserved**. The Hilt graph already resolves it; nothing in this slice changes that contract.
- **18 goldens is the floor**. If implement-stage reveals a meaningful state (e.g. feed error state, network-disconnected banner, sync-in-progress) that diverges meaningfully and is in-scope per the slice spec, +N goldens land in this slice's atomic commit. The slice spec line 53 anchors "12 minimum" — this plan upgrades to 18 to cover empty-state-as-meaningful and the 2 feature feeds.
- **AC-S1 maintainer diff fits within verify-stage timing**. 16-image side-by-side diff is ~20-30 min of focused review. If the diff turns up substantive mismatches on multiple screens, verify-stage will route back to implement with specific per-screen feedback rather than try to fix in the verify-owned fix loop.
- **Compose 1.11.1's `LazyPagingItems` + Roborazzi compatibility**. Sub-agent 4's web research did not surface any 2026 regression; the components slice already uses `PagingData.from(...)` in some preview-stage patterns (not in test, but the pattern is documented). Pattern is proven within the same compose version family.
- **No new `BottomNavTab` destinations**. Tab set stays `Twitter, Reddit, All, Map` per the locked-decisions block in `00-index.md`. Tab dispatch table in `HomeRoute` matches `selectedTab` against these 4 only.

## Blockers

None. Upstream slices (toolchain, tokens, components, layouts) ship all required APIs at `verified-partial`; web research surfaced no Compose 1.11.1 / Material3 1.4.0 / Roborazzi 1.60.0 / Robolectric 4.16 / Paging-Compose 3.3.6 / Accompanist→native Pager migration blockers; PO discovery resolved every cross-cutting design decision in 2 rounds.

## Freshness Research

Captured by parallel web-research sub-agent. Top-level takeaways that shape plan steps:

- **Accompanist Pager fully deprecated**; canonical replacement is `androidx.compose.foundation.pager.HorizontalPager` with `rememberPagerState(pageCount = { N })` lambda. `currentPage`/`scrollToPage`/`animateScrollToPage` keep names but live on the new `PagerState`. Subtle gotcha: `currentPageOffset` is replaced by `currentPageOffsetFraction`. Compose-native pager inside `Column` + `Modifier.weight(1f)` works fine (bounded measurement). ([accompanist#1463](https://github.com/google/accompanist/issues/1463), [pager docs](https://developer.android.com/reference/kotlin/androidx/compose/foundation/pager/package-summary))
- **Route/Screen split is the 2026 idiomatic pattern for Roborazzi + Hilt-injecting screens.** Factor as stateless `XxxScreen(uiState, onEvent)` + thin `XxxRoute(viewModel = hiltViewModel())`. Tests call the stateless Screen directly — zero Hilt infra. ([Roborazzi README](https://github.com/takahirom/roborazzi), [Now-in-Android screenshot tests](https://github.com/android/nowinandroid))
- **Roborazzi 1.60.x tolerance config**: `changeThreshold` is the *fraction of differing pixels* (not RGB delta); for RGB tolerance use `SimpleImageComparator(maxDistance = 0.01f)` per test class. Screen-level captures with custom TTFs render with more subpixel-AA drift than components — but the workflow's 5% changed-pixel global ceiling (set at `gradle.properties:59`) already accommodates this. Lazy layouts (LazyColumn, Pager) need `mainClock.autoAdvance = false` + `waitForIdle()` before capture. ([Roborazzi README — CompareOptions](https://github.com/takahirom/roborazzi), [How to Solve Flaky Roborazzi Tests](https://medium.com/@takahirom/how-to-solve-flaky-robolectric-and-roborazzi-tests-5731e55581cd))
- **Edge-to-edge inset consumption pattern**: `HomeScaffold` already consumes status/nav-bar insets internally; child screens use `Modifier.consumeWindowInsets(padding)` before adding further inset modifiers to avoid double-padding. For full-bleed screens (Splash, MapView placeholder), `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` on the interactive content layer is the canonical 2026 pattern. ([Set up window insets — Jetpack Compose](https://developer.android.com/develop/ui/compose/system/insets-ui), [Use Material 3 insets](https://developer.android.com/develop/ui/compose/system/material-insets))

No CVEs, deprecation notices, or version-pin issues affecting this slice. Compose 1.11.x + Material3 1.4.0 have no known screen-capture-determinism regressions in Roborazzi 1.60.x.

## Revision History

*(none — initial plan)*

## Recommended Next Stage

- **Option A (default):** `/wf implement brutalist-redesign screens` — plan is execution-ready; 16-step atomic commit with clear scope. **`/compact` recommended before proceeding** — planning research (4 sub-agent reports + 2 discovery rounds) is noise for implement.
- **Option B:** `/wf plan brutalist-redesign screens <feedback>` — revise this plan if any Round 1/Round 2 decision feels wrong on second read (e.g. reconsider hard-rewrite for feature screens vs. light reskin, or revisit Route/Screen split scope).
- **Option C:** `/wf slice brutalist-redesign` — revisit slice boundaries. **Not recommended** — discovery resolved the resplit question explicitly (Round 2 Q5 → keep single slice). No boundary problem surfaced during planning.
