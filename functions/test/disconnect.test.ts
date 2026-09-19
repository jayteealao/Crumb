// Unit tests for handlers/disconnectX.ts (onCall v2).

import { createFakeDb, type FakeContext } from "./fakes/firestore";

jest.mock("../src/lib/admin", () => ({
  db: jest.fn(),
  app: jest.fn(),
}));

jest.mock("../src/lib/secrets", () => ({
  deleteRefreshToken: jest.fn(),
}));

jest.mock("firebase-admin/firestore", () => ({
  FieldValue: { serverTimestamp: jest.fn(() => "<server-ts>") },
}));

import { db as mockedDbFn } from "../src/lib/admin";
import { deleteRefreshToken as mockedDeleteRt } from "../src/lib/secrets";
import { disconnectX } from "../src/handlers/disconnectX";

const mockedDb = mockedDbFn as jest.Mock;
const mockedDeleteRefreshToken = mockedDeleteRt as jest.Mock;

type Runner = {
  run: (request: { auth?: { uid: string }; data?: unknown; rawRequest?: unknown }) => Promise<unknown>;
};

function setupFake(): FakeContext {
  const ctx = createFakeDb();
  mockedDb.mockReturnValue(ctx.db);
  return ctx;
}

describe("disconnectX", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("(a) unauthenticated request throws HttpsError(unauthenticated)", async () => {
    setupFake();
    const runner = disconnectX as unknown as Runner;
    await expect(runner.run({ data: {}, rawRequest: {} })).rejects.toMatchObject({
      code: "unauthenticated",
    });
    expect(mockedDeleteRefreshToken).not.toHaveBeenCalled();
  });

  it("(b) deleteSecret NOT_FOUND idempotent: sync_status.linked=false still written, returns ok", async () => {
    const ctx = setupFake();
    // The lib helper already swallows code===5; here it resolves successfully.
    mockedDeleteRefreshToken.mockResolvedValueOnce(undefined);

    const runner = disconnectX as unknown as Runner;
    const result = (await runner.run({
      auth: { uid: "uid1" },
      data: {},
      rawRequest: {},
    })) as { ok: boolean };

    expect(result).toEqual({ ok: true });
    expect(mockedDeleteRefreshToken).toHaveBeenCalledWith("uid1");
    const status = ctx.store.get("users/uid1/sync_status/state");
    expect(status?.linked).toBe(false);
  });

  it("(c) deleteSecret success: sync_status.linked=false, returns ok", async () => {
    const ctx = setupFake();
    mockedDeleteRefreshToken.mockResolvedValueOnce(undefined);

    const runner = disconnectX as unknown as Runner;
    const result = (await runner.run({
      auth: { uid: "uid1" },
      data: {},
      rawRequest: {},
    })) as { ok: boolean };

    expect(result).toEqual({ ok: true });
    expect(ctx.store.get("users/uid1/sync_status/state")?.linked).toBe(false);
  });

  it("(d) deleteSecret non-recoverable error: HttpsError(internal); sync_status NOT updated", async () => {
    const ctx = setupFake();
    const err = Object.assign(new Error("permission_denied"), { code: 7 });
    mockedDeleteRefreshToken.mockRejectedValueOnce(err);

    const runner = disconnectX as unknown as Runner;
    await expect(
      runner.run({ auth: { uid: "uid1" }, data: {}, rawRequest: {} }),
    ).rejects.toMatchObject({ code: "internal" });
    expect(ctx.store.get("users/uid1/sync_status/state")).toBeUndefined();
  });
});
