---
schema: sdlc/v1
type: implement
slug: brutalist-redesign
slice-slug: layouts
status: complete
stage-number: 5
created-at: "2026-05-17T15:24:46Z"
updated-at: "2026-05-17T15:24:46Z"
metric-files-changed: 15
metric-lines-added: 731
metric-lines-removed: 1
metric-deviations-from-plan: 5
metric-review-fixes-applied: 0
commit-sha: ""
tags: [layouts, scaffolds, brutalist, designsystem, edge-to-edge, pager, activity-compose-bump]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
  slice-def: 03-slice-layouts.md
  plan: 04-plan-layouts.md
  siblings:
    - 05-implement-toolchain.md
    - 05-implement-tokens.md
    - 05-implement-quick-skip-auth-page.md
    - 05-implement-components.md
  verify: 06-verify-layouts.md
next-command: wf-verify
next-invocation: "/wf verify brutalist-redesign layouts"
---

# Implement: layouts

## Summary of Changes

Single atomic commit. Three new layout shells (`HomeScaffold`, `OverlayShell`, `OnboardingShell`) added to a fresh `core/designsystem/layouts/` sub-package, with 3 companion Roborazzi test files producing 6 golden PNGs. `MainActivity.onCreate()` gains a one-line `enableEdgeToEdge()` call so the running app follows the Android 15 / compileSdk 35 contract. `androidx.activity:activity-compose` bumped from 1.6.1 → 1.8.2 in the version catalog (and exposed on `core/designsystem` main classpath) to make `BackHandler` and `enableEdgeToEdge` reachable on both modules' production source sets.

No screen migration in this slice. Shells are pure additions; existing consumers continue to compose `CrumbsScaffold` directly until the `screens` slice migrates them. The interim visual artifact between this slice and `screens` (top-bar partially under status bar on un-migrated screens once edge-to-edge is live) is acknowledged and is **not** a regression.

## Files Changed

**New (3 main + 3 test = 6 Kotlin files, 726 LOC total):**
- [HomeScaffold.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/HomeScaffold.kt) — Material3 Scaffold wrapper. Slots: `topBar`, optional `filterBar`, `bottomBar`, `content(PaddingValues)`. Composes `Column { topBar(); filterBar?.invoke() }` in the top slot with one `statusBarsPadding()`; bottom slot wraps `bottomBar()` with `navigationBarsPadding()`. testTags `home-scaffold`, `home-scaffold-topbar`, `home-scaffold-filterbar`, `home-scaffold-bottombar`. `containerColor = LocalCrumbsColors.current.background`. Exposes `contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets` so callers can override; tests default to `ScaffoldDefaults.contentWindowInsets` which Robolectric reports as zero.
- [OverlayShell.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/OverlayShell.kt) — In-tree bottom-anchored modal shell. Outer `Box(fillMaxSize)` + scrim `Box(background = Color.Black.copy(alpha = 0.45f))` wrapped in `AnimatedVisibility(fadeIn/fadeOut)` + sheet `Surface` wrapped in `AnimatedVisibility(slideInVertically + fadeIn / slideOutVertically + fadeOut)` aligned `BottomCenter`. Sheet uses `RectangleShape`, `BorderStroke(stroke.regular, colors.ink)`, `imePadding() + navigationBarsPadding()`. `BackHandler(enabled = visible)` co-located at the outer Box scope (not inside AnimatedVisibility). Backdrop dismisses via `Modifier.clickable(MutableInteractionSource(), indication = null)` + `semantics { contentDescription = "Dismiss overlay" }` for TalkBack. testTags `overlay-shell`, `overlay-shell-backdrop`, `overlay-shell-header`, `overlay-shell-body`, `overlay-shell-apply`.
- [OnboardingShell.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/OnboardingShell.kt) — Full-bleed paged shell. Internally composes `androidx.compose.foundation.pager.HorizontalPager`. Params: `pages: ImmutableList<@Composable () -> Unit>`, `pagerState: PagerState = rememberPagerState(pageCount = { pages.size })`, optional `header`, optional `footerCtaText: String?` + `onFooterCtaClick: (() -> Unit)?`. Edge-to-edge insets consumed at Column root (`statusBarsPadding().navigationBarsPadding()`). Footer is a single `Row(SpaceBetween)` with internal `OnboardingPageIndicator` (3 pills, accent on `currentPage`, ink-25%-alpha otherwise, `RectangleShape`) on the left + optional `CrumbsButton(style = ButtonStyle.Primary)` CTA on the right. testTags `onboarding-shell`, `onboarding-shell-pager`, `onboarding-shell-footer`, `onboarding-shell-indicator`.
- [HomeScaffoldTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/layouts/HomeScaffoldTest.kt) — 2 Roborazzi tests. Stub slots are local `StubBlock` Box composables filling width + spec-height + ink-8%-alpha background.
- [OverlayShellTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/layouts/OverlayShellTest.kt) — 2 Roborazzi tests + 1 non-Roborazzi UI test (`backdrop_tap_invokes_onDismiss`) that asserts a tap on the `overlay-shell-backdrop` testTag fires `onDismiss`. Closes AC-3 entirely within the slice.
- [OnboardingShellTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/layouts/OnboardingShellTest.kt) — 2 Roborazzi tests with `composeTestRule.mainClock.autoAdvance = false` + `waitForIdle()` for lazy-layout determinism. Page 0 light + page 1 dark (via `rememberPagerState(initialPage = 1)`).

**Modified (3 files, +5/-1):**
- [MainActivity.kt](app/src/main/java/com/github/jayteealao/crumbs/MainActivity.kt) — `+import androidx.activity.enableEdgeToEdge` + `enableEdgeToEdge()` call before `setContent {}`. Two-line change.
- [core/designsystem/build.gradle](core/designsystem/build.gradle) — added `implementation libs.activityCompose` on main classpath (was test-only at 1.8.2). One line + one comment.
- [gradle/libs.versions.toml](gradle/libs.versions.toml) — `activityCompose` version bumped `1.6.1` → `1.8.2`. One-line replacement.

**New goldens (6 PNGs):**
- `HomeScaffold_default_light.png`, `HomeScaffold_default_dark.png`
- `OverlayShell_open_light.png`, `OverlayShell_open_dark.png`
- `OnboardingShell_page0_light.png`, `OnboardingShell_page1_dark.png`

All under `core/designsystem/src/test/screenshots/`.

## Shared Files (also touched by sibling slices)

- `core/designsystem/build.gradle` — `tokens` slice added the `kotlinx.collections.immutable` `api` declaration; this slice adds an `implementation libs.activityCompose` line. Non-overlapping additions; no merge risk.
- `gradle/libs.versions.toml` — `toolchain` slice authored the full catalog; this slice bumps one version pin (`activityCompose`). Other entries untouched.

## Notes on Design Choices

- **Why `enableEdgeToEdge()` lives in MainActivity, not the theme**: per discovery PO answer Round 1.A, the call is platform-scoped (one Activity → one call) and belongs in `MainActivity.onCreate()`. Doing it in `CrumbsTheme` would re-fire on every recomposition and conflate theming with windowing.
- **Why `OverlayShell` uses in-tree composition instead of `Popup`**: PO answer Round 1.B locked the choice. `AnimatedVisibility(slideInVertically + fadeIn)` aligned `BottomCenter` + `BackHandler` is the modern Compose recipe for bottom-anchored modals when IME insets must dispatch into the sheet's content (filter inputs, search fields) and TalkBack must address the backdrop dismiss target. `Popup` would have required custom inset plumbing and weaker a11y.
- **Why `OnboardingShell` exposes `ImmutableList<@Composable () -> Unit>` (composables-as-data)**: PO answer Round 1.C. `ImmutableList` ensures recomposition stability (callers can't mutate the list and trigger over-recomposition); composables-as-data is the conventional shape for pager content slots; the type is already on `core/designsystem`'s `api` scope from the components slice so callers read it transitively.
- **Why `HomeScaffold` wraps `Material3.Scaffold` directly, not `CrumbsScaffold`**: avoids the `testTag("scaffold-root")` vs `testTag("home-scaffold")` collision. `CrumbsScaffold` and `HomeScaffold` are now siblings in the design system: `CrumbsScaffold` for incidental Scaffold needs, `HomeScaffold` for the canonical brutalist home layout.
- **Why backdrop dismiss uses `Modifier.clickable(indication = null)` instead of `pointerInput { detectTapGestures }`**: PO answer Round 2.D — better TalkBack interaction (announces "Dismiss overlay" via `contentDescription`), simpler code, no ripple to suppress.
- **Why `OnboardingShellTest` sets `mainClock.autoAdvance = false`**: documented Roborazzi guidance for lazy layouts (`HorizontalPager`) — without it, pages can render empty before capture. `waitForIdle()` before `captureRoboImage` guarantees the composition has settled.

## Visual Contract Honored

Not applicable — no `02c-craft.md` for this workflow.

## Deviations from Plan

1. **`CrumbsTheme.colors` shorthand → `LocalCrumbsColors.current`**. The plan's pseudo-code used `CrumbsTheme.colors` as a static accessor; the actual codebase convention (visible in `CrumbsButton.kt`, `CrumbsScaffold.kt`, etc.) is `LocalCrumbsColors.current` / `LocalCrumbsStroke.current` / `LocalCrumbsSpacing.current`. Adopted the established convention. No functional change.
2. **`CrumbsButtonStyle.Primary` → `ButtonStyle.Primary`**. Plan used a name that doesn't exist; the actual enum is `com.github.jayteealao.crumbs.designsystem.components.ButtonStyle`. Same enum, real name.
3. **`TestCrumbsTheme` test wrapper → `CrumbsTheme(darkTheme = …)`**. Plan referenced a `TestCrumbsTheme` helper; the codebase doesn't have one (the sibling [LoadingCardTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/LoadingCardTest.kt) wraps test content in `CrumbsTheme(darkTheme = …)` directly). Matched the existing convention.
4. **`androidx.activity:activity-compose` bumped from 1.6.1 → 1.8.2 + added to `core/designsystem` main classpath.** The plan didn't anticipate this — but `BackHandler` (OverlayShell) and `enableEdgeToEdge` (MainActivity) were both introduced in `androidx.activity:activity:1.8.0` (Sep 2023). The catalog pin of 1.6.1 predated them; `core/designsystem`'s main set didn't declare `activity-compose` at all (only `testImplementation` at 1.8.2). Two-line catalog bump + one-line `implementation` add in `core/designsystem/build.gradle` unblocked both APIs the plan mandated. Minimal, scoped, version-only change.
5. **4 bonus testTags added** beyond slice spec line 52's list: `overlay-shell-header`, `onboarding-shell-pager`, `onboarding-shell-footer`, `onboarding-shell-indicator`. Non-breaking additions; make Maestro flow construction cleaner; flagged here so reviewers can confirm against the slice's testTag manifest.

## Anything Deferred

- **AC-2 inset-applied measurement** ("28dp gap top + 88dp TopBar + 34dp FilterBar + 52dp BottomNav + 8dp nav-pill"). Roborazzi captures `WindowInsets(0)` by Robolectric default — the layout-math claim ships in this slice; the runtime measurement on a real device transfers to a `runtime-evidence-deferral` at the verify stage. Cleared by the `maestro` slice's testTag round-trip with `/wf-quick probe`, or by manual emulator inspection.
- **AC-5 Maestro studio dry-run** (every testTag queryable on a running debug app). Maestro CLI is not on confirmed PATH; the dedicated `maestro` slice owns this. Transfers to a `runtime-evidence-deferral` at verify.
- **`Modifier.dropShadow` adoption** for offset-shadow on the overlay sheet. Carried forward from the `components` slice deferral list — `behaviors` slice owns the actual import + adoption.

## Known Risks / Caveats

- **Interim visual artifact between layouts merge and screens merge.** With `enableEdgeToEdge()` now active in MainActivity, screens that haven't been migrated to `HomeScaffold` (i.e., everything in `app/.../screens/` today) will render with TopBar partially under the status bar. **This is not a regression** — it's a known sequencing detail. The `screens` slice migrates every consumer. Verify-stage reviewers should not flag this on the running app between this slice's commit and the screens slice's commit.
- **`activityCompose` bump cascade**. Bumping the catalog entry from 1.6.1 → 1.8.2 affects every consumer of `libs.activityCompose` and `libs.bundles.composeInterop`. Build green confirms no caller broke; review-stage may want to spot-check.
- **`composeTestRule.mainClock.autoAdvance = false`** in `OnboardingShellTest` means the rule must explicitly `waitForIdle()` before capture. Pattern is documented inline and matches the Roborazzi maintainers' guidance; if a future test author copy-pastes without the `waitForIdle()`, captures will render with empty pages.
- **`BackHandler` co-location**. `BackHandler(enabled = visible)` sits at the outer `Box` scope of `OverlayShell`, not inside `AnimatedVisibility`. If a future refactor moves it into the AnimatedVisibility, the back-press handler will dispose mid-exit-animation and back-press won't fire during the slide-out. Left a structural comment in the code; reviewers should re-flag if a move is proposed.

## Freshness Research

Plan's freshness pass remains current (`enableEdgeToEdge` canonical; Material3 1.4.0 Scaffold contract unchanged; Compose-native `HorizontalPager`'s lambda `pageCount` form stable; in-tree modal pattern documented; `Modifier.dropShadow` import path stable). No additional research performed at implement — plan's evidence was current within the prior turn (2026-05-17T14:55:21Z).

## Recommended Next Stage

- **Option A (default):** `/wf verify brutalist-redesign layouts` — automated gates already green (compile + assembleDebug + recordRoborazziDebug + verifyRoborazziDebug + lintDebug); verify stage owns AC adjudication, AC-2 inset measurement deferral registration, and AC-5 Maestro deferral registration. **`/compact` recommended before proceeding** — implementation context is noise for verification.
- **Option B:** `/wf review brutalist-redesign layouts` — skip verify; less recommended since AC-2 (inset measurement) and AC-5 (Maestro studio) both carry runtime claims that benefit from explicit deferral bookkeeping.
- **Option C:** `/wf plan brutalist-redesign screens` — start the next slice's plan; the screens slice can run in parallel with this slice's verify.
