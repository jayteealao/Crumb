import { onRequest } from "firebase-functions/v2/https";

/**
 * Lightweight HTTP keep-alive endpoint used to warm up idle Cloud Function instances.
 *
 * @remarks
 * Crumb's Android client (or an external scheduler) may call this before a
 * latency-sensitive operation to reduce cold-start delays on other functions.
 * The handler performs no authentication, reads no state, and has no side effects.
 *
 * @param _req - HTTP request (unused).
 * @param res - HTTP response. Always responds `200 OK` with body `"ok"`.
 */
export const warmUp = onRequest((_req, res) => {
  res.status(200).send("ok");
});
