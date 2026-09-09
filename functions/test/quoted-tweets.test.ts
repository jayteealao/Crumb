// Regression test for the quoted-tweet body write in lib/poll.ts.
//
// A bookmarked tweet that quotes another tweet must persist the quoted tweet's
// body as a referenced=true tweet doc (users/{uid}/tweets/{quotedId}) so the
// Android card can render the quoted block. The X API already returns the body in
// includes.tweets[] (via the referenced_tweets.id + referenced_tweets.id.author_id
// expansions); the poll just never wrote it. This guards three contracts:
//   1. a `quoted` reference with a body → the body doc is written (referenced=true).
//   2. a `replied_to` / `retweeted` reference → no body doc (those are not surfaced).
//   3. a `quoted` reference whose body is absent (deleted/protected) → only the
//      _ref_ doc, no body doc (the client renders "unavailable").
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

describe("runPoll — quoted-tweet body write", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedGetRefreshToken.mockResolvedValue("rt-stored");
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it("writes a quoted reference's body as a referenced=true tweet doc", async () => {
    const ctx = setupFake();
    ctx.seed("users/uid1/sync_status/state", { linked: true, xUserId: "x-123" });

    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse({ access_token: "at-1" }))
      .mockResolvedValueOnce(
        jsonResponse({
          data: [
            {
              id: "T1",
              text: "check this out",
              author_id: "u-a",
              created_at: "2026-05-01T00:00:00.000Z",
              conversation_id: "T1",
              referenced_tweets: [{ type: "quoted", id: "Q1" }],
            },
          ],
          includes: {
            users: [
              { id: "u-a", username: "author", name: "Author" },
              { id: "u-q", username: "quoted", name: "Quoted Author" },
            ],
            tweets: [
              {
                id: "Q1",
                text: "the original quoted body",
                author_id: "u-q",
                created_at: "2026-04-30T00:00:00.000Z",
                conversation_id: "Q1",
              },
            ],
          },
          meta: {},
        }),
      );

    const result = await runPoll("uid1", { reason: "scheduled" });
    expect(result.ok).toBe(true);

    // The quoted body doc is written, referenced=true, with the camelCase aliases.
    const quotedSet = findSet(ctx, "users/uid1/tweets/Q1");
    expect(quotedSet).toBeDefined();
    expect(quotedSet!.data.text).toBe("the original quoted body");
    expect(quotedSet!.data.authorId).toBe("u-q");
    expect(quotedSet!.data.referenced).toBe(true);
    expect(quotedSet!.data.tweetId).toBe("Q1");

    // The reference link doc carries the type so Android can filter to quoted.
    const refSet = findSet(ctx, "users/uid1/includes/T1_ref_Q1");
    expect(refSet).toBeDefined();
    expect(refSet!.data.type).toBe("quoted");

    // The quoted author was written to twitter_users (it is in includes.users).
    expect(findSet(ctx, "users/uid1/twitter_users/u-q")).toBeDefined();

    // The parent bookmark doc is a normal (non-referenced) tweet.
    const parentSet = findSet(ctx, "users/uid1/tweets/T1");
    expect(parentSet).toBeDefined();
    expect(parentSet!.data.referenced).toBeUndefined();
  });

  it("does NOT write a body doc for replied_to / retweeted references", async () => {
    const ctx = setupFake();
    ctx.seed("users/uid1/sync_status/state", { linked: true, xUserId: "x-123" });

    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse({ access_token: "at-1" }))
      .mockResolvedValueOnce(
        jsonResponse({
          data: [
            {
              id: "T2",
              text: "a reply",
              author_id: "u-a",
              created_at: "2026-05-01T00:00:00.000Z",
              conversation_id: "C2",
              referenced_tweets: [
                { type: "replied_to", id: "R1" },
                { type: "retweeted", id: "RT1" },
              ],
            },
          ],
          includes: {
            users: [{ id: "u-a", username: "author", name: "Author" }],
            tweets: [
              { id: "R1", text: "the parent of the reply", author_id: "u-b", created_at: "2026-04-29T00:00:00.000Z", conversation_id: "C2" },
              { id: "RT1", text: "the retweeted body", author_id: "u-c", created_at: "2026-04-28T00:00:00.000Z", conversation_id: "RT1" },
            ],
          },
          meta: {},
        }),
      );

    const result = await runPoll("uid1", { reason: "scheduled" });
    expect(result.ok).toBe(true);

    // Neither the replied-to nor the retweeted body is written as a tweet doc.
    expect(findSet(ctx, "users/uid1/tweets/R1")).toBeUndefined();
    expect(findSet(ctx, "users/uid1/tweets/RT1")).toBeUndefined();

    // The _ref_ docs are still written (for both reference types).
    expect(findSet(ctx, "users/uid1/includes/T2_ref_R1")).toBeDefined();
    expect(findSet(ctx, "users/uid1/includes/T2_ref_RT1")).toBeDefined();
  });

  it("writes only the _ref_ doc when a quoted body is absent (deleted/protected)", async () => {
    const ctx = setupFake();
    ctx.seed("users/uid1/sync_status/state", { linked: true, xUserId: "x-123" });

    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse({ access_token: "at-1" }))
      .mockResolvedValueOnce(
        jsonResponse({
          data: [
            {
              id: "T3",
              text: "quotes a since-deleted tweet",
              author_id: "u-a",
              created_at: "2026-05-01T00:00:00.000Z",
              conversation_id: "T3",
              referenced_tweets: [{ type: "quoted", id: "Q3" }],
            },
          ],
          includes: {
            users: [{ id: "u-a", username: "author", name: "Author" }],
            // X silently omits a deleted/protected quoted tweet from includes.tweets.
            tweets: [],
          },
          meta: {},
        }),
      );

    const result = await runPoll("uid1", { reason: "scheduled" });
    expect(result.ok).toBe(true);

    // No body doc for the unavailable quote.
    expect(findSet(ctx, "users/uid1/tweets/Q3")).toBeUndefined();
    // The reference link doc is still written → the client renders "unavailable".
    const refSet = findSet(ctx, "users/uid1/includes/T3_ref_Q3");
    expect(refSet).toBeDefined();
    expect(refSet!.data.type).toBe("quoted");
  });
});
