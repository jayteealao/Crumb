// Regression test for the defensive video-variant write in lib/poll.ts.
//
// A video tweet's `includes.media[].variants` (HLS + progressive) must land on the
// Firestore media doc in the canonical camelCase shape ({bitRate, contentType, url})
// so the Android inline player can read them. This guards against a future refactor
// dropping the explicit mapping back to the raw `...mr` spread (which the Android
// FirestoreMedia reader would silently drop). Mirrors daily-poll.test.ts's hand-rolled
// mocks (firebase-functions-test does not support v2 onSchedule).

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

describe("runPoll — defensive video-variant media write", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedGetRefreshToken.mockResolvedValue("rt-stored");
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it("writes a video tweet's HLS + progressive variants to the media doc in camelCase", async () => {
    const ctx = setupFake();
    ctx.seed("users/uid1/sync_status/state", { linked: true, xUserId: "x-123" });

    jest
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse({ access_token: "at-1" }))
      .mockResolvedValueOnce(
        jsonResponse({
          data: [
            { id: "TV1", text: "a video tweet", attachments: { media_keys: ["mk1"] } },
          ],
          includes: {
            media: [
              {
                media_key: "mk1",
                type: "video",
                preview_image_url: "https://pbs.twimg.com/poster.jpg",
                duration_ms: 30000,
                variants: [
                  // adaptive HLS master — no bit_rate in the X v2 payload
                  { content_type: "application/x-mpegURL", url: "https://video.twimg.com/master.m3u8" },
                  // progressive renditions with bit_rate
                  { bit_rate: 832000, content_type: "video/mp4", url: "https://video.twimg.com/480.mp4" },
                  { bit_rate: 2176000, content_type: "video/mp4", url: "https://video.twimg.com/720.mp4" },
                ],
              },
            ],
          },
          meta: {},
        }),
      );

    const result = await runPoll("uid1", { reason: "scheduled" });
    expect(result.ok).toBe(true);

    const mediaSet = ctx.journal.find(
      (e): e is Extract<JournalEntry, { op: "set" }> =>
        e.op === "set" && e.path === "users/uid1/media/mk1",
    );
    expect(mediaSet).toBeDefined();

    const variants = mediaSet!.data.variants as Array<Record<string, unknown>>;
    expect(variants).toEqual([
      { bitRate: 0, contentType: "application/x-mpegURL", url: "https://video.twimg.com/master.m3u8" },
      { bitRate: 832000, contentType: "video/mp4", url: "https://video.twimg.com/480.mp4" },
      { bitRate: 2176000, contentType: "video/mp4", url: "https://video.twimg.com/720.mp4" },
    ]);
    // camelCase mediaKey alias still present alongside the typed variants.
    expect(mediaSet!.data.mediaKey).toBe("mk1");
  });
});
