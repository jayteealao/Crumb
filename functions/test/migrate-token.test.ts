// Unit tests for handlers/migrateXToken.ts (onCall v2).
// Hand-rolled mocks following the trigger-poll.test.ts precedent.

import { createFakeDb, type FakeContext } from "./fakes/firestore";

jest.mock("../src/lib/admin", () => ({
  db: jest.fn(),
  app: jest.fn(),
}));

jest.mock("../src/lib/secrets", () => ({
  setRefreshToken: jest.fn(),
  getXClientCredentials: jest.fn(async () => ({ clientId: "c", clientSecret: "s" })),
}));

const mockRunPoll = jest.fn(async () => ({ ok: true, itemsAdded: 0, itemsFlaggedPendingDelete: 0 }));
jest.mock("../src/lib/poll", () => ({
  runPoll: mockRunPoll,
}));

jest.mock("firebase-admin/firestore", () => ({
  FieldValue: { serverTimestamp: jest.fn(() => "<server-ts>") },
}));

import { db as mockedDbFn } from "../src/lib/admin";
import { setRefreshToken as mockedSetRt } from "../src/lib/secrets";
import { migrateXToken } from "../src/handlers/migrateXToken";

const mockedDb = mockedDbFn as jest.Mock;
const mockedSetRefreshToken = mockedSetRt as jest.Mock;

type Runner = {
  run: (request: { auth?: { uid: string }; data?: unknown; rawRequest?: unknown }) => Promise<unknown>;
};

function setupFake(): FakeContext {
  const ctx = createFakeDb();
  mockedDb.mockReturnValue(ctx.db);
  return ctx;
}

function flushFanOut(): Promise<void> {
  return new Promise((r) => setImmediate(r));
}

describe("migrateXToken", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedSetRefreshToken.mockResolvedValue(undefined);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it("(a) unauthenticated request throws HttpsError(unauthenticated)", async () => {
    setupFake();
    const runner = migrateXToken as unknown as Runner;
    await expect(
      runner.run({ data: { refreshToken: "rt" }, rawRequest: {} }),
    ).rejects.toMatchObject({ code: "unauthenticated" });
  });

  it("(b) missing refreshToken throws HttpsError(invalid-argument)", async () => {
    setupFake();
    const runner = migrateXToken as unknown as Runner;
    await expect(
      runner.run({ auth: { uid: "uid1" }, data: {}, rawRequest: {} }),
    ).rejects.toMatchObject({ code: "invalid-argument" });
    expect(mockedSetRefreshToken).not.toHaveBeenCalled();
  });

  it("(c) X refresh 401 returns {ok:false, reason:invalid}; no setRefreshToken; no sync_status write", async () => {
    const ctx = setupFake();
    jest.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ error: "invalid_grant" }), { status: 401 }),
    );

    const runner = migrateXToken as unknown as Runner;
    const result = (await runner.run({
      auth: { uid: "uid1" },
      data: { refreshToken: "rt-stale" },
      rawRequest: {},
    })) as { ok: boolean; reason?: string };

    expect(result).toEqual({ ok: false, reason: "invalid" });
    expect(mockedSetRefreshToken).not.toHaveBeenCalled();
    expect(ctx.store.get("users/uid1/sync_status/state")).toBeUndefined();
    expect(mockRunPoll).not.toHaveBeenCalled();
  });

  it("(d) X refresh 200 with rotated RT: setRefreshToken called with rotated, sync_status.linked=true, runPoll fan-out", async () => {
    const ctx = setupFake();
    jest.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ access_token: "at", refresh_token: "rt-rotated" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    const runner = migrateXToken as unknown as Runner;
    const result = (await runner.run({
      auth: { uid: "uid1" },
      data: { refreshToken: "rt-original" },
      rawRequest: {},
    })) as { ok: boolean };
    await flushFanOut();

    expect(result).toEqual({ ok: true });
    expect(mockedSetRefreshToken).toHaveBeenCalledWith("uid1", "rt-rotated");
    const status = ctx.store.get("users/uid1/sync_status/state");
    expect(status?.linked).toBe(true);
    expect(status?.lastError).toBeNull();
    expect(mockRunPoll).toHaveBeenCalledWith("uid1");
  });

  it("(e) X refresh 200 without rotation: setRefreshToken called with original token", async () => {
    setupFake();
    jest.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ access_token: "at" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    const runner = migrateXToken as unknown as Runner;
    const result = (await runner.run({
      auth: { uid: "uid1" },
      data: { refreshToken: "rt-original" },
      rawRequest: {},
    })) as { ok: boolean };
    await flushFanOut();

    expect(result).toEqual({ ok: true });
    expect(mockedSetRefreshToken).toHaveBeenCalledWith("uid1", "rt-original");
  });

  it("(f) X fetch throws network error: HttpsError(internal); no setRefreshToken", async () => {
    setupFake();
    jest.spyOn(globalThis, "fetch").mockRejectedValueOnce(new Error("ETIMEDOUT"));

    const runner = migrateXToken as unknown as Runner;
    await expect(
      runner.run({
        auth: { uid: "uid1" },
        data: { refreshToken: "rt" },
        rawRequest: {},
      }),
    ).rejects.toMatchObject({ code: "internal" });
    expect(mockedSetRefreshToken).not.toHaveBeenCalled();
  });
});
