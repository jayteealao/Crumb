// Unit tests for unfurl-vendor.ts — IframelyAdapter (active vendor) and
// MicrolinkAdapter (reference implementation).
//
// All tests use fake fetch responses and injectable DNS lookup (DnsLookupAll).
// No real network calls; no jest.mock() needed.
//
// IframelyAdapter reads IFRAMELY_BASE_URL from process.env. Tests inject a fake
// URL in beforeEach and mock fetch + google-auth-library.

import { resolveViaUnfurl, IframelyAdapter, MicrolinkAdapter } from "../src/lib/unfurl-vendor";
import type { DnsLookupAll } from "../src/lib/og";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Public DNS lookup stub (all hostnames resolve to a public IP). */
const publicLookup: DnsLookupAll = async () => [{ address: "18.244.124.21", family: 4 }];

/** Private DNS lookup stub (all hostnames resolve to a private IP). */
const privateLookup: DnsLookupAll = async () => [{ address: "10.0.0.1", family: 4 }];

/** A far-future deadline so budget checks do not interfere with normal tests. */
const FAR_DEADLINE = Date.now() + 60_000;

const FAKE_IFRAMELY_BASE = "https://iframely.fake-run.example.com";

/** Build a minimal fake JSON response for the iframely API. */
function fakeIframelyResponse(data: Record<string, unknown>, status = 200) {
  return {
    status,
    ok: status >= 200 && status < 300,
    headers: { get: () => "application/json" },
    json: async () => data,
  } as unknown as Response;
}

/** iframely success payload with title + thumbnail link. */
const RICH_IFRAMELY_PAYLOAD = {
  meta: {
    title: "Example Article Title",
    description: "A description of the article.",
  },
  links: {
    thumbnail: [{ href: "https://cdn.example.com/cover.png", rel: ["thumbnail"] }],
  },
};

/** Mock getAuthClient so tests don't hit the metadata service. */
jest.mock("google-auth-library", () => ({
  GoogleAuth: jest.fn().mockImplementation(() => ({
    getIdTokenClient: jest.fn().mockResolvedValue({
      getRequestHeaders: jest.fn().mockResolvedValue({
        Authorization: "Bearer fake-oidc-token",
      }),
    }),
  })),
}));

// ---------------------------------------------------------------------------
// IframelyAdapter — success paths
// ---------------------------------------------------------------------------

describe("IframelyAdapter – success: returns title and image from iframely response", () => {
  const realFetch = globalThis.fetch;
  beforeEach(() => {
    process.env["IFRAMELY_BASE_URL"] = FAKE_IFRAMELY_BASE;
  });
  afterEach(() => {
    globalThis.fetch = realFetch;
    delete process.env["IFRAMELY_BASE_URL"];
  });

  it("maps meta.title + links.thumbnail[0].href correctly", async () => {
    globalThis.fetch = jest.fn(async () => fakeIframelyResponse(RICH_IFRAMELY_PAYLOAD)) as unknown as typeof globalThis.fetch;

    const adapter = new IframelyAdapter();
    const result = await adapter.resolve("https://example.com/article", publicLookup, FAR_DEADLINE);

    expect(result).not.toBeNull();
    expect(result!.title).toBe("Example Article Title");
    expect(result!.image).toBe("https://cdn.example.com/cover.png");
    expect(result!.description).toBe("A description of the article.");
  });

  it("handles links.thumbnail as plain object (not array) — single-item edge case", async () => {
    const objectThumbnailPayload = {
      meta: { title: "Single Thumbnail" },
      links: {
        thumbnail: { href: "https://cdn.example.com/thumb.png", rel: "thumbnail" },
      },
    };
    globalThis.fetch = jest.fn(async () => fakeIframelyResponse(objectThumbnailPayload)) as unknown as typeof globalThis.fetch;

    const adapter = new IframelyAdapter();
    const result = await adapter.resolve("https://example.com/article", publicLookup, FAR_DEADLINE);

    expect(result).not.toBeNull();
    expect(result!.image).toBe("https://cdn.example.com/thumb.png");
  });

  it("falls back to links.icon[0].href when links.thumbnail is absent", async () => {
    const iconPayload = {
      meta: { title: "Icon Fallback" },
      links: {
        icon: [{ href: "https://example.com/icon.png", rel: ["icon"] }],
      },
    };
    globalThis.fetch = jest.fn(async () => fakeIframelyResponse(iconPayload)) as unknown as typeof globalThis.fetch;

    const adapter = new IframelyAdapter();
    const result = await adapter.resolve("https://example.com/article", publicLookup, FAR_DEADLINE);

    expect(result).not.toBeNull();
    expect(result!.image).toBe("https://example.com/icon.png");
  });
});

// ---------------------------------------------------------------------------
// IframelyAdapter — private image URL stripped (defence-in-depth)
// ---------------------------------------------------------------------------

describe("IframelyAdapter – private image URL stripped via isSafePublicUrl", () => {
  const realFetch = globalThis.fetch;
  beforeEach(() => {
    process.env["IFRAMELY_BASE_URL"] = FAKE_IFRAMELY_BASE;
  });
  afterEach(() => {
    globalThis.fetch = realFetch;
    delete process.env["IFRAMELY_BASE_URL"];
  });

  it("strips private thumbnail URL (192.168.x) and returns title only", async () => {
    const privateImagePayload = {
      meta: { title: "Article With Private Image" },
      links: {
        thumbnail: [{ href: "http://192.168.1.1/img.png" }],
      },
    };
    globalThis.fetch = jest.fn(async () => fakeIframelyResponse(privateImagePayload)) as unknown as typeof globalThis.fetch;

    const adapter = new IframelyAdapter();
    const result = await adapter.resolve("https://example.com/article", publicLookup, FAR_DEADLINE);

    expect(result).not.toBeNull();
    expect(result!.title).toBe("Article With Private Image");
    expect(result!.image).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// IframelyAdapter — failure paths → null
// ---------------------------------------------------------------------------

describe("IframelyAdapter – failure paths return null", () => {
  const realFetch = globalThis.fetch;
  beforeEach(() => {
    process.env["IFRAMELY_BASE_URL"] = FAKE_IFRAMELY_BASE;
  });
  afterEach(() => {
    globalThis.fetch = realFetch;
    delete process.env["IFRAMELY_BASE_URL"];
  });

  it("returns null on HTTP 500", async () => {
    globalThis.fetch = jest.fn(async () => fakeIframelyResponse({}, 500)) as unknown as typeof globalThis.fetch;

    const adapter = new IframelyAdapter();
    const result = await adapter.resolve("https://example.com/article", publicLookup, FAR_DEADLINE);
    expect(result).toBeNull();
  });

  it("returns null when IFRAMELY_BASE_URL env var is missing", async () => {
    delete process.env["IFRAMELY_BASE_URL"];
    const fetchSpy = jest.fn();
    globalThis.fetch = fetchSpy as unknown as typeof globalThis.fetch;

    const adapter = new IframelyAdapter();
    const result = await adapter.resolve("https://example.com/article", publicLookup, FAR_DEADLINE);

    expect(result).toBeNull();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("returns null immediately when deadline is already expired (no fetch)", async () => {
    const fetchSpy = jest.fn();
    globalThis.fetch = fetchSpy as unknown as typeof globalThis.fetch;

    const expiredDeadline = Date.now() - 1;
    const adapter = new IframelyAdapter();
    const result = await adapter.resolve("https://example.com/article", publicLookup, expiredDeadline);

    expect(result).toBeNull();
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// IframelyAdapter — Authorization header passed (token hygiene)
// ---------------------------------------------------------------------------

describe("IframelyAdapter – ID token sent as Authorization header", () => {
  const realFetch = globalThis.fetch;
  beforeEach(() => {
    process.env["IFRAMELY_BASE_URL"] = FAKE_IFRAMELY_BASE;
  });
  afterEach(() => {
    globalThis.fetch = realFetch;
    delete process.env["IFRAMELY_BASE_URL"];
  });

  it("sends Authorization: Bearer <token> header and not in the URL", async () => {
    const capturedOptions: RequestInit[] = [];
    globalThis.fetch = jest.fn(async (_url: unknown, opts?: RequestInit) => {
      capturedOptions.push(opts ?? {});
      return fakeIframelyResponse(RICH_IFRAMELY_PAYLOAD);
    }) as unknown as typeof globalThis.fetch;

    const adapter = new IframelyAdapter();
    await adapter.resolve("https://example.com/article", publicLookup, FAR_DEADLINE);

    expect(capturedOptions.length).toBeGreaterThan(0);
    const headers = capturedOptions[0].headers as Record<string, string>;
    expect(headers["Authorization"]).toMatch(/^Bearer /);

    // Token must NOT appear in the URL query string
    const calledUrl = String((globalThis.fetch as jest.Mock).mock.calls[0][0]);
    expect(calledUrl).not.toContain("Bearer");
  });
});

// ---------------------------------------------------------------------------
// IframelyAdapter — SSRF block on iframely service hostname
// ---------------------------------------------------------------------------

describe("IframelyAdapter – SSRF block on iframely service endpoint", () => {
  const realFetch = globalThis.fetch;
  beforeEach(() => {
    // Use a public-looking hostname that privateLookup will resolve to a private IP
    process.env["IFRAMELY_BASE_URL"] = "https://evil-iframely.fake-run.example.com";
  });
  afterEach(() => {
    globalThis.fetch = realFetch;
    delete process.env["IFRAMELY_BASE_URL"];
  });

  it("returns null without fetching when iframely hostname resolves to private IP", async () => {
    const fetchSpy = jest.fn();
    globalThis.fetch = fetchSpy as unknown as typeof globalThis.fetch;

    const adapter = new IframelyAdapter();
    // privateLookup returns 10.0.0.1 for any hostname (including evil-iframely.internal)
    const result = await adapter.resolve("https://example.com/article", privateLookup, FAR_DEADLINE);

    expect(result).toBeNull();
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// resolveViaUnfurl — delegates to IframelyAdapter
// ---------------------------------------------------------------------------

describe("resolveViaUnfurl – delegates to IframelyAdapter", () => {
  const realFetch = globalThis.fetch;
  beforeEach(() => {
    process.env["IFRAMELY_BASE_URL"] = FAKE_IFRAMELY_BASE;
  });
  afterEach(() => {
    globalThis.fetch = realFetch;
    delete process.env["IFRAMELY_BASE_URL"];
  });

  it("returns {title, image, description} from a well-formed iframely response", async () => {
    globalThis.fetch = jest.fn(async () => fakeIframelyResponse(RICH_IFRAMELY_PAYLOAD)) as unknown as typeof globalThis.fetch;

    const result = await resolveViaUnfurl(
      "https://example.com/article",
      publicLookup,
      FAR_DEADLINE,
    );

    expect(result).not.toBeNull();
    expect(result!.title).toBe("Example Article Title");
    expect(result!.image).toBe("https://cdn.example.com/cover.png");
    expect(result!.description).toBe("A description of the article.");
  });

  it("returns null when IFRAMELY_BASE_URL is not set (no fetch)", async () => {
    delete process.env["IFRAMELY_BASE_URL"];
    const fetchSpy = jest.fn();
    globalThis.fetch = fetchSpy as unknown as typeof globalThis.fetch;

    const result = await resolveViaUnfurl(
      "https://example.com/article",
      publicLookup,
      FAR_DEADLINE,
    );

    expect(result).toBeNull();
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// MicrolinkAdapter — baseline (reference implementation still works)
// ---------------------------------------------------------------------------

/** Build a minimal fake JSON response for the Microlink API. */
function fakeMicrolinkResponse(data: Record<string, unknown>, status = 200) {
  return {
    status,
    ok: status >= 200 && status < 300,
    headers: { get: () => "application/json" },
    json: async () => data,
  } as unknown as Response;
}

const RICH_MICROLINK_PAYLOAD = {
  status: "success",
  data: {
    title: "Example Article Title",
    description: "A description of the article.",
    image: { url: "https://cdn.example.com/cover.png" },
  },
};

describe("MicrolinkAdapter – reference implementation (not wired)", () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it("adapter calls fetch when budget is positive (baseline — kept as reference)", async () => {
    globalThis.fetch = jest.fn(async () => fakeMicrolinkResponse(RICH_MICROLINK_PAYLOAD)) as unknown as typeof globalThis.fetch;

    const adapter = new MicrolinkAdapter();
    const result = await adapter.resolve(
      "https://example.com/article",
      publicLookup,
      FAR_DEADLINE,
    );

    expect(result).not.toBeNull();
    expect(result!.title).toBe("Example Article Title");
    expect(globalThis.fetch as jest.Mock).toHaveBeenCalled();
  });
});
