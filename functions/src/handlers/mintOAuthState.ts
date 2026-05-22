import { onCall, HttpsError } from "firebase-functions/v2/https";
import { randomBytes } from "node:crypto";

const PKCE_VERIFIER_PATTERN = /^[A-Za-z0-9\-._~]{43,128}$/;

export const mintOAuthState = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in required");
  }

  const data = (request.data ?? {}) as { code_verifier?: unknown };
  const codeVerifier = data.code_verifier;
  if (typeof codeVerifier !== "string" || !PKCE_VERIFIER_PATTERN.test(codeVerifier)) {
    throw new HttpsError(
      "invalid-argument",
      "code_verifier must be a 43-128 char string from [A-Za-z0-9-._~]",
    );
  }

  const { signOAuthState } = await import("../lib/state");
  const nonce = randomBytes(32).toString("base64url");
  const state = await signOAuthState(request.auth.uid, nonce, codeVerifier);
  return { state, expiresAt: Math.floor(Date.now() / 1000) + 600 };
});
