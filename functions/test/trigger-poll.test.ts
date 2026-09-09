// Unit tests for handlers/triggerPoll.ts (onCall v2).
// Hand-rolled mocks per PO Round 2 Q7.

import { createFakeDb, type FakeContext } from "./fakes/firestore";

jest.mock("../src/lib/admin", () => ({
  db: jest.fn(),
  app: jest.fn(),
}));

jest.mock("../src/lib/secrets", () => ({
  getRefreshToken: jest.fn(),
  setRefreshToken: jest.fn(),
  getXClientCredentials: jest.fn(async () => ({ clientId: "c", clientSecret: "s" })),
}));

jest.mock("firebase-admin/firestore", () => {
  const ts = (ms: number) => ({ toMillis: () => ms, seconds: Math.floor(ms / 1000), nanoseconds: 0 });
  return {
    FieldValue: { serverTimestamp: jest.fn(() => "<server-ts>") },
    Timestamp: {
      now: () => ts(Date.now()),
      fromMillis: (ms: number) => ts(ms),
      fromDate: (d: Date) => ts(d.getTime()),
    },
  };
});

import { db as mockedDbFn } from "../src/lib/admin";
import { getRefreshToken as mockedGetRt } from "../src/lib/secrets";
import { triggerPoll } from "../src/handlers/triggerPoll";

const mockedDb = mockedDbFn as jest.Mock;
const mockedGetRefreshToken = mockedGetRt as jest.Mock;

type Runner = {
  run: (request: { auth?: { uid: string }; data?: unknown; rawRequest?: unknown }) => Promise<unknown>;
};

function setupFake(): FakeContext {
  const ctx = createFakeDb();
  mockedDb.mockReturnValue(ctx.db);
  return ctx;
}

describe("triggerPoll", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedGetRefreshToken.mockResolvedValue("rt-stored");
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it("(a) unauthenticated request throws HttpsError(unauthenticated)", async () => {
    setupFake();
    const runner = triggerPoll as unknown as Runner;
    await expect(
      runner.run({ data: {}, rawRequest: {} }),
    ).rejects.toMatchObject({ code: "unauthenticated" });
  });

  it("(b) within DEBOUNCE_MS of lastPolledAt: returns debounced + retryAfter, no fetch", async () => {
    const ctx = setupFake();
    ctx.seed("users/uid1/sync_status/state", {
      linked: true,
      xUserId: "x-1",
      lastPolledAt: { toMillis: () => Date.now() - 15_000, seconds: 0 },
    });
    const fetchSpy = jest.spyOn(globalThis, "fetch");

    const runner = triggerPoll as unknown as Runner;
    const result = (await runner.run({ auth: { uid: "uid1" }, data: {}, rawRequest: {} })) as {
      ok: boolean;
      reason?: string;
      retryAfter?: number;
    };

    expect(result.ok).toBe(false);
    expect(result.reason).toBe("debounced");
    expect(typeof result.retryAfter).toBe("number");
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("(c) lease held by another caller: returns in_progress, no fetch", async () => {
    const ctx = setupFake();
    ctx.seed("users/uid1/sync_status/state", {
      linked: true,
      xUserId: "x-1",
      poll_lease: {
        holder: "other",
        acquired_at: { toMillis: () => Date.now(), seconds: 0 },
        expires_at: { toMillis: () => Date.now() + 20_000, seconds: 0 },
      },
    });
    const fetchSpy = jest.spyOn(globalThis, "fetch");

    const runner = triggerPoll as unknown as Runner;
    const result = (await runner.run({ auth: { uid: "uid1" }, data: {}, rawRequest: {} })) as {
      ok: boolean;
      reason?: string;
    };

    expect(result.ok).toBe(false);
    expect(result.reason).toBe("in_progress");
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("(d) happy path: returns ok with itemsAdded, lease cleared after run", async () => {
    const ctx = setupFake();
    ctx.seed("users/uid1/sync_status/state", { linked: true, xUserId: "x-1" });

    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ access_token: "at", refresh_token: "rt-stored" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ data: [{ id: "TNEW", text: "hi" }], meta: {} }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      );

    const runner = triggerPoll as unknown as Runner;
    const result = (await runner.run({ auth: { uid: "uid1" }, data: {}, rawRequest: {} })) as {
      ok: boolean;
      itemsAdded?: number;
    };

    expect(result.ok).toBe(true);
    expect(result.itemsAdded).toBe(1);

    const status = ctx.store.get("users/uid1/sync_status/state");
    expect(status?.poll_lease).toBeNull();
  });
});
