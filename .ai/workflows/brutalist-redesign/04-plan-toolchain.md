---
schema: sdlc/v1
type: plan
slug: brutalist-redesign
slice-slug: toolchain
status: implemented
stage-number: 4
created-at: "2026-05-16T22:37:59Z"
updated-at: "2026-05-17T00:38:34Z"
metric-files-to-touch: 33
metric-step-count: 21
has-blockers: false
revision-count: 0
tags: [toolchain, kotlin, agp, compose, roborazzi, ci]
stack-source: confirmed
refs:
  index: 00-index.md
  plan-index: 04-plan.md
  slice-def: 03-slice-toolchain.md
  siblings: []
  implement: 05-implement-toolchain.md
next-command: wf-implement
next-invocation: "/wf implement brutalist-redesign toolchain"
---

# Plan: Toolchain upgrade

## Current State

**Toolchain coordinates (active runtime, not the stale catalog):**
- Kotlin: **2.0.21** (root `build.gradle`)
- AGP: **8.0.2**
- Gradle: **8.5**
- Compose Compiler plugin: `org.jetbrains.kotlin.plugin.compose` v2.0.21 — already adopted
- KSP: **2.0.21-1.0.28**
- JDK target: **mixed** — `core:designsystem` and `core:models` are on JVM 17; `app`, `core:pref`, `feature:twitter`, `feature:reddit` are still on JVM 1.8
- compileSdk: **mixed** — every module is on 34 except `core:pref` (33)
- minSdk: **24** uniformly
- KAPT: **fully removed**; KSP everywhere a Hilt/Room processor is needed
- `composeOptions { kotlinCompilerExtensionVersion '1.5.15' }` still present in `app/build.gradle` and `feature/twitter/build.gradle` — deprecated, must go

**Test infra:**
- Roborazzi **1.7.0** in `core/designsystem` only
- Robolectric **4.14.1**
- 17 test classes, **133 golden PNGs** in `core/designsystem/src/test/screenshots/`
- Every test uses `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [33])` + `@GraphicsMode(NATIVE)` + `createAndroidComposeRule<ComponentActivity>()` — no Hilt-in-tests
- `captureRoboImage("src/test/screenshots/<Name>.png")` per call site
- `verifyRoborazzi` task auto-derived from plugin but **not wired into CI**

**Stale or divergent versions:**
- `gradle/libs.versions.toml` has `kotlin = "1.7.10"` (ignored — root pins 2.0.21) and `composeUi = "1.3.1"` (ignored — `core/designsystem` uses BOM `2024.02.00`); both stale
- Material3: **`1.2.0`** in `app`, **`1.3.0`** in `feature/twitter`, **`1.2.0`** in `feature/reddit`, BOM-governed in `core/designsystem` — three different versions
- Coil: **`2.2.2`** in `app`, **`2.5.0`** in `core/designsystem` — two versions
- Accompanist: **`0.27.0`** in `app` (Pager), `0.22.0-rc` in catalog (unused) — drift
- Hilt: **`2.50`** uniformly (good)

**CI:** `.github/workflows/pr_check.yml` runs `clean assembleDebug` on `ubuntu-latest`, **JDK 21**, Android SDK **33**. No lint, no unit tests, no Roborazzi verification. `release.yml` similar. No CircleCI / GitLab / pre-commit hooks.

**Stack:** confirmed (`user-confirmed: true`). `android` CLI + `lazylogcat` CLI both on PATH with their companion skills available.

**Recent build-touching commits (last 6 months):**
- `e48d751` chore: add Kotlin Compose compiler plugin for Kotlin 2.0+ — already adopted
- `2232cb3` chore: replace KAPT with KSP plugin — KAPT migration complete
- `0d3fc08` chore: update Kotlin to version 2.0.21 — already on 2.0.21
- `326efdb` fix: upgrade Hilt from 2.44 to 2.50 — Hilt is on 2.50

The repo is in a known-clean state for a major toolchain jump. No in-flight migrations to merge around.

## Reuse Opportunities

Toolchain work is largely "edit version coordinates," but several existing patterns are kept and amplified:

- **`gradle/libs.versions.toml` catalog** — already structured for shared versions. We extend it (Compose BOM, Coil 3, latest catalog values) rather than introducing a parallel mechanism.
- **`id 'org.jetbrains.kotlin.plugin.compose'`** — already applied in every Compose-using module. We keep using it; only the `composeOptions { kotlinCompilerExtensionVersion }` block (a parallel mechanism) is removed.
- **KSP migration** — already in place; this slice does not revisit KAPT. The KSP version pair (`<kotlin>-1.0.x`) is the only change.
- **Existing `pr_check.yml`** — extended in place rather than replaced. Add gates, keep the existing structure.
- **`createAndroidComposeRule<ComponentActivity>()`** — reused as-is across the 17 test classes. No need to migrate to a Hilt-aware activity (designsystem has no Hilt in tests, and Roborazzi 1.37 still supports the simple form).
- **Existing golden filenames + `screenshots/` directory** — reused. We re-record into the same paths so the regeneration appears as a clean `git diff` per image, not a rename.

No reuse candidates identified for: AGP/Kotlin/Gradle/Compose version bumps themselves (those are pure replacements).

## Likely Files / Areas to Touch

| File | Reason |
|---|---|
| `build.gradle` (root) | KGP, AGP, KSP version pins; possibly remove `id 'org.jetbrains.kotlin.android'` if AGP 9 makes it implicit |
| `settings.gradle` (root) | Spot-check; no expected change |
| `gradle/libs.versions.toml` | Delete stale `kotlin`/`composeUi` keys; add `composeBom`, `coil` v3, modern lifecycle/coroutines/room/firebase versions; reorganize bundles |
| `gradle/wrapper/gradle-wrapper.properties` | `distributionUrl` → Gradle 9.1+ |
| `gradle.properties` | Optional cleanup of unused KAPT JVM-args lines |
| `app/build.gradle` | KGP → 2.3.21; AGP → 9.1.1; remove `composeOptions { kotlinCompilerExtensionVersion }`; `sourceCompatibility` + `targetCompatibility` + `jvmTarget` → 17; drop direct material3 pin; Coil 3 import; align test deps |
| `core/designsystem/build.gradle` | KGP → 2.3.21; AGP → 9.1.1; Compose BOM → 2026.05.00; Roborazzi → 1.37.0; Robolectric → 4.16; Coil 3 |
| `core/models/build.gradle` | KGP → 2.3.21; AGP → 9.1.1; Compose BOM → 2026.05.00 |
| `core/pref/build.gradle` | KGP → 2.3.21; AGP → 9.1.1; **compileSdk 33→34**; **JVM target 1.8→17**; KSP version |
| `feature/twitter/build.gradle` | KGP → 2.3.21; AGP → 9.1.1; remove `composeOptions { kotlinCompilerExtensionVersion }`; JVM → 17; drop material3 direct pin; Coil 3 |
| `feature/reddit/build.gradle` | KGP → 2.3.21; AGP → 9.1.1; JVM → 17; drop material3 direct pin |
| `plugins/build.gradle`, `plugins/settings.gradle` | Spot-check; unused convention plugins, no change expected |
| `core/designsystem/src/main/.../theme/CrumbsTheme.kt` | Add `Modifier.semantics { testTagsAsResourceId = true }` at the top of the theme composable so Maestro can address descendants in later slices |
| `core/designsystem/src/test/java/.../components/*.kt` (17 files) | Bump every `@Config(sdk = [33])` to `[34]` |
| `core/designsystem/src/test/java/.../TestTheme.kt`, `TestTypography.kt` | Spot-check for any API-level usage that changed under Robolectric 4.16 |
| `app/src/main/java/.../*GradientImage*.kt`, `*CrumbsBookmarkCard*.kt` (designsystem) | Coil 2 → Coil 3 import-path migration (`coil` → `coil3`) — minimal: change imports and any `ImageRequest`/`AsyncImage` API drift |
| `.github/workflows/pr_check.yml` | JDK 17, Android SDK 34, add `lintDebug`, `kotlinterCheck`, `verifyRoborazziDebug` |
| `.github/workflows/release.yml` | JDK 17, SDK 34; keep limited assembly scope |
| `core/designsystem/src/test/screenshots/*.png` (133 files) | Regenerate as single follow-up commit |

Approximate file count: **30 source files** + 133 regenerated PNGs.

## Proposed Change Strategy

Strict ordering with one safety-net spike up front. Each step lands as a discrete commit on `feat/brutalist-redesign` so a reviewer (and `git bisect`) can isolate any regression to the version it introduced.

**Phase A — Pre-upgrade verification (cheap, throwaway).**
A single spike commit verifies the riskiest hypothesis (Kotlin 2.3.21 × KSP 2.3.21-1.0.x × Hilt 2.50) compiles before we commit to mainline work. If the spike fails, escalate and re-shape the slice (potentially backing off Kotlin to 2.2.x).

**Phase B — Audits (read-only, no code change).**
Four grep-driven audits documented in `## Step-by-Step Plan` produce a one-line report each. Anything they find that needs action becomes its own pre-flight commit before the version bumps start.

**Phase C — Sequential version bumps.**
Order matters: JDK target → Gradle → AGP → Kotlin/KGP → Compose BOM → Material3 (via BOM) → Roborazzi/Robolectric → Coil. Each version bump is one commit; the message lists the version delta. `assembleDebug` is run after every commit — if it breaks, fix or revert before continuing.

**Phase D — Test harness updates.**
Bump `@Config(sdk = ...)` across 17 test classes (single search-and-replace). Add `testTagsAsResourceId` scaffolding in `CrumbsTheme`. Wire `verifyRoborazziDebug`/lint/kotlinter into the CI workflow.

**Phase E — Golden regeneration + smoke test.**
Single big commit regenerates all 133 goldens with the new toolchain on the OLD visual design. Manual diff inspection confirms drift is anti-alias / font-hinting only (≤5% changed pixels), not structural. Final emulator smoke run with `android` CLI + `lazylogcat` confirms no runtime regression.

## Step-by-Step Plan

1. **[Spike — Phase A]** Push a throwaway commit (named `spike: verify KSP 2.3.21 + Hilt 2.50`) that bumps only Kotlin to `2.3.21` and KSP to `2.3.21-1.0.x` in `libs.versions.toml` and `build.gradle`. Run `./gradlew clean :app:assembleDebug`. **If it fails**, capture the error, revert the spike, and escalate to `/wf shape` to reconsider Kotlin target. **If it succeeds**, revert the spike (we'll redo properly in Phase C) and proceed. Document outcome in the implement-stage artifact.

2. **[Audit — Phase B]** Grep `@JvmInline` across the codebase. For each hit, verify the inline class's constructor is not called with `private` visibility from outside the file. Document findings; if any consumer would break, write a fix and stage it.

3. **[Audit — Phase B]** Grep `Class.forName\("com.github.jayteealao` across the codebase. For each hit, document the class name and the call site. (`minifyEnabled false` in release config means R8 doesn't actually repackage for now, but the audit documents the risk for future enablement.)

4. **[Audit — Phase B]** Read [AGP 9.1.1 release notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes) and confirm whether `id 'org.jetbrains.kotlin.android'` must be removed. Capture the answer in the implement artifact; act on it in Step 7 below.

5. **[Audit — Phase B]** Check the latest stable `org.jmailen.kotlinter` version on https://github.com/jeremymailen/kotlinter-gradle. Confirm Kotlin 2.3.21 compatibility. Pin to that version in `libs.versions.toml` (introduce a new `kotlinter` key if not present).

6. **[Phase C step 1 — JDK target]** In `app/build.gradle`, `core/pref/build.gradle`, `feature/twitter/build.gradle`, `feature/reddit/build.gradle`, change every `sourceCompatibility JavaVersion.VERSION_1_8` and `targetCompatibility JavaVersion.VERSION_1_8` to `VERSION_17`, and every `kotlinOptions { jvmTarget = '1.8' }` to `'17'`. (Designsystem and models are already on 17.) Commit. Verify `./gradlew :app:assembleDebug` still passes.

7. **[Phase C step 2 — Gradle wrapper]** Bump `gradle/wrapper/gradle-wrapper.properties` `distributionUrl` from `gradle-8.5-bin.zip` to `gradle-9.1.2-bin.zip` (or latest 9.x). Run `./gradlew wrapper --gradle-version 9.1.2` to refresh. Commit. Verify `./gradlew :app:assembleDebug` (which now runs on Gradle 9) still passes.

8. **[Phase C step 3 — AGP]** In root `build.gradle` (or `libs.versions.toml` if AGP version is cataloged there), bump `com.android.application` and `com.android.library` plugin versions from `8.0.2` to `9.1.1`. **Decide based on Step 4 audit**: if confirmed, also remove `id 'org.jetbrains.kotlin.android'` from root and every module's `plugins { }` block. Commit. Verify `./gradlew :app:assembleDebug` passes — if AGP 9 surfaces any deprecation errors, fix them inline.

9. **[Phase C step 4 — Kotlin / KGP / Compose plugin / KSP]** In `libs.versions.toml`, set `kotlin = "2.3.21"`, `ksp = "2.3.21-1.0.x"` (use the exact KSP release matching Kotlin 2.3.21 from https://github.com/google/ksp/releases). In root `build.gradle`, bump the Kotlin and Compose plugin version pins. Also delete the stale `composeUi = "1.3.1"` entry. Commit. Verify `./gradlew :app:assembleDebug` passes.

10. **[Phase C step 5 — Remove deprecated composeOptions]** In `app/build.gradle` and `feature/twitter/build.gradle`, delete the entire `composeOptions { kotlinCompilerExtensionVersion '1.5.15' }` block. The `id 'org.jetbrains.kotlin.plugin.compose'` plugin governs the compiler version. Commit. Verify build.

11. **[Phase C step 6 — Compose BOM + Material3 unification]** In `libs.versions.toml`, add `composeBom = "2026.05.00"` and `composeBomLib = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }`. In every module's `build.gradle` that uses Compose:
    - Replace direct `androidx.compose.ui:*`, `androidx.compose.material:*`, `androidx.compose.runtime:*` version refs with `platform(libs.composeBomLib)` plus unversioned dep declarations.
    - Replace `androidx.compose.material3:material3:1.2.0` (`app`), `1.3.0` (`feature/twitter`), and the standalone `androidx-compose-bom:2024.02.00` (`core/designsystem`) with the new BOM-governed `androidx.compose.material3:material3` (no version).
    - Update the catalog's `compose` bundle to reference BOM-governed entries.
    Commit. Verify build.

12. **[Phase C step 7 — Core/pref compileSdk + JVM]** In `core/pref/build.gradle`, bump `compileSdk 33 → 34`, `targetSdk 33 → 34`. (`JVM 1.8 → 17` was already done in Step 6.) Commit. Verify `./gradlew :core:pref:assembleDebug` passes.

13. **[Phase C step 8 — Coil 3]** In `libs.versions.toml`, add `coil = "3.4.0"` (or latest stable 3.x). Replace `io.coil-kt:coil-compose:2.2.2` (`app`) and `io.coil-kt:coil-compose:2.5.0` (`core/designsystem`) with `io.coil-kt.coil3:coil-compose` + `io.coil-kt.coil3:coil-network-okhttp`. Migrate consumer imports in `GradientImage.kt`, `CrumbsBookmarkCard.kt`, and any other `import coil.*` line to `import coil3.*`. Adapt `ImageRequest`/`AsyncImage` API drift inline (Coil 3 renamed `ImageLoader.Builder` properties; consult https://coil-kt.github.io/coil/upgrading_to_coil3/). Commit. Verify build.

14. **[Phase C step 9 — Roborazzi + Robolectric]** In `libs.versions.toml`, add `roborazzi = "1.37.0"`, `robolectric = "4.16"`. In `core/designsystem/build.gradle`:
    - Bump plugin `io.github.takahirom.roborazzi` to 1.37.0.
    - Bump `roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule` to 1.37.0.
    - Bump `robolectric` to 4.16.
    Commit. Don't regenerate goldens yet — that's Step 20.

15. **[Phase C step 10 — Catalog cleanup]** In `libs.versions.toml`, delete the stale `kotlin = "1.7.10"` key (Kotlin version is now in root `build.gradle`/AGP plugin block; can also move to catalog as `kotlin = "2.3.21"` if we want centralization). Bump other stale catalog entries surfaced by sub-agents:
    - `coroutines = "1.5.2"` → latest stable (~1.10.x)
    - `lifecycle = "2.6.0-alpha01"` → latest stable (~2.8.x)
    - `room = "2.4.3"` → latest stable (~2.6.x or 2.7.x compatible with KSP 2.3.21)
    - `firebase-bom = "32.7.0"` → latest stable
    Update the dependent `libraries` blocks accordingly. Commit. Verify build.

16. **[Phase D step 1 — `@Config` bump]** Single search-and-replace across `core/designsystem/src/test/java/.../components/*.kt`: every `@Config(sdk = [33])` becomes `@Config(sdk = [34])` (17 files). Commit.

17. **[Phase D step 2 — `testTagsAsResourceId` scaffolding]** In `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTheme.kt`, wrap the theme's content slot with a `Box(Modifier.semantics { testTagsAsResourceId = true })` (or apply the semantics modifier directly to the existing top-level container). This is the no-op precondition Maestro relies on; later slices add `Modifier.testTag(...)` on components and screens. Commit.

18. **[Phase D step 3 — CI workflow]** Update `.github/workflows/pr_check.yml`:
    - `setup-java` → JDK 17.
    - `android-actions/setup-android` → SDK 34 (or set `api-level: 34`).
    - Run sequence becomes: `./gradlew clean assembleDebug lintDebug kotlinterCheck :core:designsystem:verifyRoborazziDebug`.
    - Keep the existing status-report step.
    Update `.github/workflows/release.yml`: JDK 17, SDK 34, keep `assembleDebug` scope. Commit.

19. **[Phase E step 1 — Build green checkpoint]** Run the full CI command locally: `./gradlew clean assembleDebug lintDebug kotlinterCheck :core:designsystem:verifyRoborazziDebug`. Some Roborazzi tests will fail (different pixel output under new toolchain) — that's expected. If `assembleDebug`/`lintDebug`/`kotlinterCheck` fail, fix before Step 20. Goldens diff is Step 21's concern.

20. **[Phase E step 2 — Regenerate goldens]** Run `./gradlew :core:designsystem:recordRoborazziDebug`. Verify all 133 PNGs are updated in `core/designsystem/src/test/screenshots/`. Run `./gradlew :core:designsystem:verifyRoborazziDebug` — must be green now. Commit all 133 regenerated PNGs as a single commit: `chore: regenerate roborazzi goldens for new toolchain`. Body of the commit message documents the toolchain delta and the per-pixel drift category.

21. **[Phase E step 3 — Emulator smoke test]** Manual interactive verification (see `## Test / Verification Plan`).

## Test / Verification Plan

### Automated checks

- **Build:** `./gradlew clean :app:assembleDebug` succeeds. Run after every step in Phase C and Phase D.
- **Lint:** `./gradlew lintDebug` succeeds with no new violations relative to a pre-upgrade baseline (capture baseline before Step 6 if useful).
- **Linter:** `./gradlew kotlinterCheck` succeeds.
- **Roborazzi:** `./gradlew :core:designsystem:verifyRoborazziDebug` succeeds against the regenerated goldens (Step 20).
- **CI parity:** After Step 18, the next push to `feat/brutalist-redesign` triggers GitHub Actions; the `pr_check.yml` job must complete green.
- **Spike outcome:** The Phase A spike commit's verification result is documented in `05-implement-toolchain.md` (must be "pass" before mainline work starts).

### Interactive verification (human-in-the-loop)

The slice's three interactive ACs are covered by Steps 21:

- **What to verify:** v1.1 visual design renders identically to pre-upgrade after install on Pixel 6 (API 34) emulator.
- **Platform & tool:** Android — `android` CLI for emulator + install; `lazylogcat` for log capture (both user-confirmed on PATH in `00-index.md` `stack:`).
- **Companion skills:** `android-cli` skill for emulator lifecycle and `installDebug` orchestration; `lazylogcat` skill for filtered log capture.
- **Steps:**
  1. `android avd start --name Pixel_6_API_34 --wait` (boot emulator).
  2. `./gradlew :app:installDebug`.
  3. Start `lazylogcat -t crumbs --output build/toolchain-smoke.log` in a background process.
  4. Launch the app: `adb shell am start -n com.github.jayteealao.crumbs/.MainActivity`.
  5. Navigate manually: Splash → wait for nav → Onboarding (4 pages, swipe through) → Login (skip if access tokens present) → Home → tap each of Twitter / Reddit / All / Map tabs → back. Long-press a bookmark to verify QuickActionMenu opens.
  6. Stop `lazylogcat`.
  7. Side-by-side compare screenshots against a pre-upgrade reference set (capture before Step 6 if desired, or use the maintainer's memory + the existing v1.1 build).
- **Evidence capture:**
  - `build/toolchain-smoke.log` — full session log.
  - Optional `adb shell screencap -p` per screen to `build/toolchain-smoke-screens/` for record-keeping (not strictly required since this is design-stable upgrade, but cheap).
- **Pass criteria:**
  - Zero `ERROR`-level entries in `build/toolchain-smoke.log` from the `com.github.jayteealao.crumbs` process during the happy path. (`WARN` from third-party libs is allowed.)
  - Every screen renders without crashes.
  - Visual identity matches v1.1 (cyan accent, current cut-corner shapes, current typography) — no brutalist drift yet.
- **Maestro testTag smoke:** Optional one-line probe via `maestro studio` to confirm `Modifier.semantics { testTagsAsResourceId = true }` at `CrumbsTheme` is queryable. Not a gate — the formal Maestro flows ship in the `maestro` slice.

### Golden diff review (manual)

- After Step 20, diff every regenerated PNG against its pre-regeneration version (`git diff` shows binary file changes; for inspection, open both in an image viewer side-by-side).
- Acceptable drift: anti-alias edges, font hinting variation, very thin gradient banding differences. **All visuals must still be recognizably v1.1** — same shapes, same colors, same fonts, same layouts.
- Unacceptable drift: missing strokes, repositioned elements, missing text, color shifts (e.g. cyan accent reading as a different hue). If any unacceptable drift surfaces, **STOP** and escalate; do not push the regeneration commit.

## Risks / Watchouts

- **KSP × Kotlin 2.3.21:** Highest-risk single coupling. Mitigated by the Phase A spike. Fallback if KSP support is genuinely broken: drop Kotlin target to 2.2.x stable and re-shape the slice (which preserves Roborazzi 1.37 compat per research).
- **AGP 9 R8 repackaging default:** Audit (Step 3) reduces risk. Note that `minifyEnabled false` in current release config means R8 isn't even repackaging today, so the immediate risk is low; the audit prevents future surprise if minify is later enabled.
- **`org.jetbrains.kotlin.android` plugin removal:** Step 4 audit decides. The research may be wrong — verify against canonical Google docs before trusting. If wrong and we remove it, build fails immediately and we revert; no lasting damage.
- **Coil 3 import-path migration:** `coil` → `coil3` namespace touches more than just `GradientImage` and `CrumbsBookmarkCard`. Mitigation: run `grep -r "import coil\." --include="*.kt"` after Step 13 to catch every site; update systematically before committing.
- **`@Config(sdk = [33])` → `[34]`:** could shift a handful of resource-resolution behaviors that affect golden output. Mitigated by regenerating goldens anyway (Step 20).
- **Gradle 9 daemon JVM requirement:** Step 7 changes the daemon JVM bytecode to require 17. Local dev environments still on JDK 11 will fail until they upgrade. Maintainer is on Windows with `android` CLI installed — confirm JDK 17 is present locally before Phase C step 2.
- **Roborazzi 1.37 + non-default `screenshots/` path:** Confirmed kept. Verify `captureRoboImage` calls still resolve relative paths the same way under Roborazzi 1.37 (release notes don't flag a change, but verify on the first regenerated test).
- **Catalog cleanup (Step 15) ripples:** Bumping Lifecycle, Coroutines, Room, Firebase-BOM is bonus work that could surface compile errors in code that uses APIs deprecated between versions. If any does, narrow the catalog bump to only what compiles cleanly and defer the rest to a follow-up.
- **CI workflow churn:** Step 18 changes are visible to anyone watching CI. If a CI run flakes due to GH Actions runner availability (Windows-specific maintainer, but CI runs on `ubuntu-latest`), retry — don't roll back the workflow.

## Dependencies on Other Slices

**None.** This is the first slice; every other slice (tokens, components, layouts, screens, behaviors, maestro) depends on it. No upstream slice exists.

## Assumptions

- Maintainer has JDK 17 installed locally on Windows; `JAVA_HOME` resolvable. (If not, install before Phase C step 2.)
- `android` CLI's installed SDK includes API 34 system image and Pixel_6 AVD. (If not, `android sdkmanager` installs as part of Step 21.)
- The Phase A spike succeeds — i.e., KSP 2.3.21 is in fact available and works with Hilt 2.50. If wrong, the slice immediately escalates back to shape.
- No active feature work is in flight on `main` that would conflict with the branch's toolchain commits. (Recent commits are stable; assumption holds.)
- The Roborazzi `roborazzi { generateComposePreviewRobolectricTests = true }` DSL (referenced in research) is not yet adopted by `core/designsystem` and we don't adopt it in this slice; it could be a future enhancement.
- Coil 3.4.0+'s `coil3:coil-network-okhttp` artifact name is correct as of latest. Verify against https://coil-kt.github.io/coil/upgrading_to_coil3/ at Step 13.

## Blockers

None.

## Freshness Research

- **Source:** [AGP 9.1.1 release notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes), [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes), [Update Kotlin projects for AGP 9.0 blog](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/).
  Why it matters: Steps 4, 8, 9 — AGP 9 deprecation/removal surface for `applicationVariants`, `dexOptions`, Variants API, and potentially `id 'org.jetbrains.kotlin.android'`.
  Takeaway: Variants API removal does not affect this codebase (we don't use it). `dexOptions` not used. R8 repackaging audit (Step 3) is needed but low-urgency given `minifyEnabled false`. Plugin removal claim must be verified, not blindly applied.

- **Source:** [Kotlin Compatibility Guide 2.3.x](https://kotlinlang.org/docs/compatibility-guide-23.html), [What's new in Kotlin 2.3.20](https://kotlinlang.org/docs/whatsnew2320.html).
  Why it matters: Step 2 audit (`@JvmInline` private constructors), Step 9 Kotlin bump.
  Takeaway: Inline class constructor visibility change is a real breaking change but only affects projects that instantiate inline classes from outside their declaring file. Audit will catch any local occurrences.

- **Source:** [Gradle 9.0.0 release notes](https://docs.gradle.org/9.0.0/release-notes.html), [Upgrading to Gradle 9.0.0](https://docs.gradle.org/current/userguide/upgrading_major_version_9.html).
  Why it matters: Step 7 Gradle bump; daemon JVM requirement.
  Takeaway: Gradle 9 requires JDK 17 to start the daemon. Configuration cache behavior change (incompatible-task fallback removed) is unlikely to affect this repo (no observed custom tasks holding non-serializable state).

- **Source:** [Roborazzi releases](https://github.com/takahirom/roborazzi/releases), [Compose Material3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3).
  Why it matters: Steps 14, 11, 20.
  Takeaway: Roborazzi 1.37.0 + Robolectric 4.16 confirmed compatible. Compose 1.3.1 → 1.11.1 will produce different golden pixels — regeneration is mandatory and expected. Goldens path (`screenshots/` vs default `snapshots/`) is fine; `captureRoboImage` with explicit relative path still works in 1.37.0.

- **Source:** [Upgrading to Coil 3.x](https://coil-kt.github.io/coil/upgrading_to_coil3/).
  Why it matters: Step 13 import-path migration.
  Takeaway: `coil` → `coil3` namespace; new `coil3:coil-network-okhttp` artifact for HTTP. Coexists with Coil 2 if needed but we cut over. `AsyncImage` API mostly compatible; spot-check `ImageRequest.Builder` calls in `GradientImage.kt`.

- **Source:** [Dagger KSP docs](https://dagger.dev/dev-guide/ksp.html), [Hilt release notes](https://developer.android.com/jetpack/androidx/releases/hilt).
  Why it matters: Phase A spike (Step 1).
  Takeaway: Hilt 2.50 supports KSP. KSP × Kotlin 2.3 risk noted — verify with spike before mainline. Fallback to KAPT is mechanically possible but undesirable.

## Revision History

*(none yet — initial plan)*

## Recommended Next Stage

- **Option A (default):** `/wf implement brutalist-redesign toolchain` — proceed to implementation. The plan is execution-ready, the spike-first ordering contains the highest risk, and audits are surgical pre-flight checks rather than open-ended exploration.
  **Compact recommended before proceeding** — research output, alternatives, web searches are noise during code execution. Run `/compact` first; the PreCompact hook preserves workflow state.
- **Option B:** `/wf slice brutalist-redesign` — revisit slice boundaries. Not recommended; this plan held up against the slice definition without surfacing missing scope.
- **Option C:** `/wf shape brutalist-redesign` — revisit shape. Reserved for the Phase A spike failure case (KSP × Kotlin 2.3 incompatibility). Do not invoke unless the spike fails.
