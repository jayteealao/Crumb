---
slug: brutalist-redesign
review-scope: slug-wide
review-command: security
date: 2026-05-18
scope: slug-wide
target: git diff main...HEAD
files_changed: 380
lines_added: 16821
lines_removed: 9318
verdict: REQUEST_CHANGES
---

# Security Review — brutalist-redesign (slug-wide)

**Reviewed:** slug-wide / `git diff main...HEAD`
**Date:** 2026-05-18
**Reviewer:** Claude Code (security rubric)

---

## 0) Scope, Assumptions, and Threat Summary

**What was reviewed:**
- Scope: Full branch diff against `main`
- Files: 380 changed, +16821 / -9318 lines
- Key surfaces: debug reflective bridge, OAuth token storage and logging, intent surface,
  Firestore backfill sync, Maestro debug flow, CI/release gate

**Threat model:**
- **Entry points**: `MainActivity.onNewIntent` (OAuth deep-link + debug intent dispatch),
  Firestore SDK (reads all tweets), Reddit/Twitter OAuth redirect URI
- **Trust boundaries**: Any app → MainActivity via `am start` (intent boundary);
  app → Firestore (no per-user Firestore Security Rules visible in diff);
  app → Twitter API / Reddit API (bearer token boundary)
- **Assets**: Twitter OAuth access + refresh tokens, Reddit access + refresh tokens,
  stored in DataStore (`core/pref`); tweet/post content in Room DB + Firestore
- **Privileged operations**: `DebugDataInjector.run(wipe=true)` wipes Room DB;
  `corruptTwitterToken()` breaks auth state; Firestore bulk reads of all tweets

**Authentication model:**
- Twitter: PKCE OAuth 2.0, tokens persisted in DataStore via `core/pref`
- Reddit: OAuth 2.0 with refresh token, persisted in `RedditPrefs` DataStore
- No session layer — tokens are long-lived (until revoked or expired)

**Data sensitivity:**
- High: OAuth access/refresh tokens (both platforms), Twitter user ID/username
- Medium: Saved tweet/post content, subreddit membership implied by bookmarks
- Low: Filter states, UI preferences

**Assumptions:**
1. Threat actors include other apps on the same Android device (sideloaded, malicious)
2. Debug builds may run on developer machines where ADB access is assumed legitimate
3. Firestore Security Rules enforcement is out of band (not visible in this diff)
4. `Timber.d` log output is suppressed in release by Timber's default `ReleaseTree` behavior,
   BUT no `ReleaseTree` plant is confirmed in this diff

---

## 1) Executive Summary

**Merge Recommendation:** REQUEST_CHANGES

**Rationale:**
Two HIGH findings require fixes before shipping: raw access tokens are logged in debug builds
via `Timber.d` without release-tree suppression confirmation, and the release workflow ships
a **debug APK** to GitHub Releases rather than a release APK — making the `verifyReleaseDebugInjectorAbsent`
Gradle task dead letter for actual releases. The hardcoded Twitter app secret in
`TwitterAuthClientImpl` is a pre-existing credential exposure issue. No BLOCKERs were found
(the debug intent surface is correctly restricted to debug builds by AGP source-set exclusion),
but the two HIGH findings and one MED must be addressed.

**Critical Vulnerabilities (BLOCKER):** None found.

**High-Risk Issues:**
1. **SEC-01**: Hardcoded Twitter app secret in `TwitterAuthClientImpl.kt` — credential exposure
2. **SEC-02**: Release workflow ships debug APK, bypassing `verifyReleaseDebugInjectorAbsent` gate

**Overall Security Posture:**
- Authentication: Adequate (PKCE implemented, HTTPS enforced)
- Authorization: N/A (single-user personal app)
- Input Validation: Adequate (intent extras only used in debug variant)
- Secret Management: Insecure (hardcoded app secret, token logging in debug)
- Defense-in-Depth: Limited (minify disabled in release, no release tree confirmed)

---

## 2) Threat Surface Analysis

### Entry Points

| Entry Point | Type | Restricted to build | Input Validated | Notes |
|---|---|---|---|---|
| `MainActivity.onCreate` → `dispatchDebugIntent` | Intent extra | Debug only (ClassNotFound in release) | No type/action allowlist | See SEC-03 |
| `MainActivity.onNewIntent` → `dispatchDebugIntent` | Intent extra | Debug only | No type/action allowlist | Same |
| OAuth deep-link `crumbs://graphitenerd.xyz?code=` | Intent URI | Production | `code` param used as-is | Safe: code exchanged server-side |
| Firestore `getAllTweetIds()` | Cloud read | Production | No limit / pagination | See SEC-04 |
| Maestro `launchApp.arguments: debug_action` | adb / Maestro | Debug only (per Maestro docs) | N/A | See SEC-05 |

### Assets at Risk

| Asset | Sensitivity | Exposure Risk | Findings |
|---|---|---|---|
| Twitter access token | Critical | MEDIUM | SEC-06: logged via Timber.d |
| Twitter refresh token | Critical | LOW | Stored in DataStore only |
| Reddit access token | Critical | LOW | Not found in logs |
| Twitter app client secret | Critical | HIGH | SEC-01: hardcoded in source |
| Room DB + Firestore tweet data | Medium | LOW | android:allowBackup=true (SEC-07) |

---

## 3) Findings Table

| ID | Severity | Confidence | Category | File:Line | Vulnerability |
|---|---|---|---|---|---|
| SEC-01 | HIGH | High | Hardcoded Credential | `TwitterAuthClientImpl.kt:99` | App-only client secret in source |
| SEC-02 | HIGH | High | Release Gate Missing | `.github/workflows/release.yml:~60` | Release CI ships debug APK; `verifyReleaseDebugInjectorAbsent` never runs |
| SEC-03 | MED | High | Broad Intent Surface | `MainActivity.kt:42` | `dispatchDebugIntent` swallows all `Throwable`; no action-set allowlist in release path |
| SEC-04 | MED | Med | Unbounded Cloud Read | `FirestoreRepository.kt:40` | `getAllTweetIds()` fetches entire collection without limit |
| SEC-05 | LOW | High | Debug Surface Scope Confirmed | `maestro/sync_error.yaml` | `corrupt_token` action works only on debug builds; no release risk, but worth documenting |
| SEC-06 | LOW | Med | Token Logging | `TwitterAuthClientImpl.kt:82,108` | `Timber.d("token actual data: ${result?.accessToken}")` — safe only if ReleaseTree is planted |
| SEC-07 | LOW | High | Broad Android Backup | `AndroidManifest.xml:10` + `data_extraction_rules.xml` | `allowBackup=true` with empty `<cloud-backup>` block; DataStore prefs (tokens) included in cloud backup |
| SEC-08 | NIT | High | minifyEnabled=false | `app/build.gradle:55` | Release APK not minified; debug classes harder to strip without R8 |

**Findings Summary:**
- BLOCKER: 0
- HIGH: 2
- MED: 2
- LOW: 3
- NIT: 1

---

## 4) Findings (Detailed)

### SEC-01: Hardcoded Twitter App-Only Client Secret [HIGH]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/services/TwitterAuthClientImpl.kt:99`

**Vulnerable Code:**
```kotlin
result = twitterAuthService.getAppOnlyAccessToken(
    AppOnlyBody(),
    "Basic ${Base64.encodeToString(
        "QnFuclQ0SGZIS01zVlZsdm5jU0o6MTpjaQ:r3KjJTwKRuhNrBDJgFI0SzkCQqYlf59H3CrgfBSDASda3Lc-MM".toByteArray(),
        Base64.NO_WRAP + Base64.URL_SAFE
    )}",
)
```

**Vulnerability:**
The string `QnFuclQ0SGZIS01zVlZsdm5jU0o6MTpjaQ:r3KjJTwKRuhNrBDJgFI0SzkCQqYlf59H3CrgfBSDASda3Lc-MM`
is the Twitter OAuth 2.0 Client ID + Client Secret (`clientId:clientSecret`) concatenated and
base64-encoded. The secret portion (`r3KjJTwKRuhNrBDJgFI0SzkCQqYlf59H3CrgfBSDASda3Lc-MM`) is
visible in source and git history. Note the Client ID is also embedded in the `authorizationUrl`
string at line 33.

**Exploit Scenario:**
1. Attacker clones or forks the public repo (or extracts from published APK via `apktool`)
2. Decodes the base64 string: `echo "QnFuclQ0... | base64 -d"` → `clientId:clientSecret`
3. Uses secret to call Twitter `/2/oauth2/token` with `grant_type=client_credentials`,
   obtaining an app-level bearer token
4. Rates the token against Twitter API, scraping or performing actions under the app's quota

**Impact:**
- Attacker can exhaust Twitter API rate limits for this app, blocking real users
- If Twitter revokes the secret in response to abuse, all users lose auth until the app is redeployed
- Permanent in git history even after code removal — requires history rewrite or secret rotation

**Severity:** HIGH
**Confidence:** High
**CWE:** CWE-798 (Use of Hard-coded Credentials)
**OWASP Mobile:** M9 – Insecure Data Storage / M1 – Improper Credential Usage

**Remediation:**
```diff
- "Basic ${Base64.encodeToString(
-     "QnFuclQ0SGZIS01zVlZsdm5jU0o6MTpjaQ:r3KjJTwKRuhNrBDJgFI0SzkCQqYlf59H3CrgfBSDASda3Lc-MM".toByteArray(),
-     Base64.NO_WRAP + Base64.URL_SAFE
- )}"
+ "Basic ${Base64.encodeToString(
+     "${BuildConfig.TWITTER_CLIENT_ID}:${BuildConfig.TWITTER_CLIENT_SECRET}".toByteArray(),
+     Base64.NO_WRAP + Base64.URL_SAFE
+ )}"
```

Inject via `local.properties` → `buildConfigField` in `build.gradle`, with `.gitignore` covering
`local.properties`. **Immediately rotate the exposed secret in the Twitter Developer Portal.**

---

### SEC-02: Release CI Ships Debug APK; `verifyReleaseDebugInjectorAbsent` Never Runs [HIGH]

**Location:** `.github/workflows/release.yml` — "Build APK (Debug)" step

**Vulnerable Configuration:**
```yaml
- name: Build APK (Debug)
  run: |
    ./gradlew --no-daemon clean assembleDebug
```

And later:
```yaml
APK=$(find . -type f -path "*/app/build/outputs/apk/debug/*.apk" ...)
```

**Vulnerability:**
The release workflow (triggered on `v*` tags) builds `assembleDebug`, not `assembleRelease`.
This means:
1. The `verifyReleaseDebugInjectorAbsent` Gradle task (which `dependsOn("assembleRelease")`)
   is **never invoked** in the release pipeline — the entire safety gate is dead letter.
2. GitHub Releases ship **debug APKs**: these include `DebugDataInjector`, `DebugIntentHandler`,
   and all debug-only code, exposing the full debug intent surface to any user who installs
   the release APK from the GitHub Releases page.
3. Debug APKs are signed with the Android debug keystore (`~/.android/debug.keystore`), which
   is the same on every developer machine — trivially impersonatable.

**Exploit Scenario:**
Any user who installs the GitHub-released APK can run:
```bash
adb shell am start -n com.github.jayteealao.crumbs/.MainActivity \
    --es debug_action seed --ez wipe true
```
or
```bash
adb shell am start -n com.github.jayteealao.crumbs/.MainActivity \
    --es debug_action corrupt_token
```
This wipes the local Room DB or corrupts the Twitter auth token — both destructive user-data
operations — on any installed device where the "release" APK is sideloaded.

**Impact:**
- Entire `DebugDataInjector` + `DebugIntentHandler` surface ships to end-users
- Users' bookmark databases can be wiped by a local malicious app sending an intent
- Auth tokens can be corrupted, forcing re-authentication
- Debug keystore signature means the APK can be re-signed and redistributed trivially

**Severity:** HIGH
**Confidence:** High
**CWE:** CWE-489 (Active Debug Code in Production)
**OWASP Mobile:** M8 – Security Misconfiguration

**Remediation:**
```diff
-      - name: Build APK (Debug)
-        run: |
-          ./gradlew --no-daemon clean assembleDebug
+      - name: Build & verify release APK
+        run: |
+          ./gradlew --no-daemon clean assembleRelease verifyReleaseDebugInjectorAbsent
```

```diff
-          APK=$(find . -type f -path "*/app/build/outputs/apk/debug/*.apk" ...)
+          APK=$(find . -type f -path "*/app/build/outputs/apk/release/*.apk" ...)
```

---

### SEC-03: `dispatchDebugIntent` Catches All `Throwable` — Silent Swallow Risk [MED]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/MainActivity.kt:42-54`

**Code:**
```kotlin
} catch (_: ClassNotFoundException) {
    // Release variant — DebugIntentHandler is excluded by AGP source-set rules.
} catch (_: Throwable) {
    // Any reflective failure is debug-only noise; swallow.
}
```

**Vulnerability:**
The catch-all `Throwable` block silently swallows any exception from reflection, including
`SecurityException`, `IllegalAccessException`, or failures in `EntryPointAccessors` — errors
that in production would indicate an unexpected class is present (e.g., if a build system
misconfiguration included the debug class in a release build). The contract comment says
"Release builds throw ClassNotFoundException" but if a release build ever contains the class
(e.g., due to build variant misconfiguration), the handler runs silently and executes
`run(wipe=true)` with no error surface.

Additionally, `dispatchDebugIntent` is called for **every incoming Intent**, including the initial
`onCreate` intent. If MainActivity receives an `ACTION_VIEW` deep-link intent with
`debug_action` as an extras key (which any app can craft), the handler checks `debug_action`
before `ClassNotFoundException` is thrown in release — this is fine in release but documents
a pattern that should be explicitly documented.

**Exploit Scenario (release build only if SEC-02 is not fixed):**
Given SEC-02 is unresolved, a debug APK ships to users. The broad Throwable catch means any
unexpected Hilt injection failure also passes silently, making debugging harder.

**Impact:** Moderate — primarily reduces auditability; not directly exploitable in a correctly
built release APK. Becomes HIGH if combined with SEC-02.

**Severity:** MED
**Confidence:** High
**CWE:** CWE-390 (Detection of Error Condition Without Action)

**Remediation:**
```kotlin
} catch (_: ClassNotFoundException) {
    // Release variant — expected.
} catch (e: Exception) {
    // Log unexpected reflective failures at WARN level so they surface in crash reporting.
    if (BuildConfig.DEBUG) Timber.w(e, "DebugIntentHandler dispatch failed unexpectedly")
}
```
Narrow `Throwable` to `Exception`; at minimum log unexpected cases.

---

### SEC-04: Unbounded Firestore Collection Read [MED]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreRepository.kt:40`

**Vulnerable Code:**
```kotlin
val snapshot = db.collection(TWEETS_COLLECTION).get().await()
```

**Vulnerability:**
`getAllTweetIds()` performs an unbounded `.get()` on the `tweets` Firestore collection. For a
user with many thousands of bookmarks, this reads every document on every incremental sync to
compute the diff. There is no `.limit()` call and no server-side cursor/pagination. This causes:
1. Unbounded Firestore read costs (billed per document read)
2. Potential OOM on the device if the collection is very large
3. A slow cold-path that blocks the backfill sync on every app start

While not an injection or auth bypass, the lack of bounds creates a financial DoS vector (an
attacker who gains access to the Firestore project or a user who accumulates extreme tweet volumes
could cause unbounded billing) and violates the "cost-efficient incremental sync" goal of the
recent commit.

**Severity:** MED
**Confidence:** Med
**CWE:** CWE-400 (Uncontrolled Resource Consumption)

**Remediation:**
```kotlin
// Use the most recent known tweet's server-timestamp as an upper bound,
// and store a Firestore cursor in local prefs.
db.collection(TWEETS_COLLECTION)
    .orderBy("createdAt", Query.Direction.DESCENDING)
    .limit(1000)   // or use startAfter(cursor)
    .get().await()
```
Consider storing the last-synced Firestore document snapshot as a pagination cursor in DataStore,
so incremental syncs only read new documents rather than the full collection.

---

### SEC-05: Maestro `corrupt_token` Action — Confirm Debug-Only Scope [LOW]

**Location:** `maestro/sync_error.yaml:42-43`

**Code:**
```yaml
- launchApp:
    arguments:
      debug_action: "corrupt_token"
```

**Assessment:**
The `corrupt_token` intent is only effective in debug builds because `DebugIntentHandler` lives
in `app/src/debug/`. Once SEC-02 is fixed (release CI builds `assembleRelease`), this Maestro
flow cannot affect production users. **No action required beyond fixing SEC-02.** This finding
documents the dependency.

**Severity:** LOW
**Confidence:** High

---

### SEC-06: Twitter Access Token Logged via `Timber.d` [LOW]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/services/TwitterAuthClientImpl.kt:82` and `:108`

**Vulnerable Code:**
```kotlin
Timber.tag("access token")
Timber.d("token actual data: ${result?.accessToken}")
```

**Vulnerability:**
The raw Twitter OAuth2 access token is passed to `Timber.d`. In debug builds, Timber routes to
`android.util.Log`, which writes to `logcat`. Logcat is readable by:
- Any app holding `READ_LOGS` permission (granted to shell; grantable to malicious apps on older
  Android versions)
- ADB-connected tools

In release builds, Timber's `ReleaseTree` discards debug-level logs **only if a ReleaseTree is
planted**. The diff does not show a `Timber.plant(Timber.DebugTree())` / `Timber.plant(ReleaseTree())`
split in `Crumbs.kt` or the Application class. If only `DebugTree` is planted unconditionally,
tokens are logged in release too.

**Also affected:** `AuthRepository.kt:36` logs composite string with `$access` and `$refreshToken`
in a `Timber.d` call.

**Severity:** LOW (conditional on ReleaseTree being planted; check `CrumbsApplication`)
**Confidence:** Med
**CWE:** CWE-532 (Insertion of Sensitive Information into Log File)
**OWASP Mobile:** M1 – Improper Credential Usage

**Remediation:**
1. Verify `CrumbsApplication.kt` plants `Timber.DebugTree()` inside `if (BuildConfig.DEBUG)` only.
2. Remove or redact the `accessToken` from log calls:
   ```kotlin
   // Before
   Timber.d("token actual data: ${result?.accessToken}")
   // After
   Timber.d("token obtained (length=${result?.accessToken?.length ?: 0})")
   ```
3. Apply same redaction to `AuthRepository.kt:36`.

---

### SEC-07: `android:allowBackup=true` with Empty Cloud Backup Rules [LOW]

**Location:** `app/src/main/AndroidManifest.xml:10` + `app/src/main/res/xml/data_extraction_rules.xml`

**Vulnerable Configuration:**
```xml
android:allowBackup="true"
android:dataExtractionRules="@xml/data_extraction_rules"
```
`data_extraction_rules.xml` has an empty `<cloud-backup>` block with a TODO comment.

**Vulnerability:**
With `allowBackup=true` and no explicit `<exclude>` directives, Android Auto Backup includes
DataStore files (which contain OAuth tokens) in cloud backups synced to the user's Google account.
If a Google account is compromised, an attacker can restore the backup to another device and
obtain valid OAuth tokens without re-authenticating.

**Severity:** LOW
**Confidence:** High
**CWE:** CWE-312 (Cleartext Storage of Sensitive Information)
**OWASP Mobile:** M9 – Insecure Data Storage

**Remediation:**
```xml
<!-- data_extraction_rules.xml -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="datastore" path="." />
        <exclude domain="database" path="." />
    </cloud-backup>
</data-extraction-rules>
```

---

### SEC-08: `minifyEnabled false` in Release Build [NIT]

**Location:** `app/build.gradle:55`

**Code:**
```groovy
release {
    minifyEnabled false
    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
}
```

**Issue:**
Minification (R8) is disabled for release. This means:
- Class names, method names, and string literals are not obfuscated — easier to reverse-engineer
- The `verifyReleaseDebugInjectorAbsent` task's class-name string search is correct, but without
  minification there is no second layer of protection (R8 would normally strip unreferenced debug
  classes)
- Binary size is larger than necessary

**Severity:** NIT
**Confidence:** High

**Remediation:** Enable `minifyEnabled true` and `shrinkResources true` for release builds and
tune `proguard-rules.pro` to keep required reflection targets.

---

## 5) Security Posture Assessment

### Debug Bridge Architecture
The reflective `Class.forName` pattern for debug-only code is **architecturally sound**:
AGP source-set rules exclude `app/src/debug/` from release compilation, so `DebugIntentHandler`
is genuinely absent from release bytecode. The `verifyReleaseDebugInjectorAbsent` Gradle task
provides a secondary check. **However**, SEC-02 renders that task inert because the release CI
never calls `assembleRelease`.

### OAuth Token Handling
PKCE is correctly implemented in `TwitterAuthClientImpl` with `SecureRandom` + SHA-256.
Token storage in DataStore is appropriate (encrypted at rest by Android Keystore on API 23+,
though the DataStore implementation here uses unencrypted preferences — not visible in diff).
The main weaknesses are logging (SEC-06) and the hardcoded app secret (SEC-01).

### Intent Surface
`MainActivity` is `exported=true` (required for the OAuth deep-link `crumbs://` intent filter).
The OAuth redirect URI uses a custom scheme (`crumbs://graphitenerd.xyz`) rather than `https://`
App Links, which is acceptable for a personal-use app but means any app can register the same
scheme on a device to intercept the OAuth code. This is a known limitation of custom-scheme
redirects and is pre-existing, not introduced in this branch.

### Firestore / Sync
The new incremental sync code (`fetchTweetsNotInLocal`) correctly gates inserts on tombstone
presence (`deletedBookmarkRepository.isDeleted`). The `SyncErrorBus` properly surfaces auth
errors as UI events without logging token values. The unbounded read (SEC-04) is the main gap.

---

## 6) Recommendations by Priority

### Fix Before Shipping (HIGH)

1. **SEC-01 — Rotate + externalize Twitter app secret**
   - Action: Rotate secret in Twitter Dev Portal immediately; move to `buildConfigField` from `local.properties`
   - Effort: 30 min (rotation) + 15 min (code change)

2. **SEC-02 — Fix release CI to build `assembleRelease` + run verification gate**
   - Action: Change `assembleDebug` → `assembleRelease verifyReleaseDebugInjectorAbsent` in `release.yml`
   - Effort: 5 min

### Fix Soon (MED)

3. **SEC-03 — Narrow Throwable catch in `dispatchDebugIntent`**
   - Effort: 5 min

4. **SEC-04 — Add pagination/limit to Firestore `getAllTweetIds()`**
   - Effort: 1–2 hours (requires cursor persistence)

### Backlog (LOW/NIT)

5. **SEC-06 — Remove access token from `Timber.d` calls; confirm ReleaseTree is planted**
6. **SEC-07 — Exclude DataStore + DB from cloud backup in `data_extraction_rules.xml`**
7. **SEC-08 — Enable `minifyEnabled true` for release**

---

## 7) False Positives & Notes

- **SEC-05** is informational only; once SEC-02 is fixed it has zero production impact.
- The `DebugDataInjector` seeding fake tokens (`DEBUG_TWITTER_ACCESS`, `DEBUG_REDDIT_ACCESS`)
  is safe because these are obviously invalid tokens that will 401 against real APIs — no
  accidental real-token leakage from the seed path.
- The custom scheme OAuth redirect (`crumbs://graphitenerd.xyz`) is a pre-existing design
  decision not introduced in this branch; not flagged here.

---

*Review completed: 2026-05-18*
