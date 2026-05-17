---
schema: sdlc/v1
type: plan
slug: brutalist-redesign
slice-slug: tokens
status: implemented
stage-number: 4
created-at: "2026-05-17T02:03:08Z"
updated-at: "2026-05-17T08:20:13Z"
metric-files-to-touch: 35
metric-step-count: 21
has-blockers: false
revision-count: 0
tags: [tokens, theme, fonts, brutalist, colors, typography, shapes]
stack-source: confirmed
locked-decisions:
  accent: "#FF5A1F"                      # locked at shape; handoff JSX default is #D6FF00 (alt), we keep orange
  crumbs-stroke: full                    # ship CrumbsStroke.kt this slice (hairline/regular/emphasis/offsetX/offsetY)
  tokens-preview: skipped                # AC-K5 re-purposed as manual diff against handoff-tokens.jsx
  font-loading-strategy: blocking        # default; bundled res/font requires first-frame correctness
  funnel-display-weights: [400, 500, 700]
  ibm-plex-mono-weights: [400, 500, 700]
  typography-cutover-style: mechanical-rename
  orphan-app-theme-files: delete-in-tokens
  orphan-components: delete-in-tokens    # pulled forward from components slice
  accent-alpha-value: 0.1                # preserve current visual at the 2 IconButton sites
  goldens-regen: record-directly         # no verify-first diff capture
  testtag-app-root: add                  # add Modifier.testTag("app_root") in CrumbsTheme
refs:
  index: 00-index.md
  plan-index: 04-plan.md
  slice-def: 03-slice-tokens.md
  siblings:
    - 04-plan-toolchain.md
  implement: 05-implement-tokens.md
next-command: wf-implement
next-invocation: "/wf implement brutalist-redesign tokens"
---

# Plan: Token cutover

## Current State

The post-toolchain repo is on Kotlin 2.2.10 / AGP 9.1.1 / Compose BOM 2026.05.00 / Material3 1.4.0 / Roborazzi 1.60.0, with `testTagsAsResourceId` already wired at [CrumbsTheme.kt:42](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTheme.kt). The token surface still reflects v1.1 "Modern Minimal" (cyan accent `#00D9FF`, gray neutrals, sans-only typography):

- `CrumbsColors.kt` — 10 fields (`background, surface, primary, accent, textPrimary, textSecondary, accentAlpha, surfaceVariant, navIndicator, error`). New target: 8 fields, `ink` replacing `primary/textPrimary`, `onSurfaceVariant` replacing `textSecondary`, `onAccent` added, alpha derived inline, `navIndicator/surfaceVariant` removed.
- `CrumbsTypography.kt` — 11 styles, all Funnel Display, `FontLoadingStrategy.Async` (violates the offline NFR — see Risks).
- `CrumbsShapes.kt` — 10 cut-corner shapes (`CutCornerShape` everywhere). New target: 8 fields, all `RectangleShape` except `chip = CutCornerShape(topEnd = 4.dp)`.
- `CrumbsSpacing.kt` — already exactly matches handoff (xs/sm/md/lg/xl/xxl = 4/8/12/16/24/32 dp). **No change needed.**
- `CrumbsStroke.kt` — does not exist; will be created this slice.
- `core/designsystem/src/main/res/font/` — has `funnel_display_{regular,medium,semibold,bold}.ttf`. New target: drop semibold (handoff doesn't use 600), keep regular/medium/bold, add `ibm_plex_mono_{regular,medium,bold}.ttf`.

**Cutover surface (from Explore sub-agent 1):**

| Old field | New field | Site count | Notes |
|---|---|---|---|
| `primary` | `ink` | 1 (CrumbsButton.kt:52) | mechanical |
| `textPrimary` | `ink` | ~20 | mechanical replace_all |
| `textSecondary` | `onSurfaceVariant` | ~25 | mechanical replace_all |
| `accentAlpha` | `accent.copy(alpha = 0.1f)` | 2 (CrumbsIconButton.kt) | inline expression; alpha preserved |
| `surfaceVariant` | context-dependent | ~13 | manual review per site (see Step 12) |
| `navIndicator` | `accent` | 1 (CrumbsBottomNav.kt:128) | mechanical |

Of the ~100 call sites total, ~80 live in the 13 orphan components that this slice deletes (pulled forward from the components slice — round-3 decision). Surviving consumers needing rename: `CrumbsButton`, `CrumbsBottomNav`, `CrumbsBookmarkCard`, `CrumbsIconButton`, `CrumbsProgressIndicator`, `CrumbsTopBar`, `UserProfileDisplay`, `LoginScreen`, `OnboardingScreen`. That's a much smaller cutover than the slice spec feared.

**MaterialTheme posture:** Zero active `MaterialTheme.colorScheme.*` references, zero `dynamic*ColorScheme` callers. The 4 files in `app/src/main/java/com/github/jayteealao/crumbs/ui/theme/` (Color/Shape/Theme/Type.kt) are dead orphans with zero imports — safe to delete in this slice.

## Reuse Opportunities

- `core/designsystem/src/main/res/font/funnel_display_{regular,medium,bold}.ttf` — **reuse as-is** (already bundled).
- `CrumbsSpacing` 6-step scale — **reuse as-is** (already matches handoff).
- `LocalCrumbsColors` / `LocalCrumbsTypography` / `LocalCrumbsSpacing` CompositionLocal mechanism — **reuse as-is**; add `LocalCrumbsStroke` alongside.
- `Modifier.semantics { testTagsAsResourceId = true }` scaffolding at CrumbsTheme root — **reuse as-is**; just add a `Modifier.testTag("app_root")` next to it.
- Existing Roborazzi test pattern (`createAndroidComposeRule<ComponentActivity>` + `captureRoboImage(path)` per state) — **reuse as-is**; no new test scaffolding needed since AC-K5 was re-purposed.
- No tokens-preview composable exists; AC-K5 re-purposed as manual diff — **implement fresh: nothing**.

## Likely Files / Areas to Touch

**Theme module (core/designsystem):**
- [CrumbsColors.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsColors.kt) — full rewrite (data class + light/dark values).
- [CrumbsTypography.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTypography.kt) — full rewrite (7 styles, two families, default Blocking loading).
- [CrumbsShapes.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsShapes.kt) — full rewrite (rectangles + cutSm chip).
- [CrumbsTheme.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTheme.kt) — add `LocalCrumbsStroke` provider; add `Modifier.testTag("app_root")` on inner Box.
- `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsStroke.kt` — **new file**.

**Font assets:**
- `core/designsystem/src/main/res/font/funnel_display_semibold.ttf` — delete.
- `core/designsystem/src/main/res/font/ibm_plex_mono_regular.ttf` — new.
- `core/designsystem/src/main/res/font/ibm_plex_mono_medium.ttf` — new.
- `core/designsystem/src/main/res/font/ibm_plex_mono_bold.ttf` — new.

**Surviving active components (token rename only — body unchanged):**
- `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsButton.kt`
- `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBottomNav.kt`
- `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBookmarkCard.kt`
- `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsIconButton.kt`
- `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsProgressIndicator.kt`
- `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsTopBar.kt`
- `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/UserProfileDisplay.kt`

**App-level consumers (token rename only):**
- `app/src/main/java/com/github/jayteealao/crumbs/screens/LoginScreen.kt`
- `app/src/main/java/com/github/jayteealao/crumbs/screens/OnboardingScreen.kt`

**Deletions (orphan components — pulled forward from components slice):**
- `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/{CrumbsCard, CrumbsDialog, CrumbsDivider, CrumbsFilterChip, CrumbsSortMenu, CrumbsTabBar, CrumbsTagChip, CrumbsTextField, EngagementMetrics, MediaCarousel, SearchSuggestions, ThreadIndicator, VideoPlayer}.kt` — 13 files.
- Their corresponding test files in `core/designsystem/src/test/java/.../components/` — `CrumbsCardTest`, `DividerTest`, `FilterComponentsTest`, `InputComponentsTest`, `MediaComponentsTest`, `MetricsTest`, `TabComponentsTest`, `StatesTest` (any partial deletions where tests cover both orphan and surviving components require splitting — see Step 11).

**Deletions (dead Material orphans):**
- `app/src/main/java/com/github/jayteealao/crumbs/ui/theme/Color.kt`
- `app/src/main/java/com/github/jayteealao/crumbs/ui/theme/Shape.kt`
- `app/src/main/java/com/github/jayteealao/crumbs/ui/theme/Theme.kt`
- `app/src/main/java/com/github/jayteealao/crumbs/ui/theme/Type.kt`

**Roborazzi screenshots:**
- `core/designsystem/src/test/screenshots/*.png` — many regenerate, several deleted alongside their test files.

## Proposed Change Strategy

Three phases, each a logical commit, with the orphan-deletion phase carrying the biggest scope shift:

1. **Phase A — Asset prep + token surface rewrite.** Bundle IBM Plex Mono, drop unused Funnel Display semibold, rewrite the four theme files (Colors, Typography, Shapes, Theme), add CrumbsStroke. The token surface is now brutalist but the rest of the codebase doesn't compile yet.
2. **Phase B — Caller cutover.** Apply the rename table to all surviving call sites; delete the 13 orphan components + their tests; delete the 4 dead `app/ui/theme/*.kt` Material orphans. Project compiles. Components themselves still look like v1.1 layouts but render in brutalist colors and fonts — that's the intentional intermediate state the components slice will fix.
3. **Phase C — Goldens + verification.** Regenerate Roborazzi goldens directly (no verify-first), confirm `assembleDebug + lintDebug + verifyRoborazziDebug` green, install on Pixel 6 AVD for the brutalist-palette visual confirmation.

This sequencing preserves continuous compilability after Phase B (no broken intermediate). Phase A introduces a temporary uncompilable state — that's fine on a feature branch and resolved by Phase B's caller updates.

**Typography rename lookup table** (mechanical Edit replace_all per pair):

| Old → New |
|---|
| `displayLarge` → `displayHeadline` |
| `displayMedium` → `displayHeadline` |
| `headingLarge` → `displayHeadline` |
| `headingMedium` → `displayHeadline` |
| `titleLarge` → `displaySmall` |
| `titleMedium` → `displaySmall` |
| `bodyLarge` → `bodyMono` |
| `bodyMedium` → `bodyMono` |
| `labelLarge` → `captionMono` |
| `labelMedium` → `metaMono` |
| `caption` → `metaMono` |

The mapping collapses 11→7 with intentional approximation; components will look "wrong" in the intermediate state (sans body text becomes mono) but every consumer compiles. The components slice rewrites every component anyway, so the intermediate state has zero downstream cost.

**Color rename lookup table:**

| Old → New |
|---|
| `primary` → `ink` |
| `textPrimary` → `ink` |
| `textSecondary` → `onSurfaceVariant` |
| `accentAlpha` → `accent.copy(alpha = 0.1f)` (inline) |
| `surfaceVariant` → **manual review** (likely `background` for fills, `onSurfaceVariant` for tracks/dividers) |
| `navIndicator` → `accent` |

`surfaceVariant` is the only ambiguous mapping. After orphan deletion, its only surviving site is `CrumbsProgressIndicator.kt` (4 hits for trackColor). Step 12 handles it explicitly.

## Step-by-Step Plan

### Phase A — Asset prep + token surface (commits as one)

1. **Pre-flight:** confirm clean working tree on `feat/brutalist-redesign` at the latest toolchain commit. `./gradlew --stop` to release any Windows file locks. *(safety; toolchain slice surfaced this Windows lock issue)*
2. **Bundle IBM Plex Mono fonts.** Download static TTFs (Regular 400, Medium 500, Bold 700) from Google Fonts. Place at `core/designsystem/src/main/res/font/ibm_plex_mono_regular.ttf`, `…_medium.ttf`, `…_bold.ttf`. Verify file sizes (~50KB each) and ensure each is a valid TrueType file. License: OFL 1.1 — no LICENSE file change needed (existing OFL.txt covers; if absent, add one).
3. **Delete unused Funnel Display semibold.** Remove `core/designsystem/src/main/res/font/funnel_display_semibold.ttf`. *(Handoff uses only 400/500/700; semibold/600 referenced only in old `CrumbsTypography.headingLarge` which is renamed in Phase B.)*
4. **Rewrite `CrumbsColors.kt`.** New 8-field `data class CrumbsColors(background, surface, ink, onSurfaceVariant, accent, onAccent, error, success)`. `LightColors` and `DarkColors` byte-exact to handoff hex codes BUT with `accent = Color(0xFFFF5A1F)` (orange — round-1 PO choice, overriding the handoff JSX default of `#D6FF00`). `onAccent = Color(0xFF0A0A0A)`. Both themes share accent + onAccent; structural fields (background/surface/ink/onSurfaceVariant) flip per theme.
5. **Rewrite `CrumbsTypography.kt`.** Declare `private val FunnelDisplay = FontFamily(Font(R.font.funnel_display_regular, FontWeight.Normal), Font(…medium, FontWeight.Medium), Font(…bold, FontWeight.Bold))` — **omit the `loadingStrategy` parameter** so Compose uses the default `Blocking` (round-1 PO choice, per Google's bundled-resource guidance). Same shape for `IBMPlexMono`. Define 7 styles per handoff spec: `displayHeadline` (Funnel 32sp Bold lh=32 letter=-0.6sp), `displaySmall` (Funnel 22sp Bold lh=24 letter=-0.4sp), `titleSection` (Mono 11sp Bold lh=16), `bodyMono` (Mono 12sp Normal lh=18), `metaMono` (Mono 10sp Medium lh=14 letter=0.6sp), `captionMono` (Mono 10sp Bold lh=14 letter=1.4sp), `tagMono` (Mono 10sp Medium lh=14).
6. **Rewrite `CrumbsShapes.kt`.** All fields → `RectangleShape` except `chip = CutCornerShape(topEnd = 4.dp)`. Remove `buttonSmall`, `cardSmall`, `videoPlayer` (handoff doesn't expose these). Surviving fields: `card, button, textField, dialog, navigationBar, chip, rectangle`. Imports: `RectangleShape`, `CutCornerShape`, `Shape`, `dp`.
7. **Create `CrumbsStroke.kt`.** New file under `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/`. Object with `hairline = 1.dp`, `regular = 1.5.dp`, `emphasis = 2.dp`, `offsetX = 6.dp`, `offsetY = 6.dp`. *(Round-1 PO choice: full file including offset shadow specs, even though OverlayCard usage lands in components slice.)*
8. **Update `CrumbsTheme.kt`.** Add `val LocalCrumbsStroke = compositionLocalOf { CrumbsStroke }`. Inside the `CompositionLocalProvider`, provide `LocalCrumbsStroke provides CrumbsStroke`. On the inner `Box`, chain `Modifier.testTag("app_root")` after the existing `semantics { testTagsAsResourceId = true }`. *(Round-3 PO choice; gives Maestro a stable root target for the deferred AC4 evidence.)*

   *At this point the token surface is brutalist. The project does NOT compile — orphan components reference removed fields. Continue to Phase B without committing yet, OR commit Phase A as "compile-broken intermediate" — your call. The plan's default is "single big commit after Phase B" since the broken intermediate has no value to a reviewer.*

### Phase B — Caller cutover + orphan deletion (single commit)

9. **Delete 4 dead Material orphans.** Remove `app/src/main/java/com/github/jayteealao/crumbs/ui/theme/{Color, Shape, Theme, Type}.kt`. Sub-agent confirmed zero imports across the codebase.
10. **Delete 13 orphan component files.** Remove the 13 `.kt` files under `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/` listed under Likely Files. *(Round-3 PO choice: pull forward from components slice. Cross-slice impact noted in Dependencies on Other Slices below.)*
11. **Delete corresponding orphan test files.** The 17 component test files cluster by component family, not 1-to-1:
    - **Whole-file deletion:** `CrumbsCardTest.kt`, `DividerTest.kt`, `MetricsTest.kt`, `MediaComponentsTest.kt` (covers VideoPlayer + MediaCarousel — both orphans).
    - **Per-class deletion within shared test files:** `FilterComponentsTest.kt` (orphan filter chip + survivors), `InputComponentsTest.kt` (CrumbsTextField is orphan), `TabComponentsTest.kt` (orphan CrumbsTabBar — entire file?), `StatesTest.kt` (orphan SearchSuggestions among others). Open each, delete orphan-related @Test methods, keep survivor methods. Plan-time count: ~4 whole-file deletes + ~3 partial-file edits.

    *Note: Plan-time inspection of the 17 test files for partial-delete scope is a Phase B implementation step — exact `@Test` methods can only be identified by reading each test file body. The implement stage will do this; this plan flags it as a non-trivial edit and not a `rg -lr | xargs rm` operation.*
12. **Apply mechanical color rename to surviving consumers.** Across the 9 surviving consumer files (CrumbsButton, CrumbsBottomNav, CrumbsBookmarkCard, CrumbsIconButton, CrumbsProgressIndicator, CrumbsTopBar, UserProfileDisplay, LoginScreen, OnboardingScreen):
    - `primary` → `ink` (1 hit, CrumbsButton.kt:52)
    - `textPrimary` → `ink` (per-file `Edit replace_all` since unambiguous)
    - `textSecondary` → `onSurfaceVariant` (per-file `Edit replace_all`)
    - `accentAlpha` → `accent.copy(alpha = 0.1f)` (2 hits, CrumbsIconButton.kt — inline literal expression)
    - `navIndicator` → `accent` (1 hit, CrumbsBottomNav.kt:128)
    - `surfaceVariant` → **case-by-case in CrumbsProgressIndicator.kt only** (4 hits — track colors). Map to `onSurfaceVariant.copy(alpha = 0.2f)` for tracks (visible-but-subdued line matches brutalist contrast). Other orphan sites for `surfaceVariant` already deleted in Step 10.
13. **Apply mechanical typography rename to surviving consumers.** Run the 11→7 lookup table across the 9 surviving consumer files plus any test files that survived Step 11. Per-style `Edit replace_all` per file. After this step the project must compile — verify by running Step 14.

### Phase C — Build + goldens + interactive verification (single commit for goldens)

14. **Build verify:** `./gradlew clean assembleDebug`. Must succeed. If a `surfaceVariant` or other reference was missed, the compile error names the file:line — fix and re-run.
15. **Lint verify:** `./gradlew lintDebug`. Must succeed.
16. **Regenerate Roborazzi goldens directly:** `./gradlew :core:designsystem:recordRoborazziDebug`. *(Round-3 PO choice: record directly, no verify-first diff capture. Matches what toolchain slice did.)* After this, fewer PNGs exist (orphan tests deleted) — surviving goldens reflect brutalist colors on v1.1 component layouts. Stage and commit all of `core/designsystem/src/test/screenshots/`.
17. **Verify goldens green:** `./gradlew :core:designsystem:verifyRoborazziDebug`. Must succeed against the just-recorded baselines.
18. **Run the CI-equivalent gate:** `./gradlew --no-daemon clean assembleDebug lintDebug :core:designsystem:verifyRoborazziDebug` (mirrors `pr_check.yml` line 76). Must succeed.
19. **Install on Pixel 6 AVD.** Use the `android-cli` skill to provision/select `Pixel_6_API_34` (per `00-index.md` verification block) and `./gradlew installDebug` to deploy. Launch via `monkey -p com.github.jayteealao.crumbs -c android.intent.category.LAUNCHER 1` (toolchain-slice precedent — `am start` proved unreliable). Confirm: `HomeScreen` background reads paper `#EFEEE9`; wordmark/accent reads orange `#FF5A1F`. Capture a screenshot via `MSYS_NO_PATHCONV=1 adb shell screencap` (toolchain-slice precedent for Windows path mangling) → `.ai/workflows/brutalist-redesign/verify-evidence/tokens/01-home-paper.png`.
20. **Dark-mode toggle check.** Re-launch with system dark mode on (via `adb shell cmd uimode night yes`). Confirm background flips to `#0B0B0B`, surface to `#161616`, ink to `#FFFFFF`. Capture `02-home-dark.png`.
21. **Manual handoff diff (AC-K5 re-purposed).** Open `Crumbs-handoff/crumbs/project/handoff-tokens.jsx` swatches/type samples (or the rendered HTML at `Crumbs Design Handoff.html`) and compare side-by-side against `verify-evidence/tokens/01-home-paper.png` + `02-home-dark.png`. Acceptable drift: anti-aliasing on font rendering, monitor color gamut. Unacceptable: wrong hex, wrong type family. *(Maintainer-driven; will appear as a verify-stage `runtime-evidence-deferral` if not cleared before ship.)*

## Test / Verification Plan

### Automated checks

- **Compile gate:** `./gradlew clean assembleDebug` — covers AC-K1, AC-K3 (typography compiles), AC-K5-build (toolchain still healthy).
- **Lint gate:** `./gradlew lintDebug` — must remain green.
- **Token-shape assertion (AC-K1):** `grep -rE "primary|textPrimary|textSecondary|accentAlpha|surfaceVariant|navIndicator" core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsColors.kt` → must return no matches.
- **Dynamic color absence (AC-K2):** `grep -rE "dynamicLight|dynamicDark" --include="*.kt" .` → zero matches in non-test source (excludes `.ai/workflows/` which contains plan docs).
- **Roborazzi gate:** `./gradlew :core:designsystem:verifyRoborazziDebug` — green against regenerated baselines.
- **CI-equivalent:** `./gradlew --no-daemon clean assembleDebug lintDebug :core:designsystem:verifyRoborazziDebug`.

### Interactive verification (human-in-the-loop)

Source of truth: `stack:` block in `00-index.md` (PO-confirmed). `stack.platforms = [android]`, `stack.testing = [junit, compose-ui-test]`, `stack.cli-on-path` includes `android` and `lazylogcat`. Maestro is **not** in this slice's verification stack — it lands in the `maestro` slice.

**AC-K3 — Typography renders correctly:**
- **What to verify:** `displayHeadline` Text glyphs render in bundled Funnel Display 32sp/700, not a fallback sans.
- **Platform & tool:** Android emulator (Pixel 6 API 34) via `android` CLI + visual inspection.
- **Companion skills:** `lazylogcat` for capturing any `WARN`/`ERROR` font-loading messages from Compose.
- **Steps:**
  1. `./gradlew installDebug` (per Step 19).
  2. Launch via `monkey -p com.github.jayteealao.crumbs -c android.intent.category.LAUNCHER 1`.
  3. Enable airplane mode: `adb shell cmd connectivity airplane-mode enable` (NFR: offline rendering).
  4. Navigate to any screen showing a `displayHeadline` Text (HomeScreen wordmark is the easiest target if already styled; otherwise OnboardingScreen page 1 title).
  5. Capture screenshot.
- **Evidence:** `.ai/workflows/brutalist-redesign/verify-evidence/tokens/03-display-heading-offline.png`.
- **Pass criteria:** Glyphs are unmistakably Funnel Display Bold (geometric sans, characteristic 'a' and 'g'); not Roboto or system-default sans.

**AC-K6 — Pixel 6 emulator visual:**
- **What to verify:** HomeScreen background paper `#EFEEE9`, wordmark/accent orange `#FF5A1F`. Dark-mode flip shows `#0B0B0B` background.
- **Platform & tool:** Pixel 6 API 34 emulator via `android` CLI.
- **Companion skills:** `lazylogcat` (filter `com.github.jayteealao.crumbs`) to capture any rendering warnings.
- **Steps:** Steps 19 + 20 above.
- **Evidence:** `01-home-paper.png`, `02-home-dark.png` under `verify-evidence/tokens/`.
- **Pass criteria:** Color-picker on the captured PNG returns `#EFEEE9` (±2 RGB) on background pixels, `#FF5A1F` (±2 RGB) on accent pixels.

**AC-K5 (re-purposed) — Manual handoff diff:**
- **What to verify:** Brutalist visual identity reaches the running app (not just the test goldens).
- **Platform & tool:** Manual side-by-side; `Crumbs-handoff/crumbs/project/Crumbs Design Handoff.html` in a desktop browser vs. emulator screenshots.
- **Steps:** Step 21 above.
- **Pass criteria:** Maintainer subjective judgement that hex values, type family, and stroke widths read as the handoff intends. *(Will register as a verify-stage `runtime-evidence-deferral` if not closed by maintainer before ship.)*

## Risks / Watchouts

- **The 11→7 typography rename is intentionally lossy.** Body text on v1.1 component layouts will briefly render in IBM Plex Mono after Phase B, which looks unmistakably wrong (mono-spaced body in a `bodyLarge`-positioned slot). This is the intentional intermediate state — the components slice rewrites every component anyway. Mitigation: commit message and PR description must explicitly say "intermediate intentionally looks broken; components slice fixes." Reviewers seeing the goldens between tokens and components shipping should not regress them.
- **`surfaceVariant` site-by-site judgment.** The one remaining `surfaceVariant` consumer is `CrumbsProgressIndicator`'s 4 trackColor hits. Mapping is "`onSurfaceVariant.copy(alpha = 0.2f)`" but the visual result may be wrong for a brutalist progress indicator (should it be `ink.copy(alpha = 0.15f)` instead, since brutalist favors ink+paper not gray midtones?). Mitigation: regenerate the goldens, eyeball the diff, adjust if subjectively off. Worst case: drops to a TODO in implement.
- **Font file size budget.** Bundling 3 IBM Plex Mono TTFs adds ~150KB to the APK; dropping Funnel Display semibold saves ~80KB. Net: ~+70KB. Shape NFR is `+400KB headroom` — well under budget. *(No mitigation needed; logged for transparency.)*
- **Compose first-frame flash from FontLoadingStrategy migration.** Switching from `Async` to default `Blocking` is the explicit fix — but it does change cold-start behavior slightly. The Blocking strategy blocks on first paint until the font resource is decoded. Bundled-resource decode is fast (<10ms typical), well under the shape NFR of +50ms cold-start regression. *(Risk negligible; explicit fix matches Google guidance and the offline-rendering NFR.)*
- **Roborazzi goldens may surface dark-mode bugs.** The current `CrumbsTheme(darkTheme = true)` codepath was minimally exercised under v1.1; the new contrast is much sharper, so a dark-mode-only rendering bug could surface. Mitigation: existing tests already capture light+dark variants per component; the regenerated goldens will expose any issues.
- **Windows file-lock pre-flight.** The toolchain slice surfaced the `bundleLibCompileToJarDebug` Windows file-lock issue. Step 1 explicitly runs `./gradlew --stop` before any build. Reusable pattern for downstream slices.

## Dependencies on Other Slices

**This slice's outputs feed:**
- `components` (next slice) — receives the final brutalist token surface (Colors, Typography, Shapes, Stroke, app_root testTag) and a much smaller scope: **13 orphan components already deleted, 13 tests already removed**. Components slice now starts focused on rebuilding the 8 active brutalist atomic composables (CrumbsTopBar, CrumbsBottomNav, CrumbsButton, CrumbsIconButton, CrumbsBookmarkCard) + adding 4 new ones (CrumbsFilterBar, CrumbsSnackbar, CrumbsBanner, CrumbsLongPressPopup). **Scope shifted: AC-C1 (delete 13 orphans) becomes a verification-only criterion in the components slice — those deletions are already done.**

**This slice depends on:**
- `toolchain` (shipped) — required Kotlin 2.2.10 + Compose BOM 2026.05.00 + Material3 1.4.0 + Roborazzi 1.60.0 + `testTagsAsResourceId` scaffolding. All present per `verified-partial` status on `00-index.md`.

**Sibling-plan awareness:** Only `04-plan-toolchain.md` exists; no other plans drafted yet (rolling-plan strategy). The components-slice plan, when drafted, must reflect the orphan-deletion pull-forward documented above.

## Assumptions

- IBM Plex Mono TTFs (Regular/Medium/Bold static, weights 400/500/700) downloaded from Google Fonts are byte-stable across the project lifetime. Mitigation: commit the TTFs to the repo (already standard practice; not Downloadable Fonts).
- The maintainer will run the manual handoff-diff step (Step 21) before ship; if deferred, AC-K5 becomes a `runtime-evidence-deferral` on `00-index.md` that hard-blocks ship.
- `funnel_display_semibold.ttf` has zero downstream consumers outside `CrumbsTypography.kt`. Already verified — the only ref is in the file being rewritten.
- The Pixel 6 API 34 AVD is provisioned and bootable (the toolchain slice's verify used Pixel 6 / API 34; the recent `Pixel_9_Pro` background-boot event implies the maintainer has a different AVD on hand. Either works — `00-index.md` line 52 names Pixel 6 / API 34 as the canonical device).
- The handoff JSX's typography spec (`letterSpacing = (-0.6).sp` on `displayHeadline` etc.) is the source of truth for letter-spacing values; web research confirmed Compose 1.11.1 supports negative sp spacing without caveat.

## Blockers

None. All shape-stage decisions are resolved; all sub-agent research finished; all PO discovery rounds captured. Stack is PO-confirmed (`stack-source: confirmed`).

## Freshness Research

**Funnel Display & IBM Plex Mono — license + weight coverage:**
- Source: [Google Fonts — Funnel Display](https://fonts.google.com/specimen/Funnel+Display), [Fontsource](https://fontsource.org/fonts/funnel-display).
- Source: [Google Fonts — IBM Plex Mono](https://fonts.google.com/specimen/IBM+Plex+Mono), [GitHub IBM/plex](https://github.com/IBM/plex).
- Takeaway: Both OFL 1.1, both ship static TTFs for the exact weights the handoff uses (400/500/700). No variable-axis workaround needed.

**FontLoadingStrategy for bundled resources:**
- Source: [Compose Font API reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/text/font/Font), [Work with fonts](https://developer.android.com/develop/ui/compose/text/fonts).
- Takeaway: Default is `Blocking`; `Async` is intended for Downloadable Fonts. Current `Async` on bundled fonts is a misuse — switch is risk-free and matches the offline-rendering NFR.

**Negative letterSpacing in Compose 1.11.1:**
- Source: [Compose Foundation releases](https://developer.android.com/jetpack/androidx/releases/compose-foundation), [Style paragraph](https://developer.android.com/develop/ui/compose/text/style-paragraph).
- Takeaway: `TextStyle.letterSpacing` accepts any `TextUnit` (sp or em); sign unrestricted. Negative values are well-supported for display tracking. Caveat (informational only): prefer `.em` if text must scale with user font preference; we use `.sp` since display sizes shouldn't shrink.

**`testTagsAsResourceId` posture:**
- Source: [Compose testing interoperability](https://developer.android.com/develop/ui/compose/testing/interoperability).
- Takeaway: Still `@ExperimentalComposeUiApi` in Compose 1.11.1; no deprecation. Pattern of setting once at theme root remains official guidance.

**Roborazzi 1.60 light+dark capture:**
- Source: [Roborazzi README](https://github.com/takahirom/roborazzi), [Sergio Sastre — Efficient Roborazzi testing](https://sergiosastre.hashnode.dev/efficient-testing-with-robolectric-roborazzi-across-many-ui-states-devices-and-configurations).
- Takeaway: No dedicated `captureMultiTheme` API. Existing per-test light/dark pattern (two methods or `darkTheme = it` parameter) is the idiomatic approach. *Relevant only if AC-K5 had been kept — we re-purposed it as a manual diff, so this is informational.*

**Compose BOM 2026.05.00 — FontFamily compatibility:**
- Source: [Compose UI 1.11.1 release notes](https://developer.android.com/jetpack/androidx/releases/compose-ui).
- Takeaway: `FontFamily(Font(...))` constructor API unchanged across BOM 2026.05.00. No migration needed beyond updating the file body.

## Recommended Next Stage

- **Option A (default):** `/wf implement brutalist-redesign tokens` — execute Phases A→C above. **Run `/compact` first** — planning research (sub-agent reports, web research, discovery rounds) is noise for implementation. The PreCompact hook preserves workflow state.
- **Option B:** `/wf plan brutalist-redesign tokens <feedback>` — revise this plan if any of the round-1/2/3 decisions look wrong on second read.
- **Option C:** `/wf slice brutalist-redesign` — revisit slice boundaries. **Not recommended.** Planning surfaced no missing scope; the pull-forward of orphan deletion is a scoping detail within the slice, not a boundary dispute.
