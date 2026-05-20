---
schema: sdlc/v1
type: implement-index
slug: cloud-function-bookmark-sync
status: in-progress
stage-number: 5
created-at: "2026-05-19T22:51:34Z"
updated-at: "2026-05-20T18:30:00Z"
slices-implemented: 2
slices-total: 6
metric-total-files-changed: 30
metric-total-lines-added: 1212
metric-total-lines-removed: 96
tags: [firebase-auth, credential-manager, google-sign-in, account-linking, android, hilt, robolectric, roborazzi, cloud-functions, typescript, jose, secret-manager, oauth-pkce, jest, firestore-rules]
refs:
  index: 00-index.md
  plan-index: 04-plan.md
next-command: wf-verify
next-invocation: "/wf verify cloud-function-bookmark-sync functions-oauth"
---

# Implement Index

Master index for the six-slice implementation chain. Two slices implemented (`auth-foundation`, `functions-oauth`); four slices remain to plan and implement.

## Slice Implementation Summaries

### `auth-foundation` *(implemented)*

- **Status:** complete; all gradle checks green (`testDebugUnitTest`, `recordRoborazziDebug`, `verifyRoborazziDebug`, `lintDebug`, `assembleDebug`).
- **Surface:** Firebase Auth + Credential Manager (Google Sign-In) + E/P account-linking recovery on Android. Brutalist Login UI updated.
- **Boundary:** `AuthGateway` interface; `CurrentUser(uid, email)` data class. Identity enforcement delegated function-side (next slice).
- **Foundations introduced:** Firebase BoM 34.13.0 (cascades to every later slice); Hilt-test infra (`hilt-android-testing` + `kspTest hilt-compiler`); `kotlinx-coroutines-test` for `viewModelScope` testing.
- **Deviations from plan:** 3 — see [05-implement-auth-foundation.md § Deviations from Plan](05-implement-auth-foundation.md). Most consequential: `BuildConfig.WEB_OAUTH_CLIENT_ID` instead of `R.string.default_web_client_id` to unblock compile when `google-services.json` lacks a Type 3 oauth_client.
- **Details:** [05-implement-auth-foundation.md](05-implement-auth-foundation.md).

### `functions-oauth` *(implemented)*

- **Status:** code complete; build + lint + jest all green locally. Operator checklist (12 items) gates the live deploy + AC capture for verify.
- **Surface:** `functions/` TypeScript project (16 new files + `package-lock.json`); three handlers (`mintOAuthState`, `oauthCallback`, `warmUp`); three lib modules (`admin`, `state`, `secrets`); 9 jest cases (6 state, 3 callback).
- **Toolchain pins:** `firebase-functions@^7`, `firebase-admin@^13`, `@google-cloud/secret-manager@^5`, `jose@^5`; jest 30 + ts-jest 29 CJS preset; ESLint 9 flat config with `no-restricted-imports` blocking `undici`.
- **Boundary:** identity enforcement now lives in Firestore rules (`config/allowed_emails` allowlist gate, added to `firestore.rules`). Client-side code still carries no UID/email literal — auth-foundation's forward dependency is closed.
- **Deviations from plan:** 3 — see [05-implement-functions-oauth.md § Deviations from Plan](05-implement-functions-oauth.md). Most consequential: migrated to ESLint v9 flat config (legacy `.eslintrc.json` doesn't load under v9 default).
- **Details:** [05-implement-functions-oauth.md](05-implement-functions-oauth.md).

### `daily-poll`, `android-reader`, `pending-delete`, `cutover-migration` *(not implemented this round)*

Each remains in `defined` slice state with no plan yet. Implementation deferred to per-slice `/wf plan` then `/wf implement` invocations. `daily-poll` will consume `lib/secrets.ts` already implemented here; `android-reader` owes the `code_verifier` transport contract for `oauthCallback`.

## Cross-Slice Integration Notes

- **`auth-foundation` → `functions-oauth`** *(closed in this round):* `mintOAuthState` validates `request.auth` server-side; the Firestore allowlist gate landed in `firestore.rules` and `config/allowed_emails` is queued as an operator pre-seed before the rules deploy. App side still carries no UID/email literal.
- **`functions-oauth` → `android-reader` (open):** `oauthCallback` reads `code_verifier` from `req.query.code_verifier`. If `android-reader` chooses a different transport for the PKCE verifier (e.g., persisted session doc), the callback handler needs a minor edit in that slice's plan.
- **`functions-oauth` → `daily-poll`:** `lib/secrets.ts` exposes `getRefreshToken` + `setRefreshToken`. Colocated intentionally — `daily-poll` consumes them without re-implementing.
- **`functions-oauth` → `cutover-migration`:** `migrateXToken` + `disconnectX` callables and the `verify-function-iam.sh` script all land in that slice. This slice authored only the OAuth onboarding surface.
- **`auth-foundation` → `android-reader`:** `FirebaseAuth.currentUser?.uid` is available via `AuthGateway.currentUser` (exposed as `StateFlow<CurrentUser?>` with `uid` + `email`). The Firestore path rewrite to `users/{uid}/twitter/...` will inject the gateway, not `FirebaseAuth` directly.
- **`auth-foundation` → `cutover-migration`:** the `migrateXToken` callable is invoked from a Hilt-injected coroutine that depends on the authenticated user being present. `AuthUiState.Authenticated` determines runner eligibility.
- **BoM 34.13.0 cascade:** every later slice inherits the post-`.ktx` Firestore API in `feature/twitter` (single file migrated this round). When `android-reader` rewrites `FirestoreRepository.kt` paths, it inherits the migrated imports.

## Recommended Next Stage

- **Option A (default):** `/wf verify cloud-function-bookmark-sync functions-oauth` — execute the operator checklist (12 items), then capture live evidence (warmUp smoke, cold/warm AC, rules deploy without lockout). Run `/compact` first.
- **Option B:** `/wf plan cloud-function-bookmark-sync daily-poll` — plan the next slice in parallel with operator checklist execution.
- **Option C:** `/wf review cloud-function-bookmark-sync` — slug-wide review against `main...HEAD` covering both implemented slices (`review-scope: slug-wide` per `00-index.md`).
