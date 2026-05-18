---
command: /review privacy (round 2)
session_slug: brutalist-redesign
date: 2026-05-18
scope: slug-wide (git diff main...HEAD)
target: branch feat/brutalist-redesign vs main
round: 2
---

# Privacy Review (Round 2) — brutalist-redesign

**Reviewed:** `git diff main...HEAD` (410 files, +27 691 / −9 455)
**Date:** 2026-05-18
**Reviewer:** Claude Code (privacy dimension, round 2)
**Round-1 baseline:** `07-review-privacy.md` (PRIV-01..09)

---

## 1) Executive Summary

**Merge Recommendation (privacy axis):** APPROVE_WITH_COMMENTS

**Rationale:**
Round-1 BLOCKER/HIGH items either landed or were explicitly dismissed with documented rationale:
- **H18 (PRIV-01 + PRIV-04)** — confirmed PATCHED in commit `7dcf586`. Bookmark IDs are no longer
  interpolated into `Timber.d` calls in **all three** long-press routes. Log strings now read
  `Long-press: TAG / OPEN / SHARE / DELETE` with no `${bookmark.id}` suffix.
- **H19 (PRIV-02)** — explicitly DISMISSED ("single-user app at present"). Round 2 stress-tests
  this dismissal against the threat model the user requested ("project stolen / forked /
  open-sourced"). **The dismissal is reasonable for the current state but documented as a
  brittle invariant** (R2-PRIV-01); details below.

No new BLOCKER privacy issues introduced by the fix commits.

Two new privacy concerns surfaced during round-2 search that the round-1 review did not enumerate:
- **R2-PRIV-02** — a NEW `Timber.d` line in commit `32e01af` logs the tweet ID at upload time
  (`FirestoreRepository.kt:271`). Same class of leak as H18 but in the Firestore upload path.
- **R2-PRIV-03** — `FirestoreRepository.uploadTweet()` now uses the platform-native tweet ID as
  the Firestore document ID (`db.collection(TWEETS_COLLECTION).document(tweetId)`). A viewer
  with index-only access to the Firestore project can enumerate every saved tweet ID without
  reading any document body. This is the deterministic-doc-id concern the user flagged.

Three pre-existing token-logging sites (`TwitterAuthClientImpl.kt`, `ApiResponseExt.kt`,
`AuthRepository.kt`) are **out of diff scope** — present on `main` before this branch — but
re-flagged here for completeness because Round 1 SEC-06 dismissed them as LOW and they should
be a follow-up cleanup.

---

## 2) Verification of Claimed Round-1 Fixes

### H18 / PRIV-01 + PRIV-04 — Bookmark IDs stripped from long-press logs

**Claim:** Commit `7dcf586` removed `${bookmark.id}` from every `Timber.d` long-press stub.

**Verification — all three handlers checked:**

| File | Lines | Status |
|---|---|---|
| `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt` | 271, 275, 280, 288 | PATCHED |
| `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt` | 221, 225, 230, 238 | PATCHED |
| `feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreen.kt` | 204, 208, 213, 221 | PATCHED |

**Evidence (current tree):**
```kotlin
// AllBookmarksScreen.kt:271
Timber.d("AllBookmarks long-press: TAG")
// AllBookmarksScreen.kt:288
Timber.d("AllBookmarks long-press: DELETE")

// TwitterBookmarksScreen.kt:221
Timber.d("Twitter long-press: TAG")
// TwitterBookmarksScreen.kt:238
Timber.d("Twitter long-press: DELETE")

// RedditBookmarksScreen.kt:204
Timber.d("Reddit long-press: TAG")
// RedditBookmarksScreen.kt:221
Timber.d("Reddit long-press: DELETE")
```

A repo-wide grep for `Timber.*\${.*\.id\}` and `Timber.*bookmark\.id` returns **zero** hits in
the long-press handlers. H18 is fully closed.

**Verdict:** CONFIRMED FIXED.

---

### H19 / PRIV-02 — Firestore not per-user UID-namespaced

**Dismissal recorded in Round 1:** "single-user app at present; will gate Firestore writes
behind auth-validation before allowing multi-user backend use."

**User's round-2 concern (paraphrased):** If the project were stolen / forked / open-sourced,
would another user reading the Firebase project credentials gain access?

**Verification of the dismissal's premises:**

1. **No Firebase Auth integration exists anywhere in the codebase.**
   Repo-wide grep for `FirebaseAuth`, `currentUser`, `getUid`, `firebase.auth`: zero hits.
   The Firestore client (`Firebase.firestore`) is initialized in
   `FirestoreRepository.kt:23` with no auth dependency.

2. **No `firestore.rules` or `firestore.indexes.json` file exists** in the repo.
   Security Rules are configured externally in the Firebase console (not in version control),
   so this review cannot inspect them. **The on-device code provides no authentication
   boundary; protection rests entirely on whatever Security Rules are configured server-side.**

3. **`google-services.json` IS tracked in git** (`app/google-services.json`) and contains the
   Firebase API key (`AIzaSyAUXoD37Cy8ghlz7dGggEC9w657nHLbo9U`) for project `crumbs-a4fdb`.
   If the repo is or becomes public, this key is publicly retrievable.

**Threat-model analysis for the "stolen / forked / open-source" scenario:**

| Scenario | Outcome | Reasoning |
|---|---|---|
| Repo cloned, attacker builds debug APK with same applicationId | **DEPENDS on Firebase console restrictions.** Android API keys are normally restricted by package name + SHA-1 release fingerprint. A debug build with the same `applicationId` (`com.github.jayteealao.crumbs`) and a different signing key WILL be rejected if SHA-1 restrictions are configured; otherwise the attacker can read/write Firestore. | Out of repo's scope to verify; relies on console config. |
| Repo cloned, attacker uses Firestore REST API directly with `AIzaSyAUXoD37Cy8ghlz7dGggEC9w657nHLbo9U` | **Same — depends on console restrictions.** Firebase Web API keys are not secrets by Google's own documentation; they are restricted server-side. | Same as above. |
| Repo cloned, attacker reads existing Firestore data via SDK from a different app | **Permitted if Security Rules are `allow read: if true` or any anonymous-friendly variant.** Without per-UID namespacing AND without authentication-required rules, every tweet in the `tweets` collection is readable to anyone who can authenticate (or anonymously, depending on rules). | Worst case: complete bookmark history exposed. |

**Verdict on the H19 dismissal:**

The dismissal rationale ("single-user app at present") is **defensible** in the narrow sense
that the app today writes only one user's data and has only one OAuth account configured.
However, the on-device code provides **no defence-in-depth**:
- No Firebase Auth gate before writes.
- Doc IDs are deterministic and enumerable (see R2-PRIV-03).
- The dismissal hard-depends on Firebase Security Rules being correctly configured server-side
  — but those rules are not visible to this review and are not version-controlled.

This is an **acceptable** posture for a single-user personal-use app **only if**:
1. Firebase Security Rules require `request.auth != null` and `request.auth.uid == resource.data.owner`.
2. The Firebase console API-key restrictions are set to package + SHA-1 fingerprint.
3. The repo remains private OR the API key is rotated before going public.

If any of those three conditions is unmet, H19 becomes a HIGH again. **Recommend recording H19
as a known fragile invariant with explicit pre-flight checks before broad distribution** rather
than treating it as fully resolved.

**Round-2 status:** Dismissal stands, but elevated visibility — see `R2-PRIV-01` below.

---

## 3) New Round-2 Findings

| ID | Severity | Confidence | Category | File:Line | Issue |
|---|---|---|---|---|---|
| R2-PRIV-01 | HIGH | High | Defence-in-depth / Auth boundary | `FirestoreRepository.kt` (whole file) | No Firebase Auth integration; Firestore writes proceed unconditionally. The H19 "single-user" dismissal hard-depends on externally-configured Security Rules and console restrictions that are not in this repo. |
| R2-PRIV-02 | MED | High | PII in Logs | `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreRepository.kt:271` | NEW `Timber.d("Successfully uploaded tweet $tweetId to Firestore")` introduced in commit `32e01af`. Reintroduces the same class of platform-content-ID leak that H18 patched. |
| R2-PRIV-03 | MED | High | Identifier leakage via deterministic doc-id | `FirestoreRepository.kt:222` (`db.collection(TWEETS_COLLECTION).document(tweetId)`) — added in `32e01af` | Tweet platform IDs are now the Firestore document IDs. A viewer with **index-only** access (e.g. an over-broad `list` permission in Security Rules) can enumerate every saved tweet ID without reading any document body. Doc IDs are surfaced by `Query.documentId()` and by Firestore index dumps. |
| R2-PRIV-04 | LOW | High | PII in Logs (out of diff scope, pre-existing on `main`) | `feature/twitter/.../TwitterApiServiceImpl.kt:37` | `Timber.d("userid: ${data.data.id} ${data.data.name} ${data.data.username}")` logs the Twitter user numeric ID, display name, and `@handle` whenever `getUser` succeeds. Not introduced by this branch but re-confirmed present at HEAD. |
| R2-PRIV-05 | LOW | High | Token logging (re-flag of round-1 SEC-06) | `TwitterAuthClientImpl.kt:82-85, 108-111, 144`; `ApiResponseExt.kt:30, 51, 55`; `AuthRepository.kt:36, 39, 43, 80` | OAuth access and refresh tokens are interpolated into `Timber.d` strings on multiple paths. Pre-existing on `main`; this branch did not introduce them. SEC-06 in Round 1 dismissed as LOW pending ReleaseTree planting verification. Still LOW. |

---

## 4) Findings (Detailed)

### R2-PRIV-01: Firestore has no on-device authentication boundary [HIGH]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreRepository.kt` — entire file
**Confidence:** High

**Evidence:**
```kotlin
// FirestoreRepository.kt:22-23
@Singleton
class FirestoreRepository @Inject constructor() {
    private val db: FirebaseFirestore = Firebase.firestore
    // ... no FirebaseAuth field, no auth check anywhere in the file
```

Repo-wide grep for `FirebaseAuth`, `currentUser`, `getUid`, `firebase.auth`: **0 hits**.

No `firestore.rules` or `firestore.indexes.json` file is committed. Security Rules are
managed externally in the Firebase console and cannot be inspected here.

**Threat model (project stolen / forked / open-sourced):**
- If Security Rules are misconfigured or permissive, any actor with the `google-services.json`
  contents can read/write the entire `tweets` collection.
- The `applicationId` (`com.github.jayteealao.crumbs`) and Firebase package-name binding
  prevent vanilla SDK use of the API key from a different app **only if** the Firebase
  console enforces SHA-1 release fingerprint restrictions. The repo cannot verify those.
- For REST API access, Firebase Web API keys are not secrets per Google docs; they rely on
  Security Rules. Same dependency.

**Why this is HIGH despite the H19 dismissal:**
The Round 1 dismissal said "single-user app at present" — true for write *quantity*, but the
dismissal does not address the *read* side, nor the data-at-rest exposure if the project is
forked. With no on-device auth and no version-controlled rules, this branch ships a Firestore
client that depends entirely on an out-of-repo configuration to enforce ownership. That is a
brittle invariant.

**Severity:** HIGH (defence-in-depth)
**Confidence:** High
**Compliance:** GDPR Art. 32 (security of processing — no application-level integrity check)

**Remediation (minimal acceptable):**
Before broad distribution / open-sourcing:
1. Verify Firebase console:
   - API key restricted to package name `com.github.jayteealao.crumbs` + release SHA-1.
   - Security Rules require `request.auth != null` AND scope per UID.
2. Add `firestore.rules` to this repo so the rules are version-controlled and reviewable.
3. Wire `FirebaseAuth.signInAnonymously()` or `signInWithCustomToken()` before any Firestore
   write, so the SDK actually attaches `request.auth.uid`. Today there is no `auth` token on
   any request.
4. Namespace collections by UID: `users/{uid}/tweets/{tweetId}` (the original PRIV-02 fix
   recommendation). Without this, even if rules require auth, all authenticated users see
   the same flat collection.

If item 4 is deferred ("single-user app"), the dismissal should be **documented in code** with
a `// SECURITY: requires Firebase Security Rules to enforce …` comment on the FirestoreRepository
class header so future contributors do not assume the SDK is providing protection.

---

### R2-PRIV-02: Tweet ID logged at upload time (NEW in commit 32e01af) [MED]

**Location:** `feature/twitter/.../firestore/FirestoreRepository.kt:271`

**Evidence:**
```kotlin
// Line 271 — newly added in commit 32e01af (idempotent Firestore upload)
batch.commit().await()
Timber.d("Successfully uploaded tweet $tweetId to Firestore")
```

Also at line 226: `Timber.d("Tweet $tweetId already in Firestore — merging only")` —
introduced by the same commit.

**Privacy issue:**
Same class of leak that H18 patched in long-press handlers. `tweetId` is a Twitter
platform-native ID (e.g. `1922001234567890xxx`) that maps unambiguously to a public tweet and
thus to the user's reading behaviour. These logs land in Logcat and any CI lazylogcat artifact
that captures the upload path during a Maestro flow that includes a sync.

The Round-1 review explicitly remediated this pattern in commit `7dcf586`; the new code in
`32e01af` reintroduces it in the Firestore upload path **without re-noting the PRIV-01
guidance**.

**Severity:** MED (Timber.d only, ReleaseTree-dependent; but the pattern is exactly what H18
flagged as HIGH on a different surface)
**Confidence:** High

**Remediation:**
```diff
- Timber.d("Successfully uploaded tweet $tweetId to Firestore")
+ Timber.d("Successfully uploaded tweet to Firestore")
- Timber.d("Tweet $tweetId already in Firestore — merging only")
+ Timber.d("Tweet already in Firestore — merging only")
```
Or, if the tweet ID is genuinely needed for debugging idempotency races, hash it (truncated
SHA-256) before logging.

Add a Timber `ReleaseTree` policy file or a custom `BaseTree` that strips numeric ID-shaped
substrings from log lines in production builds.

---

### R2-PRIV-03: Deterministic doc-id leaks tweet IDs via index-only access [MED]

**Location:** `FirestoreRepository.kt:222` (and the parallel reads at lines 58, 117, 132, etc.)
— introduced in commit `32e01af`.

**Evidence:**
```kotlin
// Line 222 — new in commit 32e01af
val tweetRef = db.collection(TWEETS_COLLECTION).document(tweetId)
// ...
// Line 117 — companion read path
db.collection(TWEETS_COLLECTION)
    .whereIn("tweetId", tweetIds)
```

The tweet's platform ID is used as the Firestore document key. Before commit `32e01af`,
documents were created with `.document()` (auto-id), so tweet IDs lived only inside the
document body field `tweetId`.

**Privacy issue:**
1. **Index enumeration:** Firestore indexes the document path, and `Query.documentId()` makes
   the doc ID queryable in its own right. If Security Rules permit `list` (or `list` is
   broader than `get`), a malicious authenticated user can enumerate every document key
   without reading any document body — i.e., they extract the user's saved tweet IDs even if
   the rules block reading tweet bodies.
2. **Backup / export artifacts:** Firestore Cloud Export jobs and the Emulator UI surface doc
   IDs separately from document bodies. A backup with relaxed sharing accidentally leaks the
   ID list.
3. **Logcat correlation:** Combined with R2-PRIV-02, the same ID appears in both the doc path
   and the log — easier to correlate Firestore operations to a particular bookmark from logs
   alone.

This is the trade-off the commit accepted in exchange for idempotency. The commit message
("`SetOptions.merge()`, so two concurrent syncs collapse into one document instead of racing
through existence-check + random-id `.document()`") is technically correct for the dedup
problem but introduces an identifier surface as a side effect.

**Severity:** MED
**Confidence:** High (deterministic doc-id is observable; whether it's exploitable depends on
Security Rules — same dependency as R2-PRIV-01)

**Remediation:**
- **Option A (minimal change):** Hash the tweet ID for the doc key:
  `document(tweetId.toSha256().take(16))`. Preserve dedup by storing the original `tweetId`
  field for query, but the doc path no longer leaks the platform ID.
- **Option B (better):** Namespace under user UID per R2-PRIV-01:
  `users/{uid}/tweets/{tweetId}` — at least an attacker needs to know the UID first.
- **Option C (accept and document):** Add a comment on `uploadTweet` explaining the trade-off
  and ensure Security Rules explicitly block `list` access.

---

### R2-PRIV-04 + R2-PRIV-05 [LOW — out of diff scope, pre-existing on `main`]

**R2-PRIV-04** — `feature/twitter/.../services/TwitterApiServiceImpl.kt:36-37`:
```kotlin
Timber.d("userRawData: ${response.raw().body}")               // entire API response body
Timber.d("userid: ${data.data.id} ${data.data.name} ${data.data.username}")  // PII triplet
```
Pre-existing (last modified in `800471e` on main). Not enumerated in round-1 PRIV-01 because
that scope was limited to bookmark-screen handlers. Strip to `Timber.d("getUser succeeded")`.

**R2-PRIV-05** — OAuth tokens interpolated into `Timber.d` strings at 11 call sites in
`TwitterAuthClientImpl.kt` (lines 82-85, 108-111, 144), `ApiResponseExt.kt` (30, 51, 55), and
`AuthRepository.kt` (36, 39, 43, 80). Pre-existing on `main`. Matches round-1 **SEC-06 LOW**;
deferral rests on a `ReleaseTree` being planted that suppresses `Timber.d`. Round 2 did not
find that ReleaseTree wiring in any `Application.onCreate()` — recommend audit.

Both LOW (Timber.d, ReleaseTree-dependent) and out of diff scope. Re-flagged for completeness
because the round-2 user prompt explicitly asked to re-scan for SEC-06-class leaks.

---

## 5) Data Inventory + Compliance Delta (Round 2)

No new PII *types* are collected. Two new *surfaces* for existing PII:
- `R2-PRIV-02`: tweet platform ID in Firestore upload log (commit `32e01af`).
- `R2-PRIV-03`: tweet platform ID as Firestore doc key (commit `32e01af`).

No new third-party data sharing surface, no new SDKs, no new permissions, no new telemetry.

**Compliance:** GDPR Art. 5(1)(f), 17, 25, 32 and CCPA § 1798.100/105 statuses are unchanged
from Round 1. H18 closed; R2-PRIV-02 reopens a smaller version of the Art. 5(1)(f) concern.
R2-PRIV-01 makes the Art. 25/32 brittle-invariant explicit.

---

## 6) Recommendations (Round 2)

### Address before broad distribution

1. **R2-PRIV-02** — strip `$tweetId` from the two `Timber.d` calls in
   `FirestoreRepository.kt:226, 271`. Trivial 2-line fix; matches the H18 pattern.
2. **R2-PRIV-01** — version-control `firestore.rules`; wire `FirebaseAuth.signInAnonymously()`
   before the first Firestore write so the SDK actually attaches `request.auth.uid`.
   Confirm Firebase console restrictions (API key by package + SHA-1, rules require auth).

### Address before open-sourcing

3. **R2-PRIV-03** — switch tweet doc keys to a hashed form (or namespace under UID per
   R2-PRIV-01 option B). Mitigates index-enumeration risk.
4. **H19 dismissal documentation** — annotate `FirestoreRepository` with an explicit
   `// SECURITY:` comment block describing the assumed Security Rules invariant so the
   "single-user app" rationale is durable.

### Cleanup (defer to a future "logging hygiene" slice)

5. **R2-PRIV-04** — drop `userRawData` and `userid: ...` logs in `TwitterApiServiceImpl.kt:36-37`.
6. **R2-PRIV-05** — strip token interpolations from all 11 call sites in TwitterAuthClient /
   ApiResponseExt / AuthRepository; verify a release `Timber.Tree` is planted that suppresses
   `Timber.d`.

### Already-closed in Round 2

- H18 (PRIV-01, PRIV-04) — CONFIRMED FIXED in commit `7dcf586`.

---

## 7) Round-2 Privacy Posture Delta

H18 patched; R2-PRIV-02 reintroduces a smaller leak in the Firestore upload path.
R2-PRIV-01 surfaces the auth-boundary brittle invariant explicitly. All other axes
(Storage Security, Transmission Security, Third-Party Risk, Consent / Onboarding) unchanged
from Round 1.

---

## 8) Round-2 Privacy Merge Verdict

**APPROVE_WITH_COMMENTS.**

The fix commits did not regress privacy posture in net. H18 is genuinely closed in all three
sites the user asked about. The H19 dismissal is defensible for the current single-user state
but the on-device code provides no defence-in-depth and depends on Firebase console
configuration that is outside this repo. Two new MED findings (R2-PRIV-02, R2-PRIV-03) come
from a tangential Firestore commit and are cheap to fix.

No BLOCKER. No new HIGH that wasn't already-known in spirit (R2-PRIV-01 is a re-statement of
H19 with explicit threat-model scoring rather than a new defect).

*Review completed: 2026-05-18*
