---
schema: sdlc/v1
type: implement-index
slug: brutalist-redesign
status: in-progress
stage-number: 5
created-at: "2026-05-17T00:38:34Z"
updated-at: "2026-05-17T10:23:06Z"
slices-implemented: 3
slices-total: 8
metric-total-files-changed: 164
metric-total-lines-added: 649
metric-total-lines-removed: 6074
tags: [redesign, toolchain, tokens, quick-skip-auth-page]
refs:
  index: 00-index.md
  plan-index: 04-plan.md
next-command: wf-verify
next-invocation: "/wf verify brutalist-redesign tokens"
---

# Implement Index

## Cross-Slice Integration Notes

- The `toolchain` slice produced a working Kotlin 2.2.10 / Compose 1.11.1 (BOM 2026.05.00) / Material3 1.4.0 / Room 2.8.4 / Hilt 2.59.2 chain on Gradle 9.3.1 + AGP 9.1.1 + JDK 17. **The locked Kotlin target moved from 2.3.21 → 2.2.10** to match AGP 9.1.1's bundled compiler. Downstream slices target Material3 1.4.0 APIs and Compose 1.11.1 idioms.
- The `tokens` slice replaced the cyan-era `CrumbsColors`/`CrumbsTypography`/`CrumbsShapes` surface with the brutalist Option-D handoff values, added `CrumbsStroke`, bundled IBM Plex Mono, and pulled forward the **13 orphan-component deletions** that were originally scoped for the components slice. The components slice's AC-C1 (delete 13 orphans) is now a verification-only criterion.
- **The intermediate state is intentionally lossy.** Body text on v1.1 component layouts now renders in IBM Plex Mono at 12sp `bodyMono`. Reviewers diffing tokens → components goldens must not flag the mono body on v1.1 layouts as a regression — the components slice rewrites every consumer.
- **CrumbsBookmarkCard and TagEditorDialog carry temporary inline stand-ins** for orphan components they depended on (ThreadIndicator, CrumbsTagChip, CrumbsVideoPlayer, CrumbsDialog, CrumbsTextField). Components slice owns rebuilding both with brutalist primitives.
- **`testTagsAsResourceId` scaffolding** in place at the `CrumbsTheme` root since toolchain; the `tokens` slice adds a top-level `Modifier.testTag("app_root")` so Maestro can address the root. Every later slice should add `Modifier.testTag(...)` to components and screens as it composes them; Maestro will address them in the final slice.
- **133 Roborazzi goldens regenerated** during toolchain on v1.1 visuals. The `tokens` slice replaced most of them: ~30 deleted alongside their orphan tests, ~90 regenerated with the brutalist palette on v1.1 layouts. Components slice will regenerate again after each component is rebuilt.
- **`kotlinter` is removed from the build** (toolchain). Re-introducing it is a follow-up unrelated to tokens/components/etc.
- **Coil 3 migration is deferred to the `components` slice.** `feature/twitter/components/TwitterCard.kt` still uses `com.commit451.coil-transformations` (Coil 2-only). The components slice's rewrite of the bookmark card surface absorbs the Coil 3 namespace migration.

## Slice Status

### `toolchain` — complete

- Six commits on `feat/brutalist-redesign` (`143832b`..`f637a52`); HEAD: `f637a52` (pre-tokens).
- Build green: `:app:assembleDebug` and `:core:designsystem:verifyRoborazziDebug` both pass.
- 11 plan deviations recorded — see [05-implement-toolchain.md](05-implement-toolchain.md).
- Emulator smoke test deferred to verify stage (manual).

### `tokens` — complete

- One atomic commit on `feat/brutalist-redesign` (sha recorded in `05-implement-tokens.md` frontmatter after commit lands).
- Build + lint + `recordRoborazziDebug` + `verifyRoborazziDebug` all green on the new chain.
- 4 plan deviations recorded — see [05-implement-tokens.md](05-implement-tokens.md):
  1. CrumbsBookmarkCard had orphan dependencies — inlined Material3 stand-ins.
  2. TagEditorDialog had orphan dependencies — rewritten with Material3 stand-ins.
  3. Plan under-counted surviving consumers (13 components + 2 screens vs. 7 + 2).
  4. `buttonSmall`/`cardSmall`/`videoPlayer` shape fields deleted entirely (call sites rewired to `button`/`card`/`rectangle`).
- Interactive emulator verification (Pixel 6 API 34) deferred to verify stage.
- AC-K5 (handoff manual diff) deferred per plan; will register as `runtime-evidence-deferral` at verify.

### `quick-skip-auth-page` — complete (compressed slice)

- One slice file at [05-implement-quick-skip-auth-page.md](05-implement-quick-skip-auth-page.md); attached to the workflow via slug-mode (`/wf-quick quick`) to unblock `runtime-evidence-deferrals[3]` (tokens AC-K6) and the broader screens-level evidence pipeline.
- Six files changed, +67/-11 lines. Original 2-file plan expanded to 6 files because the AC-Q3 emulator check exposed pre-existing `paging-compose:1.0.0-alpha17` ABI rot against the post-`toolchain` `paging-common:3.3.x` graph. Bumped to `paging-compose:3.3.6` and migrated three `LazyListScope.items(LazyPagingItems)` call sites to the count-based API (`TwitterBookmarksScreen`, `RedditBookmarksScreen`, `AllBookmarksScreen`).
- `HomeScreen` reachable from debug builds via a `BuildConfig.DEBUG`-gated "Skip Auth (Debug)" button on `LoginScreen`. Light- and dark-mode `HomeScreen` evidence captured.
- Newly-visible defect surfaced for later slices: duplicate `Twitter / Reddit / All / Map` tab labels at the top of `HomeScreen` content area in addition to the legitimate bottom navigation. Not introduced by this slice; belongs in `screens` or `layouts`.

### `components`, `layouts`, `screens`, `behaviors`, `maestro` — not yet planned

Per the rolling-plan strategy in [04-plan.md](04-plan.md). The components-slice plan, when drafted, must reflect (a) the 13 orphan-component deletions already shipped in tokens, (b) the temporary inline stand-ins in CrumbsBookmarkCard and TagEditorDialog that the components slice owns rebuilding, (c) the absence of `buttonSmall`/`cardSmall`/`videoPlayer` from `CrumbsShapes`.

## Recommended Next Stage

- **Option A (default):** `/wf verify brutalist-redesign tokens` — emulator install + interactive AC-K3/AC-K6/AC-K5 evidence on Pixel 6 API 34. **Compact recommended.**
- **Option B:** `/wf plan brutalist-redesign components` — kick off next-slice planning while tokens reality is fresh.
- **Option C:** `/wf review brutalist-redesign tokens` — skip verify; less recommended since AC-K6 is interactive.
