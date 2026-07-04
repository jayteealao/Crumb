// Unit tests for oembed.ts — provider registry, link discovery, and fetch.
//
// Fully deterministic: uses fake fetch + injected DNS lookup. No real network calls.

import { matchProvider, discoverOembedLink, fetchOembed } from "../src/lib/oembed";
import { DnsLookupAll } from "../src/lib/og";

// ---------------------------------------------------------------------------
// Helpers (mirrors og.test.ts pattern without importing from it)
// ---------------------------------------------------------------------------

/** A fake lookup that always returns a public IP. */
const publicLookup: DnsLookupAll =
  async () => [{ address: "93.184.216.34", family: 4 }];

/** A fake lookup that returns a specific private address (simulates DNS rebind). */
function privateLookup(address: string): DnsLookupAll {
  return async () => [{ address, family: 4 }];
}

/** Fake JSON response for a successful oEmbed endpoint. */
function fakeOembedResponse(body: object, status = 200) {
  const text = JSON.stringify(body);
  let sent = false;
  return {
    status,
    ok: status >= 200 && status < 300,
    headers: { get: () => "application/json" },
    json: async () => {
      if (!sent) { sent = true; return JSON.parse(text); }
      throw new Error("already consumed");
    },
  };
}

const FUTURE_DEADLINE = Date.now() + 30_000;

// ---------------------------------------------------------------------------
// matchProvider — registry lookups
// ---------------------------------------------------------------------------

describe("matchProvider – positive cases (registry providers)", () => {
  it.each([
    ["youtube.com",         "https://www.youtube.com/watch?v=dQw4w9WgXcQ"],
    ["youtu.be short link", "https://youtu.be/dQw4w9WgXcQ"],
    ["vimeo.com",           "https://vimeo.com/123456789"],
    ["open.spotify.com",    "https://open.spotify.com/track/abc"],
    ["soundcloud.com",      "https://soundcloud.com/artist/track"],
    ["flickr.com",          "https://www.flickr.com/photos/user/123"],
    ["tiktok.com",          "https://www.tiktok.com/@user/video/123"],
  ] as Array<[string, string]>)(
    "matches %s → returns an endpoint URL",
    (_label, url) => {
      const result = matchProvider(url);
      expect(result).not.toBeNull();
      expect(result).toMatch(/^https:\/\//);
      // The encoded page URL must appear in the endpoint query string
      expect(result).toContain(encodeURIComponent(url));
    },
  );
});

describe("matchProvider – negative cases (non-registry hosts)", () => {
  it.each([
    ["example.com",   "https://example.com/article"],
    ["twitter.com",   "https://twitter.com/user/status/123"],
    ["medium.com",    "https://medium.com/@user/article"],
    ["github.com",    "https://github.com/user/repo"],
  ] as Array<[string, string]>)(
    "returns null for %s",
    (_label, url) => {
      expect(matchProvider(url)).toBeNull();
    },
  );
});

describe("matchProvider – www. stripping", () => {
  it("matches www.youtube.com via youtube.com registry entry", () => {
    const url = "https://www.youtube.com/watch?v=abc";
    const result = matchProvider(url);
    expect(result).not.toBeNull();
    expect(result).toContain("youtube.com/oembed");
  });

  it("matches www.flickr.com via flickr.com registry entry", () => {
    const result = matchProvider("https://www.flickr.com/photos/u/1");
    expect(result).not.toBeNull();
  });
});

describe("matchProvider – malformed URL", () => {
  it("returns null for a non-URL string", () => {
    expect(matchProvider("not-a-url")).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// discoverOembedLink — HTML link-rel parser
// ---------------------------------------------------------------------------

describe("discoverOembedLink – finds href in both attribute orderings", () => {
  it("finds href when type attribute comes before href (type-before-href)", () => {
    const html =
      '<html><head>' +
      '<link rel="alternate" type="application/json+oembed" href="https://oembed.example.com/endpoint">' +
      '</head><body></body></html>';
    expect(discoverOembedLink(html)).toBe("https://oembed.example.com/endpoint");
  });

  it("finds href when href attribute comes before type (href-before-type)", () => {
    const html =
      '<html><head>' +
      '<link rel="alternate" href="https://oembed.example.com/ep2" type="application/json+oembed">' +
      '</head><body></body></html>';
    expect(discoverOembedLink(html)).toBe("https://oembed.example.com/ep2");
  });

  it("handles single-quoted attribute values", () => {
    const html =
      "<html><head>" +
      "<link rel='alternate' type='application/json+oembed' href='https://oembed.example.com/ep3'>" +
      "</head><body></body></html>";
    expect(discoverOembedLink(html)).toBe("https://oembed.example.com/ep3");
  });

  it("handles extra attributes between rel and type/href", () => {
    const html =
      '<html><head>' +
      '<link rel="alternate" title="oEmbed" type="application/json+oembed" href="https://ep.example.com/" data-x="y">' +
      '</head><body></body></html>';
    expect(discoverOembedLink(html)).toBe("https://ep.example.com/");
  });

  it("returns null when no oEmbed link tag is present", () => {
    const html = '<html><head><link rel="stylesheet" href="/style.css"></head></html>';
    expect(discoverOembedLink(html)).toBeNull();
  });

  it("returns null for empty HTML", () => {
    expect(discoverOembedLink("")).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// fetchOembed — success paths
// ---------------------------------------------------------------------------

describe("fetchOembed – success: returns title and image", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => { globalThis.fetch = realFetch; });

  it("returns {title, image} from a well-formed oEmbed JSON response", async () => {
    globalThis.fetch = jest.fn(async () =>
      fakeOembedResponse({ title: "My Video", thumbnail_url: "https://cdn.example.com/thumb.jpg" }),
    ) as unknown as typeof globalThis.fetch;

    const result = await fetchOembed(
      "https://www.youtube.com/oembed?url=https%3A%2F%2Fyoutu.be%2Fabc&format=json",
      publicLookup,
      FUTURE_DEADLINE,
    );
    expect(result).not.toBeNull();
    expect(result?.title).toBe("My Video");
    expect(result?.image).toBe("https://cdn.example.com/thumb.jpg");
  });

  it("returns {title} only when thumbnail_url is absent", async () => {
    globalThis.fetch = jest.fn(async () =>
      fakeOembedResponse({ title: "Title Only Video" }),
    ) as unknown as typeof globalThis.fetch;

    const result = await fetchOembed("https://vimeo.com/api/oembed.json?url=x", publicLookup, FUTURE_DEADLINE);
    expect(result?.title).toBe("Title Only Video");
    expect(result?.image).toBeUndefined();
  });

  it("returns null when both title and thumbnail_url are absent", async () => {
    globalThis.fetch = jest.fn(async () =>
      fakeOembedResponse({ type: "video" }),
    ) as unknown as typeof globalThis.fetch;

    const result = await fetchOembed("https://vimeo.com/api/oembed.json?url=x", publicLookup, FUTURE_DEADLINE);
    expect(result).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// fetchOembed — SSRF safety on the endpoint URL
// ---------------------------------------------------------------------------

describe("fetchOembed – SSRF block on private endpoint URL", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => { globalThis.fetch = realFetch; });

  it("returns null when the endpoint URL is on a private host (DNS resolves to private IP)", async () => {
    const fetchSpy = jest.fn();
    globalThis.fetch = fetchSpy as unknown as typeof globalThis.fetch;

    const result = await fetchOembed(
      "https://evil.example.com/oembed?url=x",
      privateLookup("10.0.0.1"),
      FUTURE_DEADLINE,
    );
    expect(result).toBeNull();
    // fetch must never be called — blocked before network
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("returns null when the endpoint URL is a literal private IP", async () => {
    const fetchSpy = jest.fn();
    globalThis.fetch = fetchSpy as unknown as typeof globalThis.fetch;

    const result = await fetchOembed(
      "http://169.254.169.254/oembed?url=x",
      publicLookup,
      FUTURE_DEADLINE,
    );
    expect(result).toBeNull();
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// fetchOembed — SSRF safety on thumbnail_url
// ---------------------------------------------------------------------------

describe("fetchOembed – private thumbnail_url is stripped", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => { globalThis.fetch = realFetch; });

  it("returns {title} without image when thumbnail_url is a private IP", async () => {
    globalThis.fetch = jest.fn(async () =>
      fakeOembedResponse({ title: "Safe Title", thumbnail_url: "http://192.168.1.1/thumb.jpg" }),
    ) as unknown as typeof globalThis.fetch;

    const result = await fetchOembed(
      "https://www.youtube.com/oembed?url=x&format=json",
      publicLookup,
      FUTURE_DEADLINE,
    );
    expect(result?.title).toBe("Safe Title");
    expect(result?.image).toBeUndefined();
  });

  it("returns {title} without image when thumbnail_url is a localhost URL", async () => {
    globalThis.fetch = jest.fn(async () =>
      fakeOembedResponse({ title: "Good Title", thumbnail_url: "http://localhost/img.png" }),
    ) as unknown as typeof globalThis.fetch;

    const result = await fetchOembed(
      "https://www.youtube.com/oembed?url=x&format=json",
      publicLookup,
      FUTURE_DEADLINE,
    );
    expect(result?.title).toBe("Good Title");
    expect(result?.image).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// fetchOembed — error handling
// ---------------------------------------------------------------------------

describe("fetchOembed – non-200 response", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => { globalThis.fetch = realFetch; });

  it("returns null for a 404 response", async () => {
    globalThis.fetch = jest.fn(async () =>
      fakeOembedResponse({}, 404),
    ) as unknown as typeof globalThis.fetch;

    const result = await fetchOembed("https://vimeo.com/api/oembed.json?url=x", publicLookup, FUTURE_DEADLINE);
    expect(result).toBeNull();
  });
});

describe("fetchOembed – malformed JSON", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => { globalThis.fetch = realFetch; });

  it("returns null when the response body is not valid JSON", async () => {
    globalThis.fetch = jest.fn(async () => ({
      status: 200,
      ok: true,
      headers: { get: () => "application/json" },
      json: async () => { throw new SyntaxError("Unexpected token"); },
    })) as unknown as typeof globalThis.fetch;

    const result = await fetchOembed("https://soundcloud.com/oembed?url=x&format=json", publicLookup, FUTURE_DEADLINE);
    expect(result).toBeNull();
  });
});

describe("fetchOembed – budget already expired", () => {
  it("returns null immediately when deadline is in the past", async () => {
    const fetchSpy = jest.fn();
    globalThis.fetch = fetchSpy as unknown as typeof globalThis.fetch;

    const expiredDeadline = Date.now() - 1;
    const result = await fetchOembed("https://www.youtube.com/oembed?url=x&format=json", publicLookup, expiredDeadline);
    expect(result).toBeNull();
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
