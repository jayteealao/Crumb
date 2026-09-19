#!/usr/bin/env node
// One-shot terminal OAuth bootstrap for Crumb's X (Twitter) integration.
//
// Mints the state JWT locally (same algorithm as functions/src/lib/state.ts),
// generates PKCE, prints the authorize URL, prompts for the redirected URL,
// then hits the live oauthCallback so the production code path exchanges the
// auth code and writes the refresh token via setRefreshToken.
//
// Requires env: STATE_SECRET (value of crumb-oauth-state-secret), X_CLIENT_ID.
// Run from functions/ so node resolves jose: cd functions && node ../scripts/oauth-bootstrap.mjs <uid>

import { SignJWT } from "jose";
import { randomBytes, createHash } from "node:crypto";
import * as readline from "node:readline/promises";
import { stdin, stdout } from "node:process";

const PROJECT_ID = "crumbs-a4fdb";
const REDIRECT_URI = "https://europe-west2-crumbs-a4fdb.cloudfunctions.net/oauthCallback";
const X_AUTHORIZE_URL = "https://x.com/i/oauth2/authorize";

const uid = process.argv[2];
if (!uid) {
  console.error("Usage: node oauth-bootstrap.mjs <uid>");
  console.error("Env required: STATE_SECRET (crumb-oauth-state-secret value), X_CLIENT_ID");
  process.exit(2);
}

const stateSecretRaw = process.env.STATE_SECRET;
const clientId = process.env.X_CLIENT_ID;
if (!stateSecretRaw || !clientId) {
  console.error("Set STATE_SECRET and X_CLIENT_ID env vars before running.");
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

console.log("\n==> Open this URL in your browser, log in to X, and consent:\n");
console.log(authorize.toString());
console.log("\n==> X redirects to oauthCallback. The page will likely show 'missing params' because");
console.log("    code_verifier is not in X's redirect (Android intercepts and appends it normally).");
console.log("==> Copy the FULL redirected URL from the browser address bar and paste below:\n");

const rl = readline.createInterface({ input: stdin, output: stdout });
const callbackUrl = (await rl.question("Paste URL: ")).trim();
rl.close();

let parsed;
try {
  parsed = new URL(callbackUrl);
} catch {
  console.error("Invalid URL.");
  process.exit(1);
}
const code = parsed.searchParams.get("code");
const returnedState = parsed.searchParams.get("state");

if (!code || !returnedState) {
  console.error("Could not parse code/state from URL.");
  process.exit(1);
}
if (returnedState !== state) {
  console.error("State mismatch — possible CSRF or stale state.");
  process.exit(1);
}

const callbackInvoke = new URL(REDIRECT_URI);
callbackInvoke.searchParams.set("code", code);
callbackInvoke.searchParams.set("state", returnedState);
callbackInvoke.searchParams.set("code_verifier", codeVerifier);

console.log("\n==> Invoking oauthCallback (production token-exchange path)...");
const cbResp = await fetch(callbackInvoke.toString(), { method: "GET", redirect: "manual" });
console.log("HTTP", cbResp.status);
const location = cbResp.headers.get("location");
if (location) console.log("Location:", location);
if (cbResp.status === 302 && location?.startsWith("crumbs://graphitenerd.xyz/x-oauth-complete")) {
  console.log("\n✓ Refresh token persisted. Project:", PROJECT_ID, "Secret: crumb-x-refresh-token-" + uid);
  process.exit(0);
}
const body = await cbResp.text();
if (body) console.log("Body:", body);
console.error("\n✗ OAuth bootstrap failed.");
process.exit(1);
