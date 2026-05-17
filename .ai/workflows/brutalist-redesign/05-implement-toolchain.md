---
schema: sdlc/v1
type: implement
slug: brutalist-redesign
slice-slug: toolchain
status: complete
stage-number: 5
created-at: "2026-05-17T00:38:34Z"
updated-at: "2026-05-17T00:38:34Z"
metric-files-changed: 35
metric-lines-added: 174
metric-lines-removed: 152
metric-deviations-from-plan: 11
metric-review-fixes-applied: 0
commit-sha: "f637a52"
tags: [toolchain, kotlin, agp, compose, roborazzi, ci, deviations]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
  slice-def: 03-slice-toolchain.md
  plan: 04-plan-toolchain.md
  siblings: []
  verify: 06-verify-toolchain.md
next-command: wf-verify
next-invocation: "/wf verify brutalist-redesign toolchain"
---

# Implement: toolchain

## Summary of Changes

The toolchain slice landed in **six commits** on `feat/brutalist-redesign`, taking the project from Gradle 8.5 / AGP 8.0.2 / Kotlin 2.0.21 / Compose 1.3.x / Hilt 2.50 / Room 2.4.3 / Coil 2 / Roborazzi 1.7.0 / Robolectric 4.14.1 / Material3 1.2/1.3 / compileSdk 34 → Gradle 9.3.1 / AGP 9.1.1 / Kotlin 2.2.10 / Compose 1.11.1 (via BOM 2026.05.00) / Hilt 2.59.2 / Room 2.8.4 / Roborazzi 1.60.0 / Robolectric 4.16 / Material3 1.4.0 / compileSdk 35. JDK target bumped 1.8 → 17 across the four lagging modules; the AGP `android.kotlinOptions {}` DSL migrated to top-level `kotlin { compilerOptions {} }`. CI workflows now run on JDK 17 / SDK 35 and gate on `lintDebug` + `:core:designsystem:verifyRoborazziDebug` in addition to `assembleDebug`. All 133 Roborazzi goldens regenerated against the new chain in a single commit; `verifyRoborazziDebug` is green against the recorded set.

Six application-code edits were forced by the toolchain bump (recorded as deviations below): one Room schema fix (a nullable `@Insert` parameter Room 2.8.4 rightly rejected), one Material3 ripple migration (Compose 1.11 removed `rememberRipple`), one `testTagsAsResourceId` scaffolding edit in `CrumbsTheme`, three deletions of dead buildscript machinery (kotlinter / kotlin.jvm / com.twitter.compose.rules-ktlint stub) that were trapping `kotlin-compiler-embeddable` on the buildscript classpath and causing KGP's `BuildTimeMetric` inheritance error.

## Files Changed

### Build configuration (toolchain bumps)
- `gradle/wrapper/gradle-wrapper.properties` — `distributionUrl` 8.5 → 9.3.1.
- `build.gradle` (root) — AGP 8.0.2 → 9.1.1; Kotlin/Compose plugin/KSP/Hilt versions advanced; remove `id 'org.jetbrains.kotlin.android'` (AGP 9 has built-in Kotlin); remove dead `kotlin.jvm`, `kotlinter`, and `com.twitter.compose.rules:ktlint` buildscript entries that pulled in conflicting `kotlin-compiler-embeddable`.
- `gradle.properties` — add `android.disallowKotlinSourceSets=false` (AGP 9 built-in Kotlin opt-out for KSP's still-using-`kotlin.sourceSets` source registration).
- `gradle/libs.versions.toml` — drop stale `kotlin = "1.7.10"` / `composeUi = "1.3.1"`; introduce `composeBom = "2026.05.00"`, `roborazzi = "1.60.0"`, `robolectric = "4.16"`; restructure compose libraries as BOM-governed unversioned entries; promote roborazzi-{core,compose,junit-rule}, robolectric, and compose-bom to the catalog; bump `room = "2.8.4"`.
- `app/build.gradle` — drop `id 'org.jetbrains.kotlin.android'`; drop `composeOptions { kotlinCompilerExtensionVersion '1.5.15' }` (governed by `kotlin.plugin.compose`); JDK 1.8 → 17; migrate `kotlinOptions {}` → top-level `kotlin { compilerOptions {} }`; adopt `platform(libs.compose.bom)`; replace material3:1.2.0 direct pin with BOM-governed `libs.compose.material3`; Hilt 2.50 → 2.59.2; switch test/debug compose libs to BOM-governed catalog entries; compileSdk/targetSdk 34 → 35.
- `core/designsystem/build.gradle` — drop `id 'org.jetbrains.kotlin.android'`; remove `kotlinOptions {}`, add top-level kotlin block; Roborazzi plugin 1.7.0 → 1.60.0; promote roborazzi/robolectric to catalog refs; replace standalone `compose-bom:2024.02.00` with `libs.compose.bom`; compileSdk/targetSdk 34 → 35.
- `core/models/build.gradle` — drop `id 'org.jetbrains.kotlin.android'`; remove `kotlinOptions {}`, add top-level kotlin block; replace standalone `compose-bom:2024.02.00` with `libs.compose.bom`; compileSdk/targetSdk 34 → 35.
- `core/pref/build.gradle` — drop `id 'org.jetbrains.kotlin.android'`; JDK 1.8 → 17; migrate `kotlinOptions {}` → top-level `kotlin {}` block; Hilt 2.50 → 2.59.2; compileSdk/targetSdk 33 → 35.
- `feature/twitter/build.gradle` — drop `id 'org.jetbrains.kotlin.android'`; drop `composeOptions { kotlinCompilerExtensionVersion '1.5.15' }`; JDK 1.8 → 17; migrate `kotlinOptions {}` → top-level `kotlin {}` block; adopt `platform(libs.compose.bom)`; replace material3:1.3.0 direct pin with BOM-governed `libs.compose.material3`; Hilt 2.50 → 2.59.2; compileSdk/targetSdk 34 → 35.
- `feature/reddit/build.gradle` — drop `id 'org.jetbrains.kotlin.android'`; JDK 1.8 → 17; migrate `kotlinOptions {}` → top-level `kotlin {}` block; adopt `platform(libs.compose.bom)`; replace material3:1.2.0 direct pin with BOM-governed `libs.compose.material3`; Hilt 2.50 → 2.59.2; compileSdk/targetSdk 34 → 35.

### Application code (forced by toolchain)
- `feature/twitter/src/main/java/.../data/TweetDao.kt` — drop nullable `pollIds: PollIds?` parameter from `insertTweetEntities(...)` (Room 2.8.4 enforces non-null `@Insert` parameters; an existing `insertPollId(pollIds: PollIds)` already exists for the per-row case).
- `feature/twitter/src/main/java/.../data/Repository.kt` — caller adapts: call `tweetDao.insertPollId(it)` separately via `tweetEntities.pollIds?.let { ... }`.
- `core/designsystem/src/main/java/.../components/CrumbsBottomNav.kt` — migrate `import androidx.compose.material.ripple.rememberRipple` + `rememberRipple(bounded = ..., color = ...)` → `import androidx.compose.material3.ripple` + `ripple(bounded = ..., color = ...)`. Compose 1.11.1 marks `rememberRipple` deprecated as an *error*, not a warning, so this is a forced edit.
- `core/designsystem/src/main/java/.../theme/CrumbsTheme.kt` — wrap content slot in `Box(Modifier.semantics { testTagsAsResourceId = true })`; `@OptIn(ExperimentalComposeUiApi::class)`. Maestro scaffolding for later slices. No visual impact.
- `core/designsystem/src/test/java/.../components/*Test.kt` × 17 — `@Config(sdk = [33])` → `[34]` via sed-replace; aligns Robolectric runtime SDK with compileSdk.

### Test goldens
- `core/designsystem/src/test/screenshots/*.png` × 133 — regenerated against the new chain in a single dedicated commit. `verifyRoborazziDebug` is green against the recorded set.

### CI / release workflows
- `.github/workflows/pr_check.yml` — JDK 21 → 17; SDK 33 / build-tools 33.0.2 → 35 / 35.0.0; gradle invocation now `clean assembleDebug lintDebug :core:designsystem:verifyRoborazziDebug`.
- `.github/workflows/release.yml` — matching JDK 17 / SDK 35 bumps.

## Shared Files (also touched by sibling slices)

None. `toolchain` is slice 1; no siblings exist yet. Downstream slices (`tokens`, `components`, etc.) will inherit this state.

## Notes on Design Choices

- **Locked-decision divergence — Kotlin 2.3.21 → 2.2.10.** Plan locked Kotlin 2.3.21, but AGP 9.1.1's built-in Kotlin compiler bundles KGP **2.2.10**. Mixing project-declared Kotlin/Compose plugin 2.3.21 with AGP's bundled 2.2.10 puts two KGPs on the buildscript classpath, producing `IncompatibleClassChangeError: class GradleBuildTime can not implement BuildTime, because it is not an interface`. User explicitly authorized downgrading the locked Kotlin to 2.2.10 to match AGP. No 2.3-specific language features are used in this codebase (verified by the Phase B `@JvmInline` audit), so the downgrade is lossless.
- **AGP 9's "built-in Kotlin"** is more than removing one plugin line — it's a posture shift. The bundled Kotlin compiler version is now coupled to the AGP version; the legacy `android.kotlinOptions {}` DSL is gone; KSP still uses `kotlin.sourceSets` so an opt-out (`android.disallowKotlinSourceSets=false`) is needed until KSP migrates. All three are deviations from a plan that pre-dated AGP 9 reality.
- **Roborazzi 1.37.0 → 1.60.0.** Plan target 1.37.0 predates AGP 9 support; 1.56.0+ migrated off the deprecated `TestedExtension` API. 1.60.0 is the latest stable.
- **`kotlinter` deferred.** Plan included `kotlinter 5.4.2` in the CI gate. Reality: kotlinter (even 5.4.2) still pulls `kotlin-compiler-embeddable` onto the buildscript classpath, conflicting with KGP 2.2.10. The Kotlin team's recommended remediation is for plugin authors to isolate compiler usage via Gradle Workers API ([kotl.in/gradle/internal-compiler-symbols](https://kotlinlang.org/docs/whatsnew21.html#compiler-symbols-hidden-from-the-kotlin-gradle-plugin-api)); kotlinter hasn't shipped that yet. Deferred to a follow-up. CI gates `lintDebug` + `verifyRoborazziDebug` ship without `kotlinterCheck`.
- **Coil 3 deferred.** Plan called for Coil 2 → 3 in this slice. Reality: `feature/twitter/src/main/java/.../components/TwitterCard.kt` uses `com.commit451.coil-transformations` (BlurTransformation, PixelationFilterTransformation, SwirlFilterTransformation) which is a Coil 2-only library — no Coil 3 port exists. Migrating Coil 3 here would either drop the decorative blur/pixelation/swirl filters (visible change, out of toolchain scope) or require rewriting them against Coil 3's transformation API. Deferred to the `components` slice where TwitterCard's image surface is being rewritten anyway.
- **Per-step bisectability traded for coupled commits.** Plan ordered JDK→Gradle→AGP→Kotlin as separate commits. Reality: Gradle 9.3.1 requires AGP ≥ 8.2.2 (so AGP must move with Gradle); AGP 9 + Kotlin 2.2.10 + Hilt 2.59.2 + Room 2.8.4 all transitively required each other and can't reach a green intermediate state separately. After user authorization, commits 1 and 2 each bundle a coordination knot; commits 3-6 stay one-concern-each.
- **Why compileSdk went all the way to 35 (plan said 34).** Compose BOM 2026.05.00 brings Compose 1.11.1, which declares `compileSdk ≥ 35` as a hard requirement (AGP fails the check, not just warns). compileSdk 35 is also AGP 9.1.1's recommended floor.

## Visual Contract Honored

N/A — `02c-craft.md` does not exist for this slice. The slice is purely toolchain; visual contract enters in the `tokens`/`components`/`screens` slices.

## Deviations from Plan

11 in total. Listed roughly in order of impact:

1. **Kotlin 2.3.21 → 2.2.10** (locked-decision change). User-authorized via in-conversation `AskUserQuestion`. Driven by AGP 9.1.1's bundled KGP version.
2. **KSP 2.3.21-1.0.x (plan) → 2.2.10-2.0.2.** KSP versioning changed in KSP2; tracks Kotlin patch version directly.
3. **Gradle 9.1.2 (plan) → 9.3.1.** AGP 9.1.1 requires Gradle ≥ 9.3.1.
4. **Roborazzi 1.37.0 (plan) → 1.60.0.** AGP 9 compat required ≥ 1.56.0.
5. **Hilt 2.50 (kept by plan) → 2.59.2.** Forced by AGP 9 (Hilt 2.59 first version supporting AGP 9 BaseExtension removal).
6. **Room 2.4.3 (kept by plan) → 2.8.4.** Forced by KSP2 (Room 2.4.3 triggered `unexpected jvm signature V` under KSP 2.2.10-2.0.2).
7. **compileSdk 34 (plan) → 35.** Forced by Compose 1.11.1 in BOM 2026.05.00.
8. **`kotlinter` removed from project (plan kept 3.12.0 → 5.4.2).** Deferred — see Notes.
9. **Coil 2 → 3 (planned) deferred to `components` slice.** See Notes — `coil-transformations` blocks the migration.
10. **`@Config(sdk = [33])` → `[34]` (plan said).** Done. (Not actually a deviation; listed for completeness.)
11. **App-code edits forced by the toolchain bumps.** TweetDao `@Insert` nullable parameter removed; `rememberRipple` migrated to `material3.ripple`; `testTagsAsResourceId` scaffolding added (planned). Plan's intent was "no visual or behavior changes during toolchain"; the first two are technically forced compile-time fixes, not behavior changes.

## Anything Deferred

- **Phase E-3 — emulator smoke test (Step 21 of plan).** Manual / interactive: requires `android avd start --name Pixel_6_API_34 --wait` (note: API 34 emulator still fine for runtime even though compileSdk is 35), `./gradlew :app:installDebug`, `lazylogcat -t crumbs --output build/toolchain-smoke.log`, manual nav through Splash → Onboarding → Login → Home → all 4 tabs → back, long-press a bookmark. The plan's pass criteria: zero `ERROR`-level entries from `com.github.jayteealao.crumbs`; v1.1 visual identity preserved. Surfaced for the user in the verify stage.
- **Manual golden-drift inspection.** Plan called for a per-PNG visual diff against pre-regen versions, with acceptable drift = anti-alias / hinting / banding and unacceptable = repositioned elements / color shifts. `verifyRoborazziDebug` is tautologically green against the just-recorded set, so this is a *visual* check the maintainer needs to make against the pre-bump v1.1 build (e.g., side-by-side screenshot comparison against a checkout of `main`).
- **`kotlinter` re-integration** (any subsequent slice or a dedicated follow-up). Either wait for kotlinter to ship Workers-API isolation, or wrap it in an isolated configuration ourselves.
- **Coil 3 migration** — moved to `components` slice, which already plans to rewrite `CrumbsBookmarkCard` and `GradientImage` and may absorb the `TwitterCard` transformation rework in the same pass.
- **Other catalog cleanup** (coroutines 1.5.2 → latest, lifecycle 2.6.0-alpha01 → stable, firebase-bom 32.7.0 → latest, accompanist 0.22.0-rc → stable/replacement). Plan called these out as bonus catalog cleanup; left untouched here so the toolchain slice's commit history stays focused on the load-bearing bumps. Surface for any later slice that wants to address them.

## Known Risks / Caveats

- **JDK 17 vs JBR 21 host divergence.** Local build runs on JDK 21 (JetBrains Runtime bundled with Android Studio); CI now runs on JDK 17. AGP 9.1.1 documents JDK 17 minimum, so both are above the floor and bytecode targets are 17. No issue expected, but worth knowing if a JDK-21-specific incompat surfaces in CI.
- **`android.disallowKotlinSourceSets=false` is a known-temporary opt-out.** AGP 10 (mid-2026 per Google's roadmap) will remove the opt-out. KSP must migrate to `android.sourceSets` by then. Track upstream KSP migration progress; flip the property back to `true` (or delete it) once safe.
- **Compose 1.11.1 deprecation warnings are tolerated, not fixed.** `Icons.Filled.Logout` → `Icons.AutoMirrored.Filled.Logout` and a handful of `createAndroidComposeRule` v1 → v2 migrations remain as warnings. Not blocking; pick up in a later slice as cleanup.
- **`kotlin-compiler-embeddable` warning may resurface** if any later slice introduces a plugin whose author hasn't isolated compiler symbols. Sentinel: KGP's "is present in the build classpath" warning; reproduce the conflict surfaced in this slice.
- **Goldens visual fidelity is asserted by `verifyRoborazziDebug` round-trip only.** A human visual review against the pre-toolchain build is recommended before the toolchain branch merges.

## Freshness Research

Captured at commit time:

- AGP 9.1.1 release notes (`developer.android.com/build/releases/agp-9-1-0-release-notes`) — Gradle 9.3.1 floor, JDK 17 minimum, SDK Build Tools 36.0.0, support window through Android API 37.
- AGP 9 built-in Kotlin migration (`developer.android.com/build/migrate-to-built-in-kotlin`) — `kotlinOptions {}` → `kotlin { compilerOptions {} }` DSL move; `android.disallowKotlinSourceSets` opt-out.
- AGP 9 + project Kotlin guidance (`blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/`) — confirms `org.jetbrains.kotlin.android` must be removed; describes built-in Kotlin posture.
- Kotlin 2.1 compiler symbols hidden (`kotlinlang.org/docs/whatsnew21.html#compiler-symbols-hidden-from-the-kotlin-gradle-plugin-api`) — describes the `kotlin-compiler-embeddable` warning and the Workers API isolation remediation kotlinter needs.
- google/ksp releases — KSP 2.2.10-2.0.2 for Kotlin 2.2.10 confirmed.
- google/dagger releases — Hilt 2.59 first version with AGP 9 support; we adopt 2.59.2 (latest patch).
- google/ksp issue #2177 — "unexpected jvm signature V" with KSP2 + old Room; fix is Room ≥ 2.7.
- androidx room releases — 2.8.4 latest stable.
- coil-kt/coil 3.4.0 release; upgrade guide (`coil-kt.github.io/coil/upgrading_to_coil3/`) confirms namespace move and the `coil-network-okhttp` artifact addition — informed the decision to *defer* Coil 3 here.
- takahirom/roborazzi releases — 1.56.0 first AGP 9 compatible; 1.60.0 latest.
- jeremymailen/kotlinter-gradle releases — 5.4.2 latest, AGP 9 / Kotlin 2.3 supported, but still triggers `kotlin-compiler-embeddable` warning until upstream isolates.

## Recommended Next Stage

- **Option A (default):** `/wf verify brutalist-redesign toolchain` — run the full CI command locally, then the emulator smoke test with `android` + `lazylogcat`. The slice's verification gates are: `assembleDebug` green ✅ (verified), `lintDebug` green (CI will run on next push; not yet verified locally), `verifyRoborazziDebug` green ✅ (verified), interactive smoke test passes (deferred for human). **Compact recommended** before verify — the toolchain research / cascade / web-search chatter is noise for verification.
- **Option B:** `/wf review brutalist-redesign` — skip verify (the build is already green and tests pass against the regenerated goldens) and go straight to slug-wide review. Best if the maintainer plans to do the emulator smoke as part of their own pre-merge ritual rather than as a workflow gate.
- **Option C:** `/wf plan brutalist-redesign tokens` — kick off planning the next slice (`tokens`) while toolchain is fresh. The rolling-plan strategy in `04-plan.md` explicitly recommends drafting the next plan against current observed reality; with AGP 9 + Compose 1.11.1 + Material3 1.4 now real, the `tokens` plan can reflect the actual `Font(...)` / `MaterialTheme` / `dynamic*ColorScheme` API surface rather than guessing.
