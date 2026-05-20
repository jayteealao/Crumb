---
schema: sdlc/v1
type: plan
slug: cloud-function-bookmark-sync
slice-slug: functions-oauth
status: complete
stage-number: 4
created-at: "2026-05-20T14:19:22Z"
updated-at: "2026-05-20T14:19:22Z"
metric-files-to-touch: 19
metric-step-count: 27
has-blockers: false
revision-count: 0
tags: [cloud-functions, typescript, firebase-functions-v7, jose, secret-manager, oauth-pkce, hmac, jest, firestore-rules, cloud-scheduler]
stack-source: confirmed
refs:
  index: 00-index.md
  plan-index: 04-plan.md
  slice-def: 03-slice-functions-oauth.md
  siblings:
    - 04-plan-auth-foundation.md
    - 04-plan-daily-poll.md
    - 04-plan-android-reader.md
    - 04-plan-pending-delete.md
    - 04-plan-cutover-migration.md
  implement: 05-implement-functions-oauth.md
next-command: wf-implement
next-invocation: "/wf implement cloud-function-bookmark-sync functions-oauth"
---

# Plan: functions-oauth

## Current State

There is no `functions/` directory in the repo. No `firebase.json`, no `.firebaserc`. Firestore is provisioned (project `crumbs-a4fdb`, region `europe-west2`) and a working `firestore.rules` file lives at the repo root (18 lines, 607 bytes) gating `users/{uid}/**` on `request.auth.uid == uid`. The only existing TypeScript/Node project in the repo is [scripts/firestore-migrate/](scripts/firestore-migrate/) — `firebase-admin@^13` + ESM `migrate.mjs`; no jest, no eslint config. The CI surface (`.github/workflows/`) has three Android workflows (`manual-release.yml`, `pr_check.yml`, `release.yml`) and zero Cloud Functions workflow.

The `auth-foundation` slice has shipped (commit `8f391f2`). Firebase Auth + Credential Manager is wired client-side; `AuthGateway` exposes `currentUser` to the rest of the app. No client-side reference to `mintOAuthState` or `oauthCallback` exists yet — those land in `android-reader`. The `crumbs://graphitenerd.xyz` deep-link is declared once in [app/src/main/AndroidManifest.xml:28-35](app/src/main/AndroidManifest.xml) (scheme + host, no path attribute — so any path under the host matches).

The X (Twitter) developer portal has app credentials but no `oauthCallback` redirect_uri registered yet — required as an operator step. The shape spec settled the design end-to-end (server-completed PKCE, HMAC-signed state with 10-minute TTL, dedicated SA, per-secret Secret Manager IAM, `europe-west2` region, foreground pre-warm); plan-stage discovery refined four tech choices and one ops decision (round 3 below).

## Reuse Opportunities

- [scripts/firestore-migrate/migrate.mjs](scripts/firestore-migrate/migrate.mjs) → **structural reuse**, not code reuse. Same `firebase-admin@^13` pin, same `applicationDefault()` credential pattern, same project ID (`crumbs-a4fdb`). Use it as the convention reference when authoring `functions/package.json` engines + admin init. Recommendation: **mirror conventions, do not import** — Cloud Functions runtime ADC is different from local CLI ADC.
- [firestore.rules](firestore.rules) → **modify in place** to add the email-allowlist gate from auth-foundation's forward dependency. No fork, no separate file.
- [.github/workflows/pr_check.yml](.github/workflows/pr_check.yml) → **template only** for the deferred CI workflow. PO answered round 2 Q4 = "defer CI entirely"; document the local `firebase deploy` invocation instead.
- `lazylogcat` (in `stack.available-cli`) → not used by this slice; reserved for `android-reader`'s live verification of the OAuth flow.
- No reuse candidate for: HMAC signing, OAuth state validation, X token exchange, Secret Manager add-then-disable, or Cloud Functions wrapper patterns. Confirmed by grep across the repo: zero matches for any of those concerns. This slice authors them from scratch.

## Likely Files / Areas to Touch

**Modify (2 existing files):**

- [firestore.rules](firestore.rules) — add the email allowlist gate. Final rule for `/users/{uid}/{document=**}`: `allow read, write: if request.auth != null && request.auth.uid == uid && get(/databases/$(database)/documents/config/allowed_emails).data.emails[request.auth.token.email] == true;`. The `config/allowed_emails` doc itself stays unreachable from clients via the default deny. One extra rule-evaluation document read per request (billed as 1 doc read; cached per-request).
- [.ai/workflows/cloud-function-bookmark-sync/00-index.md](.ai/workflows/cloud-function-bookmark-sync/00-index.md) — append `jest` to `stack.testing` (round 2 Q2 decision). Strictly additive; no PO re-confirm needed.

**New (17 files, all under `functions/` unless noted):**

*Project scaffolding (5):*
- `functions/package.json` — `engines.node: "20"`, deps `firebase-functions@^7`, `firebase-admin@^13`, `@google-cloud/secret-manager@^5`, `jose@^5`; dev-deps `typescript@^5`, `firebase-functions-test@^3`, `jest@^30`, `ts-jest@^29`, `@types/jest`, `@types/node@^20`, `eslint@^9`, `@typescript-eslint/parser`, `@typescript-eslint/eslint-plugin`. Scripts: `build`, `lint`, `test`, `serve` (emulator), `deploy`.
- `functions/tsconfig.json` — `target: ES2022`, `module: CommonJS`, `outDir: lib`, `rootDir: src`, `strict: true`, `esModuleInterop: true`, `skipLibCheck: true`.
- `functions/.eslintrc.json` — extends `@typescript-eslint/recommended`; key rules: `no-console: error` (force structured logging), `no-implicit-coercion: error`, `no-restricted-imports` blocking `undici` (use global `fetch`) and any module path matching `*token*` from non-handler files (defense against accidental token leak in shared utils).
- `functions/.gitignore` — `node_modules/`, `lib/`, `*.log`, `.env*`, `coverage/`.
- `functions/jest.config.js` — `preset: ts-jest`, `testEnvironment: node`, `testMatch: ["**/test/**/*.test.ts"]`, `clearMocks: true`. CJS-only (no ESM); offline `firebase-functions-test()`.

*Shared library (3):*
- `functions/src/lib/admin.ts` — singleton Admin SDK init. `getApps().length` guard. `getFirestore().settings({ preferRest: true })` once. Exports `db: Firestore` and `app: App`.
- `functions/src/lib/state.ts` — `signOAuthState(uid: string, nonce: string): Promise<string>` and `verifyOAuthState(token: string): Promise<{ uid: string; nonce: string; iat: number }>` using `jose.SignJWT` / `jose.jwtVerify`. HS256, `iat` set automatically, `exp` set to `iat + 600`. Signing key fetched lazily on first call via Secret Manager `accessSecretVersion` for `crumb-oauth-state-secret/versions/latest`; cached in module scope.
- `functions/src/lib/secrets.ts` — thin wrappers around `@google-cloud/secret-manager`. `getRefreshToken(uid): Promise<string | null>` (returns null if version not found / disabled); `setRefreshToken(uid, token)` doing add-then-disable-previous; `getOAuthStateSecret()`; `getXClientCredentials(): Promise<{ clientId: string; clientSecret: string }>`. Module-scope `SecretManagerServiceClient`. Errors are typed (`SecretNotFoundError`, `SecretAccessDeniedError`).

*Handlers (3):*
- `functions/src/handlers/mintOAuthState.ts` — `onCall` wrapped with `setGlobalOptions({ region: "europe-west2" })`. Rejects `!request.auth` with `HttpsError("unauthenticated", ...)`. Returns `{ state: string, expiresAt: number }`. Generates 32-byte nonce via `crypto.randomBytes`. Lazy-imports `./lib/state` inside the handler.
- `functions/src/handlers/oauthCallback.ts` — `onRequest`. Parses `code` + `state` from query. Verifies state via `verifyOAuthState`; on failure → `res.status(400).send("invalid state")` with no Secret Manager / Firestore write. On valid state: calls X token exchange (global `fetch`, `POST https://api.x.com/2/oauth2/token`, `application/x-www-form-urlencoded`, `Authorization: Basic base64(clientId:clientSecret)`, body `code/grant_type=authorization_code/redirect_uri/code_verifier`). On success: `setRefreshToken(uid, ...)` + `db.doc("users/{uid}/twitter/sync_status").set({linked: true, lastPolledAt: null, lastError: null}, {merge: true})` + `res.redirect(302, "crumbs://graphitenerd.xyz/x-oauth-complete")`. On X error: write `lastError` to sync_status (Firestore only — never log token bodies) and redirect to `crumbs://graphitenerd.xyz/x-oauth-error?reason=<error>`.
- `functions/src/handlers/warmUp.ts` — `onRequest`, no auth gate. Returns `200 "ok"`. ~5 LOC. Body deliberately trivial — sole purpose is to keep an instance warm.

*Entry point + re-exports (1):*
- `functions/src/index.ts` — `setGlobalOptions({ region: "europe-west2", maxInstances: 10 })`, then `export { mintOAuthState } from "./handlers/mintOAuthState"; export { oauthCallback } from "./handlers/oauthCallback"; export { warmUp } from "./handlers/warmUp";`. Imports admin in module scope by transitive load from `./handlers/oauthCallback` → `./lib/admin`.

*Tests (2):*
- `functions/test/state.test.ts` — `oauth-state.test.ts` per slice spec. Six cases: sign+verify round-trip, reject wrong HMAC, reject missing state, reject expired (`iat - now > 600`), reject malformed UID (non-string), reject future-dated state (`iat > now + 60s` clock-skew tolerance). Uses `jose` directly with a fixed test key; no Secret Manager mock needed because the test imports `signOAuthState/verifyOAuthState` and injects the key via a test-only `__setSigningKey(key)` export (or factored via dependency injection — see step 12).
- `functions/test/oauthCallback.test.ts` — full handler with `fetch` mocked via `jest.spyOn(globalThis, "fetch")`. Three cases: (a) bad state → 400, no Secret Manager write, no Firestore write; (b) happy path → token persisted (Secret Manager `addSecretVersion` called once with correct payload) + Firestore `sync_status` written + 302 to `crumbs://graphitenerd.xyz/x-oauth-complete`; (c) X returns `invalid_grant` → `lastError` written to Firestore, 302 to `crumbs://graphitenerd.xyz/x-oauth-error?reason=invalid_grant`, **no Secret Manager write**. `firebase-admin` and `@google-cloud/secret-manager` mocked via `jest.mock`.

*Repo-root config (2):*
- `firebase.json` — `functions` block (array form), `codebase: crumb-oauth`, `source: functions`, `predeploy: ["npm --prefix \"$RESOURCE_DIR\" run lint", "npm --prefix \"$RESOURCE_DIR\" run build"]`. Includes `firestore.rules: firestore.rules` so `firebase deploy --only firestore:rules` works.
- `.firebaserc` — `default: crumbs-a4fdb`.

**Operator prereqs (manual, bundled checklist in `05-implement-functions-oauth.md` per round 3 Q3 decision):**

- GCP Console → IAM → create SA `crumb-twitter-poller@crumbs-a4fdb.iam.gserviceaccount.com`.
- GCP Console → Secret Manager → create secret `crumb-oauth-state-secret` (32 random bytes, generate via `openssl rand -base64 32` then `base64-decode`); add SA `roles/secretmanager.secretAccessor` per-secret.
- GCP Console → Secret Manager → create `crumb-x-client-id` + `crumb-x-client-secret` with the X portal app credentials; bind SA `roles/secretmanager.secretAccessor` on each.
- GCP Console → grant `roles/secretmanager.secretVersionAdder` + `roles/secretmanager.secretVersionManager` to the SA on the **project** (needed for `crumb-x-refresh-token-{uid}` add-then-disable; per-secret binding doesn't cover create-version-on-new-secret).
- X developer portal → register `https://europe-west2-crumbs-a4fdb.cloudfunctions.net/oauthCallback` as an allowed redirect_uri on the existing Crumbs X app. Confirm `offline.access` scope is enabled (required for refresh token issuance).
- Cloud Scheduler → create job `warmup-keepalive` schedule `*/5 * * * *` target `GET https://europe-west2-crumbs-a4fdb.cloudfunctions.net/warmUp` location `europe-west2`. Requires Cloud Scheduler API enabled in the GCP project. (Round 3 Q1 decision — refines shape Q18.)
- Firebase Console → Authentication → ensure the user `jayteealao@gmail.com` exists (already true post-auth-foundation); confirm email shows in the token claims by signing in once and inspecting the Firestore Authentication debug panel.
- Local: install Firebase CLI ≥ `14.0` if missing; `firebase login`; `firebase use crumbs-a4fdb`.

## Proposed Change Strategy

Six phases; each commits cleanly if you want intermediate checkpoints, though the slice is intended as a single PR.

1. **Scaffold (Phase 0).** Create `functions/` skeleton: `package.json`, `tsconfig.json`, `jest.config.js`, `.eslintrc.json`, `.gitignore`, empty `src/index.ts`. Create `firebase.json` + `.firebaserc` at repo root. `npm install` inside `functions/` to lock the deps tree. `npm run build` returns 0 against the empty entry point. `npm test` returns "no tests found" cleanly.
2. **Shared lib (Phase 1).** Implement `lib/admin.ts`, `lib/state.ts`, `lib/secrets.ts`. Unit-test `lib/state.ts` via `test/state.test.ts` (6 cases) — green before moving on. `lib/admin.ts` and `lib/secrets.ts` are exercised transitively by handler tests.
3. **Handlers (Phase 2).** Implement `handlers/warmUp.ts`, `handlers/mintOAuthState.ts`, `handlers/oauthCallback.ts` and wire them in `src/index.ts` with `setGlobalOptions`. `npm run build` passes.
4. **Handler tests (Phase 3).** Implement `test/oauthCallback.test.ts` (3 cases). All tests green; coverage spot-checked manually (no coverage gate in this slice — added later if daily-poll wants it).
5. **Firestore rules (Phase 4).** Modify `firestore.rules` to add the email allowlist gate. Update `firebase.json` to reference `firestore.rules`. Deploy rules only (operator step, optional at plan time): `firebase deploy --only firestore:rules`. **Critical:** the `config/allowed_emails` doc must exist with the operator's email **before** the rules deploy, or auth-foundation immediately starts denying all Firestore reads — operator prereq #8.
6. **Stack update + operator prereqs (Phase 5).** Update `00-index.md` `stack.testing` to add `jest`. Author the operator checklist (in implement, not here). End-of-slice: `firebase deploy --only functions:mintOAuthState,functions:oauthCallback,functions:warmUp,firestore:rules` (manual operator invocation — not CI).

**Strict invariants across phases:**

- **No token logging.** No `console.log` is permitted in handlers (enforced by lint rule). Structured logging via `firebase-functions/logger` only, and never of bodies that could contain a token (use field allowlist).
- **No `undici` dependency.** Lint rule blocks the import. Global `fetch` only.
- **No Firebase Auth client in functions.** Admin SDK only. The token validation is implicit via `request.auth` on `mintOAuthState`; `oauthCallback` is public and identity is asserted by the HMAC state.
- **`oauthCallback` never writes a Secret Manager version on a 4xx from X.** Token exchange failure → Firestore `lastError` write only. Test (c) enforces.
- **All three handlers in `europe-west2`** — `setGlobalOptions` once at module scope; per-handler `region` overrides forbidden (review-stage check).

## Step-by-Step Plan

1. **Bootstrap `functions/` skeleton.** Create `functions/package.json`:
   ```json
   {
     "name": "crumb-functions",
     "version": "0.1.0",
     "private": true,
     "engines": { "node": "20" },
     "main": "lib/index.js",
     "scripts": {
       "build": "tsc",
       "lint": "eslint --ext .ts src test",
       "test": "jest",
       "serve": "firebase emulators:start --only functions",
       "deploy": "firebase deploy --only functions"
     },
     "dependencies": {
       "firebase-admin": "^13",
       "firebase-functions": "^7",
       "@google-cloud/secret-manager": "^5",
       "jose": "^5"
     },
     "devDependencies": {
       "typescript": "^5",
       "@types/node": "^20",
       "@types/jest": "^29",
       "jest": "^30",
       "ts-jest": "^29",
       "firebase-functions-test": "^3",
       "eslint": "^9",
       "@typescript-eslint/parser": "^8",
       "@typescript-eslint/eslint-plugin": "^8"
     }
   }
   ```
   Then `functions/tsconfig.json`:
   ```json
   {
     "compilerOptions": {
       "target": "ES2022",
       "module": "CommonJS",
       "moduleResolution": "node",
       "lib": ["ES2022"],
       "outDir": "lib",
       "rootDir": "src",
       "strict": true,
       "esModuleInterop": true,
       "skipLibCheck": true,
       "forceConsistentCasingInFileNames": true,
       "resolveJsonModule": true
     },
     "include": ["src/**/*"],
     "exclude": ["node_modules", "lib", "test"]
   }
   ```

2. **Create `functions/.eslintrc.json`:**
   ```json
   {
     "root": true,
     "parser": "@typescript-eslint/parser",
     "parserOptions": { "project": ["tsconfig.json"], "sourceType": "module" },
     "plugins": ["@typescript-eslint"],
     "extends": ["eslint:recommended", "plugin:@typescript-eslint/recommended"],
     "rules": {
       "no-console": "error",
       "no-implicit-coercion": "error",
       "@typescript-eslint/no-explicit-any": "error",
       "no-restricted-imports": ["error", { "paths": [{ "name": "undici", "message": "Use Node 20 global fetch." }] }]
     }
   }
   ```

3. **Create `functions/jest.config.js`:**
   ```js
   /** @type {import('jest').Config} */
   module.exports = {
     preset: "ts-jest",
     testEnvironment: "node",
     testMatch: ["**/test/**/*.test.ts"],
     clearMocks: true,
     collectCoverageFrom: ["src/**/*.ts"]
   };
   ```

4. **Create `functions/.gitignore`:** `node_modules/`, `lib/`, `*.log`, `.env*`, `coverage/`.

5. **Create `firebase.json` at repo root** (array form for `functions` to allow a future second codebase without breaking deploys):
   ```json
   {
     "firestore": { "rules": "firestore.rules" },
     "functions": [
       {
         "codebase": "crumb-oauth",
         "source": "functions",
         "ignore": ["node_modules", ".git", "*.log"],
         "predeploy": [
           "npm --prefix \"$RESOURCE_DIR\" run lint",
           "npm --prefix \"$RESOURCE_DIR\" run build"
         ]
       }
     ]
   }
   ```

6. **Create `.firebaserc`:** `{ "projects": { "default": "crumbs-a4fdb" } }`.

7. **Implement `functions/src/lib/admin.ts`:**
   ```ts
   import { getApps, initializeApp, type App } from "firebase-admin/app";
   import { getFirestore, type Firestore } from "firebase-admin/firestore";

   let _app: App | null = null;
   let _db: Firestore | null = null;

   export function app(): App {
     if (_app) return _app;
     _app = getApps().length ? getApps()[0]! : initializeApp();
     return _app;
   }
   export function db(): Firestore {
     if (_db) return _db;
     _db = getFirestore(app());
     _db.settings({ preferRest: true });
     return _db;
   }
   ```

8. **Implement `functions/src/lib/secrets.ts`:** module-scope `SecretManagerServiceClient`. Functions:
   - `getOAuthStateSecret(): Promise<Uint8Array>` — accesses `projects/crumbs-a4fdb/secrets/crumb-oauth-state-secret/versions/latest`. Caches in a module-scope `Uint8Array | null`.
   - `getXClientCredentials(): Promise<{ clientId: string; clientSecret: string }>` — accesses `crumb-x-client-id` and `crumb-x-client-secret` in parallel via `Promise.all`. Cached.
   - `setRefreshToken(uid: string, token: string): Promise<void>` — secret name `projects/crumbs-a4fdb/secrets/crumb-x-refresh-token-${uid}`. Sequence: `accessSecretVersion(.../latest)` to read previous name; `addSecretVersion(...)` with new token; `disableSecretVersion(<previousName>)`. If the secret doesn't exist yet, catches `NOT_FOUND` and calls `createSecret(...)` first, then `addSecretVersion`. Disable step is best-effort (catches and logs warning structured-not-bodied).
   - `getRefreshToken(uid: string): Promise<string | null>` — returns `null` on `NOT_FOUND` or all-disabled-versions; else returns the decoded utf8 string. (Used by `daily-poll` later, not this slice; we author it now to colocate Secret Manager logic.)

9. **Implement `functions/src/lib/state.ts`:**
   ```ts
   import { SignJWT, jwtVerify, type JWTPayload } from "jose";
   import { getOAuthStateSecret } from "./secrets";

   const ISSUER = "crumb-functions";
   const AUDIENCE = "crumb-oauth-callback";
   const MAX_AGE_SECONDS = 600;

   export interface OAuthStateClaims { uid: string; nonce: string; iat: number; }

   export async function signOAuthState(uid: string, nonce: string): Promise<string> {
     const key = await getOAuthStateSecret();
     return await new SignJWT({ uid, nonce })
       .setProtectedHeader({ alg: "HS256" })
       .setIssuer(ISSUER)
       .setAudience(AUDIENCE)
       .setIssuedAt()
       .setExpirationTime(`${MAX_AGE_SECONDS}s`)
       .sign(key);
   }

   export async function verifyOAuthState(token: string): Promise<OAuthStateClaims> {
     const key = await getOAuthStateSecret();
     const { payload } = await jwtVerify(token, key, {
       issuer: ISSUER,
       audience: AUDIENCE,
       clockTolerance: 5,
     });
     const { uid, nonce, iat } = payload as JWTPayload & { uid?: unknown; nonce?: unknown };
     if (typeof uid !== "string" || typeof nonce !== "string" || typeof iat !== "number") {
       throw new Error("invalid_state_claims");
     }
     return { uid, nonce, iat };
   }
   ```
   For testability: tests inject a fixed signing key by mocking `./secrets` via `jest.mock("./secrets")` — see step 12.

10. **Implement `functions/src/handlers/warmUp.ts`:**
    ```ts
    import { onRequest } from "firebase-functions/v2/https";
    export const warmUp = onRequest((_req, res) => { res.status(200).send("ok"); });
    ```

11. **Implement `functions/src/handlers/mintOAuthState.ts`:**
    ```ts
    import { onCall, HttpsError } from "firebase-functions/v2/https";
    import { randomBytes } from "node:crypto";

    export const mintOAuthState = onCall(async (request) => {
      if (!request.auth) throw new HttpsError("unauthenticated", "Sign-in required");
      const { signOAuthState } = await import("../lib/state");
      const nonce = randomBytes(32).toString("base64url");
      const state = await signOAuthState(request.auth.uid, nonce);
      return { state, expiresAt: Math.floor(Date.now() / 1000) + 600 };
    });
    ```

12. **Implement `functions/src/handlers/oauthCallback.ts`:**
    - Parse `code` and `state` from `req.query`. Reject with `res.status(400).send("missing params")` if either is missing.
    - Lazy-import `verifyOAuthState` from `../lib/state`. On `Error` → `res.status(400).send("invalid state")` and return.
    - Lazy-import `getXClientCredentials` + `setRefreshToken` from `../lib/secrets`. Lazy-import `db` from `../lib/admin`.
    - Construct Basic auth header from `{ clientId, clientSecret }`. Construct body from `code`, `grant_type=authorization_code`, `redirect_uri=https://europe-west2-crumbs-a4fdb.cloudfunctions.net/oauthCallback`, `code_verifier` (from `req.query.code_verifier` — note: see Risks for how `android-reader` will provide this).
    - `await fetch("https://api.x.com/2/oauth2/token", { method: "POST", headers: { ... }, body: new URLSearchParams(...) })`.
    - On 4xx/5xx: parse `error` from response JSON; write `db.doc(\`users/\${uid}/twitter/sync_status\`).set({ linked: false, lastError: errorCode, updatedAt: FieldValue.serverTimestamp() }, { merge: true })`; `res.redirect(302, \`crumbs://graphitenerd.xyz/x-oauth-error?reason=\${errorCode}\`)`. No Secret Manager write.
    - On 200: extract `refresh_token`; `setRefreshToken(uid, refresh_token)`; `db.doc(\`users/\${uid}/twitter/sync_status\`).set({ linked: true, lastPolledAt: null, lastError: null, updatedAt: FieldValue.serverTimestamp() }, { merge: true })`; `res.redirect(302, "crumbs://graphitenerd.xyz/x-oauth-complete")`.
    - All errors except the planned 4xx-from-X are caught at the top level: `try { ... } catch (e) { logger.error("oauth_callback_unexpected", { code: (e as Error).message }); res.status(500).send("internal"); }`. Never include the response body of X in the log.

13. **Implement `functions/src/index.ts`:**
    ```ts
    import { setGlobalOptions } from "firebase-functions/v2/options";
    setGlobalOptions({ region: "europe-west2", maxInstances: 10 });
    export { mintOAuthState } from "./handlers/mintOAuthState";
    export { oauthCallback } from "./handlers/oauthCallback";
    export { warmUp } from "./handlers/warmUp";
    ```

14. **Run `npm run build` inside `functions/`.** Must compile cleanly. Fix any tsc surface errors before tests.

15. **Write `functions/test/state.test.ts`** (six cases as listed in the slice file's Scope plus a futuredate case):
    - Setup: `jest.mock("../src/lib/secrets", () => ({ getOAuthStateSecret: jest.fn(async () => new TextEncoder().encode("test-key-32-bytes-padding-padding")) }));`
    - Cases: (a) `signOAuthState("uid1", "nonce1")` round-trips via `verifyOAuthState`; (b) wrong key → `verifyOAuthState` rejects; (c) missing token → rejects; (d) clock-advanced by 11 minutes → rejects (`exp` claim); (e) malformed claims (uid as number) → rejects with `invalid_state_claims`; (f) future-dated state (manually crafted with `setIssuedAt(now + 60)`, beyond 5s clockTolerance) → rejects. Use `jest.useFakeTimers({ doNotFake: ['nextTick'] })` for the time-travel cases.

16. **Write `functions/test/oauthCallback.test.ts`:**
    - Setup: `jest.mock("firebase-admin/firestore", ...)` returns a stub `getFirestore` whose `doc().set()` is `jest.fn()`.
    - Setup: `jest.mock("@google-cloud/secret-manager", ...)` returns a fake `SecretManagerServiceClient` whose `accessSecretVersion`, `addSecretVersion`, `disableSecretVersion`, `createSecret` are `jest.fn()` resolving with shape-correct fixtures.
    - Setup: `jest.spyOn(globalThis, "fetch").mockResolvedValue(...)` with a `Response`-shaped fake.
    - Bootstrap the handler via `firebase-functions-test()`'s offline mode; wrap `oauthCallback` using `test.wrap(...)`. Express-style `(req, res)` calls are tested via a hand-rolled `res` mock with `.status()`, `.send()`, `.redirect()` as jest.fn().
    - Three cases as in slice file Scope:
      - (a) bad state → assert `res.status(400)` called once, `addSecretVersion` not called, `doc().set` not called.
      - (b) valid state + 200 from X → assert `addSecretVersion` called once with the correct uid-keyed secret name, `disableSecretVersion` called (best-effort), `doc("users/uid1/twitter/sync_status").set` called with `linked: true`, `res.redirect(302, "crumbs://graphitenerd.xyz/x-oauth-complete")`.
      - (c) valid state + 400 from X with `invalid_grant` body → assert `addSecretVersion` **not called**, `doc().set` called with `linked: false, lastError: "invalid_grant"`, `res.redirect(302, "crumbs://graphitenerd.xyz/x-oauth-error?reason=invalid_grant")`.

17. **Run `npm test` inside `functions/`.** All 9 test cases green. Triage failures back into steps 7–16.

18. **Run `npm run lint` inside `functions/`.** No errors. `no-console` will flag any accidental `console.log` left over from development — replace with `logger.info(...)`.

19. **Modify `firestore.rules`** at repo root. New content:
    ```
    rules_version = '2';

    service cloud.firestore {
      match /databases/{database}/documents {
        match /users/{uid}/{document=**} {
          allow read, write: if request.auth != null
            && request.auth.uid == uid
            && get(/databases/$(database)/documents/config/allowed_emails)
                 .data.emails[request.auth.token.email] == true;
        }
        match /{document=**} {
          allow read, write: if false;
        }
      }
    }
    ```
    Verify locally via `firebase emulators:start --only firestore` + the rules unit test framework (`@firebase/rules-unit-testing`) if time permits — but **not in scope as a coded test** for this slice (would need a `tests/firestore-rules.test.ts` we deliberately don't add). Manual smoke test via Firestore emulator is the verify-stage path.

20. **Add `jest` to `00-index.md` `stack.testing`.** The line becomes `testing: [junit, roborazzi, maestro, jest]`. No other changes to `00-index.md` from this plan write itself (the per-slice plan write updates `workflow-files`, `updated-at`, and the master index does the rest).

21. **Operator prereqs (implement-stage manual).** Author the unchecked checklist inside `05-implement-functions-oauth.md`. Items (mirrors the bulleted list in "Likely Files / Areas to Touch" above):
    - `[ ]` Create SA `crumb-twitter-poller@crumbs-a4fdb.iam.gserviceaccount.com`.
    - `[ ]` Create + seed secret `crumb-oauth-state-secret` (32 random bytes); bind SA `secretAccessor`.
    - `[ ]` Create secrets `crumb-x-client-id` + `crumb-x-client-secret`; bind SA `secretAccessor`.
    - `[ ]` Grant SA `secretVersionAdder` + `secretVersionManager` at project level.
    - `[ ]` Create Firestore doc `config/allowed_emails` with `emails: { "jayteealao@gmail.com": true }` (one-shot via `gcloud firestore documents create ...` or the Firebase console). **Must precede the rules deploy** (step 24) — once rules go live, missing allowlist locks the user out.
    - `[ ]` Register `https://europe-west2-crumbs-a4fdb.cloudfunctions.net/oauthCallback` as a redirect_uri in the X portal; confirm `offline.access` scope is enabled.
    - `[ ]` Create Cloud Scheduler job `warmup-keepalive` (schedule `*/5 * * * *`, target `GET https://europe-west2-crumbs-a4fdb.cloudfunctions.net/warmUp`, location `europe-west2`).
    - `[ ]` Local: `firebase login` + `firebase use crumbs-a4fdb`.

22. **Deploy functions (operator step at implement time):** `firebase deploy --only functions:crumb-oauth:mintOAuthState,functions:crumb-oauth:oauthCallback,functions:crumb-oauth:warmUp --project crumbs-a4fdb`. Confirms predeploy lint + build pass against the real toolchain. Function URLs print to stdout — record them in the implement file.

23. **Smoke-test warmUp.** `curl https://europe-west2-crumbs-a4fdb.cloudfunctions.net/warmUp` returns `200 ok` within 12s cold or <500ms warm. Record cold + warm timings via `time curl ...` (or PowerShell `Measure-Command`).

24. **Deploy firestore.rules:** `firebase deploy --only firestore:rules --project crumbs-a4fdb`. **Pre-flight: confirm `config/allowed_emails` doc exists** (operator checklist item 5). Sanity check: open the Crumbs app (auth-foundation has Firebase Auth) and confirm a previously-working Firestore read still succeeds. If the app has no Firestore reads yet (likely true at this slice — `android-reader` hasn't shipped), test by signing into a debug build and observing no auth errors.

25. **One-shot end-to-end live OAuth test (operator).** The `mintOAuthState` callable can be exercised via the Firebase emulator UI or via `firebase functions:shell`. The full `oauthCallback` round-trip is **deferred to `android-reader`** because it requires Custom Tab navigation — see Risks. For this slice, manually constructing the authorize URL with a freshly-minted state and pasting it into a browser is sufficient to exercise the callback in isolation (operator confirms 302 + Secret Manager + Firestore writes via Cloud Logging).

26. **Verify cold/warm timing AC (manual one-shot per round 3 Q2):** From Cloud Logging, capture `execution_time` for one cold and one warm `oauthCallback` invocation post-deploy. Record in `06-verify-functions-oauth.md`: callback p50, p95, cold. AC pass criteria: `cold < 12s, warm p50 < 4s, warm p95 < 10s`. Acknowledge the small sample size in the verify file.

27. **Verify-stage handoff.** After steps 14–18 (build + tests + lint) and 22–26 (deploy + smoke + AC measurement) pass, the slice's acceptance criteria are met. Round-trip from authenticated callable to Secret Manager + Firestore is exercised; cold-start AC measured one-shot; rules deploy succeeded without locking out the operator. Anything that depends on the live Android client (Custom Tab → X portal → 302 deep-link → app handles `crumbs://graphitenerd.xyz/x-oauth-complete`) is acknowledged-deferred to `android-reader`'s verify cycle.

## Test / Verification Plan

### Automated checks

- **Lint:** `cd functions && npm run lint` — fails on any `console.log`, any `undici` import, any `any` type leak.
- **Build:** `cd functions && npm run build` — TypeScript compile under strict mode against ES2022 / CommonJS targets.
- **Unit tests:** `cd functions && npm test` — 9 cases total (6 state, 3 callback). Coverage not gated in this slice.
- **No Android-side gradle changes** — `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:verifyRoborazziDebug` all remain UP-TO-DATE (this slice authors no Android code).

### Interactive verification (human-in-the-loop)

**Stack from [00-index.md](.ai/workflows/cloud-function-bookmark-sync/00-index.md)** (`stack.user-confirmed: true`): `platforms: [android, service]`, `testing: [junit, roborazzi, maestro, jest]` (post-step 20), `available-cli: [firebase, gcloud, android, lazylogcat, maestro]`. Source of truth.

This slice's interactive verification leans on `gcloud` + `firebase` CLIs and Cloud Logging — *not* on Maestro, since no Android UI is in scope:

- **Cloud Scheduler keepalive (AC: warmUp pre-warm reachable).**
  - Steps: `gcloud scheduler jobs run warmup-keepalive --location europe-west2 --project crumbs-a4fdb`; then `firebase functions:log --only warmUp --lines 5`.
  - Pass criteria: log shows a 200 response within 10s of trigger; no error rows.

- **mintOAuthState callable (AC: unauthenticated → HttpsError; authenticated → signed state).**
  - Steps: `firebase functions:shell` → `mintOAuthState({})` (unauthenticated) → must throw `HttpsError("unauthenticated")`. Re-run with `auth: { uid: "test-uid" }` → must return `{ state, expiresAt }` with `state.split(".").length === 3` (JWS compact form).
  - Pass criteria: both branches return the expected shape; no token/key bytes in the log.

- **oauthCallback (AC: bad state → 400; valid state → Secret Manager write + Firestore write + 302).**
  - Steps: from a one-shot Node REPL or `curl`, hit `https://.../oauthCallback?state=forged&code=anything` → must return 400. Then mint a real state via `mintOAuthState`, paste it into an authorization URL on X.com (operator-driven), complete the authorization, observe the 302 land on `crumbs://graphitenerd.xyz/x-oauth-complete`.
  - Companion: `gcloud secrets versions list crumb-x-refresh-token-<uid>` → expect exactly one ENABLED version post-flow.
  - Companion: `firebase firestore:get users/<uid>/twitter/sync_status` → expect `linked: true, lastPolledAt: null, lastError: null`.
  - Pass criteria: all three observable side-effects present; no token literal anywhere in Cloud Logging.

- **Cold/warm latency (AC: cold < 12s, warm p50 < 4s, warm p95 < 10s).**
  - Steps: post-deploy, wait > 15 min idle, then trigger one OAuth round-trip while capturing `time` on the curl that initiates the chain (or measure `execution_time_ms` in Cloud Logging). Then trigger 5 successive warm invocations within 30s of each other and compute p50/p95.
  - Pass criteria: cold under 12s, warm p50 under 4s, warm p95 under 10s. Acknowledge n=5 small-sample; not a continuous SLO.

- **Firestore rules (AC: per-user scoping holds; allowlist enforced).**
  - Steps: with Firebase Auth signed in as `jayteealao@gmail.com`, attempt a read of `users/<own-uid>/twitter/sync_status` from the client (or via emulator) → expect success. Remove the email from `config/allowed_emails.emails` map, retry → expect PERMISSION_DENIED. Restore the map immediately.
  - Pass criteria: allow → deny → allow cycle confirms the gate is wired correctly.

If a criterion needs tooling outside `stack:`: none. All tooling above is in `stack.available-cli` post step-20 stack update.

### Operator-confirmed prereqs

The slice's eight operator prereqs (step 21 checklist) are not automatable. Implement-stage records explicit checkbox completion before declaring the slice ready for verify. See [03-slice-functions-oauth.md](.ai/workflows/cloud-function-bookmark-sync/03-slice-functions-oauth.md) "Operational prereqs" section.

## Risks / Watchouts

- **`config/allowed_emails` doc must exist BEFORE the rules deploy.** Otherwise the rule `get(...).data.emails[...]` evaluates against a missing doc and **denies all reads** — including the user's own. Mitigation: operator checklist orders Firestore doc creation BEFORE `firebase deploy --only firestore:rules` (step 24); plan calls this out explicitly. Cross-cutting note added to master index.
- **Cloud Scheduler keepalive is a refinement of shape Q18.** Shape originally decided "foreground pre-warm only." Plan adds a 5-min Cloud Scheduler ping for cheaper cold-start mitigation (round 3 Q1 decision). Record as plan-stage refinement in `po-answers.md`; not a contradiction because the foreground ping is still in scope.
- **`code_verifier` in `oauthCallback` query.** The PKCE `code_verifier` must reach the function. The shape spec implies the device generates it and passes it through. **Open question for `android-reader`:** is `code_verifier` carried as a separate query param on the callback URL, or does the function statelessly need the device to send it via a callable? The current plan assumes it's a query param — this couples the deep-link contract. Flag as a forward dependency for `android-reader` planning. *Documented as cross-slice dependency below.*
- **Refresh-token leak via accidental log line.** `console.log` is lint-banned; `firebase-functions/logger.info` is allowed but must never receive the X response body wholesale. Mitigation: tests assert no token literal in captured log calls (use `jest.spyOn(logger, "info")` with arg inspection — added to oauthCallback test case (c)).
- **Secret Manager `addSecretVersion` requires the secret to exist.** First-call-per-user requires `createSecret` first. The `setRefreshToken` helper handles this with a try/catch on `NOT_FOUND`. Test (b) hits the create-then-add branch; mock must shape both calls correctly.
- **`firebase-functions/params` vs hardcoded project id.** The project ID `crumbs-a4fdb` is hardcoded in `secrets.ts` secret paths. This is acceptable for a single-environment app (no staging vs prod). If multi-env is ever introduced, refactor to `defineString("PROJECT_ID")` from `firebase-functions/params` — out of scope here.
- **Cold-start AC sample size.** n=5 warm + n=1 cold is small; latency could fluctuate ±50% based on Cloud Run scheduler load. Mitigation: round 3 Q2 picked manual one-shot; the verify file acknowledges the limitation. If AC fails on a single bad sample, retry once before declaring `result: fail`.
- **`firestore.rules` deploy is destructive to currently-running clients.** If auth-foundation's `FirebaseAuthGateway.currentUser` is observed in production while the new rule lands without the allowlist seed, every Firestore read instantly 403s. Mitigation: operator checklist orders doc seed BEFORE rules deploy.
- **`jose` library is new to the codebase.** Pin `^5`. Verify import path works in CommonJS target (`jose` ships dual ESM/CJS — should be transparent under our ts-jest CJS preset). Sanity check at step 14 (`npm run build`) confirms.
- **Confidential vs public OAuth client mismatch.** X allows both; the plan picks confidential (server-side `Authorization: Basic`). If the existing X portal app is configured as public-only, the token exchange returns `unauthorized_client`. Operator checklist confirms `offline.access` scope; confidential mode must also be enabled (the X portal UI shows it as "App type: Confidential client" — checklist must note this explicitly).
- **`europe-west2` region for Secret Manager.** Secrets are global (not regional). No region pin needed. Functions are pinned; Firestore is `europe-west2`. Watch for an accidental `secret-manager` Endpoint override (default ok).
- **`maxInstances: 10`** is a guardrail; OAuth callback usage is human-paced. If a sign-in storm ever happens (unlikely on single-user app), the cap kicks in and excess requests get 429. Acceptable.
- **Lint rule `no-restricted-imports` on `undici`** silently disallows future drift. Document the rationale in `.eslintrc.json` as a comment so a future contributor doesn't strip it.

## Dependencies on Other Slices

- **From `auth-foundation` (already shipped):** `FirebaseAuth` is initialized client-side; `request.auth` on `mintOAuthState` will be populated. The Firestore-side allowlist gate (this slice's `firestore.rules` change + `config/allowed_emails` seed) closes the forward dependency declared in [04-plan-auth-foundation.md § Dependencies on Other Slices](04-plan-auth-foundation.md).
- **Forward dependency on `android-reader`:** owns the `mintOAuthState`-call site, the Custom Tab launch, the `code_verifier` PKCE generation, the handling of the deep-link 302 (`crumbs://graphitenerd.xyz/x-oauth-complete` vs `/x-oauth-error?reason=...`), and the live Maestro flow that exercises the full OAuth chain end-to-end. The `code_verifier` must be conveyed from the device to `oauthCallback` — the current plan assumes a query param on the redirect (`&code_verifier=<pkce>`), but the final mechanism is **`android-reader`'s decision**. This plan ships the callback with `req.query.code_verifier` as the read path; if `android-reader` chooses a different mechanism (e.g., persisted in `users/{uid}/twitter/oauth_session` doc), `oauthCallback` will need a minor amendment in that slice's plan.
- **Forward dependency on `daily-poll`:** consumes `setRefreshToken`/`getRefreshToken` from `lib/secrets.ts` — colocated here intentionally so the helpers exist once. `daily-poll` also adds the `dailyPoll` + `triggerPoll` handlers and the `verify-function-iam.sh` script.
- **Forward dependency on `cutover-migration`:** `migrateXToken` + `disconnectX` callables are NOT in scope; their absence in this slice is intentional. The `verify-function-iam.sh` shell script and the device-side X-code grep gate also land there.
- **Cross-slice cohesion note (master plan update):** the `stack.testing` array grows by one (`jest`). All later slices touching `functions/` inherit it. Master `04-plan.md` Cross-Cutting Concerns section updated.

## Assumptions

- The Firebase project ID is `crumbs-a4fdb` and the region is `europe-west2`. Confirmed by [scripts/firestore-migrate/migrate.mjs:22-23](scripts/firestore-migrate/migrate.mjs) and shape spec.
- The user's email is `jayteealao@gmail.com` (confirmed via po-answers.md and the userEmail context). Allowlist map seed uses this value verbatim.
- The X developer portal app exists and is configured as a confidential client. (Operator prereq #6 confirms.)
- Firebase CLI ≥ `14.0` is installed locally. (Operator prereq #8 confirms.)
- `firebase-functions@^7` is the current stable major in May 2026 (per freshness research). If `^8` ships during this slice's lifetime, the v2 API surface is expected to remain stable.
- Node 20 is the Cloud Functions runtime; `engines.node: "20"` matches.
- The `crumbs://graphitenerd.xyz/x-oauth-complete` deep-link path doesn't collide with any existing route in `LoginRoute`. Confirmed by grep: no path-based switch in `LoginRoute.kt`; the existing Twitter/Reddit OAuth callbacks parse by query (`?code=...`) rather than path. `android-reader` will add a path-based switch when it consumes this redirect.
- `jose@^5` CJS interop works under ts-jest CJS preset. (Cited in freshness research; sanity-checked at step 14.)

## Blockers

- **None blocking the plan.** The operator prereqs are not blockers — they are gated checklist items recorded at implement time, mirroring the `auth-foundation` pattern.

## Freshness Research

- **firebase-functions v7 is the current major (April 2026).** v2 Gen 2 API (`firebase-functions/v2/https`, `setGlobalOptions`, `HttpsError`, `onCall`, `onRequest`) is identical between v6 and v7 for our three handlers. v7 removes `functions.config()` (not used here). Round 1 Q3 → pin `^7`.
  - Sources: [Firebase callable docs](https://firebase.google.com/docs/functions/callable), [HTTP triggers](https://firebase.google.com/docs/functions/http-events).

- **`jose@^5` for HS256 state tokens.** Spec-correct compact JWS; `SignJWT`/`jwtVerify` handle `iat`/`exp`/`iss`/`aud` validation, base64url, and timing-safe verify built-in. Lazy-import inside handlers keeps `mintOAuthState` cold-start lean.
  - Sources: [jose npm](https://www.npmjs.com/package/jose).
  - Takeaway: round 1 Q1 picked `jose`. Plan uses `SignJWT(...).setIssuer/setAudience/setIssuedAt/setExpirationTime` rather than raw `crypto.createHmac` — fewer bug surfaces, exact match to PO shape spec ("HS256 base64url with iat").

- **Node 20 global `fetch` for X token exchange.** Already in the Cloud Functions runtime (undici 6.x under the hood). Single POST per invocation = no need for explicit `undici` dep.
  - Sources: [undici GitHub](https://github.com/nodejs/undici).
  - Takeaway: round 1 Q2 picked global fetch. Lint rule blocks `import undici` to prevent accidental drift.

- **Secret Manager add-then-disable recipe.** `accessSecretVersion(latest)` → capture name → `addSecretVersion(...)` → `disableSecretVersion(<previous>)`. Per-secret `roles/secretmanager.secretAccessor` is correct for read; create-version paths additionally need `secretVersionAdder` + `secretVersionManager` at the project level when the secret doesn't exist yet (first-call-per-user creates `crumb-x-refresh-token-{uid}`).
  - Sources: [@google-cloud/secret-manager docs](https://cloud.google.com/nodejs/docs/reference/secret-manager/latest/overview).
  - Takeaway: operator prereqs #2 (per-secret accessor) + #4 (project-level adder/manager) reflect this asymmetry.

- **X (Twitter) OAuth2 PKCE confidential client.** `POST https://api.x.com/2/oauth2/token` with `Content-Type: application/x-www-form-urlencoded`, `Authorization: Basic base64(client_id:client_secret)`, body `code/grant_type=authorization_code/redirect_uri/code_verifier`. Refresh token only issued with `offline.access` scope on the authorize URL.
  - Sources: [X authorization-code docs](https://docs.x.com/resources/fundamentals/authentication/oauth-2-0/authorization-code).
  - Takeaway: `oauthCallback` step 12 encodes this verbatim.

- **firebase-functions-test@^3 + ts-jest CJS preset.** Offline mode: `const test = require("firebase-functions-test")();` before importing handlers. Wrap pattern: `test.wrap(handler)`. CJS preset avoids the ESM transform tax; `jose@^5` ships dual CJS/ESM and works transparently.
  - Sources: [Firebase unit-testing docs](https://firebase.google.com/docs/functions/unit-testing).

- **Cloud Functions Gen 2 cold-start in 2026.** Still ~400–700ms runtime + app init. Cloud Scheduler 5-min ping to a trivial `warmUp` handler is the canonical cheap keepalive (~$0.10/month at 100ms execution; vs ~$15–20/month for `minInstances=1`). Round 3 Q1 picked this.
  - Sources: [Firebase tips](https://firebase.google.com/docs/functions/tips).

- **Firestore rules `get()` for cross-doc allowlist.** Counts as +1 billed read per request, cached per-request. Safe in 2026; no recent rules engine changes affect this pattern.
  - Sources: [Firestore security rules — referencing data in other documents](https://firebase.google.com/docs/firestore/security/rules-conditions#access_other_documents).

## Revision History

*(none yet — first plan write)*

## Recommended Next Stage

- **Option A (default):** `/wf implement cloud-function-bookmark-sync functions-oauth` — execute the 27-step plan. **Run `/compact` first** to discard planning research from context (the PreCompact hook preserves workflow state).
- **Option B:** `/wf plan cloud-function-bookmark-sync daily-poll` — plan the next slice. `daily-poll` consumes `lib/secrets.ts` (already specified here) and adds `dailyPoll` + `triggerPoll` handlers; planning it now means daily-poll can be implemented immediately after this one without re-entering plan.
- **Option C:** `/wf plan cloud-function-bookmark-sync functions-oauth <feedback>` — return to this plan with explicit corrections (directed-fix mode). E.g., flip back to raw `crypto`, drop the Cloud Scheduler keepalive, or change the allowlist schema.
