// Unit tests for lib/poll.ts (the pure runPoll function) + a smoke case that
// invokes dailyPoll.run(event) to verify the handler wiring.
//
// Hand-rolled mocks per PO Round 2 Q7. firebase-functions-test does not
// support v2 onSchedule, so we test the pure function directly and cover the
// handler wiring with a single cast-call smoke test.

import { createFakeDb, type FakeContext, type JournalEntry } from "./fakes/firestore";

jest.mock("../src/lib/admin", () => ({
  db: jest.fn(),
  app: jest.fn(),
}));

jest.mock("../src/lib/secrets", () => ({
  getRefreshToken: jest.fn(),
  setRefreshToken: jest.fn(),
  getXClientCredentials: jest.fn(async () => ({ clientId: "client-id", clientSecret: "client-secret" })),
}));

jest.mock("firebase-admin/firestore", () => {
  const fakeTimestamp = (ms: number) => ({
    toMillis: () => ms,
    seconds: Math.floor(ms / 1000),
    nanoseconds: 0,
  });
  return {
    FieldValue: { serverTimestamp: jest.fn(() => "<server-ts>") },
    // FieldPath.documentId() returns the literal "__name__" — the underlying
    // field-path string Firestore uses internally. The hand-rolled fake's
    // `where` filter recognizes this sentinel as a doc-id match.
    FieldPath: { documentId: () => "__name__" },
    Timestamp: {
      now: () => fakeTimestamp(Date.now()),
      fromMillis: (ms: number) => fakeTimestamp(ms),
      fromDate: (d: Date) => fakeTimestamp(d.getTime()),
    },
  };
});

import { db as mockedDbFn } from "../src/lib/admin";
import {
  getRefreshToken as mockedGetRt,
  setRefreshToken as mockedSetRt,
} from "../src/lib/secrets";
import { runPoll } from "../src/lib/poll";

const mockedDb = mockedDbFn as jest.Mock;
const mockedGetRefreshToken = mockedGetRt as jest.Mock;
const mockedSetRefreshToken = mockedSetRt as jest.Mock;

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
    ...init,
  });
}

function tokenResponse(refreshToken?: string): Response {
  return jsonResponse({ access_token: "at-1", refresh_token: refreshToken });
}

function setupFake(): FakeContext {
  const ctx = createFakeDb();
  mockedDb.mockReturnValue(ctx.db);
  return ctx;
}

function setLinkedSyncStatus(ctx: FakeContext, uid: string, extra: Record<string, unknown> = {}): void {
  ctx.seed(`users/${uid}/sync_status/state`, { linked: true, xUserId: "x-123", ...extra });
}

describe("runPoll (daily-poll)", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedGetRefreshToken.mockResolvedValue("rt-stored");
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it("(a) empty initial poll: token grant succeeds, no bookmarks, sync_status advances, no RT rotation", async () => {
    const ctx = setupFake();
    ctx.seed("users/uid1/sync_status/state", { linked: true, xUserId: "x-123" });
    const fetchSpy = jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(tokenResponse(/* same as stored */ "rt-stored"))
      .mockResolvedValueOnce(jsonResponse({ data: [], meta: {} }));

    const result = await runPoll("uid1", { reason: "scheduled" });

    expect(result).toEqual({ ok: true, itemsAdded: 0, itemsFlaggedPendingDelete: 0 });
    expect(mockedSetRefreshToken).not.toHaveBeenCalled();
    // Final lease-release write happened and lastError is null.
    const finalSets = ctx.journal.filter(
      (e): e is Extract<JournalEntry, { op: "set" }> => e.op === "set" && e.path === "users/uid1/sync_status/state",
    );
    const lastSet = finalSets[finalSets.length - 1];
    expect(lastSet.data.lastError).toBeNull();
    expect(lastSet.data.poll_lease).toBeNull();
    expect(fetchSpy).toHaveBeenCalledTimes(2); // token + bookmarks (xUserId cached)
  });

  it("(b) second poll with overlap: stops at known id, persists rotated RT, does not flag boundary as missing", async () => {
    const ctx = setupFake();
    // sync_status.latest_tweet_id is the BigInt-string cache populated by the
    // backfill script + maintained by the success-path finally. The string
    // equality fallback in `isAtOrBelowBoundary` handles synthetic ids.
    setLinkedSyncStatus(ctx, "uid1", { latest_tweet_id: "T1" });
    ctx.seed("users/uid1/tweets/T1", { id: "T1", pending_delete: true });

    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(tokenResponse("rt-NEW")) // rotated RT
      .mockResolvedValueOnce(
        jsonResponse({
          data: [
            { id: "T3", text: "newest" },
            { id: "T2", text: "newer" },
            { id: "T1", text: "known — stop here" },
          ],
          meta: {},
        }),
      );

    const result = await runPoll("uid1", { reason: "scheduled" });

    expect(result).toEqual({ ok: true, itemsAdded: 2, itemsFlaggedPendingDelete: 0 });
    expect(mockedSetRefreshToken).toHaveBeenCalledWith("uid1", "rt-NEW");
    // T3 + T2 written with pending_delete: false; T1 stays as boundary (not in collected).
    const t3Set = ctx.journal.find(
      (e): e is Extract<JournalEntry, { op: "set" }> => e.op === "set" && e.path === "users/uid1/tweets/T3",
    );
    expect(t3Set).toBeDefined();
    expect(t3Set!.data.pending_delete).toBe(false);
    const t2Set = ctx.journal.find(
      (e): e is Extract<JournalEntry, { op: "set" }> => e.op === "set" && e.path === "users/uid1/tweets/T2",
    );
    expect(t2Set).toBeDefined();
  });

  it("(c) 429 retry then success: honors x-rate-limit-reset and recovers", async () => {
    const ctx = setupFake();
    setLinkedSyncStatus(ctx, "uid1");
    const resetEpoch = Math.floor(Date.now() / 1000) + 1;

    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(
        new Response("rate limited", {
          status: 429,
          headers: { "x-rate-limit-reset": String(resetEpoch) },
        }),
      )
      .mockResolvedValueOnce(jsonResponse({ data: [{ id: "T9" }], meta: {} }));

    const result = await runPoll("uid1", { reason: "scheduled" });
    expect(result).toEqual({ ok: true, itemsAdded: 1, itemsFlaggedPendingDelete: 0 });
  }, 30000);

  it("(d) 429 exhausted after MAX_RETRIES: lastError=rate_limited, linked preserved", async () => {
    const ctx = setupFake();
    setLinkedSyncStatus(ctx, "uid1");

    const tooMany = new Response("rate limited", {
      status: 429,
      headers: { "x-rate-limit-reset": String(Math.floor(Date.now() / 1000)) },
    });
    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(tokenResponse())
      // 4 attempts (1 initial + 3 retries)
      .mockResolvedValue(tooMany);

    const result = await runPoll("uid1", { reason: "scheduled" });
    expect(result).toEqual({ ok: false, reason: "rate_limited" });

    const finalSets = ctx.journal.filter(
      (e): e is Extract<JournalEntry, { op: "set" }> => e.op === "set" && e.path === "users/uid1/sync_status/state",
    );
    const lastSet = finalSets[finalSets.length - 1];
    expect(lastSet.data.lastError).toBe("rate_limited");
    expect(lastSet.data.linked).toBeUndefined(); // not set (preserved as seeded)
    expect(lastSet.data.poll_lease).toBeNull();
  }, 30000);

  it("(e) refresh-token grant returns invalid_grant: linked=false, lastError=refresh_revoked, NO Secret Manager write", async () => {
    const ctx = setupFake();
    setLinkedSyncStatus(ctx, "uid1");

    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ error: "invalid_grant" }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        }),
      );

    const result = await runPoll("uid1", { reason: "scheduled" });
    expect(result).toEqual({ ok: false, reason: "refresh_revoked" });
    expect(mockedSetRefreshToken).not.toHaveBeenCalled();

    const finalSets = ctx.journal.filter(
      (e): e is Extract<JournalEntry, { op: "set" }> => e.op === "set" && e.path === "users/uid1/sync_status/state",
    );
    const lastSet = finalSets[finalSets.length - 1];
    expect(lastSet.data.linked).toBe(false);
    expect(lastSet.data.lastError).toBe("refresh_revoked");
  });

  it("(f) pagination-bug emulation: data.length===50 but no meta.next_token, loop terminates gracefully", async () => {
    const ctx = setupFake();
    setLinkedSyncStatus(ctx, "uid1");

    const fiftyTweets = Array.from({ length: 50 }, (_, i) => ({ id: `T${i}`, text: `t${i}` }));
    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(jsonResponse({ data: fiftyTweets, meta: {} /* no next_token */ }));

    const result = await runPoll("uid1", { reason: "scheduled" });
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.itemsAdded).toBe(50);
    }
  });

  it("(g) trigger-mode within DEBOUNCE_MS returns debounced without fetching", async () => {
    const ctx = setupFake();
    ctx.seed("users/uid1/sync_status/state", {
      linked: true,
      xUserId: "x-123",
      lastPolledAt: { toMillis: () => Date.now() - 10_000, seconds: 0 },
    });
    const fetchSpy = jest.spyOn(globalThis, "fetch");

    const result = await runPoll("uid1", { reason: "trigger" });
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.reason).toBe("debounced");
      expect(typeof result.retryAfter).toBe("number");
    }
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("(h) path-guard throws on traversal-style uid", async () => {
    setupFake();
    await expect(runPoll("../evil", { reason: "scheduled" })).rejects.toThrow(/invalid_uid/);
  });

  it("(i) concurrent invocation while lease is held returns in_progress without fetching", async () => {
    const ctx = setupFake();
    ctx.seed("users/uid1/sync_status/state", {
      linked: true,
      xUserId: "x-123",
      poll_lease: {
        holder: "other",
        acquired_at: { toMillis: () => Date.now(), seconds: 0 },
        expires_at: { toMillis: () => Date.now() + 20_000, seconds: 0 },
      },
    });
    const fetchSpy = jest.spyOn(globalThis, "fetch");

    const result = await runPoll("uid1", { reason: "trigger" });
    expect(result).toEqual({ ok: false, reason: "in_progress" });
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("(j) BigInt boundary: 18-char id is numerically below a 19-char latest_tweet_id and is NOT flagged pending_delete", async () => {
    // Mixed-length snowflake corpus: a 2017-era 18-char id and a 2024+ 19-char id.
    // Lexicographically `"823..." > "1812..."` (because '8' > '1' at pos 0),
    // but numerically the 18-char id is smaller. The fix must use BigInt.
    const ctx = setupFake();
    const cachedLatest = "1812345678901234567"; // 19-char, boundary
    const olderShort = "823456789012345678"; // 18-char, 2017-era
    const newerHigh = "1923456789012345678"; // 19-char, newer than cache
    setLinkedSyncStatus(ctx, "uid1", { latest_tweet_id: cachedLatest });
    ctx.seed(`users/uid1/tweets/${olderShort}`, { id: olderShort, pending_delete: false });
    ctx.seed(`users/uid1/tweets/${cachedLatest}`, { id: cachedLatest, pending_delete: false });

    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(
        jsonResponse({
          data: [
            { id: newerHigh, text: "newer than cache" },
            { id: cachedLatest, text: "boundary — stop here" },
          ],
          meta: {},
        }),
      );

    const result = await runPoll("uid1", { reason: "scheduled" });

    expect(result).toEqual({ ok: true, itemsAdded: 1, itemsFlaggedPendingDelete: 0 });
    // The 18-char id is below the boundary numerically; broken lex compare
    // would have flagged it pending_delete here.
    const olderShortPendingSets = ctx.journal.filter(
      (e): e is Extract<JournalEntry, { op: "set" }> =>
        e.op === "set" &&
        e.path === `users/uid1/tweets/${olderShort}` &&
        e.data.pending_delete === true,
    );
    expect(olderShortPendingSets).toHaveLength(0);
    // Success-path patch advanced the cache to the newer id.
    const finalSets = ctx.journal.filter(
      (e): e is Extract<JournalEntry, { op: "set" }> =>
        e.op === "set" && e.path === "users/uid1/sync_status/state",
    );
    const lastSet = finalSets[finalSets.length - 1];
    expect(lastSet.data.latest_tweet_id).toBe(newerHigh);
    expect(lastSet.data.poll_lease).toBeNull();
  });

  it("(k) pending_delete diff chunks writes ≤ 450 ops per batch when missingNow > 500", async () => {
    // Seed 600 stored tweets, none echoed by X this poll → all 600 land in
    // missingNow. Without chunking the single batch.commit() would exceed
    // Firestore's 500-op cap.
    const ctx = setupFake();
    setLinkedSyncStatus(ctx, "uid1");
    for (let i = 1; i <= 600; i++) {
      const id = `T${String(i).padStart(4, "0")}`;
      ctx.seed(`users/uid1/tweets/${id}`, { id });
    }
    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(jsonResponse({ data: [], meta: {} }));

    const result = await runPoll("uid1", { reason: "scheduled" });

    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.itemsFlaggedPendingDelete).toBe(600);
    }
    // 600 / 450 = 2 batch commits for pending_delete writes (the empty
    // collection-write batch loop committed nothing). Each chunk must be
    // ≤ 450 ops.
    const batchCommits = ctx.journal.filter(
      (e): e is Extract<JournalEntry, { op: "batchCommit" }> => e.op === "batchCommit",
    );
    expect(batchCommits.length).toBeGreaterThanOrEqual(2);
    for (const bc of batchCommits) {
      expect(bc.count).toBeLessThanOrEqual(450);
    }
    const totalOps = batchCommits.reduce((sum, e) => sum + e.count, 0);
    expect(totalOps).toBe(600);
  });

  it("(l) pending_delete precondition: deleted=true docs are skipped via chunked `in` query", async () => {
    // 60 stored tweets, two marked deleted. Empty bookmarks page → all 60
    // land in missingNow → 2 chunks of 30 `in` queries → 58 pending_delete
    // writes.
    const ctx = setupFake();
    setLinkedSyncStatus(ctx, "uid1");
    for (let i = 1; i <= 60; i++) {
      const id = `T${String(i).padStart(2, "0")}`;
      const data: Record<string, unknown> = { id };
      if (id === "T30" || id === "T45") data.deleted = true;
      ctx.seed(`users/uid1/tweets/${id}`, data);
    }
    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(jsonResponse({ data: [], meta: {} }));

    const result = await runPoll("uid1", { reason: "scheduled" });

    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.itemsFlaggedPendingDelete).toBe(58);
    }
    // 60 ids → 2 chunks of 30 → 2 documentId `in` queryGet entries.
    const docIdInQueries = ctx.journal.filter(
      (e): e is Extract<JournalEntry, { op: "queryGet" }> =>
        e.op === "queryGet" &&
        e.path === "users/uid1/tweets" &&
        !!e.where?.some((w) => w.field === "__name__" && w.op === "in"),
    );
    expect(docIdInQueries).toHaveLength(2);
    // No pending_delete set on T30 or T45.
    const deletedSetEvents = ctx.journal.filter(
      (e): e is Extract<JournalEntry, { op: "set" }> =>
        e.op === "set" &&
        (e.path === "users/uid1/tweets/T30" || e.path === "users/uid1/tweets/T45") &&
        e.data.pending_delete === true,
    );
    expect(deletedSetEvents).toHaveLength(0);
  });

  it("(m) finally-block visibility: daily_poll_finally_failed is logged synchronously when sync_status set throws", async () => {
    // Inject a throw on the SUCCESS-path sync_status write (predicate matches
    // only the patch with lastPolledAt, leaving the lease-tx write intact).
    // The synchronous console.error fallback must fire even though the
    // firebase-functions logger may swallow async-buffered output on Gen 2.
    const ctx = setupFake();
    setLinkedSyncStatus(ctx, "uid1");
    ctx.failNextSet(
      "firestore_unavailable",
      (path, data) =>
        path === "users/uid1/sync_status/state" && data.lastPolledAt !== undefined,
    );

    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(jsonResponse({ data: [], meta: {} }));

    const consoleSpy = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);

    const result = await runPoll("uid1", { reason: "scheduled" });
    // The try-block returned ok:true before the finally ran; finally's catch
    // swallows the injected throw after logging.
    expect(result).toEqual({ ok: true, itemsAdded: 0, itemsFlaggedPendingDelete: 0 });

    // The firebase-functions logger ALSO writes to console.error when
    // running outside a Cloud Functions runtime, so the spy receives more
    // than one call. Pick the synchronous fallback by exact message match.
    const ourCall = consoleSpy.mock.calls.find((call) => {
      const arg = call[0];
      if (typeof arg !== "string") return false;
      try {
        const parsed = JSON.parse(arg) as Record<string, unknown>;
        return parsed.message === "daily_poll_finally_failed";
      } catch {
        return false;
      }
    });
    expect(ourCall).toBeDefined();
    const parsed = JSON.parse(ourCall![0] as string) as Record<string, unknown>;
    expect(parsed.severity).toBe("ERROR");
    expect(parsed.uid).toBe("uid1");
    expect(parsed.where).toBe("throw");
    expect(parsed.code).toBe("firestore_unavailable");
  });
});

// Note: dailyPoll handler-wiring smoke test lives in dailyPoll-handler.test.ts.
// Mocking via jest.doMock conflicts with this file's top-level jest.mock of
// ../src/lib/admin, so the smoke case is isolated in its own file.
