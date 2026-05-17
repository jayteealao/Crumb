---
schema: sdlc/v1
type: plan-index
slug: brutalist-redesign
status: complete
stage-number: 4
created-at: "2026-05-16T22:37:59Z"
updated-at: "2026-05-17T11:11:14Z"
planning-mode: rolling
slices-planned: 3
slices-total: 7
implementation-order: [toolchain, tokens, components, layouts, screens, behaviors, maestro]
conflicts-found: 0
tags: [redesign, ui, compose, design-system, brutalist, roborazzi, maestro]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
next-command: wf-implement
next-invocation: "/wf implement brutalist-redesign tokens"
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

### `layouts` *(deferred)*

HomeScaffold, OverlayShell, OnboardingShell in `core/designsystem/layouts/`. Plan after `components` since the shells compose against the final component API.

### `screens` *(deferred)*

Rewrite 6 app screens + 2 feature-module screens; migrate Accompanist Pager → Compose-native Pager. **Re-split clause** active: if estimate >2 dev-days at plan time, split into `screens-feed` and `screens-shells`. Plan after `layouts` lands.

### `behaviors` *(deferred)*

Wire 4 implied behaviors + `deleted_bookmarks` Room table + tombstone-aware sync filter + version bump to 2.0/3. Plan after `screens` lands.

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
3. **`components`** *(planned, ready to implement)* — 13 active rebuilds + 4 new components + QuickActionMenu retirement + scan-line motion + Roborazzi tolerance config + kotlinx.immutable adoption.
4. **`layouts`** *(plan after components ships)* — reusable scaffolds.
5. **`screens`** *(plan after layouts ships, may re-split)* — composition.
6. **`behaviors`** *(plan after screens ships)* — wiring + DB schema.
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

- **Option A (default):** `/wf implement brutalist-redesign components` — execute the components plan. **Compact first** — planning research (sub-agent reports, web searches, discovery rounds) is noise for the implement loop; PreCompact hook preserves workflow state on disk.
- **Option B:** `/wf plan brutalist-redesign components <feedback>` — revise this slice's plan if any of the 12 PO discovery answers feels wrong on second read.
- **Option C:** `/wf verify brutalist-redesign tokens` — formally verify the tokens slice before starting components implement. Optional; the tokens slice already verified-partial.
- **Option D:** `/wf slice brutalist-redesign` — revisit slice boundaries. **Not recommended** — components-plan surfaced no boundary problem; the per-family commit grouping and ARCHIVE-flagged-for-behaviors are within-slice scoping details.
