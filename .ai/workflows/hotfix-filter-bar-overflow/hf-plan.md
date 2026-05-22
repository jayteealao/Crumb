---
schema: sdlc/v1
type: hf-plan
slug: hotfix-filter-bar-overflow
workflow-type: hotfix
root-cause-file: "core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsFilterBar.kt"
root-cause-line: 93
step-count: 3
rollback: "git revert <hotfix-sha> on feat/brutalist-redesign — single-file revert; no schema/data/runtime side effects."
data-remediation-needed: false
status: complete
created-at: "2026-05-23T00:30:00Z"
---

# Hotfix plan

Three-step minimal change, single file.

## Step 1 — Add foundation imports

Add two imports to `CrumbsFilterBar.kt`:

- `androidx.compose.foundation.horizontalScroll`
- `androidx.compose.foundation.rememberScrollState`

Both are already on the classpath (the file already imports from `androidx.compose.foundation.background` etc.).

## Step 2 — Wrap inner chip Row with horizontalScroll

In the inner `Row` at lines 93-100, after `.fillMaxHeight()` and before `.padding(horizontal = 6.dp)`, add `.horizontalScroll(rememberScrollState())`. This lets the chips measure at their natural width and overflow horizontally with scroll, instead of being squished by the bounded `weight(1f)` width.

## Step 3 — Constrain chip Text to a single line

In the chip `Text` composable at lines 126-130, add `maxLines = 1` and `softWrap = false`. Defensive measure: even if a future caller passes constraints that re-trigger width pressure, labels will never wrap mid-word.

## Rollback

`git revert <hotfix-sha>` on `feat/brutalist-redesign`. Single file changed; no schema migrations, no data writes, no runtime side effects. Revert is instantaneous.

## Tripwires

- Files touched: 1 — within ≤3 limit.
- Lines added: 4 — well within ≤50 limit.
- Architectural change: none.

## Scope boundary

Do NOT:
- Add a Roborazzi test for the 6-chip overflow case (deferred per user; would close the test-gap that let this ship but expands hotfix scope).
- Refactor `HomeFilterChips` count or labels.
- Touch `HomeScreen.kt` consumer code.
- Migrate `Row` to `LazyRow` or change to a different layout primitive.

## User confirmation

User confirmed fix shape via AskUserQuestion: "horizontalScroll + maxLines=1 + softWrap=false (Recommended)". User also confirmed branch behavior: "Land on feat/brutalist-redesign directly" — no `hotfix/<slug>` sub-branch.
