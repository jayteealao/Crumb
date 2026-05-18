---
command: /review supply-chain
session_slug: brutalist-redesign
date: 2026-05-18
scope: slug-wide
target: git diff main...HEAD
paths: gradle/libs.versions.toml, build.gradle, app/build.gradle, core/*/build.gradle, feature/*/build.gradle, gradle/wrapper/gradle-wrapper.properties, gradle.properties, .github/workflows/pr_check.yml, .github/workflows/release.yml, core/designsystem/src/main/res/font/*.ttf
---

# Supply Chain Security Review — brutalist-redesign

**Reviewed:** slug-wide / `git diff main...HEAD`
**Date:** 2026-05-18
**Reviewer:** Claude Code (supply-chain dimension)

---

## 0) Scope, Context, and Dependency Policy

**What was reviewed:**
- Scope: slug-wide (`git diff main...HEAD`)
- Dependency files: `gradle/libs.versions.toml`, root `build.gradle`, 6 module `build.gradle` files, `gradle-wrapper.properties`, `gradle.properties`
- CI/CD: `.github/workflows/pr_check.yml`, `.github/workflows/release.yml`
- Binary assets: 3 IBM Plex Mono TTF files added, 1 Funnel Display variant removed/replaced
- Firebase config: `app/google-services.json` (unchanged structure, key pre-existing)

**Deployment context:**
- Android app distributed via GitHub Releases (APK) and potentially Play Store
- Build resolves from Google Maven, Maven Central, and JitPack
- No Docker — not applicable
- No lockfile mechanism for Gradle/Maven (standard for Android projects; Gradle dependency verification not yet enabled)

**Dependency policy (inferred):**
- Version pinning: Mix of pinned versions and BOM-managed versions; Hilt hardcoded in all 5 modules
- CVE threshold: No automated scanning configured in CI
- License allowlist: Not formally defined; IBM Plex (OFL-1.1), Funnel Display (OFL-1.1) fonts in-tree

**Assumptions:**
- `app/google-services.json` containing a live Firebase API key is intentional (standard Android Firebase pattern); key was pre-existing on `main`
- JitPack is an approved registry for this project (pre-existing on `main`)
- No Gradle dependency verification (`verification-metadata.xml`) is a known gap, not a new regression

---

## 1) Executive Summary

**Merge Recommendation:** APPROVE_WITH_COMMENTS

**Rationale:**
The toolchain bump is well-structured: versions are pinned to specific releases, no `+`/`latest.release` wildcards, the BOM adoption correctly removes hardcoded Compose artifact versions. The main supply-chain risks are carry-forward issues (stale ext properties, JitPack dependency, no CI CVE scan) rather than regressions introduced by this branch. Three issues are new or worsened by this diff and warrant fixes before a production release though not a hard block on merge.

**Critical Supply Chain Risks (BLOCKER):**
— None identified.

**High-Risk Issues:**
1. **SUPPLY-01**: `funnel_display_semibold.ttf` on `main` was a corrupt file (GitHub HTML page masquerading as binary). This branch deletes it, but it was committed to git history and is still accessible via `git show`. Clean-up of history is optional; the asset is no longer shipped.
2. **SUPPLY-02**: Firebase API key `AIzaSyAUXoD37Cy8ghlz7dGggEC9w657nHLbo9U` is committed in `app/google-services.json` and tracked in git. This is pre-existing on `main` and unchanged, but the key is now publicly addressable via the repo.

**Overall Supply Chain Posture:**
- Dependency Hygiene: Good (BOM adoption, specific pins, no wildcards)
- Vulnerability Management: Reactive / Missing (no `dependencyCheck`, no `gradle --dependency-verification`)
- Build Security: Adequate (no custom registries added, HTTPS everywhere, no curl|sh)
- Provenance: Partial (CI Actions pinned to tag only, not SHA)

**Scan Results (static analysis only — no runtime audit tool available):**
- CVEs found: 0 confirmed (no audit tool run; several stale deps noted below)
- Malicious packages: 0
- Risky install scripts: 0
- Unpinned images: N/A (no Docker)

---

## 2) Dependency Changes

### Added / Upgraded Libraries

| Library | Old Version | New Version | Source | License | Risk |
|---|---|---|---|---|---|
| AGP | 8.0.2 | 9.1.1 | Google Maven | Apache-2.0 | Low — major bump, breaking changes well-documented |
| Kotlin (plugin) | 2.0.21 | 2.2.10 | Gradle Plugin Portal | Apache-2.0 | Low |
| KSP | 2.0.21-1.0.28 | 2.2.10-2.0.2 | Gradle Plugin Portal | Apache-2.0 | Low |
| Gradle wrapper | 8.5 | 9.3.1 | services.gradle.org | Apache-2.0 | Low — HTTPS, official |
| Hilt | 2.50 | 2.59.2 | Google Maven | Apache-2.0 | Low |
| Room | 2.4.3 | 2.8.4 | Google Maven | Apache-2.0 | Low |
| Compose BOM | 2024.02.00 | 2026.05.00 | Google Maven | Apache-2.0 | Low |
| Roborazzi | 1.7.0 | 1.60.0 | Maven Central | Apache-2.0 | Low — dev/test only |
| Robolectric | 4.14.1 | 4.16 | Maven Central | MIT | Low — test only |
| kotlinx-collections-immutable | (new) | 0.3.8 | Maven Central | Apache-2.0 | Low |
| IBM Plex Mono (3 weights) | (new) | N/A | Binary TTF in-tree | OFL-1.1 | Low — see SUPPLY-03 |
| paging-compose | 1.0.0-alpha17 | 3.3.6 | Google Maven | Apache-2.0 | Low — stable release |

### Removed Libraries
| Library | Notes |
|---|---|
| `org.jmailen.kotlinter` | Removed cleanly — reduces attack surface |
| `com.twitter.compose.rules:ktlint` | Removed — reduces JitPack dependency surface |
| `accompanist-pager` / `accompanist-pager-indicators` | Removed — replaced by Compose-native pager |
| Funnel Display Semibold | Removed (was corrupt HTML file on `main`, see SUPPLY-01) |

### No Open-ended Version Ranges
No `+`, `latest.release`, or `SNAPSHOT` references found in any version declaration on this branch. All Compose artifacts correctly have no version attribute (governed by BOM). ✅

---

## 3) Findings Table

| ID | Severity | Confidence | Category | Location | Issue |
|---|---|---|---|---|---|
| SUPPLY-01 | HIGH | High | Corrupt binary / provenance | `main` git history | `funnel_display_semibold.ttf` was an HTML page committed as a TTF |
| SUPPLY-02 | HIGH | High | Secret in repo | `app/google-services.json:20` | Live Firebase API key committed to tracked file |
| SUPPLY-03 | MED | High | Font provenance | `core/designsystem/src/main/res/font/` | IBM Plex Mono TTFs added with no license file alongside them |
| SUPPLY-04 | MED | High | Version drift / no catalog entry | `build.gradle` (root) `ext` block | Stale `room_version = '2.4.3'` ghost variable; modules use 2.8.4 via catalog — confusion risk |
| SUPPLY-05 | MED | Med | Version not centralized | All 5 module `build.gradle` files | Hilt 2.59.2 hardcoded in every module rather than in `libs.versions.toml` |
| SUPPLY-06 | MED | Med | CI integrity | `.github/workflows/pr_check.yml`, `release.yml` | GitHub Actions pinned to mutable tags (`@v3`, `@v4`) not SHA digests |
| SUPPLY-07 | LOW | High | JitPack dependency | `gradle/libs.versions.toml:retrofit-sandwich` | `com.github.skydoves:sandwich:1.3.1` resolved from JitPack; no integrity hash |
| SUPPLY-08 | LOW | Med | No dependency audit in CI | `.github/workflows/pr_check.yml` | No `dependencyCheckAnalyze` or OWASP step; CVE regressions would not be caught |
| SUPPLY-09 | LOW | Low | Stale lifecycle alpha | `libs.versions.toml` | `lifecycle-runtime-compose = "2.6.0-alpha03"` and `lifecycleRuntimeCompose = "2.5.1"` are far behind stable |
| SUPPLY-10 | NIT | High | media3 beta | `app/build.gradle`, `feature/twitter/build.gradle` | `media3-exoplayer:1.0.0-beta02` pre-stable; current stable is 1.x |

**Findings Summary:**
- BLOCKER: 0
- HIGH: 2
- MED: 4
- LOW: 3
- NIT: 1

---

## 4) Findings (Detailed)

### SUPPLY-01: Corrupt Font Binary in Git History [HIGH]

**Location:** `main` branch — `core/designsystem/src/main/res/font/funnel_display_semibold.ttf` (deleted in this diff)

**Evidence:**
The diff of this file shows the content was a GitHub HTML page (DOCTYPE html, CSS links, etc.) rather than binary TTF data — approximately 1,447 lines of HTML. This was almost certainly downloaded from a browser URL rather than the actual GitHub Raw or Google Fonts CDN endpoint.

**Risk:**
- The file was committed and is permanently in git history
- At runtime on `main` the app would attempt to load an HTML file as a font; the file would be silently ignored by Android's font loader (no crash, no data leak)
- The corrupt file is **deleted** in this branch — the risk is resolved for future builds
- However, the git history entry for this file persists; if someone `git show`s it or checks out old commits, they get the HTML content which is not sensitive

**Impact:** Low for the current branch (file is deleted), Medium for git history hygiene.

**Severity:** HIGH (provenance failure on `main`; resolved by this branch)
**Confidence:** High

**Remediation:**
The deletion in this branch is sufficient for forward safety. Optionally, use `git filter-repo` to scrub the corrupt file from history if binary integrity of all committed assets is a policy requirement. The replacement weights (bold/medium/regular) should be downloaded from the official Google Fonts GitHub repo:
```
https://github.com/Fonthausen/FunnelDisplay/releases
# or via Google Fonts API raw CDN
```
Verify SHA256 checksum against the font foundry release.

---

### SUPPLY-02: Firebase API Key Committed to Tracked File [HIGH]

**Location:** `app/google-services.json:20`

**Evidence:**
```json
"api_key": [
  {
    "current_key": "AIzaSyAUXoD37Cy8ghlz7dGggEC9w657nHLbo9U"
  }
]
```
Project: `crumbs-a4fdb`, project number `1032153206630`.

**Context:** This key is **pre-existing on `main`** — not introduced by this branch. However, the `google-services.json` is not in `.gitignore`, meaning it is tracked and the key is publicly exposed if this repo is or becomes public.

**Risk:**
- Firebase API keys for Android are designed to be restricted by package name and SHA-1 certificate fingerprint in the Firebase console. If restrictions are properly configured, the exposed key has limited blast radius.
- If restrictions are NOT configured, an attacker could use the key to read/write Firestore, abuse Firebase Auth, or incur billing charges.
- The Firestore backfill feature (added in a preceding commit) increases the surface area for this key.

**Impact:** Potential unauthorized Firestore reads/writes or billing abuse if key restrictions are incomplete.

**Severity:** HIGH
**Confidence:** High

**Remediation:**
1. Verify in Firebase Console → Project Settings → API key restrictions that the key is restricted to package `com.github.jayteealao.crumbs` with your release SHA-1.
2. Consider adding `app/google-services.json` to `.gitignore` and distributing it via CI secrets (inject at build time) — standard pattern for public repos.
3. If repo is or will be public, rotate the key after adding restrictions.

---

### SUPPLY-03: IBM Plex Mono TTF Files — No License File Present [MED]

**Location:** `core/designsystem/src/main/res/font/ibm_plex_mono_bold.ttf`, `ibm_plex_mono_medium.ttf`, `ibm_plex_mono_regular.ttf` (added in this branch)

**Evidence:**
Three new TTF files are added. No `OFL.txt` or `LICENSE` file is present alongside them in `core/designsystem/src/main/res/font/` or in `core/designsystem/`.

**IBM Plex Mono License:** SIL Open Font License 1.1 (OFL-1.1). OFL-1.1 requires:
> "The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Font Software."

The OFL also requires that if the font is bundled in software products, the license text be included (or the copyright notice be preserved). Not including `OFL.txt` is a license compliance violation.

**Funnel Display** (existing bold/medium/regular weights) has the same issue — also OFL-1.1, no license file present.

**Severity:** MED
**Confidence:** High

**Remediation:**
Add OFL license files to the font directory:
1. Download `OFL.txt` from https://github.com/IBM/plex (for IBM Plex Mono)
2. Download `OFL.txt` from https://github.com/Fonthausen/FunnelDisplay (for Funnel Display)
3. Place them at `core/designsystem/src/main/res/font/OFL-IBMPlexMono.txt` and `OFL-FunnelDisplay.txt`
   (or in a top-level `THIRD_PARTY_LICENSES` file).

Verify font file provenance by comparing SHA256 against the official GitHub releases.

---

### SUPPLY-04: Stale `room_version` Ghost Variable in Root `build.gradle` [MED]

**Location:** `build.gradle` (root), `ext` block

**Evidence:**
```groovy
ext {
    room_version = '2.4.3'   // ← stale; actual Room is 2.8.4 via libs.versions.toml
    kotlin_version = '2.2.10'
    ...
}
```

**Risk:**
Any module that accidentally references `$room_version` instead of `libs.versions.room` will silently pull Room 2.4.3, which does not support KSP2 V signature and would cause a build failure — or, worse, might resolve differently if a future refactor adds the old variable to a module `implementation` string.

**Severity:** MED
**Confidence:** High

**Remediation:**
Remove the stale `room_version` ext property from `build.gradle`:
```diff
 ext {
     compose_ui_version = '1.3.0'
     kotlin_version = '2.2.10'
-    room_version = '2.4.3'
     ...
 }
```

---

### SUPPLY-05: Hilt Version Hardcoded in 5 Module `build.gradle` Files [MED]

**Location:** `app/build.gradle`, `core/data/build.gradle`, `core/pref/build.gradle`, `feature/reddit/build.gradle`, `feature/twitter/build.gradle`

**Evidence:**
```groovy
implementation "com.google.dagger:hilt-android:2.59.2"
ksp "com.google.dagger:hilt-compiler:2.59.2"
```
This version string is repeated verbatim in 5 files with no entry in `libs.versions.toml`.

**Risk:**
A future Hilt security patch requires touching 10 lines across 5 files. Version skew between modules (e.g., one module on 2.59.2 and another upgraded to 2.60.x) would cause a Hilt classloader crash at runtime that is hard to diagnose.

**Severity:** MED
**Confidence:** High

**Remediation:**
Add to `libs.versions.toml`:
```toml
[versions]
hilt = "2.59.2"

[libraries]
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
```
Then replace all 10 hardcoded strings with `libs.hilt.android` / `libs.hilt.compiler`.

---

### SUPPLY-06: GitHub Actions Pinned to Mutable Tags, Not SHA Digests [MED]

**Location:** `.github/workflows/pr_check.yml`, `.github/workflows/release.yml`

**Evidence:**
```yaml
uses: actions/checkout@v4            # mutable tag
uses: actions/setup-java@v4          # mutable tag
uses: gradle/actions/setup-gradle@v3 # mutable tag
uses: android-actions/setup-android@v3 # mutable tag — third-party
uses: actions/github-script@v7       # mutable tag
uses: orhun/git-cliff-action@v4      # mutable tag — third-party
uses: softprops/action-gh-release@v2 # mutable tag — third-party
uses: actions/upload-artifact@v4     # mutable tag
```

**Risk:**
Mutable tags can be moved by the action maintainer. A compromised tag (`v4` redirected to a malicious SHA) would execute arbitrary code in CI with `contents: write` / `pull-requests: write` permissions. `android-actions/setup-android@v3` and `softprops/action-gh-release@v2` are **third-party** actions with elevated risk. `orhun/git-cliff-action@v4` is also third-party.

**Severity:** MED
**Confidence:** Med

**Remediation:**
Pin each action to its current SHA. Example:
```yaml
uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4.2.2
uses: actions/setup-java@c5195efecf7bdfc987ee8bae7a71cb8b11521c00 # v4.7.1
uses: gradle/actions/setup-gradle@94bef7e3bf06d2ff95e67e2a3efdff0e...  # v3.x
uses: android-actions/setup-android@07dc017a59f479a4d55d992cae7030  # v3.x
```
Use a tool like `pin-github-actions` or Dependabot's `pinned-actions` policy.

---

### SUPPLY-07: `retrofit-sandwich` Resolved from JitPack [LOW]

**Location:** `gradle/libs.versions.toml:retrofit-sandwich`

**Evidence:**
```toml
retrofit-sandwich = { module = "com.github.skydoves:sandwich", version = "1.3.1" }
```
`settings.gradle`:
```groovy
maven { url "https://jitpack.io" }
```

**Risk:**
JitPack builds artifacts on-demand from GitHub commits. The integrity of the artifact depends on the JitPack build infrastructure and the GitHub repo state at build time. There are no published checksums to verify against. JitPack has been a vector for supply-chain issues in the Android ecosystem. Sandwich 1.3.1 was published in 2022 and is not recently maintained.

Note: The entry is commented out in `feature/reddit/build.gradle` (`// implementation "com.github.skydoves:sandwich:1.3.1"`), suggesting it may be transitional dead code.

**Severity:** LOW
**Confidence:** High

**Remediation:**
`skydoves/sandwich` has been published to Maven Central since version 1.3.5+ as `com.github.skydoves:sandwich`. Replace the JitPack coordinate with the Maven Central one:
```toml
retrofit-sandwich = { module = "com.github.skydoves:sandwich", version = "2.0.9" }
```
If the dependency is no longer used, remove it from `libs.versions.toml` and the `retrofit` bundle.

---

### SUPPLY-08: No Dependency Vulnerability Scan in CI [LOW]

**Location:** `.github/workflows/pr_check.yml`

**Evidence:**
The PR check pipeline runs `assembleDebug`, `lintDebug`, and `verifyRoborazziDebug`. No step runs `./gradlew dependencyCheckAnalyze` (OWASP) or invokes a third-party scanner (Snyk, Dependabot vulnerability alerts, etc.).

**Risk:**
A newly published CVE against any dependency in the graph will not be caught until a human notices or a release audit is done. Given the AGP 9 + Kotlin 2.2 toolchain bump, transitive dependency churn is high.

**Severity:** LOW
**Confidence:** High

**Remediation:**
Add a Gradle OWASP Dependency Check step or enable GitHub Dependabot alerts in the repository settings. Minimal CI addition:
```yaml
- name: Dependency vulnerability check
  run: ./gradlew dependencyCheckAnalyze --info
  continue-on-error: true  # warn only until baseline established
```

---

### SUPPLY-09: Stale Lifecycle Alpha Versions Not Bumped [LOW]

**Location:** `gradle/libs.versions.toml`

**Evidence:**
```toml
lifecycleRuntimeCompose = {module = "androidx.lifecycle:lifecycle-runtime-compose", version = "2.6.0-alpha03"}
lifecycleViewmodelCompose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version = "2.5.1"}
```
Current stable: `2.8.x`. Alpha versions may contain unpatched issues and are not covered by AndroidX security advisories.

**Severity:** LOW
**Confidence:** Med

**Remediation:**
Bump to `androidx.lifecycle:lifecycle-runtime-compose:2.8.7` and `lifecycle-viewmodel-compose:2.8.7` (or whatever stable matches the Compose BOM 2026.05.00 lifecycle version).

---

### SUPPLY-10: media3 Beta Dependency [NIT]

**Location:** `app/build.gradle`, `feature/twitter/build.gradle`

**Evidence:**
```groovy
implementation("androidx.media3:media3-exoplayer:1.0.0-beta02")
implementation("androidx.media3:media3-ui:1.0.0-beta02")
```
`core/designsystem/build.gradle` uses `media3:1.2.0` (stable). There is a version split.

**Severity:** NIT
**Confidence:** High

**Remediation:**
Align all media3 usages to the same stable version (1.5.x as of 2026). Centralize in `libs.versions.toml`.

---

## 5) Repository and Registry Analysis

### Repository Order (settings.gradle)
```groovy
repositories {
    google()          // ← first ✅
    mavenCentral()    // ← second ✅
    maven { url "https://jitpack.io" }  // ← third, HTTPS ✅
}
```
Order is correct: Google Maven before Maven Central. JitPack is HTTPS. No HTTP registries. No custom or self-hosted registries.

### Plugin Repository Order (pluginManagement)
```groovy
repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}
```
Correct order for Android plugin resolution. ✅

### Gradle Wrapper Distribution
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.3.1-bin.zip
```
HTTPS, official Gradle distribution. No custom distribution URL. ✅
No `gradle/wrapper/gradle-wrapper.jar` hash/verification in `gradle-wrapper.properties` (standard; the jar is committed to the repo).

---

## 6) CI/CD Security Summary

| Workflow | Action | Pin type | Trusted? | Risk |
|---|---|---|---|---|
| pr_check, release | `actions/checkout@v4` | Tag | First-party | Medium |
| pr_check, release | `actions/setup-java@v4` | Tag | First-party | Medium |
| pr_check, release | `gradle/actions/setup-gradle@v3` | Tag | First-party | Medium |
| pr_check, release | `android-actions/setup-android@v3` | Tag | **Third-party** | Medium-High |
| pr_check | `actions/github-script@v7` | Tag | First-party | Medium |
| pr_check | `actions/upload-artifact@v4` | Tag | First-party | Medium |
| release | `orhun/git-cliff-action@v4` | Tag | **Third-party** | Medium-High |
| release | `softprops/action-gh-release@v2` | Tag | **Third-party** | Medium-High |

No script injection vulnerabilities found (no `${{ github.event.* }}` interpolated directly into `run:` commands).

CI does **not** run on push to `main` directly, only on PRs and tags — acceptable blast radius limitation.

---

## 7) Font Provenance

| File | Font Family | License | Provenance | Status |
|---|---|---|---|---|
| `funnel_display_bold.ttf` | Funnel Display | OFL-1.1 | Pre-existing on `main` | No license file ⚠️ |
| `funnel_display_medium.ttf` | Funnel Display | OFL-1.1 | Pre-existing on `main` | No license file ⚠️ |
| `funnel_display_regular.ttf` | Funnel Display | OFL-1.1 | Pre-existing on `main` | No license file ⚠️ |
| `funnel_display_semibold.ttf` | Funnel Display | — | Was corrupt HTML on `main` | **Deleted by this branch** ✅ |
| `ibm_plex_mono_bold.ttf` | IBM Plex Mono | OFL-1.1 | Added by this branch | No license file ⚠️ |
| `ibm_plex_mono_medium.ttf` | IBM Plex Mono | OFL-1.1 | Added by this branch | No license file ⚠️ |
| `ibm_plex_mono_regular.ttf` | IBM Plex Mono | OFL-1.1 | Added by this branch | No license file ⚠️ |

Both IBM Plex Mono (IBM / Google, OFL-1.1) and Funnel Display (Fonthausen, OFL-1.1) are permissively licensed for bundling in apps. The compliance gap is the missing `OFL.txt` files.

---

## 8) Transitive Override Analysis

No `resolutionStrategy`, `force`, or `configurations.all` blocks found in any `*.gradle` file. No unintentional version downgrades or forced upgrades masking vulnerabilities. ✅

---

## 9) Recommendations by Priority

### High Priority (address before public release)

1. **SUPPLY-02**: Verify Firebase key restrictions in Firebase Console; add `google-services.json` to `.gitignore` if repo will be public.
2. **SUPPLY-01**: Download Funnel Display weights from official source; verify SHA256. History scrub is optional.

### Medium Priority (address in a follow-up PR)

3. **SUPPLY-03**: Add `OFL.txt` files for IBM Plex Mono and Funnel Display. (10 min)
4. **SUPPLY-04**: Remove stale `room_version` from root `build.gradle` ext block. (5 min)
5. **SUPPLY-05**: Centralize Hilt version in `libs.versions.toml`. (15 min)
6. **SUPPLY-06**: Pin GitHub Actions to SHA digests, especially third-party actions. (20 min)

### Low Priority (backlog)

7. **SUPPLY-07**: Replace JitPack `sandwich` with Maven Central coordinate, or remove if unused. (10 min)
8. **SUPPLY-08**: Add dependency vulnerability scan step to CI. (30 min)
9. **SUPPLY-09**: Bump stale lifecycle alpha versions. (5 min)
10. **SUPPLY-10**: Align media3 versions across modules to latest stable. (5 min)

---

## 10) Supply Chain Hygiene Score

**Score: 68/100**

| Dimension | Score | Notes |
|---|---|---|
| Version pinning | 20/25 | BOM adoption good; Hilt not centralized; ghost variable |
| Build integrity | 18/25 | No wildcards; JitPack in play; no wrapper hash |
| CI/CD security | 12/25 | Tag-pinned only; 3 third-party actions; no scan |
| Font/binary provenance | 8/15 | Corrupt file removed; IBM Plex added without OFL |
| Secret management | 10/10 | Key pre-existing; build secrets via GitHub Secrets |

**Target: 80+** — achievable with SUPPLY-03, -04, -05, -06 fixes.
