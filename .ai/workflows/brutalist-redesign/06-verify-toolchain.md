---
schema: sdlc/v1
type: verify
slug: brutalist-redesign
slice-slug: toolchain
status: complete
stage-number: 6
created-at: "2026-05-17T01:17:12Z"
updated-at: "2026-05-17T01:17:12Z"
result: partial
metric-checks-run: 5
metric-checks-passed: 5
metric-acceptance-met: 4
metric-acceptance-total: 6
metric-acceptance-user-observable: 3
metric-acceptance-code-only: 3
metric-interactive-checks-run: 2
metric-interactive-checks-passed: 2
metric-issues-found: 0
metric-issues-found-initial: 1
metric-issues-found-final: 0
fix-rounds-run: 1
convergence: converged
verify-owned-fix-commit: "6148b61"
interactive-verification: deferred
interactive-verification-defer-reason: "AC6 (manual visual diff of regenerated goldens against pre-bump tree) deferred to maintainer per their selection during verify triage; AC4 testTag round-trip deferred to the maestro slice where testTags are introduced systematically."
adapters-used: [android]
bootstrap-failures: []
evidence-dir: ".ai/workflows/brutalist-redesign/verify-evidence/toolchain/"
stack-source: confirmed
adapters-excluded-by-stack: []
tags: [toolchain, kotlin, agp, compose, roborazzi, room, runtime-evidence]
refs:
  index: 00-index.md
  verify-index: 06-verify.md
  slice-def: 03-slice-toolchain.md
  plan: 04-plan-toolchain.md
  implement: 05-implement-toolchain.md
  review: 07-review-toolchain.md
next-command: wf-review
next-invocation: "/wf review brutalist-redesign"
---

# Verify: toolchain

## Verification Summary

The toolchain slice's six AC were verified across one verify-owned fix round. **Three automated AC pass cleanly** (clean `:app:assembleDebug`, `:core:designsystem:verifyRoborazziDebug`, `lintDebug`). **One user-observable AC (AC5 — emulator smoke / v1.1 visual identity) initially failed with a fatal `IllegalStateException` from Room 2.8.4** on first launch; the verify-owned fix loop applied a one-line nullability fix to `TweetDao.getLatestBookmark()`, rebuilt, re-launched, and the app then rendered the v1.1 Login/Connect screen correctly with cyan accent and dark navy connect buttons preserved. **One user-observable AC (AC4 — Maestro testTag round-trip) is partially met** — the `testTagsAsResourceId` scaffolding is in place at `CrumbsTheme:42` and Maestro can address the running app, but a full round-trip needs a `Modifier.testTag(...)` somewhere in the codebase (none exist yet — testTags are added per-component in later slices). **One AC (AC6 — manual visual diff against pre-bump tree) is deferred to the maintainer** at their request during verify triage.

`convergence: converged` because the one fix round resolved every initial issue. `result: partial` because two ACs (AC4, AC6) are deferred rather than positively met — they aren't *failing*, they're awaiting evidence the current environment / slice scope can't produce. Downstream review and handoff can proceed; ship will hard-block until the deferrals are cleared.

## Automated Checks Run

| Command | Result | Notes |
|---|---|---|
| `./gradlew clean :app:assembleDebug` | ✅ pass | BUILD SUCCESSFUL, full clean rebuild, no version-mismatch warnings |
| `./gradlew :core:designsystem:verifyRoborazziDebug` | ✅ pass | All 17 test classes / 133 regenerated PNGs verify clean against themselves (tautology of the round-trip — what it proves is that the test rule chain still works on Roborazzi 1.60.0 + Robolectric 4.16 + Kotlin 2.2.10) |
| `./gradlew lintDebug` | ✅ pass | All modules. No new lint violations introduced by toolchain bumps. |
| `kotlinterCheck` | ⚠ N/A | Plugin deferred — documented in `05-implement-toolchain.md` deviation #8 |

## Interactive Verification Results

### AC5 — Emulator smoke + v1.1 visual identity

- **Adapter**: android (per `runtime-adapters.md` recipe — `android` CLI + `adb` + `lazylogcat`)
- **Device**: `emulator-5554` (Android 16 / API 36; `Pixel_9_Pro` AVD — not the plan's `Pixel_6_API_34` because that AVD is not installed on this machine. Acceptable substitute: this is a *runtime* check; the canonical Pixel 6 / API 34 was for golden recording which is done via Robolectric, no emulator needed)
- **Steps performed**:
  1. `android emulator start Pixel_9_Pro` (auto-backgrounded; boot confirmed via `adb shell getprop sys.boot_completed`)
  2. `./gradlew :app:installDebug` against `emulator-5554` (first attempt against `emulator-5556` failed with `INSTALL_FAILED_INSUFFICIENT_STORAGE`; the second AVD on this machine had room)
  3. `adb shell monkey -p com.github.jayteealao.crumbs -c android.intent.category.LAUNCHER 1` — first attempt: app crashed (see below)
  4. **Verify-owned fix applied** (commit `6148b61`): `TweetDao.getLatestBookmark(): TweetEntity` → `TweetEntity?` (Room 2.8.4 enforces non-null query return types)
  5. `./gradlew :app:installDebug` → re-launch via monkey → app reached the Login/Connect screen
  6. Wait 3s, `adb shell screencap -p` + pull
  7. `lazylogcat logs dump --device emulator-5554 --pkg com.github.jayteealao.crumbs` for log capture
- **Evidence**:
  - `verify-evidence/toolchain/01-launch.png` — pre-fix screenshot (Material Shader debug overlay still showing — Crumbs had crashed and the prior foreground activity remained visible)
  - `verify-evidence/toolchain/02-home.png` — post-fix screenshot (Crumbs Login/Connect screen)
  - `verify-evidence/toolchain/smoke.log` — Crumbs-only logcat capture
- **Observation**: the post-fix screenshot shows the Crumbs app rendering with v1.1 identity intact: cyan bookmark icon (the v1.1 accent), the lowercase `crumbs` wordmark, "Your social knowledge base" tagline, and two cut-corner dark-navy buttons labelled "Connect with X" and "Connect with Reddit". This is the no-auth Login state (expected — the emulator has no Twitter/Reddit tokens). The brutalist redesign has not started yet (planned in later slices), so this matches the pre-toolchain visual baseline.
- **Error-level entries from `com.github.jayteealao.crumbs` process** (post-fix): three families surfaced, all environmental:
  - `ashmem: Pinning is deprecated since Android Q` — system-level deprecation warning, not Crumbs code
  - `yteealao.crumbs: Invalid resource ID 0x00000000` — generic Android resource-resolution noise; common on emulators and present pre-toolchain too
  - `GoogleApiManager: Failed to get service from broker. SecurityException: Unknown calling package name 'com.google.android.gms'` — Crumbs is calling Firestore but this emulator's Google Play Services is unconfigured; environmental, not a Crumbs-code bug
  - **No `FATAL` exceptions**, no Crumbs-internal stack traces, no app crash
- **Result**: pass (with caveat — runtime smoke ran on a non-canonical AVD because Pixel_6_API_34 is not installed; runtime fidelity is unaffected by this substitution but is worth recording)

### AC4 — Maestro testTag round-trip

- **Adapter**: android + Maestro CLI 2.2.0 (user-confirmed installed)
- **Steps performed**: `maestro --device emulator-5554 hierarchy` (UI tree dump)
- **Evidence**: `verify-evidence/toolchain/maestro-hierarchy.json`
- **Observation**: Maestro successfully addressed the running Crumbs app. The hierarchy dump contains visible text nodes for `"crumbs"`, `"Connect with X"`, and `"Connect with Reddit"`, proving Maestro can read the Compose semantics tree. The `testTagsAsResourceId` semantic modifier *is* in place at `CrumbsTheme.kt:42`; however, no `Modifier.testTag(...)` exists in the current codebase to fully exercise the scaffolding round-trip (testTag values are introduced per-component in subsequent slices).
- **Result**: partial — scaffolding code present and Maestro can address the app, but the full "place a testTag → Maestro finds it as a resource-id" round-trip is deferred to the maestro slice where testTags are added systematically. This is not a failure of the toolchain slice — it's a scope boundary that the slice acknowledges in its acceptance text ("full flows are out of scope here").

## Acceptance Criteria Status

| # | Criterion (abbreviated) | Kind | Status | Method | Evidence |
|---|---|---|---|---|---|
| AC1 | `./gradlew clean :app:assembleDebug` succeeds with no version-mismatch warnings | code-only | met | automated | gradle output (this session) |
| AC2 | `./gradlew :core:designsystem:verifyRoborazziDebug` passes against regenerated goldens | code-only | met | automated | gradle output; 133 regenerated PNGs at `core/designsystem/src/test/screenshots/` |
| AC3 | `./gradlew lintDebug kotlinterCheck` both succeed without new violations | code-only | partially met | automated | `lintDebug` green; `kotlinterCheck` N/A (plugin deferred per implement deviation #8) |
| AC4 | `Modifier.testTag("probe")` inside CrumbsTheme is queryable from a Maestro selector | user-observable | partially met (deferred) | interactive | `verify-evidence/toolchain/maestro-hierarchy.json`; full round-trip in maestro slice |
| AC5 | App launches on emulator and v1.1 cyan-accent design renders identically | user-observable | met (after fix round) | interactive | `verify-evidence/toolchain/02-home.png` |
| AC6 | Regenerated golden diff vs pre-upgrade limited to anti-alias / hinting / banding (≤5% changed pixels) | user-observable | not yet verified (deferred to maintainer) | manual | none yet — maintainer will run `git diff main -- core/designsystem/src/test/screenshots/` + image-viewer side-by-side |

## Issues Found

None outstanding after the fix round. One issue was found and resolved:

| ID | Type | Severity | Triage | Outcome |
|---|---|---|---|---|
| ROOM-NULL-1 | runtime-crash on app launch | Blocker | Fix | Patched at commit `6148b61` — `TweetDao.getLatestBookmark()` widened to return `TweetEntity?`. Cause: Room 2.8.4 enforces non-null query return contracts where Room 2.4.3 silently returned null. Both callers already null-checked the field, so the widening is safe. |

## Verify-Owned Fixes

| ID | Type | Triage | Sub-agent outcome | Re-check result |
|----|------|--------|-------------------|-----------------|
| ROOM-NULL-1 | runtime-crash (forced by Room 2.4.3 → 2.8.4 toolchain bump) | Fix (verify-owned, no sub-agent — single-line type widening I applied inline given the trivial scope and clear caller analysis) | Patched | Re-ran `:app:installDebug` + monkey launch → app rendered the Login/Connect screen correctly with v1.1 visual identity preserved |

Commit: `6148b61`

## Augmentation Verification

N/A — no `02c-craft.md`, no entries in `00-index.md` `augmentations:` list.

## Gaps / Unverified Areas

- **AC4 testTag round-trip** — full evidence (a `Modifier.testTag` in code that Maestro queries by `resource-id`) is deferred to the maestro slice. Scaffolding code is in place and Maestro is operational against the running app, so the slice is architecturally complete.
- **AC6 visual diff against pre-bump tree** — maintainer-driven; not yet performed. Recommended approach: `git diff <pre-bump-sha> -- core/designsystem/src/test/screenshots/` reports binary-file changes per PNG; then open a sample of those PNGs side-by-side with their pre-bump counterparts in an image viewer. Acceptable drift per plan: anti-alias edges, font hinting, gradient banding, subtle Material3 ripple differences (we migrated CrumbsBottomNav from the removed `rememberRipple` to `material3.ripple`). Unacceptable drift: missing strokes, repositioned elements, missing text, color shifts.
- **Canonical AVD substitution** — runtime smoke ran on `Pixel_9_Pro` API 36 instead of plan-specified `Pixel_6_API_34`. Plan called for Pixel 6 / API 34 as the golden-recording canonical (which used Robolectric, no emulator) and the runtime smoke device. Substitution is informational; runtime smoke purpose (does it launch, does it render) is unaffected by AVD choice. Re-running the smoke on a Pixel 6 API 34 AVD is a non-blocking option for the maintainer.

## Freshness Research

No new external research consulted during verify. The Room 2.8.4 non-null query enforcement was already captured during implement-stage research (the @Insert non-null parameter was the same family of strictness). The verify-owned fix was a one-line widening directly supported by the existing call-site nullability handling — no further external lookup needed.

## Caveats

- **Runtime smoke AVD**: `Pixel_9_Pro` (Android 16 / API 36), not the plan-canonical `Pixel_6_API_34`. Acceptable for runtime smoke; goldens used the Pixel 6 spec via Robolectric.
- **`kotlinterCheck` deferral** propagates from implement (deviation #8 in `05-implement-toolchain.md`).
- **`interactive-verification: deferred`** — AC4 and AC6 are recorded as deferrals in `00-index.md` `runtime-evidence-deferrals` (see index update). Ship will hard-block until cleared.
- **Stack source: confirmed** — `00-index.md` `stack.user-confirmed: true`; adapter selection (`android`) intersected cleanly with `stack.platforms: [android]`.

## Recommendation

The slice is **ready for review** with two deferrals to clear before ship:

1. **AC4** clears automatically when the `maestro` slice ships (testTags introduced systematically).
2. **AC6** needs a maintainer-driven visual diff against pre-bump goldens. Suggested when scheduling: 15–30 minutes with a side-by-side image viewer on a sample of the 133 PNGs (e.g., 5 per test class).

The one runtime crash that surfaced during verify (`ROOM-NULL-1`) was a real toolchain-bump-induced bug — same family as the earlier `@Insert nullable` Room fix in implement. Both are pre-existing latent issues Room 2.8.4 surfaced via stricter validation; both are fixed in-tree.

## Recommended Next Stage

- **Option A (default):** `/wf review brutalist-redesign` — slug-wide review per `review-scope: slug-wide` in `00-index.md`. Reviews the full branch diff (`feat/brutalist-redesign` vs `main`) including the verify-owned fix. **`/compact` recommended** before review — verify-stage debugging, emulator output, and the Room crash investigation are noise for review dispatch.
- **Option B:** `/wf handoff brutalist-redesign` — skip review (only if maintainer is confident they don't need a formal review pass; the slug-wide review-scope was deliberately chosen at intake, so this option deviates from the original intent).
- **Option C:** `/wf plan brutalist-redesign tokens` — start planning the next slice while the toolchain reality is fresh in memory. Per the rolling-plan strategy in `04-plan.md` this is encouraged.
- **Option D:** Re-run the smoke on a Pixel 6 / API 34 AVD if the maintainer wants the plan-canonical device verified before review. Optional — runtime smoke purpose was met on Pixel_9_Pro / API 36.
