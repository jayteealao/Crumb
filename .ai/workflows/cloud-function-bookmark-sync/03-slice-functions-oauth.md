---
schema: sdlc/v1
type: slice
slug: cloud-function-bookmark-sync
slice-slug: functions-oauth
status: defined
stage-number: 3
created-at: "2026-05-19T21:23:52Z"
updated-at: "2026-05-19T21:23:52Z"
complexity: l
depends-on: [auth-foundation]
tags: [cloud-functions, oauth, hmac, secret-manager, typescript, iam]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
  siblings:
    - 03-slice-auth-foundation.md
    - 03-slice-daily-poll.md
    - 03-slice-android-reader.md
    - 03-slice-pending-delete.md
    - 03-slice-cutover-migration.md
  plan: 04-plan-functions-oauth.md
  implement: 05-implement-functions-oauth.md
---

# Slice: functions-oauth

## Goal

Stand up the `functions/` TypeScript project with the OAuth handshake surface: the dedicated service account, the HMAC signing secret, the `mintOAuthState` authenticated callable, the public `oauthCallback` HTTP handler (token exchange + refresh-token persistence + 302 to the app deep link), and the `warmUp` ping target — all deployed to `europe-west2`. After this slice, an authenticated Android user can complete an end-to-end X authorization and the function persists a refresh token in Secret Manager.

## Why This Slice Exists

This is the highest-security part of the workflow. State-signing, token exchange, and Secret Manager binding all live here. Isolating them in their own slice means the function-side security boundary can be reviewed (per slug-wide review), exercised in tests, and signed off before any data poll touches Firestore. It also creates the function URL that must be registered in the X developer portal before any other handler matters.

## Scope

**In:**
- `functions/` TypeScript project: `package.json` (`firebase-functions` v6, `firebase-admin`, `undici`, dev: `typescript`, `firebase-functions-test`, `jest`, `eslint`), `tsconfig.json`, `.eslintrc.json`, `.gitignore`.
- `firebase.json` updated with `functions` block (region `europe-west2`, codebase `default`).
- `.firebaserc` confirmed; project alias set if missing.
- Handlers:
  - `mintOAuthState` (`onCall`, region `europe-west2`) — requires `request.auth`; returns HMAC-signed state `{uid, nonce, iat}` (HS256 base64url, signing key from Secret Manager `crumb-oauth-state-secret`). 10-minute TTL is encoded in `iat`.
  - `oauthCallback` (`onRequest`, region `europe-west2`) — verifies HMAC + freshness (`now - iat <= 600s`), exchanges code at `POST https://api.x.com/2/oauth2/token`, persists refresh token to Secret Manager (`crumb-x-refresh-token-{uid}`, version-add-then-disable-previous), writes `users/{uid}/twitter/sync_status = {linked: true, lastPolledAt: null, lastError: null}` via Admin SDK, returns `302` to `crumbs://graphitenerd.xyz?oauth=complete`.
  - `warmUp` (`onRequest`, region `europe-west2`, no auth) — returns `200 OK`; exists as a pre-warm target.
- TypeScript unit tests under `functions/test/`:
  - `oauth-state.test.ts` — sign/verify round-trip; reject wrong HMAC, missing state, expired state (>10 min), malformed UID, future-dated state.
  - `oauth-callback.test.ts` — full handler with `undici` mocked; reject bad state (400, no Secret Manager write), happy path (token persisted + sync_status write + 302).
- Cold-start hygiene: Admin SDK `getFirestore({ preferRest: true })`; heavy deps (`undici`, secret manager client) lazy-imported inside handlers.
- **Operational prereqs:**
  - Create dedicated SA `crumb-twitter-poller@<project>.iam.gserviceaccount.com`.
  - Create HMAC signing secret `crumb-oauth-state-secret` (32 random bytes); bind SA `roles/secretmanager.secretAccessor` at the secret level.
  - Register the deployed `oauthCallback` URL (`https://europe-west2-<project>.cloudfunctions.net/oauthCallback`) in the X developer portal as an allowed `redirect_uri`.
  - Configure X client credentials (`X_CLIENT_ID`, `X_CLIENT_SECRET`) as Secret Manager refs, bound to the SA at the secret level.
- GitHub Action / CI deploy step skeleton: `npm ci && npm run build && firebase deploy --only functions:mintOAuthState,functions:oauthCallback,functions:warmUp --project <alias>`.

**Out (handled by other slices):**
- `dailyPoll`, `triggerPoll`, `migrateXToken`, `disconnectX` — those land in `daily-poll` and `cutover-migration` respectively.
- Any Android wiring that *invokes* `mintOAuthState` or opens Custom Tabs — that's `android-reader`.
- The `verify-function-iam.sh` script — lives in `daily-poll` (where the IAM surface stabilizes).

## Acceptance Criteria

- **Given** the deployed `oauthCallback` endpoint, **when** any HTTP request hits it with a forged, expired (>10 min), or unsigned `state`, **then** the function returns `400`, does NOT write to Secret Manager, and does NOT touch Firestore. (Satisfies **AC3**.)
- **Given** an authenticated Firebase user, **when** their Android client successfully completes the X authorize flow and the `oauthCallback` runs, **then** Secret Manager contains `crumb-x-refresh-token-{uid}` and Firestore contains `users/{uid}/twitter/sync_status` with `linked: true`.
- TypeScript test suite (`oauth-state.test.ts` + `oauth-callback.test.ts`) passes locally and in CI.
- `mintOAuthState` rejects unauthenticated callers (`request.auth == null` → throw `HttpsError("unauthenticated")`).
- Dedicated SA exists; HMAC + X-client secrets exist in Secret Manager with per-secret `secretAccessor` binding (no project-level role). (Partially satisfies **AC10**; full IAM check ships with `daily-poll`.)
- X developer portal lists the function URL as a registered `redirect_uri` (operator confirms in verify).
- Cold-start measurement: callback p50 < 4s, p95 < 10s on warm; cold OAuth-callback < 12s.

## Dependencies on Other Slices

- `auth-foundation`: `mintOAuthState` requires an authenticated Firebase context; testing the callable end-to-end requires a signed-in user.

## Risks

- **State forgery via accepted unsigned state** — would let an attacker associate any X account with a victim UID. Mitigation: AC3 test cases include every negative path; reject path also has its own CI assertion.
- **Refresh-token leak through stack traces** — function must never log the bearer or refresh token. Mitigation: lint rule + dedicated test that fakes a 4xx response and asserts no token in the captured log.
- **X portal misconfiguration** — wrong `redirect_uri` → opaque `unauthorized_client` from X. Mitigation: plan-stage operator checklist; verify includes an end-to-end live authorization once.
- **Cold-start on the redirect** — 3–12s blank-tab. Mitigation: app pre-warms via `warmUp` ping just before opening Custom Tabs; documented in `android-reader`.
- **Secret Manager versioning churn** — every reconnect bumps the version. Mitigation: add-then-disable-previous; planned cleanup is a future maintenance task, not a slice.
