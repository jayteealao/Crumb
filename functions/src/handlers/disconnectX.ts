import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

/**
 * Disconnects the authenticated user's X/Twitter account from Crumb.
 *
 * @remarks
 * Invoked from the Settings screen when the user taps "Disconnect X account".
 * Performs two operations atomically from the user's perspective:
 * 1. Deletes the X refresh token from Secret Manager (`deleteRefreshToken`).
 * 2. Sets `sync_status/state.linked = false` in Firestore.
 *
 * The client is responsible for clearing its own local SharedPreferences after
 * receiving `{ ok: true }`.
 *
 * This function intentionally does **not** call the X `/2/oauth2/revoke` endpoint
 * (RFC 7009). Users who need to revoke at the X level can do so via
 * `x.com/settings/connected_apps`. Omitting the revoke call keeps the error
 * surface narrow (one server operation instead of two) and avoids unnecessary
 * network latency on the Settings screen.
 *
 * @param request - Firebase callable request; no input payload is required.
 *   `request.auth` must be present.
 * @returns `{ ok: true }` on success.
 * @throws {HttpsError} `"unauthenticated"` – when `request.auth` is absent.
 * @throws {HttpsError} `"internal"` – when the Secret Manager delete operation fails.
 */
export const disconnectX = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign-in required");
  }

  const uid = request.auth.uid;
  const { deleteRefreshToken } = await import("../lib/secrets");
  const { db } = await import("../lib/admin");
  const { FieldValue } = await import("firebase-admin/firestore");

  try {
    await deleteRefreshToken(uid);
  } catch (err) {
    logger.error("disconnect_x_delete_secret_failed", {
      uid,
      error: (err as Error).message,
    });
    throw new HttpsError("internal", "delete_failed");
  }

  await db().doc(`users/${uid}/sync_status/state`).set(
    {
      linked: false,
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );

  logger.info("disconnect_x_complete", { uid });

  return { ok: true };
});
