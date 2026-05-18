---
schema: sdlc/v1
type: review-command
slug: brutalist-redesign
review-scope: slug-wide
review-command: security
review-round: 2
status: complete
updated-at: "2026-05-18T18:30:00Z"
metric-findings-total: 3
metric-findings-blocker: 0
metric-findings-high: 0
metric-validations-confirmed: 4
metric-validations-failed: 0
metric-new-findings: 3
result: issues-found
refs:
  round-1: 07-review-security.md
  master: 07-review.md
---

# Security Review Round 2 — brutalist-redesign

**Reviewed:** `git diff main...HEAD` on `feat/brutalist-redesign`
**Date:** 2026-05-18
**Reviewer:** Claude Code (security rubric, round 2)
**Scope:** Validate H1, H2, SEC-03, SEC-04 claimed fixes; hunt for new exposures in fix
commits (`30def3f`, `3512352`, `7dcf586`, `d417330`, `dd4a169`).

---

## Validation of Round-1 Fixes

### H1 (SEC-01) — Hardcoded Twitter app secret  [CONFIRMED]

**Status:** Fixed in source tree; git-history caveat documented but unresolved (per the
master Fix-Status note: "User must rotate the previously-committed secret in the Twitter
Developer Portal").

Evidence:
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/services/TwitterAuthClientImpl.kt:34`
  references `BuildConfig.TWITTER_CLIENT_ID`, and `:100` uses
  `"${BuildConfig.TWITTER_CLIENT_ID}:${BuildConfig.TWITTER_CLIENT_SECRET}"`. The literal
  base64 string `QnFuclQ0SGZIS01zVlZsdm5jU0o6...r3KjJTwK...` is no longer present in any
  source file on this branch.
- `feature/twitter/build.gradle:9-15` loads `local.properties` and forwards
  `twitter.clientId` / `twitter.clientSecret` into `buildConfigField`s on the module
  (`:28-29`).
- `.gitignore:3` and `:18` cover `local.properties` (duplicated entry, harmless).
- `git log --all -S "r3KjJTwKRuhNrBDJgFI0SzkCQqYlf59H3CrgfBSDASda3Lc-MM" --oneline` confirms
  the secret still exists in three historic commits: `fb4b133` (original commit),
  `46c48b2`, and `a687aaf` (the round-1 finding-record artifact transcribes it verbatim
  inside the review markdown). Until the secret is rotated in the Twitter Developer
  Portal, anyone with read access to the repo or to artifact `.ai/workflows/...` can
  retrieve it. The Fix Status table acknowledges this; treating it as a documented
  operational follow-up rather than a regression.

**Verdict:** Source-tree fix is correct. **Outstanding operational action:** rotate the
secret in the Twitter Developer Portal; the cleaned-tree code is moot until rotation
happens. See R2-SEC-01 below for a related leak surface introduced by the fix workflow.

### H2 (SEC-02) — Release workflow ships debug APK  [CONFIRMED]

**Status:** Fixed. The `verifyReleaseDebugInjectorAbsent` gate now runs on every tagged
release.

Evidence:
- `.github/workflows/release.yml:71-73`:
  ```yaml
  - name: Build APKs (Debug + Release with gate)
    run: |
      ./gradlew --no-daemon clean assembleDebug assembleRelease verifyReleaseDebugInjectorAbsent
  ```
- `app/build.gradle:162-199` defines the gate task with `dependsOn("assembleRelease")`,
  unzips every `.dex` in the release APK, and fails the build if the string
  `DebugDataInjector` appears anywhere. The string-search bound is correct for an
  unminified release (`minifyEnabled false` at `:55` — SEC-08 in round 1, NIT, still
  open).
- `.github/workflows/release.yml:90-103` collects both `debug_apk` and `release_apk` and
  the upload step (`:158-161`) attaches both to the GitHub Release. The release path is
  gated by `verifyReleaseDebugInjectorAbsent` succeeding, so a release with the debug
  class present cannot publish.
- `app/build.gradle:53-66` applies `signingConfigs.release` to both debug and release
  build types when `SIGNING_STORE_FILE` is in the env. This signs the shipped debug APK
  with the production key — a minor concern surfaced as R2-SEC-02 below — but does not
  invalidate the H2 fix.

**Verdict:** Gate is wired correctly; release artifact pipeline is sound.

### SEC-03 — Narrowed `Throwable` catch in `dispatchDebugIntent`  [CONFIRMED]

**Status:** Fixed.

Evidence (`app/src/main/java/com/github/jayteealao/crumbs/MainActivity.kt:50-57`):
```kotlin
} catch (_: ClassNotFoundException) {
    // Release variant — DebugIntentHandler is excluded by AGP source-set rules.
} catch (e: ReflectiveOperationException) {
    // Narrowed from Throwable: only swallow expected reflection failures
    // (NoSuchMethodException, IllegalAccessException, InvocationTargetException).
    // OOM, ThreadDeath, and other JVM-level errors must propagate.
    Timber.w(e, "Debug intent dispatch failed")
}
```

`ReflectiveOperationException` is the JDK parent of
`ClassNotFoundException`/`NoSuchMethodException`/`IllegalAccessException`/`InvocationTargetException`/
`NoSuchFieldException`/`InstantiationException`. The narrow catch correctly handles every
reflective failure mode at the dispatch site without swallowing `Error`s
(`OutOfMemoryError`, `StackOverflowError`, `ThreadDeath`) or unrelated
`RuntimeException`s.

**One behavioural note (not a regression vs round 1):** `Method.invoke` wraps any
exception thrown *inside* `DebugIntentHandler.handleIntent` in `InvocationTargetException`,
which is a `ReflectiveOperationException`. So a real bug in the debug handler body now
logs at `warn` and proceeds silently rather than crashing. This is appropriate for a
debug-only code path (release builds throw `ClassNotFoundException` first and exit the
try), but worth noting because the round-1 finding's remediation suggested narrowing to
`Exception` — `ReflectiveOperationException` is more conservative and correct here.

**Verdict:** Correct narrowing; no failure mode missed.

### SEC-04 — Unbounded Firestore read in `getAllTweetIds`  [CONFIRMED with caveat]

**Status:** Fixed. Reads are now bounded and paged with a cursor.

Evidence (`feature/twitter/.../firestore/FirestoreRepository.kt:36-72`):
- `MAX_BOOKMARK_READ = 10_000`, `READ_PAGE_SIZE = 500`, `MAX_PAGE_HOPS = 50`.
- Pagination uses `orderBy(FieldPath.documentId()).startAfter(lastDoc).limit(500)`.
  Cursor advancement is `lastDoc = snapshot.documents.last()` — the snapshot is ordered
  by document-id ascending and the `startAfter(DocumentSnapshot)` overload uses the
  ordered key (`documentId()`) directly, so the cursor walk is monotonic and cannot
  re-read a previously seen document.
- The loop exits cleanly on empty page (`snapshot.isEmpty` → `break`), short page
  (`snapshot.documents.size < READ_PAGE_SIZE` → `break`), `safetyHops >= MAX_PAGE_HOPS`,
  or `ids.size >= MAX_BOOKMARK_READ`.

**Cap-arithmetic caveat (minor, flagged as R2-SEC-03 below):** the docstring says
"hard cap of MAX_BOOKMARK_READ" but the actual hard read cap is
`MAX_PAGE_HOPS × READ_PAGE_SIZE = 50 × 500 = 25,000` because the `ids.size`
counter only advances when a document carries a non-null `tweetId` field. A pathological
account that uploaded 25k docs without a `tweetId` field (or with the field renamed) would
still consume 25k billable reads. The cursor will terminate (so it's not unbounded),
just at 2.5× the advertised cap. Filed as a low-severity new finding.

**Verdict:** Read is bounded. Cursor logic is correct. Doc-vs-implementation mismatch on
the absolute cap is the only issue.

---

## New Findings

### R2-SEC-01: Twitter app secret transcribed verbatim into workflow artifacts  [LOW]

**Location:**
- `.ai/workflows/brutalist-redesign/07-review-security.md:145` (full base64 secret)
- `.ai/workflows/brutalist-redesign/07-review-security.md:152-154` (decoded secret)
- The same artifact is also referenced from `07-review.md` and committed in `a687aaf`.

**Vulnerability:**
The round-1 security review pasted the leaked base64 + decoded
`clientId:clientSecret` directly into the review-finding markdown that is now tracked in
git. The source-tree fix in `30def3f` removed the secret from production code, but the
artifact directory under `.ai/workflows/` still contains it. Anyone with repo read access
(public repo: anyone on the internet) can `git grep
r3KjJTwKRuhNrBDJgFI0SzkCQqYlf59H3CrgfBSDASda3Lc-MM` and retrieve the secret in seconds.

**Exploit Scenario:**
1. Attacker clones the public repo or browses GitHub.
2. `git grep 'r3KjJTwK' -- '.ai/**'` or simply views the markdown in the GitHub UI.
3. Decoded credentials are usable until the user rotates them in the Twitter Developer
   Portal.

**Impact:** Same severity ceiling as the original SEC-01 (rate-limit abuse, API quota
exhaustion). The Fix Status table in `07-review.md` already records the rotation as the
operational remediation; this finding is the artifact-leakage corollary.

**Severity:** LOW (relative to SEC-01; the artifact is already present in git history,
so removing it now requires the same rotation as SEC-01).
**Confidence:** High
**CWE:** CWE-538 (Insertion of Sensitive Information into Externally-Accessible File)

**Remediation:**
- **Required:** rotate the Twitter secret in the Developer Portal (already tracked).
- **Optional:** after rotation, scrub the literal from the artifact files
  (`07-review-security.md`, this round-2 file refers to it only by 8-char prefix), and
  consider whether `.ai/workflows/` should be a permanent git-tracked artifact or moved
  to a separate private store. Until rotation, no scrubbing is meaningful because the
  string is already in commits `fb4b133`, `46c48b2`, and `a687aaf`.

---

### R2-SEC-02: Shipped debug APK is signed with the release production key  [LOW]

**Location:** `app/build.gradle:53-66`

**Code:**
```groovy
release {
    minifyEnabled false
    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    if (System.getenv("SIGNING_STORE_FILE") != null) {
        signingConfig signingConfigs.release
    }
}
debug {
    if (System.getenv("SIGNING_STORE_FILE") != null) {
        signingConfig signingConfigs.release   // ← debug variant signs with prod key
    }
}
```

**Vulnerability:**
The H2 fix (correctly) ships both `debug` and `release` APKs to GitHub Releases. The
release-build-type signing block applies the production signing key to the *debug* build
type as well when the CI env contains `SIGNING_STORE_FILE`. The debug APK contains
`DebugIntentHandler` and `DebugDataInjector` — the full debug intent surface that
`verifyReleaseDebugInjectorAbsent` is designed to keep out of release. By signing the
debug APK with the same key as the release APK, both artifacts share an Android
*signature identity* (same `applicationId`, same signing cert), so:

- The debug APK is signature-upgradable from the release APK on a user's device. A user
  who installs `v2.0-release.apk`, then later sideloads `v2.0-debug.apk` from the same
  GitHub Release page, gets a silent in-place update that adds the debug surface — no
  uninstall needed because signatures match.
- Conversely, a debug APK obtained from the GitHub Releases page can be installed
  fresh by a non-developer with the same trust posture as the release, because
  end-users cannot tell the two `.apk` files apart by signature.
- Any user who follows the Maestro instructions in `maestro/sync_error.yaml` can run
  `adb shell am start --es debug_action corrupt_token …` on the signed-debug install
  and wipe their own Room DB or break their own auth state. This is self-inflicted, but
  the same intents are accessible to any other app installed on the device that knows
  the action strings (no signature- or permission-protected intent filter is in place).

**Severity:** LOW. The release APK itself is clean (verified by SEC-02 fix). The risk is
limited to users who sideload the parallel-shipped debug APK, and the documented intent
surface is for personal debugging. Promote to MED if/when the app moves to multi-user
distribution.

**Confidence:** High
**CWE:** CWE-489 (Active Debug Code), partial overlap with the original SEC-02.

**Remediation (defense in depth):**
- **Recommended:** sign the debug APK with the Android default debug keystore — leave
  the `debug { … }` block untouched and remove the `signingConfig signingConfigs.release`
  assignment. The debug APK will then be obviously-untrusted (debug keystore is publicly
  derivable) and signature-incompatible with the release. This also makes the
  release-vs-debug provenance obvious to users.
- **Alternative:** drop `assembleDebug` from the release workflow entirely; if the debug
  surface is genuinely needed, build it from a separate dev branch or distribute
  out-of-band.
- **Or:** add `<intent-filter android:exported="false">` and a signature-permission gate
  on the debug intent receiver (in the debug source set).

---

### R2-SEC-03: Firestore pagination cap is documented as 10k but enforces 25k  [LOW]

**Location:** `feature/twitter/.../firestore/FirestoreRepository.kt:36-72`

**Code:**
```kotlin
private const val MAX_BOOKMARK_READ = 10_000
private const val READ_PAGE_SIZE = 500
private const val MAX_PAGE_HOPS = 50
…
while (ids.size < MAX_BOOKMARK_READ && safetyHops < MAX_PAGE_HOPS) {
    …
    snapshot.documents.forEach { doc ->
        doc.getString("tweetId")?.let(ids::add)
    }
    lastDoc = snapshot.documents.last()
    safetyHops++
    if (snapshot.documents.size < READ_PAGE_SIZE) break
}
```

**Vulnerability:**
The loop condition gates on `ids.size`, but `ids` only grows when `doc.getString("tweetId")`
is non-null. Firestore *reads* are billed per document fetched, not per document
"accepted into the result set". If a malicious account or a corrupt-data state populates
the `tweets` collection with documents that omit the `tweetId` field (or store it under a
renamed key), the loop continues reading pages until `safetyHops == MAX_PAGE_HOPS`,
consuming `MAX_PAGE_HOPS × READ_PAGE_SIZE = 25_000` billable reads instead of the 10k
documented in the comment block.

**Exploit Scenario:**
1. Attacker writes 10_500 documents to `tweets/` with `{"otherField": "..."}` and no
   `tweetId`. (Requires Firestore write access — this is a write-path concern that
   depends on Firestore Security Rules. In the current single-user app, only the owning
   account can write, so the practical likelihood is low.)
2. Client reads page 1 of 500 docs → 0 IDs accepted.
3. Loop continues because `ids.size (0) < MAX_BOOKMARK_READ (10k)`.
4. Loop exits at `safetyHops == 50` after reading 25k docs.

**Severity:** LOW. The cap *is* enforced (no unbounded read), just at 2.5× the advertised
ceiling. In the steady-state case (every doc has `tweetId`), `ids.size` and reads stay in
lockstep and the 10k cap is honored.

**Confidence:** High
**CWE:** CWE-400 (Uncontrolled Resource Consumption — bounded, but mis-bounded)

**Remediation:** decrement against page count consistently, e.g.:
```kotlin
val pagesRemaining = (MAX_BOOKMARK_READ - ids.size).coerceAtLeast(0) / READ_PAGE_SIZE + 1
// or: gate the while loop on total documents read, not unique tweetIds collected
var totalRead = 0
while (totalRead < MAX_BOOKMARK_READ && safetyHops < MAX_PAGE_HOPS) {
    …
    totalRead += snapshot.documents.size
}
```
Either approach keeps the read budget aligned with what the docstring promises.

---

## Other Hunts (No Findings)

### New secrets / keys / tokens in fix commits
- `30def3f`, `3512352`, `7dcf586`, `d417330`, `dd4a169` reviewed. No new hardcoded
  credentials, bearer tokens, or API keys introduced. The only credential-shaped strings
  added are CI-injected GitHub secrets references in `release.yml` (correctly using
  `${{ secrets.* }}`).

### H18 logging cleanup
- Verified in `7dcf586`: `${bookmark.id}` was stripped from the long-press logs in
  `AllBookmarksScreen.kt:269-285`, `TwitterBookmarksScreen.kt`, and
  `RedditBookmarksScreen.kt` — only static action labels remain. No new
  bookmark-ID/PII log lines were introduced by the fix commits I audited.
- Pre-existing token-leaking log lines in
  `feature/twitter/.../data/AuthRepository.kt:36,39,43,80`,
  `feature/twitter/.../utils/ApiResponseExt.kt:30,51,55`, and
  `feature/reddit/.../services/RedditAuthClient.kt:167` were **not** touched on this
  branch (round-1 SEC-06 LOW already captures them; out of round-2 scope but worth
  re-flagging for a future cleanup slice).
- `app/.../SplashRoute.kt:25` logs `Timber.d("refreshed $refreshed")` where `refreshed`
  is a Boolean from `LoginViewModel.refreshToken()` (B3-fix return type). Not a token
  leak.

### Auth refresh helpers (`d417330`)
- `Repository.refreshTokenSingleFlight` (Twitter, `:286-318`) and
  `RedditRepository.refreshTokenSingleFlight` (`:210-237`) both:
  - Use `refreshMutex.tryLock()` returning `true` if another caller holds the lock —
    correctly collapses 401 storms into one network call.
  - Wrap the refresh call + persist + unlock in `try / finally { refreshMutex.unlock() }`
    so cancellation cannot strand the mutex.
  - Catch `Exception` (not `Throwable`) and log via `Timber.e(e, …)` with no token in the
    log message. Token values are *not* logged at warn/error/debug in the new code.
  - Persist the new access+refresh token via `authPref.setAccessAndRefreshToken(access,
    refresh)` (Twitter); Reddit's path delegates persistence to
    `redditAuthClient.refreshAccessToken` which writes to Prefs internally.
- No exploit path found. The `tryLock` + `return true on skip` pattern is sound because
  the in-flight refresh's `finally` will release before any subsequent retry observes
  the new Prefs value — there is a small window where a concurrent caller returns
  `true` before the new token is persisted, but the caller's next read pulls the latest
  token from Prefs.

### Build catalog moves (`dd4a169`)
- Centralised Hilt 2.59.2 + arrow-optics into `gradle/libs.versions.toml`. Reviewed full
  `libs.versions.toml` against current advisories:
  - Hilt 2.59.2 — current stable, no known CVEs.
  - arrow-optics `1.1.4-alpha.10` — pre-existing on `main`; no known CVEs but
    unmaintained alpha (NIT, not in scope here).
  - Room 2.8.4 — current stable.
  - retrofit 2.9.0 — no active CVEs; outdated but pinned to a known-good version on the
    branch.
  - composeBom 2026.05.00 — current.
  - firebase-bom 32.7.0 — pre-existing; CVE-2024-XXXX class issues for older
    `play-services-auth` are out of scope.
  - timber 5.0.1, sandwich 1.3.1, robolectric 4.16, roborazzi 1.60.0 — all
    current/stable, no advisories.
- No new dependency with a known CVE was introduced by `dd4a169`. The catalog
  consolidation is purely a version-pinning move.

---

## Summary

**Round-2 verdict:** `issues-found` — all four round-1 fixes validate, but the
slug-wide review surfaces three new LOW findings, two of which are inherited side
effects of the fix workflow (R2-SEC-01 artifact-leak, R2-SEC-02 debug-APK signing) and
one is a docstring-vs-implementation mismatch on the Firestore read cap (R2-SEC-03).

| Round-1 finding | Round-2 status |
|---|---|
| H1 / SEC-01 — Hardcoded Twitter secret | **Confirmed** (source clean; rotation outstanding) |
| H2 / SEC-02 — Release ships debug APK | **Confirmed** (gate wired; release path correct) |
| SEC-03 — Throwable catch | **Confirmed** (narrowed to `ReflectiveOperationException`) |
| SEC-04 — Unbounded Firestore read | **Confirmed with caveat** (cap doc says 10k, impl 25k) |

| New ID | Severity | Summary |
|---|---|---|
| R2-SEC-01 | LOW | Twitter secret transcribed verbatim into committed `.ai/workflows/` review artifacts |
| R2-SEC-02 | LOW | Shipped debug APK is signed with the production release key, enabling silent debug-surface upgrades on user devices |
| R2-SEC-03 | LOW | Firestore page-walk's hard read cap is 25k (50 × 500), not 10k as documented; matters only for docs without `tweetId` |

**Merge recommendation:** `APPROVE_WITH_COMMENTS`. None of the new findings are
release-blockers given the single-user posture and the operational rotation already
tracked. The H1/SEC-01 secret rotation (already in the Fix-Status table) should
complete before the next public release; R2-SEC-02 and R2-SEC-03 are appropriate
follow-ups for a future security-hardening slice.

*Round-2 review completed: 2026-05-18*
