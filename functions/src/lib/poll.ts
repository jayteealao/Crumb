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
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import type { DocumentReference, WriteBatch, Firestore } from "firebase-admin/firestore";

import { db } from "./admin";
import { getRefreshToken, setRefreshToken, getXClientCredentials } from "./secrets";
import { buildBookmarksUrl, USERS_ME_URL, TOKEN_URL } from "./twitter-api";

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
  poll_lease?: LeaseState | null;
  [key: string]: unknown;
}

function assertPathScoped(path: string, uid: string): void {
  const prefix = `users/${uid}/`;
  if (!path.startsWith(prefix)) {
    throw new Error(`path_guard_violation: ${path}`);
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
    const latestIdSnap = await database
      .collection(`users/${uid}/tweets`)
      .orderBy("id", "desc")
      .limit(1)
      .select()
      .get();
    const latestIdInDb: string | undefined = latestIdSnap.empty
      ? undefined
      : latestIdSnap.docs[0].id;

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
        if (latestIdInDb && tweet.id === latestIdInDb) {
          // Boundary tweet IS still present in X — record as seen but do not
          // re-write its full payload (no new data above the overlap line for it).
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
      const tweetDoc: Record<string, unknown> = {
        ...tweetWithoutMetrics,
        pending_delete: false,
        updatedAt: FieldValue.serverTimestamp(),
      };
      enqueue(database.doc(`users/${uid}/tweets/${tweet.id}`), tweetDoc);

      if (public_metrics && typeof public_metrics === "object") {
        enqueue(database.doc(`users/${uid}/metrics/${tweet.id}`), {
          ...(public_metrics as Record<string, unknown>),
          updatedAt: FieldValue.serverTimestamp(),
        });
      }

      const includesUsers = includes?.users ?? [];
      for (const u of includesUsers) {
        enqueue(database.doc(`users/${uid}/twitter_users/${u.id}`), {
          ...u,
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
        enqueue(database.doc(`users/${uid}/media/${m.media_key}`), {
          ...m,
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
        // Only flag if strictly above the overlap boundary.
        if (latestIdInDb && id > latestIdInDb) {
          missingNow.push(id);
        }
      } else {
        missingNow.push(id);
      }
    }

    if (missingNow.length > 0) {
      const pdBatch = database.batch();
      let flagged = 0;
      for (const id of missingNow) {
        const docRef = database.doc(`users/${uid}/tweets/${id}`);
        assertPathScoped(docRef.path, uid);
        const docSnap = await docRef.get();
        if (docSnap.data()?.deleted === true) continue;
        pdBatch.set(
          docRef,
          {
            pending_delete: true,
            pending_delete_detected_at: Timestamp.now(),
            updatedAt: FieldValue.serverTimestamp(),
          },
          { merge: true },
        );
        flagged++;
      }
      if (flagged > 0) {
        await pdBatch.commit();
      }
      flaggedCount = flagged;
    }

    return {
      ok: true,
      itemsAdded: collectedCount,
      itemsFlaggedPendingDelete: flaggedCount,
    };
  } finally {
    // Sub-step 4h: release lease + write sync_status (always runs).
    try {
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
        await statusRef.set(finalPatch, { merge: true });
      }
    } catch (e) {
      logger.error("daily_poll_finally_failed", { uid, code: (e as Error).message });
    }
  }
}
