---
schema: sdlc/v1
type: shape
slug: brutalist-redesign
status: complete
stage-number: 2
created-at: "2026-05-16T21:44:39Z"
updated-at: "2026-05-16T21:44:39Z"
docs-needed: true
docs-types: [readme-update, reference, explanation]
tags: [redesign, ui, compose, design-system, brutalist, roborazzi, maestro]
refs:
  index: 00-index.md
  intake: 01-intake.md
  next: 03-slice.md
next-command: wf-slice
next-invocation: "/wf slice brutalist-redesign"
---

# Shape

## Problem Statement

The Crumb Android app's current visual design (cyan accent, soft cards, generic Compose chrome) does not match the brand direction the maintainer has committed to. A complete design system handoff — **Brutalist Mono Option D v2.0** — exists in `Crumbs-handoff/crumbs/project/` as HTML/JSX prototypes and explicitly targets the existing `core/designsystem` module by name. The handoff is delivered for surgical replacement (tokens drop in, callers untouched at the type level), but the existing `CrumbsColors` schema, `CrumbsTypography` scales, component set, and screen layouts differ enough from the handoff that "replacement" is in practice a complete visual rewrite of every user-visible surface.

The redesign must reach ≥95% visual fidelity to the HTML mocks on both light and dark themes, wire up four implied behaviors (long-press menu, multi-select filter bar, soft-delete with undo, sync-error banner), and ship without regressing the existing OAuth/sync/persistence pipeline that the team finished in v1.1. The repo's verification infrastructure (Roborazzi 1.7.0 in `core/designsystem`) is also stale; modern Roborazzi requires a toolchain jump that touches every module's `build.gradle`.

## Primary Actor / User

End-user of the Crumb Android app — a single-user consumer who connects Twitter and/or Reddit accounts and reads their saved bookmarks. Secondary: the maintainer (solo developer) who must keep extending the new design system after this workflow closes.

## Desired Behavior

### Visual surface

The app renders the Brutalist Mono Option D design language on every user-visible surface:

- **Palette:** background `#EFEEE9` (paper) / surface `#FFFFFF` / ink `#0A0A0A` / onSurfaceVariant `#535353` / accent `#FF5A1F` (orange) / onAccent `#0A0A0A`, plus `error #A40000` and `success #206040`. Dark mode mirrors the structure with background `#0B0B0B`, surface `#161616`, ink `#FFFFFF`, onSurfaceVariant `#9A9A9A`. Accent + onAccent are theme-invariant.
- **Typography:** Funnel Display (UI sans, 400/500/600/700/800) and IBM Plex Mono (kickers, metadata, monospaced labels, 400/500/600/700), both bundled into `app/src/main/res/font/`.
- **Strokes & shapes:** 1.5dp / 2dp ink borders, sharp corners (no rounding except where the existing cut-corner shapes are demonstrably part of the brutalist language). Material3 elevation/shadows are off everywhere.
- **Dynamic color:** explicitly disabled; the brutalist palette is never tinted by wallpaper.

### Layout shells

Three reusable scaffolds replace the current `CrumbsScaffold` usage:

- `HomeScaffold` — TopBar (88dp · kicker + wordmark + search) + FilterBar (34dp) + Feed (LazyColumn) + BottomNav (52+8dp).
- `OverlayShell` — bottom-anchored sheet for multi-select filter pickers (Tags, Collection) and any future heavy-state UI. Faded ink backdrop.
- `OnboardingShell` — full-bleed paged layout used by `OnboardingScreen` and `LoginScreen`.

### Six screens

| Screen | Source-of-truth | Brutalist treatment |
|---|---|---|
| `SplashScreen` | `option-d-screens.jsx` splash mock | Wordmark `crumbs•` (accent bullet), centered. ≤1.0s before nav. |
| `OnboardingScreen` | onboarding pager mock | 4 pages using new HorizontalPager (Compose-native, not Accompanist). Brutalist illustrations or ink-stroked panels per page. |
| `LoginScreen` | login mock | TopBar + UserProfileDisplay (brutalist) per provider + "CONNECT TWITTER / CONNECT REDDIT" CrumbsButton primary actions. |
| `HomeScreen` | home/feed mock | HomeScaffold; tab switches between `TwitterBookmarksScreen`, `RedditBookmarksScreen`, `AllBookmarksScreen`, `MapViewScreen`. |
| `AllBookmarksScreen` | all-bookmarks mock | Feed of CrumbsBookmarkCard (brutalist). Long-press → contextual popup. |
| `MapViewScreen` | placeholder | Brutalist "COMING SOON" full-tab empty-state. No maps SDK. |

(The two feature-module screens `TwitterBookmarksScreen` and `RedditBookmarksScreen` count as *consumers* of the new design system, not as new screens.)

### Behaviors wired

Four implied behaviors are wired to real handlers, no stubs:

1. **Long-press menu** — long-press on a `CrumbsBookmarkCard` opens a contextual popup anchored to the card. Actions: Open/Read, Share, Edit tags / Add to collection, Delete. Tap outside dismisses.
2. **Filter chip bar** — `CrumbsFilterBar` exposes three chip groups: Type (single-select, instant), Tags (multi-select via OverlayShell), Collection (multi-select via OverlayShell). Selection state lives in the screen's ViewModel and re-queries the paging source.
3. **Soft-delete + Undo** — Delete from the long-press menu writes the bookmark id to a new `deleted_ids` Room table and removes it from the feed. A brutalist `CrumbsSnackbar` shows "DELETED · UNDO" for 5 seconds; tapping UNDO removes the tombstone and restores. On expiry, the tombstone stays and the next sync skips that id.
4. **Sync-error banner** — when an OAuth/sync call returns 4xx/5xx, an ink-stroked banner pins above the affected tab's feed: kicker "ERR · RECONNECT TWITTER" + accent button. Banner is sticky; dismisses only when the underlying error resolves.

## Acceptance Criteria

Each criterion is tagged with verification method: `automated` (Roborazzi/JUnit), `interactive` (Maestro + emulator), or `manual` (human visual judgement).

### Toolchain & build (slice 1 candidate)

- **AC-T1** [automated] `./gradlew assembleDebug` succeeds on Kotlin **2.3.21**, AGP **9.1.1**, Gradle **9.1+**, Compose BOM **2026.05.00**, Material3 **1.4.0**, Roborazzi **1.37.0**, Robolectric **4.16**, JDK 17.
- **AC-T2** [automated] All pre-existing Roborazzi golden tests in `core/designsystem` that survive the orphan-component cleanup pass on the new toolchain after golden regeneration.
- **AC-T3** [automated] `./gradlew lintDebug` and `kotlinter` checks pass.

### Tokens (slice candidate)

- **AC-K1** [automated] `CrumbsColors` data class has exactly these fields: `background, surface, ink, onSurfaceVariant, accent, onAccent, error, success`. Old fields (`primary, textPrimary, textSecondary, accentAlpha, surfaceVariant, navIndicator`) are removed.
- **AC-K2** [automated] `LightColors` and `DarkColors` values are byte-exact to the handoff hex codes (orange accent).
- **AC-K3** [automated] `CrumbsTypography` exposes the handoff's named styles (`displayHeadline, displaySmall, titleSection, bodyMono, …`) using Funnel Display (sans) and IBM Plex Mono (mono), loaded from `res/font/`.
- **AC-K4** [automated] No `dynamicLightColorScheme` / `dynamicDarkColorScheme` call exists in the codebase (grep verification).
- **AC-K5** [interactive] Side-by-side Roborazzi capture of the Tokens preview matches the Handoff doc's Tokens section render at ≥95%.

### Components (slice candidate)

- **AC-C1** [automated] The 13 orphan components are deleted: `CrumbsCard, CrumbsDialog, CrumbsDivider, CrumbsFilterChip, CrumbsSortMenu, CrumbsTabBar, CrumbsTagChip, CrumbsTextField, EngagementMetrics, MediaCarousel, SearchSuggestions, ThreadIndicator, VideoPlayer`. Their test files are also deleted. Build remains green.
- **AC-C2** [automated] The 8 active brutalist atomic composables exist with the handoff-spec signatures and have Roborazzi golden coverage at light + dark for each meaningful state: `CrumbsTopBar`, `CrumbsBottomNav`, `CrumbsButton`, `CrumbsIconButton`, `CrumbsBookmarkCard`, `CrumbsFilterBar` (new), `CrumbsSnackbar` (new), `CrumbsBanner` (new for sync-error).
- **AC-C3** [interactive] Each active component's Roborazzi golden matches the handoff mock at ≥95% per-pixel RGB threshold (1%) with ≤5% changed-pixel allowance.

### Layouts & navigation (slice candidate)

- **AC-L1** [automated] `HomeScaffold`, `OverlayShell`, `OnboardingShell` exist at `core/designsystem/layouts/` with the slot APIs described in `handoff-layouts-pages.jsx`.
- **AC-L2** [automated] The bottom-nav destinations remain `Twitter, Reddit, All, Map` (existing 4); destination wiring in `Crumbs.kt` is unchanged at the route level.
- **AC-L3** [interactive] The TopBar's expanding search affordance functions identically to the current behavior (regression check).

### Screens (slice candidate)

- **AC-S1** [interactive] Each of the 6 screens (`Splash, Onboarding, Login, Home, AllBookmarks, MapView`) renders at ≥95% match to its Option D mock at Pixel 6 (411×891 dp, density 2.625, API 34) on **both** light and dark themes. Captured via Roborazzi golden suite at `feature:*` and `app/` test sources.
- **AC-S2** [interactive] Maestro happy-path flow runs end-to-end without assertion failure: launch → onboarding (4 pages, skip ok) → login (skip if already authed) → home/Twitter tab → home/Reddit tab → home/All tab → long-press a card → tap each menu action → return → tap filter chip → toggle a Type filter → open Tags overlay → multi-select → Apply.
- **AC-S3** [automated] `OnboardingScreen` uses `androidx.compose.foundation.pager.HorizontalPager` (not `com.google.accompanist.pager.HorizontalPager`).

### Behaviors (slice candidate)

- **AC-B1** [interactive] Long-press on a bookmark card displays the contextual popup anchored within ±8dp of the touch point on a Pixel 6; the popup contains exactly four actions in this order: Open, Share, Edit tags, Delete.
- **AC-B2** [interactive] Type chip changes apply instantly (single-select); Tags and Collection chips open the OverlayShell bottom sheet, allow multi-select, and apply on tapping the brutalist "APPLY" button.
- **AC-B3** [automated] Soft-delete writes an entry to a new `deleted_ids` Room table; the next sync filters out tombstoned ids. Undo within 5s removes the tombstone and re-displays the card.
- **AC-B4** [automated] `deleted_ids` table has a `bookmark_id PRIMARY KEY` + `deleted_at INTEGER` schema; Room migration from current DB version to the next is exported to `app/schemas/` and runs cleanly on an existing v1.1 DB.
- **AC-B5** [interactive] Forcing a Twitter 401 (via test interceptor) causes the inline ink-stroked banner to appear above the Twitter tab feed within 1s; resolving the error (replay with 200) clears it.

### Regression (always-on)

- **AC-R1** [automated] All non-deleted pre-existing JUnit tests pass.
- **AC-R2** [automated] `core/designsystem` Roborazzi suite passes on regenerated goldens (intentional change) with no failed *un*-regenerated goldens for components that survived (i.e. regenerate where we changed visuals, but don't tolerate accidental drift in unchanged areas).
- **AC-R3** [automated] OAuth flows (Twitter, Reddit) and Firestore sync paths are not modified at the bytecode level outside of import/theme adjustments — verified by absence of changes in `feature/twitter/.../LoginViewModel.kt`, `feature/reddit/.../RedditViewModel.kt`, and the sync modules during the redesign slices.

## Non-Functional Requirements

- **Offline rendering:** With network disabled, all six screens render fonts and colors exactly as online (fonts bundled, no Downloadable Fonts path, no remote token fetch).
- **Cold-start budget:** No regression beyond +50ms on a Pixel 6 (debug build) compared to v1.1 baseline. (Compose 1.11.1 ships incremental cold-start improvements per release notes; budget should be easy.)
- **APK size:** +400KB headroom for two bundled font families (Funnel Display + IBM Plex Mono, regular + bold + extrabold variants).
- **Min/target SDK:** unchanged at minSdk 24 / compileSdk 34 / targetSdk 34.
- **Accessibility:** every interactive composable surfaces a `contentDescription` or visible text label. (Full a11y audit is out of scope; baseline semantic correctness is in scope.)

## Edge Cases / Failure Modes

| Case | Treatment |
|---|---|
| Funnel Display / IBM Plex Mono font file corrupt at runtime | Fall back to `FontFamily.SansSerif` / `FontFamily.Monospace`. Log via Timber. The brutalist visual degrades but the app stays functional. |
| Soft-delete during active sync → undo after sync ran but before window expired | Removing the tombstone restores the card; if the sync had already deleted it from the feed cache, the feed re-shows the next paged page. Test with a fake clock. |
| User multi-selects 50+ tags in the OverlayShell | OverlayShell is scrollable; "APPLY" stays pinned to bottom. No artificial limit. |
| Roborazzi golden drift due to font hinting on a different host machine | Tolerated up to 5% changed-pixel allowance. Goldens committed from the dev's local machine; CI uses the same emulator profile (Pixel 6, API 34). |
| Dark-mode toggle mid-session | Recomposes via `isSystemInDarkTheme()`; user-selected override (if any future setting) goes through DataStore. |
| OAuth banner overlaps with the filter bar | Banner inserts between TopBar and FilterBar — banner stacks ABOVE the FilterBar, not below it. |
| Maestro can't find a card to long-press because feed hasn't synced | Maestro flow seeds DB with two fake bookmarks via an `androidTest`-mode debug-only data injector. Production code unaffected. |
| Migration of `deleted_ids` table fails on an existing v1.1 install | Room test verifies the migration against an exported v1.1 schema fixture. Migration is additive (CREATE TABLE) — destructive fallback disabled. |
| Compose-Accompanist Pager API differences | Accompanist `rememberPagerState(pageCount = 4)` → Compose-native `rememberPagerState(pageCount = { 4 })` (lambda form). Caught by compiler; addressed in slice that touches `OnboardingScreen`. |

## Affected Areas

Sourced from Explore sub-agent 1.

**Modules touched (visual + theme):**
- `core/designsystem/` — every file in `theme/` and `components/`; layouts/ created; ~13 components deleted, ~13 rewritten, ~3 new (`CrumbsFilterBar`, `CrumbsSnackbar`, `CrumbsBanner`).
- `app/` — every file under `screens/`, `ui/theme/` (`Color.kt`, `Shape.kt`, `Theme.kt`, `Type.kt` reconcile with `core/designsystem` theme — likely retire), `MainActivity.kt`, `Crumbs.kt` (nav graph passes new shells), `CrumbApplication.kt` (no change).
- `feature/twitter/` and `feature/reddit/` — `TwitterBookmarksScreen` and `RedditBookmarksScreen` re-skin to use new shells. ViewModels and OAuth code untouched.

**Modules touched (toolchain only):**
- Every `build.gradle` and root `build.gradle`; `gradle/libs.versions.toml`; `gradle/wrapper/gradle-wrapper.properties` (Gradle 9.1+); `settings.gradle` (no change expected); `gradle.properties` (JVM args possibly).

**Resources:**
- `app/src/main/res/font/funnel_display_regular.ttf`, `…_bold.ttf`, `…_extrabold.ttf`.
- `app/src/main/res/font/ibm_plex_mono_regular.ttf`, `…_bold.ttf`.
- Possibly `core/designsystem/src/main/res/font/` if fonts move to the design module.

**Persistence:**
- `app/src/main/java/com/github/jayteealao/crumbs/db/AppDatabase.kt` — DB version bump; new `DeletedBookmarkDao` + `DeletedBookmark` entity.
- `app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/5.json` (new exported schema).
- New Room migration file.

**Tests:**
- `core/designsystem/src/test/.../components/*` — 13 deleted, 13 rewritten, 3 new test files.
- `app/src/test/` (new) — golden suite per screen × theme.
- `maestro/` (new top-level dir) — `happy_path.yaml`, `long_press.yaml`, `filter_overlay.yaml`, `sync_error.yaml`.

**Areas explicitly NOT touched:**
- `feature/twitter/.../LoginViewModel.kt`, `BookmarksViewModel.kt`, OAuth client code.
- `feature/reddit/.../RedditViewModel.kt`, Reddit OAuth code.
- Firestore sync modules.
- Room database tables other than the new `deleted_bookmarks` table.

## Dependencies / Sequencing Notes

Discovered constraints from Explore sub-agent 2:

1. **Toolchain upgrade must land first.** Roborazzi 1.37.0 requires Kotlin 2.x + AGP 9.0+. No partial path. Sequence inside the upgrade: Gradle wrapper → JDK 17 confirmation → KGP 2.3.21 → Compose BOM 2026.05.00 → Material3 1.4.0 → Roborazzi 1.37.0 + Robolectric 4.16. Regenerate the existing 154 Roborazzi goldens (with the OLD visual still — this isolates "did the toolchain break rendering" from "did the redesign change rendering").
2. **Tokens before components.** New `CrumbsColors`/`CrumbsTypography` are the type signature every component depends on. Cutover happens in the tokens slice.
3. **Components before layouts.** Layouts compose components in their slots.
4. **Layouts before screens.** Screens consume `HomeScaffold` / `OverlayShell` / `OnboardingShell`.
5. **Behaviors layer onto screens.** Long-press popup, filter overlay, snackbar, banner can land in a final slice or interleaved with the screens that host them. Slicer decides.
6. **`deleted_ids` schema bump** can be its own micro-slice or part of the soft-delete behavior slice; either way it precedes the behavior wiring and includes the Room migration test.
7. **Maestro flows** depend on every UI surface being final. They are the *last* slice or interleaved with the screen slices, not before.

## Questions Asked This Stage

1. Long-press menu actions?
2. Filter chip dimensions?
3. Onboarding trigger?
4. Save-input model?
5. Delete UX (reversibility)?
6. Filter application style (instant vs. apply)?
7. Toolchain upgrade placement?
8. MapView treatment?
9. Long-press surface (popup vs. sheet)?
10. Bottom-nav destinations?
11. Empty-state copy?
12. Loading state visual?
13. Soft-delete vs. re-sync race?
14. Font fallback strategy?
15. Sync-error UI surface?
16. Roborazzi tolerance thresholds?
17. Orphan-component disposition?
18. CrumbsColors schema migration?
19. Accompanist Pager migration?
20. Canonical golden-test device profile?

## Answers Captured This Stage

See `po-answers.md` § shape for the full structured log. Key locked decisions:

- Long-press: 4 actions, contextual popup anchored to card.
- Filters: Type instant, Tags + Collection multi-select via OverlayShell.
- Onboarding: first-launch only.
- Save: integration auto-pull only (no manual paste, no share intent).
- Delete: soft + 5s undo + tombstone (`deleted_ids` table).
- Toolchain: inline first slice; Kotlin 2.3.21 / AGP 9.1.1 / Compose 1.11.1 / Roborazzi 1.37.0.
- Map: brutalist "COMING SOON" placeholder, no maps SDK.
- Nav tabs: Twitter, Reddit, All, Map (unchanged set).
- Empty state: "NO CRUMBS YET" + CONNECT AN ACCOUNT CTA → LoginScreen.
- Loading: sharp skeletons + scan-line.
- Fonts: bundled in `res/font/`, both families, OFL.
- Sync error: inline ink-stroked banner above affected feed.
- Roborazzi: 1% threshold, 5% changed-pixel allowance.
- Orphans: delete all 13 outright.
- Color schema: hard cutover, no deprecation.
- Pager: migrate Onboarding to Compose-native HorizontalPager.
- Device: Pixel 6 (`w411-h891-xxhdpi`, API 34).

## Out of Scope

- Manual URL paste / share-intent ingestion.
- New bottom-nav destinations beyond the current 4.
- Functional Map view (still placeholder).
- Maps SDK / geolocation permissions.
- Tablet layout adaptation; XR / foldable / glasses; landscape goldens.
- Full accessibility audit; TalkBack-specific UI; reduce-motion variants.
- Performance work beyond cold-start non-regression.
- Backend / API / Firestore protocol changes.
- Twitter / Reddit OAuth flow changes beyond surface re-skin.
- DB schema changes other than the `deleted_bookmarks` table.
- Compose multiplatform / iOS parity.

## Definition of Done

The redesign is done when **all** of the following hold:

1. **AC-T1…T3, AC-K1…K5, AC-C1…C3, AC-L1…L3, AC-S1…S3, AC-B1…B5, AC-R1…R3** are met (Acceptance Criteria section).
2. The Maestro happy-path flow plus its three failure-mode flows (long-press, filter overlay, sync error) run green from `maestro test maestro/` on a freshly-installed debug build on a Pixel 6 emulator (API 34).
3. The `core/designsystem` Roborazzi suite produces zero diffs on a regenerated golden baseline; per-screen Roborazzi suite (added under `app/src/test/`) produces zero diffs against the regenerated baseline for all 6 screens × 2 themes = 12 goldens minimum.
4. `lazylogcat` capture during the Maestro run shows zero `ERROR`-level theming or rendering warnings.
5. PR description (handoff stage) describes the user-facing change without referencing internal workflow stages.
6. `versionCode` bumps to **3** and `versionName` to **2.0** in `app/build.gradle`.
7. `CHANGELOG.md` has a v2.0 entry describing the visual identity, new behaviors, and the toolchain bump (in user language).

## Verification Strategy

**Automated checks (CI/test suite):**
- `./gradlew :app:assembleDebug :core:designsystem:assembleDebug :feature:twitter:assembleDebug :feature:reddit:assembleDebug :core:pref:assembleDebug :core:models:assembleDebug` — build all modules.
- `./gradlew lintDebug kotlinterCheck` — static checks.
- `./gradlew test verifyRoborazziDebug` — JVM tests including Roborazzi golden compare.
- `./gradlew :app:connectedDebugAndroidTest` — instrumentation tests, including the Room migration test for `deleted_bookmarks`.

**Interactive verification (running app on emulator):**
- Platform: Android.
- Tool: Maestro 2.4.0 CLI (installed on dev box) + `android` CLI for AVD orchestration + `lazylogcat` for log capture.
- AVD: `Pixel_6_API_34` (`w411-h891-xxhdpi`, density 2.625).
- Flows (under `maestro/`):
  - `happy_path.yaml` — full nav walk through every screen and every long-press action.
  - `long_press.yaml` — focused popup + 4-action verification.
  - `filter_overlay.yaml` — Type instant + Tags overlay + Collection overlay.
  - `sync_error.yaml` — token-failure banner appears / clears.
- Evidence capture: Maestro auto-records device output to `~/.maestro/tests/<timestamp>/`; Roborazzi goldens live in `core/designsystem/src/test/snapshots/` and per-screen test source dirs.

**Human-in-the-loop checks:**
- Side-by-side visual review of each rendered screen against its Option D HTML mock in a desktop browser at 412×920 viewport. ≥95% subjective match.
- Verify accent orange `#FF5A1F` reads correctly on AMOLED dark backgrounds (no muddy bleed).
- Spot-check the `verify-*.jpg` author reference screenshots from the handoff bundle against the live render for the screens those JPGs cover.

## Documentation Plan

| Type | Audience | Must cover | Must NOT cover | Target location |
|---|---|---|---|---|
| **readme-update** | new contributors & future-self | new visual identity, toolchain version table, how to run the Roborazzi + Maestro suites locally, the `android-cli` + `lazylogcat` companion workflow | per-component API; design rationale | `README.md` |
| **reference** | maintainer | `CrumbsColors`, `CrumbsTypography`, `CrumbsShapes`, `CrumbsSpacing` public APIs; component signatures for the 13 surviving atomic composables; the three layout shells | tutorial-level setup; opinion | `docs/design-system.md` (new) |
| **explanation** | future maintainer | Why the brutalist direction; why Roborazzi over Paparazzi; why fonts bundled instead of Downloadable; why no Material3 dynamic theming | step-by-step how-to | `docs/design-decisions.md` (new) |
| **changelog** | end-user-ish (release notes lens) | v2.0 entry: new look, new behaviors, no functional regressions, toolchain bump implications | internal stage names | `CHANGELOG.md` |

No tutorial doc required — Crumb is a single-user app, no "getting started" path exists. No how-to doc required for v1 — the design system is internal-only and the README + reference suffice.

## Freshness Research

- **Source:** [Kotlin 2.3.20 release blog](https://blog.jetbrains.com/kotlin/2026/03/kotlin-2-3-20-released/) and [Compose April 2026 updates](https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html).
  Why it matters: AC-T1 pins exact versions; the toolchain slice must hit reproducible coordinates.
  Takeaway: Compose BOM `2026.05.00` (Compose 1.11.1) + Kotlin `2.3.21` + AGP `9.1.1` + Gradle `9.1+` + JDK 17 is the locked target.

- **Source:** [Roborazzi GitHub](https://github.com/takahirom/roborazzi) and [Roborazzi snapshot testing guide](https://medium.com/@prnksingh829/snapshot-testing-jetpack-compose-with-roborazzi-a-quickstart-guide-911358662d9c).
  Why it matters: existing repo already has Roborazzi 1.7.0; we are upgrading, not adding.
  Takeaway: Roborazzi 1.37.0 + Robolectric 4.16 is the target. Hilt integration is via standard `HiltAndroidRule`; no special wiring needed beyond what `CrumbApplication` already has.

- **Source:** [Maestro Compose docs](https://docs.maestro.dev/get-started/supported-platform/android/jetpack) and [Maestro CLI 2.4.0](https://maestro.dev/blog/maestro-cli-2-4-0).
  Why it matters: behavioral verification (AC-S2, AC-B1, AC-B2, AC-B5).
  Takeaway: no Gradle dep. Layout shells must add `Modifier.semantics { testTagsAsResourceId = true }` at the root and `Modifier.testTag("…")` on every node Maestro asserts on. Flows live under `maestro/` at repo root.

- **Source:** [Funnel Display on Google Fonts](https://fonts.google.com/specimen/Funnel%2BDisplay) and [IBM Plex Mono on Google Fonts](https://fonts.google.com/specimen/IBM%2BPlex%2BMono).
  Why it matters: AC-K3 + NFR offline rendering + AC font-fallback edge case.
  Takeaway: Both OFL 1.1, free for commercial bundling. Bundle into `res/font/` as static TTFs (regular/bold/extrabold for sans, regular/bold for mono) — variable-axis fonts not needed.

- **Source:** [Material3 with Compose · disabling dynamic color](https://siddroid.com/post/compose/dynamic-themes-with-compose-and-material-3/).
  Why it matters: AC-K4 — wallpaper-tinted accent would ruin the brutalist identity on Android 12+.
  Takeaway: Never call `dynamicLightColorScheme()` / `dynamicDarkColorScheme()`; build the `MaterialTheme` colorScheme manually from our literal hex values (or skip MaterialTheme entirely and stay on the existing CompositionLocal-based `CrumbsTheme`).

- **Source:** [Compose grid layouts](https://developer.android.com/develop/ui/compose/lists) and [Compose shadows](https://developer.android.com/develop/ui/compose/graphics/draw/shadows).
  Why it matters: CSS→Compose translation gotchas (`gridTemplateColumns`, `box-shadow`).
  Takeaway: `Row + Modifier.weight(1f)` for fixed grid; `LazyVerticalGrid(GridCells.Fixed(n))` when scroll is needed. Brutalist uses no shadows — omit `Modifier.shadow` entirely.

- **Source:** [Android Security Bulletin May 2026](https://source.android.com/docs/security/bulletin/2026/2026-05-01).
  Why it matters: confirm no advisories block the toolchain bump.
  Takeaway: zero CVEs on Compose, Material3, Hilt, Room, Roborazzi, Maestro in the last 12 months. No security blockers.

## Recommended Next Stage

- **Option A (default):** `/wf slice brutalist-redesign` — proceed to slicing. The shaped spec has at minimum 6 natural delivery clusters (toolchain, tokens, components, layouts, screens, behaviors+tests) with explicit sequencing constraints; slicing is mandatory.
- **Option B:** `/wf plan brutalist-redesign` — skip slice and go straight to a single plan. **Not recommended.** Too many delivery units with internal dependencies; a single plan would either be unbuildable or paper over the slicing decisions.
- **Option C:** `/wf intake brutalist-redesign` — revisit intake. Not recommended; intake brief held up against shaping.
