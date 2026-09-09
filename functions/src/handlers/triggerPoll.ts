import { onCall, HttpsError } from "firebase-functions/v2/https";

/**
 * On-demand X/Twitter bookmark poll triggered by the Android client (e.g. pull-to-refresh).
 *
 * @remarks
 * Configured with a 60 s timeout to accommodate a single-user backfill within
 * Gen 2 callable limits. Soft failure states (debounced, in_progress,
 * rate_limited, refresh_revoked) are returned as structured response objects
 * rather than thrown errors so the client can render them as distinct UI states.
 * Only a missing `request.auth` causes a hard `HttpsError`.
 *
 * @param request - Firebase callable request; `request.data` is ignored (no
 *   input payload required). `request.auth` must be present.
 * @returns A `PollResult` object. On success: `{ status: "ok" }`. On soft
 *   failure: `{ status: "debounced" | "in_progress" | "rate_limited" | "refresh_revoked", ... }`.
 * @throws {HttpsError} `"unauthenticated"` – when `request.auth` is absent.
 */
export const triggerPoll = onCall(
  { region: "europe-west2", timeoutSeconds: 60 },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in required");
    }
    const { runPoll } = await import("../lib/poll");
    return await runPoll(request.auth.uid, { reason: "trigger" });
  },
);
