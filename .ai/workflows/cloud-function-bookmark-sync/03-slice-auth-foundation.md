---
schema: sdlc/v1
type: slice
slug: cloud-function-bookmark-sync
slice-slug: auth-foundation
status: implemented
stage-number: 3
created-at: "2026-05-19T21:23:52Z"
updated-at: "2026-05-19T21:23:52Z"
complexity: m
depends-on: []
tags: [firebase-auth, google-sign-in, account-linking, android]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
  siblings:
    - 03-slice-functions-oauth.md
    - 03-slice-daily-poll.md
    - 03-slice-android-reader.md
    - 03-slice-pending-delete.md
    - 03-slice-cutover-migration.md
  plan: 04-plan-auth-foundation.md
  implement: 05-implement-auth-foundation.md
---

# Slice: auth-foundation

## Goal

Land the Firebase Auth wiring on Android so that Google Sign-In becomes the primary credential, links to the existing Email/Password account under UID `6yPmdM14V3dPHLe3LO9XCfU4l9f1`, and unblocks every downstream slice that needs an authenticated `FirebaseAuth` / callable context.

## Why This Slice Exists

Every subsequent slice — OAuth state-minting callables, the Android Firestore reader, pull-to-refresh, migration — assumes there is an authenticated Firebase user in the running app. Standing up Google Sign-In + `linkWithCredential` first removes that assumption from every later plan. It is also the cheapest place to surface the `FirebaseAuthUserCollisionException` recovery flow (AC1) without entangling it with OAuth or Firestore changes.

## Scope

**In:**
- Add `firebase-auth-ktx` and `play-services-auth` to `gradle/libs.versions.toml` + `app/build.gradle`; confirm `firebase-firestore-ktx` is present (no upgrade in this slice).
- `CrumbApplication.kt` — initialize `FirebaseAuth` (Firebase is already initialized; just expose the instance via Hilt module if not already).
- `LoginScreen.kt` / `LoginRoute.kt` — add Google Sign-In button as primary; demote Email/Password to a "sign in with email instead" link/secondary path.
- `LoginViewModel.kt` — add `signInWithGoogle()` that:
  - Triggers `GoogleSignIn` credential pick (One Tap / `play-services-auth`).
  - Calls `signInWithCredential(GoogleAuthProvider.getCredential(...))`.
  - On `FirebaseAuthUserCollisionException` → emits a UI state directing user to sign in with email first, then auto-runs `linkWithCredential(googleCredential)` after the email/password sign-in completes.
  - Asserts UID == `6yPmdM14V3dPHLe3LO9XCfU4l9f1` after a successful sign-in for the single-user app (logged + visible in tests).
- Robolectric unit tests for `LoginViewModel`: success path, collision-then-link path, generic error path.
- Roborazzi snapshots for `LoginScreen` in three states: signed-out (Google primary), collision-prompt (existing-account dialog), signed-in (post-link transition).
- **Operational prereqs (bundled per slice 1 decision):**
  - Enable Google Sign-In provider in Firebase Console.
  - Register debug SHA-1, release SHA-1, and Play App Signing SHA-1 in Firebase Console.
  - Verify `google-services.json` is current and includes the OAuth client ID.

**Out (handled by other slices):**
- Any X / Twitter OAuth UI — that's `functions-oauth` + `android-reader`.
- Any Firestore reads or writes from the app — that's `android-reader`.
- Any callables — they need functions deployed first (`functions-oauth`).
- Migration of existing users' state from `Prefs.kt` X tokens — that's `cutover-migration`.

## Acceptance Criteria

- **Given** a fresh install on the user's device with no prior Crumb data, **when** the user taps Google Sign-In and picks `jayteealao@gmail.com`, **then** the app authenticates as UID `6yPmdM14V3dPHLe3LO9XCfU4l9f1` (existing UID, not a new one) and lazylogcat captures the matching UID under tag `FirebaseAuth`. (Satisfies the auth half of **AC1**.)
- **Given** an install where Google Sign-In yields `FirebaseAuthUserCollisionException` (existing email/password credential), **when** the user follows the in-app dialog and signs in with Email/Password, **then** `linkWithCredential(GoogleAuthCredential)` succeeds on the same UID and a single auth state results. (Satisfies the link half of **AC1**.)
- Robolectric tests for `LoginViewModel` pass: success, collision-then-link, generic error.
- Roborazzi snapshots for `LoginScreen` (3 states) match committed PNGs.
- Google Sign-In provider is enabled in Firebase Console and all three SHA-1s registered (operator confirms in verify).

## Dependencies on Other Slices

- None. This slice can ship first independently.

## Risks

- **SHA-1 misconfiguration** → `DEVELOPER_ERROR (10)` from One Tap. Mitigation: capture all three SHA-1s in the plan's operator checklist and confirm in verify before declaring AC1 met.
- **Existing UID re-binding gone wrong** → if `linkWithCredential` fails silently and the user lands on a brand-new UID, the 15,834 migrated docs become invisible. Mitigation: hard-assert `auth.currentUser?.uid == "6yPmdM14V3dPHLe3LO9XCfU4l9f1"` post-sign-in in code (single-user app — a guarded log is acceptable) and a Roborazzi/Maestro check that the bookmarks-list seed renders.
- **Google account picker UX differs across emulators** → Maestro flow `sign_in_google.yaml` lives in a later slice (it depends on the navigation past LoginScreen which the reader slice owns); here we cover sign-in via Robolectric + Roborazzi only. Live Maestro confirmation is acknowledged-deferred to `android-reader`.
