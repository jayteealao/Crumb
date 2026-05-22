---
schema: sdlc/v1
type: index
slug: hotfix-filter-bar-overflow
workflow-type: hotfix
title: "Filter chip row overflows + wraps labels mid-word"
status: complete
current-stage: ship
stage-number: 6
created-at: "2026-05-23T00:30:00Z"
updated-at: "2026-05-23T00:30:00Z"
branch: "feat/brutalist-redesign"
base-branch: "feat/brutalist-redesign"
production-branch-deviation: "Bug only exists on feat/brutalist-redesign (brutalist redesign not yet on main). Per-user-direction the fix lands on the feature branch directly; no hotfix/<slug> sub-branch."
workflow-files:
  - 00-index.md
  - hf-brief.md
  - hf-plan.md
  - hf-implement.md
  - hf-verify.md
recommended-next-command: wf-ship
recommended-next-invocation: "/wf ship hotfix-filter-bar-overflow"
tags: [hotfix, designsystem, compose, brutalist, filter-bar, horizontal-scroll]
---

# Hotfix: filter-bar-overflow

User-observed visual defect on the home filter chip row: the live 6-chip list (`ALL/ARTICLES/VIDEOS/IMAGES/THREADS/TEXT`) overflows the screen width, squishes each chip, and wraps the `THREADS` label mid-word (`THR` / `EAD`). Sort label (`RECENT`) is partially clipped behind the wrapped chip.

Diagnosed in `CrumbsFilterBar.kt:93-100` — inner chip Row uses `Modifier.weight(1f)` with no horizontal scroll, and each chip carries `minimumInteractiveComponentSize()` (48dp min, added for a11y in `790bdba`). On a 411dp Pixel width, six 48dp+ chips + count cell + sort cell + spacing + dividers do not fit. The chip Text was also missing `maxLines = 1` / `softWrap = false`, so the constrained width forced mid-word wrapping.

Fix applied: two-layer mitigation in one file.
1. Inner chip Row gets `.horizontalScroll(rememberScrollState())` — chips render at natural width and scroll horizontally past the screen edge.
2. Chip `Text` gets `maxLines = 1` / `softWrap = false` — defensive against future width-constraint surprises.

One file, +4 lines, no new dependencies (`androidx.compose.foundation` was already on the classpath).
