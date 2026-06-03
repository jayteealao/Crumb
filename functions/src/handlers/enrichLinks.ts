import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";

/**
 * Firestore-triggered link-preview enrichment. Fires once when the daily poll
 * (or any writer) creates a tweet doc and best-effort OG-fetches the first
 * external link's metadata into a `textAnnotations` url doc the Android card
 * reads. Isolated from the poll so a slow/hung destination URL never stalls the
 * bookmark sync, and so the network/SSRF failure surface is contained to this
 * function. Runs least-privilege; the heavy lifting (and unit tests) live in
 * `lib/enrich-links` + `lib/og`.
 *
 * @param event - the create event for `users/{uid}/tweets/{tweetId}`.
 */
export const enrichTweetLinks = onDocumentCreated(
  {
    document: "users/{uid}/tweets/{tweetId}",
    region: "europe-west2",
    timeoutSeconds: 30,
    memory: "256MiB",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const { uid, tweetId } = event.params;
    const data = snap.data() as Record<string, unknown> | undefined;
    const entities = data?.entities;
    if (!entities) return;

    const { db } = await import("../lib/admin");
    const { runEnrichLinks } = await import("../lib/enrich-links");
    const { fetchOpenGraph } = await import("../lib/og");

    try {
      const outcome = await runEnrichLinks(db(), uid, tweetId, entities, fetchOpenGraph);
      logger.info("enrich_links_done", { uid, tweetId, outcome });
    } catch (e) {
      logger.error("enrich_links_failed", { uid, tweetId, code: (e as Error).message });
    }
  },
);
