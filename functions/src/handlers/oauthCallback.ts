import { onRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

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
