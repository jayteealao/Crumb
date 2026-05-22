import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

// User-triggered disconnect from the Settings screen. Removes the
// server-stored X refresh token and flips sync_status.linked=false. The
// client clears its local Prefs after this callable returns ok.
//
// We deliberately do NOT call X's /2/oauth2/revoke endpoint (RFC 7009): users
// who want to revoke from the X side can do so via
// x.com/settings/connected_apps. Skipping the revoke saves a network hop and
// keeps the error surface narrow (one operation rather than two).
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
