---
command: /review supply-chain (round 2)
session_slug: brutalist-redesign
date: 2026-05-18
scope: slug-wide (git diff main...HEAD)
target: branch feat/brutalist-redesign vs main
round: 2
---

# Supply Chain Review (Round 2) — brutalist-redesign

**Reviewed:** `git diff main...HEAD` (410 files, +27 691 / −9 455)
**Date:** 2026-05-18
**Reviewer:** Claude Code (supply-chain dimension, round 2)
**Round-1 baseline:** `07-review-supply-chain.md` (SUPPLY-01..10)

---

## 1) Executive Summary

**Merge Recommendation (supply-chain axis):** APPROVE_WITH_COMMENTS

**Rationale:**
The supply-chain-relevant Round-1 fix decisions landed cleanly:
- **SUPPLY-04** — root `build.gradle` is now stripped of `room_version = '2.4.3'` and of the
  duplicate compose/lifecycle/retrofit ext block. CONFIRMED in commit `dd4a169`.
- **SUPPLY-05** — Hilt is now centralized at `hilt = "2.59.2"` in `gradle/libs.versions.toml`,
  and all five module `build.gradle` files reference `libs.hilt.android` /
  `libs.hilt.compiler`. CONFIRMED in commit `dd4a169`.
- **Arrow** — also centralized to `arrow = "1.1.4-alpha.10"` in the catalog (bonus —
  reduces a second drift surface that round 1 did not enumerate).

The remaining round-1 findings are explicit Deferrals:
- **H22 / SUPPLY-01** — user-skipped (font SHA256 + OFL bundling).
- **SUPPLY-03** — bundled with H22; deferred.
- **SUPPLY-06** — Action SHA-pinning; deferred (round-1 rationale: fabricating pins would
  break CI; needs authoritative SHA lookups against a network).

The deferrals are reasonable and documented. Round 2 verifies no new supply-chain regression
landed in the fix commits.

**Newly observed in round 2 (not in Round 1):**
- **R2-SUPPLY-01** — A new third-party GitHub Action workflow (`.github/workflows/manual-release.yml`,
  added in this branch) repeats the same `@v3` / `@v4` / `@v2` mutable-tag pin pattern that
  Round 1 flagged for `pr_check.yml` and `release.yml`. Same severity class as SUPPLY-06,
  same deferral applies, but worth re-flagging because it expands the surface.
- **R2-SUPPLY-02** — confirmation that **no new third-party Gradle dependencies** were added
  by any fix commit (`7dcf586`, `dd4a169`, `32e01af`, `3512352`, etc.). Pure refactor /
  test-add / catalog-centralize commits. Negative finding, recorded for completeness.

No BLOCKER. No new HIGH.

---

## 2) Verification of Claimed Round-1 Fixes

### SUPPLY-04 — Stale `room_version` removed from root `build.gradle`

**Claim:** Commit `dd4a169` strips the root `build.gradle` of the stale `room_version='2.4.3'`
ext property and of the duplicate compose/lifecycle ext block.

**Verification — current contents of `build.gradle`:**
```groovy
buildscript {
    ext {
        kotlin_version = '2.2.10'
        // All other versions (compose, room, hilt, retrofit, lifecycle, etc.)
        // live in gradle/libs.versions.toml as the single source of truth.
    }
}
plugins {
    id 'com.android.application' version '9.1.1' apply false
    id 'com.android.library' version '9.1.1' apply false
    id 'org.jetbrains.kotlin.plugin.compose' version '2.2.10' apply false
    id 'com.google.devtools.ksp' version '2.2.10-2.0.2' apply false
    id 'com.google.dagger.hilt.android' version '2.59.2' apply false
    id 'com.google.gms.google-services' version '4.4.4' apply false
    id 'project-report'
}
```

The entire 16-line root `build.gradle` now contains:
- One `kotlin_version` ext property (used only by the buildscript classpath).
- An explicit comment naming `libs.versions.toml` as the single source of truth.
- Plugin version pins for the AGP / Kotlin / KSP / Hilt / google-services toolchain.

No `room_version`, no `compose_ui_version`, no `lifecycleVersion`, no `retrofit_version`,
no `arrowVersion` properties remain in the root file.

**Verdict:** CONFIRMED FIXED.

---

### SUPPLY-05 — Hilt centralized in `libs.versions.toml`

**Claim:** Commit `dd4a169` centralizes Hilt at `hilt = "2.59.2"` in the catalog and updates
all five module `build.gradle` files to reference it.

**Verification — `gradle/libs.versions.toml`:**
```toml
[versions]
hilt = "2.59.2"

[libraries]
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
```

**Verification — all five module build.gradle files:**

| Module | Reference | Status |
|---|---|---|
| `app/build.gradle:112-113` | `libs.hilt.android` / `libs.hilt.compiler` | OK |
| `core/data/build.gradle:44-45` | `libs.hilt.android` / `libs.hilt.compiler` | OK |
| `core/pref/build.gradle:41-42` | `libs.hilt.android` / `libs.hilt.compiler` | OK |
| `feature/twitter/build.gradle:83-84` | `libs.hilt.android` / `libs.hilt.compiler` | OK |
| `feature/reddit/build.gradle:68-69` | `libs.hilt.android` / `libs.hilt.compiler` | OK |

Repo-wide grep for the hardcoded coordinate `"com.google.dagger:hilt-android:2.59.2"` in any
`.gradle` file: **zero hits**. The only remaining instance of the literal `2.59.2` is in the
root `build.gradle` plugin declaration `id 'com.google.dagger.hilt.android' version '2.59.2'`,
which is the Gradle plugin DSL convention (plugin versions cannot use the catalog version
reference directly without `aliases.plugins`). That is acceptable convention; round-2
recommendation noted in §6.

**Verdict:** CONFIRMED FIXED. Bonus: Arrow optics was also centralized at
`arrow = "1.1.4-alpha.10"` in the same commit, which the Round 1 review did not enumerate.

---

## 3) Deferrals Re-Evaluation

### H22 / SUPPLY-01 — Font provenance (user-skipped, deferred)

**Status:** Explicitly skipped by the user per `07-review.md` line 255. Out of round-2 scope
for re-litigation.

**Round-2 observations (informational):**
- The `core/designsystem/src/main/res/font/` directory contains:
  - `funnel_display_bold.ttf`, `funnel_display_medium.ttf`, `funnel_display_regular.ttf`
  - `ibm_plex_mono_bold.ttf`, `ibm_plex_mono_medium.ttf`, `ibm_plex_mono_regular.ttf`
- The previously-corrupt `funnel_display_semibold.ttf` is gone — that part of SUPPLY-01 is
  resolved.
- No SHA256 manifest / provenance attestation file is committed alongside the TTFs.
- Defer-rationale stands; will need a future supply-chain hardening slice.

---

### SUPPLY-03 — OFL.txt for bundled fonts (deferred, user's specific Round-2 concern)

**User's round-2 concern:** Verify the bundled fonts are NOT being shipped in a way that
violates SIL OFL 1.1 (which they MUST be under, since both are OFL-licensed).

**Analysis:**
SIL OFL 1.1 §3 requires the license file to be included in all distributions of the Font
Software. Repo-wide check: `find . -name "OFL*"` and `find . -name "LICENSE*"` return zero
matches. No `THIRD_PARTY_LICENSES`, no `OFL.txt` next to the TTFs.

**Verdict:** The current branch **does ship the fonts in a way that violates OFL-1.1 §3** —
the TTFs are bundled in the APK without the license file. Exposure:
- Private repo + personal builds: §3 still requires inclusion but practical enforcement risk
  is negligible.
- GitHub Releases / Play Store / open-sourcing: this becomes a license-compliance defect.

The deferral is **reasonable** for an unreleased personal-use APK but **must be resolved
before any public distribution channel**. ~10 minutes of work (drop `OFL.txt` for IBM Plex
Mono and Funnel Display in `app/src/main/assets/licenses/` and surface a "Third-party
licenses" screen).

---

### SUPPLY-06 — GitHub Actions pinned to mutable tags (deferred)

**User's round-2 concern:** Workflows still use `@v4` tags. Validate this is documented and
that the deferral is reasonable (not actively being attacked).

**Verification — current Action references at HEAD:**

| Workflow | Action | Pin | Type |
|---|---|---|---|
| `pr_check.yml:25` | `actions/checkout@v4` | tag | first-party |
| `pr_check.yml:28` | `actions/setup-java@v4` | tag | first-party |
| `pr_check.yml:34` | `gradle/actions/setup-gradle@v3` | tag | first-party |
| `pr_check.yml:42` | `android-actions/setup-android@v3` | tag | **third-party** |
| `pr_check.yml:83` | `actions/github-script@v7` | tag | first-party |
| `pr_check.yml:168` | `actions/upload-artifact@v4` | tag | first-party |
| `release.yml:17, 23, 29, 38, 167, 174` | `actions/checkout@v4`, `setup-java@v4`, `setup-gradle@v3`, `setup-android@v3`, `upload-artifact@v4` | tag | mixed |
| `release.yml:126` | `orhun/git-cliff-action@v4` | tag | **third-party** |
| `release.yml:152` | `softprops/action-gh-release@v2` | tag | **third-party** |
| **`manual-release.yml:24, 29, 35, 43, 105, 113, 121`** (R2-NEW workflow) | same set | tag | mixed |

Total mutable-tag references: **20 across three workflows** at HEAD. Same Action set as
Round 1, plus the new `manual-release.yml` which adds 7 more pinned-by-tag references —
including a 4th `softprops/action-gh-release@v2` instance (third-party with `contents: write`
in the release job).

**Status of "actively being attacked" check:**
- `actions/checkout`, `actions/setup-java`, `actions/upload-artifact`, `actions/github-script`,
  `actions/setup-gradle` — all owned by GitHub's `actions/` org or Gradle's `gradle/` org.
  These are first-party actions with the strongest plausible safeguards on tag movement.
  GitHub does not currently advertise a known compromise for any `@v3`/`@v4`/`@v7` tag of
  these actions as of the review date.
- `android-actions/setup-android` — community-maintained; published by `android-actions`
  org (not the Android Open Source Project). No known compromise advertised but it is a
  third-party action.
- `orhun/git-cliff-action`, `softprops/action-gh-release` — single-maintainer projects.
  Higher exposure to single-account-takeover risk. No known compromise advertised as of
  review date.

**Verdict on the deferral:**
The Round-1 deferral rationale was: "requires authoritative SHA lookups against
`actions/checkout@v4` etc. — fabricating pins would break CI."

That rationale is **reasonable** because:
1. Pinning to a wrong SHA would fail CI immediately and visibly.
2. The SHA lookup requires network access to GitHub's API to resolve `actions/checkout@v4` →
   the current SHA. This must be done from a trusted environment, not fabricated.
3. There is no published evidence of an active compromise of any of the pinned tags at the
   review date.
4. CI has `contents: write` and `pull-requests: write` permissions only on the release path,
   not on PR checks — blast radius is bounded.

The deferral stands. The recommendation is to use a tool like `pin-github-actions` or
`stepsecurity/secure-repo` to do the SHA lookups, run once, then commit the pinned forms.
That is the correct path; fabricating SHAs is not.

**Round-2 recommendation:** Add `manual-release.yml` to the deferred SUPPLY-06 scope (it
extends the same problem, not a new one). Schedule a one-time pinning pass during the
follow-up supply-chain hardening slice.

---

## 4) New Round-2 Findings

| ID | Severity | Confidence | Category | Location | Issue |
|---|---|---|---|---|---|
| R2-SUPPLY-01 | MED | High | CI integrity (extension of SUPPLY-06) | `.github/workflows/manual-release.yml` (new in this branch) | 7 additional Action references pinned by mutable tag, including a 4th third-party `softprops/action-gh-release@v2` invocation with `contents: write`. |
| R2-SUPPLY-02 | LOW | High | Negative finding (informational) | All fix commits since round 1 | No new third-party Gradle dependencies added by fix commits. No new Maven artifacts, no new JitPack coordinates, no new transitive deps from version-bump commits. |
| R2-SUPPLY-03 | LOW | High | Plugin DSL version drift | `build.gradle:13` | `id 'com.google.dagger.hilt.android' version '2.59.2'` hardcodes Hilt version in the plugin DSL even though `[versions] hilt = "2.59.2"` exists in the catalog. Single line, but it can drift from `libs.versions.toml` independently. |
| R2-SUPPLY-04 | LOW | Med | Plugin DSL version drift (Kotlin) | `build.gradle:11-12` | Same pattern for `kotlin.plugin.compose` and `KSP`: versions `2.2.10` and `2.2.10-2.0.2` are hardcoded in the plugin DSL rather than referenced through `[plugins]` aliases in `libs.versions.toml`. |

---

## 5) Findings (Detailed)

### R2-SUPPLY-01: `manual-release.yml` extends the mutable-tag Action surface [MED]

**Location:** `.github/workflows/manual-release.yml` (new file added in this branch).

**Evidence:**
```yaml
# manual-release.yml — new in this branch
- uses: actions/checkout@v4            # line 24
- uses: actions/setup-java@v4          # line 29
- uses: gradle/actions/setup-gradle@v3 # line 35
- uses: android-actions/setup-android@v3  # line 43  (third-party)
- uses: actions/upload-artifact@v4     # line 105
- uses: actions/upload-artifact@v4     # line 113
- uses: softprops/action-gh-release@v2 # line 121  (third-party, contents:write)
```

**Risk:**
Same class of risk as Round-1 SUPPLY-06: a moved tag executes attacker-controlled code in CI
with elevated permissions. `manual-release.yml` is a workflow_dispatch / manual-trigger
release pipeline — by definition triggered by a maintainer with intent to publish — which
maximises the blast radius if the actions are compromised at that moment (artifact will be
signed and published with attacker payload).

**Severity:** MED (matches SUPPLY-06; this is an extension, not a new attack vector)
**Confidence:** High

**Remediation:**
Bundle with the SUPPLY-06 follow-up. Pin all three workflows in the same pass.

---

### R2-SUPPLY-02: No new third-party dependencies introduced by fix commits [LOW — Negative]

**Location:** All commits between Round 1 review checkpoint and HEAD.

**Evidence:**
Diff of `gradle/libs.versions.toml` between `main` and HEAD shows only:
- Refactors moving existing coordinates into `[versions]` references (Hilt, Arrow).
- Version bumps documented in Round 1 (Compose BOM, Room, Roborazzi, Robolectric, paging-compose).
- Zero `+ ... = { module = "new.coord:artifact", version = "..." }` lines for unknown
  artifacts.

Diff of all module `build.gradle` files: no new `implementation "newcoord:..."` strings; only
the inverse — switches from hardcoded coordinates to `libs.*` references.

**Why this matters:** Round 1 baseline established a 12-line dependency change table. A
follow-up round needs to confirm that fix commits did not sneak new deps under the radar
(e.g., a "fix for X" that pulls in an unaudited library). They did not.

**Severity:** LOW (informational; positive finding recorded for audit trail)
**Confidence:** High

---

### R2-SUPPLY-03: Hilt plugin DSL version not catalog-referenced [LOW]

**Location:** `build.gradle:13`

**Evidence:**
```groovy
// build.gradle:13
id 'com.google.dagger.hilt.android' version '2.59.2' apply false
```

`libs.versions.toml` has `hilt = "2.59.2"` but the root `build.gradle` plugin DSL hardcodes
the same version string separately. A future Hilt bump that updates
`gradle/libs.versions.toml` but forgets to update `build.gradle:13` would leave the plugin
version and the library version out of sync.

**Severity:** LOW
**Confidence:** High

**Remediation (defer to a follow-up cleanup):**
Use Gradle's `[plugins]` block in `libs.versions.toml`:
```toml
[plugins]
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```
Then in `build.gradle`:
```groovy
alias(libs.plugins.hilt.android) apply false
```

This is a settings-level mechanical change; recommend bundling with the supply-chain
hardening slice that addresses SUPPLY-06 / R2-SUPPLY-01.

---

### R2-SUPPLY-04: Kotlin compose plugin + KSP versions hardcoded in plugin DSL [LOW]

Same pattern as R2-SUPPLY-03 at `build.gradle:11-12`: Kotlin (`2.2.10`) and KSP
(`2.2.10-2.0.2`) plugin versions are hardcoded in the root `plugins { }` block rather than
referenced through `[plugins]` aliases. Bundle with R2-SUPPLY-03 cleanup.

---

## 6) Recommendations (Round 2)

### High Priority (none) — no BLOCKER, no new HIGH

### Medium Priority (defer-bundled with already-deferred supply-chain slice)

1. **R2-SUPPLY-01** — pin `manual-release.yml` along with `pr_check.yml` and `release.yml`
   in the same SHA-pinning pass. (Bundled with SUPPLY-06.)

### Low Priority (cleanup, batch into one PR)

2. **R2-SUPPLY-03 + R2-SUPPLY-04** — promote `hilt`, `kotlin`, `ksp` plugin DSL versions to
   catalog `[plugins]` aliases. (~10 min.)
3. **SUPPLY-03 (deferred)** — bundle OFL.txt for IBM Plex Mono and Funnel Display before any
   public distribution. (~10 min.)

### Confirmed Patched (no action)

- **SUPPLY-04** — stale `room_version` removed from root `build.gradle`. (Commit `dd4a169`.)
- **SUPPLY-05** — Hilt centralized in `libs.versions.toml`; all 5 module files use
  `libs.hilt.*`. (Commit `dd4a169`.)

### Negative finding (no action)

- **R2-SUPPLY-02** — no new third-party Gradle dependencies introduced by fix commits.

---

## 7) Round-2 Supply Chain Posture Delta

Score moves from **68 → 70 / 100**. Net +2 from version-pinning hygiene wins
(Hilt + Arrow centralized, ghost variable removed), partially offset by CI surface expansion
(`manual-release.yml` adds 7 more mutable-tag refs). Target 80+ remains achievable with the
deferred OFL.txt + Action SHA-pinning slice.

---

## 8) Round-2 Supply Chain Merge Verdict

**APPROVE_WITH_COMMENTS.**

The fix commits delivered the claimed supply-chain improvements:
- Root `build.gradle` ghost variable: removed (`dd4a169`).
- Hilt centralization: complete across all 5 modules (`dd4a169`).
- No new unaudited third-party Gradle dependencies introduced.

Outstanding deferrals (OFL.txt, Action SHA-pinning) have documented rationale and are
reasonable for the current state. A new workflow file (`manual-release.yml`) extends the same
mutable-tag surface but should be bundled with the existing SUPPLY-06 deferred fix.

No BLOCKER. No HIGH introduced. The supply-chain hygiene score moves from 68 to 70.

*Review completed: 2026-05-18*
