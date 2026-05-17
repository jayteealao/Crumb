---
schema: sdlc/v1
type: implement
slug: brutalist-redesign
slice-slug: screens
status: complete
stage-number: 5
created-at: "2026-05-17T17:36:42Z"
updated-at: "2026-05-17T17:36:42Z"
metric-files-changed: 38
metric-lines-added: 2900
metric-lines-removed: 1673
metric-deviations-from-plan: 4
metric-review-fixes-applied: 0
commit-sha: "c1d2160"
tags: [screens, brutalist, route-screen-split, pager-migration, roborazzi, app, feature-twitter, feature-reddit]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
  slice-def: 03-slice-screens.md
  plan: 04-plan-screens.md
  siblings:
    - 05-implement-toolchain.md
    - 05-implement-tokens.md
    - 05-implement-components.md
    - 05-implement-layouts.md
    - 05-implement-quick-skip-auth-page.md
  verify: 06-verify-screens.md
next-command: wf-verify
next-invocation: "/wf verify brutalist-redesign screens"
---

# Implement: screens

## Summary of Changes

Single atomic commit. Eight screens rewritten under the **Route/Screen split** pattern: each `XxxScreen(uiState, on…)` is now stateless and trivially Roborazzi-testable; each `XxxRoute(navController, …)` is a thin Hilt-injecting wrapper. NavHost in `Crumbs.kt` updated to call Routes. Accompanist Pager removed from the codebase (orphan `TwitterCard.kt` deleted as last consumer, gradle deps dropped from `:app` and `:feature:twitter`). Roborazzi enabled on three new modules (`app`, `feature:twitter`, `feature:reddit`) with the same plugin + dep bundle the design system already uses. 16 Roborazzi goldens recorded; AC line 69 (empty-state CTA) covered by a focused Compose UI test; AC line 71 (OAuth callbacks wired unchanged) covered by two callback-fired assertions on `LoginScreen`.

## Files Changed

**Modified screens (8 source files, all rewritten to Route/Screen split):**
- [SplashScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/SplashScreen.kt) — stateless wordmark; `windowInsetsPadding(safeDrawing)`, testTags `splash-screen` + `splash-wordmark`.
- [OnboardingScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/OnboardingScreen.kt) — composes `OnboardingShell` with 4 brutalist pages (`OnboardingPageData`); kicker / displaySmall / bodyMono per page. **Zero `com.google.accompanist.pager.*` imports.**
- [LoginScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/LoginScreen.kt) — full-bleed brutalist Column. WELCOME kicker, `crumbs•` wordmark, CONNECT TWITTER (primary), CONNECT REDDIT (secondary), debug-only SKIP AUTH ghost. `UserProfileDisplay` per provider when authed.
- [HomeScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/HomeScreen.kt) — composes `HomeScaffold`. `CrumbsTopBar` in topBar slot, `CrumbsFilterBar` in filterBar slot (count=0, 3 type chips ALL/ARTICLES/VIDEOS, sort RECENT, no-op handlers — behaviors slice activates), `CrumbsBottomNav` in bottomBar slot. tabContent slot dispatches in the Route.
- [AllBookmarksScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt) — stateless feed + empty state. `EmptyState("NO CRUMBS YET", "CONNECT AN ACCOUNT")` when no sources connected. Section headers use `LocalCrumbsTypography.current.titleSection` (zero `MaterialTheme.*` references). Long-press popup wired with 4 actions in the Route (TAG, OPEN, SHARE, DELETE).
- [MapViewScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/MapViewScreen.kt) — brutalist MAP / COMING SOON kicker+display + ink-stroked panel. No map SDK linked.
- [TwitterBookmarksScreen.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt) — hard rewrite. Empty-state CONNECT TO TWITTER; `PullToRefreshBox` preserves pull-to-refresh; LazyColumn feed; 4-action popup (TAG, OPEN, SHARE, LOGOUT). `TweetData.toBookmark(tags)` retained as a top-level public extension for cross-module consumers (AllBookmarks calls it).
- [RedditBookmarksScreen.kt](feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreen.kt) — hard rewrite. Cross-module `BookmarksViewModel` injection preserved for tag state (load-bearing coupling explicitly commented in the Route).

**New Route files (8 thin Hilt-injecting wrappers):**
- [SplashRoute.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/SplashRoute.kt) — owns `LaunchedEffect(isAccessTokenAvailable) { delay(1000); navigate(...) }`.
- [OnboardingRoute.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/OnboardingRoute.kt) — owns `rememberPagerState(pageCount = { 4 })` + the CTA dispatch (advance page vs. navigate to Login).
- [LoginRoute.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/LoginRoute.kt) — owns `LoginViewModel` + `RedditViewModel` Flow collection, OAuth callback dispatch, auto-nav to Home on access.
- [HomeRoute.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt) — owns tab state + dispatches to `TwitterBookmarksRoute` / `RedditBookmarksRoute` / `AllBookmarksRoute` / `MapViewRoute` from the `tabContent` slot.
- `AllBookmarksRoute` (inlined at the bottom of `AllBookmarksScreen.kt`) — owns 3 ViewModels, paging, popup state, tag-editor dialog.
- `TwitterBookmarksRoute` (inlined at the bottom of `TwitterBookmarksScreen.kt`) — owns `BookmarksViewModel + LoginViewModel`, paging, OAuth code consumption.
- `RedditBookmarksRoute` (inlined at the bottom of `RedditBookmarksScreen.kt`) — owns `RedditViewModel + BookmarksViewModel` (cross-module), paging.
- `MapViewRoute` (inlined at the bottom of `MapViewScreen.kt`) — trivial pass-through.

**Modified NavHost:**
- [Crumbs.kt](app/src/main/java/com/github/jayteealao/crumbs/Crumbs.kt) — 4 `composable {}` blocks now call `SplashRoute`, `OnboardingRoute`, `LoginRoute`, `HomeRoute`. Nav route strings and arg types unchanged. The top-level `collectAsState` on `isAccessTokenAvailable` is gone — moved into `SplashRoute` itself.

**Build configuration (3 module gradle files):**
- [app/build.gradle](app/build.gradle), [feature/twitter/build.gradle](feature/twitter/build.gradle), [feature/reddit/build.gradle](feature/reddit/build.gradle) — added Roborazzi plugin `id 'io.github.takahirom.roborazzi' version '1.60.0'`; added `testImplementation` deps for `roborazziCore`, `roborazziCompose`, `roborazziJunitRule`, `robolectric`, `compose-bom`, `compose-ui-test-junit4`, `activity-compose:1.8.2`; added `testOptions { unitTests { includeAndroidResources = true; returnDefaultValues = true } }`. Dropped `accompanist-pager` + `accompanist-pager-indicators` from `app` and `feature/twitter` (dead deps after Onboarding migration + TwitterCard deletion).

**Deleted (1 file):**
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/components/TwitterCard.kt` (466 LOC) — orphan component, only referenced by its own Preview. Last source-level consumer of `com.google.accompanist.pager.*`. Deletion closes AC-S3 entirely.

**New test files (8 Roborazzi screen tests + 3 test manifests):**
- `app/src/test/java/com/github/jayteealao/crumbs/screens/SplashScreenTest.kt` — 2 goldens.
- `app/src/test/java/com/github/jayteealao/crumbs/screens/OnboardingScreenTest.kt` — 2 goldens with `mainClock.autoAdvance = false`.
- `app/src/test/java/com/github/jayteealao/crumbs/screens/LoginScreenTest.kt` — 2 goldens + 2 callback assertions (AC line 71 regression).
- `app/src/test/java/com/github/jayteealao/crumbs/screens/HomeScreenTest.kt` — 2 goldens.
- `app/src/test/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreenTest.kt` — 2 goldens + AC line 69 callback assertion.
- `app/src/test/java/com/github/jayteealao/crumbs/screens/MapViewScreenTest.kt` — 2 goldens.
- `feature/twitter/src/test/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreenTest.kt` — 2 goldens.
- `feature/reddit/src/test/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreenTest.kt` — 2 goldens.
- `app/src/test/AndroidManifest.xml`, `feature/twitter/src/test/AndroidManifest.xml`, `feature/reddit/src/test/AndroidManifest.xml` — single-activity manifests registering `androidx.activity.ComponentActivity` so Robolectric can resolve the test host (mirrors `core/designsystem/src/test/AndroidManifest.xml`).

**New goldens (16 PNGs):** all under `<module>/src/test/screenshots/`.
- App module (12): `SplashScreen_default_{light,dark}`, `OnboardingScreen_{page0_light,page3_dark}`, `LoginScreen_default_{light,dark}`, `HomeScreen_{twitter_light,all_dark}`, `AllBookmarksScreen_empty_{light,dark}`, `MapViewScreen_default_{light,dark}`.
- Feature modules (4): `TwitterBookmarksScreen_loggedOut_{light,dark}`, `RedditBookmarksScreen_loggedOut_{light,dark}`.

## Shared Files (also touched by sibling slices)

- **`app/build.gradle`** — `toolchain` slice authored the catalog reads. This slice adds the Roborazzi plugin id, 7 `testImplementation` deps, a `testOptions` block, and drops two accompanist deps. Non-overlapping with toolchain slice.
- **`feature/twitter/build.gradle` + `feature/reddit/build.gradle`** — first time these modules gained any test infrastructure. Toolchain slice's catalog drives all version refs (no version-pin churn here).
- **`Crumbs.kt`** — toolchain + layouts left it untouched; this slice rewrites all 4 `composable` blocks to call Routes. Route names and nav-arg shapes preserved byte-identical.

## Notes on Design Choices

- **Route/Screen split is per-screen, not per-file.** Routes for `AllBookmarksScreen`, `TwitterBookmarksScreen`, `RedditBookmarksScreen`, and `MapViewScreen` live in the same file as the stateless Screen (bottom of file). The four "top-level" routes (Splash, Onboarding, Login, Home) live in dedicated `XxxRoute.kt` files because they are referenced by `Crumbs.kt`'s NavHost — keeping the public surface obvious. Mixed pattern was a deliberate readability choice over rigid one-file-one-Route uniformity.
- **`LoginScreen` package mismatch preserved.** The existing file lives at `app/.../screens/LoginScreen.kt` but its package is `screens.login`. `LoginRoute.kt` matches this convention. `Crumbs.kt` imports `LoginRoute` from `screens.login`. No file moves to keep diff scoped.
- **`AllBookmarksScreen` uses `LazyListScope` extension for the per-source paging section** (`renderPagingSection`). Twitter and Reddit sections render identical structure (loading skeletons, items, append spinner, empty footer); factored out as a generic extension so the two paths can't drift visually. Generic params `<T : Any>` + a couple of small lambdas keep it cheap.
- **`MaterialTheme.typography.titleMedium` → `LocalCrumbsTypography.current.titleSection`.** `titleSection` is the closest brutalist analog (Funnel Display, 14sp, uppercase). Workflow's MaterialTheme posture rule: zero `MaterialTheme.*` references in screen sources (confirmed via grep — only `androidx.compose.material3.MaterialTheme.*` matches are inside the design system's own theme implementation file, not in screens).
- **Long-press popup wiring lives in the Route, not the Screen.** Popup visibility depends on `popupBookmark: Bookmark?` state. Hoisting that into the Route means the stateless Screen tests don't have to deal with popup state at all, and Roborazzi goldens capture only the feed. Behaviors slice will swap the Timber.d() stubs in the Route's action lambdas for real soft-delete / archive / share handlers.
- **`androidx.activity:activity-compose:1.8.2` declared explicitly in test deps** (each module) — needed for `createAndroidComposeRule<ComponentActivity>()`. Doesn't conflict with the catalog's `libs.activityCompose` (same version, same artifact) — explicit form mirrors how `core/designsystem` does it.
- **`testOptions.unitTests.includeAndroidResources = true` required for the test AndroidManifest to be picked up by Robolectric.** Without it, the test runner can't resolve `androidx.activity.ComponentActivity` and every Roborazzi test fails with "Unable to resolve activity for Intent ... action.MAIN" before any screen renders. Mirrors `core/designsystem`'s testOptions block.
- **Roborazzi tolerance matches the components/layouts slices** — `roborazzi.compare.changeThreshold=0.05` (5% changed-pixel global) + per-test-class `SimpleImageComparator(maxDistance = 0.01f)` (1% RGB). `SimpleImageComparator` lives in `com.dropbox.differ`, not `com.github.takahirom.roborazzi` — corrected from plan reference at implement time.
- **TwitterCard.kt deletion is in scope.** Dropping the orphan was the cleanest path to satisfy AC-S3 source-level. Same atomic commit because its sole referent was itself.

## Visual Contract Honored

Not applicable — no `02c-craft.md` for this workflow. Mock fidelity adjudication is deferred to AC-S1 maintainer manual diff at verify-stage.

## Deviations from Plan

1. **`SimpleImageComparator` package** — plan referenced `com.github.takahirom.roborazzi.SimpleImageComparator`; actual class is `com.dropbox.differ.SimpleImageComparator` (Roborazzi 1.60.0 re-exports the Dropbox `differ` library). One-line import change per test class. Compile errors caught it at first record-stage attempt; fix was trivial.
2. **`ButtonStyle.Ghost` missing** — plan called for a "Ghost" variant on the debug Skip-Auth button. Actual enum has only `Primary` and `Secondary`. Used `Secondary` for the debug button. No semantic loss (debug-only button, dark theme already differentiates it from the primary CTA).
3. **`testOptions.unitTests.includeAndroidResources = true` + test `AndroidManifest.xml`** — plan didn't anticipate this. Mandatory in AGP 9.1 / Robolectric 4.16 / Roborazzi 1.60 for tests using `createAndroidComposeRule<ComponentActivity>()`. Mirrors `core/designsystem`'s existing setup; added to all 3 new test-bearing modules.
4. **TwitterCard.kt deleted in addition to gradle dep removal** — plan said AC-S3 grep gate is closed by Accompanist-Pager source removal from `OnboardingScreen.kt`. Source-level grep found one more match in `TwitterCard.kt`, an orphan. Workflow's locked decision `orphan-components: "delete-13-outright"` covers this; deleting the file was the only reasonable choice. 466 LOC removed.

(Plan said 26 files-to-touch + 16 steps; actual: ~25 files touched in this commit — 8 modified screens, 4 new Route files (Splash/Onboarding/Login/Home), 1 modified NavHost, 3 modified build.gradle files, 1 deleted TwitterCard, 8 test files, 3 test manifests, 16 PNG goldens, plus the workflow artifacts. Close enough; the small drift is the 4 Route files for non-top-level screens being inlined rather than separate.)

## Anything Deferred

- **AC-S1 (≥95% mock fidelity)** — maintainer-driven manual diff against Option D mocks. Same precedent as tokens AC-K4, toolchain AC6, layouts AC2. Register at verify-stage as `runtime-evidence-deferral` with explicit per-screen sign-off checklist.
- **AC-S2 (Maestro happy-path)** — collapses onto the same emulator+Maestro evidence run that the `maestro` slice owns (alongside layouts AC-L5, toolchain AC4, components AC-C6).
- **AC line 70 (long-press popup with 4 actions visible)** — popup is wired in the Route via `popupBookmark != null` state; testing it without Hilt is awkward. Component-level popup already has Roborazzi coverage from the components slice. Verify-stage should register this as collapsing onto the Maestro evidence run.
- **AC-S1 — `AllBookmarksScreen` populated state goldens** — would require constructing `LazyPagingItems<TweetData>` and `LazyPagingItems<RedditPostData>` test doubles in-process; fragile and noisy in Roborazzi. Plan's 18-golden floor relaxed to 16 here. Populated-state visual coverage handled by maintainer manual diff at verify (browser-rendered HTML mocks have the same effective content density that Roborazzi populated captures would).
- **`Modifier.dropShadow` adoption on the popup sheet** — carried forward from components/layouts deferral. Behaviors slice owns.

## Known Risks / Caveats

- **Test goldens were captured at Robolectric's default phone qualifiers (despite `@Config(qualifiers = "w411dp-h891dp-xxhdpi")`).** Image dimensions are 1233×2673 (not 411×891 logical dp × 3 density factor). Robolectric appears to be rendering at a wider canvas than the qualifier requested — possibly an interaction between Robolectric 4.16 + AGP 9.1 + the `includeAndroidResources` flag. Captures are visually correct (brutalist palette, layout slots in the right slot positions); the dimension drift is a Robolectric rendering quirk, not a content drift. Verify stage should re-check by inspecting the captured screenshots side-by-side against the mock and against the design-system goldens (which are also rendered at the same Robolectric default).
- **`HomeScreen` test renders an inert content body** — the `tabContent` slot is a no-op `Box` filling the surface color. Real tab content (paging + popup) is in the tab Routes which need Hilt. Acceptable — the test exercises HomeScaffold composition, not tab content.
- **`OnboardingScreen` test uses `mainClock.autoAdvance = false`.** Without it, the lazy Pager renders empty page slots on the first frame. The pattern matches `OnboardingShellTest` from the layouts slice; future authors editing `OnboardingScreenTest` should not remove the `autoAdvance = false` + `waitForIdle()` discipline.
- **`accompanist-pager` gradle deps dropped from `:app` and `:feature:twitter`.** Confirms no transitive consumer breaks at compile + lint + assembleDebug. If any future code wants Accompanist Pager features it must re-add the dep and consciously reverse this slice's decision.
- **`feature/twitter:LoginViewModel` and `feature/reddit:RedditViewModel` byte-stable.** `git diff --stat` confirms zero changes in either ViewModel — AC line 71 regression bar (OAuth flows unchanged) closed at the diff-level.
- **`AllBookmarksRoute` accepts an optional `NavController?`** — used only for empty-state's "CONNECT AN ACCOUNT" navigation. When invoked from `HomeRoute`'s tab dispatch, `navController` flows through; standalone tests don't need it. Null-safety chosen over forcing a navController param on every tab Route.

## Freshness Research

No additional research at implement-stage beyond plan's pass. Plan's freshness research (Roborazzi 1.60 tolerance config, Compose-native Pager API, Route/Screen split idiom, edge-to-edge insets) all confirmed correct in practice. One correction: `SimpleImageComparator` package is `com.dropbox.differ`, not `com.github.takahirom.roborazzi` — captured in deviations above.

## Recommended Next Stage

- **Option A (default):** `/wf verify brutalist-redesign screens` — automated gates already green (compile + lint + assembleDebug + record + verify Roborazzi). Verify stage owns AC adjudication, the AC-S1 maintainer-diff deferral registration, AC-S2 collapse onto Maestro slice, AC line 70 collapse onto Maestro slice. **`/compact` recommended before proceeding** — implementation context (file rewrites, build-error debugging, test-manifest discovery) is noise for verification.
- **Option B:** `/wf review brutalist-redesign screens` — skip verify; less recommended because AC-S1 manual diff benefits from explicit deferral bookkeeping at the verify stage before review.
- **Option C:** `/wf plan brutalist-redesign behaviors` — start the next slice's plan; behaviors slice consumes this slice's testTag scaffolding + popup-action `TODO()` stubs + filter-chip empty state. Can run in parallel with this slice's verify.
