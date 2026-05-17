---
schema: sdlc/v1
type: slice-index
slug: brutalist-redesign
status: complete
stage-number: 3
created-at: "2026-05-16T22:37:59Z"
updated-at: "2026-05-16T22:37:59Z"
total-slices: 7
best-first-slice: toolchain
tags: [redesign, ui, compose, design-system, brutalist, roborazzi, maestro]
slices:
  - slug: toolchain
    status: defined
    complexity: l
    depends-on: []
  - slug: tokens
    status: defined
    complexity: m
    depends-on: [toolchain]
  - slug: components
    status: defined
    complexity: l
    depends-on: [tokens]
  - slug: layouts
    status: defined
    complexity: s
    depends-on: [components]
  - slug: screens
    status: defined
    complexity: l
    depends-on: [layouts]
  - slug: behaviors
    status: defined
    complexity: m
    depends-on: [screens]
  - slug: maestro
    status: defined
    complexity: s
    depends-on: [behaviors]
refs:
  index: 00-index.md
  shape: 02-shape.md
next-command: wf-plan
next-invocation: "/wf plan brutalist-redesign toolchain"
---

# Slice Index

## Slice Strategy

The shaped spec splits cleanly along the **technical-layer axis** rather than the screen axis. The handoff itself is organized this way (Tokens · Components · Layouts · Pages); the codebase mirrors it (`core/designsystem/theme/` · `…/components/` · `…/layouts/` to be created · `app/screens/`); and the sequencing constraints discovered in shape's freshness research force this ordering anyway (Roborazzi/Compose toolchain → token surface → atomic primitives → composition shells → screens → behaviors).

The 7 slices are **strictly linearly dependent** (one chain, no parallelism between siblings). This is deliberate:

- **`toolchain`** is the risk-isolation move: regenerate the existing 154 Roborazzi goldens against the *old* visual design on the *new* toolchain to prove the chain is healthy before introducing redesign drift.
- **`tokens` → `components` → `layouts` → `screens`** is a strict refactor pipeline: each slice's outputs are the type-level inputs of the next. Trying to parallelize would force temporary shims (e.g. a deprecation window on `CrumbsColors`) that shape explicitly rejected.
- **`behaviors`** lands last among visual-affecting slices because it's the only slice with non-design-layer changes (the `deleted_bookmarks` Room table + sync filter). Co-locating the schema change with the soft-delete UX keeps the relaxed-non-goal story coherent for review.
- **`maestro`** is the verification safety net, after every UI surface is final.

### Why not group?

The 4-question slicing-strategy round considered (a) merging tokens+components+layouts into a single "foundations" slice and (b) splitting screens into two finer slices (feed-screens vs. onboarding-screens). The PO chose **thin slices**, and the linear-dependency reality means a "foundations" chunk would not actually save any wall-clock time — its constituent steps still have to run in order. The screens slice retains a documented **re-split clause** (see Cross-Cutting Concerns) if plan-stage estimates it >2 days.

## Recommended Order

1. **`toolchain`** — risk-first. Establishes that the new chain renders the existing v1.1 visuals identically (within the 5% changed-pixel tolerance). If this slice surfaces a rendering regression, we fix it before any redesign work begins.
2. **`tokens`** — the type-level contract for everything downstream. Bundles fonts. Hard CrumbsColors cutover. At the end, every screen renders with brutalist colors and fonts on top of v1.1 layouts.
3. **`components`** — atomic primitives reach pixel parity. 13 orphans deleted, 13 active rebuilt, 4 new added. Each Roborazzi-locked against the handoff's component renders.
4. **`layouts`** — three reusable scaffolds (`HomeScaffold`, `OverlayShell`, `OnboardingShell`) that screens will compose.
5. **`screens`** — pure composition. 6 app screens + 2 feature-module screens reach ≥95% match against Option D mocks. Pager migration off Accompanist. Long-press popup opens but actions are stubs.
6. **`behaviors`** — every interactive affordance is wired. Soft-delete + 5s undo + `deleted_bookmarks` tombstone + tombstone-aware sync filter. Filter chips re-query. Sync-error banner appears on auth failure. Version bump to 2.0 / versionCode 3.
7. **`maestro`** — four end-to-end flows + debug-only data injector + `android` + `lazylogcat` integration script. Last slice before review.

## Cross-Cutting Concerns

- **Roborazzi golden regeneration is a recurring cost.** Toolchain regenerates with old visuals; tokens regenerates with brutalist colors on old layouts; components regenerates with brutalist atomic states; screens adds 16+ new per-screen goldens. Plan-stage should budget the regeneration step explicitly for each slice — it's never zero.
- **TestTag discipline.** Every component and shell adds `Modifier.testTag(...)`s during its own slice; Maestro slice consumes them. If a downstream slice changes a tag name to "improve" it, Maestro flows break. Tag names should be considered as load-bearing as public APIs from the moment they're introduced.
- **Slug-wide review at handoff.** Per `00-index.md`, `review-scope: slug-wide`. Per-slice review files are NOT written; the single `07-review.md` runs against the full branch diff after every slice is implemented. This means individual slice verify gates protect us until that final review.
- **`screens` slice re-split clause.** If plan-stage estimates `screens` >2 focused dev-days, split into:
  - `screens-feed` — Home, AllBookmarks, TwitterBookmarksScreen, RedditBookmarksScreen (the LazyColumn-driven family)
  - `screens-shells` — Splash, Onboarding, Login, MapView (the full-bleed family + placeholder)
  This would shift `behaviors` to depend on `screens-feed` only (sufficient since long-press / filters / banner only need the feed surface); `screens-shells` and `behaviors` could then run in parallel. **Decision deferred to plan stage** — it's a 1-line edit to depends-on and a new sibling slice file.
- **Intake non-goal relaxations.** Two intake non-goals were explicitly relaxed during shape: (a) DB schema change limited to the new `deleted_bookmarks` table, and (b) the toolchain bump is in-scope. Both live entirely in their named slices (`behaviors` and `toolchain` respectively). Reviewers should expect those scopes there and nowhere else.
- **`MaterialTheme` posture.** The current `CrumbsTheme` does NOT wrap `MaterialTheme`. The brutalist redesign keeps that posture — every Material chrome element (ripples, elevation, default shapes, dynamic color) is explicitly rejected. Components and screens must not introduce a `MaterialTheme` wrapper. Plan-stage check: any `MaterialTheme.colorScheme.*` reference in the diff fails review.
- **Slug-wide branch.** All 7 slices land on `feat/brutalist-redesign`. No per-slice branches. The PR (opened at `/wf handoff`) covers the full diff against `main`.

## Dependencies Between Slices

Linear chain:

```
toolchain → tokens → components → layouts → screens → behaviors → maestro
```

No parallelism among defined slices. The optional `screens` re-split would introduce one parallel arm (`screens-shells` ∥ `behaviors`) but is not committed.

## Deferred / Optional Slices

- **`screens-shells` / `screens-feed`** — re-split of the `screens` slice, deferred to plan stage. Triggered if plan estimate exceeds 2 dev-days.
- **CI Maestro integration** — wiring Maestro flows into GitHub Actions on every PR. Surfaced as an optional plan-stage choice within the `maestro` slice; default is local-only via `scripts/run-maestro.sh`.
- **Compose Preview Screenshot Testing (Google native)** — could complement Roborazzi as a second screenshot pipeline (per shape's freshness research). Not in any slice; would be a follow-up workflow if the maintainer wants per-preview IDE feedback.
- **Functional Map view** — explicitly out of scope; the `MapViewScreen` placeholder ships in the `screens` slice. Future workflow.
- **Full a11y audit** — out of scope. Baseline semantics only.

## Freshness Research

- **Source:** [Roborazzi 1.37.0 GitHub](https://github.com/takahirom/roborazzi) and the Hilt + Robolectric chain requirements.
  Why it matters: Slice 1 (`toolchain`) must establish the new test-rule chain before any subsequent slice tries to regenerate goldens.
  Takeaway: `HiltAndroidRule` chained as outer rule before `ComposeTestRule`; Robolectric 4.16 + Roborazzi 1.37.0 are the locked pair.

- **Source:** [AGP 9.1.1 release notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes) and [Compose 1.11 release notes](https://developer.android.com/jetpack/androidx/releases/compose-ui).
  Why it matters: The toolchain slice's "what could break" surface.
  Takeaway: `composeOptions { kotlinCompilerExtensionVersion = ... }` is deprecated in favor of the KGP plugin form `id 'org.jetbrains.kotlin.plugin.compose'` — already present in `app/build.gradle`, must be present in every module that uses Compose. Verify during plan-stage of `toolchain`.

- **Source:** [Maestro Compose docs](https://docs.maestro.dev/get-started/supported-platform/android/jetpack).
  Why it matters: Slice 7 (`maestro`) needs the `testTagsAsResourceId` scaffolding established as early as the `toolchain` slice and propagated via every component slice.
  Takeaway: Setting `Modifier.semantics { testTagsAsResourceId = true }` at the `CrumbsTheme` root works for the entire tree; per-component testTags compose underneath.

- **Source:** Existing repo grep showing 154 Roborazzi test files in `core/designsystem/src/test/`.
  Why it matters: The `toolchain` slice's regeneration step has a large fixed cost.
  Takeaway: Budget ~30 min for the initial regeneration + diff inspection in slice 1. Subsequent regenerations (per slice) are smaller scoped.

## Recommended Next Stage

- **Option A (default):** `/wf plan brutalist-redesign toolchain` — proceed to planning the first slice (toolchain). The risk-first ordering and the strict linear dependency chain make sequential planning the natural choice; we want the toolchain to be implemented and verified before drafting plans for downstream slices whose details may shift based on what we learn.
- **Option B:** `/wf plan brutalist-redesign all` — plan all 7 slices in parallel. **Not recommended.** The slices are strictly linearly dependent; downstream plans would have to be revised once toolchain reality is observed (e.g. if Robolectric 4.16 + Roborazzi 1.37.0 needs a workaround we don't yet know about). Parallel planning would invite throwaway work.
- **Option C:** `/wf shape brutalist-redesign` — revisit shape. **Not recommended.** Shape held up against slicing; no missing decisions surfaced.
