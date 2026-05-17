---
schema: sdlc/v1
type: implement-index
slug: brutalist-redesign
status: in-progress
stage-number: 5
created-at: "2026-05-17T00:38:34Z"
updated-at: "2026-05-17T13:16:45Z"
slices-implemented: 4
slices-total: 8
metric-total-files-changed: 191
metric-total-lines-added: 1715
metric-total-lines-removed: 7907
tags: [redesign, toolchain, tokens, quick-skip-auth-page, components]
refs:
  index: 00-index.md
  plan-index: 04-plan.md
next-command: wf-verify
next-invocation: "/wf verify brutalist-redesign components"
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

### `layouts`, `screens`, `behaviors`, `maestro` — not yet planned

Per the rolling-plan strategy in [04-plan.md](04-plan.md). The `behaviors` slice owns:
- Real fingertip Offset routing from `CrumbsBookmarkCard.onLongPress(bookmark, offsetPx)` into `CrumbsLongPressPopup.anchorOffsetPx`.
- ARCHIVE action behavioral semantics (hide-from-feed, retrievable via settings).
- `Modifier.dropShadow` follow-up (BookmarkCard pressed-state + LongPressPopup container).
- Snackbar timer + soft-delete tombstone state machine.
- Banner trigger (sync-error 401).
- Reconciling the Twitter screen's 4th `Logout` popup action against the canonical handoff Screen 5 layout.

## Recommended Next Stage

- **Option A (default):** `/wf verify brutalist-redesign components` — Roborazzi gate is green; verify stage owns the maintainer-subjective AC-C2 diff, interactive AC-C5 scan-line check, and Maestro studio AC-C6 dry-run on Pixel 6 API 34. **Compact recommended** — implementation context is noise for verification.
- **Option B:** `/wf plan brutalist-redesign layouts` — start the next slice's planning in parallel with verify.
- **Option C:** `/wf review brutalist-redesign components` — skip verify; less recommended since AC-C5 and AC-C6 are interactive.
