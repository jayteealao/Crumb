jest.mock("../src/lib/state", () => ({
  verifyOAuthState: jest.fn(),
}));

jest.mock("../src/lib/secrets", () => ({
  getXClientCredentials: jest.fn(),
  setRefreshToken: jest.fn(),
}));

const mockDocSet = jest.fn(async () => undefined);
const mockDoc = jest.fn(() => ({ set: mockDocSet }));

jest.mock("../src/lib/admin", () => ({
  db: jest.fn(() => ({ doc: mockDoc })),
  app: jest.fn(),
}));

jest.mock("firebase-admin/firestore", () => ({
  FieldValue: { serverTimestamp: jest.fn(() => "<server-ts>") },
}));

import { verifyOAuthState } from "../src/lib/state";
import { getXClientCredentials, setRefreshToken } from "../src/lib/secrets";
import { oauthCallback } from "../src/handlers/oauthCallback";

type AnyFn = (...args: unknown[]) => unknown;

function makeReq(query: Record<string, string>): unknown {
  return { query, method: "GET", headers: {} };
}

function makeRes() {
  const res: {
    statusCode: number | null;
    sentBody: unknown;
    redirectedTo: { status: number; url: string } | null;
    status: jest.Mock;
    send: jest.Mock;
    redirect: jest.Mock;
    set: jest.Mock;
    setHeader: jest.Mock;
    end: jest.Mock;
  } = {
    statusCode: null,
    sentBody: undefined,
    redirectedTo: null,
    status: jest.fn(),
    send: jest.fn(),
    redirect: jest.fn(),
    set: jest.fn(),
    setHeader: jest.fn(),
    end: jest.fn(),
  };
  res.status.mockImplementation((code: number) => {
    res.statusCode = code;
    return res;
  });
  res.send.mockImplementation((body: unknown) => {
    res.sentBody = body;
    return res;
  });
  res.redirect.mockImplementation((status: number, url?: string) => {
    if (typeof url === "string") {
      res.redirectedTo = { status, url };
    } else {
      res.redirectedTo = { status: 302, url: status as unknown as string };
    }
    return res;
  });
  return res;
}

async function invoke(req: unknown, res: unknown): Promise<void> {
  // firebase-functions v2 onRequest returns an Express-style handler.
  await (oauthCallback as unknown as AnyFn)(req, res);
}

describe("oauthCallback", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (getXClientCredentials as jest.Mock).mockResolvedValue({
      clientId: "client-id",
      clientSecret: "client-secret",
    });
    (setRefreshToken as jest.Mock).mockResolvedValue(undefined);
  });

  afterEach(() => {
    // restore fetch
    if ((globalThis.fetch as { mockRestore?: () => void } | undefined)?.mockRestore) {
      (globalThis.fetch as unknown as { mockRestore: () => void }).mockRestore();
    }
  });

  it("rejects an invalid state with 400 and does not touch Secret Manager or Firestore", async () => {
    (verifyOAuthState as jest.Mock).mockRejectedValue(new Error("invalid_state_missing"));
    const fetchSpy = jest.spyOn(globalThis, "fetch");

    const req = makeReq({ code: "c", state: "forged", code_verifier: "v" });
    const res = makeRes();
    await invoke(req, res);

    expect(res.statusCode).toBe(400);
    expect(setRefreshToken).not.toHaveBeenCalled();
    expect(mockDocSet).not.toHaveBeenCalled();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("happy path: persists refresh token, writes sync_status, redirects to success deep link", async () => {
    (verifyOAuthState as jest.Mock).mockResolvedValue({
      uid: "uid1",
      nonce: "n",
      iat: Math.floor(Date.now() / 1000),
    });
    jest.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ refresh_token: "rt-secret", access_token: "at" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    const req = makeReq({ code: "c", state: "valid", code_verifier: "v" });
    const res = makeRes();
    await invoke(req, res);

    expect(setRefreshToken).toHaveBeenCalledWith("uid1", "rt-secret");
    expect(mockDoc).toHaveBeenCalledWith("users/uid1/sync_status/state");
    expect(mockDocSet).toHaveBeenCalledWith(
      expect.objectContaining({ linked: true, lastPolledAt: null, lastError: null }),
      { merge: true },
    );
    expect(res.redirectedTo).toEqual({
      status: 302,
      url: "crumbs://graphitenerd.xyz/x-oauth-complete",
    });
  });

  it("X returns invalid_grant: writes lastError, no Secret Manager write, redirects to error deep link", async () => {
    (verifyOAuthState as jest.Mock).mockResolvedValue({
      uid: "uid1",
      nonce: "n",
      iat: Math.floor(Date.now() / 1000),
    });
    jest.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ error: "invalid_grant" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      }),
    );

    const req = makeReq({ code: "c", state: "valid", code_verifier: "v" });
    const res = makeRes();
    await invoke(req, res);

    expect(setRefreshToken).not.toHaveBeenCalled();
    expect(mockDocSet).toHaveBeenCalledWith(
      expect.objectContaining({ linked: false, lastError: "invalid_grant" }),
      { merge: true },
    );
    expect(res.redirectedTo).toEqual({
      status: 302,
      url: "crumbs://graphitenerd.xyz/x-oauth-error?reason=invalid_grant",
    });
  });
});
