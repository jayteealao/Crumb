---
schema: sdlc/v1
type: implement-index
slug: cloud-function-bookmark-sync
status: in-progress
stage-number: 5
created-at: "2026-05-19T22:51:34Z"
updated-at: "2026-05-19T22:51:34Z"
slices-implemented: 1
slices-total: 6
metric-total-files-changed: 13
metric-total-lines-added: 500
metric-total-lines-removed: 94
tags: [firebase-auth, credential-manager, google-sign-in, account-linking, android, hilt, robolectric, roborazzi]
refs:
  index: 00-index.md
  plan-index: 04-plan.md
next-command: wf-verify
next-invocation: "/wf verify cloud-function-bookmark-sync auth-foundation"
---

# Implement Index

Master index for the six-slice implementation chain. One slice implemented in this round (`auth-foundation`); five slices remain to plan and implement.

## Slice Implementation Summaries

### `auth-foundation` *(implemented)*

- **Status:** complete; all gradle checks green (`testDebugUnitTest`, `recordRoborazziDebug`, `verifyRoborazziDebug`, `lintDebug`, `assembleDebug`).
- **Surface:** Firebase Auth + Credential Manager (Google Sign-In) + E/P account-linking recovery on Android. Brutalist Login UI updated.
- **Boundary:** `AuthGateway` interface; `CurrentUser(uid, email)` data class. Identity enforcement delegated function-side (next slice).
- **Foundations introduced:** Firebase BoM 34.13.0 (cascades to every later slice); Hilt-test infra (`hilt-android-testing` + `kspTest hilt-compiler`); `kotlinx-coroutines-test` for `viewModelScope` testing.
- **Deviations from plan:** 3 — see [05-implement-auth-foundation.md § Deviations from Plan](05-implement-auth-foundation.md). Most consequential: `BuildConfig.WEB_OAUTH_CLIENT_ID` instead of `R.string.default_web_client_id` to unblock compile when `google-services.json` lacks a Type 3 oauth_client.
- **Details:** [05-implement-auth-foundation.md](05-implement-auth-foundation.md).

### `functions-oauth`, `daily-poll`, `android-reader`, `pending-delete`, `cutover-migration` *(not implemented this round)*

Each remains in `defined` (slice) or unplanned state. Implementation deferred to per-slice `/wf implement` invocations. Every later slice ships on the BoM 34.13.0 baseline established here.

## Cross-Slice Integration Notes

- **`auth-foundation` → `functions-oauth`:** callable handlers (`mintOAuthState`, `migrateXToken`, `disconnectX`, `triggerPoll`) now have an authenticated Firebase context to validate via `request.auth`. The forward dependency captured in the plan — a Firestore allowlist doc (`config/allowed_emails`) plus updated `firestore.rules` gating `users/{uid}/**` on `request.auth.token.email` — is owed by `functions-oauth`. The app side contributes no UID/email check.
- **`auth-foundation` → `android-reader`:** `FirebaseAuth.currentUser?.uid` is available via `AuthGateway.currentUser` (exposed as `StateFlow<CurrentUser?>` with `uid` + `email`). The Firestore path rewrite to `users/{uid}/twitter/...` will inject the gateway, not `FirebaseAuth` directly.
- **`auth-foundation` → `cutover-migration`:** the `migrateXToken` callable is invoked from a Hilt-injected coroutine that depends on the authenticated user being present. `AuthUiState.Authenticated` determines runner eligibility.
- **BoM 34.13.0 cascade:** every later slice inherits the post-`.ktx` Firestore API in `feature/twitter` (single file migrated this round). When `android-reader` rewrites `FirestoreRepository.kt` paths, it inherits the migrated imports.

## Recommended Next Stage

- **Option A (default):** `/wf verify cloud-function-bookmark-sync auth-foundation` — run AC gate + re-run gradle checks. Run `/compact` first.
- **Option B:** `/wf plan cloud-function-bookmark-sync functions-oauth` — plan the next slice in parallel.
- **Option C:** `/wf review cloud-function-bookmark-sync auth-foundation` — skip verify (not recommended; live operator prereqs still need a checklist gate).
