import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";

/**
 * Scheduled twice-daily X/Twitter bookmark poll for all linked users.
 *
 * @remarks
 * Runs at 09:00 and 21:00 UTC every day (`0 9,21 * * *`). Configured with
 * 540 s timeout (Gen 2 event-driven maximum) and 512 MiB memory to provide
 * headroom for large bookmark diff sets (~800 tweets).
 *
 * Iterates all users with `sync_status/state.linked == true` using a
 * Firestore `collectionGroup` query. Requires the `firestore.indexes.json`
 * single-field index on `sync_status.linked` to be deployed and built before
 * the first run. Per-user failures are caught and logged so one bad account
 * does not abort the batch.
 *
 * @param event - Cloud Scheduler event metadata (`event.scheduleTime`,
 *   `event.jobName`). No application-level input is required.
 * @returns `void` — side effects are written to Firestore and logged.
 */
export const dailyPoll = onSchedule(
  {
    schedule: "0 9,21 * * *",
    timeZone: "UTC",
    region: "europe-west2",
    timeoutSeconds: 540,
    memory: "512MiB",
  },
  async (event) => {
    const { db } = await import("../lib/admin");
    const { runPoll } = await import("../lib/poll");
    logger.info("daily_poll_started", {
      scheduleTime: event.scheduleTime,
      jobName: event.jobName,
    });

    const linkedSnap = await db()
      .collectionGroup("sync_status")
      .where("linked", "==", true)
      .get();

    for (const doc of linkedSnap.docs) {
      const uid = doc.ref.parent.parent?.id;
      if (!uid) continue;
      try {
        const result = await runPoll(uid, { reason: "scheduled" });
        logger.info("daily_poll_user_completed", { uid, result });
      } catch (e) {
        logger.error("daily_poll_user_failed", { uid, code: (e as Error).message });
      }
    }

    logger.info("daily_poll_completed", { userCount: linkedSnap.docs.length });
  },
);
