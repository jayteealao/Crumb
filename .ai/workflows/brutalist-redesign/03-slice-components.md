---
schema: sdlc/v1
type: slice
slug: brutalist-redesign
slice-slug: components
status: defined
stage-number: 3
created-at: "2026-05-16T22:37:59Z"
updated-at: "2026-05-16T22:37:59Z"
complexity: l
depends-on: [tokens]
tags: [components, brutalist, designsystem, roborazzi]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
  siblings:
    - 03-slice-toolchain.md
    - 03-slice-tokens.md
    - 03-slice-layouts.md
    - 03-slice-screens.md
    - 03-slice-behaviors.md
    - 03-slice-maestro.md
  plan: 04-plan-components.md
  implement: 05-implement-components.md
---

# Slice: Atomic component rebuild

## Goal

Rebuild every active component in `core/designsystem/components/` to the brutalist spec; delete the 13 orphans; introduce 4 new components the redesign requires. The component surface must match the handoff's atomic-composable section pixel-for-pixel (≥95%) under Roborazzi at light and dark, before any layout shell or screen consumes them.

## Why This Slice Exists

Components are the unit Roborazzi can verify in isolation — and the unit that downstream layout/screen slices compose. Getting the atomic level right (and golden-locked) prevents regressions when screens start consuming them. The 13 orphan components are also dead weight that would slow plan-stage and review-stage discovery if carried forward — removing them now shrinks the surface every later slice has to reason about.

## Scope

**In: delete (13 components + their tests).**

`CrumbsCard`, `CrumbsDialog`, `CrumbsDivider`, `CrumbsFilterChip`, `CrumbsSortMenu`, `CrumbsTabBar`, `CrumbsTagChip`, `CrumbsTextField`, `EngagementMetrics`, `MediaCarousel`, `SearchSuggestions`, `ThreadIndicator`, `VideoPlayer` — file deletion + corresponding `core/designsystem/src/test/.../components/*Test.kt` file deletion + their PNG snapshots under `core/designsystem/src/test/snapshots/`.

**In: rebuild to brutalist (13 active components).**

`CrumbsBookmarkCard`, `CrumbsBottomNav`, `CrumbsButton`, `CrumbsIconButton`, `CrumbsProgressIndicator`, `CrumbsScaffold`, `CrumbsTopBar`, `EmptyState`, `GradientImage`, `LoadingCard`, `QuickActionMenu`, `TagEditorDialog`, `UserProfileDisplay`.

Each rebuild:
- Match the handoff atomic spec for the equivalent component (verify against `handoff-components.jsx`).
- Use only `CrumbsTheme.colors.*`, `CrumbsTheme.typography.*`, `CrumbsTheme.shapes.*`, `CrumbsTheme.spacing.*` for design values. No hard-coded colors, sizes, or fonts.
- Add `Modifier.testTag("...")` on the root + any sub-element Maestro will need to query later. Test tag naming: kebab-case, scoped to component (e.g. `card-title`, `card-actions`, `btn-primary`).
- Add new Roborazzi golden tests covering every interaction state the handoff shows: default, pressed, disabled, focused (where applicable), light + dark. One golden image per state-theme pair.
- `LoadingCard` gains the subtle horizontal scan-line motion specified in shape — implemented via `Modifier.drawBehind` + `infiniteTransition`. Golden captured at a fixed `currentTimeMillis` deterministic value (Roborazzi clock control) so the scan-line position is reproducible.

**In: add 4 new components.**

- `CrumbsFilterBar` — horizontal row of filter chips, supports both single-select (Type) and multi-select (Tags, Collection) modes via a sealed-class `FilterMode`. Renders 1.5dp ink border, mono kicker label. testTag `filter-bar` + per-chip `filter-chip-<id>`.
- `CrumbsSnackbar` — brutalist snackbar for the soft-delete undo flow. Black ink background, accent border, mono uppercase action text. Auto-dismiss timer is the *caller's* responsibility; this component is the visual shell only. testTag `snackbar` + `snackbar-action`.
- `CrumbsBanner` — sticky ink-stroked banner for the sync-error inline state. Slot for kicker text + slot for an accent CTA button. testTag `banner` + `banner-cta`.
- `CrumbsLongPressPopup` — contextual popup anchored to a card position; renders a vertical action list with ink dividers. Anchor logic via `Popup(alignment, offset)` from `androidx.compose.ui.window`. testTag `popup` + per-action `popup-action-<id>`.

**In: misc.**
- Update `core/designsystem` Roborazzi config to enforce the 1%-RGB / 5%-changed-pixel tolerance from shape (`compareOptions = ChangeThreshold(0.05f, ImageComparator.Companion.PixelMatcher(0.01f))`).
- Wire the testTagsAsResourceId scaffolding into each component's `Modifier` chain so Maestro can address it once flows arrive.

**Out:**
- Layout shells (`HomeScaffold`, `OverlayShell`, `OnboardingShell`) — handled by `layouts` slice.
- Behavioral wiring (long-press menu state machine, filter selection state, snackbar timer, banner trigger) — handled by `behaviors` slice. This slice ships the **visual shells** only.
- Screen-level composition — handled by `screens` slice.
- DB schema work.

## Acceptance Criteria

- **Given** the codebase after this slice, **when** the dev runs `find core/designsystem/src/main -name "Crumbs*.kt" -o -name "EmptyState.kt" -o -name "EngagementMetrics.kt" -o -name "GradientImage.kt" -o -name "LoadingCard.kt" -o -name "MediaCarousel.kt" -o -name "QuickActionMenu.kt" -o -name "SearchSuggestions.kt" -o -name "TagEditorDialog.kt" -o -name "ThreadIndicator.kt" -o -name "UserProfileDisplay.kt" -o -name "VideoPlayer.kt"`, **then** the 13 deleted components are absent and 17 components remain (13 active + 4 new). *(automated)*
- **Given** the rebuilt `CrumbsBookmarkCard`, **when** the Roborazzi golden test runs against the handoff's BookmarkCard mock at Pixel 6 (411×891), **then** the diff is ≤5% changed pixels at 1% RGB tolerance per pixel. Equivalent criterion for the other 12 rebuilt components and 4 new components, light + dark. *(automated)*
- **Given** every component, **when** inspected, **then** every `Color(0xFF...)` literal in component source is gone; only `CrumbsTheme.colors.*` references remain (with the documented exception of `Color.Transparent` and `Color.Black.copy(alpha=…)` for scrims). *(automated — grep)*
- **Given** the deleted components, **when** the codebase compiles, **then** no remaining import references the 13 deleted classes. *(automated — `./gradlew assembleDebug` is the gate)*
- **Given** `LoadingCard` with the scan-line motion, **when** rendered, **then** the scan-line is the only animated element; the surrounding ink stroke and skeleton blocks remain static. *(manual review)*
- **Given** the testTag scaffolding, **when** the dev installs the debug app via `android` CLI and runs `maestro studio` to inspect, **then** every rebuilt component's testTags are queryable. *(interactive — Maestro studio dry run)*

## Dependencies on Other Slices

- **`tokens`**: this slice references `CrumbsTheme.colors`, `CrumbsTheme.typography`, etc. — those must be the brutalist values before component rebuilds can produce correct goldens.

## Risks

- **Volume**: 13 rebuilds + 4 new + 13 deletes + ~30+ new golden images per state × 2 themes = 60+ goldens. Time-to-green for the Roborazzi suite is substantial. Mitigation: rebuild + golden one component at a time; commit per component.
- **`CrumbsScaffold` and `CrumbsTopBar` are also used by feature modules** (`feature/twitter`, `feature/reddit`). API-compatible rebuilds are required — the Composable parameter list cannot change without breaking feature-module call sites. Mitigation: preserve public Composable signatures; rebuild internals only.
- **`MaterialTheme` interaction**: rebuilt components must NOT depend on `MaterialTheme.colorScheme` (would re-introduce Material chrome). Mitigation: lint rule or grep gate during component review.
- **Scan-line determinism**: Roborazzi captures need deterministic time control for the `LoadingCard` animation. Mitigation: use Roborazzi's `RoborazziComposeOptions.builder().withCustomComposable { ... }` with a fixed `infiniteTransition` time, or hoist the time to a parameter and pass a constant in tests.
- **`QuickActionMenu` is currently active** but the new `CrumbsLongPressPopup` overlaps in purpose. Risk of inadvertently shipping both. Mitigation: at the end of this slice, decide whether `QuickActionMenu` is the new long-press popup (rename + rebuild) or a different artifact (keep both, document distinction). Likely outcome: `QuickActionMenu` retires once `CrumbsLongPressPopup` is the single long-press primitive. Surface as plan-stage question.
