import { onCall, HttpsError } from "firebase-functions/v2/https";

// On-demand poll triggered by the Android client (pull-to-refresh).
//   timeout: 60s (Gen 2 callable default range; one-user backfill must fit)
//   memory:  default (256MiB)
//
// Returns the PollResult shape directly for soft failures (debounced /
// in_progress / rate_limited / refresh_revoked) instead of throwing — the
// client renders these as UI states, not errors.
//
// Throws HttpsError("unauthenticated") only when request.auth is absent.
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
