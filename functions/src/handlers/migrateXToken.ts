import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

const X_TOKEN_ENDPOINT = "https://api.x.com/2/oauth2/token";

// One-shot callable invoked by the on-device migration worker after the
// server-side cutover. The client uploads its locally-stored refresh token; we
// validate it against X (grant_type=refresh_token), persist the rotated token
// in Secret Manager, flip sync_status.linked=true, and fan out runPoll so the
// first server-driven bookmarks land within ~30s.
//
// Returns { ok: false, reason: "invalid" } when X rejects the upload — the
// worker treats that as terminal (no retry) and the reconnect banner becomes
// the UX. Network failures bubble up as HttpsError("internal") so WorkManager
// retries with backoff.
export const migrateXToken = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in required");
  }

  const data = (request.data ?? {}) as { refreshToken?: unknown };
  const refreshToken = data.refreshToken;
  if (typeof refreshToken !== "string" || refreshToken.length === 0) {
    throw new HttpsError("invalid-argument", "missing refreshToken");
  }

  const uid = request.auth.uid;

  const { getXClientCredentials, setRefreshToken } = await import("../lib/secrets");
  const { db } = await import("../lib/admin");
  const { FieldValue } = await import("firebase-admin/firestore");

  const { clientId, clientSecret } = await getXClientCredentials();
  const basic = Buffer.from(`${clientId}:${clientSecret}`, "utf8").toString("base64");

  const body = new URLSearchParams({
    grant_type: "refresh_token",
    refresh_token: refreshToken,
  });

  let response: Response;
  try {
    response = await fetch(X_TOKEN_ENDPOINT, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        Authorization: `Basic ${basic}`,
      },
      body,
    });
  } catch (err) {
    logger.error("migrate_x_token_network_failure", {
      uid,
      error: (err as Error).message,
    });
    throw new HttpsError("internal", "x_unreachable");
  }

  if (!response.ok) {
    logger.info("migrate_x_token_invalid", { uid, status: response.status });
    return { ok: false, reason: "invalid" };
  }

  const payload = (await response.json()) as { refresh_token?: unknown };
  const rotated = typeof payload.refresh_token === "string" ? payload.refresh_token : null;
  // X usually rotates the refresh_token on every successful refresh, but we
  // accept the legacy non-rotating path by reusing the original on a missing
  // field. setRefreshToken handles the add-then-disable-previous semantics.
  const tokenToStore = rotated ?? refreshToken;

  await setRefreshToken(uid, tokenToStore);

  await db().doc(`users/${uid}/sync_status/state`).set(
    {
      linked: true,
      lastError: null,
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );

  logger.info("migrate_x_token_linked", { uid });

  // Fan out to runPoll so the first server-driven bookmarks land within ~30s
  // of migration. Fire-and-forget — the worker is not blocked by poll latency.
  const fanOut = (async () => {
    try {
      const { runPoll } = await import("../lib/poll");
      const result = await runPoll(uid);
      logger.info("migrate_x_token_fanout_poll", { uid, result });
    } catch (err) {
      logger.error("migrate_x_token_fanout_poll_failed", {
        uid,
        error: (err as Error).message,
      });
    }
  })();
  void fanOut;

  return { ok: true };
});
