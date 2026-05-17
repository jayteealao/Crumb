---
schema: sdlc/v1
type: slice
slug: brutalist-redesign
slice-slug: toolchain
status: implemented
stage-number: 3
created-at: "2026-05-16T22:37:59Z"
updated-at: "2026-05-17T00:38:34Z"
complexity: l
depends-on: []
tags: [toolchain, upgrade, kotlin, agp, compose, roborazzi]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
  siblings:
    - 03-slice-tokens.md
    - 03-slice-components.md
    - 03-slice-layouts.md
    - 03-slice-screens.md
    - 03-slice-behaviors.md
    - 03-slice-maestro.md
  plan: 04-plan-toolchain.md
  implement: 05-implement-toolchain.md
---

# Slice: Toolchain upgrade

## Goal

Bring Kotlin, AGP, Gradle, Compose, Material3, Roborazzi, and Robolectric to the locked target versions across every module, while keeping the **current visual design fully intact**. Zero pixels change in this slice — only `build.gradle` files, the wrapper, the version catalog, and (where the upgrade forces it) test code shape changes. Existing 154 Roborazzi goldens are regenerated against the new chain to establish a known-good baseline before any visual work starts.

## Why This Slice Exists

Roborazzi 1.37.0 requires Kotlin 2.x and AGP 9.x; Compose 1.11.x requires AGP 9.x; the project today is on Kotlin 2.0.21 / AGP 8.0.2. The redesign cannot use a modern Roborazzi golden pipeline without this upgrade, and there is no partial upgrade path (per freshness research). Shipping this as slice 1 isolates the substantial chance of toolchain-induced rendering drift from the deliberate visual redesign that follows. If something breaks because of, say, a font-hinting change in Compose 1.11.1, we discover that *here* with old visuals, not entangled with the brutalist palette switch.

## Scope

**In:**
- Bump `gradle/wrapper/gradle-wrapper.properties` distributionUrl to **Gradle 9.1+**.
- Confirm JDK 17 in CI + dev (no source change beyond JVM target updates if any module still on 1.8).
- Update `gradle/libs.versions.toml`: align it with what's actually used; bump the catalog values for Kotlin, Compose, Material3, Coroutines, Lifecycle, Accompanist, Coil, Media3, Room, Retrofit.
- Update every module's `build.gradle` (app, core:designsystem, core:models, core:pref, feature:twitter, feature:reddit, plus root):
  - `kotlin = 2.3.21` via KGP `org.jetbrains.kotlin.android` and `org.jetbrains.kotlin.plugin.compose`.
  - AGP `com.android.application` / `com.android.library` → **9.1.1**.
  - Compose BOM **2026.05.00** (Compose 1.11.1).
  - Material3 **1.4.0**.
  - Drop `composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }` — KGP 2.3.21 manages the compiler plugin.
  - JVM target 17 everywhere (replace lingering 1.8 in app/feature modules).
- Bump Roborazzi to **1.37.0** in `core/designsystem` and add it to any other module that gains golden tests.
- Bump Robolectric to **4.16**.
- Regenerate all existing Roborazzi golden images in `core/designsystem/src/test/snapshots/` with the OLD visual design still in place. Commit regenerated PNGs.
- Add the Maestro testTagsAsResourceId enablement scaffolding (a CompositionLocal or top-level `Modifier.semantics { testTagsAsResourceId = true }` in `CrumbsTheme`) — empty harness only, no specific testTags yet (those land per screen/component).
- Update any test code shapes broken by the version jumps (Robolectric 4.16 dropped SDK 21–22, our minSdk is 24 so we're fine; `Accompanist 0.27` → built-in Pager would land later in screens slice — this slice does NOT migrate Pager).
- Run `./gradlew :app:assembleDebug :app:lintDebug :core:designsystem:verifyRoborazziDebug` and ensure green.

**Out:**
- Any change to `CrumbsColors`, `CrumbsTypography`, `CrumbsShapes`, `CrumbsSpacing` values (handled by `tokens` slice).
- Any change to component implementations (handled by `components` slice).
- Migrating `OnboardingScreen` off Accompanist Pager (handled by `screens` slice; this slice only adjusts version coordinates if Accompanist needs a bump to coexist with Compose 1.11.1).
- DB schema changes (handled by `behaviors` slice).
- Font asset bundling (handled by `tokens` slice).
- Maestro yaml flows (handled by `maestro` slice).

## Acceptance Criteria

- **Given** the working tree on `feat/brutalist-redesign` with this slice's changes, **when** the dev runs `./gradlew clean :app:assembleDebug`, **then** the build succeeds with no warnings about Kotlin/AGP/Compose version mismatches. *(automated)*
- **Given** the upgraded toolchain, **when** the dev runs `./gradlew :core:designsystem:verifyRoborazziDebug`, **then** all pre-existing golden tests pass against goldens regenerated within this slice. *(automated)*
- **Given** the upgraded toolchain, **when** the dev runs `./gradlew lintDebug kotlinterCheck`, **then** both succeed without new violations. *(automated)*
- **Given** the upgraded `CrumbsTheme` with `Modifier.semantics { testTagsAsResourceId = true }` at its root, **when** an arbitrary composable inside the theme calls `Modifier.testTag("probe")`, **then** the test tag is queryable from a Maestro selector. *(interactive — Maestro `evalScript` smoke probe; full flows are out of scope here)*
- **Given** the running debug build installed via `android` CLI on a Pixel 6 (API 34) emulator, **when** the app launches and reaches `HomeScreen`, **then** the existing v1.1 cyan-accent design renders identically to v1.1. *(interactive — visual comparison; manual)*
- **Given** the regenerated golden images, **when** the dev inspects the diff against pre-upgrade goldens, **then** any differences are limited to anti-alias / font-hinting drift (≤5% changed pixels per image) and not structural. *(manual review)*

## Dependencies on Other Slices

None — this is the foundation. Every downstream slice depends on this one.

## Risks

- **Kotlin 1.7→2.0 had already occurred (codebase shows 2.0.21 active)** but the catalog still lists 1.7.10. Cleanup of that stale catalog value may surface latent bugs that were silently using the runtime version. Mitigation: align catalog with runtime in this slice.
- **AGP 8→9 introduces flag removals** (`android.useAndroidX` etc. were already mandatory by AGP 8.0; verify nothing remains in `gradle.properties` that AGP 9 rejects).
- **Robolectric 4.16 + Roborazzi 1.37.0 + Hilt** test rule chain may differ from the current 4.14.1/1.7.0 setup. Mitigation: rerun the largest existing `core/designsystem` test class first and fix the rule-chaining before mass regeneration.
- **Compose 1.11.1 may render fonts subtly differently** even on the same TTF (font-hinting changes are documented per release). Mitigation: this is exactly what golden regeneration captures; if drift exceeds 5% changed pixels on unchanged visuals, escalate before continuing.
- **Compose Compiler plugin migration** (composeOptions block removal) could break individual modules if some modules use the old block and others use the new plugin form. Mitigation: a single consistent application across all modules in one commit.
