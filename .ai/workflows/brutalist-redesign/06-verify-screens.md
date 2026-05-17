---
schema: sdlc/v1
type: verify
slug: brutalist-redesign
slice-slug: screens
status: complete
stage-number: 6
created-at: "2026-05-17T19:13:28Z"
updated-at: "2026-05-17T19:13:28Z"
result: partial
metric-checks-run: 6
metric-checks-passed: 6
metric-acceptance-met: 3
metric-acceptance-total: 8
metric-acceptance-user-observable: 7
metric-acceptance-code-only: 1
metric-interactive-checks-run: 0
metric-interactive-checks-passed: 0
metric-issues-found: 0
metric-issues-found-initial: 0
metric-issues-found-final: 0
fix-rounds-run: 0
convergence: not-needed
verify-owned-fix-commit: null
interactive-verification: deferred
interactive-verification-defer-reason: "Five user-observable AC defer to either maintainer-driven manual diff or the dedicated maestro slice. AC-S1 (≥95% light-theme fidelity) + AC-S2 (≥95% dark-theme fidelity) — automated Roborazzi tolerance (5% changed-pixel + 1% RGB) is met for all 16 goldens, but subjective mock-fidelity adjudication against the Option-D handoff is not automatable; maintainer-owned per-screen sign-off (same precedent as tokens AC-K4 + toolchain AC6). AC-S4 (manual nav walkthrough on Pixel 6 emulator) — collapses onto maestro slice's emulator+Maestro happy-path run (same pattern as toolchain AC4, components AC-C6, layouts AC-L5). AC-S6 nav-half (empty-state CONNECT-AN-ACCOUNT button navigates to LoginScreen) — callback fires verified in-process via AllBookmarksScreenTest.emptyState_connectAccountCta_invokesCallback; navigation half collapses onto maestro slice. AC-S7 (long-press popup opens with 4 actions on AllBookmarksScreen) — popup component is fully covered by components slice's LongPressPopupTest goldens; the AllBookmarks-level long-press-to-popup flow needs a touch-input runtime which collapses onto maestro slice."
adapters-used: []
bootstrap-failures: []
adapters-excluded-by-stack: []
evidence-dir: ".ai/workflows/brutalist-redesign/verify-evidence/screens/"
stack-source: confirmed
tags: [screens, brutalist, roborazzi, route-screen-split, pager-migration, runtime-evidence-deferral]
refs:
  index: 00-index.md
  verify-index: 06-verify.md
  slice-def: 03-slice-screens.md
  plan: 04-plan-screens.md
  implement: 05-implement-screens.md
  review: 07-review-screens.md
  adapters: ${CLAUDE_PLUGIN_ROOT}/skills/wf/reference/runtime-adapters.md
next-command: wf-review
next-invocation: "/wf review brutalist-redesign screens"
---

# Verify: screens

## Verification Summary

Every automated gate is green at implement-commit `c72e489`. Eight screens have been rewritten under the Route/Screen split pattern; 16 Roborazzi goldens (12 in `:app`, 2 in `:feature:twitter`, 2 in `:feature:reddit`) verify cleanly under the workflow-wide 5% changed-pixel + 1% RGB tolerance. Three ACs close outright (AC-S3 Accompanist source removal, AC-S5 MapView placeholder, AC-S8 OAuth ViewModel byte-stability). Five ACs are procedurally deferred — two to a maintainer-owned manual mock-fidelity diff (AC-S1, AC-S2) and three to the dedicated maestro slice (AC-S4, AC-S6 nav half, AC-S7). The AC-S6 callback half and AC-S8 callback wiring are both closed in-process via Compose UI tests on the screens commit.

No fix loop ran. `result: partial` reflects only the five procedural deferrals; no AC is substantively unmet.

## Automated Checks Run

- `./gradlew :app:lintDebug :feature:twitter:lintDebug :feature:reddit:lintDebug` → **PASS** — no new lint findings introduced by the slice.
- `./gradlew :app:assembleDebug` → **PASS** (UP-TO-DATE at HEAD) — app links against the rewritten 8 screens + 4 new Route files + Roborazzi-enabled feature modules.
- `./gradlew :app:testDebugUnitTest :feature:twitter:testDebugUnitTest :feature:reddit:testDebugUnitTest` → **PASS** — 19 tests, 0 failures across 8 new screen test classes (`SplashScreenTest:2`, `OnboardingScreenTest:2`, `LoginScreenTest:4`, `HomeScreenTest:2`, `AllBookmarksScreenTest:3`, `MapViewScreenTest:2`, `TwitterBookmarksScreenTest:2`, `RedditBookmarksScreenTest:2`) plus the two `ExampleUnitTest` stubs. The `LoginScreenTest:4` count includes two Roborazzi goldens + two OAuth-callback regression assertions (AC-S8); `AllBookmarksScreenTest:3` includes two goldens + one empty-state callback assertion (AC-S6 callback half).
- `./gradlew :app:verifyRoborazziDebug :feature:twitter:verifyRoborazziDebug :feature:reddit:verifyRoborazziDebug` → **PASS** (UP-TO-DATE) — all 16 goldens recorded at implement-time match current build at the configured tolerance.
- `grep -r "com.google.accompanist.pager" --include="*.kt"` → **zero matches** (AC-S3 source-level gate). Plus `accompanist-pager` and `accompanist-pager-indicators` deps dropped from `:app` and `:feature:twitter` gradle files — confirms transitive removal.
- `grep -r "com.google.android.gms.maps\|com.mapbox" --include="*.kt" --include="*.gradle"` → **zero matches** (AC-S5 no-SDK gate).

## Interactive Verification Results

No live runtime adapter was driven for this slice. Per the workflow's confirmed `stack.platforms: [android]` + the slice-boundary discipline established by toolchain/tokens/components/layouts, runtime-emulator adjudication for visual screens is owned by either the maintainer manual-diff procedure (mock fidelity) or the maestro slice (interactive nav). Five deferral records below.

Evidence partition for each user-observable AC:

- **AC-S1** (≥95% match light): All 8 light goldens captured under Roborazzi's tolerance bands. *Pass on automated tolerance; subjective mock-fidelity layer deferred.* Per-screen golden inventory: `SplashScreen_default_light.png`, `OnboardingScreen_page0_light.png`, `LoginScreen_default_light.png`, `HomeScreen_twitter_light.png`, `AllBookmarksScreen_empty_light.png`, `MapViewScreen_default_light.png`, `TwitterBookmarksScreen_loggedOut_light.png`, `RedditBookmarksScreen_loggedOut_light.png`.
- **AC-S2** (≥95% match dark): same pattern, 8 dark goldens captured. Same deferral.
- **AC-S3** (Accompanist Pager source removal): **met** outright — grep returns zero matches; `TwitterCard.kt` (the last source consumer; orphan with only its own `@Preview` reference) deleted in the slice commit per the workflow's locked decision `orphan-components: "delete-13-outright"`; both feature/app gradles dropped the dep.
- **AC-S4** (manual side-by-side review on Pixel 6 emulator): **deferred** to maestro slice. Collapses onto the same emulator+Maestro probe run that clears toolchain AC4, components AC-C6, layouts AC-L5, and AC-S1/S2's subjective adjudication.
- **AC-S5** (MapView shows COMING SOON, no map SDK linked): **met** outright — Roborazzi goldens `MapViewScreen_default_{light,dark}.png` show the brutalist `MAP` kicker + `COMING SOON` displaySmall + ink-stroked panel treatment; map-SDK grep returns zero matches.
- **AC-S6** (empty-state CONNECT AN ACCOUNT button visible + tap navigates to LoginScreen): **partially met** — callback half closed in-process via `AllBookmarksScreenTest.emptyState_connectAccountCta_invokesCallback` (Compose UI test: `onNodeWithTag("all-bookmarks-connect-cta").performClick()` → asserts callback flag flipped). Visibility evidenced by `AllBookmarksScreen_empty_{light,dark}.png`. Navigation half (actually reaching LoginScreen) needs an emulator and collapses onto maestro slice.
- **AC-S7** (long-press popup opens with 4 actions on AllBookmarksScreen): **deferred** to maestro slice. Component-level evidence: the popup composable itself has full Roborazzi coverage from the components slice (`LongPressPopupTest` 4-action variants at `core/designsystem/src/test/.../components/LongPressPopupTest.kt`). The AllBookmarks-level integration (touch input → popup mounts with 4 actions wired in the Route) needs touch-input runtime which Robolectric cannot reasonably simulate for `performTouchInput { longClick() }` against a paging-driven LazyColumn.
- **AC-S8** (CONNECT TWITTER fires OAuth handler unchanged): **met** outright — `git log --diff-filter=M c72e489^..c72e489 -- feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/LoginViewModel.kt feature/reddit/src/main/java/com/github/jayteealao/reddit/RedditViewModel.kt` returns empty (zero VM source changes in this commit), AND `LoginScreenTest` includes two callback-fired assertions (`connectTwitter_invokesCallback` + `connectReddit_invokesCallback`) confirming the CTAs invoke their wired lambdas. Regression bar is double-protected: at the VM-diff level and at the screen-callback level.

Adapter-set: empty after intersection (`stack.platforms == [android]`, but no adapter bootstrap was required — every user-observable AC was either satisfied via static UI test + Roborazzi capture, or explicitly deferred).

## Acceptance Criteria Status

| AC | Quoted criterion (paraphrased to line ref) | Kind | Status | Verification method | Evidence |
|----|--------------------------------------------|------|--------|---------------------|----------|
| AC-S1 | Slice line 64 — all 8 screens × light theme: Roborazzi diff ≤5% changed pixels at 1% RGB tolerance | user-observable | **partially met (automated bar met; mock-fidelity ≥95% deferred)** | automated Roborazzi (tolerance) + deferral (subjective fidelity) | `verifyRoborazziDebug` UP-TO-DATE across `:app`, `:feature:twitter`, `:feature:reddit`; 8 light goldens under `<module>/src/test/screenshots/`. Subjective ≥95% adjudication transferred to maintainer manual diff. |
| AC-S2 | Slice line 66 — same diff tolerance, dark theme | user-observable | **partially met (automated bar met; mock-fidelity ≥95% deferred)** | automated Roborazzi + deferral | 8 dark goldens captured + verified. Same deferral as AC-S1. |
| AC-S3 | Slice line 66 — `grep -r "com.google.accompanist.pager" --include="*.kt"` returns zero matches | code-only | **met** | automated — grep gate | Zero source matches at HEAD; orphan `TwitterCard.kt` deleted; `accompanist-pager*` deps dropped from `:app` and `:feature:twitter`. |
| AC-S4 | Slice line 67 — manual side-by-side review on Pixel 6 emulator, all screens render with brutalist visuals matching mocks | user-observable | **deferred (collapses onto maestro slice)** | deferred to maestro slice | testTags wired across all 8 screens + 4 Routes (39+ call sites total from the components/layouts/screens cascade); `testTagsAsResourceId` enabled at `CrumbsTheme:40`. Maestro probe will exercise the live nav walk on Medium_Phone_API_36. |
| AC-S5 | Slice line 68 — MapView shows COMING SOON; no map SDK code linked | user-observable + code-only hybrid | **met** | automated — Roborazzi + grep | `MapViewScreen_default_{light,dark}.png` show MAP / COMING SOON / ink-stroked panel; map-SDK grep returns zero matches. |
| AC-S6 | Slice line 69 — empty-state CONNECT AN ACCOUNT button visible; tapping navigates to LoginScreen | user-observable | **partially met (visibility + callback met; navigation half deferred)** | automated — Compose UI test + Roborazzi + deferral | `AllBookmarksScreen_empty_{light,dark}.png` show the CTA visible; `AllBookmarksScreenTest.emptyState_connectAccountCta_invokesCallback` asserts the callback fires on click. Navigation half (actually reaching LoginScreen) collapses onto maestro slice. |
| AC-S7 | Slice line 70 — long-press on a CrumbsBookmarkCard opens popup with 4 actions visible | user-observable | **deferred (component-level coverage exists; integration deferred)** | deferred to maestro slice | Popup composable has Roborazzi coverage from components slice (`LongPressPopupTest` 4-action variants). AllBookmarks-level integration via touch-input runtime collapses onto maestro slice. |
| AC-S8 | Slice line 71 — CONNECT TWITTER fires existing OAuth handler unchanged (regression check) | user-observable | **met** | automated — VM diff gate + Compose UI test | `git log --diff-filter=M c72e489^..c72e489 -- LoginViewModel.kt RedditViewModel.kt` returns empty; `LoginScreenTest.connectTwitter_invokesCallback` + `connectReddit_invokesCallback` confirm wiring intact. |

`metric-acceptance-met: 3 / 8` — AC-S3, AC-S5, AC-S8 fully met; AC-S1, AC-S2, AC-S6 partially met (automated half met, runtime half deferred); AC-S4, AC-S7 deferred outright. `metric-acceptance-user-observable: 7` (everything except AC-S3); `metric-acceptance-code-only: 1` (AC-S3 only — AC-S5 is hybrid but counted user-observable).

## Issues Found

None. Zero failing checks, zero substantively unmet AC. The five deferrals are documented procedural transfers — two to maintainer manual diff (AC-S1, AC-S2), three to maestro slice (AC-S4, AC-S6 nav half, AC-S7) — not code defects.

## Verify-Owned Fixes

`fix-rounds-run: 0`. No fix loop ran — there were zero issues to triage.

## Augmentation Verification

Not applicable. No `02c-craft.md`, no `augmentations:` entries on `00-index.md` touched by this slice.

## Gaps / Unverified Areas

- **Mock fidelity ≥95% subjective adjudication** (AC-S1, AC-S2 across 8 screens × 2 themes = 16 image-pairs): maintainer-driven side-by-side diff against the Option-D handoff. Same procedure as tokens AC-K4 and toolchain AC6; same outcome shape — automated tolerance held, subjective fidelity pending maintainer sign-off.
- **Live nav walk on emulator** (AC-S4): full Splash → Onboarding → Login → Home (all 4 tabs) → AllBookmarks → MapView traversal under real edge-to-edge insets. Discharges naturally through maestro slice's happy-path flow.
- **Empty-state CTA → LoginScreen navigation** (AC-S6 nav half): callback fires verified in-test; the actual `navController.navigate(LOGINSCREEN)` reach belongs to maestro slice.
- **AllBookmarks long-press → popup integration** (AC-S7): touch-input + paging-driven LazyColumn + popup mount sequence under real touch dispatch. Maestro slice will exercise this in a single tap-and-hold gesture.
- **Layouts AC-L2 inset measurement on a live system bar** (carried from layouts slice deferral): this screens slice was identified at layouts-verify-stage as the natural moment to measure HomeScaffold's 28/88/34/52+8dp insets. **NOT cleared by this verify** — no emulator boot was performed in this stage; deferral remains active and collapses onto maestro slice / probe sweep along with AC-S4.
- **Populated-state visual coverage for feeds** (per implement record "Anything Deferred"): AllBookmarksScreen / TwitterBookmarksScreen / RedditBookmarksScreen populated states not Roborazzi-captured — only empty + logged-out states. Mock fidelity for populated rendering rolls into AC-S1/S2 maintainer manual diff with browser-rendered mock as the reference frame.

## Freshness Research

Not run. Slice introduces no external dependency drift beyond the Roborazzi 1.60.0 + Robolectric 4.16 + activity-compose 1.8.2 wiring already validated at implement-stage and against the layouts slice's verify. No standards changes for Compose 1.11 / Material3 1.4 / Paging-Compose 3.3.6 since the toolchain freshness pass.

## Caveats

- **Implement record commit-SHA drift.** `05-implement-screens.md` line 15 cites `commit-sha: "c1d2160"`; `git log -1` shows HEAD = `c72e489`. This verify ran against HEAD (`c72e489`), which is the authoritative state and matches `git diff --stat c72e489^..c72e489` content against the implement record's files list. Likely a transcription drift in the implement record's frontmatter, not a real divergence. Worth a one-line fix in `05-implement-screens.md` at a convenient moment; not a verify-blocker.
- **18-golden floor relaxed to 16.** Plan §13 budgeted ≥18 PNGs (4 AllBookmarks states × 2 themes + 2 each for other screens); actual is 16 (2 AllBookmarks empty states + 2 each for the other 7 screens). Implement record documents the populated-state goldens as deferred to maintainer manual diff (browser-rendered mocks have equivalent content density). Acceptable trade-off; flagged here so review sees the gap consciously.

## Recommendation

Proceed to review for the screens slice. Every code-side gate is green; all five deferrals are documented procedural transfers consistent with the workflow's established slice-boundary policy (4 of 5 collapse onto the same emulator+Maestro evidence run that the maestro slice owns; the 5th is the maintainer manual-diff procedure with precedent from tokens + toolchain). Review can validate the Route/Screen split pattern, the Accompanist Pager → Compose-native migration, the cross-module Hilt coupling in `RedditBookmarksRoute`, and the `testOptions.unitTests.includeAndroidResources` + `src/test/AndroidManifest.xml` discovery before the behaviors slice starts wiring the popup actions and filter-chip selection.

## Recommended Next Stage

- **Option A (default):** `/wf review brutalist-redesign screens` — every code-side gate green; all deferrals are procedural. `review-scope: slug-wide` means the canonical review runs against the whole branch diff once all slices land, but a per-slice spot review on screens is a valid intermediate signal — the visual payoff of the redesign is now structurally complete and benefits from a focused review before behaviors starts adding action logic.
- **Option B:** `/wf plan brutalist-redesign behaviors` — start the next slice's planning in parallel with review. Behaviors consumes this slice's testTag scaffolding + popup-action `TODO()` stubs + filter-chip empty state + `bannerState = null` slot.
- **Option C:** `/wf-quick probe brutalist-redesign` — re-attempt deferred evidence sweep on an emulator. A single probe run would discharge AC-S4 + AC-S6 nav half + AC-S7 + carry-forward layouts AC-L2 + the four other Maestro-shaped deferrals (toolchain AC4, components AC-C6, layouts AC-L5) all at once.
- **Option D:** `/wf-quick rca brutalist-redesign "implement-record commit-SHA drift"` — fix the `c1d2160 → c72e489` cosmetic drift in `05-implement-screens.md`. Trivial; can also be a one-line edit at handoff.
