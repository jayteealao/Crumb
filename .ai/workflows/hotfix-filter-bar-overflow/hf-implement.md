---
schema: sdlc/v1
type: hf-implement
slug: hotfix-filter-bar-overflow
workflow-type: hotfix
files-changed:
  - "core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsFilterBar.kt"
lines-changed: 9   # 8 inserted, 1 deleted per git diff --stat (net +7 lines: 2 imports + 1 modifier + 2 Text params + 4 comment lines)
commit-sha: "9d3dcbf"
test-result: pass
status: complete
created-at: "2026-05-23T00:30:00Z"
---

# Hotfix implement

## Files changed

`core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsFilterBar.kt` — single file, 9 lines net.

### Diff summary

1. **Imports added** (lines 7, 16):
   - `import androidx.compose.foundation.horizontalScroll`
   - `import androidx.compose.foundation.rememberScrollState`

2. **Inner chip Row gains horizontal scroll** (line ~97):
   ```kotlin
   Row(
       modifier = Modifier
           .weight(1f)
           .fillMaxHeight()
           .horizontalScroll(rememberScrollState())   // NEW
           .padding(horizontal = 6.dp),
       ...
   )
   ```
   Inline comment added explaining the rationale (lets long chip sets extend past the screen edge instead of squishing chips and wrapping labels).

3. **Chip Text gains no-wrap constraints** (line ~131):
   ```kotlin
   Text(
       text = chip.label.uppercase(),
       style = typography.metaMono,
       color = if (selected) colors.onAccent else colors.ink,
       maxLines = 1,        // NEW
       softWrap = false,    // NEW
   )
   ```

## What was intentionally NOT changed

- `HomeScreen.kt` / `HomeFilterChips` list — consumer code is correct; the defect is in the bar.
- `FilterBarTest.kt` — no new Roborazzi test added (per user direction; the existing 3-chip baselines stay unchanged and `--rerun-tasks` confirms zero pixel drift).
- Other `CrumbsFilterBar` callers — none exist outside `HomeScreen.kt` and `FilterBarTest.kt`.
- Other Compose components — no propagation of the scroll pattern elsewhere this round.

## Build + test results

- `:core:designsystem:compileDebugKotlin` — PASS (new imports resolve, no warnings).
- `:core:designsystem:testDebugUnitTest` — PASS (FilterBarTest's 3 cases still pass; Roborazzi captures still match committed baselines).
- `:core:designsystem:verifyRoborazziDebug` — PASS with `--rerun-tasks`. Existing baselines (`CrumbsFilterBar_default_light.png`, `_default_dark.png`, `_noSelection_light.png`) unchanged because the 3-chip fixture fits within the available width — `horizontalScroll` is invisible when content does not overflow.
- `:app:assembleDebug` — BUILD SUCCESSFUL in 35s. Rebuilt APK installed to `emulator-5554` via `adb install -r -d`.

## Atomic commit

`fix(designsystem): horizontal-scroll filter chip row + no-wrap labels` on `feat/brutalist-redesign`.
