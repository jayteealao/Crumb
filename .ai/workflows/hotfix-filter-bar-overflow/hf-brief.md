---
schema: sdlc/v1
type: hf-brief
slug: hotfix-filter-bar-overflow
workflow-type: hotfix
symptom: "Home filter chip row overflows screen width; chips squish and the THREADS label wraps mid-word (THR / EAD); sort label RECENT is partially obscured"
impact: medium
affected-scope: feature-branch
recent-changes: "Brutalist redesign on feat/brutalist-redesign — CrumbsFilterBar introduced in 39d4b6c; minimumInteractiveComponentSize (48dp) added per chip in 790bdba for TalkBack/touch-target compliance"
status: complete
created-at: "2026-05-23T00:30:00Z"
---

# Hotfix brief

## What is broken

The home filter chip row in the brutalist redesign renders incorrectly when the consumer passes more than ~3 chips with long labels. Observed on the Twitter tab: `ALL`, `ARTICLES`, `VIDEOS`, `IMAGES`, `THREADS`, `TEXT` — six chips, none fitting because each is forced to 48dp min plus padding plus text. Result on a Pixel ~411dp width: chips are squished, `THREADS` wraps to two lines (`THR` / `EAD`), and the sort cell (`RECENT`) is overlapped.

## Impact

Visual-only, pre-release. The brutalist redesign is in-flight on `feat/brutalist-redesign` and has not shipped to production yet. Fixing now prevents the defect from leaking into the eventual release.

## Recent changes

- `39d4b6c feat(design-system): rebuild components to brutalist contract` — introduced `CrumbsFilterBar` with the bounded `weight(1f)` chip Row.
- `790bdba fix(a11y): TalkBack semantics + 48dp touch targets on brutalist controls` — added `minimumInteractiveComponentSize()` per chip. This is the change that pushed total chip width above the screen width.

## Diagnosis

Root cause file: `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsFilterBar.kt`.
Root cause lines: 93-100 (inner chip Row, missing horizontalScroll) and 126-130 (chip Text, missing maxLines/softWrap).

Why automated tests didn't catch it: `FilterBarTest.kt` exercises a 3-chip fixture (`Text`/`Image`/`Link`) that fits within the bounded width. The live `HomeFilterChips` config in `HomeScreen.kt:34-41` was never put through Roborazzi. The 6-chip overflow path is not covered by the test suite — followup test addition was deferred per user direction.
