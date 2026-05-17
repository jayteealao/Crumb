---
schema: sdlc/v1
type: slice
slug: brutalist-redesign
slice-slug: layouts
status: defined
stage-number: 3
created-at: "2026-05-16T22:37:59Z"
updated-at: "2026-05-16T22:37:59Z"
complexity: s
depends-on: [components]
tags: [layouts, scaffolds, brutalist]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
  siblings:
    - 03-slice-toolchain.md
    - 03-slice-tokens.md
    - 03-slice-components.md
    - 03-slice-screens.md
    - 03-slice-behaviors.md
    - 03-slice-maestro.md
  plan: 04-plan-layouts.md
  implement: 05-implement-layouts.md
---

# Slice: Layout shells

## Goal

Add three reusable layout scaffolds in `core/designsystem/layouts/` (a new sub-package) that the redesigned screens compose into: `HomeScaffold`, `OverlayShell`, `OnboardingShell`. Each shell takes slots, applies brutalist padding/insets, and wires the testTag scaffolding so Maestro can address its regions.

## Why This Slice Exists

The handoff dedicates an entire section (Layouts) to these three shells; screens cannot be cleanly rebuilt to the design without them. Putting layouts in their own slice — between components and screens — also lets screens be a pure composition exercise rather than mixing layout architecture and screen-specific behavior.

## Scope

**In:**
- New sub-package `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/`.
- `HomeScaffold.kt`:
  - Slots: `topBar`, `filterBar`, `bottomBar`, `content: @Composable (PaddingValues) -> Unit`.
  - Wraps a Material3 `Scaffold` with `containerColor = CrumbsTheme.colors.background`.
  - Composes `Column { topBar(); filterBar() }` in the top slot to match the handoff diagram.
  - Edge-to-edge with `WindowInsets.statusBars` consumed by topBar; gesture indicator at bottom respected via `WindowInsets.navigationBars`.
  - testTag `home-scaffold` + slot tags `home-scaffold-topbar`, `home-scaffold-filterbar`, `home-scaffold-bottombar`.
- `OverlayShell.kt`:
  - Bottom-anchored modal-like surface for multi-select filter pickers and any future heavy-state UI.
  - Renders a faded ink backdrop (`Color.Black.copy(alpha = 0.45f)`) over the underlying screen + a slide-up panel with the handoff's ink stroke + paper surface.
  - Slots: `header`, `body`, `footer` (for an APPLY button on filter overlays).
  - Dismiss on backdrop tap (lambda passed in).
  - testTag `overlay-shell`, `overlay-shell-backdrop`, `overlay-shell-apply`.
- `OnboardingShell.kt`:
  - Full-bleed paged layout used by `OnboardingScreen` and `LoginScreen`.
  - Internal slot for a Compose-native `HorizontalPager` (no Accompanist — the migration off Accompanist happens at the screen slice when `OnboardingScreen` is rewritten; this shell ships ready for the new pager).
  - Slots: `header`, `pages: List<@Composable () -> Unit>`, `footer` (CTA + page indicator).
  - testTag `onboarding-shell`.
- Roborazzi goldens for each shell composed with stub slot content (e.g. solid rectangles labeled "topBar"/"filterBar"/"bottomBar") at light + dark. These goldens prove layout math is correct independent of screen-specific content.

**Out:**
- Screen-specific composition (handled by `screens` slice).
- The `HorizontalPager` migration itself for `OnboardingScreen` (handled by `screens` slice).
- Behavior wiring inside the overlay (handled by `behaviors` slice).

## Acceptance Criteria

- **Given** the new layouts package, **when** the dev runs `ls core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/`, **then** `HomeScaffold.kt`, `OverlayShell.kt`, `OnboardingShell.kt` exist. *(automated — file existence)*
- **Given** `HomeScaffold` composed with the spec'd stub slots, **when** the Roborazzi golden runs at Pixel 6 (411×891) light theme, **then** the rendered image shows: statusBar 28dp gap top, TopBar 88dp, FilterBar 34dp, content fills remaining height, BottomNav 52dp + 8dp nav-pill at bottom. *(automated)*
- **Given** `OverlayShell` with a backdrop tap registered, **when** the user taps outside the surface in a Maestro flow (added in `maestro` slice), **then** the dismiss lambda fires. testable proxy: Compose UI test asserting `onClick` invocation. *(automated)*
- **Given** `OnboardingShell` with three stub pages, **when** rendered, **then** the page indicator at the bottom shows three pills, the current page is the accent color, and the footer CTA is right-aligned. *(automated — Roborazzi)*
- **Given** all three shells, **when** inspected with `maestro studio` from a debug build, **then** every shell-level and slot-level testTag is queryable. *(interactive)*

## Dependencies on Other Slices

- **`components`**: shells compose `CrumbsTopBar`, `CrumbsBottomNav`, `CrumbsFilterBar`, `CrumbsButton` — all must exist in their brutalist forms. (Shell goldens use stubs, not the real components, so this dependency is for live use, not for the slice's own goldens.)

## Risks

- **WindowInsets handling differences in Compose 1.11.x**: edge-to-edge defaults may have shifted; system bar consumption needs verification. Mitigation: cross-check against `androidx.activity:activity-compose` `enableEdgeToEdge()` recommendation in Compose 1.11.x release notes.
- **OverlayShell + Material3 ModalBottomSheet**: Material3 has its own ModalBottomSheet; using it would inherit Material chrome (rounded corners, drag handle). Mitigation: do NOT use ModalBottomSheet; implement OverlayShell from primitives (`Box`, `Surface`, animated `slideInVertically`).
- **OnboardingShell pre-emptively uses Compose-native Pager**: if the `screens` slice still needs Accompanist Pager for some interim moment, the shell would not work with it. Mitigation: shell is pure layout — pass pager state in as a slot parameter, both Accompanist and Compose Pager can satisfy the slot. But target Compose-native from day one.
