---
schema: sdlc/v1
type: plan-index
slug: brutalist-redesign
status: complete
stage-number: 4
created-at: "2026-05-16T22:37:59Z"
updated-at: "2026-05-17T00:00:00Z"
planning-mode: single
slices-planned: 1
slices-total: 7
implementation-order: [toolchain, tokens, components, layouts, screens, behaviors, maestro]
conflicts-found: 0
tags: [redesign, ui, compose, design-system, brutalist, roborazzi, maestro]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
next-command: wf-implement
next-invocation: "/wf implement brutalist-redesign toolchain"
---

# Plan Index

This is a **rolling plan index**. The chain of slices is strictly linear (toolchain → tokens → components → layouts → screens → behaviors → maestro), so plans are written one at a time. The first plan (`toolchain`) is complete and execution-ready; the remaining six are deferred until their predecessor implements + verifies. After each slice ships, run `/wf plan brutalist-redesign <next-slice>` to draft the next plan against current codebase state.

## Slice Plan Summaries

### `toolchain` *(planned)*

- **Files to touch:** ~30 source files (build.gradle ×7, libs.versions.toml, gradle wrapper, CrumbsTheme.kt, 17 test classes with `@Config` bumps, GradientImage.kt + CrumbsBookmarkCard.kt for Coil 3 migration, two GH Actions workflows) + 133 regenerated PNG goldens.
- **Strategy:** strict ordering with a Phase A spike up front (KSP × Kotlin 2.3.21 verification). Each version bump is a discrete commit. Compose BOM 2026.05.00 governs all `androidx.compose.*` deps and Material3 1.4.0; Coil bumps to 3.x; Roborazzi 1.7.0 → 1.37.0; Robolectric 4.14.1 → 4.16. CI workflow gains lint + kotlinter + verifyRoborazziDebug gates. Final commit regenerates all 133 goldens against the unchanged v1.1 design on the new chain.
- **Key risk:** KSP × Kotlin 2.3.21 compatibility (mitigated by Phase A spike). Secondary: Coil 3 import-path migration ripple.
- **See:** [04-plan-toolchain.md](04-plan-toolchain.md).

### `tokens` *(deferred)*

CrumbsColors / CrumbsTypography / CrumbsShapes / CrumbsSpacing brutalist hard-cutover; bundle Funnel Display + IBM Plex Mono in `res/font/`; disable dynamic color. Plan is written after `toolchain` ships and the codebase is on Kotlin 2.3.21 + Compose 1.11.1 — call signatures for `Font(...)`, `MaterialTheme`, and `dynamic*ColorScheme` may have evolved on the new chain and the plan should be drafted against observed reality.

### `components` *(deferred)*

13 orphan deletions + 13 active rebuilds + 4 new (CrumbsFilterBar, CrumbsSnackbar, CrumbsBanner, CrumbsLongPressPopup). Roborazzi goldens at light + dark for every meaningful state. Plan is written after `tokens` lands so the rebuilt components can be planned against the final brutalist token surface.

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

1. **`toolchain`** *(planned, ready to implement)* — risk-first; everything else depends on the new chain.
2. **`tokens`** *(plan after toolchain ships)* — type contract for all downstream.
3. **`components`** *(plan after tokens ships)* — atomic primitives.
4. **`layouts`** *(plan after components ships)* — reusable scaffolds.
5. **`screens`** *(plan after layouts ships, may re-split)* — composition.
6. **`behaviors`** *(plan after screens ships)* — wiring + DB schema.
7. **`maestro`** *(plan after behaviors ships)* — end-to-end coverage.

## Conflicts Found

None. The toolchain plan is the first plan and has no sibling plans to conflict with. The slice manifest (`03-slice.md`) shows no cycle, no shared-file conflicts between defined slices, and no contradictory acceptance criteria.

## Freshness Research

Captured in detail in [04-plan-toolchain.md](04-plan-toolchain.md) `## Freshness Research`. Top-level summary:

- Toolchain target versions confirmed compatible: Kotlin 2.3.21 + AGP 9.1.1 + Gradle 9.1.2 + Compose BOM 2026.05.00 + Material3 1.4.0 + Roborazzi 1.37.0 + Robolectric 4.16 + JDK 17.
- Known breaking changes audited: AGP Variants API removal (n/a — we don't use it), `dexOptions` removal (n/a), R8 repackaging default (audit step), `id 'org.jetbrains.kotlin.android'` removal claim (verification step).
- KSP × Kotlin 2.3 risk surfaced and contained by a Phase A spike commit.
- Coil 3 migration path documented (`coil` → `coil3` namespace + new okhttp artifact).

## Recommended Next Stage

- **Option A (default):** `/wf implement brutalist-redesign toolchain` — execute the toolchain plan. Pre-flight: run `/compact` to drop planning research from the conversation (the PreCompact hook preserves workflow state on disk).
- **Option B:** `/wf plan brutalist-redesign all` — plan all 7 slices in parallel. **Not recommended.** The chain is strictly linear; downstream plans would be drafted against assumed (not observed) post-toolchain state and almost certainly need rework.
- **Option C:** `/wf slice brutalist-redesign` — revisit slice boundaries. Not recommended; planning surfaced no missing scope or boundary disputes.
