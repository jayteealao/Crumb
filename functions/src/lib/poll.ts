// Shared X-bookmarks poll engine. Invoked by:
//   - handlers/dailyPoll.ts  (onSchedule, twice daily UTC)
//   - handlers/triggerPoll.ts (onCall, on-demand)
//
// Invariants:
//   - Every Firestore write goes through a path guard that asserts the doc path
//     starts with `users/${uid}/`. A guard violation throws before any write.
//   - The poll-lease + lastPolledAt write is in a `finally` block: even on
//     uncaught exception, the lease is released so the next caller does not
//     have to wait for the 30s TTL.
//   - The refresh-token is persisted via `setRefreshToken` ONLY when X returns
//     a rotated token (`response.refresh_token !== storedRt`). X v2 refresh
//     tokens are single-use in 2026, so a missed rotation breaks the user.
//   - Firestore batched writes are capped at 450 ops per commit (Firestore hard
//     cap is 500; 450 leaves headroom).
//   - X bookmarks request is locked to max_results=50 because max_results=100
//     has an active pagination bug (see lib/twitter-api.ts).

import { logger } from "firebase-functions/v2";
import { FieldValue, FieldPath, Timestamp } from "firebase-admin/firestore";
import type { DocumentReference, WriteBatch, Firestore } from "firebase-admin/firestore";

import { db } from "./admin";
import { getRefreshToken, setRefreshToken, getXClientCredentials } from "./secrets";
import { buildBookmarksUrl, USERS_ME_URL, TOKEN_URL } from "./twitter-api";
import { normalizeCreatedAt } from "./tweet-utils";

const BATCH_SIZE = 450;
const DEBOUNCE_MS = 60_000;
const LEASE_TTL_MS = 30_000;
const MAX_RETRIES = 3;
const BACKOFF_BASE_MS = 1_000;
const MAX_BACKOFF_WAIT_MS = 60_000;

export type PollFailureReason =
  | "refresh_revoked"
  | "rate_limited"
  | "no_refresh_token"
  | "missing_x_user_id"
  | "x_user_lookup_failed"
  | "in_progress"
  | "debounced";

export type PollResult =
  | { ok: true; itemsAdded: number; itemsFlaggedPendingDelete: number }
  | { ok: false; reason: PollFailureReason; retryAfter?: number };

export interface PollOptions {
  reason?: "scheduled" | "trigger";
}

interface TweetData {
  id: string;
  public_metrics?: Record<string, unknown>;
  referenced_tweets?: Array<{ id: string; type: string }>;
  entities?: { annotations?: Array<{ type: string; start: number; end: number; normalized_text?: string }> };
  [key: string]: unknown;
}

interface IncludesData {
  users?: Array<{ id: string; [key: string]: unknown }>;
  media?: Array<{ media_key: string; [key: string]: unknown }>;
  tweets?: Array<{ id: string; [key: string]: unknown }>;
}

interface BookmarksPage {
  data?: TweetData[];
  includes?: IncludesData;
  meta?: { next_token?: string };
}

interface LeaseState {
  holder: string;
  acquired_at: Timestamp;
  expires_at: Timestamp;
}

interface SyncStatusData {
  linked?: boolean;
  lastPolledAt?: Timestamp;
  lastError?: string | null;
  xUserId?: string;
  // BigInt-string of the numerically-largest stored tweet id. Seeded by
  // scripts/backfill-tweet-id-field.mjs and maintained by runPoll's success
  // path. Used as the stop-on-overlap boundary; replaces the broken
  // `orderBy("id", "desc").limit(1)` which compared snowflakes lexicographically.
  latest_tweet_id?: string;
  poll_lease?: LeaseState | null;
  [key: string]: unknown;
}

function assertPathScoped(path: string, uid: string): void {
  const prefix = `users/${uid}/`;
  if (!path.startsWith(prefix)) {
    throw new Error(`path_guard_violation: ${path}`);
  }
}

// Numeric-snowflake comparison helpers. In production every tweet id is a
// valid u64 snowflake string, so BigInt conversion succeeds. Test fixtures
// use synthetic ids like `T1` that are not numeric — fall back to string
// equality (NOT lex `<`/`>`, which is the original defect for mixed-length
// snowflakes).
function isAtOrBelowBoundary(id: string, boundary: string): boolean {
  try {
    return BigInt(id) <= BigInt(boundary);
  } catch {
    return id === boundary;
  }
}

function isStrictlyAboveBoundary(id: string, boundary: string): boolean {
  try {
    return BigInt(id) > BigInt(boundary);
  } catch {
    return false;
  }
}

function assertValidUid(uid: string): void {
  // Defensive: prevent traversal-style paths in the doc path.
  if (!uid || uid.includes("/") || uid.includes("..")) {
    throw new Error(`invalid_uid: ${uid}`);
  }
}

async function fetchWithBackoff(
  url: string,
  accessToken: string,
): Promise<Response | null> {
  for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
    const resp = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } });
    if (resp.status !== 429 && resp.status < 500) return resp;
    if (attempt === MAX_RETRIES) return null;
    const resetHeader = resp.headers.get("x-rate-limit-reset");
    let waitMs: number;
    if (resetHeader) {
      const resetUnix = Number(resetHeader);
      if (Number.isFinite(resetUnix)) {
        waitMs = Math.max(0, resetUnix * 1000 - Date.now() + 1_000);
      } else {
        waitMs = BACKOFF_BASE_MS * Math.pow(2, attempt);
      }
    } else {
      waitMs = BACKOFF_BASE_MS * Math.pow(2, attempt);
    }
    const capped = Math.min(waitMs, MAX_BACKOFF_WAIT_MS);
    logger.warn("daily_poll_backoff", { url, attempt, status: resp.status, waitMs: capped });
    await new Promise((r) => setTimeout(r, capped));
  }
  return null;
}

async function releaseLeaseWithError(
  database: Firestore,
  uid: string,
  errorCode: string,
  opts: { setUnlinked?: boolean } = {},
): Promise<void> {
  const statusRef = database.doc(`users/${uid}/sync_status/state`);
  assertPathScoped(statusRef.path, uid);
  const patch: Record<string, unknown> = {
    poll_lease: null,
    lastError: errorCode,
    updatedAt: FieldValue.serverTimestamp(),
  };
  if (opts.setUnlinked) {
    patch.linked = false;
  }
  await statusRef.set(patch, { merge: true });
}

export async function runPoll(uid: string, opts: PollOptions = {}): Promise<PollResult> {
  assertValidUid(uid);
  const database = db();
  const statusRef = database.doc(`users/${uid}/sync_status/state`);
  assertPathScoped(statusRef.path, uid);

  // Sub-step 4a: lease + debounce transaction.
  type ClaimResult =
    | { kind: "debounced"; retryAfter: number }
    | { kind: "in_progress" }
    | { kind: "acquired"; holder: string };

  const claim: ClaimResult = await database.runTransaction(async (tx) => {
    const snap = await tx.get(statusRef);
    const data = (snap.data() ?? {}) as SyncStatusData;
    const now = Timestamp.now();

    if (opts.reason === "trigger" && data.lastPolledAt) {
      const elapsedMs = now.toMillis() - data.lastPolledAt.toMillis();
      if (elapsedMs < DEBOUNCE_MS) {
        return {
          kind: "debounced" as const,
          retryAfter: Math.ceil((DEBOUNCE_MS - elapsedMs) / 1000),
        };
      }
    }

    const lease = data.poll_lease;
    if (lease && lease.expires_at && lease.expires_at.toMillis() > now.toMillis()) {
      return { kind: "in_progress" as const };
    }

    const holder = `${opts.reason ?? "unknown"}_${now.toMillis()}_${Math.random().toString(36).slice(2, 10)}`;
    tx.set(
      statusRef,
      {
        poll_lease: {
          holder,
          acquired_at: now,
          expires_at: Timestamp.fromMillis(now.toMillis() + LEASE_TTL_MS),
        },
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );
    return { kind: "acquired" as const, holder };
  });

  if (claim.kind === "debounced") {
    return { ok: false, reason: "debounced", retryAfter: claim.retryAfter };
  }
  if (claim.kind === "in_progress") {
    return { ok: false, reason: "in_progress" };
  }

  let collectedCount = 0;
  let flaggedCount = 0;
  let resolvedXUserId: string | undefined;
  let pollFailed: PollFailureReason | null = null;
  let newLatestTweetId: string | undefined;

  try {
    // Sub-step 4b: refresh-token grant + conditional rotation persist.
    const storedRt = await getRefreshToken(uid);
    if (!storedRt) {
      pollFailed = "no_refresh_token";
      logger.warn("daily_poll_no_refresh_token", { uid });
      return { ok: false, reason: "no_refresh_token" };
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
      const body = (await refreshResp.json().catch(() => ({}))) as { error?: string };
      const code = body.error ?? `http_${refreshResp.status}`;
      if (code === "invalid_grant") {
        pollFailed = "refresh_revoked";
        logger.warn("daily_poll_refresh_revoked", { uid });
        return { ok: false, reason: "refresh_revoked" };
      }
      pollFailed = "rate_limited";
      logger.warn("daily_poll_refresh_failed", { uid, code });
      return { ok: false, reason: "rate_limited" };
    }

    const tokens = (await refreshResp.json()) as {
      access_token: string;
      refresh_token?: string;
    };
    const accessToken = tokens.access_token;

    // Conditional rotation persist (PO Round 1 Q1).
    if (tokens.refresh_token && tokens.refresh_token !== storedRt) {
      await setRefreshToken(uid, tokens.refresh_token);
      logger.info("daily_poll_rt_rotated", { uid });
    }

    // Sub-step 4c: X user-id lookup with cache.
    const statusSnap = await statusRef.get();
    const currentStatus = (statusSnap.data() ?? {}) as SyncStatusData;
    let xUserId = currentStatus.xUserId;
    if (!xUserId) {
      const meResp = await fetchWithBackoff(USERS_ME_URL, accessToken);
      if (!meResp || !meResp.ok) {
        pollFailed = "x_user_lookup_failed";
        logger.warn("daily_poll_users_me_failed", { uid, status: meResp?.status });
        return { ok: false, reason: "x_user_lookup_failed" };
      }
      const me = (await meResp.json()) as { data?: { id?: string } };
      if (!me.data?.id) {
        pollFailed = "missing_x_user_id";
        logger.warn("daily_poll_users_me_empty", { uid });
        return { ok: false, reason: "missing_x_user_id" };
      }
      xUserId = me.data.id;
    }
    resolvedXUserId = xUserId;

    // Sub-step 4d: paginated bookmark fetch with stop-on-overlap.
    //
    // `latest_tweet_id` is the BigInt-max-string of stored tweet ids — seeded
    // by scripts/backfill-tweet-id-field.mjs and re-written by this function's
    // success-path finally patch (see `newLatestTweetId` below). It replaces
    // an earlier `orderBy("id", "desc").limit(1)` query that compared snowflake
    // strings lexicographically (2017-era 18-char ids sorted AFTER 2024+
    // 19-char ids because `'9' > '2'` at position 0), which caused
    // stop-on-overlap to mis-fire and the function to silently drop work.
    const latestIdInDb: string | undefined = currentStatus.latest_tweet_id;

    const collected: Array<{ tweet: TweetData; includes?: IncludesData }> = [];
    const seenIds = new Set<string>();
    let nextToken: string | undefined;
    let stop = false;
    let stoppedOnOverlap = false;
    do {
      const url = buildBookmarksUrl(xUserId, nextToken);
      const resp = await fetchWithBackoff(url, accessToken);
      if (!resp) {
        pollFailed = "rate_limited";
        logger.warn("daily_poll_bookmarks_rate_limited", { uid });
        return { ok: false, reason: "rate_limited" };
      }
      if (!resp.ok) {
        pollFailed = "rate_limited";
        logger.warn("daily_poll_bookmarks_failed", { uid, status: resp.status });
        return { ok: false, reason: "rate_limited" };
      }
      const json = (await resp.json()) as BookmarksPage;
      const page = json.data ?? [];
      for (const tweet of page) {
        // BigInt comparison: snowflake ids are variable-length numeric strings;
        // lexicographic compare misclassifies 18-char (2017-era) vs 19-char
        // (2024+) ids. `<=` includes the boundary tweet itself as still-present.
        if (latestIdInDb && isAtOrBelowBoundary(tweet.id, latestIdInDb)) {
          // Boundary (or below) IS still present in X — record as seen but do
          // not re-write the payload. Anything strictly below the boundary
          // was not paged in this poll; its presence remains unknown.
          seenIds.add(tweet.id);
          stoppedOnOverlap = true;
          stop = true;
          break;
        }
        seenIds.add(tweet.id);
        collected.push({ tweet, includes: json.includes });
      }
      nextToken = json.meta?.next_token;
    } while (!stop && nextToken);

    collectedCount = collected.length;

    // Sub-step 4f: batched writes with content-derived composite IDs.
    const writes: Array<[DocumentReference, Record<string, unknown>]> = [];
    const seenRefs = new Set<string>();

    function enqueue(ref: DocumentReference, data: Record<string, unknown>): void {
      assertPathScoped(ref.path, uid);
      if (seenRefs.has(ref.path)) return;
      seenRefs.add(ref.path);
      writes.push([ref, data]);
    }

    for (const { tweet, includes } of collected) {
      const { public_metrics, ...tweetWithoutMetrics } = tweet;
      // Tweet doc: spread the raw X-API snake_case payload first (preserves
      // every field for future readers), then overlay camelCase aliases the
      // Android FirestoreTweet / FirestoreRepository.getAllTweetIds reader
      // requires. Without these, getAllTweetIds() returns 0 ids and the
      // device's Room cache never hydrates from server-written docs.
      const tweetDoc: Record<string, unknown> = {
        ...tweetWithoutMetrics,
        tweetId: tweet.id,
        authorId: (tweet as Record<string, unknown>).author_id,
        // Normalize the camelCase createdAt the Android reader and orderBy("createdAt")
        // depend on to canonical ISO-8601; the raw snake_case created_at from the spread
        // above is left verbatim for future readers.
        createdAt: normalizeCreatedAt((tweet as Record<string, unknown>).created_at),
        conversationId: (tweet as Record<string, unknown>).conversation_id,
        inReplyToUserId: (tweet as Record<string, unknown>).in_reply_to_user_id,
        pending_delete: false,
        updatedAt: FieldValue.serverTimestamp(),
        // First-seen / poll observation time. The X v2 bookmarks API exposes no true
        // bookmark timestamp, so this stamps when the poll first collected the tweet. Only
        // tweets in `collected` reach this loop (ids strictly above the stored boundary),
        // so it is written once and never overwritten on later polls.
        retrievedAt: FieldValue.serverTimestamp(),
      };
      enqueue(database.doc(`users/${uid}/tweets/${tweet.id}`), tweetDoc);

      if (public_metrics && typeof public_metrics === "object") {
        // Metrics doc: same shape contract — snake_case raw + camelCase aliases
        // the Android FirestoreMetrics reader expects (likeCount, retweetCount,
        // replyCount, quoteCount, bookmarkCount, impressionCount).
        const pm = public_metrics as Record<string, unknown>;
        enqueue(database.doc(`users/${uid}/metrics/${tweet.id}`), {
          ...pm,
          tweetId: tweet.id,
          likeCount: pm.like_count,
          retweetCount: pm.retweet_count,
          replyCount: pm.reply_count,
          quoteCount: pm.quote_count,
          bookmarkCount: pm.bookmark_count,
          impressionCount: pm.impression_count,
          updatedAt: FieldValue.serverTimestamp(),
        });
      }

      const includesUsers = includes?.users ?? [];
      for (const u of includesUsers) {
        // User doc: snake_case raw + camelCase aliases for Android FirestoreUser
        // (userId, profileImageUrl, verifiedType, followersCount, etc.).
        const ur = u as Record<string, unknown>;
        const userPublicMetrics =
          (ur.public_metrics as Record<string, unknown> | undefined) ?? {};
        enqueue(database.doc(`users/${uid}/twitter_users/${u.id}`), {
          ...ur,
          userId: u.id,
          profileImageUrl: ur.profile_image_url,
          verifiedType: ur.verified_type,
          pinnedTweetId: ur.pinned_tweet_id,
          createdAt: ur.created_at,
          followersCount: userPublicMetrics.followers_count,
          followingCount: userPublicMetrics.following_count,
          tweetCount: userPublicMetrics.tweet_count,
          listedCount: userPublicMetrics.listed_count,
          updatedAt: FieldValue.serverTimestamp(),
        });
        enqueue(database.doc(`users/${uid}/includes/${tweet.id}_user_${u.id}`), {
          tweetId: tweet.id,
          userId: u.id,
          kind: "user",
        });
      }

      const includesMedia = includes?.media ?? [];
      for (const m of includesMedia) {
        // Media doc: snake_case raw + camelCase aliases for Android
        // FirestoreMedia (mediaKey, previewImageUrl, durationMs, altText).
        const mr = m as Record<string, unknown>;
        // Defensive, explicit video-variant mapping (HLS/DASH/progressive stream URLs
        // the Android inline player needs). The raw `...mr` spread already carries a
        // snake_case `variants` array, but we re-map it to the canonical camelCase shape
        // ({bitRate, contentType, url}) AFTER the spread so the field is typed and
        // overrides the raw one — the Android FirestoreMedia reader prefers camelCase.
        // `bit_rate` is absent on adaptive (HLS/DASH) variants, so it defaults to 0.
        const rawVariants =
          (mr.variants as Array<Record<string, unknown>> | undefined) ?? [];
        const variants = rawVariants.map((v) => ({
          bitRate: typeof v.bit_rate === "number" ? v.bit_rate : 0,
          contentType: v.content_type,
          url: v.url,
        }));
        enqueue(database.doc(`users/${uid}/media/${m.media_key}`), {
          ...mr,
          mediaKey: m.media_key,
          previewImageUrl: mr.preview_image_url,
          durationMs: mr.duration_ms,
          altText: mr.alt_text,
          variants,
          updatedAt: FieldValue.serverTimestamp(),
        });
        enqueue(database.doc(`users/${uid}/includes/${tweet.id}_media_${m.media_key}`), {
          tweetId: tweet.id,
          mediaKey: m.media_key,
          kind: "media",
        });
      }

      const refs = tweet.referenced_tweets ?? [];
      for (const r of refs) {
        enqueue(database.doc(`users/${uid}/includes/${tweet.id}_ref_${r.id}`), {
          tweetId: tweet.id,
          referencedId: r.id,
          referencedTweetId: r.id,
          type: r.type,
          kind: "referenced_tweet",
        });
      }

      const annotations = tweet.entities?.annotations ?? [];
      for (const ann of annotations) {
        enqueue(
          database.doc(
            `users/${uid}/textAnnotations/${tweet.id}_${ann.type}_${ann.start}_${ann.end}`,
          ),
          { tweetId: tweet.id, ...ann },
        );
      }
    }

    // Commit writes in 450-op chunks.
    for (let i = 0; i < writes.length; i += BATCH_SIZE) {
      const batch: WriteBatch = database.batch();
      const chunk = writes.slice(i, i + BATCH_SIZE);
      for (const [ref, data] of chunk) {
        batch.set(ref, data, { merge: true });
      }
      await batch.commit();
    }

    // Sub-step 4g: pending_delete diff.
    //
    // Semantics: a stored tweet is "still present" if X echoed it in this poll's
    // response stream (either collected as new OR encountered as the stop-on-overlap
    // boundary). When stop-on-overlap fires, anything stored BELOW the boundary
    // (older snowflake id) was not examined this poll — its presence is unknown
    // and we MUST NOT flag it. Only tweets strictly above the boundary that are
    // both stored and unseen this poll get the pending_delete flag.
    //
    // When pagination completes naturally (no overlap), the response stream
    // covers everything X has → unseen stored ids are safely flaggable.
    const existingIdsSnap = await database
      .collection(`users/${uid}/tweets`)
      .select()
      .get();
    const existingIds = new Set(existingIdsSnap.docs.map((d) => d.id));
    const missingNow: string[] = [];
    for (const id of existingIds) {
      if (seenIds.has(id)) continue;
      if (stoppedOnOverlap) {
        // Only flag if strictly above the overlap boundary. BigInt compare
        // for the same reason as the overlap check above.
        if (latestIdInDb && isStrictlyAboveBoundary(id, latestIdInDb)) {
          missingNow.push(id);
        }
      } else {
        missingNow.push(id);
      }
    }

    if (missingNow.length > 0) {
      // Read the `deleted` precondition for every candidate via chunked `in`
      // queries — 30 ids per query is the Firestore documentId-in cap. One
      // RPC per chunk; up to 30 docs returned. Replaces a serial loop of
      // 3,000+ individual docRef.get() calls.
      const IN_CHUNK = 30;
      const idChunks: string[][] = [];
      for (let i = 0; i < missingNow.length; i += IN_CHUNK) {
        idChunks.push(missingNow.slice(i, i + IN_CHUNK));
      }
      const snaps = await Promise.all(
        idChunks.map((ids) =>
          database
            .collection(`users/${uid}/tweets`)
            .where(FieldPath.documentId(), "in", ids)
            .select("deleted")
            .get(),
        ),
      );
      const deletedSet = new Set<string>();
      for (const snap of snaps) {
        for (const d of snap.docs) {
          if ((d.data() as Record<string, unknown>)?.deleted === true) {
            deletedSet.add(d.id);
          }
        }
      }

      const pdWrites: Array<[DocumentReference, Record<string, unknown>]> = [];
      for (const id of missingNow) {
        if (deletedSet.has(id)) continue;
        const docRef = database.doc(`users/${uid}/tweets/${id}`);
        assertPathScoped(docRef.path, uid);
        pdWrites.push([
          docRef,
          {
            pending_delete: true,
            pending_delete_detected_at: Timestamp.now(),
            updatedAt: FieldValue.serverTimestamp(),
          },
        ]);
      }

      // Same 450-chunk commit pattern as the collection-write loop above.
      // Hard cap is 500 ops per batch; 450 leaves headroom.
      for (let i = 0; i < pdWrites.length; i += BATCH_SIZE) {
        const batch: WriteBatch = database.batch();
        const chunk = pdWrites.slice(i, i + BATCH_SIZE);
        for (const [ref, data] of chunk) {
          batch.set(ref, data, { merge: true });
        }
        await batch.commit();
      }
      flaggedCount = pdWrites.length;
    }

    // Compute new BigInt-max boundary for the success-path patch (used in
    // finally below). Newer collected ids supersede the existing cache.
    // Wrapped in try/catch: defensive against non-numeric ids (test fixtures
    // use synthetic strings; production snowflakes are always numeric).
    if (collected.length > 0) {
      try {
        const base = latestIdInDb ? BigInt(latestIdInDb) : 0n;
        const newMax = collected.reduce(
          (max, { tweet }) => (BigInt(tweet.id) > max ? BigInt(tweet.id) : max),
          base,
        );
        if (newMax > 0n && (!latestIdInDb || newMax > BigInt(latestIdInDb))) {
          newLatestTweetId = newMax.toString();
        }
      } catch {
        // Non-numeric id — skip cache update; the existing cache stays.
      }
    }

    return {
      ok: true,
      itemsAdded: collectedCount,
      itemsFlaggedPendingDelete: flaggedCount,
    };
  } finally {
    // Sub-step 4h: release lease + write sync_status (always runs).
    //
    // Observability: the firebase-functions logger buffers async, so on Gen 2
    // post-response CPU throttling its log lines can be lost. Wrap the work
    // in a 5s Promise.race timeout, then emit BOTH the firebase logger line
    // (for normal cases) AND a synchronous console.error JSON envelope (for
    // when the logger's async flush is too slow). stderr is captured
    // immediately by Cloud Logging.
    const finallyWork = async (): Promise<void> => {
      if (pollFailed) {
        await releaseLeaseWithError(database, uid, pollFailed, {
          setUnlinked: pollFailed === "refresh_revoked",
        });
      } else {
        const finalPatch: Record<string, unknown> = {
          linked: true,
          lastPolledAt: FieldValue.serverTimestamp(),
          lastError: null,
          itemsAdded: collectedCount,
          poll_lease: null,
          updatedAt: FieldValue.serverTimestamp(),
        };
        if (resolvedXUserId) finalPatch.xUserId = resolvedXUserId;
        if (newLatestTweetId) finalPatch.latest_tweet_id = newLatestTweetId;
        await statusRef.set(finalPatch, { merge: true });
      }
    };
    let timeoutHandle: ReturnType<typeof setTimeout> | undefined;
    try {
      await Promise.race([
        finallyWork(),
        new Promise<never>((_, reject) => {
          timeoutHandle = setTimeout(
            () => reject(new Error("finally_timeout_5s")),
            5000,
          );
        }),
      ]);
    } catch (e) {
      const msg = (e as Error).message;
      const where = msg === "finally_timeout_5s" ? "timeout" : "throw";
      logger.error("daily_poll_finally_failed", { uid, code: msg, where });
      // Synchronous fallback bypasses the firebase-functions logger's async
      // flush path; stderr is captured immediately by Cloud Logging.
      // eslint-disable-next-line no-console
      console.error(
        JSON.stringify({
          severity: "ERROR",
          message: "daily_poll_finally_failed",
          uid,
          code: msg,
          where,
        }),
      );
    } finally {
      if (timeoutHandle) clearTimeout(timeoutHandle);
    }
  }
}
