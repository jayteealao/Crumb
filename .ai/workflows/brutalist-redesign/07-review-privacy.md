---
command: /review privacy
session_slug: brutalist-redesign
date: 2026-05-18
scope: slug-wide (git diff main...HEAD)
target: branch main...HEAD
paths: all changed files
---

# Privacy Review — brutalist-redesign

**Reviewed:** diff / git diff main...HEAD
**Date:** 2026-05-18
**Reviewer:** Claude Code

---

## 0) Scope, Context, and Data Classification

**What was reviewed:**
- Scope: slug-wide (`git diff main...HEAD`)
- 380 files changed, +16 821 additions, −9 318 removals
- Key surfaces: OAuth token storage/transit, DebugDataInjector, Firestore backfill, logging, error banners, Maestro flows, onboarding/splash, snapshot goldens, README

**Privacy context:**
- **Jurisdiction:** App is single-user (personal bookmarks), target market implicit US/global
- **Applicable regulations:** GDPR, CCPA (if distributed via Play Store); COPPA N/A (no age gate, no children's features)
- **User base:** personal-use social-content bookmarking; user is their own data subject
- **Data sensitivity:** OAuth access/refresh tokens (highly sensitive); Twitter/Reddit usernames + display names + profile image URLs (PII); tweet body text + Reddit post body text (potentially sensitive); bookmarks as behavioral data; Firestore backup of above

**Data inventory:**

| Field | Sensitivity | Where stored | Where transmitted |
|---|---|---|---|
| Twitter access token | Highly Sensitive | DataStore (Prefs) | Twitter API `Authorization: Bearer` header |
| Twitter refresh token | Highly Sensitive | DataStore (Prefs) | Twitter OAuth token-refresh endpoint |
| Reddit access token | Highly Sensitive | DataStore (Prefs) | Reddit API `Authorization: Bearer` header |
| Reddit refresh token | Highly Sensitive | DataStore (Prefs) | Reddit OAuth token-refresh endpoint |
| Twitter userId | Sensitive PII | Room DB + Firestore | Twitter API (path param) |
| Twitter username | Sensitive PII | Room DB + Firestore | UI display; Firestore |
| Twitter display name | Sensitive PII | Room DB + Firestore | UI display; Firestore |
| Twitter profile image URL | Sensitive PII | Room DB + Firestore | UI display; Firestore |
| Tweet body text | Sensitive (behavioral) | Room DB + Firestore | Firestore backup |
| Reddit username | Sensitive PII | DataStore + Room DB | UI display |
| Reddit post body/title | Sensitive (behavioral) | Room DB | — |
| Bookmark tombstones (IDs) | Less Sensitive | Room DB (`deleted_bookmarks`) | — |

**Debug-only seed data (excluded from release):**
- Debug tokens: `DEBUG_TWITTER_ACCESS`, `DEBUG_TWITTER_REFRESH`, `DEBUG_REDDIT_ACCESS`, `DEBUG_REDDIT_REFRESH`
- Debug usernames: `crumbs_test`, userId: `debug-user-twitter`

---

## 1) Executive Summary

**Merge Recommendation:** APPROVE_WITH_COMMENTS

**Rationale:**
No BLOCKER findings. The debug token isolation architecture is sound — AGP source-set exclusion plus a Gradle `verifyReleaseDebugInjectorAbsent` gate makes accidental release leakage mechanically prevented and verified. Two HIGH findings require attention before broad distribution: (1) bookmark IDs (`tweet.id` / `reddit.id`) are logged at `Timber.d` level in long-press action handlers in production-build code — these are platform identifiers that could allow reverse-identification; (2) the Firestore backfill lacks Firebase Auth-based per-user namespacing, meaning all tweets land in a flat top-level collection with no enforced ownership boundary.

The remaining findings are MED and LOW/NIT — logging hygiene improvements and the absence of a deletion pathway for Firestore-backed data.

**Overall Privacy Posture:**
- Data Minimization: Good (error banners contain no tokens; UI fields are scoped tightly)
- Storage Security: Adequate (tokens in DataStore; no evidence of plaintext disk writes beyond DataStore)
- Transmission Security: Strong (HTTPS via OkHttp/Retrofit; Bearer tokens in headers only, never URLs)
- Logging Hygiene: Mostly Clean (no tokens in logs; bookmark IDs logged at debug level in production code — HIGH)
- User Rights: Partially Supported (logout/clear-tokens implemented; no deletion of Firestore-backed data; no data export)
- Third-Party Risk: Moderate (Firestore receives tweet content, usernames, display names; no evidence of other third-party sharing)

**Compliance Status:**
- GDPR: Issues (Art. 17 right to erasure gap for Firestore data; Art. 5(1)(c) data minimisation concern in Firestore flat collections)
- CCPA: Issues (same deletion gap)
- HIPAA: N/A

---

## 2) Findings Table

| ID | Severity | Confidence | Category | File:Line | Privacy Issue |
|---|---|---|---|---|---|
| PRIV-01 | HIGH | High | PII in Logs | `AllBookmarksScreen.kt:454–524` | Bookmark IDs logged via `Timber.d` in release-eligible code |
| PRIV-02 | HIGH | High | Firestore Namespacing / Missing Deletion | `FirestoreRepository.kt` (whole file) | Flat top-level Firestore collections, no per-user namespace, no delete flow for remote data |
| PRIV-03 | MED | High | Logging — Boolean token flag | `LoginRoute.kt:40` | `Timber.d("access approved (Twitter: $twitterAccess, Reddit: $redditAccess)")` logs boolean auth state |
| PRIV-04 | MED | Med | Logging — bookmark IDs in Reddit/Twitter routes | `RedditBookmarksScreen.kt`, `TwitterBookmarksScreen.kt` | Same `Timber.d` long-press pattern as PRIV-01 |
| PRIV-05 | MED | High | Firestore write: tweet content + usernames | `FirestoreRepository.kt:uploadTweet` | Tweet body, author username, display name, profile image URL synced to Firestore with no field-level minimisation |
| PRIV-06 | LOW | High | Missing Firestore deletion on local soft-delete | `Repository.kt:softDelete` | `DeletedBookmarkRepository.softDelete` tombstones locally but does not delete from Firestore |
| PRIV-07 | LOW | Med | debug_action via Intent extra is ADB-accessible | `DebugIntentHandler.kt:28` | `debug_action` reads any Intent extra; in debug builds a malicious app with `QUERY_ALL_PACKAGES` could trigger seed/corrupt via crafted implicit intent |
| PRIV-08 | LOW | High | Onboarding: no first-launch data collection | `OnboardingScreen.kt`, `SplashRoute.kt` | (POSITIVE finding) — Onboarding collects no data; splash auto-navigates based on token presence only |
| PRIV-09 | NIT | High | README: no mention of Firestore data retention | `README.md` | No user-facing description of what is synced or how to delete Firestore backup |

---

## 3) Findings (Detailed)

### PRIV-01: Bookmark IDs logged in production-eligible long-press handlers [HIGH]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt` — lines 454, 464, 470, 475, 524

**Evidence:**
```kotlin
// AllBookmarksScreen.kt ~line 454
Timber.d("AllBookmarks long-press: TAG ${bookmark.id}")
Timber.d("AllBookmarks long-press: OPEN ${bookmark.id}")
Timber.d("AllBookmarks long-press: SHARE ${bookmark.id}")
Timber.d("AllBookmarks long-press: DELETE ${bookmark.id}")
```

**Privacy Issue:**
`bookmark.id` is the platform-native tweet ID (e.g. `"1922001234567890xxx"`) or Reddit post `name` (e.g. `"t3_xyz123"`). While not a human name or email, these are stable, globally-unique identifiers that map unambiguously to public posts and thus to the user's reading behaviour. They appear in Logcat output in all build variants (Timber strips debug-tree in release but the call-sites are in production source; whether a release tree is planted is app-level configuration that can change). They also appear in the Maestro `lazylogcat` log artifact captured in CI.

**Data flow:**
```
User long-presses bookmark
  → Timber.d("... ${bookmark.id}")
  → Logcat (accessible to: any app with READ_LOGS, ADB, logcat CI artifacts)
  → maestro-logs/<timestamp>.log captured by scripts/run-maestro.ps1
```

**Impact:**
- Bookmark IDs in logs allow log readers (including CI artefacts) to reconstruct the user's saved content list.
- GDPR Art. 5(1)(f): confidentiality/integrity of processing.

**Severity:** HIGH
**Confidence:** High
**Compliance:** GDPR Art. 5(1)(f); CCPA § 1798.100(c)

**Remediation:**
Remove `bookmark.id` from `Timber.d` stubs, or strip it:
```kotlin
// Before
Timber.d("AllBookmarks long-press: DELETE ${bookmark.id}")
// After — no identifier in log
Timber.d("AllBookmarks long-press: DELETE")
```
Since these are placeholder stubs that will be replaced by real handlers, remove them entirely when wiring real behavior.

---

### PRIV-02: Firestore backfill uses flat top-level collections with no per-user namespace and no deletion pathway [HIGH]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreRepository.kt` — entire file

**Evidence:**
```kotlin
private const val TWEETS_COLLECTION = "tweets"
private const val USERS_COLLECTION = "users"
// ...
db.collection(TWEETS_COLLECTION).document()   // No UID sub-path
db.collection(USERS_COLLECTION).document()    // No UID sub-path
```
No `FirebaseAuth.getInstance().currentUser?.uid` call exists anywhere in `FirestoreRepository.kt` (confirmed by grep: 0 hits for `FirebaseAuth`, `currentUser`, `getUid`, `auth.`).

**Privacy Issue:**
All tweets — including tweet body, author username, display name, profile image URL — are written to flat global Firestore collections (`tweets`, `users`, `media`, etc.) not partitioned by any user identifier. Firestore Security Rules (not in this repo) are the only protection against cross-user read. Without per-user namespacing (e.g. `users/{uid}/tweets/{tweetId}`):

1. A misconfigured Security Rules push exposes all users' bookmark content to any authenticated Firebase user.
2. There is no way to delete a specific user's data from Firestore (no `deleteUserData(uid)` function exists in the repo).

**Data stored in Firestore per tweet:**
- `text` (tweet body — behavioral data)
- `authorId`, `userId` (Twitter numeric user ID)
- `username`, `name` (Twitter username + display name — PII)
- `profileImageUrl` (PII)
- `publicMetrics` (engagement stats)

**Data flow:**
```
Twitter API → produceTweetResponseEntities()
  → Repository.saveTweetEntities(uploadToFirestore = true)
    → FirestoreRepository.uploadTweet(tweetEntities)
      → db.collection("tweets").document()
         .set(FirestoreTweet.fromTweetEntity(...))
      → db.collection("users").document()
         .set(FirestoreUser.fromTwitterUserEntity(...))
```

**Impact:**
- GDPR Art. 17: no mechanism to delete user data from Firestore on account logout or user request.
- GDPR Art. 5(1)(c): data minimization — tweet engagement metrics (`publicMetrics`) are uploaded but not necessary for bookmark sync.
- Potential for cross-user data exposure if Firestore rules are misconfigured.

**Severity:** HIGH
**Confidence:** High
**Compliance:** GDPR Art. 17, Art. 5(1)(c), Art. 32; CCPA § 1798.105

**Remediation:**

1. **Namespace by UID:**
```kotlin
// Use Firebase Auth UID as top-level segment
private fun userDoc(): DocumentReference {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
        ?: error("FirestoreRepository used without authenticated Firebase user")
    return db.collection("users").document(uid)
}

// All collections become sub-collections:
// users/{uid}/tweets/{docId}
// users/{uid}/twitterUsers/{docId}
```

2. **Add a `deleteAllUserData(uid)` function** that batch-deletes all sub-collection documents.

3. **Minimise fields:** do not upload `publicMetrics` — these can be re-fetched from the Twitter API if needed and add unnecessary personal data volume.

---

### PRIV-03: Auth-state boolean logged in LoginRoute [MED]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/LoginRoute.kt:40`

**Evidence:**
```kotlin
Timber.d("access approved (Twitter: $twitterAccess, Reddit: $redditAccess)")
```

**Privacy Issue:**
While `$twitterAccess` and `$redditAccess` are Boolean values (`true`/`false`), this log line confirms that a specific device user has active OAuth sessions with both Twitter and Reddit. Combined with device-level log access, this constitutes a low-level credential-status disclosure.

**Severity:** MED
**Confidence:** High
**Compliance:** GDPR Art. 5(1)(f)

**Remediation:**
```kotlin
// Remove log entirely, or use a non-identifying message:
Timber.d("Login gate passed")
```

---

### PRIV-04: Bookmark IDs in Reddit and Twitter long-press logs [MED]

**Location:**
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreen.kt` (long-press stubs)
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt` (long-press stubs)

**Evidence:** (same pattern as PRIV-01)
```kotlin
Timber.d("Reddit long-press: TAG ${bookmark.id}")
Timber.d("Twitter long-press: DELETE ${bookmark.id}")
```

**Privacy Issue:** Same as PRIV-01 — platform content identifiers in Logcat.

**Severity:** MED (downgraded from HIGH relative to PRIV-01 because these are in feature modules rather than the primary AllBookmarks route, but same root cause)
**Confidence:** High
**Remediation:** Remove `${bookmark.id}` from log message bodies when replacing stubs.

---

### PRIV-05: Tweet content and user PII fields synced wholesale to Firestore [MED]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreRepository.kt` — `uploadTweet()`

**Evidence:**
```kotlin
batch.set(tweetRef, FirestoreTweet.fromTweetEntity(tweetEntities.tweetEntity))
// FirestoreTweet contains: text, authorId, lang, createdAt, ...

batch.set(userRef, FirestoreUser.fromTwitterUserEntity(user))
// FirestoreUser contains: userId, name, username, profileImageUrl, description, verified
```

**Privacy Issue:**
`FirestoreUser` uploads `description` (the Twitter user's bio — free-text PII) and `verified` / `verifiedType`. The `description` field is not needed for bookmark sync and constitutes over-collection. `publicMetrics` (likes, retweets, replies count) are engagement data attached to tweets that are not needed for local bookmark restoration.

**Severity:** MED
**Confidence:** Med (depends on what `FirestoreTweet` / `FirestoreUser` map classes include — not all fields visible in the diff)
**Compliance:** GDPR Art. 5(1)(c) data minimization

**Remediation:**
Audit `FirestoreTweet.fromTweetEntity()` and `FirestoreUser.fromTwitterUserEntity()` to exclude: `description`, `publicMetrics`, `verifiedType`. Keep only the minimum needed to reconstruct the bookmark: `tweetId`, `text`, `authorId`, `createdAt`, `lang`, `order`, `username`, `name`.

---

### PRIV-06: Soft-delete tombstone not propagated to Firestore [LOW]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt` — `softDelete()`

**Evidence:**
```kotlin
// Repository.kt
suspend fun softDelete(id: String) {
    deletedBookmarkRepository.softDelete(id, BookmarkSource.TWITTER)
}
// No Firestore deletion call
```

**Privacy Issue:**
When a user deletes a bookmark locally, the tombstone is written to Room's `deleted_bookmarks` table and the item is filtered from paging queries. However, the corresponding Firestore document is never deleted. On a fresh install or new device, the Firestore backfill (`fetchTweetsNotInLocal`) would re-import the "deleted" tweet because the tombstone does not exist on the new device. This means: (a) user intent to delete is not honoured durably; (b) content the user removed from the app persists indefinitely in Firestore.

**Severity:** LOW (single-user app, user controls their own Firestore; but violates user expectation and GDPR Art. 17 spirit)
**Confidence:** High
**Compliance:** GDPR Art. 17 (right to erasure — not met for remote backup)

**Remediation:**
```kotlin
suspend fun softDelete(id: String) {
    deletedBookmarkRepository.softDelete(id, BookmarkSource.TWITTER)
    scope.launch(Dispatchers.IO) {
        firestoreRepository.deleteTweet(id) // add this method to FirestoreRepository
    }
}
```
Add `suspend fun deleteTweet(tweetId: String)` to `FirestoreRepository` that deletes the document in the `tweets` collection (and cascades to `users`/`media`/`metrics` sub-documents for that tweet).

---

### PRIV-07: `debug_action` Intent extra readable by any app in debug builds [LOW]

**Location:** `app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugIntentHandler.kt:28`

**Evidence:**
```kotlin
val action = intent?.getStringExtra("debug_action") ?: return
```
The handler processes any Intent whose `debug_action` extra matches `"seed"` or `"corrupt_token"`. Because `MainActivity` processes both `onCreate` and `onNewIntent`, any app that can send an Intent to `MainActivity` could trigger the seed (wipes + repopulates Room) or the corrupt-token action (invalidates the Twitter access code, causing the next sync to 401).

**Privacy Issue:**
This is debug-build only and excluded from release via AGP source sets + `verifyReleaseDebugInjectorAbsent` task. However, in debug builds on a developer's device, a malicious or misconfigured app could:
- Wipe and reseed the database (data loss)
- Corrupt the access token (forces re-auth, potential session disruption)

The `exported` flag on `MainActivity` is not explicitly set to `false` in the debug `AndroidManifest.xml` addition.

**Severity:** LOW
**Confidence:** Med (requires another app on the same device to know the exact intent structure; debug build only)

**Remediation:**
Add a check in `DebugIntentHandler` to verify the Intent comes from the same UID, or add a secret nonce:
```kotlin
// Quick mitigation: verify caller UID
if (activity.callingActivity?.packageName != activity.packageName) {
    Timber.w("Ignored debug_action from external caller")
    return
}
```
Alternatively, set `android:exported="false"` on `MainActivity` in the debug `AndroidManifest.xml` override (though this may break Maestro, which needs the activity launchable from ADB).

---

### PRIV-08: Onboarding and Splash collect no data — CLEAN [LOW / Positive]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/OnboardingScreen.kt`, `SplashRoute.kt`

**Finding:**
Onboarding is purely presentational — static copy pages with no form fields, no analytics calls, no network requests, no permissions requests. `SplashRoute` only reads `loginViewModel.isAccessTokenAvailable` (a local DataStore boolean) to decide navigation direction. No first-launch data collection occurs before explicit OAuth consent.

**Verdict:** No privacy issues. Positive finding: consent is effectively gated at the OAuth step, which is appropriate for a single-user OAuth app.

---

### PRIV-09: README does not disclose Firestore sync or data retention [NIT]

**Location:** `README.md` (new file, lines 1–74)

**Evidence:**
The README describes build, test, Maestro flows, and project layout. It does not mention:
- That tweet content, usernames, and display names are synced to Google Firestore
- How to revoke or delete that data
- What Firebase project is used

**Severity:** NIT (developer/contributor README, not a user-facing privacy policy)
**Compliance:** Not a compliance violation for a personal-use app; would become one if distributed publicly without a privacy policy.

**Remediation:**
Add a "Data & Privacy" section to README noting: Firestore backup is optional; data is stored under the user's own Firebase project; deletion instructions if/when the Firestore delete pathway is implemented (PRIV-06).

---

## 4) Debug Token Surface Assessment

**Token values:**
- `DEBUG_TWITTER_ACCESS` = literal string `"DEBUG_TWITTER_ACCESS"`
- `DEBUG_TWITTER_REFRESH` = literal string `"DEBUG_TWITTER_REFRESH"`
- `DEBUG_REDDIT_ACCESS` = literal string `"DEBUG_REDDIT_ACCESS"`
- `DEBUG_REDDIT_REFRESH` = literal string `"DEBUG_REDDIT_REFRESH"`
- `INVALID_DEBUG_TOKEN` = used for corruption flow

**Collision risk with real tokens:**
Twitter access tokens are `OAuth2` Bearer tokens: opaque strings beginning with `AAAA...` (base64url encoded, 156+ chars). Reddit access tokens are also opaque random strings (`xxxxxxxx-xxxxxx_xxxxxxxx-x...`, typically UUID-like). None of the debug token strings match the format of real tokens. Collision probability is negligible.

**Firestore upload guard:**
`Repository.saveTweetEntities()` is called with `uploadToFirestore = true` for API-fetched tweets. Debug-seeded tweets are inserted directly via `dao.insertTweet()` in `DebugDataInjector.seedTwitter()` — bypassing `saveTweetEntities()` entirely. This means debug seed data is **not** uploaded to Firestore. This is correct.

**Release exclusion:**
The `verifyReleaseDebugInjectorAbsent` Gradle task scans the release APK's DEX entries for `DebugDataInjector` and fails the build if found. This provides a hard gate. The reflective `Class.forName` in `MainActivity.dispatchDebugIntent()` silently catches `ClassNotFoundException` in release — the release bytecode path is stable with one extra try/catch that is effectively a no-op.

**Overall debug token verdict:** Architecture is sound. No issues.

---

## 5) Snapshot / Screenshot Golden Assessment

**Location:** `core/designsystem/src/test/screenshots/`, `app/src/test/screenshots/`, `feature/*/src/test/screenshots/`

All golden screenshots are generated from composable previews using seeded/hardcoded test data:
- `HomeScreen_withSyncErrorBanner_*`: shows "ERR · RECONNECT TWITTER" / "RECONNECT" — no token, no user ID
- `LoginScreen_default_*`: shows only UI chrome, no credentials
- `UserProfileDisplay_*`: uses static preview data (`"@design"`, not a real username per LoginScreen preview)
- `CrumbsBookmarkCard_*`: uses fixture text (`"Brutalist design system applied to bookmarks..."`) that matches DebugDataInjector seed content

No real credentials, real user IDs, or real content found in the screenshot test fixtures.

**Verdict:** No PII in goldens.

---

## 6) Maestro Flow Assessment

**`sync_error.yaml` token corruption:**
```yaml
- launchApp:
    arguments:
      debug_action: "corrupt_token"
```
This writes `"INVALID_DEBUG_TOKEN"` to the `ACCESS_CODE` DataStore key. It does not commit any data to test infrastructure outside the emulator. The `takeScreenshot: sync_error_banner` output is a Maestro artefact stored locally (not uploaded anywhere in the diff). No real tokens are used or committed.

**`happy_path.yaml` seed:**
Seeds with `DEBUG_TWITTER_ACCESS` / `DEBUG_REDDIT_ACCESS` strings — not real tokens. See debug token assessment above.

**Verdict:** No privacy issues in Maestro flows.

---

## 7) Data Flow Summary

```
User → OAuth (Twitter/Reddit)
  → Access/Refresh tokens → DataStore (device-local, not logged)
  → Twitter API / Reddit API (HTTPS, Bearer in header)
    → Tweet/Post entities → Room DB (device-local)
      → Firestore backup (tweet text, username, display name — PRIV-02, PRIV-05)
        ← Firestore backfill on new device (respects local tombstones — PRIV-06 gap)

User long-press → Timber.d("... ${bookmark.id}") → Logcat → CI lazylogcat (PRIV-01, PRIV-04)

Error banner → "Twitter session expired. Tap to reconnect." → no token in message (CLEAN)
```

---

## 8) Recommendations by Priority

### High Priority (Fix Before Broad Distribution)

1. **PRIV-01 + PRIV-04**: Remove `${bookmark.id}` from all `Timber.d` long-press stubs in `AllBookmarksScreen.kt`, `RedditBookmarksScreen.kt`, `TwitterBookmarksScreen.kt`. Effort: 15 minutes. These stubs will be replaced anyway when behavior is wired; remove the ID portion now.

2. **PRIV-02**: Add Firebase Auth UID namespace to Firestore collections and implement `deleteAllUserData()`. Effort: 2–4 hours. This is the most significant architectural gap — currently the only protection against cross-user data exposure is Firestore Security Rules which are not in this repo.

### Medium Priority (Address in Next Slice)

3. **PRIV-03**: Remove or sanitize the `Timber.d("access approved (Twitter: $twitterAccess, Reddit: $redditAccess)")` line in `LoginRoute.kt:40`.

4. **PRIV-05**: Audit `FirestoreTweet.fromTweetEntity()` and `FirestoreUser.fromTwitterUserEntity()` to exclude `description`, `publicMetrics`, `verifiedType`.

### Low Priority / Backlog

5. **PRIV-06**: Propagate soft-delete to Firestore in `Repository.softDelete()` and `RedditRepository.softDelete()`.

6. **PRIV-07**: Add caller-UID check in `DebugIntentHandler` or document the accepted risk.

7. **PRIV-09**: Add a "Data & Privacy" section to README.

---

## 9) Overall Privacy Posture

| Axis | Rating | Notes |
|---|---|---|
| Data Minimization | Incomplete | Firestore uploads more fields than needed (PRIV-05) |
| Storage Security | Adequate | Tokens in DataStore; Room DB not encrypted at field level but is device-local |
| Transmission Security | Strong | HTTPS enforced; Bearer in headers only |
| Logging Hygiene | Mostly Clean | No tokens in logs; bookmark IDs logged at `Timber.d` (PRIV-01, PRIV-04) |
| User Rights (Deletion) | Partially Supported | Local soft-delete works; Firestore delete missing (PRIV-06) |
| Third-Party Risk | Moderate | Firestore (tweet content, usernames); no analytics; no ad SDKs |
| Debug Isolation | Strong | AGP source sets + `verifyReleaseDebugInjectorAbsent` gate |
| Consent / Onboarding | Clean | No pre-auth data collection; OAuth is the consent gate |

---

*Review completed: 2026-05-18*
