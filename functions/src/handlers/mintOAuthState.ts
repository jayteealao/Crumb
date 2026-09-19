import { onCall, HttpsError } from "firebase-functions/v2/https";
import { randomBytes } from "node:crypto";

const PKCE_VERIFIER_PATTERN = /^[A-Za-z0-9\-._~]{43,128}$/;

/**
 * Mints a short-lived, signed OAuth state token for the X/Twitter PKCE flow.
 *
 * @remarks
 * Called by the Android client before redirecting the user to the X OAuth
 * consent screen. The client generates a PKCE `code_verifier` locally and
 * passes it here; the server signs a JWT containing the verifier and a random
 * nonce (CSRF protection) and returns the compact state string.
 *
 * The state token is valid for 10 minutes (600 s). After the OAuth redirect,
 * `oauthCallback` verifies the state and extracts the code verifier to
 * complete the PKCE exchange without the client needing to persist the verifier
 * server-side.
 *
 * @param request - Callable request. `request.data.code_verifier` (string,
 *   43–128 characters from the unreserved PKCE alphabet `[A-Za-z0-9-._~]`)
 *   is required. `request.auth` must be present.
 * @returns `{ state: string, expiresAt: number }` where `state` is the signed
 *   JWT to pass as the OAuth `state` parameter and `expiresAt` is a Unix
 *   timestamp (seconds) indicating token expiry.
 * @throws {HttpsError} `"unauthenticated"` – when `request.auth` is absent.
 * @throws {HttpsError} `"invalid-argument"` – when `code_verifier` is missing
 *   or does not conform to the PKCE unreserved-character alphabet (RFC 7636 §4.1).
 */
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
