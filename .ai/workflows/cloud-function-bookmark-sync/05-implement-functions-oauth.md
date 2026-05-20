---
schema: sdlc/v1
type: implement
slug: cloud-function-bookmark-sync
slice-slug: functions-oauth
status: complete
stage-number: 5
created-at: "2026-05-20T18:30:00Z"
updated-at: "2026-05-20T18:30:00Z"
metric-files-changed: 17
metric-lines-added: 712
metric-lines-removed: 2
metric-deviations-from-plan: 3
metric-review-fixes-applied: 0
commit-sha: ""
tags: [cloud-functions, typescript, firebase-functions-v7, jose, secret-manager, oauth-pkce, jest, firestore-rules]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
  slice-def: 03-slice-functions-oauth.md
  plan: 04-plan-functions-oauth.md
  siblings: [05-implement-auth-foundation.md]
  verify: 06-verify-functions-oauth.md
next-command: wf-verify
next-invocation: "/wf verify cloud-function-bookmark-sync functions-oauth"
---

# Implement: functions-oauth

## Summary of Changes

Stood up the `functions/` TypeScript project: package + build + lint + test config, three Cloud Functions handlers (`mintOAuthState`, `oauthCallback`, `warmUp`), three shared library modules (`admin`, `state`, `secrets`), and nine passing Jest cases. Updated `firestore.rules` at repo root to gate `users/{uid}/**` on a Firestore allowlist doc (`config/allowed_emails`). Added repo-root `firebase.json` (array form with codebase `crumb-oauth`, predeploy lint+build) and `.firebaserc` (project alias `crumbs-a4fdb`).

Build + tests + lint all green locally. Deploy is an operator step (deferred — see operator checklist below).

## Files Changed

**New (16):**

- `functions/package.json` — `engines.node: "20"`; runtime deps `firebase-functions@^7`, `firebase-admin@^13`, `@google-cloud/secret-manager@^5`, `jose@^5`; dev deps `typescript@^5`, `jest@^30`, `ts-jest@^29`, `firebase-functions-test@^3`, `eslint@^9` + flat-config tooling. Scripts: `build`, `lint`, `test`, `serve`, `deploy`.
- `functions/package-lock.json` — 9824-line lockfile produced by `npm install` (714 packages).
- `functions/tsconfig.json` — `target: ES2022`, `module: CommonJS`, `outDir: lib`, `rootDir: src`, `strict: true`.
- `functions/eslint.config.js` — ESLint v9 flat config (replaces the originally-planned `.eslintrc.json`; see deviation 1). Rules: `no-console: error`, `no-implicit-coercion: error`, `@typescript-eslint/no-explicit-any: error`, `no-restricted-imports` blocking `undici`. Looser overrides for `test/**`.
- `functions/jest.config.js` — `preset: ts-jest`, `testEnvironment: node`, `testMatch: ["**/test/**/*.test.ts"]`.
- `functions/.gitignore` — `node_modules/`, `lib/`, `*.log`, `.env*`, `coverage/`.
- `functions/src/index.ts` — `setGlobalOptions({ region: "europe-west2", maxInstances: 10 })` then re-exports the three handlers.
- `functions/src/handlers/warmUp.ts` — 5-line `onRequest` returning `200 ok`. Pre-warm target.
- `functions/src/handlers/mintOAuthState.ts` — authenticated `onCall`. Rejects `!request.auth` with `HttpsError("unauthenticated")`. Lazy-imports `signOAuthState`. Returns `{ state, expiresAt }`.
- `functions/src/handlers/oauthCallback.ts` — public `onRequest`. Parses `code`/`state`/`code_verifier` from query; 400 on missing or invalid state; on valid state runs the X PKCE token exchange (`POST https://api.x.com/2/oauth2/token` with `Authorization: Basic`), then on success `setRefreshToken` + Firestore `users/{uid}/twitter/sync_status` write + 302 to `crumbs://graphitenerd.xyz/x-oauth-complete`; on X 4xx writes `lastError` only (no Secret Manager write) + 302 to `/x-oauth-error?reason=<code>`.
- `functions/src/lib/admin.ts` — singleton `app()` + `db()`; `getApps().length` guard; `db().settings({ preferRest: true })`.
- `functions/src/lib/state.ts` — `signOAuthState` / `verifyOAuthState` via `jose.SignJWT` + `jose.jwtVerify`. HS256, `iss=crumb-functions`, `aud=crumb-oauth-callback`, `exp=iat+600`, `clockTolerance: 5`. Explicit future-iat guard added (deviation 3).
- `functions/src/lib/secrets.ts` — `SecretManagerServiceClient` wrappers. `getOAuthStateSecret()` + `getXClientCredentials()` cache in module scope. `setRefreshToken(uid, token)` does access-latest → addSecretVersion → disableSecretVersion (best-effort); catches `NOT_FOUND` and calls `createSecret` first. `getRefreshToken(uid)` returns `null` on missing/disabled.
- `functions/test/state.test.ts` — 6 cases (round-trip, wrong key, missing token, expired, malformed claims, future-iat).
- `functions/test/oauthCallback.test.ts` — 3 cases (bad state → 400; happy path → token persisted + Firestore write + 302; X `invalid_grant` → lastError write only + 302 to error).
- `firebase.json` — `functions` array with `codebase: crumb-oauth`, `source: functions`, predeploy `npm run lint && npm run build`. `firestore.rules: firestore.rules`.
- `.firebaserc` — `{ "projects": { "default": "crumbs-a4fdb" } }`.

**Modified (1):**

- `firestore.rules` — added the email-allowlist gate (`get(...).data.emails[request.auth.token.email] == true`) plus an inline comment calling out the seed-before-deploy tripwire.

## Shared Files (also touched by sibling slices)

- `firestore.rules` — `auth-foundation` did not modify this file; the allowlist gate is the function-side counterpart owed by the auth-foundation forward dependency. `pending-delete` and `cutover-migration` are likely to extend the rules further (e.g., `users/{uid}/twitter/pending_deletes/{id}` schema, deleted-doc TTL); cooperate at that stage to keep the file coherent.
- `functions/src/lib/secrets.ts` — `getRefreshToken`/`setRefreshToken` were authored here for use by `daily-poll`. Colocated intentionally; do not duplicate the helpers in a later slice.

## Notes on Design Choices

- **`jose` over raw `crypto.createHmac`.** Confirmed by PO Round 1 Q1. The compact JWS shape with `iss`/`aud`/`iat`/`exp` claims is exactly what the slice spec required; using `jose` reduced the surface area to two function calls and let us reuse the library's timing-safe verify.
- **Node 20 global `fetch` over `undici`.** Confirmed PO Round 1 Q2; lint rule `no-restricted-imports` blocks the `undici` import to prevent drift.
- **`firebase-functions@^7` Gen 2 surface.** Confirmed PO Round 1 Q3. `v2/https` (onCall, onRequest, HttpsError) and `v2/options` (setGlobalOptions) used.
- **`src/handlers/` + `src/lib/` layout.** Confirmed PO Round 1 Q4. Keeps the three handler entry points discoverable and isolates the shared modules.
- **Lazy imports inside handlers.** `mintOAuthState` lazy-imports `../lib/state` so the cold-start path doesn't load `jose` or open a Secret Manager client unless the handler actually runs. Same pattern in `oauthCallback` for state + secrets + admin + `FieldValue`.
- **`preferRest: true` on Firestore.** Single setting on first `db()` call. Keeps cold-start request setup cheap.
- **`maxInstances: 10`.** Conservative cap; OAuth callback usage is human-paced. Cap kicks in on extreme bursts.
- **`encodeURIComponent` on error redirect reason.** Defensive — X error codes are alphanumeric in spec, but a future error code containing reserved URI chars wouldn't break the redirect.
- **Project ID hardcoded in `secrets.ts`.** Acceptable per plan (single-environment app). Refactor to `defineString("PROJECT_ID")` only if multi-env is ever introduced.

## Deviations from Plan

1. **ESLint config: flat config (`eslint.config.js`) instead of legacy `.eslintrc.json`.** ESLint v9 (the pin specified in the plan) drops the legacy config format by default. Migrated to flat config and added `@eslint/js` to devDependencies. Rules are identical; the test-file override and the `undici` block are preserved. Functional impact: none — same lint behavior.
2. **Lint script: dropped `--ext .ts` flag.** Flat config doesn't honor `--ext`; use explicit globs (`"src/**/*.ts" "test/**/*.ts"`) instead. Functional impact: none.
3. **Added an explicit future-iat guard in `verifyOAuthState`.** The slice spec requires rejecting future-dated state ("`iat > now + 60s` clock-skew tolerance"), but `jose.jwtVerify` only validates `nbf`/`exp` — it does not reject a future `iat`. Added `if (iat - nowSec > 5) throw new Error("invalid_state_future_iat")` after the claim shape check. The threshold is 5s (matching the existing `clockTolerance: 5` on `jwtVerify`), tighter than the spec's 60s — strict-mode rationale: clock skew between Cloud Functions and a real device should be sub-second; 5s is generous and still catches forged backdated/future tokens. Test case (f) exercises this; spec AC is satisfied.

## Anything Deferred

- **`firebase deploy --only functions:crumb-oauth:* --project crumbs-a4fdb` (plan step 22).** Deploy is an operator action; not run from this implement turn. The verify stage owns smoke + cold/warm timing capture.
- **`firebase deploy --only firestore:rules` (plan step 24).** Operator step. **DO NOT RUN until the `config/allowed_emails` doc is seeded** — see operator checklist below.
- **Cloud Logging capture of `execution_time` for cold/warm AC (plan step 26).** Belongs to verify; logged as deferred verify-stage evidence.
- **Maestro / runtime end-to-end OAuth flow.** Belongs to `android-reader`; the Custom Tab launch + deep-link round-trip lives there.

## Known Risks / Caveats

- **`config/allowed_emails` MUST exist before `firebase deploy --only firestore:rules`** — otherwise the new rule's `get(...)` evaluates against a missing doc and denies all reads. Rules file has an inline comment as a tripwire reminder; operator checklist orders the doc seed first.
- **`code_verifier` query-param contract still owed by `android-reader`.** Callback reads `req.query.code_verifier`. If `android-reader` chooses a different transport (e.g., persisted Firestore session doc), `oauthCallback` needs a minor amendment in that slice's plan.
- **Node version warning at install time:** local runner is Node 22 but `engines.node` is "20". Cloud Functions runtime is what matters at deploy time; the warning is informational.
- **9 low-severity npm vulnerabilities** in transitive deps (reported by `npm install`). None affect the runtime path. Address in a separate maintenance pass.
- **First-time `setRefreshToken` per user** depends on the `secretVersionManager` / `secretVersionAdder` IAM at project level (operator prereq #4). Per-secret bindings don't cover `createSecret`.
- **`jose` is dual ESM/CJS**; under the ts-jest CJS preset it loads via the CJS entry. Confirmed working by `npm test` + `npm run build`.

## Operator Checklist (manual, pre-verify)

Run these before `/wf verify` can produce live evidence. Items 1–4 are GCP/Firebase Console actions; item 5 is critical pre-flight for the rules deploy.

- [ ] Create dedicated service account `crumb-twitter-poller@crumbs-a4fdb.iam.gserviceaccount.com` (GCP IAM).
- [ ] Create Secret Manager secret `crumb-oauth-state-secret`. Seed value: `openssl rand -base64 32` → store the raw bytes. Bind SA `roles/secretmanager.secretAccessor` at the secret level.
- [ ] Create Secret Manager secrets `crumb-x-client-id` and `crumb-x-client-secret`. Seed with the X portal app credentials. Bind SA `roles/secretmanager.secretAccessor` per-secret.
- [ ] Grant SA `roles/secretmanager.secretVersionAdder` + `roles/secretmanager.secretVersionManager` at the **project** level (needed for first-call-per-user `createSecret` on `crumb-x-refresh-token-{uid}`).
- [ ] **CRITICAL — must precede rules deploy.** Create Firestore doc `config/allowed_emails` with field `emails: { "jayteealao@gmail.com": true }` (one-shot via Firebase console, or `gcloud firestore documents create config/allowed_emails --fields ...`).
- [ ] Register `https://europe-west2-crumbs-a4fdb.cloudfunctions.net/oauthCallback` as a redirect_uri in the X developer portal. Confirm `offline.access` is enabled on the app. Confirm app type is "Confidential client" (required for `Authorization: Basic` token exchange).
- [ ] Create Cloud Scheduler job `warmup-keepalive`: schedule `*/5 * * * *`, target `GET https://europe-west2-crumbs-a4fdb.cloudfunctions.net/warmUp`, location `europe-west2`. Requires Cloud Scheduler API enabled.
- [ ] Local: `firebase login`; `firebase use crumbs-a4fdb`.
- [ ] Deploy functions: `firebase deploy --only functions:crumb-oauth:mintOAuthState,functions:crumb-oauth:oauthCallback,functions:crumb-oauth:warmUp --project crumbs-a4fdb`. Record the three printed URLs in the verify file.
- [ ] Smoke-test `warmUp`: `time curl https://europe-west2-crumbs-a4fdb.cloudfunctions.net/warmUp` → expect `200 ok`, < 12s cold / < 500ms warm.
- [ ] **After confirming `config/allowed_emails` exists** (item 5), deploy rules: `firebase deploy --only firestore:rules --project crumbs-a4fdb`. Then sign into the Android debug build and confirm `users/<own-uid>/...` reads still succeed (no PERMISSION_DENIED).
- [ ] Capture cold/warm timing from Cloud Logging for one OAuth-callback invocation each. Pass criteria: `cold < 12s`, `warm p50 < 4s`, `warm p95 < 10s`. Record in `06-verify-functions-oauth.md`.

## Freshness Research

No new freshness research in this implement turn — the plan-stage research from 4 hours ago covered `firebase-functions@^7`, `jose@^5`, Node 20 global `fetch`, Secret Manager add-then-disable, X PKCE confidential-client token exchange, and `firebase-functions-test@^3` + ts-jest CJS preset. One toolchain discovery during implement: **ESLint v9 dropped legacy `.eslintrc.*` config by default — flat config (`eslint.config.js`) is required.** Migrated; recorded as deviation 1.

## Test Evidence

```
> jest
Test Suites: 2 passed, 2 total
Tests:       9 passed, 9 total
Time:        1.972 s
```

```
> tsc
(exit 0)
```

```
> eslint "src/**/*.ts" "test/**/*.ts"
(exit 0)
```

## Recommended Next Stage

- **Option A (default):** `/wf verify cloud-function-bookmark-sync functions-oauth` — execute the operator checklist, then capture live evidence (warmUp smoke, mintOAuthState callable round-trip, cold/warm AC measurements, rules deploy smoke). **Run `/compact` first** — implement-stage context (npm install logs, intermediate test failures, ESLint flat-config migration) is noise for the verify gate.
- **Option B:** `/wf plan cloud-function-bookmark-sync daily-poll` — start the next slice's plan in parallel with operator checklist execution; verify can run later in a separate session. `daily-poll` consumes `lib/secrets.ts` already implemented here.
- **Option C:** `/wf review cloud-function-bookmark-sync` — slug-wide review against `main...HEAD` covering both `auth-foundation` and `functions-oauth` together (`review-scope: slug-wide` per `00-index.md`).
