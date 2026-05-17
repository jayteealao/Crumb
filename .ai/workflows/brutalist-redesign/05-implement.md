---
schema: sdlc/v1
type: implement-index
slug: brutalist-redesign
status: in-progress
stage-number: 5
created-at: "2026-05-17T00:38:34Z"
updated-at: "2026-05-17T15:24:46Z"
slices-implemented: 5
slices-total: 8
metric-total-files-changed: 206
metric-total-lines-added: 2446
metric-total-lines-removed: 7908
tags: [redesign, toolchain, tokens, quick-skip-auth-page, components, layouts]
refs:
  index: 00-index.md
  plan-index: 04-plan.md
next-command: wf-verify
next-invocation: "/wf verify brutalist-redesign layouts"
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

### `screens`, `behaviors`, `maestro` — not yet planned

Per the rolling-plan strategy in [04-plan.md](04-plan.md). The `behaviors` slice owns:
- Real fingertip Offset routing from `CrumbsBookmarkCard.onLongPress(bookmark, offsetPx)` into `CrumbsLongPressPopup.anchorOffsetPx`.
- ARCHIVE action behavioral semantics (hide-from-feed, retrievable via settings).
- `Modifier.dropShadow` follow-up (BookmarkCard pressed-state + LongPressPopup container).
- Snackbar timer + soft-delete tombstone state machine.
- Banner trigger (sync-error 401).
- Reconciling the Twitter screen's 4th `Logout` popup action against the canonical handoff Screen 5 layout.

## Recommended Next Stage

- **Option A (default):** `/wf verify brutalist-redesign layouts` — automated gates already green (compile + assembleDebug + recordRoborazziDebug + verifyRoborazziDebug + lintDebug); verify stage owns AC adjudication, AC-2 inset measurement deferral registration, AC-5 Maestro deferral registration. **Compact recommended** — implementation context is noise for verification.
- **Option B:** `/wf plan brutalist-redesign screens` — start the next slice's plan; can run in parallel with layouts verify.
- **Option C:** `/wf review brutalist-redesign layouts` — skip verify; less recommended since AC-2 + AC-5 carry runtime claims that benefit from explicit deferral bookkeeping.
- **Option D:** `/wf verify brutalist-redesign components` — finish the still-open components verify-partial gate (AC-C5 reconciled to static, AC-C6 deferred to maestro). If you want to land components fully before opening more verify scope, this clears the books first.
