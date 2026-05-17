---
schema: sdlc/v1
type: plan
slug: brutalist-redesign
slice-slug: layouts
status: implemented
stage-number: 4
created-at: "2026-05-17T14:55:21Z"
updated-at: "2026-05-17T15:24:46Z"
metric-files-to-touch: 8
metric-step-count: 14
has-blockers: false
revision-count: 0
tags: [layouts, scaffolds, brutalist, designsystem, edge-to-edge, pager]
stack-source: confirmed
refs:
  index: 00-index.md
  plan-index: 04-plan.md
  slice-def: 03-slice-layouts.md
  siblings:
    - 04-plan-toolchain.md
    - 04-plan-tokens.md
    - 04-plan-components.md
  implement: 05-implement-layouts.md
next-command: wf-implement
next-invocation: "/wf implement brutalist-redesign layouts"
---

# Plan: layouts

## Current State

The `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/` sub-package does not yet exist. The slice creates it.

Edge-to-edge configuration is **absent repo-wide**: zero matches for `enableEdgeToEdge`, `WindowInsets`, or any `*sPadding` insets modifier across the codebase. `MainActivity.onCreate()` ([app/src/main/java/com/github/jayteealao/crumbs/MainActivity.kt:1](app/src/main/java/com/github/jayteealao/crumbs/MainActivity.kt:1)) currently wraps content in `CrumbsTheme { CrumbsNavHost(...) }` with no `enableEdgeToEdge()` call. Android 15 enforcement (compileSdk 35) makes this a slice precondition — PO chose to land it in this slice.

Existing screen composition pattern (representative — [HomeScreen.kt:37](app/src/main/java/com/github/jayteealao/crumbs/screens/HomeScreen.kt:37)):

```kotlin
CrumbsScaffold(
    topBar = { CrumbsTopBar(...) },
    bottomBar = { CrumbsBottomNav(...) },
) { padding -> ... }
```

Screens compose `CrumbsScaffold` + `CrumbsTopBar` + `CrumbsBottomNav` directly; no intermediate layout wrapper exists today. `LoginScreen` has no Scaffold (full-bleed custom layout); `OnboardingScreen` composes Accompanist `HorizontalPager` directly inside `CrumbsScaffold`.

The components slice already established:
- `CrumbsScaffold` ([CrumbsScaffold.kt:34](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsScaffold.kt:34)) — Material3 Scaffold passthrough, `testTag("scaffold-root")`, no inset handling.
- `CrumbsTopBar` — two-row layout (kicker + 56dp wordmark/search), no status-bar inset awareness.
- `CrumbsBottomNav` — 4-cell Row, no nav-bar inset awareness.
- Roborazzi tolerance at `gradle.properties:59` (`roborazzi.compare.changeThreshold=0.05`).
- Test boilerplate ([LoadingCardTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/LoadingCardTest.kt)): `@RunWith(RobolectricTestRunner)`, `@GraphicsMode(NATIVE)`, `@Config(sdk = [34])`, `createAndroidComposeRule<ComponentActivity>()`, `TestCrumbsTheme` wrapper, `captureRoboImage("src/test/screenshots/...")`.
- `testTagsAsResourceId = true` at [CrumbsTheme.kt:40](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTheme.kt:40).
- `kotlinx.collections.immutable` on `api` scope — `ImmutableList` visible transitively.

Accompanist Pager `0.22.0-rc` still on classpath ([libs.versions.toml:7](gradle/libs.versions.toml:7)), but the slice spec says OnboardingShell pre-emptively imports the Compose-native pager (`androidx.compose.foundation.pager.HorizontalPager`); the screens slice owns the OnboardingScreen migration off Accompanist itself.

## Reuse Opportunities

| Candidate | Location | Match | Recommendation |
|-----------|----------|-------|----------------|
| `CrumbsScaffold` | [components/CrumbsScaffold.kt:34](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsScaffold.kt:34) | Material3 Scaffold passthrough that HomeScaffold can compose into without re-inventing | **Reuse as-is.** HomeScaffold wraps CrumbsScaffold and adds slot wiring + insets. |
| `CrumbsTopBar` / `CrumbsBottomNav` / `CrumbsFilterBar` | components/ | Stable APIs from the components slice — HomeScaffold's slot consumers | **Reuse as-is** in callers; HomeScaffold itself remains slot-generic (any composable accepted). |
| `CrumbsLongPressPopup` PopupPositionProvider | [components/CrumbsLongPressPopup.kt:90](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsLongPressPopup.kt:90) | Proves `Popup` + `dismissOnBackPress` + `dismissOnClickOutside` pattern | **Reference only.** PO chose in-tree composition for OverlayShell (better IME + a11y). Pattern not reused. |
| `AnimatedVisibility` usage in CrumbsTopBar | [components/CrumbsTopBar.kt:93](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsTopBar.kt:93) | Show/hide transitions already in use | **Reuse pattern** for OverlayShell scrim + sheet entrance/exit. |
| `pointerInput { detectTapGestures }` precedent | [components/CrumbsBookmarkCard.kt:77](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBookmarkCard.kt:77) | Click-style gesture detection | **Not reused.** PO chose `Modifier.clickable(indication = null)` for backdrop (better a11y, simpler). |
| LoadingCardTest scaffold | [test/.../components/LoadingCardTest.kt](core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/LoadingCardTest.kt) | Roborazzi test boilerplate | **Copy + adapt** for `layouts/` test package. |
| Existing onboarding / login / modal wrappers | (none) | The codebase has no reusable layout shell today | **Implement fresh.** Layouts slice is greenfield. |

No reuse candidates would force a backward-incompatible signature change. New shells are additive; consumer-screen migration is the screens slice's job.

## Likely Files / Areas to Touch

**New files (4):**
- `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/HomeScaffold.kt`
- `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/OverlayShell.kt`
- `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/OnboardingShell.kt`
- (optional internal helper) `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/OnboardingPageIndicator.kt` — private to package, only if extracting from OnboardingShell improves readability. Default: inline in `OnboardingShell.kt`.

**New test files (3):**
- `core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/layouts/HomeScaffoldTest.kt` — 2 Roborazzi goldens (light + dark) with stub slot content.
- `core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/layouts/OverlayShellTest.kt` — 2 Roborazzi goldens (open light + open dark) + 1 non-Roborazzi UI test asserting backdrop tap fires `onDismiss`.
- `core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/layouts/OnboardingShellTest.kt` — 2 Roborazzi goldens (page 0 light + page 1 dark) with 3 stub pages and a stub CTA.

**Modified files (1):**
- `app/src/main/java/com/github/jayteealao/crumbs/MainActivity.kt` — add `enableEdgeToEdge()` call before `setContent`, import `androidx.activity.enableEdgeToEdge`.

**Regenerated goldens:** 6 new PNGs under `core/designsystem/src/test/screenshots/`:
- `HomeScaffold_default_light.png`, `HomeScaffold_default_dark.png`
- `OverlayShell_open_light.png`, `OverlayShell_open_dark.png`
- `OnboardingShell_page0_light.png`, `OnboardingShell_page1_dark.png`

## Proposed Change Strategy

Single atomic commit per the implement-stage contract. Build the three shells additively, write goldens, record + verify Roborazzi, run `:app:assembleDebug` (no callers migrate yet — that's the screens slice). The shells are pure additions; nothing existing breaks until screens slice composes them.

**Locked design decisions (from Round 1+2 discovery):**
- **enableEdgeToEdge** lands in `MainActivity.onCreate()` during this slice.
- **OverlayShell** uses in-tree composition: outer `Box` + scrim `Box` with `AnimatedVisibility(fadeIn)` + sheet `AnimatedVisibility(slideInVertically + fadeIn)` aligned `BottomCenter`. Pair with `BackHandler(enabled = visible) { onDismiss() }`.
- **OnboardingShell** pager slot: `pages: ImmutableList<@Composable () -> Unit>` + `pagerState: PagerState = rememberPagerState(pageCount = { pages.size })`. Shell internally renders `androidx.compose.foundation.pager.HorizontalPager`.
- **filterBar** is `(@Composable () -> Unit)? = null` on HomeScaffold.
- **Backdrop dismiss** uses `Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }` with `Modifier.semantics { contentDescription = "Dismiss overlay" }` for a11y.
- **Inset strategy in tests**: HomeScaffold exposes a `contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets` parameter; tests default to `WindowInsets(0)` for determinism. Insets render as 0 in goldens; AC-2's "28dp gap" is preserved as a runtime-observable claim verified by the maestro slice / live emulator.
- **AC-3 verification** ships in this slice as a non-Roborazzi Compose UI test in `OverlayShellTest.kt`.
- **Footer in OnboardingShell** is a single internally-composed `Row(SpaceBetween)` with shell-owned `OnboardingPageIndicator` (3 pills, accent on `currentPage`) + an optional `CrumbsButton` driven by `footerCtaText: String?` + `onFooterCtaClick: (() -> Unit)?`.

## Step-by-Step Plan

1. **Create the `layouts/` sub-package.** Add an empty `package-info.kt` or just rely on Kotlin package inference — create the directory by writing the first file.

2. **`HomeScaffold.kt`** — slots `topBar: @Composable () -> Unit`, `filterBar: (@Composable () -> Unit)? = null`, `bottomBar: @Composable () -> Unit`, `content: @Composable (PaddingValues) -> Unit`. Wraps `androidx.compose.material3.Scaffold` directly (do **not** wrap `CrumbsScaffold` — its `testTag("scaffold-root")` would collide with HomeScaffold's `testTag("home-scaffold")`; instead compose Scaffold here with its own testTag). Set `containerColor = CrumbsTheme.colors.background`. In the `topBar` slot, compose `Column(Modifier.statusBarsPadding().testTag("home-scaffold-topbar")) { topBar(); filterBar?.invoke() }` so the status-bar inset is consumed once at the Column level (avoids double-consume). Bottom slot wraps `bottomBar()` in `Box(Modifier.navigationBarsPadding().testTag("home-scaffold-bottombar")) { bottomBar() }`. Slot tags: filterBar appears as `home-scaffold-filterbar` only when supplied (wrap filterBar invocation in a `Box(Modifier.testTag("home-scaffold-filterbar"))`). Root testTag `home-scaffold`. Single `@Preview` showing stub slot content (labeled rectangles) for both themes.

3. **`OverlayShell.kt`** — params: `visible: Boolean`, `onDismiss: () -> Unit`, `header: (@Composable () -> Unit)? = null`, `body: @Composable () -> Unit`, `footer: (@Composable () -> Unit)? = null`. Compose:
   ```
   Box(Modifier.fillMaxSize().testTag("overlay-shell")) {
     AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
       Box(Modifier.fillMaxSize()
             .background(Color.Black.copy(alpha = 0.45f))
             .testTag("overlay-shell-backdrop")
             .semantics { contentDescription = "Dismiss overlay" }
             .clickable(remember { MutableInteractionSource() }, indication = null) { onDismiss() })
     }
     AnimatedVisibility(visible,
         enter = slideInVertically { it } + fadeIn(),
         exit = slideOutVertically { it } + fadeOut(),
         modifier = Modifier.align(Alignment.BottomCenter)) {
       Surface(color = CrumbsTheme.colors.surface,
               shape = RectangleShape,
               border = BorderStroke(stroke.regular, CrumbsTheme.colors.ink),
               modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
         Column {
           header?.let { Box(Modifier.testTag("overlay-shell-header")) { it() } }
           Box(Modifier.testTag("overlay-shell-body")) { body() }
           footer?.let { Box(Modifier.testTag("overlay-shell-apply")) { it() } }
         }
       }
     }
   }
   BackHandler(enabled = visible) { onDismiss() }
   ```
   No Material3 chrome. Surface uses brutalist ink-border + paper background. `footer`'s testTag is `overlay-shell-apply` per slice spec (line 52).

4. **`OnboardingPageIndicator` (private to layouts package)** — `@Composable internal fun OnboardingPageIndicator(pagerState: PagerState, modifier: Modifier = Modifier)`. Renders `Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(pagerState.pageCount) { i -> Box(Modifier.size(width = 14.dp, height = 4.dp).background(if (i == pagerState.currentPage) accent else ink.copy(alpha = 0.25f))) } }`. RectangleShape pills (brutalist — no rounding).

5. **`OnboardingShell.kt`** — params: `pages: ImmutableList<@Composable () -> Unit>`, `pagerState: PagerState = rememberPagerState(pageCount = { pages.size })`, `header: (@Composable () -> Unit)? = null`, `footerCtaText: String? = null`, `onFooterCtaClick: (() -> Unit)? = null`. Compose `Column(Modifier.fillMaxSize().testTag("onboarding-shell").statusBarsPadding().navigationBarsPadding()) { header?.let { it() }; HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { i -> pages[i]() }; Row(Modifier.fillMaxWidth().padding(spacing.lg), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { OnboardingPageIndicator(pagerState); if (footerCtaText != null && onFooterCtaClick != null) { CrumbsButton(text = footerCtaText, onClick = onFooterCtaClick, style = CrumbsButtonStyle.Primary) } } }`. Edge-to-edge insets consumed at the Column root.

6. **`HomeScaffoldTest.kt`** — 2 tests: `homeScaffold_default_light()` + `homeScaffold_default_dark()`. Setup wraps `TestCrumbsTheme(darkTheme = false/true) { HomeScaffold(topBar = { StubBlock("topBar", 88.dp) }, filterBar = { StubBlock("filterBar", 34.dp) }, bottomBar = { StubBlock("bottomBar", 52.dp) }) { padding -> Box(Modifier.padding(padding).fillMaxSize().background(colors.surface).testTag("stub-content")) } }`. `StubBlock` is a local helper that renders a `Box(Modifier.fillMaxWidth().height(height).background(colors.ink.copy(alpha=0.08f)))` with the label centered. Capture to `src/test/screenshots/HomeScaffold_default_{light,dark}.png`.

7. **`OverlayShellTest.kt`** — 2 Roborazzi tests with `visible = true` + stub header/body/footer. Capture `OverlayShell_open_{light,dark}.png`. Plus 1 Compose UI test (no captureRoboImage):
   ```kotlin
   @Test fun backdrop_tap_invokes_onDismiss() {
     var dismissed = false
     composeTestRule.setContent {
       TestCrumbsTheme { OverlayShell(visible = true, onDismiss = { dismissed = true }) { Text("body") } }
     }
     composeTestRule.onNodeWithTag("overlay-shell-backdrop").performClick()
     assertTrue(dismissed)
   }
   ```
   Closes AC-3.

8. **`OnboardingShellTest.kt`** — 2 Roborazzi tests with `pages = persistentListOf({ StubPage("Page 0") }, { StubPage("Page 1") }, { StubPage("Page 2") })`. First test stays on `currentPage = 0` (default), captures `OnboardingShell_page0_light.png`. Second test uses `rememberPagerState(pageCount = { 3 }, initialPage = 1)` to set `currentPage = 1` and captures `OnboardingShell_page1_dark.png`. Disable `composeTestRule.mainClock.autoAdvance = false` + `composeTestRule.waitForIdle()` before capture (per Roborazzi guidance for lazy layouts).

9. **`MainActivity.kt` edit** — import `androidx.activity.enableEdgeToEdge`; call `enableEdgeToEdge()` as the first line of `onCreate()` after `super.onCreate(savedInstanceState)`. No further MainActivity changes.

10. **Build gate** — run `./gradlew :app:assembleDebug` to confirm MainActivity + shells compile. No caller migrations in this slice (screens slice owns those).

11. **Record + verify goldens** — run `./gradlew :core:designsystem:recordRoborazziDebug` to materialize the 6 new PNGs. Inspect them visually for sanity (status-bar 0dp gap as expected; layout math correct; brutalist ink stroke present on OverlayShell sheet). Then `./gradlew :core:designsystem:verifyRoborazziDebug` to confirm the recorded set passes the configured tolerance loop on itself.

12. **Lint gate** — run `./gradlew :core:designsystem:lintDebug :app:lintDebug` to catch any inset-handling lint warnings (Android lint sometimes complains about missing `WindowInsetsListener` patterns; we use the Compose path, which should be clean).

13. **testTag dry-grep** — `grep -rn 'testTag(' core/designsystem/src/main/.../layouts/` — confirm `home-scaffold`, `home-scaffold-topbar`, `home-scaffold-filterbar`, `home-scaffold-bottombar`, `overlay-shell`, `overlay-shell-backdrop`, `overlay-shell-apply`, `overlay-shell-header` (added beyond slice spec for completeness — non-breaking), `overlay-shell-body`, `onboarding-shell` all appear. AC-5 (Maestro studio) is deferred to maestro slice per workflow precedent.

14. **Atomic commit** with subject `feat(design-system): add HomeScaffold / OverlayShell / OnboardingShell layout shells` and the implement record's standard body. Do not push.

## Test / Verification Plan

### Automated checks

- **lint/typecheck**: `./gradlew :core:designsystem:lintDebug :app:lintDebug` — green expected; new code follows established patterns.
- **unit tests**: `./gradlew :core:designsystem:testDebugUnitTest` — exercises the 7 new tests in `layouts/` (6 Roborazzi + 1 dismissal UI test).
- **Roborazzi gate**: `./gradlew :core:designsystem:verifyRoborazziDebug` — must pass at the 5%-changed-pixel + 1%-RGB tolerance configured at `gradle.properties:59`. Initial run records (no diff); subsequent runs verify.
- **build gate**: `./gradlew :app:assembleDebug` — MainActivity's `enableEdgeToEdge()` import resolves; layouts package compiles for all consumers (none yet — additive).
- **grep guard**: confirm no `Color(0xFF…)` literals and no `MaterialTheme.*` references in `layouts/` (mirror the components slice's gate).

### Interactive verification (human-in-the-loop)

Per the workflow's confirmed `stack: [android]` and `stack.testing: [junit, compose-ui-test]`:

- **What to verify**: AC-5 — Maestro studio dry-run confirms shell-level + slot-level testTags are queryable on the running debug app.
- **Platform & tool**: Android — Maestro CLI + `android` CLI for `installDebug`. Per workflow precedent (toolchain AC4, components AC-C6), Maestro studio interactive verification is **owned by the dedicated `maestro` slice**, not run here.
- **Companion skills**: `android-cli` (from `stack.available-skills`) for emulator install. `lazylogcat` for any inset-related Android logs during a manual smoke-test if needed.
- **Steps (when finally run in maestro slice)**:
  1. `./gradlew :app:installDebug`
  2. Boot Pixel 6 API 34 emulator (or Medium_Phone_API_36 per AVD inventory drift).
  3. `maestro studio` against the running app — confirm each layouts testTag is queryable. Layouts shells become reachable only after the `screens` slice composes them into real screens.
- **Evidence capture**: Maestro studio screenshot of the View Hierarchy panel showing each testTag.
- **Pass criteria**: `home-scaffold`, `home-scaffold-topbar`, `home-scaffold-filterbar`, `home-scaffold-bottombar`, `overlay-shell`, `overlay-shell-backdrop`, `overlay-shell-apply`, `overlay-shell-body`, `overlay-shell-header`, `onboarding-shell` all appear in the queryable resource-id list. Maestro slice owns the artifact write.

- **AC-2 visual fidelity (28dp status-bar gap, 88dp top, 34dp filter, 52dp bottom + 8dp nav-pill)**: real-device verification is a **maestro-slice or behaviors-slice** concern with real WindowInsets dispatched. The layouts-slice goldens use `WindowInsets(0)` for determinism. The runtime claim transfers to a runtime-evidence-deferral on AC-2.

Register at verify-stage as runtime-evidence-deferrals:
- **AC-2 inset-applied measurement** — deferrable; cleared by emulator capture in maestro slice or by `/wf-quick probe` once a screen composes HomeScaffold and renders on a real device.
- **AC-5 Maestro studio dry-run** — deferrable; cleared by maestro slice's testTag round-trip flow.

### Compose UI test (in-process, AC-3)

`OverlayShellTest.kt` ships a non-Roborazzi `@Test fun backdrop_tap_invokes_onDismiss()` — closes AC-3 entirely within the slice.

## Risks / Watchouts

- **`Modifier.statusBarsPadding()` inside Material3 Scaffold's `topBar` slot**: web research notes a double-consume risk if Scaffold's `contentWindowInsets` is left at default AND the topBar slot also pads. The mitigation: HomeScaffold's topBar slot is a `Column` wrapping `topBar() + filterBar()`, and we apply `statusBarsPadding()` once on that Column. Do NOT call `Modifier.windowInsetsPadding(WindowInsets.statusBars)` separately inside `topBar()` itself. The Scaffold's default `contentWindowInsets` (`ScaffoldDefaults.contentWindowInsets`) is fine — Scaffold computes content insets from what slots consume.
- **AnimatedVisibility + BackHandler**: `BackHandler(enabled = visible)` must be co-located in the same Composable scope as the OverlayShell parent, not inside an `AnimatedVisibility` that disposes when `visible=false` — otherwise back-press wouldn't fire while the exit animation is running. Place BackHandler at the outer `Box` level.
- **HorizontalPager under Robolectric**: lazy-layout rendering needs `composeTestRule.waitForIdle()` and `mainClock.autoAdvance = false` before `captureRoboImage`, or pages can render empty. Roborazzi 1.60.0 + `@GraphicsMode(NATIVE)` fixes the historical software-rendering crash (issue #290); we keep the rule defensively.
- **OnboardingShell pager + indicator drift**: putting the indicator inside the pager's content slot would scroll it with pages — keep indicator in the footer `Row` outside the Pager. Plan does this correctly; flag for review.
- **MainActivity edge-to-edge cascade**: introducing `enableEdgeToEdge()` makes existing screens (LoginScreen, HomeScreen, OnboardingScreen) render under the status bar until they consume insets. The screens slice will migrate them. **Interim verify on the running app may look broken** between layouts merge and screens merge — flag this in the implement record and in the verify-stage report.
- **OverlayShell IME**: with in-tree composition, IME insets dispatch correctly; `Modifier.imePadding()` on the inner Surface lifts the sheet above the keyboard. Goldens won't show IME (no soft keyboard in Robolectric); verify behaviorally in maestro slice.
- **Roborazzi WindowInsets-0 default ≠ real device**: AC-2's "28dp gap top" claim cannot be captured by Robolectric goldens. Plan defers the measurement-level fidelity check to the maestro slice. Reviewer of goldens should not flag the 0dp top as a regression.

## Dependencies on Other Slices

- **`components` (verified-partial)**: HomeScaffold's *callers* will pass `CrumbsTopBar` / `CrumbsBottomNav` / `CrumbsFilterBar` instances — those must exist in their brutalist forms. They do. The shell itself is slot-generic and does not import any specific component.
- **`tokens` (verified-partial)**: `CrumbsTheme.colors.{background, surface, ink, accent}`, `CrumbsTheme.typography.*`, `LocalCrumbsSpacing`, `LocalCrumbsStroke` — all must be the brutalist values. They are.
- **`screens` (deferred)** *consumes* this slice: screens slice migrates every screen onto HomeScaffold / OverlayShell / OnboardingShell + handles Accompanist → Compose Pager migration in `OnboardingScreen`.
- **`maestro` (deferred)** *consumes* this slice's testTag scaffolding for AC-5 round-trip.
- **`behaviors` (deferred)**: the slice spec OUT list includes "Behavior wiring inside the overlay (handled by behaviors slice)." Behaviors slice owns the long-press popup state machine + filter overlay show/hide state + snackbar timer + banner trigger.

## Assumptions

- The shells are pure additions to the design system. No screen migration in this slice; nothing breaks.
- `enableEdgeToEdge()` in MainActivity makes the running app render under the status bar from this slice forward. **Interim visual artifact**: between this slice's merge and the screens slice's screen migrations, screens that don't yet consume insets will show TopBar partially under the status bar. This is acknowledged and recorded as a known interim state.
- The Roborazzi goldens use `WindowInsets(0)`-style insets-as-zero behavior, which is the Robolectric default. Goldens prove **layout composition is correct**; real-device inset application is a maestro / live-emulator concern.
- 4 Roborazzi goldens for HomeScaffold/Overlay (×2 themes each, ignoring OnboardingShell's page-state variation) + 2 for OnboardingShell (page 0 light + page 1 dark) = 6 total new PNGs. No 4-state matrix per shell (default-only — shells are layout primitives, not interactive components with multiple visual states).
- `ImmutableList<@Composable () -> Unit>` on the `OnboardingShell.pages` parameter is the right type for stability — composables-as-data are common in Compose APIs and `ImmutableList` keeps recomposition deterministic. `core/designsystem`'s `api libs.kotlinx.collections.immutable` declaration (components slice) makes this importable transitively.

## Blockers

None. All `stack:` tooling is present; all upstream slices (toolchain, tokens, components) provide the required APIs; web research shows no blocking dependency drift in Compose 1.11.1 / Material3 1.4.0 / Roborazzi 1.60.0 / Robolectric 4.16.

## Freshness Research

Captured in full from the parallel web-research sub-agent. Top-level takeaways that directly shape plan steps:

- **enableEdgeToEdge() is canonical in Compose 1.11 / compileSdk 35** — Android 15 enforces edge-to-edge once targetSdk = 35, and the opt-out attribute is being removed in future SDKs. ([developer.android.com/develop/ui/compose/system/setup-e2e](https://developer.android.com/develop/ui/compose/system/setup-e2e))
- **Material3 1.4.0 Scaffold's `contentWindowInsets`** still applies to the content slot as `PaddingValues`; topBar/bottomBar slots own their own inset consumption. No breaking API change in 1.4.0. ([developer.android.com/develop/ui/compose/system/material-insets](https://developer.android.com/develop/ui/compose/system/material-insets))
- **Accompanist Pager is fully deprecated**; the replacement is `androidx.compose.foundation.pager.HorizontalPager` with `rememberPagerState(pageCount = { N })` lambda form (stable since 1.6). ([developer.android.com/develop/ui/compose/layouts/pager](https://developer.android.com/develop/ui/compose/layouts/pager), [accompanist#1567](https://github.com/google/accompanist/issues/1567))
- **No first-party `HorizontalPagerIndicator`** — pattern is `Row` of `Box` clip CircleShape (or RectangleShape for brutalist). Slice plan owns the in-shell `OnboardingPageIndicator`. ([custom indicator guide](https://developer.android.com/develop/ui/compose/quick-guides/content/custom-page-indicator))
- **Custom bottom-anchored modal pattern**: `AnimatedVisibility(slideInVertically + fadeIn)` aligned `BottomCenter` + scrim with `Modifier.clickable(indication = null)` + `BackHandler` is the modern Compose recipe; preferred over `Popup` for IME and a11y reasons. ([slanglabs writeup](https://medium.com/slanglabs/animating-bottom-sheets-in-jetpack-compose-add-life-to-your-ui-981fbfe5f048))
- **`Modifier.dropShadow` (deferred from components slice)** lives at `androidx.compose.ui.graphics.shadow.dropShadow`, stable since Compose 1.9 (BOM 2026.05.00 honors). Behaviors slice owns the actual adoption. ([Compose Aug '25 release blog](https://android-developers.googleblog.com/2025/08/whats-new-in-jetpack-compose-august-25-release.html))
- **Roborazzi 1.60.0 + Robolectric 4.16**: WindowInsets are 0 by default; `statusBarsPadding()` contributes 0dp. For deterministic captures, plan hoists insets as a test parameter and accepts 0 in goldens (PO decision). Lazy layouts (HorizontalPager) need `mainClock.autoAdvance = false` + `waitForIdle()` before capture. ([Roborazzi flaky tests](https://medium.com/@takahirom/how-to-solve-flaky-robolectric-and-roborazzi-tests-5731e55581cd))

No CVEs, deprecation notices, or version pin issues surfaced affecting this slice's dependencies.

## Revision History

*(none — initial plan)*

## Recommended Next Stage

- **Option A (default):** `/wf implement brutalist-redesign layouts` — plan is execution-ready; small, additive slice with clear single-commit shape. **`/compact` recommended** — research and discovery rounds are noise for implement.
- **Option B:** `/wf plan brutalist-redesign layouts <feedback>` — revise this plan if any Round 1/Round 2 decision feels wrong on second read (e.g., reconsider Popup vs in-tree, or revisit the `enableEdgeToEdge()` placement).
- **Option C:** `/wf slice brutalist-redesign` — revisit slice boundaries. **Not recommended** — this plan surfaced no boundary problem; the interim-visual-artifact caveat (screens not yet migrated post-edge-to-edge) is a sequencing detail captured here, not a slice-boundary defect.
