import { onCall, HttpsError } from "firebase-functions/v2/https";
import { randomBytes } from "node:crypto";

export const mintOAuthState = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in required");
  }
  const { signOAuthState } = await import("../lib/state");
  const nonce = randomBytes(32).toString("base64url");
  const state = await signOAuthState(request.auth.uid, nonce);
  return { state, expiresAt: Math.floor(Date.now() / 1000) + 600 };
});
