import { SignJWT } from "jose";

const TEST_KEY = new TextEncoder().encode("test-key-32-bytes-padding-padding");
const WRONG_KEY = new TextEncoder().encode("wrong-key-32-bytes-padding-padded");

jest.mock("../src/lib/secrets", () => ({
  getOAuthStateSecret: jest.fn(async () => TEST_KEY),
}));

import { signOAuthState, verifyOAuthState } from "../src/lib/state";
import * as secrets from "../src/lib/secrets";

describe("oauth state token", () => {
  beforeEach(() => {
    (secrets.getOAuthStateSecret as jest.Mock).mockResolvedValue(TEST_KEY);
  });

  it("signs and verifies a round-trip", async () => {
    const token = await signOAuthState("uid1", "nonce1");
    const claims = await verifyOAuthState(token);
    expect(claims.uid).toBe("uid1");
    expect(claims.nonce).toBe("nonce1");
    expect(typeof claims.iat).toBe("number");
  });

  it("rejects a token signed with the wrong key", async () => {
    const forged = await new SignJWT({ uid: "uid1", nonce: "n" })
      .setProtectedHeader({ alg: "HS256" })
      .setIssuer("crumb-functions")
      .setAudience("crumb-oauth-callback")
      .setIssuedAt()
      .setExpirationTime("600s")
      .sign(WRONG_KEY);
    await expect(verifyOAuthState(forged)).rejects.toThrow();
  });

  it("rejects an empty/missing token", async () => {
    await expect(verifyOAuthState("")).rejects.toThrow();
  });

  it("rejects an expired token (issued > 10 minutes ago)", async () => {
    const expired = await new SignJWT({ uid: "uid1", nonce: "n" })
      .setProtectedHeader({ alg: "HS256" })
      .setIssuer("crumb-functions")
      .setAudience("crumb-oauth-callback")
      .setIssuedAt(Math.floor(Date.now() / 1000) - 700)
      .setExpirationTime(Math.floor(Date.now() / 1000) - 100)
      .sign(TEST_KEY);
    await expect(verifyOAuthState(expired)).rejects.toThrow();
  });

  it("rejects malformed claims (uid not a string)", async () => {
    const malformed = await new SignJWT({ uid: 42, nonce: "n" })
      .setProtectedHeader({ alg: "HS256" })
      .setIssuer("crumb-functions")
      .setAudience("crumb-oauth-callback")
      .setIssuedAt()
      .setExpirationTime("600s")
      .sign(TEST_KEY);
    await expect(verifyOAuthState(malformed)).rejects.toThrow("invalid_state_claims");
  });

  it("rejects a future-dated token (iat beyond 5s clock tolerance)", async () => {
    const future = Math.floor(Date.now() / 1000) + 120;
    const futureToken = await new SignJWT({ uid: "uid1", nonce: "n" })
      .setProtectedHeader({ alg: "HS256" })
      .setIssuer("crumb-functions")
      .setAudience("crumb-oauth-callback")
      .setIssuedAt(future)
      .setExpirationTime(future + 600)
      .sign(TEST_KEY);
    await expect(verifyOAuthState(futureToken)).rejects.toThrow();
  });
});
