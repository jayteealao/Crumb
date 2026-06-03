import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import type { QueryDocumentSnapshot } from "firebase-admin/firestore";

/**
 * One-shot link-preview backfill over the EXISTING tweet corpus. The
 * create-trigger ([enrichTweetLinks]) only enriches tweets written after it was
 * deployed; this callable sweeps the back-catalogue so already-stored bookmarks
 * also gain previews.
 *
 * @remarks
 * Self-scoped (least-privilege): a caller backfills only their OWN
 * `users/{uid}/tweets` sub-collection — there is no cross-user admin mode, so a
 * compromised token can never enrich another user's data. Paginates by document
 * id and reuses the idempotent {@link runEnrichLinks} (skip-if-exists), so it is
 * safe to re-run and composes with the trigger without duplicating rows. Bounded
 * by {@link MAX_BACKFILL_TWEETS}; a cap hit is logged, not silently truncated.
 *
 * @param request - callable request; `request.data` is ignored. `request.auth`
 *   must be present.
 * @returns `{ scanned, enriched, skipped, capped }` tallies for the sweep.
 * @throws {HttpsError} `"unauthenticated"` when `request.auth` is absent.
 */
const PAGE_SIZE = 200;
const MAX_BACKFILL_TWEETS = 5_000;

export const backfillTweetLinks = onCall(
  { region: "europe-west2", timeoutSeconds: 540, memory: "512MiB" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in required");
    }
    const uid = request.auth.uid;

    const { db } = await import("../lib/admin");
    const { runEnrichLinks } = await import("../lib/enrich-links");
    const { fetchOpenGraph } = await import("../lib/og");
    const { FieldPath } = await import("firebase-admin/firestore");

    const database = db();
    const tweetsCol = database.collection(`users/${uid}/tweets`);

    let scanned = 0;
    let enriched = 0;
    let skipped = 0;
    let lastDoc: QueryDocumentSnapshot | undefined;
    let capped = false;

    while (scanned < MAX_BACKFILL_TWEETS) {
      let query = tweetsCol.orderBy(FieldPath.documentId()).limit(PAGE_SIZE);
      if (lastDoc) query = query.startAfter(lastDoc);
      const snap = await query.get();
      if (snap.empty) break;

      for (const doc of snap.docs) {
        const data = doc.data() as Record<string, unknown> | undefined;
        const entities = data?.entities;
        if (entities) {
          try {
            const outcome = await runEnrichLinks(database, uid, doc.id, entities, fetchOpenGraph);
            if (outcome === "written") enriched++;
            else if (outcome === "skipped") skipped++;
          } catch (e) {
            logger.warn("backfill_links_doc_failed", { uid, tweetId: doc.id, code: (e as Error).message });
          }
        }
        scanned++;
      }

      lastDoc = snap.docs[snap.docs.length - 1];
      if (snap.docs.length < PAGE_SIZE) break;
      if (scanned >= MAX_BACKFILL_TWEETS) {
        capped = true;
        break;
      }
    }

    logger.info("backfill_links_done", { uid, scanned, enriched, skipped, capped });
    return { scanned, enriched, skipped, capped };
  },
);
