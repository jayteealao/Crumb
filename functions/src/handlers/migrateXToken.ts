import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

const X_TOKEN_ENDPOINT = "https://api.x.com/2/oauth2/token";

/**
 * Validates and migrates a locally-stored X/Twitter refresh token to server-side custody.
 *
 * @remarks
 * Called once by the on-device `XTokenMigrationWorker` after the server-side OAuth
 * cutover. The worker uploads the refresh token it previously stored in Android
 * SharedPreferences. This function:
 * 1. Validates the token against the X token endpoint (`grant_type=refresh_token`).
 * 2. Persists the rotated token (or the original if X does not rotate) in Secret Manager
 *    via `setRefreshToken`, which uses add-then-disable-previous semantics.
 * 3. Writes `sync_status/state.linked = true` to Firestore for the user.
 * 4. Fire-and-forgets `runPoll` so the first server-driven bookmarks arrive within ~30 s.
 *
 * A `{ ok: false, reason: "invalid" }` response signals a terminal failure — the worker
 * should not retry and the reconnect banner becomes the user-facing recovery path.
 * Network errors are rethrown as `HttpsError("internal")` so WorkManager retries with
 * exponential backoff.
 *
 * @param request - Callable request. `request.data.refreshToken` (string, required) is
 *   the X OAuth 2.0 refresh token obtained during the previous on-device PKCE flow.
 * @returns `{ ok: true }` on success, or `{ ok: false, reason: "invalid" }` when X
 *   rejects the provided token (terminal — no client retry).
 * @throws {HttpsError} `"unauthenticated"` – when `request.auth` is absent.
 * @throws {HttpsError} `"invalid-argument"` – when `request.data.refreshToken` is missing or empty.
 * @throws {HttpsError} `"internal"` – on network failure reaching the X token endpoint.
 */
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
