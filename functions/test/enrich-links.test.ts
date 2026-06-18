// Tests for the link-preview enrichment core (lib/links + lib/enrich-links).
//
// Exercises the pure external-URL picker and the best-effort doc writer against
// the in-memory Firestore fake + a mocked OG fetcher (the real lib/og is never
// imported, so the suite has no `open-graph-scraper` dependency). Mirrors
// poll-media-variants.test.ts's firebase-admin/firestore mock for FieldValue.

import { createFakeDb, type FakeContext } from "./fakes/firestore";

jest.mock("firebase-admin/firestore", () => ({
  FieldValue: { serverTimestamp: jest.fn(() => "<server-ts>") },
  FieldPath: { documentId: () => "__name__" },
}));

import { pickExternalUrl, isExternalUrl } from "../src/lib/links";
import { runEnrichLinks } from "../src/lib/enrich-links";
import type { OpenGraphData } from "../src/lib/og";

const URL_DOC = "users/uid1/textAnnotations/TW1_urls_10_33";

function entitiesWith(urls: Array<Record<string, unknown>>): unknown {
  return { urls };
}

describe("pickExternalUrl", () => {
  it("returns the first external (non-twitter/x.com) link", () => {
    const picked = pickExternalUrl(
      entitiesWith([
        { expanded_url: "https://twitter.com/user/status/1", url: "https://t.co/a", start: 0, end: 5 },
        { expanded_url: "https://example.com/article", url: "https://t.co/b", start: 10, end: 33 },
        { expanded_url: "https://other.com/second", url: "https://t.co/c", start: 40, end: 60 },
      ]),
    );
    expect(picked?.expanded_url).toBe("https://example.com/article");
  });

  it("skips internal x.com / twitter.com permalinks", () => {
    const picked = pickExternalUrl(
      entitiesWith([{ expanded_url: "https://x.com/user/status/2", url: "https://t.co/x" }]),
    );
    expect(picked).toBeNull();
  });

  it("skips media URLs that carry a media_key", () => {
    const picked = pickExternalUrl(
      entitiesWith([{ expanded_url: "https://pic.twitter.com/abc", media_key: "mk1" }]),
    );
    expect(picked).toBeNull();
  });

  it("returns null for entities with no urls array", () => {
    expect(pickExternalUrl({})).toBeNull();
    expect(pickExternalUrl(undefined)).toBeNull();
  });

  it("isExternalUrl mirrors the ARTICLE filter LIKE rule", () => {
    expect(isExternalUrl("https://example.com")).toBe(true);
    expect(isExternalUrl("https://twitter.com/x")).toBe(false);
    expect(isExternalUrl("https://x.com/x")).toBe(false);
    expect(isExternalUrl(undefined)).toBe(false);
  });
});

describe("runEnrichLinks", () => {
  let ctx: FakeContext;
  const okFetch = async (): Promise<OpenGraphData> => ({
    title: "Brutalist Web Design",
    description: "A guide to raw, honest interfaces.",
    image: "https://cdn.example.com/og.jpg",
  });
  const failFetch = async (): Promise<OpenGraphData> => ({});

  beforeEach(() => {
    jest.clearAllMocks();
    ctx = createFakeDb();
  });

  it("writes the url doc with full OG metadata on fetch success", async () => {
    const outcome = await runEnrichLinks(
      ctx.db,
      "uid1",
      "TW1",
      entitiesWith([
        { expanded_url: "https://example.com/article", url: "https://t.co/b", display_url: "example.com/article", start: 10, end: 33 },
      ]),
      okFetch,
    );
    expect(outcome).toBe("written");
    const doc = ctx.store.get(URL_DOC)!;
    expect(doc).toMatchObject({
      tweetId: "TW1",
      type: "urls",
      start: 10,
      end: 33,
      url: "https://t.co/b",
      expandedUrl: "https://example.com/article",
      displayUrl: "example.com/article",
      title: "Brutalist Web Design",
      description: "A guide to raw, honest interfaces.",
      imageUrl: "https://cdn.example.com/og.jpg",
    });
  });

  it("always writes the URL row even when the OG fetch yields nothing (best-effort)", async () => {
    const outcome = await runEnrichLinks(
      ctx.db,
      "uid1",
      "TW1",
      entitiesWith([{ expanded_url: "https://example.com/article", url: "https://t.co/b", start: 10, end: 33 }]),
      failFetch,
    );
    expect(outcome).toBe("written");
    const doc = ctx.store.get(URL_DOC)!;
    expect(doc.expandedUrl).toBe("https://example.com/article");
    expect(doc.title).toBeNull();
    expect(doc.description).toBeNull();
    expect(doc.imageUrl).toBeNull();
  });

  it("is idempotent: a second run skips the existing doc and does not clobber it", async () => {
    await runEnrichLinks(
      ctx.db,
      "uid1",
      "TW1",
      entitiesWith([{ expanded_url: "https://example.com/article", url: "https://t.co/b", start: 10, end: 33 }]),
      okFetch,
    );
    // Second run with a failing fetch must NOT overwrite the enriched row.
    const outcome = await runEnrichLinks(
      ctx.db,
      "uid1",
      "TW1",
      entitiesWith([{ expanded_url: "https://example.com/article", url: "https://t.co/b", start: 10, end: 33 }]),
      failFetch,
    );
    expect(outcome).toBe("skipped");
    expect(ctx.store.get(URL_DOC)!.title).toBe("Brutalist Web Design");
  });

  it("returns no_link (and writes nothing) when the tweet has no external link", async () => {
    const outcome = await runEnrichLinks(
      ctx.db,
      "uid1",
      "TW1",
      entitiesWith([{ expanded_url: "https://x.com/user/status/2" }]),
      okFetch,
    );
    expect(outcome).toBe("no_link");
    expect(ctx.store.size).toBe(0);
  });
});
