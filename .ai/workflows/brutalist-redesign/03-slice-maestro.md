---
schema: sdlc/v1
type: slice
slug: brutalist-redesign
slice-slug: maestro
status: implemented
stage-number: 3
created-at: "2026-05-16T22:37:59Z"
updated-at: "2026-05-16T22:37:59Z"
complexity: s
depends-on: [behaviors]
tags: [maestro, e2e, testing, lazylogcat, android-cli]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
  siblings:
    - 03-slice-toolchain.md
    - 03-slice-tokens.md
    - 03-slice-components.md
    - 03-slice-layouts.md
    - 03-slice-screens.md
    - 03-slice-behaviors.md
  plan: 04-plan-maestro.md
  implement: 05-implement-maestro.md
---

# Slice: End-to-end Maestro coverage

## Goal

Add four Maestro `.yaml` flows covering the redesigned app's happy path and three failure-mode behaviors, plus a debug-only fake-data injector that seeds the DB so flows run deterministically. Wire the `android` CLI for emulator orchestration and `lazylogcat` for log capture during runs. This is the final slice before review/handoff.

## Why This Slice Exists

Maestro flows depend on every UI surface being final — testTags stable, behaviors wired, copy locked. Splitting Maestro into its own slice avoids the churn of rewriting `.yaml` files every time a screen changes during the screens slice. It also creates a clean "behavioral safety net" slice that's easy to extend post-ship.

## Scope

**In: Maestro flows under `maestro/` at repo root.**
- `happy_path.yaml`: launch (cold-start) → Splash auto-nav → Login (skip if access tokens present) → Home/Twitter tab → Home/Reddit tab → Home/All tab → Home/Map tab (verify "COMING SOON") → back to All → tap long-press on first card → tap Open → return → tap long-press → tap Share (dismiss share sheet) → tap long-press → tap Delete → see snackbar → tap UNDO → verify card restored → tap each filter chip and verify state.
- `long_press.yaml`: focused flow on the `CrumbsLongPressPopup` — exercise all 4 actions, verify popup dismiss on backdrop tap.
- `filter_overlay.yaml`: Type single-select instant filter, Tags multi-select via overlay, Collection multi-select via overlay, APPLY commits, backdrop tap cancels.
- `sync_error.yaml`: force Twitter 401 via the debug-only API interceptor → verify banner appears within 1s → tap banner CTA → verify OAuth flow initiates → restore valid token → verify banner clears on next sync.

**In: debug-only fake-data injector.**
- New `app/src/debug/java/.../DebugDataInjector.kt` activatable via a `debugImplementation` dependency or a debug-only entry point in `Crumbs.kt`.
- Seeds the Room DB with 8 fake bookmarks (4 Twitter, 4 Reddit) plus 3 fake tags and 2 fake collections, so flows have deterministic content to interact with.
- Resets state between flow runs (`onCreate` clears + re-seeds when a debug intent flag is set).
- Release builds: the injector source set is debug-only — release variant cannot link against it.

**In: Maestro + tooling integration.**
- New shell script `scripts/run-maestro.sh` (cross-platform via bash; PowerShell sibling `scripts/run-maestro.ps1` for Windows-first dev): orchestrates `android avd start Pixel_6_API_34` (via `android-cli` skill) → install debug build → `lazylogcat start -t crumbs` background capture → `maestro test maestro/` → `lazylogcat dump` to `build/maestro-logs/<timestamp>.log` → emulator stop.
- Document the script + manual fallback in `README.md` (covered by the docs plan; this slice updates the README section only).

**In: CI integration (optional, surfaced as plan-stage choice).**
- Add a GitHub Actions workflow that runs Maestro flows on the same Pixel 6 emulator profile on every PR. If the maintainer prefers manual-only invocation, drop this and document `scripts/run-maestro.sh` as the canonical entry point.

**Out:**
- Any UI / behavior changes — Maestro is non-invasive; if a flow can't pass, the bug lives in another slice and triggers a return loop (per workflow contract, would surface in `/wf verify`).
- Roborazzi golden additions — those are owned by their originating slice.
- Functional Map view (still placeholder).

## Acceptance Criteria

- **Given** the four `.yaml` flows in `maestro/`, **when** the dev runs `scripts/run-maestro.sh` (or `maestro test maestro/` on a pre-prepared emulator), **then** all four flows pass with zero assertion failures. *(automated within the script; interactive in that an emulator must be running)*
- **Given** the debug-only data injector, **when** the debug app is launched with the seeding intent, **then** the DB shows 8 bookmarks, 3 tags, 2 collections. *(automated — instrumentation test)*
- **Given** a release variant assembly attempt, **when** `./gradlew :app:assembleRelease`, **then** the build succeeds and the release APK does not contain `DebugDataInjector` (verified via `aapt dump --values resources` or class scan). *(automated)*
- **Given** the Maestro run finishes, **when** the dev inspects `build/maestro-logs/<timestamp>.log`, **then** zero ERROR-level entries appear from the app process during the happy-path run (theming, layout, or rendering errors specifically — sync-error.yaml is allowed to log expected 401s). *(manual review of log file)*
- **Given** `sync_error.yaml`, **when** the flow forces a 401, **then** the banner is present in the emulator screenshot Maestro captures at the assertion point. *(automated)*

## Dependencies on Other Slices

- **`behaviors`**: every flow exercises behaviors wired in the previous slice — long-press menu, filter chips, snackbar undo, banner.
- **`screens`**: testTags on every navigated screen must be stable.
- **`components`**: testTags on every queried component must be stable.

## Risks

- **Flakiness on cold-start timing**: Splash auto-nav uses a 1000ms delay; Maestro's default polling interval may race the navigation. Mitigation: explicit `extendedWaitUntil` on the Login screen indicator or on `home-scaffold` testTag with a 5s timeout.
- **OAuth screen in `happy_path.yaml`**: if the access token is *not* already cached, the flow would have to drive a real OAuth browser flow. Mitigation: the debug-only injector seeds tokens too (test-mode tokens that satisfy the `isAccessTokenAvailable` check; sync calls are intercepted to return fake data, so no real API call escapes).
- **Cross-platform script**: bash + PowerShell duplication. Mitigation: keep both scripts thin (delegate to `maestro` CLI and `android` CLI which are cross-platform); test on Windows since that's the user's primary dev environment.
- **`lazylogcat` filter accuracy**: tag-based filtering may miss native crashes. Mitigation: capture unfiltered alongside filtered; log review checks both.
- **CI emulator cost / time**: a full Maestro run takes ~3–5 minutes per emulator boot. Mitigation: surface CI integration as an explicit opt-in during plan stage; default to local-only.
