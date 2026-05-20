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
    setLinkedSyncStatus(ctx, "uid1");
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
});

// Note: dailyPoll handler-wiring smoke test lives in dailyPoll-handler.test.ts.
// Mocking via jest.doMock conflicts with this file's top-level jest.mock of
// ../src/lib/admin, so the smoke case is isolated in its own file.
