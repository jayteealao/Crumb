import { SignJWT, jwtVerify, type JWTPayload } from "jose";
import { getOAuthStateSecret } from "./secrets";

const ISSUER = "crumb-functions";
const AUDIENCE = "crumb-oauth-callback";
const MAX_AGE_SECONDS = 600;

export interface OAuthStateClaims {
  uid: string;
  nonce: string;
  iat: number;
  cv: string;
}

export async function signOAuthState(
  uid: string,
  nonce: string,
  codeVerifier: string,
): Promise<string> {
  const key = await getOAuthStateSecret();
  return await new SignJWT({ uid, nonce, cv: codeVerifier })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuer(ISSUER)
    .setAudience(AUDIENCE)
    .setIssuedAt()
    .setExpirationTime(`${MAX_AGE_SECONDS}s`)
    .sign(key);
}

export async function verifyOAuthState(token: string): Promise<OAuthStateClaims> {
  if (!token) throw new Error("invalid_state_missing");
  const key = await getOAuthStateSecret();
  const { payload } = await jwtVerify(token, key, {
    issuer: ISSUER,
    audience: AUDIENCE,
    clockTolerance: 5,
  });
  const { uid, nonce, iat, cv } = payload as JWTPayload & {
    uid?: unknown;
    nonce?: unknown;
    cv?: unknown;
  };
  if (
    typeof uid !== "string" ||
    typeof nonce !== "string" ||
    typeof iat !== "number" ||
    typeof cv !== "string" ||
    cv.length === 0
  ) {
    throw new Error("invalid_state_claims");
  }
  const nowSec = Math.floor(Date.now() / 1000);
  if (iat - nowSec > 5) {
    throw new Error("invalid_state_future_iat");
  }
  return { uid, nonce, iat, cv };
}
