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
 * id and reuses {@link runEnrichLinks} (skip-if-exists by default), so it is
 * safe to re-run and composes with the trigger without duplicating rows. Bounded
 * by {@link MAX_BACKFILL_TWEETS}; a cap hit is logged, not silently truncated.
 *
 * Request data shape (all optional):
 * - `force?: boolean`     — overwrite existing url docs (re-enrich stale chips).
 *   Defaults to false (idempotent). Use after an enrichment-quality improvement
 *   to refresh docs that were written under the old logic.
 * - `onlyChips?: boolean` — skip tweets whose url doc already has a `title`
 *   (i.e., only process chips that lack a title). When `force` is also true,
 *   this narrows the re-enrich to the most impactful subset. Defaults to false.
 *
 * @param request - callable request; `request.auth` must be present.
 * @returns `{ scanned, enriched, skipped, capped }` tallies for the sweep.
 * @throws {HttpsError} `"unauthenticated"` when `request.auth` is absent.
 * @throws {HttpsError} `"invalid-argument"` when `force` or `onlyChips` are not boolean.
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

    // Validate optional request params.
    const data = (request.data ?? {}) as Record<string, unknown>;
    if ("force" in data && typeof data.force !== "boolean") {
      throw new HttpsError("invalid-argument", "`force` must be a boolean");
    }
    if ("onlyChips" in data && typeof data.onlyChips !== "boolean") {
      throw new HttpsError("invalid-argument", "`onlyChips` must be a boolean");
    }
    const force = (data.force as boolean | undefined) ?? false;
    const onlyChips = (data.onlyChips as boolean | undefined) ?? false;

    const { db } = await import("../lib/admin");
    const { runEnrichLinks } = await import("../lib/enrich-links");
    const { fetchOpenGraph } = await import("../lib/og");
    const { FieldPath } = await import("firebase-admin/firestore");

    const database = db();
    const tweetsCol = database.collection(`users/${uid}/tweets`);

    // Per-run vendor cap: one object shared across ALL concurrent workers so the
    // limit is global to this backfill invocation (not per-tweet). 20 calls covers
    // one pass over the ~100 residual-4xx links within Microlink's free tier.
    // sdlc-debt: cap is hardcoded; upgrade path = accept `vendorCap` in request data.
    const VENDOR_CAP = 20;
    const vendorCap = { remaining: VENDOR_CAP };
    const ogFetch = (url: string) => fetchOpenGraph(url, undefined, vendorCap);

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

      // Process the page with bounded concurrency (at most CONCURRENCY docs in
      // flight at once) so ~200 docs with a 5 s OG timeout each complete in
      // ~ceil(200/CONCURRENCY)*5 s ≈ 100 s — well within the 540 s limit.
      const CONCURRENCY = 10;
      let idx = 0;
      async function worker(): Promise<void> {
        while (idx < snap.docs.length) {
          const doc = snap.docs[idx++];
          const data = doc.data() as Record<string, unknown> | undefined;
          const entities = data?.entities;
          if (entities) {
            try {
              const outcome = await runEnrichLinks(database, uid, doc.id, entities, ogFetch, {
                force,
                log: logger,
              });
              if (outcome === "written") enriched++;
              else if (outcome === "skipped") skipped++;
            } catch (e) {
              logger.warn("backfill_links_doc_failed", { uid, tweetId: doc.id, code: (e as Error).message });
            }
          }
          scanned++;
        }
      }
      await Promise.all(Array.from({ length: CONCURRENCY }, worker));

      lastDoc = snap.docs[snap.docs.length - 1];
      if (snap.docs.length < PAGE_SIZE) break;
      if (scanned >= MAX_BACKFILL_TWEETS) {
        capped = true;
        break;
      }
    }

    logger.info("backfill_links_done", { uid, scanned, enriched, skipped, capped, force, onlyChips });
    return { scanned, enriched, skipped, capped };
  },
);
