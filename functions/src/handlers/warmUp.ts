import { onRequest } from "firebase-functions/v2/https";

/**
 * Lightweight HTTP keep-alive endpoint used to warm up idle Cloud Function instances.
 *
 * @remarks
 * Crumb's Android client (or an external scheduler) may call this before a
 * latency-sensitive operation to reduce cold-start delays on other functions.
 * The handler requires a valid Firebase ID token in the `Authorization: Bearer`
 * header, consistent with the rest of this codebase's auth pattern. Returns
 * `401` when the header is absent or the token is invalid.
 *
 * @param req - HTTP request. Must include `Authorization: Bearer <idToken>`.
 * @param res - HTTP response. `200 OK` with body `"ok"` on success; `401` when
 *   authentication fails.
 */
export const warmUp = onRequest(async (req, res) => {
  const authHeader = req.headers.authorization ?? "";
  const idToken = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : "";
  if (!idToken) {
    res.status(401).send("Unauthorized");
    return;
  }
  try {
    const { getAuth } = await import("firebase-admin/auth");
    const { app } = await import("../lib/admin");
    await getAuth(app()).verifyIdToken(idToken);
  } catch {
    res.status(401).send("Unauthorized");
    return;
  }
  res.status(200).send("ok");
});
