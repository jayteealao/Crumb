---
schema: sdlc/v1
type: verify-index
slug: brutalist-redesign
status: in-progress
stage-number: 6
created-at: "2026-05-17T01:17:12Z"
updated-at: "2026-05-17T23:48:00Z"
slices-verified: 6
slices-total: 7
tags: [redesign, toolchain, tokens, components, layouts, screens, behaviors, verify-owned-fix, runtime-evidence-deferral]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
next-command: wf-review
next-invocation: "/wf review brutalist-redesign behaviors"
---

# Verify Index

## Slice Verification Status

### `toolchain` — partial (converged)

- `result: partial` — three deferrals (AC3 `kotlinterCheck` plugin-removed, AC4 maestro round-trip deferred to maestro slice, AC6 maintainer-driven visual diff).
- `convergence: converged` — one fix round resolved every initial issue.
- `metric-issues-found-initial: 1`, `metric-issues-found-final: 0`.
- Verify-owned fix: `6148b61` (`TweetDao.getLatestBookmark(): TweetEntity` → `TweetEntity?`; forced by Room 2.8.4 strict non-null query return contract).
- Build + lint + Roborazzi-verify all green; emulator smoke shows v1.1 design rendering correctly after fix.
- Maestro hierarchy dump confirms scaffolding addressable; full testTag round-trip deferred to maestro slice.
- See: [06-verify-toolchain.md](06-verify-toolchain.md).

### `tokens` — partial (not-needed)

- `result: partial` — `interactive-verification: deferred` for AC-K4 (maintainer manual handoff diff) + AC-K6 (HomeScreen paper background, auth-blocked).
- `convergence: not-needed` — zero code-side failures and zero substantively-unmet user-observable AC entered the fix loop.
- `metric-acceptance-met: 4 / 6` (AC-K1/K2/K3/K5 fully met; AC-K4 deferred, AC-K6 partial).
- `metric-checks-passed: 5 / 5` — every automated check green: AC-K1 grep, AC-K2 grep, AC-K5 `assembleDebug + lintDebug + verifyRoborazziDebug`.
- Interactive evidence on Medium_Phone_API_36 confirms: orange accent `#FF5A1F`, dark-mode `#0B0B0B` background, Funnel Display Bold wordmark, IBM Plex Mono body — all reaching the running app under both light/dark themes and under airplane mode.
- `bootstrap-failures: []`; `adapters-used: [android]`.
- See: [06-verify-tokens.md](06-verify-tokens.md).

### `components` — partial (not-needed)

- `result: partial` — single `interactive-verification: deferred` annotation on AC-C6 (Maestro studio dry-run deferred to the dedicated `maestro` slice).
- `convergence: not-needed` — zero failing checks and zero substantively-unmet user-observable ACs entered the fix loop. `metric-issues-found-initial: 0`.
- `metric-acceptance-met: 5 / 6` (AC-C1 reconciled count, AC-C2/C3/C4 automated green, AC-C5 met via static evidence; AC-C6 deferred).
- `metric-checks-passed: 5 / 5` — `assembleDebug`, `verifyRoborazziDebug`, `lintDebug`, AC-C3 grep gate, AC-C4 dangling-import gate all green.
- AC-C5 (LoadingCard scan-line) accepted via static source inspection: exactly one `rememberInfiniteTransition` in the file at `LoadingCard.kt:48`, its fraction applied only to the scan-line `drawLine` via `drawBehind` at lines 67–73; all 4 skeleton boxes are static. PO-accepted via AskUserQuestion 2026-05-17T13:29Z.
- AC-C6 (Maestro studio dry-run) deferred — 39 testTag call sites confirm the scaffolding; Maestro CLI is not on the confirmed PATH and the dedicated `maestro` slice owns the round-trip per the workflow's slice boundary. PO-decided via the same AskUserQuestion.
- `bootstrap-failures: []`; `adapters-used: []` (no adapter bootstrap needed — both user-observable ACs satisfied via static evidence/deferral).
- See: [06-verify-components.md](06-verify-components.md).

### `layouts` — partial (not-needed)

- `result: partial` — `interactive-verification: deferred` for two procedural transfers: AC-L2 precise-inset measurement (Robolectric `WindowInsets(0)` cannot expose 28/88/34/52+8dp gaps; first real HomeScaffold consumer in the screens slice will measure on a live system bar) and AC-L5 Maestro studio testTag round-trip (deferred to the dedicated maestro slice on the established pattern).
- `convergence: not-needed` — zero failing checks and zero substantively-unmet user-observable ACs entered the fix loop. `metric-issues-found-initial: 0`.
- `metric-acceptance-met: 3 / 5` (AC-L1 file existence, AC-L3 backdrop dismiss via Compose UI test, AC-L4 indicator + CTA via Roborazzi — all fully met; AC-L2 slot composition met + inset measurement deferred; AC-L5 deferred).
- `metric-checks-passed: 5 / 5` — `testDebugUnitTest` (7 layout tests pass), `verifyRoborazziDebug`, `assembleDebug`, `lintDebug`, and the AC-L1 file-existence gate all green.
- AC-L3 OverlayShell backdrop dismiss closed entirely in-slice via `OverlayShellTest.backdrop_tap_invokes_onDismiss` — Compose UI test performs `performClick()` on `overlay-shell-backdrop` testTag and asserts the dismiss callback fires.
- AC-L4 OnboardingShell evidence confirms 3-pill indicator with accent on current page (`page0_light` shows accent at index 0; `page1_dark` shows accent migrates to index 1) and right-aligned NEXT CTA in brutalist Primary style.
- `bootstrap-failures: []`; `adapters-used: []` (every user-observable AC satisfied via static UI test, Roborazzi capture, or explicit deferral — no live adapter bootstrap required).
- See: [06-verify-layouts.md](06-verify-layouts.md).

### `screens` — partial (not-needed)

- `result: partial` — five `interactive-verification: deferred` annotations: AC-S1 + AC-S2 (subjective ≥95% mock-fidelity adjudication, maintainer-owned manual diff) and AC-S4 + AC-S6 nav half + AC-S7 (emulator/Maestro-shaped, collapses onto the dedicated maestro slice).
- `convergence: not-needed` — zero failing checks and zero substantively-unmet user-observable ACs entered the fix loop. `metric-issues-found-initial: 0`.
- `metric-acceptance-met: 3 / 8` — AC-S3 Accompanist source removal (grep clean + TwitterCard.kt orphan deleted), AC-S5 MapView placeholder (Roborazzi + map-SDK grep both clean), AC-S8 OAuth ViewModel byte-stability (`git log --diff-filter=M` empty + LoginScreen callback assertions in test) fully met. AC-S1/S2/S6 partially met (automated half met, runtime half deferred). AC-S4/S7 deferred outright.
- `metric-checks-passed: 6 / 6` — `:app:lintDebug`, `:feature:twitter:lintDebug`, `:feature:reddit:lintDebug`, `:app:assembleDebug`, `:app:testDebugUnitTest` + `:feature:*:testDebugUnitTest` (19 tests, 0 failures across 8 new screen test classes), and `:app:verifyRoborazziDebug` + `:feature:*:verifyRoborazziDebug` (16 goldens). Plus AC-S3 grep gate and AC-S5 map-SDK grep gate.
- Five deferrals collapse onto two clearing paths: maintainer manual mock-fidelity diff (AC-S1, AC-S2) shares the procedure with tokens AC-K4 + toolchain AC6; the three Maestro-shaped deferrals (AC-S4, AC-S6 nav, AC-S7) collapse onto the same emulator+Maestro evidence run that the maestro slice owns.
- `bootstrap-failures: []`; `adapters-used: []` (every user-observable AC satisfied via Roborazzi capture, Compose UI test callback assertion, source-level grep, or explicit deferral — no live adapter bootstrap required).
- Layouts AC-L2 (HomeScaffold inset measurement on a live system bar) — identified at layouts-verify as the natural moment for this slice to discharge — is **not cleared by this verify** because no emulator boot was performed. Deferral remains active; collapses onto maestro/probe sweep along with AC-S4.
- See: [06-verify-screens.md](06-verify-screens.md).

### `behaviors` — partial (converged)

- `result: partial` — 7 `interactive-verification: deferred` annotations for the user-observable ACs (90 emulator migration, 92/93 long-press DELETE+UNDO, 95 type filter re-query, 96 tags overlay APPLY, 97 banner appears, 98 banner CTA OAuth). All collapse onto the maestro slice's emulator+Maestro evidence run.
- `convergence: converged` — one fix round closed the only initial issue. `metric-issues-found-initial: 1`, `metric-issues-found-final: 0`.
- Verify-owned fix: `47ee1b78` — added `DeletedBookmarkRepositoryTest.kt` (3 tests, all PASS) for AC 94's tombstone round-trip coverage. PO triage chose Fix over Skip/Defer.
- `metric-acceptance-met: 3 / 10` fully met (AC 91 migration test exists, AC 94 sync filter unit test, AC 99 versionCode/Name); 6 ACs (92/93/95/97/98 + 90) met-with-runtime-deferral (callback wiring + static + golden evidence; runtime via maestro); AC 96 has a substantive gap — the OverlayShell-mounted tag-filter UI was not delivered in-stage.
- `metric-checks-passed: 7 / 7` — `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, lint × 4 modules, `verifyRoborazziDebug` × 4 modules, `aapt dump badging` (versionCode=3 versionName=2.0), `:app:testDebugUnitTest --tests "*DeletedBookmarkRepositoryTest"` (3/3).
- `bootstrap-failures: []`; `adapters-used: []` (Maestro CLI still not on confirmed PATH; per workflow precedent, behaviors interactive evidence collapses onto the maestro slice).
- **Carry-forward gaps to review/handoff**: (1) `tweetEntity.type` does not exist — `TypeFilter` enum wired but DAO predicate is tombstone-only (future cleanup: derive type via JOIN or add column with v6 migration); (2) OverlayShell-mounted tag-filter UI not delivered (AC 96 substantive gap — recommend pre-ship refinement decision).
- See: [06-verify-behaviors.md](06-verify-behaviors.md).

### `maestro` — not yet verified

Per the rolling-plan strategy — each slice verifies after its implement.

## Cross-Slice Observations

- **`runtime-evidence-deferrals` is now 19 entries (1 cleared, 18 active).** Behaviors adds 7: AC 90 (Room migration on real device), AC 92 (long-press DELETE 200ms + snackbar), AC 93 (UNDO restores), AC 95 (type filter 300ms re-query), AC 96 (tags OverlayShell APPLY), AC 97 (banner appears within 1s on 401), AC 98 (banner CTA → OAuth). All 7 collapse onto the same emulator+Maestro evidence run that clears the prior 11. Maestro slice (or a single probe sweep with both `android` and `maestro` on PATH) discharges the entire set at once. Maintainer-owned visual-diff deferrals (toolchain AC6, tokens AC-K4, screens AC-S1 + AC-S2) clear on a separate cadence.

- **Prior cross-slice observation (preserved):** Toolchain contributed AC4 (maestro round-trip) + AC6 (maintainer goldens diff). Tokens contributed AC-K4 (maintainer handoff diff) + AC-K6 (HomeScreen paper — cleared by `quick-skip-auth-page` slice on 2026-05-17T10:23Z). Components contributed AC-C6 (Maestro studio dry-run). Layouts contributed AC-L2 (HomeScaffold inset measurement on a live system bar) + AC-L5 (Maestro studio testTags for shells). Screens adds five: AC-S1 + AC-S2 (≥95% mock fidelity, maintainer manual diff — same procedure as tokens AC-K4 + toolchain AC6), AC-S4 (Pixel 6 emulator nav walkthrough), AC-S6-nav (empty-state CTA → LoginScreen navigation half), AC-S7 (AllBookmarks long-press → 4-action popup integration). Ship will hard-block until each active entry is cleared. The seven Maestro-shaped deferrals (toolchain AC4, components AC-C6, layouts AC-L2 + AC-L5, screens AC-S4 + AC-S6-nav + AC-S7) collapse onto the same emulator+Maestro evidence run — the maestro slice (or a single probe sweep) will discharge them all at once. Maintainer can clear toolchain AC6 + tokens AC-K4 + screens AC-S1 + AC-S2 via the visual-diff procedure at any time.
- **Room 2.8.4 strictness pattern continues to hold.** Tokens slice did not touch DAO surfaces; no new nullability bugs surfaced. Downstream DAO-touching slices (behaviors) should still watch for the family.
- **Brutalist palette is structurally complete on the running app.** The token cutover landed cleanly enough that LoginScreen + dark-mode + airplane-mode all show the intended Option-D visual identity. Components/screens slices can now build against verified token reality instead of inferred token reality.
- **AVD inventory drift.** Plan said Pixel 6 / API 34 was canonical; actually-provisioned AVDs are Medium_Phone_API_36 (used for tokens verify) and Pixel_9_Pro (boot reservation hit `INSUFFICIENT_STORAGE` on install). Plan-stage assumptions section accepted "Either works"; this is a noted-but-not-blocking deviation.
- **Component slice landed without a fix loop.** Toolchain needed one (Room nullability); tokens needed none; components needed none; layouts now also needed none. Three consecutive fix-loop-free slices is a signal that the implement-stage's rebuild-then-regenerate-goldens-in-one-atomic-commit cadence is paying off — every code-side gate is green at verify entry because implement already closes `recordRoborazziDebug` → `verifyRoborazziDebug` → `lintDebug` → `assembleDebug` before committing.
- **Edge-to-edge wiring landed at MainActivity.** The layouts slice flipped on `enableEdgeToEdge()` for the first time. Pre-migration screens that have not yet adopted HomeScaffold will render with TopBar under the status bar until the screens slice migrates them — interim regression is by design and limited to the inter-slice window.

## Recommended Next Stage

- **Option A (default):** `/wf review brutalist-redesign behaviors` — every code-side gate green; behaviors brings the largest cross-layer surface change (5 layers: DB schema + repo + VM + UI route + component slot) and benefits from a focused review before the workflow ships. `review-scope: slug-wide` means the canonical review runs against the whole branch diff, but a per-slice spot review on behaviors is a strong intermediate signal.
- **Option B:** `/wf plan brutalist-redesign maestro` — start the final slice's plan. Maestro now has everything it needs: 18 active runtime-evidence-deferrals + complete testTag inventory + sync-error trigger pathway + tombstone event flow.
- **Option C:** `/wf-quick probe brutalist-redesign` — re-attempt deferred evidence sweep on an emulator. A single probe run with both `android` and `maestro` on PATH would discharge most of the 18 deferrals in one shot, including all 7 new behaviors entries. Useful pre-handoff if the maintainer prefers to ship the workflow before authoring maestro flows.
