import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";

// Twice-daily X-bookmarks poll (PO Round 3 Q9).
//   schedule: 0 9,21 * * * UTC
//   timeout:  540s  (Gen 2 event-driven max)
//   memory:   512MiB (headroom for the 800-tweet diff set; default 256MiB is tight)
//
// Iterates linked users via collectionGroup("sync_status"). Requires the
// firestore.indexes.json single-field index on sync_status.linked to be
// deployed and built before the first run.
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
