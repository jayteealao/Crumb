---
schema: sdlc/v1
type: plan-index
slug: brutalist-redesign
status: complete
stage-number: 4
created-at: "2026-05-16T22:37:59Z"
updated-at: "2026-05-17T21:19:04Z"
planning-mode: rolling
slices-planned: 6
slices-total: 7
implementation-order: [toolchain, tokens, components, layouts, screens, behaviors, maestro]
conflicts-found: 0
tags: [redesign, ui, compose, design-system, brutalist, roborazzi, maestro]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
next-command: wf-implement
next-invocation: "/wf implement brutalist-redesign behaviors"
---

# Plan Index

This is a **rolling plan index**. The chain of slices is strictly linear (toolchain → tokens → components → layouts → screens → behaviors → maestro), so plans are written one at a time. The first plan (`toolchain`) is complete and execution-ready; the remaining six are deferred until their predecessor implements + verifies. After each slice ships, run `/wf plan brutalist-redesign <next-slice>` to draft the next plan against current codebase state.

## Slice Plan Summaries

### `toolchain` *(planned)*

- **Files to touch:** ~30 source files (build.gradle ×7, libs.versions.toml, gradle wrapper, CrumbsTheme.kt, 17 test classes with `@Config` bumps, GradientImage.kt + CrumbsBookmarkCard.kt for Coil 3 migration, two GH Actions workflows) + 133 regenerated PNG goldens.
- **Strategy:** strict ordering with a Phase A spike up front (KSP × Kotlin 2.3.21 verification). Each version bump is a discrete commit. Compose BOM 2026.05.00 governs all `androidx.compose.*` deps and Material3 1.4.0; Coil bumps to 3.x; Roborazzi 1.7.0 → 1.37.0; Robolectric 4.14.1 → 4.16. CI workflow gains lint + kotlinter + verifyRoborazziDebug gates. Final commit regenerates all 133 goldens against the unchanged v1.1 design on the new chain.
- **Key risk:** KSP × Kotlin 2.3.21 compatibility (mitigated by Phase A spike). Secondary: Coil 3 import-path migration ripple.
- **See:** [04-plan-toolchain.md](04-plan-toolchain.md).

### `tokens` *(planned)*

- **Files to touch:** ~35 files — 5 theme files (CrumbsColors/Typography/Shapes/Theme rewritten, new CrumbsStroke), 7 surviving component files (mechanical token rename), 2 app screens (rename), 4 dead Material orphans deleted (app/ui/theme/*.kt), 13 orphan components deleted + their test files, IBM Plex Mono TTFs added (×3), funnel_display_semibold.ttf removed, ~90 PNG goldens regenerated.
- **Strategy:** three-phase cutover. Phase A rewrites the token surface (compile-broken intermediate is fine on the feature branch). Phase B applies the rename lookup table to surviving consumers and deletes orphans — project compiles, components look intentionally wrong (mono body text on v1.1 layouts). Phase C regenerates Roborazzi goldens directly (no verify-first), runs the CI-equivalent gate, then installs on Pixel 6 API 34 to confirm paper background + orange accent. AC-K5 re-purposed from a goldens-based assertion to a maintainer-driven manual diff against `handoff-tokens.jsx`.
- **Key risk:** the 11→7 typography rename is intentionally lossy — body text temporarily renders mono. Components slice fixes this. Reviewers of the tokens-vs-components diff must not flag the intermediate as regression.
- **Cross-slice impact:** the orphan-component deletion is **pulled forward** from the components slice (round-3 PO decision). AC-C1 in components becomes a verification-only criterion (the 13 deletions are already done).
- **See:** [04-plan-tokens.md](04-plan-tokens.md).

### `components` *(planned)*

- **Files to touch:** ~38 (13 active component rewrites + 4 new component files + 4 new test files + 8 existing test-file regens + QuickActionMenu.kt deletion + ActionComponentsTest partial-delete + libs.versions.toml + core/designsystem/build.gradle + 2 app-screen call-site updates for the BookmarkCard onLongPress signature widening + ~60 Roborazzi PNG regenerations).
- **Strategy:** Mixed Material3 stance — case-by-case per component (strip vs keep+override). Six commits grouped by visual family (chrome primitives, layout chrome, cards & states, dialog/menu + QuickActionMenu retire, new components, goldens regen). New `Modifier.dropShadow` (Compose 1.11 native) replaces sibling-Box trick for brutalist offset shadows. Adds `kotlinx.collections.immutable` dependency for `ImmutableList` parameters. LoadingCard scan-line motion uses hoist-time-as-parameter for Roborazzi determinism. `CrumbsBookmarkCard` switches from `combinedClickable` to `detectTapGestures(onLongPress = { offset -> ... })` — public API widens `onLongPress` to include the fingertip Offset.
- **Key risk:** Material3 wrapper drift on future BOM bump (`CrumbsButton`/`CrumbsScaffold` keep wrappers with chrome aggressively overridden — a new shape/color default could regress). Mitigated by Roborazzi regression goldens. Secondary: BookmarkCard `onLongPress` API change ripples to HomeScreen + AllBookmarksScreen — co-located in same commit.
- **PO decisions captured (12 across 3 rounds):** Material3 stance = mixed; QuickActionMenu = retire; long-press popup follows handoff Screen 5 (2×2 grid TAG/SHARE/ARCHIVE/DELETE — overrides slice spec line 59); commit cadence = grouped by family; scan-line determinism = hoist time as parameter; lists = `kotlinx.collections.immutable.ImmutableList`; CrumbsScaffold = keep Material3 passthrough; Snackbar/Banner = brutalist token defaults; testTag verification = Maestro studio dry-run; Roborazzi tolerance = slice-local in `core/designsystem/build.gradle`; offset shadow = `Modifier.dropShadow`; goldens coverage = meaningful-state matrix × 2 themes (~24 new).
- **Cross-slice impact:** ARCHIVE action (new on the long-press popup, comes from handoff Screen 5) is a behavior introduced by the handoff but **wired** in the behaviors slice — components slice ships visual shell only.
- **See:** [04-plan-components.md](04-plan-components.md).

### `layouts` *(planned)*

- **Files to touch:** 8 — 3 new shells (`HomeScaffold.kt`, `OverlayShell.kt`, `OnboardingShell.kt`) + 3 new test files (`HomeScaffoldTest`, `OverlayShellTest`, `OnboardingShellTest`) + `MainActivity.kt` (add `enableEdgeToEdge()`) + 6 regenerated PNG goldens. Optional 4th source file (internal `OnboardingPageIndicator.kt`) if extracted; default inline in `OnboardingShell.kt`.
- **Strategy:** Single atomic commit, additive only. New sub-package `core/designsystem/layouts/`. HomeScaffold composes Material3 Scaffold with `containerColor = CrumbsTheme.colors.background`, status-bar inset consumed once at the `Column { topBar(); filterBar() }` topBar slot, nav-bar inset on bottomBar slot. OverlayShell uses in-tree composition: `Box` + `AnimatedVisibility(fadeIn)` scrim + `AnimatedVisibility(slideInVertically + fadeIn)` sheet + `BackHandler`; backdrop tap via `Modifier.clickable(indication = null)`; brutalist Surface (RectangleShape + 1.5dp ink border). OnboardingShell renders Compose-native `HorizontalPager` with `pages: ImmutableList<@Composable () -> Unit>` + `rememberPagerState(pageCount = { pages.size })`; footer is a single `Row(SpaceBetween)` with shell-owned `OnboardingPageIndicator` (3 RectangleShape pills, accent on currentPage) + optional `CrumbsButton`. MainActivity gains `enableEdgeToEdge()` first thing in `onCreate()`.
- **Key risk:** Interim-state visual artifact — between this slice's merge and the screens slice's screen migrations, screens that don't yet consume insets will render TopBar partially under the status bar (because MainActivity now calls `enableEdgeToEdge()`). Acknowledged; recorded as known interim state. Verify report should not treat this as a regression. Mitigation: implement record and verify-stage report both explicitly flag the interim render.
- **PO decisions captured (8 across 2 rounds):** enableEdgeToEdge() placement = MainActivity in this slice; OverlayShell technique = in-tree Box + AnimatedVisibility; OnboardingShell pager slot = PagerState + ImmutableList<@Composable () -> Unit>; filterBar = nullable; backdrop dismiss = Modifier.clickable(indication = null); Roborazzi insets strategy = hoist as test param, accept 0 default; AC-3 dismissal test = ship in layouts slice (non-Roborazzi UI test); footer composition = single internally-composed Row with shell-owned indicator.
- **Cross-slice impact:** MainActivity edit is a one-line touch in `app/` from a slice nominally scoped to `core/designsystem/`. Co-located deliberately so HomeScaffold's edge-to-edge assumption is satisfiable. AC-2's "28dp gap" measurement and AC-5's Maestro testTag round-trip will register as `runtime-evidence-deferrals` at verify-stage (per workflow precedent — emulator/Maestro evidence belongs to the maestro slice). AC-3 (backdrop dismissal callback) closes within the slice via an in-process Compose UI test.
- **See:** [04-plan-layouts.md](04-plan-layouts.md).

### `screens` *(planned)*

- **Files to touch:** ~26 — 8 screen rewrites (`SplashScreen`, `OnboardingScreen`, `LoginScreen`, `HomeScreen`, `AllBookmarksScreen`, `MapViewScreen` in `app/`; `TwitterBookmarksScreen` in `feature/twitter/`; `RedditBookmarksScreen` in `feature/reddit/`) + 8 new Route wrappers + 1 NavHost rewire (`Crumbs.kt`) + 3 module build.gradle edits (Roborazzi enablement on `app`/`feature/twitter`/`feature/reddit`) + 8 new Roborazzi test files + ≥18 new PNG goldens.
- **Strategy:** Route/Screen split applied to all 8 screens — stateless `XxxScreen(uiState, onEvent)` + thin `XxxRoute(viewModel = hiltViewModel())` wrapper. Zero Hilt test infra introduced. Single atomic commit per implement-stage contract. Enable Roborazzi plugin + dep bundle on 3 new modules (`app`, `feature/twitter`, `feature/reddit`) by copying the `core/designsystem/build.gradle` template. Hard-rewrite TwitterBookmarksScreen + RedditBookmarksScreen to Option D mock 1:1 (ViewModels untouched; cross-module `BookmarksViewModel` injection in `RedditBookmarksRoute` preserved). LoginScreen full-bleed brutalist (no shell wrapper). HomeScreen composes `HomeScaffold` with `CrumbsFilterBar` empty/inert in filterBar slot. OnboardingScreen kills Accompanist Pager (AC-S3 grep gate). AllBookmarksScreen converts `MaterialTheme.typography.*` → `LocalCrumbsTypography.current.*`. Roborazzi tolerance matches component goldens (5% changed-pixel + 1% RGB via `SimpleImageComparator(maxDistance = 0.01f)`).
- **Key risk:** `LazyPagingItems` recomposition in Roborazzi tests + `LazyPagingItems` as data-class field pattern (mitigated by passing items as separate Screen parameter, not inside UiState). Secondary: testTag name churn risk — testTag names introduced this slice are public-API-stable for the maestro slice's flows.
- **PO decisions captured (8 across 2 rounds):** screen factoring = Route/Screen split for all 8; test location = `app/src/test/` + `feature/*/src/test/` (3 module Roborazzi enablement); feature-screens rewrite depth = hard rewrite to mock 1:1; LoginScreen layout = full-bleed brutalist; resplit = keep single slice; Roborazzi tolerance = match components (5%/1%); AC-S1 fidelity method = maintainer-driven manual diff (runtime-evidence-deferral); HomeScreen filterBar = `CrumbsFilterBar` empty/inert.
- **Cross-slice impact:** NavHost route-call updates in `Crumbs.kt` (4 calls: Splash/Onboarding/Login/Home → XxxRoute). Roborazzi plugin enablement spreads to 3 new modules. Edge-to-edge inset issue from layouts slice closes here — every screen migrates to `HomeScaffold` or to `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)`. AC-L2 (HomeScaffold inset measurement on a live system bar) from the layouts slice's `runtime-evidence-deferrals` discharges naturally during this slice's verify when a host screen first composes `HomeScaffold` on Medium_Phone_API_36. AC-S1 (≥95% mock fidelity, maintainer manual diff) + AC-S2 (Maestro happy-path) register as new deferrals at verify-stage.
- **See:** [04-plan-screens.md](04-plan-screens.md).

### `behaviors` *(planned)*

- **Files to touch:** ~32 — new `core/data` module (5 source files: `DeletedBookmark`, `DeletedBookmarkDao`, `DeletedBookmarkRepository`, `SnackbarEvent`, `SyncErrorEvent` + `SyncErrorBus` + `TypeFilter` + `FilterState`), `AppDatabase` version bump 4→5, `DatabaseModule` new `MIGRATION_4_5` + DAO provider, app/feature build.gradles add `core/data` dep, `room-testing` libs alias, `app/build.gradle` `versionCode 3` / `versionName "2.0"` + androidTest assets srcDir, new `MigrationTest.kt` (first instrumentation test in the repo), `HomeScaffold` gains hoisted `banner` slot + 2 new goldens, feature/twitter `Repository` + `BookmarksViewModel` + feature/reddit `RedditRepository` + `RedditViewModel` gain tombstone filter + filter state + sync-error event emission (additive, OAuth-client untouched), new `AllBookmarksViewModel` in `app/` to own the All-tab filter state + combined paging, `HomeRoute` rewritten to lift filter + banner state from active tab's VM, popup DELETE stubs replaced with `softDelete()` in 3 Routes (Twitter's LOGOUT migrates to LoginScreen), `SnackbarHostState` consumes `DeletedBookmarkRepository.events` at HomeRoute scope, LoginScreen gains per-provider LOGOUT button when authed, 4-6 new Roborazzi goldens (HomeScaffold-with-banner ×2, HomeScreen-with-syncErrorBanner ×2, LoginScreen-loggedIn ×2), exported `5.json` schema.
- **Strategy:** Single atomic commit. Order: scaffold `core/data` module → wire AppDatabase + Hilt + migration test → extend HomeScaffold banner slot → wire Twitter sync filter + filter state + banner emission → mirror on Reddit → introduce AllBookmarksViewModel → rewrite HomeRoute lifting → replace DELETE stubs (and Twitter's LOGOUT→DELETE swap) → add LoginScreen LOGOUT → wire SnackbarHostState → version bump → record + verify goldens → lint/assemble/test gates → commit. Migration validated on `Medium_Phone_API_36` AVD before any UI work proceeds.
- **Key risk:** Cross-module DAO access (resolved by new `core/data` module — PO Round 1 Q1); `tweetEntity.type` column may not exist and the Type filter may need to derive from existing joins; AllBookmarks combined paging interleave is non-trivial (plan recommends two-section LazyColumn rather than `MediatorPagingSource`); `SnackbarDuration.Short` ≈ 4s vs spec's 5s (cosmetic; falls back to manual `delay(5000)` if strict).
- **PO decisions captured (8 across 2 rounds):** tombstone module = new `core/data`; Collection filter = reinterpreted as tag-set facet (no schema for collections); banner slot = hoisted on HomeScaffold with call-site AnimatedVisibility; 4th popup action = uniform DELETE across all three Routes (Twitter's LOGOUT migrates to LoginScreen per-provider); LOGOUT placement = LoginScreen when authed; filter state ownership = per-tab VM (each tab independent); TypeFilter values = `ALL/ARTICLE/VIDEO/IMAGE/THREAD/TEXT`; migration AVD = `Medium_Phone_API_36` (continuity with prior slices; slice text "Pixel 6 API 34" updated at verify).
- **Cross-slice impact:** First DB schema change in the workflow (v4 → v5 additive `deleted_bookmarks` table). First instrumentation test in the repo. First `core/data` module addition. New `HomeScaffold.banner` slot is additive — does not break the screens slice's existing `HomeScaffold` callers. Twitter's LOGOUT relocation requires LoginScreen to gain `twitterConnected`/`redditConnected` UI state. Reddit gains a small additive `RedditViewModel.logout()` method (clears local pref store; no OAuth-client touch). Maestro slice will own the 6 interactive ACs (lines 92, 93, 95, 96, 97, 98) that register as runtime-evidence-deferrals at verify-stage.
- **See:** [04-plan-behaviors.md](04-plan-behaviors.md).

### `maestro` *(deferred)*

4 yaml flows + debug-only data injector + `scripts/run-maestro.sh` orchestrating `android` CLI + `lazylogcat`. Plan after `behaviors` lands.

## Cross-Cutting Concerns

- **Plans are rolling.** Linear dependency chain means each slice's plan informs the next. Re-planning a slice with `/wf plan brutalist-redesign <slug>` after the previous slice ships is **expected**, not exceptional — that's the auto-review path the plan stage supports.
- **CI gates evolve with the workflow.** The `toolchain` plan wires `lint + kotlinterCheck + verifyRoborazziDebug` into `pr_check.yml`. Later slices may add screen-level Roborazzi sets and Maestro flow execution — those are slice-local decisions when the time comes.
- **The 17 existing `@Config(sdk = [33])` tests** are all rewritten/deleted by the `components` slice (13 orphans go, the other 4 get golden re-records). The `toolchain` slice bumps `@Config` to `[34]` so the *intermediate* state between toolchain and components is buildable.
- **Coil 3 migration** is started in `toolchain` (build deps + import paths) but only the existing call sites need to compile. The `components` slice's full rewrite of `CrumbsBookmarkCard` and `GradientImage` may further adapt Coil 3 patterns.
- **Spike-first risk discipline.** The Phase A spike pattern in `toolchain` (validate the riskiest unknown on a throwaway commit before mainline work) is a template later slices can adopt if they encounter similar unknowns.
- **Stack confirmation propagates.** All seven plans inherit `stack-source: confirmed` from `00-index.md`. If a later slice would need tooling outside `stack:`, that's a route-back-to-shape signal, not a silent fill-in.

## Integration Points Between Slices

Strictly linear — each slice's outputs are the next slice's inputs:

```
toolchain  → tokens     : new Kotlin 2.3.21 + Compose 1.11.1 + Material3 1.4.0 APIs for Font/Color/Theme
tokens     → components : final CrumbsColors / CrumbsTypography / CrumbsShapes / CrumbsSpacing types
components → layouts    : final Composable signatures the shells will compose
layouts    → screens    : HomeScaffold / OverlayShell / OnboardingShell slot APIs
screens    → behaviors  : screen-level testTags, ViewModel surface area, navigation graph hooks
behaviors  → maestro    : working long-press, filter chips, snackbar, banner, soft-delete — every flow has a real target to assert on
```

No parallel integration points exist unless the `screens` re-split clause fires (which would let `screens-shells` ∥ `behaviors` run in parallel).

## Recommended Implementation Order

1. **`toolchain`** *(implemented + verified-partial)* — risk-first; everything else depends on the new chain.
2. **`tokens`** *(implemented + verified-partial)* — type contract for all downstream. Included orphan-component deletion pulled forward from the components slice.
3. **`components`** *(implemented + verified-partial)* — 12 active rebuilds + 4 new components + QuickActionMenu retirement + scan-line motion + Roborazzi tolerance config + kotlinx.immutable adoption.
4. **`layouts`** *(implemented + verified-partial)* — HomeScaffold / OverlayShell / OnboardingShell + MainActivity edge-to-edge enablement.
5. **`screens`** *(implemented + verified-partial)* — 8 screen rewrites with Route/Screen split, 3-module Roborazzi enablement, hard-rewrite of feature screens, Accompanist Pager retired. Resplit ruled out at plan stage; single atomic commit.
6. **`behaviors`** *(planned, ready to implement)* — wiring + DB schema. New `core/data` module hosts `DeletedBookmark` + DAO + tombstone repo + filter/event types. AppDatabase v4 → v5 with additive migration. Hoisted `banner` slot on HomeScaffold. Per-tab filter state ownership. Twitter LOGOUT migrates to LoginScreen. Six interactive ACs register as runtime-evidence-deferrals at verify-stage; `maestro` slice clears.
7. **`maestro`** *(plan after behaviors ships)* — end-to-end coverage.

## Conflicts Found

**Tokens plan — resolved cleanly:**

- **Handoff JSX default accent (`#D6FF00` lime) vs. shape-locked accent (`#FF5A1F` orange).** Resolved at plan stage: PO confirmed orange holds. `LightColors.accent = #FF5A1F`. Handoff's `accent.orange` alt swatch becomes the default.
- **Tokens-slice scope vs. handoff scope: `CrumbsStroke.kt`.** The handoff introduces a new file the slice spec didn't list. Resolved at plan stage: full `CrumbsStroke.kt` ships in tokens slice (PO choice).
- **AC-K5 (tokens-preview Roborazzi golden) vs. nonexistent preview composable.** Resolved at plan stage: AC re-purposed as a maintainer-driven manual diff against `handoff-tokens.jsx`. No new preview composable or test introduced.
- **Cross-slice scope shift: orphan deletion pulled forward.** 13 orphan components originally scheduled for the components slice now delete in the tokens slice (PO choice). Components slice plan, when drafted, must reflect this.

No conflicts between sibling plans (toolchain ships shapes/colors that tokens replaces — that's correctness, not conflict). No cycle, no contradictory acceptance criteria.

## Freshness Research

Captured in detail in [04-plan-toolchain.md](04-plan-toolchain.md) `## Freshness Research`. Top-level summary:

- Toolchain target versions confirmed compatible: Kotlin 2.3.21 + AGP 9.1.1 + Gradle 9.1.2 + Compose BOM 2026.05.00 + Material3 1.4.0 + Roborazzi 1.37.0 + Robolectric 4.16 + JDK 17.
- Known breaking changes audited: AGP Variants API removal (n/a — we don't use it), `dexOptions` removal (n/a), R8 repackaging default (audit step), `id 'org.jetbrains.kotlin.android'` removal claim (verification step).
- KSP × Kotlin 2.3 risk surfaced and contained by a Phase A spike commit.
- Coil 3 migration path documented (`coil` → `coil3` namespace + new okhttp artifact).

## Recommended Next Stage

- **Option A (default):** `/wf implement brutalist-redesign behaviors` — execute the behaviors plan. 18-step atomic commit with 8 PO-locked decisions. **Compact first** — planning research (3 sub-agent reports + 2 discovery rounds) is noise for the implement loop; PreCompact hook preserves workflow state on disk.
- **Option B:** `/wf review brutalist-redesign screens` — open the per-slice review on the screens slice before extending the diff. `review-scope: slug-wide` means the load-bearing review runs at the end of the slice chain, but per-slice signal is still valid.
- **Option C:** `/wf plan brutalist-redesign maestro` — start the next slice's plan in parallel. Maestro slice depends on behaviors landing first to test against, but its plan can be drafted against this slice's testTag inventory + AC list now. Useful for unblocking maestro work as soon as behaviors implements.
- **Option D:** `/wf-quick probe brutalist-redesign` — single emulator+Maestro probe run to discharge the 11 existing runtime-evidence-deferrals before behaviors ships. Not blocking, but consolidates prior verification debt.
