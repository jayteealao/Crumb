---
schema: sdlc/v1
type: intake
slug: brutalist-redesign
status: complete
stage-number: 1
created-at: "2026-05-16T21:44:39Z"
updated-at: "2026-05-16T21:44:39Z"
tags: [redesign, ui, compose, design-system, brutalist]
refs:
  index: 00-index.md
  next: 02-shape.md
next-command: wf-shape
next-invocation: "/wf shape brutalist-redesign"
---

# Intake

## Restated Request

Replace the current visual design of the Crumbs Android app, **pixel-for-pixel** (target: ≥95% visual fidelity), with the **Brutalist Mono (Option D, v2.0)** design system delivered in the `Crumbs-handoff` bundle. Canonical sources of truth are exactly two files:

- `Crumbs-handoff/crumbs/project/Crumbs Design Handoff.html` — token + component + layout + page handoff doc.
- `Crumbs-handoff/crumbs/project/Crumbs Option D.html` — the six interactive screen mocks.

All other files in the bundle (`option-a..f.jsx`, `tweaks-panel.jsx`, `design-canvas.jsx`, JPG references) are **reference-only** and may inform interpretation but are not the spec.

## Intended Outcome

The Crumbs app's user-visible surface — tokens, components, layout shells, and all six screens — is replaced with the Brutalist Mono direction. The redesign covers both **light** and **dark** themes, with **orange `#FF5A1F`** as the canonical accent. All behaviors implied by the new design (long-press menus, filter chip bar, overlay shells, refreshed bottom-nav destinations) are implemented and wired to real handlers, not stubs.

The non-visual behavior of the app (auth, sync, persistence, integrations) is functionally unchanged.

## Primary User / Actor

End-users of the Crumbs Android app. Secondary: the maintainer (single-developer project) who will continue building on the new system.

## Known Constraints

- **Target tech:** Android · Kotlin · Jetpack Compose · Material3. Existing module layout under `app/`, `core/designsystem/`, `core/models/`, `core/pref/`, `feature/twitter/`, `feature/reddit/`.
- **Handoff alignment:** the design doc explicitly states tokens map "directly to existing `core/designsystem/theme/Crumbs*.kt` files" and patches drop in "without renaming any callers." Existing component file names (`CrumbsButton`, `CrumbsBookmarkCard`, `CrumbsTopBar`, …) are expected to remain stable.
- **Tooling on PATH:** `android` CLI and `lazylogcat` CLI are installed with their corresponding skills. Verify stage uses these for install/run + log capture.
- **Verification stack:** screenshot tests (Roborazzi pending plan-stage confirmation) **and** Maestro flows **and** visual review — all three layers required.
- **Compose / Material3 versions in repo today:** Compose UI `1.3.1`, Material3 `1.2.0`, Kotlin `1.7.10`, AGP per `build.gradle`. These are old; a Compose/Kotlin/AGP bump may be a prerequisite for Roborazzi + Maestro and will be evaluated in `/wf plan`.
- **Branch model:** dedicated branch `feat/brutalist-redesign` off `main`, PR opened at handoff, rebase+merge at ship.
- **Review model:** slug-wide — one `07-review.md` against the cumulative branch diff; `/wf handoff` gates on its verdict.

## Assumptions

- No DB schema, API, or integration changes are required to render the new design — the existing data models already provide everything the new components display.
- The six existing screens (`SplashScreen`, `LoginScreen`, `OnboardingScreen`, `HomeScreen`, `AllBookmarksScreen`, `MapViewScreen`) map 1:1 to the six mocks in Option D (to be confirmed in shape).
- The handoff's 8 atomic components are a superset of, or replace into, the existing 26 `Crumbs*` components — some current components may consolidate or retire.
- Fonts: **Funnel Display** (UI sans) and **IBM Plex Mono** (monospace token labels, kickers, metadata). To be bundled as Compose `Font` resources rather than relying on Google Fonts at runtime.

## Product Owner Questions Asked

**Batch A (structured, AskUserQuestion):**
1. Branch strategy?
2. Appetite?
3. Review scope?

**Batch A.2 (design-specific, AskUserQuestion):**
4. Canonical accent color?
5. Dark mode in scope?
6. Which screens in scope?
7. How is "pixel-for-pixel" verified?

**Batch B (freeform, chat):**
8. Stack confirmation / off-limits areas?
9. Source-of-truth files?
10. Non-goals?
11. Concrete success criteria?
12. Timeline & version bump?
13. Behavioral additions: wired, stubbed, or deferred?

## Product Owner Answers

| # | Answer |
|---|---|
| 1 | **Dedicated** → `feat/brutalist-redesign` off `main` |
| 2 | **Large** (multi-day, slicing expected) |
| 3 | **Slug-wide** review (one `07-review.md`) |
| 4 | **Orange `#FF5A1F`** as canonical accent |
| 5 | **Both light + dark** in scope |
| 6 | **All six screens** in scope |
| 7 | **Screenshot tests + Maestro + visual review** (all three layers) |
| 8 | `android` + `lazylogcat` CLIs are installed with skills; no off-limits areas declared |
| 9 | Only `Crumbs Design Handoff.html` + `Crumbs Option D.html` are canonical |
| 10 | No DB, no API, no nav-graph changes beyond layout-driven, no new features |
| 11 | ≥95% visual match on both themes; screenshot tests green; Maestro happy-path clean; no JUnit regressions; emulator-installed + log-captured |
| 12 | No fixed deadline; version bump on ship "as appropriate" (working assumption: `versionCode 3 / versionName 2.0`) |
| 13 | **All implied behaviors fully wired** — no stubs |

## Unknowns / Open Questions

To be resolved during `/wf shape`:

- **Screen mapping ambiguity:** does Option D's six-screen set map 1:1 to the existing six screens, or does it imply renaming / merging (e.g. Onboarding+Login collapse, or a new Detail/Reader screen)? Read `option-d-screens.jsx` carefully.
- **New bottom-nav destinations:** the handoff's `CrumbsBottomNav` tabs may differ from the current set. If so, do nav graph entries get added/removed?
- **Component retirement:** which of the existing 26 `Crumbs*` components stay, get replaced, or are deleted? (e.g. `MediaCarousel`, `EngagementMetrics`, `ThreadIndicator` may not fit the brutalist palette.)
- **Compose/Kotlin/AGP upgrade necessity:** are the proposed verification libs (Roborazzi, Maestro) compatible with the current Compose 1.3.1 / Kotlin 1.7.10, or must we upgrade first? This drives the first slice.
- **Font licensing:** Funnel Display is OFL; IBM Plex Mono is OFL — confirm bundling rights for the APK and the chosen distribution (Google Fonts downloadable vs. bundled `font/` resources).
- **Reference verification images:** `verify-*.jpg` files in the handoff are author screenshots. Should those be used as canonical reference for the 95% fidelity check, or only the HTML render?

## Dependencies / External Factors

- **Roborazzi** (or Paparazzi / Compose Preview Screenshot Testing) — new dev dependency for snapshot tests.
- **Maestro** — installed on the developer machine; no Gradle dep, but Compose tree needs `testTagsAsResourceId = true` + `testTag(...)`s.
- **Funnel Display & IBM Plex Mono** font assets — needs to be added to `app/src/main/res/font/` or fetched via Compose Downloadable Fonts.
- **Compose BOM / Material3 / Kotlin / AGP** — likely upgrade required; touches every module's `build.gradle`.
- **`android-cli` + `lazylogcat` skills** — leveraged in verify stage.

## Risks if Misunderstood

- **Treating tokens as drop-in but renaming public types** — would break every consumer in `app/` and `feature/*`. The handoff explicitly forbids this; the slicer must preserve the `CrumbsColors` / `CrumbsTypography` / `CrumbsTheme` API.
- **Skipping the Compose/Kotlin/AGP upgrade and discovering it's required mid-implement** — would force a mid-workflow detour. Best to evaluate compatibility up front in shape/plan.
- **Implementing behaviors as stubs because "the design only shows the UI"** — directly contradicts answer #13. Every interactive affordance in Option D must call real handlers.
- **Pixel-perfect understood as 100% rather than ≥95%** — would push effort into chasing font-metric and shadow differences that don't materially improve fidelity.
- **Slicing along screens only** — misses the fact that tokens + layouts + bottom-nav are shared infrastructure that must land first.
- **Letting the design's monochrome aesthetic drift toward "Material You" defaults** — the brutalist direction is deliberately non-Material; using `MaterialTheme` colors directly will bleed standard Material chrome (ripples, elevation overlays, default shapes) through.

## Success Criteria

A reviewer should be able to verify the redesign is complete by checking that **all** of the following hold:

1. Side-by-side comparison of each of the six rendered screens against its Option D HTML mock at 412×920 shows ≥95% visual match on both light and dark themes.
2. Roborazzi (or chosen) snapshot tests cover: every atomic `Crumbs*` component, both theme variants, and per-screen golden images. All green on a clean run.
3. Maestro happy-path flow runs end-to-end: launch → onboarding → login → home (feed) → all bookmarks → map view → long-press → filter → back. No assertion failures.
4. Existing JUnit + `compose-ui-test` suite passes without regressions.
5. Emulator install via `android-cli` succeeds; `lazylogcat` capture shows no theming-related warnings (missing resources, color contrast failures from Material3 internals, etc.).
6. PR description (handoff stage) translates the change into product language: "new visual identity, all six screens, light + dark, behaviors fully wired."

## Out of Scope for Now

- DB schema changes; Room migrations triggered solely by the redesign.
- API or sync-protocol changes (Firestore, Reddit, Twitter integrations).
- New product features beyond what the design implies.
- Accessibility audit / a11y improvements as a primary deliverable. (Basic semantics + content descriptions for new components are in scope; a full audit is not.)
- Performance optimization (the redesign should not regress, but tuning is not a deliverable).
- iOS / web / desktop parallels.
- Migrating away from Hilt, Room, Retrofit, Coil, etc.

## Freshness Research

- **Source:** [Roborazzi GitHub](https://github.com/takahirom/roborazzi) and [Paparazzi vs Roborazzi comparison](https://medium.com/@papppali2000/comparison-of-android-snapshot-testing-libraries-paparazzi-vs-roborazzi-vs-compose-preview-3e506d2d9c05).
  Why it matters: verification criterion #2 (screenshot tests) needs a library decision.
  Takeaway: Roborazzi supports interaction-driven snapshots (button clicks, state changes) where Paparazzi is single-frame only. Given answer #13 wires behaviors, Roborazzi is the preferred candidate; plan stage will confirm compatibility with the repo's Compose version.

- **Source:** [Maestro Jetpack Compose docs](https://docs.maestro.dev/platform-support/android-jetpack-compose).
  Why it matters: verification criterion #3 (Maestro flows).
  Takeaway: no Gradle dependency required; Compose tree needs `Modifier.semantics { testTagsAsResourceId = true }` at the root + `Modifier.testTag("…")` on asserted nodes. Adds a small surface-area requirement to the new layout shells. Maestro yaml flows live outside the app build.

- **Source:** Repo `gradle/libs.versions.toml` — Compose UI `1.3.1`, Material3 `1.2.0`, Kotlin `1.7.10`.
  Why it matters: both Roborazzi and modern Maestro Compose support typically expect newer Compose/Kotlin.
  Takeaway: A toolchain bump (Compose BOM, Kotlin, AGP) is *probably* the first slice. Defer the precise scope to `/wf shape`.

## Recommended Next Stage

- **Option A (default): `/wf shape brutalist-redesign`** — proceed to shape. The redesign has meaningful ambiguity (screen mapping, component retirement, toolchain upgrade scope, font distribution) that warrants the 20-question product-owner shaping pass before slicing.
- **Option B:** `/wf plan brutalist-redesign` — skip shape and go straight to plan. **Not recommended** for a workflow of this size; too many open questions for a single plan to absorb cleanly.
- **Option C:** Blocked — none of the required intake answers are missing; intake is complete.
