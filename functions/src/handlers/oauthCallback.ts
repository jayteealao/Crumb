import { onRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

/**
 * HTTP handler that receives the X/Twitter OAuth 2.0 PKCE authorization callback.
 *
 * @remarks
 * X redirects the user's browser here after they approve the OAuth consent screen.
 * The handler:
 * 1. Validates the `state` parameter by verifying the signed JWT minted by
 *    `mintOAuthState` (CSRF protection + PKCE code verifier retrieval).
 * 2. Exchanges the `code` parameter for tokens at the X token endpoint
 *    (`grant_type=authorization_code`).
 * 3. Stores the resulting refresh token in Secret Manager via `setRefreshToken`.
 * 4. Sets `sync_status/state.linked = true` in Firestore.
 * 5. Fire-and-forgets `runPoll` so the first bookmarks arrive within ~30 s of
 *    OAuth completion without delaying the redirect.
 * 6. Redirects the browser to the deep-link URI `crumbs://graphitenerd.xyz/x-oauth-complete`
 *    on success, or `crumbs://graphitenerd.xyz/x-oauth-error?reason=<code>` on failure.
 *
 * All recoverable errors redirect to the error deep-link rather than returning
 * HTTP error statuses, ensuring the Android custom tab can hand off cleanly.
 *
 * @param req - Express-style HTTP request. Expected query params:
 *   - `code` (string) – authorization code from X.
 *   - `state` (string) – signed state token minted by `mintOAuthState`.
 * @param res - Express-style HTTP response. Responds with a 302 redirect on
 *   both success and most failure paths; 400 for missing/invalid params; 500
 *   for unexpected internal errors.
 */
const X_TOKEN_ENDPOINT = "https://api.x.com/2/oauth2/token";
const REDIRECT_URI = "https://europe-west2-crumbs-a4fdb.cloudfunctions.net/oauthCallback";
const DEEP_LINK_SUCCESS = "crumbs://graphitenerd.xyz/x-oauth-complete";
const DEEP_LINK_ERROR = "crumbs://graphitenerd.xyz/x-oauth-error";

function firstQueryValue(value: unknown): string | undefined {
  if (typeof value === "string") return value;
  if (Array.isArray(value) && typeof value[0] === "string") return value[0];
  return undefined;
}

export const oauthCallback = onRequest(async (req, res) => {
  try {
    const code = firstQueryValue(req.query.code);
    const state = firstQueryValue(req.query.state);

    if (!code || !state) {
      res.status(400).send("missing params");
      return;
    }

    const { verifyOAuthState } = await import("../lib/state");
    let claims;
    try {
      claims = await verifyOAuthState(state);
    } catch {
      res.status(400).send("invalid state");
      return;
    }

    const codeVerifier = claims.cv;

    const { getXClientCredentials, setRefreshToken } = await import("../lib/secrets");
    const { db } = await import("../lib/admin");
    const { FieldValue } = await import("firebase-admin/firestore");

    const { clientId, clientSecret } = await getXClientCredentials();
    const basic = Buffer.from(`${clientId}:${clientSecret}`, "utf8").toString("base64");

    const body = new URLSearchParams({
      code,
      grant_type: "authorization_code",
      redirect_uri: REDIRECT_URI,
      code_verifier: codeVerifier,
    });

    const response = await fetch(X_TOKEN_ENDPOINT, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        Authorization: `Basic ${basic}`,
      },
      body,
    });

    const statusDoc = db().doc(`users/${claims.uid}/sync_status/state`);

    if (!response.ok) {
      const errorCode = await extractErrorCode(response);
      logger.info("oauth_callback_token_exchange_failed", {
        uid: claims.uid,
        status: response.status,
        error: errorCode,
      });
      await statusDoc.set(
        {
          linked: false,
          lastError: errorCode,
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true },
      );
      res.redirect(302, `${DEEP_LINK_ERROR}?reason=${encodeURIComponent(errorCode)}`);
      return;
    }

    const payload = (await response.json()) as { refresh_token?: unknown };
    const refreshToken = typeof payload.refresh_token === "string" ? payload.refresh_token : null;
    if (!refreshToken) {
      logger.error("oauth_callback_missing_refresh_token", { uid: claims.uid });
      await statusDoc.set(
        {
          linked: false,
          lastError: "missing_refresh_token",
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true },
      );
      res.redirect(302, `${DEEP_LINK_ERROR}?reason=missing_refresh_token`);
      return;
    }

    await setRefreshToken(claims.uid, refreshToken);
    await statusDoc.set(
      {
        linked: true,
        lastPolledAt: null,
        lastError: null,
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );

    logger.info("oauth_callback_linked", { uid: claims.uid });

    // Fan out to runPoll so the first bookmarks land within ~30s of OAuth
    // completion. Fire-and-forget — the redirect is never delayed by poll
    // latency. Errors are logged but do not affect the user-facing flow.
    const fanOut = (async () => {
      try {
        const { runPoll } = await import("../lib/poll");
        const result = await runPoll(claims.uid);
        logger.info("oauth_callback_fanout_poll", { uid: claims.uid, result });
      } catch (err) {
        logger.error("oauth_callback_fanout_poll_failed", {
          uid: claims.uid,
          error: (err as Error).message,
        });
      }
    })();
    void fanOut;

    res.redirect(302, DEEP_LINK_SUCCESS);
  } catch (e) {
    logger.error("oauth_callback_unexpected", { code: (e as Error).message });
    res.status(500).send("internal");
  }
});

async function extractErrorCode(response: Response): Promise<string> {
  try {
    const parsed = (await response.json()) as { error?: unknown };
    if (typeof parsed.error === "string") return parsed.error;
  } catch {
    // fall through
  }
  return `http_${response.status}`;
}
