---
schema: sdlc/v1
type: implement
slug: brutalist-redesign
slice-slug: tokens
status: complete
stage-number: 5
created-at: "2026-05-17T08:20:13Z"
updated-at: "2026-05-17T08:20:13Z"
metric-files-changed: 123
metric-lines-added: 408
metric-lines-removed: 5911
metric-deviations-from-plan: 4
metric-review-fixes-applied: 0
commit-sha: "98edb64"
tags: [tokens, theme, fonts, brutalist, colors, typography, shapes, stroke]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
  slice-def: 03-slice-tokens.md
  plan: 04-plan-tokens.md
  siblings:
    - 05-implement-toolchain.md
  verify: 06-verify-tokens.md
next-command: wf-verify
next-invocation: "/wf verify brutalist-redesign tokens"
---

# Implement: token cutover

## Summary of Changes

Hard cutover of the design-token surface to the brutalist Option-D handoff. The token data classes were rewritten in place (`CrumbsColors` 10→8 fields, `CrumbsTypography` 11→7 styles, `CrumbsShapes` 10→7 fields with mostly rectangles), a new `CrumbsStroke` object was added, IBM Plex Mono was bundled into `res/font/`, the dead `app/ui/theme/*.kt` Material orphans were removed, and the 13 orphan components flagged at plan time were deleted (pulled forward from the components slice). The 9 active component files and 2 active screens were mechanically renamed against the new token surface. CrumbsBookmarkCard and TagEditorDialog — which the plan listed as survivors but actually depended on now-deleted orphans — had those dependencies inlined with minimal Material3 stand-ins that the components slice will rebuild properly. Build + lint + Roborazzi-record + Roborazzi-verify all green on the new chain; goldens regenerated against the brutalist palette on v1.1 component layouts (intentional intermediate state).

## Files Changed

**Theme module (rewritten in place):**
- [CrumbsColors.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsColors.kt): 10-field cyan-era data class → 8-field brutalist data class; `LightColors` paper + ink + orange accent, `DarkColors` derived via `.copy(...)`.
- [CrumbsTypography.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTypography.kt): 11 Funnel-Display-only styles → 7 styles split across Funnel Display (`displayHeadline`, `displaySmall`) and IBM Plex Mono (`titleSection`, `bodyMono`, `metaMono`, `captionMono`, `tagMono`). Default `Blocking` font-loading strategy replaces the prior `Async` misuse on bundled resources (per Google guidance).
- [CrumbsShapes.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsShapes.kt): cut-corner-everywhere → `RectangleShape` everywhere except `chip = CutCornerShape(topEnd = 4.dp)`. `buttonSmall`/`cardSmall`/`videoPlayer` fields removed (their call sites either deleted or rewired to `button`/`card`).
- [CrumbsStroke.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsStroke.kt) — new: `hairline`/`regular`/`emphasis`/`offsetX`/`offsetY` Dp constants.
- [CrumbsTheme.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTheme.kt): provides `LocalCrumbsStroke` alongside existing providers; root `Box` now also carries `Modifier.testTag("app_root")` for the deferred Maestro-addressability work.

**Font assets:**
- `core/designsystem/src/main/res/font/funnel_display_semibold.ttf` — deleted (handoff uses only 400/500/700).
- `core/designsystem/src/main/res/font/ibm_plex_mono_{regular,medium,bold}.ttf` — added (Fontsource jsdelivr CDN, OFL 1.1, latin subset ~24 KB each).

**Surviving consumers (mechanical rename + minor adjustments):**
- [CrumbsButton.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsButton.kt) — `primary` → `ink`, `textPrimary` → `ink`, `buttonSmall` → `button` (one shape now), typography → mono.
- [CrumbsBottomNav.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBottomNav.kt) — `navIndicator` → `accent`, `textPrimary` → `ink`, `textSecondary` → `onSurfaceVariant`, label style → `metaMono`.
- [CrumbsIconButton.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsIconButton.kt) — `accentAlpha` → inline `accent.copy(alpha = 0.1f)`, `buttonSmall` → `button`.
- [CrumbsProgressIndicator.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsProgressIndicator.kt) — `surfaceVariant` → `onSurfaceVariant.copy(alpha = 0.2f)` for trackColor (4 sites).
- [CrumbsTopBar.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsTopBar.kt) — `textPrimary` → `ink`, `textSecondary` → `onSurfaceVariant`, `titleLarge` → `displaySmall`, `bodyLarge` → `bodyMono`.
- [CrumbsBookmarkCard.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBookmarkCard.kt) — token renames; `CrumbsVideoPlayer(...)` call inlined as a thumbnail-only `AsyncImage`; `ThreadIndicator(...)` inlined as a `Text("+ N more")`; `CrumbsTagChip(label=tag)` inlined as a `Surface` with bordered `Text("#tag")`.
- [CrumbsScaffold.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsScaffold.kt) — `textPrimary` → `ink`.
- [EmptyState.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/EmptyState.kt) — token + typography rename.
- [GradientImage.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/GradientImage.kt) — `surfaceVariant` → `surface`, `titleLarge` → `displaySmall`.
- [LoadingCard.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/LoadingCard.kt) — `surfaceVariant` → `onSurfaceVariant`, `textSecondary` → `onSurfaceVariant`.
- [QuickActionMenu.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/QuickActionMenu.kt) — `textPrimary` → `ink`, `bodyMedium` → `bodyMono`.
- [TagEditorDialog.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/TagEditorDialog.kt) — full rewrite to inline Material3 `AlertDialog` + `OutlinedTextField` + bordered `Surface` chips, removing the now-deleted `CrumbsDialog`/`CrumbsTextField`/`CrumbsTagChip` dependencies. Components slice rebuilds the brutalist version.
- [UserProfileDisplay.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/UserProfileDisplay.kt) — `surfaceVariant` → `surface`, `textPrimary` → `ink`, `textSecondary` → `onSurfaceVariant`, multiple typography renames.
- [OnboardingScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/OnboardingScreen.kt) — token + typography rename.
- [LoginScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/LoginScreen.kt) — `displayLarge` → `displayHeadline`, `headingMedium` → `displayHeadline`, `bodyLarge` → `bodyMono`.

**Deletions (orphan components — pulled forward from components slice):**
- `core/designsystem/.../components/`: `CrumbsCard`, `CrumbsDialog`, `CrumbsDivider`, `CrumbsFilterChip`, `CrumbsSortMenu`, `CrumbsTabBar`, `CrumbsTagChip`, `CrumbsTextField`, `EngagementMetrics`, `MediaCarousel`, `SearchSuggestions`, `ThreadIndicator`, `VideoPlayer` (13 files).
- `app/src/main/java/com/github/jayteealao/crumbs/ui/theme/{Color, Shape, Theme, Type}.kt` (4 dead Material orphans, zero imports).
- `core/designsystem/src/test/java/.../components/`: `CrumbsCardTest`, `DividerTest`, `FilterComponentsTest`, `InputComponentsTest`, `MediaComponentsTest`, `MetricsTest`, `TabComponentsTest` (7 orphan-only test files).
- `core/designsystem/src/test/java/.../TestTypography.kt` — duplicate `object TestCrumbsTypography` declaration (was already at the canonical `TestCrumbsTypography.kt` path after the rewrite).

**Tests:**
- [CardComponentsTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/CardComponentsTest.kt) — trimmed: removed three `ThreadIndicator*` test methods + their unused imports; kept the five `bookmarkCard*` tests since `CrumbsBookmarkCard` survives.
- [ImageComponentsTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/ImageComponentsTest.kt) — `titleLarge` → `displaySmall`.
- [TestCrumbsTypography.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/TestCrumbsTypography.kt) — written fresh against the new 7-style scale, backed by `FontFamily.SansSerif` / `FontFamily.Monospace` for Robolectric NATIVE mode compatibility.

**Goldens:** `core/designsystem/src/test/screenshots/*.png` — regenerated against the brutalist palette. Shows expected per-component diff (paper background, ink text, orange accent), with the intentional intermediate-state caveat that body text temporarily renders in IBM Plex Mono on v1.1 layouts.

## Shared Files (also touched by sibling slices)

- `CrumbsTheme.kt` — toolchain slice added `testTagsAsResourceId`. This slice keeps that scaffolding and adds `Modifier.testTag("app_root")` + `LocalCrumbsStroke` provider.
- `core/designsystem/src/test/screenshots/*.png` — toolchain slice regenerated all 133 goldens. This slice deleted ~30 goldens that no longer have tests and regenerated the survivors with the brutalist palette.
- `core/designsystem/src/test/java/.../TestTheme.kt` — unchanged behavior (still wraps `CrumbsTheme`).

## Notes on Design Choices

- **Accent color = orange `#FF5A1F`.** Locked at shape stage and reaffirmed at plan-round-1. The handoff JSX's default lime `#D6FF00` is the swatch the designer offered as default; the PO held to orange because of brand association and contrast against paper.
- **`FontLoadingStrategy` default `Blocking`** for both font families. The prior `Async` was a misuse — Google's official guidance reserves `Async` for Downloadable Fonts; bundled `res/font` resources should block on first paint (sub-10ms decode, well under the cold-start NFR).
- **Negative `letterSpacing` `(-0.6).sp` / `(-0.4).sp`** on `displayHeadline` / `displaySmall`. Compose 1.11.1's `TextStyle.letterSpacing` accepts negative `TextUnit` without caveat.
- **`CrumbsStroke.offsetX` / `offsetY` ship now, even though consumers land later.** PO chose full file at round-1; the components slice will wire the offset-shadow specs into `OverlayCard`.
- **Inlined Material3 stand-ins for `CrumbsBookmarkCard` + `TagEditorDialog`.** Plan listed both as survivors, but their bodies still imported now-deleted orphans (`ThreadIndicator`, `CrumbsTagChip`, `CrumbsVideoPlayer`, `CrumbsDialog`, `CrumbsTextField`). The minimal-viable replacement keeps the project compiling without expanding the slice into a full component rewrite. The components slice rebuilds both properly.
- **`surfaceVariant` consumers each got a context-appropriate target.** `CrumbsProgressIndicator` trackColor → `onSurfaceVariant.copy(alpha = 0.2f)`; `GradientImage` background → `surface`; `LoadingCard` shimmer → `onSurfaceVariant.copy(alpha = …)`; `UserProfileDisplay` avatar background → `surface`. Each picked for visual neutrality against the brutalist palette.
- **Goldens recorded directly without verify-first.** Round-3 PO choice. Mirrors what the toolchain slice did and avoids the noise of an intermediate diff against pre-tokens baselines.

## Visual Contract Honored

No `02c-craft.md` artifact present for this workflow, so no per-item check applies. The handoff JSX (`Crumbs-handoff/crumbs/project/handoff-tokens.jsx`) serves as the visual source of truth and is referenced for the AC-K5 manual-diff at verify.

## Deviations from Plan

1. **CrumbsBookmarkCard depended on three orphans (ThreadIndicator, CrumbsTagChip, CrumbsVideoPlayer)** — not surfaced by the plan-stage sub-agent that catalogued surviving consumers. Resolved by inlining minimal Material3 stand-ins.
2. **TagEditorDialog depended on three orphans (CrumbsDialog, CrumbsTextField, CrumbsTagChip)** — same root cause as (1). Resolved by full rewrite using Material3 `AlertDialog` / `OutlinedTextField` / bordered `Surface` chips.
3. **More surviving consumers than the plan listed.** Plan-stage listed 7 components + 2 screens. Actual mechanical-rename surface was 13 components + 2 screens (added: `CrumbsScaffold`, `EmptyState`, `LoadingCard`, `QuickActionMenu`, `TagEditorDialog`, `GradientImage`). Cause: plan sub-agent under-sampled `LocalCrumbsColors.current.textPrimary` paths that weren't flagged through `colors.textPrimary` directly.
4. **`buttonSmall`/`cardSmall`/`videoPlayer` shape fields removed entirely, and their consumers rewired to `button`/`card`/`rectangle`.** Plan kept these shapes alive at the type level but the brutalist palette has no use for size-graded shapes; deleted to match handoff exactly.

## Anything Deferred

- **AC-K5 — manual handoff diff** — re-purposed at plan stage as a maintainer-driven step. Will register as a verify-stage `runtime-evidence-deferral` if not closed before ship.
- **Emulator install + screenshot capture (Pixel 6 AVD, Steps 19–20)** — deferred to the verify stage. The build + Roborazzi loop is green and provides a sufficient code-level signal for implement closure; interactive verification belongs in `/wf verify`.
- **AC4 (toolchain) — full Maestro testTag round-trip** — partially advanced: `app_root` testTag is in place. Full clearance still waits on the maestro slice.

## Known Risks / Caveats

- **The intermediate state intentionally looks wrong.** Body text on v1.1 component layouts now renders in IBM Plex Mono at a 12sp `bodyMono` size that's smaller than the prior 14–16sp `bodyMedium`/`bodyLarge`. Reviewers diffing the goldens between this slice and the components slice should not flag the mono body on v1.1 layouts as a regression.
- **`CrumbsBookmarkCard.tags` rendering uses a temporary inline chip.** It compiles and renders, but the visual is not the brutalist final form. Components slice owns the proper rebuild.
- **`TagEditorDialog` uses Material3 stock components.** Its dialog frame, text field, and chip styling is Material3-default, not brutalist. Components slice rewrites with the brutalist primitives.
- **Funnel Display semibold (600) is gone.** Any future style demanding 600 weight will need a re-bundle. Handoff uses only 400/500/700 so the deletion is correct against the spec.

## Freshness Research

No new external freshness research was needed beyond what was captured in `04-plan-tokens.md` `## Freshness Research`. The plan's research on Funnel Display/IBM Plex Mono licensing, `FontLoadingStrategy` semantics, negative `letterSpacing` support, `testTagsAsResourceId` posture, Roborazzi 1.60 light/dark capture, and Compose BOM 2026.05.00 `FontFamily` API compatibility was reused verbatim and held under implementation.

## Recommended Next Stage

- **Option A (default):** `/wf verify brutalist-redesign tokens` — emulator install + interactive AC-K3/AC-K6/AC-K5 evidence on Pixel 6 API 34. **Run `/compact` first.**
- **Option B:** `/wf review brutalist-redesign tokens` — skip verify if the maintainer plans to fold the emulator smoke into their own pre-merge ritual. Less recommended: AC-K6 is interactive and review can't replace it.
- **Option C:** `/wf plan brutalist-redesign components` — kick off the next slice's plan in parallel; tokens reality is observed. Components slice plan should reflect (a) the orphan-deletion pull-forward already shipped, (b) the temporary inline stand-ins in `CrumbsBookmarkCard` and `TagEditorDialog` that the components slice owns rebuilding.
