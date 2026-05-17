---
schema: sdlc/v1
type: plan
slug: brutalist-redesign
slice-slug: components
status: complete
stage-number: 4
created-at: "2026-05-17T11:11:14Z"
updated-at: "2026-05-17T11:11:14Z"
metric-files-to-touch: 38
metric-step-count: 24
has-blockers: false
revision-count: 0
tags: [components, brutalist, designsystem, roborazzi, popup, snackbar, banner, filterbar]
stack-source: confirmed
locked-decisions:
  material3-stance: mixed-case-by-case      # strip Button/IconButton/ProgressIndicator/TopBar/BottomNav/TagEditorDialog; keep Scaffold passthrough
  quickactionmenu-disposition: retire       # delete; CrumbsLongPressPopup is the single long-press primitive
  longpress-actions-canonical: handoff-screen-5  # 2x2 grid, TAG/SHARE/ARCHIVE/DELETE (overrides slice-spec line 59)
  archive-behavior: flag-for-behaviors-slice  # new behavior introduced by handoff actions
  commit-cadence: grouped-by-family         # ~6 commits (chrome, layout-chrome, cards, dialog, new, goldens)
  scanline-determinism: hoist-time-as-parameter
  list-type-strategy: kotlinx-immutable     # adds kotlinx.collections.immutable dependency
  scaffold-rebuild: material3-passthrough   # keep wrapper
  snackbar-banner-spec: brutalist-token-defaults
  testtag-verification: maestro-studio-dry-run
  roborazzi-tolerance-location: slice-local-build-gradle
  offset-shadow-api: modifier-dropshadow    # Compose 1.11 native
  goldens-coverage: meaningful-state-matrix-by-2-themes  # ~24 new goldens, ~40 existing regen
refs:
  index: 00-index.md
  plan-index: 04-plan.md
  slice-def: 03-slice-components.md
  siblings:
    - 04-plan-toolchain.md
    - 04-plan-tokens.md
  implement: 05-implement-components.md
next-command: wf-implement
next-invocation: "/wf implement brutalist-redesign components"
---

# Plan: Atomic component rebuild

## Current State

The post-tokens repo is on Kotlin 2.2.10 / AGP 9.1.1 / Compose BOM 2026.05.00 / Material3 1.4.0 / Roborazzi 1.60.0, with the full brutalist token surface (`CrumbsColors` 8-field, `CrumbsTypography` 7-style, `CrumbsShapes` rectangle-first, `CrumbsStroke` shipped, IBM Plex Mono bundled, `Modifier.testTag("app_root")` at `CrumbsTheme:42`). The 13 orphan components have been deleted (pulled forward by tokens slice). What remains:

**13 active components** that survived tokens-slice mechanical token rename — visually still v1.1 (sans body → mono now reads correctly, but card chrome, button shapes, top-bar layout are all old):

| # | Component | Lines | External API stability? | Material3 wrapper? | Animation? |
|---|---|---|---|---|---|
| 1 | CrumbsBookmarkCard | 371 | **YES** (HomeScreen, AllBookmarksScreen) | None | No |
| 2 | CrumbsBottomNav | 199 | **YES** (HomeScreen) | NavigationBar | No |
| 3 | CrumbsButton | 131 | **YES** (LoginScreen, OnboardingScreen) | Button + ButtonDefaults | No |
| 4 | CrumbsIconButton | 208 | NO (internal) | FilledIconButton/Outlined/Standard | No |
| 5 | CrumbsProgressIndicator | 181 | NO (internal) | CircularProgressIndicator/Linear | Yes (indeterminate spin) |
| 6 | CrumbsScaffold | 131 | **YES** (HomeScreen, OnboardingScreen) | Scaffold (structural passthrough) | No |
| 7 | CrumbsTopBar | 277 | **YES** (HomeScreen, OnboardingScreen) | TopAppBar + TextField | animateDpAsState |
| 8 | EmptyState | 148 | **YES** (AllBookmarksScreen) | None | No |
| 9 | GradientImage | 246 | NO (internal) | None (Coil 3) | No |
| 10 | LoadingCard | 222 | NO (internal) | None | Yes (shimmer; scan-line TODO) |
| 11 | QuickActionMenu | 149 | NO (internal) | DropdownMenu | No |
| 12 | TagEditorDialog | 184 | NO (internal) | AlertDialog + OutlinedTextField | No |
| 13 | UserProfileDisplay | 245 | **YES** (HomeScreen) | None | No |

**0 components have `Modifier.testTag(...)` wired** today (per slice AC, this is required scaffolding for the maestro slice).

**4 new components** to add this slice: `CrumbsFilterBar`, `CrumbsSnackbar`, `CrumbsBanner`, `CrumbsLongPressPopup`.

**Roborazzi state:** 1.60.0 plugin applied at [core/designsystem/build.gradle:4](core/designsystem/build.gradle); `testImplementation libs.roborazzi.{core,compose,junit.rule}` lines 64–66. **No `roborazzi { compareOptions = … }` block today** — slice-AC requires adding `ChangeThreshold(0.05f, PixelMatcher(0.01f))` here. Goldens live at `core/designsystem/src/test/screenshots/` and are committed. Robolectric `@Config(sdk = [34])`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, light/dark via per-test `@Test` methods (not parameterized). `TestCrumbsTheme.kt:20-30` already exists as the wrapper (sub-agent 1 reported missing — corrected by sub-agent 3).

**Material3 audit:** No `MaterialTheme.colorScheme.*` references anywhere in active source (verified by sub-agent 1). Material3 leaks only via wrapper Composables with explicit color/elevation overrides — but those wrappers still bring **ripple, default shape, and ink/onSurface fallbacks** that conflict with the brutalist contract.

**Handoff source-of-truth divergence (critical):**

- **Long-press popup** — `option-d-screens.jsx:660-760` shows a **2×2 grid** with TAG (accent) / SHARE / ARCHIVE / DELETE (#a40000 text), each button is a `1.5px solid var(--d-ink)` border with a hint subtitle. Container is `boxShadow: '6px 6px 0 var(--d-ink)'` brutalist offset block. The slice spec (line 59) said *vertical action list with ink dividers, Open/Share/Edit-tags/Delete* — **PO-resolved this round 1 Q3: handoff wins**. ARCHIVE is a new behavior (hide-from-feed) — flagged for the behaviors slice.
- **CrumbsSnackbar / CrumbsBanner** — no rendered mock in handoff-components.jsx OR option-d-screens.jsx. Visual contract = brutalist token defaults (PO-resolved this round 2 Q4).
- **handoff-layouts-pages.jsx:31** confirms `CrumbsFilterBar` is a 34dp slot in `HomeScaffold`.

## Reuse Opportunities

- **`Modifier.shimmerEffect()`** at [LoadingCard.kt:41-54](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/LoadingCard.kt:41) — **reuse with modification.** Extract from LoadingCard's body into a private file-level `Modifier.brutalistScanLine(progressFraction: Float, ink: Color)` helper. The existing `infiniteTransition` stays for emulator/preview; tests pass an explicit `progressFraction` to bypass it.
- **`Brush.verticalGradient` overlay pattern** at [GradientImage.kt:87-142](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/GradientImage.kt:87) — **reuse as-is.** Sibling-Box overlay pattern over `AsyncImage`; Coil 3 has no replacement API.
- **`combinedClickable`** at [CrumbsBookmarkCard.kt:77](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBookmarkCard.kt:77) — **drop and replace.** Cannot get long-press fingertip position from `combinedClickable.onLongClick`. Replace with `Modifier.pointerInput(Unit) { detectTapGestures(onTap = …, onLongPress = { offsetPx -> … }) }`. Re-add semantics for TalkBack via `Modifier.semantics { onClick … ; onLongClick … }`.
- **`CrumbsTheme.colors / typography / spacing / stroke` CompositionLocal facade** — **reuse as-is**; same access pattern as tokens slice locked.
- **`TestCrumbsTheme` wrapper** at [core/designsystem/src/test/.../TestCrumbsTheme.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/TestCrumbsTheme.kt) — **reuse as-is.**
- **Existing per-test light/dark capture pattern** (one `@Test fun foo_light()` + one `@Test fun foo_dark()`) — **reuse as-is.** No parameterized refactor.
- **Roborazzi mainClock control** — **net-new pattern** for this codebase. Web research (issue #413) confirms `composeTestRule.mainClock.autoAdvance = false; advanceTimeBy(N)` is required to avoid infinite-animation test hangs. Used only by LoadingCard tests; everything else is static.
- **`Modifier.dropShadow(...)`** — **net-new API for this codebase.** Compose 1.11 (August '25 release) shipped native `dropShadow`/`innerShadow` supporting hard offsets at `blurRadius = 0.dp`. Replaces the sibling-Box trick we'd otherwise need.

## Likely Files / Areas to Touch

**Build configuration:**
- [core/designsystem/build.gradle](core/designsystem/build.gradle) — add `roborazzi { compareOptions = ChangeThreshold(0.05f, PixelMatcher(0.01f)) }`; add `implementation libs.kotlinx.collections.immutable`.
- [gradle/libs.versions.toml](gradle/libs.versions.toml) — add `kotlinxCollectionsImmutable = "0.3.8"` + `kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "kotlinxCollectionsImmutable" }`.

**13 active components — rebuild (with per-component Material3 stance):**

| Component | Strategy | testTags added |
|---|---|---|
| CrumbsBookmarkCard | **Strip** Material3 wrapper-free. New 2x card layout per Screen 5 + atomic mock. Use `dropShadow` for offset block (interactive press state only). Replace `combinedClickable` with `detectTapGestures`. Hoist `onLongPress(positionInRoot: Offset)`. | `bookmark-card`, `card-title`, `card-source`, `card-actions` |
| CrumbsBottomNav | **Strip** Material3 NavigationBar. Manual `Row` with 4 fixed-weight cells, each a `Box(Modifier.clickable(...).background(if-selected-then-ink))`. No ripple. | `bottom-nav`, `nav-tab-<name>` (twitter/reddit/all/map) |
| CrumbsButton | **Keep wrapper, override chrome.** Public API consumed by LoginScreen + OnboardingScreen; signature stable. Set `colors = ButtonColors(accent, onAccent, accent.copy(alpha=0.4f), onAccent.copy(alpha=0.4f))`, `shape = RectangleShape`, `elevation = ButtonDefaults.buttonElevation(0.dp,0.dp,0.dp,0.dp,0.dp)`, `border = BorderStroke(CrumbsStroke.regular, ink)`, `contentPadding = PaddingValues(...)`. Style enum + Size enum preserved. | `btn-<style>-<size>` (e.g. `btn-primary-medium`) |
| CrumbsIconButton | **Strip** Material3 IconButton variants. Single `Box(Modifier.size(...).clickable(onClick = …).then(if-filled-background-else-border))`. Variants live as a `style` enum that toggles background vs border. | `icon-btn-<style>` |
| CrumbsProgressIndicator | **Strip** Material3 Circular/Linear. Manual `Canvas { drawLine + drawArc }` for both. Indeterminate Circular spins via `rememberInfiniteTransition` — use same hoisted-time pattern as LoadingCard so the test variant can pin a frame. | `progress-<style>` |
| CrumbsScaffold | **Keep Material3 passthrough.** Pure structural; no chrome to leak. Override `containerColor = LocalCrumbsColors.current.background`, `contentColor = LocalCrumbsColors.current.ink`. | `scaffold-root` |
| CrumbsTopBar | **Strip** Material3 TopAppBar. Manual 88dp `Column` with mono kicker + Funnel Display wordmark + expanding search row (`AnimatedVisibility` + `BasicTextField`). | `top-bar`, `top-bar-kicker`, `top-bar-wordmark`, `top-bar-search`, `top-bar-search-field` |
| EmptyState | No Material3 today. Brutalist visual rebuild: 1.5dp ink border container, Funnel Display 22sp bold title, mono kicker subtitle, accent CTA button slot (uses `CrumbsButton`). | `empty-state`, `empty-state-cta` |
| GradientImage | **Strip** Surface wrapper. `Box` with `Modifier.border(CrumbsStroke.hairline, ink, RectangleShape)` + `AsyncImage(Modifier.matchParentSize())` + sibling Box overlay. Explicit `Modifier.size(...)` mandatory (Coil 3 default = Size.ORIGINAL). | `gradient-image` |
| LoadingCard | Brutalist rebuild: sharp-edged skeleton blocks (Box with `background(ink.copy(alpha=0.08f))`), 1.5dp ink border. **Add horizontal scan-line motion**: `Modifier.drawBehind { drawLine(ink, start=(0, h*p), end=(w, h*p)) }` where `p = animationValue` from `rememberInfiniteTransition(durationMillis=1800, easing=LinearEasing)`. **Hoist time parameter**: optional `scanLinePositionFraction: Float? = null` — if non-null, override the infinite transition (test entry-point). | `loading-card`, `loading-card-skeleton` |
| QuickActionMenu | **DELETE** — superseded by CrumbsLongPressPopup (PO round-1 Q2). | n/a (deleted) |
| TagEditorDialog | **Strip** Material3 AlertDialog + OutlinedTextField. Compose `Dialog { Box(brutalist border + dropShadow) { Column { kicker / title / FlowRow chips / BasicTextField input / Row actions } } }`. | `tag-editor-dialog`, `tag-editor-input`, `tag-editor-chip-<id>`, `tag-editor-save`, `tag-editor-cancel` |
| UserProfileDisplay | No Material3 today. Brutalist visual rebuild: square avatar (`RectangleShape`, NOT CircleShape) + 1.5dp ink border, mono kicker handle, sans display name. | `user-profile`, `user-profile-handle`, `user-profile-name` |

**4 new components:**

| Component | Files | testTags |
|---|---|---|
| `CrumbsFilterBar.kt` | New file at `core/designsystem/components/`. Per handoff-layouts-pages.jsx:31 + Screen 5 line 681: 34dp tall, `Row` of `[count cell (accent-filled, mono 10.5sp, letter-spacing 0.6)]` + `[divider 1dp ink]` + `[filter slot, centered]` + `[Spacer.weight]` + `[divider 1dp ink]` + `[sort slot]`. Sealed `FilterMode` (Single / Multi). Lists are `ImmutableList<FilterChip>`. **State is hoisted** — caller owns selection. | `filter-bar`, `filter-bar-count`, `filter-bar-chip-<id>`, `filter-bar-sort` |
| `CrumbsSnackbar.kt` | New. Brutalist token defaults: `Box(Modifier.fillMaxWidth().padding(horizontal=16.dp).background(ink).border(CrumbsStroke.regular, accent, RectangleShape).padding(12.dp))`. `Row { Text(message, color=paper, style=bodyMono); Spacer.weight; if(actionLabel!=null) ClickableText(actionLabel.uppercase(), style=captionMono.copy(color=accent), onClick=onAction) }`. **Stateless** — caller owns timer; AC-wise this is just the visual shell. `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` for TalkBack. | `snackbar`, `snackbar-action` |
| `CrumbsBanner.kt` | New. Surface bg, 1.5dp ink top + bottom border (NOT full perimeter — sticks visually to feed). `Row { Column { Text(kickerLine, captionMono); Text(detail, metaMono) }; Spacer.weight; ClickableText(ctaLabel.uppercase(), color=accent, onClick=onCta) }`. Stateless shell. | `banner`, `banner-cta` |
| `CrumbsLongPressPopup.kt` | New. Uses `androidx.compose.ui.window.Popup` with custom `PopupPositionProvider`. Anchor: fingertip `Offset` in window coords, clamped via `coerceIn(0, windowSize - popupContentSize)`. Container: `Modifier.background(surface).border(CrumbsStroke.regular, ink, RectangleShape).dropShadow(DpOffset(6.dp,6.dp), color=ink, blurRadius=0.dp, shape=RectangleShape)`. Header row matches Screen 5: accent index cell + mono source kicker + handle + age. **2×2 grid** of action buttons (NOT vertical list — handoff wins). Each button: `Column { Text(label, mono 12sp bold letter-spacing 1, color=if-danger-then-error); Text(hint, mono 9.5sp alpha=0.65) }`, border 1.5dp ink. TAG button has `background=accent`. ImmutableList<PopupAction> param. | `popup`, `popup-action-<id>` |

**Test files:**

- [CrumbsBookmarkCardTest / CardComponentsTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/CardComponentsTest.kt) — regenerate goldens; add `card-pressed` state (for the long-press visual feedback variant).
- [CrumbsBottomNavTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBottomNavTest.kt) — regen.
- [CrumbsButtonTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsButtonTest.kt) — regen.
- [ActionComponentsTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/ActionComponentsTest.kt) — regen IconButton tests; **delete** any `quickActionMenu_*` @Test methods.
- [ProgressTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/ProgressTest.kt) — regen; new pinned-time test for the indeterminate spinner mid-cycle.
- [ScaffoldTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/ScaffoldTest.kt) — regen.
- [CrumbsTopBarTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsTopBarTest.kt) — regen; add `topbar_search_expanded_*` state.
- [ImageComponentsTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/ImageComponentsTest.kt) — regen EmptyState + GradientImage tests.
- `LoadingCardTest.kt` — **new file.** Pinned-time scan-line test (4 goldens: has-image / no-image × light / dark).
- [ProfileComponentsTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/ProfileComponentsTest.kt) — regen UserProfileDisplay + TagEditorDialog.
- `FilterBarTest.kt` — **new file** (FilterBar states matrix × 2 themes).
- `SnackbarTest.kt` — **new file** (default, with-action × 2 = 4 goldens).
- `BannerTest.kt` — **new file** (sync-error, success × 2 = 4 goldens).
- `LongPressPopupTest.kt` — **new file** (default, danger-pressed × 2 = 4 goldens).
- [StatesTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/StatesTest.kt) — audit: if any state belongs to a deleted/QuickActionMenu test, remove; otherwise regen.

**Deletions:**
- `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/QuickActionMenu.kt`
- Any `@Test fun quickActionMenu_*` in `ActionComponentsTest.kt` (partial-file delete).
- Stale screenshot PNGs for `QuickActionMenu_*` in `core/designsystem/src/test/screenshots/`.

## Proposed Change Strategy

**Six commits, grouped by visual family**, each independently reviewable. Phase A precedes them as setup:

**Phase A — Setup (commit 1):** infra — Roborazzi tolerance config, ImmutableList dep, kotlinx import path.

**Phase B — Component families (commits 2–6):**
- **Commit 2 (Family A — chrome primitives):** `CrumbsButton` (keep+override), `CrumbsIconButton` (strip), `CrumbsProgressIndicator` (strip).
- **Commit 3 (Family B — layout chrome):** `CrumbsScaffold` (passthrough), `CrumbsTopBar` (strip), `CrumbsBottomNav` (strip).
- **Commit 4 (Family C — cards & states):** `CrumbsBookmarkCard` (strip + popup wiring), `EmptyState`, `LoadingCard` (scan-line motion), `GradientImage`, `UserProfileDisplay`.
- **Commit 5 (Family D — dialog/menu):** `TagEditorDialog` (strip), **delete `QuickActionMenu.kt` + its goldens + its tests**.
- **Commit 6 (Family E — new components):** `CrumbsFilterBar`, `CrumbsSnackbar`, `CrumbsBanner`, `CrumbsLongPressPopup` (with their 4 new test files).

**Phase C — Goldens + verification (commit 7):**
- `./gradlew :core:designsystem:recordRoborazziDebug` (single big regen).
- `./gradlew :core:designsystem:verifyRoborazziDebug` (green gate).
- CI-equivalent gate command.
- Pixel 6 emulator interactive checks (light/dark + Maestro studio dry-run).

Total: 7 commits. Each component family commit's `verifyRoborazziDebug` will fail until commit 7's regen — that's intentional (regenerate once at end, not per family).

## Step-by-Step Plan

### Phase A — Setup (commit 1)

1. **Pre-flight.** Confirm clean working tree on `feat/brutalist-redesign` at HEAD `d0a568f` (quick-skip-auth-page). `./gradlew --stop` to release Windows file locks.

2. **Add `kotlinx.collections.immutable` to libs.versions.toml.** Section `[versions]`: `kotlinxCollectionsImmutable = "0.3.8"`. Section `[libraries]`: `kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "kotlinxCollectionsImmutable" }`. Add to `core/designsystem/build.gradle` dependencies: `implementation libs.kotlinx.collections.immutable`.

3. **Add Roborazzi tolerance config to `core/designsystem/build.gradle`.** Insert after the existing `android { … }` block, before `dependencies { … }`:
   ```groovy
   import com.github.takahirom.roborazzi.RoborazziCompareOptions
   import com.github.takahirom.roborazzi.ThresholdValidator
   roborazzi {
       compare {
           changeThreshold.set(0.05f)
       }
   }
   ```
   *(The exact DSL surface depends on Roborazzi 1.60's Gradle plugin API. Verify with `./gradlew :core:designsystem:tasks --group=verification` and Roborazzi README; if the kotlin-DSL form differs, port accordingly. The per-test override via `RoborazziOptions(compareOptions = ChangeThreshold(0.05f, PixelMatcher(0.01f)))` is the fallback if the plugin block doesn't accept both knobs.)*
4. **Build verify.** `./gradlew :core:designsystem:assembleDebug` — must succeed. No source changes yet; this verifies the dep + Roborazzi config don't break the build.

### Phase B Family A — chrome primitives (commit 2)

5. **Rewrite `CrumbsButton.kt`.** Keep Material3 `Button` wrapper (signature consumed by LoginScreen/OnboardingScreen — stable). Override every chrome default per "keep wrapper, override chrome" strategy:
   ```kotlin
   Button(
       onClick = onClick,
       enabled = enabled,
       shape = RectangleShape,
       colors = ButtonDefaults.buttonColors(
           containerColor = if (style == ButtonStyle.Primary) colors.accent else colors.surface,
           contentColor   = if (style == ButtonStyle.Primary) colors.onAccent else colors.ink,
           disabledContainerColor = colors.surface,
           disabledContentColor   = colors.onSurfaceVariant,
       ),
       elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
       border = BorderStroke(CrumbsTheme.stroke.regular, colors.ink),
       contentPadding = PaddingValues(horizontal = sizeFor(size).hPad, vertical = sizeFor(size).vPad),
       modifier = modifier.testTag("btn-${style.name.lowercase()}-${size.name.lowercase()}"),
   ) { Text(text.uppercase(), style = CrumbsTheme.typography.bodyMono) }
   ```
   Preserve the public `enum ButtonStyle / ButtonSize` and the parameter order. Update `CrumbsButtonTest.kt` golden filenames to match new naming if the existing ones don't.

6. **Rewrite `CrumbsIconButton.kt`.** Strip Material3. Single Composable replacing the three Material3 variants:
   ```kotlin
   Box(
       modifier
           .size(sizeFor(size).square)
           .then(if (style == IconButtonStyle.Filled) Modifier.background(colors.accent)
                 else                                  Modifier.background(colors.surface)
                                                              .border(CrumbsTheme.stroke.regular, colors.ink, RectangleShape))
           .clickable(enabled = enabled) { onClick() }
           .testTag("icon-btn-${style.name.lowercase()}"),
       contentAlignment = Alignment.Center
   ) { icon() }
   ```
   Keep public Composable signature (parameters + their order + defaults). Update test goldens.

7. **Rewrite `CrumbsProgressIndicator.kt`.** Strip Material3. Use `Canvas`:
   - **Circular indeterminate:** rotating arc + 1.5dp stroke. Use `rememberInfiniteTransition` with `durationMillis = 1200`; hoist time as `progressFraction: Float? = null` for test override.
   - **Linear indeterminate:** sliding 30%-width bar across 100%-width track; same hoist pattern.
   - **Determinate (if `progress: Float?` non-null):** static fill.
   Preserve public `enum ProgressStyle / ProgressSize`. Update ProgressTest.kt — add one pinned-time test per indeterminate variant.

8. **Family A build verify.** `./gradlew :core:designsystem:assembleDebug` — must succeed. `./gradlew :app:assembleDebug` — must succeed (LoginScreen / OnboardingScreen still compile against new CrumbsButton).

### Phase B Family B — layout chrome (commit 3)

9. **Rewrite `CrumbsScaffold.kt`.** Keep Material3 `Scaffold` passthrough. Override:
   ```kotlin
   Scaffold(
       modifier = modifier.testTag("scaffold-root"),
       topBar = topBar, bottomBar = bottomBar,
       snackbarHost = snackbarHost,
       floatingActionButton = floatingActionButton,
       containerColor = LocalCrumbsColors.current.background,
       contentColor   = LocalCrumbsColors.current.ink,
       content = content,
   )
   ```
   Confirm public signature byte-stable (HomeScreen / OnboardingScreen / quick-skip-auth-page paths still compile).

10. **Rewrite `CrumbsTopBar.kt`.** Strip Material3 TopAppBar + TextField. Manual:
    ```kotlin
    Column(modifier.fillMaxWidth().background(colors.surface).testTag("top-bar")) {
        // Kicker row — Mono 10sp uppercase
        Row(Modifier.fillMaxWidth().padding(horizontal = sp.md, top = sp.sm)) {
            Text("↳ ${LocalDate.now().formatBrutalist()}", style = typo.captionMono, color = colors.onSurfaceVariant,
                 modifier = Modifier.testTag("top-bar-kicker"))
        }
        // Wordmark + search row — 56dp tall
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = sp.md), verticalAlignment = Alignment.CenterVertically) {
            if (!isSearchActive) {
                Text("crumbs•", style = typo.displayHeadline,
                     modifier = Modifier.testTag("top-bar-wordmark").clickable { onSearchActiveChange(true) })
            } else {
                BasicTextField(value = searchQuery, onValueChange = onSearchQueryChange,
                               textStyle = typo.bodyMono.copy(color = colors.ink),
                               modifier = Modifier.fillMaxWidth().testTag("top-bar-search-field"))
            }
        }
        // 1.5dp ink bottom border
        Box(Modifier.fillMaxWidth().height(stroke.regular).background(colors.ink))
    }
    ```
    Total height ~88dp matches shape. Drop `scrollBehavior` parameter — Material TopAppBarScrollBehavior is Material-specific. Replace with `Boolean isCollapsed = false` if needed (likely the HomeScreen never collapses today). Audit external callers and update.

11. **Rewrite `CrumbsBottomNav.kt`.** Strip Material3 NavigationBar. Manual:
    ```kotlin
    Box(modifier.fillMaxWidth().background(colors.surface).testTag("bottom-nav")) {
        Row(Modifier.fillMaxWidth().height(52.dp)) {
            BottomNavTab.entries.forEach { tab ->
                Box(Modifier.weight(1f).fillMaxHeight()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onTabSelected(tab) }
                    .background(if (tab == selectedTab) colors.ink else Color.Transparent)
                    .testTag("nav-tab-${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tab.label.uppercase(),
                         style = typo.captionMono,
                         color = if (tab == selectedTab) colors.accent else colors.ink)
                }
            }
        }
        // 8dp safe-area padding
        Spacer(Modifier.fillMaxWidth().height(8.dp))
    }
    ```
    Preserve `BottomNavTab` enum + public Composable signature.

12. **Family B build verify.** `./gradlew :core:designsystem:assembleDebug :app:assembleDebug` — both must succeed.

### Phase B Family C — cards & states (commit 4)

13. **Rewrite `CrumbsBookmarkCard.kt`.** Strip `Surface`. Brutalist card per Screen 5 + handoff atomic mock:
    - Outer: `Box(Modifier.fillMaxWidth().padding(horizontal = sp.md, vertical = sp.xs).background(colors.surface).border(stroke.regular, colors.ink, RectangleShape).testTag("bookmark-card"))`.
    - Header row: accent index cell + source kicker + handle + age. Each separated by `Box(Modifier.width(stroke.hairline).fillMaxHeight().background(colors.ink))`.
    - Title + body Column.
    - Tag chips FlowRow.
    - **Replace `combinedClickable`** with `pointerInput`:
      ```kotlin
      .pointerInput(bookmark.id) {
          detectTapGestures(
              onTap = { onCardClick(bookmark.id) },
              onLongPress = { offsetPx -> onLongPress(bookmark, offsetPx) }
          )
      }
      .semantics { onClick(label = "Open bookmark") { onCardClick(bookmark.id); true }
                   onLongClick(label = "Show actions") { onLongPress(bookmark, Offset.Zero); true } }
      ```
    - **API change:** `onLongPress` signature widens from `(Bookmark) -> Unit` to `(Bookmark, Offset) -> Unit`. Update [HomeScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/HomeScreen.kt), [AllBookmarksScreen.kt](app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt) call sites — pass `Offset.Zero` for now (real popup wiring lands in behaviors slice).
    - Pressed-state visual via `dropShadow(DpOffset(6.dp, 6.dp), color=ink, blurRadius=0.dp, shape=RectangleShape)` toggled by `var pressed by remember { mutableStateOf(false) }` (set on `onPress { pressed = true; tryAwaitRelease(); pressed = false }`).

14. **Rewrite `EmptyState.kt`.** No Material3 strip needed. Brutalist visual:
    ```kotlin
    Column(modifier.fillMaxSize().padding(sp.lg).testTag("empty-state"),
           horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.Center) {
        Text(title, style = typo.displaySmall, color = colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(sp.sm))
        Text(message, style = typo.bodyMono, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionText != null && onActionClick != null) {
            Spacer(Modifier.height(sp.lg))
            CrumbsButton(onClick = onActionClick, text = actionText,
                         modifier = Modifier.testTag("empty-state-cta"))
        }
    }
    ```
    Drop the `icon` parameter (handoff doesn't use icons; brutalist favors typographic empty states).

15. **Rewrite `LoadingCard.kt` with scan-line motion.** Replace shimmer-based body:
    ```kotlin
    @Composable
    fun LoadingCard(
        hasImage: Boolean = true,
        modifier: Modifier = Modifier,
        scanLinePositionFraction: Float? = null,  // test override
    ) {
        val transition = rememberInfiniteTransition(label = "loading-card")
        val animatedFraction by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
            label = "scan-line",
        )
        val fraction = scanLinePositionFraction ?: animatedFraction

        Box(modifier.fillMaxWidth().padding(sp.md)
            .background(colors.surface)
            .border(stroke.regular, colors.ink, RectangleShape)
            .testTag("loading-card")
            .drawBehind {
                val y = size.height * fraction
                drawLine(colors.ink, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = stroke.hairline.toPx())
            }
        ) {
            Column(Modifier.padding(sp.md)) {
                if (hasImage) Box(Modifier.fillMaxWidth().height(120.dp).background(colors.ink.copy(alpha = 0.08f)).testTag("loading-card-skeleton"))
                Spacer(Modifier.height(sp.sm))
                Box(Modifier.fillMaxWidth(0.7f).height(16.dp).background(colors.ink.copy(alpha = 0.08f)))
                Spacer(Modifier.height(sp.xs))
                Box(Modifier.fillMaxWidth(0.4f).height(12.dp).background(colors.ink.copy(alpha = 0.08f)))
            }
        }
    }
    ```
    Create `LoadingCardTest.kt` (new file) with 4 tests, each passing `scanLinePositionFraction = 0.5f` (mid-cycle): `loading_hasImage_light/dark`, `loading_noImage_light/dark`.

16. **Rewrite `GradientImage.kt`.** Strip `Surface`. Brutalist `Box` + AsyncImage + sibling gradient overlay. Add `Modifier.size(...)` requirement to docstring (Coil 3 ORIGINAL default). testTag root.

17. **Rewrite `UserProfileDisplay.kt`.** No Material3. Square avatar (replace `CircleShape` with `RectangleShape` per brutalist) + 1.5dp ink border; mono kicker handle; sans display name. testTags `user-profile`, `user-profile-handle`, `user-profile-name`.

18. **Family C build verify.** `./gradlew :core:designsystem:assembleDebug :app:assembleDebug` — both must succeed. Both HomeScreen + AllBookmarksScreen call sites updated for the BookmarkCard signature change.

### Phase B Family D — dialog/menu (commit 5)

19. **Rewrite `TagEditorDialog.kt`.** Strip Material3 AlertDialog + OutlinedTextField. Use `androidx.compose.ui.window.Dialog` + custom Box:
    ```kotlin
    if (isVisible) Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth(0.9f)
            .background(colors.surface)
            .border(stroke.regular, colors.ink, RectangleShape)
            .dropShadow(DpOffset(6.dp,6.dp), color=colors.ink, blurRadius=0.dp, shape=RectangleShape)
            .testTag("tag-editor-dialog")
            .padding(sp.md)
        ) {
            Column { /* kicker, title, FlowRow chips with testTag("tag-editor-chip-$tag"),
                       BasicTextField with testTag("tag-editor-input"),
                       Row { Cancel button testTag("tag-editor-cancel"), Save button testTag("tag-editor-save") } */ }
        }
    }
    ```
    Change list parameters to `ImmutableList<String>`.

20. **Delete `QuickActionMenu.kt`** + its golden PNGs in `core/designsystem/src/test/screenshots/QuickActionMenu_*.png` + every `@Test fun quickActionMenu_*` in `ActionComponentsTest.kt`. Verify `grep -r "QuickActionMenu" --include="*.kt"` returns zero matches before commit.

21. **Family D build verify.** `./gradlew :core:designsystem:assembleDebug` — must succeed.

### Phase B Family E — new components (commit 6)

22. **Implement the 4 new components** + their 4 new test files per the file specs above (Likely Files / Areas to Touch). Each new component takes `ImmutableList<…>` for any list parameter. Each new test file uses the `TestCrumbsTheme(darkTheme: Boolean)` wrapper + `@Config(sdk = [34])` + `@GraphicsMode(GraphicsMode.Mode.NATIVE)`. Per-state-per-theme @Test method pattern (no parameterization).
    Build verify: `./gradlew :core:designsystem:assembleDebug` — must succeed.

### Phase C — Goldens + verification (commit 7)

23. **Regenerate Roborazzi goldens.** `./gradlew --stop && ./gradlew :core:designsystem:recordRoborazziDebug`. Inspect `core/designsystem/src/test/screenshots/` — expect ~60+ updated PNGs (40 existing components regenerated, ~24 new). `git status` shows expected adds + deletes (QuickActionMenu_* removed).

24. **CI-equivalent gate + interactive verification.**
    - `./gradlew --no-daemon clean assembleDebug lintDebug :core:designsystem:verifyRoborazziDebug` — must succeed.
    - `./gradlew :app:installDebug` to the Pixel 6 AVD (substitute `Pixel_9_Pro` if Pixel_6_API_34 still not installed locally — same as tokens slice).
    - Use the quick-skip-auth-page debug affordance to advance past LoginScreen and reach HomeScreen.
    - Visual check: bottom-nav renders as brutalist Row, bookmark cards have new chrome, top-bar shows kicker + Funnel Display wordmark.
    - **Maestro studio dry-run** (AC #6 — PO round 3 Q1): `maestro studio` against the running app. Navigate to HomeScreen, expand the inspector. Confirm `app_root`, `top-bar`, `bottom-nav`, `nav-tab-twitter`/`reddit`/`all`/`map`, `bookmark-card` testTags are queryable. Screenshot the Maestro studio hierarchy panel → `.ai/workflows/brutalist-redesign/verify-evidence/components/01-maestro-studio-hierarchy.png`.
    - Dark mode toggle (`adb shell cmd uimode night yes`) → second emulator capture `02-dark.png`.
    - Long-press a bookmark card in the emulator (popup won't fire yet — behaviors slice wires the callback — but the `onLongPress` callback should at least be called with the correct Offset, verifiable via lazylogcat-instrumented temporary log).

## Test / Verification Plan

### Automated checks

- **Compile gate:** `./gradlew :core:designsystem:assembleDebug :app:assembleDebug :feature:twitter:assembleDebug :feature:reddit:assembleDebug` — must succeed. Verifies the 13 active rebuilds + 4 new components + the BookmarkCard API change ripple don't break any module.
- **Lint gate:** `./gradlew lintDebug` — green. Watches for any unused-import / dead-code drift after the QuickActionMenu deletion.
- **Material3 grep guard (AC-C3):** `grep -rE "MaterialTheme\.|Material[Tt]heme" core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/ --include="*.kt"` → must return zero matches. (Material3 *wrapper composables* like `Button(...)` and `Scaffold(...)` are allowed because the components slice chose mixed-stance — but no token reads.)
- **Hardcoded color grep guard (AC-C3 verbatim):** `grep -rE "Color\(0x[0-9A-Fa-f]+\)" core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/ --include="*.kt"` → zero matches.
- **QuickActionMenu deletion guard (AC-C1 echo):** `grep -r "QuickActionMenu" --include="*.kt" --include="*.png"` → zero matches.
- **Roborazzi gate:** `./gradlew :core:designsystem:verifyRoborazziDebug` — green against regenerated baselines, every diff under the 5%-changed-pixel / 1%-RGB tolerance.
- **CI-equivalent:** `./gradlew --no-daemon clean assembleDebug lintDebug :core:designsystem:verifyRoborazziDebug` — must succeed (mirrors `pr_check.yml:76`).

### Interactive verification (human-in-the-loop)

Stack: `stack.platforms = [android]`, `stack.testing = [junit, compose-ui-test]`, `stack.cli-on-path` includes `android` and `lazylogcat`. Maestro is **out of stack for this slice** but `maestro studio` is permitted as a **read-only inspector** (PO round 3 Q1) — it's already installed per the toolchain verify.

**AC-C3 — Roborazzi golden match (interactive subjective gate):**
- **What to verify:** Each rebuilt component's primary state golden, side-by-side with the handoff JSX render, looks ≥95% match.
- **Platform & tool:** Manual side-by-side; `core/designsystem/src/test/screenshots/*.png` + `Crumbs-handoff/crumbs/project/Crumbs Design Handoff.html` in browser.
- **Companion skills:** none.
- **Steps:** Open the HTML handoff at the components section. For each of `CrumbsBookmarkCard`, `CrumbsButton`, `CrumbsBottomNav`, `CrumbsTopBar`, `CrumbsFilterBar`, `CrumbsLongPressPopup` (the visually heaviest), compare the regenerated golden against the JSX render. Acceptable drift: anti-aliasing, font hinting. Unacceptable: wrong layout, wrong hex.
- **Pass criteria:** Maintainer subjective judgement ≥95% match. Will register as a `runtime-evidence-deferral` on `00-index.md` if not closed before ship.

**AC-C5 — LoadingCard scan-line motion (manual review):**
- **What to verify:** Scan-line is the only animated element; surrounding ink stroke and skeleton blocks are static.
- **Platform & tool:** Pixel 6 (or Pixel 9 Pro fallback) emulator via `android` CLI.
- **Companion skills:** `lazylogcat` for any draw-warnings.
- **Steps:** `./gradlew :app:installDebug`; advance past LoginScreen via Skip Auth (Debug); HomeScreen renders empty-state OR (if seed bookmarks exist) loading cards. Watch for ~10 seconds — only the horizontal line should move.
- **Evidence:** `.ai/workflows/brutalist-redesign/verify-evidence/components/03-loading-card-scanline.gif` (use `adb shell screenrecord` for 5s clip + `ffmpeg -i ... .gif`).
- **Pass criteria:** Visual confirmation. Skeleton blocks do NOT shimmer.

**AC-C6 — Maestro testTag round-trip (interactive):**
- **What to verify:** Every rebuilt component's testTag is queryable from Maestro studio.
- **Platform & tool:** Maestro studio (read-only inspector mode; no flow recording).
- **Companion skills:** `android` CLI (emulator orchestration).
- **Steps:** Launch debug build; advance to HomeScreen via Skip Auth (Debug); `maestro studio`; expand UI hierarchy; visually verify each of the testTags listed in the per-component table is queryable. Capture screenshot of the hierarchy panel.
- **Evidence:** `.ai/workflows/brutalist-redesign/verify-evidence/components/01-maestro-studio-hierarchy.png`.
- **Pass criteria:** Every listed testTag is visible in the Maestro hierarchy inspector. Clears AC4 (toolchain) deferral if all testTags address.

## Risks / Watchouts

- **BookmarkCard `onLongPress` signature change ripples to HomeScreen + AllBookmarksScreen.** Public API widens from `(Bookmark) -> Unit` to `(Bookmark, Offset) -> Unit`. Callers will pass `Offset.Zero` until behaviors slice wires the real popup. Mitigation: do the call-site updates IN THE SAME COMMIT as the BookmarkCard rebuild (commit 4).
- **`detectTapGestures` + `Modifier.clickable` collision.** Web research confirms they conflict — Compose docs say "do not combine." After the BookmarkCard rewrite, **manually grep for `clickable` on every component to ensure none coexists with `pointerInput`-based gesture detection** in the same node. Mitigation: code review checklist line.
- **Roborazzi animation hang on LoadingCard.** Without `mainClock.autoAdvance = false` OR the hoisted-time parameter, `rememberInfiniteTransition` causes test timeouts (issue #413). We've chosen the hoist-time-as-parameter path — tests pass a constant fraction, so `mainClock` manipulation is unnecessary. But if a future test forgets the parameter, the test will hang. Mitigation: code-comment on the `scanLinePositionFraction` parameter explaining its test purpose.
- **Material3 wrapper drift on Compose BOM minor bump.** `CrumbsButton`/`CrumbsScaffold` keep Material3 wrappers with chrome overridden. A future BOM bump could re-introduce a default (e.g., new shape token, new color slot). Mitigation: pin the Material3 version (already governed by BOM), and add a regression-detection golden test for `CrumbsButton_primary_medium_light` so any drift fails the slice's Roborazzi gate.
- **ARCHIVE action is new behavior** (per handoff Screen 5). Slice spec didn't mention archive; shape mentioned only Open/Share/Edit-tags/Delete. The popup ships the visual slot, but **archive behavior implementation goes into the behaviors slice.** Mitigation: docstring on `PopupAction.Archive`: "behavioral wiring deferred to behaviors slice; this slice ships visual shell only."
- **`Modifier.dropShadow` API availability in Compose 1.11.1.** Web research says shipped in Aug-25 release. We're on Compose BOM 2026.05.00 (Compose 1.11.1) — should be available. Mitigation step: in commit 1 (Phase A), add a one-line preview Composable to a scratch test that uses `Modifier.dropShadow` and run `:core:designsystem:assembleDebug`. If the import fails, fall back to the sibling-Box trick and document the deviation.
- **Coil 3 `Size.ORIGINAL` default in `GradientImage`** can blow up memory on large remote images. Mitigation: docstring on `GradientImage` mandating `Modifier.size(...)` or `Modifier.aspectRatio(...)`.
- **ImmutableList migration ripple.** TagEditorDialog's existing `List<String>` parameters change to `ImmutableList<String>`. Call sites in HomeScreen / AllBookmarksScreen / whatever uses the editor must wrap with `.toImmutableList()`. Mitigation: do the ripple in the same commit (commit 5) as the dialog rewrite.
- **QuickActionMenu deletion test-file fallout.** `ActionComponentsTest.kt` contains some QuickActionMenu @Test methods alongside IconButton tests. Plan-time we don't know the exact `@Test` distribution — implement-time has to read the file and delete only the QuickActionMenu-related methods. Same risk pattern as the tokens slice's orphan-test-deletion step.
- **Maestro studio availability.** PO round 3 confirmed `maestro` CLI is installed locally (per toolchain verify). If it's removed before this slice runs, AC-C6 falls back to a `runtime-evidence-deferral`. Mitigation: verify at top of Phase C with `which maestro`.
- **`testTagsAsResourceId` is `@ExperimentalComposeUiApi`** in 1.11.1 (per web research). May fire a compiler `OptIn` warning when we add testTags. Already opted-in at CrumbsTheme:42; per-component compose call sites should inherit.

## Dependencies on Other Slices

**This slice's outputs feed:**
- `layouts` (next slice) — receives the final 17-component surface with stable Composable signatures; uses them as slot content for `HomeScaffold`/`OverlayShell`/`OnboardingShell`.
- `behaviors` (downstream) — receives the popup visual shell, the snackbar shell, the banner shell. Behaviors slice wires the **state machines** (long-press → popup show; tap UNDO → restore tombstone; sync 401 → banner show). **ARCHIVE action** specifically introduced here as visual but wired in behaviors.
- `screens` (downstream) — picks up the rebuilt screens via the layout shells.
- `maestro` (downstream) — consumes every testTag this slice added.

**This slice depends on:**
- `tokens` (shipped) — required CrumbsColors / CrumbsTypography / CrumbsShapes / CrumbsStroke / IBM Plex Mono / app_root testTag. All present and verified.
- `toolchain` (shipped) — required Compose 1.11.1 (for `Modifier.dropShadow`), Coil 3, Roborazzi 1.60, Robolectric 4.16. All present.

**Sibling-plan awareness:** `04-plan-toolchain.md` and `04-plan-tokens.md` exist. The components-slice plan honors the orphan-deletion pull-forward (no re-deletion of the 13 orphans — done in tokens).

## Assumptions

- The BookmarkCard `onLongPress(Bookmark, Offset)` API change is acceptable to break — callers are internal to `app/`, never crossed module boundaries. Verified by grep (no `feature/*` imports of CrumbsBookmarkCard).
- Material3 wrapper defaults in Compose BOM 2026.05.00 don't introduce visual drift after we override every parameter listed in step 5. Will be detected by Roborazzi if violated.
- `Modifier.dropShadow` (Compose 1.11+ native) is available in BOM 2026.05.00. Phase-A scratch verifies (see Risks).
- `kotlinx.collections.immutable:0.3.8` is the current stable version. Web research cutoff is Jan 2026; verify at implement-time and bump to current if newer.
- Maestro CLI is on PATH at slice-implement time.
- The Pixel 6 API 34 AVD (or Pixel 9 Pro fallback) is bootable.
- ARCHIVE action's behavioral semantics ("hide from feed, retrievable via settings") will be decided in the behaviors slice; this slice only ships the visual button.

## Blockers

None. All shape-stage decisions and all 12 PO discovery answers are captured. Stack is PO-confirmed (`stack-source: confirmed`).

## Freshness Research

**`Modifier.dropShadow` for brutalist offset shadows (Compose 1.11+):**
- Source: [What's new in Jetpack Compose August '25 release](https://android-developers.googleblog.com/2025/08/whats-new-in-jetpack-compose-august-25-release.html); [The Art of Shadows in Jetpack Compose — droidcon](https://www.droidcon.com/2025/10/13/the-art-of-shadows-in-jetpack-compose/); [BoltUiX — Jetpack Compose Shadows](https://www.boltuix.com/2025/11/jetpack-compose-shadows.html).
- Takeaway: Native API ships in Compose 1.11; supports `DpOffset` + `blurRadius = 0.dp` for hard offset shadows. Use over the sibling-Box trick.

**`androidx.compose.ui.window.Popup` with custom PopupPositionProvider for fingertip anchoring:**
- Source: [Popup composable — composables.com](https://composables.com/compose-ui/popup); [PopupProperties API reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/PopupProperties).
- Takeaway: Use `PopupPositionProvider.calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize)` to clamp to window edges. `PopupProperties(focusable = true, dismissOnClickOutside = true, dismissOnBackPress = true, clippingEnabled = true)`. The fingertip Offset from `detectTapGestures(onLongPress = { px -> ... })` is in pixels relative to the gestured Composable; add to `anchorBounds.left/top` to convert to window coords.

**Roborazzi 1.60 + `infiniteTransition` deterministic capture:**
- Source: [Roborazzi issue #413](https://github.com/takahirom/roborazzi/issues/413); [Roborazzi tips talk](https://speakerdeck.com/sumio/a-collection-of-useful-tips-for-taking-screenshots-in-roborazzi); [Test animations — Android Developers](https://developer.android.com/develop/ui/compose/animation/testing).
- Takeaway: `composeRule.mainClock.autoAdvance = false; advanceTimeBy(N); captureRoboImage()` avoids the infinite-test-hang. We chose the alternative: hoist time as a Composable parameter, pass a constant. Same effect, simpler test code.

**`combinedClickable` vs `detectTapGestures` for long-press with position:**
- Source: [Tap and press — Android Developers](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/tap-and-press); [combinedClickable reference](https://composables.com/foundation/combinedclickable).
- Takeaway: `combinedClickable.onLongClick` does NOT supply position. Must use `detectTapGestures(onLongPress = { offset: Offset -> ... })`. The two cannot coexist on the same Modifier chain — they consume the same events.

**Coil 3.x default size = `Size.ORIGINAL`:**
- Source: [Upgrading to Coil 3 docs](https://coil-kt.github.io/coil/upgrading_to_coil3/); [Coil 3 Compose docs](https://coil-kt.github.io/coil/compose/).
- Takeaway: Without explicit `Modifier.size(...)` or `.aspectRatio(...)`, AsyncImage loads at original resolution → memory blow-up risk for remote URLs. Document in GradientImage/CrumbsBookmarkCard.

**`kotlinx.collections.immutable` for Compose stability:**
- Source: [Twitter Compose lint rules](https://twitter.github.io/compose-rules/rules/); [The Great Kotlin List Paradox](https://medium.com/@devabdulkadirali/the-great-kotlin-list-paradox-why-jetpack-compose-thinks-your-immutable-lists-are-unstable-ce49b106ff96).
- Takeaway: `List<T>` is unstable even when declared `val` (interface, not implementation). `ImmutableList<T>` is `@Immutable`-annotated and gives Compose skippability for free.

**`LiveRegionMode.Polite` for accessible snackbar announcement:**
- Source: [LiveRegionMode API](https://developer.android.com/reference/kotlin/androidx/compose/ui/semantics/LiveRegionMode); [Compose accessibility techniques: PopupMessages](https://github.com/cvs-health/android-compose-accessibility-techniques/blob/main/doc/components/PopupMessages.md).
- Takeaway: Set `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` on snackbar root so TalkBack announces non-interruptively.

**CVE / security:** No CVEs surfaced for Compose UI 1.11.x, Material3 1.4.x, Coil 3.x, Roborazzi 1.60 in 2026 web searches.

## Revision History

*(empty — first revision)*

## Recommended Next Stage

- **Option A (default):** `/wf implement brutalist-redesign components` — execute Phases A→C above (7 commits, ~24 plan steps). Pre-flight: `/compact` first to drop planning research from the conversation; PreCompact hook preserves workflow state.
- **Option B:** `/wf plan brutalist-redesign components <feedback>` — revise this plan. Use if any of the 12 PO answers feels wrong on second read, or if the per-component Material3 stance allocation (which components strip vs keep wrapper) doesn't match maintainer intuition.
- **Option C:** `/wf slice brutalist-redesign` — revisit slice boundaries. **Not recommended** — planning surfaced no slice-boundary problem; the per-family commit grouping is a within-slice scoping detail.
