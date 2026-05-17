---
schema: sdlc/v1
type: slice
slug: brutalist-redesign
slice-slug: tokens
status: defined
stage-number: 3
created-at: "2026-05-16T22:37:59Z"
updated-at: "2026-05-16T22:37:59Z"
complexity: m
depends-on: [toolchain]
tags: [tokens, theme, fonts, brutalist]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
  siblings:
    - 03-slice-toolchain.md
    - 03-slice-components.md
    - 03-slice-layouts.md
    - 03-slice-screens.md
    - 03-slice-behaviors.md
    - 03-slice-maestro.md
  plan: 04-plan-tokens.md
  implement: 05-implement-tokens.md
---

# Slice: Token cutover

## Goal

Replace the design-token surface (colors, typography, shapes, spacing) with the brutalist values from the handoff in a single hard cutover — old fields removed, new fields added, every consumer updated to the new names, no deprecation window. Funnel Display and IBM Plex Mono are bundled into `res/font/` and wired into `CrumbsTypography`. Dynamic color is explicitly disabled. At the end of this slice, every existing screen renders with the new palette and type — components themselves are visually wrong (still cyan-era layouts) but every color/font reads as brutalist.

## Why This Slice Exists

Every component file imports `CrumbsColors` and `CrumbsTypography` — a stable token surface is the type contract every downstream slice depends on. The handoff's `CrumbsColors` schema (8 fields: background, surface, ink, onSurfaceVariant, accent, onAccent, error, success) is incompatible with today's 10-field schema; both schemas cannot coexist (per shape decision). Cutover first, rebuild components second, so the component slice has clean types to refactor against.

## Scope

**In:**
- Rewrite `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsColors.kt`:
  - New `data class CrumbsColors(background, surface, ink, onSurfaceVariant, accent, onAccent, error, success)`.
  - New `LightColors` and `DarkColors` values byte-exact to the handoff hex codes (orange `#FF5A1F` accent).
  - Delete old fields: `primary, textPrimary, textSecondary, accentAlpha, surfaceVariant, navIndicator`.
- Rewrite `CrumbsTypography.kt`:
  - Define `FontFamily.FunnelDisplay` and `FontFamily.IBMPlexMono` backed by `res/font/` resources.
  - Replace existing 11-style scale with the handoff scale: `displayHeadline, displaySmall, titleSection, bodyMono, bodyLarge, labelSmall, etc.` with the spec'd sizes/weights/line-heights.
- Rewrite `CrumbsShapes.kt`: brutalist uses mostly square corners. Audit the current cut-corner shapes; keep only those the handoff explicitly endorses (likely a small subset for buttons / cards). Default to `RectangleShape` where appropriate.
- Rewrite `CrumbsSpacing.kt`: confirm or adjust the 6-step scale (xs/sm/md/lg/xl/xxl) per handoff token values.
- Rewrite `CrumbsTheme.kt`: ensure `MaterialTheme` (if used) is wired with a hard-coded `lightColorScheme()` / `darkColorScheme()` built from `CrumbsColors` (NOT `dynamicLightColorScheme` / `dynamicDarkColorScheme`). The `Modifier.semantics { testTagsAsResourceId = true }` root from the toolchain slice stays here.
- Bundle font assets:
  - `app/src/main/res/font/funnel_display_regular.ttf` (400)
  - `app/src/main/res/font/funnel_display_medium.ttf` (500)
  - `app/src/main/res/font/funnel_display_bold.ttf` (700)
  - `app/src/main/res/font/funnel_display_extrabold.ttf` (800)
  - `app/src/main/res/font/ibm_plex_mono_regular.ttf` (400)
  - `app/src/main/res/font/ibm_plex_mono_medium.ttf` (500)
  - `app/src/main/res/font/ibm_plex_mono_bold.ttf` (700)
- Hard-cutover every consumer of the old color/typography names across the codebase: `app/`, `feature/twitter/`, `feature/reddit/`, `core/designsystem/` (components themselves, even though they'll be rewritten next slice — they need to compile). The substitution rules:
  - `primary` → `ink`
  - `textPrimary` → `ink`
  - `textSecondary` → `onSurfaceVariant`
  - `surfaceVariant` → `surface` (or `background` per context — manual review of each site)
  - `accentAlpha` → derived `accent.copy(alpha = …)` inline
  - `navIndicator` → `accent`
- Delete `app/src/main/java/com/github/jayteealao/crumbs/ui/theme/Color.kt`, `Shape.kt`, `Theme.kt`, `Type.kt` if they duplicate `core/designsystem/theme/` (the Explore report shows these exist; confirm during plan).
- Regenerate every Roborazzi golden in `core/designsystem` — they will now show brutalist colors on the old component shapes. Expected major diffs; commit the new goldens.

**Out:**
- Component implementation changes (handled by `components` slice). At end of this slice, components compile against new tokens but their internal layout is unchanged.
- Screen-level reshuffling (handled by `screens` slice).
- New components (`CrumbsFilterBar`, `CrumbsSnackbar`, `CrumbsBanner`, `CrumbsLongPressPopup`) — they don't exist yet.
- Maestro flows.

## Acceptance Criteria

- **Given** the new `CrumbsColors` data class, **when** the project compiles, **then** the file declares exactly the 8 fields in the handoff spec and zero of the old field names. *(automated — grep + compile)*
- **Given** the codebase after this slice, **when** the dev runs `grep -r "dynamicLight\|dynamicDark" --include="*.kt"`, **then** zero matches in non-test source. *(automated)*
- **Given** the bundled fonts, **when** `CrumbsTypography.displayHeadline` is applied to any `Text`, **then** the rendered glyphs match Funnel Display 32sp/700/32lh. Verified offline (airplane mode emulator). *(interactive — visual + Roborazzi)*
- **Given** the regenerated goldens, **when** the dev inspects them, **then** every component image shows the new color palette and the new fonts, even though component layouts remain v1.1. *(manual)*
- **Given** `./gradlew assembleDebug verifyRoborazziDebug lintDebug`, **when** run, **then** all three succeed. The Roborazzi suite is green against regenerated baselines. *(automated)*
- **Given** a Pixel 6 emulator install via `android` CLI, **when** the user launches the app, **then** the `HomeScreen` background reads as paper `#EFEEE9` and the wordmark/accent reads as orange `#FF5A1F`. *(interactive)*

## Dependencies on Other Slices

- **`toolchain`**: requires Compose 1.11.1, Material3 1.4.0, and Roborazzi 1.37.0 for the new TextStyle + FontFamily APIs and reliable golden regeneration.

## Risks

- **Caller breakage explosion**: 8-field schema replacing 10 fields with renames means ~50+ call sites need touching. Risk of compile-time fan-out exceeding effort estimate. Mitigation: identify all call sites via grep before starting; substitute via Edit `replace_all` per field where unambiguous, manual review where contextual (e.g. `surfaceVariant`).
- **Funnel Display weight coverage**: handoff uses weights 400/500/600/700/800. If Google Fonts only exposes a subset of static TTFs, may need to use a variable-axis font instead — slightly different Compose `Font` declaration. Mitigation: download static TTFs for each weight from Google Fonts repository; if a weight is missing, use the nearest available + `weight =` parameter.
- **`CrumbsShapes` ambiguity**: handoff is mostly square but a few cut-corner accents may remain. Risk of stripping shapes that the handoff actually keeps. Mitigation: cross-check against `option-d-screens.jsx` per-screen card/button shapes during plan-stage prep.
- **Material3 colorScheme leak**: any composable that calls `MaterialTheme.colorScheme.primary` instead of `LocalCrumbsColors.current.ink` would still render with Material's defaults. Mitigation: grep for `MaterialTheme.colorScheme` and replace with `CrumbsTheme.colors.*` patterns; consider whether to keep `MaterialTheme` wrapper at all (current code does not wrap `CrumbsTheme` in `MaterialTheme` per Explore report — surfacing this for plan-stage decision).
