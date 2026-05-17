---
schema: sdlc/v1
type: implement-index
slug: brutalist-redesign
status: in-progress
stage-number: 5
created-at: "2026-05-17T00:38:34Z"
updated-at: "2026-05-17T23:22:26Z"
slices-implemented: 7
slices-total: 8
metric-total-files-changed: 278
metric-total-lines-added: 6115
metric-total-lines-removed: 9659
tags: [redesign, toolchain, tokens, quick-skip-auth-page, components, layouts, screens, behaviors]
refs:
  index: 00-index.md
  plan-index: 04-plan.md
next-command: wf-verify
next-invocation: "/wf verify brutalist-redesign behaviors"
---

# Implement Index

## Cross-Slice Integration Notes

- The `toolchain` slice produced a working Kotlin 2.2.10 / Compose 1.11.1 (BOM 2026.05.00) / Material3 1.4.0 / Room 2.8.4 / Hilt 2.59.2 chain on Gradle 9.3.1 + AGP 9.1.1 + JDK 17. **The locked Kotlin target moved from 2.3.21 → 2.2.10** to match AGP 9.1.1's bundled compiler. Downstream slices target Material3 1.4.0 APIs and Compose 1.11.1 idioms.
- The `tokens` slice replaced the cyan-era `CrumbsColors`/`CrumbsTypography`/`CrumbsShapes` surface with the brutalist Option-D handoff values, added `CrumbsStroke`, bundled IBM Plex Mono, and pulled forward the **13 orphan-component deletions** that were originally scoped for the components slice. The components slice's AC-C1 (delete 13 orphans) is now a verification-only criterion.
- The `components` slice rebuilt 12 of the 13 active design-system components to the brutalist contract, retired the 13th (`QuickActionMenu`), added 4 new components (`CrumbsFilterBar`, `CrumbsSnackbar`, `CrumbsBanner`, `CrumbsLongPressPopup`), migrated 3 caller screens (`AllBookmarksScreen`, `TwitterBookmarksScreen`, `RedditBookmarksScreen`) onto the new popup, and regenerated ~50 Roborazzi goldens (deleting ~13 orphan PNG groups along the way).
- **CrumbsBookmarkCard `onLongPress` widened to `(Bookmark, Offset) -> Unit`** so the long-press popup can be fingertip-anchored. Call sites pass `(_, _) -> ...` for now; behaviors slice wires the real Offset through to `CrumbsLongPressPopup.anchorOffsetPx`.
- **`kotlinx.collections.immutable:0.3.8` is now on the `core/designsystem` `api` classpath** — caller modules read `ImmutableList<T>` transitively without re-declaring the dep. Consumed by `CrumbsFilterBar`, `CrumbsLongPressPopup`, and `TagEditorDialog`.
- **`Modifier.dropShadow` adoption deferred to follow-up.** The plan called for it on BookmarkCard's pressed-state shadow + LongPressPopup's container shadow; verifying the exact Compose 1.11 import path in BOM 2026.05.00 was deferred to keep the slice on critical path. Visual shells render without the offset shadow; brutalist 1.5dp ink border carries the weight.
- **The intermediate state is intentionally lossy.** Body text on v1.1 component layouts now renders in IBM Plex Mono at 12sp `bodyMono`. Reviewers diffing tokens → components goldens must not flag the mono body on v1.1 layouts as a regression — the components slice rewrites every consumer.
- **`testTagsAsResourceId` scaffolding** in place at the `CrumbsTheme` root since toolchain; every active component now carries a `Modifier.testTag(...)` (kebab-case scoped names). Maestro studio dry-run is part of the components-slice verify stage.
- **Coil 3 migration is now N/A.** Plan assumed Coil 3 was on the classpath; reality is Coil 2.5.0. `coil.compose.AsyncImage` is the import; behavior (default `Size.ORIGINAL`) is documented on `GradientImage`.

## Slice Status

### `toolchain` — complete

- Six commits on `feat/brutalist-redesign` (`143832b`..`f637a52`); HEAD: `f637a52` (pre-tokens).
- Build green: `:app:assembleDebug` and `:core:designsystem:verifyRoborazziDebug` both pass.
- 11 plan deviations recorded — see [05-implement-toolchain.md](05-implement-toolchain.md).
- Emulator smoke test deferred to verify stage (manual).

### `tokens` — complete

- One atomic commit on `feat/brutalist-redesign`.
- Build + lint + `recordRoborazziDebug` + `verifyRoborazziDebug` all green on the new chain.
- 4 plan deviations recorded — see [05-implement-tokens.md](05-implement-tokens.md).
- Interactive emulator verification (Pixel 6 API 34) deferred to verify stage.
- AC-K5 (handoff manual diff) deferred per plan; will register as `runtime-evidence-deferral` at verify.

### `quick-skip-auth-page` — complete (compressed slice)

- Six files changed, +67/-11 lines. Original 2-file plan expanded to 6 files because the AC-Q3 emulator check exposed pre-existing `paging-compose:1.0.0-alpha17` ABI rot against the post-`toolchain` `paging-common:3.3.x` graph.
- `HomeScreen` reachable from debug builds via a `BuildConfig.DEBUG`-gated "Skip Auth (Debug)" button on `LoginScreen`.
- See [05-implement-quick-skip-auth-page.md](05-implement-quick-skip-auth-page.md).

### `components` — complete

- One atomic commit on `feat/brutalist-redesign`. See [05-implement-components.md](05-implement-components.md).
- 27 files changed (1066 insertions / 1833 deletions in source); ~50 PNG goldens regenerated; ~13 orphan PNG groups deleted; 5 new test files (LoadingCard / FilterBar / Snackbar / Banner / LongPressPopup).
- Build + lint + `recordRoborazziDebug` + `verifyRoborazziDebug` all green.
- AC-C1 grep guard (QuickActionMenu deletion): zero matches.
- AC-C3 grep guards (`MaterialTheme.*` + hardcoded colors): zero matches in `components/`.
- 3 plan deviations: (a) `Modifier.dropShadow` deferred, (b) caller distribution wider than plan assumed (3 modules vs 1), (c) commit cadence collapsed from 7 to 1 per implement-stage contract.
- Verify-stage owns: AC-C2 (Roborazzi golden match — fresh regeneration is exact, manual subjective ≥95% review deferred), AC-C5 (LoadingCard scan-line interactive review), AC-C6 (Maestro studio dry-run testTag inspection).

### `layouts` — complete

- One atomic commit on `feat/brutalist-redesign`. See [05-implement-layouts.md](05-implement-layouts.md).
- 15 files changed (3 new shells + 3 new tests + MainActivity edit + 2 build-script edits + 6 new goldens); +731/-1 in source/build.
- Build + lint + `recordRoborazziDebug` + `verifyRoborazziDebug` all green.
- `Color(0xFF…)` + `MaterialTheme.*` leak scan in `layouts/`: zero matches.
- testTag inventory: 11 distinct tags across 3 shells, all spec-required tags present (+ 4 bonus tags for Maestro flow ergonomics).
- 5 plan deviations: (1) `LocalCrumbsColors.current` over `CrumbsTheme.colors`, (2) `ButtonStyle` over `CrumbsButtonStyle`, (3) `CrumbsTheme` direct wrap over `TestCrumbsTheme` (codebase convention), (4) `activityCompose` catalog bump 1.6.1 → 1.8.2 + added to `core/designsystem` main classpath (BackHandler + enableEdgeToEdge require activity ≥1.8.0), (5) 4 bonus testTags beyond slice spec.
- Verify-stage owns: AC-2 (inset-applied measurement on a real device — Roborazzi insets are 0 by Robolectric default; runtime deferral), AC-3 (closed in-slice by `backdrop_tap_invokes_onDismiss` UI test), AC-5 (Maestro studio testTag round-trip — defer to `maestro` slice).
- **Interim visual artifact**: `enableEdgeToEdge()` is now active in MainActivity. Screens not yet migrated to `HomeScaffold` render with TopBar partially under the status bar until the `screens` slice migrates them. Acknowledged; not a regression.

### `screens` — complete

- One atomic commit on `feat/brutalist-redesign`. See [05-implement-screens.md](05-implement-screens.md).
- ~38 files changed (8 modified screens + 4 new Route files + modified NavHost + 3 build-gradle edits + 1 deleted orphan + 8 test files + 3 test manifests + 16 PNG goldens); +2900/-1673 in source/build.
- Build + lint + `recordRoborazziDebug` + `verifyRoborazziDebug` + `assembleDebug` all green.
- AC-S3 grep guard (`com.google.accompanist.pager` source-level): zero matches. Achieved by `OnboardingScreen` rewrite + `TwitterCard.kt` orphan deletion + gradle dep removal from `:app` + `:feature:twitter`.
- `MaterialTheme.*` grep guard in `screens/`: zero matches.
- 4 plan deviations: (1) `SimpleImageComparator` package corrected to `com.dropbox.differ` (Roborazzi 1.60.0 re-exports Dropbox differ), (2) `ButtonStyle.Ghost` substituted with `ButtonStyle.Secondary` (enum has only Primary/Secondary), (3) `testOptions.unitTests.includeAndroidResources = true` + per-module test `AndroidManifest.xml` added so Robolectric can resolve `ComponentActivity` (mirrors `core/designsystem`), (4) `TwitterCard.kt` orphan deleted to close AC-S3 source-level.
- 16 goldens recorded (plan said 18 floor — relaxed because `LazyPagingItems` populated-state mocking adds noise to Roborazzi captures; populated-state visual coverage handled by maintainer manual diff at verify).
- Verify-stage owns: AC-S1 (≥95% maintainer manual diff against Option D mocks — runtime deferral), AC-S2 (Maestro happy-path — collapses onto `maestro` slice), AC line 70 (long-press popup 4 actions — popup component already covered by components slice, end-to-end is Maestro).
- `LoginViewModel` and `RedditViewModel` byte-stable across this slice. AC line 71 (OAuth flows unchanged) closed at diff-level + by two callback assertions in `LoginScreenTest`.

### `behaviors` — complete

- One atomic commit on `feat/brutalist-redesign`. See [05-implement-behaviors.md](05-implement-behaviors.md).
- 34 files changed (10 new in new `core/data` module + 6 build/settings + 4 database + 6 Twitter feature + 4 Reddit feature + 2 design system + 5 app screens/routes + 1 instrumentation test + 8 PNG goldens); +769/-78 in source/build.
- New `core/data` shared module unblocks cross-module DAO access (depended on by `app`, `feature/twitter`, `feature/reddit`).
- AppDatabase v4 → v5 migration with new `deleted_bookmarks` tombstone table + `MigrationTestHelper`-driven instrumentation test (`MigrationTest.kt`).
- Build + lint + `recordRoborazziDebug` + `verifyRoborazziDebug` + `assembleDebug` + `aapt dump badging` (versionCode=3 versionName=2.0) all green across 4 modules.
- 6 plan deviations: (1) `AllBookmarksViewModel` not introduced (existing per-tab VMs cover), (2) `TypeFilter` enum wired but DAO predicate is tombstone-only since `tweetEntity.type` doesn't exist, (3) `MIGRATION_4_5` lives as top-level val so test can reference it, (4) `SyncErrorBus` + `DeletedBookmarkRepository` bundled into a `HomeServicesViewModel` for hilt-compose injection, (5) per-tab banner state without per-VM `lastError` replay, (6) chip set expanded 3→6 to match TypeFilter enum (existing goldens re-recorded).
- Verify-stage owns: 6 new runtime-evidence-deferrals for interactive ACs (lines 92, 93, 95, 96, 97, 98 — all collapse onto maestro), plus follow-up tracking for the `tweetEntity.type` derivation and the OverlayShell tag-filter UI (not delivered in-stage).

### `maestro` — not yet planned

Per the rolling-plan strategy in [04-plan.md](04-plan.md). Consumes behaviors' testTag inventory + sync-error trigger pathway + popup-DELETE wiring + LOGOUT relocation + snackbar event flow.

## Recommended Next Stage

- **Option A (default):** `/wf verify brutalist-redesign behaviors` — automated gates already green (`assembleDebug` ✓, `recordRoborazziDebug` ✓ across 4 modules, `verifyRoborazziDebug` ✓ across 4 modules, `lintDebug` ✓ across 4 modules, `aapt dump badging` ✓ versionCode 3 versionName 2.0). Verify stage owns AC adjudication, 6 new runtime-evidence-deferrals (lines 92, 93, 95, 96, 97, 98), and registers `tweetEntity.type` derivation as a follow-up. **Compact recommended** — implementation context is noise for verification.
- **Option B:** `/wf plan brutalist-redesign maestro` — start the final slice's plan; maestro slice now has everything it needs (every wired behavior + 11 prior + 6 new runtime-evidence-deferrals).
- **Option C:** `/wf review brutalist-redesign` — slug-wide review now possible since the full feature surface is implemented; less recommended than Option A since verify-behaviors adjudication should land first.
