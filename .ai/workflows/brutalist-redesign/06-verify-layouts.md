---
schema: sdlc/v1
type: verify
slug: brutalist-redesign
slice-slug: layouts
status: complete
stage-number: 6
created-at: "2026-05-17T16:05:31Z"
updated-at: "2026-05-17T16:05:31Z"
result: partial
metric-checks-run: 5
metric-checks-passed: 5
metric-acceptance-met: 3
metric-acceptance-total: 5
metric-acceptance-user-observable: 4
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
interactive-verification-defer-reason: "AC-L2 precise inset measurement (28dp status / 88dp TopBar / 34dp FilterBar / 52dp BottomNav + 8dp pill) deferred because Robolectric defaults WindowInsets to zero and the layouts slice ships shells only — no host screen has yet adopted HomeScaffold so the running-app evidence belongs to the screens slice. AC-L5 Maestro studio testTag round-trip deferred to the dedicated maestro slice (same pattern as toolchain AC4 + components AC-C6). Slot-composition correctness for AC-L2 and pill+CTA structural correctness for AC-L4 are both directly evidenced by the Roborazzi captures; AC-L3 backdrop dismiss is fully evidenced by the non-Roborazzi UI test."
adapters-used: []
bootstrap-failures: []
evidence-dir: ".ai/workflows/brutalist-redesign/verify-evidence/layouts/"
stack-source: confirmed
tags: [layouts, brutalist, designsystem, roborazzi, runtime-evidence-deferral]
refs:
  index: 00-index.md
  verify-index: 06-verify.md
  slice-def: 03-slice-layouts.md
  plan: 04-plan-layouts.md
  implement: 05-implement-layouts.md
  review: 07-review-layouts.md
  adapters: ${CLAUDE_PLUGIN_ROOT}/skills/wf/reference/runtime-adapters.md
next-command: wf-review
next-invocation: "/wf review brutalist-redesign layouts"
---

# Verify: layouts

## Verification Summary

The three layout shells (`HomeScaffold`, `OverlayShell`, `OnboardingShell`) land cleanly. Every automated check is green. AC-L1 file existence and AC-L3 backdrop dismiss are met outright. AC-L2 and AC-L4 are met *at the slot-composition and structural level* via Roborazzi captures; the inset-pixel-measurement subset of AC-L2 cannot be observed under Robolectric (zero default insets) and is transferred to the screens slice, which is where a real host screen first consumes `HomeScaffold` against a live status bar. AC-L5 Maestro testTag queryability defers to the maestro slice on the established pattern.

No fix loop was needed. `result: partial` is solely a consequence of two procedural deferrals; nothing is substantively unmet.

## Automated Checks Run

- `./gradlew :core:designsystem:testDebugUnitTest` → **PASS** — 3 new test classes, 7 tests, 0 failures: `HomeScaffoldTest` (2), `OverlayShellTest` (3 incl. `backdrop_tap_invokes_onDismiss`), `OnboardingShellTest` (2). Sibling component tests unaffected.
- `./gradlew :core:designsystem:verifyRoborazziDebug` → **PASS** (`UP-TO-DATE` — goldens recorded during implement match current build at the configured tolerance `roborazzi.compare.changeThreshold=0.05` + 1% per-pixel RGB).
- `./gradlew :app:assembleDebug` → **PASS** — full app links against the new layouts package + the bumped `activityCompose:1.8.2`.
- `./gradlew :core:designsystem:lintDebug` → **PASS** — no new lint findings introduced by the slice.
- `ls core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/layouts/` → 3 files present: `HomeScaffold.kt`, `OverlayShell.kt`, `OnboardingShell.kt`. AC-L1 gate clean.

## Interactive Verification Results

No live runtime adapter was driven for this slice. Evidence partitions as follows:

- **AC-L2** (HomeScaffold layout math): Roborazzi captures `HomeScaffold_default_{light,dark}.png` confirm the slot composition (topBar → filterBar → content → bottomBar, top-to-bottom). Precise inset gap measurement (28dp/88dp/34dp/52dp+8dp pill) is **deferred** — Robolectric's default `WindowInsets(0)` cannot expose status-bar/navigation-bar padding, and no consumer screen exists yet to validate the math against a live system bar. The screens slice owns the first running-app HomeScaffold instance.
- **AC-L3** (OverlayShell backdrop tap → onDismiss): **met** via `OverlayShellTest.backdrop_tap_invokes_onDismiss` (Compose UI test, non-Roborazzi). Test finds node by testTag `overlay-shell-backdrop`, performs click, asserts the captured `dismissed` flag flips to `true`. This satisfies the criterion's "Compose UI test asserting onClick invocation" testable proxy verbatim.
- **AC-L4** (OnboardingShell 3-pill indicator + right-aligned CTA): **met** via `OnboardingShell_page0_light.png` (and `OnboardingShell_page1_dark.png` for the active-page transition). Capture shows: 3 pills bottom-left, leftmost in accent `#FF5A1F` (current page = 0), the other two in ink at ~25% alpha; "NEXT" CTA right-aligned with brutalist black border + accent fill via `CrumbsButton(style = ButtonStyle.Primary)`. Page-1-dark capture confirms the accent moves to the middle pill on `initialPage = 1`.
- **AC-L5** (Maestro studio dry-run for all shell testTags): **deferred** to the maestro slice. Static evidence: `home-scaffold` + 3 slot tags in `HomeScaffold.kt`; `overlay-shell` + backdrop/header/body/apply tags in `OverlayShell.kt`; `onboarding-shell` + pager/footer/indicator tags in `OnboardingShell.kt`. `testTagsAsResourceId = true` already wired at `CrumbsTheme.kt:40`. Same procedural deferral pattern as toolchain AC4 and components AC-C6 — they collapse onto a single Maestro slice probe.

Adapter-set: empty after intersection (`stack.platforms == [android]`, but no adapter bootstrap was required — every user-observable AC was satisfied via static UI test, Roborazzi capture, or explicit deferral).

## Acceptance Criteria Status

| AC | Quoted criterion | Kind | Status | Verification method | Evidence |
|----|------------------|------|--------|---------------------|----------|
| AC-L1 | `HomeScaffold.kt`, `OverlayShell.kt`, `OnboardingShell.kt` exist under `core/designsystem/.../layouts/` | code-only | **met** | automated — file existence | All three files present at expected paths; commit `ef121f0`. |
| AC-L2 | HomeScaffold at Pixel 6 light: statusBar 28dp top, TopBar 88dp, FilterBar 34dp, content fills, BottomNav 52dp + 8dp pill | user-observable | **partially met (slot composition met; inset measurement deferred)** | automated Roborazzi (composition) + deferral (insets) | `HomeScaffold_default_light.png` + `_dark.png` confirm topBar → filterBar → content → bottomBar slot order top-to-bottom. Inset pixel measurement transferred to the screens slice — first real HomeScaffold consumer will measure against live system bars. |
| AC-L3 | OverlayShell backdrop tap fires onDismiss lambda (testable proxy: Compose UI test asserting onClick invocation) | user-observable | **met** | automated — Compose UI test | `OverlayShellTest.backdrop_tap_invokes_onDismiss` performs `performClick()` on `overlay-shell-backdrop`, asserts `dismissed == true`. Passes. |
| AC-L4 | OnboardingShell with three stub pages: indicator shows three pills, current page accent, footer CTA right-aligned | user-observable | **met** | automated — Roborazzi | `OnboardingShell_page0_light.png`: 3 pills bottom-left (accent on index 0, ink-α on 1 & 2), "NEXT" CTA right-aligned with brutalist border + accent fill. `OnboardingShell_page1_dark.png` confirms accent migrates to index 1 on `initialPage = 1`. |
| AC-L5 | Maestro studio queries all shell-level + slot-level testTags against running debug app | user-observable | **deferred (scaffolding met)** | deferred to maestro slice | All testTags wired in source: 4 on HomeScaffold, 5 on OverlayShell, 4 on OnboardingShell. `testTagsAsResourceId` enabled at `CrumbsTheme:40`. Live round-trip belongs to the maestro slice; collapses onto the same emulator+Maestro run that clears toolchain AC4 + components AC-C6. |

`metric-acceptance-met: 3 / 5` — three ACs fully met; one partially met (AC-L2 composition met, inset measurement deferred); one deferred (AC-L5).

## Issues Found

None. Zero failing checks, zero substantively unmet AC. The two deferrals are procedural transfers (inset measurement → screens slice; Maestro round-trip → maestro slice), not code defects.

## Verify-Owned Fixes

`fix-rounds-run: 0`. No fix loop ran — there were zero issues to triage.

## Augmentation Verification

Not applicable. No `02c-craft.md`, no `augmentations:` entries on `00-index.md` touched by this slice.

## Gaps / Unverified Areas

- **Inset math at runtime**: HomeScaffold's exact 28/88/34/52+8dp gaps will be measured once a consumer screen lands. The screens slice's verify is the first natural moment to capture this — its host screens render under live status bars on Medium_Phone_API_36.
- **Maestro testTag round-trip**: shell + slot tag queryability against the running app. Same emulator+Maestro pass discharges three deferrals at once.
- **Interim visual regression on un-migrated screens** (already noted in implement record): now that `enableEdgeToEdge()` is wired in `MainActivity`, any screen that has not yet adopted `HomeScaffold` may render with TopBar partially under the status bar. Not a regression of this slice — it is the by-design cutover gap that the screens slice will close. Worth keeping an eye on if anyone runs the app between slices.

## Freshness Research

Not run. Slice introduces no external dependency drift beyond the `activityCompose 1.6.1 → 1.8.2` bump that was already validated at implement-stage build/lint/test. No relevant standards changes for Compose 1.11 / Material3 1.4 since the toolchain slice's freshness pass.

## Recommendation

Proceed to review for the layouts slice. Every code-side gate is green; both deferrals are documented procedural transfers consistent with the workflow's slice-boundary policy. Review can validate the shell-architectural choices (slot signatures, in-tree modal vs Material3 ModalBottomSheet, Compose-native Pager from day one) before the screens slice starts composing against them.

## Recommended Next Stage

- **Option A (default):** `/wf review brutalist-redesign layouts` — every code-side gate green; both deferrals are procedural. `review-scope: slug-wide` means the canonical review runs against the whole branch diff once all slices land, but a per-slice spot review on layouts is still a valid intermediate signal — particularly given that screens will compose against these APIs next.
- **Option B:** `/wf plan brutalist-redesign screens` — start the next slice's planning. Screens is the largest remaining slice (complexity `l`) and is where the inset-measurement deferral from AC-L2 will be discharged on a host screen.
- **Option C:** `/wf-quick probe brutalist-redesign` — re-attempt deferred evidence sweep (Maestro round-trip + HomeScaffold inset measurement) once an emulator and Maestro CLI are both on PATH.
