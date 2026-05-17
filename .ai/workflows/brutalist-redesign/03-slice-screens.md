---
schema: sdlc/v1
type: slice
slug: brutalist-redesign
slice-slug: screens
status: verified-partial
stage-number: 3
created-at: "2026-05-16T22:37:59Z"
updated-at: "2026-05-17T19:13:28Z"
complexity: l
depends-on: [layouts]
tags: [screens, brutalist, pager-migration]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
  siblings:
    - 03-slice-toolchain.md
    - 03-slice-tokens.md
    - 03-slice-components.md
    - 03-slice-layouts.md
    - 03-slice-behaviors.md
    - 03-slice-maestro.md
  plan: 04-plan-screens.md
  implement: 05-implement-screens.md
---

# Slice: Screen rewrites

## Goal

Rewrite every user-visible screen to compose the new layout shells and brutalist components, hitting ≥95% visual fidelity against the Option D mocks at Pixel 6 (411×891) on both light and dark themes. Migrate `OnboardingScreen` off Accompanist Pager onto `androidx.compose.foundation.pager.HorizontalPager`. `MapViewScreen` becomes a brutalist "COMING SOON" placeholder. **Behaviors stay stubbed** — long-press handlers are no-ops, filter chips don't actually filter, soft-delete is wired to a TODO — behaviors land in the next slice.

## Why This Slice Exists

This is the visible payoff of the redesign. With tokens + components + layouts already brutalist, screens become pure composition; this isolates "did I assemble the screen correctly?" from "did the underlying primitives render correctly?" (already proven) and from "did the behavior wire up?" (next slice).

## Scope

**In: 6 app screens.**
- `SplashScreen.kt` — replace with wordmark `crumbs•` centered, ≤1s nav timer unchanged.
- `OnboardingScreen.kt` — compose `OnboardingShell` with 4 brutalist pages; migrate `com.google.accompanist.pager.HorizontalPager` → `androidx.compose.foundation.pager.HorizontalPager` (lambda-form `pageCount = { 4 }`).
- `LoginScreen.kt` (under `app/screens/login/`) — compose `OnboardingShell`-style or simpler full-bleed; UserProfileDisplay per provider; CONNECT-TWITTER / CONNECT-REDDIT buttons wired to existing OAuth handlers (handlers untouched).
- `HomeScreen.kt` — compose `HomeScaffold`; tab dispatch table to `TwitterBookmarksScreen` / `RedditBookmarksScreen` / `AllBookmarksScreen` / `MapViewScreen` unchanged. Search affordance preserved.
- `AllBookmarksScreen.kt` — compose `HomeScaffold`'s content slot with a LazyColumn of `CrumbsBookmarkCard`s; empty state uses the rebuilt `EmptyState` with "NO CRUMBS YET" + CONNECT AN ACCOUNT CTA that navigates to `LoginScreen`. Long-press handlers wire to `CrumbsLongPressPopup` but actions are TODO placeholders (logged via Timber).
- `MapViewScreen.kt` — brutalist full-tab empty-state: kicker "MAP", displaySmall "COMING SOON", ink-stroked panel. No maps SDK.

**In: 2 feature-module screens.**
- `feature/twitter/.../TwitterBookmarksScreen.kt` — compose the same brutalist feed pattern as `AllBookmarksScreen` but filtered to Twitter source. Wired to existing `BookmarksViewModel` paging source.
- `feature/reddit/.../RedditBookmarksScreen.kt` — same pattern, Reddit source. Wired to existing `RedditViewModel`.

**In: Roborazzi goldens.**
- Add Roborazzi to `app` module (or run the goldens from a new `core/screens-test` module — plan-stage decision).
- One golden per screen × 2 themes = **12 minimum**. More if states diverge meaningfully (e.g. AllBookmarks empty vs populated vs error).

**Out:**
- Wiring the long-press popup actions (Open, Share, Edit tags, Delete) to real handlers — handled by `behaviors` slice.
- Wiring filter chip selection to a paging source predicate — handled by `behaviors` slice.
- Soft-delete + tombstone — handled by `behaviors` slice.
- Sync-error banner trigger logic — handled by `behaviors` slice. (Screen renders the banner slot but `bannerState` is hard-coded `null`.)
- Maestro flows — handled by `maestro` slice.

## Acceptance Criteria

- **Given** each of the 8 screens (6 app + 2 feature), **when** Roborazzi captures it at Pixel 6 (411×891) light theme, **then** the diff against the corresponding Option D mock is ≤5% changed pixels at 1% RGB tolerance. *(automated)*
- **Given** each of the 8 screens, **when** captured dark theme, **then** same diff tolerance. *(automated)*
- **Given** `OnboardingScreen`, **when** the dev runs `grep -r "com.google.accompanist.pager" --include="*.kt"`, **then** zero matches (Accompanist Pager fully removed from the codebase). *(automated)*
- **Given** the app installed on a Pixel 6 emulator via `android` CLI, **when** the user navigates through Splash → Onboarding → Login → Home (all 4 tabs) → AllBookmarks → MapView, **then** every screen renders with brutalist visuals matching the mocks (manual side-by-side review). *(interactive — manual visual)*
- **Given** the `MapViewScreen`, **when** opened, **then** it shows "COMING SOON" — no map SDK code is linked. *(manual)*
- **Given** the `AllBookmarksScreen` empty state, **when** the user has no synced bookmarks, **then** the CONNECT AN ACCOUNT button is visible and tapping it navigates to `LoginScreen`. *(interactive)*
- **Given** a long-press on a `CrumbsBookmarkCard` in `AllBookmarksScreen`, **when** held, **then** `CrumbsLongPressPopup` opens with 4 actions visible. Tapping each action logs via Timber but does not yet perform the action. *(interactive — manual)*
- **Given** OAuth flows from `LoginScreen`, **when** the user taps CONNECT TWITTER, **then** the existing OAuth handler fires unchanged. Regression check; verify with intercepted callback. *(interactive)*

## Dependencies on Other Slices

- **`layouts`**: every screen composes `HomeScaffold` / `OverlayShell` / `OnboardingShell`.
- **`components`**: every screen consumes the brutalist component set including the 4 new ones.
- **`tokens`**: implicit, via components.
- **`toolchain`**: implicit.

## Risks

- **Slice size**: 8 screens × 2 themes = 16+ goldens minimum, plus 8 sets of layout/composition logic. This is the largest single slice by file count. **Re-split candidate**: if plan-stage estimates >2 days of focused work, split into `screens-feed` (Home, AllBookmarks, TwitterBookmarks, RedditBookmarks) and `screens-onboarding` (Splash, Onboarding, Login, MapView). Slicing decision is reversible until plan stage commits.
- **Feature-module screen rewrites**: `TwitterBookmarksScreen` and `RedditBookmarksScreen` live in `feature/twitter` and `feature/reddit` modules whose ViewModels are intake non-goals to touch. Mitigation: confirm the screens only compose UI from view-state Flows — no ViewModel changes — before starting. If a ViewModel exposes the wrong shape for the new UI, surface as plan-stage open question.
- **Compose-native Pager API differences**: `rememberPagerState(pageCount = { N })` is lambda-form, `currentPage`/`scrollToPage` calls are slightly different. Mitigation: smoke-test `OnboardingScreen` early in this slice; the API shift is documented and small.
- **Search bar regression in `CrumbsTopBar`**: existing expanding-search affordance is custom and may not survive a faithful brutalist rebuild. Mitigation: explicit AC-L3 from shape (expanding search functions identically) protects this; component slice has already rebuilt it.
- **`MaterialTheme.colorScheme` references inside feature modules**: feature modules may still import Material3 directly. Mitigation: grep `feature/twitter/` and `feature/reddit/` for `MaterialTheme.colorScheme` and replace with `CrumbsTheme.colors.*` during this slice.
