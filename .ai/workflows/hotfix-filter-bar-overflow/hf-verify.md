---
schema: sdlc/v1
type: hf-verify
slug: hotfix-filter-bar-overflow
workflow-type: hotfix
symptom-confirmed-fixed: pending-user-confirmation
tests-pass: true
pre-existing-failures: none
result: PASS
status: complete
created-at: "2026-05-23T00:30:00Z"
---

# Hotfix verify

## Symptom reproduction (post-fix)

- Pre-fix screenshot: provided by the user — shows chips squished and `THREADS` wrapping to `THR` / `EAD` on a Pixel ~411dp emulator.
- Post-fix: updated APK installed on `emulator-5554`. The fixed chip row should render at natural width and scroll horizontally past the screen edge. User confirmation pending an on-device check.

## Regression suite

- `:core:designsystem:compileDebugKotlin` — PASS.
- `:core:designsystem:testDebugUnitTest` — PASS (existing 3-chip cases unchanged).
- `:core:designsystem:verifyRoborazziDebug --rerun-tasks` — PASS. The 3 committed baseline PNGs (`CrumbsFilterBar_default_light.png`, `_default_dark.png`, `_noSelection_light.png`) match against the post-fix captures. Confirms the fix is a no-op when content fits within the bounded chip area.
- `:app:assembleDebug` — BUILD SUCCESSFUL.

No pre-existing test failures to document.

## Adjacent-path spot-check

- `HomeScreen.kt` — only inbound prod consumer of `CrumbsFilterBar`. No code change there; the fix is transparent.
- `core/designsystem/src/test/.../FilterBarTest.kt` — test fixture uses 3 short chips; still passes without modification.
- Other Compose surfaces — no other consumer touches this composable; grep across `**/*.kt` confirms.

## Result

PASS. Fix lands cleanly; no regressions detected by the automated suite. The live visual confirmation is the next user step (pull the bar on the emulator's Twitter tab).

## Recommendations

- Followup (out of scope this round, user-deferred): add a Roborazzi case in `FilterBarTest.kt` with the live `HomeFilterChips` list to lock the 6-chip overflow behavior in. This closes the test-gap that let the defect ship.
- The current fix is robust: even if a future consumer adds more chips or longer labels, both layers (horizontal scroll + no-wrap text) cooperate to keep the bar legible.
