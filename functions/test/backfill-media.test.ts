// Unit tests for handlers/backfillMedia.ts (onCall v2).
//
// The poll fix corrects attribution only for NEW bookmarks; this callable repairs
// the back-catalogue's includes/{tweetId}_media_{mediaKey} junction docs. For each
// stored tweet it keeps the junctions whose media_key the tweet OWNS (its
// attachments.media_keys), DELETES the page-level cross-product junctions it does
// not own, and writes any owned junction that is missing (e.g. a quoted body whose
// media was never linked). It reads only data already on the stored doc — no X API
// quota — and is idempotent. Mirrors disconnect.test.ts's onCall runner pattern.

import { createFakeDb, type FakeContext } from "./fakes/firestore";

jest.mock("../src/lib/admin", () => ({
  db: jest.fn(),
  app: jest.fn(),
}));

jest.mock("firebase-admin/firestore", () => ({
  FieldValue: { serverTimestamp: jest.fn(() => "<server-ts>") },
  FieldPath: { documentId: () => "__name__" },
}));

import { db as mockedDbFn } from "../src/lib/admin";
import { backfillTweetMedia } from "../src/handlers/backfillMedia";

const mockedDb = mockedDbFn as jest.Mock;

type Runner = {
  run: (request: { auth?: { uid: string }; data?: unknown; rawRequest?: unknown }) => Promise<{
    scanned: number;
    rewritten: number;
    deleted: number;
    capped: boolean;
  }>;
};

function setupFake(): FakeContext {
  const ctx = createFakeDb();
  mockedDb.mockReturnValue(ctx.db);
  return ctx;
}

/** Seed the cross-product corruption: A owns M1, B owns M2, T (text) owns nothing,
 *  Q (quoted body) owns M3 but was never linked. */
function seedCorruptCorpus(ctx: FakeContext): void {
  ctx.seed("users/uid1/tweets/A", { tweetId: "A", attachments: { media_keys: ["M1"] } });
  ctx.seed("users/uid1/tweets/B", { tweetId: "B", attachments: { media_keys: ["M2"] } });
  ctx.seed("users/uid1/tweets/T", { tweetId: "T", text: "a text tweet, no media" });
  ctx.seed("users/uid1/tweets/Q", { tweetId: "Q", referenced: true, attachments: { media_keys: ["M3"] } });

  // Cross-product junctions written by the old poll: every tweet × every page media.
  ctx.seed("users/uid1/includes/A_media_M1", { tweetId: "A", mediaKey: "M1", kind: "media" }); // correct
  ctx.seed("users/uid1/includes/A_media_M2", { tweetId: "A", mediaKey: "M2", kind: "media" }); // wrong
  ctx.seed("users/uid1/includes/B_media_M1", { tweetId: "B", mediaKey: "M1", kind: "media" }); // wrong
  ctx.seed("users/uid1/includes/B_media_M2", { tweetId: "B", mediaKey: "M2", kind: "media" }); // correct
  ctx.seed("users/uid1/includes/T_media_M1", { tweetId: "T", mediaKey: "M1", kind: "media" }); // wrong (text tweet)
  // Q_media_M3 is MISSING — the quoted body's media was never linked by the old poll.

  // A non-media junction that must survive the kind="media" filter untouched.
  ctx.seed("users/uid1/includes/A_user_u-a", { tweetId: "A", userId: "u-a", kind: "user" });
}

describe("backfillTweetMedia", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("rejects an unauthenticated request", async () => {
    setupFake();
    const runner = backfillTweetMedia as unknown as Runner;
    await expect(runner.run({ data: {}, rawRequest: {} })).rejects.toMatchObject({
      code: "unauthenticated",
    });
  });

  it("deletes wrong junctions, keeps correct ones, and writes missing owned junctions", async () => {
    const ctx = setupFake();
    seedCorruptCorpus(ctx);

    const runner = backfillTweetMedia as unknown as Runner;
    const result = await runner.run({ auth: { uid: "uid1" }, data: {}, rawRequest: {} });

    // 4 tweets scanned; 3 wrong junctions deleted; 1 missing owned junction written.
    expect(result.scanned).toBe(4);
    expect(result.deleted).toBe(3);
    expect(result.rewritten).toBe(1);
    expect(result.capped).toBe(false);

    // Correct junctions survive.
    expect(ctx.store.has("users/uid1/includes/A_media_M1")).toBe(true);
    expect(ctx.store.has("users/uid1/includes/B_media_M2")).toBe(true);

    // Cross-product junctions are gone.
    expect(ctx.store.has("users/uid1/includes/A_media_M2")).toBe(false);
    expect(ctx.store.has("users/uid1/includes/B_media_M1")).toBe(false);
    expect(ctx.store.has("users/uid1/includes/T_media_M1")).toBe(false);

    // The quoted body's own media junction is created.
    expect(ctx.store.has("users/uid1/includes/Q_media_M3")).toBe(true);
    expect(ctx.store.get("users/uid1/includes/Q_media_M3")).toMatchObject({
      tweetId: "Q",
      mediaKey: "M3",
      kind: "media",
    });

    // The user junction is left untouched (kind != "media").
    expect(ctx.store.has("users/uid1/includes/A_user_u-a")).toBe(true);
  });

  it("is idempotent — a second run changes nothing", async () => {
    const ctx = setupFake();
    seedCorruptCorpus(ctx);

    const runner = backfillTweetMedia as unknown as Runner;
    await runner.run({ auth: { uid: "uid1" }, data: {}, rawRequest: {} });
    const second = await runner.run({ auth: { uid: "uid1" }, data: {}, rawRequest: {} });

    expect(second.scanned).toBe(4);
    expect(second.deleted).toBe(0);
    expect(second.rewritten).toBe(0);

    // Final state: exactly the correct owned junctions (+ the user junction).
    const mediaJunctions = [...ctx.store.keys()]
      .filter((k) => k.startsWith("users/uid1/includes/") && k.includes("_media_"))
      .sort();
    expect(mediaJunctions).toEqual([
      "users/uid1/includes/A_media_M1",
      "users/uid1/includes/B_media_M2",
      "users/uid1/includes/Q_media_M3",
    ]);
  });
});
