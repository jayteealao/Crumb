import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import type { QueryDocumentSnapshot } from "firebase-admin/firestore";

/**
 * One-shot quoted-tweet backfill over the EXISTING bookmark corpus. The poll
 * function only writes a quoted tweet's body for bookmarks collected after the
 * write loop was deployed; this callable repairs the back-catalogue so already
 * stored bookmarks that quote another tweet also gain a rendered quote.
 *
 * @remarks
 * Self-scoped (least-privilege): a caller backfills only their OWN
 * `users/{uid}/tweets` sub-collection. Unlike the link backfill (whose data is
 * already on the stored doc), the quoted bodies are not in Firestore yet, so this
 * spends the user's X API quota — it re-fetches each missing quoted body via
 * `GET /2/tweets?ids=`. It is therefore gated behind an explicit invocation
 * (never auto-run) and skips quotes whose body doc already exists. Idempotent and
 * bounded ({@link MAX_BACKFILL_TWEETS}); a cap hit is logged, not silently
 * truncated.
 *
 * @param request - callable request; `request.data` is ignored. `request.auth`
 *   must be present and the caller's X account must be linked.
 * @returns tallies for the sweep.
 * @throws {HttpsError} `"unauthenticated"` / `"failed-precondition"` / `"unavailable"`.
 */
const PAGE_SIZE = 200;
const MAX_BACKFILL_TWEETS = 5_000;
const X_IDS_CHUNK = 100; // GET /2/tweets?ids= hard cap.
const EXISTS_CHUNK = 30; // Firestore documentId "in" cap.

export const backfillQuotedTweets = onCall(
  { region: "europe-west2", timeoutSeconds: 540, memory: "512MiB" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign-in required");
    }
    const uid = request.auth.uid;

    const { db } = await import("../lib/admin");
    const { FieldValue, FieldPath } = await import("firebase-admin/firestore");
    const { getRefreshToken, getXClientCredentials } = await import("../lib/secrets");
    const { TOKEN_URL, X_API_BASE, TWEETFIELDS, USERFIELDS } = await import("../lib/twitter-api");
    const { normalizeCreatedAt } = await import("../lib/tweet-utils");

    const database = db();

    // 1. Mint an access token from the user's stored refresh token (same exchange
    // as runPoll). We deliberately do NOT persist a rotated refresh token here:
    // X v2 refresh tokens are single-use, and the poll owns rotation — this
    // one-shot is meant to run alongside / just after a poll.
    const storedRt = await getRefreshToken(uid);
    if (!storedRt) {
      throw new HttpsError("failed-precondition", "X account not linked");
    }
    const { clientId, clientSecret } = await getXClientCredentials();
    const basicAuth = Buffer.from(`${clientId}:${clientSecret}`, "utf8").toString("base64");
    const refreshResp = await fetch(TOKEN_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        Authorization: `Basic ${basicAuth}`,
      },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        refresh_token: storedRt,
        client_id: clientId,
      }),
    });
    if (!refreshResp.ok) {
      throw new HttpsError("unavailable", "Could not refresh X token");
    }
    const accessToken = ((await refreshResp.json()) as { access_token: string }).access_token;

    // 2. Page the user's own bookmarks, collecting quoted reference ids. The raw
    // `referenced_tweets:[{id,type}]` array is preserved on the stored tweet doc
    // (the poll's `...tweetWithoutMetrics` spread), so no extra read is needed.
    const tweetsCol = database.collection(`users/${uid}/tweets`);
    const quotedIds = new Set<string>();
    let scanned = 0;
    let lastDoc: QueryDocumentSnapshot | undefined;
    let capped = false;

    while (scanned < MAX_BACKFILL_TWEETS) {
      let query = tweetsCol.orderBy(FieldPath.documentId()).limit(PAGE_SIZE);
      if (lastDoc) query = query.startAfter(lastDoc);
      const snap = await query.get();
      if (snap.empty) break;

      for (const doc of snap.docs) {
        const data = doc.data() as Record<string, unknown>;
        // A quoted body doc (referenced=true) is not a bookmark — don't scan it.
        if (data.referenced !== true) {
          const refs =
            (data.referenced_tweets as Array<{ id?: string; type?: string }> | undefined) ?? [];
          for (const r of refs) {
            if (r.type === "quoted" && r.id) quotedIds.add(r.id);
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

    // 3. Drop quoted ids whose body doc already exists (don't re-spend X quota).
    const missing: string[] = [];
    const idList = [...quotedIds];
    for (let i = 0; i < idList.length; i += EXISTS_CHUNK) {
      const chunk = idList.slice(i, i + EXISTS_CHUNK);
      const existsSnap = await tweetsCol
        .where(FieldPath.documentId(), "in", chunk)
        .select()
        .get();
      const present = new Set(existsSnap.docs.map((d) => d.id));
      for (const id of chunk) {
        if (!present.has(id)) missing.push(id);
      }
    }

    // 4. X-fetch the missing bodies in 100-id chunks; write each as referenced=true
    // + its author. `merge:true` keeps it idempotent and safe to re-run.
    let written = 0;
    for (let i = 0; i < missing.length; i += X_IDS_CHUNK) {
      const chunk = missing.slice(i, i + X_IDS_CHUNK);
      const url =
        `${X_API_BASE}/tweets?` +
        new URLSearchParams({
          ids: chunk.join(","),
          "tweet.fields": TWEETFIELDS,
          expansions: "author_id",
          "user.fields": USERFIELDS,
        }).toString();
      const resp = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } });
      if (!resp.ok) {
        logger.warn("backfill_quotes_chunk_failed", { uid, status: resp.status });
        continue;
      }
      const json = (await resp.json()) as {
        data?: Array<Record<string, unknown>>;
        includes?: { users?: Array<Record<string, unknown>> };
      };
      const batch = database.batch();
      for (const qt of json.data ?? []) {
        const id = qt.id as string;
        batch.set(
          database.doc(`users/${uid}/tweets/${id}`),
          {
            ...qt,
            tweetId: id,
            authorId: qt.author_id,
            createdAt: normalizeCreatedAt(qt.created_at),
            conversationId: qt.conversation_id,
            inReplyToUserId: qt.in_reply_to_user_id,
            referenced: true,
            pending_delete: false,
            updatedAt: FieldValue.serverTimestamp(),
          },
          { merge: true },
        );
        written++;
      }
      for (const u of json.includes?.users ?? []) {
        const ur = u as Record<string, unknown>;
        batch.set(
          database.doc(`users/${uid}/twitter_users/${u.id}`),
          {
            ...ur,
            userId: u.id,
            profileImageUrl: ur.profile_image_url,
            verifiedType: ur.verified_type,
            updatedAt: FieldValue.serverTimestamp(),
          },
          { merge: true },
        );
      }
      await batch.commit();
    }

    logger.info("backfill_quotes_done", {
      uid,
      scanned,
      candidates: quotedIds.size,
      missing: missing.length,
      written,
      capped,
    });
    return { scanned, candidates: quotedIds.size, missing: missing.length, written, capped };
  },
);
