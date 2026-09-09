// Shared X OAuth 2.0 refresh-token exchange.
//
// X v2 refresh tokens are single-use. Every caller that exchanges a refresh
// token MUST persist the rotated token that comes back, or the next call will
// receive `invalid_grant` and permanently unlink the account.
//
// This helper:
//   1. Reads the stored refresh token for [uid].
//   2. Exchanges it with X.
//   3. Conditionally persists the rotated token (if X returned a new one).
//   4. Returns the short-lived access token.
//
// poll.ts's `invalid_grant` → "refresh_revoked" classification is preserved via
// the returned error shape; callers that need different error handling catch and
// reclassify the thrown error themselves.

import { logger } from "firebase-functions/v2";
import { TOKEN_URL } from "./twitter-api";
import { getRefreshToken, setRefreshToken, getXClientCredentials } from "./secrets";

export interface OAuthRefreshResult {
  accessToken: string;
}

export class RefreshRevokedError extends Error {
  constructor() {
    super("invalid_grant");
    this.name = "RefreshRevokedError";
  }
}

export class RefreshFailedError extends Error {
  readonly code: string;
  constructor(code: string) {
    super(`refresh_failed: ${code}`);
    this.name = "RefreshFailedError";
    this.code = code;
  }
}

export class NoRefreshTokenError extends Error {
  constructor() {
    super("no_refresh_token");
    this.name = "NoRefreshTokenError";
  }
}

/**
 * Exchange the stored X refresh token for a fresh access token, persisting any
 * rotated refresh token returned by X.
 *
 * @throws {NoRefreshTokenError} when no refresh token is stored for [uid].
 * @throws {RefreshRevokedError} when X returns `invalid_grant`.
 * @throws {RefreshFailedError} when X returns any other non-OK response.
 */
export async function refreshXToken(uid: string): Promise<OAuthRefreshResult> {
  const storedRt = await getRefreshToken(uid);
  if (!storedRt) {
    throw new NoRefreshTokenError();
  }

  const { clientId, clientSecret } = await getXClientCredentials();
  const basicAuth = Buffer.from(`${clientId}:${clientSecret}`, "utf8").toString("base64");

  const refreshResp = await fetch(TOKEN_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Authorization: `Basic ${basicAuth}`,
    },
    body: new URLSearchParams({
      grant_type: "refresh_token",
      refresh_token: storedRt,
      client_id: clientId,
    }),
  });

  if (!refreshResp.ok) {
    const body = (await refreshResp.json().catch(() => ({}))) as { error?: string };
    const code = body.error ?? `http_${refreshResp.status}`;
    if (code === "invalid_grant") {
      throw new RefreshRevokedError();
    }
    throw new RefreshFailedError(code);
  }

  const tokens = (await refreshResp.json()) as {
    access_token: string;
    refresh_token?: string;
  };

  if (tokens.refresh_token && tokens.refresh_token !== storedRt) {
    await setRefreshToken(uid, tokens.refresh_token);
    logger.info("x_rt_rotated", { uid });
  }

  return { accessToken: tokens.access_token };
}

/**
 * Exchange a refresh token with an abort signal (for callers with their own
 * timeout management). Skips the stored-token lookup — the caller owns the
 * stored token and abort lifecycle.
 *
 * Persists a rotated token on success, mirroring {@link refreshXToken}.
 */
export async function refreshXTokenWithSignal(
  uid: string,
  storedRt: string,
  signal: AbortSignal,
): Promise<OAuthRefreshResult> {
  const { clientId, clientSecret } = await getXClientCredentials();
  const basicAuth = Buffer.from(`${clientId}:${clientSecret}`, "utf8").toString("base64");

  const refreshResp = await fetch(TOKEN_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Authorization: `Basic ${basicAuth}`,
    },
    body: new URLSearchParams({
      grant_type: "refresh_token",
      refresh_token: storedRt,
      client_id: clientId,
    }),
    signal,
  });

  if (!refreshResp.ok) {
    throw new RefreshFailedError(`http_${refreshResp.status}`);
  }

  const tokens = (await refreshResp.json()) as {
    access_token: string;
    refresh_token?: string;
  };

  if (tokens.refresh_token && tokens.refresh_token !== storedRt) {
    await setRefreshToken(uid, tokens.refresh_token);
    logger.info("x_rt_rotated", { uid });
  }

  return { accessToken: tokens.access_token };
}
