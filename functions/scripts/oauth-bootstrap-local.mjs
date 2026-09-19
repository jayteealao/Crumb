#!/usr/bin/env node
// Local-redirect OAuth bootstrap. Spins up an HTTP listener on 127.0.0.1:8765,
// opens the browser to X's authorize URL, captures the callback in-process,
// exchanges the code with X directly, persists the refresh token to Secret
// Manager, and writes sync_status. One Authorize click, no copy/paste.
//
// Prereqs:
//   - X portal must include http://127.0.0.1:8765/callback in Callback URLs.
//   - Env: STATE_SECRET (crumb-oauth-state-secret), X_CLIENT_ID, X_CLIENT_SECRET, GOOGLE_APPLICATION_CREDENTIALS pointing at a SA key with secretmanager + datastore.user access (or omit and rely on gcloud ADC for Secret Manager via @google-cloud/secret-manager + firebase-admin for Firestore).
//
// Run from functions/ so node resolves jose + @google-cloud/secret-manager + firebase-admin.

import { SignJWT } from "jose";
import { randomBytes, createHash } from "node:crypto";
import { createServer } from "node:http";
import { exec } from "node:child_process";
import { writeFileSync } from "node:fs";

const PROJECT_ID = "crumbs-a4fdb";
const PORT = 8765;
const REDIRECT_URI = `http://127.0.0.1:${PORT}/callback`;
const X_AUTHORIZE_URL = "https://x.com/i/oauth2/authorize";
const X_TOKEN_URL = "https://api.x.com/2/oauth2/token";

const uid = process.argv[2];
if (!uid) {
  console.error("Usage: node oauth-bootstrap-local.mjs <uid>");
  process.exit(2);
}

const stateSecretRaw = process.env.STATE_SECRET;
const clientId = process.env.X_CLIENT_ID;
const clientSecret = process.env.X_CLIENT_SECRET;
if (!stateSecretRaw || !clientId || !clientSecret) {
  console.error("Set STATE_SECRET, X_CLIENT_ID, X_CLIENT_SECRET env vars.");
  process.exit(2);
}

const key = new Uint8Array(Buffer.from(stateSecretRaw, "utf8"));
const nonce = randomBytes(32).toString("base64url");
const state = await new SignJWT({ uid, nonce })
  .setProtectedHeader({ alg: "HS256" })
  .setIssuer("crumb-functions")
  .setAudience("crumb-oauth-callback")
  .setIssuedAt()
  .setExpirationTime("600s")
  .sign(key);

const codeVerifier = randomBytes(32).toString("base64url");
const codeChallenge = createHash("sha256").update(codeVerifier).digest("base64url");

const authorize = new URL(X_AUTHORIZE_URL);
authorize.searchParams.set("response_type", "code");
authorize.searchParams.set("client_id", clientId);
authorize.searchParams.set("redirect_uri", REDIRECT_URI);
authorize.searchParams.set("scope", "bookmark.read offline.access tweet.read users.read");
authorize.searchParams.set("state", state);
authorize.searchParams.set("code_challenge", codeChallenge);
authorize.searchParams.set("code_challenge_method", "S256");

const handle = new Promise((resolve, reject) => {
  const server = createServer(async (req, res) => {
    try {
      const url = new URL(req.url, REDIRECT_URI);
      if (url.pathname !== "/callback") {
        res.statusCode = 404;
        res.end("not found");
        return;
      }
      const code = url.searchParams.get("code");
      const returnedState = url.searchParams.get("state");
      if (!code || !returnedState) {
        res.statusCode = 400;
        res.end("missing code/state");
        return;
      }
      if (returnedState !== state) {
        res.statusCode = 400;
        res.end("state mismatch");
        return;
      }

      const body = new URLSearchParams({
        code,
        grant_type: "authorization_code",
        redirect_uri: REDIRECT_URI,
        code_verifier: codeVerifier,
      });
      const basic = Buffer.from(`${clientId}:${clientSecret}`, "utf8").toString("base64");
      const tokenResp = await fetch(X_TOKEN_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          Authorization: `Basic ${basic}`,
        },
        body,
      });
      const tokenText = await tokenResp.text();
      let tokenJson;
      try { tokenJson = JSON.parse(tokenText); } catch { tokenJson = {}; }

      if (!tokenResp.ok || !tokenJson.refresh_token) {
        console.error("X token exchange failed:", tokenResp.status, tokenText);
        res.statusCode = 500;
        res.end(`X token exchange failed: ${tokenResp.status} ${tokenText}`);
        server.close();
        reject(new Error(`token_exchange_failed: ${tokenResp.status}`));
        return;
      }

      const refreshToken = tokenJson.refresh_token;
      console.log("✓ X token exchange succeeded");
      console.log("  access_token_len:", tokenJson.access_token?.length);
      console.log("  refresh_token_len:", refreshToken.length);
      console.log("  scope:", tokenJson.scope);

      // Persist refresh_token to Secret Manager and write sync_status.
      const { SecretManagerServiceClient } = await import("@google-cloud/secret-manager");
      const { initializeApp, applicationDefault } = await import("firebase-admin/app");
      const { getFirestore, FieldValue } = await import("firebase-admin/firestore");

      const sm = new SecretManagerServiceClient();
      const secretId = `crumb-x-refresh-token-${uid}`;
      const parent = `projects/${PROJECT_ID}`;
      const secretPath = `${parent}/secrets/${secretId}`;
      let previousVersionName = null;
      try {
        const [prev] = await sm.accessSecretVersion({ name: `${secretPath}/versions/latest` });
        previousVersionName = prev.name ?? null;
      } catch (e) {
        if (e && e.code === 5) {
          await sm.createSecret({ parent, secretId, secret: { replication: { automatic: {} } } });
        } else { throw e; }
      }
      await sm.addSecretVersion({ parent: secretPath, payload: { data: Buffer.from(refreshToken, "utf8") } });
      if (previousVersionName) {
        try { await sm.disableSecretVersion({ name: previousVersionName }); } catch {}
      }
      console.log("✓ Refresh token persisted to Secret Manager");

      // Init Firebase Admin (uses GOOGLE_APPLICATION_CREDENTIALS or ADC).
      try {
        initializeApp({ credential: applicationDefault(), projectId: PROJECT_ID });
      } catch (e) {
        if (!/already exists/.test(String(e))) throw e;
      }
      const db = getFirestore();
      await db.doc(`users/${uid}/sync_status/state`).set(
        { linked: true, lastPolledAt: null, lastError: null, updatedAt: FieldValue.serverTimestamp() },
        { merge: true },
      );
      console.log("✓ sync_status doc written (linked: true)");

      res.statusCode = 200;
      res.setHeader("Content-Type", "text/html; charset=utf-8");
      res.end("<h2>OAuth complete</h2><p>You can close this tab and return to the terminal.</p>");
      server.close();
      resolve({ refreshToken, scope: tokenJson.scope });
    } catch (e) {
      console.error("handler error:", e);
      res.statusCode = 500;
      res.end(`error: ${e.message}`);
      server.close();
      reject(e);
    }
  });
  server.listen(PORT, "127.0.0.1", () => {
    console.log(`Listening on ${REDIRECT_URI}`);
    console.log("Opening browser...");
    const cmd = process.platform === "win32"
      ? `start "" "${authorize.toString()}"`
      : process.platform === "darwin"
      ? `open '${authorize.toString()}'`
      : `xdg-open '${authorize.toString()}'`;
    exec(cmd);
    console.log("If the browser does not open, paste this URL manually:");
    console.log(authorize.toString());
  });
});

try {
  const result = await handle;
  writeFileSync(
    "../../.ai/workflows/cloud-function-bookmark-sync/verify-evidence/daily-poll/oauth-bootstrap-local-result.json",
    JSON.stringify({ ts: new Date().toISOString(), uid, scope: result.scope, refreshTokenLen: result.refreshToken.length }, null, 2),
  );
  console.log("DONE.");
  process.exit(0);
} catch (e) {
  process.exit(1);
}
