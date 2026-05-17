---
schema: sdlc/v1
type: implement-index
slug: brutalist-redesign
status: in-progress
stage-number: 5
created-at: "2026-05-17T00:38:34Z"
updated-at: "2026-05-17T00:38:34Z"
slices-implemented: 1
slices-total: 7
metric-total-files-changed: 35
metric-total-lines-added: 174
metric-total-lines-removed: 152
tags: [redesign, toolchain]
refs:
  index: 00-index.md
  plan-index: 04-plan.md
next-command: wf-verify
next-invocation: "/wf verify brutalist-redesign toolchain"
---

# Implement Index

## Cross-Slice Integration Notes

- The `toolchain` slice produced a working Kotlin 2.2.10 / Compose 1.11.1 (BOM 2026.05.00) / Material3 1.4.0 / Room 2.8.4 / Hilt 2.59.2 chain on Gradle 9.3.1 + AGP 9.1.1 + JDK 17. **The locked Kotlin target moved from 2.3.21 → 2.2.10** to match AGP 9.1.1's bundled compiler. Downstream slices should target Material3 1.4.0 APIs (e.g. `material3.ripple` not `material.ripple.rememberRipple`) and assume Compose 1.11.1 idioms.
- **133 Roborazzi goldens regenerated** against the new chain on v1.1 visuals. The `tokens` slice's hard-cutover changes will produce a new, much larger golden diff against this baseline — that's the expected interaction.
- **`testTagsAsResourceId` scaffolding** is in place at the `CrumbsTheme` root. Every later slice should add `Modifier.testTag(...)` to components and screens as it composes them; Maestro will address them by name in the final slice.
- **`kotlinter` is removed from the build**. Re-introducing it (when upstream isolates `kotlin-compiler-embeddable` via Gradle Workers API) is a follow-up, not a `tokens`/`components`/etc. concern.
- **Coil 3 migration is deferred to the `components` slice**. `feature/twitter/components/TwitterCard.kt` still uses `com.commit451.coil-transformations` (Coil 2-only). The `components` slice's rewrite of the bookmark card surface should absorb the Coil 3 namespace migration + replace the blur/pixelation/swirl transformations with Coil 3 equivalents (or drop them outright — they're decorative on the v1.1 design).

## Slice Status

### `toolchain` — complete

- Six commits on `feat/brutalist-redesign` (`143832b`..`f637a52`); HEAD: `f637a52`.
- Build green: `:app:assembleDebug` and `:core:designsystem:verifyRoborazziDebug` both pass.
- 11 plan deviations recorded — see `05-implement-toolchain.md` for the full deviation log.
- Emulator smoke test deferred to verify stage (manual).
- See: [05-implement-toolchain.md](05-implement-toolchain.md).

### `tokens` — not yet planned

Drafted at plan time after toolchain ships. The toolchain reality (Kotlin 2.2.10 + Compose 1.11.1 + Material3 1.4.0) is the baseline for the tokens plan; `04-plan-tokens.md` should be drafted against observed `Font(...)` / `MaterialTheme` / `dynamic*ColorScheme` APIs rather than guessing.

### `components`, `layouts`, `screens`, `behaviors`, `maestro` — not yet planned

Per the rolling-plan strategy in `04-plan.md`.

## Recommended Next Stage

- **Option A (default):** `/wf verify brutalist-redesign toolchain` — verify the implementation (CI command + emulator smoke). **Compact recommended.**
- **Option B:** `/wf review brutalist-redesign` — slug-wide review skipping verify; suitable if the maintainer plans to fold the emulator smoke into their own pre-merge ritual.
- **Option C:** `/wf plan brutalist-redesign tokens` — kick off the next slice's plan while toolchain reality is fresh.
