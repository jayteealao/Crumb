import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import type { QueryDocumentSnapshot } from "firebase-admin/firestore";

/**
 * One-shot media-attribution repair over the EXISTING tweet corpus. The poll fix
 * ([runPoll] attributing media by each tweet's own `attachments.media_keys`) only
 * corrects bookmarks written after it deploys; this callable rewrites the
 * back-catalogue's `includes/{tweetId}_media_{mediaKey}` junction docs so already
 * stored bookmarks (and their quoted bodies) carry the correct tweet↔media pairing.
 *
 * @remarks
 * Self-scoped (least-privilege): a caller repairs only their OWN
 * `users/{uid}/tweets` + `users/{uid}/includes` sub-collections — no cross-user
 * admin mode. Like {@link backfillTweetLinks} (and unlike the quoted-body backfill)
 * the authoritative source is already on the stored tweet doc — the
 * `attachments.media_keys` field spread on by the poll — so this spends **no X API
 * quota**. For each tweet it re-derives the correct owned-key set, writes any
 * missing junction doc (`merge`), and **deletes** every existing media junction
 * whose `media_key` the tweet does not own (the page-level cross-product the poll
 * fix stopped producing). Idempotent (a second run finds nothing to change) and
 * bounded by {@link MAX_BACKFILL_TWEETS}; a cap hit is logged, not silently
 * truncated. The media docs themselves (`users/{uid}/media/{mediaKey}`) are keyed
 * globally by media_key and are already correct — only the junctions are rewritten.
 *
 * @param request - callable request; `request.data` is ignored. `request.auth`
 *   must be present.
 * @returns `{ scanned, rewritten, deleted, capped, failed }` tallies for the sweep.
 * @throws {HttpsError} `"unauthenticated"` when `request.auth` is absent.
 */
const PAGE_SIZE = 200;
const MAX_BACKFILL_TWEETS = 5_000;
// Flush-and-reset the batch when ops reach this ceiling (Firestore hard cap is
// 500; 450 leaves headroom matching the BATCH_SIZE constant in lib/poll.ts).
const BATCH_LIMIT = 450;

export const backfillTweetMedia = onCall(
  { region: "europe-west2", timeoutSeconds: 540, memory: "512MiB" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in required");
    }
    const uid = request.auth.uid;

    const { db } = await import("../lib/admin");
    const { FieldPath } = await import("firebase-admin/firestore");
    const { assertValidUid } = await import("../lib/uid");
    const { ownedMediaKeys, mediaJunctionData, mediaJunctionDocId } = await import(
      "../lib/media-attribution"
    );

    // M2: validate uid before any Firestore access, mirroring poll.ts / backfillLinks.
    assertValidUid(uid);

    const database = db();
    const tweetsCol = database.collection(`users/${uid}/tweets`);
    const includesPath = `users/${uid}/includes`;
    const includesCol = database.collection(includesPath);

    let scanned = 0;
    let rewritten = 0; // missing owned junctions written (committed)
    let deleted = 0;   // wrong (cross-product) junctions removed (committed)
    let failed = 0;    // H2(c): per-tweet errors that were swallowed
    let lastDoc: QueryDocumentSnapshot | undefined;
    let capped = false;

    while (scanned < MAX_BACKFILL_TWEETS) {
      let query = tweetsCol.orderBy(FieldPath.documentId()).limit(PAGE_SIZE);
      if (lastDoc) query = query.startAfter(lastDoc);

      // H2(a): wrap the page-level fetch so a transient Firestore error stops
      // pagination gracefully rather than throwing out of the whole callable.
      let snap: Awaited<ReturnType<typeof query.get>>;
      try {
        snap = await query.get();
      } catch (e) {
        logger.warn("backfill_media_page_failed", {
          uid,
          scanned,
          code: (e as Error).message,
        });
        // Return what we have so far; mark incomplete via capped=false (the
        // caller can inspect `failed` to see it's partial).
        break;
      }
      if (snap.empty) break;

      // Process the page with bounded concurrency (mirrors backfillTweetLinks): each
      // tweet does one junction query + at most one batch commit, so ~200 tweets
      // complete well within the 540 s budget.
      const CONCURRENCY = 10;
      let idx = 0;
      async function worker(): Promise<void> {
        while (idx < snap.docs.length) {
          const doc = snap.docs[idx++];
          scanned++;
          try {
            const data = doc.data() as Record<string, unknown> | undefined;
            // The tweet's OWN media keys — the only correct attribution (includes
            // quoted bodies, which carry their own attachments.media_keys).
            const owned = new Set(ownedMediaKeys({ attachments: data?.attachments }));

            // M3: Identify media junction docs by their id suffix pattern
            // (`_media_<mediaKey>`, the scheme used by mediaJunctionDocId) rather
            // than relying solely on the `kind` field.  Legacy junction docs written
            // without a `kind` field would otherwise be invisible to this repair and
            // stay wrong-attributed.  We query by tweetId only and then filter in
            // application code: keep a doc only if its id matches the
            // `{tweetId}_media_` prefix OR its kind == "media".  Non-media junctions
            // (kind == "user", "referenced_tweet", etc.) whose ids don't start with
            // the media prefix are explicitly skipped so they're never touched.
            //
            // Note: the composite index on (tweetId, kind) is added in
            // firestore.indexes.json; here we query by tweetId only (single-field
            // index, no composite needed) so legacy kind-less docs surface too.
            const mediaPrefix = `${doc.id}_media_`;
            const existing = await includesCol
              .where("tweetId", "==", doc.id)
              .get();

            const present = new Set<string>();
            let batch = database.batch();
            let ops = 0;

            // Helper: flush the current batch and reset, adding committed counts.
            // H2(d): accumulate pending counts per batch and add to totals only
            // after a successful commit.
            let pendingDeleted = 0;
            let pendingRewritten = 0;
            const flushBatch = async (): Promise<void> => {
              if (ops === 0) return;
              await batch.commit();
              deleted += pendingDeleted;
              rewritten += pendingRewritten;
              batch = database.batch();
              ops = 0;
              pendingDeleted = 0;
              pendingRewritten = 0;
            };

            for (const j of existing.docs) {
              const jData = j.data() as Record<string, unknown>;
              const isMediaJunction =
                j.id.startsWith(mediaPrefix) || jData.kind === "media";
              if (!isMediaJunction) continue; // non-media junction — skip

              const mk = jData.mediaKey as string | undefined;
              if (typeof mk === "string" && owned.has(mk)) {
                present.add(mk); // correct attribution — keep
              } else {
                // Wrong attribution (a co-page / quoted tweet's media) or malformed —
                // delete it. This is the cross-product the poll fix stopped writing.
                batch.delete(database.doc(`${includesPath}/${j.id}`));
                ops++;
                pendingDeleted++;
                // H2(b): flush before hitting Firestore's 500-op hard cap.
                if (ops >= BATCH_LIMIT) await flushBatch();
              }
            }

            // Write any owned junction that is missing (e.g. a quoted tweet whose own
            // media was never linked, or a tweet whose junction was deleted as wrong
            // under the old attribution before this owner was processed).
            for (const mk of owned) {
              if (present.has(mk)) continue;
              batch.set(
                database.doc(`${includesPath}/${mediaJunctionDocId(doc.id, mk)}`),
                mediaJunctionData(doc.id, mk),
                { merge: true },
              );
              ops++;
              pendingRewritten++;
              // H2(b): flush before hitting Firestore's 500-op hard cap.
              if (ops >= BATCH_LIMIT) await flushBatch();
            }

            // Flush remaining ops. H2(d): tallies are incremented inside flushBatch
            // AFTER commit succeeds.
            await flushBatch();
          } catch (e) {
            // H2(c): count swallowed per-tweet errors so a partial repair is
            // observable at the API boundary via the `failed` counter.
            failed++;
            logger.warn("backfill_media_doc_failed", {
              uid,
              tweetId: doc.id,
              code: (e as Error).message,
            });
          }
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

    logger.info("backfill_media_done", { uid, scanned, rewritten, deleted, failed, capped });
    return { scanned, rewritten, deleted, failed, capped };
  },
);
