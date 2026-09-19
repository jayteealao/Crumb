// Regression test for the tweet↔media attribution fix in lib/poll.ts.
//
// `includes.media` is page-level (every media object for every tweet on a page).
// The poll used to loop the whole bag per tweet, writing an
// `includes/{tweetId}_media_{mediaKey}` junction for EVERY (tweet × page-media)
// pair — a cross-product that attached a neighbour's (commonly a quoted/co-page
// tweet's) image or video to the wrong card. The fix attributes media by each
// tweet's OWN `attachments.media_keys`, and routes a quoted tweet's media to the
// quoted body (X v2 does not promote it into the quoter). This guards:
//   1. two media tweets on one page → each gets ONLY its own junction (no cross-product).
//   2. a quote tweet → the quoted tweet's media junction is on the QUOTED id, never the quoter.
//
// Mirrors poll-media-variants.test.ts's hand-rolled mocks.

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
    FieldPath: { documentId: () => "__name__" },
    Timestamp: {
      now: () => fakeTimestamp(Date.now()),
      fromMillis: (ms: number) => fakeTimestamp(ms),
      fromDate: (d: Date) => fakeTimestamp(d.getTime()),
    },
  };
});

import { db as mockedDbFn } from "../src/lib/admin";
import { getRefreshToken as mockedGetRt } from "../src/lib/secrets";
import { runPoll } from "../src/lib/poll";

const mockedDb = mockedDbFn as jest.Mock;
const mockedGetRefreshToken = mockedGetRt as jest.Mock;

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

function setupFake(): FakeContext {
  const ctx = createFakeDb();
  mockedDb.mockReturnValue(ctx.db);
  return ctx;
}

function findSet(ctx: FakeContext, path: string) {
  return ctx.journal.find(
    (e): e is Extract<JournalEntry, { op: "set" }> => e.op === "set" && e.path === path,
  );
}

describe("runPoll — per-tweet media attribution", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedGetRefreshToken.mockResolvedValue("rt-stored");
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it("writes ONLY each tweet's own media junction — no page-level cross-product", async () => {
    const ctx = setupFake();
    ctx.seed("users/uid1/sync_status/state", { linked: true, xUserId: "x-123" });

    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse({ access_token: "at-1" }))
      .mockResolvedValueOnce(
        jsonResponse({
          // Two media tweets returned on the same page. The page-level media bag
          // holds BOTH media objects; the old code attributed both to both tweets.
          data: [
            { id: "A", text: "tweet A", author_id: "u-a", created_at: "2026-05-01T00:00:00.000Z", conversation_id: "A", attachments: { media_keys: ["M1"] } },
            { id: "B", text: "tweet B", author_id: "u-b", created_at: "2026-05-01T00:01:00.000Z", conversation_id: "B", attachments: { media_keys: ["M2"] } },
          ],
          includes: {
            users: [
              { id: "u-a", username: "aa", name: "AA" },
              { id: "u-b", username: "bb", name: "BB" },
            ],
            media: [
              { media_key: "M1", type: "photo", url: "https://img/M1.jpg" },
              { media_key: "M2", type: "photo", url: "https://img/M2.jpg" },
            ],
          },
          meta: {},
        }),
      );

    const result = await runPoll("uid1", { reason: "scheduled" });
    expect(result.ok).toBe(true);

    // Each tweet links ONLY its own media.
    expect(findSet(ctx, "users/uid1/includes/A_media_M1")).toBeDefined();
    expect(findSet(ctx, "users/uid1/includes/B_media_M2")).toBeDefined();

    // The cross-product junctions must NOT be written.
    expect(findSet(ctx, "users/uid1/includes/A_media_M2")).toBeUndefined();
    expect(findSet(ctx, "users/uid1/includes/B_media_M1")).toBeUndefined();

    // The media docs themselves are still written (keyed globally by media_key),
    // with the camelCase alias the Android reader needs.
    const mediaM1 = findSet(ctx, "users/uid1/media/M1");
    expect(mediaM1).toBeDefined();
    expect(mediaM1!.data.mediaKey).toBe("M1");

    // The owned junction carries the canonical { tweetId, mediaKey, kind } shape.
    const junctionA = findSet(ctx, "users/uid1/includes/A_media_M1")!;
    expect(junctionA.data).toMatchObject({ tweetId: "A", mediaKey: "M1", kind: "media" });
  });

  // M1: a tweet whose attachments.media_keys lists a key NOT present in
  // includes.media should write no media junction and no media doc for that key,
  // and the path should be exercised (logger.warn is emitted).
  it("skips an owned media key that is absent from includes.media (unresolved key)", async () => {
    const ctx = setupFake();
    ctx.seed("users/uid1/sync_status/state", { linked: true, xUserId: "x-123" });

    // Tweet D owns key M99 but includes.media only has M1 (a different key).
    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse({ access_token: "at-1" }))
      .mockResolvedValueOnce(
        jsonResponse({
          data: [
            {
              id: "D",
              text: "tweet with missing media",
              author_id: "u-d",
              created_at: "2026-05-01T00:00:00.000Z",
              conversation_id: "D",
              attachments: { media_keys: ["M99"] },
            },
          ],
          includes: {
            users: [{ id: "u-d", username: "dd", name: "DD" }],
            // M99 is absent — only an unrelated M1 is in the bag.
            media: [{ media_key: "M1", type: "photo", url: "https://img/M1.jpg" }],
          },
          meta: {},
        }),
      );

    const result = await runPoll("uid1", { reason: "scheduled" });
    expect(result.ok).toBe(true);

    // No junction doc for the missing key.
    expect(findSet(ctx, "users/uid1/includes/D_media_M99")).toBeUndefined();
    // No media doc for the missing key either.
    expect(findSet(ctx, "users/uid1/media/M99")).toBeUndefined();
    // The tweet doc itself is still written.
    expect(findSet(ctx, "users/uid1/tweets/D")).toBeDefined();
  });

  it("routes a quoted tweet's media to the QUOTED id, never the quoter", async () => {
    const ctx = setupFake();
    ctx.seed("users/uid1/sync_status/state", { linked: true, xUserId: "x-123" });

    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse({ access_token: "at-1" }))
      .mockResolvedValueOnce(
        jsonResponse({
          // C quotes Q. C has NO media of its own; Q owns M3. X v2 keeps Q's media
          // on the quoted tweet object (includes.tweets), not on C.
          data: [
            {
              id: "C",
              text: "look at this",
              author_id: "u-c",
              created_at: "2026-05-02T00:00:00.000Z",
              conversation_id: "C",
              referenced_tweets: [{ type: "quoted", id: "Q" }],
            },
          ],
          includes: {
            users: [
              { id: "u-c", username: "cc", name: "CC" },
              { id: "u-q", username: "qq", name: "QQ" },
            ],
            tweets: [
              {
                id: "Q",
                text: "the quoted body with a photo",
                author_id: "u-q",
                created_at: "2026-04-30T00:00:00.000Z",
                conversation_id: "Q",
                attachments: { media_keys: ["M3"] },
              },
            ],
            media: [{ media_key: "M3", type: "photo", url: "https://img/M3.jpg" }],
          },
          meta: {},
        }),
      );

    const result = await runPoll("uid1", { reason: "scheduled" });
    expect(result.ok).toBe(true);

    // The quoted body doc is written referenced=true.
    const quotedBody = findSet(ctx, "users/uid1/tweets/Q");
    expect(quotedBody).toBeDefined();
    expect(quotedBody!.data.referenced).toBe(true);

    // Q's media is attributed to Q.
    expect(findSet(ctx, "users/uid1/includes/Q_media_M3")).toBeDefined();
    // C (the quoter) must NOT carry the quoted media.
    expect(findSet(ctx, "users/uid1/includes/C_media_M3")).toBeUndefined();
  });
});
